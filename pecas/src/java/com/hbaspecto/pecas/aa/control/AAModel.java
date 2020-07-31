/*
 * Copyright  2005 PB Consult Inc and HBA Specto Incorporated
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
package com.hbaspecto.pecas.aa.control;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import org.apache.log4j.Logger;

import com.hbaspecto.models.FutureObject;
import com.hbaspecto.pecas.ChoiceModelOverflowException;
import com.hbaspecto.pecas.FormatLogger;
import com.hbaspecto.pecas.ModelDidntWorkException;
import com.hbaspecto.pecas.NoAlternativeAvailable;
import com.hbaspecto.pecas.OverflowException;
import com.hbaspecto.pecas.aa.activities.ActivityConstraints;
import com.hbaspecto.pecas.aa.activities.AggregateActivity;
import com.hbaspecto.pecas.aa.activities.AmountInZone;
import com.hbaspecto.pecas.aa.activities.ProductionActivity;
import com.hbaspecto.pecas.aa.commodity.AbstractCommodity;
import com.hbaspecto.pecas.aa.commodity.Commodity;
import com.hbaspecto.pecas.aa.commodity.CommodityZUtility;
import com.hbaspecto.pecas.aa.commodity.Exchange;
import com.hbaspecto.pecas.zones.PECASZone;
import com.hbaspecto.pecas.zones.ZoneGroup;
import com.pb.common.util.ResourceUtil;

import drasys.or.linear.algebra.Algebra;
import drasys.or.linear.algebra.CroutPivot;
import drasys.or.matrix.DenseMatrix;
import drasys.or.matrix.DenseVector;
import drasys.or.matrix.Matrix;
import drasys.or.matrix.SMFWriter;

/**
 * aaModel essentially has to: 1) Call migrationAndAllocation for each
 * ProductionActivity for a given time step. 2) if some Production Activities
 * are modelled as being in equilibrium with each other, aaModel will need to
 * adjust prices/utilities and then call reMigrationAndReAllocation for those
 * ProductionActivities repeatedly, adjusting prices until an equilibrium is
 * achived Note that different ProductionActivities could have different time
 * steps. Thus a DisaggregateActivity could be simulated with a monthly time
 * step with some prices adjusted after each month, while a set of
 * AggregateActivities are simulated as being in equilibrium at the end of each
 * one-year time period.
 * 
 * @author John Abraham
 */
public class AAModel {
    
    
    protected static Logger logger = Logger.getLogger(AAModel.class);
    private static FormatLogger loggerf = new FormatLogger(logger);
    // so we can easily write out intermpediate results, we keep a reference to our pprocessor
    final AAPProcessor myOutputWriter;
    
    private double conFac = 0.01; //default value

    private double minimumStepSize = 1.0E-4; //default value

    private double stepSize = .5; //actual stepSize that will be used by aa,
                                  // will go up or down depending on the

    //merit measure. It is changed via the "increaseStepSize"
    // and "decreaseStepSizeAndAdjustPrices" method.
    private double maximumStepSize = 1.0; // default value

    // re-activate this code if we need some specific adjustments for specific
    // stubborn commodities
    private double commoditySpecificScalingAdjustment = 0; //default value
    
    protected static Integer maxThreads = null;

    HashMap newPricesC = null; //a new HashMap will be created every time
                               // "calculateNewPrices" is called

    HashMap oldPricesC = new HashMap();

    public double convergenceTolerance;

    double localPriceStepSizeAdjustment = 1.0;
    
    public ResourceBundle aaRb;
 
	private boolean allPriceChangesMustRespectLocalSign;
    
    static public ResourceBundle lastAaRb;

    private static Executor commodityThreadPool;

    private static Executor activityThreadPool;


    /*
     * This constructor is used by AAControl and by AADAF - The aa.properties
     * file will not be in the classpath when running on the cluster (or even in
     * the mono-version when running for mulitple years using AO) and therefore
     * we need to pass in the ResourceBundle to aaModel from within the
     * AAServerTask. Because there may be more than 1 aa.properties file over
     * the course of a 30 year simulation it is not practical to include 30
     * different location in the classpath. AO will locate the most recent
     * aa.properties file based on the timeInterval and will write it's absolute
     * path location into a file called 'RunParameters.txt' where it will be
     * read in by AAServerTask in it's 'onStart( ) method and will subsequently
     * be passed to aaModel during the 'new aaModel(rb)' call.
     */
    public AAModel(ResourceBundle aaRb, AAPProcessor aaWriter) {
    	myOutputWriter = aaWriter;
        setResourceBundles(aaRb);
        String initialStepSizeString = ResourceUtil.getProperty(aaRb, "aa.initialStepSize");
        if (initialStepSizeString == null) {
            logger.info("*   No aa.initialStepSize set in properties file -- using default");
        } else {
            double iss = Double.valueOf(initialStepSizeString);
            this.stepSize = iss; //set the stepSize to the initial value
            logger.info("*   Initial step size set to " + iss);
        }

        this.minimumStepSize = ResourceUtil.getDoubleProperty(aaRb, "aa.minimumStepSize", 1.0E-4);
        logger.info("*   Minimum step size set to " + minimumStepSize);
        
        this.allPriceChangesMustRespectLocalSign = ResourceUtil.getBooleanProperty(aaRb, "aa.allPriceChangesMustRespectLocalSign", false);
        
        conFac = ResourceUtil.getDoubleProperty(aaRb, "aa.ConFac");

        String maximumStepSizeString = ResourceUtil.getProperty(aaRb, "aa.maximumStepSize");
        if (maximumStepSizeString == null) {
            logger.info("*   No aa.maximumStepSize set in properties file -- using default");
        } else {
            double mss = Double.valueOf(maximumStepSizeString);
            this.maximumStepSize = mss;
            logger.info("*   Maximum step size set to " + mss);
        }

        String convergedString = ResourceUtil.getProperty(aaRb, "aa.converged");
        if (convergedString == null) {
            logger.info("*   No aa.converged set in properties file -- using default");
        } else {
            double converged = Double.valueOf(convergedString);
            this.convergenceTolerance = converged;
            logger.info("*   Convergence tolerance set to " + converged);
        }

        String localPriceStepSizeAdjustmentString = ResourceUtil.getProperty(aaRb, "aa.localPriceStepSizeAdjustment");
        if (localPriceStepSizeAdjustmentString == null) {
            logger.info("*   No aa.localPriceStepSizeAdjustment set in properties file -- using default of 1.0");
        } else {
            double lpssa = Double.valueOf(localPriceStepSizeAdjustmentString);
            this.localPriceStepSizeAdjustment = lpssa;
            logger.info("*   Local price step size adjustment set to " + lpssa);
        }

        // different step sizes for stubborn    commodities
        String commoditySpecificAdjustmentString =
        ResourceUtil.getProperty(aaRb, "aa.commoditySpecificAdjustment");
        if (commoditySpecificAdjustmentString == null) {
            logger.info("*   No aa.commoditySpecificAdjustment set in properties file -- using default");
        } else {
            double csa =
         Double.valueOf(commoditySpecificAdjustmentString);
            this.commoditySpecificScalingAdjustment = csa;
            logger.info("*   Commodity specific adjustment set to " + csa);
        }

    }
    
