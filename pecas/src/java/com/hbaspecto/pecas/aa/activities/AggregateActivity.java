/*
 *  Copyright 2005-2018 HBA Specto Incorporated
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

import org.apache.log4j.Logger;

import com.hbaspecto.discreteChoiceModelling.Alternative;
import com.hbaspecto.discreteChoiceModelling.LogitModel;
import com.hbaspecto.pecas.ChoiceModelOverflowException;
import com.hbaspecto.pecas.NoAlternativeAvailable;
import com.hbaspecto.pecas.OverflowException;
import com.hbaspecto.pecas.aa.activities.ProductionActivity.CantRedoError;
import com.hbaspecto.pecas.aa.technologyChoice.ConsumptionFunction;
import com.hbaspecto.pecas.aa.technologyChoice.ProductionFunction;
import com.hbaspecto.pecas.zones.AbstractZone;
import com.hbaspecto.pecas.zones.PECASZone;

/**
 * This is the class that represents a certain type of activity using aggregate quantities and prices.  There is no
 * microsimulation in this class
 *
 * @author J. Abraham
 */
public class AggregateActivity extends ProductionActivity {

    private static Logger logger = Logger.getLogger("com.pb.models.pecas");
    private double totalAmount;
    private ConsumptionFunction lnkConsumptionFunction;
    private ProductionFunction lnkProductionFunction;
   // private double locationDispersionParameter;
    static boolean forceConstraints = true;

    static boolean doingAllocationForSizes = false;
    public static void setDoingAllocationForSizes(boolean doingAllocationForSizes) {
		AggregateActivity.doingAllocationForSizes = doingAllocationForSizes;
	}
	public static boolean isDoingAllocationForSizes() {
		return doingAllocationForSizes;
	}


	public LogitModel logitModelOfZonePossibilities = new LogitModel();


    /**
     * Creates an aggregate activity. The activity needs to set up its array of AggregateDistributions
     * to keep track of how much activity is in each zone.  To ensure that this
     * is done properly we make the constructor take as an argument the array of all zones.
     * @param name Name of the new activity
     * @param allZones an array of all the TAZs in the system
     * @param importerExporter True if this is an importer/exporter, false otherwise
     * @param constInExchangeSize 
     */
    public AggregateActivity(String name, PECASZone[] allZones, boolean importerExporter, boolean constInExchangeSize) {
        super(name, allZones, importerExporter, constInExchangeSize);
        initialize(allZones);
    }
    
    /**
     * Create an aggregate activity with a specific activity number.
     * @param name Name of the new activity
     * @param number Activity number
     * @param allZones an array of all the TAZs in the system
     * @param isImporterOrExporter True if this is an importer/exporter, false otherwise
     * @param constInExchangeSize
     */
    public AggregateActivity(String name, int number, PECASZone[] allZones, boolean isImporterOrExporter, boolean constInExchangeSize) {
        super(name, number, allZones, isImporterOrExporter, constInExchangeSize);
        initialize(allZones);
    }
    
    private void initialize(PECASZone[] allZones) {
        // fix the amountInZone references to be objects of AggregateDistribution instead of just AmountInZone;
        logitModelOfZonePossibilities = new LogitModel();
        for (int z = 0; z < allZones.length; z++) {
            // TODO create new ImporterExporter activity type that doesn't have any AmountInZone items for internal zones
            // if (!importerExporter || allZones[z].isExternal()) {
                 myDistribution[z] = new ActivityInLocationWithLogitTechnologyChoice(this, allZones[z]);
                logitModelOfZonePossibilities.addAlternative((AggregateDistribution) myDistribution[z]);
            //} 
        }
    }


    /**
     * This is the main workhorse routine that allocates the regional production to zones.  PA calls this for each production
     * activity.  To simulate economic equilibrium, aaModel may have to call this function once, then repeatedly call
     * reMigrateAndReAllocate. Note, though, that not all activities are in spatial equilibrium.  For ODOT, for instance,
     * household transitions are not in equilibrium. This routine models a discrete time step.  It's important to be able to
     * adjust the time step if necessary, and in fact different things may need different time steps.  So these routines
     * should be careful to use this parameter.  A longer time step implies larger changes.
     *
     * @param timeStep the amount of time that has passed since this function was last called.  If zero, then the
     *        ProductionActivity is to redo the previous allocation
     */
    public void migrationAndAllocation(double timeStep) throws ChoiceModelOverflowException {
        logitModelOfZonePossibilities.setDispersionParameter(getLocationDispersionParameter());
        if(logger.isDebugEnabled()) {
            logger.debug("total amount for " + this + " is " + getTotalAmount());
        }
        // No difference between reMigration and Migration for AggregateActivity
        try {
            reMigrationAndReAllocation();
        } catch (CantRedoError e) {
            logger.fatal("This error shouldn't occur -- AggregateActivities can always redo their allocation process");
            throw new RuntimeException("This error shouldn't occur -- AggregateActivities can always redo their allocation process");
        }
    }

