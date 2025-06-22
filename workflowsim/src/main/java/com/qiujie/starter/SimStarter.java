package com.qiujie.starter;

import ch.qos.logback.classic.Level;
import cn.hutool.core.io.FileUtil;
import cn.hutool.json.JSONUtil;
import com.qiujie.aop.ClockModifier;
import com.qiujie.entity.*;
import com.qiujie.core.WorkflowBroker;
import com.qiujie.enums.LevelEnum;
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
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

import java.lang.reflect.Constructor;
import java.util.*;

import static com.qiujie.Constants.*;


@Slf4j
public class SimStarter {

    private final ContinuousDistribution random;
    private final WorkflowPlannerAbstract planner;
    private final SimParameter simParameter;
    @Getter
    private Result result;
    private double runtime;

    private final Marker STARTUP_SIM = MarkerFactory.getMarker(LevelEnum.STARTUP_SIM.name());


    public SimStarter(SimParameter simParameter) throws Exception {
        this.simParameter = simParameter;
        this.random = new UniformDistr(0, 1, simParameter.getSeed());
        // init planner
        Class<?> plannerClass = Class.forName(simParameter.getPlannerClass());
        Constructor<?> constructor = plannerClass.getDeclaredConstructor(ContinuousDistribution.class, Parameter.class);
        this.planner = (WorkflowPlannerAbstract) constructor.newInstance(random, simParameter.getParameter());
        start();
    }

    public void start() {
        log.info(STARTUP_SIM, "{}: Starting...", planner);
        long start = System.currentTimeMillis();
        try {
            run();
            long end = System.currentTimeMillis();
            this.runtime = (end - start) / 1000.0;
            log.info(STARTUP_SIM, String.format("%s: Running %.2fs", planner, runtime));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void run() throws Exception {
        ClockModifier.modifyClockMethod();
        org.cloudbus.cloudsim.Log.disable();
        // init cloudsim
        CloudSim.init(USERS, Calendar.getInstance(), TRACE_FLAG);
        // create datacenters
        ExperimentUtil.createDatacenters();
        // create broker
        WorkflowBroker broker = new WorkflowBroker(random, planner);
        // submit vms
        List<Vm> vmList = ExperimentUtil.createVms(random, broker.getId());
        broker.submitGuestList(vmList);
        // submit workflows
        List<Workflow> workflowList = simParameter.getDaxPathList().stream().map(WorkflowParser::parse).toList();
        broker.submitWorkflowList(workflowList);
        // start simulation
        CloudSim.startSimulation();
        ExperimentUtil.printSimResult(broker.getCloudletReceivedList(), planner.toString());
        if (ENABLE_SIM_DATA) {
            ExperimentUtil.generateSimData(broker.getCloudletReceivedList(), simParameter.getExperimentName() + "_" + planner);
        }
        setResult(broker);
    }

    private void setResult(WorkflowBroker broker) {
        this.result = new Result(planner.toString(),
                ExperimentUtil.getPrefixFromClassName(simParameter.getParameter().getWorkflowComparator()),
                simParameter.getParameter().isAscending(),
                simParameter.getParameter().getDeadlineFactor(),
                simParameter.getParameter().getReliabilityFactor(),
                simParameter.getParameter().getJobSequenceStrategy().name(),
                simParameter.getParameter().getNeighborhoodFactor(),
                simParameter.getParameter().getSlackTimeFactor(),
                simParameter.getDaxPathList().stream().map(FileUtil::mainName).toList(),
                broker.getCloudletReceivedList().stream().mapToDouble(cloudlet -> ((Job) cloudlet).getElecCost()).sum(),
                broker.getCloudletReceivedList().getLast().getExecFinishTime(),
                broker.getCloudletReceivedList().stream().mapToInt(cloudlet -> ((Job) cloudlet).getRetryCount()).sum(),
                (int) broker.getWorkflowList().stream().filter(Workflow::isOverdue).count(),
                planner.getRuntime(),
                runtime
        );
    }


    public static void main(String[] args) {
        try {
            int logLevel = Integer.parseInt(args[0]);
            Log.setLevel(Level.toLevel(logLevel));
            String paramPath = args[1];
            // Read file content as string
            String json = FileUtil.readUtf8String(paramPath);
            // Convert JSON string to SimParameter object
            SimParameter simParameter = JSONUtil.toBean(json, SimParameter.class);
            SimStarter starter = new SimStarter(simParameter);
            String path = RESULT_DIR + simParameter.getId() + ".json";
            FileUtil.writeUtf8String(JSONUtil.toJsonPrettyStr(starter.getResult()), path);
            System.exit(0);
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(2);
        }
    }


}



