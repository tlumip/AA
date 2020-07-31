/*
 * Copyright  2007 HBA Specto Incorporated
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

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.log4j.Logger;

import com.hbaspecto.discreteChoiceModelling.Alternative;
import com.hbaspecto.discreteChoiceModelling.Coefficient;
import com.hbaspecto.discreteChoiceModelling.LogitModel;
import com.hbaspecto.pecas.ChoiceModelOverflowException;
import com.hbaspecto.pecas.NoAlternativeAvailable;
import com.hbaspecto.pecas.land.LandInventory;
import com.hbaspecto.pecas.land.Tazs;
import com.hbaspecto.pecas.sd.estimation.EstimationMatrix;
import com.hbaspecto.pecas.sd.estimation.ExpectedValue;
import com.hbaspecto.pecas.sd.estimation.SpaceTypeCoefficient;
import com.hbaspecto.pecas.sd.estimation.TazGroupCoefficient;
import com.hbaspecto.pecas.sd.orm.RandomSeeds;
import com.hbaspecto.pecas.sd.orm.TazGroups;
import com.hbaspecto.pecas.sd.orm.TransitionCostCodes;
import com.hbaspecto.pecas.sd.orm.ZoningRulesI_gen;

import no.uib.cipr.matrix.Matrix;
import no.uib.cipr.matrix.Vector;
import simpleorm.dataset.SQuery;
import simpleorm.sessionjdbc.SSessionJdbc;

/**
 * A class that represents the regulations that control the DevelopmentTypes
 * that are allowed to occur on a parcel. The regulations are stored in a map
 * (lookup) by development type.
 * 
 * Each ZoningScheme also builds itself a DiscreteChoice model that can be used
 * to monte carlo simulate specific construction actions from within the set of
 * allowable possibilities.
 * 
 * @author John Abraham
 */
