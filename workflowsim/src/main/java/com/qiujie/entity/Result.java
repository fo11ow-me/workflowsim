package com.qiujie.entity;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

@Data
@Accessors(chain = true)
public class Result {
    private String name;
    private String workflowComparator;
    private boolean ascending;
    private double deadlineFactor;
    private double reliabilityFactor;
    private String jobSequenceStrategy;
    private double neighborhoodFactor;
    private double slackTimeFactor;
    private List<String> daxList;
    private double elecCost;
    private double finishTime;
    private int retryCount;
    private int overdueCount;
    private double plnRuntime;
    private double runtime;
}
