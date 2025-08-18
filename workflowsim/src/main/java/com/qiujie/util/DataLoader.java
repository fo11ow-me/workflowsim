package com.qiujie.util;

import cn.hutool.core.io.IORuntimeException;
import cn.hutool.json.JSONUtil;
import com.qiujie.entity.Cpu;
import com.qiujie.entity.Freq2Power;
import com.qiujie.entity.Dax;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import static com.qiujie.Constants.DAX_DIR;

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
        RedisUtil.setObject("cpu:list", getCpus());
    }

    private static void uploadDaxs() {
        File dir = new File(DAX_DIR);
        for (File file : Objects.requireNonNull(dir.listFiles())) {
            if (file.getName().endsWith(".xml")) {
                Dax dax = DaxParser.parse(file);
                RedisUtil.setObject(dax.getName(), dax);
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
            list.forEach(cpu ->
                    cpu.getFreq2PowerList().sort(Comparator.comparingDouble(Freq2Power::getFrequency).reversed())
            );
            return list;
        } catch (IOException e) {
            throw new IORuntimeException("Failed to read cpu.json", e);
        }
    }


}
