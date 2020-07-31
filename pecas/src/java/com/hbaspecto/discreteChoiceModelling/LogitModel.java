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
package com.hbaspecto.discreteChoiceModelling;


import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import org.apache.log4j.Logger;

import com.hbaspecto.pecas.ChoiceModelOverflowException;
import com.hbaspecto.pecas.NoAlternativeAvailable;
import com.hbaspecto.pecas.sd.estimation.ExpectedValue;

import no.uib.cipr.matrix.DenseMatrix;
import no.uib.cipr.matrix.DenseVector;
import no.uib.cipr.matrix.Matrix;
import no.uib.cipr.matrix.Vector;

public class LogitModel extends DiscreteChoiceModel implements ParameterSearchAlternative {
    private static Logger logger = Logger.getLogger("com.pb.models.pecas");

    private double dispersionParameter;
    private Coefficient wrappedDispersionParameter;
    private double constantUtility=0;
    private Coefficient wrappedConstantUtility;
    private ArrayList<Alternative> alternatives;
    
    private Double randomNumber = null;

    private Consumer consumer;
    private boolean caching = false;

    public void allocateQuantity(double amount) throws ChoiceModelOverflowException {
        double[] probs;
        try {
            probs = getChoiceProbabilities();
        } catch (NoAlternativeAvailable e) {
            throw new ChoiceModelOverflowException("Can't allocate quantity because all alternatives are unavailable", e);
        }
        for (int i=0;i<probs.length;i++) {
            Alternative a = alternativeAt(i);
            ((AggregateAlternative) a).setAggregateQuantity(amount*probs[i],amount*probs[i]*(1-probs[i])*dispersionParameter);
        }
    }

    /** @param a the alternative to add into the choice set */
    public void addAlternative(Alternative a) {
        alternatives.add(a);
    }
    
    public boolean hasAlternatives(){
    	return !alternatives.isEmpty();
    }

    public LogitModel() {
        alternatives = new ArrayList<>();
        dispersionParameter = 1.0;
    }

    //use this constructor if you know how many alternatives
    public LogitModel(int numberOfAlternatives) {
        alternatives = new ArrayList<>(numberOfAlternatives);
        dispersionParameter = 1.0;
    }
    
    public void setConsumer(Consumer consumer) {
        this.consumer = consumer;
    }

    private double lastUtility;
    private boolean utilityCached = false;

	public double getUtilityNoSizeEffect() throws ChoiceModelOverflowException {
	    if(utilityCached)
            return lastUtility;
        double sum = 0;
        int i = 0;
        while (i<alternatives.size()) {
            sum += Math.exp(getDispersionParameter() * ((Alternative)alternatives.get(i)).getUtility(getDispersionParameter()));
            i++;
        }
        if (sum==0) {
            if (logger.isDebugEnabled()) logger.debug("No alternative available for "+this+", returning -Infinity for logsum calculation");
            return Double.NEGATIVE_INFINITY;
        }
        double util = (1 / getDispersionParameter()) * Math.log(sum);
        if (Double.isNaN(util)||Double.isInfinite(util)) {
            logger.error("Choice model overflow error in composite utility calculation, denominator term is "+sum);
            logger.error("Dispersion parameter is "+getDispersionParameter());
            int j = 0;
            while (j<alternatives.size()) {
                Alternative a = alternatives.get(j);
                logger.error("Alt "+a+" has utility "+a.getUtility(getDispersionParameter()));
                j++;
            }
            throw new ChoiceModelOverflowException("Overflow getting composite utility");
        }
        if(caching)
            utilityCached = true;
        lastUtility = util + getConstantUtility();
        if (consumer != null)
            consumer.consumeCompositeUtility(lastUtility);
        return lastUtility;
    }
    
    /** @return the composite utility (log sum value) of all the alternatives */
    public double getUtility(double higherLevelDispersionParameter) throws ChoiceModelOverflowException {
        return getUtilityNoSizeEffect();
    }

    public double getDispersionParameter() {
        if(wrappedDispersionParameter == null)
            return dispersionParameter;
        else
            return wrappedDispersionParameter.getValue();
    }
    
    /**
     * Returns the coefficient object representing the dispersion parameter, if this
     * parameter was set using a coefficient object. Otherwise returns null.
     * @return The coefficient object, or null if there isn't one.
     */
    public Coefficient getDispersionParameterAsCoeff() { return wrappedDispersionParameter; }

    public void setDispersionParameter(double dispersionParameter) {
        if(wrappedDispersionParameter == null)
            this.dispersionParameter = dispersionParameter;
        else
            wrappedDispersionParameter.setValue(dispersionParameter);
    }
    
