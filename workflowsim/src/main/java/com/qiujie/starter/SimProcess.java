package com.qiujie.starter;

import ch.qos.logback.classic.Level;

import com.esotericsoftware.kryo.kryo5.Kryo;
import com.esotericsoftware.kryo.kryo5.io.Input;
import com.esotericsoftware.kryo.kryo5.io.Output;
import com.qiujie.Constants;
import com.qiujie.entity.Result;
import com.qiujie.entity.SimParam;
import com.qiujie.util.KryoUtil;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

@Slf4j
class SimProcess {
    private final Process process;
    private final Output output;
    private final Input input;
    private final Kryo kryo;


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
        output = new Output(process.getOutputStream());
        input = new Input(process.getInputStream());
        kryo = KryoUtil.getInstance();
    }

    Result run(SimParam simParam) {
        kryo.writeObject(output, simParam);
        output.flush();
        return kryo.readObject(input, Result.class);
    }


    void sendPoisonPillAndClose() {
        try {
            kryo.writeObject(output, SimParam.POISON_PILL);
            output.flush();
            boolean exited = process.waitFor(Constants.PROC_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!exited) {
                process.destroyForcibly();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            close();
        }
    }

    void close() {
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

    boolean isAlive() {
        return process.isAlive();
    }

}
