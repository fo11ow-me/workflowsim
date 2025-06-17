
package com.qiujie.example;

import ch.qos.logback.classic.Level;
import com.qiujie.entity.Parameter;
import com.qiujie.starter.ExperimentStarter;
import com.qiujie.starter.SimStarter;
import com.qiujie.planner.*;
import com.qiujie.util.ExperimentUtil;
import com.qiujie.util.Log;
import org.cloudbus.cloudsim.distributions.UniformDistr;

import java.util.List;

import static com.qiujie.Constants.MAX_RETRY_COUNT;


/**
 * Algorithm comparison
 *
 * @author qiujie
 */
public class Example05 extends ExperimentStarter {


    public static void main(String[] args) {
        new Example05();
    }


    @Override
    public void run() throws Exception {
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

        MAX_RETRY_COUNT = 10;

        List<SimStarter> simStarterList = List.of(
                new SimStarter(new UniformDistr(0, 1, seed), daxPathList, HEFTPlanner.class, new Parameter())
                , new SimStarter(new UniformDistr(0, 1, seed), daxPathList, RandomPlanner.class, new Parameter())
        );

        simStarterList.forEach(SimStarter::printSimResult);
        simStarterList.forEach(SimStarter::generateSimGanttData);

        ExperimentUtil.printExperimentResult(simStarterList);
    }
}
