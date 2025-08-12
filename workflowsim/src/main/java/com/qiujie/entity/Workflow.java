package com.qiujie.entity;

import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@Getter
public class Workflow {

    private static final AtomicInteger nextId = new AtomicInteger(0);

    private final int id;

    private final String name;

    private final List<Job> jobList;

    private final int jobNum;

    private final long length;

    private final int depth;

    @Setter
    private double deadline;

    @Setter
    private double reliGoal;

    public Workflow(String name, List<Job> jobList) {
        this.id = nextId.getAndIncrement();
        this.name = name;
        this.jobList = jobList;
        this.jobNum = jobList.size();
        this.length = jobList.stream().mapToLong(Job::getLength).sum();
        this.depth = jobList.stream().mapToInt(Job::getDepth).max().getAsInt();
    }

    public boolean isOverdue() {
        return jobList.stream().anyMatch(job -> job.getExecFinishTime() > deadline);
    }

}
