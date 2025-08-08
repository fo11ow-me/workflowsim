package com.qiujie.util;

import cn.hutool.json.JSONUtil;
import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

public class RedisUtil {

    private static final String HOST = "localhost";
    private static final int PORT = 6379;
    private static final String PASSWORD = "123456";

    private static volatile JedisPool jedisPool;

    // lazy loading
    private static JedisPool getJedisPool() {
        if (jedisPool == null) {
            synchronized (RedisUtil.class) {
                if (jedisPool == null) {
                    jedisPool = new JedisPool(HOST, PORT, null, PASSWORD);
                }
            }
        }
        return jedisPool;
    }

    public static void setObject(String key, Object value) {
        Kryo kryo = KryoUtil.getInstance();
        try (Jedis jedis = getJedisPool().getResource();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
             Output output = new Output(outputStream)) {
            kryo.writeObject(output, value);
            output.flush();
            byte[] bytes = outputStream.toByteArray();
            jedis.set(key.getBytes(), bytes);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static <T> T getObject(String key, Class<T> clazz) {
        Kryo kryo = KryoUtil.getInstance();
        try (Jedis jedis = getJedisPool().getResource()) {
            byte[] bytes = jedis.get(key.getBytes());
            if (bytes == null) return null;
            try (ByteArrayInputStream inputStream = new ByteArrayInputStream(bytes);
                 Input input = new Input(inputStream)) {
                return kryo.readObject(input, clazz);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
