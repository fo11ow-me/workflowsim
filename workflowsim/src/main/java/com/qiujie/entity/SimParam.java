package com.qiujie.entity;

import com.qiujie.planner.WorkflowPlannerAbstract;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@Data
@NoArgsConstructor
public class SimParam {

    private static final AtomicInteger nextId = new AtomicInteger(0);
    private int id;
    private long seed;
    private List<String> daxList;
    private String plannerClass;
    private Param param;

    public SimParam(long seed, List<String> daxList, Class<? extends WorkflowPlannerAbstract> plannerClass, Param param) {
        this.id = nextId.getAndIncrement();
        this.seed = seed;
        this.daxList = new ArrayList<>(daxList);
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
