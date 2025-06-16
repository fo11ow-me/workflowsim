package com.qiujie.starter;

import com.qiujie.comparator.DefaultComparator;
import com.qiujie.entity.Job;
import com.qiujie.entity.Workflow;
import com.qiujie.core.WorkflowBroker;
import com.qiujie.enums.JobSequenceStrategyEnum;
import com.qiujie.planner.WorkflowPlannerAbstract;
import com.qiujie.comparator.WorkflowComparatorInterface;
import com.qiujie.util.ExperimentUtil;
import com.qiujie.util.WorkflowParser;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.cloudbus.cloudsim.Cloudlet;
import org.cloudbus.cloudsim.Vm;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.distributions.ContinuousDistribution;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static com.qiujie.Constants.*;


@Getter
@Slf4j
public class SimStarter {
    private static final AtomicInteger nextId = new AtomicInteger(0);

    @Getter(AccessLevel.NONE)
    private final int id;

    private String name;

    private final ContinuousDistribution random;
    private final List<String> daxPathList;
    private final Class<? extends WorkflowPlannerAbstract> plannerClass;
    private final Class<? extends WorkflowComparatorInterface> comparatorClass;
    private final boolean ascending;
    private final JobSequenceStrategyEnum jobSequenceStrategy;
    private double runtime;
    //    @Getter(AccessLevel.NONE)
    private WorkflowBroker broker;


    public SimStarter(ContinuousDistribution random, List<String> daxPathList, Class<? extends WorkflowPlannerAbstract> plannerClass, Class<? extends WorkflowComparatorInterface> comparatorClass, boolean ascending, JobSequenceStrategyEnum jobSequenceStrategy) {
        this.id = nextId.getAndIncrement();
        setName(plannerClass.getSimpleName(), comparatorClass.getSimpleName(), ascending ? "asc" : "desc", jobSequenceStrategy.name());
        this.random = random;
        this.daxPathList = daxPathList;
        this.plannerClass = plannerClass;
        this.comparatorClass = comparatorClass;
        this.ascending = ascending;
        this.jobSequenceStrategy = jobSequenceStrategy;
        start();
    }

    public SimStarter(ContinuousDistribution random, List<String> daxPathList, Class<? extends WorkflowPlannerAbstract> plannerClass, Class<? extends WorkflowComparatorInterface> comparatorClass, boolean ascending) throws Exception {
        this(random, daxPathList, plannerClass, comparatorClass, ascending, JobSequenceStrategyEnum.DEFAULT);
    }

    public SimStarter(ContinuousDistribution random, List<String> daxPathList, Class<? extends WorkflowPlannerAbstract> plannerClass) throws Exception {
        this(random, daxPathList, plannerClass, DefaultComparator.class, true, JobSequenceStrategyEnum.DEFAULT);
    }

    public SimStarter(ContinuousDistribution random, String daxPath, Class<? extends WorkflowPlannerAbstract> plannerClass, JobSequenceStrategyEnum jobSequenceStrategy) throws Exception {
        this(random, List.of(daxPath), plannerClass, DefaultComparator.class, true, jobSequenceStrategy);
    }

    /**
     * single workflow
     */
    public SimStarter(ContinuousDistribution random, String daxPath, Class<? extends WorkflowPlannerAbstract> plannerClass) throws Exception {
        this(random, List.of(daxPath), plannerClass);
    }


    private void start() {
        log.info(STARTUP, "{}: Starting...", name);
        long start = System.currentTimeMillis();
        try {
            run();
            long end = System.currentTimeMillis();
            this.runtime = (end - start) / 1000.0;
            log.info(STARTUP, String.format("%s: Running %.2fs", name, runtime));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    private void run() throws Exception {
        SIM_NAME = name;
        RANDOM = random;
        JOB_SEQUENCE_STRATEGY = jobSequenceStrategy;
        // init cloudsim
        CloudSim.init(USERS, Calendar.getInstance(), TRACE_FLAG);
        // create datacenters
        ExperimentUtil.createDatacenters();
        // create broker
        broker = new WorkflowBroker(plannerClass);
        // submit vms
        List<Vm> vmList = ExperimentUtil.createVms(broker.getId());
        broker.submitGuestList(vmList);
        // create workflow comparator
        Comparator<Workflow> comparator = comparatorClass.getDeclaredConstructor().newInstance().get(ascending);
        // submit workflows
        List<Workflow> workflowList = daxPathList.stream().map(WorkflowParser::parse).sorted(comparator).toList();
        broker.submitWorkflowList(workflowList);
        // start simulation
        CloudSim.startSimulation();
    }


    private void setName(String... args) {
        StringBuilder name = new StringBuilder();
        for (int i = 0; i < args.length; i++) {
            if (args[i].isEmpty()) {
                continue;
            }
            if (i != 0) {
                name.append("_");
            }
            name.append(args[i]);
        }
        this.name = name.toString();
    }


    public double getSimElecCost() {
        return broker.getCloudletReceivedList().stream().mapToDouble(cloudlet -> ((Job) cloudlet).getElecCost()).sum();
    }


    public double getSimFinishTime() {
        return broker.getCloudletReceivedList().getLast().getExecFinishTime();
    }

    public int getRetryCount() {
        return broker.getCloudletReceivedList().stream().mapToInt(cloudlet -> ((Job) cloudlet).getRetryCount()).sum();
    }

    public void printSimResult() {
        ExperimentUtil.printSimResult(broker.getCloudletReceivedList(), name);
    }

    public void generateSimGanttData() {
        ExperimentUtil.generateSimGanttData(broker.getCloudletReceivedList(), name);
    }


    public double getPlnElecCost() {
        return broker.getPlnElecCost();
    }

    public double getPlnFinishTime() {
        return broker.getPlnFinishTime();
    }

    public double getPlnRuntime() {
        return broker.getPlnRuntime();
    }

    public int getDcCount() {
        return (int) broker.getCloudletReceivedList().stream().mapToInt(Cloudlet::getResourceId).distinct().count();
    }

    public int getVmCount() {
        return (int) broker.getCloudletReceivedList().stream().mapToInt(Cloudlet::getGuestId).distinct().count();
    }

    public int getOverdueCount() {
        return (int) broker.getWorkflowList().stream().filter(Workflow::isOverdue).count();
    }


    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SimStarter simStarter = (SimStarter) o;
        return id == simStarter.id;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}