    public void setDispersionParameterAsCoeff(Coefficient dispersionParameter) {
        wrappedDispersionParameter = dispersionParameter;
    }

    /**
     * Sets the random number that will be used for the next alternative selection.
     */
    public void setRandomNumber(double randomNumber) {
        this.randomNumber = randomNumber;
    }
    
    /**
     * Wraps the dispersion parameter in the given coefficient object.
     * @param wrapper A coefficient object that will hold the dispersion parameter.
     */
    public void wrapDispersionParameter(Coefficient wrapper) {
        wrapper.setValue(getDispersionParameter());
        wrappedDispersionParameter = wrapper;
    }
    
    private double[] lastProb;
    private boolean probCached = false;
    
    public double[] getChoiceProbabilities()
            throws ChoiceModelOverflowException, NoAlternativeAvailable {
        if(probCached)
            return Arrays.copyOf(lastProb, lastProb.length);
        synchronized (alternatives) {
            double[] weights = new double[alternatives.size()];
            double sum = 0;
            Iterator<Alternative> it = alternatives.listIterator();
            int i = 0;
            while (it.hasNext()) {
                Alternative a = it.next();
                double utility = a.getUtility(getDispersionParameter());
                weights[i] = Math.exp(getDispersionParameter() * utility);
                if (Double.isNaN(weights[i])) {
                    throw new ChoiceModelOverflowException(
                            "NAN in weight for alternative " + a);
                }
                if (Double.isInfinite(weights[i])) {
                    throw new ChoiceModelOverflowException(
                            weights[i]+ " weight for alternative " + a + ", Dispersion: " + getDispersionParameter() + ", Utility: " +utility);
                }
                sum += weights[i];
                i++;
            }
            if (sum == 0){
                throw new NoAlternativeAvailable("Denominator in "+this+" model is 0");
            }
            if (Double.isInfinite(sum)) throw new ChoiceModelOverflowException("Denominator in logit model is infinite");
            for (i = 0; i < weights.length; i++) {
                weights[i] /= sum;
                if (consumer != null)
                    consumer.consumeAlternativeProbability(alternatives.get(i),
                            weights[i]);
            }
            if(caching)
                probCached = true;
            lastProb = weights;
            return Arrays.copyOf(lastProb, lastProb.length);
        }
    }

    /**
     * Returns an array of the derivatives of the
     * probabilities of each alternative with
     * respect to the utility of each alternative.
     * (rows are probabilities of alternatives, columns are utilities of alternatives)
     * @return
     * @throws ChoiceModelOverflowException
     */
    public double[][] choiceProbabilityDerivatives() throws ChoiceModelOverflowException {
        double[] weights = new double[alternatives.size()];
        double[][]derivatives = new double[weights.length][weights.length];
        synchronized(alternatives) {
            double sum = 0;
            Iterator<Alternative> it = alternatives.listIterator();
            int i = 0;
            while (it.hasNext()) {
                Alternative a = it.next();
                double utility = a.getUtility(getDispersionParameter());
                weights[i] = Math.exp(getDispersionParameter() * utility);
                if (Double.isNaN(weights[i])) {
                    throw new ChoiceModelOverflowException("NAN in weight for alternative "+a);
                }
                sum += weights[i];
                i++;
            }
            if (sum == 0){
                // no alternative available
                // pretend derivatives are all zero
                return derivatives;
            }
            if (Double.isInfinite(sum)) throw new ChoiceModelOverflowException("Denominator in logit model is infinite");
            for (i = 0; i < weights.length; i++) {
                weights[i] /= sum;
            }
            for (int a=0;a<derivatives.length;a++) {
                derivatives[a][a] += getDispersionParameter()*weights[a];
                for (int p=0;p<derivatives.length;p++) {
                    derivatives[a][p]-=getDispersionParameter()*weights[a]*weights[p];
                }
            }
            return derivatives;
        }
    }

     public Alternative alternativeAt(int i) { return (Alternative) alternatives.get(i);}// should throw an error if out of range


    /** Picks one of the alternatives based on the logit model probabilities */
    public Alternative monteCarloChoice() throws NoAlternativeAvailable, ChoiceModelOverflowException {
        double rand = randomNumber == null ? Math.random() : randomNumber;
        return monteCarloChoice(rand);
    }
    
