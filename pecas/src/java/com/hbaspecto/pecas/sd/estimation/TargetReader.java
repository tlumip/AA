package com.hbaspecto.pecas.sd.estimation;

import java.util.List;

public interface TargetReader {
    /**
     * Returns the list of targets being calibrated to
     */
    public List<EstimationTarget> targets();

    /**
     * Returns the variance of the specified target
     */
    public double variance(EstimationTarget target);

    /**
     * Returns the covariance between the two specified targets. This is always
     * symmetric, i.e. covariance(x, y) is the same as covariance(y, x). The
     * covariance of a target with itself is the variance of that target.
     */
    public double covariance(EstimationTarget target1,
            EstimationTarget target2);

    /**
     * Returns the variance-covariance matrix of the specified targets, with
     * both rows and columns in the iteration order of the list.
     */
    public double[][] variance(List<EstimationTarget> targets);
}
