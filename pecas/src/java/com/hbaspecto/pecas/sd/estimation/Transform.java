package com.hbaspecto.pecas.sd.estimation;

/**
 * Represents a transformation applied to a parameter or target. Applying the
 * transform converts the normal or <i>inner</i> value of the parameter or
 * target into a <i>transformed</i> value. The SD expected value model sees only
 * the inner value, while SD calibration sees only the transformed value. This
 * allows parameters and targets to have non-normal probability distributions,
 * even though SD calibration expects all distributions to be normal.
 * 
 * @author Graham Hill
 *
 */
public interface Transform {
    /**
     * Transforms the specified inner value
     */
    public double transform(double innerValue);

    /**
     * Returns the inner value corresponding to the specified transformed value
     */
    public double untransform(double transformedValue);

    /**
     * Returns the derivative of the transformation with respect to the inner
     * value, at the specified inner value
     */
    public double transformDerivative(double innerValue);

    /**
     * Returns the derivative of the inverse transformation with respect to the
     * transformed value, at the specified transformed value
     */
    public double untransformDerivative(double transformedValue);
}
