package com.qiujie.starter;

import ch.qos.logback.classic.Level;
import cn.hutool.core.io.FileUtil;
import cn.hutool.json.JSONUtil;
import com.qiujie.entity.Result;
import com.qiujie.entity.SimParameter;
import lombok.AccessLevel;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static com.qiujie.Constants.*;

public abstract class ExperimentStarter {
    public final String name;
    public long seed;
    private final Logger log;
    @Setter(AccessLevel.PROTECTED)
    private Level level;
    private final List<SimParameter> paramList;
    private static final Result POISON_PILL = new Result(); // End marker for queue

    public ExperimentStarter() {
        this.name = getClass().getSimpleName();
        System.setProperty("startup.class", name);
        this.log = LoggerFactory.getLogger(ExperimentStarter.class);
        this.level = LEVEL;
        this.paramList = new ArrayList<>();
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
        createDirs();
        addParams();
        startSimProcesses();
    }

    private void createDirs() {
        FileUtil.mkdir(EXPERIMENT_DIR);
        FileUtil.mkdir(SIM_DIR);
        FileUtil.mkdir(PARAM_DIR);
        FileUtil.mkdir(RESULT_DIR);
    }

    protected abstract void addParams();

    protected void addParam(SimParameter simParameter) {
        paramList.add(simParameter);
    }


    private void startSimProcesses() {
        int totalSims = paramList.size();
        int progressStep = (int) Math.max(1, Math.sqrt(totalSims));
        int availableCores = Runtime.getRuntime().availableProcessors();
        int maxConcurrent = Math.max(1, availableCores - RESERVED_CORES);
        log.info("🖥️  Detected CPU cores: {}, setting max concurrent processes: {}", availableCores, maxConcurrent);

        String javaPath = System.getProperty("java.home") + "/bin/java";
        String classPath = System.getProperty("java.class.path");
        String paramPath = PARAM_DIR + name + ".json";
        FileUtil.writeUtf8String(JSONUtil.toJsonPrettyStr(paramList), paramPath);

        AtomicInteger counter = new AtomicInteger();
        ExecutorService executor = Executors.newFixedThreadPool(maxConcurrent);

        // Writer thread (consumer) writes results to file from the queue
        BlockingQueue<Result> resultQueue = new LinkedBlockingQueue<>();
        Thread writerThread = createWriterThread(resultQueue, executor);

        // Submit simulation tasks (producers)
        for (int i = 0; i < totalSims; i++) {
            int simIdx = i;
            executor.submit(() -> {
                ProcessBuilder pb = new ProcessBuilder(
                        javaPath,
                        "-Xms512m",
                        "-Xmx1g",
                        "-XX:+EnableDynamicAgentLoading",
                        "-Dstartup.class=" + name,
                        "-Dsim.idx=" + simIdx,
                        "-cp", classPath,
                        SimStarter.class.getName(),
                        String.valueOf(level.levelInt),
                        paramPath
                );
                try {
                    Process process = pb.start();
                    boolean finished = process.waitFor(SIM_TIMEOUT_MINUTES, TimeUnit.MINUTES);
                    if (!finished) {
                        process.destroyForcibly();
                        log.error("❌  Sim {} timed out and was forcibly terminated", simIdx);
                    } else if (process.exitValue() != 0) {
                        log.error("❌  Sim {} failed", simIdx);
                    } else {
                        Result result = getResult(simIdx);
                        resultQueue.put(result); // Put result in queue (thread-safe blocking)
                    }
                } catch (Exception e) {
                    log.error("❌  Failed to start sim {}", simIdx, e);
                } finally {
                    int count = counter.incrementAndGet();
                    if (count == 1 || count == totalSims || count % progressStep == 0) {
                        log.info("✅  Progress: {} / {}", count, totalSims);
                    }
                    // After all tasks finish, put poison pill to signal writer thread to stop
                    if (count == totalSims) {
                        try {
                            resultQueue.put(POISON_PILL);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    }
                }
            });
        }

        executor.shutdown();
        try {
            executor.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
            writerThread.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("❌  Interrupted while waiting for completion", e);
        }
    }


    /**
     * Create a thread to write results to file from the queue
     *
     * @param resultQueue
     * @param executor
     * @return
     */
    private Thread createWriterThread(BlockingQueue<Result> resultQueue, ExecutorService executor) {
        Thread writerThread = new Thread(() -> {
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(EXPERIMENT_DIR + name + ".jsonl", false))) {
                List<Result> batch = new ArrayList<>();
                while (true) {
                    Result result = resultQueue.poll(1, TimeUnit.SECONDS);
                    if (result == null) {
                        // Timeout: check if all tasks finished and queue is empty
                        if (executor.isTerminated() && resultQueue.isEmpty()) break;
                        continue;
                    }
                    if (result == POISON_PILL) break;

                    batch.add(result);
                    if (batch.size() >= BATCH_SIZE) {
                        writeBatch(writer, batch);
                        batch.clear();
                    }
                }
                // Write remaining results
                if (!batch.isEmpty()) {
                    writeBatch(writer, batch);
                    batch.clear();
                }
            } catch (IOException | InterruptedException e) {
                log.error("❌  Failed to write results", e);
            }
        });
        writerThread.start();
        return writerThread;
    }

    private Result getResult(int simIdx) {
        File file = new File(RESULT_DIR + name + "_" + simIdx + ".json");
        Result result = null;
        try {
            String json = FileUtil.readUtf8String(file);
            result = JSONUtil.toBean(json, Result.class);
        } catch (Exception e) {
            log.error("❌  Failed to read result file {}", file.getName(), e);
        }
        file.delete();
        return result;
    }

    private void writeBatch(BufferedWriter writer, List<Result> batch) throws IOException {
        for (Result r : batch) {
            String json = JSONUtil.toJsonStr(r);
            writer.write(json);
            writer.newLine();
        }
        writer.flush();
    }
}
