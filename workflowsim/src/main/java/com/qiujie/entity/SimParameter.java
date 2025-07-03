package com.qiujie.entity;

import com.qiujie.planner.WorkflowPlannerAbstract;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
public class SimParameter {
    private long seed;
    private List<String> daxList;
    private String plannerClass;
    private Parameter parameter;

    public SimParameter(long seed, List<String> daxList, Class<? extends WorkflowPlannerAbstract> plannerClass, Parameter parameter) {
        this.seed = seed;
        this.daxList = daxList;
        this.plannerClass = plannerClass.getName();
        this.parameter = parameter;
    }

    public SimParameter(long seed, List<String> daxList, Class<? extends WorkflowPlannerAbstract> plannerClass) {
        this(seed, daxList, plannerClass, new Parameter());
    }


    /**
     * single workflow
     */
    public SimParameter(long seed, String daxPath, Class<? extends WorkflowPlannerAbstract> plannerClass, Parameter parameter) {
        this(seed, List.of(daxPath), plannerClass, parameter);
    }

    public SimParameter(long seed, String daxPath, Class<? extends WorkflowPlannerAbstract> plannerClass) {
        this(seed, List.of(daxPath), plannerClass, new Parameter());
    }
}
