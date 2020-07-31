package com.hbaspecto.pecas.sd.estimation;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public abstract class RenovationTarget extends EstimationTarget implements ExpectedValue {

	private double modelledValue;
	private double[] derivs;

	@Override
	public double getModelledTotalNewValueForParcel(int spacetype,
			double expectedAddedSpace, double expectedNewSpace) {
		return 0;
	}

	@Override
	public double getModelledTotalNewDerivativeWRTAddedSpace(int spacetype,
			double expectedAddedSpace, double expectedNewSpace) {
		return 0;
	}

	@Override
	public double getModelledTotalNewDerivativeWRTNewSpace(int spacetype,
			double expectedAddedSpace, double expectedNewSpace) {
		return 0;
	}
	
	public abstract double getModelledRenovateQuantityForParcel(int spacetype, double quantity);
	
	public abstract double getModelledRenovateDerivativeForParcel(int spacetype, double quantity);

	@Override
	public void setModelledValue(double value) {
	    modelledValue = value;
	}

	@Override
	public double getModelledValue() {
	    return modelledValue;
	}

	@Override
	public List<ExpectedValue> getAssociatedExpectedValues() {
	    return Collections.<ExpectedValue>singletonList(this);
	}

	@Override
	public void setDerivatives(double[] derivatives) {
	    derivs = Arrays.copyOf(derivatives, derivatives.length);
	}

	@Override
	public double[] getDerivatives() {
	    return Arrays.copyOf(derivs, derivs.length);
	}


}
