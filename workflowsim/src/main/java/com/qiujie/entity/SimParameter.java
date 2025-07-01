package com.qiujie.entity;

import com.qiujie.planner.WorkflowPlannerAbstract;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
public class SimParameter {
    private String name;
    private String experimentName;
    private long seed;
    private List<String> daxList;
    private String plannerClass;
    private Parameter parameter;

    public SimParameter(String experimentName, long seed, List<String> daxList, Class<? extends WorkflowPlannerAbstract> plannerClass, Parameter parameter) {
        this.experimentName = experimentName;
        this.seed = seed;
        this.daxList = daxList;
        this.plannerClass = plannerClass.getName();
        this.parameter = parameter;
    }

    public SimParameter(String experimentName, long seed, List<String> daxList, Class<? extends WorkflowPlannerAbstract> plannerClass) {
        this(experimentName, seed, daxList, plannerClass, new Parameter());
    }


    /**
     * single workflow
     */
    public SimParameter(String experimentName, long seed, String daxPath, Class<? extends WorkflowPlannerAbstract> plannerClass, Parameter parameter) {
        this(experimentName, seed, List.of(daxPath), plannerClass, parameter);
    }

    public SimParameter(String experimentName, long seed, String daxPath, Class<? extends WorkflowPlannerAbstract> plannerClass) {
        this(experimentName, seed, List.of(daxPath), plannerClass, new Parameter());
    }
}
