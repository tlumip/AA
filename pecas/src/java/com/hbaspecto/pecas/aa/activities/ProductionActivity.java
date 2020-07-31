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

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.log4j.Logger;

import com.hbaspecto.pecas.FormatLogger;
import com.hbaspecto.pecas.OverflowException;
import com.hbaspecto.pecas.aa.technologyChoice.ConsumptionFunction;
import com.hbaspecto.pecas.aa.technologyChoice.ProductionFunction;
import com.hbaspecto.pecas.zones.AbstractZone;
import com.hbaspecto.pecas.zones.PECASZone;
import com.hbaspecto.pecas.zones.Zone;

/**
 * This class represents a type of economic activity that occurs in the modelled area.
 * This is an abstract class -- all ProductionActivity is represented by one of the subclasses AggregateActivity or
 * DisaggregateActivity The main role of this class is to track the amount of activity in each zone and to allocate the
 * changes in the amount of activity in each zone over each time step.  As part of this, the class also needs to work with an
 * AssignmentPeriod to load a network with trips.
 * @author John E. Abraham
 */
public abstract class ProductionActivity implements ConstrainableActivity<PECASZone> {

    private static FormatLogger logger = new FormatLogger(Logger.getLogger(ProductionActivity.class));
    /** The textual name of the category of activity.  A unique identifier */
    public final String name;
    public AmountInZone[] myDistribution;
    private double sizeTermCoefficient;
    //private static final Vector<ProductionActivity> allProductionActivities = new Vector<ProductionActivity>();
    private static final Map<Integer, ProductionActivity> allProductionActivities = new LinkedHashMap<>();
    /**
     * @associates <{DevelopmentType}>
     * @supplierCardinality 1..*
     * @supplierRole allowedIn
     */
	public final int myNumber;
	public final boolean importerExporter;
	public final boolean constInExchangeSize;

    @SuppressWarnings("serial")
	public class CantRedoError extends Exception {
        public CantRedoError() { };

        public CantRedoError(String s) {
            super(s);
        };
    };
    
    public void setSizeTermsToZero() {
        for (int z=0;z<myDistribution.length;z++) {
            myDistribution[z].setAllocationSizeTerm(0);
        }
    }
    
    public void increaseSizeTerm(int zoneUserNumber, double sizeTermIncrement) {
        int zoneIndex = AbstractZone.findZoneByUserNumber(zoneUserNumber).getZoneIndex();
        myDistribution[zoneIndex].setAllocationSizeTerm(myDistribution[zoneIndex].getAllocationSizeTerm()+sizeTermIncrement);
    }

    /**
     * Creates a production activity. The activity needs to set up its array of AggregateDistributions
     * to keep track of how much activity is in each zone.  To ensure that this
     * is done properly we make the constructor take as an argument the array of all zones.
     * @param name Name of the new activity
     * @param allZones an array of all the TAZs in the system
     * @param isImporterOrExporter True if this is an importer/exporter, false otherwise
     * @param constInExchangeSize 
     */
    protected ProductionActivity(String name, PECASZone[] allZones, boolean isImporterOrExporter, boolean constInExchangeSize) {
        this(name, allProductionActivities.size(), allZones, isImporterOrExporter, constInExchangeSize);
    }
    
    /**
     * Create a production activity with a specific activity number.
     * @param name Name of the new activity
     * @param number Activity number
     * @param allZones an array of all the TAZs in the system
     * @param isImporterOrExporter True if this is an importer/exporter, false otherwise
     * @param constInExchangeSize
     */
    protected ProductionActivity(String name, int number, PECASZone[] allZones, boolean isImporterOrExporter, boolean constInExchangeSize) {

        ProductionActivity oldOne = retrieveProductionActivity(name);
        if (oldOne != null) {
            String msg = "Tried to create a duplicate ProductionActivity: " + name;
            logger.fatal(msg);
            throw new RuntimeException(msg);
        }
        if(allProductionActivities.containsKey(number)) {
            String msg = "Tried to create two ProductionActivities with the same activity number: " + number;
            logger.fatal(msg);
            throw new RuntimeException(msg);
        }
        this.name = name;
        myDistribution = new AmountInZone[allZones.length];
        for (int z = 0; z < allZones.length; z++) {
            myDistribution[z] = new AmountInZone(this, allZones[z]);
        }
        myNumber = number;
        allProductionActivities.put(myNumber, this);
        importerExporter = isImporterOrExporter;
        this.constInExchangeSize = constInExchangeSize; 
    }
    
    /**
     * Retrieves the activity with the specified number, or null if no such
     * activity exists
     */
    public static ProductionActivity retrieveProductionActivity(int number) {
        return allProductionActivities.get(number);
    }

    /**
     * Retrieves the activity with the specified name, or null if no such
     * activity exists
     */
    public static ProductionActivity retrieveProductionActivity(String name) {
        for(ProductionActivity act : allProductionActivities.values()) {
            if (act.name.equals(name)) {
                return act;
            }
        }
        return null;
    }

    public static Collection<ProductionActivity> getAllProductionActivities() {
        return Collections.unmodifiableCollection(allProductionActivities.values());
    }

    public AmountInZone[] getMyDistribution() {
        return myDistribution;
    }
    
    @Override
    public double getQuantity(PECASZone zone) {
        return getAmountInZone(zone).getQuantity();
    }

