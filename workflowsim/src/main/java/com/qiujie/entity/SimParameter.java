package com.qiujie.entity;

import com.qiujie.planner.WorkflowPlannerAbstract;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
public class SimParameter {
    private String id;
    private String experimentName;
    private long seed;
    private List<String> daxPathList;
    private String plannerClass;
    private Parameter parameter;

    public SimParameter(String experimentName, long seed, List<String> daxPathList, Class<? extends WorkflowPlannerAbstract> plannerClass, Parameter parameter) {
        this.experimentName = experimentName;
        this.seed = seed;
        this.daxPathList = daxPathList;
        this.plannerClass = plannerClass.getName();
        this.parameter = parameter;
    }

    public SimParameter(String experimentName, long seed, List<String> daxPathList, Class<? extends WorkflowPlannerAbstract> plannerClass) {
        this(experimentName, seed, daxPathList, plannerClass, new Parameter());
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
