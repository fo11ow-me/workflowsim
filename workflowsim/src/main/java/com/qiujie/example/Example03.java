
package com.qiujie.example;

import ch.qos.logback.classic.Level;
import com.qiujie.aop.ClockModifier;
import com.qiujie.entity.Job;
import com.qiujie.entity.Parameter;
import com.qiujie.entity.Workflow;
import com.qiujie.core.WorkflowBroker;
import com.qiujie.planner.HEFTPlanner;
import com.qiujie.util.ExperimentUtil;
import com.qiujie.util.Log;
import com.qiujie.util.WorkflowParser;
import org.cloudbus.cloudsim.Datacenter;
import org.cloudbus.cloudsim.Vm;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.distributions.ContinuousDistribution;
import org.cloudbus.cloudsim.distributions.UniformDistr;

import java.util.Calendar;
import java.util.List;

import static com.qiujie.Constants.*;


/**
 * multiple brokers, each broker schedule multiple workflows
 */
public class Example03 {
    public static void main(String[] args) throws Exception {
        long send = System.currentTimeMillis();
        ContinuousDistribution random = new UniformDistr(0, 1, send);
        ClockModifier.modifyClockMethod();
        org.cloudbus.cloudsim.Log.disable();
        CloudSim.init(2, Calendar.getInstance(), TRACE_FLAG,MIN_TIME_BETWEEN_EVENTS);
        Log.setLevel(Level.TRACE);
        List<String> daxList = List.of(
//                "Inspiral_1000",
//                "Inspiral_100",
                "Inspiral_50",
//                "Epigenomics_997",
                "Sipht_30",
//                "Montage_1000",
//                "CyberShake_100",
//                "CyberShake_30",
//                "Epigenomics_46",
//                "Epigenomics_24",
                "Montage_100",
//                "Montage_1000",
                "Montage_50"
        );

        List<String> daxList1 = List.of(
//                "Inspiral_1000",
//                "Inspiral_100",
                "Inspiral_50",
//                "Epigenomics_997",
                "Sipht_30",
//                "Montage_1000",
//                "CyberShake_100",
//                "CyberShake_30",
//                "Epigenomics_46",
//                "Epigenomics_24",
                "Montage_100",
//                "Montage_1000",
                "Montage_50"
        );

        VMS = 100;

        List<Datacenter> datacenterList = ExperimentUtil.createDatacenters();

        WorkflowBroker broker = new WorkflowBroker(random, new HEFTPlanner(random, new Parameter()));
        List<Vm> vmList = ExperimentUtil.createVms(random, broker.getId());
        broker.submitGuestList(vmList);
        List<Workflow> workflowList = daxList.stream().map(WorkflowParser::parse).toList();
        broker.submitWorkflowList(workflowList);

        WorkflowBroker broker1 = new WorkflowBroker(random, new HEFTPlanner(random, new Parameter()));
        List<Vm> vmList1 = ExperimentUtil.createVms(random, broker1.getId());
        broker1.submitGuestList(vmList1);
        List<Workflow> workflowList1 = daxList1.stream().map(WorkflowParser::parse).toList();
        broker1.submitWorkflowList(workflowList1);

        CloudSim.startSimulation();

        List<Job> cloudletReceivedList = broker.getCloudletReceivedList();
        ExperimentUtil.printSimResult(cloudletReceivedList, broker.getName());
        ExperimentUtil.generateSimData(cloudletReceivedList, broker.getName());

        List<Job> cloudletReceivedList1 = broker1.getCloudletReceivedList();
        ExperimentUtil.printSimResult(cloudletReceivedList1, broker1.getName());
        ExperimentUtil.generateSimData(cloudletReceivedList1, broker1.getName());


        String className = new Object() {
        }.getClass().getEnclosingClass().getSimpleName();
        System.out.println(className + " task " + (System.currentTimeMillis() - send) / 1000.0 + "s");
    }
}
