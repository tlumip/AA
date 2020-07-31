/*
 * Created on 11-Oct-2006
 *
 * Copyright  2006 HBA Specto Incorporated
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

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.log4j.Logger;

import com.hbaspecto.discreteChoiceModelling.Coefficient;
import com.hbaspecto.pecas.FormatLogger;
import com.hbaspecto.pecas.land.ExchangeResults;
import com.hbaspecto.pecas.land.Parcels;
import com.hbaspecto.pecas.land.PostgreSQLLandInventory;
import com.hbaspecto.pecas.land.SimpleORMLandInventory;
import com.hbaspecto.pecas.land.Tazs;
import com.hbaspecto.pecas.sd.estimation.ByZonePrefilter;
import com.hbaspecto.pecas.sd.estimation.ConcurrentLandInventory;
import com.hbaspecto.pecas.sd.estimation.EstimationDataSet;
import com.hbaspecto.pecas.sd.estimation.EstimationMatrix;
import com.hbaspecto.pecas.sd.estimation.EstimationTarget;
import com.hbaspecto.pecas.sd.estimation.ExpectedTargetModel;
import com.hbaspecto.pecas.sd.estimation.ExpectedValue;
import com.hbaspecto.pecas.sd.estimation.ExpectedValueFilter;
import com.hbaspecto.pecas.sd.estimation.Field;
import com.hbaspecto.pecas.sd.estimation.GaussBayesianObjective;
import com.hbaspecto.pecas.sd.estimation.MarquardtMinimizer;
import com.hbaspecto.pecas.sd.estimation.OptimizationException;
import com.hbaspecto.pecas.sd.estimation.ParameterPrinter;
import com.hbaspecto.pecas.sd.estimation.PriorReader;
import com.hbaspecto.pecas.sd.estimation.TargetPrinter;
import com.hbaspecto.pecas.sd.estimation.TargetReader;
import com.hbaspecto.pecas.sd.orm.ObservedDevelopmentEvents;
import com.hbaspecto.pecas.sd.orm.RandomSeeds;
import com.hbaspecto.pecas.sd.orm.SiteSpecTotals;
import com.hbaspecto.pecas.sd.orm.SpaceTazLimits;
import com.hbaspecto.pecas.sd.orm.SpaceTypesGroup;
import com.hbaspecto.pecas.sd.orm.TazGroups;
import com.hbaspecto.pecas.sd.orm.TazLimitGroups;
import com.pb.common.datafile.CSVFileWriter;
import com.pb.common.datafile.GeneralDecimalFormat;
import com.pb.common.datafile.OLD_CSVFileReader;
import com.pb.common.datafile.TableDataFileReader;
import com.pb.common.datafile.TableDataFileWriter;
import com.pb.common.datafile.TableDataSetCollection;
import com.pb.common.util.ResourceUtil;

import no.uib.cipr.matrix.DenseMatrix;
import no.uib.cipr.matrix.DenseVector;
import no.uib.cipr.matrix.Matrices;
import no.uib.cipr.matrix.Matrix;
import no.uib.cipr.matrix.MatrixSingularException;
import no.uib.cipr.matrix.Vector;
import simpleorm.dataset.SQuery;
import simpleorm.dataset.SQueryResult;
import simpleorm.dataset.SQueryTransient;
import simpleorm.dataset.SRecordTransient;
import simpleorm.sessionjdbc.SSessionJdbc;

public class StandardSDModel extends SDModel {
    // Maximum number of times we'll try to redo development to achieve all
    // required development minimums
    private static final int MAX_TRIES = 3;

    static boolean excelLandDatabase;

    protected static transient Logger logger = Logger
            .getLogger(StandardSDModel.class);
    protected static transient FormatLogger loggerf = new FormatLogger(logger);

    protected String landDatabaseUser;

    protected String landDatabasePassword
    ,databaseSchema;

    private TableDataFileWriter writer;

    public static void main(String[] args) {
        boolean worked = true; // assume this is going to work
        rbSD = ResourceUtil.getResourceBundle("sd");
        initOrm();
        SDModel mySD;
        if (ResourceUtil.getBooleanProperty(rbSD, "UseMatchmaker", false)) {
            loggerf.info("Running with constraints using the Gale-Shapely algorithm");
            mySD = new ControlledSDModel();
        } else {
            mySD = new StandardSDModel();
        }
        try {
            currentYear = Integer.valueOf(args[0]) + Integer.valueOf(args[1]);
            baseYear = Integer.valueOf(args[0]);
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(
                    "Put base year and time interval on command line"
                            + "\n For example, 1990 1");
        }
        try {
            mySD.setUp();


            // for testing, remove this line
            ((SimpleORMLandInventory) mySD.land).readInventoryTable("floorspacei_view");

            mySD.runSD(currentYear, baseYear, rbSD);
        } catch (Throwable e) {
            logger.fatal("Unexpected error "+e);
            e.printStackTrace();
            do {
                logger.fatal(e.getMessage());
                StackTraceElement elements[] = e.getStackTrace();
                for (int i=0;i<elements.length;i++) {
                    logger.fatal(elements[i].toString());
                }
                logger.fatal("Caused by...");
            } while ((e=e.getCause())!=null);
            worked = false; // oops it didn't work 
        } finally {
            if (mySD.land !=null) mySD.land.disconnect();
            if (!worked) System.exit(1); // don't need to manually call exit if everything worked ok.
        }
    }

    public StandardSDModel() {
        super();
    }

    static void initOrm(ResourceBundle rb) {
        rbSD= rb;
        initOrm();
    }

    static void initOrm() {
        Parcels.init(rbSD);
        ZoningPermissions.init(rbSD);
        ExchangeResults.init(rbSD);
        SiteSpecTotals.init(rbSD);
        if (!evs && ResourceUtil.getBooleanProperty(rbSD, "UsePredefinedRandomNumbers")) {
            RandomSeeds.init(rbSD);
        }
    }

    @Override
    public void setUpLandInventory(String className, int year) {

        try {
            Class<?> landInventoryClass = Class.forName(className);
            SimpleORMLandInventory sormland = 
                    (SimpleORMLandInventory) landInventoryClass.newInstance();
            land =sormland;
            sormland.setDatabaseConnectionParameter(rbSD, landDatabaseDriver,
                    landDatabaseSpecifier, landDatabaseUser,
                    landDatabasePassword, databaseSchema);


            sormland.setLogFile(logFilePath + "developmentEvents.csv");
            logger.info("Log file is at " + logFilePath + "developmentEvents.csv");
            boolean choiceUtilityLoggingEnabled;
            if (evs) {
                choiceUtilityLoggingEnabled = false;
            } else {
                choiceUtilityLoggingEnabled = ResourceUtil.getBooleanProperty(rbSD, "ChoiceUtilityLogging", true);
            }
            if (choiceUtilityLoggingEnabled) {
                sormland.setChoiceUtilityLogFile(logFilePath + "choiceUtilities.csv");
                logger.info("Choice utilities are written to " + logFilePath + "choiceUtilities.csv");
            }

            land.init(year);
            land.setMaxParcelSize(ResourceUtil.getDoubleProperty(rbSD, "MaxParcelSize", Double.POSITIVE_INFINITY));


        }catch (InstantiationException ie) {
            logger.fatal("Instantiating : " + className +'\n'+ ie.getMessage());
            throw new RuntimeException("Instantiating " + className, ie);
        } catch (Exception e) {
            logger.fatal("Can't open land database table using "
                    + landDatabaseDriver);
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    public void setUp() {
        SpaceTypesGroup.setCurrentYear(currentYear);
        landDatabaseDriver = ResourceUtil.checkAndGetProperty(rbSD,
                "LandJDBCDriver");
        try {
            Class.forName(landDatabaseDriver).newInstance();
        } catch (Exception e) {
            logger.fatal("Can't start land database driver" + e);
            throw new RuntimeException("Can't start land database driver",
                    e);
        }
        landDatabaseSpecifier = ResourceUtil.checkAndGetProperty(rbSD,
                "LandDatabase");

        landDatabaseUser = ResourceUtil.getProperty(rbSD,
                "LandDatabaseUser", "");
        landDatabasePassword = ResourceUtil.getProperty(rbSD,
                "LandDatabasePassword", "");
        databaseSchema = ResourceUtil.getProperty(rbSD, "schema");

        logFilePath = ResourceUtil.checkAndGetProperty(rbSD, "LogFilePath");
        String className = ResourceUtil.getProperty(rbSD, "LandInventoryClass", PostgreSQLLandInventory.class.getName());
        setUpLandInventory(className, currentYear);
        ZoningRulesI.land=land;
        setUpDevelopmentTypes();
        TableDataFileReader reader = setUpCsvReaderWriter();

        // need to get prices from file if it exists
        if (ResourceUtil.getBooleanProperty(rbSD, "ReadExchangeResults",true)) {
            land.readSpacePrices(reader);
        }
        if (ResourceUtil.getBooleanProperty(rbSD, "SmoothPrices", false)) {
            land.applyPriceSmoothing(reader, writer);
        }
        if (ResourceUtil.getBooleanProperty(rbSD, "LimitSpaceByTAZGroup", false)) {
            SpaceTypesI.enableTazLimitGroups();
            applySpaceLimits(land.getSession());
        }
    }

    private TableDataFileReader setUpCsvReaderWriter() {
        OLD_CSVFileReader outputTableReader = new OLD_CSVFileReader();
        CSVFileWriter outputTableWriter = new CSVFileWriter();
        outputTableWriter.setMyDecimalFormat(new GeneralDecimalFormat(
                "0.#########E0", 10000000, .001));
        if (ResourceUtil
                .getBooleanProperty(rbSD, "UseYearSubdirectories", true)) {
            outputTableReader.setMyDirectory(ResourceUtil.getProperty(
                    rbSD, "AAResultsDirectory")
                    + currentYear + File.separatorChar);
            outputTableWriter.setMyDirectory(new File(ResourceUtil.getProperty(
                    rbSD, "AAResultsDirectory")
                    + (currentYear + 1) + File.separatorChar));
        } else {
            outputTableReader.setMyDirectory(ResourceUtil.getProperty(
                    rbSD, "AAResultsDirectory"));
            outputTableWriter.setMyDirectory(new File(ResourceUtil.getProperty(
                    rbSD, "AAResultsDirectory")));
        }

        outputDatabase = new TableDataSetCollection(outputTableReader,
                outputTableWriter);
        writer = outputTableWriter;
        return outputTableReader;
    }

    private void applySpaceLimits(SSessionJdbc ses) {
        List<SpaceTazLimits> limits = SpaceTazLimits
                .getLimitsForCurrentYear(ses);
        SQuery<Parcels> qry = new SQuery<Parcels>(Parcels.meta).as("p");
        SQueryTransient qryt = new SQueryTransient(qry)
        .sum("p", Parcels.SpaceQuantity).groupBy("p", Parcels.Taz)
        .groupBy("p", Parcels.SpaceTypeId);
        SQueryResult<SRecordTransient> totals = ses.queryTransient(qryt);
        Map<Integer, Map<Integer, Double>> existingSpaceMap = new HashMap<Integer, Map<Integer, Double>>();
        for (SRecordTransient total : totals) {
            int taz = total.getInt("GROUP_" + Parcels.Taz.getColumnName());
            int sptype = total.getInt("GROUP_"
                    + Parcels.SpaceTypeId.getColumnName());
            TazLimitGroups spaceGroup = SpaceTypesI
                    .getAlreadyCreatedSpaceTypeBySpaceTypeID(sptype)
                    .getTazLimitGroup();
            TazGroups tazGroup = Tazs.getTazRecord(taz).getTazGroup();
            if(spaceGroup != null && tazGroup != null) {
                int spaceGroupId = spaceGroup.get_TazLimitGroupId();
                int tazGroupId = tazGroup.get_TazGroupId();
                double quantity = total.getDouble("SUM_"
                        + Parcels.SpaceQuantity.getColumnName());
                if (!existingSpaceMap.containsKey(tazGroupId))
                    existingSpaceMap.put(tazGroupId, new HashMap<Integer, Double>());
                Map<Integer, Double> tazMap = existingSpaceMap.get(tazGroupId);
                if (!tazMap.containsKey(spaceGroupId))
                    tazMap.put(spaceGroupId, 0.0);
                tazMap.put(spaceGroupId, tazMap.get(spaceGroupId) + quantity);
            }
        }
        for (SpaceTazLimits limit : limits) {
            int tazGroup = limit.get_TazGroupId();
            int spaceGroup = limit.get_TazLimitGroupId();
            double minSpace = limit.get_MinQuantity();
            double maxSpace = limit.get_MaxQuantity();
            double existingSpace = 0;
            if (existingSpaceMap.containsKey(tazGroup)
                    && existingSpaceMap.get(tazGroup).containsKey(spaceGroup))
                existingSpace = existingSpaceMap.get(tazGroup).get(spaceGroup);
            TazLimitGroups group = TazLimitGroups.getTazLimitGroupsByID(spaceGroup);
            group.setExistingFloorspace(tazGroup, existingSpace);
            if (!limit.isNull(SpaceTazLimits.MinQuantity)) {
                group.setFloorspaceMinimum(tazGroup, minSpace);
            }
            if (!limit.isNull(SpaceTazLimits.MaxQuantity)) {
                group.setFloorspaceMaximum(tazGroup, maxSpace);
            }
        }
    }
    
    public void simulateDevelopment() {
        boolean prepareEstimationData = ResourceUtil.getBooleanProperty(rbSD, "PrepareEstimationDataset", false);
        if (prepareEstimationData) {
            doEstimation();
        } else {
            doSimulation();
        }
    }
    
    private void doEstimation() {
        String estimationFileNamePath = ResourceUtil.checkAndGetProperty(rbSD, "EstimationDatasetFileNameAndPath");
        double sampleRatio = ResourceUtil.getDoubleProperty(rbSD, "SampleRatio");
        try (EstimationDataSet eDataSet = new EstimationDataSet(estimationFileNamePath, sampleRatio)) {
            // grab all development permit records into the SimpleORM Cache
            land.getSession().query(new SQuery<ObservedDevelopmentEvents>(ObservedDevelopmentEvents.meta));
            
            LandPassRunner pass = new LandPassRunner(land, zr -> {
                //Keeping the csv file opened for the whole period of the run might not be a good idea.  
                eDataSet.compileEstimationRow(land);
                eDataSet.writeEstimationRow();
            });
            
            pass.calculateInThisThread();
        } finally {
            try {
                land.getDevelopmentLogger().close();
            } catch (Exception e) {
                logger.fatal(e);
            }
        }
    }
    
    private void doSimulation() {
        try {
            boolean ignoreErrors = ResourceUtil.getBooleanProperty(rbSD, "IgnoreErrors", false);
            
            LandPassRunner pass = new LandPassRunner(land, zr -> {
                zr.simulateDevelopmentOnCurrentParcel(land, ignoreErrors);
            });
            
            pass.calculateInThisThread();
            
            if (ResourceUtil.getBooleanProperty(rbSD, "LimitSpaceByTAZGroup", false)) {
                // Find all TAZ group/space type combinations that didn't meet their minimum
                Map<Integer, List<SpaceTypesI>> unfulfilled = new HashMap<>();
                boolean isUnfulfilled = false;
                for (Integer tazGroup : TazGroups.getAllTazGroupIds(land.getSession())) {
                    unfulfilled.put(tazGroup, new LinkedList<SpaceTypesI>());
                    for (SpaceTypesI st : SpaceTypesI.getAllSpaceTypes()) {
                        if (st.requiredNewSpaceInGroup(tazGroup) > 0) {
                            isUnfulfilled = true;
                            unfulfilled.get(tazGroup).add(st);
                        }
                    }
                }
                int tryNumber = 1;
                while (isUnfulfilled && tryNumber <= MAX_TRIES) {
                    land.getDevelopmentLogger().flush();
                    land.getChoiceUtilityLogger().flush();
                    reportUnfulfilled(unfulfilled);
                    
                    pass = new LandPassRunner(land, zr -> {
                        int taz = land.getTaz();
                        int tazGroup = Tazs.getTazRecord(taz).getTazGroup()
                                .get_TazGroupId();
                        if (!unfulfilled.get(tazGroup).isEmpty()) {
                            List<SpaceTypesI> sptypes = unfulfilled
                                    .get(tazGroup);
                            Set<SpaceTypesI> mustBuild = new HashSet<>(
                                    sptypes);
                            
                            zr.simulateDevelopmentOnCurrentParcel(land, ignoreErrors, mustBuild);
                            
                            Iterator<SpaceTypesI> it = sptypes.iterator();
                            while (it.hasNext()) {
                                if (it.next().requiredNewSpaceInGroup(
                                        tazGroup) <= 0) {
                                    it.remove();
                                }
                            }
                            // Check for regression due to existing space
                            // being built over.
                            SpaceTypesI existingSpace = SpaceTypesI
                                    .getAlreadyCreatedSpaceTypeBySpaceTypeID(land
                                            .getCoverage());
                            if (!mustBuild.contains(existingSpace)
                                    && existingSpace
                                            .requiredNewSpace(tazGroup) > 0) {
                                sptypes.add(existingSpace);
                            }
                        }
                    });
                    
                    pass.calculateInThisThread();
                    
                    tryNumber++;
                }
            }
    
            land.getDevelopmentLogger().flush();
            land.getChoiceUtilityLogger().flush();
            land.addNewBits();
        } finally {
            try {
                land.getDevelopmentLogger().close();
                land.getChoiceUtilityLogger().close();
            } catch (Exception e) {
                logger.fatal(e);
            }
        }
    }
    
    private void reportUnfulfilled(Map<Integer, List<SpaceTypesI>> unfulfilled) {
        Map<SpaceTypesI, Integer> unfulfilledCounts = new HashMap<>();
        for (SpaceTypesI st : SpaceTypesI.getAllSpaceTypes()) {
            unfulfilledCounts.put(st, 0);
        }
        for (List<SpaceTypesI> sptypes : unfulfilled.values()) {
            for (SpaceTypesI st : sptypes) {
                unfulfilledCounts.put(st,
                        unfulfilledCounts.get(st) + 1);
            }
        }
        for (SpaceTypesI st : SpaceTypesI.getAllSpaceTypes()) {
            logger.info("Space type " + st.getName()
                    + " has unfulfilled space minimums in "
                    + unfulfilledCounts.get(st) + " zones");
        }
    }
    
    public void calculateExpectedValues(ResourceBundle rb,
            List<EstimationTarget> evsToCalculate, TargetPrinter printer, int currentY, int baseY) {
       baseYear = baseY;
       currentYear = currentY;
       rbSD = rb;
       setUp();
       initZoningScheme(currentY, baseY);
       
       List<ExpectedValue> evObjects = EstimationTarget.convertToExpectedValueObjects(evsToCalculate);
       
       int queueSize = ResourceUtil.getIntegerProperty(rbSD, "QueueSize", 5);
       ConcurrentLandInventory cli = new ConcurrentLandInventory(land, queueSize, rbSD);

       ExpectedValueFilter filter = new ByZonePrefilter(evObjects, cli);
       EstimationMatrix matrix = new EstimationMatrix(filter, Collections.emptyList());
       
       LandPassRunner pass = new LandPassRunner(cli, zr -> {
           zr.startCaching(cli);
           zr.addExpectedValuesToMatrix(matrix,
                   cli);
           zr.endCaching(cli);
       });
       
       int concurrency = ResourceUtil.getIntegerProperty(rbSD, "ExpectedValueCalculationThreads");
       pass.calculateConcurrently(concurrency);
       
       Vector expValues = matrix.getExpectedValues();
       for (int i = 0; i < expValues.size(); i++)
           evObjects.get(i).setModelledValue(expValues.get(i));

        File outf = new File(String.valueOf(currentYear), "evs.csv");

        writeEVs(outf, printer, evsToCalculate, "ExpectedValue",
                "Could not write expected values");
    }
    
    protected void writeEVs(File outf, TargetPrinter printer, List<EstimationTarget> evs, String evColName, String failureMsg) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(outf))) {
            List<Field> header = printer.getCommonFields(evs);
            writer.write(header.stream().map(Field::getName)
                    .collect(Collectors.joining(",")));
            writer.write(",ExpectedValue");
            writer.newLine();
            for (EstimationTarget ev : evs) {
                List<String> row = printer.adaptToFields(ev, header);
                writer.write(String.join(",", row));
                writer.write("," + ev.getModelledValue());
                writer.newLine();
            }
        } catch (IOException e) {
            loggerf.throwFatal(e, "Could not write expected values");
        }
    }

    public void calibrateModel(ResourceBundle rb, PriorReader priorReader,
            ParameterPrinter paramPrinter, TargetReader targetReader,
            TargetPrinter targetPrinter, int baseY, int currentY,
            double epsilon, int maxits) {
        baseYear = baseY;
        currentYear = currentY;
        rbSD = rb;
        try {
            setUp();
            initZoningScheme(currentY, baseY);

            List<EstimationTarget> targets = targetReader.targets();

            Matrix targetVariance = new DenseMatrix(targetReader.variance(targets));

            List<Coefficient> coeffs = priorReader.parameters();

            Vector means = new DenseVector(priorReader.means(coeffs));

            Matrix variance = new DenseMatrix(priorReader.variance(coeffs));

            Vector epsilons = new DenseVector(means);
            for(int i = 0; i < means.size(); i++)
                epsilons.set(i, Math.abs(Math.sqrt(variance.get(i, i))) * epsilon);

            ZoningRulesI.ignoreErrors = ResourceUtil.getBooleanProperty(rbSD, "IgnoreErrors", false);
            double initialStepSize = ResourceUtil.getDoubleProperty(rbSD, "InitialLambda", 600);
            double stepSizeInc = ResourceUtil.getDoubleProperty(rbSD, "LambdaIncrement", 2);
            double stepSizeDec = ResourceUtil.getDoubleProperty(rbSD, "LambdaDecrement", 0.9);
            int numThreads = ResourceUtil.getIntegerProperty(rbSD, "EstimationCalculationThreads", 0);
            String derivsFolder = ResourceUtil.getProperty(rbSD, "EstimationDerivativesFolder");
            String gradientFolder = ResourceUtil.getProperty(rbSD, "EstimationGradientFolder");
            String hessianFolder = ResourceUtil.getProperty(rbSD, "EstimationHessianFolder");
            String paramsFolder = ResourceUtil.getProperty(rbSD, "EstimationParametersFolder");
            String targobjFolder = ResourceUtil.getProperty(rbSD, "EstimationTargetsObjectiveFolder");
            String stdErrorFile = ResourceUtil.getProperty(rbSD, "EstimationStdErrorFile", "stderror") + ".csv";
            double maxChangeFactor = ResourceUtil.getDoubleProperty(rbSD, "MaxParameterChange", Double.POSITIVE_INFINITY);
            Vector maxChange = new DenseVector(variance.numRows());
            for(int i = 0; i < maxChange.size(); i++) {
                maxChange.set(i, Math.sqrt(variance.get(i, i)) * maxChangeFactor);
            }

            ExpectedTargetModel theModel;
            if (numThreads == 0) {
                logger.info("Running in single-threaded mode");
                theModel = new ExpectedTargetModel(coeffs, land);
            } else {
                logger.info("Running in multi-threaded mode with " + numThreads
                        + " threads");
                int queueSize = ResourceUtil.getIntegerProperty(rbSD, "QueueSize", 5);
                theModel = new ExpectedTargetModel(coeffs,
                        new ConcurrentLandInventory(land, queueSize, rbSD), numThreads);
            }
            try {
                if (ResourceUtil.getBooleanProperty(rbSD,
                        "EstimationExpectedValuesOnly", false)) {
                    logger.info("Calculating expected values only");
                    theModel.calculateAllValues(targets);
                    BufferedWriter tWriter = null;

                    try {
                        tWriter = new BufferedWriter(new FileWriter(
                                new File("expected_values.csv")));
                        theModel.printCurrentValues(tWriter, targets, targetPrinter);
                    } catch (IOException e) {
                        logger.error(e.getMessage());
                    } finally {
                        if (tWriter != null) {
                            try {
                                tWriter.close();
                            } catch (IOException e) {
                            }
                        }
                    }
                } else {

                    final GaussBayesianObjective theObjective = new GaussBayesianObjective(
                            theModel, coeffs, targets, targetVariance, means,
                            variance);

                    land.getSession(); // opens up the session and begins a
                                       // transaction
                    MarquardtMinimizer theMinimizer = new MarquardtMinimizer(
                            theObjective,
                            new DenseVector(priorReader.startValues(coeffs)));
                    theMinimizer.setInitialMarquardtFactor(initialStepSize);
                    theMinimizer.setMarquardtFactorAdjustments(stepSizeInc,
                            stepSizeDec);
                    theMinimizer.setMaxParameterChange(maxChange);

                    double initialObj = theMinimizer.getCurrentObjectiveValue();

                    // Clear out the output folders to prepare for new output.
                    prepare(derivsFolder);
                    prepare(gradientFolder);
                    prepare(hessianFolder);
                    prepare(paramsFolder);
                    prepare(targobjFolder);

                    BeforePrinter before = new BeforePrinter(derivsFolder,
                            gradientFolder, hessianFolder, theObjective,
                            theModel, paramPrinter, targetPrinter);

                    AfterPrinter after = new AfterPrinter(paramsFolder,
                            targobjFolder, theObjective, paramPrinter,
                            targetPrinter);

                    Vector optimalParameters = theMinimizer
                            .iterateToConvergence(epsilons, maxits, before,
                                    after);
                    Vector optimalTargets = theModel.getTargetValues(targets,
                            optimalParameters);
                    String paramsAsString = Arrays
                            .toString(Matrices.getArray(optimalParameters));
                    BufferedWriter sWriter = null;
                    if (theMinimizer.lastRunConverged()) {
                        logger.info(
                                "SD parameter estimation converged on a solution: "
                                        + paramsAsString);
                        logger.info(
                                "Initial objective function = " + initialObj);
                        logger.info("Optimal objective function = "
                                + theMinimizer.getCurrentObjectiveValue());
                        logger.info("Convergence after "
                                + theMinimizer.getNumberOfIterations()
                                + " iterations");
                    } else {
                        int numits = theMinimizer.getNumberOfIterations();
                        logger.info("SD parameter estimation stopped after "
                                + numits + " iteration"
                                + (numits == 1 ? "" : "s")
                                + " without finding a solution");
                        logger.info(
                                "Current parameter values: " + paramsAsString);
                        if (theMinimizer.lastRunMaxIterations())
                            logger.info(
                                    "Reason: stopped at maximum allowed iterations");
                        else
                            logger.info(
                                    "Reason: could not find a valid next iteration");
                        logger.info(
                                "Initial objective function = " + initialObj);
                        logger.info("Optimal objective function = "
                                + theMinimizer.getCurrentObjectiveValue());
                    }
                    logger.info("Target values at optimum: " + Arrays
                            .toString(Matrices.getArray(optimalTargets)));
                    try {
                        sWriter = new BufferedWriter(
                                new FileWriter(new File(stdErrorFile)));
                        theObjective.printStdError(sWriter, paramPrinter);
                    } catch (IOException e) {
                        logger.error(e.getMessage());
                    } catch (MatrixSingularException e) {
                        logger.error(e.getMessage());
                    } finally {
                        if (sWriter != null) {
                            try {
                                sWriter.close();
                            } catch (IOException e) {
                            }
                        }
                    }
                }
            } catch (OptimizationException e) {
                logger.error("Bad initial guess: "
                        + Arrays.toString(Matrices.getArray(means)));
            }
        } finally {
            if (land != null)
                land.disconnect();
        }
    }
    
    private void prepare(String fname) {
        if(fname == null)
            return;
        
        File dir = new File(fname);

        if(dir.exists()) {
            boolean worked = false;
            int i = 1;
            while(!worked) {
                File backup = new File(fname + "_backup" + i);
                if (!backup.exists())
                    worked = dir.renameTo(backup);
                i++;
                if (i > 1000) {
                    throw new RuntimeException("Cannot back up previous output folder. Please rename it manually.");
                }
            }
        }
        
        dir.mkdirs();
    }

    private static class BeforePrinter
            implements MarquardtMinimizer.BeforeIterationCallback {
        private String derivsFolder;
        private String gradientFolder;
        private String hessianFolder;
        private GaussBayesianObjective theObjective;
        private ExpectedTargetModel theModel;
        private ParameterPrinter paramPrinter;
        private TargetPrinter targetPrinter;

        private BeforePrinter(String derivsFolder, String gradientFolder,
                String hessianFolder, GaussBayesianObjective theObjective,
                ExpectedTargetModel theModel, ParameterPrinter paramPrinter, TargetPrinter targetPrinter) {
            this.derivsFolder = derivsFolder;
            this.gradientFolder = gradientFolder;
            this.hessianFolder = hessianFolder;
            this.theObjective = theObjective;
            this.theModel = theModel;
            this.paramPrinter = paramPrinter;
            this.targetPrinter = targetPrinter;
        }

        @Override
        public void startIteration(int iteration) {
            writeOutputs("" + iteration);
        }

        @Override
        public void startFailedIteration(int iteration, int attempt) {
            writeOutputs(iteration + "-" + attempt);
        }

        private void writeOutputs(String iteration) {
            // Write out derivative matrix
            BufferedWriter dWriter = null;
            BufferedWriter gWriter = null;
            BufferedWriter hWriter = null;
            try {
                if (derivsFolder != null) {
                    dWriter = new BufferedWriter(new FileWriter(new File(
                            derivsFolder, "derivs" + iteration + ".csv")));
                    theModel.printCurrentDerivatives(dWriter, paramPrinter, targetPrinter);
                }
                if (gradientFolder != null) {
                    gWriter = new BufferedWriter(new FileWriter(new File(
                            gradientFolder, "gradient" + iteration + ".csv")));
                    theObjective.printGradient(gWriter, paramPrinter);
                }
                if (hessianFolder != null) {
                    hWriter = new BufferedWriter(new FileWriter(new File(
                            hessianFolder, "hessian" + iteration + ".csv")));
                    theObjective.printHessian(hWriter, paramPrinter);
                }
            } catch (IOException e) {
            } finally {
                if (dWriter != null)
                    try {
                        dWriter.close();
                    } catch (IOException e) {
                    }
                if (gWriter != null)
                    try {
                        gWriter.close();
                    } catch (IOException e) {
                    }
                if (hWriter != null)
                    try {
                        hWriter.close();
                    } catch (IOException e) {
                    }
            }
        }
    }

    private class AfterPrinter implements MarquardtMinimizer.AfterIterationCallback {
        private String paramsFolder;
        private String targobjFolder;
        private GaussBayesianObjective theObjective;
        private ParameterPrinter paramPrinter;
        private TargetPrinter targetPrinter;

        private AfterPrinter(String paramsFolder, String targobjFolder,
                GaussBayesianObjective theObjective,
                ParameterPrinter paramPrinter, TargetPrinter targetPrinter) {
            this.paramsFolder = paramsFolder;
            this.targobjFolder = targobjFolder;
            this.theObjective = theObjective;
            this.paramPrinter = paramPrinter;
            this.targetPrinter = targetPrinter;
        }

        @Override
        public void finishedIteration(int iteration) {
            writeOutputs("" + iteration);
        }

        @Override
        public void finishedFailedIteration(int iteration, int attempt) {
            writeOutputs(iteration + "-" + attempt);
        }
        
        public void writeOutputs(String iteration) {
            BufferedWriter pWriter = null;
            BufferedWriter tWriter = null;

            try {
                if(paramsFolder != null) {
                    pWriter = new BufferedWriter(new FileWriter(new File(paramsFolder, "params" + iteration + ".csv")));
                    theObjective.printParameters(pWriter, paramPrinter);
                }
                if(targobjFolder != null) {
                    tWriter = new BufferedWriter(new FileWriter(new File(targobjFolder, "targobj" + iteration + ".csv")));
                    theObjective.printTargetAndObjective(tWriter, targetPrinter);
                }
            } catch(IOException e) {
                logger.error(e.getMessage());
            } finally {
                if(pWriter != null) {
                    try {
                        pWriter.close();
                    } catch(IOException e) {}
                }
                if(tWriter != null) {
                    try {
                        tWriter.close();
                    } catch(IOException e) {}
                }
            }
        }
    }
}