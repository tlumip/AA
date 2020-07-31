/*
 * Copyright  2005 HBA Specto Incorporated
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
package com.hbaspecto.pecas.aa.commodity;

import java.util.Iterator;

import org.apache.log4j.Logger;

import com.hbaspecto.pecas.ChoiceModelOverflowException;
import com.hbaspecto.pecas.OverflowException;
import com.hbaspecto.pecas.aa.travelAttributes.TravelUtilityCalculatorInterface;
import com.hbaspecto.pecas.zones.PECASZone;


/**
 * This the the utility of buying or selling a commodity in a zone.  It is a function of the commodity prices in the exchange zones and the associated transport disutility.
 *
 * @author John Abraham
 */
public class SellingZUtility extends CommodityZUtility {

    public static Logger logger = Logger.getLogger(SellingZUtility.class);

    public SellingZUtility(Commodity c, PECASZone t, TravelUtilityCalculatorInterface tp) {
        super(c, t, tp);
        c.addSellingZUtility(this);
     //   t.addSellingZUtility(this, c);
    }

    public String toString() {
        return "SellingZUtility" + super.toString();
    };

    public void allocateQuantityToFlowsAndExchanges() throws OverflowException {
    	if (getQuantity() < 0) {
    		logger.error("Negative quantity for "+this);
    	}
        try {
            myFlows.setAggregateQuantity(getQuantity(), getDerivative());
        } catch (ChoiceModelOverflowException e) {
            throw new OverflowException(e.toString());
        }


    }

    public void addAllExchanges() {
        Iterator it = myCommodity.getAllExchanges().iterator();
        while (it.hasNext()) {
            Exchange x = (Exchange) it.next();
            x.addFlowIfNotAlreadyThere(this, false);
        }
    }

    public void addExchange(Exchange x) {
        x.addFlowIfNotAlreadyThere(this, false);
    }
}
