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
import com.hbaspecto.pecas.land.LandInventory;
import com.hbaspecto.pecas.land.ParcelInterface;
import com.hbaspecto.pecas.land.Tazs;
import com.hbaspecto.pecas.sd.estimation.DemolitionTarget;
import com.hbaspecto.pecas.sd.estimation.DensityShapingFunctionParameter;
import com.hbaspecto.pecas.sd.estimation.ExpectedValue;
import com.hbaspecto.pecas.sd.estimation.SpaceTypeCoefficient;
import com.hbaspecto.pecas.sd.estimation.SpaceTypeTazGroupCoefficient;
import com.hbaspecto.pecas.sd.estimation.TransitionConstant;
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

public class DevelopNewAlternative extends DevelopmentAlternative {

    static Logger logger = Logger.getLogger(DevelopNewAlternative.class);

    private final ZoningRulesI scheme;
    final SpaceTypesI theNewSpaceTypeToBeBuilt;
    final ZoningPermissions zoningReg;
    double sizeTerm; // size term for change alternatives.

    // Parameters stored as coefficients.
    private Coefficient dispersionCoeff;
    
    private Coefficient[] stepPointCoeffs;
    private Coefficient[] aboveStepPointCoeffs;
    private Coefficient[] stepPointAmountCoeffs;
    
    private Coefficient newToTransitionCoeff;
    private Coefficient localNewToTransitionCoeff;
    private Coefficient transitionCoeff;
    private Coefficient costModifier;

    private double existingQuantity;
    private int existingSpaceType;
    
    private int numRanges;
    private DensityShapingFunction dsf;

    private double constCostPerUnitSpace;
    private double ongoingCost;
    private double rentPerUnitSpace;

    private double lastUtility;
    private boolean utilityCached = false;

    private double transitionConstant;
    private double newToTransitionConstant;
    
    private Double randomNumber = null;

    private boolean caching = false;

    public DevelopNewAlternative(ZoningRulesI scheme, SpaceTypesI dt) {
        this.scheme = scheme;

        this.theNewSpaceTypeToBeBuilt = dt;
        if (scheme != null) {
        	zoningReg = this.scheme.getZoningForSpaceType(dt);
        } else {
        	zoningReg = null;
        }

        int newDT = theNewSpaceTypeToBeBuilt.get_SpaceTypeId();
        dispersionCoeff = SpaceTypeCoefficient.getIntensityDisp(newDT);
        
        List<Integer> stepPoints = DensityStepPoints.getStepPointsForSpaceType(newDT);
        numRanges = stepPoints.size();
        stepPointCoeffs = new Coefficient[numRanges];
        aboveStepPointCoeffs = new Coefficient[numRanges];
        stepPointAmountCoeffs = new Coefficient[numRanges];
        
        for (int i = 0; i < numRanges; i++) {
            int stepPointNumber = stepPoints.get(i);
            stepPointCoeffs[i] = DensityShapingFunctionParameter
                    .getStepPoint(newDT, stepPointNumber);
            aboveStepPointCoeffs[i] = DensityShapingFunctionParameter
                    .getAboveStepPointAdj(newDT, stepPointNumber);
            stepPointAmountCoeffs[i] = DensityShapingFunctionParameter
                    .getStepPointAmount(newDT, stepPointNumber);
        }
        
        newToTransitionCoeff = SpaceTypeCoefficient
                .getNewToTransitionConst(newDT);
        costModifier = SpaceTypeCoefficient.getCostModifier(newDT);
    }

    public void setRandomNumber(double randomNumber) {
        this.randomNumber = randomNumber;
    }

