package com.hbaspecto.pecas.sd.estimation;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import com.hbaspecto.pecas.sd.SpaceTypesI;
import com.hbaspecto.pecas.sd.ZoningRulesI;

public class AdditionIntoSpaceTypesTarget extends EstimationTarget implements ExpectedValue {
	
	private int[] spaceTypes;
	private double modelledValue;
	private double[] derivs;
	public static final String NAME = "addition";

	public AdditionIntoSpaceTypesTarget(int... spacetypes) {
	    this.spaceTypes = Arrays.copyOf(spacetypes, spacetypes.length);
	}

    public AdditionIntoSpaceTypesTarget(String... specification) {
        spaceTypes = parseSpaceTypeStrings(specification);
    }
    
    public int[] getSpaceTypes() {
        return Arrays.copyOf(spaceTypes, spaceTypes.length);
    }

	@Override
	public boolean appliesToCurrentParcel() {
	    return true;
	}
	
    @Override
    public double getModelledTotalNewValueForParcel(int checkSpaceType, double expectedAddedSpace,
            double expectedNewSpace) {
        // Must not be vacant to be counted.
        if(SpaceTypesI.getAlreadyCreatedSpaceTypeBySpaceTypeID(ZoningRulesI.land.getCoverage()).isVacant())
            return 0;
        return in(checkSpaceType, spaceTypes) ? expectedAddedSpace : 0;
    }

    @Override
    public double getModelledTotalNewDerivativeWRTAddedSpace(int checkSpaceType, double expectedAddedSpace,
            double expectedNewSpace) {
        if(SpaceTypesI.getAlreadyCreatedSpaceTypeBySpaceTypeID(ZoningRulesI.land.getCoverage()).isVacant())
            return 0;
        return in(checkSpaceType, spaceTypes) ? 1 : 0;
    }

    @Override
    public double getModelledTotalNewDerivativeWRTNewSpace(int spacetype, double expectedAddedSpace,
            double expectedNewSpace) {
        return 0;
    }

    @Override
    public String getName() {
    	return joinHyphens(NAME, spaceTypes);
    }

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
