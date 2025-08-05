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

import java.lang.reflect.Constructor;
import java.util.*;


/**
 * The Random planning algorithm
 */

@Slf4j
public class RandomPlanner extends WorkflowPlannerAbstract {

    private Map<Job, Map<Vm, Double>> localDataTransferTimeMap;
    private Map<Job, Double> finishTimeMap;
    private Map<Job, Double> upwardRankMap;

    public RandomPlanner(ContinuousDistribution random, Param param) {
        super(random, param);
    }

    public void init() throws Exception {
        Class<?> clazz = Class.forName(getParam().getWorkflowComparator());
        Constructor<?> constructor = clazz.getDeclaredConstructor();
        WorkflowComparatorInterface comparatorInterface = (WorkflowComparatorInterface) constructor.newInstance();
        Comparator<Workflow> workflowComparator = comparatorInterface.get(getParam().isAscending());
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
            calculateExecTimeAndReliability(workflow);
            allocateJobs(workflow);
        }
    }


    /**
     * calculate predicted average local data transfer time
     */
    private Map<Job, Double> calculateAvgLocalDataTransferTime(Workflow workflow) {
        Map<Job, Double> avgLocalDataTransferTimeMap = new HashMap<>();
        localDataTransferTimeMap = new HashMap<>();
        for (Job job : workflow.getJobList()) {
            double total = 0.0;
            Map<Vm, Double> jobLocalDataTransferTimeMap = new HashMap<>();
            for (Vm vm : getVmList()) {
                double temp = ExperimentUtil.calculateLocalDataTransferTime(job, (Host) vm.getHost());
                jobLocalDataTransferTimeMap.put(vm, temp);
                total += temp;
            }
            avgLocalDataTransferTimeMap.put(job, total / getVmList().size());
            localDataTransferTimeMap.put(job, jobLocalDataTransferTimeMap);
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
        double slackTime = upwardRank * getParam().getDeadlineFactor();
        workflow.setDeadline(getFinishTime() + upwardRank + slackTime);

    }


    /**
     * allocate jobs
     */
    private void allocateJobs(Workflow workflow) {
        log.info("{}: {}: Starting planning workflow #{} {}, a total of {} Jobs...", CloudSim.clock(), this, workflow.getId(), workflow.getName(), workflow.getJobNum());
        List<Job> sequence = workflow.getJobList().stream().sorted(Comparator.comparingDouble(upwardRankMap::get).reversed()).toList();
        Set<Job> scheduledSet = new HashSet<>();
        finishTimeMap = new HashMap<>();
        Solution solution = new Solution();
        double elecCost = 0;
        double reliability = 1;
        double finishTime = 0;
        while (scheduledSet.size() < sequence.size()) {
            for (Job job : sequence) {
                if (scheduledSet.contains(job) || !scheduledSet.containsAll(job.getParentList())) {
                    continue;
                }
                scheduledSet.add(job);
                elecCost += allocateJob(job, solution, getExecWindowMap());
                reliability *= reliabilityMap.get(job).get(solution.getResult().get(job));
                finishTime = Math.max(finishTime, finishTimeMap.get(job));
                solution.getSequence().add(job);
            }
        }
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
     * allocate job
     *
     * @return electric cost
     */
    private double allocateJob(Job job, Solution solution, Map<Vm, List<ExecWindow>> execWindowMap) {
        double transferStartTime = job.getParentList().stream().mapToDouble(finishTimeMap::get).min().orElse(0);
        DvfsVm vm = (DvfsVm) ExperimentUtil.getRandomElement(getRandom(), getVmList());
        double max = 0;
        for (Job parent : job.getParentList()) {
            max = Math.max(max, finishTimeMap.get(parent) + ExperimentUtil.calculatePredecessorDataTransferTime(job, (Host) vm.getHost(), parent, (Host) solution.getResult().get(parent).getVm().getHost()));
        }
        double readyTime = max + localDataTransferTimeMap.get(job).get(vm);
        Fv fv = ExperimentUtil.getRandomElement(getRandom(), vm.getFvList());
        ExecWindow execWindow = findExecWindow(job, fv, readyTime, execWindowMap);
        WorkflowDatacenter datacenter = (WorkflowDatacenter) fv.getVm().getDatacenter();
        List<Double> elecPrice = datacenter.getElecPrice();
        double elecCost = ExperimentUtil.calculateElecCost(elecPrice, transferStartTime, readyTime, execWindow.getFv().getPower()) +
                ExperimentUtil.calculateElecCost(elecPrice, execWindow.getStartTime(), execWindow.getFinishTime(), execWindow.getFv().getPower());
        execWindow.setElecCost(elecCost);
        execWindowMap.get(execWindow.getFv().getVm()).add(execWindow.getInsertPos(), execWindow);
        finishTimeMap.put(job, execWindow.getFinishTime());
        solution.bindJobToFv(job, execWindow.getFv());
        return elecCost;
    }


}