    /** Picks one of the alternatives based on the logit model probabilities;
          use this if you want to give method random number */
    public Alternative monteCarloChoice(double randomNumber) throws NoAlternativeAvailable, ChoiceModelOverflowException {
        synchronized(alternatives) {
            double[] weights = new double[alternatives.size()];
            double sum = 0;
            Iterator<Alternative> it = alternatives.listIterator();
            int i = 0;
            while (it.hasNext()) {
                double utility = it.next().getUtility(getDispersionParameter());
                weights[i] = Math.exp(getDispersionParameter() * utility);
                if (Double.isNaN(weights[i])) {
                    throw new ChoiceModelOverflowException("in monteCarloChoice alternative was such that LogitModel weight was NaN");
                }
                sum += weights[i];
                i++;
            }
            if (sum==0) throw new NoAlternativeAvailable();
            double selector = randomNumber * sum;
            for (i = 0; i < weights.length; i++) {
                double prob = weights[i] / sum;
                if (consumer != null)
                    consumer.consumeAlternativeProbability(alternatives.get(i),
                            prob);
            }
            sum = 0;
            for (i = 0; i < weights.length; i++) {
                sum += weights[i];
                if (selector <= sum) return alternatives.get(i);
            }
            //yikes!
            throw new Error("Random Number Generator in Logit Model didn't return value between 0 and 1");
        }
    }




    public String toString() {
        StringBuffer altsString = new StringBuffer();
    	int alternativeCounter = 0;
        if (alternatives.size() > 5) { altsString.append("LogitModel with " + alternatives.size() + "alternatives {"); }
        else altsString.append("LogitModel, choice between ");
        Iterator<Alternative> it = alternatives.iterator();
        while (it.hasNext() && alternativeCounter < 5) {
            altsString.append(it.next());
            altsString.append(",");
            alternativeCounter ++;
        }
        if (it.hasNext()) altsString.append("...}"); else altsString.append("}");
        return new String(altsString);
    }

    public double getConstantUtility() {
        if(wrappedConstantUtility == null)
            return constantUtility;
        else
            return wrappedConstantUtility.getValue();
    }
    
    /**
     * Returns the coefficient object representing the constant utility, if this
     * parameter was set using a coefficient object. Otherwise returns null.
     * @return The coefficient object, or null if there isn't one.
     */
    public Coefficient getConstantUtilityAsCoeff() { return wrappedConstantUtility; }

    public void setConstantUtility(double constantUtility) {
        if(wrappedConstantUtility == null)
            this.constantUtility = constantUtility;
        else
            wrappedConstantUtility.setValue(constantUtility);
    }
    
    public void setConstantUtilityAsCoeff(Coefficient constantUtility) {
        wrappedConstantUtility = constantUtility;
    }
    
    /**
     * Wraps the dispersion parameter in the given coefficient object.
     * @param wrapper A coefficient object that will hold the dispersion parameter.
     */
    public void wrapConstantUtility(Coefficient wrapper) {
        wrapper.setValue(getConstantUtility());
        wrappedConstantUtility = wrapper;
    }

    /**
     * Method arrayCoefficientSimplifiedChoice.
     * @param theCoefficients
     * @param theAttributes
     * @return int
     */
    public static int arrayCoefficientSimplifiedChoice(
        double[][] theCoefficients,
        double[] theAttributes) {

		double[] utilities = new double[theCoefficients.length];    
		int alt;
    	for (alt =0; alt < theCoefficients.length; alt++){
    		utilities[alt] = 0;
    		for (int c=0;c<theAttributes.length;c++) {
    			utilities[alt]+=theCoefficients[alt][c]*theAttributes[c];
    		}
    	}
    	int denominator = 0;
    	for (alt=0;alt<utilities.length;alt++) {
    		utilities[alt] = Math.exp(utilities[alt]);
    		denominator+=utilities[alt];
    	}
    	double selector = Math.random()*denominator;
    	double cumulator = 0;
    	for (alt=0;alt<utilities.length;alt++) {
    		cumulator += utilities[alt];
    		if (selector<=cumulator) return alt;
    	}
        // shouldn't happen
        return utilities.length-1;
    }

