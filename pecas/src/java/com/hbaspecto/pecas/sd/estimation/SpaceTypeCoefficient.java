package com.hbaspecto.pecas.sd.estimation;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

import com.hbaspecto.discreteChoiceModelling.Coefficient;
import com.hbaspecto.pecas.sd.SpaceTypesI;

/**
 * A coefficient that has a single spacetype it applies to. This class provides
 * static methods to retrieve each coefficient type. They all guarantee that
 * they will return the same object every time they are called with the same
 * parameters, meaning that they can be compared by object identity.
 */
public abstract class SpaceTypeCoefficient implements Coefficient {

    public static interface SpaceTypeCoefficientType extends CoefficientType {
        /**
         * Indicates whether this coefficient's space type represents the new
         * space type rather than the existing space type.
         * 
         * @return
         */
        public boolean isTo();
    }
    
    public static enum Type implements SpaceTypeCoefficientType {
        // Transition constants
        NO_CHANGE_CONST("NoChangeConstant"),
        DEMOLISH_CONST("DemolishConstant"),
        DERELICT_CONST("DerelictConstant"),
        RENOVATE_CONST("RenovateConstant"),
        RENOVATE_DERELICT_CONST("RenovateDerelictConstant"),
        ADD_CONST("AddConstant"),
        NEW_FROM_CONST("NewFromConstant"),
        NEW_TO_CONST("NewToConstant", true),
        COST_MODIFIER("CostModifier", true),

        // Dispersion parameters
        TOP_DISP("TopDispersion"),
        CHANGE_DISP("ChangeDispersion"),
        DECAY_DISP("DecayDispersion"),
        GROW_DISP("GrowDispersion"),
        BUILD_DISP("BuildDispersion"),
        NEW_TYPE_DISP("NewTypeDispersion"),
        INTENSITY_DISP("IntensityDispersion", true);

        private String typeName;
        private boolean isTo;

        private Type(String typeName) {
            this(typeName, false);
        }

        private Type(String typeName, boolean isTo) {
            this.typeName = typeName;
            this.isTo = isTo;
        }

        @Override
        public String getTypeName() {
            return typeName;
        }
        
        public boolean isTo() {
            return isTo;
        }
    }

    private SpaceTypeCoefficientType type;
    private String name;
    private int spacetype;

    // Transition constants
    public static final String NO_CHANGE_CONST = "ncconst";
    public static final String DEMOLISH_TRANSITION_CONST = "democonst";
    public static final String DERELICT_TRANSITION_CONST = "drltconst";
    public static final String RENOVATE_TRANSITION_CONST = "renoconst";
    public static final String RENOVATE_DERELICT_CONST = "rendconst";
    public static final String ADD_TRANSITION_CONST = "addconst";
    public static final String NEW_FROM_TRANSITION_CONST = "newfromconst";
    public static final String NEW_TO_TRANSITION_CONST = "newtoconst";
    public static final String COST_MODIFIER = "costmod";

    // Dispersion parameters
    public static final String NO_CHANGE_DISP = "topdisp";
    public static final String CHANGE_OPTIONS_DISP = "chdisp";
    public static final String DEMOLISH_DERELICT_DISP = "dddisp";
    public static final String RENOVATE_ADD_NEW_DISP = "randisp";
    public static final String ADD_NEW_DISP = "andisp";
    public static final String NEW_TYPE_DISP = "typdisp";
    public static final String INTENSITY_DISP = "intdisp";

    protected static final Object coeffLock = new Object();
    private static final Map<Type, Map<Integer, SpaceTypeCoefficient>> theCoeffs = new EnumMap<>(
            Type.class);

    protected SpaceTypeCoefficient(SpaceTypeCoefficientType type, String name, int spacetype) {
        this.type = type;
        this.name = name;
        this.spacetype = spacetype;
    }

    @Override
    public String getName() {
        return name + "-" + spacetype;
    }

    @Override
    public double getTransformedValue() {
        return getValue();
    }

