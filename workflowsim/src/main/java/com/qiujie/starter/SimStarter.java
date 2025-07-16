package com.qiujie.starter;

import ch.qos.logback.classic.Level;
import cn.hutool.core.io.FileUtil;
import cn.hutool.json.JSONUtil;
import com.qiujie.aop.ClockModifier;
import com.qiujie.entity.*;
import com.qiujie.core.WorkflowBroker;
import com.qiujie.planner.WorkflowPlannerAbstract;
import com.qiujie.util.ExperimentUtil;
import com.qiujie.util.Log;
import com.qiujie.util.WorkflowParser;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.cloudbus.cloudsim.Vm;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.distributions.ContinuousDistribution;
import org.cloudbus.cloudsim.distributions.UniformDistr;

import java.lang.reflect.Constructor;
import java.util.*;

import static com.qiujie.Constants.*;


@Slf4j
public class SimStarter {

    private final int simIdx;
    private final String name;
    private final String experimentName;
    private final ContinuousDistribution random;
    private final WorkflowPlannerAbstract planner;
    private final SimParameter simParameter;
    @Getter
    private Result result;

    private SimStarter(int simIdx, String experimentName, SimParameter simParameter) throws Exception {
        this.simIdx = simIdx;
        this.experimentName = experimentName;
        this.simParameter = simParameter;
        this.random = new UniformDistr(0, 1, simParameter.getSeed());
        // init planner
        Class<?> plannerClass = Class.forName(simParameter.getPlannerClass());
        Constructor<?> constructor = plannerClass.getDeclaredConstructor(ContinuousDistribution.class, Parameter.class);
        this.planner = (WorkflowPlannerAbstract) constructor.newInstance(random, simParameter.getParameter());
        this.name = planner.toString();
        start();
    }

    public SimStarter(SimParameter simParameter) throws Exception {
        this(0, "", simParameter);
    }

    public void start() throws Exception {
        log.info("{}: Starting...", name);
        long start = System.currentTimeMillis();
        run();
        long end = System.currentTimeMillis();
        double runtime = (end - start) / 1000.0;
        log.info("{}: Finished in {}s", name, runtime);
        result.setRuntime(runtime);
    }

    private void run() throws Exception {
        ClockModifier.modifyClockMethod();
        org.cloudbus.cloudsim.Log.disable();
        // init cloudsim
        CloudSim.init(USERS, Calendar.getInstance(), TRACE_FLAG, MIN_TIME_BETWEEN_EVENTS);
        // create datacenters
        ExperimentUtil.createDatacenters();
        // create broker
        WorkflowBroker broker = new WorkflowBroker(random, planner);
        // submit vms
        List<Vm> vmList = ExperimentUtil.createVms(random, broker.getId());
        broker.submitGuestList(vmList);
        // submit workflows
        List<Workflow> workflowList = simParameter.getDaxList().stream().map(WorkflowParser::parse).toList();
        broker.submitWorkflowList(workflowList);
        // start simulation
        CloudSim.startSimulation();
        if (broker.getCloudletReceivedList().isEmpty()) {
            throw new IllegalStateException("No cloudlet received");
        }
        setResult(broker);
        ExperimentUtil.printSimResult(broker.getCloudletReceivedList(), planner.toString());
        if (ENABLE_SIM_DATA) {
            ExperimentUtil.generateSimData(broker.getCloudletReceivedList(), experimentName + "_" + simIdx + "_" + planner);
        }
    }

    private void setResult(WorkflowBroker broker) {
        int finishedCloudlets = broker.getCloudletReceivedList().size();
        int totalCloudlets = broker.getWorkflowList().stream().mapToInt(Workflow::getJobNum).sum();
        this.result = new Result()
                .setSimIdx(simIdx)
                .setName(name)
                .setWorkflowComparator(ExperimentUtil.getPrefixFromClassName(simParameter.getParameter().getWorkflowComparator()))
                .setAscending(simParameter.getParameter().isAscending())
                .setDeadlineFactor(simParameter.getParameter().getDeadlineFactor())
                .setReliabilityFactor(simParameter.getParameter().getReliabilityFactor())
                .setJobSequenceStrategy(simParameter.getParameter().getJobSequenceStrategy().name())
                .setNeighborhoodFactor(simParameter.getParameter().getNeighborhoodFactor())
                .setSlackTimeFactor(simParameter.getParameter().getSlackTimeFactor())
                .setDaxList(new ArrayList<>(simParameter.getDaxList()))
                .setCompletionDetail(String.format("%.2f%% (%d / %d)", finishedCloudlets * 100.0 / totalCloudlets, finishedCloudlets, totalCloudlets))
                .setElecCost(broker.getCloudletReceivedList().stream().mapToDouble(cloudlet -> ((Job) cloudlet).getElecCost()).sum())
                .setFinishTime(broker.getCloudletReceivedList().getLast().getExecFinishTime())
                .setRetryCount(broker.getCloudletReceivedList().stream().mapToInt(cloudlet -> ((Job) cloudlet).getRetryCount()).sum())
                .setOverdueCount((int) broker.getWorkflowList().stream().filter(Workflow::isOverdue).count())
                .setPlnRuntime(planner.getRuntime());

    }


    public static void main(String[] args) {
        try {
            int logLevel = Integer.parseInt(args[0]);
            Log.setLevel(Level.toLevel(logLevel));
            String paramPath = args[1];
            int simIdx = Integer.parseInt(System.getProperty("sim.idx"));
            String experimentName = System.getProperty("startup.class");
            String json = FileUtil.readUtf8String(paramPath);
            List<SimParameter> paramList = JSONUtil.toList(json, SimParameter.class);
            SimParameter simParameter = paramList.get(simIdx);
            SimStarter starter = new SimStarter(simIdx, experimentName, simParameter);
            String resultPath = RESULT_DIR + experimentName + "_" + simIdx + ".json";
            FileUtil.writeUtf8String(JSONUtil.toJsonPrettyStr(starter.getResult()), resultPath);
            System.exit(0);
        } catch (Exception e) {
            log.error("❌  Sim failed", e);
            System.exit(1);
        }
    }
}
