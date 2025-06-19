package com.qiujie.entity;

import com.qiujie.comparator.WorkflowComparatorInterface;
import com.qiujie.enums.JobSequenceStrategyEnum;
import lombok.Getter;

import static com.qiujie.Constants.*;

/**
 * Configuration parameters class with smart toString() implementation
 * that only displays explicitly set parameters and omits parentheses
 * when no parameters are set.
 */
@Getter
public class Parameter {
    // Default parameter values from Constants
    private Class<? extends WorkflowComparatorInterface> workflowComparator = WORKFLOW_COMPARATOR;
    private boolean ascending = ASCENDING;
    private double deadlineFactor = DEADLINE_FACTOR;
    private double reliabilityFactor = RELIABILITY_FACTOR;
    private JobSequenceStrategyEnum jobSequenceStrategy = JOB_SEQUENCE_STRATEGY;
    private double neighborhoodFactor = NEIGHBORHOOD_FACTOR;
    private double slackTimeFactor = SLACK_TIME_FACTOR;

    // Flags to track which parameters have been explicitly set
    private transient boolean workflowComparatorSet = false;
    private transient boolean ascendingSet = false;
    private transient boolean deadlineFactorSet = false;
    private transient boolean reliabilityFactorSet = false;
    private transient boolean jobSequenceStrategySet = false;
    private transient boolean neighborhoodFactorSet = false;
    private transient boolean slackTimeFactorSet = false;

    // Overridden setters that update both the value and the flag
    public Parameter setWorkflowComparator(Class<? extends WorkflowComparatorInterface> workflowComparator) {
        this.workflowComparator = workflowComparator;
        this.workflowComparatorSet = true;
        return this;
    }

    public Parameter setAscending(boolean ascending) {
        this.ascending = ascending;
        this.ascendingSet = true;
        return this;
    }

    public Parameter setDeadlineFactor(double deadlineFactor) {
        this.deadlineFactor = deadlineFactor;
        this.deadlineFactorSet = true;
        return this;
    }

    public Parameter setReliabilityFactor(double reliabilityFactor) {
        this.reliabilityFactor = reliabilityFactor;
        this.reliabilityFactorSet = true;
        return this;
    }

    public Parameter setJobSequenceStrategy(JobSequenceStrategyEnum jobSequenceStrategy) {
        this.jobSequenceStrategy = jobSequenceStrategy;
        this.jobSequenceStrategySet = true;
        return this;
    }

    public Parameter setNeighborhoodFactor(double neighborhoodFactor) {
        this.neighborhoodFactor = neighborhoodFactor;
        this.neighborhoodFactorSet = true;
        return this;
    }

    public Parameter setSlackTimeFactor(double slackTimeFactor) {
        this.slackTimeFactor = slackTimeFactor;
        this.slackTimeFactorSet = true;
        return this;
    }

    /**
     * Generates a string representation containing only explicitly set parameters.
     * Parentheses are included only if at least one parameter is set.
     *
     * @return String representation of set parameters, or empty string if none are set
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        boolean hasParameters = false;

        // Check and append each parameter if it was set
        if (workflowComparatorSet) {
            appendParameter(sb, "cmp", workflowComparator.getSimpleName().replace("Comparator", ""), !hasParameters);
            hasParameters = true;
        }

        if (ascendingSet) {
            appendParameter(sb, "asc", ascending, !hasParameters);
            hasParameters = true;
        }

        if (deadlineFactorSet) {
            appendParameter(sb, "df", String.format("%.2f", deadlineFactor), !hasParameters);
            hasParameters = true;
        }

        if (reliabilityFactorSet) {
            appendParameter(sb, "rf", String.format("%.3f", reliabilityFactor), !hasParameters);
            hasParameters = true;
        }

        if (jobSequenceStrategySet) {
            appendParameter(sb, "seq", jobSequenceStrategy.name(), !hasParameters);
            hasParameters = true;
        }

        if (neighborhoodFactorSet) {
            appendParameter(sb, "nf", String.format("%.2f", neighborhoodFactor), !hasParameters);
            hasParameters = true;
        }

        if (slackTimeFactorSet) {
            appendParameter(sb, "stf", String.format("%.2f", slackTimeFactor), !hasParameters);
            hasParameters = true;
        }

        // Add parentheses only if parameters exist
        if (hasParameters) {
            sb.insert(0, "(");
            sb.append(")");
        }

        return sb.toString();
    }

    /**
     * Helper method to append parameter to StringBuilder with proper formatting
     *
     * @param sb The StringBuilder to append to
     * @param name The parameter name (short code)
     * @param value The parameter value
     * @param isFirst Whether this is the first parameter being appended
     */
    private void appendParameter(StringBuilder sb, String name, Object value, boolean isFirst) {
        if (!isFirst) {
            sb.append(",");
        }
        sb.append(name).append("=").append(value);
    }
}
