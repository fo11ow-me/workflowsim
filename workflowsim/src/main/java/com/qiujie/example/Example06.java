
package com.qiujie.example;

import ch.qos.logback.classic.Level;
import com.qiujie.Constants;
import com.qiujie.comparator.WorkflowComparatorInterface;
import com.qiujie.entity.Parameter;
import com.qiujie.entity.Result;
import com.qiujie.entity.SimParameter;
import com.qiujie.enums.JobSequenceStrategyEnum;
import com.qiujie.planner.HEFTPlanner;
import com.qiujie.planner.MyPlanner;
import com.qiujie.starter.ExperimentStarter;
import com.qiujie.starter.SimStarter;
import com.qiujie.util.ExperimentUtil;
import com.qiujie.util.Log;
import org.cloudbus.cloudsim.distributions.UniformDistr;

import java.util.ArrayList;
import java.util.List;


/**
 * @author qiujie
 * <p>
 * two-way ANOVA
 */
public class Example06 extends ExperimentStarter {

    public static void main(String[] args) {
        new Example06();
    }


    @Override
    protected void init() {
        setLevel(Level.WARN);
        List<String> daxList = List.of(
                "CyberShake_25"
                , "CyberShake_50"
                , "CyberShake_100"
                , "CyberShake_200"
                , "Montage_25"
                , "Montage_50"
                , "Montage_100"
                , "Montage_400"
        );
        for (Class<? extends WorkflowComparatorInterface> workflowComparator : Constants.WORKFLOW_COMPARATOR_LIST) {
            for (Boolean ascending : Constants.ASCENDING_LIST) {
                for (int i = 0; i < Constants.REPEAT_TIMES; i++) {
                    addParam(new SimParameter(name, seed + i, daxList, HEFTPlanner.class,
                            new Parameter().setWorkflowComparator(workflowComparator).setAscending(ascending)));
                }
            }
        }
    }
}
