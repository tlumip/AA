package com.hbaspecto.pecas.sd.estimation;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.hbaspecto.pecas.sd.ZoningRulesI;

public class SpaceTypeIntensityTarget extends EstimationTarget {
	
	private int[] spaceTypes;
	private double expectedFARSum;
	private double expectedBuildNewEvents;
	private double[] expectedFARSumDerivatives;
	private double[] expectedBuildNewEventsDerivatives;
	private List<ExpectedValue> associates = null;
	public static final String NAME = "fartarg";
	
	public SpaceTypeIntensityTarget(int... spacetypes) {
	    spaceTypes = Arrays.copyOf(spacetypes, spacetypes.length);
	}
	
	public int[] getSpaceTypes() {
	    return Arrays.copyOf(spaceTypes, spaceTypes.length);
	}

    @Override
    public String getName() {
        return joinHyphens(NAME, spaceTypes);
    }

    @Override
    public double getModelledValue() {
        if (expectedBuildNewEvents == 0) {
            return 0;
        } else {
            return expectedFARSum / expectedBuildNewEvents;
        }
    }

    @Override
    public double[] getDerivatives() {
        int numCoeffs = expectedFARSumDerivatives.length;
        double[] result = new double[numCoeffs];
        for(int i = 0; i < numCoeffs; i++) {
            // Apply the quotient rule to find the derivative of expectedFARSum / expectedBuildNewEvents.
            if (expectedBuildNewEvents == 0) {
                result[i] = 0;
            } else {
                double loDhi = expectedBuildNewEvents * expectedFARSumDerivatives[i];
                double hiDlo = expectedFARSum * expectedBuildNewEventsDerivatives[i];
                double denominatorSquared = expectedBuildNewEvents * expectedBuildNewEvents;
                result[i] = (loDhi - hiDlo) / denominatorSquared;
            }
        }
        
        return result;
    }

    @Override
    public List<ExpectedValue> getAssociatedExpectedValues() {
        if(associates == null) {
            associates = new ArrayList<ExpectedValue>();
            associates.add(new ExpectedFARSum());
            associates.add(new ExpectedBuildNewEvents());
            return associates;
        }
        return associates;
    }
    
    // Calculates the expected sum of the FARs of all of the parcels on which Build-new is selected.
    private class ExpectedFARSum implements ExpectedValue {
        @Override
        public boolean appliesToCurrentParcel() {
            return true;
        }

        @Override
        public double getModelledTotalNewValueForParcel(int spacetype, double expectedAddedSpace,
                double expectedNewSpace) {
            // Only new space counts for this target.
            return in(spacetype, spaceTypes) ? expectedNewSpace / ZoningRulesI.land.getLandArea() : 0;
        }

        @Override
        public double getModelledTotalNewDerivativeWRTAddedSpace(int spacetype, double expectedAddedSpace,
                double expectedNewSpace) {
            // This target doesn't include added space.
            return 0;
        }

        @Override
        public double getModelledTotalNewDerivativeWRTNewSpace(int spacetype, double expectedAddedSpace,
                double expectedNewSpace) {
            return in(spacetype, spaceTypes) ? 1 / ZoningRulesI.land.getLandArea() : 0;
        }

        @Override
        public void setModelledValue(double value) {
            expectedFARSum = value;
        }

        @Override
        public void setDerivatives(double[] derivatives) {
            expectedFARSumDerivatives = Arrays.copyOf(derivatives, derivatives.length);
        }
    }
    
    // Class that counts the expected number of times the Build-new alternative will be selected.
    private class ExpectedBuildNewEvents implements ExpectedValue {

        @Override
        public boolean appliesToCurrentParcel() {
            return true;
        }

        @Override
        public double getModelledTotalNewValueForParcel(int spacetype, double expectedAddedSpace,
                double expectedNewSpace) {
            return (in(spacetype, spaceTypes) && expectedNewSpace > 0) ? 1 : 0;
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

        @Override
        public void setModelledValue(double value) {
            expectedBuildNewEvents = value;
        }

        @Override
        public void setDerivatives(double[] derivatives) {
            expectedBuildNewEventsDerivatives = Arrays.copyOf(derivatives, derivatives.length);
        }
        
    }
}
