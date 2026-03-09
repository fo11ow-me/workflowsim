package com.qiujie.starter;

import ch.qos.logback.classic.Level;
import com.esotericsoftware.kryo.kryo5.Kryo;
import com.esotericsoftware.kryo.kryo5.io.Input;
import com.esotericsoftware.kryo.kryo5.io.Output;
import com.qiujie.entity.Result;
import com.qiujie.entity.SimParam;
import com.qiujie.util.KryoUtil;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;

@Slf4j
class SimProcess {
    private final Process process;
    private final Output output;
    private final Input input;
    @Setter
    private Kryo kryo;


    SimProcess(String javaPath, String classPath, String name, Level level) {
        ProcessBuilder pb = new ProcessBuilder(
                javaPath,
                "-Xms768m",
                "-Xmx768m",
                "-Dstartup.class=" + name,
                "-cp", classPath,
                SimStarter.class.getName(),
                String.valueOf(level.levelInt)
        );
        try {
            process = pb.start();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        output = new Output(process.getOutputStream());
        input = new Input(process.getInputStream());
    }

    Result run(SimParam simParam) {
        kryo.writeObject(output, simParam);
        output.flush();
        return kryo.readObject(input, Result.class);
    }

    public void shutdown() {
        try {
            kryo.writeObject(output, SimParam.POISON_PILL);
            output.flush();
            process.waitFor();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            try {
                input.close();
            } catch (Exception ignored) {
            }
            try {
                output.close();
            } catch (Exception ignored) {
            }
        }
    }
}
