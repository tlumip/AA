package com.hbaspecto.pecas.sd.estimation;

import java.util.HashMap;
import java.util.Map;

import com.hbaspecto.discreteChoiceModelling.Coefficient;
import com.hbaspecto.pecas.sd.orm.TazGroups;

public abstract class TazGroupCoefficient implements Coefficient {
    public static enum Type implements CoefficientType {
        CONSTRUCTION_CONST;
        
        @Override
        public String getTypeName() {
            return "TazGroupConstant";
        }
    }
    
    private String name;
    private int tazGroup;

    public static final String CONSTRUCTION_CONST = "groupconst";

    private static final Object coeffLock = new Object();
    private static final Map<Integer, TazGroupCoefficient> theCoeffs = new HashMap<>();

    protected TazGroupCoefficient(String name, int tazGroup) {
        this.name = name;
        this.tazGroup = tazGroup;
    }

    public int getTazGroup() {
        return tazGroup;
    }

    @Override
    public String getName() {
        return name + "-" + tazGroup;
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

    public static TazGroupCoefficient getConstructionConstant(int tazGroupId) {
        synchronized (coeffLock) {
            TazGroupCoefficient coeff = theCoeffs.get(tazGroupId);
            if (coeff == null) {
                coeff = new ConstructionConstant(tazGroupId);
                theCoeffs.put(tazGroupId, coeff);
            }
            return coeff;
        }
    }

    private static class ConstructionConstant extends TazGroupCoefficient {
        private ConstructionConstant(int tazGroup) {
            super(CONSTRUCTION_CONST, tazGroup);
        }

        @Override
        public double getValue() {
            return TazGroups.getTazGroup(getTazGroup())
                    .getConstructionConstant();
        }

        @Override
        public void setValue(double v) {
            TazGroups group = TazGroups.getTazGroup(getTazGroup());
            group.setOrCreateConstructionConstant(v);
        }

    }
}
