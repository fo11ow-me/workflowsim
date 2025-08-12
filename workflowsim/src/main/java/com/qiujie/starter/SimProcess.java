package com.qiujie.starter;

import ch.qos.logback.classic.Level;

import com.qiujie.Constants;
import com.qiujie.entity.Result;
import com.qiujie.entity.SimParam;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.concurrent.TimeUnit;

@Slf4j
class SimProcess {
    private final Process process;
    private final ObjectOutputStream output;
    private final ObjectInputStream input;


    SimProcess(String javaPath, String classPath, String name, Level level) throws IOException {
        ProcessBuilder pb = new ProcessBuilder(
                javaPath,
                "-Xms512m",
                "-Xmx1g",
                "-Dstartup.class=" + name,
                "-cp", classPath,
                SimStarter.class.getName(),
                String.valueOf(level.levelInt)
        );
        process = pb.start();
        output = new ObjectOutputStream(process.getOutputStream());
        input = new ObjectInputStream(process.getInputStream());
    }

    synchronized Result run(SimParam simParam) throws IOException, ClassNotFoundException {
        output.writeObject(simParam);
        output.flush();
        return (Result) input.readObject();
    }


    synchronized void sendPoisonPillAndClose() {
        try {
            output.writeObject(SimParam.POISON_PILL);
            output.flush();
            boolean exited = process.waitFor(Constants.PROC_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!exited) {
                process.destroyForcibly();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            close();
        }
    }

    synchronized void close() {
        try {
            output.close();
        } catch (Exception ignored) {
        }

        try {
            input.close();
        } catch (Exception ignored) {
        }
        try {
            if (process.isAlive()) {
                process.destroy();
                process.waitFor(Constants.PROC_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public boolean isAlive() {
        return process.isAlive();
    }

}
