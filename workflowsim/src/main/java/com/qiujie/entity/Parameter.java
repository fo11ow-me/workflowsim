package com.qiujie.entity;

import com.qiujie.comparator.WorkflowComparatorInterface;
import com.qiujie.enums.JobSequenceStrategyEnum;
import com.qiujie.util.ExperimentUtil;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

import static com.qiujie.Constants.*;

@Setter
@Getter
@Accessors(chain = true)
@NoArgsConstructor
public class Parameter {
    // Default parameter values from Constants
    private String workflowComparator = WORKFLOW_COMPARATOR.getName();
    private boolean ascending = ASCENDING;
    private double deadlineFactor = DEADLINE_FACTOR;
    private double reliabilityFactor = RELIABILITY_FACTOR;
    private JobSequenceStrategyEnum jobSequenceStrategy = JOB_SEQUENCE_STRATEGY;
    private double neighborhoodFactor = NEIGHBORHOOD_FACTOR;
    private double slackTimeFactor = SLACK_TIME_FACTOR;

    /**
     * Hutool — specifically, the `JSONUtil.toBean(...)` method — does not call your custom `setWorkflowComparator(Class<?>)` method because:
     * It is a regular method (not a standard JavaBean setter), and Hutool's deserialization mechanism only calls standard JavaBean-style setters like `setXxx(String)`, or it directly sets the field via reflection.
     *
     */
    public Parameter setWorkflowComparator(Class<? extends WorkflowComparatorInterface> workflowComparator) {
        return setWorkflowComparator(workflowComparator.getName());
    }

    public Parameter setWorkflowComparator(String workflowComparator) {
        this.workflowComparator = workflowComparator;
        return this;
    }


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

        if (!workflowComparator.equals(WORKFLOW_COMPARATOR.getName())) {
            appendIfNotDefault.accept("cmp", ExperimentUtil.getPrefixFromClassName(workflowComparator));
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