    /* (non-Javadoc)
     * @see com.pb.models.pecas.ProductionActivity#migrationAndAllocation(double, double, double)
     */
    public void migrationAndAllocation(double timeStep, double inMigration, double outMigration) throws OverflowException {
        setTotalAmount(getTotalAmount() + (inMigration - outMigration));
        try {
            migrationAndAllocation(timeStep);
        } catch (ChoiceModelOverflowException e) {
            logger.error("Overflow exception when allocating activity "+name);
            throw new OverflowException("Overflow exception when allocating activity "+name+" "+e.toString(),e);
        }

    }

    /**
     * For certain types of ProductionActivity, including most AggregateActivities, there is a need to do the allocation, then
     * adjust the prices and redo the allocation repeatedly to find the economic equilibrium.  Thus aaModel needs to call
     * migrationAndAllocation once, to set the in migration, out migration and time period and to reset the previous time
     * point.  Then aaModel can adjust the prices that result and have the ProductionActivity reallocate itself and redo the
     * migration in response to different prices by calling this method.
     * 
     * @throws CantRedoError not all types of ProductionActivity can redo their allocation.  Obviously, then, they can't be
     *                       modelled as being in spatial economic equilibrium
     */
    public void reMigrationAndReAllocation() throws CantRedoError {
        try {
            reMigrationAndReAllocationWithOverflowTracking();
        } catch (OverflowException e) {
            logger.error("Overflow exception when allocating activity "+name);
            throw new RuntimeException("Overflow exception when allocating activity "+name,e);
        }
    }

