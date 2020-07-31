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
/*
 * Created on Jan 3, 2005
 *
 * To change the template for this generated file go to
 * Window - Preferences - Java - Code Generation - Code and Comments
 */
package com.hbaspecto.pecas.aa.control;

import drasys.or.linear.algebra.Algebra;

import java.util.Iterator;

import com.hbaspecto.pecas.aa.commodity.Commodity;
import com.hbaspecto.pecas.aa.commodity.CommodityZUtility;
import com.hbaspecto.pecas.aa.commodity.Exchange;
import com.hbaspecto.pecas.zones.AbstractZone;

/**
 * @author jabraham
 * 
 * To change the template for this generated type comment go to Window -
 * Preferences - Java - Code Generation - Code and Comments
 */
public class CommodityPriceSurplusDerivativeMatrix {

    public final double data[][];
    
    public final Commodity commodity;

    public static Algebra a = new Algebra();
    public final int size;

    public CommodityPriceSurplusDerivativeMatrix(Commodity c) {
        size = c.getAllExchanges().size();
        data = new double[size][size];
        
        commodity = c;
        double[][] temp = new double[1][1];
        int i;
        int j;

        //Exchange[] exchanges = (Exchange[]) c.getAllExchanges().toArray(new Exchange[1]);
        AbstractZone[] allZones = AbstractZone.getAllZones();

        // need this for adding to the array
        //int[] rows = new int[exchanges.length];
        //        for (int r=0;r<exchanges.length;r++) {
        //            rows[r] = r;
        //        }
        //        
        for (int z = 0; z < allZones.length; z++) {
//            try {
                // first add in the effect of exchange choice
                CommodityZUtility bzu = c.retrieveCommodityZUtility(allZones[z], false);
                CommodityZUtility szu = c.retrieveCommodityZUtility(allZones[z], true);
                if ((c.exchangeType != 'n' && c.exchangeType != 'c')) {
                    temp = bzu.myFlows.getChoiceDerivatives(temp);
                    double scale = -bzu.getQuantity() * c.getBuyingUtilityPriceCoefficient();
                    for (i = 0; i < temp.length; i++) {
                        for (j = 0; j < temp[i].length; j++) {
                            data[i][j]+=temp[i][j]*scale;
                        }
                    }
                }
                //            MatrixI bzuDerivatives = new DenseMatrix(temp);
                if ((c.exchangeType != 'n' && c.exchangeType != 'p')) {
                    temp = szu.myFlows.getChoiceDerivatives(temp);
                    double scale = szu.getQuantity() * c.getSellingUtilityPriceCoefficient();
                    for (i = 0; i < temp.length; i++) {
                        for (j = 0; j < temp[i].length; j++) {
                            data[i][j]+=temp[i][j]*scale;
                        }
                    }
                }

                // now add in the effect of changes in production, consumption
                // and location
                // ENHANCEMENT simplify if there is no exchange choice (not transportable, or exchanged where bought)
                double[] xProbabilitiesDouble = bzu.getExchangeProbabilities();
                //DenseVector xProbabilities = new DenseVector(xProbabilitiesDouble);
                // create a column matrix;
                //DenseMatrix xProbabilitiesMatrix = new DenseMatrix(xProbabilities.size(),1);
                //xProbabilitiesMatrix.setColumn(0,xProbabilities);
                double[] logSumDerivativesDouble = bzu.myFlows.getLogsumDerivativesWRTPrices();
                //DenseVector logSumDerivatives = new DenseVector(logSumDerivativesDouble);
                //DenseMatrix logSumDerivativesMatrix = new DenseMatrix(1,logSumDerivatives.size());
                //logSumDerivativesMatrix.setRow(0,logSumDerivatives);
                //DenseMatrix exchangeQuantityChangeByPrice;

                //exchangeQuantityChangeByPrice = a.multiply(xProbabilitiesMatrix, logSumDerivativesMatrix);
                double bzuDerivative = bzu.getDerivative();
                int col;
                int r;
                for (r = 0; r < xProbabilitiesDouble.length; r++) {
                    for (col = logSumDerivativesDouble.length-1; col>=0; col--) {
//                        data[r][col]+=exchangeQuantityChangeByPrice.elementAt(r, col) * -bzu.getDerivative();
                        data[r][col]+=xProbabilitiesDouble[r]*logSumDerivativesDouble[col]* -bzuDerivative;
                    }
                }

                // now for selling
                // ENHANCEMENT simplify if there is no exchange choice (non transportable, or exchanged where sold)
                xProbabilitiesDouble = szu.getExchangeProbabilities();
                //xProbabilities = new DenseVector(xProbabilitiesDouble);
                // create a column matrix;
                //xProbabilitiesMatrix = new DenseMatrix(xProbabilities.size(),1);
                //xProbabilitiesMatrix.setColumn(0,xProbabilities);
                logSumDerivativesDouble = szu.myFlows.getLogsumDerivativesWRTPrices();
                //logSumDerivatives = new DenseVector(logSumDerivativesDouble);
                //logSumDerivativesMatrix = new DenseMatrix(1,logSumDerivatives.size());
                //logSumDerivativesMatrix.setRow(0,logSumDerivatives);

                //exchangeQuantityChangeByPrice = a.multiply(xProbabilitiesMatrix, logSumDerivativesMatrix);
                double szuDerivative = szu.getDerivative();

                for ( r = 0; r < xProbabilitiesDouble.length; r++) {
                    for (col = logSumDerivativesDouble.length-1;col>=0;col--) {
                        data[r][col]+=xProbabilitiesDouble[r]*logSumDerivativesDouble[col] * szuDerivative;
                    }
                }
//            } catch (AlgebraException e) {
//                e.printStackTrace();
//                throw new RuntimeException(e);
//
//            }
        }

        // add in the effects of imports and exports
        Iterator exIt = c.getAllExchanges().iterator();
        int xNum = 0;
        while (exIt.hasNext()) {
            Exchange x = (Exchange) exIt.next();
            double[] importsAndExports = x.importsAndExports(x.getPrice());
            data[xNum][xNum]+= importsAndExports[2] - importsAndExports[3];
            //            add(xNum,xNum,importsAndExports[2]-importsAndExports[3]);
            xNum++;
        }

    }
}