    public boolean calculateExchangeSizeTermsForSpecifiedNonFloorspace() {
        snapShotCurrentPrices();
        // set prices to default values;
        // set size terms to 1.0 just in case.
        for (AbstractCommodity ac: Commodity.getAllCommodities()) {
            Commodity c = (Commodity) ac;
            for (Exchange x : c.getAllExchanges()) {
            	int exchangeCount = c.getAllExchanges().size();
                x.setPrice(c.getExpectedPrice());
                if (!c.isFloorspaceCommodity() && !c.isManualSizeTerms()) {
                	if (c.getTotalSizeForSizeTerms()!=null) {
                		x.setBuyingSizeTerm(c.getTotalSizeForSizeTerms()/exchangeCount);
                		x.setSellingSizeTerm(c.getTotalSizeForSizeTerms()/exchangeCount);
                	} else {
                		x.setBuyingSizeTerm(1.0);
                		x.setSellingSizeTerm(1.0);
                	}
                }
            }
            
        }
        
        boolean nanPresent=false;
        if (!nanPresent) nanPresent = calculateCompositeBuyAndSellUtilities(); //this is distributed
        //calculates TP,TC, dTP,dTC for all activities in all zones
        if (!nanPresent) nanPresent = calculateLocationConsumptionAndProduction();
        //calculates the B qty and S qty for each commodity in all zones
        if (!nanPresent) nanPresent = allocateQuantitiesToFlowsAndExchanges(true); //this is distributed
        if (nanPresent) {
            logger.fatal("Default prices caused overflow in size term calculation");
            throw new RuntimeException("Default prices caused overflow in size term calculation");
        }
        boolean belowTolerance = true;
        for (AbstractCommodity ac: Commodity.getAllCommodities()) {
            Commodity c = (Commodity) ac;
            if (!c.isFloorspaceCommodity() && !c.isManualSizeTerms()) {
            	double totalBuyingSize = 0;
            	double totalSellingSize = 0;
                double buyingSizeScaler=1;
                double sellingSizeScaler=1;
                if (c.getTotalSizeForSizeTerms()!=null) {
	                for (Exchange x : c.getAllExchanges()) {
	                	double[] ie = x.importsAndExports(x.getPrice());
	                	totalBuyingSize += x.soldTotal()+ie[0];
	                	totalSellingSize += x.boughtTotal()+ie[1];
	                }
                	buyingSizeScaler = c.getTotalSizeForSizeTerms()/totalBuyingSize;
                	sellingSizeScaler = c.getTotalSizeForSizeTerms()/totalSellingSize;
                }
                for (Exchange x : c.getAllExchanges()) {
                	double[] ie = x.importsAndExports(x.getPrice());
                	x.setBuyingSizeTerm((x.soldTotal()+ie[0])*buyingSizeScaler);
                	x.setSellingSizeTerm((x.boughtTotal()+ie[1])*sellingSizeScaler);
                	if ((x.getBuyingSizeTerm() == 0 || x.getSellingSizeTerm() == 0) && ie[2] == 0 && ie[3] == 0) {
                		if (x.importsAndExports(x.getPrice())[1] == 0) {
                			x.setNoSupplyDemand(true);
                		}
                	}
                }
            }
        }
        // update our zone constants so we can calculate consistent exchange sizes off of them even if we don't have constraints.
        if (AggregateActivity.isForceConstraints()) updateActivityZonalConstantsBasedOnConstraints(1.0);
        myOutputWriter.writeLocationTable("ActivityLocationsForSizeTerms");

        backUpToLastValidPrices();
        return belowTolerance;
       
    }
    


    /*
     * This method calculates CUBuy and CUSell for each commodity in each zone. 
     * These are the logsum of the destination (or origin) choice models.
     * 
     * The prices must have been set already in each Exchange object for the
     * Commodity.  These prices are effectively the input to this routine,
     * although they do not appear in the parameter list because a precondition
     * is that the prices have already been set.
     * 
     * The Commodity object must also know that the prices have been changed, this
     * can be accomplished by calling the unfixPricesAndConditions method.
     * 
     * The output values are set in the appropriate commodityZUtility object
     * in CommodityZUtility.lastCalculatedUtility, thus this method
     * does not return any results; the values have been calculated
     * 
     */
    public boolean calculateCompositeBuyAndSellUtilities() {
        long startTime = System.currentTimeMillis();
        Commodity.unfixPricesAndConditionsForAllCommodities();
        boolean nanPresent = false;
        Iterator allOfUs = Commodity.getAllCommodities().iterator();

        class ConditionCalculator implements Runnable {
        	
        	double[] prices;
        	double[][] zutilities;
            
            final Commodity c;
            FutureObject worked = new FutureObject();
            
            ConditionCalculator(Commodity cParam) {
                c = cParam;
                // SMP program, prices already set in shared memory 
            }

            public void run() {
                try {
                	// this is the method call where we are telling the commodity object
                	// c that the prices have been set, and hence it should
                	// calculate the CommodityZUtility.lastCalculatedUtility values.
                	
                     zutilities =  c.fixPricesAndConditionsAtNewValues();
                } catch (OverflowException e) {
                    worked.setValue(e);
                }
                if (!worked.isSet()) {
                    worked.setValue(new Boolean(true));
                }
            }
            
        }

        ArrayList<ConditionCalculator> conditionCalculators = new ArrayList<ConditionCalculator>();

        while (allOfUs.hasNext()) {
            Commodity c = (Commodity) allOfUs.next();
            
            ConditionCalculator calc = new ConditionCalculator(c);
            conditionCalculators.add(calc);
            getCommodityThreadPool().execute(calc);
        }
        for (int c=0;c<conditionCalculators.size();c++) {
            Object worked;
            try {
                worked = conditionCalculators.get(c).worked.getValue();
            } catch (InterruptedException e) {
                throw new RuntimeException("Thread was interrupted");
            }
            if (worked instanceof OverflowException) {
                nanPresent = true;
                logger.error("Overflow error in CUBuy, CUSell calcs "+(OverflowException) worked);
                ((OverflowException) worked).printStackTrace();
            }
        }
        logger.info("Composite buy and sell utilities have been calculated for all commodities. Time in seconds: "
                + ((System.currentTimeMillis() - startTime) / 1000.0));
        return nanPresent;
    }