@SuppressWarnings("serial")
public class ZoningRulesI extends ZoningRulesI_gen implements
        ZoningRulesIInterface, java.io.Serializable {

    protected static transient Logger logger = Logger
            .getLogger("com.pb.osmp.ld");
    public static int currentYear;
    public static int baseYear;
    public static boolean ignoreErrors;
    public static boolean usePredefinedRandomNumbers;
    public static final int maxZoningSchemeIndex = 32767;
    
    private static final Random metaRandom = new Random();

    /**
     * This is the storage for the zoning regulations for the zoning scheme.
     * Each ZoningRegulation describes what is allowed (and hence possible) for
     * a particular DevelopmentType.
     */
    private List<ZoningPermissions> zoning;

    // tree of options which includes the options in zoning_permissions table
    LogitModel myLogitModel = null;

    // protected static Hashtable allZoningSchemes = new Hashtable();

    static double amortizationFactor = 1.0 / 30;
    public static LandInventory land = null;

    // these three variables are used in the alternative classes in the logit
    // model,
    // so they need to be set before the logitmodel is used.

    // this is just a temporary place to store the current parcel's development
    // type,
    // TODO should be removed from this class as it's not directly related to
    // zoning.
    SpaceTypesI existingDT;

    // is mapped to zoning_rules_code
    private int gridCode;
    
    private int errorsReported = 0;

    /**
     * Method getZoningSchemeByIndex.
     * 
     * @param i
     * @return ZoningScheme
     */
    public static ZoningRulesI getZoningRuleByZoningRulesCode(
            SSessionJdbc session, int zoningRulesCode) {

        ZoningRulesI zoningScheme = session.find(ZoningRulesI.meta,
                zoningRulesCode);
        return zoningScheme;

    }

    private Set<SpaceTypeInterface> notAllowedSpaceTypes = new HashSet<SpaceTypeInterface>();
    private LogitModel gkChangeOptions;
    private LogitModel gzGrowthOptions;
    private LogitModel gyBuildOptions;
    private LogitModel gwDecayOptions;
    private LogitModel developNewOptions;
    private DevelopMoreAlternative addSpaceAlternative;
    private Map<Integer, DevelopNewAlternative> newSpaceAlternatives;

    public Iterator<ZoningPermissions> getAllowedSpaceTypes() {
        SSessionJdbc session = land.getSession();

        SQuery<ZoningPermissions> qryPermissions = new SQuery<ZoningPermissions>(
                ZoningPermissions.meta).eq(ZoningPermissions.ZoningRulesCode,
                        this.get_ZoningRulesCode());

        List<ZoningPermissions> zoning = session.query(qryPermissions);
        return zoning.iterator();
    }

    public void simulateDevelopmentOnCurrentParcel(LandInventory l,
            boolean ignoreErrors) {
        simulateDevelopmentOnCurrentParcel(l, ignoreErrors, Collections.<SpaceTypesI>emptySet());
    }

    /**
     * Simulates development with the additional restriction that all
     * development events must contribute space to one of the space types in
     * {@code mustBuild}. All other events are skipped. If {@code mustBuild} is
     * empty, this method is equivalent to
     * {@code simulateDevelopmentOnCurrentParcel(l, ignoreErrors)}
     * 
     * @param l The land inventory
     * @param ignoreErrors Whether to ignore errors
     * @param mustBuild The set of space types that must be built
     */
    public void simulateDevelopmentOnCurrentParcel(LandInventory l,
            boolean ignoreErrors, Set<SpaceTypesI> mustBuild) {

        land = l;
        
        boolean doIt = land.isDevelopable();
        if (!doIt)
            return; // don't do development if it's impossible to develop!

        RepeatableRandomStream stream = getRandomStream();

        // gridFee = 0.0;
        LogitModel developChoice = setUpLogitModelForRun();
        
        // If we are considering land n acres at a time, then when a parcel is
        // greater than n acres we need to
        // call monteCarloElementalChoice repeatedly. There may also need to be
        // some special treatment for parcels that are
        // much less than n acres.
        //        
        double originalLandArea = land.getLandArea();
        for (int sampleTimes = 0; sampleTimes <= originalLandArea
                / land.getMaxParcelSize(); sampleTimes++) {
            setRandomNumbers(stream);
            DevelopmentAlternative a;
            try {
                a = (DevelopmentAlternative) developChoice
                        .monteCarloElementalChoice();
            } catch (NoAlternativeAvailable e) {
                handleNoAlternativeAvailable(e);
                continue;
            } catch (ChoiceModelOverflowException e) {
                handleChoiceModelOverflowException(e);
                continue;
            }
            if (mustBuild.isEmpty()) {
                a.doDevelopment();
            } else {
                if (a instanceof DevelopNewAlternative) {
                    DevelopNewAlternative dna = (DevelopNewAlternative) a;
                    if (mustBuild.contains(dna.theNewSpaceTypeToBeBuilt)) {
                        a.doDevelopment();
                    }
                    else {
                        land.getChoiceUtilityLogger().clearWithoutLog(land.getPECASParcelNumber());
                    }
                } else if (a instanceof DevelopMoreAlternative) {
                    SpaceTypesI existing = SpaceTypesI.getAlreadyCreatedSpaceTypeBySpaceTypeID(land.getCoverage());
                    if (mustBuild.contains(existing)) {
                        a.doDevelopment();
                    }
                    else {
                        land.getChoiceUtilityLogger().clearWithoutLog(land.getPECASParcelNumber());
                    }
                } else {
                    land.getChoiceUtilityLogger().clearWithoutLog(land.getPECASParcelNumber());
                }
            }
            // System.out.println(sampleTimes +", "+ land.getLandArea());
        }
        if (originalLandArea > land.getLandArea()) {
            // if the original parcel area is bigger than the current land area,
            // it means
            // that the parcel was split. Therefore, we need to write out the
            // remaining parcel area.
            land.getDevelopmentLogger().logRemainingOfSplitParcel(
                    land);
            land.getChoiceUtilityLogger().logNoChangeWithSplit(
                    land.getPECASParcelNumber());
        }
    }

    private RepeatableRandomStream getRandomStream() {
        SSessionJdbc session = land.getSession();
        long pecasParcelNum = land.getPECASParcelNumber();

        long seed;
        if (usePredefinedRandomNumbers) {
            seed = RandomSeeds.getRandomSeed(session, pecasParcelNum, currentYear);
        } else {
            synchronized (metaRandom) {
                seed = metaRandom.nextLong();
            }
        }
        Random random = new Random(seed);
        return new RandomRandomStream(random);
    }

    private void setDispersionParameters() {
        // set dispersion parameters before the tree is built.
        getMyLogitModel().setDispersionParameter(
                existingDT.get_NochangeDispersionParameter());
        gkChangeOptions.setDispersionParameter(existingDT
                .get_GkDispersionParameter());
        gwDecayOptions.setDispersionParameter(existingDT
                .get_GwDispersionParameter());
        gzGrowthOptions.setDispersionParameter(existingDT
                .get_GzDispersionParameter());
        gyBuildOptions.setDispersionParameter(existingDT
                .get_GyDispersionParameter());
        developNewOptions.setDispersionParameter(existingDT
                .get_NewTypeDispersionParameter());
    }

    private void setRandomNumbers(RepeatableRandomStream stream) {
        getMyLogitModel().setRandomNumber(stream.next());
        gkChangeOptions.setRandomNumber(stream.next());
        gwDecayOptions.setRandomNumber(stream.next());
        gzGrowthOptions.setRandomNumber(stream.next());
        gyBuildOptions.setRandomNumber(stream.next());
        developNewOptions.setRandomNumber(stream.next());
        addSpaceAlternative.setRandomNumber(stream.next());
        List<Integer> spaceTypes = SpaceTypesI.getAllSpaceTypes().stream()
                .map(SpaceTypesI::get_SpaceTypeId).sorted()
                .collect(Collectors.toList());
        for (int spaceType : spaceTypes) {
            // Advance to the next random number even if this type can't be built,
            // so that changes to zoning policy won't change downstream results.
            double randomNumber = stream.next();
            DevelopNewAlternative a = newSpaceAlternatives.get(spaceType);
            if (a != null) {
                a.setRandomNumber(randomNumber);
            }
        }
    }

    private void insertDispersionParameterObjects() {
        int spacetype = land.getCoverage();
        Coefficient rootParam = SpaceTypeCoefficient.getNoChangeDisp(spacetype);
        getMyLogitModel().setDispersionParameterAsCoeff(rootParam);
        Coefficient changeParam = SpaceTypeCoefficient
                .getChangeOptionsDisp(spacetype);
        gkChangeOptions.setDispersionParameterAsCoeff(changeParam);
        Coefficient demoderParam = SpaceTypeCoefficient
                .getDemolishDerelictDisp(spacetype);
        gwDecayOptions.setDispersionParameterAsCoeff(demoderParam);
        Coefficient renoaddnewParam = SpaceTypeCoefficient
                .getRenovateAddNewDisp(spacetype);
        gzGrowthOptions.setDispersionParameterAsCoeff(renoaddnewParam);
        Coefficient addnewParam = SpaceTypeCoefficient.getAddNewDisp(spacetype);
        gyBuildOptions.setDispersionParameterAsCoeff(addnewParam);
        Coefficient newParam = SpaceTypeCoefficient.getNewTypeDisp(spacetype);
        developNewOptions.setDispersionParameterAsCoeff(newParam);
    }

    public double getAllowedFAR(SpaceTypeInterface dt) {
        ZoningPermissions reg = getZoningForSpaceType(dt);
        if (reg == null)
            return 0;
        return reg.get_MaxIntensityPermitted();
    }

    public int getGridCode() {
        return gridCode;
    }

    private LogitModel getMyLogitModel() {
        if (myLogitModel != null)
            return myLogitModel;

        // Tree structure currently hard coded. TODO change this?
        
        myLogitModel = new LogitModel();
        gkChangeOptions = new LogitModel();
        myLogitModel.addAlternative(gkChangeOptions);
        gwDecayOptions = new LogitModel();
        gkChangeOptions.addAlternative(gwDecayOptions);
        gzGrowthOptions = new LogitModel();
        gkChangeOptions.addAlternative(gzGrowthOptions);
        gyBuildOptions = new LogitModel();
        gzGrowthOptions.addAlternative(gyBuildOptions);
        developNewOptions = new LogitModel();
        gyBuildOptions.addAlternative(developNewOptions);

        final Alternative noChange = new NoChangeAlternative();
        final Alternative demolishAlternative = new DemolishAlternative();
        final Alternative derelictAlternative = new DerelictAlternative();
        final Alternative renovateAlternative = new RenovateAlternative();
        addSpaceAlternative = new DevelopMoreAlternative(this);
        newSpaceAlternatives = new HashMap<>();
        
        if (this.get_NoChangePossibilities()) {
            myLogitModel.addAlternative(noChange);
        }
        if (this.get_DemolitionPossibilities()) {
            gwDecayOptions.addAlternative(demolishAlternative);
        }
        if (this.get_DerelictionPossibilities()) {
            gwDecayOptions.addAlternative(derelictAlternative);
        }
        if (this.get_RenovationPossibilities()) {
            gzGrowthOptions.addAlternative(renovateAlternative);
        }
        if (this.get_AdditionPossibilities()) {
            gyBuildOptions.addAlternative(addSpaceAlternative);
        }
        
        if (this.get_NewDevelopmentPossibilities()) {
            Iterator<ZoningPermissions> it = getAllowedSpaceTypes();
            while (it.hasNext()) {
                ZoningPermissions zp = (ZoningPermissions) it.next();
                SpaceTypesI whatWeCouldBuild = SpaceTypesI
                        .getAlreadyCreatedSpaceTypeBySpaceTypeID(zp
                                .get_SpaceTypeId());
                if (!whatWeCouldBuild.isVacant()) {
                    DevelopNewAlternative aNewSpaceAlternative = new DevelopNewAlternative(
                            this, whatWeCouldBuild);
                    developNewOptions.addAlternative(aNewSpaceAlternative);
                    newSpaceAlternatives.put(whatWeCouldBuild.get_SpaceTypeId(),
                            aNewSpaceAlternative);
                }

            }
        }

        myLogitModel.setConsumer(new LogitModel.Consumer() {
            @Override
            public void consumeAlternativeProbability(Alternative alternative,
                    double probability) {
                if (alternative == noChange)
                    land.getChoiceUtilityLogger().logNoChangeProbability(
                            land.getPECASParcelNumber(), probability);
            }
        });
        
        gkChangeOptions.setConsumer(new LogitModel.Consumer() {
            @Override
            public void consumeCompositeUtility(double utility) {
                land.getChoiceUtilityLogger().logChangeCompositeUtility(
                        land.getPECASParcelNumber(), utility);
            }

            @Override
            public void consumeAlternativeProbability(Alternative alternative,
                    double probability) {
                if (alternative == gzGrowthOptions)
                    land.getChoiceUtilityLogger().logGrowthProbability(
                            land.getPECASParcelNumber(), probability);
            }
        });
        
        gwDecayOptions.setConsumer(new LogitModel.Consumer() {
            @Override
            public void consumeCompositeUtility(double utility) {
                land.getChoiceUtilityLogger().logDecayCompositeUtility(
                        land.getPECASParcelNumber(), utility);
            }

            @Override
            public void consumeAlternativeProbability(Alternative alternative,
                    double probability) {
                if (alternative == demolishAlternative)
                    land.getChoiceUtilityLogger().logDemolishProbability(
                            land.getPECASParcelNumber(), probability);
                else if (alternative == derelictAlternative)
                    land.getChoiceUtilityLogger().logDerelictProbability(
                            land.getPECASParcelNumber(), probability);
            }
        });
        
        gzGrowthOptions.setConsumer(new LogitModel.Consumer() {
            @Override
            public void consumeCompositeUtility(double utility) {
                land.getChoiceUtilityLogger().logGrowthCompositeUtility(
                        land.getPECASParcelNumber(), utility);
            }

            @Override
            public void consumeAlternativeProbability(Alternative alternative,
                    double probability) {
                if (alternative == renovateAlternative)
                    land.getChoiceUtilityLogger().logRenovateProbability(
                            land.getPECASParcelNumber(), probability);
            }
        });
        
        gyBuildOptions.setConsumer(new LogitModel.Consumer() {
            @Override
            public void consumeCompositeUtility(double utility) {
                land.getChoiceUtilityLogger().logBuildCompositeUtility(
                        land.getPECASParcelNumber(), utility);
            }

            @Override
            public void consumeAlternativeProbability(Alternative alternative,
                    double probability) {
                if (alternative == addSpaceAlternative)
                    land.getChoiceUtilityLogger().logAddSpaceProbability(
                            land.getPECASParcelNumber(), probability);
            }
        });
        
        developNewOptions.setConsumer(new LogitModel.Consumer() {
            @Override
            public void consumeCompositeUtility(double utility) {
                land.getChoiceUtilityLogger().logNewSpaceUtility(
                        land.getPECASParcelNumber(), utility);
            }

            @Override
            public void consumeAlternativeProbability(Alternative alternative,
                    double probability) {
                DevelopNewAlternative a = (DevelopNewAlternative) alternative;
                int spaceType = a.theNewSpaceTypeToBeBuilt.get_SpaceTypeId();
                land.getChoiceUtilityLogger().logNewSpaceProbability(
                        land.getPECASParcelNumber(), spaceType, probability);
            }
        });

        return myLogitModel;
    }

    public String getName() {
        return get_ZoningRulesCodeName();
    }

    // FIXME getServicingCost() method
    public double getServicingCost(SpaceTypesI dt) {
        SSessionJdbc session = land.getSession();

        // 1. Get service required in zoning
        int serviceRequired = session.mustFind(ZoningPermissions.meta,
                this.get_ZoningRulesCode(), dt.get_SpaceTypeId())
                .get_ServicesRequirement();

        // 2. Get the available service level on the parcel
        int availableService = land.getAvailableServiceCode();

        // 3. Get the costs associated with installing new services:
        if (availableService < serviceRequired) {
            // new costs should be applied!
            // TODO: do required calculations to get the value of services costs
            TransitionCostCodes costsRecord = session.mustFind(
                    TransitionCostCodes.meta, land.get_CostScheduleId());
            costsRecord.get_LowCapacityServicesInstallationCost();
            costsRecord.get_HighCapacityServicesInstallationCost();
            // costsRecord.get_BrownFieldCleanupCost();
            // costsRecord.get_GreenFieldPreparationCost();
        }
        return 0;
    }

    public List<ZoningPermissions> getZoning() {
        if (zoning != null)
            return zoning;
        SSessionJdbc session = land.getSession();
        SQuery<ZoningPermissions> qryPermissions = new SQuery<ZoningPermissions>(
                ZoningPermissions.meta).eq(ZoningPermissions.ZoningRulesCode,
                        this.get_ZoningRulesCode());

        zoning = session.query(qryPermissions);
        return zoning;
    }

    public ZoningPermissions getZoningForSpaceType(SpaceTypeInterface dt) {
        SSessionJdbc session = land.getSession();

        ZoningPermissions zp = session.mustFind(ZoningPermissions.meta,
                this.get_ZoningRulesCode(), dt.getSpaceTypeID());

        return zp;
    }

    public ZoningPermissions checkZoningForSpaceType(SpaceTypeInterface dt) {

        // cache the items not found, otherwise SimpleORM will continually check
        // the database to look for them
        if (notAllowedSpaceTypes.contains(dt))
            return null;

        SSessionJdbc session = land.getSession();

        ZoningPermissions zp = session.find(ZoningPermissions.meta,
                this.get_ZoningRulesCode(), dt.getSpaceTypeID());

        if (zp == null) {
            notAllowedSpaceTypes.add(dt);
        }

        return zp;

    }

    public boolean isAllowed(SpaceTypeInterface dt) {

        if (getZoningForSpaceType(dt) == null)
            return false;
        return true;
    }

    public void noLongerAllowDevelopmentType(SpaceTypeInterface dt) {
        if (getZoning() != null) {
            getZoning().remove(dt);
        }
        myLogitModel = null;
    }

    public int size() {
        return getZoning().size();
    }

    @Override
    public String toString() {
        return "ZoningScheme " + getName();
    }

    public void addExpectedValuesToMatrix(EstimationMatrix values,
            LandInventory l) {
        land = l;
        
        boolean doIt = land.isDevelopable();
        if (!doIt)
            return; // no contribution to identified targets if it's impossible
        // to develop!
        
        LogitModel developChoice = setUpLogitModelForExpectedValues();
        
        try {
            List<ExpectedValue> expValues = values
                    .getTargetsApplicableToCurrentParcel();
            Vector component = developChoice.getExpectedTargetValues(expValues);
            values.addExpectedValueComponentApplicableToCurrentParcel(component);

        } catch (ChoiceModelOverflowException e) {
            handleChoiceModelOverflowException(e);
        } catch (NoAlternativeAvailable e) {
            handleNoAlternativeAvailable(e);
        }
    }

    public void addDerivativesToMatrix(EstimationMatrix partialDerivatives,
            LandInventory l) {
        land = l;
        
        boolean doIt = land.isDevelopable();
        if (!doIt)
            return; // no contribution to identified targets if it's impossible
        // to develop!

        // gridFee = 0.0;
        LogitModel developChoice = setUpLogitModelForExpectedValues();
        
        try {
            List<ExpectedValue> expValues = partialDerivatives
                    .getTargetsApplicableToCurrentParcel();
            List<Coefficient> coeffs = partialDerivatives.getCoefficients();
            
            Matrix component = developChoice
                    .getExpectedTargetDerivativesWRTParameters(expValues,
                            coeffs);

            if (errorsReported < 10) {
                outer:
                for (int i = 0; i < component.numRows(); i++) {
                    for (int j = 0; j < component.numColumns(); j++) {
                        double entry = component.get(i, j);
                        if (Double.isInfinite(entry) || Double.isNaN(entry)) {
                            logger.error(
                                    "Infinite or NaN value in derivatives for parcel "
                                            + land.getPECASParcelNumber());
                            errorsReported++;
                            break outer;
                        }
                    }
                }
            }
            
            partialDerivatives
            .addDerivativeComponentApplicableToCurrentParcel(component);

        } catch (ChoiceModelOverflowException e) {
            handleChoiceModelOverflowException(e);
        } catch (NoAlternativeAvailable e) {
            handleNoAlternativeAvailable(e);
        }
        // }
    }

    public void addAlternatives(DeferredAlternatives alts, LandInventory l) {
        land = l;
        
        boolean doIt = land.isDevelopable();
        if (!doIt)
            return;
        
        LogitModel developChoice = setUpLogitModelForRun();

        try {
            Map<Alternative, Double> altMap = developChoice
                    .elementalProbabilities();
            for (Map.Entry<Alternative, Double> entry : altMap.entrySet()) {
                if (entry.getValue() > 0) {
                    alts.add(makeDeferredAlternative(entry.getKey(),
                            land.getPECASParcelNumber(), entry.getValue()));
                }
            }
        } catch (ChoiceModelOverflowException e) {
            handleChoiceModelOverflowException(e);
        } catch (NoAlternativeAvailable e) {
            handleNoAlternativeAvailable(e);
        }
    }

    private DeferredAlternative makeDeferredAlternative(Alternative alt,
            long parcelNum, double prob) {
        int taz = land.getTaz();
        if (alt instanceof NoChangeAlternative) {
            return new DeferredNoChangeAlternative(parcelNum, taz, prob);
        } else if (alt instanceof DemolishAlternative) {
            return new DeferredDemolishAlternative(parcelNum, taz, prob);
        } else if (alt instanceof DerelictAlternative) {
            return new DeferredDerelictAlternative(parcelNum, taz, prob);
        } else if (alt instanceof RenovateAlternative) {
            return new DeferredRenovateAlternative(parcelNum, taz, prob);
        } else if (alt instanceof DevelopMoreAlternative) {
            return new DeferredDevelopMoreAlternative(
                    (DevelopMoreAlternative) alt, parcelNum, taz, prob);
        } else if (alt instanceof DevelopNewAlternative) {
            return new DeferredDevelopNewAlternative(
                    (DevelopNewAlternative) alt, parcelNum, taz, prob);
        } else {
            throw new AssertionError("Shouldn't happen");
        }
    }

    private LogitModel setUpLogitModelForRun() {
        existingDT = getCurrentSpaceType();

        setDispersionParameters();

        LogitModel developChoice = getMyLogitModel();

        developNewOptions
                .setConstantUtility(existingDT.get_NewFromTransitionConst());

        gyBuildOptions.setConstantUtility(
                Tazs.getTazRecord(land.getTaz()).getConstructionConstant());

        return developChoice;
    }
    
    private LogitModel setUpLogitModelForExpectedValues() {
        existingDT = getCurrentSpaceType();

        insertDispersionParameterObjects();

        LogitModel developChoice = getMyLogitModel();

        developNewOptions.setConstantUtilityAsCoeff(SpaceTypeCoefficient
                .getNewFromTransitionConst(land.getCoverage()));
        
        TazGroups group = Tazs.getTazRecord(land.getTaz()).getTazGroup();
        if (group != null) {
            gyBuildOptions.setConstantUtilityAsCoeff(TazGroupCoefficient
                    .getConstructionConstant(group.get_TazGroupId()));
        }
        
        return developChoice;
    }
    
    private SpaceTypesI getCurrentSpaceType() {
        SpaceTypesI result = SpaceTypesI.getAlreadyCreatedSpaceTypeBySpaceTypeID(land
                .getCoverage());
        if (result == null) {
            logger.fatal("Invalid coverage code " + land.getCoverage() + " at "
                    + land.parcelToString());
            throw new RuntimeException("Invalid coverage code "
                    + land.getCoverage() + " at " + land.parcelToString());
        }
        return result;
    }

    public void startCaching(LandInventory l) {
        land = l;
        getMyLogitModel().startCaching();
    }

    public void endCaching(LandInventory l) {
        land = l;
        getMyLogitModel().endCaching();
    }
    
    private void handleNoAlternativeAvailable(NoAlternativeAvailable e) {
        String msg = "No reasonable development choices available for "
                + this + " in parcel " + land.getParcelId();
        logger.fatal(msg);
        if (!ignoreErrors)
            throw new RuntimeException(msg, e);
        else {
            land.getParcelErrorLog().logParcelError(land, e);
        }
    }
    
    private void handleChoiceModelOverflowException(ChoiceModelOverflowException e) {
        String msg = "Choice model overflow exception for " + this
                + " in parcel " + land.getParcelId();
        logger.fatal(msg);
        if (!ignoreErrors)
            throw new RuntimeException(msg, e);
        else {
            land.getParcelErrorLog().logParcelError(land, e);
        }
    }
    
    private static abstract class BaseDeferredAlternative implements DeferredAlternative {
        
        private long parcelNum;
        private int taz;
        private double probability;
        
        private BaseDeferredAlternative(long parcelNum, int taz, double probability) {
            this.parcelNum = parcelNum;
            this.taz = taz;
            this.probability = probability;
        }
        
        @Override
        public long parcelNum() {
            return parcelNum;
        }
        
        @Override
        public Tazs taz() {
            return Tazs.getTazRecord(taz);
        }

        @Override
        public double probability() {
            return probability;
        }

        @Override
        public boolean isConstruction() {
            return false;
        }

        @Override
        public boolean isRenovation() {
            return false;
        }
        
        @Override
        public String toString() {
            return this.getClass().getSimpleName();
        }
    }

    private static class DeferredNoChangeAlternative
            extends BaseDeferredAlternative {

        private DeferredNoChangeAlternative(
                long parcelNum, int taz, double probability) {
            super(parcelNum, taz, probability);
        }

        @Override
        public int priority() {
            return 1;
        }
        
        @Override
        public SpaceTypesI activeType() {
            throw new UnsupportedOperationException();
        }

        @Override
        public double amount() {
            return 0;
        }

        @Override
        public boolean tryForceAmount(double amount) {
            // A no-change alternative can only have an amount of 0
            return false;
        }

        @Override
        public void doDevelopment() {
            (new NoChangeAlternative()).doDevelopment();
        }
    }
    
    private static class DeferredDemolishAlternative extends BaseDeferredAlternative {
        
        private int existingType;
        private double existingQuantity;

        private DeferredDemolishAlternative(
                long parcelNum, int taz, double probability) {
            super(parcelNum, taz, probability);
            existingType = land.getCoverage();
            existingQuantity = land.getQuantity();
        }
        
        @Override
        public int priority() {
            return 5;
        }

        @Override
        public SpaceTypesI activeType() {
            return SpaceTypesI.getAlreadyCreatedSpaceTypeBySpaceTypeID(existingType);
        }

        @Override
        public double amount() {
            return existingQuantity;
        }

        @Override
        public boolean tryForceAmount(double amount) {
            // A demolish alternative can only demolish the entire parcel
            return false;
        }

        @Override
        public void doDevelopment() {
            (new DemolishAlternative()).doDevelopment();
        }
    }
    
    private static class DeferredDerelictAlternative extends BaseDeferredAlternative {
        
        private int existingType;
        private double existingQuantity;

        private DeferredDerelictAlternative(
                long parcelNum, int taz, double probability) {
            super(parcelNum, taz, probability);
            existingType = land.getCoverage();
            existingQuantity = land.getQuantity();
        }
        
        @Override
        public int priority() {
            return 4;
        }

        @Override
        public SpaceTypesI activeType() {
            return SpaceTypesI.getAlreadyCreatedSpaceTypeBySpaceTypeID(existingType);
        }
        
        @Override
        public double amount() {
            return existingQuantity;
        }

        @Override
        public boolean tryForceAmount(double amount) {
            // A derelict alternative can only derelict the entire parcel
            return false;
        }

        @Override
        public void doDevelopment() {
            (new DerelictAlternative()).doDevelopment();
        }
    }
    
    private static class DeferredRenovateAlternative extends BaseDeferredAlternative {
        
        private int existingType;
        private double existingQuantity;

        private DeferredRenovateAlternative(
                long parcelNum, int taz, double probability) {
            super(parcelNum, taz, probability);
            existingType = land.getCoverage();
            existingQuantity = land.getQuantity();
        }
        
        @Override
        public int priority() {
            return 2;
        }

        @Override
        public SpaceTypesI activeType() {
            return SpaceTypesI.getAlreadyCreatedSpaceTypeBySpaceTypeID(existingType);
        }

        @Override
        public boolean isRenovation() {
            return true;
        }

        @Override
        public double amount() {
            return existingQuantity;
        }

        @Override
        public boolean tryForceAmount(double amount) {
            // A renovate alternative can only renovate the entire parcel
            return false;
        }

        @Override
        public void doDevelopment() {
            (new RenovateAlternative()).doDevelopment();
        }
    }
    
    private static class DeferredDevelopMoreAlternative extends BaseDeferredAlternative {
        
        private int zoningRuleId;
        private int existingType;
        private double expectedQuantity;

        private DeferredDevelopMoreAlternative(DevelopMoreAlternative inner,
                long parcelNum, int taz, double probability) {
            super(parcelNum, taz, probability);
            zoningRuleId = inner.getScheme().get_ZoningRulesCode();
            existingType = land.getCoverage();
            expectedQuantity = inner.getExpectedFAR() * land.getLandArea() - land.getQuantity();
        }
        
        @Override
        public int priority() {
            return 3;
        }

        @Override
        public SpaceTypesI activeType() {
            return SpaceTypesI.getAlreadyCreatedSpaceTypeBySpaceTypeID(existingType);
        }

        @Override
        public boolean isConstruction() {
            return true;
        }

        @Override
        public double amount() {
            return expectedQuantity;
        }

        @Override
        public void doDevelopment() {
            SSessionJdbc ses = land.getSession();
            ZoningRulesI scheme = ZoningRulesI.getZoningRuleByZoningRulesCode(ses, zoningRuleId);
            (new DevelopMoreAlternative(scheme)).doDevelopment(expectedQuantity + land.getQuantity());
        }

        @Override
        public boolean tryForceAmount(double amount) {
            expectedQuantity = amount;
            return true;
        }
    }
    
    private static class DeferredDevelopNewAlternative extends BaseDeferredAlternative {
        
        private int zoningRuleId;
        private int newType;
        private double expectedQuantity;

        private DeferredDevelopNewAlternative(DevelopNewAlternative inner,
                long parcelNum, int taz, double probability) {
            super(parcelNum, taz, probability);
            zoningRuleId = inner.getScheme().get_ZoningRulesCode();
            newType = inner.theNewSpaceTypeToBeBuilt.get_SpaceTypeId();
            expectedQuantity = inner.getExpectedFAR() * land.getLandArea();
        }
        
        @Override
        public int priority() {
            return 6;
        }

        @Override
        public SpaceTypesI activeType() {
            return SpaceTypesI.getAlreadyCreatedSpaceTypeBySpaceTypeID(newType);
        }

        @Override
        public boolean isConstruction() {
            return true;
        }

        @Override
        public double amount() {
            return expectedQuantity;
        }

        @Override
        public boolean tryForceAmount(double amount) {
            expectedQuantity = amount;
            return true;
        }

        @Override
        public void doDevelopment() {
            SSessionJdbc ses = land.getSession();
            ZoningRulesI scheme = ZoningRulesI.getZoningRuleByZoningRulesCode(ses, zoningRuleId);
            (new DevelopNewAlternative(scheme, activeType())).doDevelopment(expectedQuantity);
        }
    }
}
