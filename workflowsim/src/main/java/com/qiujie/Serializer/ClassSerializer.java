package com.qiujie.Serializer;

import com.esotericsoftware.kryo.kryo5.Kryo;
import com.esotericsoftware.kryo.kryo5.io.Input;
import com.esotericsoftware.kryo.kryo5.io.Output;

public class ClassSerializer extends com.esotericsoftware.kryo.kryo5.Serializer<Class<?>> {

    @Override
    public void write(Kryo kryo, Output output, Class<?> object) {
        output.writeString(object.getName());
    }

    @Override
    public Class<?> read(Kryo kryo, Input input, Class<? extends Class<?>> type) {
        try {
            return Class.forName(input.readString());
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }
}