    /**
     * This method calculates the TC, TP and derivative of TC and derivative of
     * TP for each commodity in each zone. Along the way, it calculates several
     * utilitiy functions. The TC, TP and dTC, dTP are stored in the appropriate
     * CommodityZUtilitiy object (quantity and derivative)
     */
    public boolean calculateLocationConsumptionAndProduction() {
        if (logger.isDebugEnabled()) {
            logger.debug("Beginning Activity Iteration: calling 'migrationAndAllocationWithOverflowTracking' for each activity");
        }
        CommodityZUtility.resetCommodityBoughtAndSoldQuantities();
        long startTime = System.currentTimeMillis();
        boolean nanPresent = false;
        int count = 1;
        Iterator it = ProductionActivity.getAllProductionActivities().iterator();
        while (it.hasNext()) {
            AggregateActivity aa = (AggregateActivity) it.next();
            long activityStartTime = System.currentTimeMillis();
            try {
                aa.migrationAndAllocation(1.0, 0, 0);
            } catch (OverflowException e) {
                nanPresent = true;
                logger.warn("Overflow error in CUBuy, CUSell calcs "+e);
                e.printStackTrace();
            }
            if (logger.isDebugEnabled()) {
                logger.debug("Finished activity " + count + " in " + (System.currentTimeMillis() - activityStartTime) / 1000.0 + " seconds");
            }
            count++;
        }
        logger.info("Finished all Activity allocation: Time in seconds: " + (System.currentTimeMillis() - startTime) / 1000.0);
        return nanPresent;
    }

    /**
     * This method calculates the TC, TP and derivative of TC and derivative of
     * TP for each commodity in each zone. Along the way, it calculates several
     * utility functions. The TC, TP and dTC, dTP are stored in the appropriate
     * CommodityZUtilitiy object (quantity and derivative)
     */
    public boolean recalculateLocationConsumptionAndProduction() {
        
        class LocationConsumptionProductionAllocator implements Runnable {
            final AggregateActivity mine;
            FutureObject done = new FutureObject();
            
            LocationConsumptionProductionAllocator(AggregateActivity aParam) {
                mine = aParam;
            }

            public void run() {
                try {
                    mine.reMigrationAndReAllocationWithOverflowTracking();
                } catch (OverflowException e) {
                    logger.warn("Overflow error in CUBuy, CUSell calcs "+e);
                    e.printStackTrace();
                    done.setValue(e);
                }
                if (!done.isSet()) {
                    done.setValue(new Boolean(true));
                }
            }
            
        }
        if (logger.isDebugEnabled()) {
            logger.debug("Beginning Activity Iteration: calling 'reMigrationAndAllocationWithOverflowTracking' for each activity");
        }
        CommodityZUtility.resetCommodityBoughtAndSoldQuantities();
        long startTime = System.currentTimeMillis();
        boolean nanPresent = false;
        int count = 1;
        Iterator it = ProductionActivity.getAllProductionActivities().iterator();
        ArrayList<LocationConsumptionProductionAllocator> allocators = new ArrayList<LocationConsumptionProductionAllocator>();
        while (it.hasNext()) {
            AggregateActivity aa = (AggregateActivity) it.next();
            LocationConsumptionProductionAllocator allocator = new LocationConsumptionProductionAllocator(aa);
            allocators.add(allocator);
            getActivityThreadPool().execute(allocator);
        }
        for (int anum = 0; anum < allocators.size();anum++) {
            // will block until it's done
            Object done;
            try {
                done = allocators.get(anum).done.getValue();
            } catch (InterruptedException e) {
                logger.fatal("Unexpected interrupted exception");
                throw new RuntimeException("Unexpected interrupted exception", e);
            }
            if (done instanceof OverflowException) {
                nanPresent = true;
                logger.warn("Overflow error in CUBuy, CUSell calcs "+((OverflowException) done));
                ((OverflowException) done).printStackTrace();
            }
            if (logger.isDebugEnabled()) {
                logger.debug("Finished activity "+anum);
            }
            count++;
        }
        logger.info("Finished all Activity allocation: Time in seconds: " + (System.currentTimeMillis() - startTime) / 1000.0);
        return nanPresent;
    }

     /**
     * This method calculates the Buying and Selling quantities of each
     * commodity produced or consumed in each zone that is allocated to a
     * particular exchange zone for selling or buying. Bc,z,k and Sc,z,k.
     * 
     * This is the multithreaded version for a shared memory (multicore) machine.
     * @param settingSizeTerms indicates whether we are currently setting size terms, ignored in this Symmetric Multiprocessing version but in multiple-machine subclasses this is important
     */
    public boolean allocateQuantitiesToFlowsAndExchanges(boolean settingSizeTerms) {
        if (logger.isDebugEnabled()) {
            logger.debug("Beginning 'allocateQuantitiesToFlowsAndExchanges'");
        }
        long startTime = System.currentTimeMillis();
        boolean nanPresent = false;
        Commodity.clearAllCommodityExchangeQuantities();//iterates through the
                                                        // exchange objects
                                                        // inside the commodity
        //objects and sets the sell, buy qtys and the derivatives to 0
        Iterator allComms = Commodity.getAllCommodities().iterator();
        int count = 1;
        
        class FlowAllocator implements Runnable {
        	
            
            final Commodity c;
            FutureObject worked = new FutureObject();
            
            FlowAllocator(Commodity cParam) {
                c = cParam;
            }

            public void run() {
                OverflowException error = null;
                Hashtable<Integer, CommodityZUtility> ht;
                for (int b = 0; b < 2; b++) {
                	Iterator<CommodityZUtility> it;
                    if (b == 0)
                        it = c.getBuyingUtilitiesIterator();
                    else
                        it = c.getSellingUtilitiesIterator();
                    while (it.hasNext()) {
                        CommodityZUtility czu = (CommodityZUtility) it.next();
                        try {
                            czu.allocateQuantityToFlowsAndExchanges();
                        } catch (OverflowException e) {
                            error = e;
                            logger.warn("Overflow error in Bc,z,k and Sc,z,k calculations");
                        }
                    }
                }
                if (error!=null) {
                    worked.setValue(error);
                } else {
                    worked.setValue(new Boolean(true));
                }
            }
            
        }
        
        ArrayList<FlowAllocator> flowAllocators = new ArrayList<FlowAllocator>();
// creating a job with one task for each commodity.
        while (allComms.hasNext()) {
            Commodity c = (Commodity) allComms.next();
            FlowAllocator flower = new FlowAllocator(c);
            long activityStartTime = System.currentTimeMillis();
            flowAllocators.add(flower);
            getCommodityThreadPool().execute(flower);
        }
        //
        // getting the results back.
        for (int cnum=0;cnum<flowAllocators.size();cnum++) {
            FlowAllocator flower = flowAllocators.get(cnum);
            Object worked;
            try {
                worked = flower.worked.getValue();
            } catch (InterruptedException e) {
                logger.fatal("Thread was interrupted unexpectedly");
                throw new RuntimeException("Thread was interrupted unexpectedly",e);
            }
            if (worked instanceof Exception) {
                logger.warn("Overflow error in Bc,z,k and Sc,z,k calculations for "+flower.c, (Exception) worked);
                nanPresent = true;
            } else {
                flower.c.setFlowsValid(true);
                if (logger.isDebugEnabled()) {
                    logger.debug("Finished allocating commodity " + flower.c );
                }
            }
        }
        logger.info("All commodities have been allocated.  Time in seconds: " + (System.currentTimeMillis() - startTime) / 1000.0);
        return nanPresent;
    }


