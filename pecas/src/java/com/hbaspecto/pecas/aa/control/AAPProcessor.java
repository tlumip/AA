/*
 * Copyright  2005 PB Consult Inc and HBA Specto Incorporated
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
package com.hbaspecto.pecas.aa.control;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Writer;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.log4j.Logger;

import com.hbaspecto.functions.LogisticPlusLinearFunction;
import com.hbaspecto.functions.LogisticPlusLinearWithOverrideFunction;
import com.hbaspecto.matrix.SparseMatrix;
import com.hbaspecto.pecas.ChoiceModelOverflowException;
import com.hbaspecto.pecas.FormatLogger;
import com.hbaspecto.pecas.NoAlternativeAvailable;
import com.hbaspecto.pecas.OverflowException;
import com.hbaspecto.pecas.aa.AAStatusLogger;
import com.hbaspecto.pecas.aa.activities.Activity;
import com.hbaspecto.pecas.aa.activities.ActivityConstraints;
import com.hbaspecto.pecas.aa.activities.AggregateActivity;
import com.hbaspecto.pecas.aa.activities.AmountInZone;
import com.hbaspecto.pecas.aa.activities.PopSynTargets;
import com.hbaspecto.pecas.aa.activities.ProductionActivity;
import com.hbaspecto.pecas.aa.commodity.AbstractCommodity;
import com.hbaspecto.pecas.aa.commodity.BuyingZUtility;
import com.hbaspecto.pecas.aa.commodity.Commodity;
import com.hbaspecto.pecas.aa.commodity.CommodityFlowArray;
import com.hbaspecto.pecas.aa.commodity.CommodityZUtility;
import com.hbaspecto.pecas.aa.commodity.Exchange;
import com.hbaspecto.pecas.aa.commodity.NonTransportableExchange;
import com.hbaspecto.pecas.aa.commodity.SellingZUtility;
import com.hbaspecto.pecas.aa.control.FlowMatrixCompressor.FourCoordinatesI;
import com.hbaspecto.pecas.aa.technologyChoice.ConsumptionFunction;
import com.hbaspecto.pecas.aa.technologyChoice.LogitTechnologyChoice;
import com.hbaspecto.pecas.aa.technologyChoice.LogitTechnologyChoiceConsumptionFunction;
import com.hbaspecto.pecas.aa.technologyChoice.LogitTechnologyChoiceProductionFunction;
import com.hbaspecto.pecas.aa.technologyChoice.ProductionFunction;
import com.hbaspecto.pecas.aa.travelAttributes.LinearFunctionOfSomeSkims;
import com.hbaspecto.pecas.aa.travelAttributes.LinearSkimFunctionEEOverride;
import com.hbaspecto.pecas.aa.travelAttributes.PayloadNonlinearTransportCosts;
import com.hbaspecto.pecas.aa.travelAttributes.SomeSkims;
import com.hbaspecto.pecas.aa.travelAttributes.TransportKnowledge;
import com.hbaspecto.pecas.zones.AbstractZone;
import com.hbaspecto.pecas.zones.PECASZone;
import com.hbaspecto.pecas.zones.SimpleZoneGroupSystem;
import com.hbaspecto.pecas.zones.ZoneGroup;
import com.hbaspecto.pecas.zones.ZoneGroupSystem;
import com.hbaspecto.pecas.zones.ZoneSystem;
import com.pb.common.datafile.CSVFileReader;
import com.pb.common.datafile.CSVFileWriter;
import com.pb.common.datafile.GeneralDecimalFormat;
import com.pb.common.datafile.JDBCTableReader;
import com.pb.common.datafile.TableDataReader;
import com.pb.common.datafile.TableDataSet;
import com.pb.common.datafile.TableDataSetCollection;
import com.pb.common.datafile.TableDataSetIndex;
import com.pb.common.datafile.TableDataSetIndexedValue;
import com.pb.common.matrix.AlphaToBeta;
import com.pb.common.matrix.CSVMatrixWriter;
import com.pb.common.matrix.Emme2311MatrixWriter;
import com.pb.common.matrix.HashtableAlphaToBeta;
import com.pb.common.matrix.Matrix;
import com.pb.common.matrix.MatrixHistogram;
import com.pb.common.matrix.MatrixWriter;
import com.pb.common.matrix.StringIndexedNDimensionalMatrix;
import com.pb.common.matrix.ZipMatrixWriter;
import com.pb.common.sql.JDBCConnection;
import com.pb.common.util.ResourceUtil;

import no.uib.cipr.matrix.MatrixEntry;


/**
 * This class is responsible for reading in the AA input files
 * and setting up objects used by AAModel.  It is created by the AAControl
 * class which is responsible for setting the ResourceBundle and the timePeriod
 *
 * @author Christi Willison, John Abraham
 * @version Mar 17, 2004, Sept 2007
 */
/**
 * @author John Abraham
 *
 */
public abstract class AAPProcessor {
    protected static Logger logger = Logger.getLogger(AAPProcessor.class);
    private static FormatLogger loggerf = new FormatLogger(logger);
    //the following 2 params are set by the AAControl's constructor which is called
    //when AA is run in monolithic fashion.  Otherwise, these params are passed to
    //the AAPProcessor constructor when called from AADAF (see AAServerTask's 'onStart( )' method)
    protected int timePeriod;
    protected int baseYear;
    protected ResourceBundle aaRb;
    private HashMap<String, HashMap<String, TableDataSetCollection>> collections = new HashMap<>();
    
    private boolean rerunForOutputs = false;
    

    protected PECASZone[] zones; //will be initialized in setUpZones method after length has been determined.

    private String outputPath = null;
    protected String zipExtension = null;
    protected Hashtable<String,ZoneQuantityStorage> floorspaceInventory;

    protected HashtableAlphaToBeta floorspaceZoneCrossref;
    protected int maxAlphaZone=0;
    
    ArrayList<HistogramSpec> histogramSpecifications = new ArrayList<HistogramSpec>();
    static class HistogramSpec {
        String commodityName;
        String categorizationSkim;
        private Commodity com = null;
        /**
         * <code>boundaries</code> contains Float objects describing the histogram band boundaries
         */
        ArrayList<Float> boundaries = new ArrayList<Float>();
        
        Commodity getCommodity() {
            if (com == null) {
                com = Commodity.retrieveCommodity(commodityName);
            }
            return com;
        }
    }
    protected boolean logitTechnologyChoice;
    // 4 dimensional matrix, activity, zoneNumber, commodity, MorU
    private StringIndexedNDimensionalMatrix zonalMakeUseCoefficients;

    protected CSVFileReader csvFileReader=null;
//    private Hashtable alphaZoneActivityConstraintsInventory = new Hashtable();
    //Hashtable betaZoneActivityConstraintsInventory = new Hashtable();
    public double maxConstantChange;
	private BufferedWriter overRideFile = null;
	public static Boolean isSetup = false;
	private PopSynTargets popSyn = null;
    
    public AAPProcessor(){
    }

    public AAPProcessor(int timePeriod, ResourceBundle aaRb){
        this.timePeriod = timePeriod;
        this.aaRb = aaRb;
    }

    /**
     */
    public void setUpAA() {
       
        setUpZones(); // read in PECASZonesI.csv and creates a 'Zone' table data set
        String[] skimNames = setUpCommodities(); //read in CommoditiesI.csv and initalize an array of commodities
		try {
			popSyn = new PopSynTargets(this);
		} catch (FileNotFoundException e) {
			logger.warn("Can't load PopSyn targets file");
		}
        readFloorspaceZones();
        setUpTransportConditions(skimNames); //read in betapktime.zip and betapkdist.zip
        setUpProductionActivities(); //read in ActivitiesI.csv and initalize an array of aggregate activities
        setUpExchangesAndZUtilities();//read in ExchangeImportExportI.csv, create an 'Exchanges'
        //table data set and link the exchanges to commodities.
        setUpPutTaxes();// read in PutTaxes.csv, set the exchanges up with their put taxes.
        setExchangePrices();
        setUpMakeAndUse();//read in MakeUseI.csv and create a 'MakeUse' table data set.
        				  // Or in case of AASetupWithTechnologySubstitution class, it reads TechnologyOptionsI.csv
        checkTechnologyNestingCoefficients();
        double[][] floorspaceInventoryByLUZ = readFloorspace();
        if (ResourceUtil.getBooleanProperty(aaRb,"aa.automaticTechnologySizeTerms",true)) {
        	logger.info("aa.automaticTechnologySizeTerms is true, not setting FloorspaceBuyingSizeTerms");
        	setFloorspaceProportionsForTechnologySizeTerms(floorspaceInventoryByLUZ);
    		logger.info("Since you are using technology size terms, ActivitySizeTermsI should only have entries for activities that do not use space");
        	recalcActivitySizeTerms(floorspaceInventoryByLUZ);
        } else {
        	logger.warn("aa.automaticTechnologySizeTerms is false, using FloorspaceBuyingSizeTerms instead, this is deprecated functionality, please upgrade your model");
	        recalcActivitySizeTerms(floorspaceInventoryByLUZ);
    		logger.info("Since you are not using technology size terms, be sure ActivitySizeTermsI has entries for all activities that use space");
	        recalcFloorspaceBuyingSizeTerms();
        }
        recalcFloorspaceImport();
        
    }
    
	protected abstract double[][] readFloorspace();


    protected void setUpZones() {
        logger.info("Setting up Zones");
        TableDataSet ztab = loadTableDataSet("PECASZonesI","aa.reference.data");
        PECASZone.setUpZones(ztab);
        
        //Once the array of AbstractZone is created it is then copied back into
        //an array of PECASZone objects. (the PECASZone class is in the despair.aa package
        //whereas the AbstractZone class is in the despair.model package.
        //My thought is that we could get rid of the AbstractZone[] initialization
        //as I think all the relevent properties are set in the PECASZone objects.

        AbstractZone[] allZones = AbstractZone.getAllZones();
        zones = new PECASZone[allZones.length];
        for (int z = 0; z < allZones.length; z++) {
            zones[z] = (PECASZone) allZones[z];
        }
    }

    protected abstract void setUpProductionActivities();
    /**
     * Sets up commodities by reading in CommoditiesI.csv
     * @return array of names of skims
     */
    protected String[] setUpCommodities() {
        ArrayList<String> skimNames = new ArrayList<String>();
        logger.info("Setting up Commodities");
        TableDataSet ctab=loadDataSetFromYearOrAllYears("CommoditiesI");
        int nameColumn = ctab.checkColumnPosition("Commodity");
        int numberColumn = ctab.getColumnPosition("CommodityNumber");
        int bdpColumn = ctab.checkColumnPosition("BuyingDispersionParameter");
        int sdpColumn = ctab.checkColumnPosition("SellingDispersionParameter");
        int bscColumn = ctab.checkColumnPosition("BuyingSizeCoefficient");
        int bpcColumn = ctab.checkColumnPosition("BuyingPriceCoefficient");
        int btcColumn = ctab.checkColumnPosition("BuyingTransportCoefficient");
        int sscColumn = ctab.checkColumnPosition("SellingSizeCoefficient");
        int spcColumn = ctab.checkColumnPosition("SellingPriceCoefficient");
        int stcColumn = ctab.checkColumnPosition("SellingTransportCoefficient");
        int exchangeTypeColumn = ctab.checkColumnPosition("ExchangeType");
        int floorspaceTypeC = ctab.checkColumnPosition("FloorspaceCommodity");
        int expectedPriceC = ctab.checkColumnPosition("ExpectedPrice");
        int manualSizeTermsC = ctab.getColumnPosition("ManualSizeTerms");
        int minPriceC = ctab.getColumnPosition("MinimumPrice");
        int maxPriceC = ctab.getColumnPosition("MaximumPrice");
        int totalSizeTermC = ctab.getColumnPosition("TotalSizeTerm");
        int detailedOutputC = ctab.getColumnPosition("DetailedOutputs");
        int refDistanceC = ctab.getColumnPosition("PayloadReferenceDistance");
        int payloadDistanceC  = ctab.getColumnPosition("AnotherPayloadDistance");
        int payloadDistanceSkimC = ctab.getColumnPosition("PayloadInterchangeName");
        if (refDistanceC + payloadDistanceC + payloadDistanceSkimC != -3 && (refDistanceC == -1 || payloadDistanceC == -1 || payloadDistanceSkimC == -1)) {
        	String msg = "You need to specify all three columns ReferencePayload, AnotherPayload and PayloadInterchangeName to use nonlinear skims";
        	logger.fatal(msg);
        	throw new RuntimeException(msg);
        }
        int searchC = ctab.getColumnPosition("Search"); // whether to search for prices for this commodity (optional column)
        if (manualSizeTermsC <=0) {
        	if (ResourceUtil.getBooleanProperty(aaRb,"calculateExchangeSizes",false) ) {
        		String msg = "No ManualSizeTerms column in CommoditiesI, calculateExchangeSizes=true, calculating size terms for all non-floorspace commodities";
        		Commodity.setCalculateSizeTerms(true);
        		logger.info(msg);
        	} else {
        		String msg = "No ManualSizeTerms column in CommoditiesI, not calculating size terms for non-floorspace commodities";
        		Commodity.setCalculateSizeTerms(false);
        		logger.info(msg);
        	}
        } else {
        	logger.info("ManualSizeTerms column in CommoditiesI, calculating size terms for non floorspace commodities where ManualSizeTerms=true");
        	Commodity.setCalculateSizeTerms(true);
        	if (!ResourceUtil.getBooleanProperty(aaRb,"calculateExchangeSizes",true)) {
        		logger.warn("calculateExchangeSizes=false in properties file, this conflicts with the existance of the ManualSizeTerms column in CommoditiesI, ignoring entry in properties file");
        	}
        }
        Commodity.setDetailedOutputSpecified(detailedOutputC > 0);
        int[] overRideExternalZones = PECASZone.getOverrideExternalZones();
        for (int row = 1; row <= ctab.getRowCount(); row++) {
            String commodityName = ctab.getStringValueAt(row, nameColumn);
            float defaultBuyingDispersionParameter = ctab.getValueAt(row, bdpColumn);
            float defaultSellingDispersionParameter = ctab.getValueAt(row, sdpColumn);
            float buyingSizeCoefficient = ctab.getValueAt(row, bscColumn);
            float buyingPriceCoefficient = ctab.getValueAt(row, bpcColumn);
            float buyingTransportCoefficient = ctab.getValueAt(row, btcColumn);
            float sellingSizeCoefficient = ctab.getValueAt(row, sscColumn);
            float sellingPriceCoefficient = ctab.getValueAt(row, spcColumn);
            float sellingTransportCoefficient = ctab.getValueAt(row, stcColumn);
            boolean isFloorspace = ctab.getBooleanValueAt(row,floorspaceTypeC);
            char exchangeTypePar = ctab.getStringValueAt(row, exchangeTypeColumn).charAt(0);
            
            Double minPrice = null;
            if (minPriceC != -1) {
                String minPriceString = ctab.getStringValueAt(row,  minPriceC);
                if (minPriceString != null) {
                	if (minPriceString.length()>0) {
                		minPrice = Double.valueOf(minPriceString);
                	}
                }
            }
            Double maxPrice = null;
            if (maxPriceC != -1) {
                String maxPriceString = ctab.getStringValueAt(row,  maxPriceC);
                if (maxPriceString != null) {
                	if (maxPriceString.length()>0) {
                		maxPrice = Double.valueOf(maxPriceString);
                	}
                }
            }
            
            Commodity c;
            if(numberColumn == -1) {
                c = Commodity.createOrRetrieveCommodity(commodityName, exchangeTypePar, minPrice, maxPrice);
            } else {
                c = Commodity.createOrRetrieveCommodity(commodityName, (int) ctab.getValueAt(row, numberColumn), exchangeTypePar, minPrice, maxPrice);
            }
            c.setDefaultBuyingDispersionParameter(defaultBuyingDispersionParameter);
            c.setDefaultSellingDispersionParameter(defaultSellingDispersionParameter);
            c.setBuyingUtilityCoefficients(buyingSizeCoefficient, buyingPriceCoefficient, buyingTransportCoefficient);
            c.setSellingUtilityCoefficients(sellingSizeCoefficient, sellingPriceCoefficient, sellingTransportCoefficient);
            c.setFloorspaceCommodity(isFloorspace);
            LinearFunctionOfSomeSkims lfoss;
            if (overRideExternalZones == null) {
                lfoss = new LinearFunctionOfSomeSkims();
            } else {
                lfoss = new LinearSkimFunctionEEOverride(overRideExternalZones, -Double.MAX_VALUE);
            }
            int skimNumber=1;
            boolean found=true;
            while(found) {
            	int coeffColumnNumber=ctab.getColumnPosition("InterchangeCoefficient"+skimNumber);
            	int nameColumnNumber=ctab.getColumnPosition("InterchangeName"+skimNumber);
            	if (skimNumber==1 && (coeffColumnNumber==-1 || nameColumnNumber==-1)) {
            		String msg="No InterchangeName1/InterchangeCoefficient1 in CommditiesI";
            		logger.fatal(msg);
            		throw new RuntimeException(msg);
            	}
            	// last skim
            	if (coeffColumnNumber==-1 || nameColumnNumber==-1) {
            		found = false;
            	} else {
                    String skimName = ctab.getStringValueAt(row,nameColumnNumber);
                    if (skimName != null){
                        if (skimName.length()!=0 && skimName.trim().length()!=0 && !skimName.equalsIgnoreCase("none")) {
                            lfoss.addSkim(skimName,ctab.getValueAt(row,coeffColumnNumber));
                            if (!skimNames.contains(skimName)) skimNames.add(skimName);
                        }
                    }
            		
            	}
            	skimNumber++;
            }
            if (payloadDistanceC != -1) {
            	double payloadDistance = ctab.getValueAt(row, payloadDistanceC);
            	if (payloadDistance == 0) {
            		c.setCommodityTravelPreferences(lfoss);
            	} else {
            		double payloadRefDistance = ctab.getValueAt(row, refDistanceC);
            		String payloadSkimName = ctab.getStringValueAt(row,  payloadDistanceSkimC);
            		logger.info("Setting up non-linear transport costs for "+c.getName()+" with a \"AnotherPayloadDistance\" of "+payloadDistance+" "+payloadSkimName);
            		PayloadNonlinearTransportCosts ptcosts = new PayloadNonlinearTransportCosts(payloadRefDistance, payloadDistance, lfoss, payloadSkimName);
            		c.setCommodityTravelPreferences(ptcosts);
            	}
            } else {
            	c.setCommodityTravelPreferences(lfoss);
            }
            c.compositeMeritMeasureWeighting = ctab.getValueAt(row, "GOFWeighting");
            c.setExpectedPrice(ctab.getValueAt(row,expectedPriceC));
            if (manualSizeTermsC > 0) c.setManualSizeTerms(ctab.getBooleanValueAt(row,manualSizeTermsC));
            if (searchC > 0) c.setDoSearch(ctab.getBooleanValueAt(row, searchC));
            if (totalSizeTermC > 0) c.setTotalSize(ctab.getValueAt(row, totalSizeTermC));
            if (detailedOutputC > 0) c.setDetailedOutput(ctab.getBooleanValueAt(row, detailedOutputC));

        }
        return (String[]) skimNames.toArray(new String[0]);
    }

