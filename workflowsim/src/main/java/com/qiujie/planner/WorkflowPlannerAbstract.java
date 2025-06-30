package com.qiujie.planner;

import com.qiujie.entity.*;
import com.qiujie.enums.JobSequenceStrategyEnum;
import com.qiujie.util.ExperimentUtil;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.cloudbus.cloudsim.Host;
import org.cloudbus.cloudsim.Vm;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.distributions.ContinuousDistribution;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.qiujie.Constants.*;

@Slf4j
public abstract class WorkflowPlannerAbstract {

    @Getter
    private final ContinuousDistribution random;

    @Getter
    private final Parameter parameter;

    @NonNull
    @Setter
    @Getter(AccessLevel.PROTECTED)
    private List<Workflow> workflowList;

    @NonNull
    @Setter
    @Getter(AccessLevel.PROTECTED)
    private List<Vm> vmList;


    // job schedule sequence
    @Getter
    private final List<Job> sequence;

    @Setter(AccessLevel.PROTECTED)
    @Getter(AccessLevel.PROTECTED)
    private Map<Vm, List<ExecWindow>> execWindowMap;

    @Setter
    @Getter
    private double elecCost;

    @Setter
    @Getter
    private double finishTime;

    @Getter
    private double runtime;


    public WorkflowPlannerAbstract(ContinuousDistribution random, Parameter parameter) {
        this.random = random;
        this.parameter = parameter;
        sequence = new ArrayList<>();
        execWindowMap = new HashMap<>();
        elecCost = 0;
        finishTime = 0;
    }