    public double calculateMeritMeasureWithoutLogging() throws OverflowException {
        boolean nanPresent = false;
        double meritMeasure = 0;
        Iterator commodities = Commodity.getAllCommodities().iterator();
        while (commodities.hasNext()) {
            Commodity c = (Commodity) commodities.next();
            Iterator exchanges = c.getAllExchanges().iterator();
            while (exchanges.hasNext() && !nanPresent) {
                Exchange ex = (Exchange) exchanges.next();
                double surplus = ex.exchangeSurplus();
                if (Double.isNaN(surplus)) {
                    nanPresent = true;
                    throw new OverflowException("NaN present at " + ex);
                }
                if (c.isDoSearch() && ex.isDoSearch()) {
                	meritMeasure += c.compositeMeritMeasureWeighting * c.compositeMeritMeasureWeighting * surplus * surplus;
                }
            }
        }
        return meritMeasure;
    }

    public double calculateMeritMeasureWithLogging() throws OverflowException {
        boolean nanPresent = false;
        double meritMeasure = 0;
        Iterator commodities = Commodity.getAllCommodities().iterator();
        while (commodities.hasNext()) {
            int numExchanges = 0;
            double totalSurplus = 0;
            double totalPrice = 0;
            double maxSurplus = 0;
            double maxSurplusSigned = 0;
            double maxPrice = -Double.MAX_VALUE;
            double minPrice = Double.MAX_VALUE;
            double commodityMeritMeasure = 0;
            Exchange minPriceExchange = null;
            Exchange maxPriceExchange = null;

            Exchange maxExchange = null;
            Commodity c = (Commodity) commodities.next();
            logger.info(c.toString());
            Iterator exchanges = c.getAllExchanges().iterator();
            while (exchanges.hasNext() && !nanPresent) {
                Exchange ex = (Exchange) exchanges.next();
                double surplus = ex.exchangeSurplus();
                if (Math.abs(surplus) > maxSurplus) {
                    maxExchange = ex;
                    maxSurplus = Math.abs(surplus);
                    maxSurplusSigned = surplus;
                }
                totalSurplus += ex.exchangeSurplus();
                totalPrice += ex.getPrice();
                numExchanges++;
                if (ex.getPrice() > maxPrice) {
                    maxPrice = ex.getPrice();
                    maxPriceExchange = ex;
                }
                if (ex.getPrice() < minPrice) {
                    minPrice = ex.getPrice();
                    minPriceExchange = ex;
                }
                if (Double.isNaN(surplus)) { /* || newPrice.isNaN() */
                    nanPresent = true;
                    logger.warn("\t NaN present at " + ex);
                    throw new OverflowException("\t NaN present at " + ex);
                }
                if (c.isDoSearch() && ex.isDoSearch()) {
                	meritMeasure += c.compositeMeritMeasureWeighting * c.compositeMeritMeasureWeighting * surplus * surplus;
                }
                commodityMeritMeasure += surplus * surplus;
            }
            if (maxExchange != null) {
                logger.info("\t maxSurp: " + maxSurplusSigned + " in " + maxExchange + ". Current price is " + maxExchange.getPrice() /*
                                                                                                                                        * changing  "
                                                                                                                                        * to " +
                                                                                                                                        * newPriceAtMaxExchange
                                                                                                                                        */);
            }
            if (maxPriceExchange != null) {
                logger.info("\t PMax " + maxPrice + " in " + maxPriceExchange);
            }
            if (minPriceExchange != null) {
                logger.info("\t PMin " + minPrice + " in " + minPriceExchange);
            }
            logger.info("\t Total surplus " + totalSurplus);
            logger.info("\t Average price " + totalPrice / numExchanges);

            double rmsError = Math.sqrt(commodityMeritMeasure/c.getAllExchanges().size());
            double oldRmsError = Math.sqrt(c.oldMeritMeasure/c.getAllExchanges().size());
            if (commodityMeritMeasure > c.oldMeritMeasure) {
                if (c.scalingAdjustmentFactor > 1) {
                    //c.scalingAdjustmentFactor= 1.0;
                    c.scalingAdjustmentFactor /= Math.pow(1+commoditySpecificScalingAdjustment,3);
                    if (c.scalingAdjustmentFactor < 0.1) c.scalingAdjustmentFactor = 0.1;
                }
                logger.info("\t Commodity RMS Error " + rmsError + " NOT IMPROVING was " + oldRmsError +" (adj now "+ c.scalingAdjustmentFactor +")");
            } else {
                c.scalingAdjustmentFactor *= (1+commoditySpecificScalingAdjustment);
                if (c.scalingAdjustmentFactor > 3) c.scalingAdjustmentFactor = 3;
                logger.info("\t Commodity RMS Error " + rmsError + " (was " + oldRmsError + ") (adj now "+ c.scalingAdjustmentFactor +")");
            }
            logger.info("\t Weighted Commodity Merit Measure is "+commodityMeritMeasure*c.compositeMeritMeasureWeighting*c.compositeMeritMeasureWeighting);
            double tClear =Math.sqrt(commodityMeritMeasure*c.compositeMeritMeasureWeighting*c.compositeMeritMeasureWeighting)/c.getAverageExchangeTotal();
            double maxSClear = c.getLargestSClear(conFac);
            logger.info("\t TClear is "+tClear+",  maxSClear is "+maxSClear);
            c.oldMeritMeasure = commodityMeritMeasure;

        }
        return meritMeasure;

    }

