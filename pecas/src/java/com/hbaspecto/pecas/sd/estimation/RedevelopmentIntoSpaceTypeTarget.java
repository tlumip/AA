package com.hbaspecto.pecas.sd.estimation;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import com.hbaspecto.pecas.sd.SpaceTypesI;
import com.hbaspecto.pecas.sd.ZoningRulesI;

public class RedevelopmentIntoSpaceTypeTarget extends EstimationTarget
        implements ExpectedValue {

    private int[] spaceTypes;
    private double modelledValue;
    private double[] derivs;
    public static final String NAME = "redevel";

    public RedevelopmentIntoSpaceTypeTarget(int... spacetypes) {
        this.spaceTypes = Arrays.copyOf(spacetypes, spacetypes.length);
    }

    public int[] getSpaceTypes() {
        return Arrays.copyOf(spaceTypes, spaceTypes.length);
    }

    @Override
    public boolean appliesToCurrentParcel() {
        return true;
    }

    @Override
    public double getModelledTotalNewValueForParcel(int checkSpaceType,
            double expectedAddedSpace, double expectedNewSpace) {
        // Must not be vacant to be counted.
        if (SpaceTypesI.getAlreadyCreatedSpaceTypeBySpaceTypeID(
                ZoningRulesI.land.getCoverage()).isVacant())
            return 0;
        // Must be in the correct spacetype to be counted.
        return in(checkSpaceType, spaceTypes) ? expectedAddedSpace + expectedNewSpace : 0;
    }

    @Override
    public double getModelledTotalNewDerivativeWRTAddedSpace(int checkSpaceType,
            double expectedAddedSpace, double expectedNewSpace) {
        if (SpaceTypesI.getAlreadyCreatedSpaceTypeBySpaceTypeID(
                ZoningRulesI.land.getCoverage()).isVacant())
            return 0;
        return in(checkSpaceType, spaceTypes) ? 1 : 0;
    }

    @Override
    public double getModelledTotalNewDerivativeWRTNewSpace(int checkSpaceType,
            double expectedAddedSpace, double expectedNewSpace) {
        if (SpaceTypesI.getAlreadyCreatedSpaceTypeBySpaceTypeID(
                ZoningRulesI.land.getCoverage()).isVacant())
            return 0;
        return in(checkSpaceType, spaceTypes) ? 1 : 0;
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
        return Collections.<ExpectedValue> singletonList(this);
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
