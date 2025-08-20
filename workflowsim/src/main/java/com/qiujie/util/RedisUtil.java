package com.qiujie.util;

import com.esotericsoftware.kryo.kryo5.Kryo;
import com.esotericsoftware.kryo.kryo5.io.Input;
import com.esotericsoftware.kryo.kryo5.io.Output;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;

import static com.qiujie.Constants.*;

public class RedisUtil {


    private static volatile JedisPool jedisPool;

    private static JedisPool getJedisPool() {
        if (jedisPool == null) {
            synchronized (RedisUtil.class) {
                if (jedisPool == null) {
                    try {
                        jedisPool = new JedisPool(HOST, PORT, null, PASSWORD);
                    } catch (Exception e) {
                        throw new IllegalStateException("Redis service is not available or not started", e);
                    }
                }
            }
        }
        return jedisPool;
    }

    public static void set(String key, Object value) {
        Kryo kryo = KryoUtil.getInstance();
        try (Jedis jedis = getJedisPool().getResource();
             ByteArrayOutputStream baos = new ByteArrayOutputStream();
             Output output = new Output(baos)) {
            kryo.writeClassAndObject(output, value);
            output.flush();
            jedis.set(key.getBytes(), baos.toByteArray());

        } catch (Exception e) {
            throw new RuntimeException("Redis set failed", e);
        }
    }


    @SuppressWarnings("unchecked")
    public static <T> T get(String key) {
        Kryo kryo = KryoUtil.getInstance();
        try (Jedis jedis = getJedisPool().getResource()) {
            byte[] bytes = jedis.get(key.getBytes());
            if (bytes == null) {
                return null;
            }
            try (ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
                 Input input = new Input(bais)) {
                return (T) kryo.readClassAndObject(input);
            }
        } catch (Exception e) {
            throw new RuntimeException("Redis get failed", e);
        }
    }

    public static void flushDB() {
        try (Jedis jedis = getJedisPool().getResource()) {
            jedis.flushDB();
        } catch (Exception e) {
            throw new RuntimeException("Redis flushDB failed", e);
        }
    }


    public static void lpush(String listKey, Object value) {
        Kryo kryo = KryoUtil.getInstance();
        try (Jedis jedis = getJedisPool().getResource();
             ByteArrayOutputStream baos = new ByteArrayOutputStream();
             Output output = new Output(baos)) {
            kryo.writeClassAndObject(output, value);
            output.flush();
            jedis.lpush(listKey.getBytes(), baos.toByteArray());
        } catch (Exception e) {
            throw new RuntimeException("Redis lpush failed", e);
        }
    }

    @SuppressWarnings("unchecked")
    public static <T> T brpop(String listKey, int timeout) {
        Kryo kryo = KryoUtil.getInstance();
        try (Jedis jedis = getJedisPool().getResource()) {
            List<byte[]> res = jedis.brpop(timeout, listKey.getBytes());
            if (res == null || res.isEmpty()) return null;
            byte[] data = res.get(1);
            try (ByteArrayInputStream bais = new ByteArrayInputStream(data);
                 Input input = new Input(bais)) {
                return (T) kryo.readClassAndObject(input);
            }
        } catch (Exception e) {
            throw new RuntimeException("Redis brpop failed", e);
        }
    }


}