    public void increaseStepSize() {
        final double increaseStepSizeMultiplier = 1.1;
        stepSize = stepSize * increaseStepSizeMultiplier;
        if (stepSize > maximumStepSize)
            stepSize = maximumStepSize;
    }

    public void decreaseStepSizeAndAdjustPrices() {
        final double decreaseStepSizeMultiplier = 0.5;
        stepSize = stepSize * decreaseStepSizeMultiplier;
        if (stepSize < minimumStepSize)
            stepSize = minimumStepSize;
        newPricesC = StepPartwayBackBetweenTwoOtherPrices(oldPricesC, newPricesC, decreaseStepSizeMultiplier);
        setExchangePrices(newPricesC);
    }

    public void decreaseStepSizeEvenIfBelowMinimumAndAdjustPrices() {
        final double decreaseStepSizeMultiplier = 0.5;
        stepSize = stepSize * decreaseStepSizeMultiplier;
        if (stepSize < minimumStepSize) {
            logger.warn("Setting step size to "+stepSize+" which is *below* minimum of "+minimumStepSize);
        }
        newPricesC = StepPartwayBackBetweenTwoOtherPrices(oldPricesC, newPricesC, decreaseStepSizeMultiplier);
        setExchangePrices(newPricesC);
    }

    public void backUpToLastValidPrices() {
        setExchangePrices(oldPricesC);
    }

    public void calculateNewPricesUsingBlockDerivatives(boolean calcDeltaUsingDerivatives) {
        // ENHANCEMENT change this to use the MTJ library for matrices instead of using the ORObjects library which is not open source and is no longer available
        Algebra a = new Algebra();
        newPricesC = new HashMap();
        logger.info("Calculating average commodity price change");
        AveragePriceSurplusDerivativeMatrix.calculateMatrixSize();

        AveragePriceSurplusDerivativeMatrix avgMatrix = makeAveragePriceSurplusDerivativeMatrix();
        DenseVector totalSurplusVector = new TotalSurplusVector();
        for (int i = 0; i < totalSurplusVector.size(); i++) {
            totalSurplusVector.setElementAt(i, totalSurplusVector.elementAt(i) * -1);
        }

        // try something like Levenberg Marquadt, increasing the diagonals
        if (stepSize < 1) {
            for (int i = 0; i < avgMatrix.sizeOfColumns(); i++) {
                avgMatrix.setElementAt(i, i, avgMatrix.elementAt(i, i) / stepSize);
            }
        }
        DenseVector averagePriceChange = null;
        try {
            CroutPivot solver = new CroutPivot(avgMatrix);
            averagePriceChange = solver.solveEquations(totalSurplusVector);
            //        avgMatrix.solve(totalSurplusVector,averagePriceChange);
        } catch (Exception e) {
            logger.error("Can't solve average price matrix " +e);
            writeOutMatrix(avgMatrix, e);
        }
        
        if (stepSize >=1) {
            for (int i =0;i<averagePriceChange.size();i++) {
                averagePriceChange.setElementAt(i,averagePriceChange.elementAt(i)*stepSize);
            }
        }

        Iterator comIt = Commodity.getAllCommodities().iterator();
        int commodityNumber = 0;
        while (comIt.hasNext()) {
            Commodity c = (Commodity) comIt.next();
            if (logger.isDebugEnabled()) logger.debug("Calculating local price change for commodity " + c);
            double[] deltaPricesDouble = null;
            if (calcDeltaUsingDerivatives) {
                try {
                	logger.debug("Calculating local price change for commodity " + c +" using matrix calculations");
                    CommodityPriceSurplusDerivativeMatrix comMatrixData = new CommodityPriceSurplusDerivativeMatrix(c);
                    DenseMatrix comMatrix = new DenseMatrix(comMatrixData.data);
                    double[] surplus = c.getSurplusInAllExchanges();
                    for (int i = 0; i < surplus.length; i++) {
                        surplus[i] *= -1;
                    }
                    DenseVector deltaSurplusPlus = new DenseVector(surplus);
                    //                for (int i=0;i<surplus.length;i++) {
                    //                    deltaSurplusPlus.setElementAt(i,-surplus[i]-totalSurplusVector.elementAt(commodityNumber)/surplus.length);
                    //                }
                    //                deltaSurplusPlus.setElementAt(surplus.length,0);
                    //DenseMatrix crossTransposed = new
                    // DenseMatrix(surplus.length,surplus.length);
                    //comMatrix.transAmult(comMatrix,crossTransposed);
                    //DenseVector crossTransposedVector = new
                    // DenseVector(surplus.length);
                    //comMatrix.transMult(deltaSurplusPlus,crossTransposedVector);
                    DenseVector deltaPrices = new DenseVector(surplus.length);
                    // regular solution
                    //crossTransposed.solve(crossTransposedVector,deltaPrices);
                    // using the libraries least squares type rectangular matrix
                    // solver
                    //                try {
                    //                    comMatrix.solve(deltaSurplusPlus,deltaPrices);
                    //                } catch (MatrixSingularException e) {
                    //                    e.printStackTrace();
                    //                    throw new RuntimeException("Can't find delta prices for
                    // commodity "+c,e);
                    //                }
                    
                    // something like Levenberg Marquadt where we increase the diagonal
                    if (stepSize*localPriceStepSizeAdjustment*c.scalingAdjustmentFactor<1) {
                        for (int i=0;i<comMatrix.sizeOfColumns() ; i++) {
                            comMatrix.setElementAt(i,i,comMatrix.elementAt(i,i)/stepSize/localPriceStepSizeAdjustment/c.scalingAdjustmentFactor);
                        }
                    }
                    CroutPivot solver2 = new CroutPivot(comMatrix);
                    solver2.solveEquations(deltaSurplusPlus, deltaPrices);
                    if (stepSize*localPriceStepSizeAdjustment*c.scalingAdjustmentFactor>1) {
                        for (int i=0;i<deltaPrices.size();i++) {
                            deltaPrices.setElementAt(i,deltaPrices.elementAt(i)*stepSize*localPriceStepSizeAdjustment*c.scalingAdjustmentFactor);
                        }
                    }
                    
                    // ENHANCEMENT experiment with not setting average price change to zero (ie comment out this block)
                    double totalPrice = deltaPrices.sum();
                    deltaPricesDouble = deltaPrices.getArray();
                    // make sure average price change is zero
                    for (int i = 0; i < deltaPricesDouble.length; i++) {
                        deltaPricesDouble[i] -= totalPrice / deltaPricesDouble.length;
                    }
                    
                } catch (Exception e) {
                    e.printStackTrace();
                    throw new RuntimeException(e);
                }
            } else {
            	logger.debug("Calculating local price change for commodity " + c +" using diagonal approximation");
                List exchanges = c.getAllExchanges();
                deltaPricesDouble = new double[exchanges.size()];
                for (int xNum = 0; xNum < exchanges.size(); xNum++) {
                    Exchange x = (Exchange) exchanges.get(xNum);
                    double[] sAndD = x.exchangeSurplusAndDerivative();
                    double increase = 0;
                    if (!x.isNoSupplyDemand() || sAndD[1]  != 0) {
                    	increase = -sAndD[0] / sAndD[1];
                    }
                    deltaPricesDouble[xNum] = increase * stepSize * localPriceStepSizeAdjustment*c.scalingAdjustmentFactor;
                    if (x.monitor) {
                        logger.info("In "+x+" surplus is "+sAndD[0]+" derivative is "+sAndD[1]+" unscaled local price change "+increase+" scaled "+deltaPricesDouble[xNum]);
                    }
                }
                // ENHANCEMENT but average price change for this commodity should be zero because the average price change is calculated separately, try reactivating this code!
                //                for (int xNum=0;xNum<exchanges.size();xNum++) {
                //                    deltaPricesDouble[xNum] -= totalIncrease/numExchanges;
                //                }
            }
            List exchanges = c.getAllExchanges();
            for (int xNum=0; xNum < exchanges.size(); xNum++) {
                Exchange x = (Exchange) exchanges.get(xNum);
                double price;
                    // already did scaling for stepSize and localPriceStepSizeAdjsutment
                price = x.getPrice() + averagePriceChange.elementAt(commodityNumber) + 
                         deltaPricesDouble[xNum];
                if (c.isHasFixedPrices()) {
                	// don't do average price change for this commodity if some zones are not changing their prices,
                	// unfair to the zones that ARE changing their prices to try to solve any global inbalance problem with the commodity.
                	price = x.getPrice()+deltaPricesDouble[xNum];
                }
                if (Double.isNaN(price)) {
                	logger.error("Planning NaN price in "+x+" oldPrice:"+x.getPrice()+" averagePriceChange:"+averagePriceChange.elementAt(commodityNumber)+" local price change:"+deltaPricesDouble[xNum]);
                	writeOutMatrix(avgMatrix,new RuntimeException("Planning NaN price in "+x+" oldPrice:"+x.getPrice()+" averagePriceChange:"+averagePriceChange.elementAt(commodityNumber)+" local price change:"+deltaPricesDouble[xNum]));
                }
                if (x.monitor) {
                    logger.info("Planning "+price+" price in "+x+" oldPrice:"+x.getPrice()+" averagePriceChange:"+averagePriceChange.elementAt(commodityNumber)+" local price change:"+deltaPricesDouble[xNum]);
                }
                if (c.isDoSearch() && x.isDoSearch()) {
                	if (allPriceChangesMustRespectLocalSign) {
                		double[] sAndD = x.exchangeSurplusAndDerivative();
                		if (Math.signum((price - x.getPrice())*sAndD[1]*sAndD[0]) == -1) 
                			newPricesC.put(x, new Double(c.applyMinMaxPriceConstraints(price)));
                		else
                			newPricesC.put(x,  new Double(x.getPrice()));
                				
                	} else {
                		newPricesC.put(x, new Double(c.applyMinMaxPriceConstraints(price)));
                	}
                } else {
                	newPricesC.put(x, x.getPrice());
                }
            }
            commodityNumber++;

        }
        setExchangePrices(newPricesC);

    }

