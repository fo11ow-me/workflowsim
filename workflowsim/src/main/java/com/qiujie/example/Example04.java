
package com.qiujie.example;

import ch.qos.logback.classic.Level;
import com.qiujie.comparator.DepthComparator;
import com.qiujie.comparator.LengthComparator;
import com.qiujie.aop.ClockModifier;
import com.qiujie.entity.Parameter;
import com.qiujie.entity.SimParameter;
import com.qiujie.planner.RandomPlanner;
import com.qiujie.starter.SimStarter;
import com.qiujie.planner.HEFTPlanner;
import com.qiujie.util.ExperimentUtil;
import com.qiujie.util.Log;

import java.util.List;

import static com.qiujie.Constants.*;


/**
 * simulation starter
 *
 * @author QIUJIE
 * <p>
 */
public class Example04 {
    public static void main(String[] args) throws Exception {
        String name = new Object() {
        }.getClass().getEnclosingClass().getSimpleName();
        long seed = System.currentTimeMillis();
        ClockModifier.modifyClockMethod();
        org.cloudbus.cloudsim.Log.disable();
        Log.setLevel(Level.DEBUG);
        List<String> daxList = List.of(
                "Genome_50");
        VMS = 50;
        SimStarter simStarter = new SimStarter(new SimParameter(seed, daxList, RandomPlanner.class, new Parameter().setWorkflowComparator(DepthComparator.class).setAscending(true)));
//        Log.setLevel(Level.OFF);
        SimStarter simStarter1 = new SimStarter(new SimParameter(seed, daxList, RandomPlanner.class, new Parameter().setWorkflowComparator(LengthComparator.class).setAscending(true)));
        SimStarter simStarter2 = new SimStarter(new SimParameter(seed, daxList, RandomPlanner.class, new Parameter().setWorkflowComparator(LengthComparator.class).setAscending(false)));
        SimStarter simStarter3 = new SimStarter(new SimParameter(seed, daxList, RandomPlanner.class, new Parameter().setWorkflowComparator(LengthComparator.class).setAscending(true)));
        List<SimStarter> simStarterList = List.of(simStarter, simStarter1, simStarter2, simStarter3);
        ExperimentUtil.printExperimentResult(simStarterList.stream().map(SimStarter::getResult).toList());
        System.out.println(name + " run " + (System.currentTimeMillis() - seed) / 1000.0 + "s");
    }
}
