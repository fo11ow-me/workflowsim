package com.qiujie;

import ch.qos.logback.classic.Level;
import com.qiujie.comparator.*;
import com.qiujie.entity.Cpu;
import com.qiujie.enums.JobSequenceStrategyEnum;
import generator.app.*;

import java.io.File;
import java.util.List;

import static com.qiujie.util.ExperimentUtil.getCpuList;

public class Constants {


    public static final String DAX_DIR = System.getProperty("user.dir") + File.separator + "data" + File.separator + "dax" + File.separator;
    public static final String SIM_DIR = System.getProperty("user.dir") + File.separator + "data" + File.separator + "sim" + File.separator;
    public static final String EXPERIMENT_DIR = System.getProperty("user.dir") + File.separator + "data" + File.separator + "experiment" + File.separator;
    public static final String PARAM_DIR = System.getProperty("user.dir") + File.separator + "tmp" + File.separator + "param" + File.separator;
    public static final String RESULT_DIR = System.getProperty("user.dir") + File.separator + "tmp" + File.separator + "result" + File.separator;

    public static final int RESERVED_CORES = 4;  // Number of CPU cores to reserve for system tasks
    public static final int SIM_TIMEOUT_MINUTES = 20; // Timeout for each simulation in minutes
    public static final int BATCH_SIZE = 1000;

    public static final Level LEVEL = Level.ERROR;

    public static final int USERS = 1;
    public static final boolean TRACE_FLAG = false;
    public static final double MIN_TIME_BETWEEN_EVENTS = 0.0001;

    public static final int DCS = 10;
    public static final int DC_HOSTS = 10;
    public static final double DC_SCHEDULING_INTERVAL = 0;

    /**
     * Electricity price matrix for data centers (unit: $/kWh)
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

    public static final int HOST_PES = 2;
    public static final double HOST_MIPS = 2000;
    public static final int HOST_RAM = 4096; // MEGA
    public static final long HOST_BW = 10000;
    public static final long HOST_STORAGE = 1000000; // MEGA

    public static final List<Cpu> CPU_LIST = getCpuList();

    public static int VMS = 200;
    public static final int VM_RAM = 512; // MEGA
    public static final long VM_BW = 1000;
    public static final long VM_SIZE = 10000; // image size (Megabyte)

    // Transient failure rate of the virtual machine at maximum operating frequency
    public static double λ = 1e-5;
    // Measures the sensitivity of transient fault rate to frequency scaling
    public static final int SR = 2;

    public static final double INTER_BANDWIDTH = 1e6;
    public static final double INTRA_BANDWIDTH = 10 * INTER_BANDWIDTH;

    // data
    public static List<Class<? extends Application>> APP_LIST = List.of(Montage.class, Genome.class);
    public static List<Integer> JOB_NUM_LIST = List.of(50, 100, 200, 400);
    public static int INSTANCE_NUM = 5;
    public static final int REPEAT_TIMES = 5;

    //  List of parameter ranges
    public static final List<Class<? extends WorkflowComparatorInterface>> WORKFLOW_COMPARATOR_LIST = List.of(DefaultComparator.class, DepthComparator.class, JobNumComparator.class, LengthComparator.class);
    public static final List<Boolean> ASCENDING_LIST = List.of(true, false);
    public static final List<Double> DEADLINE_FACTOR_LIST = List.of(0.2, 0.4, 0.6, 0.8);
    public static final List<Double> RELIABILITY_FACTOR_LIST = List.of(0.92, 0.94, 0.96, 0.98);
    public static final List<JobSequenceStrategyEnum> JOB_SEQUENCE_STRATEGY_LIST = List.of(JobSequenceStrategyEnum.DEADLINE, JobSequenceStrategyEnum.UPWARD_RANK, JobSequenceStrategyEnum.DOWNWARD_RANK);
    public static final List<Double> NEIGHBORHOOD_FACTOR_LIST = List.of(0.2, 0.4, 0.6, 0.8);
//    public static final List<Double> SLACK_TIME_FACTOR_LIST = List.of(0.2, 0.4, 0.6, 0.8);

    // default parameter values
    public static Class<? extends WorkflowComparatorInterface> WORKFLOW_COMPARATOR = WORKFLOW_COMPARATOR_LIST.getFirst();
    public static boolean ASCENDING = ASCENDING_LIST.getFirst();
    public static double DEADLINE_FACTOR = DEADLINE_FACTOR_LIST.getFirst();
    public static double RELIABILITY_FACTOR = RELIABILITY_FACTOR_LIST.getLast();
    public static JobSequenceStrategyEnum JOB_SEQUENCE_STRATEGY = JOB_SEQUENCE_STRATEGY_LIST.getFirst();
    public static double NEIGHBORHOOD_FACTOR = NEIGHBORHOOD_FACTOR_LIST.getFirst();
    public static double SLACK_TIME_FACTOR = 1;

    public static final int MAX_RETRY_COUNT = Integer.MAX_VALUE;

    public static final boolean ENABLE_DVFS = false;
    public static final boolean ENABLE_SIM_DATA = false;

    public static final double α = 110.0;
    public static final double β = 0.9;
    public static final double γ = 1.2;

}
