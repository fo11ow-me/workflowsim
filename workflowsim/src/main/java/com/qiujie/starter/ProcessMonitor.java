package com.qiujie.starter;

import ch.qos.logback.classic.Level;
import org.slf4j.Logger;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;

import static com.qiujie.Constants.PROCESS_MONITOR_SLEEP_MS;

class ProcessMonitor implements Runnable {
    private final List<SimProcess> processList;
    private final BlockingQueue<SimProcess> processPool;
    private final int maxConcurrent;
    private final String javaPath;
    private final String classPath;
    private final String name;
    private final Level level;
    private final Logger log;

    public ProcessMonitor(List<SimProcess> processList, BlockingQueue<SimProcess> processPool, int maxConcurrent,
                          String javaPath, String classPath, String name, Level level, Logger log) {
        this.processList = processList;
        this.processPool = processPool;
        this.maxConcurrent = maxConcurrent;
        this.javaPath = javaPath;
        this.classPath = classPath;
        this.name = name;
        this.level = level;
        this.log = log;
    }

    @Override
    public void run() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                int aliveCount = 0;
                //  use a temporary collection to avoid concurrent modification exception
                List<SimProcess> toRemove = new ArrayList<>();

                for (SimProcess simProcess : processList) {
                    if (simProcess.isAlive()) {
                        aliveCount++;
                    } else {
                        try {
                            simProcess.close();
                        } catch (Exception ignored) {
                        }
                        toRemove.add(simProcess);
                        //  remove process from pool
                        processPool.remove(simProcess);
                    }
                }
                //  remove dead process from processList
                processList.removeAll(toRemove);

                int needCreate = maxConcurrent - aliveCount;
                for (int i = 0; i < needCreate; i++) {
                    try {
                        SimProcess simProcess = new SimProcess(javaPath, classPath, name, level);
                        processList.add(simProcess);
                        processPool.put(simProcess);
                    } catch (IOException e) {
                        log.error("Failed to create new process", e);
                    }
                }
                Thread.sleep(PROCESS_MONITOR_SLEEP_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }
}
