
package com.qiujie.example;

import ch.qos.logback.classic.Level;
import com.qiujie.comparator.DepthComparator;
import com.qiujie.comparator.JobNumComparator;
import com.qiujie.comparator.LengthComparator;
import com.qiujie.aop.ClockModifier;
import com.qiujie.entity.Parameter;
import com.qiujie.starter.SimStarter;
import com.qiujie.planner.HEFTPlanner;
import com.qiujie.util.ExperimentUtil;
import com.qiujie.util.Log;
import org.cloudbus.cloudsim.distributions.UniformDistr;

import java.util.List;

import static com.qiujie.Constants.*;


/**
 * @author QIUJIE
 * <p>
 * different workflow sort strategy
 */
public class Example04 {
    public static void main(String[] args) throws Exception {
        long seed = System.currentTimeMillis();
        ClockModifier.modifyClockMethod();
        org.cloudbus.cloudsim.Log.disable();
        Log.setLevel(Level.ALL);
        List<String> daxPathList = List.of(
//                "data/dax/Inspiral_1000.xml",
//                "data/dax/Inspiral_100.xml",
//                "data/dax/Epigenomics_997.xml",
//                "data/dax/Sipht_30.xml",
//                "data/dax/Sipht_100.xml",
//                "data/dax/Montage_1000.xml"
//                "data/dax/CyberShake_100.xml",
//                "data/dax/CyberShake_30.xml",
                "data/dax/CyberShake_200.xml",
//                "data/dax/SIPHT_200.xml",
//                "data/dax/Epigenomics_46.xml",
//                "data/dax/Inspiral_50.xml",
//                "data/dax/Epigenomics_24.xml",
//                "data/dax/Montage_200.xml",
//                "data/dax/Montage_1000.xml",
                "data/dax/Montage_100.xml");


        VMS = 50;

        LENGTH_FACTOR = 1e5;



        SimStarter simStarter = new SimStarter(new UniformDistr(0, 1, seed), daxPathList, HEFTPlanner.class,new Parameter().setWorkflowComparator(DepthComparator.class).setAscending(true));
        Log.setLevel(Level.OFF);
        SimStarter simStarter1 = new SimStarter(new UniformDistr(0, 1, seed), daxPathList, HEFTPlanner.class, new Parameter().setWorkflowComparator(JobNumComparator.class).setAscending(true));
        SimStarter simStarter2 = new SimStarter(new UniformDistr(0, 1, seed), daxPathList, HEFTPlanner.class, new Parameter().setWorkflowComparator(LengthComparator.class).setAscending(true));
        SimStarter simStarter3 = new SimStarter(new UniformDistr(0, 1, seed), daxPathList, HEFTPlanner.class, new Parameter().setWorkflowComparator(LengthComparator.class).setAscending(false));
        SimStarter simStarter4 = new SimStarter(new UniformDistr(0, 1, seed), daxPathList, HEFTPlanner.class, new Parameter().setWorkflowComparator(DepthComparator.class).setAscending(false));
        SimStarter simStarter5 = new SimStarter(new UniformDistr(0, 1, seed), daxPathList, HEFTPlanner.class, new Parameter().setWorkflowComparator(JobNumComparator.class).setAscending(false));
        List<SimStarter> simStarterList = List.of(simStarter, simStarter1, simStarter2, simStarter3, simStarter4, simStarter5);

        simStarter.printSimResult();

        String className = new Object() {
        }.getClass().getEnclosingClass().getSimpleName();

        ExperimentUtil.printExperimentResult(simStarterList, className);

        System.out.println(className + " run " + (System.currentTimeMillis() - seed) / 1000.0 + "s");
    }
}