    protected void readFloorspaceZones() {
        logger.info("Reading Floorspace Zones");
        floorspaceZoneCrossref = new HashtableAlphaToBeta();
        if (ResourceUtil.getProperty(aaRb,"aa.useFloorspaceZones").equalsIgnoreCase("true")) {
            TableDataSet alphaZoneTable = loadTableDataSet("FloorspaceZonesI","aa.reference.data");
            int tazColumn = alphaZoneTable.getColumnPosition("TAZ");
            if (tazColumn == -1) tazColumn = alphaZoneTable.checkColumnPosition("AlphaZone");
            int luzColumn = alphaZoneTable.getColumnPosition("LUZ");
            if (luzColumn == -1) luzColumn = alphaZoneTable.checkColumnPosition("PECASZone");
            for (int zRow = 1; zRow <= alphaZoneTable.getRowCount(); zRow++) {
                Integer floorspaceZone = new Integer( (int) alphaZoneTable.getValueAt(zRow, tazColumn));
                int pecasZoneInt = (int) alphaZoneTable.getValueAt(zRow,luzColumn);
                AbstractZone pecasZone = AbstractZone.findZoneByUserNumber(pecasZoneInt);
                if (pecasZone != null) {
                    // don't add in bogus records -- there might be land use zones that aren't covered by the spatial IO model
                    Integer pecasZoneInteger = new Integer(pecasZoneInt);
                    floorspaceZoneCrossref.put(floorspaceZone,pecasZoneInteger);
                } else {
                    logger.warn("Bad  LUZ number "+pecasZoneInt+" in FloorspaceZonesI ... ignoring TAZ "+floorspaceZone.intValue());
                }
            }
        } else {
            logger.info("Not using floorspace zones (TAZs) -- TAZ are the same as LUZ");
            for (int z= 0; z<zones.length;z++) {
                Integer zoneNumber = new Integer(zones[z].getZoneUserNumber());
                floorspaceZoneCrossref.put(zoneNumber,zoneNumber);
            }
        }
        maxAlphaZone = 0; // force reset
        maxAlphaZone = maxAlphaZone();
    }

    public int maxAlphaZone() {
        if (maxAlphaZone == 0) {
	        for (Object obj : floorspaceZoneCrossref.keySet()) {
	            int alphaZoneNumber = (Integer) obj;
	            if (alphaZoneNumber>maxAlphaZone) {
	                maxAlphaZone = alphaZoneNumber;
	            }
	        }
        }
        return  maxAlphaZone;
    }
    
    /**
     * Returns the mapping between alpha and beta zones
     */
    public Map<Integer, Integer> getAlphaToBetaMap() {
        return Collections.unmodifiableMap(floorspaceZoneCrossref);
    }

    protected static class ZoneQuantityStorage {
        public final String typeName;
        //private final float [] inventory;
        private final HashMap<Integer,Double> inventoryMap = new HashMap<Integer,Double>();

        public ZoneQuantityStorage(String name) {
            typeName = name;
        }

        public Commodity getFloorspaceType() {
            return Commodity.retrieveCommodity(typeName);
        }

        public double getQuantityForZone(int zoneNumber) {
            Double inventory = inventoryMap.get(new Integer(zoneNumber));
            if (inventory == null) return 0;
            return inventory.doubleValue();
        }

        public void setQuantityForZone(int zoneNumber, double quantity) {
            inventoryMap.put(new Integer(zoneNumber),new Double(quantity));
        }

        public void increaseQuantityForZone(int zoneNumber, double increase) {
            Integer zoneNumberObject = new Integer(zoneNumber);
            Double inventory = inventoryMap.get(zoneNumberObject);
            if (inventory == null) inventory = new Double(increase);
            else inventory = new Double(inventory.doubleValue()+increase);
            inventoryMap.put(zoneNumberObject,inventory);


        }
        /**
         * @return Returns the inventoryMap.
         */
        public HashMap<Integer, Double> getInventoryMap() {
            return inventoryMap;
        }

        /**
         * @param zoneNumber
         * @return boolean
         */
        public boolean isEntryForZone(int zoneNumber) {
            Double inventory = inventoryMap.get(new Integer(zoneNumber));
            if (inventory == null) return false;
            return true;
        }
    }

    protected void setUpPutTaxes() {
    	TableDataSet putTaxes = loadDataSetFromYearOrAllYears("PutTaxes", false);
    	if (putTaxes == null) {
    		logger.info("No Put Taxes defined in PutTaxes.csv");
    		return;
    	}
    	int putColumn = putTaxes.getColumnPosition("Put");
    	if (putColumn == -1) {
    		putColumn = putTaxes.checkColumnPosition("Commodity");
    	}
    	int zoneColumn = putTaxes.checkColumnPosition("ZoneNumber");
    	int percentTaxColumn = putTaxes.checkColumnPosition("PercentTax");
    	int absoluteTaxColumn = putTaxes.checkColumnPosition("AbsoluteTax");
    	for (int row =1 ; row <= putTaxes.getRowCount(); row++) {
    		Commodity c = Commodity.retrieveCommodity(putTaxes.getStringValueAt(row, putColumn));
    		int zone = (int) putTaxes.getValueAt(row,  zoneColumn);
    		float percentTax = putTaxes.getValueAt(row,  percentTaxColumn);
    		float absoluteTax = putTaxes.getValueAt(row,  absoluteTaxColumn);
    		if (zone == -1) {
    			// Taxes in all zones
    			for (Exchange ex : c.getAllExchanges()) {
    				ex.setPercentTax(percentTax);
    				ex.setAbsoluteTax(absoluteTax);
    			}
    		} else {
    			PECASZone pz = PECASZone.getPECASZoneByUserNumber(zone);
    			Exchange ex = c.getExchange(pz.zoneIndex);
    			ex.setPercentTax(percentTax);
    			ex.setAbsoluteTax(absoluteTax);
    		}
    	}
    }
    
    protected void setUpExchangesAndZUtilities() {
        logger.info("Setting up Exchanges and ZUtilitites");
        int numExchangeNotFoundErrors=0;
        TableDataSet exchanges = loadDataSetFromYearOrAllYears("ExchangeImportExportI");
        class ExchangeInputData {
            String commodity;
            int zone;
            float buyingSize;
            float sellingSize;
            boolean specifiedExchange;
            float importFunctionMidpoint;
            float importFunctionMidpointPrice;
            float importFunctionLambda;
            float importFunctionDelta;
            float importFunctionSlope;
            float exportFunctionMidpoint;
            float exportFunctionMidpointPrice;
            float exportFunctionLambda;
            float exportFunctionDelta;
            float exportFunctionSlope;
            boolean monitorExchange;
            double price;

        }
        HashMap<String, ExchangeInputData> exchangeTempStorage = new HashMap<String, ExchangeInputData>();
        int priceColumn = exchanges.getColumnPosition("Price");
        if (priceColumn == -1) logger.info("No price data in ExchangeImportExport table");
        int monitorColumn = exchanges.getColumnPosition("MonitorExchange");
        if (monitorColumn == -1) logger.warn("No MonitorExchange column in ExchangeImportExport table -- not monitoring any exchanges");
        for (int row = 1; row <= exchanges.getRowCount(); row++) {
            String key = exchanges.getStringValueAt(row, "Commodity") + "$" + String.valueOf((int) exchanges.getValueAt(row, "ZoneNumber"));
            ExchangeInputData exInputData = new ExchangeInputData();
            exInputData.commodity = exchanges.getStringValueAt(row, "Commodity");
            if (Commodity.retrieveCommodity(exInputData.commodity)==null) {
                logger.fatal("Invalid commodity name "+exInputData.commodity+" in ExchangeImportExportI");
                throw new RuntimeException("Invalid commodity name "+exInputData.commodity+" in ExchangeImportExportI");
            }
            exInputData.zone = (int) exchanges.getValueAt(row, "ZoneNumber");
            if (exInputData.zone!=-1) {
            	if (PECASZone.findZoneByUserNumber(exInputData.zone)==null) {
            		logger.fatal("Invalid zone number "+exInputData.zone+" in ExchangeImportExportTable");
            		throw new RuntimeException("Invalid zone number "+exInputData.zone+" in ExchangeImportExportTable");
            	}
            }
            exInputData.buyingSize = exchanges.getValueAt(row, "BuyingSize");
            exInputData.sellingSize = exchanges.getValueAt(row, "SellingSize");
            String ses = exchanges.getStringValueAt(row, "SpecifiedExchange");
            if (ses.equalsIgnoreCase("true")) {
                exInputData.specifiedExchange = true;
            } else {
                exInputData.specifiedExchange = false;
            }
            exInputData.importFunctionMidpoint = exchanges.getValueAt(row, "ImportFunctionMidpoint");
            exInputData.importFunctionMidpointPrice = exchanges.getValueAt(row, "ImportFunctionMidpointPrice");
            exInputData.importFunctionLambda = exchanges.getValueAt(row, "ImportFunctionEta");
            exInputData.importFunctionDelta = exchanges.getValueAt(row, "ImportFunctionDelta");
            exInputData.importFunctionSlope = exchanges.getValueAt(row, "ImportFunctionSlope");
            exInputData.exportFunctionMidpoint = exchanges.getValueAt(row, "ExportFunctionMidpoint");
            exInputData.exportFunctionMidpointPrice = exchanges.getValueAt(row, "ExportFunctionMidpointPrice");
            exInputData.exportFunctionLambda = exchanges.getValueAt(row, "ExportFunctionEta");
            exInputData.exportFunctionDelta = exchanges.getValueAt(row, "ExportFunctionDelta");
            exInputData.exportFunctionSlope = exchanges.getValueAt(row, "ExportFunctionSlope");
            if (monitorColumn == -1)
                exInputData.monitorExchange = false;
            else {
                String monitor = exchanges.getStringValueAt(row, monitorColumn);
                if (monitor.equalsIgnoreCase("true")) {
                    exInputData.monitorExchange = true;
                } else {
                    exInputData.monitorExchange = false;
                }
            }
            if (priceColumn != -1) {
                exInputData.price = exchanges.getValueAt(row, priceColumn);
            } else {
                exInputData.price = Commodity.retrieveCommodity(exInputData.commodity).getExpectedPrice();
            }
            exchangeTempStorage.put(key, exInputData);
        }
        for (AbstractCommodity ac : Commodity.getAllCommodities()) {
            Commodity c = (Commodity) ac;
            for (int z = 0; z < zones.length; z++) {
                SellingZUtility szu = new SellingZUtility(c, zones[z],  c.getCommodityTravelPreferences());
                BuyingZUtility bzu = new BuyingZUtility(c, zones[z], c.getCommodityTravelPreferences());
                szu.setDispersionParameter(c.getDefaultSellingDispersionParameter());
                bzu.setDispersionParameter(c.getDefaultBuyingDispersionParameter());
                String key = c.name + "$" + zones[z].getZoneUserNumber();
                ExchangeInputData exData = exchangeTempStorage.get(key);
                boolean found = true;
                if (exData == null) {
                    // try to find the default values for this commodity
                    key = c.name + "$" + "-1";
                    exData = exchangeTempStorage.get(key);
                    if (exData == null) {
                        found = false;
                    }
//                    else {
//                        logger.info("Using default exchange data for commodity " + c + " zone " + zones[z].getZoneUserNumber());
//                    }
                }
                boolean specifiedExchange = false;
                if (found) {
                    specifiedExchange = exData.specifiedExchange;
                }
                if (c.exchangeType != 's' || specifiedExchange) {
                    Exchange xc;
                    if (c.exchangeType == 'n')
                        xc = new NonTransportableExchange(c, zones[z]);
                    else
                        xc = new Exchange(c, zones[z], zones.length);
                    if (!found) {//backup default data.
                        if (numExchangeNotFoundErrors < 20 && !c.isFloorspaceCommodity()) {
                        	numExchangeNotFoundErrors++;
                            logger.info("Can't locate size term for Commodity " + c + " zone " +
                                    zones[z].getZoneUserNumber() + " using 1.0 for size terms and setting imports/exports to zero");
                            if (numExchangeNotFoundErrors == 20) logger.warn("Surpressing further warnings on missing size terms");
                        }
                        xc.setBuyingSizeTerm(1.0);
                        xc.setSellingSizeTerm(1.0);
                        xc.setImportFunction(Commodity.zeroFunction);
                        xc.setExportFunction(Commodity.zeroFunction);
                        xc.setPrice(c.getExpectedPrice());
                    } else {
                        xc.setBuyingSizeTerm(exData.buyingSize);
                        xc.setSellingSizeTerm(exData.sellingSize);
                        xc.setImportFunction(new LogisticPlusLinearFunction(exData.importFunctionMidpoint,
                                exData.importFunctionMidpointPrice,
                                exData.importFunctionLambda,
                                exData.importFunctionDelta,
                                exData.importFunctionSlope));
                        xc.setExportFunction(new LogisticPlusLinearFunction(exData.exportFunctionMidpoint,
                                exData.exportFunctionMidpointPrice,
                                exData.exportFunctionLambda,
                                exData.exportFunctionDelta,
                                exData.exportFunctionSlope));
                        if (exData.monitorExchange) {
                            xc.monitor = true;
                        }
                        xc.setPrice(exData.price);
                    }
                    if (c.exchangeType == 'p' || c.exchangeType == 'a' || c.exchangeType == 'n') szu.addExchange(xc);
                    if (c.exchangeType == 'c' || c.exchangeType == 'a' || c.exchangeType == 'n') bzu.addExchange(xc);
                }
            }
            if(logger.isDebugEnabled()) {
                logger.debug("Created all exchanges for commodity " + c + " -- now linking exchanges to production and consumption for " + zones.length + " zones");
            }
            for (int z = 0; z < zones.length; z++) {
                if (z % 100 == 0) {
                    if(logger.isDebugEnabled()) {
                        logger.debug(" " + z + "(" + zones[z].getZoneUserNumber() + ")");
                    }
                }
                if (c.exchangeType == 'c' || c.exchangeType == 's' || c.exchangeType == 'a') {
                    CommodityZUtility czu = c.retrieveCommodityZUtility(zones[z], true);
                    czu.addAllExchanges(); // add in all the other exchanges for that commodity
                }
                if (c.exchangeType == 'p' || c.exchangeType == 's' || c.exchangeType == 'a') {
                    CommodityZUtility czu = c.retrieveCommodityZUtility(zones[z],false);
                    czu.addAllExchanges(); // add in all the other exchanges for that commodity
                }
            }

        }
    }

	public TableDataSet loadDataSetFromYearOrAllYears(String tableName) {
		return loadDataSetFromYearOrAllYears(tableName, true);
	}
    
	public TableDataSet loadDataSetFromYearOrAllYears(String tableName, boolean check) {
		TableDataSet table = loadTableDataSet(tableName,"aa.current.data",false);
        if (table == null) {
        	logger.info("Did not find "+tableName+" in aa.current.data, looking in aa.base.data");
        	table = loadTableDataSet(tableName, "aa.base.data",check);
        	if (table == null) logger.info("Did not find "+tableName+" in aa.base data either");
        } else {
        	logger.info("Found year-specific "+tableName+" in current year (aa.current.data)");
        }
		return table;
	}

    protected void setExchangePrices() {
    	TableDataSet initialPrices = null;
    	if (isRerunForOutputs()) {
    		logger.info("Rerun with existing prices");
    		initialPrices = loadTableDataSet("ExchangeResults", "output.data", true);
    	} else {
    		logger.info("Getting Exchange Prices");
    		initialPrices = loadTableDataSet("ExchangeResultsI","aa.current.data", false);
    		if (initialPrices == null) {
    			logger.info("No special price data for current year, check for previous year prices");
    			initialPrices = loadTableDataSet("ExchangeResults","aa.previous.data", false);
    			if(initialPrices == null){
    				logger.error("No previous year ExchangeResults to get prices from");
    				return;
    			}
    			logger.info("ExchangeResults from the previous year have been found");
    		}
    	}
    	int commodityNameColumn = initialPrices.getColumnPosition("Commodity");
    	if  (commodityNameColumn == -1) commodityNameColumn = initialPrices.checkColumnPosition("Put");
    	int priceColumn = initialPrices.checkColumnPosition("Price");
    	int zoneNumberColumn = initialPrices.checkColumnPosition("ZoneNumber");
        int fixedPriceColumn = initialPrices.getColumnPosition("FixedPrice");
        if (commodityNameColumn * priceColumn * zoneNumberColumn <= 0) {
            logger.fatal("Missing column in Exchange Results -- check for Commodity, Price and ZoneNumber columns.  Continuing without initial prices");
        }        for (int row = 1; row <= initialPrices.getRowCount(); row++) {
            String cname = initialPrices.getStringValueAt(row, commodityNameColumn);
            Commodity com = Commodity.retrieveCommodity(cname);
            if (com == null) {
                logger.warn("Invalid commodity name " + cname + " in ExchangeResultsI price table, you might need to discard your ExchangeResultsI file or delete extra rows");
            } else {
                int zoneUserNumber = (int) initialPrices.getValueAt(row, zoneNumberColumn);
                AbstractZone t = PECASZone.findZoneByUserNumber(zoneUserNumber);
                if (t==null) {
                	logger.warn("Zone "+zoneUserNumber+" refered to in initial prices does not exist");
                } else {
	                int zoneIndex = t.getZoneIndex();
	                Exchange x = com.getExchange(zoneIndex);
	                if (x == null) {
	                    logger.error("No exchange for " + cname + " in " + zoneUserNumber + " check price input file to see if your zone numbers are correct, are you running with a different zone set?");
	                } else {
	                    x.setPrice(initialPrices.getValueAt(row, priceColumn));
	                    if (fixedPriceColumn >=0) {
	                    	if (initialPrices.getBooleanValueAt(row, fixedPriceColumn)) {
	                    		x.setDoSearch(false);
	                    	}
	                    }
	                }
                }
            }
        }
        return;
    }


