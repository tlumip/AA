package com.hbaspecto.pecas.sd.estimation;

import com.hbaspecto.discreteChoiceModelling.Coefficient;

/**
 * A wrapper around a coefficient that adds a transformation to it. Note that
 * this does not stack with any transformations the coefficient already has
 * defined.
 * @author Graham Hill
 */
public class TransformedCoefficient implements Coefficient {

    private final Coefficient inner;
    private final Transform transform;

    public TransformedCoefficient(Coefficient inner, Transform transform) {
        this.inner = inner;
        this.transform = transform;
    }

    @Override
    public double getValue() {
        return inner.getValue();
    }

    @Override
    public void setValue(double v) {
        inner.setValue(v);
    }

    @Override
    public double getTransformedValue() {
        return transform.transform(inner.getValue());
    }

    @Override
    public void setTransformedValue(double v) {
        inner.setValue(transform.untransform(v));
    }

    @Override
    public double getTransformationDerivative() {
        return transform.transformDerivative(inner.getValue());
    }

    @Override
    public double getInverseTransformationDerivative() {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public String getName() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public CoefficientType getType() {
        // TODO Auto-generated method stub
        return null;
    }

}
