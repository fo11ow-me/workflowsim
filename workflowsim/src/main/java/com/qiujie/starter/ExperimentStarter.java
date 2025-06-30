package com.qiujie.starter;

import ch.qos.logback.classic.Level;
import cn.hutool.core.io.FileUtil;
import cn.hutool.json.JSONUtil;
import com.qiujie.entity.Result;
import com.qiujie.entity.SimParameter;
import com.qiujie.enums.LevelEnum;
import com.qiujie.util.ExperimentUtil;
import lombok.AccessLevel;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static com.qiujie.Constants.*;

public abstract class ExperimentStarter {
    public final String name;
    public long seed;
    private final Logger log;
    @Setter(AccessLevel.PROTECTED)
    private Level level;
    private final List<SimParameter> paramList;
    private final Marker EXPERIMENT;

    public ExperimentStarter() {
        // Initialize experiment with the name of the class
        this.name = getClass().getSimpleName();
        System.setProperty("startup.class", name);
        this.log = LoggerFactory.getLogger(ExperimentStarter.class);
        this.level = LEVEL;
        this.paramList = new ArrayList<>();
        this.EXPERIMENT = MarkerFactory.getMarker(LevelEnum.EXPERIMENT.name());
        start();
    }

    private void start() {
        log.info(EXPERIMENT, "{}: Starting...", name);
        this.seed = System.currentTimeMillis();
        try {
            run();
            log.info(EXPERIMENT, String.format("%s: Finished in %.2fs", name, (System.currentTimeMillis() - seed) / 1000.0));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void run() throws Exception {
        createDirs();
        init();
        // Start subprocesses to run simulations
        startSimProcesses();
        // Collect and save results
        List<Result> results = collectResults();
        ExperimentUtil.printExperimentResult(results, name);
        ExperimentUtil.generateExperimentData(results, name);
        // Delete temporary parameter files
        new File(PARAM_DIR + name + ".json").delete();
    }

    private void createDirs() {
        // Create directories for saving parameter, result, simulation data, and experiment data
        FileUtil.mkdir(PARAM_DIR);
        FileUtil.mkdir(RESULT_DIR);
        FileUtil.mkdir(SIM_DATA_DIR);
        FileUtil.mkdir(EXPERIMENT_DATA_DIR);
    }

    protected abstract void init();

    protected void addParam(SimParameter simParameter) {
        // Add a parameter with a unique ID based on the paramList size
        String id = String.format(name + "_%06d", paramList.size());
        simParameter.setId(id);
        paramList.add(simParameter);
    }

    private void startSimProcesses() throws Exception {
        int reservedCores = 8;
        int progressStep = 10;
        String javaPath = System.getProperty("java.home") + "/bin/java";
        int availableCores = Runtime.getRuntime().availableProcessors();
        int maxConcurrent = Math.max(1, availableCores - reservedCores);
        log.info(EXPERIMENT, "🖥️ Detected CPU cores: {}, setting max concurrent processes: {}", availableCores, maxConcurrent);

        String paramFilePath = PARAM_DIR + name + ".json";
        FileUtil.writeUtf8String(JSONUtil.toJsonPrettyStr(paramList), paramFilePath);
        int totalSims = paramList.size();
        AtomicInteger finishedSims = new AtomicInteger();
        ExecutorService executor = Executors.newFixedThreadPool(maxConcurrent);
        List<Future<?>> futures = new ArrayList<>();

        // Start first sim progress log
        log.info(EXPERIMENT, "✅ Progress: 0 / {}", totalSims);

        for (int index = 0; index < totalSims; index++) {
            final int simIndex = index;
            futures.add(executor.submit(() -> {
                try {
                    ProcessBuilder pb = new ProcessBuilder(
                            javaPath,
                            "-Xms512m",
                            "-Xmx1g",
                            "-XX:+EnableDynamicAgentLoading",
                            "-Dstartup.class=" + name,
                            "-cp", System.getProperty("java.class.path"),
                            SimStarter.class.getName(),
                            String.valueOf(level.levelInt),
                            paramFilePath,
                            String.valueOf(simIndex)
                    );
                    pb.inheritIO();
                    Process process = pb.start();
                    if (process.waitFor() != 0) {
                        log.error(EXPERIMENT, "❌ Sim {} failed", simIndex);
                    }

                    synchronized (this) {
                        finishedSims.getAndIncrement();
                        if (finishedSims.get() % progressStep == 0) {
                            log.info(EXPERIMENT, "✅ Progress: {} / {}", finishedSims, totalSims);
                        }
                    }
                } catch (Exception e) {
                    log.error(EXPERIMENT, "Error executing sim {}: {}", simIndex, e.getMessage());
                }
            }));
        }

        // Wait for all processes to finish
        for (Future<?> future : futures) {
            future.get();  // Wait for the completion of each sim
        }

        // Final progress log after all tasks finish
        if (finishedSims.get() % progressStep != 0) {
            log.info(EXPERIMENT, "✅ Final Progress: {} / {}", finishedSims, totalSims);
        }

        executor.shutdown();  // Gracefully shutdown the executor
    }


    // Collect results and delete corresponding result files
    private List<Result> collectResults() {
        List<Result> resultList = new ArrayList<>();
        File[] resultFileList = new File(RESULT_DIR).listFiles(
                (dir, fileName) -> fileName.startsWith(name) && fileName.endsWith(".json")
        );

        if (resultFileList == null || resultFileList.length == 0) {
            log.error(EXPERIMENT, "❌ No result files found!");
            return resultList;
        }

        // Process each result file
        for (File file : resultFileList) {
            try {
                // Read and parse the result file
                String json = FileUtil.readUtf8String(file);
                Result result = JSONUtil.toBean(json, Result.class);
                resultList.add(result);

                // After reading the result, delete the corresponding result file
                if (file.exists() && !file.delete()) {
                    log.warn(EXPERIMENT, "⚠️ Failed to delete result file: {}", file.getName());
                }
            } catch (Exception e) {
                log.warn(EXPERIMENT, "⚠️ Failed to read result file: {}", file.getName(), e);
            }
        }
        return resultList;
    }
}
