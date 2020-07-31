/*
 * Copyright  2005 PB Consult Inc.
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
package com.pb.common.model;

import com.pb.common.math.MathUtil;
import com.pb.common.matrix.ColumnVector;
import com.pb.common.matrix.Matrix;

import org.apache.log4j.Logger;


/**
 * Implementation of the Event model for special events.
 *
 * The Event model is a trip generation and distribution model.  The form of
 * the model is:
 *
 *      P = A^theta * exp(lambda * cu)
 *
 *      where:  P - probability of a trip
 *              A - zonal activity size
 *              cu - measure of impedance or combined utility
 *              theta, lambda - estimated coefficients
 *
 *  @author   Andrew Stryker <stryker@pbworld.com>
 *  @version  1.1 2/9/2004
 */
public class EventModel {
    protected Logger logger = Logger.getLogger("com.pb.common.model");
    protected String name;
    protected boolean available;
    protected double theta;
    protected double lambda;
    protected Matrix impedance;
    protected ColumnVector size;
    protected Matrix trips;
    protected int eventZone;

    /**
     * Constructor.
     *
     * Default.
     */
    public EventModel() {
    }

    /**
     * Constructor.
     *
     * Sets model parameters.
     *
     * @param name of the model/scenario
     * @param eventZone location of event
     * @param theta size term coefficient
     * @param lambda impedance coefficient
     */
    public EventModel(String name, int eventZone, float theta, float lambda) {
        this.name = name;
        this.eventZone = eventZone;
        this.theta = theta;
        this.lambda = lambda;
        logger.debug("EventModel object constructed with theta = " + theta +
            " and lambda = " + lambda);
    }

    /**
     * Constructor.
     *
     * Sets model parameters.
     *
     * @param name of the model/scenario
     * @param eventZone location of event
     * @param theta size term coefficient
     * @param lambda impedance coefficient
     */
    public EventModel(String name, int eventZone, double theta, double lambda) {
        this.name = name;
        this.eventZone = eventZone;
        this.theta = theta;
        this.lambda = lambda;
        logger.debug("EventModel object constructed with theta = " + theta +
            " and lambda = " + lambda);
    }

    /*---------- setters and getters ----------*/

    /**
     * Set eventZone.
     *
     * @param eventZone location of the event
     */
    public void setEventZone(int eventZone) {
        this.eventZone = eventZone;
    }

    /**
     * Get eventZone.
     */
    public int getEventZone() {
        return eventZone;
    }

    /**
     * Set theta.
     *
     * @param theta activity size coefficient
     */
    public void setTheta(double theta) {
        this.theta = theta;
        available = false;
    }

    /**
     * Set lambda.
     *
     * @param lambda impedance coefficient
     */
    public void setLambda(double lambda) {
        this.lambda = lambda;
        available = false;
    }

    /**
     * Set name.
     *
     * @param name of the model/scenario
     */
    public void setName(String name) {
        this.name = name;
        available = false;
    }

    /**
     * Set impdenace matrix.
     *
     * @param impedance production to Event zones impedance
     */
    public void setImpedance(Matrix impedance) {
        this.impedance = impedance;
        available = false;
    }

    /**
     * Set size vector.
     *
     * @param size zone activity size
     */
    public void setSize(ColumnVector size) {
        this.size = size;
        available = false;
    }

    /**
     * Get theta.
     *
     * @return theta, the size parameter.
     */
    public double getTheta() {
        return theta;
    }

    /**
     * Get lambda.
     *
     * @return lambda, the impedance parameter.
     */
    public double getLambda() {
        return lambda;
    }

    /**
     * Get name of model.
     *
     * @return lambda, the impedance parameter.
     */
    public String getName() {
        return name;
    }

    /*---------- general methods ----------*/

    /**
     * Indicator for available probabilities.
     */
    public boolean isAvailable() {
        return available;
    }

    /**
     * Indicator of calculation readiness.
     */
    public boolean isReady() {
        if ((impedance == null) || (size == null)) {
            return false;
        }

        return true;
    }

    /**
     * Calculate probabilities in the Event framework.
     *
     * The results are scaled so that each column sums to one.
     *
     * @throws ModelException if size or impedance is not set.
     */
    public void calculateProbalities() throws ModelException {
        calculateTrips((float) 1.0);
    }

    /**
     * Calculate trips in the Event framework.
     *
     * The results are scaled so that each column sums to number of trips,
     * which should be the exogenously determined total number of attractions.
     *
     * @param attractions trip attractions is an int
     *
     * @throws ModelException if size or impedance is not set.
     */
    public void calculateTrips(int attractions) throws ModelException {
        calculateTrips((float) attractions);
    }