    protected void setUpTransportConditions(String[] skimNames) {
    	logger.info("Setting up Transport Conditions");
    	String skimFormat = ResourceUtil.getProperty(aaRb, "Model.skimFormat");
    	SomeSkims someSkims = null;
		String path1 = ResourceUtil.getProperty(aaRb, "skim.data");
		String path2 = ResourceUtil.getProperty(aaRb, "skim.data1");
    	if (skimFormat.equalsIgnoreCase("TableDataSet")) {
    		String[] skimColumns = new String[skimNames.length+4];
    		skimColumns[0] = "Origin";
    		skimColumns[1] = "Destination";
    		skimColumns[2] = "i";
    		skimColumns[3] = "j";
    		for(int i =0;i<skimNames.length;i++) {
    			skimColumns[i+4] = skimNames[i];
    		}
    		String skimFileName=ResourceUtil.getProperty(aaRb, "skim.filename");
    		TableDataSet s = readSkimFile(path1, path2, skimColumns,
					skimFileName);
    		someSkims = new SomeSkims();
    		someSkims.addTableDataSetSkims(s,skimNames,AbstractZone.maxZoneNumber);
    	} else if (skimFormat.equalsIgnoreCase("MatrixCSV")){
    		logger.info("reading in MatrixCSVSkims");
    		someSkims = new SomeSkims(path1,path2);
    		for (int s=0;s<skimNames.length;s++) {
    			someSkims.addCSVSquareMatrix(skimNames[s]);
    		}
    	} else {
    		logger.info("reading in zipMatrixSkims");

    		someSkims = new SomeSkims(path1, path2);
    		for (int s=0;s<skimNames.length;s++) {
    			someSkims.addZipMatrix(skimNames[s]);
    		}
    	}
    	TransportKnowledge.globalTransportKnowledge = someSkims;
    	ArrayList<Integer> internalZoneNumbers = new ArrayList<Integer>();
    	ArrayList<Integer> externalZoneNumbers = new ArrayList<Integer>();
    	for (PECASZone z : zones) {
    		if (z.isExternal()) {
    			externalZoneNumbers.add(z.zoneUserNumber);
    		} else {
    			internalZoneNumbers.add(z.zoneUserNumber);
    		}
    	}
    	someSkims.checkCompleteness(internalZoneNumbers, externalZoneNumbers);
    }

	public TableDataSet readSkimFile(String path1, String path2,
			String[] skimColumns, String skimFileName) {
		boolean columnFilter = true;
		if (skimColumns == null) columnFilter = false;
		CSVFileReader reader = new CSVFileReader();
		IOException e=null;
		TableDataSet s = null;
		try {
			// read from file
			s = reader.readFile(new File(path1 + skimFileName), columnFilter, skimColumns);
		} catch (IOException e1) {
			e = e1;
		}
		if (s==null) {
			try {
				//read from URL
				s = reader.readFile(path1+skimFileName, columnFilter, skimColumns);
			} catch (IOException e2) {
			}
		}
		if (s==null) {
			try {
				//read from file
				s = reader.readFile(new File(path2+skimFileName), columnFilter, skimColumns);
			} catch (IOException e3) {}
		}
		if (s==null) {
			try {
				//read from URL
				s = reader.readFile(path2+skimFileName, columnFilter, skimColumns);
			} catch (IOException e4) {}
		}
		if (s==null) {
			logger.fatal("Error loading in skim "+skimFileName, e);
			throw new RuntimeException("Error loading in skims "+skimFileName, e);
		}
		return s;
	}

	protected abstract void setUpMakeAndUse();
    
	private void checkTechnologyNestingCoefficients() {
        TableDataSet maxErrorTermSizeConsumption = new TableDataSet();
        maxErrorTermSizeConsumption.setName("ConsumptionErrorTermSizes");
        TableDataSet maxErrorTermSizeProduction = new TableDataSet();
        maxErrorTermSizeProduction.setName("ProductionErrorTermSizes");
        String[] activityNames = new String[AggregateActivity.getAllProductionActivities().size()];
        Iterator<ProductionActivity> it = AggregateActivity.getAllProductionActivities().iterator();
        int a=0;
        while (it.hasNext()) {
            activityNames[a]=it.next().name;
            a++;
        }
        maxErrorTermSizeConsumption.appendColumn(activityNames,"Activity");
        maxErrorTermSizeProduction.appendColumn(activityNames,"Activity");
        logger.info("Logit Scale Ratio Logging:");
        a=0;
        for (ProductionActivity p : ProductionActivity.getAllProductionActivities()) {
            a++; // TableDataSets are 1-based
            AggregateActivity aa = (AggregateActivity) p;
            aa.getConsumptionFunction().doFinalSetupAndSetCommodityOrder(Commodity.getAllCommodities());
            // we have a different AAPProcessor for LogitTechnologyChoice, to avoid all these if statements
            if (aa.getConsumptionFunction() instanceof LogitTechnologyChoiceConsumptionFunction) {
                LogitTechnologyChoice techChoice = ((LogitTechnologyChoiceConsumptionFunction) aa.getConsumptionFunction()).myTechnologyChoice;
                techChoice.checkAndLogRatios(aa.name, maxErrorTermSizeProduction, maxErrorTermSizeConsumption, a);
            }
            // Don't have to log ratios or do setup here for LogitTechnologyChoice, because the consumption function and production functions are integrated together and we already did the consumption function
            
            aa.getProductionFunction().doFinalSetupAndSetCommodityOrder(Commodity.getAllCommodities());
        }
        getTableDataSetCollection().addTableDataSet(maxErrorTermSizeConsumption);
        getTableDataSetCollection().addTableDataSet(maxErrorTermSizeProduction);
        getTableDataSetCollection().flushAndForget(maxErrorTermSizeConsumption);
        getTableDataSetCollection().flushAndForget(maxErrorTermSizeProduction);
    }

    public final void doInputProcessing() {
        boolean updateConstructionSizeTerms = ResourceUtil.getBooleanProperty(
                aaRb, "aa.updateConstructionSizeTerms", false);
        if(updateConstructionSizeTerms) {
            updateConstructionSizeTerms();
        }
        boolean xVectorConstants = ResourceUtil.getBooleanProperty(aaRb,  "aa.xVectorConstants", false);
        if(xVectorConstants) {
        	updateZoneConstantsAndSizeTerms();
        }
        // FIXME change  updateZoneConstantsAndSizeTerms to also support construction size terms
        if (xVectorConstants && updateConstructionSizeTerms) {
        	String msg = "PECAS does not yet support both aa.updateConstructionSizeTerms and aa.xVectorConstants in the same model";
        	logger.fatal(msg);
        	throw new RuntimeException(msg);
        }
        doProjectSpecificInputProcessing();
    }
	
    public void doProjectSpecificInputProcessing() {
    
    }
    
   
    protected void updateZoneConstantsAndSizeTerms() {
    	TableDataSetCollection baseData = getTableDataSetCollection("aa.base.data", "aa.base.data");
    	// check for xVectorCoefficients
		TableDataSet xVectorCoefficients = baseData.getTableDataSet("XVectorCoefficients");
    	// TODO give the option of having different coefficients for different years
   		/* TableDataSet xVectorCoefficients = loadTableDataSet("XVectorCoefficients", "aa.previous.data", false);
		if (xVectorCoefficients == null) {
		   xVectorCoefficients = loadTableDataSet("XVectorCoefficients","aa.base.data");
		} */
		if (xVectorCoefficients == null) {
			logger.info("No XVectorCoefficients table, not applying XVector adjustments to activity zonal constants");
		} else {
			TableDataSetIndex xVectorCoefficientsIndex = baseData.getTableDataSetIndex("XVectorCoefficients", new String[] {"Activity"}, new String[0]);
			int xVectorCoefficientInfluenceNameColumn = xVectorCoefficients.checkColumnPosition("XVectorName");
			int xVectorCoefficientColumn = xVectorCoefficients.checkColumnPosition("Coefficient");
	    	TableDataSetCollection previousData = getTableDataSetCollection("aa.previous.data", "aa.current.data");
	    	// load previous years X vector amounts
	    	TableDataSet oldXVector = previousData.getTableDataSet("XVector");
	    	// load current year X vector amounts
	    	TableDataSetCollection currentData = getTableDataSetCollection("aa.current.data", "aa.current.data");
	    	TableDataSet newXVector = currentData.getTableDataSet("XVector");
	    	// load previous years constants from last year's ActivityLocations.csv
	    	TableDataSet oldZoneValues = previousData.getTableDataSet("ActivityLocations");

	    	TableDataSetIndex oldXVectorIndex = previousData.getTableDataSetIndex("XVector", new String[]{"Activity"}, new String[] {"ZoneNumber"});
	    	TableDataSetIndex oldZoneValuesIndex = previousData.getTableDataSetIndex("ActivityLocations", new String[] {"Activity"}, new String[] {"ZoneNumber"});

	    	int newXVectorZoneColumn = newXVector.checkColumnPosition("ZoneNumber");
	    	int newXVectorBasisColumn = newXVector.checkColumnPosition("BaseConstant");
	    	int newXVectorActivityColumn = newXVector.checkColumnPosition("Activity");
	    	int zoneValuesConstantColumn = oldZoneValues.checkColumnPosition("ZoneConstant");
	    	// TODO should we also modify the ZoneConstantForExchangeSize ???
	    	int[] zoneOnly = new int[1];
	    	String[] activityOnly = new String[1];
	    	
	    	// Place to store new constants temporarily until we're all done and ready to write them out
	    	TreeMap<String, Map<Integer, Double>> newConstants  = new TreeMap<String, Map<Integer, Double>>();
	    	
	    	for (int xvRow=1;xvRow<=newXVector.getRowCount();xvRow++) {
	    		double baseConstant = 0;
	    		String baseConstantBasisEntry = newXVector.getStringValueAt(xvRow, newXVectorBasisColumn);
	    		
	    		// The Base Constant can be the Previous value ("Previous"), the previous value from another zone ("Znnnn") or can be specified numerically.
	    		Integer zoneForBaseConstant  = null;
    			activityOnly[0] = newXVector.getStringValueAt(xvRow, newXVectorActivityColumn);
    			int[] influences = xVectorCoefficientsIndex.getRowNumbers(activityOnly, new int[0]);
	    		if (baseConstantBasisEntry.equalsIgnoreCase("Previous")) {
	    			zoneForBaseConstant = (int) newXVector.getValueAt(xvRow, newXVectorZoneColumn );
	    		}
	    		if (baseConstantBasisEntry.startsWith("Z")) {
	    			try {
	    				zoneForBaseConstant = Integer.valueOf(baseConstantBasisEntry.substring(1));
	    			} catch (NumberFormatException e) {
	    				String msg = "Can't interpret entry "+baseConstantBasisEntry+" as another zone number";
	    				logger.fatal(msg);
	    				throw new RuntimeException(msg);
	    			}
	    		}
	    		if (zoneForBaseConstant == null) {
	    			try {
	    				baseConstant = Double.valueOf(baseConstantBasisEntry);
	    			} catch (NumberFormatException e) {
	    				String msg = "Can't interpret entry "+baseConstantBasisEntry+" as a numeric base constant";
	    				logger.fatal(msg);
	    				throw new RuntimeException(msg);
	    			}
	    		} else {
	    			zoneOnly[0] = zoneForBaseConstant;
	    			int[] oldXVectorRow = oldXVectorIndex.getRowNumbers(activityOnly, zoneOnly);
	    			if (oldXVectorRow.length != 1) {
	    				String msg = "Not exactly one row for activity "+activityOnly[0] +" and zone number "+zoneOnly[0]+" in previous year's xVector";
	    				logger.fatal(msg);
	    				throw new RuntimeException(msg);	    				
	    			}
	    			int[] rowForPreviousValue = oldZoneValuesIndex.getRowNumbers(activityOnly, zoneOnly);
	    			if (rowForPreviousValue.length != 1) {
	    				String msg = "Not exactly one row for activity "+activityOnly[0] +" and zone number "+zoneOnly[0]+" in last year's ActivityLocations";
	    				logger.fatal(msg);
	    				throw new RuntimeException(msg);
	    			}
	    			baseConstant = oldZoneValues.getValueAt(rowForPreviousValue[0],  zoneValuesConstantColumn);
	    			for (int row : influences) {
	    				String influence = xVectorCoefficients.getStringValueAt(row, xVectorCoefficientInfluenceNameColumn);
	    				Double influenceCoefficient = (double) xVectorCoefficients.getValueAt(row,  xVectorCoefficientColumn);
	    				double oldXVectorValue = oldXVector.getValueAt(oldXVectorRow[0], influence);
	    				baseConstant -=  influenceCoefficient * oldXVectorValue;
	    			}
	    			
	    		}
	    		
	    		// now we've established our base constant, make sure we're working with our new zone
    			zoneOnly[0] = (int) newXVector.getValueAt(xvRow, newXVectorZoneColumn );
    			// And then establish the new constant by adding in the new XVector effect.
    			double newConstant = baseConstant;
    			for (int row : influences) {
    				String influence = xVectorCoefficients.getStringValueAt(row, xVectorCoefficientInfluenceNameColumn);
    				Double influenceCoefficient = (double) xVectorCoefficients.getValueAt(row,  xVectorCoefficientColumn);
    				double newXVectorValue = newXVector.getValueAt(xvRow, influence);
    				newConstant +=  influenceCoefficient * newXVectorValue;
    			}
    			Map<Integer, Double> values = newConstants.get(activityOnly[0]);
    			if (values == null) {
    				values = new TreeMap<Integer, Double>();
    				newConstants.put(activityOnly[0],  values);
    			}
    			values.put(zoneOnly[0],  newConstant);
	    	}
	    	
	    	// We delay changing the zone values as we don't want them changing under our feet.
	    	for (Entry<String, Map<Integer, Double>> m : newConstants.entrySet()) {
	    		activityOnly[0] = m.getKey();
	    		for (Entry<Integer, Double> zm : m.getValue().entrySet()) {
	    			zoneOnly[0] = zm.getKey();
	    			int[] rowForNewValue = oldZoneValuesIndex.getRowNumbers(activityOnly, zoneOnly);
	    			oldZoneValues.setValueAt(rowForNewValue[0],  zoneValuesConstantColumn,  zm.getValue().floatValue());
	    			if (rowForNewValue.length != 1) {
	    				String msg = "Not exactly one row for activity "+activityOnly[0] +" and zone number "+zoneOnly[0]+" in last year's ActivityLocations";
	    				logger.fatal(msg);
	    				throw new RuntimeException(msg);
	    			}
	    		}

	    	}
		
    
	    			
   	    	// TODO update construction size terms if necessary
			writeActivityZonalValuesWTable(oldZoneValues);

		}
    	// TODO update construction size terms even if no XVector!
		
    }
    
    protected void updateConstructionSizeTerms() {
    	

        TableDataSet zonalValuesTable = getActivityZonalValuesOrActivityLocations();
        
        updateConstructionSizeTerms(zonalValuesTable);
    }
    
    protected void updateConstructionSizeTerms(TableDataSet zonalValuesTable) {
    	
        TableDataSet devEvents = loadTableDataSet("developmentEvents",
                "aa.previous.data", false);
        if (devEvents == null) {
            logger.info("Not updating construction size terms "
                    + "- development events not found");
            return;
        }

        logger.info("Creating ActivitiesZonalValuesW.csv using SD outputs");

        // Correspondence betwen TAZs and LUZs
        TableDataSet tazToLuz = loadTableDataSet("FloorspaceZonesI",
                "aa.reference.data");
        tazToLuz.buildIndex(tazToLuz.getColumnPosition("TAZ"));

        // Correspondence between SD space types and AA space commodities
        TableDataSet spaceTypesI = loadTableDataSet("space_types_i",
                "aa.reference.data");
        spaceTypesI.buildIndex(spaceTypesI.getColumnPosition("space_type_id"));

        // Correspondence between space commodities and construction activities
        TableDataSet commoditiesI = loadTableDataSet("CommoditiesI",
                "aa.reference.data");
        commoditiesI.buildStringIndex(commoditiesI.getColumnPosition("Commodity"));


        Map<String, Map<Integer, Float>> increments = addUpDevelopmentEvents(
                devEvents, spaceTypesI, commoditiesI, tazToLuz);
        
        for(int r = 1; r <= zonalValuesTable.getRowCount(); r++) {
            String activity = zonalValuesTable.getStringValueAt(r, "Activity");
            activity = activity.toLowerCase();
            Map<Integer, Float> actIncrements = increments.get(activity);
            if(actIncrements != null) {
                int zone = (int) zonalValuesTable.getValueAt(r, "ZoneNumber");
                Float amt = actIncrements.get(zone);
                if(amt != null) {
                    zonalValuesTable.setValueAt(r, "Size", amt);
                }
            }
        }
        
        writeActivityZonalValuesWTable(zonalValuesTable);
    }

	/**
	 * @param zonalValuesTable
	 */
	protected void writeActivityZonalValuesWTable(TableDataSet zonalValuesTable) {
		logger.info("Writing out the ActivitiesZonalValuesW.csv file to the current aa directory");
        
        String aaCurrentPath = ResourceUtil.getProperty(aaRb, "aa.current.data");
        CSVFileWriter writer = new CSVFileWriter();
        try {
            writer.writeFile(zonalValuesTable, new File(aaCurrentPath + "ActivitiesZonalValuesW.csv"));
        } catch (IOException e) {
            loggerf.throwFatal(e, e.getMessage());
        }
	}

	/**
	 * @return
	 */
	protected TableDataSet getActivityZonalValuesOrActivityLocations() {
		TableDataSet zonalValuesTable;
        if (ResourceUtil.getBooleanProperty(aaRb,
                "aa.useActivitiesZonalValuesI", true)) {
            logger.info("Checking for ActivitiesZonalValuesI "
                    + "and previous year's ActivityLocations");
            zonalValuesTable = tryLoadingActivityZonalValuesI();
        } else {
            logger.info("Creating ActivitiesZonalValuesW directly from "
                    + "previous year's ActivityLocations");
            zonalValuesTable = loadTableDataSet("ActivityLocations",
                    "aa.previous.data");
        }
		return zonalValuesTable;
	}

    protected TableDataSet tryLoadingActivityZonalValuesI() {
        TableDataSet zonalValuesTable = loadTableDataSet("ActivitiesZonalValuesI",
                "aa.current.data", false);
        if (zonalValuesTable == null) {
        	logger.info("No ActivitiesZonalValuesI.csv file, checking for previous year's ActivityLocations");
            zonalValuesTable = loadTableDataSet("ActivityLocations",
                    "aa.previous.data");

        }
        return zonalValuesTable;

    }

