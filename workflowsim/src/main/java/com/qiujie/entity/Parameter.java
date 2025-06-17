package com.qiujie.entity;

import com.qiujie.comparator.DefaultComparator;
import com.qiujie.comparator.WorkflowComparatorInterface;
import com.qiujie.enums.JobSequenceStrategyEnum;
import lombok.Data;
import lombok.experimental.Accessors;

import static com.qiujie.Constants.*;


@Data
@Accessors(chain = true)
public class Parameter {
    private Class<? extends WorkflowComparatorInterface> workflowComparator = WORKFLOW_COMPARATOR;
    private boolean ascending = ASCENDING;
    private double deadlineFactor = DEADLINE_FACTOR;
    private double reliabilityFactor = RELIABILITY_FACTOR;
    private JobSequenceStrategyEnum jobSequenceStrategy = JOB_SEQUENCE_STRATEGY;
    private double neighborhoodFactor = NEIGHBORHOOD_FACTOR;
    private double slackTimeFactor = SLACK_TIME_FACTOR;


    @Override
    public String toString() {
        String cmpName = workflowComparator.getSimpleName().replace("Comparator", "");
        return String.format(
                "(cmp:%s,asc:%b,df:%.2f,rf:%.3f,seq:%s,nf:%.2f,stf:%.2f)",
                cmpName,
                ascending,
                deadlineFactor,
                reliabilityFactor,
                jobSequenceStrategy.name(),
                neighborhoodFactor,
                slackTimeFactor
        );
    }
}
