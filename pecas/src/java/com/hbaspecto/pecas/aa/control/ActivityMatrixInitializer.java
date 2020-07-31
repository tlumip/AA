/**
 * 
 */
package com.hbaspecto.pecas.aa.control;

import com.hbaspecto.discreteChoiceModelling.Alternative;
import com.hbaspecto.models.FutureObject;
import com.hbaspecto.pecas.ChoiceModelOverflowException;
import com.hbaspecto.pecas.NoAlternativeAvailable;
import com.hbaspecto.pecas.aa.activities.AggregateActivity;
import com.hbaspecto.pecas.aa.activities.AggregateDistribution;
import com.hbaspecto.pecas.zones.AbstractZone;

import no.uib.cipr.matrix.DenseMatrix;
import no.uib.cipr.matrix.DenseVector;
import no.uib.cipr.matrix.Matrix;


class ActivityMatrixInitializer implements Runnable {
        
        final AggregateActivity activity;
        
        FutureObject done = new FutureObject();
        final double[][] dStorage;
        
        ActivityMatrixInitializer(AggregateActivity actParam, double[][] tempStorageParam) {
            activity = actParam;
            dStorage = tempStorageParam;
        }

        public void run() {
        	
        	// Initialize ZUtilities
        	for (Alternative alt : activity.logitModelOfZonePossibilities.getAlternatives()) {
        		if (alt instanceof AggregateDistribution) {
        			((AggregateDistribution) alt).setLockUtilities(true);
        		}
        	}
        	
            // build up relationship between average commodity price and total surplus
            DenseVector pl; // P(z|a) in new documentation
            DenseMatrix fpl; 
            try {
                pl= new DenseVector(activity.logitModelOfZonePossibilities.getChoiceProbabilities());
                fpl = new DenseMatrix(activity.logitModelOfZonePossibilities.choiceProbabilityDerivatives());
                
            } catch (ChoiceModelOverflowException e) {
                e.printStackTrace();
                done.setValue(e);
                throw new RuntimeException("Can't solve for amounts in zone",e);
            } catch (NoAlternativeAvailable e) {
                e.printStackTrace();
                done.setValue(e);
                throw new RuntimeException("Can't solve for amounts in zone",e);
            }
            // dulbydprice is derivative of location utility wrt changes in average prices of commodites
            // is d(LU(a,z)/d(Price(bar)(c)) in new notation
            DenseMatrix dulbydprice = new DenseMatrix(fpl.numColumns(),AveragePriceSurplusDerivativeMatrix.numCommodities);
            for (int location =0;location<pl.size();location++) {
                AggregateDistribution l = (AggregateDistribution) activity.logitModelOfZonePossibilities.alternativeAt(location);
                double[] valuesForRow = l.calculateLocationUtilityWRTAveragePrices();
                //dulbydprice.set(rows,columns,valuesToAdd)
                for (int col=0;col<valuesForRow.length;col++) {
                	dulbydprice.set(location,col,valuesForRow[col]);
                }
            }
            Matrix dLocationByDPrice = new DenseMatrix(AbstractZone.getAllZones().length,AveragePriceSurplusDerivativeMatrix.numCommodities);
            dLocationByDPrice = fpl.mult(dulbydprice, dLocationByDPrice);
                for (int r1 = 0;r1<dLocationByDPrice.numRows();r1++) {
                    for (int c1 = 0; c1<dLocationByDPrice.numColumns();c1++) {
                        if (Double.isNaN(dLocationByDPrice.get(r1,c1))) {
                        	// TODO write out these matrices again
                            AveragePriceSurplusDerivativeMatrix.logger.fatal("NaN in dLocationByDPrice");
                            /*AAModel.writeOutMatrix(dLocationByDPrice, "dLocationByDPrice");
                            AAModel.writeOutMatrix(fpl, "zoneChoiceWRTUtility");
                            AAModel.writeOutMatrix(dulbydprice, "utiltiyWRTPrices"); */
                            RuntimeException e = new RuntimeException("NaN in dLocationByDPrice");
                            done.setValue(e);
                            throw e;
                            
                        }
                    }
                }
            for (int location =0;location<pl.size();location++) {
                 DenseVector dThisLocationByPrices = new DenseVector(AveragePriceSurplusDerivativeMatrix.numCommodities);
                for (int i=0;i<dThisLocationByPrices.size();i++) {
                    dThisLocationByPrices.set(i,dLocationByDPrice.get(location,i));
                }
                AggregateDistribution l = (AggregateDistribution) activity.logitModelOfZonePossibilities.alternativeAt(location);
                l.addTwoComponentsOfDerivativesToAveragePriceMatrix(activity.getTotalAmount(),dStorage,dThisLocationByPrices);
//                System.out.println();
            }
            
        	for (Alternative k : activity.logitModelOfZonePossibilities.getAlternatives()) {
        		if (k instanceof AggregateDistribution) {
        			((AggregateDistribution) k).setLockUtilities(false);
        		}
        	}
        	
            done.setValue(new Boolean(true));
        }
        
    }