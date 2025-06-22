
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
    protected void init() {
        Log.setLevel(Level.DEBUG);
        List<String> daxPathList = List.of(
                "data/dax/CyberShake_25.xml"
                , "data/dax/CyberShake_50.xml"
                , "data/dax/CyberShake_100.xml"
                , "data/dax/CyberShake_200.xml"
                , "data/dax/CyberShake_400.xml"
                , "data/dax/CyberShake_500.xml"
                , "data/dax/Montage_25.xml"
                , "data/dax/Montage_50.xml"
                , "data/dax/Montage_100.xml"
                , "data/dax/Montage_200.xml"
                , "data/dax/Montage_400.xml"
                , "data/dax/Montage_500.xml"
        );

        addParam(new SimParameter(name, seed, daxPathList, RandomPlanner.class, new Parameter().setWorkflowComparator(DefaultComparator.class).setAscending(true)));
        addParam(new SimParameter(name, seed, daxPathList, RandomPlanner.class, new Parameter().setWorkflowComparator(LengthComparator.class).setAscending(false)));
        addParam(new SimParameter(name, seed, daxPathList, HEFTPlanner.class, new Parameter().setWorkflowComparator(DefaultComparator.class).setAscending(true)));
    }
}