    private boolean setUpParameters() {

        double landArea = ZoningRulesI.land.getLandArea();
        existingQuantity = ZoningRulesI.land.getQuantity();
        existingSpaceType = ZoningRulesI.land.getCoverage();
        double dispersion = dispersionCoeff.getValue();
        double minFAR = Math.max(theNewSpaceTypeToBeBuilt.get_MinIntensity(),
                zoningReg.get_MinIntensityPermitted());
        double maxFAR = Math.min(theNewSpaceTypeToBeBuilt.get_MaxIntensity(),
                zoningReg.get_MaxIntensityPermitted());

        // Limit by TAZ group-level constraint.
        double existingSpace = theNewSpaceTypeToBeBuilt
                .isInTazLimitGroupWith(existingSpaceType) ? existingQuantity
                : 0;
        double existingFAR = existingSpace / landArea;
        double allowedSpace = theNewSpaceTypeToBeBuilt
                .allowedNewSpace(ZoningRulesI.land.getTaz());
        double allowedFAR = allowedSpace
                / (landArea / numSplits(ZoningRulesI.land));
        // Demolishing the existing space might free up capacity.
        allowedFAR += existingFAR;
        maxFAR = Math.min(maxFAR, allowedFAR);

        // Can't build if there is no allowed range.
        if (minFAR >= maxFAR)
            return false;

        SSessionJdbc tempSession = ZoningRulesI.land.getSession();
        long costScheduleID = ZoningRulesI.land.get_CostScheduleId();
        TransitionCostCodes costCodes = tempSession.mustFind(
                TransitionCostCodes.meta, costScheduleID);
        TransitionCosts transitionCost = null;
        try {
            transitionCost = tempSession.mustFind(TransitionCosts.meta,
                    costScheduleID, theNewSpaceTypeToBeBuilt.get_SpaceTypeId());
        } catch (simpleorm.utils.SException.Data e) {
            logger.warn("No transition costs for space type "
                    + theNewSpaceTypeToBeBuilt + " in cost schedule "
                    + costScheduleID + ", disallowing transition");
            return false;
        }
        DevelopmentFees df = tempSession.mustFind(DevelopmentFees.meta,
                ZoningRulesI.land.get_FeeScheduleId(),
                theNewSpaceTypeToBeBuilt.get_SpaceTypeId());

        double[] intensityPoints = new double[numRanges + 1];
        double[] perSpaceAdjustments = new double[numRanges];
        double[] perLandAdjustments = new double[numRanges];
        
        double utilityPerSpace = getUtilityPerUnitSpace(transitionCost, df);
        double utilityPerLand = getUtilityPerUnitLand(costCodes, transitionCost, df);

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

        transitionCoeff = TransitionConstant.getCoeff(
                ZoningRulesI.land.getCoverage(),
                theNewSpaceTypeToBeBuilt.get_SpaceTypeId());
        transitionConstant = transitionCoeff.getValue();

        newToTransitionConstant = newToTransitionCoeff.getValue();
        
        TazGroups group = Tazs.getTazRecord(ZoningRulesI.land.getTaz()).getTazGroup();
        if (group != null) {
            localNewToTransitionCoeff = SpaceTypeTazGroupCoefficient
                    .getConstructionConstant(
                            theNewSpaceTypeToBeBuilt.get_SpaceTypeId(),
                            group.get_TazGroupId());
            newToTransitionConstant += localNewToTransitionCoeff.getValue();
        }

        return true;
    }

