package com.hbaspecto.pecas.sd.estimation;

import java.util.ArrayList;
import java.util.List;

import org.apache.log4j.Logger;

import com.hbaspecto.pecas.FormatLogger;
import com.hbaspecto.pecas.land.LandInventory;

public abstract class EstimationTarget {

    private double targetValue;
    private LandInventory land;

    protected void setLandInventory(LandInventory l) {
        land = l;
    }

    protected LandInventory getLandInventory() {
        return land;
    }

    public void setTargetValue(double targetValue) {
        this.targetValue = targetValue;
    }

    public double getTargetValue() {
        return targetValue;
    }

    /**
     * Returns the modelled value. This should be called after
     * <code>setModelledValue</code> has been invoked on every associated
     * <code>ExpectedValue</code>.
     * 
     * @return The modelled value.
     */
    public abstract double getModelledValue();

    /**
     * Returns the derivative of the modelled value with respect to each
     * coefficient. This should be called after <code>setModelledValue</code>
     * and <code>setDerivative</code> have been called on every associated
     * <code>ExpectedValue</code>.
     * 
     * @return The derivatives.
     */
    public abstract double[] getDerivatives();

    /**
     * Returns the list of expected value calculators whose values this target
     * depends on. Normally, if an <code>EstimationTarget</code> object also
     * implements <ExpectedValue>, its <code>getComponentTargets()</code> method
     * should return a collection containing only the object itself.
     * 
     * @return The list of components.
     */
    public abstract List<ExpectedValue> getAssociatedExpectedValues();

    public abstract String getName();

    protected static int[] parseSpaceTypeStrings(String... strings) {
        int[] result = new int[strings.length - 1];
        for (int i = 1; i < strings.length; i++) {
            int type = 0;
            try {
                type = Integer.valueOf(strings[i]).intValue();
            } catch (NumberFormatException e) {
                FormatLogger logger = new FormatLogger(
                        Logger.getLogger(EstimationTarget.class));
                logger.throwFatal("Can't interpret space type " + strings[i]);
            }
            result[i - 1] = type;
        }
        return result;
    }
    
    protected boolean in(int type, int[] spaceTypes) {
        for (int spaceType : spaceTypes) {
            if (spaceType == type) {
                return true;
            }
        }
        return false;
    }
    
    protected String joinHyphens(String name, int[] spaceTypes) {
        StringBuffer buf = new StringBuffer(name);
        for (int type : spaceTypes) {
            buf.append("-");
            buf.append(type);
        }
        return buf.toString();
    }
    
    protected String joinHyphens(String name, int region, int[] spaceTypes) {
        return joinHyphens(name + "-" + region, spaceTypes);
    }
    
    public static List<ExpectedValue> convertToExpectedValueObjects(
            List<EstimationTarget> targets) {
        List<ExpectedValue> result = new ArrayList<ExpectedValue>();
        for (EstimationTarget t : targets)
            result.addAll(t.getAssociatedExpectedValues());
        return result;
    }
    
    @Override
    public String toString() {
        return getName();
    }
}
