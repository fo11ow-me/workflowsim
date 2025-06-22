package com.qiujie.planner;

import com.qiujie.comparator.WorkflowComparatorInterface;
import com.qiujie.core.WorkflowDatacenter;
import com.qiujie.entity.*;
import com.qiujie.util.ExperimentUtil;
import lombok.extern.slf4j.Slf4j;
import org.cloudbus.cloudsim.Host;
import org.cloudbus.cloudsim.Vm;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.distributions.ContinuousDistribution;

import java.util.*;


/**
 * Performance-effective and low-complexity task scheduling for heterogeneous computing
 */

@Slf4j
public class HEFTPlanner extends WorkflowPlannerAbstract {

    // record local data transfer time
    private Map<Job, Map<Vm, Double>> localDataTransferTimeMap;
    private Map<Job, Double> eftMap;
    private Map<Job, Double> upwardRankMap;

    public HEFTPlanner(ContinuousDistribution random, Parameter parameter) {
        super(random, parameter);
    }


    public void init() throws Exception {
        Class<? extends WorkflowComparatorInterface> clazz =
                (Class<? extends WorkflowComparatorInterface>) Class.forName(getParameter().getWorkflowComparator());
        WorkflowComparatorInterface comparatorInterface = clazz.getDeclaredConstructor().newInstance();
        Comparator<Workflow> workflowComparator = comparatorInterface.get(getParameter().isAscending());
        getWorkflowList().sort(workflowComparator);
    }

    /**
     * The main function
     */
    @Override
    public void run() throws Exception {
        init();
        for (Workflow workflow : getWorkflowList()) {
            calculateUpwardRank(workflow);
            calculateExecutionTimeAndReliability(workflow);
            allocateJobs(workflow);
        }
    }


    /**
     * calculate predicted average local data transfer time
     */
    private Map<Job, Double> calculateAvgLocalDataTransferTime(Workflow workflow) {
        localDataTransferTimeMap = new HashMap<>();
        Map<Job, Double> avgLocalDataTransferTimeMap = new HashMap<>();
        int vmNum = getVmList().size();
        for (Job job : workflow.getJobList()) {
            double total = 0.0;
            localDataTransferTimeMap.put(job, new HashMap<>());
            for (Vm vm : getVmList()) {
                double temp = ExperimentUtil.calculateLocalDataTransferTime(job, (Host) vm.getHost());
                localDataTransferTimeMap.get(job).put(vm, temp);
                total += temp;
            }
            double avgTime = total / vmNum;
            avgLocalDataTransferTimeMap.put(job, avgTime);
        }
        return avgLocalDataTransferTimeMap;
    }

    /**
     * calculate predicted upward rank
     *
     * @param avgLocalDataTransferTimeMap
     * @param avgPredecessorDataTransferTimeMap
     * @param avgMips
     * @param workflow
     */
    private double calculateUpwardRank(Map<Job, Double> avgLocalDataTransferTimeMap, Map<Job, Map<Job, Double>> avgPredecessorDataTransferTimeMap, double avgMips, Workflow workflow) {
        upwardRankMap = new HashMap<>();
        List<Job> list = workflow.getJobList().stream().sorted(Comparator.comparingDouble(Job::getDepth).reversed()).toList();
        double maxUpwardRank = 0;
        for (Job job : list) {
            double max = 0.0;
            for (Job child : job.getChildList()) {
                // check whether the upward rank of child job has been calculated
                if (!upwardRankMap.containsKey(child)) {
                    throw new IllegalStateException(String.format("Child job #%d upward rank has not been calculated!", child.getCloudletId()));
                }
                double temp = upwardRankMap.get(child) + avgPredecessorDataTransferTimeMap.get(child).get(job);
                max = Math.max(max, temp);
            }
            double upwardRank = max + avgLocalDataTransferTimeMap.get(job) + job.getLength() / avgMips;
            maxUpwardRank = Math.max(maxUpwardRank, upwardRank);
            upwardRankMap.put(job, upwardRank);
        }
        return maxUpwardRank;
    }


    private void calculateUpwardRank(Workflow workflow) {
        Map<Job, Double> avgLocalDataTransferTimeMap = calculateAvgLocalDataTransferTime(workflow);
        Map<Job, Map<Job, Double>> avgPredecessorDataTransferTimeMap = calculateAvgPredecessorDataTransferTime(workflow);
        double mips = getVmList().stream().mapToDouble(Vm::getMips).average().getAsDouble();
        double upwardRank = calculateUpwardRank(avgLocalDataTransferTimeMap, avgPredecessorDataTransferTimeMap, mips, workflow);
        double slackTime = upwardRank * getParameter().getDeadlineFactor();
        workflow.setDeadline(getFinishTime() + upwardRank + slackTime);
    }


