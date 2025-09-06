package com.qiujie.util;

import cn.hutool.core.io.IORuntimeException;
import cn.hutool.json.JSONUtil;
import com.qiujie.entity.Cpu;
import com.qiujie.entity.Dax;
import org.cloudbus.cloudsim.distributions.UniformDistr;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import static com.qiujie.Constants.*;

public class DataLoader {
    public static void main(String[] args) {
        upload();
    }


    public static void upload() {
        RedisUtil.flushDB();
        uploadCpus();
        uploadDaxs();
    }

    private static void uploadCpus() {
        RedisUtil.set("cpu:list", getCpus());
    }

    private static void uploadDaxs() {
        File dir = new File(DAX_DIR);
        for (File file : Objects.requireNonNull(dir.listFiles())) {
            if (file.getName().endsWith(".xml")) {
                Dax dax = DaxParser.parse(file);
                RedisUtil.set(dax.getName(), dax);
            }
        }
    }


    public static List<Cpu> getCpus() {
        try (InputStream inputStream = ClassLoader.getSystemClassLoader().getResourceAsStream("cpu.json")) {
            if (inputStream == null) {
                throw new IORuntimeException("Unable to find cpu.json file");
            }
            String jsonStr = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            List<Cpu> list = JSONUtil.toList(jsonStr, Cpu.class);
            UniformDistr random = new UniformDistr(1, 10, 100);
            list.forEach(cpu -> {
                        cpu.getFvList().sort(Comparator.comparingDouble(Cpu.Fv::getFrequency).reversed());
                        double lambda = λ * random.sample();
                        List<Cpu.Fv> fvList = cpu.getFvList();
                        for (int i = 0; i < fvList.size(); i++) {
                            Cpu.Fv fv = fvList.get(i);
                            // smaller frequency, bigger lambda (transient fault rate)
                            fv.setLambda(lambda * Math.pow(10, (SR * (cpu.getFrequency() - fv.getFrequency()) / (cpu.getFrequency() - fvList.getLast().getFrequency()))));
                            fv.setMips(cpu.getMips() * fv.getFrequency() / cpu.getFrequency());
                            fv.setLevel(fvList.size() - 1 - i);
                            fv.setType(cpu.getName() + " (L" + fv.getLevel() + ")");
                        }
                    }
            );
            return list;
        } catch (IOException e) {
            throw new IORuntimeException("Failed to read cpu.json", e);
        }
    }


}
