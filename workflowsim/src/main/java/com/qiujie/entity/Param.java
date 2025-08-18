package com.qiujie.entity;

import com.qiujie.comparator.WorkflowComparatorInterface;
import com.qiujie.enums.JobSequenceStrategyEnum;
import com.qiujie.util.ExperimentUtil;
import lombok.Data;
import lombok.experimental.Accessors;
import static com.qiujie.Constants.*;

@Data
@Accessors(chain = true)
public class Param  {
    // Default parameter values from Constants
    private Class<? extends WorkflowComparatorInterface> workflowComparator = WORKFLOW_COMPARATOR;
    private boolean ascending = ASCENDING;
    private double deadlineFactor = DEADLINE_FACTOR;
    private double reliabilityFactor = RELIABILITY_FACTOR;
    private JobSequenceStrategyEnum jobSequenceStrategy = JOB_SEQUENCE_STRATEGY;
    private double neighborhoodFactor = NEIGHBORHOOD_FACTOR;
    private double slackTimeFactor = SLACK_TIME_FACTOR;

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        final boolean[] hasDiff = {false};

        java.util.function.BiConsumer<String, Object> appendIfNotDefault = (key, val) -> {
            if (hasDiff[0]) sb.append(",");
            else sb.append("(");
            sb.append(key).append("=").append(val);
            hasDiff[0] = true;
        };

        if (!workflowComparator.getName().equals(WORKFLOW_COMPARATOR.getName())) {
            appendIfNotDefault.accept("cmp", ExperimentUtil.getPrefixFromClassName(workflowComparator.getSimpleName()));
        }

        if (ascending != ASCENDING) {
            appendIfNotDefault.accept("asc", ascending);
        }

        if (Double.compare(deadlineFactor, DEADLINE_FACTOR) != 0) {
            appendIfNotDefault.accept("df", String.format("%.2f", deadlineFactor));
        }

        if (Double.compare(reliabilityFactor, RELIABILITY_FACTOR) != 0) {
            appendIfNotDefault.accept("rf", String.format("%.3f", reliabilityFactor));
        }

        if (jobSequenceStrategy != JOB_SEQUENCE_STRATEGY) {
            appendIfNotDefault.accept("seq", jobSequenceStrategy);
        }

        if (Double.compare(neighborhoodFactor, NEIGHBORHOOD_FACTOR) != 0) {
            appendIfNotDefault.accept("nf", String.format("%.2f", neighborhoodFactor));
        }

        if (Double.compare(slackTimeFactor, SLACK_TIME_FACTOR) != 0) {
            appendIfNotDefault.accept("stf", String.format("%.2f", slackTimeFactor));
        }

        if (hasDiff[0]) {
            sb.append(")");
            return sb.toString();
        } else {
            return "";
        }
    }

}
