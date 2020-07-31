/*
 * Created on 28-Oct-2005
 *
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
package com.hbaspecto.pecas.sd;

import java.util.List;
import java.util.Optional;

import org.apache.log4j.Logger;

import com.hbaspecto.discreteChoiceModelling.Coefficient;
import com.hbaspecto.pecas.ChoiceModelOverflowException;
import com.hbaspecto.pecas.NoAlternativeAvailable;
import com.hbaspecto.pecas.land.ParcelInterface;
import com.hbaspecto.pecas.land.Tazs;
import com.hbaspecto.pecas.sd.estimation.DensityShapingFunctionParameter;
import com.hbaspecto.pecas.sd.estimation.ExpectedValue;
import com.hbaspecto.pecas.sd.estimation.SpaceTypeCoefficient;
import com.hbaspecto.pecas.sd.estimation.SpaceTypeTazGroupCoefficient;
import com.hbaspecto.pecas.sd.orm.DensityStepPoints;
import com.hbaspecto.pecas.sd.orm.DevelopmentFees;
import com.hbaspecto.pecas.sd.orm.TazGroups;
import com.hbaspecto.pecas.sd.orm.TransitionCostCodes;
import com.hbaspecto.pecas.sd.orm.TransitionCosts;

import no.uib.cipr.matrix.DenseMatrix;
import no.uib.cipr.matrix.DenseVector;
import no.uib.cipr.matrix.Matrix;
import no.uib.cipr.matrix.Vector;
import simpleorm.sessionjdbc.SSessionJdbc;

class DevelopMoreAlternative extends DevelopmentAlternative {
    
	static Logger logger = Logger.getLogger(DevelopMoreAlternative.class);
	private final ZoningRulesI scheme;
	private SpaceTypesI myDt;
	ZoningPermissions zoningReg;
	private double maxFAR;
	
	// Parameters stored as coefficients.
    private Coefficient dispersionCoeff;
    
    private Coefficient[] stepPointCoeffs;
    private Coefficient[] aboveStepPointCoeffs;
    private Coefficient[] stepPointAmountCoeffs;
    
    private Coefficient transitionCoeff;
    private Coefficient localTransitionCoeff;
    private Coefficient costModifier;
	
    private int numRanges;
	private DensityShapingFunction dsf;
	
	private double constant;
	
	private Double randomNumber = null;
	
	private boolean caching = false;

	DevelopMoreAlternative(ZoningRulesI scheme) {
		this.scheme = scheme;
	}

    public void setRandomNumber(double randomNumber) {
        this.randomNumber = randomNumber;
    }

	ZoningPermissions getMyZoningReg() {
		return (ZoningPermissions) this.scheme.getZoningForSpaceType(myDt);
	}

	// Returns true if there is actually a possibility of development.
	private boolean setUpParameters() {
	    myDt = SpaceTypesI.getAlreadyCreatedSpaceTypeBySpaceTypeID(ZoningRulesI.land.getCoverage());
	    
        int spacetype = myDt.get_SpaceTypeId();
        dispersionCoeff = SpaceTypeCoefficient.getIntensityDisp(spacetype);
        
        List<Integer> stepPoints = DensityStepPoints.getStepPointsForSpaceType(spacetype);
        numRanges = stepPoints.size();
        stepPointCoeffs = new Coefficient[numRanges];
        aboveStepPointCoeffs = new Coefficient[numRanges];
        stepPointAmountCoeffs = new Coefficient[numRanges];
        
        for (int i = 0; i < numRanges; i++) {
            int stepPointNumber = stepPoints.get(i);
            stepPointCoeffs[i] = DensityShapingFunctionParameter
                    .getStepPoint(spacetype, stepPointNumber);
            aboveStepPointCoeffs[i] = DensityShapingFunctionParameter
                    .getAboveStepPointAdj(spacetype, stepPointNumber);
            stepPointAmountCoeffs[i] = DensityShapingFunctionParameter
                    .getStepPointAmount(spacetype, stepPointNumber);
        }
        
        transitionCoeff = SpaceTypeCoefficient.getAddTransitionConst(spacetype);
        constant = transitionCoeff.getValue();
        
        TazGroups group = Tazs.getTazRecord(ZoningRulesI.land.getTaz())
                .getTazGroup();
        if (group != null) {
            localTransitionCoeff = SpaceTypeTazGroupCoefficient
                    .getConstructionConstant(ZoningRulesI.land.getCoverage(),
                            group.get_TazGroupId());
            constant += localTransitionCoeff.getValue();
        }
        
        costModifier = SpaceTypeCoefficient.getCostModifier(spacetype);
            
	    // Can't adjust the quantity of vacant types of land.
	    if(myDt.isVacant() || ZoningRulesI.land.isDerelict()) return false;
	    
	    zoningReg = this.scheme.checkZoningForSpaceType(myDt);
	    
	    // If the existing spacetype is not allowed anymore, prevent the development.
	    if(zoningReg == null) return false;
	    
	    double landArea = ZoningRulesI.land.getLandArea();
        double currentFAR = ZoningRulesI.land.getQuantity() / landArea;
        double dispersion = dispersionCoeff.getValue();
        double minFAR = Math.max(myDt.get_MinIntensity(),zoningReg.get_MinIntensityPermitted());
        if (minFAR < currentFAR) minFAR = currentFAR;
        maxFAR = Math.min(myDt.get_MaxIntensity(),zoningReg.get_MaxIntensityPermitted());
        
        // Limit by TAZ-level constraint.
        double allowedSpace = myDt.allowedNewSpace(ZoningRulesI.land.getTaz());
        double allowedFAR = allowedSpace / (landArea / numSplits(ZoningRulesI.land));
        maxFAR = Math.min(maxFAR, allowedFAR + currentFAR);
        
        // Can't build if already too close to the maximum intensity.
        if(currentFAR >= maxFAR * 0.95) return false;
        
        // Can't build if there is no allowed range.
        if(minFAR >= maxFAR) return false;

        SSessionJdbc tempSession = ZoningRulesI.land.getSession();        
        long costScheduleID = ZoningRulesI.land.get_CostScheduleId();
        TransitionCostCodes costCodes = tempSession.mustFind(TransitionCostCodes.meta, costScheduleID);
        TransitionCosts transitionCost = null;
        try {
        	transitionCost = tempSession.mustFind(TransitionCosts.meta, costScheduleID, myDt.get_SpaceTypeId() );
        } catch (simpleorm.utils.SException.Data e) {
        	logger.warn("No transition costs for space type "+myDt+" in cost schedule "+costScheduleID+", disallowing transition");
        	return false;
        }
        DevelopmentFees df = tempSession.mustFind(DevelopmentFees.meta, ZoningRulesI.land.get_FeeScheduleId(), myDt.get_SpaceTypeId() );
        double rent = ZoningRulesI.land.getPrice(myDt.getSpaceTypeID(), ZoningRulesI.currentYear, ZoningRulesI.baseYear);

        double[] intensityPoints = new double[numRanges + 1];
        double[] perSpaceAdjustments = new double[numRanges];
        double[] perLandAdjustments = new double[numRanges];
        
        double utilityPerSpace = getUtilityPerUnitNewSpace(transitionCost, df);
        double utilityPerLand = getUtilityPerUnitLand(costCodes, transitionCost, df);
        double perSpaceExisting = getUtilityPerUnitExistingSpace(rent);
        
        // Adjust utility per land to account for existing development.
        // FIXME problem here, relying on substantial numerical precision if utility of new space is extremely low (e.g. if penaltyAcknlowledged = 1e37).
        utilityPerLand += (perSpaceExisting - utilityPerSpace) * currentFAR;

        double a = ZoningRulesI.amortizationFactor;
        for (int i = 0; i < numRanges; i++) {
            intensityPoints[i] = stepPointCoeffs[i].getValue();
            perSpaceAdjustments[i] = aboveStepPointCoeffs[i].getValue() * a;
            perLandAdjustments[i] = stepPointAmountCoeffs[i].getValue() * a;
        }
        intensityPoints[0] = minFAR;
        intensityPoints[numRanges] = maxFAR;

        dsf = new DensityShapingFunction(dispersion, landArea, utilityPerSpace,
                utilityPerLand, intensityPoints, perSpaceAdjustments,
                perLandAdjustments);

        return true;
	}
	
	private double lastUtility;
	private boolean utilityCached = false;
	
	@Override
	public double getUtility(double dispersionParameterForSizeTermCalculation)
			throws ChoiceModelOverflowException {
		return getUtilityNoSizeEffect();
	}
	public double getUtilityNoSizeEffect() throws ChoiceModelOverflowException {
	    if(utilityCached)
	        return lastUtility;
	    boolean canBuild = setUpParameters();
	    if(!canBuild)
	        return Double.NEGATIVE_INFINITY;
	    
		double result = dsf.getCompositeUtility();

        if (Double.isNaN(result)) {
            String msg = "NAN utility for DevelopMoreAlternative on parcel "
                    + ZoningRulesI.land.getPECASParcelNumber() + " with " + dsf;
            logger.error(msg);
            throw new ChoiceModelOverflowException(msg);
        }
        
		// add in alternative specific constant for addition
		result += constant;
		
		if(caching)
		    utilityCached = true;
        lastUtility = result;
        ZoningRulesI.land.getChoiceUtilityLogger().logAddSpaceUtility(
                ZoningRulesI.land.getPECASParcelNumber(), result);
		return result;

	}
	
	/**
	 * Returns the expected FAR of the development performed by this alternative
	 */
	double getExpectedFAR() {
	    boolean canBuild = setUpParameters();
	    
	    if (!canBuild)
	        return ZoningRulesI.land.getQuantity() / ZoningRulesI.land.getLandArea();
	    
	    return dsf.getExpectedFAR();
	}

    /**
     * Do the development, enforcing a final quantity of space for the parcel
     * rather than sampling from the density shaping function
     */
    void doDevelopment(double newQuantity) {
        doDevelopmentImpl(Optional.of(newQuantity));
    }

	public void doDevelopment() {
	    doDevelopmentImpl(Optional.empty());
	}
	
	private void doDevelopmentImpl(Optional<Double> requiredQuantity) {
        double size = ZoningRulesI.land.getLandArea();
        int taz = ZoningRulesI.land.getTaz();
        
        if (myDt == null) {
            setUpParameters();
        }
        
        int sptype = myDt.getSpaceTypeID();
        double oldDevQuantity;
        double newDevQuantity;

        if (size > ZoningRulesI.land.getMaxParcelSize()) {
            // If development occurs on a parcel that is greater than n acres,
            // split off n acres into a new "pseudo parcel" and add the new
            // pseudo parcel into the database
            ParcelInterface newBit = splitParcel(ZoningRulesI.land);
            oldDevQuantity = newBit.get_SpaceQuantity();
            newDevQuantity = requiredQuantity
                    .orElseGet(() -> sampleIntensity() * newBit.get_LandArea());
            if (zoningReg != null) {
                if (maxFAR * ZoningRulesI.land.getLandArea() > oldDevQuantity) {
                    newBit.set_SpaceQuantity(newDevQuantity);
                }
            }
            newBit.set_SpaceTypeId(sptype);

            int servicingNeeded = zoningReg.get_ServicesRequirement();
            newBit.set_AvailableServicesCode(Math.max(
                    newBit.get_AvailableServicesCode(), servicingNeeded));

            int oldYear = newBit.get_YearBuilt();
            newBit.set_YearBuilt((oldYear + ZoningRulesI.currentYear) / 2);

            // keeps track of the total amount of development added for a
            // spacetype.
            myDt.cumulativeAmountOfDevelopment += newBit.get_SpaceQuantity()
                    - oldDevQuantity;
            ZoningRulesI.land.getDevelopmentLogger().logAdditionWithSplit(
                    ZoningRulesI.land, newBit, oldDevQuantity);
            ZoningRulesI.land.getChoiceUtilityLogger().logAdditionWithSplit(
                    ZoningRulesI.land.getPECASParcelNumber(), newBit.get_PecasParcelNum());
        } else {

            oldDevQuantity = ZoningRulesI.land.getQuantity();
            newDevQuantity = oldDevQuantity;
            if (zoningReg != null) {
                if (maxFAR * ZoningRulesI.land.getLandArea() > oldDevQuantity) {
                    newDevQuantity = requiredQuantity.orElseGet(() -> sampleIntensity()
                            * ZoningRulesI.land.getLandArea());
                }
            }

            ZoningRulesI.land.putQuantity(newDevQuantity);
            int servicing = ZoningRulesI.land.getAvailableServiceCode();
            int servicingNeeded = zoningReg.get_ServicesRequirement();
            if (servicingNeeded > servicing)
                ZoningRulesI.land.putAvailableServiceCode(servicingNeeded);

            int oldYear = ZoningRulesI.land.getYearBuilt();

            int newYear = (int) ((oldYear + ZoningRulesI.currentYear) / 2);
            ZoningRulesI.land.putYearBuilt(newYear);

            // keeps track of the total amount of development added for a
            // spacetype.
            myDt.cumulativeAmountOfDevelopment += ZoningRulesI.land
                    .getQuantity() - oldDevQuantity;
            ZoningRulesI.land.getDevelopmentLogger().logAddition(
                    ZoningRulesI.land, oldDevQuantity, oldYear);
            ZoningRulesI.land.getChoiceUtilityLogger().logAddition(
                    ZoningRulesI.land.getPECASParcelNumber());
        }
        
        // Update space limits.
        double addedQuantity = newDevQuantity - oldDevQuantity;
        myDt.recordSpaceChange(taz, addedQuantity);
	}


	private double getUtilityPerUnitExistingSpace(double rent) {

		if (myDt.isVacant() || ZoningRulesI.land.isDerelict()) return 0;

		int age = ZoningRulesI.currentYear - ZoningRulesI.land.getYearBuilt();
		// these next two lines are for reference when building the keep-the-same alternative, where age is non-zero.
		// No change alternative implies that the space is one year older. Therefore, adjust the the rent and the maintenance cost. 
		rent *= myDt.getRentDiscountFactor(age);        

		double cost = myDt.getAdjustedMaintenanceCost(age);
		
		return rent - cost;        
	}

	private double getUtilityPerUnitNewSpace(TransitionCosts transitionCost, DevelopmentFees df) {            
		double rent = ZoningRulesI.land.getPrice(myDt.getSpaceTypeID(), ZoningRulesI.currentYear, ZoningRulesI.baseYear);        
		return getUtilityPerUnitNewSpace(transitionCost, df, rent);
	}

	private double getUtilityPerUnitNewSpace(TransitionCosts transitionCost, DevelopmentFees df, double rent) {

		//SpaceTypesI myDt = SpaceTypesI.getAlreadyCreatedSpaceTypeBySpaceTypeID(ZoningRulesI.land.getCoverage());

		//TODO adjust constructoin cost for density shaping functions
		double constCostPerUnitSpace = transitionCost.get_AdditionCost();
		constCostPerUnitSpace += costModifier.getValue();
		if (zoningReg.get_AcknowledgedUse()) constCostPerUnitSpace += zoningReg.get_PenaltyAcknowledgedSpace();

		constCostPerUnitSpace += df.get_DevelopmentFeePerUnitSpaceInitial();
		double annualCost = constCostPerUnitSpace * ZoningRulesI.amortizationFactor;

		// add in ongoing costs
		annualCost += df.get_DevelopmentFeePerUnitSpaceOngoing();

		int age = 0;

		annualCost += myDt.getAdjustedMaintenanceCost(age);
		
		//Update the variables of CapacityConstrained feature 
		myDt.cumulativeCostForAdd += annualCost;  
		myDt.numberOfParcelsConsideredForAdd++;
		
		rent *= myDt.getRentDiscountFactor(age);

		return rent - annualCost;
	}

	private double getUtilityPerUnitLand(TransitionCostCodes costCodes, TransitionCosts transitionCost, DevelopmentFees df) {

		double cost = 0;

		// we decided that for "add" the the development fees were already paid (when it was "new") so we don't add them in again.
		/*
    	if (ZoningRulesI.land.isBrownfield()) {
        	cost += costCodes.get_BrownFieldCleanupCost();
        } else {
        	cost += costCodes.get_GreenFieldPreparationCost();
        }
		 */

		// check to see if servicing is required
		int servicingRequired = zoningReg.get_ServicesRequirement();
		if (servicingRequired > ZoningRulesI.land.getAvailableServiceCode()) {
			// ENHANCEMENT don't hard code the two servicing code integer interpretations
			// ENHANCEMENT put future servicing xref into xref table instead of inparcel table.
			if (servicingRequired == 1) {
				cost += costCodes.get_LowCapacityServicesInstallationCost();
			} else {
				// assume servicingRequired == 2
				cost += costCodes.get_HighCapacityServicesInstallationCost();
			}
		}

		// we decided that for "add" the the development fees were already paid (when it was "new") so we don't add them in again.
		/*
    	 DevelopmentFees df = tempSession.mustFind(DevelopmentFees.meta, ZoningRulesI.land.get_FeeScheduleId(), spaceType.get_SpaceTypeId());
    	 cost += df.get_DevelopmentFeePerUnitLandInitial();
		 */
		double annualCost = cost*ZoningRulesI.amortizationFactor;

		// ongoing development fees, again these were assessed when the development was new, so don't assess them again.
		//annualCost += df.get_DevelopmentFeePerUnitLandOngoing();

		return -annualCost;    
	}


	private double sampleIntensity() {
		boolean canBuild = setUpParameters();
		
		// If no construction is possible, the new intensity must equal the old intensity.
		if(!canBuild)
		    return ZoningRulesI.land.getQuantity() / ZoningRulesI.land.getLandArea();

        double rand = randomNumber == null ? Math.random() : randomNumber;
        return dsf.sampleIntensity(rand);
	}


	public ZoningRulesI getScheme() {
		return scheme;
	}

	private Vector lastTarget;
	private boolean targetCached = false;
	
    @Override
    public Vector getExpectedTargetValues(List<ExpectedValue> ts) throws NoAlternativeAvailable,
            ChoiceModelOverflowException {
        if(targetCached)
            return lastTarget.copy();
        boolean canBuild = setUpParameters();
        int spacetype = myDt.get_SpaceTypeId();
        
        double expectedAddedSpace;
        double expectedNewSpace = 0; // Never any new space in a develop more alternative.
        
        // If no construction is possible, the expected added space is 0.
        if(!canBuild)
            expectedAddedSpace = 0;
        else {
            double expectedFAR = dsf.getExpectedFAR();
            expectedAddedSpace = expectedFAR * ZoningRulesI.land.getLandArea()-ZoningRulesI.land.getQuantity();
        }

        Vector result = new DenseVector(ts.size());
        
        int i = 0;
        for(ExpectedValue value : ts) {
            result.set(i, value.getModelledTotalNewValueForParcel(spacetype, expectedAddedSpace, expectedNewSpace));
            i++;
        }
        if(caching)
            targetCached = true;
        lastTarget = result;
        return lastTarget.copy();
    }

    private Vector lastUtilDeriv;
    private boolean utilDerivCached = false;
    
    @Override
    public Vector getUtilityDerivativesWRTParameters(List<Coefficient> cs)
            throws NoAlternativeAvailable, ChoiceModelOverflowException {
        if (utilDerivCached)
            return lastUtilDeriv.copy();
        boolean canBuild = setUpParameters();

        // If no construction is possible, the utility is always negative
        // infinity. Just return all zeroes.
        if (!canBuild)
            return new DenseVector(cs.size());

        Vector results = dsf.getUtilityDerivativesWRTParameters();
        double spaceUtilityDerivative = dsf.getUtilityDerivativeWRTUtilityPerUnitSpace();

        // Pack the results into the output vector.
        Vector vector = prepareVectorWithDSFResults(results, cs);
        int dispersionIndex = cs.indexOf(dispersionCoeff);
        int transitionIndex = cs.indexOf(transitionCoeff);
        int localTransitionIndex = cs.indexOf(localTransitionCoeff);
        int costModifierIndex = cs.indexOf(costModifier);
        if (dispersionIndex >= 0)
         // Dispersion parameter is always last in the results vector
            vector.set(dispersionIndex, results.get(results.size() - 1));
        if (transitionIndex >= 0)
            vector.set(transitionIndex, 1);
        if (localTransitionIndex >= 0)
            vector.set(localTransitionIndex, 1);
        if (costModifierIndex >= 0)
            vector.set(costModifierIndex, -ZoningRulesI.amortizationFactor * spaceUtilityDerivative);

        if (caching)
            utilDerivCached = true;
        lastUtilDeriv = vector;
        return lastUtilDeriv.copy();
    }

    @Override
    public Matrix getExpectedTargetDerivativesWRTParameters(List<ExpectedValue> ts,
            List<Coefficient> cs) throws NoAlternativeAvailable, ChoiceModelOverflowException {
        
        boolean canBuild = setUpParameters();
        int spacetype = myDt.get_SpaceTypeId();
        
        // If no construction is possible, the expected added space is always zero, so the overall
        // derivatives are all zero.
        if(!canBuild)
            return new DenseMatrix(ts.size(), cs.size());
        
        double expectedFAR = dsf.getExpectedFAR();
        double expectedAddedSpace = expectedFAR * ZoningRulesI.land.getLandArea();
        double expectedNewSpace = 0; // Never any new space in a develop more alternative.
        
        // Build vector of derivatives of the targets with respect to the expected added space.
        Matrix dTdE = new DenseMatrix(ts.size(), 1);
        int i = 0;
        for(ExpectedValue value : ts) {
            dTdE.set(i, 0, value.getModelledTotalNewDerivativeWRTAddedSpace(spacetype, expectedAddedSpace, expectedNewSpace));
            i++;
        }
        
        // Scale by land area because of the chain rule.
        dTdE.scale(ZoningRulesI.land.getLandArea());
        
        Vector results = dsf.getExpectedFARDerivativesWRTParameters();
        
        // Build vector of derivatives of the expected added space with respect to the parameters.
        Vector dEdt = prepareVectorWithDSFResults(results, cs);
        
        int dispersionIndex = cs.indexOf(dispersionCoeff);
        if (dispersionIndex >= 0) {
            // Dispersion parameter is always last in the results vector
            dEdt.set(dispersionIndex, results.get(results.size() - 1));
        }
        
        Matrix dEdtMatrix = new DenseMatrix(1, cs.size());
        dEdtMatrix = new DenseMatrix(dEdt).transpose(dEdtMatrix);
        Matrix answer = new DenseMatrix(ts.size(), cs.size());
        answer = dTdE.mult(dEdtMatrix, answer);
        
        return answer;
    }
    
    private Vector prepareVectorWithDSFResults(Vector results, List<Coefficient> cs) {
        double a = ZoningRulesI.amortizationFactor;
        Vector dsfResults = new DenseVector(cs.size());
        
        for (int i = 0; i < numRanges; i++) {
            int stepPointIndex = cs.indexOf(stepPointCoeffs[i]);
            int aboveStepPointIndex = cs.indexOf(aboveStepPointCoeffs[i]);
            int stepPointAmountIndex = cs.indexOf(stepPointAmountCoeffs[i]);
            
            if (stepPointIndex >= 0) {
                dsfResults.set(stepPointIndex, results.get(i));
            }
            // The extra "+1" skips over the max intensity, which isn't a
            // calibrated step point
            if (aboveStepPointIndex >= 0) {
                dsfResults.set(aboveStepPointIndex,
                        results.get(numRanges + 1 + i) * a);
            }
            if (stepPointAmountIndex >= 0) {
                dsfResults.set(stepPointAmountIndex,
                        results.get(numRanges * 2 + 1 + i) * a);
            }
        }
        
        return dsfResults;
    }

    @Override
    public void startCaching() {
        caching = false;
    }

    @Override
    public void endCaching() {
        caching = false;
        utilityCached = false;
        targetCached = false;
        utilDerivCached = false;
    }
}