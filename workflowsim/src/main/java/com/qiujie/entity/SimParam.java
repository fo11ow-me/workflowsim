package com.qiujie.entity;

import com.qiujie.planner.WorkflowPlannerAbstract;
import lombok.AccessLevel;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

@Data
@Accessors(chain = true)
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class SimParam {

    private static final AtomicInteger nextId = new AtomicInteger(0);
    private int id;
    private long seed;
    private List<String> daxList;
    private Class<? extends WorkflowPlannerAbstract> plannerClass;
    private Param param;

    public static final SimParam POISON_PILL = new SimParam().setId(-1);

    public SimParam(long seed, List<String> daxList, Class<? extends WorkflowPlannerAbstract> plannerClass, Param param) {
        this.id = nextId.getAndIncrement();
        this.seed = seed;
        this.daxList = new ArrayList<>(daxList);
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
        this(seed, List.of(dax), plannerClass, param);
    }

    public SimParam(long seed, String dax, Class<? extends WorkflowPlannerAbstract> plannerClass) {
        this(seed, dax, plannerClass, new Param());
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) return true;
        if (object == null || getClass() != object.getClass()) return false;
        SimParam simParam = (SimParam) object;
        return id == simParam.id;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
