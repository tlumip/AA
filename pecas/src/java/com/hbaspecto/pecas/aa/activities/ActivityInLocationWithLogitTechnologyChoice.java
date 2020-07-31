/*
 *  Copyright 2007 HBA Specto Incorpoated
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

import java.io.IOException;
import java.io.PrintWriter;
import java.io.Writer;
import java.util.Iterator;

import com.hbaspecto.matrix.SparseMatrix;
//import com.aparapi.Kernel;
//import com.aparapi.Range;
import com.hbaspecto.pecas.ChoiceModelOverflowException;
import com.hbaspecto.pecas.InvalidZUtilityError;
import com.hbaspecto.pecas.NoAlternativeAvailable;
import com.hbaspecto.pecas.OverflowException;
import com.hbaspecto.pecas.aa.commodity.Commodity;
import com.hbaspecto.pecas.aa.technologyChoice.ConsumptionFunction;
import com.hbaspecto.pecas.aa.technologyChoice.LogitTechnologyChoice;
import com.hbaspecto.pecas.aa.technologyChoice.LogitTechnologyChoiceConsumptionFunction;
import com.hbaspecto.pecas.aa.technologyChoice.LogitTechnologyChoiceProductionFunction;
import com.hbaspecto.pecas.aa.technologyChoice.ProductionFunction;
import com.hbaspecto.pecas.zones.AbstractZone;
import com.hbaspecto.pecas.zones.PECASZone;

import no.uib.cipr.matrix.AbstractVector;
import no.uib.cipr.matrix.Matrix;
import no.uib.cipr.matrix.MatrixEntry;

public class ActivityInLocationWithLogitTechnologyChoice extends
AggregateDistribution {

    public ActivityInLocationWithLogitTechnologyChoice(ProductionActivity p, PECASZone t) {
        super(p, t);
    }


    @Override
    public double calcLocationUtilityNoSizeEffect(ConsumptionFunction cf, ProductionFunction pf) throws OverflowException {
        lastConsumptionFunction = cf;
        lastProductionFunction = pf;


        initializeZUtilities();

        double utility;
        try {
            LogitTechnologyChoice tchoice = ((LogitTechnologyChoiceProductionFunction) pf).myTechnologyChoice;
            tchoice.setUtilities(buyingCommodityUtilities, sellingCommodityUtilities,myZone.zoneIndex);
            utility = tchoice.getUtilityNoSizeEffect();
        } catch (InvalidZUtilityError e) {
            logger.fatal(this.toString());
            throw e;
        }
        return utility + getLocationSpecificUtilityInclTaxes();            
    }


    @Override
    public double calcLocationUtilityDebug(ConsumptionFunction cf, ProductionFunction pf, boolean debug, double higherLevelDispersionParameter) throws OverflowException {
        lastConsumptionFunction = cf;
        lastProductionFunction = pf;


        initializeZUtilities();

        double utility;
        try {
            LogitTechnologyChoice tchoice = ((LogitTechnologyChoiceProductionFunction) pf).myTechnologyChoice;
            tchoice.setUtilities(buyingCommodityUtilities, sellingCommodityUtilities,myZone.zoneIndex);
            utility = tchoice.getUtility(1.0);
        } catch (InvalidZUtilityError e) {
            logger.fatal(this.toString());
            throw e;
        }
        if (debug) {
            // TODO more useful debugging information 
            LogitTechnologyChoice tchoice = ((LogitTechnologyChoiceProductionFunction) pf).myTechnologyChoice;
            logger.info(this + "production/consumption utility:" + tchoice.overallUtility(buyingCommodityUtilities, sellingCommodityUtilities, myZone.zoneIndex));
            StringBuffer bob = new StringBuffer();
            bob.append("buying:");
            for (int c = 0; c < buyingCommodityUtilities.length; c++) bob.append(buyingCommodityUtilities[c] + " ");
            logger.info(bob.toString());
            bob = new StringBuffer();
            bob.append("selling:");
            for (int c = 0; c < sellingCommodityUtilities.length; c++)
                bob.append(sellingCommodityUtilities[c] + " ");
            logger.info(bob.toString());
        }
        if (debug)
            logger.info("utility = " + utility + " + " + getLocationSpecificUtilityInclTaxes() +
                    " + " + (1 / higherLevelDispersionParameter * myProductionActivity.getSizeTermCoefficient() * Math.log(getAllocationSizeTerm())));
        return utility + getLocationSpecificUtilityInclTaxes() +
                1 / higherLevelDispersionParameter * myProductionActivity.getSizeTermCoefficient() * Math.log(getAllocationSizeTerm());
    }

    @Override
    public void setCommoditiesBoughtAndSold(ConsumptionFunction cf, ProductionFunction pf) throws OverflowException {
        LogitTechnologyChoice techChoice = ((LogitTechnologyChoiceConsumptionFunction)cf).myTechnologyChoice;
        boolean debug = false;

        //TODO allow user to specify activiy-zone base debug logs.
        /*if ((myProductionActivity.name.equals("AM5111Manu_imp") || myProductionActivity.name.equals("AX6111Manu_exp")) && myTaz.getZoneUserNumber()>1500) {
        	debug = true;
        }*/
        //if (myProductionActivity.name.equals("lt 10")) debug = true;
        if (getQuantity() == 0 && derivative == 0) return; // no-one here and no-one wants to come here, so they won't be buying or selling anything

        lastConsumptionFunction = cf;
        lastProductionFunction = pf;

        // first get the relevent ZUtilities and calculate the buying and selling utility values
        initializeZUtilities();

        // then figure out how much we want to buy and sell
        double[] buyingQuantities= null;
        double[] sellingQuantities = null;
        try {
            buyingQuantities = techChoice.calcBuyingAmounts(buyingCommodityUtilities, sellingCommodityUtilities, myZone.zoneIndex);
            sellingQuantities = techChoice.calcSellingAmounts(buyingCommodityUtilities, sellingCommodityUtilities, myZone.zoneIndex);
        } catch (NoAlternativeAvailable e) {
            if (getQuantity() !=0) {
                String msg = "Amount nonzero ("+getQuantity()+") "+ (constrained ? "(Constraint "+constraintQuantity+")" : "")+ " but no technology options available, possibly no floorspace suitable, in "+this;
                logger.error(msg);
                //throw new RuntimeException(msg,e);
            } 
            buyingQuantities = new double[buyingCommodityUtilities.length];
            sellingQuantities = new double[sellingCommodityUtilities.length];

        }

        // now figure out the self partial derivatives -- how much more you would buy (or sell) of things if the
        // attractiveness of doing so went up, all other things being equal.
        double[] amountsDerivatives = techChoice.amountsDerivatives(buyingCommodityUtilities, sellingCommodityUtilities, myZone.zoneIndex);
        double[] compositeUtilityDerivatives;
        try {
            compositeUtilityDerivatives = techChoice.compositeUtilityDerivatives(buyingCommodityUtilities, sellingCommodityUtilities, myZone.zoneIndex);
        } catch (NoAlternativeAvailable e1) {
            String msg = "No alternative available when calculating composite utility derivatives for "+this;
            logger.fatal(msg);
            logger.fatal("buyingCommodityUtilities :"+buyingCommodityUtilities);
            logger.fatal("sellingCommodityUtilities :"+sellingCommodityUtilities);
            throw new RuntimeException(msg);
        }
        // then set the flows to move it through the network
        int numCommodities = techChoice.getMyCommodityOrder().size();
        for (int c=0;c<numCommodities; c++) {
            Commodity commodity = techChoice.getMyCommodityOrder().get(c);
            if (Double.isNaN(buyingQuantities[c]) || Double.isInfinite(buyingQuantities[c])) {
                String error = "Error in consumption :" + myProductionActivity + " in " + myZone + " consumes " + buyingQuantities[c] + " of " + commodity;
                logger.error(error);
                throw new RuntimeException(error);
            }

            if (debug) {
                logger.info("In zone " + myZone.getZoneUserNumber() + " " + getQuantity() + " of " + myProductionActivity + " consumes " + buyingQuantities[c] * getQuantity() + " of " + commodity);
                logger.info("In zone " + myZone.getZoneUserNumber() + " " + getQuantity() + " of " + myProductionActivity + " produces " + sellingQuantities[c] * getQuantity() + " of " + commodity);
            }
            try {
                // first set the amount bought
                if (getQuantity() != 0) {
                    buyingZUtilities[c].changeQuantityBy(-getQuantity() * buyingQuantities[c]);
                    sellingZUtilities[c].changeQuantityBy(getQuantity() * sellingQuantities[c]);
                }
                // two components to partial derivative, first is because quantity bought or sold would change, second is because quantity of activity in zone would change.
                // add in one or both
                // (Product rule of derivatives)
                // Made = (amount of activity )* (make coeffificent)
                // M = a*m
                // d(M)/d(Uc) = d(a)/d(Uc)*m + a*d(m)/d(Uc)
                //            = d(a)/d(Ua)*d(Ua)/d(Uc)*m + a*d(m)/d(Uc)
                // a is getQuantity, if it's is zero the second term is zero
                // d(a)/d(Ua) is derivative.  It it's zero the first term is zero.
                if (derivative == 0) {
                    buyingZUtilities[c].changeDerivativeBy(-getQuantity() * amountsDerivatives[c]);
                    sellingZUtilities[c].changeDerivativeBy(getQuantity() * amountsDerivatives[c+numCommodities]);
                } else if (getQuantity() == 0) {
                    buyingZUtilities[c].changeDerivativeBy(derivative * compositeUtilityDerivatives[c] * buyingQuantities[c]);
                    // TODO use debugger or test case to check if the sign is right.
                    sellingZUtilities[c].changeDerivativeBy(derivative * compositeUtilityDerivatives[c+numCommodities]*sellingQuantities[c]);
                } else {
                    buyingZUtilities[c].changeDerivativeBy(derivative * compositeUtilityDerivatives[c] * buyingQuantities[c] - getQuantity() * amountsDerivatives[c]);
                    sellingZUtilities[c].changeDerivativeBy(derivative * compositeUtilityDerivatives[c+numCommodities]*sellingQuantities[c] +
                            getQuantity()*amountsDerivatives[c+numCommodities]);
                }

            } catch (OverflowException e) {
                logger.fatal("Error: " + myProductionActivity + " in " + myZone + " consumes " + buyingQuantities[c] + " of " + commodity + " leading to " + e);
                e.printStackTrace();
                throw new Error("Error: " + myProductionActivity + " in " + myZone + " consumes " + buyingQuantities[c] + " of " + commodity + " leading to " + e);
            }

        }
    }



    @Override
    public void writeLocationUtilityTerms(Writer w) throws ChoiceModelOverflowException {
        LogitTechnologyChoice techChoice = ((LogitTechnologyChoiceConsumptionFunction)lastConsumptionFunction).myTechnologyChoice;

        initializeZUtilities();

        try {
            // TODO should write out something about how each business condition contributes to attractiveness
            //w.write(productionUtility + ",");
            //w.write(consumptionUtility + ",");
            techChoice.setUtilities(buyingCommodityUtilities, sellingCommodityUtilities,myZone.zoneIndex);
            double technologyChoiceLogsum = techChoice.getUtility(1);
            w.write(technologyChoiceLogsum + ",");
            double dispersionParameter = ((AggregateActivity) myProductionActivity).getLocationDispersionParameter();
            double sizeTerm = 1 / dispersionParameter * myProductionActivity.getSizeTermCoefficient() * Math.log(getAllocationSizeTerm());
            w.write(sizeTerm + ",");
            w.write(getLocationSpecificUtilityInclTaxes() + ",");
            w.write(constrained + ",");
            w.write(constraintQuantity + ",");
            w.write(technologyChoiceLogsum + getActivityZoneConstant()+ sizeTerm + ",");
            w.write(technologyChoiceLogsum-techChoice.getUtilityNoSizeEffect() + ",");
            //w.write(technologyChoiceLogsum-techChoice.getUtilityNoSizeEffectThisLevel() + ",");
            w.write(getAllocationSizeTerm() + "," + getActivityZoneConstantForSizeTerms() + "\n");
        } catch (IOException e) {
            logger.fatal("Error in writing ActivityLocations");
            throw new RuntimeException(e);
        }
    }

    @Override
    protected void allocateLocationChoiceAveragePriceDerivatives(double totalActivityQuantity, double[][] averagePriceSurplusMatrix, AbstractVector locationChoiceDerivatives) throws OverflowException {
        LogitTechnologyChoice techChoice = ((LogitTechnologyChoiceConsumptionFunction) lastConsumptionFunction).myTechnologyChoice;

        // TODO don't call this twice in both location choice and production technology choice
        //initializeZUtilities();

        // then figure out how much we want to buy and sell
        double[] buyingNegativeQuantities = null;
        double[] sellingQuantities = null;
        try {
            buyingNegativeQuantities = techChoice.calcBuyingAmounts(buyingCommodityUtilities, sellingCommodityUtilities,myZone.zoneIndex);
            sellingQuantities = techChoice.calcSellingAmounts(buyingCommodityUtilities, sellingCommodityUtilities,myZone.zoneIndex);
        } catch (NoAlternativeAvailable e) {
            if (getQuantity() !=0) {
                String msg = "Amount nonzero ("+getQuantity()+") "+ (constrained ? "(Constraint "+constraintQuantity+")" : "")+ " but no technology options available, possibly no floorspace suitable, in "+this;
                logger.fatal(msg);
                throw new RuntimeException(msg,e);
            } else {
                buyingNegativeQuantities = new double[buyingCommodityUtilities.length];
                sellingQuantities = new double[sellingCommodityUtilities.length];
            }
        }
        //now multiply it by the location choice derivatives
        // TODO check if sparse matrix can be exploited here, many activities are not related to many commodities
        for (int cNum = 0; cNum < techChoice.getMyCommodityOrder().size(); cNum++) {
            //Commodity commodity = techChoice.myCommodityOrder.get(cNum);
            double buyingQuantity = totalActivityQuantity*buyingNegativeQuantities[cNum];
            double sellingQuantity = totalActivityQuantity*sellingQuantities[cNum];
            for (int c1=0;c1<locationChoiceDerivatives.size();c1++) {
                // this is if the price of one commodity goes up the quantity of activity here will go
                // down a bit, and hence to quantity bought/sold will also go down.
                if (Double.isNaN(buyingQuantity*locationChoiceDerivatives.get(c1))) {
                    logger.fatal(this+" effect of location change on quantity distribution through buying is NaN, commodities "+cNum+","+c1);
                    logger.fatal("buyingQuantity:"+buyingQuantity+" locationChoiceDerivatives:"+locationChoiceDerivatives);
                    throw new RuntimeException(this+" effect of location change on quantity distribution through buying is NaN, commodities "+cNum+","+c1);
                }
                if (Double.isNaN(sellingQuantity*locationChoiceDerivatives.get(c1))) {
                    logger.fatal(this+" effect of location change on quantity distribution through selling is NaN, commodities "+cNum+","+c1);
                    logger.fatal("sellingQuantity:"+sellingQuantity+" locationChoiceDerivatives:"+locationChoiceDerivatives);
                    throw new RuntimeException(this+" effect of location change on quantity distribution through selling is NaN, commodities "+cNum+","+c1);
                }
                averagePriceSurplusMatrix[cNum][c1]+=(buyingQuantity + sellingQuantity)*locationChoiceDerivatives.get(c1);
            }
        }
    }

    @Override
    protected void allocateProductionChoiceAveragePriceDerivatives(double[][] averagePriceSurplusMatrix) {
        // this is the matrix effect of local changes in production technology.
        LogitTechnologyChoice techChoice = ((LogitTechnologyChoiceConsumptionFunction) lastConsumptionFunction).myTechnologyChoice;

        //initializeZUtilities();

        int numCommodities = techChoice.getMyCommodityOrder().size();
        double[] sellingPriceCoefficients = new double[numCommodities];
        double[] buyingPriceCoefficients = new double[numCommodities];

        for (int c = 0; c < numCommodities; c++) {
            Commodity commodity = techChoice.getMyCommodityOrder().get(c);
            sellingPriceCoefficients[c]=commodity.getSellingUtilityPriceCoefficient();
            buyingPriceCoefficients[c]=commodity.getBuyingUtilityPriceCoefficient();
        }

        // looks like this is slow because garbage collection occurs here
        SparseMatrix technologyDerivatives = techChoice.amountsDerivativesMatrix(buyingCommodityUtilities, sellingCommodityUtilities,myZone.zoneIndex);
        double quantity = getQuantity();

        Boolean aparapi = false;
        /* if (aparapi) {
            Kernel kernel = new Kernel() {
                @Override 
                public void run() {

                    int row = getGlobalId(0);
                    int col = getGlobalId(1);
                    //				for (int row=0;row<averagePriceSurplusMatrix.length;row++) {
                    //					for (int col=0;col<averagePriceSurplusMatrix[row].length;col++) {
                    // change in amount bought wrt buying price
                    averagePriceSurplusMatrix[row][col]+=technologyDerivatives[row][col]*(quantity*buyingPriceCoefficients[col]);
                    // change in amount bought wrt selling price
                    averagePriceSurplusMatrix[row][col]+=technologyDerivatives[row][col+numCommodities]*quantity*sellingPriceCoefficients[col];
                    // change in amount sold wrt buying price
                    averagePriceSurplusMatrix[row][col]+=technologyDerivatives[row+numCommodities][col]* quantity*buyingPriceCoefficients[col];
                    // change in amount sold wrt selling price
                    averagePriceSurplusMatrix[row][col]+=technologyDerivatives[row+numCommodities][col+numCommodities]*quantity*sellingPriceCoefficients[col];
                    //					}
                    //				}
                }
            };
            Range range = Range.create2D(averagePriceSurplusMatrix.length, averagePriceSurplusMatrix[0].length);
            kernel.execute(range);
        } else { */


        // old way 
        /*
            for (int row=0;row<averagePriceSurplusMatrix.length;row++) {
                for (int col=0;col<averagePriceSurplusMatrix[row].length;col++) {
                    // change in amount bought wrt buying price
                    averagePriceSurplusMatrix[row][col]+=technologyDerivatives.get(row, col)*(quantity*buyingPriceCoefficients[col]);
                    // change in amount bought wrt selling price
                    averagePriceSurplusMatrix[row][col]+=technologyDerivatives.get(row,col+numCommodities)*quantity*sellingPriceCoefficients[col];
                    // change in amount sold wrt buying price
                    averagePriceSurplusMatrix[row][col]+=technologyDerivatives.get(row+numCommodities,col)* quantity*buyingPriceCoefficients[col];
                    // change in amount sold wrt selling price
                    averagePriceSurplusMatrix[row][col]+=technologyDerivatives.get(row+numCommodities,col+numCommodities)*quantity*sellingPriceCoefficients[col];
                }
            }
        	*/	
        // new way, check if same
            	Iterator<MatrixEntry> it = technologyDerivatives.iterator();
            	MatrixEntry entry = null;
				while (it.hasNext()) {
            		entry = it.next();
            		int row = entry.row();
            		int destRow = row % averagePriceSurplusMatrix.length;
            		int col = entry.column();
            		int destCol = col % averagePriceSurplusMatrix[0].length;
            		// use selling coefficient for right hand side of matrix
            		double priceCoefficient = col >= technologyDerivatives.numColumns()/2 ? sellingPriceCoefficients[destCol] : buyingPriceCoefficients[destCol];
            		averagePriceSurplusMatrix[destRow][destCol]+=entry.get()*(quantity*priceCoefficient);
            	}
       // }
    }

    @Override
    public double[] calculateLocationUtilityWRTAveragePrices() {
        initializeZUtilities();
        LogitTechnologyChoice techChoice = ((LogitTechnologyChoiceConsumptionFunction)lastConsumptionFunction).myTechnologyChoice;
        double[] compositeUtilityDerivatives = null;
        try {
            compositeUtilityDerivatives = techChoice.compositeUtilityDerivatives(techChoice.buyingUtilities, techChoice.sellingUtilities,myZone.zoneIndex);
        } catch (NoAlternativeAvailable e1) {
            String msg = "No alternative available when calculating composite utility derivatives for "+this;
            logger.fatal(msg);
            logger.fatal("buyingCommodityUtilities :"+techChoice.buyingUtilities);
            logger.fatal("sellingCommodityUtilities :"+techChoice.sellingUtilities);
            throw new RuntimeException(msg);
        }
        double[] compositeUtilityDerivativesWrtChangesInRegionPrice = new double[techChoice.getMyCommodityOrder().size()];
        for(int cNum=0;cNum<techChoice.getMyCommodityOrder().size();cNum++) {
            Commodity c = (Commodity) techChoice.getMyCommodityOrder().get(cNum);
            double buyingPriceCoefficient = c.getBuyingUtilityPriceCoefficient();
            double sellingPriceCoefficient = c.getSellingUtilityPriceCoefficient();
            if (Double.isNaN(compositeUtilityDerivatives[cNum])|| Double.isNaN(compositeUtilityDerivatives[cNum+techChoice.getMyCommodityOrder().size()])) {
                logger.warn("NaN occuring in locationUtilityWRTAveragePrices");
            }
            compositeUtilityDerivativesWrtChangesInRegionPrice[cNum] =
                    -compositeUtilityDerivatives[cNum] * buyingPriceCoefficient +
                    compositeUtilityDerivatives[cNum+techChoice.getMyCommodityOrder().size()] * sellingPriceCoefficient;
            if (Double.isNaN(compositeUtilityDerivativesWrtChangesInRegionPrice[cNum])) {
                logger.warn("NaN occuring in locationUtilityWRTAveragePrices");
            }
        }
        return compositeUtilityDerivativesWrtChangesInRegionPrice;
    }

    public void writeOutInformationOnEachOption(int zoneNumber, PrintWriter out) throws IOException, ChoiceModelOverflowException {
        LogitTechnologyChoice techChoice = ((LogitTechnologyChoiceConsumptionFunction)lastConsumptionFunction).myTechnologyChoice;
        initializeZUtilities();
        techChoice.setUtilities(buyingCommodityUtilities, sellingCommodityUtilities,myZone.zoneIndex);
        techChoice.writeOutOptionUtilities(myProductionActivity.getNumber(),zoneNumber, out);
    }

}