    @Override
    public void setTransformedValue(double v) {
        setValue(v);
    }

    @Override
    public double getTransformationDerivative() {
        return 1;
    }

    @Override
    public double getInverseTransformationDerivative() {
        return 1;
    }

    public int getSpacetype() {
        return spacetype;
    }

    @Override
    public String toString() {
        return getName();
    }

    @Override
    public SpaceTypeCoefficientType getType() {
        return type;
    }

    // Returns null if the coefficient doesn't exist.
    protected static SpaceTypeCoefficient getExistingCoefficient(Type type,
            int spacetype) {
        if (theCoeffs.containsKey(type)) {
            Map<Integer, SpaceTypeCoefficient> spaceTypeCoeffs = theCoeffs
                    .get(type);
            if (spaceTypeCoeffs.containsKey(spacetype))
                return spaceTypeCoeffs.get(spacetype);
            else
                return null;
        } else
            return null;
    }

    protected static void insertNewCoefficient(Type type, int spacetype,
            SpaceTypeCoefficient newCoeff) {
        if (theCoeffs.containsKey(type))
            theCoeffs.get(type).put(spacetype, newCoeff);
        else {
            HashMap<Integer, SpaceTypeCoefficient> spaceTypeCoeffs = new HashMap<Integer, SpaceTypeCoefficient>();
            theCoeffs.put(type, spaceTypeCoeffs);
            spaceTypeCoeffs.put(spacetype, newCoeff);
        }
    }

    public static SpaceTypeCoefficient getConstByType(Type type,
            int spacetype) {
        switch (type) {
        case NO_CHANGE_CONST:
            return getNoChangeConst(spacetype);
        case DEMOLISH_CONST:
            return getDemolishTransitionConst(spacetype);
        case DERELICT_CONST:
            return getDerelictTransitionConst(spacetype);
        case RENOVATE_CONST:
            return getRenovateTransitionConst(spacetype);
        case RENOVATE_DERELICT_CONST:
            return getRenovateDerelictConst(spacetype);
        case ADD_CONST:
            return getAddTransitionConst(spacetype);
        case NEW_FROM_CONST:
            return getNewFromTransitionConst(spacetype);
        case NEW_TO_CONST:
            return getNewToTransitionConst(spacetype);
        case COST_MODIFIER:
            return getCostModifier(spacetype);
        case TOP_DISP:
            return getNoChangeDisp(spacetype);
        case CHANGE_DISP:
            return getChangeOptionsDisp(spacetype);
        case DECAY_DISP:
            return getDemolishDerelictDisp(spacetype);
        case GROW_DISP:
            return getRenovateAddNewDisp(spacetype);
        case BUILD_DISP:
            return getAddNewDisp(spacetype);
        case NEW_TYPE_DISP:
            return getNewTypeDisp(spacetype);
        case INTENSITY_DISP:
            return getIntensityDisp(spacetype);
        default:
            throw new AssertionError("Unknown coefficient type");
        }
    }

    /**
     * Returns the no-change transition constant for the given spacetype.
     */
    public static SpaceTypeCoefficient getNoChangeConst(int spacetype) {
        synchronized (coeffLock) {
            SpaceTypeCoefficient coeff = getExistingCoefficient(
                    Type.NO_CHANGE_CONST, spacetype);
            if (coeff == null) {
                coeff = new NoChangeConstant(spacetype);
                insertNewCoefficient(Type.NO_CHANGE_CONST, spacetype, coeff);
            }
            return coeff;
        }
    }

    /**
     * Returns the demolish transition constant for the given spacetype.
     */
    public static SpaceTypeCoefficient getDemolishTransitionConst(
            int spacetype) {
        synchronized (coeffLock) {
            SpaceTypeCoefficient coeff = getExistingCoefficient(
                    Type.DEMOLISH_CONST, spacetype);
            if (coeff == null) {
                coeff = new DemolishTransitionConstant(spacetype);
                insertNewCoefficient(Type.DEMOLISH_CONST, spacetype, coeff);
            }
            return coeff;
        }
    }

