package com.hbaspecto.pecas.sd.estimation;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

import com.hbaspecto.pecas.sd.orm.DensityStepPoints;

public abstract class DensityShapingFunctionParameter
        extends SpaceTypeCoefficient {
    public static enum Type implements SpaceTypeCoefficientType {
        STEP_POINT("StepPoint"),
        ABOVE_STEP_POINT_ADJ("AboveStepPoint"),
        STEP_POINT_AMOUNT("StepPointAmount");

        private String typeName;

        private Type(String typeName) {
            this.typeName = typeName;
        }

        @Override
        public String getTypeName() {
            return typeName;
        }

        @Override
        public boolean isTo() {
            return true;
        }
    }

    private final int stepPointNumber;

    public static final String STEP_POINT = "step";
    public static final String ABOVE_STEP_POINT_ADJ = "above";
    public static final String STEP_POINT_AMOUNT = "stepamt";

    protected static final Object coeffLock = new Object();
    private static final Map<Type, Map<Key, DensityShapingFunctionParameter>> theCoeffs = new EnumMap<>(
            Type.class);

    protected DensityShapingFunctionParameter(Type type, String name,
            int spacetype, int stepPointNumber) {
        super(type, name, spacetype);
        this.stepPointNumber = stepPointNumber;
    }

    @Override
    public String getName() {
        return super.getName() + "-" + stepPointNumber;
    }

    public int getStepPointNumber() {
        return stepPointNumber;
    }

    // Returns null if the coefficient doesn't exist.
    protected static DensityShapingFunctionParameter getExistingCoefficient(
            Type type, Key k) {
        if (theCoeffs.containsKey(type)) {
            Map<Key, DensityShapingFunctionParameter> spaceTypeCoeffs = theCoeffs
                    .get(type);
            if (spaceTypeCoeffs.containsKey(k))
                return spaceTypeCoeffs.get(k);
            else
                return null;
        } else
            return null;
    }

    protected static void insertNewCoefficient(Type type, Key k,
            DensityShapingFunctionParameter newCoeff) {
        if (theCoeffs.containsKey(type)) {
            theCoeffs.get(type).put(k, newCoeff);
        } else {
            HashMap<Key, DensityShapingFunctionParameter> spaceTypeCoeffs = new HashMap<>();
            theCoeffs.put(type, spaceTypeCoeffs);
            spaceTypeCoeffs.put(k, newCoeff);
        }
    }

    public static DensityShapingFunctionParameter getConstByType(Type type,
            int spacetype, int stepPointNumber) {
        switch (type) {
        case STEP_POINT:
            return getStepPoint(spacetype, stepPointNumber);
        case ABOVE_STEP_POINT_ADJ:
            return getAboveStepPointAdj(spacetype, stepPointNumber);
        case STEP_POINT_AMOUNT:
            return getStepPointAmount(spacetype, stepPointNumber);
        default:
            throw new AssertionError("Unknown coefficient type");
        }
    }

    /**
     * Returns the density shaping function's step point for the given
     * spacetype.
     */
    public static DensityShapingFunctionParameter getStepPoint(int spacetype,
            int stepPointNumber) {
        synchronized (coeffLock) {
            Key k = new Key(spacetype, stepPointNumber);
            DensityShapingFunctionParameter coeff = getExistingCoefficient(
                    Type.STEP_POINT, k);
            if (coeff == null) {
                coeff = new StepPoint(spacetype, stepPointNumber);
                insertNewCoefficient(Type.STEP_POINT, k, coeff);
            }
            return coeff;
        }
    }

    /**
     * Returns the density shaping function's adjustment above the step point
     * for the given spacetype.
     */
    public static DensityShapingFunctionParameter getAboveStepPointAdj(
            int spacetype, int stepPointNumber) {
        synchronized (coeffLock) {
            Key k = new Key(spacetype, stepPointNumber);
            DensityShapingFunctionParameter coeff = getExistingCoefficient(
                    Type.ABOVE_STEP_POINT_ADJ, k);
            if (coeff == null) {
                coeff = new AboveStepPointAdjustment(spacetype,
                        stepPointNumber);
                insertNewCoefficient(Type.ABOVE_STEP_POINT_ADJ, k, coeff);
            }
            return coeff;
        }
    }

    /**
     * Returns the density shaping function's step point size for the given
     * spacetype.
     */
    public static DensityShapingFunctionParameter getStepPointAmount(
            int spacetype, int stepPointNumber) {
        synchronized (coeffLock) {
            Key k = new Key(spacetype, stepPointNumber);
            DensityShapingFunctionParameter coeff = getExistingCoefficient(
                    Type.STEP_POINT_AMOUNT, k);
            if (coeff == null) {
                coeff = new StepPointAmount(spacetype, stepPointNumber);
                insertNewCoefficient(Type.STEP_POINT_AMOUNT, k, coeff);
            }
            return coeff;
        }
    }

    private static class StepPoint extends DensityShapingFunctionParameter {
        private StepPoint(int spacetype, int stepPointNumber) {
            super(Type.STEP_POINT, STEP_POINT, spacetype, stepPointNumber);
        }

        @Override
        public double getValue() {
            return DensityStepPoints
                    .getRecord(getSpacetype(), getStepPointNumber())
                    .get_StepPointIntensity();
        }

        @Override
        public void setValue(double v) {
            DensityStepPoints.getRecord(getSpacetype(), getStepPointNumber())
                    .set_StepPointIntensity(v);
        }
    }

    private static class AboveStepPointAdjustment
            extends DensityShapingFunctionParameter {
        private AboveStepPointAdjustment(int spacetype, int stepPointNumber) {
            super(Type.ABOVE_STEP_POINT_ADJ, ABOVE_STEP_POINT_ADJ, spacetype,
                    stepPointNumber);
        }

        @Override
        public double getValue() {
            return DensityStepPoints
                    .getRecord(getSpacetype(), getStepPointNumber())
                    .get_SlopeAdjustment();
        }

        @Override
        public void setValue(double v) {
            DensityStepPoints.getRecord(getSpacetype(), getStepPointNumber())
                    .set_SlopeAdjustment(v);
        }
    }

    private static class StepPointAmount
            extends DensityShapingFunctionParameter {
        private StepPointAmount(int spacetype, int stepPointNumber) {
            super(Type.STEP_POINT_AMOUNT, STEP_POINT_AMOUNT, spacetype,
                    stepPointNumber);
        }

        @Override
        public double getValue() {
            return DensityStepPoints
                    .getRecord(getSpacetype(), getStepPointNumber())
                    .get_StepPointAdjustment();
        }

        @Override
        public void setValue(double v) {
            DensityStepPoints.getRecord(getSpacetype(), getStepPointNumber())
                    .set_StepPointAdjustment(v);
        }
    }

    public static class Key {
        public final int spacetype;
        public final int stepPointNumber;

        public Key(int spacetype, int stepPointNumber) {
            this.spacetype = spacetype;
            this.stepPointNumber = stepPointNumber;
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + spacetype;
            result = prime * result + stepPointNumber;
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (!(obj instanceof Key))
                return false;
            Key other = (Key) obj;
            return (spacetype == other.spacetype)
                    && (stepPointNumber == other.stepPointNumber);
        }
        
        @Override
        public String toString() {
            return "Key(space=" + spacetype + ", step=" + stepPointNumber + ")";
        }
    }
}
