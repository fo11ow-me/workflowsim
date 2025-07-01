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
        // Get unique types of VMs, retaining only one instance for each type
        List<Vm> vmList = getVmList().stream()
                .collect(Collectors.toMap(
                        vm -> ((DvfsVm) vm).getType(),  // Use VM type as the key
                        Function.identity(),            // Use the VM instance as the value
                        (existing, replacement) -> existing // Keep the first VM if duplicate types exist
                ))
                .values()
                .stream()
                .toList();

        // Initialize the mapping for each task's execution time and reliability under different frequencies
        execTimeMap = new HashMap<>();
        reliabilityMap = new HashMap<>();

        double sumLogReliability = 0.0; // Sum of the logarithms of each task's reliability

        // Iterate over all tasks in the workflow
        for (Job job : workflow.getJobList()) {
            execTimeMap.put(job, new HashMap<>());
            reliabilityMap.put(job, new HashMap<>());
            double maxSubReliability = 0; // Store the maximum reliability for the current task

            // Iterate over all unique VM types
            for (Vm vm : vmList) {
                DvfsVm dvfsVm = (DvfsVm) vm; // Cast to DvfsVm to access frequency list

                // Iterate over all supported frequency-voltage pairs of the VM
                for (Fv fv : dvfsVm.getFvList()) {
                    // Calculate the execution time at the current frequency: task length / MIPS
                    double executionTime = job.getLength() / fv.getMips();
                    execTimeMap.get(job).put(fv, executionTime); // Store execution time

                    // Calculate reliability based on failure rate (lambda) and execution time using exponential decay model
                    double reliability = ExperimentUtil.calculateReliability(fv.getLambda(), executionTime);
                    reliabilityMap.get(job).put(fv, reliability); // Store reliability

                    // Update the maximum reliability found for the current task
                    maxSubReliability = Math.max(maxSubReliability, reliability);
                }
            }

            // Protect against extreme small reliability values to avoid log(0) or negative infinity errors
            double safeReliability = Math.max(maxSubReliability, 1e-12);

            // Accumulate the logarithm of the maximum reliability for each task
            sumLogReliability += Math.log(safeReliability);
        }

        int jobNum = workflow.getJobNum(); // Total number of tasks in the workflow

        // Calculate the average log reliability (geometric mean in log space)
        double avgLogReliability = sumLogReliability / jobNum;

        // Convert the average log reliability back to the geometric mean reliability
        double smoothReliability = Math.exp(avgLogReliability);

        // Set the workflow's reliability goal:
        // Apply the reliability factor (raised to the power of task count) as a weight
        // Multiply by the smoothed reliability to balance precision and stability
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
