package com.qiujie.starter;

import ch.qos.logback.classic.Level;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.text.csv.CsvUtil;
import cn.hutool.core.text.csv.CsvWriter;
import com.qiujie.entity.Result;
import com.qiujie.entity.SimParam;
import com.qiujie.util.ExperimentUtil;
import com.qiujie.util.KryoUtil;
import lombok.AccessLevel;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Field;
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
    private final BlockingQueue<SimParam> taskQueue;
    private int total;

    public ExperimentStarter() {
        this.name = getClass().getSimpleName();
        System.setProperty("startup.class", name);
        this.log = LoggerFactory.getLogger(ExperimentStarter.class);
        this.level = LEVEL;
        this.taskQueue = new LinkedBlockingQueue<>(QUEUE_CAPACITY);
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
        startSimProcesses();
    }

    private void createDirs() {
        FileUtil.mkdir(RESULT_DIR);
        FileUtil.mkdir(SIM_DIR);
    }

    protected abstract void addParams() throws InterruptedException;

    protected void addParam(SimParam simParam) throws InterruptedException {
        taskQueue.put(simParam);
        total++;
    }


    private void startSimProcesses() {
        int availableCores = Runtime.getRuntime().availableProcessors();
        int maxConcurrent = Math.max(1, availableCores - RESERVED_CORES);
        String javaPath = System.getProperty("java.home") + "/bin/java";
        String classPath = System.getProperty("java.class.path");

        log.info("🖥️  Detected CPU cores: {}, setting max concurrent processes: {}", availableCores, maxConcurrent);

        BlockingQueue<Result> resultQueue = new LinkedBlockingQueue<>();
        Thread writer = getWriter(resultQueue);
        writer.start();

        total = 0;
        Thread producer = new Thread(() -> {
            try {
                addParams();
                for (int i = 0; i < maxConcurrent; i++) {
                    taskQueue.put(SimParam.POISON_PILL);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        producer.start();

        List<SimProcess> simProcesses = new ArrayList<>();
        for (int i = 0; i < maxConcurrent; i++) {
            simProcesses.add(new SimProcess(javaPath, classPath, name, level));
        }

        ExecutorService executor = Executors.newFixedThreadPool(maxConcurrent);
        AtomicInteger counter = new AtomicInteger(0);
        for (int i = 0; i < maxConcurrent; i++) {
            SimProcess simProcess = simProcesses.get(i);
            executor.submit(() -> {
                try {
                    simProcess.setKryo(KryoUtil.getInstance());
                    while (true) {
                        SimParam simParam = taskQueue.take();
                        if (simParam.equals(SimParam.POISON_PILL)) break;
                        Result result = simProcess.run(simParam);
                        int count = counter.incrementAndGet();
                        if (count == 1 || count == total || count % 500 == 0) {
                            log.info("✅  Progress: {} / {}", count, total);
                        }
                        if (result.equals(Result.POISON_PILL)) {
                            log.error("❌ Sim {} failed", simParam);
                        } else {
                            resultQueue.put(result);
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }

        executor.shutdown();
        try {
            if (!executor.awaitTermination(12, TimeUnit.HOURS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }

        try {
            resultQueue.put(Result.POISON_PILL);
            writer.join();
        } catch (InterruptedException e) {
            writer.interrupt();
        }

        for (SimProcess sp : simProcesses) {
            sp.shutdown();
        }

    }


    private Thread getWriter(BlockingQueue<Result> resultQueue) {
        return new Thread(() -> {
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(RESULT_DIR + name + ".csv", false))) {
                CsvWriter csvWriter = CsvUtil.getWriter(writer);
                List<Field> fields = ExperimentUtil.getNonStaticFields(Result.class);
                csvWriter.writeHeaderLine(fields.stream().map(Field::getName).toArray(String[]::new));
                List<Result> batch = new ArrayList<>();
                while (true) {
                    Result result = resultQueue.take();
                    if (result.equals(Result.POISON_PILL)) break;
                    batch.add(result);
                    if (batch.size() >= BATCH_SIZE) {
                        csvWriter.writeBeans(batch, false);
                        batch.clear();
                    }
                }
                if (!batch.isEmpty()) {
                    csvWriter.writeBeans(batch, false);
                    batch.clear();
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
    }
}
