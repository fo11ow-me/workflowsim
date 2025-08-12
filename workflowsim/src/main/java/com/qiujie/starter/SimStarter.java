package com.qiujie.starter;

import ch.qos.logback.classic.Level;

import com.qiujie.aop.ClockModifier;
import com.qiujie.entity.*;
import com.qiujie.core.WorkflowBroker;
import com.qiujie.planner.WorkflowPlannerAbstract;
import com.qiujie.util.ExperimentUtil;
import com.qiujie.util.Log;
import lombok.extern.slf4j.Slf4j;
import org.cloudbus.cloudsim.Vm;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.distributions.ContinuousDistribution;
import org.cloudbus.cloudsim.distributions.UniformDistr;
import org.slf4j.MDC;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.reflect.Constructor;
import java.util.*;

import static com.qiujie.Constants.*;


@Slf4j
public class SimStarter {


    private final String name;

    private SimStarter(String name) {
        this.name = name;
        ClockModifier.modifyClockMethod();
        org.cloudbus.cloudsim.Log.disable();
    }

    public SimStarter() {
        this("");
    }


    public Result start(SimParam simParam) {
        MDC.put("sim.id", String.valueOf(simParam.getId()));
        try {
            ContinuousDistribution random = new UniformDistr(0, 1, simParam.getSeed());
            Class<?> plannerClass = Class.forName(simParam.getPlannerClass());
            Constructor<?> constructor = plannerClass.getDeclaredConstructor(ContinuousDistribution.class, Param.class);
            WorkflowPlannerAbstract planner = (WorkflowPlannerAbstract) constructor.newInstance(random, simParam.getParam());
            log.info("{}: Starting...", planner);
            long startTime = System.currentTimeMillis();
            WorkflowBroker broker = run(simParam, random, planner);
            double runtime = (System.currentTimeMillis() - startTime) / 1000.0;
            log.info("{}: Finished in {}s\n", planner, runtime);
            return new Result(simParam, planner, broker, runtime);
        } catch (Exception e) {
            log.error("❌ Sim {} failed", simParam, e);
            return null;
        } finally {
            MDC.remove("sim.id");
        }
    }


    private WorkflowBroker run(SimParam simParam, ContinuousDistribution random, WorkflowPlannerAbstract planner) throws Exception {
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
        ExperimentUtil.printSimResult(broker.getCloudletReceivedList(), planner.toString());
        if (ENABLE_SIM_DATA) {
            ExperimentUtil.generateSimData(broker.getCloudletReceivedList(), name + "_" + simParam.getId() + "_" + planner);
        }
        return broker;
    }


    public static void main(String[] args) {
        Log.setLevel(Level.toLevel(Integer.parseInt(args[0])));
        SimStarter starter = new SimStarter(System.getProperty("startup.class"));
        try (ObjectOutputStream output = new ObjectOutputStream(System.out); ObjectInputStream input = new ObjectInputStream(System.in)) {
            output.flush();
            while (true) {
                SimParam simParam = (SimParam) input.readObject();
                if (simParam.equals(SimParam.POISON_PILL)) {
                    output.writeObject(Result.POISON_PILL);
                    output.flush();
                    break;
                }
                Result result = starter.start(simParam);
                if (result != null) {
                    output.writeObject(result);
                    output.flush();
                } else {
                    output.writeObject(Result.POISON_PILL);
                    output.flush();
                }
            }
        } catch (IOException | ClassNotFoundException e) {
            log.error("❌  SimStarter failed", e);
        }
    }
}
