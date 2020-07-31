package com.hbaspecto.pecas.sd.estimation;

/**
 * A simple log transform, allowing parameters and targets to have a lognormal
 * distribution
 * 
 * @author Graham Hill
 */
public class LogTransform implements Transform {
    private double base;
    private boolean unnatural = true;

    public LogTransform() {
        this(Math.E);
        unnatural = false;
    }

    public LogTransform(double base) {
        if (base <= 0) {
            throw new IllegalArgumentException(
                    "Invalid base " + base + ", must be positive");
        }
        this.base = base;
    }

    @Override
    public double transform(double innerValue) {
        double result = Math.log(innerValue);
        if (unnatural) {
            result /= Math.log(base);
        }
        return result;
    }

    @Override
    public double untransform(double transformedValue) {
        if (unnatural) {
            return Math.pow(base, transformedValue);
        } else {
            return Math.exp(transformedValue);
        }
    }

    @Override
    public double transformDerivative(double innerValue) {
        double result = 1 / innerValue;
        if (unnatural) {
            result /= Math.log(base);
        }
        return result;
    }
    
    @Override
    public double untransformDerivative(double transformedValue) {
        double result = untransform(transformedValue);
        if (unnatural) {
            result *= Math.log(base);
        }
        return result;
    }
}