    public void reMigrationAndReAllocationWithOverflowTracking() throws OverflowException {
    	double remainingAmount = getTotalAmount();
    	logitModelOfZonePossibilities.setDispersionParameter(getLocationDispersionParameter());
    	for (Alternative a : logitModelOfZonePossibilities.getAlternatives()) {
    		if (a instanceof AggregateDistribution) {
    			((AggregateDistribution) a).setLockUtilities(true);
    		}
    	}
    	double[] probs;
    	try {
    		probs = logitModelOfZonePossibilities.getChoiceProbabilities();
    	} catch (ChoiceModelOverflowException e) {
    		throw new OverflowException("Overflow allocating "+this.name+","+e.toString(),e);
    	} catch (NoAlternativeAvailable e) {
    		throw new OverflowException("Overflow allocating "+this.name+","+e.toString(),e);
    	}
    	// first do constrained zones;
    	double originalTotal = remainingAmount;
    	double totalProbs = 0;
    	boolean allZonesConstrained = true;

    	for (int i = 0; i < probs.length; i++) {
    		Alternative a = logitModelOfZonePossibilities.alternativeAt(i);
    		if (a instanceof AggregateDistribution) {
    			AggregateDistribution amountInZone = (AggregateDistribution) a;
    			// want to constrain, unless 
    			
    			if (    amountInZone.constrained  && isForceConstraints() && 
    					// TODO should have a minimum amount for constraints, definitional based on activity, set to zero if below this
    					// FIXME what about when constant is effectively negative infinity but constraints are not active (e.g. constrained to zero in past year)
    					(!isDoingAllocationForSizes() ||  constInExchangeSize ))// turn off constraints if we're doing the allocation for exchagne sizes, but not for the special activities like importers and exporters
    			{
    				/*if (isDoingAllocationForSizes() && amountInZone.constraintQuantity == 0) {
    				   logger.info(amountInZone.toString() + "using zero constraint in allocating initial size terms");
    				}*/
    				remainingAmount -= amountInZone.constraintQuantity;
    				amountInZone.setAggregateQuantity(amountInZone.constraintQuantity, 0);
    				if (probs[i] == 0 && amountInZone.constraintQuantity>0) {
    					if (!isDoingAllocationForSizes()) {
    						logger.warn("No valid choices for "+amountInZone+" yet constraint is non=zero ("+amountInZone.constraintQuantity+")");
    					}
    				}
    			} else {
    				totalProbs += probs[i];
    				allZonesConstrained = false;
    			}
    		} else {
    			logger.fatal("AmountInZone object for activity "+this+" is not of type AggregateDistribution -- this is a programming error, contact developer");
    			throw new RuntimeException("AmountInZone object for activity "+this+" is not of type AggregateDistribution -- this is a programming error, contact developer");
    		}
    	}
    	// now allocate remaining amount, rely on the IIA property of the logit model
    	

    	if (originalTotal==0 && remainingAmount <0) {
    		String msg = "For "+this+" total amount is zero but constraints are non-zero";
    		logger.fatal(msg);
    		throw new RuntimeException(msg);
    	}
    	if (originalTotal != 0) {
    		if (remainingAmount <=0 && remainingAmount/originalTotal < -.00001) {
    			logger.warn("Constraints on "+this+" exceed total amount of activity by "+-remainingAmount + " this is higher than what we might expect from rounding error check ActivityConstraintsI against ActivityTotalsI");
    			remainingAmount =0;
    		}
    		if (remainingAmount >0 && remainingAmount/originalTotal <0.001 & !allZonesConstrained) {
    			logger.info("More than 99.9% of "+this+" is constrained, assuming you wanted to constrain 100 and that this is due to rounding error!  Constraints on zones not mentioned in ActivityConstraintsI are being set to 0");
    			constrainRemainingZonesToZero(probs);        
    		}
    		if (remainingAmount <=0 && !allZonesConstrained) {
    			logger.debug("Constraints for "+this+" meet or exceed total, thus all unconstrained zones must have zero quantity, constraints are being updated accordingly");
    			constrainRemainingZonesToZero(probs);
    		}
    		if (allZonesConstrained && Math.abs(remainingAmount/originalTotal)>0.001) {
    			logger.error("All zones constrained for "+this+" but constraints do not match total within 0.1%, this should be fixed so that ActivityConstraintsI total up to match ActivityTotalsI");
    		}
    	}
    	
    	// TODO if there are superdistrict constraints, allocate those superdistrict totals amongst the 
    	// conditional probabilities that make up the zones in the superdistrict.

    	for (int i = 0; i < probs.length; i++) {
    		Alternative a = logitModelOfZonePossibilities.alternativeAt(i);
    		if (a instanceof AggregateDistribution) {
    			if (!(((AggregateDistribution)a).isConstrained()  && isForceConstraints() && 
    					(!isDoingAllocationForSizes() ||  constInExchangeSize))) {
    				if (totalProbs <=0) {
    					logger.error("Allocating "+remainingAmount+" of "+this+" amongst unconstrained zones, but utility of every unconstrained zone is negative infinity");
    					logger.error("This can happen if the unconstrained zones have no suitable space, in which case the solution is to enter a constraint of zero in any zone with no suitable space");
    					logger.error("The first unconstrained zone is "+a);
    					throw new OverflowException("Allocating "+remainingAmount+" of "+this+" amongst unconstrained zones, but utility of every unconstrained zone is negative infinity, perhaps there is no suitable space in the unconstrained zones.  First unconstrained zone is "+a);
    				}
    				double prob = probs[i]/totalProbs;
    				((AggregateDistribution) a).setAggregateQuantity(remainingAmount * prob, prob * (1 - prob) * logitModelOfZonePossibilities.getDispersionParameter() * remainingAmount);
    			}
    		} else {
    			logger.fatal("AmountInZone object for activity "+this+" is not of type AggregateDistribution -- this is a programming error, contact developer");
    			throw new RuntimeException("AmountInZone object for activity "+this+" is not of type AggregateDistribution -- this is a programming error, contact developer");
    		}
    	}
    	for (Alternative a : logitModelOfZonePossibilities.getAlternatives()) {
    		if (a instanceof AggregateDistribution) {
    			((AggregateDistribution) a).setLockUtilities(false);
    		}
    	}
    }
    