    /**
     * Calculate trips in the Event framework.
     *
     * The results are scaled so that each column sums to number of trips,
     * which should be the exogenously determined total number of attractions.
     *
     * @throws ModelException if size or impedance is not set.
     */
    public void calculateTrips(float attractions) throws ModelException {
        if (!isReady()) {
            throw new ModelException("Size and impedance must be set first.");
        }

        logger.debug("calculating trips.");
        logger.debug("        zone  site  theta lambda     size impedance probability");

        int rowCount = impedance.getRowCount();
        int columnCount = impedance.getColumnCount();
        trips = new Matrix(name, "Event model trips", rowCount, columnCount);
        
        int[] externals =impedance.getExternalNumbers();
        logger.info("externals[0]="+externals[0]);
        
        if( externals[0]==0)
            trips.setExternalNumbers(impedance.getExternalNumbers());
        else
            trips.setExternalNumbersZeroBased(impedance.getExternalNumbers());
            
        // loop through the rowCount of size as this can be smaller than that
        // of impedance
        int sizeRowCount = size.getRowCount();
        float value;
        int extRow;

        for (int row = 1; row <= sizeRowCount; row++) {
        	//Wu added next line, it seem like a bug if matrix index numbers are not sequential
        	//getValueAt(row, column) of Matrix or getValueAt(row) of ColumnVector always assume external row and column index
        	extRow=size.getExternalNumber(row-1);
            
           float s = size.getValueAt(extRow);

            if (s > 0) {
                value = (float) (Math.pow(s, theta) * MathUtil.exp(lambda * impedance.getValueAt(
                            extRow, eventZone)));
            } else {
                value = 0;
            }

            if (Double.isNaN(value)) {
                throw new ModelException(ModelException.INVALID_UTILITY);
            }

            trips.setValueAt(extRow, eventZone, value);

            if ((row < 10) || ((row % 100) == 0)) {
                float imp = 0;
                if (s > 0) {
                   imp = impedance.getValueAt(extRow, eventZone);
                }
 
            logger.debug("      " + String.format("%5d ", extRow) +

                    String.format("%5d ", eventZone) +
                    String.format("%6.3f ", theta) +
                    String.format("%6.3f ", lambda) +
                    String.format("%6f ", size.getValueAt(extRow)) +
                    String.format("%9f ", imp) +
                    String.format("%19.17f", trips.getValueAt(extRow, eventZone)));
            }
        }

        scale(attractions);
        available = true;
    }

    /**
     * Scale so that each column sum to the target.
     */
    protected void scale(float target) {
        float columnSum;

        logger.debug("scalingtrips.");
        logger.debug(" zone probalility factor scaled");

        int rowTotal = trips.getRowCount();

        columnSum = (float) trips.getColumnSum(eventZone);

        // Dividing each matrix cell in a column by the column sum results
        // in a column summing to 1.0.  Multiply by the target to get
        // a column summing to the target.
        float factor = target / columnSum;
        float prob;
        float scaled;
        int extRow;

        for (int row = 1; row <= rowTotal; row++) {
            
        	//Wu added next lines, it seem like a bug if matrix zones are not sequential
        	//getValueAt(row, column) of Matrix or getValueAt(row) of ColumnVector always assume external row and column index
        	//eventZone is ok, because usually is is read in as an external zone
        	extRow=trips.getExternalNumber(row-1);
        	
            prob = trips.getValueAt(extRow, eventZone);
            scaled = prob * factor;
            trips.setValueAt(extRow, eventZone, scaled);

            if ((row < 10) || ((row % 100) == 0)) {
                logger.debug("      " + String.format("%5d ", extRow) +
                    String.format("%9f ", prob) +
                    String.format("%10.5f ", factor) +
                    String.format("%18.16f", trips.getValueAt(extRow, eventZone)));
            }
        }
    }

    /**
     * Get the choice probabilities.
     *
     * @return an array containing the choice probabilities.
     */
    public Matrix getTrips() {
        return trips;
    }

    /**
     * Summarize application.
     *
     * @return string describing model application.
     */
    public String summarize() {
        String status = "EventModel summary for " + name + ":\n" + "theta = " +
            theta + "\n" + "lambda = " + lambda + "\n";

        if (isAvailable()) {
            status += ("sum of probabilities: " + trips.getSum());
        } else {
            status += "probabilities not yet calculated";
        }

        return status;
    }

    /**
     * Test case and usage example.
     */
    public static void main(String[] args) {
        double t = 0.4; // theta
        double l = -0.015; // lambda
        int[] zones = { 0, 1, 2 };
        float[] sz = { (float) 8.3, (float) 9.9 };
        float[][] impedance = {
            { (float) 87.342, (float) 98.23 },
            { (float) 97.82, (float) 89.43 }
        };

        ColumnVector a = new ColumnVector(sz);
        a.setExternalNumbers(zones);

        Matrix cu = new Matrix(impedance);
        cu.setExternalNumbers(zones);

        Matrix result;

        Logger log = Logger.getLogger("com.pb.model.EventModel");

        EventModel e1 = new EventModel("test1", 1, t, l);

        try {
            e1.calculateProbalities();
        } catch (ModelException e) {
            log.info("Caught premature calculation attempt.");
        }

        e1.setSize(a);
        e1.setImpedance(cu);
        log.info(e1.summarize());
        e1.calculateProbalities();
        result = e1.getTrips();

        log.info(e1.summarize());

        for (int row = 1; row <= result.getRowCount(); row++)
            for (int column = 1; column <= result.getColumnCount(); column++)
                log.info("zone " + row + "," + column + ": " +
                    result.getValueAt(row, column));
    }
}