    @Override
    public double getUtility(double dispersionParameterForSizeTermCalculation)
            throws ChoiceModelOverflowException {
        return getUtilityNoSizeEffect();
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.hbaspecto.pecas.Alternative#getUtility(double)
     */
    public double getUtilityNoSizeEffect() throws ChoiceModelOverflowException {
        // with continuous intensity options, this is V(tilde)(h)/l in equation
        // 90
        // note NOT v(tilde)(h) but divided by land size PLUS
        // the transition constant
        if (utilityCached)
            return lastUtility;
        boolean canBuild = setUpParameters();

        if (!canBuild)
            return Double.NEGATIVE_INFINITY;

        double result = dsf.getCompositeUtility();

        if (Double.isNaN(result)) {
            // for the debugger
            dsf.getCompositeUtility();

            String msg = "NAN utility for DevelopNewAlternative on parcel "
                    + ZoningRulesI.land.getPECASParcelNumber() + " with " + dsf;
            logger.error(msg);
            throw new ChoiceModelOverflowException(msg);
        }
        result += transitionConstant; // the from-to transition constant in the
                                      // transitionconstantsi table

        result += newToTransitionConstant; // the transition constant for all
                                           // new construction of this type;
        if (caching)
            utilityCached = true;
        lastUtility = result;
        ZoningRulesI.land.getChoiceUtilityLogger().logNewSpaceUtility(
                ZoningRulesI.land.getPECASParcelNumber(),
                theNewSpaceTypeToBeBuilt.get_SpaceTypeId(), result);
        return lastUtility;

    }

    double getUtilityPerUnitSpace(TransitionCosts transitionCost,
            DevelopmentFees df) {

        constCostPerUnitSpace = getConstructionCostPerUnitSpace(transitionCost,
                df);
        constCostPerUnitSpace += costModifier.getValue();
        double annualCost = constCostPerUnitSpace
                * ZoningRulesI.amortizationFactor;

        ongoingCost = getOngingCostsPerUnitSpace(df, annualCost);

        rentPerUnitSpace = getRentPerUnitSpace();

        return rentPerUnitSpace - ongoingCost;

    }

    /**
     * @param df
     * @param annualCost
     * @return
     */
    private double getOngingCostsPerUnitSpace(DevelopmentFees df,
            double annualCost) {
        // add in ongoing costs
        annualCost += df.get_DevelopmentFeePerUnitSpaceOngoing();

        annualCost += theNewSpaceTypeToBeBuilt.getAdjustedMaintenanceCost(0);
        //
        theNewSpaceTypeToBeBuilt.cumulativeCostForDevelopNew += annualCost;
        theNewSpaceTypeToBeBuilt.numberOfParcelsConsideredForDevelopNew++;
        return annualCost;
    }

    /**
     * @param transitionCost
     * @param df
     * @return
     */
    private double getConstructionCostPerUnitSpace(
    		TransitionCosts transitionCost, DevelopmentFees df) {
    	double constCostPerUnitSpace = transitionCost.get_ConstructionCost();
    	if (zoningReg != null) {
    		if (zoningReg.get_AcknowledgedUse())
    		constCostPerUnitSpace += zoningReg.get_PenaltyAcknowledgedSpace();
    	}
    	// FIXME: CosnstructionCosts should be adjusted here.
    	// FIXME: if CapacityConstraint is ON: AdjTrCost = TrCost *
    	// theNewSpaceTypeToBeBuilt.get_CostAdjustmentFactor();

    	constCostPerUnitSpace += df.get_DevelopmentFeePerUnitSpaceInitial();
    	return constCostPerUnitSpace;
    }

    /**
     * @param age
     * @return
     */
    private double getRentPerUnitSpace() {
        int age = 0;
        double rent = ZoningRulesI.land.getPrice(
                theNewSpaceTypeToBeBuilt.getSpaceTypeID(),
                ZoningRulesI.currentYear, ZoningRulesI.baseYear);

        rent *= theNewSpaceTypeToBeBuilt.getRentDiscountFactor(age);
        return rent;
    }

    double getUtilityPerUnitLand(TransitionCostCodes costCodes,
            TransitionCosts transitionCost, DevelopmentFees df) {
        constructionCostPerUnitLand = getConstructionCostPerUnitLand(costCodes,
                df);

        double annualCost = constructionCostPerUnitLand
                * ZoningRulesI.amortizationFactor;

        ongoingFeePerUnitLand = df.get_DevelopmentFeePerUnitLandOngoing();
        annualCost += ongoingFeePerUnitLand;

        return -annualCost;
    }

    /**
     * @param costCodes
     * @param df
     * @return
     */
    private double getConstructionCostPerUnitLand(
            TransitionCostCodes costCodes, DevelopmentFees df) {
        SSessionJdbc tempSession = ZoningRulesI.land.getSession();

        long costScheduleID = ZoningRulesI.land.get_CostScheduleId();

        double cost = 0;
        // first demolish any existing space whether derelict or not
        int oldSpaceType = ZoningRulesI.land.getCoverage();

        if (oldSpaceType != LandInventory.VACANT_ID) {
            try {
                TransitionCosts oldSpaceTypeCosts = tempSession.mustFind(
                        TransitionCosts.meta, costScheduleID, oldSpaceType);
                cost += oldSpaceTypeCosts.get_DemolitionCost()
                        * ZoningRulesI.land.getQuantity()
                        / ZoningRulesI.land.getLandArea();
            } catch (simpleorm.utils.SException.Data e) {
                logger.warn("No demolition costs for "
                        + oldSpaceType
                        + " in cost schedule "
                        + costScheduleID
                        + ", disallowing any demolition by setting to infinity.");
                cost = Double.POSITIVE_INFINITY;
            }
        }

        if (ZoningRulesI.land.isBrownfield()) {
            cost += costCodes.get_BrownFieldCleanupCost();
        } else {
            cost += costCodes.get_GreenFieldPreparationCost();
        }

        // check to see if servicing is required
        int servicingRequired = zoningReg.get_ServicesRequirement();
        if (servicingRequired > ZoningRulesI.land.getAvailableServiceCode()) {
            // ENHANCEMENT don't hard code the two servicing code integer
            // interpretations
            // ENHANCEMENT put future servicing xref into xref table instead of
            // inparcel table.
            if (servicingRequired == 1) {
                cost += costCodes.get_LowCapacityServicesInstallationCost();
            } else {
                // assume servicingRequired == 2
                cost += costCodes.get_HighCapacityServicesInstallationCost();
            }
        }

        // pay the development fees
        cost += df.get_DevelopmentFeePerUnitLandInitial();
        return cost;
    }

    double getExpectedFAR() {
        boolean canBuild = setUpParameters();
        
        if (!canBuild)
            return 0;
        
        return dsf.getExpectedFAR();
    }
    
    /**
     * do the development, enforcing a quantity of development rather than
     * sampling from the density shaping function.
     */
    void doDevelopment(double quantity) {
        doDevelopmentImpl(Optional.of(quantity));
    }
    
    public void doDevelopment() {
        doDevelopmentImpl(Optional.empty());
    }

    private void doDevelopmentImpl(Optional<Double> requiredQuantity) {
        double size = ZoningRulesI.land.getLandArea();
        int taz = ZoningRulesI.land.getTaz();
        
        int oldDT = ZoningRulesI.land.getCoverage();
        int newDT = theNewSpaceTypeToBeBuilt.getSpaceTypeID();
        double oldDevQuantity;
        double newDevQuantity;

        if (size > ZoningRulesI.land.getMaxParcelSize()) {
            // If development occurs on a parcel that is greater than n acres,
            // split off n acres into a new "pseudo parcel" and add the new
            // pseudo parcel into the database
            ParcelInterface newBit = splitParcel(ZoningRulesI.land);

            int servicingNeeded = zoningReg.get_ServicesRequirement();
            newBit.set_AvailableServicesCode(Math.max(
                    newBit.get_AvailableServicesCode(), servicingNeeded));

            oldDevQuantity = newBit.get_SpaceQuantity();
            newDevQuantity = requiredQuantity
                    .orElseGet(() -> sampleIntensity() * newBit.get_LandArea());

            newBit.set_SpaceQuantity(newDevQuantity);
            newBit.set_SpaceTypeId(newDT);
            newBit.set_YearBuilt(ZoningRulesI.currentYear);
            newBit.set_IsDerelict(false);
            newBit.set_IsBrownfield(false);

            // keeps track of the total amount of development for a spacetype
            theNewSpaceTypeToBeBuilt.cumulativeAmountOfDevelopment += newBit
                    .get_SpaceQuantity();

            updateTotalProfit(newBit.get_SpaceQuantity(), newBit.get_LandArea());

            ZoningRulesI.land.getDevelopmentLogger().logDevelopmentWithSplit(
                    ZoningRulesI.land, newBit, oldDevQuantity);
            ZoningRulesI.land.getChoiceUtilityLogger().logDevelopmentWithSplit(
                    ZoningRulesI.land.getPECASParcelNumber(),
                    newBit.get_PecasParcelNum());
        } else {
            newDevQuantity = requiredQuantity
                    .orElseGet(() -> sampleIntensity() * size);
            oldDevQuantity = ZoningRulesI.land.getQuantity();
            boolean oldIsDerelict = ZoningRulesI.land.isDerelict();
            boolean oldIsBrownfield = ZoningRulesI.land.isBrownfield();

            ZoningRulesI.land.putCoverage(newDT);
            ZoningRulesI.land.putQuantity(newDevQuantity);
            ZoningRulesI.land.putDerelict(false);
            ZoningRulesI.land.putBrownfield(false);

            int servicing = ZoningRulesI.land.getAvailableServiceCode();

            /*
             * float servicingNeeded = (float)
             * (newDevQuantity*dt.getServicingRequirement()); if
             * (servicingNeeded >servicing)
             * ZoningRulesI.land.putServiceLevel(servicingNeeded);
             */
            int servicingNeeded = zoningReg.get_ServicesRequirement();
            ZoningRulesI.land.putAvailableServiceCode(Math.max(servicing,
                    servicingNeeded));

            int oldYear = ZoningRulesI.land.getYearBuilt();
            ZoningRulesI.land.putYearBuilt(ZoningRulesI.currentYear);

            // keeps track of the total amount of development for a spacetype
            theNewSpaceTypeToBeBuilt.cumulativeAmountOfDevelopment += ZoningRulesI.land
                    .getQuantity();

            updateTotalProfit(newDevQuantity, size);

            ZoningRulesI.land.getDevelopmentLogger().logDevelopment(
                    ZoningRulesI.land, oldDT, oldDevQuantity, oldYear,
                    oldIsDerelict, oldIsBrownfield);
            ZoningRulesI.land.getChoiceUtilityLogger().logDevelopment(
                    ZoningRulesI.land.getPECASParcelNumber());
        }

        // Update space limits.
        SpaceTypesI oldType = SpaceTypesI.getAlreadyCreatedSpaceTypeBySpaceTypeID(oldDT);
        oldType.recordSpaceChange(taz, -oldDevQuantity);
        theNewSpaceTypeToBeBuilt.recordSpaceChange(taz, newDevQuantity);
    }

    private void updateTotalProfit(double spaceQuantity, double landArea) {
        if (dsf == null) {
            setUpParameters();
        }
        double far = spaceQuantity / landArea;
        theNewSpaceTypeToBeBuilt.cumulativeAnnualProfitOnNewDevelopmentNoDensityShaping += dsf.getUtilityPerUnitSpace()
                * spaceQuantity + dsf.getUtilityPerUnitLand() * landArea;
        theNewSpaceTypeToBeBuilt.cumulativeAnnualProfitOnNewDevelopmentWithDensityShaping += dsf.getUtilityAtFAR(far)
                * landArea;
        theNewSpaceTypeToBeBuilt.cumulativeNewSpaceBuilt += spaceQuantity;
    }

    double sampleIntensity() {
        boolean canBuild = setUpParameters();

        if (!canBuild)
            return 0;

        double rand = randomNumber == null ? Math.random() : randomNumber;
        return dsf.sampleIntensity(rand);
    }

    @Override
    public String toString() {
        return "Development alternative to build " + theNewSpaceTypeToBeBuilt
                + " in zoning " + this.scheme.toString();
    }

    public ZoningRulesI getScheme() {
        return scheme;
    }

    private Vector lastTarget;
    private boolean targetCached = false;

    @Override
    public Vector getExpectedTargetValues(List<ExpectedValue> ts)
            throws NoAlternativeAvailable, ChoiceModelOverflowException {
        if (targetCached)
            return lastTarget.copy();
        boolean canBuild = setUpParameters();
        int spacetype = theNewSpaceTypeToBeBuilt.get_SpaceTypeId();

        double expectedAddedSpace = 0; // Never any added space in a develop new
                                       // alternative.
        double expectedNewSpace;

        // If no construction is possible, the expected new space is 0.
        if (!canBuild)
            expectedNewSpace = 0;
        else {
            double expectedFAR = dsf.getExpectedFAR();
            expectedNewSpace = expectedFAR * ZoningRulesI.land.getLandArea();
        }

        Vector result = new DenseVector(ts.size());

        int i = 0;
        for (ExpectedValue value : ts) {
            result.set(i, value.getModelledTotalNewValueForParcel(spacetype,
                    expectedAddedSpace, expectedNewSpace));
            if (value instanceof DemolitionTarget) {
                DemolitionTarget demoT = (DemolitionTarget) value;
                result.add(i, demoT.getModelledDemolishQuantityForParcel(
                        existingSpaceType, existingQuantity));
            }
            i++;
        }
        if (caching)
            targetCached = true;
        lastTarget = result;
        return lastTarget.copy();
    }

    private Vector lastUtilDeriv;
    private boolean utilDerivCached = false;

    private double constructionCostPerUnitLand;

    private double ongoingFeePerUnitLand;

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
        int newToIndex = cs.indexOf(newToTransitionCoeff);
        int localNewToIndex = cs.indexOf(localNewToTransitionCoeff);
        int costModifierIndex = cs.indexOf(costModifier);
        if (dispersionIndex >= 0)
            // Dispersion parameter is always last in the results vector
            vector.set(dispersionIndex, results.get(results.size() - 1));
        if (transitionIndex >= 0)
            vector.set(transitionIndex, 1);
        if (newToIndex >= 0)
            vector.set(newToIndex, 1);
        if (localNewToIndex >= 0)
            vector.set(localNewToIndex, 1);
        if (costModifierIndex >= 0)
            vector.set(costModifierIndex, -ZoningRulesI.amortizationFactor * spaceUtilityDerivative);

        if (caching)
            utilDerivCached = true;
        lastUtilDeriv = vector;
        return lastUtilDeriv.copy();
    }

