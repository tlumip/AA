package com.hbaspecto.pecas.sd.estimation;

import com.hbaspecto.pecas.sd.SpaceTypesI;

/**
 * A coefficient representing a dispersion parameter. Dispersion parameters
 * cannot be negative, so they use a transformation to keep them positive. By
 * default, this is a logarithmic transform (v' = ln(v)), which forces the
 * parameter to be positive but does not impose any upper bound. If an upper
 * bound is desired, use a logistic (sigmoid) transform instead (v' = h/4 * (2 -
 * ln(h/v - 1)), where h is the upper bound).
 * 
 */
public abstract class DispersionParameter extends SpaceTypeCoefficient {
    private Double sigmoidMaximum;

    protected DispersionParameter(Type type, String name, int spacetype) {
        super(type, name, spacetype);
    }

    @Override
    public double getTransformedValue() {
        if (sigmoidMaximum == null) {
            return Math.log(getValue());
        } else {
            double h = sigmoidMaximum;
            double v = getValue();
            return h / 4 * (2 - Math.log(h / v - 1));
        }
    }

    @Override
    public void setTransformedValue(double vt) {
        if (sigmoidMaximum == null) {
            setValue(Math.exp(vt));
        } else {
            double h = sigmoidMaximum;
            double v = h / (1 + Math.exp(2 - 4 * vt / h));
            setValue(v);
        }
    }

    @Override
    public double getTransformationDerivative() {
        if (sigmoidMaximum == null) {
            return 1 / getValue();
        } else {
            double h = sigmoidMaximum;
            double v = getValue();
            return h * h / (4 * v * (h - v));
        }
    }

    @Override
    public double getInverseTransformationDerivative() {
        if (sigmoidMaximum == null) {
            return getValue();
        } else {
            double h = sigmoidMaximum;
            double v = getValue();
            return 4 * v * (h - v) / (h * h);
        }
    }

    /**
     * Tells the dispersion parameter to use the logarithmic transform (the
     * default).
     * 
     * @return The parameter itself (for fluent calls)
     */
    public DispersionParameter useLogTransform() {
        sigmoidMaximum = null;
        return this;
    }

    /**
     * Tells the dispersion parameter to use the sigmoid transform.
     * 
     * @param max The upper bound of the sigmoid transform
     * @return The parameter itself (for fluent calls)
     */
    public DispersionParameter useSigmoidTransform(double max) {
        sigmoidMaximum = max;
        return this;
    }

    /**
     * Returns the top-level dispersion parameter for the given spacetype.
     */
    public static DispersionParameter getNoChangeDisp(int spacetype) {
        synchronized (coeffLock) {
            DispersionParameter coeff = (DispersionParameter) getExistingCoefficient(
                    Type.TOP_DISP, spacetype);
            if (coeff == null) {
                coeff = new NoChangeDispersion(spacetype);
                insertNewCoefficient(Type.TOP_DISP, spacetype, coeff);
            }
            return coeff;
        }
    }

    /**
     * Returns the change options dispersion parameter for the given spacetype.
     */
    public static DispersionParameter getChangeOptionsDisp(int spacetype) {
        synchronized (coeffLock) {
            DispersionParameter coeff = (DispersionParameter) getExistingCoefficient(
                    Type.CHANGE_DISP, spacetype);
            if (coeff == null) {
                coeff = new ChangeOptionsDispersion(spacetype);
                insertNewCoefficient(Type.CHANGE_DISP, spacetype, coeff);
            }
            return coeff;
        }
    }

    /**
     * Returns the demolish/derelict dispersion parameter for the given
     * spacetype.
     */
    public static DispersionParameter getDemolishDerelictDisp(int spacetype) {
        synchronized (coeffLock) {
            DispersionParameter coeff = (DispersionParameter) getExistingCoefficient(
                    Type.DECAY_DISP, spacetype);
            if (coeff == null) {
                coeff = new DemolishDerelictDispersion(spacetype);
                insertNewCoefficient(Type.DECAY_DISP, spacetype, coeff);
            }
            return coeff;
        }
    }

    /**
     * Returns the renovate/add space/build new dispersion parameter for the
     * given spacetype.
     */
    public static DispersionParameter getRenovateAddNewDisp(int spacetype) {
        synchronized (coeffLock) {
            DispersionParameter coeff = (DispersionParameter) getExistingCoefficient(
                    Type.GROW_DISP, spacetype);
            if (coeff == null) {
                coeff = new RenovateAddNewDispersion(spacetype);
                insertNewCoefficient(Type.GROW_DISP, spacetype, coeff);
            }
            return coeff;
        }
    }

    /**
     * Returns the add space/build new dispersion parameter for the given
     * spacetype.
     */
    public static DispersionParameter getAddNewDisp(int spacetype) {
        synchronized (coeffLock) {
            DispersionParameter coeff = (DispersionParameter) getExistingCoefficient(
                    Type.BUILD_DISP, spacetype);
            if (coeff == null) {
                coeff = new AddNewDispersion(spacetype);
                insertNewCoefficient(Type.BUILD_DISP, spacetype, coeff);
            }
            return coeff;
        }
    }

