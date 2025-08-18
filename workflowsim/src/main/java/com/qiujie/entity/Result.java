package com.qiujie.entity;

import com.qiujie.core.WorkflowBroker;
import com.qiujie.planner.WorkflowPlannerAbstract;
import com.qiujie.util.ExperimentUtil;
import lombok.Data;
import lombok.experimental.Accessors;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Data
@Accessors(chain = true)
public class Result {
    private int id;
    private String name;
    private String workflowComparator;
    private boolean ascending;
    private double deadlineFactor;
    private double reliabilityFactor;
    private String jobSequenceStrategy;
    private double neighborhoodFactor;
    private double slackTimeFactor;
    private List<String> daxList;
    private String completionDetail;
    private double elecCost;
    private double finishTime;
    private int retryCount;
    private int overdueCount;
    private double plnRuntime;
    private double runtime;

    public static final Result POISON_PILL = new Result().setId(-1);


    public Result() {
        this.daxList = new ArrayList<>();
    }

    public Result(SimParam simParam, WorkflowPlannerAbstract planner, WorkflowBroker broker, double runtime) {
        this();
        int finishedCloudlets = broker.getCloudletReceivedList().size();
        int totalCloudlets = broker.getWorkflowList().stream().mapToInt(Workflow::getJobNum).sum();
        setId(simParam.getId());
        setName(planner.toString());
        getDaxList().addAll(simParam.getDaxList());
        setWorkflowComparator(ExperimentUtil.getPrefixFromClassName(simParam.getParam().getWorkflowComparator().getSimpleName()));
        setAscending(simParam.getParam().isAscending());
        setDeadlineFactor(simParam.getParam().getDeadlineFactor());
        setReliabilityFactor(simParam.getParam().getReliabilityFactor());
        setJobSequenceStrategy(simParam.getParam().getJobSequenceStrategy().name());
        setNeighborhoodFactor(simParam.getParam().getNeighborhoodFactor());
        setSlackTimeFactor(simParam.getParam().getSlackTimeFactor());
        setCompletionDetail(String.format("%d / %d", finishedCloudlets, totalCloudlets));
        setElecCost(broker.getCloudletReceivedList().stream().mapToDouble(cloudlet -> ((Job) cloudlet).getElecCost()).sum());
        setFinishTime(broker.getCloudletReceivedList().getLast().getExecFinishTime());
        setRetryCount(broker.getCloudletReceivedList().stream().mapToInt(cloudlet -> ((Job) cloudlet).getRetryCount()).sum());
        setOverdueCount((int) broker.getWorkflowList().stream().filter(Workflow::isOverdue).count());
        setPlnRuntime(planner.getRuntime());
        setRuntime(runtime);
    }


    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Result result = (Result) o;
        return id == result.id;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
