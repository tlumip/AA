package com.hbaspecto.pecas.sd.estimation;

import java.util.HashMap;
import java.util.Map;

import com.hbaspecto.discreteChoiceModelling.Coefficient;
import com.hbaspecto.pecas.sd.orm.TazGroups;

public abstract class SpaceTypeTazGroupCoefficient implements Coefficient {
    public static enum Type implements CoefficientType {
        CONSTRUCTION_CONST;

        @Override
        public String getTypeName() {
            return "TazGroupSpaceConstant";
        }
    }
    
    private String name;
    private int tazGroup;
    private int spacetype;

    public static final String CONSTRUCTION_CONST = "groupspaceconst";

    private static final Object coeffLock = new Object();
    private static final Map<Key, SpaceTypeTazGroupCoefficient> theCoeffs = new HashMap<>();

    protected SpaceTypeTazGroupCoefficient(String name, int spacetype,
            int tazGroup) {
        this.name = name;
        this.spacetype = spacetype;
        this.tazGroup = tazGroup;
    }

    public int getSpacetype() {
        return spacetype;
    }

    public int getTazGroup() {
        return tazGroup;
    }

    @Override
    public String getName() {
        return name + "-" + spacetype + "-" + tazGroup;
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

    @Override
    public String toString() {
        return getName();
    }

    @Override
    public CoefficientType getType() {
        return Type.CONSTRUCTION_CONST;
    }

    public static SpaceTypeTazGroupCoefficient getConstructionConstant(
            int spacetype, int tazGroup) {
        synchronized (coeffLock) {
            Key key = new Key(spacetype, tazGroup);
            SpaceTypeTazGroupCoefficient coeff = theCoeffs.get(key);
            if (coeff == null) {
                coeff = new ConstructionConstant(spacetype, tazGroup);
                theCoeffs.put(key, coeff);
            }
            return coeff;
        }
    }

    private static class ConstructionConstant
            extends SpaceTypeTazGroupCoefficient {
        private ConstructionConstant(int spacetype, int tazGroup) {
            super(CONSTRUCTION_CONST, spacetype, tazGroup);
        }

        @Override
        public double getValue() {
            return TazGroups.getTazGroup(getTazGroup())
                    .getConstructionConstantForSpaceType(getSpacetype());
        }

        @Override
        public void setValue(double v) {
            TazGroups group = TazGroups.getTazGroup(getTazGroup());
            group.setOrCreateConstructionConstantForSpaceType(getSpacetype(),
                    v);
        }
    }

    private static class Key {
        private int spacetype;
        private int tazGroup;

        private Key(int spacetype, int tazGroup) {
            this.spacetype = spacetype;
            this.tazGroup = tazGroup;
        }

        @Override
        public boolean equals(Object other) {
            if (other == this) {
                return true;
            } else if (!(other instanceof Key)) {
                return false;
            } else {
                Key k = (Key) other;
                return k.spacetype == this.spacetype
                        && k.tazGroup == this.tazGroup;
            }
        }

        @Override
        public int hashCode() {
            int result = 17;
            result = 31 * result + spacetype;
            result = 31 * result + tazGroup;
            return result;
        }

        @Override
        public String toString() {
            return "Key(space type=" + spacetype + ", taz group=" + tazGroup
                    + ")";
        }
    }
}
