package com.qiujie.entity;

import com.qiujie.planner.WorkflowPlannerAbstract;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@Data
@Accessors(chain = true)
public class SimParam {

    private static final AtomicInteger nextId = new AtomicInteger(0);
    private int id;
    private long seed;
    private List<String> daxList;
    private Class<? extends WorkflowPlannerAbstract> plannerClass;
    private Param param;

    public static final SimParam POISON_PILL = new SimParam().setId(-1);

    public SimParam() {
        this.id = nextId.getAndIncrement();
        this.daxList = new ArrayList<>();
    }

    public SimParam(long seed, List<String> daxList, Class<? extends WorkflowPlannerAbstract> plannerClass, Param param) {
        this();
        this.seed = seed;
        this.daxList.addAll(daxList);
        this.plannerClass = plannerClass;
        this.param = param;
    }

    public SimParam(long seed, List<String> daxList, Class<? extends WorkflowPlannerAbstract> plannerClass) {
        this(seed, daxList, plannerClass, new Param());
    }


    /**
     * single workflow
     */
    public SimParam(long seed, String dax, Class<? extends WorkflowPlannerAbstract> plannerClass, Param param) {
        this();
        this.seed = seed;
        this.daxList.add(dax);
        this.plannerClass = plannerClass;
        this.param = param;
    }

    public SimParam(long seed, String dax, Class<? extends WorkflowPlannerAbstract> plannerClass) {
        this(seed, dax, plannerClass, new Param());
    }
}
