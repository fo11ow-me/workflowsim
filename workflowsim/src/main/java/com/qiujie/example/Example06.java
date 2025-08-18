
package com.qiujie.example;

import ch.qos.logback.classic.Level;
import com.qiujie.Constants;
import com.qiujie.comparator.WorkflowComparatorInterface;
import com.qiujie.entity.Param;
import com.qiujie.entity.SimParam;
import com.qiujie.planner.HEFTPlanner;
import com.qiujie.starter.ExperimentStarter;
import generator.app.Montage;

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
    protected void addParams() {
        List<String> daxList = List.of(
                "Genome_100"
                , "Montage_50"
                , "Montage_200"
        );
        Constants.REPEAT_TIMES = 20;
        for (Class<? extends WorkflowComparatorInterface> workflowComparator : Constants.WORKFLOW_COMPARATOR_LIST) {
            for (Boolean ascending : Constants.ASCENDING_LIST) {
                for (int i = 0; i < Constants.REPEAT_TIMES; i++) {
                    addParam(new SimParam(seed + i, daxList, HEFTPlanner.class,
                            new Param().setWorkflowComparator(workflowComparator).setAscending(ascending)));
                }
            }
        }
    }
}