	@Override
	public void checkConstraintConsistency() {
    	double remainingAmount = getTotalAmount();
    	// check for bad inputs or inactive activity
    	if (remainingAmount <=0) {
    		if (remainingAmount ==0) {
	    		logger.warn("Quantity of "+this+" is zero");
    		} else {
    			String msg = "Negative amount of "+this+" in model";
    			logger.fatal(msg);
    			throw new RuntimeException(msg);
    		}
    	} else
    	// check the constraint consistency
    	{
    		double constraintTotal =0;
	    	double originalTotal = remainingAmount;
	    	boolean allZonesConstrained=true;
	    	for (AmountInZone a : myDistribution) {
	    		if (importerExporter && !a.myZone.isExternal()) {
	    			a.setConstrained(true);
	    			a.constraintQuantity=0;
	    		} else {
	    			if (a.isConstrained()) {
	    				remainingAmount -= a.constraintQuantity;
	    				constraintTotal += a.constraintQuantity;
	    			} else {
	    				allZonesConstrained= false;
	    			}
	    		}
	    	}
	    	if (allZonesConstrained || remainingAmount<=.05*originalTotal) {
	    		if (!allZonesConstrained && remainingAmount >.0001*originalTotal) {
	    			logger.error("Less than 5% of "+this+" was left to be allocated amongst unconstrained zones, assuming this is an error and all zones should be constrained");
	    		}
	    		double ratio = remainingAmount/originalTotal;
	    		if (Math.abs(ratio)>.0001) {
	    			if (allZonesConstrained) logger.error("Total amount of "+this+" is "+getTotalAmount()+" but constraints are "+(1-ratio)*100+"% of this, and all zones are constrained.  Scaling constraints for now but please fix this.");
	    			else logger.error("All zones of " + this + " implictly constrained because constraints exceed total by " + (-ratio) * 100 + "%");
	    			logger.error("originalTotal = "+originalTotal+", constraints sum to "+constraintTotal);
	    		}
	    		else {
	    			String msg = "All zones of "+this+" are constrained";
	    			if (!allZonesConstrained) msg = msg + " (implicitly since constraints=total)";
	    			logger.info(msg);
	    		}
	    		for (AmountInZone a : myDistribution) {
	    			if (a.isConstrained()) {
	    				a.constraintQuantity *= (1+ratio);
	    				a.quantity = a.constraintQuantity;
	    			} else {
	    				a.setConstrained(true);
	    				a.constraintQuantity = 0;
	    				a.quantity = a.constraintQuantity;
	    			}
	    		}
	    	} else {
	    		logger.info("Amount of "+this+" to be allocated amongst unconstrained zones is "+remainingAmount+ " ("+remainingAmount/originalTotal*100+"%)");
	    	}
    	}
    	
		
	}



	private void constrainRemainingZonesToZero(double[] probs) {
		for (int i = 0; i < probs.length; i++) {
		    Alternative a = logitModelOfZonePossibilities.alternativeAt(i);
		    if (a instanceof AggregateDistribution) {
		        AggregateDistribution amountInZone = (AggregateDistribution) a;
		        if (!amountInZone.constrained) {
		        	amountInZone.constraintQuantity=0;
		        	amountInZone.constrained=true;
		        	amountInZone.quantity=0;
		        }
		    } else {
		        logger.fatal("AmountInZone object for activity "+this+" is not of type AggregateDistribution -- this is a programming error, contact developer");
		        throw new RuntimeException("AmountInZone object for activity "+this+" is not of type AggregateDistribution -- this is a programming error, contact developer");
		    }
		}
	}

    public double getUtility() throws OverflowException {
        try {
            return logitModelOfZonePossibilities.getUtility(1);
        } catch (ChoiceModelOverflowException e) {
            throw new OverflowException(e.toString());
        }
    }

/*------------------------Getters and Setters---------------------------------------------------------*/

    public ConsumptionFunction getConsumptionFunction() {
        return lnkConsumptionFunction;
    }


    public ProductionFunction getProductionFunction() {
        return lnkProductionFunction;
    }

    public void setProductionFunction(ProductionFunction productionFunction) {
        lnkProductionFunction = productionFunction;
    }

    public void setConsumptionFunction(ConsumptionFunction consumptionFunction) {
        lnkConsumptionFunction = consumptionFunction;
    }

    public double getLocationDispersionParameter() {
        return logitModelOfZonePossibilities.getDispersionParameter();
    }

    public void setLocationDispersionParameter(double locationDispersionParameter) {
        logitModelOfZonePossibilities.setDispersionParameter(locationDispersionParameter);
        logger.debug("Setting dispersion parameter for "+this+" to "+locationDispersionParameter);

    }

    public void setTotalAmount(double totalAmount) {
        this.totalAmount = totalAmount;
    }

    public double getTotalAmount() {
        return totalAmount;
    }


    public double[] getConstantsForLocations() {
        double[] cs = new double[myDistribution.length];
        for (int z=0;z<myDistribution.length;z++) {
            cs[z] = myDistribution[z].getLocationSpecificUtilityInclTaxes();
        }
        return cs;
    }


    public static boolean isForceConstraints() {
    	return forceConstraints;
    }


    public static void setForceConstraints(boolean forceConstraints) {
        AggregateActivity.forceConstraints = forceConstraints;
    }



}

