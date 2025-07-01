package com.qiujie.starter;

import ch.qos.logback.classic.Level;
import cn.hutool.core.io.FileUtil;
import cn.hutool.json.JSONUtil;
import com.qiujie.entity.Result;
import com.qiujie.entity.SimParameter;
import com.qiujie.enums.LevelEnum;
import com.qiujie.util.ExperimentUtil;
import com.qiujie.util.Log;
import lombok.AccessLevel;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    public ExperimentStarter() {
        // Initialize the experiment name with the simple class name
        this.name = getClass().getSimpleName();
        System.setProperty("startup.class", name);
        this.log = LoggerFactory.getLogger(ExperimentStarter.class);
        this.level = LEVEL;
        this.paramList = new ArrayList<>();
        // Create a marker for experiment-specific logging
        SYSTEM = MarkerFactory.getMarker(LevelEnum.SYSTEM.name());
        Log.setLevel(level);
        start();
    }

    private void start() {
        log.info(SYSTEM, "{}: Starting...", name);
        this.seed = System.currentTimeMillis();
        run();
        long end = System.currentTimeMillis();
        double runtime = (end - seed) / 1000.0;
        log.info(SYSTEM, "{}: Finished in {}s", name, runtime);
    }

    private void run() {
        // Create necessary directories for saving parameters, results, simulation data, and experiment data
        createDirs();
        // Initialize experiment-specific parameters
        init();
        // Start subprocesses to run simulations concurrently
        startSimProcesses();
        // Collect results from simulation output files
        List<Result> results = collectResults();
        // Print the experiment results summary
        ExperimentUtil.printExperimentResult(results, name);
        // Generate experiment data files
        ExperimentUtil.generateExperimentData(results, name);
        // Delete the temporary parameter file used by simulations
        new File(PARAM_DIR + name + ".json").delete();
    }

    private void createDirs() {
        // Create directories if they don't exist
        FileUtil.mkdir(PARAM_DIR);
        FileUtil.mkdir(RESULT_DIR);
        FileUtil.mkdir(SIM_DATA_DIR);
        FileUtil.mkdir(EXPERIMENT_DATA_DIR);
    }

    protected abstract void init();

    protected void addParam(SimParameter simParameter) {
        // Assign a unique ID to the parameter based on the current parameter list size
        String name = String.format(this.name + "_%06d", paramList.size());
        simParameter.setName(name);
        paramList.add(simParameter);
    }

    private void startSimProcesses() {
        int reservedCores = 8;  // Number of CPU cores to reserve for system tasks
        int progressStep = 10;  // Log progress every 10 completed simulations
        String javaPath = System.getProperty("java.home") + "/bin/java";  // Path to Java executable
        int availableCores = Runtime.getRuntime().availableProcessors();
        int maxConcurrent = Math.max(1, availableCores - reservedCores);  // Maximum number of concurrent processes
        log.info(SYSTEM, "🖥️ Detected CPU cores: {}, setting max concurrent processes: {}", availableCores, maxConcurrent);

        // Write the simulation parameters to a JSON file for subprocess consumption
        String paramFilePath = PARAM_DIR + name + ".json";
        FileUtil.writeUtf8String(JSONUtil.toJsonPrettyStr(paramList), paramFilePath);

        int totalSims = paramList.size();
        AtomicInteger finishedSims = new AtomicInteger();  // Counter for finished simulations

        // Create a fixed thread pool to control the max number of concurrent tasks
        ExecutorService executor = Executors.newFixedThreadPool(maxConcurrent);
        List<Future<?>> futures = new ArrayList<>();
        for (int index = 0; index < totalSims; index++) {
            final int simIndex = index;
            futures.add(executor.submit(() -> {
                try {
                    // Build the process command to launch the simulation subprocess
                    ProcessBuilder pb = new ProcessBuilder(
                            javaPath,
                            "-Xms512m",  // Initial heap memory
                            "-Xmx1g",  // Maximum heap memory
                            "-XX:+EnableDynamicAgentLoading",
                            "-Dstartup.class=" + name,  // Pass the startup class name as a system property
                            "-cp", System.getProperty("java.class.path"),  // Set classpath
                            SimStarter.class.getName(),  // Main class to start
                            String.valueOf(level.levelInt),  // Logging level
                            paramFilePath,  // Path to parameter JSON file
                            String.valueOf(simIndex)  // Simulation index
                    );
                    pb.inheritIO();  // Inherit I/O so that subprocess logs print directly to the console
                    Process process = pb.start();
                    process.waitFor();  // Wait for the process to finish
                } catch (Exception e) {
                    log.error("Error executing sim {}", simIndex, e);
                } finally {
                    // Increment the finished simulations counter and log progress periodically
                    synchronized (this) {
                        int done = finishedSims.getAndIncrement();
                        if (done % progressStep == 0 || done == totalSims) {
                            log.info(SYSTEM, "✅  Progress: {} / {}", done, totalSims);
                        }
                    }
                }
            }));
        }

        // Wait for all submitted simulation tasks to finish
        for (Future<?> future : futures) {
            try {
                future.get();  // Wait for the completion of each sim
            } catch (Exception e) {
                log.error("Error waiting for sim task completion", e);
            }
        }
        executor.shutdown();  // Gracefully shut down the thread pool
    }


    /**
     * Collect simulation results and delete corresponding result files
     *
     * @return
     */
    private List<Result> collectResults() {
        List<Result> resultList = new ArrayList<>();
        for (SimParameter simParameter : paramList) {
            // Construct result file path based on simParameter's id
            File file = new File(RESULT_DIR + simParameter.getName() + ".json");

            // Check if the file exists
            if (!file.exists()) {
                log.warn("❌  Result file {} does not exist!", file.getName());
                continue;
            }

            try {
                // Read and parse the JSON result file into a Result object
                String json = FileUtil.readUtf8String(file);
                Result result = JSONUtil.toBean(json, Result.class);
                resultList.add(result);
                log.info("Successfully collected result file: {}", file.getName()); // Success log
            } catch (Exception e) {
                // Log error and skip this file, do not throw to avoid breaking overall result collection
                log.error("❌  Failed to read result file {}", file.getName(), e);
                continue;
            }

            try {
                // Try to delete the result file after processing, log warning if fails
                if (file.exists() && !file.delete()) {
                    log.warn("⚠️  Failed to delete result file: {}", file.getName());
                } else {
                    log.info("✅  Successfully deleted result file: {}", file.getName()); // Success log for deletion
                }
            } catch (Exception e) {
                log.warn("⚠️  Exception when deleting result file {}", file.getName(), e);
            }
        }
        return resultList;
    }

}