	public double[] choiceProbabilityDerivativesWRTDispersion() throws ChoiceModelOverflowException {
        double[] weights = new double[alternatives.size()];
        double[] derivatives = new double[weights.length];
        double[] utilities = new double[weights.length];
        synchronized(alternatives) {
            double sum = 0;
            Iterator<Alternative> it = alternatives.listIterator();
            int i = 0;
            while (it.hasNext()) {
                Alternative a = it.next();
                utilities[i] = a.getUtility(getDispersionParameter());
                weights[i] = Math.exp(getDispersionParameter() * utilities[i]);
                if (Double.isNaN(weights[i])) {
                    throw new ChoiceModelOverflowException("NAN in weight for alternative "+a);
                }
                sum += weights[i];
                i++;
            }
            if (sum == 0){
                // no alternative available
                // pretend derivatives are all zero
                return derivatives;
            }
            if (Double.isInfinite(sum)) throw new ChoiceModelOverflowException("Denominator in logit model is infinite");
            for (i = 0; i < weights.length; i++) {
                weights[i] /= sum;
            }
            for (int a=0;a<derivatives.length;a++) {
                derivatives[a] += weights[a];
                double thing1 = 0;
                double thing2 = 0;
                for (int p=0;p<derivatives.length;p++) {
                    if (p!=a && weights[p] > 0 && weights[a] > 0) {
                    	thing1+=utilities[a]*weights[p];
                    	thing2-=utilities[p]*weights[p];
                    }
                }
                derivatives[a] *=(thing1+thing2);
            }
            return derivatives;
        }
	}

	protected void setAlternatives(ArrayList<Alternative> alternatives) {
		this.alternatives = alternatives;
	}

	public ArrayList<Alternative> getAlternatives() {
		return alternatives;
	}

	private Vector lastTarget;
	private boolean targetCached = false;
	
    public Vector getExpectedTargetValues(
            List<ExpectedValue> ts) throws NoAlternativeAvailable,
            ChoiceModelOverflowException {
        if(targetCached)
            return lastTarget.copy();
        // Sum expected values for each alternative weighted by their probabilities.
        Vector values = new DenseVector(ts.size());
        
        double[] probs = this.getChoiceProbabilities();
        int i = 0;
        for(Alternative a : getAlternatives()) {
            if(probs[i] > 0.0) {
                ParameterSearchAlternative da = (ParameterSearchAlternative) a;
                Vector altexpected = da.getExpectedTargetValues(ts);
                altexpected.scale(probs[i]);
                values.add(altexpected);
            }
            i++;
        }
        if(caching)
            targetCached = true;
        lastTarget = values;
        return lastTarget.copy();
    }

    private Vector lastUtilDeriv;
    private boolean utilDerivCached = false;
    
    public Vector getUtilityDerivativesWRTParameters(
            List<Coefficient> cs) throws NoAlternativeAvailable,
            ChoiceModelOverflowException {
        if(utilDerivCached)
            return lastUtilDeriv.copy();
        int dispIndex = cs.indexOf(getDispersionParameterAsCoeff());
        int constIndex = cs.indexOf(getConstantUtilityAsCoeff());
        
        double sumWeights = 0;
        double sumWeightsTimesUtilities = 0;
        Vector sumWeightsTimesDerivatives = new DenseVector(cs.size());
        // Iterate through alternatives.
        Iterator<Alternative> it = getAlternatives().iterator();
        while(it.hasNext()) {
            ParameterSearchAlternative da = (ParameterSearchAlternative) it.next();
            double utility = da.getUtility(getDispersionParameter());
            double weight = Math.exp(getDispersionParameter() * utility);
            Vector derivatives = da.getUtilityDerivativesWRTParameters(cs);
            Vector weightTimesDerivatives = derivatives.scale(weight);
            sumWeights += weight;
            if(weight > 0) {
                sumWeightsTimesUtilities += weight * utility;
                sumWeightsTimesDerivatives.add(weightTimesDerivatives);
            }
        }
        
        // If there are no valid alternatives, the utility is always -infinity. Return 0 for the derivatives.
        if(sumWeights == 0)
            return new DenseVector(cs.size());
        
        // Derivatives wrt normal parameters.
        sumWeightsTimesDerivatives.scale(1 / sumWeights);
        
        // Derivative wrt dispersion parameter.
        if(dispIndex >= 0) {
            double term1 = sumWeightsTimesUtilities / (getDispersionParameter() * sumWeights);
            double term2 = Math.log(sumWeights) / (getDispersionParameter() * getDispersionParameter());
            double dispderivative = term1 - term2;
            sumWeightsTimesDerivatives.set(dispIndex, dispderivative);
        }
        
        // Derivative wrt constant utility, which is always 1.
        if(constIndex >= 0) {
            sumWeightsTimesDerivatives.set(constIndex, 1);
        }
        
        if(caching)
            utilDerivCached = true;
        lastUtilDeriv = sumWeightsTimesDerivatives;
        return lastUtilDeriv.copy();
    }

