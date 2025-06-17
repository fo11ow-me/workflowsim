package com.qiujie.starter;

import com.qiujie.entity.Job;
import com.qiujie.entity.Parameter;
import com.qiujie.entity.Workflow;
import com.qiujie.core.WorkflowBroker;
import com.qiujie.enums.LevelEnum;
import com.qiujie.planner.WorkflowPlannerAbstract;
import com.qiujie.util.ExperimentUtil;
import com.qiujie.util.WorkflowParser;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.cloudbus.cloudsim.Cloudlet;
import org.cloudbus.cloudsim.Vm;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.distributions.ContinuousDistribution;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static com.qiujie.Constants.*;


@Getter
@Slf4j
public class SimStarter {

    private static final AtomicInteger nextId = new AtomicInteger(0);

    @Getter(AccessLevel.NONE)
    private final int id;
    private final String name;
    private final ContinuousDistribution random;
    private final List<String> daxPathList;
    private final Class<? extends WorkflowPlannerAbstract> plannerClass;
    private double runtime;
    //    @Getter(AccessLevel.NONE)
    private WorkflowBroker broker;

    private final Parameter parameter;

    private final Marker STARTUP = MarkerFactory.getMarker(LevelEnum.STARTUP.name());

    public SimStarter(ContinuousDistribution random, List<String> daxPathList, Class<? extends WorkflowPlannerAbstract> plannerClass, Parameter parameter) {
        this.id = nextId.getAndIncrement();
        this.random = random;
        this.daxPathList = daxPathList;
        this.plannerClass = plannerClass;
        this.parameter = parameter;
        this.name = plannerClass.getSimpleName().replace("Planner", "") + parameter;
        start();
    }

    public SimStarter(ContinuousDistribution random, List<String> daxPathList, Class<? extends WorkflowPlannerAbstract> plannerClass) {
        this(random, daxPathList, plannerClass, new Parameter());
    }

    /*************       single workflow              *************/
    public SimStarter(ContinuousDistribution random, String daxPath, Class<? extends WorkflowPlannerAbstract> plannerClass, Parameter parameter) {
        this(random, List.of(daxPath), plannerClass, parameter);
    }

    public SimStarter(ContinuousDistribution random, String daxPath, Class<? extends WorkflowPlannerAbstract> plannerClass) {
        this(random, List.of(daxPath), plannerClass, new Parameter());
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
        // init cloudsim
        CloudSim.init(USERS, Calendar.getInstance(), TRACE_FLAG);
        // create datacenters
        ExperimentUtil.createDatacenters();
        // create broker
        broker = new WorkflowBroker(plannerClass, parameter);
        // submit vms
        List<Vm> vmList = ExperimentUtil.createVms(broker.getId());
        broker.submitGuestList(vmList);
        // submit workflows
        List<Workflow> workflowList = daxPathList.stream().map(WorkflowParser::parse).toList();
        broker.submitWorkflowList(workflowList);
        // start simulation
        CloudSim.startSimulation();
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