    @Override
    public void constrain(PECASZone zone, double amount) {
        AmountInZone amt = getAmountInZone(zone);
        amt.constrained = true;
        amt.constraintQuantity = amount;
        
    }

    @Override
    public void unconstrain(PECASZone zone) {
        AmountInZone amt = getAmountInZone(zone);
        amt.constrained = false;
        amt.constraintQuantity = 0;
    }

    @Override
    public boolean isConstrained(PECASZone zone) {
        return getAmountInZone(zone).isConstrained();
    }

    @Override
    public double constrainedAmount(PECASZone zone) {
        AmountInZone amt = getAmountInZone(zone);
        if (!amt.isConstrained()) {
            throw new IllegalArgumentException("No constraint for zone " + zone);
        }
        return amt.constraintQuantity;
    }
    
    /**
     * Returns the {@code AmountInZone} for the given zone
     */
    public AmountInZone getAmountInZone(PECASZone zone) {
        return myDistribution[zone.getZoneIndex()];
    }
    
    /**
     * Returns the {@code AmountInZone} for the given TAZ number
     */
    public AmountInZone getAmountInUserZone(int zoneNumber) {
        for (AmountInZone amount : myDistribution) {
            if (amount.myZone.getZoneUserNumber() == zoneNumber) {
                return amount;
            }
        }
        return logger.throwFatalGeneric("No TAZ with number %d", zoneNumber);
    }

    /**
     * setDistribution sets the amount of activity in a zone.  The ProbabilityDensityFunction v can be used to specify
     * how this activity might not be homogeneous.
     * @param zone the zone in which the activity is being set
     * param v a statistical function for accounting for the variation within this category of activity, only used for
     * Disaggregate activities
     * @param quantity the amount of activity in the zone
     */
    public void setDistribution(AbstractZone zone, double quantity) {
        for (int z = 0; z < myDistribution.length; z++) {
            if (myDistribution[z].getMyZone().equals(zone)) {
                myDistribution[z].setQuantity(quantity);
            }
        }
    }

    /**
     * setDistribution sets the amount of activity in a zone.  The ProbabilityDensityFunction v can be used to specify
     * how this activity might not be homogeneous.
     * @param zone the zone in which the activity is being set
     * param v a statistical function for accounting for the variation within this category of activity, only used for
     * Disaggregate activities
     * @param quantity the amount of activity in the zone
     * @param constantForSizeTerm 
     */
    public void setDistribution(AbstractZone zone,  double quantity, double sizeTerm, double locationUtility, double constantForSizeTerm) {
        for (int z = 0; z < myDistribution.length; z++) {
            if (myDistribution[z].getMyZone().equals(zone)) {
                myDistribution[z].setQuantity(quantity);
                myDistribution[z].setLocationSpecificUtilityInclTaxes(locationUtility);
                myDistribution[z].setAllocationSizeTerm(sizeTerm);
                myDistribution[z].setActivityZoneConstantForSizeTerms(constantForSizeTerm);
            }
        }
    }

    /**
     * This is the main workhorse routine that allocates the regional production to zones.  PA calls this for each production
     * activity.  To simulate economic equilibrium, AAModel may have to call this function once, then repeatedly call
     * reMigrateAndReAllocate. Note, though, that not all activities are in spatial equilibrium.  For ODOT, for instance,
     * household transitions are not in equilibrium. This routine models a discrete time step.  It's important to be able to
     * adjust the time step if necessary, and in fact different things may need different time steps.  So these routines
     * should be careful to use this parameter.  A longer time step implies larger changes.
     * @param timeStep the amount of time that has passed since this function was last called.  If zero, then the
     * ProductionActivity is to redo the previous allocation
     * @param inMigration the amount of activity moving to the region
     * @param outMigration the amount of activity leaving the region.  Net migration is inMigration-outMigration.
     */
    abstract public void migrationAndAllocation(double timeStep, double inMigration, double outMigration) throws OverflowException ;

    /**
     * For certain types of ProductionActivity, including most AggregateActivities, there is a need to do the allocation, then
     * adjust the prices and redo the allocation repeatedly to find the economic equilibrium.  Thus AAModel needs to call
     * mygrationAndAllocation once, to set the in migration, out migration and time period and to reset the previous time
     * point.  Then AAModel can adjust the prices that result and have the ProductionActivity reallocate itself and redo the
     * migration in response to different prices by calling this method.
     * @exception CantRedoError not all types of ProductionActivity can redo their allocation.  Obviously, then, they can't be
     * modelled as being in spatial economic equilibrium
     */
    abstract public void reMigrationAndReAllocation() throws CantRedoError;

    public abstract double getUtility() throws OverflowException ;

    /**
     * @associates <{ProductionFunction}>
     * @supplierRole make table
     * @supplierCardinality 1
     * @clientCardinality 1..*
     */
    public abstract ProductionFunction getProductionFunction();

    /**
     * @associates <{ConsumptionFunction}>
     * @supplierRole use table
     * @clientCardinality 1..*
     */
    public abstract ConsumptionFunction getConsumptionFunction();

    public String toString() { return name; };

    public double getSizeTermCoefficient() {
        return sizeTermCoefficient;
    }

    public void setSizeTermCoefficient(double sizeTermCoefficient) {
        this.sizeTermCoefficient = sizeTermCoefficient;
    }

	public int getNumber() {
		return myNumber; 
	}

	public abstract void checkConstraintConsistency();

}
