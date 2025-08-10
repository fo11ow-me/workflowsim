package com.qiujie.util;

import com.esotericsoftware.kryo.Kryo;
import com.qiujie.entity.*;
import com.qiujie.enums.JobSequenceStrategyEnum;
import com.qiujie.entity.Dax;

import java.util.ArrayList;
import java.util.HashMap;

public class KryoUtil {

    private static final ThreadLocal<Kryo> kryoThreadLocal = ThreadLocal.withInitial(() -> {
        Kryo kryo = new Kryo();
        kryo.register(Result.class);
        kryo.register(ArrayList.class);
        kryo.register(SimParam.class);
        kryo.register(Param.class);
        kryo.register(JobSequenceStrategyEnum.class);
        kryo.register(String.class);
        kryo.register(Dax.class);
        kryo.register(Dax.Job.class);
        kryo.register(Dax.File.class);
        kryo.register(Cpu.class);
        kryo.register(Freq2Power.class);
        kryo.register(HashMap.class);
        try {
            kryo.register(Class.forName("java.util.ImmutableCollections$ListN"));
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
        return kryo;
    });

    public static Kryo getInstance() {
        return kryoThreadLocal.get();
    }
}