	protected AveragePriceSurplusDerivativeMatrix makeAveragePriceSurplusDerivativeMatrix() {
		AveragePriceSurplusDerivativeMatrix avgMatrix = new AveragePriceSurplusDerivativeMatrix(getActivityThreadPool());
		avgMatrix.init();
		return avgMatrix;
	}

	protected boolean useJPPF() {
		return false;
	}

	private void writeOutMatrix(AveragePriceSurplusDerivativeMatrix avgMatrix, Exception e) {
		logger.fatal("Having problems solving for change in average prices", e);
		logger.fatal("Writing out average price change derivative matrix to file AvgMatrix.txt");
		FileOutputStream badMatrixStream = null;
		try{
		    badMatrixStream = new FileOutputStream(ResourceUtil.getProperty(aaRb,"output.data")+"AvgMatrix.txt");
		} catch (FileNotFoundException e1) {
		    logger.fatal("Can't seem to open file "+ResourceUtil.getProperty(aaRb,"output.data")+"AvgMatrix.txt");
		    throw new RuntimeException(e);
		}
		SMFWriter matrixWriter = new SMFWriter(badMatrixStream);
		matrixWriter.writeMatrix(avgMatrix);
		matrixWriter.flush();
		matrixWriter.close();
		try {
		    badMatrixStream.flush();
		    badMatrixStream.close();
		} catch (IOException e1) {}
		throw new RuntimeException(e);
	}

	public static void writeOutMatrix(Matrix aMatrix, String matrixName) {
		logger.info("Having problems with matrix "+matrixName+" writing it out to a file");
		FileOutputStream badMatrixStream = null;
		try{
		    badMatrixStream = new FileOutputStream(lastAaRb.getString("output.data")+matrixName+".txt");
		} catch (FileNotFoundException e1) {
		    logger.error("Can't seem to open file "+lastAaRb.getString("output.data")+matrixName+".txt");
		}
		SMFWriter matrixWriter = new SMFWriter(badMatrixStream);
		matrixWriter.writeMatrix(aMatrix);
		matrixWriter.flush();
		matrixWriter.close();
		try {
		    badMatrixStream.flush();
		    badMatrixStream.close();
		} catch (IOException e1) {}
	}

	
	/**
	 *@deprecated 
	 */
	public void calculateNewPricesUsingDiagonalApproximation() {
        newPricesC = new HashMap();
        Iterator commodities = Commodity.getAllCommodities().iterator();
        while (commodities.hasNext()) {
            Commodity c = (Commodity) commodities.next();
            Iterator exchanges = c.getAllExchanges().iterator();
            while (exchanges.hasNext()) {
                Exchange ex = (Exchange) exchanges.next();
                double[] surplusAndDerivative = ex.exchangeSurplusAndDerivative();
                Double newPrice = new Double(ex.getPrice() - ((stepSize * c.scalingAdjustmentFactor * surplusAndDerivative[0]) / surplusAndDerivative[1]));
                if (ex.monitor || Double.isNaN(surplusAndDerivative[0]) || newPrice.isNaN()) {
                    logger.info("Exchange:" + ex + " surplus:" + surplusAndDerivative[0] + " planning price change from " + ex.getPrice() + " to "
                            + newPrice);
                }
                if (c.isDoSearch() && ex.isDoSearch()) {
                	newPricesC.put(ex, newPrice);
                } else {
                	newPricesC.put(ex,ex.getPrice());
                }
            }
        }
        setExchangePrices(newPricesC);
    }

