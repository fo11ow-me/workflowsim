
package com.qiujie.example;

import ch.qos.logback.classic.Level;
import com.qiujie.comparator.DepthComparator;
import com.qiujie.comparator.JobNumComparator;
import com.qiujie.comparator.LengthComparator;
import com.qiujie.aop.ClockModifier;
import com.qiujie.entity.Param;
import com.qiujie.entity.Result;
import com.qiujie.entity.SimParam;
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
                "Genome_50",
                "Montage_100");
        VMS = 50;
        SimStarter simStarter = new SimStarter();
        Result result = simStarter.start(new SimParam(seed, daxList, RandomPlanner.class, new Param().setWorkflowComparator(DepthComparator.class).setAscending(true)));
//        Log.setLevel(Level.OFF);
        Result result1 = simStarter.start(new SimParam(seed, daxList, RandomPlanner.class, new Param().setWorkflowComparator(LengthComparator.class).setAscending(true)));
        Result result2 = simStarter.start(new SimParam(seed, daxList, HEFTPlanner.class, new Param().setWorkflowComparator(LengthComparator.class).setAscending(false)));
        Result result3 = simStarter.start(new SimParam(seed, daxList, HEFTPlanner.class, new Param().setWorkflowComparator(JobNumComparator.class).setAscending(false)));
        List<Result> resultList = List.of(result, result1, result2, result3);
        ExperimentUtil.printExperimentResult(resultList);
        ExperimentUtil.generateExperimentData(resultList, name);
        System.out.println(name + " take " + (System.currentTimeMillis() - seed) / 1000.0 + "s");
    }
}