    public void start() {
        log.info("{}: {}: Starting planning...", CloudSim.clock(), this);
        long start = System.currentTimeMillis();
        try {
            run();
            long end = System.currentTimeMillis();
            this.runtime = (end - start) / 1000.0;
            log.info("{}: {}: Running {}s", CloudSim.clock(), this, this.runtime);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    protected abstract void run() throws Exception;


    /**
     * calculate predicted average predecessor data transfer time
     */
    protected Map<Job, Map<Job, Double>> calculateAvgPredecessorDataTransferTime(Workflow workflow) {
        Map<Job, Map<Job, Double>> avgPredecessorDataTransferTimeMap = new HashMap<>();
        int vmNum = getVmList().size();
        for (Job job : workflow.getJobList()) {
            avgPredecessorDataTransferTimeMap.put(job, new HashMap<>());
            for (Job parentJob : job.getParentList()) {
                double total = 0.0;
                for (Vm vm : getVmList()) {
                    for (Vm parentVm : getVmList()) {
                        double temp = ExperimentUtil.calculatePredecessorDataTransferTime(job, (Host) vm.getHost(), parentJob, (Host) parentVm.getHost());
                        total += temp;
                    }
                }
                double avgTime = total / (vmNum * vmNum);
                avgPredecessorDataTransferTimeMap.get(job).put(parentJob, avgTime);
            }
        }
        return avgPredecessorDataTransferTimeMap;
    }


    /**
     * find earliest finish time
     *
     * @param job
     * @param fv
     * @param readyTime
     * @param execTimeMap
     * @param occupySlot
     * @return
     */
    protected double findEFT(Job job, Fv fv, double readyTime, Map<Job, Map<Fv, Double>> execTimeMap, boolean occupySlot, Map<Vm, List<ExecWindow>> execWindowMap) {
        List<ExecWindow> execWindows = execWindowMap.computeIfAbsent(fv.getVm(), vm -> new ArrayList<>());
        double execTime = execTimeMap.get(job).get(fv);
        double eft = Double.MAX_VALUE;
        int insertPos = execWindows.size(); // default to append to the end
        // consider the first window
        if (!execWindows.isEmpty() && readyTime + execTime <= execWindows.getFirst().getStartTime()) {
            eft = readyTime + execTime;
            insertPos = 0;
        } else {
            // try to find a gap
            for (int k = 0; k < execWindows.size() - 1; k++) {
                double gapStart = Math.max(readyTime, execWindows.get(k).getFinishTime());
                double gapEnd = execWindows.get(k + 1).getStartTime();
                if (gapStart + execTime <= gapEnd) {
                    eft = gapStart + execTime;
                    insertPos = k + 1;
                    break;
                }
            }
            if (eft == Double.MAX_VALUE) {
                double lastWindowFinish = execWindows.isEmpty() ? readyTime : execWindows.getLast().getFinishTime();
                double lastFinish = Math.max(readyTime, lastWindowFinish);
                eft = lastFinish + execTime;
                insertPos = execWindows.size();
            }
        }
        if (occupySlot) {
            double startTime = eft - execTime;
            execWindows.add(insertPos, new ExecWindow(startTime, eft, job));
            if (readyTime - startTime > ε) {
                throw new IllegalStateException(String.format("Job #%d (insertPos %d): startTime %f is less than readyTime %f", job.getCloudletId(), insertPos, startTime, readyTime));
            }
            if (insertPos > 0) {
                ExecWindow lastWindow = execWindows.get(insertPos - 1);
                if (lastWindow.getFinishTime() - startTime > ε) {
                    throw new IllegalStateException(String.format("Time window overlap detected when inserting [%s - %s] into VM[%s] with existing [%s - %s] (Job %s)", startTime, eft, fv.getVm().getId(), lastWindow.getStartTime(), lastWindow.getFinishTime(), lastWindow.getJob().getCloudletId()));
                }
            }
        }
        return eft;
    }


    protected boolean isNotTopologicalOrder(List<Job> sequence) {
        // create a map to store the positions of each job in the sequence
        Map<Job, Integer> positionMap = new HashMap<>();
        for (int i = 0; i < sequence.size(); i++) {
            positionMap.put(sequence.get(i), i);
        }
        // check whether each job's parent's position in the sequence is before the current job's position
        for (Job job : sequence) {
            for (Job parent : job.getParentList()) {
                // If a parent is not in the sequence, return true (invalid sequence)
                if (!positionMap.containsKey(parent)) {
                    return true;
                }
                // If a parent appears after the job, return true (not topological order)
                if (positionMap.get(parent) > positionMap.get(job)) {
                    return true;
                }
            }
        }
        return false;
    }


    /**
     * calculate iPW(l) = PpW(l) * (1 - γ * sigmoid)
     * - PpW(l) = performance / power
     * - sigmoid = 1 / (1 + exp(-α / maxLoad * (currentLoad - β * maxLoad)))
     *
     * @param currentLoad
     * @param maxLoad
     * @param performance
     * @param power
     * @return
     */
    protected double calculateIPW(double currentLoad,
                                  double maxLoad,
                                  double performance,
                                  double power) {

        final double α = 110.0;
        final double β = 0.9;
        final double γ = 1.2;

        // 1. Calculate Performance per Watt (PpW)
        double ppw = performance / power;
        // 2. Compute the exponent for sigmoid
        double exponent = -(α / maxLoad) * (currentLoad - β * maxLoad);
        // 3. Calculate sigmoid function
        double sigmoid = 1.0 / (1.0 + Math.exp(exponent));
        // 4. Compute penalty term
        double penaltyTerm = 1.0 - γ * sigmoid;
        // 5. Final score
        return ppw * penaltyTerm;
    }


    protected Map<Job, Map<Fv, Double>> execTimeMap;
    protected Map<Job, Map<Fv, Double>> reliabilityMap;

    /**
     * calculate predicted execution time and reliability
     *
     * @param workflow
     */
//    protected void calculateExecutionTimeAndReliability(Workflow workflow) {
//        // get unique vm types
//        List<Vm> vmList = getVmList().stream()
//                .collect(Collectors.toMap(
//                        vm -> ((DvfsVm) vm).getType(),
//                        Function.identity(),
//                        (existing, replacement) -> existing
//                ))
//                .values()
//                .stream()
//                .toList();
//
//        double maxReliability = 1;
//        double maxSubReliability;
//        execTimeMap = new HashMap<>();
//        reliabilityMap = new HashMap<>();
//        for (Job job : workflow.getJobList()) {
//            execTimeMap.put(job, new HashMap<>());
//            reliabilityMap.put(job, new HashMap<>());
//            maxSubReliability = 0;
//            for (Vm vm : vmList) {
//                DvfsVm dvfsVm = (DvfsVm) vm;
//                for (Fv fv : dvfsVm.getFvList()) {
//                    double executionTime = job.getLength() / fv.getMips();
//                    execTimeMap.get(job).put(fv, executionTime);
//                    double reliability = ExperimentUtil.calculateReliability(fv.getLambda(), executionTime); // smaller frequency, smaller reliability
//                    reliabilityMap.get(job).put(fv, reliability);
//                    maxSubReliability = Math.max(maxSubReliability, reliability);
//                }
//            }
//            maxReliability *= maxSubReliability; // To maximize reliability, you need to select the Fv with the smallest value of lambda/mips.
//        }
//        workflow.setReliGoal(Math.pow(getParameter().getReliabilityFactor(), workflow.getJobNum()) * maxReliability);
//    }


    protected void calculateExecutionTimeAndReliability(Workflow workflow) {
        // 获取不同类型的虚拟机，去除重复类型，只保留每种类型的一个实例
        List<Vm> vmList = getVmList().stream()
                .collect(Collectors.toMap(
                        vm -> ((DvfsVm) vm).getType(),  // 以虚拟机类型为key
                        Function.identity(),             // 虚拟机对象为value
                        (existing, replacement) -> existing // 有冲突时保留第一个
                ))
                .values()
                .stream()
                .toList();

        // 初始化存储每个任务对应不同频率的执行时间和可靠性映射表
        execTimeMap = new HashMap<>();
        reliabilityMap = new HashMap<>();

        double sumLogReliability = 0.0; // 用于累计所有任务可靠性的对数和（log乘积 = 和）

        // 遍历工作流的所有任务
        for (Job job : workflow.getJobList()) {
            execTimeMap.put(job, new HashMap<>());
            reliabilityMap.put(job, new HashMap<>());
            double maxSubReliability = 0; // 用于存储当前任务的最大可靠性

            // 遍历去重后的虚拟机列表
            for (Vm vm : vmList) {
                DvfsVm dvfsVm = (DvfsVm) vm; // 强制类型转换，方便获取频率列表
                // 遍历该虚拟机支持的所有频率（频率-电压对）
                for (Fv fv : dvfsVm.getFvList()) {
                    // 计算任务在当前频率下的执行时间 = 任务长度 / 当前频率MIPS
                    double executionTime = job.getLength() / fv.getMips();
                    execTimeMap.get(job).put(fv, executionTime); // 记录执行时间

                    // 计算当前频率下任务的可靠性 (基于失效率lambda和执行时间的指数衰减模型)
                    double reliability = ExperimentUtil.calculateReliability(fv.getLambda(), executionTime);
                    reliabilityMap.get(job).put(fv, reliability); // 记录可靠性

                    // 更新当前任务所有频率的最大可靠性值
                    maxSubReliability = Math.max(maxSubReliability, reliability);
                }
            }

            // 防止 maxSubReliability 极端小导致 log(0) 或负无穷错误，做安全下限保护
            double safeReliability = Math.max(maxSubReliability, 1e-12);

            // 累加所有任务最大可靠性的对数（乘积转为求和）
            sumLogReliability += Math.log(safeReliability);
        }

        int jobNum = workflow.getJobNum(); // 任务数量

        // 计算平均对数可靠性（即总对数和除以任务数，变成几何平均的对数）
        double avgLogReliability = sumLogReliability / jobNum;

        // 将对数均值转换回原始可靠性值（几何平均可靠性）
        double smoothReliability = Math.exp(avgLogReliability);

        // 设置可靠性目标：
        // 用参数中的 ReliabilityFactor 的 jobNum 次方乘以平滑后的可靠性
        // 保留了原有可靠性因子的权重，同时平滑了多个任务的乘积计算问题
        workflow.setReliGoal(Math.pow(getParameter().getReliabilityFactor(), jobNum) * smoothReliability);
    }



    protected List<Job> constructInitialJobSequence(Workflow workflow) {
        JobSequenceStrategyEnum jobSequenceStrategy = getParameter().getJobSequenceStrategy();
        List<Job> initialSequence;
        if (jobSequenceStrategy == JobSequenceStrategyEnum.DEPTH) {
            initialSequence = workflow.getJobList().stream().sorted(Comparator.comparingDouble(Job::getDepth)).toList();
        } else if (jobSequenceStrategy == JobSequenceStrategyEnum.LENGTH_ASC) {
            initialSequence = workflow.getJobList().stream().sorted(Comparator.comparingDouble(Job::getLength)).toList();
        } else if (jobSequenceStrategy == JobSequenceStrategyEnum.LENGTH_DESC) {
            initialSequence = workflow.getJobList().stream().sorted(Comparator.comparingDouble(Job::getLength).reversed()).toList();
        } else {
            initialSequence = workflow.getJobList();
        }
        return initialSequence;
    }

    @Override
    public String toString() {
        return ExperimentUtil.getPrefixFromClassName(getClass().getName()) + parameter;
    }
}
