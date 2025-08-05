package com.qiujie.entity;

import lombok.Data;
import lombok.experimental.Accessors;
import org.cloudbus.cloudsim.Vm;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

@Data
@Accessors(chain = true)
public class Fv {

    private double frequency;
    private double mips;
    private double power; // watt
    // Transient fault arrival rate
    private double lambda;
    private int level;
    private Vm vm;
    private String type;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Fv fv = (Fv) o;
        return Objects.equals(type, fv.getType());
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(type);
    }


}
