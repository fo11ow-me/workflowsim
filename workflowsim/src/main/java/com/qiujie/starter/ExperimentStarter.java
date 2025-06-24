package com.qiujie.starter;

import ch.qos.logback.classic.Level;
import cn.hutool.core.io.FileUtil;
import cn.hutool.json.JSONUtil;
import com.qiujie.entity.Result;
import com.qiujie.entity.SimParameter;
import com.qiujie.enums.LevelEnum;
import com.qiujie.util.ExperimentUtil;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static com.qiujie.Constants.*;

public abstract class ExperimentStarter {
    public final String name;
    public long seed;
    private final Logger log;
    @Setter
    private Level level;
    private final List<File> paramFileList;

    private final Marker STARTUP_EXPERIMENT;

    public ExperimentStarter() {
        this.name = getClass().getSimpleName();
        System.setProperty("startup.class", name);
        this.log = LoggerFactory.getLogger(ExperimentStarter.class);
        this.level = LEVEL;
        this.paramFileList = new ArrayList<>();
        this.STARTUP_EXPERIMENT = MarkerFactory.getMarker(LevelEnum.STARTUP_EXPERIMENT.name());
        start();
    }

    private void start() {
        log.info(STARTUP_EXPERIMENT, "{}: Starting...", name);
        this.seed = System.currentTimeMillis();
        try {
            run();
            log.info(STARTUP_EXPERIMENT, String.format("%s: Finished in %.2fs", name, (System.currentTimeMillis() - seed) / 1000.0));
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
        // Delete temporary parameter and result files
        deleteFiles(paramFileList, "parameter");
        deleteFiles(List.of(Objects.requireNonNull(new File(RESULT_DIR).listFiles(
                (dir, fileName) -> fileName.startsWith(name) && fileName.endsWith(".json")
        ))), "result");
    }

    private void createDirs() {
        FileUtil.mkdir(PARAM_DIR);
        FileUtil.mkdir(RESULT_DIR);
        FileUtil.mkdir(SIM_DATA_DIR);
        FileUtil.mkdir(EXPERIMENT_DATA_DIR);
    }

    protected abstract void init();

    protected void addParam(SimParameter simParameter) {
        String id = String.format(name + "_%06d", paramFileList.size());
        simParameter.setId(id);
        String json = JSONUtil.toJsonPrettyStr(simParameter);
        File file = new File(PARAM_DIR + id + ".json");
        FileUtil.writeUtf8String(json, file);
        if (!file.exists() || file.length() == 0) {
            throw new RuntimeException("❌ Parameter file creation failed or is empty: " + file.getAbsolutePath());
        }
        paramFileList.add(file);
    }

    private void startSimProcesses() throws Exception {
        // Automatically get the current JDK installation path
        String javaPath = System.getProperty("java.home") + "/bin/java";
        List<Process> runningProcesses = new ArrayList<>();
        // Dynamically calculate the maximum number of concurrent processes
        int availableCores = Runtime.getRuntime().availableProcessors();
        int maxConcurrent = Math.max(1, availableCores - 4); // Keep 4 cores for system

        log.info("🖥️  Detected CPU cores: {}, setting max concurrent processes: {}", availableCores, maxConcurrent);

        for (File paramFile : paramFileList) {
            // Wait if current running processes reach the limit
            while (runningProcesses.size() >= maxConcurrent) {
                for (int i = 0; i < runningProcesses.size(); i++) {
                    Process p = runningProcesses.get(i);
                    if (!p.isAlive()) {
                        runningProcesses.remove(i);
                        i--;
                    }
                }
                Thread.sleep(500); // Sleep briefly to avoid busy waiting
            }

            // Start a new simulation process
            ProcessBuilder pb = new ProcessBuilder(
                    javaPath,
                    "-Xms256m", // Set minimum heap size
                    "-Xmx512m", // Set maximum heap size
                    "-Dstartup.class=" + name,
                    "-cp", System.getProperty("java.class.path"),
                    SimStarter.class.getName(),
                    String.valueOf(level.levelInt),
                    paramFile.getAbsolutePath()
            );
            pb.inheritIO(); // Inherit IO to show real-time logs
            Process process = pb.start();
            runningProcesses.add(process);
        }
        // Wait for all remaining processes to complete
        for (Process process : runningProcesses) {
            process.waitFor();
        }

    }


    private List<Result> collectResults() {
        List<Result> resultList = new ArrayList<>();
        File[] resultFileList = new File(RESULT_DIR).listFiles(
                (dir, fileName) -> fileName.startsWith(name) && fileName.endsWith(".json")
        );

        if (resultFileList == null || resultFileList.length == 0) {
            log.error("❌ No result files found!");
            return resultList;
        }

        for (File file : resultFileList) {
            try {
                String json = FileUtil.readUtf8String(file);
                Result result = JSONUtil.toBean(json, Result.class);
                resultList.add(result);
            } catch (Exception e) {
                log.warn("⚠️ Failed to read result file: {}", file.getName(), e);
            }
        }

        return resultList;
    }

    private void deleteFiles(List<File> list, String type) {
        if (list == null) return;
        for (File file : list) {
            if (!file.delete()) {
                log.warn("⚠️ Failed to delete {} file: {}", type, file.getName());
            }
        }
    }
}