    /**
     * allocate jobs
     */
    private void allocateJobs(Workflow workflow) {
        log.info("{}: {}: Starting planning workflow #{} {}, a total of {} Jobs...", CloudSim.clock(), this, workflow.getId(), workflow.getName(), workflow.getJobNum());
        List<Job> sequence = workflow.getJobList().stream().sorted(Comparator.comparingDouble(upwardRankMap::get).reversed()).toList();
        eftMap = new HashMap<>();
        Solution solution = new Solution();
        double elecCost = 0;
        double reliability = 1;
        double finishTime = 0;
        List<Job> scheduleSequence = new ArrayList<>();
        while (scheduleSequence.size() < sequence.size()) {
            for (Job job : sequence) {
                if (scheduleSequence.contains(job) || !new HashSet<>(scheduleSequence).containsAll(job.getParentList())) {
                    continue;
                }
                scheduleSequence.add(job);
                elecCost += allocateJob(job, solution, getExecWindowMap());
                reliability *= reliabilityMap.get(job).get(solution.getResult().get(job));
                finishTime = Math.max(finishTime, eftMap.get(job));
            }
        }
        if (isNotTopologicalOrder(scheduleSequence)) {
            throw new IllegalStateException("Not a topological order!");
        }
        solution.setSequence(scheduleSequence);
        solution.setElecCost(elecCost);
        solution.setReliability(reliability);
        solution.setFinishTime(finishTime);
        for (Job job : solution.getSequence()) {
            Fv fv = solution.getResult().get(job);
            job.setFv(fv);
            job.setGuestId(fv.getVm().getId());
            job.setVm(fv.getVm());
        }
        getSequence().addAll(solution.getSequence());
        setElecCost(getElecCost() + solution.getElecCost());
        setFinishTime(Math.max(getFinishTime(), solution.getFinishTime()));
        log.debug(String.format("%.2f: %s: %s: Best %s", CloudSim.clock(), this, workflow.getName(), solution));

    }


    /**
     * @return electric cost
     */
    private double allocateJob(Job job, Solution solution, Map<Vm, List<ExecWindow>> execWindowMap) {
        double beginTime = job.getParentList().isEmpty() ? 0 : job.getParentList().stream().mapToDouble(eftMap::get).min().getAsDouble();
        Fv bestFv = null;
        double bestReadyTime = Double.MAX_VALUE;
        double bestEft = Double.MAX_VALUE;
        for (Vm vm : getVmList()) {
            DvfsVm dvfsVm = (DvfsVm) vm;
            double max = 0;
            for (Job parent : job.getParentList()) {
                if (!eftMap.containsKey(parent)) {
                    throw new IllegalStateException(String.format("Parent job #%d eft has not been calculated!", parent.getCloudletId()));
                }
                max = Math.max(max, eftMap.get(parent) + ExperimentUtil.calculatePredecessorDataTransferTime(job, (Host) dvfsVm.getHost(), parent, (Host) solution.getResult().get(parent).getVm().getHost()));
            }
            double readyTime = max + localDataTransferTimeMap.get(job).get(dvfsVm);
            Fv fv = dvfsVm.getFvList().getFirst();
            double eft = findEFT(job, fv, readyTime, execTimeMap, false, execWindowMap);
            if (eft < bestEft) {
                bestFv = fv;
                bestEft = eft;
                bestReadyTime = readyTime;
            }
        }

        double eft = findEFT(job, bestFv, bestReadyTime, execTimeMap, true, execWindowMap);
        WorkflowDatacenter dc = (WorkflowDatacenter) bestFv.getVm().getDatacenter();
        List<Double> elecPrice = dc.getElecPrice();
        double transferElecCost = ExperimentUtil.calculateElecCost(elecPrice, beginTime, bestReadyTime, bestFv.getPower());
        double execElecCost = ExperimentUtil.calculateElecCost(elecPrice, eft - execTimeMap.get(job).get(bestFv), eft, bestFv.getPower());
        double elecCost = transferElecCost + execElecCost;
        eftMap.put(job, eft);
        solution.bindJobToFv(job, bestFv);
        return elecCost;
    }


}
