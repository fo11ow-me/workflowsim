package com.qiujie.util;

import cn.hutool.core.io.IORuntimeException;
import cn.hutool.core.io.IoUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONUtil;
import com.qiujie.entity.Cpu;
import com.qiujie.entity.Dax;
import com.qiujie.entity.Freq2Power;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import static com.qiujie.Constants.DAX_DIR;

public class DataLoader {
    public static void main(String[] args) {
        loadCpus();
        loadDaxs();
    }


    private static void loadCpus() {
        try (InputStream inputStream = ClassLoader.getSystemClassLoader().getResourceAsStream("cpu.json")) {
            if (inputStream == null) {
                throw new IORuntimeException("Unable to find cpu.json file");
            }
            String jsonStr = IoUtil.readUtf8(inputStream);
            JSONArray array = JSONUtil.parseArray(jsonStr);
            List<Cpu> list = JSONUtil.toList(array, Cpu.class);
            list.forEach(cpu ->
                    cpu.getFreq2PowerList().sort(Comparator.comparingDouble(Freq2Power::getFrequency).reversed())
            );
            RedisUtil.setObject("cpu:list", list);
        } catch (IOException e) {
            throw new IORuntimeException("Failed to read cpu.json", e);
        }
    }

    private static void loadDaxs() {
        File dir = new File(DAX_DIR);
        for (File file : Objects.requireNonNull(dir.listFiles())) {
            if (file.getName().endsWith(".xml")) {
                Dax dax = DaxParser.parse(file);
                RedisUtil.setObject(dax.getName(), dax);
            }
        }
    }
}
