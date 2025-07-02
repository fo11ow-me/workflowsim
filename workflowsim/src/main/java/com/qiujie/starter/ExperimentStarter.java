package com.qiujie.starter;

import ch.qos.logback.classic.Level;
import cn.hutool.core.io.FileUtil;
import cn.hutool.json.JSONUtil;
import com.qiujie.entity.Result;
import com.qiujie.entity.SimParameter;
import com.qiujie.util.ExperimentUtil;
import lombok.AccessLevel;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static com.qiujie.Constants.*;

public abstract class ExperimentStarter {
    public final String name;
    public long seed;
    private final Logger log;
    @Setter(AccessLevel.PROTECTED)
    private Level level;
    private final List<SimParameter> paramList;
    private final List<String> finishedSimList;

    public ExperimentStarter() {
        // Initialize the experiment name with the simple class name
        this.name = getClass().getSimpleName();
        System.setProperty("startup.class", name);
        this.log = LoggerFactory.getLogger(ExperimentStarter.class);
        this.level = LEVEL;
        this.paramList = new ArrayList<>();
        this.finishedSimList = new ArrayList<>();
        start();
    }

    private void start() {
        log.info("{}: Starting...", name);
        this.seed = System.currentTimeMillis();
        run();
        long end = System.currentTimeMillis();
        double runtime = (end - seed) / 1000.0;
        log.info("{}: Finished in {}s", name, runtime);
    }

    private void run() {
        // Create necessary directories for saving parameters, results, simulation data, and experiment data
        createDirs();
        // Add sim-specific parameters
        addParams();
        // Start subprocesses to run simulations concurrently
        startSimProcesses();
        // Collect results from simulation output files
        List<Result> resultList = collectResults();
        if (resultList.isEmpty()) {
            log.error("❌  No sim results");
            return;
        }
        // Print the experiment results summary
        ExperimentUtil.printExperimentResult(resultList, name);
        // Generate experiment data files
        ExperimentUtil.generateExperimentData(resultList, name);

    }

    private void createDirs() {
        // Create directories if they don't exist
        FileUtil.mkdir(EXPERIMENT_DATA_DIR);
        FileUtil.mkdir(SIM_DATA_DIR);
        FileUtil.mkdir(PARAM_DIR);
        FileUtil.mkdir(RESULT_DIR);
    }

    protected abstract void addParams();

    protected void addParam(SimParameter simParameter) {
        // Assign a unique ID to the parameter based on the current parameter list size
        String name = String.format(this.name + "_%08d", paramList.size());
        simParameter.setName(name);
        paramList.add(simParameter);
    }

    private void startSimProcesses() {
        int totalSims = paramList.size();
        int progressStep = (int) Math.max(1, Math.sqrt(totalSims));
        String javaPath = System.getProperty("java.home") + "/bin/java";  // Path to Java executable
        int availableCores = Runtime.getRuntime().availableProcessors();
        int maxConcurrent = Math.max(1, availableCores - RESERVED_CORES);  // Maximum number of concurrent processes
        log.info("🖥️  Detected CPU cores: {}, setting max concurrent processes: {}", availableCores, maxConcurrent);
        // Write the simulation parameters to a JSON file for subprocess consumption
        String paramFilePath = PARAM_DIR + name + ".json";
        FileUtil.writeUtf8String(JSONUtil.toJsonPrettyStr(paramList), paramFilePath);
        AtomicInteger completedSims = new AtomicInteger();  // Counter for finished simulations
        // Create a fixed thread pool to control the max number of concurrent tasks
        ExecutorService executor = Executors.newFixedThreadPool(maxConcurrent);
        List<Future<?>> futures = new ArrayList<>();
        for (int index = 0; index < totalSims; index++) {
            final int simIndex = index;
            futures.add(executor.submit(() -> {

                // Build the process command to launch the simulation subprocess
                ProcessBuilder pb = new ProcessBuilder(
                        javaPath,
                        "-Xms512m",  // Initial heap memory
                        "-Xmx1g",  // Maximum heap memory
                        "-XX:+EnableDynamicAgentLoading",
                        "-Dstartup.class=" + name,  // Pass the startup class name as a system property
                        "-Dlog.suffix=" + String.format("_%08d", simIndex),
                        "-cp", System.getProperty("java.class.path"),  // Set classpath
                        SimStarter.class.getName(),  // Main class to start
                        String.valueOf(level.levelInt),  // Logging level
                        paramFilePath,  // Path to parameter JSON file
                        String.valueOf(simIndex)  // Simulation index
                );
                try {
                    Process process = pb.start();
                    // Wait for the subprocess to complete, with a timeout
                    boolean finished = process.waitFor(TIMEOUT_MINUTES, TimeUnit.MINUTES);
                    if (!finished) {
                        // If the subprocess does not finish within the timeout, forcefully kill it
                        process.destroyForcibly();
                        log.error("❌  Sim {} timed out and was forcibly terminated", simIndex);
                    } else {
                        if (process.exitValue() != 0) {
                            log.error("❌  Sim {} failed", simIndex);
                        } else {
                            finishedSimList.add(paramList.get(simIndex).getName());
                        }
                    }
                } catch (Exception e) {
                    log.error("❌  Failed to start sim {}", simIndex, e);
                } finally {
                    // Increment the completed simulations counter and log progress periodically
                    int done = completedSims.incrementAndGet();
                    if (done == 1 || done == totalSims || done % progressStep == 0) {
                        log.info("✅  Progress: {} / {}", done, totalSims);
                    }
                }
            }));
        }

        // Wait for all submitted simulation tasks to finish
        for (Future<?> future : futures) {
            try {
                future.get();  // Wait for the completion of each sim
            } catch (Exception e) {
                log.error("❌  Failed to wait for sim completion", e);
            }
        }
        executor.shutdown();  // Gracefully shut down the thread pool
        // Delete the temporary parameter file used by simulations
        new File(PARAM_DIR + name + ".json").delete();
    }


    /**
     * Collect simulation results and delete corresponding result files
     */
    private List<Result> collectResults() {
        List<Result> resultList = new ArrayList<>();
        for (String name : finishedSimList) {
            // Construct result file path based on simParameter's id
            File file = new File(RESULT_DIR + name + ".json");
            try {
                // Read and parse the JSON result file into a Result object
                String json = FileUtil.readUtf8String(file);
                Result result = JSONUtil.toBean(json, Result.class);
                resultList.add(result);
            } catch (Exception e) {
                log.error("❌  Failed to read result file {}", file.getName(), e);
                continue;
            }
            file.delete();
        }
        return resultList;
    }
}