    private Map<String, Map<Integer, Float>> addUpDevelopmentEvents(
            TableDataSet devEvents, TableDataSet spaceTypesI,
            TableDataSet commoditiesI, TableDataSet tazToLuz) {
        Map<String, Map<Integer, Float>> increments = new HashMap<String, Map<Integer, Float>>();
        for (int r = 1; r <= devEvents.getRowCount(); r++) {
            char eventType = devEvents.getStringValueAt(r, "event_type").charAt(0);
            if(eventType == 'A' || eventType == 'C') {
                int sptype = (int) devEvents.getValueAt(r, "new_space_type_id");
                int flrow = spaceTypesI.getIndexedRowNumber(sptype);
                String flcommod = spaceTypesI.getStringValueAt(flrow, "space_type_name");
                int commodrow = commoditiesI.getStringIndexedRowNumber(flcommod);
                String constrAct = commoditiesI.getStringValueAt(commodrow, "ConstructionActivity");
                constrAct = constrAct.toLowerCase();
                
                float amt = devEvents.getValueAt(r, "new_space_quantity");
                if(eventType == 'A') {
                    amt -= devEvents.getValueAt(r, "old_space_quantity");
                }
                
                int taz = (int) devEvents.getValueAt(r, "taz");
                int tazrow = tazToLuz.getIndexedRowNumber(taz);
                int luz = (int) tazToLuz.getValueAt(tazrow, "LUZ");
                
                Map<Integer, Float> actIncrements = increments.get(constrAct);
                if(actIncrements == null) {
                    actIncrements = new HashMap<Integer, Float>();
                    increments.put(constrAct, actIncrements);
                }
                Float curAmt = actIncrements.get(luz);
                if(curAmt == null) {
                    curAmt = 0f;
                }
                actIncrements.put(luz, curAmt + amt);
            }
        }
        return increments;
    }

    protected JDBCTableReader getJDBCTableReader() {
        JDBCTableReader jdbcTableReader = new JDBCTableReader(getJDBCConnection());
        boolean excelInputs = ResourceUtil.getBooleanProperty(aaRb,"aa.excelInputs",false);
        if (excelInputs) {
            jdbcTableReader.setMangleTableNamesForExcel(true);
        }

        return jdbcTableReader;
    }

    private JDBCConnection getJDBCConnection() {
        JDBCConnection jdbcConnection = null;
        String datasourceUrl =
            ResourceUtil.getProperty(aaRb, "aa.jdbcUrl");
        if (datasourceUrl!=null) {
            try {
                jdbcConnection=(JDBCConnection) DriverManager.getConnection(datasourceUrl);
            } catch (SQLException e) {
                logger.fatal("Cannot connect to JDBC connection "+datasourceUrl);
                throw new RuntimeException("Cannot connect to JDBC connection "+datasourceUrl,e);
            }            
        } else {
            String datasourceName =
                    ResourceUtil.checkAndGetProperty(aaRb, "aa.datasource");
            String jdbcDriver =
                    ResourceUtil.checkAndGetProperty(aaRb, "aa.jdbcDriver");
    //                Class.forName(jdbcDriver);
            jdbcConnection = new JDBCConnection(datasourceName, jdbcDriver, "", "");
        }
        return jdbcConnection;
    }

    private void setFloorspaceProportionsForTechnologySizeTerms(
			double[][] floorspaceInventoryByLUZ) {
    	// set up relative size terms, so that sum of size over each floorspace type is 1.0
    	// NOTE not that the sum of sizes in each zone is zero, because then larger zones wouldn't get higher importance at the next level
    	// We do this to avoid making a technology option more attractive overall just because there is more inventory, however
    	// within one particular zone it should be more attractive if there is more inventory
    	
    	// set up the array, probably an more concise way to do this in Java 6 but do it the easy old-fashioned verbose way.
    	double[][] relativeFloorspaceInventoryByLUZ = new double[floorspaceInventoryByLUZ.length][];
    	for (int zone=0;zone<floorspaceInventoryByLUZ.length;zone++) {
    		relativeFloorspaceInventoryByLUZ[zone] = new double[floorspaceInventoryByLUZ[zone].length];
    	}
    	
    	for (int spaceType = 0; spaceType < floorspaceInventoryByLUZ[0].length; spaceType++) {
    			double totalSpace = 0;
    			for (int i = 0;i<floorspaceInventoryByLUZ.length;i++) {
    				totalSpace += floorspaceInventoryByLUZ[i][spaceType];
    			}
    			if (totalSpace >0) {
    				for (int i = 0;i<floorspaceInventoryByLUZ.length;i++) {
    					relativeFloorspaceInventoryByLUZ[i][spaceType] = floorspaceInventoryByLUZ[i][spaceType]/totalSpace;
    				}
    			}
    	}
    	
    	for (ProductionActivity a : ProductionActivity.getAllProductionActivities()) {
    		LogitTechnologyChoiceProductionFunction pf = (LogitTechnologyChoiceProductionFunction) a.getProductionFunction();
    		LogitTechnologyChoice tc = pf.myTechnologyChoice;
    		tc.setFloorspaceProportionsByLUZ(relativeFloorspaceInventoryByLUZ);
    	}
	}

    
    
    /**
     * @param floorspaceInventoryByLUZ 
     *
     */
    protected void recalcActivitySizeTerms(double[][] floorspaceInventoryByLUZ) {
        logger.info("Reading Activity Size Terms");
        TableDataSet activitySizeTermsCalculation = loadTableDataSet("ActivitySizeTermsI","aa.base.data", false);
        if (activitySizeTermsCalculation == null) {
            logger.fatal("No ActivitySizeTermsI table, not recalculating activity size terms from floorspace quantities");
            throw new RuntimeException("No ActivitySizeTermsI table, not recalculating activity size terms from floorspace quantities");
        }
        Set<ProductionActivity> zeroedOutActivities = new HashSet<ProductionActivity>();
        for (int row=1;row<= activitySizeTermsCalculation.getRowCount();row++) {
            String activityName = activitySizeTermsCalculation.getStringValueAt(row,"Activity");
            ProductionActivity a = ProductionActivity.retrieveProductionActivity(activityName);
            if (a==null) {
                logger.error("Bad production activity in zone size term calculation: "+activityName);
                throw new Error("Bad production activity in zone size term calculation: "+activityName);
            }
            if (!zeroedOutActivities.contains(a)) {
                zeroedOutActivities.add(a);
                a.setSizeTermsToZero();
            }
            double weight = activitySizeTermsCalculation.getValueAt(row,"Weight");
            String commodityName = activitySizeTermsCalculation.getStringValueAt(row,"Floorspace");
            Commodity c = Commodity.retrieveCommodity(commodityName);
            if (c==null)  {
                logger.error("Bad commodity name in zone size term calculation: "+commodityName);
                throw new Error("Bad commodity name in zone size term calculation: "+commodityName);
            }
            
            for (AbstractZone z : AbstractZone.getAllZones()) {
            	a.increaseSizeTerm(z.getZoneUserNumber(), weight*floorspaceInventoryByLUZ[z.zoneIndex][c.commodityIndex]);
            }
        }
        
    }

    /**
     * 
     */
    protected void recalcFloorspaceBuyingSizeTerms() {
        logger.info("Reading Floorspace Buying Size Terms");
        TableDataSet floorspaceSizeTermsCalculation = null;
        try {
            floorspaceSizeTermsCalculation = loadTableDataSet("FloorspaceBuyingSizeTermsI","aa.base.data", false);
        } catch (RuntimeException e) {
            logger.fatal("Exception loading floorspace buying size terms "+e);
            throw new RuntimeException("Exception loading floorspace buying size terms",e);
        }
        if (floorspaceSizeTermsCalculation == null) {
            logger.warn("No FloorspaceBuyingSizeTermsI table, not recalculating floorspace buying size terms from floorspace quantities");
            return;
        }
        int scaleColumn = floorspaceSizeTermsCalculation.getColumnPosition("Scale");
        if (scaleColumn == -1) logger.warn("No scale column in FloorspaceBuyingSizeTermI ... assuming all types are scale 1");
        Hashtable<String, ZoneQuantityStorage> floorspaceGroups = new Hashtable<String, ZoneQuantityStorage>();
        for (int row=1;row<= floorspaceSizeTermsCalculation.getRowCount();row++) {
            String groupName = floorspaceSizeTermsCalculation.getStringValueAt(row,"FloorspaceGroup");
            if (!floorspaceGroups.containsKey(groupName)) {
                logger.info("Setting up floorspace group "+groupName);
                ZoneQuantityStorage inv = new ZoneQuantityStorage(groupName);
                floorspaceGroups.put(groupName,inv);
            }
        }
        for (int row=1;row<= floorspaceSizeTermsCalculation.getRowCount();row++) {
            String groupName = floorspaceSizeTermsCalculation.getStringValueAt(row,"FloorspaceGroup");
            ZoneQuantityStorage group = floorspaceGroups.get(groupName);
            String typeName = floorspaceSizeTermsCalculation.getStringValueAt(row,"FloorspaceType");
            float typeScale = (float) 1.0;
            if (scaleColumn!= -1) typeScale = floorspaceSizeTermsCalculation.getValueAt(row,scaleColumn);
            ZoneQuantityStorage type =  floorspaceInventory.get(typeName);
            if (type == null) {
                logger.fatal("invalid floorspace type "+typeName+" in FloorspaceBuyingSizeTermsI");
                throw new RuntimeException("invalid floorspace type "+typeName+" in FloorspaceBuyingSizeTermsI");
            }
            for(Entry<Integer, Double> typeEntry : type.getInventoryMap().entrySet()) {
                Integer alphaZone = typeEntry.getKey();
                group.increaseQuantityForZone(alphaZone.intValue(),(typeEntry.getValue()).doubleValue()*typeScale);
            }
//            for (int z=0;z<type.inventory.length;z++) {
//                group.inventory[z] += type.inventory[z];
//            }
        }
        //logger.info("Group inventory is set, now calculating size terms");
        for (int row=1;row<= floorspaceSizeTermsCalculation.getRowCount();row++) {
            String groupName = floorspaceSizeTermsCalculation.getStringValueAt(row,"FloorspaceGroup");
            ZoneQuantityStorage group = null;
            group = floorspaceGroups.get(groupName);
            String typeName = floorspaceSizeTermsCalculation.getStringValueAt(row,"FloorspaceType");
            ZoneQuantityStorage type =  floorspaceInventory.get(typeName);
            float typeScale = (float) 1.0;
            if (scaleColumn!= -1) typeScale = floorspaceSizeTermsCalculation.getValueAt(row,scaleColumn);
            logger.info("Calculating size terms for "+typeName);
            for (int betaZone=0;betaZone < zones.length; betaZone++) {
                int betaZoneUserNumber = zones[betaZone].getZoneUserNumber();
                //logger.info("Doing land use zone (luz) "+betaZoneUserNumber);
                double groupQuantity = 0;
                double typeQuantity = 0;
                for(Map.Entry<Integer, Integer> zoneCrossRef : floorspaceZoneCrossref.entrySet()) {
                    Integer a = zoneCrossRef.getKey();
                    Integer b = zoneCrossRef.getValue();
                    if (b!=null) {
                        if (b.intValue()==betaZoneUserNumber) {
                            groupQuantity += group.getQuantityForZone(a.intValue());
                            typeQuantity += type.getQuantityForZone(a.intValue())*typeScale;
                            if (Double.isNaN(groupQuantity)|| Double.isNaN(typeQuantity)) throw new RuntimeException("NaN floorspace buying size term for zone "+betaZoneUserNumber+" commodity "+type.getFloorspaceType());
                        }
                    }
                }
                double size = typeQuantity;
                if (!(groupName.equalsIgnoreCase("none") || groupName.equals(""))) {
                    size = typeQuantity/groupQuantity;
                    if (groupQuantity == 0) {
                    	if (typeQuantity!=0) {
                    		logger.fatal("Error in size term calculation -- type quantity is zero but the associated group quantity is non-zero");
                    		throw new RuntimeException("Error in size term calculation -- type quantity is zero but the associated group quantity is non-zero");
                    	}
                    	size = 0;
                    }
                }
                Commodity c = type.getFloorspaceType();
                Exchange x = c.getExchange(betaZone);
                x.setBuyingSizeTerm(size);
            }
        }
        //logger.info("Done calculating floorspace buying size terms");
       }

    /**
     * 
     */
    protected void recalcFloorspaceImport() {
        logger.info("Calculating floorspace supply functions");
//        boolean oldWay = false;
        String deltaString = ResourceUtil.getProperty(aaRb,"aa.floorspaceDelta");
        double delta=1;
        double eta=1;
        double p0=0;
        double slope=1;
        double midpoint=0;
        double zeroPrice = 0;
        double exportSlope = 1;
        if (deltaString !=null) {
            logger.fatal("aa.floorspaceDelta is specified in properties file; this is deprecated, please specify FloorspaceSupplyI input table instead");
            throw new RuntimeException("aa.floorspaceDelta is specified in properties file; this is deprecated, please specify FloorspaceSupplyI input table instead");       
        }
        Iterator<ZoneQuantityStorage> it = floorspaceInventory.values().iterator();
        while(it.hasNext()) {
            ZoneQuantityStorage fqs = it.next();
            logger.info("Building floorspace import functions for "+fqs.typeName);
            String[][] floorspaceNames = new String[1][1];
            floorspaceNames[0][0] = fqs.typeName;
            TableDataSetCollection collection = getTableDataSetCollection();
            TableDataSetIndexedValue floorspaceParam = new TableDataSetIndexedValue("FloorspaceSupplyI",new String[] {"Commodity"},new String[0],floorspaceNames,new int[1][0],"SupplyFunctionMidpointFactor");
            floorspaceParam.setErrorOnMissingValues(true);
            midpoint = floorspaceParam.retrieveValue(collection);
            floorspaceParam.setMyFieldName("SupplyFunctionMidpointPrice");
            p0 = floorspaceParam.retrieveValue();
            floorspaceParam.setMyFieldName("SupplyFunctionEta");
            eta = floorspaceParam.retrieveValue();
            floorspaceParam.setMyFieldName("SupplyFunctionDeltaFactor");
            delta = floorspaceParam.retrieveValue();
            floorspaceParam.setMyFieldName("SupplyFunctionSlopeFactor");
            slope = floorspaceParam.retrieveValue();
            floorspaceParam.setMyFieldName("NoQuantityPrice");
            zeroPrice = floorspaceParam.retrieveValue();
            floorspaceParam.setMyFieldName("NoQuantitySlope");
            exportSlope = floorspaceParam.retrieveValue();
            boolean oversupplyFunction = true;
            double oversupplyExponent=2;
            double oversupplyAmount=1000000;
            double oversupplyPrice = 100;
            try {
            	floorspaceParam.setMyFieldName("OversupplyPrice");
            	if (floorspaceParam.hasValidLinks()) {
            		oversupplyPrice=floorspaceParam.retrieveValue();
            	} else {
            		oversupplyFunction=false;
            	}
            	floorspaceParam.setMyFieldName("OversupplyExponent");
            	if (floorspaceParam.hasValidLinks()) {
            		oversupplyExponent=floorspaceParam.retrieveValue();
            	} else {
            		oversupplyFunction=false;
            	}
            	floorspaceParam.setMyFieldName("OversupplyAmount");
            	if (floorspaceParam.hasValidLinks()) {
                	oversupplyAmount=floorspaceParam.retrieveValue();
            	} else {
            		oversupplyFunction=false;
            	}
            	if (oversupplyFunction) {
            		logger.info("Setting up oversupply function for "+fqs.typeName);
            	} else {
            		logger.info("No oversupply functions for "+fqs.typeName);
            	}
            } catch (RuntimeException e) {
            	logger.info("No oversupply function for floorspace type "+fqs.typeName+", value missing", e);
            	oversupplyFunction=false;
            }
            for (int bz = 0; bz<zones.length; bz++) {
                AbstractZone betaZone = zones[bz];
                int betaZoneNumber = betaZone.getZoneUserNumber();
                double quantityExpected = 0;
                for (Map.Entry<Integer, Double> entry : fqs.getInventoryMap().entrySet()) {
                    Integer alphaZoneInteger = entry.getKey();
                    Integer betaZoneInteger = (Integer) floorspaceZoneCrossref.get(alphaZoneInteger);
                    if (betaZoneInteger != null) {
                        if (betaZoneInteger.intValue() == betaZoneNumber) {
                            quantityExpected += (entry.getValue()).doubleValue();
                        }
                    }
                }
                Commodity c = fqs.getFloorspaceType();
                Exchange x = c.getExchange(bz);
                LogisticPlusLinearFunction baseFunction = new LogisticPlusLinearFunction(quantityExpected*midpoint,
                        p0, eta, quantityExpected*delta,quantityExpected*slope);
                if (oversupplyFunction) {
                	x.setImportFunction(new LogisticPlusLinearWithOverrideFunction(baseFunction, oversupplyPrice, oversupplyAmount, oversupplyExponent));
                } else {
                	x.setImportFunction(baseFunction);
                }
                x.setExportFunction(new LogisticPlusLinearFunction(0, zeroPrice, 0, 0, exportSlope));
                
            }
           }
        
    }
    
    public void writeOutputs() {
        writeOutputs(false);
    }
    
    /**
     * @param writeItAll 
     * 
     */
    public void writeOutputs(boolean writeItAll) {
    	if (!rerunForOutputs) {
    		logger.info("Writing ExchangeResults.csv");
    		AAStatusLogger.logText("Writing ExchangeResults.csv");
    		writeExchangeResults(); //write out ExchangeResults.csv (prices of all commodites at all exchanges)
    	}
    	logger.info("Writing ZonalMakeUse.csv");
    	AAStatusLogger.logText("Writing ZonalMakeUse.csv");
    	writeZonalMakeUseCoefficients(); //writes out ZonalMakeUse.csv
    	logger.info("Writing TechnologyChoice.csv");
    	AAStatusLogger.logText("Writing TechnologyChoice.csv");
    	writeTechnologyChoice();
    	if(!rerunForOutputs) {
    		logger.info("Writing ActivityLocations.csv");
    		AAStatusLogger.logText("Writing ActivityLocations.csv");
    		writeLocationTable("ActivityLocations");
    	}
    	writeDetailedCommodityOutputs();
    	logger.info("Writing CommodityZUtilities.csv");
    	AAStatusLogger.logText("Writing CommodityZUtilities.csv");
    	zonalMakeUseCoefficients=null; // don't need this anymore, free the memory
    	writeZUtilitiesTable(); //writes out CommodityZUtilities.csv
    	logger.info("Writing ActivitySummary.csv");
    	AAStatusLogger.logText("Writing ActivitySummary.csv");
    	writeActivitySummary(); // write out the top level logsums for benefit analysis - ActivitySummary.csv
    	//TODO should have a better method to avoid time consuming output writes on the first round of a constrained run.
    	if ((ResourceUtil.getBooleanProperty(aaRb,"aa.writeFlowMatrices",true)&& writeItAll) || rerunForOutputs ) {
    		logger.info("Writing buying/selling matrices, PctIntrazonalxCommodityxBzone.csv and Histograms.csv");
    		AAStatusLogger.logText("Writing buying/selling matrices, PctIntrazonalxCommodityxBzone.csv and Histograms.csv");
    		writeAllFlowMatrices(ResourceUtil.getBooleanProperty(aaRb,"aa.omxMatrices",false)); //writes out a 'buying_commodityName' and a 'selling_commodityName'
    		// zipped matrix files for each commodity, plus fills the Histograms.csv and the IntrazonalPercentFile
    		//with a rows for buying and selling values for each commmodity.
    	} else {
            logger.info("Writing Histograms.csv");
            AAStatusLogger.logText("Writing Histograms.csv");
            writeAllHistograms();
        }
        TableDataSet moreHistograms = loadTableDataSet("HistogramZoneGroups","aa.base.data", false);
        if (moreHistograms != null) {
        	HashMap<String, List<Integer>> histogramGroups = new HashMap<String, List<Integer>>();
        	for (int row = 1; row < moreHistograms.getRowCount(); row++) {
        		String groupName = moreHistograms.getStringValueAt(row,"Group");
        		if (!histogramGroups.containsKey(groupName)) {
        			histogramGroups.put(groupName, new ArrayList<Integer>());
        		}
        		List<Integer> groupList = histogramGroups.get(groupName);
        		groupList.add((int) moreHistograms.getValueAt(row, "LUZ"));
        	}
        	for (String groupName : histogramGroups.keySet()){
        		writeAllHistograms("Histograms_"+groupName, histogramGroups.get(groupName));
        	}
        }
        if ((ResourceUtil.getBooleanProperty(aaRb,"aa.createCompressedFlowVisuals",true) && writeItAll) || rerunForOutputs ) {
        	writeCompressedFlowVisuals();
        }
        // ENHANCEMENT test this code which is supposed to do a crosstab of zonalMakeUse.
//        if (ResourceUtil.getBooleanProperty(aaRb,"aa.crossTabMakeUse",false)) {
//            TableDataSetCollection col = getTableDataSetCollection("output.data","output.data");
//            TableDataSet crossTabMakeUse = TableDataSetCrosstabber.crossTabDataset(
//                    col,"ZonalMakeUse",new String[] {"Activity","MorU"},"Commodity","Amount");
//            col.addTableDataSet(crossTabMakeUse);
//            col.flush();
//        }
        
    }
    