    public Matrix getExpectedTargetDerivativesWRTParameters(
            List<ExpectedValue> ts, List<Coefficient> cs) throws NoAlternativeAvailable,
            ChoiceModelOverflowException {
        int numTargets = ts.size();
        int numCoeffs = cs.size();
        int numAlts = getAlternatives().size();
        
        Matrix derivatives = new DenseMatrix(numTargets, numCoeffs);
        
        // find which coefficient is the dispersion parameter.
        int dispIndex = cs.indexOf(getDispersionParameterAsCoeff());
        
        // derivative of probability wrt utility (dp by du)
        double[][] probDerivativesWRTUtility = choiceProbabilityDerivatives();
        // derivative of probability wrt top-level dispersion parameter (dp by dlambda)
        double[] probDerivativesWRTDispersion = choiceProbabilityDerivativesWRTDispersion();
        double[] probabilities = getChoiceProbabilities();
        // du by dc
        Vector[] dUtilityBydParameters = new Vector[numAlts];
        // Set up du by dc.
        int i=0;
        for (Alternative a : getAlternatives()) {
            ParameterSearchAlternative da = (ParameterSearchAlternative) a;
            dUtilityBydParameters[i] = da.getUtilityDerivativesWRTParameters(cs);
            i++;
        }
        // Turn du by dc into a matrix.
        Matrix dudct = new DenseMatrix(dUtilityBydParameters);
        Matrix dudc = new DenseMatrix(numAlts, numCoeffs);
        dudc = dudct.transpose(dudc);
        i=0;
        
        for (Alternative a : getAlternatives()) {
            double prob = probabilities[i];
            if(prob > 0.0) {
                ParameterSearchAlternative da = (ParameterSearchAlternative) a;
                // derivative of target value wrt parameters.
                Matrix dmdc = da.getExpectedTargetDerivativesWRTParameters(ts, cs);
                // expected target values.
                Vector altexpected = da.getExpectedTargetValues(ts);
                // contribution to matrix is 
                // (dmdc)p + m(dpdu . dudc)
                // These vectors have to be multiplied as matrices.
                Matrix m = new DenseMatrix(altexpected);
                // And this vector needs to be a row vector.
                Matrix dpdut = new DenseMatrix(new DenseVector(probDerivativesWRTUtility[i]));
                Matrix dpdu = new DenseMatrix(1, numAlts);
                dpdu = dpdut.transpose(dpdu);
                
                //Matrix term1 = dmdc.scale(prob);
                Matrix dpdc = new DenseMatrix(1, numCoeffs);
                //Matrix term2 = new DenseMatrix(numTargets, numCoeffs);
                dpdc = dpdu.mult(dudc, dpdc);
                
                // Correct 0 * infinity cases that occur when an alternative is impossible
                for (int j = 0; j < dpdc.numRows(); j++) {
                    for (int k = 0; k < dpdc.numColumns(); k++) {
                        if (Double.isNaN(dpdc.get(j, k))) {
                            dpdc.set(j, k, 0);
                        }
                    }
                }
                
                // Copy dispersion parameter derivative into this matrix.
                if(dispIndex >= 0)
                    dpdc.set(0, dispIndex, probDerivativesWRTDispersion[i]);
                
                //term2 = m.mult(dpdc, term2);
                //Matrix altderivative = term1.add(term2);
                
                // Add the results to the running total.
                //derivatives.add(altderivative);
                
                // Term 1: add prob * dmdc
                derivatives = derivatives.add(prob, dmdc);
                // Term 2: add m * dpdc
                derivatives = m.multAdd(dpdc, derivatives);
            }

            i++;
        }
        return derivatives;
    }

    @Override
    public void startCaching() {
        caching = true;
        for(Alternative a : alternatives) {
            ParameterSearchAlternative pa = (ParameterSearchAlternative) a;
            pa.startCaching();
        }
    }

    @Override
    public void endCaching() {
        caching = false;
        utilityCached = false;
        targetCached = false;
        probCached = false;
        utilDerivCached = false;
        for(Alternative a : alternatives) {
            ParameterSearchAlternative pa = (ParameterSearchAlternative) a;
            pa.endCaching();
        }
    }
    
    public static abstract class Consumer {
        /**
         * Called when the composite utility of this logit model has been computed
         * @param utility the composite utility
         */
        public void consumeCompositeUtility(double utility) {
            // Does nothing.
        }
        
        /**
         * Called once for each alternative when its conditional probability has been computed
         * @param alternative the alternative
         * @param probability the conditional probability
         */
        public void consumeAlternativeProbability(Alternative alternative, double probability) {
            // Does nothing.
        }
    }
}


