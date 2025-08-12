package com.qiujie.util;

import com.esotericsoftware.kryo.kryo5.Kryo;
import com.qiujie.entity.*;
import java.util.*;

public class KryoUtil {

    private static final ThreadLocal<Kryo> kryoThreadLocal = ThreadLocal.withInitial(() -> {
        Kryo kryo = new Kryo();
        kryo.register(String.class);
        kryo.register(Dax.class);
        kryo.register(Dax.Job.class);
        kryo.register(Dax.File.class);
        kryo.register(Cpu.class);
        kryo.register(Freq2Power.class);
        kryo.register(ArrayList.class);
        kryo.register(HashMap.class);
        kryo.register(HashSet.class);
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