    /**
     * Returns the derelict transition constant for the given spacetype.
     */
    public static SpaceTypeCoefficient getDerelictTransitionConst(
            int spacetype) {
        synchronized (coeffLock) {
            SpaceTypeCoefficient coeff = getExistingCoefficient(
                    Type.DERELICT_CONST, spacetype);
            if (coeff == null) {
                coeff = new DerelictTransitionConstant(spacetype);
                insertNewCoefficient(Type.DERELICT_CONST, spacetype, coeff);
            }
            return coeff;
        }
    }

    /**
     * Returns the renovate transition constant for non-derelict space of the
     * given spacetype.
     */
    public static SpaceTypeCoefficient getRenovateTransitionConst(
            int spacetype) {
        synchronized (coeffLock) {
            SpaceTypeCoefficient coeff = getExistingCoefficient(
                    Type.RENOVATE_CONST, spacetype);
            if (coeff == null) {
                coeff = new RenovateTransitionConstant(spacetype);
                insertNewCoefficient(Type.RENOVATE_CONST, spacetype, coeff);
            }
            return coeff;
        }
    }

    /**
     * Returns the renovate transition constant for derelict space of the given
     * spacetype.
     */
    public static SpaceTypeCoefficient getRenovateDerelictConst(int spacetype) {
        synchronized (coeffLock) {
            SpaceTypeCoefficient coeff = getExistingCoefficient(
                    Type.RENOVATE_DERELICT_CONST, spacetype);
            if (coeff == null) {
                coeff = new RenovateDerelictConstant(spacetype);
                insertNewCoefficient(Type.RENOVATE_DERELICT_CONST, spacetype,
                        coeff);
            }
            return coeff;
        }
    }

    /**
     * Returns the add space transition constant for the given spacetype.
     */
    public static SpaceTypeCoefficient getAddTransitionConst(int spacetype) {
        synchronized (coeffLock) {
            SpaceTypeCoefficient coeff = getExistingCoefficient(Type.ADD_CONST,
                    spacetype);
            if (coeff == null) {
                coeff = new AddTransitionConstant(spacetype);
                insertNewCoefficient(Type.ADD_CONST, spacetype, coeff);
            }
            return coeff;
        }
    }

    /**
     * Returns the build new from transition constant for the given spacetype,
     * constant for building anything new on a parcel of this type
     */
    public static SpaceTypeCoefficient getNewFromTransitionConst(
            int spacetype) {
        synchronized (coeffLock) {
            SpaceTypeCoefficient coeff = getExistingCoefficient(
                    Type.NEW_FROM_CONST, spacetype);
            if (coeff == null) {
                coeff = new NewFromTransitionConstant(spacetype);
                insertNewCoefficient(Type.NEW_FROM_CONST, spacetype, coeff);
            }
            return coeff;
        }
    }

    /**
     * Returns the build new to transition constant for the given spacetype,
     * constant for building this type new on any existing.
     */
    public static SpaceTypeCoefficient getNewToTransitionConst(int spacetype) {
        synchronized (coeffLock) {
            SpaceTypeCoefficient coeff = getExistingCoefficient(
                    Type.NEW_TO_CONST, spacetype);
            if (coeff == null) {
                coeff = new NewToTransitionConstant(spacetype);
                insertNewCoefficient(Type.NEW_TO_CONST, spacetype, coeff);
            }
            return coeff;
        }
    }

    public static SpaceTypeCoefficient getCostModifier(int spacetype) {
        synchronized (coeffLock) {
            SpaceTypeCoefficient coeff = getExistingCoefficient(
                    Type.COST_MODIFIER, spacetype);
            if (coeff == null) {
                coeff = new CostModifier(spacetype);
                insertNewCoefficient(Type.COST_MODIFIER, spacetype, coeff);
            }
            return coeff;
        }
    }

