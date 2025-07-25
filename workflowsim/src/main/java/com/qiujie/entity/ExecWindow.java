package com.qiujie.entity;


import lombok.Data;

import java.util.concurrent.atomic.AtomicInteger;

@Data
public class ExecWindow {

    private static final AtomicInteger nextId = new AtomicInteger(0);

    private final int id;

    // execution start time
    private final double startTime;
    // execution finish time
    private final double finishTime;

    public ExecWindow(double startTime, double finishTime) {
        this.id = nextId.getAndIncrement();
        this.startTime = startTime;
        this.finishTime = finishTime;
    }
}
