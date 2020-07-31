/*
 *  Copyright 2005 HBA Specto Incorporated
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */
package com.hbaspecto.pecas.aa.activities;

import java.io.Writer;

import org.apache.log4j.Logger;

import com.hbaspecto.discreteChoiceModelling.AggregateAlternative;
import com.hbaspecto.pecas.ChoiceModelOverflowException;
import com.hbaspecto.pecas.OverflowException;
import com.hbaspecto.pecas.aa.commodity.Commodity;
import com.hbaspecto.pecas.aa.commodity.CommodityZUtility;
import com.hbaspecto.pecas.aa.technologyChoice.ConsumptionFunction;
import com.hbaspecto.pecas.aa.technologyChoice.ProductionFunction;
import com.hbaspecto.pecas.zones.AbstractZone;
import com.hbaspecto.pecas.zones.PECASZone;

import drasys.or.matrix.VectorI;
import no.uib.cipr.matrix.AbstractVector;

/**
 * Attributes (and methods) that relate to a particular ProductionActivity in a particular zone.  Includes the quantity, net
 * tax, and any constraints.  Also a zone-specific Utility. This implements the "Alternative" interface because the total
 * amount of activity is allocated amongst these with a logit model.
 *
 * @author John Abraham
 */
public abstract class AggregateDistribution extends AmountInZone implements AggregateAlternative {
	
	private boolean lockUtilities = false;
    protected static transient Logger logger =
            Logger.getLogger("com.pb.models.pecas");
    double derivative;
    ConsumptionFunction lastConsumptionFunction;
    ProductionFunction lastProductionFunction;

    static int numdebug = 0;
    boolean utilitiesValid = false;
    protected double[] buyingCommodityUtilities;
    protected CommodityZUtility[] buyingZUtilities;
    protected CommodityZUtility[] sellingZUtilities;
    protected double[] sellingCommodityUtilities;

    public AggregateDistribution(ProductionActivity p, PECASZone t) {
        super(p, t);
    }
    
    public void initializeZUtilities() {
        if (buyingZUtilities == null) {
        	buyingZUtilities = new CommodityZUtility[lastConsumptionFunction.size()];
            for (int c = 0; c < lastConsumptionFunction.size(); c++) {
                Commodity com = (Commodity) lastConsumptionFunction.commodityAt(c);
                if (com == null) buyingZUtilities[c] = null;
                else buyingZUtilities[c] = com.retrieveCommodityZUtility(getMyZone(), false);
            }
        }
        if (sellingZUtilities == null) {
        	sellingZUtilities = new CommodityZUtility[lastProductionFunction.size()];
            for (int c = 0; c < lastProductionFunction.size(); c++) {
                Commodity com = (Commodity) lastProductionFunction.commodityAt(c);
                if (com == null) sellingZUtilities[c] = null;
                else sellingZUtilities[c] = com.retrieveCommodityZUtility(getMyZone(), true);
            }
        }
    	if (isLockUtilities() && utilitiesValid) return;

    	if (buyingCommodityUtilities == null) buyingCommodityUtilities = new double[lastConsumptionFunction.size()];
        if (sellingCommodityUtilities == null) sellingCommodityUtilities = new double[lastProductionFunction.size()];
        int c = 0;
        for (CommodityZUtility z : buyingZUtilities) {
        	if (z == null) {
        		buyingCommodityUtilities[c] = 0;
        	} else {
                try {
                    buyingCommodityUtilities[c] = z.myCommodity.calcZUtility(getMyZone(), false);
                } catch (OverflowException e1) {
                    buyingCommodityUtilities[c] = Double.NaN;
                }
        	}
        	c++;
        }
        c = 0;
        for (CommodityZUtility z : sellingZUtilities) {
        	if (z == null) {
                sellingCommodityUtilities[c] = 0;
            } else {
                try {
                    sellingCommodityUtilities[c] = z.myCommodity.calcZUtility(getMyZone(), true);
                } catch (OverflowException e1) {
                    sellingCommodityUtilities[c] = Double.NaN;
                }
            }
            c++;
        }
        if (isLockUtilities()) utilitiesValid = true;
    }



    /**
     * Method updateLocationUtilityTerms.
     * @throws ChoiceModelOverflowException 
     */
    public abstract void writeLocationUtilityTerms(Writer w) throws ChoiceModelOverflowException;

    /**
     * Calculate a location disutility based on a different make and use table
     *
     * @param cf use table
     * @param pf make table
     */


    public double calcLocationUtility(ConsumptionFunction cf, ProductionFunction pf, double higherLevelDispersionParameter) throws OverflowException {
    	boolean debug = false;
        return calcLocationUtilityDebug(cf, pf, debug, higherLevelDispersionParameter);
    }

