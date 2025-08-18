
package com.qiujie.example;

import com.qiujie.Constants;
import com.qiujie.comparator.WorkflowComparatorInterface;
import com.qiujie.entity.Param;
import com.qiujie.entity.SimParam;
import com.qiujie.planner.HEFTPlanner;
import com.qiujie.planner.RandomPlanner;
import com.qiujie.starter.ExperimentStarter;

import java.util.List;


/**
 * @author qiujie
 * <p>
 * algorithm comparison
 */
public class Example07 extends ExperimentStarter {

    public static void main(String[] args) {
        new Example07();
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
            for (int i = 0; i < Constants.REPEAT_TIMES; i++) {
                addParam(new SimParam(seed + i, daxList, HEFTPlanner.class,
                        new Param().setWorkflowComparator(workflowComparator)));
                addParam(new SimParam(seed + i, daxList, RandomPlanner.class,
                        new Param().setWorkflowComparator(workflowComparator)));
            }

        }
    }
}
