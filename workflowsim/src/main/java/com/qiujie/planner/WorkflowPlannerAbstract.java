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
    private final Param param;

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


    public WorkflowPlannerAbstract(ContinuousDistribution random, Param param) {
        this.random = random;
        this.param = param;
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
            log.info("{}: {}: Finished in {}s", CloudSim.clock(), this, this.runtime);
        } catch (Exception e) {
            log.error("{}: {}: ❌  Planning failed", CloudSim.clock(), this, e);
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
            Map<Job, Double> map = new HashMap<>();
            for (Job parentJob : job.getParentList()) {
                double total = 0.0;
                for (Vm vm : getVmList()) {
                    for (Vm parentVm : getVmList()) {
                        double temp = ExperimentUtil.calculatePredecessorDataTransferTime(job, (Host) vm.getHost(), parentJob, (Host) parentVm.getHost());
                        total += temp;
                    }
                }
                double avgTime = total / (vmNum * vmNum);
                map.put(parentJob, avgTime);
            }
            avgPredecessorDataTransferTimeMap.put(job, map);
        }
        return avgPredecessorDataTransferTimeMap;
    }


    protected ExecWindow findExecWindow(Job job, Fv fv, double readyTime, Map<Vm, List<ExecWindow>> execWindowMap) {
        List<ExecWindow> execWindowList = execWindowMap.computeIfAbsent(fv.getVm(), vm -> new ArrayList<>());
        double execTime = execTimeMap.get(job).get(fv);
        double finishTime = Double.MAX_VALUE;
        int insertPos = execWindowList.size(); // default to append to the end
        // consider the first window
        if (!execWindowList.isEmpty() && readyTime + execTime <= execWindowList.getFirst().getStartTime()) {
            finishTime = readyTime + execTime;
            insertPos = 0;
        } else {
            // try to find a gap
            for (int k = 0; k < execWindowList.size() - 1; k++) {
                double gapStart = Math.max(readyTime, execWindowList.get(k).getFinishTime());
                double gapEnd = execWindowList.get(k + 1).getStartTime();
                if (gapStart + execTime <= gapEnd) {
                    finishTime = gapStart + execTime;
                    insertPos = k + 1;
                    break;
                }
            }
            if (finishTime == Double.MAX_VALUE) {
                double lastWindowFinish = execWindowList.isEmpty() ? readyTime : execWindowList.getLast().getFinishTime();
                double lastFinish = Math.max(readyTime, lastWindowFinish);
                finishTime = lastFinish + execTime;
                insertPos = execWindowList.size();
            }
        }
        return new ExecWindow(finishTime - execTime, finishTime, insertPos, fv);
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

        double ppw = performance / power;
        double exponent = -(α / maxLoad) * (currentLoad - β * maxLoad);
        double sigmoid = 1.0 / (1.0 + Math.exp(exponent));
        double penaltyTerm = 1.0 - γ * sigmoid;
        return ppw * penaltyTerm;
    }


    protected Map<Job, Map<Fv, Double>> execTimeMap;
    protected Map<Job, Map<Fv, Double>> reliabilityMap;

    protected void calculateExecTimeAndReliability(Workflow workflow) {
        // Get unique types of VMs, retaining only one instance for each type
        List<DvfsVm> uniqueVmList = getVmList().stream()
                .map(vm -> (DvfsVm) vm)
                .collect(Collectors.toMap(
                        DvfsVm::getCpu,
                        Function.identity(),
                        (existing, replacement) -> existing
                ))
                .values()
                .stream()
                .toList();
        execTimeMap = new HashMap<>();
        reliabilityMap = new HashMap<>();
        double sumLogReliability = 0.0;
        for (Job job : workflow.getJobList()) {
            Map<Fv, Double> jobExecTimeMap = new HashMap<>();
            Map<Fv, Double> jobReliabilityMap = new HashMap<>();
            double maxSubReliability = 0.0;
            for (DvfsVm vm : uniqueVmList) {
                for (Fv fv : vm.getFvList()) {
                    double executionTime = job.getLength() / fv.getMips();
                    jobExecTimeMap.put(fv, executionTime);
                    double reliability = ExperimentUtil.calculateReliability(fv.getLambda(), executionTime);
                    jobReliabilityMap.put(fv, reliability);
                    maxSubReliability = Math.max(maxSubReliability, reliability);
                }
            }
            execTimeMap.put(job, jobExecTimeMap);
            reliabilityMap.put(job, jobReliabilityMap);
            // Accumulate the logarithm of the maximum reliability for each job
            sumLogReliability += Math.log(maxSubReliability);
        }
        int jobNum = workflow.getJobNum();
        // Calculate the average log reliability
        double avgLogReliability = sumLogReliability / jobNum;
        double smoothReliability = Math.exp(avgLogReliability);
        workflow.setReliGoal(Math.pow(getParam().getReliabilityFactor() * smoothReliability, jobNum));
    }


    protected List<Job> constructInitialJobSequence(Workflow workflow) {
        JobSequenceStrategyEnum jobSequenceStrategy = getParam().getJobSequenceStrategy();
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
        return ExperimentUtil.getPrefixFromClassName(getClass().getName()) + param;
    }
}