    public abstract double calcLocationUtilityNoSizeEffect(ConsumptionFunction cf, ProductionFunction pf) throws OverflowException;

    public abstract double calcLocationUtilityDebug(ConsumptionFunction cf, ProductionFunction pf, boolean debug, double higherLevelDispersionParameter) throws OverflowException;

    public double getUtility(double higherLevelDispersionParameter) throws ChoiceModelOverflowException {
        try {
            return calcLocationUtility(myProductionActivity.getConsumptionFunction(),
                    myProductionActivity.getProductionFunction(), higherLevelDispersionParameter);
        } catch (OverflowException e) {
            throw new ChoiceModelOverflowException(e.toString());}
    }

    public double getUtilityNoSizeEffect() throws ChoiceModelOverflowException {
        try {
            return calcLocationUtilityNoSizeEffect(myProductionActivity.getConsumptionFunction(),
                    myProductionActivity.getProductionFunction());
        } catch (OverflowException e) {
            throw new ChoiceModelOverflowException(e.toString());}
    }

    public void setCommoditiesBoughtAndSold() throws OverflowException {
        setCommoditiesBoughtAndSold(myProductionActivity.getConsumptionFunction(),
                myProductionActivity.getProductionFunction());
    }


    public String toString() {
        return myProductionActivity + " in " + getMyZone();
    };


    public void setAggregateQuantity(double amount, double derivative) throws ChoiceModelOverflowException {
        if (Double.isNaN(amount) || Double.isInfinite(amount)) {
            logger.fatal("amount in zone is NaN/Infinite " + this + " previous quantity:" + getQuantity() + " -- try less agressive step size...");
            logger.fatal("following (between ***) is the utility calculation for this location");
            logger.fatal("**********************************************");
            try {
                calcLocationUtilityDebug(lastConsumptionFunction, lastProductionFunction, true, ((AggregateActivity) myProductionActivity).getLocationDispersionParameter());
            } catch (OverflowException e) {
                e.printStackTrace();
            }
            logger.fatal("**********************************************");
            throw new Error("amount in zone is NaN/Infinite " + this + " previous quantity:" + getQuantity() + " -- try less agressive step size...");
        }
        setQuantity(amount);
        this.derivative = derivative;
        try {
            setCommoditiesBoughtAndSold();
        } catch (OverflowException e) {
            throw new ChoiceModelOverflowException(e.toString());
        }
    }

    public abstract void setCommoditiesBoughtAndSold(ConsumptionFunction cf, ProductionFunction pf) throws OverflowException;
    
    /**
     * @param averagePriceSurplusMatrix
     */
    protected abstract void allocateLocationChoiceAveragePriceDerivatives(double totalActivityQuantity, double[][] averagePriceSurplusMatrix, AbstractVector thisLocationByPrices) throws OverflowException;
    

    /**
     * @param averagePriceSurplusMatrix
     * @param thisLocationByPrices
     */
    public void addTwoComponentsOfDerivativesToAveragePriceMatrix(double activityAmount, double[][] averagePriceSurplusMatrix, AbstractVector thisLocationByPrices) {
        if (lastConsumptionFunction == null) lastConsumptionFunction = myProductionActivity.getConsumptionFunction();
        if (lastProductionFunction == null) lastProductionFunction = myProductionActivity.getProductionFunction();
        // This is a product rule formula.  AmountMade = AmountOfActivity * MakeCoefficient.
        // d(AmountMade)/dx = d(AmountOfActivity)/dx*MakeCoefficient + AmountOfActivity * d(MakeCoefficient)/dx
         //first term is moving activity around to places where production functinos are different
        initializeZUtilities();
        try {
           allocateLocationChoiceAveragePriceDerivatives(activityAmount, averagePriceSurplusMatrix, thisLocationByPrices);  //21.81 % of time here but only 9.41% in callee
        } catch (OverflowException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
        //second term is moving technology around within the same zone.
        allocateProductionChoiceAveragePriceDerivatives(averagePriceSurplusMatrix); // 45.98% of time here but only 7.47% in callee
    }
    
    /**
     * @param averagePriceSurplusMatrix
     */
    protected abstract void allocateProductionChoiceAveragePriceDerivatives(double[][] averagePriceSurplusMatrix);

    public abstract double[] calculateLocationUtilityWRTAveragePrices();

	boolean isLockUtilities() {
		return lockUtilities;
	}

	public void setLockUtilities(boolean lockUtilities) {
		this.lockUtilities = lockUtilities;
		if (!lockUtilities) utilitiesValid = false;
		
	}
}
