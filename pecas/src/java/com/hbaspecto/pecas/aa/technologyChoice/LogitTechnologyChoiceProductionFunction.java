/*
 *  Copyright 2006 HBA Specto Incorporated
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
package com.hbaspecto.pecas.aa.technologyChoice;

import java.util.List;

import org.apache.log4j.Logger;

import com.hbaspecto.pecas.ChoiceModelOverflowException;
import com.hbaspecto.pecas.NoAlternativeAvailable;
import com.hbaspecto.pecas.aa.commodity.AbstractCommodity;

public class LogitTechnologyChoiceProductionFunction implements
        ProductionFunction {

    public final LogitTechnologyChoice myTechnologyChoice;
    static final Logger logger = Logger.getLogger("com.pb.models.pecas");
    
    public LogitTechnologyChoiceProductionFunction(LogitTechnologyChoice myTechChoice) {
        myTechnologyChoice= myTechChoice;
    }

    public int size() {
        if (myTechnologyChoice.sellingUtilities==null) {
            String msg = "Error, sortToMatch not called yet we don't know how many commodities are in play in the logit substitution";
            logger.fatal(msg);
            throw new RuntimeException(msg);
        }
        return myTechnologyChoice.sellingUtilities.length;
    }

    public AbstractCommodity commodityAt(int i) {
        if (myTechnologyChoice.getMyCommodityOrder()==null) {
            String msg = "Error, sortToMatch not called yet we don't know how many commodities are in play in the logit substitution";
            logger.fatal(msg);
            throw new RuntimeException(msg);
        }
        return (AbstractCommodity) myTechnologyChoice.getMyCommodityOrder().get(i);
    }

    public void doFinalSetupAndSetCommodityOrder(List commodityList) {
        myTechnologyChoice.doFinalSetupAndSetCommodityOrder(commodityList);
    }

    public double[] amountsDerivatives(double[] individualCommodityUtilities) {
        String errorString = "Don't know how to calculate amountsDerivatives in technology choice production function, use the techChoice method instead";
        logger.error(errorString);
        throw new RuntimeException(errorString);
    }

	public double[] calcAmounts(double[] buyingZUtilities,
			double[] sellingZUtilities, int zoneIndex) throws NoAlternativeAvailable {
		try {
			return myTechnologyChoice.calcSellingAmounts(buyingZUtilities, sellingZUtilities, zoneIndex);
		} catch (ChoiceModelOverflowException e) {
            logger.error("Can't calculate amounts "+e);
            throw new RuntimeException("Can't calculate amounts",e);
		}
	}

}