    /**
     * Returns the new spacetype dispersion parameter for the given spacetype.
     */
    public static DispersionParameter getNewTypeDisp(int spacetype) {
        synchronized (coeffLock) {
            DispersionParameter coeff = (DispersionParameter) getExistingCoefficient(
                    Type.NEW_TYPE_DISP, spacetype);
            if (coeff == null) {
                coeff = new NewTypeDispersion(spacetype);
                insertNewCoefficient(Type.NEW_TYPE_DISP, spacetype, coeff);
            }
            return coeff;
        }
    }

    /**
     * Returns the building intensity dispersion parameter for the given
     * spacetype.
     */
    public static DispersionParameter getIntensityDisp(int spacetype) {
        synchronized (coeffLock) {
            DispersionParameter coeff = (DispersionParameter) getExistingCoefficient(
                    Type.INTENSITY_DISP, spacetype);
            if (coeff == null) {
                coeff = new IntensityDispersion(spacetype);
                insertNewCoefficient(Type.INTENSITY_DISP, spacetype, coeff);
            }
            return coeff;
        }
    }

    // Types of dispersion parameters.

    private static class NoChangeDispersion extends DispersionParameter {
        private NoChangeDispersion(int spacetype) {
            super(Type.TOP_DISP, NO_CHANGE_DISP, spacetype);
        }

        @Override
        public double getValue() {
            return SpaceTypesI
                    .getAlreadyCreatedSpaceTypeBySpaceTypeID(getSpacetype())
                    .get_NochangeDispersionParameter();
        }

        @Override
        public void setValue(double v) {
            SpaceTypesI.getAlreadyCreatedSpaceTypeBySpaceTypeID(getSpacetype())
                    .set_NochangeDispersionParameter(v);
        }
    }

    private static class ChangeOptionsDispersion extends DispersionParameter {
        private ChangeOptionsDispersion(int spacetype) {
            super(Type.CHANGE_DISP, CHANGE_OPTIONS_DISP, spacetype);
        }

        @Override
        public double getValue() {
            return SpaceTypesI
                    .getAlreadyCreatedSpaceTypeBySpaceTypeID(getSpacetype())
                    .get_GkDispersionParameter();
        }

        @Override
        public void setValue(double v) {
            SpaceTypesI.getAlreadyCreatedSpaceTypeBySpaceTypeID(getSpacetype())
                    .set_GkDispersionParameter(v);
        }
    }

    private static class DemolishDerelictDispersion
            extends DispersionParameter {
        private DemolishDerelictDispersion(int spacetype) {
            super(Type.DECAY_DISP, DEMOLISH_DERELICT_DISP, spacetype);
        }

        @Override
        public double getValue() {
            return SpaceTypesI
                    .getAlreadyCreatedSpaceTypeBySpaceTypeID(getSpacetype())
                    .get_GwDispersionParameter();
        }

        @Override
        public void setValue(double v) {
            SpaceTypesI.getAlreadyCreatedSpaceTypeBySpaceTypeID(getSpacetype())
                    .set_GwDispersionParameter(v);
        }
    }

    private static class RenovateAddNewDispersion extends DispersionParameter {
        private RenovateAddNewDispersion(int spacetype) {
            super(Type.GROW_DISP, RENOVATE_ADD_NEW_DISP, spacetype);
        }

        @Override
        public double getValue() {
            return SpaceTypesI
                    .getAlreadyCreatedSpaceTypeBySpaceTypeID(getSpacetype())
                    .get_GzDispersionParameter();
        }

        @Override
        public void setValue(double v) {
            SpaceTypesI.getAlreadyCreatedSpaceTypeBySpaceTypeID(getSpacetype())
                    .set_GzDispersionParameter(v);
        }
    }

    private static class AddNewDispersion extends DispersionParameter {
        private AddNewDispersion(int spacetype) {
            super(Type.BUILD_DISP, ADD_NEW_DISP, spacetype);
        }

        @Override
        public double getValue() {
            return SpaceTypesI
                    .getAlreadyCreatedSpaceTypeBySpaceTypeID(getSpacetype())
                    .get_GyDispersionParameter();
        }

        @Override
        public void setValue(double v) {
            SpaceTypesI.getAlreadyCreatedSpaceTypeBySpaceTypeID(getSpacetype())
                    .set_GyDispersionParameter(v);
        }
    }

    private static class NewTypeDispersion extends DispersionParameter {
        private NewTypeDispersion(int spacetype) {
            super(Type.NEW_TYPE_DISP, NEW_TYPE_DISP, spacetype);
        }

        @Override
        public double getValue() {
            return SpaceTypesI
                    .getAlreadyCreatedSpaceTypeBySpaceTypeID(getSpacetype())
                    .get_NewTypeDispersionParameter();
        }

        @Override
        public void setValue(double v) {
            SpaceTypesI.getAlreadyCreatedSpaceTypeBySpaceTypeID(getSpacetype())
                    .set_NewTypeDispersionParameter(v);
        }
    }

    private static class IntensityDispersion extends DispersionParameter {
        private IntensityDispersion(int spacetype) {
            super(Type.INTENSITY_DISP, INTENSITY_DISP, spacetype);
        }

        @Override
        public double getValue() {
            return SpaceTypesI
                    .getAlreadyCreatedSpaceTypeBySpaceTypeID(getSpacetype())
                    .get_IntensityDispersionParameter();
        }

        @Override
        public void setValue(double v) {
            SpaceTypesI.getAlreadyCreatedSpaceTypeBySpaceTypeID(getSpacetype())
                    .set_IntensityDispersionParameter(v);
        }
    }
}