    private void writeDetailedCommodityOutputs() {
        TAZSplitter splitter = null;
        AlphaToBeta tableA2B = new AlphaToBeta(floorspaceZoneCrossref);
        if (ResourceUtil.getBooleanProperty(aaRb,"aa.useFloorspaceZones", true)) {
            if (ResourceUtil.getBooleanProperty(aaRb,"aa.splitOutputToFloorspaceZones", true)) {
                splitter = new TAZSplitter(tableA2B,floorspaceInventory, maxAlphaZone(), getOutputPath(), zones, zonalMakeUseCoefficients, popSyn);
                ArrayList<String> detailedCommodityList;
                if (Commodity.isDetailedOutputSpecified()) {
                    detailedCommodityList = new ArrayList<>();
                    for (AbstractCommodity ac : Commodity.getAllCommodities()) {
                        Commodity c = (Commodity) ac;
                        if (c.isDetailedOutput()) {
                            detailedCommodityList.add(c.getName());
                        }
                    }
                } else {
                    detailedCommodityList = ResourceUtil.getListWithUserDefinedSeparator(aaRb, "aa.detailedCommodities", ",");
                    for (int i = 0; i < detailedCommodityList.size(); i++) {
                        detailedCommodityList.set(i, detailedCommodityList.get(i).trim());
                    }
                }
                splitter.writeFloorspaceZoneTables(detailedCommodityList);
            }
        }
        if (splitter==null) writeTripMatrices(null, null);
        else writeTripMatrices(splitter.alphaZonalMake,splitter.alphaZonalUse);
        writeProjectSpecificSplitOutputs(splitter);
    }
    
    /**
     * Clusters the flows.
     * 
     * Also try equal weighted clustering? 
     * http://code.google.com/p/ekmeans/
     * 
     */
    private void writeCompressedFlowVisuals() {
    	try {
    		//FlowMatrixCompressor me = new FlowMatrixCompressor();
    		float[][] coordsArray = new float[zones.length][3];
    		int i = 0;
    		double xmin = Double.POSITIVE_INFINITY;
    		double xmax = Double.NEGATIVE_INFINITY;
    		double ymin = xmin;
    		double ymax = xmax;
    		for (PECASZone zone : zones) {
    			coordsArray[i][0] = zone.getZoneUserNumber();
    			coordsArray[i][1] = zone.getXCoordinate();
    			xmax = Math.max(xmax,  zone.getXCoordinate());
    			xmin = Math.min(xmin,  zone.getXCoordinate());
    			coordsArray[i][2] = zone.getYCoordinate();
    			ymax = Math.max(ymax,  zone.getYCoordinate());
    			ymin = Math.min(ymin, zone.getYCoordinate());
    			i++;
    		}

    		int[] clusterCounts = ResourceUtil.getIntValuesFromString(ResourceUtil.checkAndGetProperty(aaRb, "aa.flowClusterCounts"));
    		int numClusterIterations = ResourceUtil.getIntegerProperty(aaRb,  "aa.flowClusterIterations", 30);
    		int numThreads = ResourceUtil.getIntegerProperty(aaRb,  "aa.flowClusterThreads", Commodity.getAllCommodities().size());
    		String projection = ResourceUtil.getProperty(aaRb, "aa.flowClusterProjection", null);
    		projection = projection == null? "": " srsName=\"" + projection + "\"";
    		ExecutorService myThreadPool = Executors.newFixedThreadPool(numThreads /*, Executors.privilegedThreadFactory()*/ );  

    		for (int nClusters : clusterCounts) {
        		ArrayList<OneMatrixCompressor> clusterers = new ArrayList<OneMatrixCompressor>();
        		Iterator<AbstractCommodity> comIt = Commodity.getAllCommodities().iterator();
    			while (comIt.hasNext() /*&& count <4*/) {
    				Commodity com = (Commodity) comIt.next(); 
    				if (com.exchangeType == 'p' || com.exchangeType == 'a' ) {
    					//count++;
    					Matrix b = com.getBuyingFlowMatrix();
    					TableDataSet coords = TableDataSet.create(coordsArray);
    					OneMatrixCompressor c = new OneMatrixCompressor(com, 'b', nClusters, numClusterIterations, b, coords);
    					clusterers.add(c);
    					myThreadPool.execute(c);
    				}
    				if (com.exchangeType == 'c' || com.exchangeType == 'a' ) {
    					//count++;
    					TableDataSet coords = TableDataSet.create(coordsArray);
    					Matrix b = com.getSellingFlowMatrix();
    					OneMatrixCompressor c = new OneMatrixCompressor(com, 's', nClusters, numClusterIterations, b, coords);
    					clusterers.add(c);
    					myThreadPool.execute(c);
    				}
    			}
	    		BufferedWriter os = new BufferedWriter(new FileWriter(getOutputPath()
	    				+ "ClusteredFlows_"+nClusters+".gml"));
	    		os.write(			
	    				"<?xml version=\"1.0\" encoding=\"utf-8\" ?>\n<Root>\n");
	    		int featureNumber = 0;
	    		for (OneMatrixCompressor c : clusterers) {
	    			String fixedName = c.c.getName().replace(' ', '_');
	    			try {
	    				// write out  flows, note c.worked is a FutureObject so this will block until the compression is done
	    				if (c.worked.getValue() instanceof Boolean) {
	    					Collection<FourCoordinatesI> results = c.clustered;
	    					if (results == null) {
	    					    logger.warn("No flows for " + c.c.getName() + ", not clustering");
	    					} else {
    	    					double totalWeight = 0;
    	    					for (FourCoordinatesI r : results)
    	    					    totalWeight += r.getWeight();
    	    					os.write(			
    	    							"<ogr:FeatureCollection\n"+
    	    									"    xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n"+
    	    									"    xsi:schemaLocation=\"http://ho.hbaspecto.com http://ho.hbaspecto.com/public/econflows.xsd\""+
    	    									"    xmlns:ogr=\"http://ogr.maptools.org/\"\n"+
    	    							"    xmlns:gml=\"http://www.opengis.net/gml\">\n");
    	    					os.write(  
    	    							"<gml:boundedBy>\n"+
    	    									"  <gml:Box>\n"+
    	    									"	<gml:coord><gml:X>"+xmin+"</gml:X><gml:Y>"+ymin+"</gml:Y></gml:coord>\n"+
    	    									"   <gml:coord><gml:X>"+xmax+"</gml:X><gml:Y>"+ymax+"</gml:Y></gml:coord>\n"+
    	    							" </gml:Box>\n</gml:boundedBy>\n");
    	    					for (FourCoordinatesI r : results) {
    	    						os.write("       <gml:featureMember>\n");
    	    						os.write("        <ogr:"+c.direction+"_"+fixedName+"_"+c.count+" fid=\"F"+featureNumber+"\">\n");
    	    						featureNumber++;
    	    						os.write("         <ogr:geometryProperty>\n");
    	    						os.write("           <gml:LineString" + projection + "><gml:coordinates>"+r.getOrigX()+","+r.getOrigY()+" "+r.getDestX()+","+r.getDestY()+"</gml:coordinates></gml:LineString>\n");
    	    						//os.write("       <feature:geometry>\n");
    	    						os.write("         </ogr:geometryProperty>\n");
    	    						os.write("         <ogr:COMMODITY>"+c.c.getName()+"</ogr:COMMODITY>\n");
    	    						os.write("         <ogr:DIRECTION>"+c.direction+"</ogr:DIRECTION>\n");
    	    						os.write("         <ogr:CLUSTERCOUNT>"+c.count+"</ogr:CLUSTERCOUNT>\n");
    	    						os.write("         <ogr:VALUE>"+r.getWeight()+"</ogr:VALUE>\n");
    	    						os.write("         <ogr:PERCENTVALUE>"+r.getWeight() / totalWeight+"</ogr:PERCENTVALUE>\n");
    	    						os.write("        </ogr:"+c.direction+"_"+fixedName+"_"+c.count+">\n");
    	    						os.write("       </gml:featureMember>\n");
    	    					}
    	    					os.write("</ogr:FeatureCollection>\n");
	    					}
	    				} else {
	    					logger.error("Problem creating clusters for "+c.direction+" and Commodity "+c.c.getName(),((Exception) c.worked.getValue()));
	    				}
	    			} catch (Exception e) {
	    				logger.error("Problem writing compressed flows for commodity "+c.c.getName(), e);
	    			} finally {
	    				//os.write("</feature:feature>\n");
	    			}
	    		}
	    		os.write("</Root>\n");
	    		os.close();
    		}
    		myThreadPool.shutdown();
    	} catch (Exception e) {
    		logger.error("Problem writing compressed flow visualizations", e);
    	}
    }

	protected void writeProjectSpecificSplitOutputs(TAZSplitter splitter) {
		// Nothing by default
		
	}

	protected abstract void writeTechnologyChoice();

	public void writeTripMatrices(StringIndexedNDimensionalMatrix aZoneMake,
		StringIndexedNDimensionalMatrix aZoneUse) {
        if (ResourceUtil.getBooleanProperty(aaRb, "aa.makeTripMatrices", false)) {
            logger.info("Writing TripMatrices");
            
            double useRoundedAt = ResourceUtil.getDoubleProperty(aaRb, "aa.roundExpectedValueForTripsIfHigherThan", Double.POSITIVE_INFINITY);
            double useNormalAt = ResourceUtil.getDoubleProperty(aaRb, "aa.useNormalPoissonApproximationIfHigherThan", 10.0);
            
        	TableDataSet tripCalculationSpecification = loadDataSetFromYearOrAllYears("TripCalculationsI");
        	boolean usePoissonDistribution = ResourceUtil.getBooleanProperty(aaRb, "aa.poissonTrips", true);
        	TripCalculator myTripCalculator = new TripCalculator(useNormalAt, useRoundedAt);
        	Matrix[] tripArrays = myTripCalculator.calculateLuzTrips(tripCalculationSpecification);
        	
        	// build TAZ arrays
        	if (ResourceUtil.getBooleanProperty(aaRb, "aa.TAZTripList", false)) {
        		if (aZoneMake==null || aZoneUse == null) {
        			String msg = "Can't build TAZTripList without splitting quantities to TAZ; set aa.splitOutputToFloorspaceZones to true if aa.TAZTripList is true";
        			logger.error(msg);
        		} else {
					SparseMatrix[] tazTrips = myTripCalculator.sampleTazTrips(
	        				tripArrays, floorspaceZoneCrossref,
	        				tripCalculationSpecification,
	        				aZoneMake, aZoneUse, maxAlphaZone);
					if (ResourceUtil.getBooleanProperty(aaRb, "aa.writeEmme2FormatTazTrips", true)) {
						for (int i=0;i<tazTrips.length;i++){
							int alphaCount = floorspaceZoneCrossref.getAlphaExternals0Based().length;
							Matrix m = new Matrix(alphaCount, alphaCount);
							m.setExternalNumbers(floorspaceZoneCrossref.getAlphaExternals1Based());
							for (MatrixEntry e : tazTrips[i]) {
								m.setValueAt(e.row(), e.column(), (float) e.get());
							}
							Emme2311MatrixWriter writer = new Emme2311MatrixWriter(new File(getOutputPath()+"TAZxTAZ"+myTripCalculator.tripTypes[i]+".csv"));
							writer.writeMatrix(m);
						}
					}
					
					try {
			            BufferedWriter tazTripFile = new BufferedWriter(new FileWriter(getOutputPath() + "tazTrips.csv"));
			            tazTripFile.write("origin,destination,type\n");
						for (int i=0;i<tazTrips.length;i++){
							for (MatrixEntry e : tazTrips[i]) {
								// one row for each trip
								for (int j=0;j<e.get();j++) {
									tazTripFile.write(e.row()+","+e.column()+","+myTripCalculator.tripTypes[i]+"\n");
								}
							}
							
						}
						tazTripFile.close();
			        } catch (IOException e) {
			        	logger.error("Error writing trip list ",e);
			        }
        		}
        	}
        	
        	if (usePoissonDistribution) tripArrays = myTripCalculator.applyPoissonDistribution();
        	MatrixWriter writer = new CSVMatrixWriter(new File(getOutputPath()+"LUZTrips.csv"));
        	writer.writeMatrices(myTripCalculator.getTripArrayNames(), tripArrays);
        	
        }
    }
    