    /**
     * Returns the top-level dispersion parameter for the given spacetype.
     */
    public static SpaceTypeCoefficient getNoChangeDisp(int spacetype) {
        return DispersionParameter.getNoChangeDisp(spacetype);
    }

    /**
     * Returns the change options dispersion parameter for the given spacetype.
     */
    public static SpaceTypeCoefficient getChangeOptionsDisp(int spacetype) {
        return DispersionParameter.getChangeOptionsDisp(spacetype);
    }

    /**
     * Returns the demolish/derelict dispersion parameter for the given
     * spacetype.
     */
    public static SpaceTypeCoefficient getDemolishDerelictDisp(int spacetype) {
        return DispersionParameter.getDemolishDerelictDisp(spacetype);
    }

    /**
     * Returns the renovate/add space/build new dispersion parameter for the
     * given spacetype.
     */
    public static SpaceTypeCoefficient getRenovateAddNewDisp(int spacetype) {
        return DispersionParameter.getRenovateAddNewDisp(spacetype);
    }

    /**
     * Returns the add space/build new dispersion parameter for the given
     * spacetype.
     */
    public static SpaceTypeCoefficient getAddNewDisp(int spacetype) {
        return DispersionParameter.getAddNewDisp(spacetype);
    }

    /**
     * Returns the new spacetype dispersion parameter for the given spacetype.
     */
    public static SpaceTypeCoefficient getNewTypeDisp(int spacetype) {
        return DispersionParameter.getNewTypeDisp(spacetype);
    }

    /**
     * Returns the building intensity dispersion parameter for the given
     * spacetype.
     */
    public static SpaceTypeCoefficient getIntensityDisp(int spacetype) {
        return DispersionParameter.getIntensityDisp(spacetype);
    }

    // A subclass for each type of coefficient.

    private static class NoChangeConstant extends SpaceTypeCoefficient {
        private NoChangeConstant(int spacetype) {
            super(Type.NO_CHANGE_CONST, NO_CHANGE_CONST, spacetype);
        }

        @Override
        public double getValue() {
            return SpaceTypesI
                    .getAlreadyCreatedSpaceTypeBySpaceTypeID(getSpacetype())
                    .get_NoChangeTransitionConst();
        }

        @Override
        public void setValue(double v) {
            SpaceTypesI.getAlreadyCreatedSpaceTypeBySpaceTypeID(getSpacetype())
                    .set_NoChangeTransitionConst(v);
        }
    }

    private static class DemolishTransitionConstant
            extends SpaceTypeCoefficient {
        private DemolishTransitionConstant(int spacetype) {
            super(Type.DEMOLISH_CONST, DEMOLISH_TRANSITION_CONST, spacetype);
        }

        @Override
        public double getValue() {
            return SpaceTypesI
                    .getAlreadyCreatedSpaceTypeBySpaceTypeID(getSpacetype())
                    .get_DemolishTransitionConst();
        }

        @Override
        public void setValue(double v) {
            SpaceTypesI.getAlreadyCreatedSpaceTypeBySpaceTypeID(getSpacetype())
                    .set_DemolishTransitionConst(v);
        }
    }

    private static class DerelictTransitionConstant
            extends SpaceTypeCoefficient {
        private DerelictTransitionConstant(int spacetype) {
            super(Type.DERELICT_CONST, DERELICT_TRANSITION_CONST, spacetype);
        }

        @Override
        public double getValue() {
            return SpaceTypesI
                    .getAlreadyCreatedSpaceTypeBySpaceTypeID(getSpacetype())
                    .get_DerelictTransitionConst();
        }

        @Override
        public void setValue(double v) {
            SpaceTypesI.getAlreadyCreatedSpaceTypeBySpaceTypeID(getSpacetype())
                    .set_DerelictTransitionConst(v);
        }
    }