    /**
     *  
     */
    public void snapShotCurrentPrices() {
        Iterator commodities = Commodity.getAllCommodities().iterator();
        while (commodities.hasNext()) {
            Commodity c = (Commodity) commodities.next();
            Iterator exchanges = c.getAllExchanges().iterator();
            while (exchanges.hasNext()) {
                Exchange ex = (Exchange) exchanges.next();
                oldPricesC.put(ex, new Double(ex.getPrice()));
            }
        }
    }

    private static void setExchangePrices(HashMap prices) {
        Iterator it = Commodity.getAllCommodities().iterator();
        while (it.hasNext()) {
            Commodity c = (Commodity) it.next();
            c.unfixPricesAndConditions();
        }
        it = prices.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry e = (Map.Entry) it.next();
            Exchange x = (Exchange) e.getKey();
            //            if (x.myCommodity.name.equals("FLR Agriculture")) {
            //                System.out.println(x.getPrice()+" to "+e.getValue()+" in "+x);
            //            }
            Double price = (Double) e.getValue();
            x.setPrice(price.doubleValue());
        }
    }

    /**
     * For each price calculates the difference between the first one and the
     * second one, and returns a new price that is a scaled back step
     * 
     * @param firstPrices
     *            Hashmap of first set of prices keyed by exchange
     * @param secondPrices
     *            Hashmap of second set of prices keyed by exchange
     * @param howFarBack
     *            how far back to step (0.5 is halfway back, 0.75 is 3/4 way
     *            back)
     * @return Hashmap of new prices in between the other two
     */
    private static HashMap StepPartwayBackBetweenTwoOtherPrices(HashMap firstPrices, HashMap secondPrices, double howFarBack) {
        HashMap newPrices = new HashMap();
        Iterator commodities = Commodity.getAllCommodities().iterator();
        while (commodities.hasNext()) {
            Commodity c = (Commodity) commodities.next();
            Iterator exchanges = c.getAllExchanges().iterator();
            while (exchanges.hasNext()) {
                Exchange ex = (Exchange) exchanges.next();
                Double aggressivePrice = (Double) secondPrices.get(ex);
                Double previousPrice = (Double) firstPrices.get(ex);
                double lessAggressivePrice = (aggressivePrice.doubleValue() - previousPrice.doubleValue()) * howFarBack + previousPrice.doubleValue();
                newPrices.put(ex, new Double(lessAggressivePrice));
                if (ex.monitor) {
                    logger.info("Exchange:" + ex + "  reducing amount of price change, changing p from " + aggressivePrice + " to "
                            + lessAggressivePrice);
                }
            }
        }
        return newPrices;
    }

    public double getStepSize() {
        return stepSize;
    }

    public double getMinimumStepSize() {
        return minimumStepSize;
    }

     public void setResourceBundles(ResourceBundle appRb){
        this.aaRb = appRb;
        lastAaRb = appRb;
    }

    /*
     * This method was/is called by AO. Before startModel is called the most current
     * aa.properties file has been found in the tn subdirectories and has been
     * set by calling the setApplicationResourceBundle method (a ModelComponent
     * method) from AO.
     * 
     * This is duplicated code from AAControl.main().  If you change this code please
     * change AAControl.main() as well.
     */
    public void startModel(int baseYear, int timeInterval) {
        String pProcessorClass = ResourceUtil.getProperty(aaRb, "pprocessor.class");
        logger.info("PECAS will be using the " + pProcessorClass + " for pre and post processing");
        boolean doInputProcessing = !ResourceUtil.getBooleanProperty(aaRb, "aa.technologyScaling");
        AAControl aa;
        try {
            aa = new AAControl(Class.forName(pProcessorClass), aaRb, baseYear, timeInterval, doInputProcessing);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(pProcessorClass + " could not be instantiated ", e);
        }
        try {
            aa.runModelPerhapsWithConstraints();
        } catch (ModelDidntWorkException e) {
            logger.fatal("AA model did not work, "+e);
            throw new RuntimeException("AA model didn't work", e);
        }
    }


    public void setStepSize(double stepSizeParameter) {
        stepSize = stepSizeParameter;
        
    }

    /**
     * Updates the ActivityZonalConstant terms based on the probability difference between the constraint and the current value
     * NOTE updates AmountInZone.activityZoneConstantForSizeTerms if AggregateActivity.isDoingAllocationForSizes() && ProducationActivity.constInExchangeSize
     * (otherwise updates AmountInZone.activityZoneConstant
     * @param adjustment adjustment could be less than 1.0 if we want to iterate
     */
    public void updateActivityZonalConstantsBasedOnConstraints(double adjustment) {
        Logger ascChangeLogger = Logger.getLogger("com.pb.models.pecas.aaPProcessor.updateActivityZonalConstants");
        Collection activities = ProductionActivity.getAllProductionActivities();
        Iterator it = activities.iterator();
        while (it.hasNext()) {
            AggregateActivity a = (AggregateActivity) it.next();
            AmountInZone[] amounts = a.myDistribution;
            double totalAmount = a.getTotalAmount();
            double[] probs;
            int runAroundCount = 0;
            double maxAbsChange = 0;
            do {// have to do this iteratively because the logsum denominator changes
            	maxAbsChange = 0;
            	runAroundCount++;
	            try {
	                probs = a.logitModelOfZonePossibilities.getChoiceProbabilities();
	            } catch (NoAlternativeAvailable e) {
	                String msg = "Activity "+a+" seems to have no location alternatives";
	                logger.fatal(msg);
	                throw new RuntimeException(msg, e);
	            } catch (ChoiceModelOverflowException e) {
	                String msg = "Activity "+a+" overflows in allocation when adjusting constants";
	                logger.fatal(msg);
	                throw new RuntimeException(msg, e);
	            }
	            for (int z=0;z<amounts.length;z++) {
	                if (amounts[z].isConstrained() && (a.constInExchangeSize || !AggregateActivity.isDoingAllocationForSizes())) {
	                    if (amounts[z].constraintQuantity!= amounts[z].quantity) {
	                        String msg = "Problem in constraint process -- constraint quantity is unequal to quantity for "+amounts[z];
	                        logger.fatal(msg);
	                        throw new RuntimeException("msg");
	                    }
	                    double currentConstant = amounts[z].getLocationSpecificUtilityInclTaxes();
	                    double newConstant = 0;
	                    double constantChange = 0;
	                    if (amounts[z].constraintQuantity==0) {
	                        if (probs[z]==0) {
	                            //We use to leave these unchanged, but then the zero wasn't always repeatable.
	                            newConstant = Double.NEGATIVE_INFINITY;
	                            constantChange = Double.NEGATIVE_INFINITY;
	                        } else {
	                            // with constraint = 0 might as well set the constant to negative infinity rather than goofing around with minor changes
	                            newConstant = Double.NEGATIVE_INFINITY;
	                            constantChange = Double.NEGATIVE_INFINITY;
	                        }
	                    } else {
	                        // constraint !=0
	                        if (probs[z]==0) {
	                            if (runAroundCount==1) ascChangeLogger.error("In zone "+amounts[z].getMyZone().getZoneUserNumber()+" constraint for "+a.name+" is non-zero ("+amounts[z].constraintQuantity+") but current amount is zero.  Increasing Zonal ASC by arbitrary amount.");
	                            constantChange = 1/a.getLocationDispersionParameter()*adjustment;
	                            newConstant = currentConstant + constantChange;
	                        } else {
	                            // This is the normal case of adjusting the constant based on ratio
	                            double probRatio = (amounts[z].constraintQuantity/totalAmount/probs[z]);
	                            constantChange = 1/a.getLocationDispersionParameter()*Math.log(probRatio);
	                            maxAbsChange = Math.max(Math.abs(constantChange), maxAbsChange);
	                            newConstant = currentConstant + constantChange;
	                        }
	                    }
	                    ascChangeLogger.debug("Changing zone "+amounts[z].getMyZone().getZoneUserNumber()+" constant on "+a.name+" by "+constantChange+" to attempt to change "+amounts[z].quantity+" to "+amounts[z].constraintQuantity+", current constant is "+currentConstant);
	                    amounts[z].setLocationSpecificUtilityInclTaxes(newConstant);
	                }
	            }
            } while (maxAbsChange > .000001/a.getLocationDispersionParameter() && runAroundCount<500);
        	if (runAroundCount >= 500)  {
        		String msg = "Can't update ASC for activity "+a+", iterated "+runAroundCount+" times but this isn't converging, giving up";
        	}
        }
    
    }

    public static boolean checkConstraints(double constraintTolerance) {
        Collection<ProductionActivity> activities = ProductionActivity
                .getAllProductionActivities();
        boolean ok = true;
        for (ProductionActivity pa : activities) {
            AggregateActivity a = (AggregateActivity) pa;
            AmountInZone[] amounts = a.myDistribution;
            for (int z = 0; z < amounts.length; z++) {
                if (amounts[z].isConstrained()) {
                    if (!checkRelativeError(constraintTolerance,
                            amounts[z].constraintQuantity,
                            amounts[z].quantity)) {
                        String msg = "constraint not matched, cons="
                                + amounts[z].constraintQuantity
                                + " , modelled=" + amounts[z].quantity
                                + ", in " + amounts[z];
                        logger.error(msg);
                        ok = false;
                    }
                }
            }
        }
        return ok;
    }

    /**
     * Updates the activity constraints to fulfil the LUZ group minimums and
     * maximums.
     * 
     * @param forcer The forcer that satisfies the group minimum and maximums
     * @param constraints The zonal activity constraints
     * @param iteration The constraint iteration number
     */
    public static void updateConstraintsForZoneGroups(
            ZoneGroupBoundForcer<PECASZone> forcer) {
        logger.info("Updating zonal constants to match zone group bounds");
        
        forcer.solve();

        List<ProductionActivity> activities = new ArrayList<>(
                ProductionActivity.getAllProductionActivities());

        for (ZoneGroup<PECASZone> group : forcer.groups()) {
            for (ProductionActivity act : activities) {
                if (forcer.isNewlyForced(group, act)) {
                    double expectedQuantity = forcer.expectedAmount(group, act);
                    double constrainedQuantity = 0;
                    double freeQuantity = 0;

                    for (PECASZone z : group.groupZones()) {
                        AmountInZone amt = act
                                .getAmountInUserZone(z.zoneUserNumber);
                        if (amt.isConstrained()) {
                            constrainedQuantity += amt.getQuantity();
                        }
                        else {
                            freeQuantity += amt.getQuantity();
                        }
                    }

                    double expectedFreeQuantity = expectedQuantity - constrainedQuantity;
                    loggerf.info("For activity %s in zone group %s:", act, group);
                    loggerf.info("\tExpected free quantity = %.3g",
                            expectedFreeQuantity);
                    loggerf.info("\tActual quantity = %.3g", freeQuantity);
                    loggerf.info(
                            "\tSo this group gets adjusted by a factor of %.3g",
                            expectedFreeQuantity / freeQuantity);

                    for (PECASZone z : group.groupZones()) {
                        AmountInZone amt = act
                                .getAmountInUserZone(z.zoneUserNumber);
                        if (!amt.isConstrained()) {
                            amt.setConstrained(true);
                            amt.constraintQuantity = amt.quantity
                                    * expectedFreeQuantity / freeQuantity;
                        }
                    }
                }
            }
        }
    }

    private static boolean checkRelativeError(double tolerance, double target,
            double modelled) {
        boolean isItOk = false;
        if (modelled==0 && target==0) isItOk=true;
        if (Math.abs(modelled-target)/target < tolerance) isItOk=true;
        return isItOk;
    }
    
    protected static Executor getCommodityThreadPool() {
    	int size = maxThreads == null ? Commodity.getAllCommodities().size() : maxThreads;
    	
        if (commodityThreadPool==null) commodityThreadPool = Executors.newFixedThreadPool(size); 
        return commodityThreadPool;
    }

    private static Executor getActivityThreadPool() {
    	
    	int size = maxThreads == null ? AggregateActivity.getAllProductionActivities().size() : maxThreads;

        if (activityThreadPool==null) activityThreadPool = Executors.newFixedThreadPool(size); 
        return activityThreadPool;
    }
    
}