    /* Writes out ZonalMakeUse.csv 
     * 
     */
    public void writeZonalMakeUseCoefficients() {
        boolean ascii = ResourceUtil.getBooleanProperty(aaRb,"aa.writeAsciiZonalMakeUse", true);
        boolean binary = ResourceUtil.getBooleanProperty(aaRb,"aa.writeBinaryZonalMakeUse",false);
        if (rerunForOutputs) {
        	ascii = true;
        	binary = true;
        }
        boolean stringsInZonalMakeUse = ResourceUtil.getBooleanProperty(aaRb,"aa.stringsInZonalMakeUse",false);
        if (!stringsInZonalMakeUse) writeCommodityAndActivityFiles();
        zonalMakeUseCoefficients = null;
        StringIndexedNDimensionalMatrix utilities = null;
        StringIndexedNDimensionalMatrix quantities = null;
       // create this whether or not we're writing out the binary files; need it later (otherwise we could do "if (binary)" { up here)
        int[] shape = new int[4];
        String[] columnNames = {"Activity","ZoneNumber","Commodity","MorU"};
        shape[3] = 2; // "M" or "U"
        shape[1] = AbstractZone.getAllZones().length;
        shape[2]= AbstractCommodity.getAllCommodities().size();
        shape[0] = ProductionActivity.getAllProductionActivities().size();
        zonalMakeUseCoefficients = new StringIndexedNDimensionalMatrix("Coefficient",4,shape,columnNames);
        zonalMakeUseCoefficients.setAddKeysOnTheFly(true);
        if (binary) {
            utilities = new StringIndexedNDimensionalMatrix("Utility",4,shape,columnNames);
            utilities.setAddKeysOnTheFly(true);
            quantities = new StringIndexedNDimensionalMatrix("Amount",4,shape,columnNames);
            quantities.setAddKeysOnTheFly(true);
            
        }
        BufferedWriter zMakeUseFile = null;
        try {
            if (ascii) {
                zMakeUseFile = new BufferedWriter(new FileWriter(getOutputPath() + "ZonalMakeUse.csv"));
                zMakeUseFile.write("Activity,ZoneNumber,Commodity,MorU,Coefficient,Utility,Amount\n");
            }
            BufferedWriter aggregateMakeUseFile = new BufferedWriter(new FileWriter(getOutputPath() + "MakeUse.csv"));
            aggregateMakeUseFile.write("Activity,Commodity,MorU,Coefficient,StdDev,Amount\n");
            for(ProductionActivity p : ProductionActivity.getAllProductionActivities()) {
                ConsumptionFunction cf = p.getConsumptionFunction();
                ProductionFunction pf = p.getProductionFunction();
                
                // also total up amounts made and used by each activity
                double[] madeAmounts = new double[Commodity.getAllCommodities().size()];
                double[] usedAmounts = new double[madeAmounts.length];
                
                // track these for calculating standard deviation, "Rapid Calculation Method for Standard Deviation" http://en.wikipedia.org/wiki/Standard_deviation
                double[] madeA = new double[madeAmounts.length];
                double[] usedA = new double[usedAmounts.length];
                double[] madeQ = new double[madeAmounts.length];
                double[] usedQ = new double[usedAmounts.length];
                
                // count non zero zones for mean and std dev of coeffs
                double totalQuantityForWeights = 0;
                
                // Go through all the zones now
                for (int z = 0; z < p.getMyDistribution().length; z++) {
                	// We need to calculate the buying and selling utilities so we can calculate TechnologyChoice
                    double[] buyingZUtilities = new double[cf.size()];
                    for (int c = 0; c < cf.size(); c++) {
                        AbstractCommodity com = cf.commodityAt(c);
                        if (com == null) {
                            buyingZUtilities[c] = 0;
                        } else {
                            buyingZUtilities[c] = com.calcZUtility(zones[z], false);
                        }
                    } //CUSellc,z and CUBuyc,z have now been calculated for the commodites made or used by the activity
                    double[] sellingZUtilities = new double[pf.size()];
                    for (int c = 0; c < pf.size(); c++) {
                        AbstractCommodity com = pf.commodityAt(c);
                        if (com == null) {
                            sellingZUtilities[c] = 0;
                        } else {
                            sellingZUtilities[c] = com.calcZUtility(zones[z], true);
                        }
                    } //CUSellc,z and CUBuyc,z have now been calculated for the commodites made or used by the activity
                    
                    double[] consumptionAmounts=null;
                    double[] productionAmounts=null;
                    double activityAmount = p.getMyDistribution()[z].getQuantity();
					try {
						consumptionAmounts = cf.calcAmounts(buyingZUtilities, sellingZUtilities, z);
						productionAmounts = pf.calcAmounts(buyingZUtilities, sellingZUtilities, z);
					} catch (NoAlternativeAvailable e) {
						if (activityAmount!=0) {
							logger.error(p.getMyDistribution()[z]+" has no valid production/consumption functions but has quantity "+activityAmount);
						} //else {
							consumptionAmounts = new double[buyingZUtilities.length];
							productionAmounts = new double[sellingZUtilities.length];
						//}
					}

                    String[] indices = new String[4];
                    indices[0] = p.name;
                    indices[1] = Integer.toString(zones[z].getZoneUserNumber());
                    indices[3] = "U";
                    
                    
                    for (int c = 0; c < cf.size(); c++) {
                        AbstractCommodity com = cf.commodityAt(c);
                        if (com!= null) {
                        	if (ascii) {
                        		if (activityAmount*consumptionAmounts[c] != 0) {
                        			if (stringsInZonalMakeUse) zMakeUseFile.write(p.name+",");
                        			else zMakeUseFile.write(p.getNumber()+",");
                        			zMakeUseFile.write(zones[z].getZoneUserNumber()+",");
                        			if (stringsInZonalMakeUse) zMakeUseFile.write(com.getName()+",");
                        			else zMakeUseFile.write(com.commodityNumber+",");
                        			zMakeUseFile.write("U,");
                        			zMakeUseFile.write(consumptionAmounts[c]+",");
                        			zMakeUseFile.write(buyingZUtilities[c]+",");
                        			zMakeUseFile.write(activityAmount*consumptionAmounts[c]+"\n");
                        		}
                        	}
                            indices[2] = com.getName();
                            zonalMakeUseCoefficients.setValue((float) consumptionAmounts[c],indices);
                            if (binary) {
                                utilities.setValue((float) buyingZUtilities[c],indices);
                                quantities.setValue((float) (activityAmount*consumptionAmounts[c]),indices);
                            }
                            
                            // stuff for average and standard deviation
                            double usedAmount = activityAmount*consumptionAmounts[c];
                            usedAmounts[com.commodityIndex]+=usedAmount;
                            if (activityAmount!=0) {
                            		double oldUsedA = usedA[com.commodityIndex];
                            		usedA[com.commodityIndex]= oldUsedA + (consumptionAmounts[c]-oldUsedA)*(activityAmount)/(activityAmount + totalQuantityForWeights);
                            		usedQ[com.commodityIndex] = usedQ[com.commodityIndex] + activityAmount*(consumptionAmounts[c]-oldUsedA)*(consumptionAmounts[c]-usedA[com.commodityIndex]);
                            }
                        }
                    } 
                    indices[3] = "M";
                    for (int c = 0; c < pf.size(); c++) {
                        AbstractCommodity com = pf.commodityAt(c);
                        if (com!= null) {
                            if (ascii) {
                            	if (activityAmount*productionAmounts[c] != 0) {
                            		if (stringsInZonalMakeUse) zMakeUseFile.write(p.name+",");
                            		else zMakeUseFile.write(p.getNumber()+",");
                            		zMakeUseFile.write(zones[z].getZoneUserNumber()+",");
                            		if (stringsInZonalMakeUse) zMakeUseFile.write(com.getName()+",");
                            		else zMakeUseFile.write(com.commodityNumber+",");
                            		zMakeUseFile.write("M,");
                            		zMakeUseFile.write(productionAmounts[c]+",");
                            		zMakeUseFile.write(sellingZUtilities[c]+",");
                            		zMakeUseFile.write(activityAmount*productionAmounts[c]+"\n");
                            	}
                            }
                            indices[2] = com.getName();
                            zonalMakeUseCoefficients.setValue((float) productionAmounts[c],indices);
                            if (binary) {
                                utilities.setValue((float) sellingZUtilities[c],indices);
                                quantities.setValue((float) (activityAmount*productionAmounts[c]),indices);
                            }
                            
                            // stuff for average and standard deviation
                            double madeAmount = activityAmount*productionAmounts[c];
                            madeAmounts[com.commodityIndex]+=madeAmount;
                            if (activityAmount!=0) {
                            		double oldMadeA = madeA[com.commodityIndex];
                            		madeA[com.commodityIndex]= oldMadeA + (productionAmounts[c]-oldMadeA)*(activityAmount)/(activityAmount + totalQuantityForWeights);
                            		madeQ[com.commodityIndex] = madeQ[com.commodityIndex] + activityAmount*(productionAmounts[c]-oldMadeA)*(productionAmounts[c]-madeA[com.commodityIndex]);
                            }
                        }
                    }
                    totalQuantityForWeights += activityAmount;
                } // end of zone loop
                
                for(AbstractCommodity ac : Commodity.getAllCommodities()) {
                	Commodity c = (Commodity) ac;
                	double madeSD = Math.sqrt(madeQ[c.commodityIndex]/totalQuantityForWeights);
                	double usedSD = Math.sqrt(usedQ[c.commodityIndex]/totalQuantityForWeights);
                    if (madeSD>0 || madeA[c.commodityIndex]!=0) aggregateMakeUseFile.write(p.name+","+c.name+",M,"+madeA[c.commodityIndex]+","+madeSD+","+madeAmounts[c.commodityIndex]+"\n");
                    if (usedSD>0 || usedA[c.commodityIndex]!=0) aggregateMakeUseFile.write(p.name+","+c.name+",U,"+usedA[c.commodityIndex]+","+usedSD+","+usedAmounts[c.commodityIndex]+"\n");
                }
            } // end of production activity loop
            
            if (ascii) {
                zMakeUseFile.close();
                logger.info("\tZonalMakeUse.csv has been written");
            }
            aggregateMakeUseFile.close();
        } catch (IOException e) {
            logger.fatal("Can't create ZonalMakeUse output file");
            e.printStackTrace();
        } catch (OverflowException e) {
            logger.fatal("Can't create ZonalMakeUse output file");
            e.printStackTrace();
		//} catch (NoAlternativeAvailable e) {
        //    logger.fatal("Can't create ZonalMakeUse output file");
        //    e.printStackTrace();
		}
        if (binary) {
            String filename = getOutputPath() + "ZonalMakeUse.bin";
            if (filename != null) {
                try {
                    java.io.FileOutputStream fos = new java.io.FileOutputStream(filename);
                    java.io.ObjectOutputStream out = new java.io.ObjectOutputStream(fos);
                    out.writeObject(zonalMakeUseCoefficients);
                    out.writeObject(utilities);
                    out.writeObject(quantities);
                    out.flush();
                    out.close();
                } catch (java.io.IOException e) {
                    logger.fatal("Can't write out zonal make use binary file "+e);
                }
            }
        }
    }//end writeZonalMakeUse
    
    private void writeCommodityAndActivityFiles() {
		try {
	        BufferedWriter activitiesFile = new BufferedWriter(new FileWriter(getOutputPath() + "ActivityNumbers.csv"));
	        activitiesFile.write("ActivityNumber,Activity\n");
	        for (ProductionActivity p : ProductionActivity.getAllProductionActivities()) {
	        	activitiesFile.write(p.getNumber()+","+p.name+"\n");
	        }
	        activitiesFile.close();

	        BufferedWriter commoditiesFile = new BufferedWriter(new FileWriter(getOutputPath() + "CommodityNumbers.csv"));
	        commoditiesFile.write("CommodityNumber,Commodity\n");
	        for (AbstractCommodity ac : Commodity.getAllCommodities()) {
	        	Commodity c = (Commodity) ac;
	        	commoditiesFile.write(c.commodityNumber+","+c.name+"\n");
	        }
	        commoditiesFile.close();
		} catch (IOException e) {
			logger.warn("Can't write ActivityNumbers.csv and CommodityNumbers.csv, "+e);
		}
	}
    

    public void writeLocationTable(String tableName) {
        BufferedWriter locationsFile;
        try {
            locationsFile = new BufferedWriter(new FileWriter(getOutputPath() + tableName+".csv"));
            //ENHANCEMENT shall we write out the dollars spent and earned as well as the utility associated with size/variation/transport components?
            if (logitTechnologyChoice) {
                locationsFile.write("Activity,ZoneNumber,Quantity,TechnologyLogsum,SizeUtility,ZoneConstant,Constrained,ConstraintValue,LocationUtility,FullEmbeddedSize,Size,ZoneConstantForExchangeSize\n");
            } else {
                locationsFile.write("Activity,ZoneNumber,Quantity,ProductionUtility,ConsumptionUtility,SizeUtility,ZoneConstant,Constrained,ConstraintValue,LocationUtility\n");
            }
            for(ProductionActivity p : ProductionActivity.getAllProductionActivities()) {
                for (int z = 0; z < p.getMyDistribution().length; z++) {
                    try {
                        locationsFile.write(p.name+",");
                        locationsFile.write(p.getMyDistribution()[z].getMyZone().getZoneUserNumber()+",");
                        locationsFile.write(p.getMyDistribution()[z].getQuantity()+",");
                        p.getMyDistribution()[z].writeLocationUtilityTerms(locationsFile);
                    } catch (IOException e) {
                        logger.fatal("Error adding activity quantity to ActivityLocations table");
                        e.printStackTrace();
                    } catch (ChoiceModelOverflowException e) {
                        logger.fatal("Error adding activity quantity to ActivityLocations table");
                        e.printStackTrace();
                    }
                }
            }
            logger.info("\tActivityLocations.csv has been written");
            locationsFile.close();
        } catch (IOException e) {
            logger.fatal("Can't create location output file");
            e.printStackTrace();
        }
    }
    
    public void writeZUtilitiesTable() {
        BufferedWriter os = null;
        try {
            os = new BufferedWriter(new FileWriter(getOutputPath()
                    + "CommodityZUtilities.csv"));
            boolean writeComponents = ResourceUtil.getBooleanProperty(aaRb,
                    "aa.writeUtilityComponents");
            if (writeComponents) {
                os
                        .write("Commodity,Zone,BuyingOrSelling,Quantity,zUtility,VariationComponent,PriceComponent,SizeComponent,TransportComponent1,TransportComponent2,TransportComponent3,TransportComponent4\n");
            } else {
                os.write("Commodity,Zone,BuyingOrSelling,Quantity,zUtility\n");
            }
            for(AbstractCommodity ac : Commodity.getAllCommodities()) {
                Commodity c = (Commodity) ac;
                for (int b = 0; b < 2; b++) {
                    Iterator<CommodityZUtility> it2;
                    if (b == 0) {
                        it2 = c.getBuyingUtilitiesIterator();
                    } else {
                        it2 = c.getSellingUtilitiesIterator();
                    }
                    while (it2.hasNext()) {
                    	CommodityZUtility czu = it2.next();
                    	try {
                    		os.write(czu.myCommodity.toString() + ",");
                    		os.write(czu.myLuz.getZoneUserNumber() + ",");
                    		if (czu instanceof BuyingZUtility) {
                    			os.write("B,");
                    		}
                    		if (czu instanceof SellingZUtility) {
                    			os.write("S,");
                    		}
                    		os.write(czu.getQuantity() + ",");
                    		// ENHANCEMENT these should probably go into a separate table so that we don't have repeating fields and so that the database is normalized
                    		if (writeComponents) {
                    			os.write(czu.getUtility(czu.getLastHigherLevelDispersionParameter())+ ",");
                    			double[] components = czu.getUtilityComponents(czu.getLastHigherLevelDispersionParameter());
                    			for (int i = 0; i < 7; i++) {
                    				if (components.length >i) os.write(String.valueOf(components[i]));
                    				if (i<6) os.write(",");
                    			}
                    			os.write("\n");
                    		} else {
                    			os.write(czu.getUtility(czu.getLastHigherLevelDispersionParameter())+ "\n");
                    		}
                    	} catch (IOException e) {
                    		logger.fatal("unable to write zUtility to file");
                    		e.printStackTrace();
                    	} catch (ChoiceModelOverflowException e) {
                    		logger
                    		.error("Overflow exception in calculating utility for CommodityZUtilityoutput "
                    				+ this);
                    	}
                    }
                }
            }
            logger.info("\tCommodityZUtilities.csv has been written");
            os.close();
        } catch (IOException e) {
            logger.fatal("Can't create CommodityZUtilities output file");
            e.printStackTrace();
        }
    }//end writeZUtilities()
    
    public void writeExchangeResults() {

        //boolean writeDerivatives = ResourceUtil.getBooleanProperty(aaRb,"aa.writeExchangeDerivatives", false);
        //boolean writeSizeTerms = ResourceUtil.getBooleanProperty(aaRb, "calculateExchangeSizes", false);
        BufferedWriter exchangeResults = null;
        int i=-1;
        while (exchangeResults == null && i<200) {
            try {
                i++;
                if (i==0) exchangeResults = new BufferedWriter(new FileWriter(getOutputPath() + "ExchangeResults.csv"));
                else exchangeResults = new BufferedWriter(new FileWriter(getOutputPath() + "ExchangeResults-"+i+".csv"));
                exchangeResults.write("Commodity,ZoneNumber,Demand,InternalBought,Exports,Supply,InternalSold,Imports,Surplus,Price");
                exchangeResults.write(",BuyingSizeTerm,SellingSizeTerm");
                exchangeResults.write(",Derivative");
                exchangeResults.write("\n");
            } catch (IOException e) {
                exchangeResults = null;
                logger.warn("Can't create ExchangeResults version "+i+", trying another version");
            }
        }
        BufferedWriter exchangeResultsTotals = null;
        try {
            exchangeResultsTotals = new BufferedWriter(new FileWriter(getOutputPath() + "ExchangeResultsTotals.csv"));
            exchangeResultsTotals.write("Commodity,Demand,InternalBought,Exports,Supply,InternalSold,Imports,RMSSurplus,AveragePrice\n");
        } catch (IOException e) {
            exchangeResultsTotals = null;
            logger.warn("Can't create ExchangeResultsTotals");
        }
        if (exchangeResults != null) { 
            for (AbstractCommodity ac : Commodity.getAllCommodities()) {
                Commodity c = (Commodity) ac;
                double internalBought = 0;
                double exports = 0;
                double internalSold = 0;
                double imports = 0;
                double price =0;
                double msSurplus = 0;
                int numberOfExchanges = c.getAllExchanges().size();
                for (Exchange ex : c.getAllExchanges()) {
                    try {
                        exchangeResults.write(ex.myCommodity.name + "," + ex.exchangeLocationUserID + ",");
                        double surplus = ex.exchangeSurplus();
                        double[] importExport = ex.importsAndExports(ex.getPrice());
                        exchangeResults.write((ex.boughtTotal()+importExport[1])+","+ex.boughtTotal() + ","+ importExport[1] + "," +(ex.soldTotal()+importExport[0])+","+ ex.soldTotal()+","+importExport[0] + "," + surplus + "," + ex.getPrice() );
                        exchangeResults.write(","+ex.getBuyingSizeTerm()+","+ex.getSellingSizeTerm());
                        exchangeResults.write(","+ex.exchangeDerivative());
                        if (ex.getImportFunction() instanceof LogisticPlusLinearWithOverrideFunction) {
                        	BufferedWriter overrideFile = getOverrideFile();
                        	if (overrideFile!=null) ((LogisticPlusLinearWithOverrideFunction) ex.getImportFunction()).writeOverride(overrideFile, ex.exchangeLocationUserID, ex.myCommodity.getName(), ex.getPrice());
                        	((LogisticPlusLinearWithOverrideFunction) ex.getImportFunction()).logOverrides(ex.getPrice(),ex.toString());
                        }
                        exchangeResults.write("\n");
                        internalBought += ex.boughtTotal();
                        exports+=importExport[1];
                        internalSold += ex.soldTotal();
                        imports+=importExport[0];
                        price += ex.getPrice()/numberOfExchanges;
                        msSurplus += surplus*surplus/numberOfExchanges;
                    } catch (IOException e) {
                     logger.error("Error adding exchange " + this + " to table");
                     e.printStackTrace();
                    }
    
                }
                if (exchangeResultsTotals!=null) {
                    try {
                        exchangeResultsTotals.write(c.name + "," + (internalBought+exports) +","+internalBought+","+exports+","+(internalSold+imports)+","+internalSold+","+imports+","+Math.sqrt(msSurplus)+","+price+"\n");
                    } catch (IOException e) {
                        logger.error("Error writing value to ExchangeResultsTotals file");
                        e.printStackTrace();
                    }
                }
            }
            logger.info("\tExchangeResults.csv has been written");
            try {
            	exchangeResults.close();
            } catch (IOException e) {
            	logger.error("Can't close ExchangeResults output file");
            	e.printStackTrace();
            }
            if (overRideFile!=null) {
            	try {
            		overRideFile.close();
            	} catch (IOException e) {
            		logger.error("Can't close FloorspaceOverrides output file");
            		e.printStackTrace();
            	}
        		overRideFile=null;
            }
            if (exchangeResultsTotals!=null) {
            	try {
            		exchangeResultsTotals.close();
            	} catch (IOException e) {
            		logger.error("Can't close ExchangeResultsTotals output file");
            		e.printStackTrace();
            	}
            }
        } else {
            logger.fatal("Can't create ExchangeResults output file");
        }
    }

    private BufferedWriter getOverrideFile() {
    	if (overRideFile  == null) {
    		try {
				overRideFile = new BufferedWriter(new FileWriter(getOutputPath()+"FloorspaceOverrides.csv"));
				overRideFile.write(LogisticPlusLinearWithOverrideFunction.getHeader()+"\n");
			} catch (IOException e) {
				String msg = "Can't open FloorspaceOverrides.csv for writing";
				logger.error(msg);
			}
    	}
		return overRideFile;
	}

	/*Used by DAF to write out a single file for each commodity
     * 
     */
    public void writeExchangeResults(String commodityName) {
        Commodity c = Commodity.retrieveCommodity(commodityName);
        try {
            BufferedWriter exchangeResults = new BufferedWriter(new FileWriter(getOutputPath() + commodityName+"_ExchangeResults.csv"));
            exchangeResults.write("Commodity,ZoneNumber,Demand,InternalBought,Exports,Supply,InternalSold,Imports,Surplus,Price\n");
            for (Exchange ex : c.getAllExchanges()) {
                try {
                    exchangeResults.write(ex.myCommodity.name + "," + ex.exchangeLocationUserID + ",");
                    double[] importExport = ex.importsAndExports(ex.getPrice());
                    exchangeResults.write((ex.boughtTotal()+importExport[1])+","+ex.boughtTotal() + ","+ importExport[1] + "," +(ex.soldTotal()+importExport[0])+","+ ex.soldTotal()+","+importExport[0] + "," + ex.exchangeSurplus() + "," + ex.getPrice() + "\n");
                } catch (IOException e) {
                    logger.fatal("Error adding exchange " + this + " to table");
                    e.printStackTrace();
                }
                
            }
            exchangeResults.close();
        } catch (IOException e) {
            logger.fatal("Can't create exchange results output file for commodity "+commodityName);
            e.printStackTrace();
        }
    }
    
