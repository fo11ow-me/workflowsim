package com.qiujie.starter;

import ch.qos.logback.classic.Level;
import cn.hutool.json.JSONUtil;
import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.qiujie.aop.ClockModifier;
import com.qiujie.entity.*;
import com.qiujie.core.WorkflowBroker;
import com.qiujie.planner.WorkflowPlannerAbstract;
import com.qiujie.util.ExperimentUtil;
import com.qiujie.util.KryoUtil;
import com.qiujie.util.Log;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.cloudbus.cloudsim.Vm;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.distributions.ContinuousDistribution;
import org.cloudbus.cloudsim.distributions.UniformDistr;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.reflect.Constructor;
import java.util.*;

import static com.qiujie.Constants.*;


@Slf4j
public class SimStarter {

    private final String name;
    private final String experimentName;
    private final ContinuousDistribution random;
    private final WorkflowPlannerAbstract planner;
    private final SimParam simParam;
    @Getter
    private Result result;

    private SimStarter(String experimentName, SimParam simParam) throws Exception {
        this.experimentName = experimentName;
        this.simParam = simParam;
        this.random = new UniformDistr(0, 1, simParam.getSeed());
        // init planner
        Class<?> plannerClass = Class.forName(simParam.getPlannerClass());
        Constructor<?> constructor = plannerClass.getDeclaredConstructor(ContinuousDistribution.class, Param.class);
        this.planner = (WorkflowPlannerAbstract) constructor.newInstance(random, simParam.getParam());
        this.name = planner.toString();
        start();
    }

    public SimStarter(SimParam simParam) throws Exception {
        this("", simParam);
    }

    public void start() throws Exception {
        log.info("{}: Starting...", name);
        long start = System.currentTimeMillis();
        run();
        long end = System.currentTimeMillis();
        double runtime = (end - start) / 1000.0;
        log.info("{}: Finished in {}s\n", name, runtime);
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
        List<Workflow> workflowList = ExperimentUtil.createWorkflow(simParam.getDaxList());
        broker.submitWorkflow(workflowList);
        // start simulation
        CloudSim.startSimulation();
        if (broker.getCloudletReceivedList().isEmpty()) {
            throw new IllegalStateException("No cloudlet received");
        }
        setResult(broker);
        ExperimentUtil.printSimResult(broker.getCloudletReceivedList(), planner.toString());
        if (ENABLE_SIM_DATA) {
            ExperimentUtil.generateSimData(broker.getCloudletReceivedList(), experimentName + "_" + simParam.getId() + "_" + planner);
        }
    }

    private void setResult(WorkflowBroker broker) {
        int finishedCloudlets = broker.getCloudletReceivedList().size();
        int totalCloudlets = broker.getWorkflowList().stream().mapToInt(Workflow::getJobNum).sum();
        this.result = new Result()
                .setId(simParam.getId())
                .setName(name)
                .setWorkflowComparator(ExperimentUtil.getPrefixFromClassName(simParam.getParam().getWorkflowComparator()))
                .setAscending(simParam.getParam().isAscending())
                .setDeadlineFactor(simParam.getParam().getDeadlineFactor())
                .setReliabilityFactor(simParam.getParam().getReliabilityFactor())
                .setJobSequenceStrategy(simParam.getParam().getJobSequenceStrategy().name())
                .setNeighborhoodFactor(simParam.getParam().getNeighborhoodFactor())
                .setSlackTimeFactor(simParam.getParam().getSlackTimeFactor())
                .setDaxList(new ArrayList<>(simParam.getDaxList()))
                .setCompletionDetail(String.format("%.2f%% (%d / %d)", finishedCloudlets * 100.0 / totalCloudlets, finishedCloudlets, totalCloudlets))
                .setElecCost(broker.getCloudletReceivedList().stream().mapToDouble(cloudlet -> ((Job) cloudlet).getElecCost()).sum())
                .setFinishTime(broker.getCloudletReceivedList().getLast().getExecFinishTime())
                .setRetryCount(broker.getCloudletReceivedList().stream().mapToInt(cloudlet -> ((Job) cloudlet).getRetryCount()).sum())
                .setOverdueCount((int) broker.getWorkflowList().stream().filter(Workflow::isOverdue).count())
                .setPlnRuntime(planner.getRuntime());

    }


    public static void main(String[] args) {
        int exitCode = 0;
        try {

            int logLevel = Integer.parseInt(args[0]);
            Log.setLevel(Level.toLevel(logLevel));
            Kryo kryo = KryoUtil.getInstance();
            SimParam simParam;
            try(Input input = new Input(System.in)){
                 simParam = kryo.readObject(input, SimParam.class);
            }

            String experimentName = System.getProperty("startup.class");
            SimStarter starter = new SimStarter(experimentName, simParam);

            try (Output output = new Output(System.out)) {
                kryo.writeObject(output, starter.getResult());
                output.flush();
            }

        } catch (Exception e) {
            log.error("❌  Sim failed", e);
            exitCode = 1;
        } finally {
            System.exit(exitCode);
        }
    }
}
