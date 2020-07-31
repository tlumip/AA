/*
 *  Copyright 2019 HBA Specto Incorporated
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
/*
 * Created on Nov 7, 2019
 *
 */
package com.hbaspecto.pecas.aa.travelAttributes;

import java.util.ArrayList;

import org.apache.log4j.Logger;

/**
 * 
 * This class implements non-linear travel costs with an increasing payload 
 * description. The travel costs are calculated as a LinearFunctionOfSomeSkims,
 * but then they are scaled by a payload factor. The payload factor is the purchases
 * per tour and it is
 * 
 * 1+(distance-refdistance)/anotherpayloaddistance
 * 
 * For every doublingdistance increment of distance the payload increases by 1.
 * 
 * At refdistance, the normal amount 1.0 of the put is purchased (or sold)
 * at (refdistance+anohterpayloaddistance), 2.0 of the put is purchased (or sold)
 * at (refdistance + 2*anotherpayloaddistance), 3.0 of the put is purchased (or sold).
 * 
 * This class scales the transport costs appropriately, so that travel costs can be used
 * as a cost of travel per value of commodity.
 * 
 * Note that the transport costs are often in logsum skims, which have no absolute value.
 * However the payload scaling
 * is based on a distance skim that should never go negative.
 * 
 * @author jabraham
 *
 */
public class PayloadNonlinearTransportCosts
    implements TravelUtilityCalculatorInterface {
    
    private static Logger logger = Logger.getLogger(PayloadNonlinearTransportCosts.class);
    
    SomeSkims lastSkims;
    final LinearFunctionOfSomeSkims linearCosts;
    
    double refDistance;
    double anotherPayloadDistance;
    
    final String distanceSkimName;
    
    int matrixIDForDistance;

    /* (non-Javadoc)
     * @see com.pb.models.pecas.TravelUtilityCalculatorInterface#getUtility(com.pb.models.pecas.TravelAttributesInterface)
     */
    public double getUtility(int origin, int destination, TravelAttributesInterface travelConditions) {
        if (travelConditions == lastSkims) {
        	double travelUtility = linearCosts.getUtility(origin, destination, travelConditions);
        	double distance = lastSkims.matrices[matrixIDForDistance].getValueAt(origin,  destination);
        	double payloadFactor = 1 + (distance - refDistance)/anotherPayloadDistance;
        	return travelUtility/payloadFactor;
        } 
        if (travelConditions instanceof SomeSkims) {
            // On the other hand, if the index numbers are not yet correct, then need to look up the matrices by name 
            lastSkims = (SomeSkims) travelConditions;
            matrixIDForDistance = lastSkims.getMatrixId(distanceSkimName);
            // now that we've set up all the index numbers to match the matrix names, we can call recursively to access by index number 
            return getUtility(origin, destination, lastSkims);
        }
        else throw new RuntimeException("Can't use "+this.getClass().getName()+" with travel attributes of type "+travelConditions.getClass());
    }
    
    /* (non-Javadoc)
     * @see com.pb.models.pecas.TravelUtilityCalculatorInterface#getUtilityComponents(int, int, com.pb.models.pecas.TravelAttributesInterface)
     */
    public double[] getUtilityComponents(int origin, int destination, TravelAttributesInterface travelConditions) {
        if (travelConditions == lastSkims) {
        	double[] components = linearCosts.getUtilityComponents(origin,  destination,  travelConditions);
        	double distance = lastSkims.matrices[matrixIDForDistance].getValueAt(origin,  destination);
        	double payloadFactor = 1 + (distance - refDistance)/anotherPayloadDistance;
        	for (int i=0; i<components.length; i++) {
        		components[i] = components[i]/payloadFactor;
        	}
        	return components;
        }
        if (travelConditions instanceof SomeSkims) {
            lastSkims = (SomeSkims) travelConditions;
            matrixIDForDistance = lastSkims.getMatrixId(distanceSkimName);
            return getUtilityComponents(origin, destination, lastSkims);
        }
        else throw new RuntimeException("Can't use "+this.getClass().getName()+" with travel attributes of type "+travelConditions.getClass());
    }

    public PayloadNonlinearTransportCosts(double refDistance, double anotherPayloadDistance, LinearFunctionOfSomeSkims linearTripCosts, String distanceSkimName) {
    	this.refDistance = refDistance;
    	this.anotherPayloadDistance = anotherPayloadDistance;
    	this.linearCosts = linearTripCosts;
    	this.distanceSkimName = distanceSkimName;
    }
    
}