    public void writeActivitySummary() {
        BufferedWriter activityFile;
        try {
            activityFile = new BufferedWriter(new FileWriter(getOutputPath() + "ActivitySummary.csv"));
            //ENHANCEMENT shall we write out the dollars spent and earned as well as the utility associated with size/variation/transport components?
            activityFile.write("Activity,CompositeUtility,Size\n");
            for (ProductionActivity p : ProductionActivity.getAllProductionActivities()) {
                try {
                    activityFile.write(p.name+",");
                    activityFile.write(p.getUtility()+","+((AggregateActivity) p).getTotalAmount()+"\n");
                } catch (IOException e) {
                    logger.fatal("Error adding activity quantity to activity location table");
                    e.printStackTrace();
                } catch (OverflowException e) {
                    activityFile.write("Overflow\n");
                }
            }
            logger.info("\tActivitySummary.csv has been written");
            activityFile.close();
        } catch (IOException e) {
            logger.fatal("Can't create location output file");
            e.printStackTrace();
        }
    }

    public String[] readInHistogramSpecifications() {
        ArrayList<String> newSkimNames = new ArrayList<String>();
        TableDataSet histogramSpecTable = loadTableDataSet("HistogramsI","aa.base.data", false);
        if (histogramSpecTable == null) {
        	logger.error("No histogram specifications -- please create a HistogramsI.csv file");
        	return new String[0];
        }
        for (int row = 1; row <= histogramSpecTable.getRowCount(); row++ ) {
            HistogramSpec hspec = new HistogramSpec();
            hspec.commodityName = histogramSpecTable.getStringValueAt(row,"Commodity");
            hspec.categorizationSkim = histogramSpecTable.getStringValueAt(row,"Skim");
            if (!newSkimNames.contains(hspec.categorizationSkim)) newSkimNames.add(hspec.categorizationSkim);
            float lastHistogramBoundary = Float.NEGATIVE_INFINITY;
            for (int i=0;i<histogramSpecTable.getColumnCount()-2;i++) {
                int column = histogramSpecTable.getColumnPosition("C"+i);
                if (column >0) {
                    float boundary = histogramSpecTable.getValueAt(row,column);
                    if (boundary > lastHistogramBoundary) {// force increasing boudaries
                        hspec.boundaries.add(new Float(boundary));
                        lastHistogramBoundary = boundary;
                    }
                }
            }
            histogramSpecifications.add(hspec);
        }
        String[] bob = new String[newSkimNames.size()];
        bob = newSkimNames.toArray(bob);
        return bob;
    }
    
    public void writeAllFlowMatrices(boolean omx) {
//      check for property in aa.properties - should start with a '.' in the file.
        if(zipExtension == null){
            zipExtension = ResourceUtil.getProperty(aaRb,"zip.extension",".zipMatrix"); 
        }
        try {
        	OMXUtility omxutil = null;
        	if (omx) {
        	    omxutil = new OMXUtility();
                omxutil.createOMXFlowFile(new File(getOutputPath()+"flow_matrices.omx").toPath(), zones);
        	}
            BufferedWriter histogramFile = new BufferedWriter(new FileWriter(getOutputPath() + "Histograms.csv"));
            histogramFile.write("Commodity,BuyingSelling,BandNumber,LowerBound,Quantity,AverageLength\n");
            PrintWriter pctFile = new PrintWriter(new BufferedWriter(new FileWriter(getOutputPath() + "PctIntrazonalxCommodityxBzone.csv")));
            pctFile.println("Bzone,Commodity,BuyIntra,BuyFrom,BuyTo,BuyPctIntraFrom,BuyPctIntraTo,SellIntra,SellFrom,SellTo,SellPctIntraFrom,SellPctIntraTo");
            for (AbstractCommodity ac : Commodity.getAllCommodities()) {
                writeFlowZipMatrices(ac.getName(), histogramFile, pctFile, omxutil);
            }
            logger.info("All Buying and Selling Flow matrices have been written");
        	if (omx) {
        		omxutil.save();
        		if (logger.isDebugEnabled()) logger.debug(omxutil.summary());
        		omxutil.close();
        	}
            histogramFile.close();
            pctFile.close();
            logger.info("\tAll buying and selling flow matrices have been written");
            logger.info("\tHistograms.csv has been written");
            logger.info("\tPctIntrazonalxCommodityxBzone.csv has been written");
            
       } catch (IOException e) {
           logger.fatal("Problems writing histogram output file "+e);
           e.printStackTrace();
       }
    }

    public void writeFlowZipMatrices(String name, Writer histogramFile, PrintWriter pctFile, OMXUtility omxutil) {
        Commodity com = Commodity.retrieveCommodity(name);
        Matrix b = com.getBuyingFlowMatrix();
        Matrix s = com.getSellingFlowMatrix();
        
        if (omxutil == null) {
            if (b!=null) {
    	        ZipMatrixWriter  zmw = new ZipMatrixWriter(new File(getOutputPath()+"buying_"+com.name+zipExtension));
    	        zmw.writeMatrix(b);
            } 
                    
            if (s!=null) {
            	ZipMatrixWriter zmw = new ZipMatrixWriter(new File(getOutputPath()+"selling_"+com.name+zipExtension));
                zmw.writeMatrix(s);
            }
        } else {
            omxutil.writeFlowOMXMatrices(com, b, s);
        }
        if(logger.isDebugEnabled()) logger.debug("Buying and Selling Commodity Flow Matrices have been written for " + name);
        
        //write intrazonal numbers to calculate percentages
        if (b!=null && s!=null) writePctIntrazonalFile(pctFile,name,b,s);
        
        if (b!=null && s!=null) writeFlowHistograms(histogramFile, name,b,s,null);
    }
    
    protected void writePctIntrazonalFile(PrintWriter writer,String name,Matrix b, Matrix s){
        boolean closePctFile = false;
        try {
          /* for daf version, we have to write out a file for each commodity so
           * we create a new file each time this routine is called, write to the
           * file and then close it.  In the monolithic version, we just write lines
           * to a single file as we iterate over the commodities and the file
           * will be closed by the calling method.
           */ 
            if (writer == null) { 
                writer = new PrintWriter(new BufferedWriter(new FileWriter(getOutputPath() + "PctIntrazonalxBetazone_"+name+".csv")));
                writer.println("Bzone,Commodity,BuyIntra,BuyFrom,BuyTo,BuyPctIntraFrom,BuyPctIntraTo,SellIntra,SellFrom,SellTo,SellPctIntraFrom,SellPctIntraTo");
                closePctFile = true;
            }   
            
            float buyIntra = 0;
            float buyFrom = 0;
            float buyTo = 0;
            float buyPctFrom = 0;
            float buyPctTo = 0;
            float sellIntra = 0;
            float sellFrom = 0;
            float sellTo = 0;
            float sellPctFrom = 0;
            float sellPctTo = 0;
                
            for(int i=0; i<b.getRowCount(); i++){
                int betaZone = b.getExternalNumber(i);
                buyIntra = b.getValueAt(betaZone,betaZone);
                buyFrom = b.getRowSum(betaZone);
                buyTo = b.getColumnSum(betaZone);
                buyPctFrom = buyIntra/buyFrom;
                buyPctTo = buyIntra/buyTo;
                sellIntra = s.getValueAt(betaZone,betaZone);
                sellFrom = s.getRowSum(betaZone);
                sellTo = s.getColumnSum(betaZone);
                sellPctFrom = sellIntra/sellFrom;
                sellPctTo = sellIntra/sellTo;
                writer.print(betaZone + ",");
                writer.print(name + ",");
                writer.print(buyIntra +","); //buyIntra
                writer.print(buyFrom +","); //buyFrom
                writer.print(buyTo + ","); //buyTo
                writer.print(buyPctFrom + ","); //buyPctFrom
                writer.print(buyPctTo + ","); //buyPctTo
                writer.print(sellIntra +","); //sellIntra
                writer.print(sellFrom +","); //sellFrom
                writer.print(sellTo + ","); //sellTo
                writer.print(sellPctFrom + ","); //sellPctFrom
                writer.println(sellPctTo); //sellPctTo
            }
            
            /* close the file if we are running the DAF version of aa
             * otherwise the file will be closed after we have iterated
             * thru all the commodities.
             */
            if (writer !=null && closePctFile == true) {
                writer.close();
            }
            
        } catch (Exception e) {
            logger.fatal("Error writing to file " + e);
            System.exit(1);
        }    
    }
    
    /**
     * @param name name of the commodity to write histograms for 
     * @param stream stream to write to, can be null in which case a file will be created and used
     * @param zones_to_include 
     */
    public void writeFlowHistograms(String name, Writer stream, List<Integer> zones_to_include) {
        boolean someHistogramsForThisCommodity = false;
        Commodity com = Commodity.retrieveCommodity(name);
        for (int i=0;i<histogramSpecifications.size();i++) {
            if ((histogramSpecifications.get(i)).getCommodity() == com) someHistogramsForThisCommodity=true;
        }
        if (someHistogramsForThisCommodity) {
            Matrix b = com.getBuyingFlowMatrix();
            Matrix s = com.getSellingFlowMatrix();
            if (b!=null & s!=null) writeFlowHistograms(stream,name,b,s, zones_to_include);
        }
    }
    
