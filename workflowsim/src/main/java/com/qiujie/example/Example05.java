
package com.qiujie.example;

import ch.qos.logback.classic.Level;
import com.qiujie.comparator.DefaultComparator;
import com.qiujie.comparator.LengthComparator;
import com.qiujie.entity.Parameter;
import com.qiujie.entity.SimParameter;
import com.qiujie.starter.ExperimentStarter;
import com.qiujie.planner.*;
import com.qiujie.util.Log;

import java.util.List;

import static com.qiujie.Constants.MAX_RETRY_COUNT;


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
                "CyberShake_25"
                , "CyberShake_50"
                , "CyberShake_100"
//                , "CyberShake_200"
//                , "CyberShake_400"
//                , "CyberShake_500"
//                , "Montage_25"
//                , "Montage_50"
//                , "Montage_100"
//                , "Montage_200"
//                , "Montage_400"
//                , "Montage_500"
        );

        addParam(new SimParameter(name, seed, daxList, RandomPlanner.class, new Parameter().setWorkflowComparator(DefaultComparator.class).setAscending(true)));
        addParam(new SimParameter(name, seed, daxList, RandomPlanner.class, new Parameter().setWorkflowComparator(LengthComparator.class).setAscending(false)));
        addParam(new SimParameter(name, seed, daxList, HEFTPlanner.class, new Parameter().setWorkflowComparator(DefaultComparator.class).setAscending(true)));
    }
}
