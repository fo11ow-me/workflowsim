package com.qiujie.entity;

import com.qiujie.planner.WorkflowPlannerAbstract;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
public class SimParam {
    private long seed;
    private List<String> daxList;
    private String plannerClass;
    private Param param;

    public SimParam(long seed, List<String> daxList, Class<? extends WorkflowPlannerAbstract> plannerClass, Param param) {
        this.seed = seed;
        this.daxList = daxList;
        this.plannerClass = plannerClass.getName();
        this.param = param;
    }

    public SimParam(long seed, List<String> daxList, Class<? extends WorkflowPlannerAbstract> plannerClass) {
        this(seed, daxList, plannerClass, new Param());
    }


    /**
     * single workflow
     */
    public SimParam(long seed, String daxPath, Class<? extends WorkflowPlannerAbstract> plannerClass, Param param) {
        this(seed, List.of(daxPath), plannerClass, param);
    }

    public SimParam(long seed, String daxPath, Class<? extends WorkflowPlannerAbstract> plannerClass) {
        this(seed, List.of(daxPath), plannerClass, new Param());
    }
}
