package com.qiujie;

import ch.qos.logback.classic.Level;
import com.qiujie.comparator.*;
import com.qiujie.enums.JobSequenceStrategyEnum;
import generator.app.Application;
import generator.app.CyberShake;
import generator.app.Montage;
import org.cloudbus.cloudsim.distributions.ContinuousDistribution;
import org.slf4j.Marker;

import java.util.List;

public class Constants {

    public static String SIM_NAME = "Sim";

    public static final int USERS = 1;
    public static final boolean TRACE_FLAG = false;

    public static final int DCS = 10;
    public static final int DC_HOSTS = 10;
    public static double DC_SCHEDULING_INTERVAL = 0;

    /**
     * Electricity price matrix for data centers (unit: yuan/kWh)
     * Structure description:
     * - Outer List contains 10 data centers
     * - Inner List contains 12-hour electricity prices for each data center
     * Example: ELEC_PRICES.get(0).get(0) represents the electricity price of the 1st hour in the 1st data center
     */
    public static final List<List<Double>> ELEC_PRICES = List.of(
            List.of(0.10, 0.09, 0.11, 0.11, 0.09, 0.09, 0.10, 0.10, 0.15, 0.15, 0.15, 0.13)
            , List.of(0.11, 0.12, 0.12, 0.12, 0.12, 0.22, 0.22, 0.22, 0.22, 0.22, 0.19, 0.18)
            , List.of(0.15, 0.10, 0.11, 0.11, 0.19, 0.22, 0.19, 0.11, 0.12, 0.07, 0.08, 0.10)
            , List.of(0.07, 0.08, 0.09, 0.07, 0.13, 0.14, 0.13, 0.13, 0.12, 0.12, 0.09, 0.09)
            , List.of(0.13, 0.13, 0.15, 0.15, 0.17, 0.19, 0.21, 0.21, 0.21, 0.18, 0.18, 0.17)
            , List.of(0.18, 0.19, 0.18, 0.18, 0.18, 0.21, 0.22, 0.25, 0.25, 0.16, 0.16, 0.17)
            , List.of(0.24, 0.24, 0.19, 0.15, 0.15, 0.16, 0.16, 0.08, 0.08, 0.08, 0.08, 0.16)
            , List.of(0.21, 0.21, 0.21, 0.21, 0.16, 0.16, 0.21, 0.21, 0.21, 0.21, 0.21, 0.19)
            , List.of(0.19, 0.18, 0.18, 0.18, 0.19, 0.19, 0.18, 0.18, 0.18, 0.18, 0.19, 0.19)
            , List.of(0.08, 0.08, 0.09, 0.09, 0.13, 0.13, 0.13, 0.13, 0.11, 0.11, 0.09, 0.09));

    public static final String ARCH = "x86";
    public static final String OS = "Linux";
    public static final String VMM = "Xen";
    public static final double TIME_ZONE = 10.0;
    public static final double COST_PER_SEC = 3.0; // the cost of using processing in this resource
    public static final double COST_PER_MEM = 0.05; // the cost of using memory in this resource
    public static final double COST_PER_STORAGE = 0.001; // the cost of using storage in this resource
    public static final double COST_PER_BW = 0.1; // the cost of using bw in this resource

    public static final int HOST_RAM = 4096; // MEGA
    public static final long HOST_BW = 10000;
    public static final long HOST_STORAGE = 1000000; // MEGA

    public static int VMS = 300;

    public static final int VM_RAM = 512; // MEGA
    public static final long VM_BW = 1000;
    public static final long VM_SIZE = 10000; // image size (Megabyte)

    // Transient failure rate of the virtual machine at maximum operating frequency
    public static final double λ = 1e-6;
    // Measures the sensitivity of transient fault rate to frequency scaling
    public static final int SR = 2;

    public static final double INTER_BANDWIDTH = 1e6;
    public static final double INTRA_BANDWIDTH = 10 * INTER_BANDWIDTH;

    public static double LENGTH_FACTOR = 1e3;

    public static ContinuousDistribution RANDOM;
    // data
    public static List<Class<? extends Application>> APP_LIST = List.of(Montage.class, CyberShake.class);
    public static List<Integer> JOB_NUM_LIST = List.of(50, 100, 200, 400, 800);
    public static List<Integer> INSTANCE_NUM_LIST = List.of(20, 40, 60, 80, 100);
    //  List of parameter ranges
    public static final List<Class<? extends WorkflowComparatorInterface>> WORKFLOW_COMPARATOR_LIST = List.of(DefaultComparator.class, DepthComparator.class, JobNumComparator.class, LengthComparator.class);
    public static final List<Boolean> ASCENDING_LIST = List.of(true, false);
    public static final List<Double> DEADLINE_FACTOR_LIST = List.of(0.2, 0.4, 0.6, 0.8);
    public static final List<Double> RELIABILITY_FACTOR_LIST = List.of(0.992, 0.994, 0.996, 0.998);
    public static final List<JobSequenceStrategyEnum> JOB_SEQUENCE_STRATEGY_LIST = List.of(JobSequenceStrategyEnum.UPWARD_RANK, JobSequenceStrategyEnum.DOWNWARD_RANK, JobSequenceStrategyEnum.DEADLINE);
    public static final List<Double> NEIGHBORHOOD_FACTOR_LIST = List.of(0.2, 0.4, 0.6, 0.8);
    public static final List<Double> SLACK_TIME_FACTOR_LIST = List.of(0.2, 0.4, 0.6, 0.8);
    // // default parameter values
    public static Class<? extends WorkflowComparatorInterface> WORKFLOW_COMPARATOR = DefaultComparator.class;
    public static boolean ASCENDING = true;
    public static double DEADLINE_FACTOR = 0.2;
    public static double RELIABILITY_FACTOR = 0.998;
    public static JobSequenceStrategyEnum JOB_SEQUENCE_STRATEGY = JobSequenceStrategyEnum.UPWARD_RANK;
    public static double NEIGHBORHOOD_FACTOR = 0.6;
    public static double SLACK_TIME_FACTOR = 0.8;

    public static final double ε = 1e-6;

    public static int MAX_RETRY_COUNT = 10;

    public static boolean ENABLE_STARTUP = true;

}
