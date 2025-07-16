
package com.qiujie.example;

import com.qiujie.comparator.DefaultComparator;
import com.qiujie.comparator.LengthComparator;
import com.qiujie.entity.Parameter;
import com.qiujie.entity.SimParameter;
import com.qiujie.starter.ExperimentStarter;
import com.qiujie.planner.*;

import java.util.List;


/**
 * parallel simulation
 *
 * @author qiujie
 */
public class Example05 extends ExperimentStarter {


    public static void main(String[] args) {
        new Example05();
    }


    @Override
    protected void addParams() {
        List<String> daxList = List.of(
                "Montage_50"
                , "Montage_100"
                , "Montage_200"
                , "Montage_400"
        );

        addParam(new SimParameter(seed, daxList, RandomPlanner.class, new Parameter().setWorkflowComparator(DefaultComparator.class).setAscending(true)));
        addParam(new SimParameter(seed, daxList, RandomPlanner.class, new Parameter().setWorkflowComparator(LengthComparator.class).setAscending(false)));
        addParam(new SimParameter(seed, daxList, HEFTPlanner.class, new Parameter().setWorkflowComparator(DefaultComparator.class).setAscending(true)));
    }
}
