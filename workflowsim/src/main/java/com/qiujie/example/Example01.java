package com.qiujie.example;

import ch.qos.logback.classic.Level;
import com.qiujie.aop.ClockModifier;
import com.qiujie.entity.Job;
import com.qiujie.entity.Param;
import com.qiujie.entity.Workflow;
import com.qiujie.core.WorkflowBroker;
import com.qiujie.planner.RandomPlanner;
import com.qiujie.util.ExperimentUtil;
import com.qiujie.util.Log;
import org.cloudbus.cloudsim.Datacenter;
import org.cloudbus.cloudsim.Vm;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.distributions.ContinuousDistribution;
import org.cloudbus.cloudsim.distributions.UniformDistr;

import java.util.Calendar;
import java.util.List;

import static com.qiujie.Constants.*;


/**
 * @author QIUJIE
 * <p>
 * schedule a workflow
 */
public class Example01 {
    public static void main(String[] args) throws Exception {
        long send = System.currentTimeMillis();
        ClockModifier.modifyClockMethod();
        org.cloudbus.cloudsim.Log.disable();
        CloudSim.init(USERS, Calendar.getInstance(), TRACE_FLAG, MIN_TIME_BETWEEN_EVENTS);
        Log.setLevel(Level.TRACE);
        VMS = 25;
        String dax = "Montage_50";
        // basic parameters
        ContinuousDistribution random = new UniformDistr(0, 1, send);

        // create datacenters
        List<Datacenter> datacenterList = ExperimentUtil.createDatacenters();
        // create broker
        WorkflowBroker broker = new WorkflowBroker(random, new RandomPlanner(random, new Param()));
        // submit vms
        List<Vm> vmList = ExperimentUtil.createVms(random, broker.getId());
        broker.submitGuestList(vmList);
        // submit workflows
        Workflow workflow = ExperimentUtil.createWorkflow(dax);
        broker.submitWorkflow(workflow);
        // start simulation
        CloudSim.startSimulation();
        List<Job> cloudletReceivedList = broker.getCloudletReceivedList();

        String className = new Object() {
        }.getClass().getEnclosingClass().getSimpleName();

        // print result
        ExperimentUtil.printSimResult(cloudletReceivedList, broker.getName());
        // generate gantt chart data
        ExperimentUtil.generateSimData(cloudletReceivedList, className + "_" + broker.getName());
        System.out.println(className + " take " + (System.currentTimeMillis() - send) / 1000.0 + "s");
    }
}
