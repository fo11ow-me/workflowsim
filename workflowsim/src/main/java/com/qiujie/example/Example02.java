
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
import org.cloudbus.cloudsim.Vm;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.distributions.ContinuousDistribution;
import org.cloudbus.cloudsim.distributions.UniformDistr;

import java.util.Calendar;
import java.util.List;

import static com.qiujie.Constants.*;


/**
 * schedule multiple workflows
 */
public class Example02 {
    public static void main(String[] args) throws Exception {
        long send = System.currentTimeMillis();
        ContinuousDistribution random = new UniformDistr(0, 1, send);
        ClockModifier.modifyClockMethod();
        org.cloudbus.cloudsim.Log.disable();
        CloudSim.init(USERS, Calendar.getInstance(), TRACE_FLAG);
        Log.setLevel(Level.INFO);
        List<String> daxList = List.of(
                "Inspiral_1000",
                "Inspiral_100",
                "Inspiral_50",
//                "Epigenomics_997",
                "Sipht_30",
//                "Montage_1000",
//                "CyberShake_100",
//                "CyberShake_30",
//                "Epigenomics_46",
                "Epigenomics_24",
                "Montage_100",
//                "Montage_1000",
                "Montage_50"
        );
        // create datacenters
        ExperimentUtil.createDatacenters();
        // create broker
        WorkflowBroker broker = new WorkflowBroker(random , new HEFTPlanner(random,new Parameter()));
        // submit vms
        List<Vm> vmList = ExperimentUtil.createVms(random,broker.getId());
        broker.submitGuestList(vmList);
        // submit workflows
        List<Workflow> workflowList = daxList.stream().map(WorkflowParser::parse).toList();
        broker.submitWorkflowList(workflowList);
        // start simulation
        CloudSim.startSimulation();
        List<Job> cloudletReceivedList = broker.getCloudletReceivedList();
        // plot dc electricity price chart
//            ExperimentUtil.plotElecPriceChart(datacenterList);
        // print result
        ExperimentUtil.printSimResult(cloudletReceivedList);
        // generate gantt chart data

        String className = new Object() {
        }.getClass().getEnclosingClass().getSimpleName();

        ExperimentUtil.generateSimData(cloudletReceivedList, className + "_" + broker.getName());
        ExperimentUtil.generateSimData(cloudletReceivedList, className + "_" + broker.getName());

        System.out.println(className + " task " + (System.currentTimeMillis() - send) / 1000.0 + "s");
    }
}