    /**
     * @param histogramFile the file to write the histogram to.  Can be "null" in which case a file named 
     * histograms_commodityName.csv will be created. 
     * @param commodityName
     * @param buyingMatrix
     * @param sellingMatrix
     * @param zones_to_include 
     */
    protected void writeFlowHistograms(Writer histogramFile, String commodityName, Matrix buyingMatrix, Matrix sellingMatrix, List<Integer> zones_to_include) {
        boolean closeHistogramFile = false;
                
        // ignore externals
        int[] ignoreZones = null;
        if (zones_to_include == null) {
	        if (!ResourceUtil.getBooleanProperty(aaRb, "aa.externalZonesInHistogram", false)) {
	        	ignoreZones = PECASZone.getOverrideExternalZones();
	        }
        } else {
        	ArrayList<Integer> ignoreZonesList = new ArrayList<Integer>();
        	Set<Integer> zones_to_include_set = new HashSet<Integer>();
        	for (int a : zones_to_include) { zones_to_include_set.add(a); }
        	for (PECASZone z : zones) {
        		if (!zones_to_include_set.contains(z.zoneUserNumber)) {
        			ignoreZonesList.add(z.zoneUserNumber);
        		}
        	}
        	ignoreZones = new int[ignoreZonesList.size()];
        	for (int i =0; i<ignoreZonesList.size(); i++) {ignoreZones[i] = ignoreZonesList.get(i);}
        }
        for (HistogramSpec hspec : histogramSpecifications) {
            if (hspec.commodityName.equals(commodityName)) {
                double[] boundaries = new double[hspec.boundaries.size()];
                for (int bound=0;bound<hspec.boundaries.size();bound++) {
                    boundaries[bound] = hspec.boundaries.get(bound);
                }
                MatrixHistogram mhBuying = new MatrixHistogram(boundaries);
                MatrixHistogram mhSelling = new MatrixHistogram(boundaries);
                Matrix skim=null;
                try {
                    skim =((SomeSkims) TransportKnowledge.globalTransportKnowledge).getMatrix(hspec.categorizationSkim);
                } catch (RuntimeException e) {
                    logger.fatal("Can't find skim name "+hspec.categorizationSkim+" in existing skims -- attempting to read it separately");
                }
                if (skim==null) {
                    // try to read it in.
                    ((SomeSkims) TransportKnowledge.globalTransportKnowledge).addZipMatrix(hspec.categorizationSkim);
                    skim =((SomeSkims) TransportKnowledge.globalTransportKnowledge).getMatrix(hspec.categorizationSkim);
                }
                if (ignoreZones==null) {
                	mhBuying.generateHistogram(skim,buyingMatrix);
                	mhSelling.generateHistogram(skim,sellingMatrix);
                } else {
                	mhBuying.generateHistogram(skim,buyingMatrix,ignoreZones);
                	mhSelling.generateHistogram(skim,sellingMatrix,ignoreZones);	
                }
                try {
                    if (histogramFile == null) {
                        histogramFile = new BufferedWriter(new FileWriter(getOutputPath() + "Histograms_"+commodityName+".csv"));
                        histogramFile.write("Commodity,BuyingSelling,BandNumber,LowerBound,Quantity,AverageLength\n");
                        closeHistogramFile = true;
                    }   
                    mhBuying.writeHistogram(commodityName,"buying", histogramFile);
                    mhSelling.writeHistogram(commodityName,"selling", histogramFile);
                } catch (IOException e) {
                    logger.fatal("IO exception "+e+" in writing out histogram file for "+this);
                    logger.fatal(e);
                    System.exit(1);
                }
            }
        }
        if (histogramFile !=null && closeHistogramFile == true) {
            try {
                histogramFile.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

    }
    
    public void writeAllHistograms() {
    	writeAllHistograms("Histograms", null);
    }
    
    public void writeAllHistograms(String filename, List<Integer> zones_to_include) {
        try {
        	logger.info("Writing histograms");
            BufferedWriter histogramFile = new BufferedWriter(new FileWriter(getOutputPath() + filename + ".csv"));
            histogramFile.write("Commodity,BuyingSelling,BandNumber,LowerBound,Quantity,AverageLength\n");
            for (AbstractCommodity ac : Commodity.getAllCommodities()){
                writeFlowHistograms(ac.getName(),histogramFile, zones_to_include);
            }
            histogramFile.close();
            logger.info("\tHistograms.csv has been written");
        } catch (IOException e) {
            logger.fatal("Problems writing histogram output file "+e);
            e.printStackTrace();
         }
    }
    
    public void writeFlowsToFile(BufferedWriter writer, CommodityFlowArray myFlows){
            Commodity com = myFlows.theCommodityZUtility.getCommodity();
            int tazIndex = myFlows.theCommodityZUtility.myLuz.getZoneIndex();
            int tazUserID = myFlows.theCommodityZUtility.myLuz.getZoneUserNumber();
            char selling = 's';
            if (myFlows.theCommodityZUtility instanceof BuyingZUtility) selling = 'b';
            try {
                if (com.exchangeType == 'p' && selling == 's' || com.exchangeType == 'c' && selling == 'b' || com.exchangeType == 'n') {
                    writer.write( com.toString() + ",");
                    Exchange x = com.getExchange(tazIndex);
                    writer.write(tazUserID + ",");
                    writer.write(tazUserID + ",");
                    if (selling == 'b') {
                        writer.write(String.valueOf(-x.getFlowQuantity(tazIndex, selling)));
                    } else {
                        writer.write(String.valueOf(x.getFlowQuantity(tazIndex, selling)));
                    }
    /*                if (myFlows.timeAndDistanceUtilities && myFlows.peakAutoSkims != null) {
                        writer.write("," + myFlows.peakAutoSkims.getDistance(tazUserID, tazUserID));
                        writer.write("," + myFlows.peakAutoSkims.getTime(tazUserID, tazUserID));
                    } */
                    writer.write("\n");
                } else {
                    Collection<Exchange> theExchanges = com.getAllExchanges();
                    synchronized (theExchanges) {
                        for (Exchange x : theExchanges) {
                            int exchangeID = x.exchangeLocationUserID;
                            writer.write(com.toString() + ",");
                            if (selling == 'b') {
                                writer.write(exchangeID + ",");
                                writer.write(tazUserID + ",");
                                writer.write(String.valueOf(-x.getFlowQuantity(tazIndex, selling)));
    /*                            if (myFlows.timeAndDistanceUtilities && myFlows.peakAutoSkims != null) {
                                    writer.write("," + myFlows.peakAutoSkims.getDistance(exchangeID, tazUserID));
                                    writer.write("," + myFlows.peakAutoSkims.getTime(exchangeID, tazUserID));
                                } */
                                writer.write("\n");
                            } else {
                                writer.write(tazUserID + ",");
                                writer.write(exchangeID + ",");
                                writer.write(String.valueOf(x.getFlowQuantity(tazIndex, selling)));
    /*                            if (myFlows.timeAndDistanceUtilities && myFlows.peakAutoSkims != null) {
                                    writer.write("," + myFlows.peakAutoSkims.getDistance(tazUserID, exchangeID));
                                    writer.write("," + myFlows.peakAutoSkims.getTime(tazUserID, exchangeID));
                                } */
                                writer.write("\n");
                            }
                        }
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

    public void writeFlowTextFiles() {
        try {
            BufferedWriter consumptionFlows = new BufferedWriter(new FileWriter(getOutputPath() + "FlowsFromConsumption(buying).csv"));
            BufferedWriter productionFlows = new BufferedWriter(new FileWriter(getOutputPath() + "FlowsFromProduction(selling).csv"));
            consumptionFlows.write("commodity,origin,destination,quantity,distance,time\n");
            productionFlows.write("commodity,origin,destination,quantity,distance,time\n");
            //AbstractZone[] zones = AbstractZone.getAllZones();
            //for (int z = 0; z < zones.length; z++) {
            for (AbstractCommodity ac : Commodity.getAllCommodities()) {
                Commodity c = (Commodity) ac;
                for (int bs = 0; bs < 2; bs++) {
                	Iterator<CommodityZUtility> it;
                    if (bs == 0)
                        it = c.getBuyingUtilitiesIterator();
                    else
                        it = c.getSellingUtilitiesIterator();
                    while (it.hasNext()) {
                        CommodityZUtility czu = it.next();
                        if (bs == 0)
                            writeFlowsToFile(consumptionFlows,czu.getMyFlows());
                        else
                            writeFlowsToFile(productionFlows,czu.getMyFlows());
                    }
                }
            }
            consumptionFlows.close();
            productionFlows.close();
        } catch (IOException e) {
            logger.fatal("Error writing flow tables to disk");
            e.printStackTrace();
        }
    }

    public void writeLaborConsumptionAndProductionFiles(){}

    /**
     * @return Returns the timePeriod.
     */
    public int getTimePeriod() {
        return timePeriod;
    }

    /**
     * @param timePeriod The timePeriod to set.
     */
    public void setTimePeriod(int timePeriod) {
        this.timePeriod = timePeriod;
    }

    public void setBaseYear(int baseYear) {
        this.baseYear = baseYear;
    }

    public void setAaResourceBundle(ResourceBundle aaRb){
        this.aaRb = aaRb;
    }

    public void setResourceBundles(ResourceBundle aaRb){
        setAaResourceBundle(aaRb);
    }

    public String getOutputPath() {
        if (outputPath == null) {
            outputPath = ResourceUtil.checkAndGetProperty(aaRb, "output.data");
        }
        return outputPath;
    }

    /**
     * @return Returns the csvFileReader.
     */
    public CSVFileReader getCsvFileReader() {
        if (csvFileReader==null) {
            csvFileReader = new CSVFileReader();
        }
        return csvFileReader;
    }
    
    
    /**
     * To stop from breaking existing code, use this method.
     * If we are using SQL inputs we specify them in aa.datasource.
     * If we are using CSV inputs we look in aa.base.data.
     * @return the default TableDataSetCollection
     */
    public TableDataSetCollection getTableDataSetCollection() {
        if (ResourceUtil.getBooleanProperty(aaRb,"aa.useSQLInputs",false)) {
            return getTableDataSetCollection("aa.datasource","output.data");
        } else {
            return getTableDataSetCollection("aa.base.data","output.data");
        }
    }
    
    /**
     * Gets a TableDataSetCollection which will read from inputSource and write to outputDirectory
     * if aa.useSQLInputs is set to true in the properties file then inputSource must be a property which defines the name of a defined jdbc datasource
     * defined in the OS or a database.  
     * If aa.useSQLInputs is set to false (the default value) then inputSource is a property which defines the name of a directory containing inputs csv files.
     *  
     * @param inputSource the name of a property in the aa.properties file which defines the inputs source (directory or jdbc database)
     * @param outputSource the name of a property in the aa.properties file which defines the output directory
     * @return a TableDataSetCollection which can be used to read and write
     */
    protected TableDataSetCollection getTableDataSetCollection(String inputSource, String outputSource) {
        TableDataSetCollection collection = collections.getOrDefault(inputSource, new HashMap<>()).get(outputSource);
        if (collection !=null) return collection;
        boolean useSQLInputs=ResourceUtil.getBooleanProperty(aaRb,"aa.useSQLInputs",false);
        String outputDirectory = ResourceUtil.checkAndGetProperty(aaRb,outputSource);
        TableDataReader reader = null;
        if (useSQLInputs) {
            String datasourceName =
                ResourceUtil.checkAndGetProperty(aaRb, inputSource);
            String jdbcDriver =
                ResourceUtil.checkAndGetProperty(aaRb, "aa.jdbcDriver");
            JDBCConnection jdbcConnection = new JDBCConnection(datasourceName, jdbcDriver, "", "");
            JDBCTableReader jdbcTableReader = new JDBCTableReader(jdbcConnection);
            boolean excelInputs = ResourceUtil.getBooleanProperty(aaRb,"aa.excelInputs",false);
            if (excelInputs) {
                jdbcTableReader.setMangleTableNamesForExcel(true);
            }
            reader = jdbcTableReader;
        } else {
            String inputPath = ResourceUtil.checkAndGetProperty(aaRb,inputSource);
            CSVFileReader csvReader = new CSVFileReader();
            logger.info("Setting up to retrieve inputs from path " + inputPath);
            csvReader.setMyDirectory(inputPath);
            reader = csvReader;
        }
        CSVFileWriter writer = new CSVFileWriter();
        logger.info("Setting up to write outputs to path " + outputDirectory);
        writer.setMyDirectory(new File(outputDirectory));
        writer.setMyDecimalFormat(new GeneralDecimalFormat("0.#########E0",10000000,.001));
        collection = new TableDataSetCollection(reader,writer);
        collections.putIfAbsent(inputSource, new HashMap<>());
        collections.get(inputSource).put(outputSource, collection);
        return collection;
    }
    
    /**
     * Load a table data set from our specified input source.  Calls loadTableDataSet(string,string,true)
     * @param tableName name of table to get
     * @param source location (aa.base.data, etc.)
     * @return the table data set (guaranteed to be non-null, throws a RuntimeException if it can't find the data)
     */
    public final TableDataSet loadTableDataSet(String tableName, String source) {
        return loadTableDataSet(tableName,source,true);
    };

    /**
     * Load a table data set from our specified input source.
     * Checks "aa.useSQLInputs" 
     * If aa.useSQLInputs is true, tries to get it in the SQL table (a JDBC connection)
     * Otherwise, tries to read a .csv file.
     * Note that the override version in AASetupWithTechnologySubstitution will
     * look for a .CSV file first, then look in JDBC if it isn't there.
     * @param tableName name of table to get
     * @param source source location (aa.base.data, etc., specified in properties file)
     * @param check if true, will throw a RuntimeException if it can't find the data
     * @return the table data set, or null if the data was not found and check=false
     */
    public abstract TableDataSet loadTableDataSet(String tableName, String source, boolean check);
    
    /**
     * Attempts to load data from an SQL table (through a JDBC connection)
     * @param tableName name of table to get
     * @param source source location (aa.base.data, etc., specified in properties file)
     * @param check if true, will throw a RuntimeException if it can't find the data
     * @return the table data set, or null if the data was not found and check=false
     */
    protected TableDataSet loadTableDataSetFromJDBC(String tableName, String source, boolean check) {
        try {
            TableDataSet table = getJDBCTableReader().readTable(tableName);

            if (table == null)
                logFailure("Can't find table "+tableName+" in JDBC table", check);
            return table;
        } catch (IOException e) {
            String msg = "Can't find input table " + tableName + " even though the file exists";
            logger.fatal(msg);
            throw new RuntimeException(msg, e);
        }
    }
    
    /**
     * Logs the specified failure message. If check is false, the message is logged
     * at the WARN level and the method returns normally. Otherwise, the message is
     * logged at the FATAL level and a RuntimeException with msg as the detail message is thrown.
     * @param msg The failure message
     * @param check Whether the failure is critical
     */
    protected void logFailure(String msg, boolean check) {
        if(check) {
            logger.fatal(msg);
            throw new RuntimeException(msg);
        } else
            logger.warn(msg);
    }

    public ActivityConstraints<PECASZone, ProductionActivity> setupActivityConstraints(ZoneSystem<PECASZone> zones) {
    	ActivityConstraints<PECASZone, ProductionActivity> constraints = readActivityConstraints(zones);
    	
    	for (Map.Entry<PECASZone, Map<ProductionActivity, Double>> e : constraints.constraints().entrySet()) {
    	    for (Map.Entry<ProductionActivity, Double> f : e.getValue().entrySet()) {
    	        ProductionActivity a = f.getKey(); 
                AmountInZone amt = a.getAmountInUserZone(e.getKey().zoneUserNumber);
                amt.setConstrained(true);
                amt.constraintQuantity += f.getValue();
    	    }
    	}
    	
    	checkActivityConstraints();
    	
    	return constraints;
    }

    protected ActivityConstraints<PECASZone, ProductionActivity> readActivityConstraints(
            ZoneSystem<PECASZone> zones) {
        logger.info("Reading ActivityConstraintsI File");
        if (maxAlphaZone == 0)
            readFloorspaceZones();
        TableDataSet activityConstraintsTable = loadTableDataSet(
                "ActivityConstraintsI", "aa.current.data");
        int alphaZoneColumn = activityConstraintsTable
                .getColumnPosition("AZone");
        if (alphaZoneColumn == -1)
            alphaZoneColumn = activityConstraintsTable.checkColumnPosition("TAZ");
        int quantityColumn = activityConstraintsTable
                .checkColumnPosition("Quantity");
        int activityColumn = activityConstraintsTable
                .checkColumnPosition("Activity");

        ActivityConstraints<PECASZone, ProductionActivity> constraints = new ActivityConstraints<>();

        if (alphaZoneColumn != -1) {
            Hashtable<String, ZoneQuantityStorage> alphaZoneActivityConstraintsInventory = new Hashtable<String, ZoneQuantityStorage>();
            for (int row = 1; row <= activityConstraintsTable
                    .getRowCount(); row++) {
                int alphaZone = (int) activityConstraintsTable.getValueAt(row,
                        alphaZoneColumn);
                float quantity = activityConstraintsTable.getValueAt(row,
                        quantityColumn);
                String activityName = activityConstraintsTable
                        .getStringValueAt(row, activityColumn);
                ProductionActivity a = ProductionActivity
                        .retrieveProductionActivity(activityName);
                if (a == null) {
                    logger.fatal("Bad activity name " + activityName
                            + " in ActivityConstraintsI.csv");
                    throw new RuntimeException("Bad activity name "
                            + activityName + " in ActivityConstraintsI.csv");
                }
                ZoneQuantityStorage constraintSet = alphaZoneActivityConstraintsInventory
                        .get(activityName);
                if (constraintSet == null) {
                    constraintSet = new ZoneQuantityStorage(activityName);
                    alphaZoneActivityConstraintsInventory.put(activityName,
                            constraintSet);
                }
                constraintSet.increaseQuantityForZone(alphaZone, quantity);
            }

            // now collapse to land use zone (luz)s
            for (Map.Entry<String, ZoneQuantityStorage> constraintEntry : alphaZoneActivityConstraintsInventory
                    .entrySet()) {
                String activityName = constraintEntry.getKey();
                ProductionActivity activity = ProductionActivity
                        .retrieveProductionActivity(activityName);
                if (activity == null) {
                    logger.fatal("Invalid constraint activity " + activityName);
                    throw new RuntimeException(
                            "Invalid constraint activity " + activityName);
                }
                ZoneQuantityStorage alphaConstraint = constraintEntry
                        .getValue();
                for (Map.Entry<Integer, Double> e : alphaConstraint
                        .getInventoryMap().entrySet()) {
                    Integer alphaZone = e.getKey();
                    int betaZone = floorspaceZoneCrossref
                            .getBetaZone(alphaZone.intValue());
                    PECASZone zone = zones.getZoneByUserNumber(betaZone);

                    double currentConstraint = constraints
                            .getConstraintDefault(zone, activity, 0);
                    constraints.constrain(zone, activity,
                            currentConstraint + e.getValue());
                }
            }
        } else {
            // Betazone constraints table
            logger.info(
                    "No TAZ or AZone Column in ActivityConstraintsI, now checking for LUZ or BZone");
            int betaZoneColumn = activityConstraintsTable
                    .getColumnPosition("BZone");
            if (betaZoneColumn == -1)
                betaZoneColumn = activityConstraintsTable
                        .getColumnPosition("LUZ");
            if (betaZoneColumn == -1) {
                String msg = "Can't figure out ActivityConstraintsI table, no TAZ column or LUZ column";
                logger.fatal(msg);
                throw new RuntimeException(msg);
            }
            for (int row = 1; row <= activityConstraintsTable
                    .getRowCount(); row++) {
                int betaZone = (int) activityConstraintsTable.getValueAt(row,
                        betaZoneColumn);
                float quantity = activityConstraintsTable.getValueAt(row,
                        quantityColumn);
                String activityName = activityConstraintsTable
                        .getStringValueAt(row, activityColumn);
                ProductionActivity activity = ProductionActivity
                        .retrieveProductionActivity(activityName);
                if (activity == null) {
                    logger.fatal("Bad activity name " + activityName
                            + " in ActivityConstraintsI.csv");
                    throw new RuntimeException("Bad activity name "
                            + activityName + " in ActivityConstraintsI.csv");
                }

                PECASZone zone = zones.getZoneByUserNumber(betaZone);

                double currentConstraint = constraints
                        .getConstraintDefault(zone, activity, 0);
                constraints.constrain(zone, activity,
                        currentConstraint + quantity);
            }
        }

        return constraints;
    }
    
    
    protected void checkActivityConstraints() {
    	for (ProductionActivity a : ProductionActivity.getAllProductionActivities()){
    		a.checkConstraintConsistency();
    	}
    }
    
    public ZoneGroupSystem<PECASZone> readZoneGroups(ZoneSystem<PECASZone> zones) {
        logger.info("Reading zone group definitions");
        TableDataSet groupTable = loadTableDataSet("ZoneGroupsI", "aa.base.data");
        
        int groupNumberColumn = groupTable.checkColumnPosition("GroupNumber");
        int groupNameColumn = groupTable.checkColumnPosition("GroupName");
        
        Map<Integer, String> groupDefs = new LinkedHashMap<>();
        
        for (int row = 1; row <= groupTable.getRowCount(); row++) {
            int groupNumber = (int) groupTable.getValueAt(row, groupNumberColumn);
            String groupName = groupTable.getStringValueAt(row, groupNameColumn);
            groupDefs.put(groupNumber, groupName);
        }
        
        TableDataSet zoneTable = loadTableDataSet("PECASZonesByZoneGroupI",
                "aa.current.data", false);
        if (zoneTable == null) {
        	// If we use the same groups for each year we put them in PECASZonesI as a column called "GroupNumber" instead of having a separate file.
            zoneTable = loadTableDataSet("PECASZonesI", "aa.base.data");
        }

        int zoneNumberColumn = zoneTable.checkColumnPosition("ZoneNumber");
        int zoneGroupNumberColumn = zoneTable.checkColumnPosition("GroupNumber");

        Map<PECASZone, Integer> zoneToGroup = new HashMap<>();
        Map<Integer, List<PECASZone>> groupToZone = new HashMap<>();
        for (int row = 1; row <= zoneTable.getRowCount(); row++) {
            int zoneNumber = (int) zoneTable.getValueAt(row, zoneNumberColumn);
            PECASZone zone = zones.getZoneByUserNumber(zoneNumber);
            int groupNumber = (int) zoneTable.getValueAt(row,
                    zoneGroupNumberColumn);
            if (!groupToZone.containsKey(groupNumber)) {
                groupToZone.put(groupNumber, new ArrayList<PECASZone>());
            }
            List<PECASZone> groupZones = groupToZone.get(groupNumber);
            groupZones.add(zone);

            if (zoneToGroup.containsKey(zone)) {
                int existingGroup = zoneToGroup.get(zone);
                loggerf.throwFatal(
                        "Zone %s has been assigned to groups %s and %s; "
                                + "overlapping zone groups are not currently supported",
                        zone, existingGroup, groupNumber);
            }
            zoneToGroup.put(zone, groupNumber);
        }
        
        List<ZoneGroup<PECASZone>> groups = new ArrayList<>();
        for (Map.Entry<Integer, List<PECASZone>> entry : groupToZone.entrySet()) {
            int groupNumber = entry.getKey();
            List<PECASZone> groupZones = entry.getValue();
            String groupName = groupDefs.get(groupNumber);
            groups.add(new ZoneGroup<PECASZone>(groupNumber, groupName, groupZones));
        }
        
        return new SimpleZoneGroupSystem<>(groups);
    }
    
    /**
     * Reads the minimum and maximum bounds on zone groups and returns them
     * after checking their consistency.
     */
    public void readZoneGroupBounds(ZoneGroupSystem<PECASZone> groups) {
        logger.info("Reading zone group bounds");
        
        TableDataSet boundTable = loadTableDataSet(
                "ZoneGroupBoundsI", "aa.current.data");
        int groupNumberColumn = boundTable.checkColumnPosition("GroupNumber");
        int activityColumn = boundTable.checkColumnPosition("Activity");
        int minimumColumn = boundTable.checkColumnPosition("Minimum");
        int maximumColumn = boundTable.checkColumnPosition("Maximum");

        for (int row = 1; row <= boundTable.getRowCount(); row++) {
            int groupNumber = (int) boundTable.getValueAt(row,
                    groupNumberColumn);
            ZoneGroup<PECASZone> group = groups.getZoneGroupByNumber(groupNumber);
            if (group == null) {
                loggerf.throwFatal("Zone group %d not defined", groupNumber);
            }
            
            String activityName = boundTable.getStringValueAt(row, activityColumn);
            ProductionActivity act = ProductionActivity
                    .retrieveProductionActivity(activityName);
            if (act == null) {
                loggerf.throwFatal(
                        "Bad activity name %s in ZoneGroupConstraintsI",
                        activityName);
            }
            
            float minimum = boundTable.getValueAt(row, minimumColumn);
            float maximum = boundTable.getValueAt(row, maximumColumn);
            
            group.setMinimum(act, minimum);
            group.setMaximum(act, maximum);
        }
        checkZoneGroupBoundConsistency(groups);
    }
    
    protected void checkZoneGroupBoundConsistency(ZoneGroupSystem<PECASZone> groups) {
        Map<ZoneGroup<PECASZone>, Map<ProductionActivity, Double>> constrainedTotals = new HashMap<>();
        Map<ZoneGroup<PECASZone>, Set<ProductionActivity>> notFullyConstrained = new HashMap<>();
        
        for (ProductionActivity act : ProductionActivity
                .getAllProductionActivities()) {
            for (AmountInZone amt : act.getMyDistribution()) {
                ZoneGroup<PECASZone> group = groups.getGroupForZone(amt.myZone);
                if (amt.isConstrained()) {
                    if (!constrainedTotals.containsKey(group)) {
                        constrainedTotals.put(group, new HashMap<ProductionActivity, Double>());
                    }
                    Map<ProductionActivity, Double> totalsByGroup = constrainedTotals.get(group);
                    
                    double existing = totalsByGroup.containsKey(act) ? totalsByGroup.get(act) : 0;
                    totalsByGroup.put(act, amt.constraintQuantity + existing);
                } else {
                    if (!notFullyConstrained.containsKey(group)) {
                        notFullyConstrained.put(group, new HashSet<ProductionActivity>());
                    }
                    notFullyConstrained.get(group).add(act);
                }
            }
        }
        
        for (ZoneGroup<PECASZone> group : groups.getAllGroups()) {
            if (constrainedTotals.containsKey(group)) {
                Map<ProductionActivity, Double> groupTotals = constrainedTotals
                        .get(group);
                for (Activity<PECASZone> activity : group.boundedActivities()) {
                    if (groupTotals.containsKey(activity)) {
                        double total = groupTotals.get(activity);
                        if (total > group.maximum(activity)) {
                            loggerf.throwFatal(
                                    "Zonal constraints for activity %s in group %s exceed the group maximum",
                                    activity, group);
                        }
                        boolean fullyConstrained = !(notFullyConstrained
                                .containsKey(group) && notFullyConstrained.get(
                                group).contains(activity));
                        if (fullyConstrained
                                && total < group.minimum(activity)) {
                            loggerf.throwFatal(
                                    "All zones in group %s are constrained for activity %s, "
                                            + "but the total constraint is less than the group minimum",
                                    group, activity);
                        }
                    }
                }
            }
        }
    }

	boolean isRerunForOutputs() {
		return rerunForOutputs;
	}

	void setRerunForOutputs(boolean rerunForOutputs) {
		this.rerunForOutputs = rerunForOutputs;
	}
	
	public static void main(String[] args) {
        ResourceBundle aaRb = ResourceUtil.getResourceBundle("aa");
	    String pProcessorClass = ResourceUtil.getProperty(aaRb, "pprocessor.class");
	    logger.info("Running standalone preprocessing with " + pProcessorClass);
	    try {
            AAPProcessor pProcessor = (AAPProcessor) Class.forName(pProcessorClass).newInstance();
            pProcessor.setResourceBundles(aaRb);
            pProcessor.setTimePeriod(ResourceUtil.getIntegerProperty(aaRb, "t.year"));
            pProcessor.setBaseYear(ResourceUtil.getIntegerProperty(aaRb, "base.year"));
            pProcessor.doInputProcessing();
        } catch (ClassNotFoundException e) {
            loggerf.throwFatal("Can't find pprocessor class " + pProcessorClass);
        } catch (InstantiationException e) {
            loggerf.throwFatal("Can't create new instance of AAPProcessor of type " + pProcessorClass);
        } catch (IllegalAccessException e) {
            loggerf.throwFatal("Can't create new instance of AAPProcessor of type " + pProcessorClass);
        }
	}
}
