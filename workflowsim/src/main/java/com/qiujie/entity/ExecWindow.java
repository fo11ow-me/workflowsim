package com.qiujie.entity;


import lombok.Data;

import java.util.concurrent.atomic.AtomicInteger;

@Data
public class ExecWindow {

    private static final AtomicInteger nextId = new AtomicInteger(0);

    private final int id;
    private final int insertPos;
    private final double startTime;
    private double finishTime;
    private Fv fv;
    private double elecCost;

    public ExecWindow(double startTime, double finishTime, int insertPos, Fv fv) {
        this.id = nextId.getAndIncrement();
        this.startTime = startTime;
        this.finishTime = finishTime;
        this.insertPos = insertPos;
        this.fv = fv;
    }
}
