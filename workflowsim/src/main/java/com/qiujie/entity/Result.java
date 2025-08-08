package com.qiujie.entity;

import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.util.List;
import java.util.Objects;

@Data
@Accessors(chain = true)
public class Result implements Serializable {
    private int id;
    private String name;
    private String workflowComparator;
    private boolean ascending;
    private double deadlineFactor;
    private double reliabilityFactor;
    private String jobSequenceStrategy;
    private double neighborhoodFactor;
    private double slackTimeFactor;
    private List<String> daxList;
    private String completionDetail;
    private double elecCost;
    private double finishTime;
    private int retryCount;
    private int overdueCount;
    private double plnRuntime;
    private double runtime;


    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Result result = (Result) o;
        return id == result.id;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