    @Override
    public Matrix getExpectedTargetDerivativesWRTParameters(
            List<ExpectedValue> ts, List<Coefficient> cs)
            throws NoAlternativeAvailable, ChoiceModelOverflowException {
        boolean canBuild = setUpParameters();
        int spacetype = theNewSpaceTypeToBeBuilt.get_SpaceTypeId();

        // If no construction is possible, the expected new space is always
        // zero, so the overall
        // derivatives are all zero.
        if (!canBuild)
            return new DenseMatrix(ts.size(), cs.size());

        double expectedFAR = dsf.getExpectedFAR();
        double expectedAddedSpace = 0; // Never any added space in a develop new
                                       // alternative.
        double expectedNewSpace = expectedFAR * ZoningRulesI.land.getLandArea();

        // Build vector of derivatives of the targets with respect to the
        // expected new space.
        Matrix dTdE = new DenseMatrix(ts.size(), 1);
        int i = 0;
        for (ExpectedValue value : ts) {
            dTdE.set(i, 0, value.getModelledTotalNewDerivativeWRTNewSpace(
                    spacetype, expectedAddedSpace, expectedNewSpace));
            i++;
        }

        // Scale by land area because of the chain rule.
        dTdE.scale(ZoningRulesI.land.getLandArea());

        Vector results = dsf.getExpectedFARDerivativesWRTParameters();
        double spaceUtilityDerivative = dsf.getExpectedFARDerivativeWRTUtilityPerUnitSpace();

        // Build vector of derivatives of the expected new space with respect to
        // the parameters.
        Vector dEdt = prepareVectorWithDSFResults(results, cs);

        int dispersionIndex = cs.indexOf(dispersionCoeff);
        int costModifierIndex = cs.indexOf(costModifier);
        if (dispersionIndex >= 0) {
            // Dispersion parameter is always last in the results vector
            dEdt.set(dispersionIndex, results.get(results.size() - 1));
        }
        if (costModifierIndex >= 0) {
            dEdt.set(costModifierIndex, -ZoningRulesI.amortizationFactor * spaceUtilityDerivative);
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
        caching = true;
    }

    @Override
    public void endCaching() {
        caching = false;
        utilityCached = false;
        targetCached = false;
        utilDerivCached = false;
    }
}