
package com.qiujie.example;

import ch.qos.logback.classic.Level;
import com.qiujie.comparator.DefaultComparator;
import com.qiujie.comparator.LengthComparator;
import com.qiujie.entity.Param;
import com.qiujie.entity.SimParam;
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
        setLevel(Level.WARN);
        List<String> daxList = List.of(
                "Montage_50"
                , "Montage_100"
                , "Montage_200"
                , "Montage_400"
        );

        addParam(new SimParam(seed, daxList, RandomPlanner.class, new Param().setWorkflowComparator(DefaultComparator.class).setAscending(true)));
        addParam(new SimParam(seed, daxList, RandomPlanner.class, new Param().setWorkflowComparator(LengthComparator.class).setAscending(false)));
        addParam(new SimParam(seed, daxList, HEFTPlanner.class, new Param().setWorkflowComparator(DefaultComparator.class).setAscending(true)));
    }
}
