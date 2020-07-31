package com.hbaspecto.pecas.sd.estimation;

import java.util.List;

import com.hbaspecto.discreteChoiceModelling.Coefficient;

/**
 * A general reader for SD calibration inputs
 * 
 * @author Graham Hill
 */
public interface PriorReader {
    /**
     * Returns the list of parameters being calibrated
     */
    public List<Coefficient> parameters();

    /**
     * Returns the mean of the specified parameter
     */
    public double mean(Coefficient param);

    /**
     * Returns the means of the specified parameters, in the iteration order of
     * the list
     */
    public double[] means(List<Coefficient> params);

    /**
     * Returns the start value for the specified parameter
     */
    public double startValue(Coefficient param);

    /**
     * Returns the start values for the specified parameters, in the iteration
     * order of the list
     */
    public double[] startValues(List<Coefficient> params);

    /**
     * Returns the variance of the prior for the specific parameter
     */
    public double variance(Coefficient param);

    /**
     * Returns the covariance between the two specified parameters. This is
     * always symmetric, i.e. covariance(x, y) is the same as covariance(y, x).
     * The covariance of a parameter with itself is the variance of that
     * parameter.
     */
    public double covariance(Coefficient param1, Coefficient param2);

    /**
     * Returns the variance-covariance matrix of the specified parameters, with
     * both rows and columns in the iteration order of the list
     */
    public double[][] variance(List<Coefficient> params);
}
