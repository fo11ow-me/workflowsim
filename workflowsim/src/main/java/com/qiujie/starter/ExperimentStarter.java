package com.qiujie.starter;

import ch.qos.logback.classic.Level;
import cn.hutool.core.io.FileUtil;
import cn.hutool.json.JSONUtil;
import com.qiujie.entity.Result;
import com.qiujie.entity.SimParam;
import lombok.AccessLevel;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
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
    private final List<SimParam> paramList;

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
        log.info("{}: Finished in {}s\n", name, runtime);
    }

    private void run() {
        createDirs();
        addParams();
        startSimProcesses();
    }

    private void createDirs() {
        FileUtil.mkdir(RESULT_DIR);
        FileUtil.mkdir(SIM_DIR);
    }

    protected abstract void addParams();

    protected void addParam(SimParam simParam) {
        paramList.add(simParam);
    }


    private void startSimProcesses() {
        int size = paramList.size();
        int progressStep = (int) Math.max(1, Math.sqrt(size));
        int availableCores = Runtime.getRuntime().availableProcessors();
        int maxConcurrent = Math.max(1, availableCores - RESERVED_CORES);
        log.info("🖥️  Detected CPU cores: {}, setting max concurrent processes: {}", availableCores, maxConcurrent);

        String javaPath = System.getProperty("java.home") + "/bin/java";
        String classPath = System.getProperty("java.class.path");

        // Manage the idle process processes with a blocking queue
        List<SimProcess> processList = new CopyOnWriteArrayList<>();
        BlockingQueue<SimProcess> processPool = new ArrayBlockingQueue<>(maxConcurrent);

        // Initialize the process pool
        for (int i = 0; i < maxConcurrent; i++) {
            try {
                SimProcess simProcess = new SimProcess(javaPath, classPath, name, level);
                processList.add(simProcess);
                processPool.put(simProcess);
            } catch (IOException | InterruptedException e) {
                log.error("❌  Failed to start SimProcess", e);
                Thread.currentThread().interrupt();
            }
        }

        Thread monitorThread = new Thread(new ProcessMonitor(processList, processPool, maxConcurrent, javaPath, classPath, name, level, log));
        monitorThread.setDaemon(true);
        monitorThread.start();

        ExecutorService executor = Executors.newFixedThreadPool(maxConcurrent);
        AtomicInteger counter = new AtomicInteger();

        BlockingQueue<Result> resultQueue = new LinkedBlockingQueue<>();
        Thread writerThread = createWriterThread(resultQueue);
        writerThread.start();


        for (SimParam simParam : paramList) {
            executor.submit(() -> {
                SimProcess simProcess = null;
                try {
                    // Take an available process from the pool, blocking if none is free
                    simProcess = processPool.take();
                    Result result = simProcess.run(simParam);
                    if (result == null) {
                        throw new IllegalStateException("Receiving null result");
                    }
                    if (result.equals(Result.POISON_PILL)) {
                        throw new IllegalStateException("Receiving Poison pill");
                    }
                    resultQueue.put(result);
                } catch (Exception e) {
                    log.error("❌ Sim {} failed", simParam, e);
                } finally {
                    // log progress
                    int count = counter.incrementAndGet();
                    if (count == 1 || count == size || count % progressStep == 0) {
                        log.info("✅  Progress: {}% ({} / {})", count * 100.0 / size, count, size);
                    }
                    // Return the process to the pool after the simulation is done
                    if (simProcess != null && simProcess.isAlive()) {
                        try {
                            processPool.put(simProcess);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            log.error("❌ Interrupted returning worker to pool", e);
                        }
                    }
                }
            });
        }

        executor.shutdown();
        try {

            executor.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);

            monitorThread.interrupt();
            monitorThread.join();

            // After all simulations are completed, close all processes
            for (SimProcess simProcess : processList) {
                simProcess.sendPoisonPillAndClose();
            }

            // close writer thread
            resultQueue.put(Result.POISON_PILL);
            writerThread.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("❌ Interrupted while waiting for executor or writer thread", e);
        }

    }


    /**
     * Create a thread to write results to file from the queue
     */
    private Thread createWriterThread(BlockingQueue<Result> resultQueue) {
        return new Thread(() -> {
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(RESULT_DIR + name + ".jsonl", false))) {
                List<Result> batch = new ArrayList<>();
                while (true) {
                    Result result = resultQueue.take();
                    if (result.equals(Result.POISON_PILL)) {
                        break;
                    }
                    batch.add(result);
                    if (batch.size() >= BATCH_SIZE) {
                        writeBatch(writer, batch);
                        batch.clear();
                    }
                }
                if (!batch.isEmpty()) {
                    writeBatch(writer, batch);
                }
            } catch (IOException | InterruptedException e) {
                log.error("❌  Failed to write results", e);
                Thread.currentThread().interrupt();
            }
        });
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