    private static class RenovateTransitionConstant
            extends SpaceTypeCoefficient {
        private RenovateTransitionConstant(int spacetype) {
            super(Type.RENOVATE_CONST, RENOVATE_TRANSITION_CONST, spacetype);
        }

        @Override
        public double getValue() {
            return SpaceTypesI
                    .getAlreadyCreatedSpaceTypeBySpaceTypeID(getSpacetype())
                    .get_RenovateTransitionConst();
        }

        @Override
        public void setValue(double v) {
            SpaceTypesI.getAlreadyCreatedSpaceTypeBySpaceTypeID(getSpacetype())
                    .set_RenovateTransitionConst(v);
        }
    }

    private static class RenovateDerelictConstant extends SpaceTypeCoefficient {
        private RenovateDerelictConstant(int spacetype) {
            super(Type.RENOVATE_DERELICT_CONST, RENOVATE_DERELICT_CONST, spacetype);
        }

        @Override
        public double getValue() {
            return SpaceTypesI
                    .getAlreadyCreatedSpaceTypeBySpaceTypeID(getSpacetype())
                    .get_RenovateDerelictTransitionConst();
        }

        @Override
        public void setValue(double v) {
            SpaceTypesI.getAlreadyCreatedSpaceTypeBySpaceTypeID(getSpacetype())
                    .set_RenovateDerelictTransitionConst(v);
        }
    }

    private static class AddTransitionConstant extends SpaceTypeCoefficient {
        private AddTransitionConstant(int spacetype) {
            super(Type.ADD_CONST, ADD_TRANSITION_CONST, spacetype);
        }

        @Override
        public double getValue() {
            return SpaceTypesI
                    .getAlreadyCreatedSpaceTypeBySpaceTypeID(getSpacetype())
                    .get_AddTransitionConst();
        }

        @Override
        public void setValue(double v) {
            SpaceTypesI.getAlreadyCreatedSpaceTypeBySpaceTypeID(getSpacetype())
                    .set_AddTransitionConst(v);
        }
    }

    private static class NewFromTransitionConstant
            extends SpaceTypeCoefficient {
        private NewFromTransitionConstant(int spacetype) {
            super(Type.NEW_FROM_CONST, NEW_FROM_TRANSITION_CONST, spacetype);
        }

        @Override
        public double getValue() {
            return SpaceTypesI
                    .getAlreadyCreatedSpaceTypeBySpaceTypeID(getSpacetype())
                    .get_NewFromTransitionConst();
        }

        @Override
        public void setValue(double v) {
            SpaceTypesI.getAlreadyCreatedSpaceTypeBySpaceTypeID(getSpacetype())
                    .set_NewFromTransitionConst(v);
        }
    }

    private static class NewToTransitionConstant extends SpaceTypeCoefficient {
        private NewToTransitionConstant(int spacetype) {
            super(Type.NEW_TO_CONST, NEW_TO_TRANSITION_CONST, spacetype);
        }

        @Override
        public double getValue() {
            return SpaceTypesI
                    .getAlreadyCreatedSpaceTypeBySpaceTypeID(getSpacetype())
                    .get_NewToTransitionConst();
        }

        @Override
        public void setValue(double v) {
            SpaceTypesI.getAlreadyCreatedSpaceTypeBySpaceTypeID(getSpacetype())
                    .set_NewToTransitionConst(v);
        }

        @Override
        public Type getType() {
            return Type.NEW_TO_CONST;
        }
    }
    
    private static class CostModifier extends SpaceTypeCoefficient {
        private CostModifier(int spacetype) {
            super(Type.COST_MODIFIER, COST_MODIFIER, spacetype);
        }

        @Override
        public double getValue() {
            return SpaceTypesI.getAlreadyCreatedSpaceTypeBySpaceTypeID(getSpacetype())
                    .get_CostAdjustmentFactor();
        }

        @Override
        public void setValue(double v) {
            SpaceTypesI.getAlreadyCreatedSpaceTypeBySpaceTypeID(getSpacetype())
                    .set_CostAdjustmentFactor(v);
        }

    }
}
