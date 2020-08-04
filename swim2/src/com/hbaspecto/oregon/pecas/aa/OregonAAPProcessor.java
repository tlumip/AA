/*
 * Copyright  2005 PB Consult Inc.
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
package com.hbaspecto.oregon.pecas.aa;

import com.hbaspecto.pecas.aa.AAStatusLogger;
import com.hbaspecto.pecas.aa.activities.AggregateActivity;
import com.hbaspecto.pecas.aa.control.AASetupWithTechnologySubstitution;
import com.hbaspecto.pecas.aa.control.TAZSplitter;

// TODO compiling against HBA's version of these, check to make sure it compiles against PB's version, and update
import com.pb.common.datafile.*;
import com.pb.common.matrix.*;
import com.pb.common.util.ResourceUtil;

import com.pb.models.reference.IndustryOccupationSplitIndustryReference;
// local copies made of these two to limit dependency on PB subversion repository.
import com.pb.tlumip.model.IncomeSize2;
import com.pb.tlumip.model.WorldZoneExternalZoneUtil;

import java.io.*;
import java.text.DecimalFormat;
import java.util.*;

/**
 * @author John Abraham
 *
 */
public class OregonAAPProcessor extends AASetupWithTechnologySubstitution {

	String year;
	IndustryOccupationSplitIndustryReference indOccRef; //initially null
	String[] occupations; //initially null
	MatrixCompression compressor = null;
	ArrayList<String> activities;
	String[] activitiesColumns;
	HashMap<String, Double> activitiesI;
	HashMap<String, List<String>> nonOfficeActivities;
	private boolean visum;
	
	private static final HashMap<String, String> activityAbbrs;
	static
	{
	    activityAbbrs = new HashMap<String, String>();
	    activityAbbrs.put("RES", " Resources");
	    activityAbbrs.put("ENGY", " Energy");
	    activityAbbrs.put("CNST", " Construction");
	    activityAbbrs.put("MFG", " Manufacturing");
	    activityAbbrs.put("WHSL", " Wholesale");
	    activityAbbrs.put("RET", " Retail Store");
	    activityAbbrs.put("TRNS", "Transport");
	    activityAbbrs.put("INFO", " Information");
	    activityAbbrs.put("UTL", " Utilities");
	    activityAbbrs.put("K12", " Education k12");
	    activityAbbrs.put("GOV", " Government Administration");
	}
	
	private static final DecimalFormat DECIMAL_FORMAT = new GeneralDecimalFormat("0.############E0",10000000,.001);

	public OregonAAPProcessor() {
		super();

	}

	/**
	 * @param timePeriod time perios
	 * @param aaRb aa resource bundle
	 * @param globalRb global resource bundle
	 */
	public OregonAAPProcessor(int timePeriod, ResourceBundle aaRb) {

		super(timePeriod, aaRb);
		indOccRef = new IndustryOccupationSplitIndustryReference(IndustryOccupationSplitIndustryReference.getSplitCorrespondenceFilepath(aaRb));

		this.year = timePeriod < 10 ? "1990" : "2000";

	}
	
	// Just for testing.
	public static void main(String[] args) {
	    OregonAAPProcessor pp = new OregonAAPProcessor();
	    pp.setTimePeriod(1);
	    pp.setResourceBundles(ResourceUtil.getResourceBundle("aa"));
	    logger.info("project specific processing");
	    pp.doProjectSpecificInputProcessing();
	    logger.info("setup aa");
	    pp.setUpAA();
	}

	/* (non-Javadoc)
	 * @see com.pb.tlumip.pi.PIPProcessor#setUpPi()
	 */
	@Override
	public void setUpAA() {
	    // Not needed here - in doProjectSpecificInputProcessing.
		// createActivityTotalsIFile();
		super.setUpAA();  
	}


	@Override
	public TableDataSet loadDataSetFromYearOrAllYears(String tableName) {
	    String wName = tableName.replaceFirst("I$", "W");
	    TableDataSet table = loadTableDataSet(wName, "aa.current.data", false);
	    if (table == null) {
	        logger.info("Did not find " + wName + " in aa.current.data, looking for " + tableName);
	        return super.loadDataSetFromYearOrAllYears(tableName);
	    } else {
	        logger.info("Found year-specific " + wName + " in aa.current.data");
	        return table;
	    }
	}
	
	private Map<String, Map<String, List<Double>>> loadTechnologyOptionsAsMap() {
	    Map<String, Map<String, List<Double>>> result = new HashMap<String, Map<String, List<Double>>>();
	    TableDataSet technologyOptions = loadDataSetFromYearOrAllYears("TechnologyOptionsI");
	    String[] activities = technologyOptions.getColumnAsString("Activity");
	    // Go through the rows, mapping each activity type to its values.
	    for(int i = 0; i < technologyOptions.getRowCount(); i++) {
	        if(!result.containsKey(activities[i]))
	            result.put(activities[i], new HashMap<String, List<Double>>());
	        Map<String, List<Double>> activityBlock = result.get(activities[i]);
	        // Go through the columns, but leave out the first two, which are headers.
	        for(int j = 2; j < technologyOptions.getColumnCount(); j++) {
	            String columnLabel = technologyOptions.getColumnLabel(j + 1);
	            if(!activityBlock.containsKey(columnLabel))
	                activityBlock.put(columnLabel, new ArrayList<Double>());
	            
	            List<Double> values = activityBlock.get(columnLabel);
	            values.add(Double.valueOf(technologyOptions.getValueAt(i + 1, columnLabel)));
	        }
	    }
	    
	    return result;
	}
	

	@Override
	protected void writeProjectSpecificSplitOutputs(TAZSplitter splitter) {
		
		
		
	}
	
	@Override
	protected void readActivityTotals() {
		// Set up activity totals out of separate table.
        TableDataSet activityTotalsTable = loadTableDataSet("ActivityTotalsW","aa.current.data");
        for (int row = 1; row <= activityTotalsTable.getRowCount(); row++) {
            String name = activityTotalsTable.getStringValueAt(row, "Activity");
            AggregateActivity a = (AggregateActivity) AggregateActivity.retrieveProductionActivity(name);
            if (a==null) {
                String msg = "Missing or misspelled activity name in ActivityTotals "+name;
                logger.fatal(msg);
                throw new RuntimeException(msg);
            }
            a.setTotalAmount(activityTotalsTable.getValueAt(row, "TotalAmount"));
        }
	}
    

	public void doProjectSpecificInputProcessing() {
	    // System.out.println(ResourceUtil.getProperty(aaRb, "industry.occupation.to.split.industry.correspondence"));
	    // System.out.println(ResourceUtil.getProperty(aaRb, "world.to.external.distances"));
		indOccRef = new IndustryOccupationSplitIndustryReference( ResourceUtil.getProperty(aaRb, "industry.occupation.to.split.industry.correspondence"));
		visum = ResourceUtil.getBooleanProperty(aaRb, "aa.visum", false);
		boolean constrained = ResourceUtil.getBooleanProperty(aaRb, "constrained", false);

		if (ResourceUtil.getBooleanProperty(aaRb, "aa.buildZoneFiles", true)) {//create PECASZonesI and FloorspaceZonesI.csv file for PI to use
			createZoneFiles();
		}

		//set up calibration params.
		if(ResourceUtil.getBooleanProperty(aaRb,"pi.readMetaParameters",false ))  {
			setUpMetaParameters();
		}

		boolean doIntegratedModuleRun = ResourceUtil.getBooleanProperty(aaRb, "aa.NEDandALDInputs", false);

		if (doIntegratedModuleRun) {

			String currPath = ResourceUtil.getProperty(aaRb,"aa.current.data");
			
			deleteOldFile(currPath, "FloorspaceW.csv");
			deleteOldFile(currPath, "ActivitiesZonalValuesW.csv");
			
			createActivityTotalsWFile();
			createFloorspaceWFile();
			createActivitiesZonalValuesWFile();
			createTechnologyOptionsWFile(currPath);
			if(visum && constrained)
				fixActivityConstraintsI();
		}
	}

	private TableDataSet readCsvTable(ResourceBundle rb, String property) {
	    CSVFileReader reader = new CSVFileReader();		
		TableDataSet table;
		try {
			table = reader.readFile(new File(ResourceUtil.getProperty(rb,property)));
		} catch (IOException e) {
			logger.error(e);
			throw new RuntimeException(e);
		}
		return table;
	}

	private void createTechnologyOptionsWFile(String currPath) {
	    if (ResourceUtil.getBooleanProperty(aaRb, "aa.technologyScaling")) {
	        // Technology scaling does this better.
	        return;
	    }
        deleteOldFile(currPath, "TechnologyOptionsW.csv");
        
		logger.info("Creating new TechnologyOptionsW.csv using current NED data");
		AAStatusLogger.logText("Creating new TechnologyOptionsW.csv using current NED data");

		//First read in the activity dollar data for aa that ED produced
		//and add up the total dollars of production (don't include capitalists and govt inst.)
		TableDataSet dollars = readCsvTable(aaRb,"ned.activity_forecast.path");
		double totalDollarsProduction = 0.0;
		List<String> activitiesToSkip = Collections.emptyList(); //Arrays.asList("GOV_admn_gov","GOV_offc_off"); //todo: add activities equivalent to "Capitalists"
		outer: for(int row=1; row <= dollars.getRowCount(); row++){
		    for (String activityToSkip : activitiesToSkip) {
		        if (dollars.getStringValueAt(row,1).equalsIgnoreCase(activityToSkip))
		            continue outer;
			}
		    totalDollarsProduction += dollars.getValueAt(row,3);
		}
		double totalDollarsProductionInMillions = totalDollarsProduction/1000000.0;
		logger.debug("TotalDollarsProductionInMillions: " + totalDollarsProductionInMillions);

		//Next read in the job data for spg1 that NED produced and add up the total
		double totalJobs = 0.0;
		for(int row=1; row <= dollars.getRowCount(); row++){
			totalJobs += dollars.getValueAt(row, 2);
		}
		logger.debug("Total jobs: " + totalJobs);

		//Calculate Jobs/Dollars ratio
		double dollarsToJobs = totalDollarsProductionInMillions / totalJobs;
		logger.debug("DollarsToJobs: " + dollarsToJobs);

		//Read the 98 ratio from the properties file
		double dollarsToJobsTo98 = ResourceUtil.getDoubleProperty(aaRb, "aa.09.productivity.rate", 0.120753428);
		logger.debug("DollarsToJobs-98: " +  dollarsToJobsTo98);

		//Calculate the LaborUseScalingFactor
		double laborUseScalor = dollarsToJobsTo98 / dollarsToJobs;
		logger.debug("LaborUseScalingFactor: " + laborUseScalor);

		//        if(timePeriod == 8 && Math.abs(1-laborUseScalor) > .1){
		//            logger.warn("WARNING: Expected LaborUseScalingFactor is 1, Actual value is " + laborUseScalor);
		//        }


		if(timePeriod == 8 && laborUseScalor != 1.0d){
			logger.warn("WARNING: Expected LaborUseScalingFactor is 1, Actual value is " + laborUseScalor);
		} else {
			logger.info("LaborUseScalingFactor is: " + laborUseScalor);
		}

		//Now scale the amounts of the activities that use labor commodities
		//in the TechnologyOptionsI.csv file and write it out as TechnologyOptionsW.csv

		//Scale the use columns for labor commodities in TechnologyOptionsI by the laborUseScalor
		TableDataSet technologyOptionsITable = loadTableDataSet("TechnologyOptionsI","aa.base.data");
		String activityName;
		String commodityName;
		float value;

		for(int row = 1; row <= technologyOptionsITable.getRowCount(); row++){
			activityName = technologyOptionsITable.getStringValueAt(row,"Activity");
			if(!activityName.startsWith("HH") && !activityName.endsWith("impt") && !activityName.endsWith("expt")){
				Iterator<String> itr = indOccRef.getOccupationLabels().iterator();
				while(itr.hasNext()){
					commodityName = itr.next();        	
					if (!commodityName.equalsIgnoreCase("No_Occupation")){									
						// Check that there is a column with the same commodity name for sure. Otherwise, throw an exception
						int columnIndex =technologyOptionsITable.checkColumnPosition(commodityName+":1");        					
						value = technologyOptionsITable.getValueAt(row, columnIndex);
						value *= laborUseScalor;
						technologyOptionsITable.setValueAt(row, columnIndex, value);		
					}
				}
			}
		}

		logger.info("Writing out the TechnologyOptionsW.csv file to the current aa directory");
		//String piOutputsPath = ResourceUtil.getProperty(aaRb, "output.data"); //It should be current instead of output
		String aaCurrentPath = ResourceUtil.getProperty(aaRb, "aa.current.data"); 
		CSVFileWriter writer = new CSVFileWriter();
		writer.setMyDecimalFormat(DECIMAL_FORMAT);
		try {
			writer.writeFile(technologyOptionsITable, new File(aaCurrentPath + "TechnologyOptionsW.csv"));
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private void deleteOldFile(String currPath, String fileName) {
		File file = new File(currPath + fileName);
		if(file.exists()){
			file.delete();
			logger.info("Deleted old " + fileName + " to prepare for new file");
		}
	}

	private void createZoneFiles(){
		logger.info("Creating PECASZonesI and FloorspaceZonesI");
		AAStatusLogger.logText("Creating PECASZonesI and FloorspaceZonesI");
		TableDataSet a2b = loadTableDataSet("alpha2beta", visum? "aa.current.data" : "aa.reference.data");
		WorldZoneExternalZoneUtil wZEZUtil = new WorldZoneExternalZoneUtil(aaRb);
		int[] worldZones = wZEZUtil.getWorldZones();

		//First create FloorspaceZone file
		//get array of alpha internals and add the world zones to it.
		int[] internals = a2b.getColumnAsInt("Azone");
		int[] alphas = new int[internals.length + worldZones.length];
		System.arraycopy(internals,0,alphas,0,internals.length);
		System.arraycopy(worldZones,0, alphas,internals.length,worldZones.length);
		//now get the array of beta internals and add the world zones to it.
		int[] internalBetas = a2b.getColumnAsInt("Bzone");
		int[] betas = new int[internalBetas.length + worldZones.length];
		System.arraycopy(internalBetas,0,betas,0,internalBetas.length);
		System.arraycopy(worldZones,0, betas, internalBetas.length,worldZones.length);
		//finally get the FIPS code and add the world zones to the bottom of that
		//The FIPS is a concatenation of 2 columns from a2b.
		int[] stateFIPS = a2b.getColumnAsInt("STATEFIPS");
		int[] countyFIPS = a2b.getColumnAsInt("COUNTYFIPS");
		int[] fips = new int[stateFIPS.length + worldZones.length];
		for(int i=0; i<stateFIPS.length; i++){
			fips[i] = stateFIPS[i]*1000 + countyFIPS[i];
		}
		System.arraycopy(worldZones,0,fips,stateFIPS.length,worldZones.length);

		TableDataSet floorspaceZonesI = new TableDataSet();
		floorspaceZonesI.appendColumn(alphas, "AlphaZone");
		floorspaceZonesI.appendColumn(betas, "PecasZone");
		floorspaceZonesI.appendColumn(fips, "FIPS");

		//Now do the PECASZones file
		//The beta zone column is already done from above
		//but we also need the ZoneName column
		String[] internalNames = a2b.getColumnAsString("PECASName");
		String[] names = new String[internalNames.length + worldZones.length];
		System.arraycopy(internalNames, 0, names, 0, internalNames.length);
		for(int i=0; i<worldZones.length; i++){
			names[i+internalNames.length] = "Z" + worldZones[i] + "ImportExport";
		}
		
		// Create the External column.
		String[] ext = new String[internalNames.length + worldZones.length];
		for(int i = 0; i < internalNames.length; i++)
		    ext[i] = Boolean.toString(false);
		for(int i = internalNames.length; i < ext.length; i++)
		    ext[i] = Boolean.toString(true);

		//these arrays have repeats so we have to eliminate them
		//do that with a TreeMap which will sort and only store the
		//first of each pair
		TreeMap<Integer, String> namemap = new TreeMap<Integer,String>();
		TreeMap<Integer, String> extmap = new TreeMap<Integer, String>();
		for(int i=0; i< betas.length; i++){
			namemap.put(betas[i],names[i]);
			extmap.put(betas[i], ext[i]);
		}
		//Now put the values back into an array
		betas = new int[namemap.size()];
		names = new String[namemap.size()];
		ext = new String[extmap.size()];
		int index = 0;
		for(Integer zone : namemap.keySet()){
			betas[index] = zone;
			names[index] = namemap.get(zone);
			ext[index] = extmap.get(zone);
			index++;
		}

		TableDataSet pecasZonesI = new TableDataSet();
		pecasZonesI.appendColumn(betas, "ZoneNumber");
		pecasZonesI.appendColumn(names, "ZoneName");
		pecasZonesI.appendColumn(ext, "External");

		// write the updated PI input file
		String referencePath = ResourceUtil.getProperty(aaRb, "aa.reference.data");
		CSVFileWriter writer = new CSVFileWriter();
		try {
			writer.writeFile(floorspaceZonesI, new File(referencePath + "FloorspaceZonesI.csv"),0,DECIMAL_FORMAT);
			writer.writeFile(pecasZonesI, new File(referencePath + "PECASZonesI.csv"));
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	//createActivityTotalsIFile method for aa. This method is a substitution for createActivitWFile in pi
	private void createActivityTotalsWFile()
	{
		logger.info("Creating ActivityTotalsW.csv using current ED, SPG, and ImportsExports data.");
		AAStatusLogger.logText("Creating ActivityTotalsW.csv using current ED, SPG, and ImportsExports data.");
		boolean readSpgHHFile = (ResourceUtil.getProperty(aaRb, "aa.readHouseholdsByHHCategory").equalsIgnoreCase("true"));
		boolean readEDDollarFile = (ResourceUtil.getProperty(aaRb, "aa.readActivityDollarDataForPI").equalsIgnoreCase("true"));
		boolean updateImportsAndExports = ResourceUtil.getBooleanProperty(aaRb, "aa.updateImportsAndExports", true);
		
        activitiesI = new HashMap<String, Double>();
        // read the PI input file into a TableDataSet
        String aaInputsPath = ResourceUtil.getProperty(aaRb, "aa.base.data");

        try {           
            //activitiesI = readActivitiesIFile(aaInputsPath + "ActivitiesI.csv");
            activitiesI = readActivitiesAndSizeFromActivitiesIFile(aaInputsPath + "ActivityTotalsI.csv");
        } catch (IOException e) {
            logger.fatal(e);
            throw new RuntimeException(e);
        }
		
		CSVFileReader reader = new CSVFileReader();

		// the SPG1 File has HHCategory as rows and Size as columns
		if (readSpgHHFile) {
	        int[] householdsByIncomeSize = null;
			String hhPath = ResourceUtil.getProperty(aaRb, "spg.input.data");
			//read the SPG input file and put the data into an array by hhIndex
			TableDataSet hh = null;
			try {
				hh = reader.readFile(new File(hhPath + "householdsByHHCategory.csv"));
			} catch (IOException e) {
				e.printStackTrace();
			}

			IncomeSize2 inc = new IncomeSize2(ResourceUtil.changeResourceBundleIntoHashMap(aaRb));
			householdsByIncomeSize = new int[hh.getRowCount()];
			for (int r = 0; r < hh.getRowCount(); r++) {
				int incomeSize = inc.getIncomeSizeIndex(hh.getStringValueAt(r + 1, 1)); //HHCategory
				householdsByIncomeSize[incomeSize] = (int) hh.getValueAt(r + 1, 2); //Size
			}

			// update the values in the actI TableDataSet using the SPG1 results
			for (String activity: activities) {
				int incomeSize = inc.getIncomeSizeIndex(activity);
				if (incomeSize >= 0) {
					activitiesI.put(activity , Double.parseDouble(Integer.toString(householdsByIncomeSize[incomeSize])));
				}
			}
		}
		//The ED File has 2 columns, Activity and Dollar Amounts
		if(readEDDollarFile){
	        HashMap<String, Double> dollarsByIndustry = new HashMap<String, Double>();
			//read the ED input file and put the data into a hashmap by industry
			try {
				dollarsByIndustry = readActivityDollarDataFile(ResourceUtil.getProperty(aaRb,"ned.activity_forecast.path"));
			} catch (IOException e) {
				throw new RuntimeException("Can't find " + ResourceUtil.getProperty(aaRb,"ned.activity_forecast.path"),e);
			}

			//update the values in the actI TableDataSet using the ED results
			for (String activity: activities) {
				if (dollarsByIndustry.containsKey(activity)){            				
					activitiesI.put(activity, dollarsByIndustry.get(activity));
				}
			}
			
            HashMap<String, Double> govtAmounts = new HashMap<String, Double>();
            
		    // read the government amounts
		    try {
                govtAmounts = readGovtDataFile(ResourceUtil.getProperty(aaRb,"ned.government_forecast.path"));
            } catch (IOException e) {
                throw new RuntimeException("Can't find " + ResourceUtil.getProperty(aaRb,"ned.government_forecast.path"),e);
            }
            
            for(String activity: activities) {
                if(govtAmounts.containsKey(activity)) {
                    activitiesI.put(activity, govtAmounts.get(activity));
                }
            }
		}

		if(updateImportsAndExports && !ResourceUtil.getBooleanProperty(aaRb, "aa.technologyScaling")) {
			if(ResourceUtil.getBooleanProperty(aaRb, "aa.importAndExportFromMakeUse", true)) {
				updateImportsAndExportsFromMakeUse();
			}
			else
			{
				HashMap<String, Double> tradeAmounts = new HashMap<String, Double>();
			    // read the trade amounts
			    try {
			        tradeAmounts = readTradeDataFile(ResourceUtil.getProperty(aaRb,"ned.trade_forecast.path"));
			    } catch (IOException e) {
                    throw new RuntimeException("Can't find " + ResourceUtil.getProperty(aaRb,"ned.trade_forecast.path"),e);
                }
			    for(String activity: activities) {
			        if(tradeAmounts.containsKey(activity)) {
			            activitiesI.put(activity, tradeAmounts.get(activity));
			        }
			    }
			}
		}  //done updating the imports and exports
		
		// Add office amounts using TechnologyOptionsI, pre-read technologyoptionsi (or technologyoptionsw) to
		// figure out how much office support activity is needed.
		Map<String, Map<String, List<Double>>> technologyOptions = loadTechnologyOptionsAsMap();
		for(String activity : activities) {
		    String[] activityPieces = activity.split("_");
		    if(activityIsOffice(activityPieces)) {
		        // Find all the non-office activities that use internal services generated by this office activity.
                List<String> nonoffacts = nonOfficeActivities.get(activityPieces[0]);
                if(nonoffacts != null) {
	                double internalConsumption = 0;
	                for(String nonoffact : nonoffacts) {
	                    List<Double> weights = technologyOptions.get(nonoffact).get("OptionWeight");
	                    String useColumn = "Internal Services" + activityAbbrs.get(activityPieces[0]) + ":1";
	                    List<Double> values = technologyOptions.get(nonoffact).get(useColumn);
	                    
	                    // (need the negative sign because the consumptions are listed as negative)
	                    double averageValue = -findWeightedAverage(weights, values);
	                    
	                    // Multiply that factor by the production of that non-office activity.
	                    internalConsumption += averageValue * activitiesI.get(nonoffact);
	                }
	                
	                // Now divide the total internal consumption by the office production weight.
	                List<Double> weights = technologyOptions.get(activity).get("OptionWeight");
	                String makeColumn = "Internal Services" + activityAbbrs.get(activityPieces[0]);
	                List<Double> values = technologyOptions.get(activity).get(makeColumn);
	                
	                double averageValue = findWeightedAverage(weights, values);
	                
	                if(averageValue > 0) {
	                    double internalProduction = internalConsumption / averageValue;
	                    activitiesI.put(activity, internalProduction);
	                }
                }
		    }
		}
        
        // Write Actitivity TotalsW
        writeActivityTotalsWFile();
	}
	
	private void updateImportsAndExportsFromMakeUse() {
		Map<String, Double> makeCommodityTotals = new HashMap<String, Double>();
		Map<String, Double> useCommodityTotals = new HashMap<String, Double>();
		// Map from activities to commodities.
		Map<String, String> commoditiesByActivity = new HashMap<String, String>();
		Set<String> importActivities = new HashSet<String>();
		Set<String> exportActivities = new HashSet<String>();
		// Find out which activities are imports and which are exports.
		TableDataSet activities = loadTableDataSet("ActivitiesI", "aa.base.data", true);
		for(int row = 1; row <= activities.getRowCount(); row++) {
			String importOrExport = activities.getStringValueAt(row, "ImporterExporter");
			String activity = activities.getStringValueAt(row, "Activity");
			if(importOrExport.equalsIgnoreCase("Importer"))
				importActivities.add(activity);
			else if(importOrExport.equalsIgnoreCase("Exporter"))
				exportActivities.add(activity);
			else if(!importOrExport.equalsIgnoreCase("Neither"))
				logger.error("Invalid importer/exporter designation");
		}
		TableDataSet mu = loadTableDataSet("MakeUse", "aa.previous.data", true);
		for(int row = 1; row <= mu.getRowCount(); row++) {
			String activity = mu.getStringValueAt(row, "Activity");
			String commodity = mu.getStringValueAt(row, "Commodity");
			String moru = mu.getStringValueAt(row, "MorU");
			double amount = mu.getValueAt(row, "Amount");
			// For exports:
			// Add up previous year's total internal make by commodity (exclude imports).
			if(!importActivities.contains(activity) && !exportActivities.contains(activity) && moru.equals("M")) {
				if(!makeCommodityTotals.containsKey(commodity))
					makeCommodityTotals.put(commodity, 0.0);
				double newTotal = makeCommodityTotals.get(commodity) + amount;
				makeCommodityTotals.put(commodity, newTotal);
			}
			// Find out which exporting activity corresponds to each commodity.
			if(exportActivities.contains(activity))
				commoditiesByActivity.put(activity, commodity);
			// For imports:
			// Add up previous year's total internal use by commodity (exclude exports).
			if(!importActivities.contains(activity) && !exportActivities.contains(activity) && moru.equals("U")) {
				if(!useCommodityTotals.containsKey(commodity))
					useCommodityTotals.put(commodity, 0.0);
				double newTotal = useCommodityTotals.get(commodity) + amount;
				useCommodityTotals.put(commodity, newTotal);
			}
			// Find out which importing activity corresponds to each commodity.
			if(importActivities.contains(activity))
				commoditiesByActivity.put(activity, commodity);
		}
		// Write the totals by activity to ActivityTotalsW.
		for(String activity : activitiesI.keySet()) {
			// For exports.
			if(exportActivities.contains(activity)) {
				double newAmount = makeCommodityTotals.get(commoditiesByActivity.get(activity));
				activitiesI.put(activity, newAmount);
			}
			// For imports.
			if(importActivities.contains(activity)) {
				double newAmount = -useCommodityTotals.get(commoditiesByActivity.get(activity));
				activitiesI.put(activity, newAmount);
			}
		}
	}
	
	private double findWeightedAverage(List<Double> weights, List<Double> values) {
	    double totalWeightedValue = 0;
        double totalWeight = 0;
        
        // Average the different options according to their weights.
        for(int i = 0; i < values.size(); i++) {
            totalWeightedValue += values.get(i) * weights.get(i);
            totalWeight += weights.get(i);
        }
        
        return totalWeightedValue / totalWeight;
	}

	private void writeActivityTotalsWFile(){
		// write the updated PI input file
		String aaCurrentPath = ResourceUtil.getProperty(aaRb, "aa.current.data");
		String filePath = aaCurrentPath + "ActivityTotalsW.csv";

		try {
			BufferedWriter bw = new BufferedWriter(new FileWriter(new File(filePath).getAbsolutePath()));

			logger.info("Writing ActivityTotalsW file: " + filePath);
			String headerLine = "Activity,TotalAmount";

			bw.write(headerLine);
			bw.newLine();
			for (String activity: activities) {
				String currentLine = activity + ",";
				currentLine += activitiesI.get(activity ) + ",";

				//remove final comma
				currentLine = currentLine.substring(0, currentLine.length()-1);
				bw.write(currentLine);
				bw.newLine();
			}
			bw.flush();
			bw.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private HashMap<String, Double> readActivitiesAndSizeFromActivitiesIFile(String filePath) throws IOException {
		HashMap<String, Double> activitiesI = new HashMap<String, Double>();
		activities = new ArrayList<String>();
		nonOfficeActivities = new HashMap<String, List<String>>();
		for(String abbr : activityAbbrs.keySet())
		    nonOfficeActivities.put(abbr, new ArrayList<String>());
		try {
			BufferedReader br = new BufferedReader(new FileReader(new File(filePath)));
			String s;
			StringTokenizer st;
			s = br.readLine();

			while ((s = br.readLine()) != null) {
				st = new StringTokenizer(s, ",");
				String activity = st.nextToken();
				if (activity.startsWith("\""))
					activity = activity.substring(1, activity.length()-1);
				activities.add(activity);
				// activitiesI.put(activity, new Double(0));
				activitiesI.put(activity , Double.parseDouble(st.nextToken()));//size
				
				// Check if this activity is an office activity - otherwise put it in the office map.
				String[] activityPieces = activity.split("_");
				if(nonOfficeActivities.containsKey(activityPieces[0]) && !activityIsOffice(activityPieces))
				    nonOfficeActivities.get(activityPieces[0]).add(activity);
			}
			br.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return activitiesI;
	}
	
	private boolean activityIsOffice(String[] activity) {
	    // Office types have "off" as the 3rd element, but if there is also a 4th element "li", it doesn't count as office.
	    boolean result = activity.length >= 3;
	    result = result && activity[2].equals("off");
	    result = result && !(activity.length >= 4 && activity[3].equals("li"));
	    return result;
	}

	private HashMap<String, Double> readActivityDollarDataFile(String filePath) throws IOException {
		HashMap<String, Double> activityDollarData = new HashMap<String, Double>();
		try {
			BufferedReader br = new BufferedReader(new FileReader(new File(filePath)));
			String s;
			StringTokenizer st;
			//This is the header line
			br.readLine();
			while ((s = br.readLine()) != null) {
				if (s.startsWith("#")) continue;    // skip comment records
				st = new StringTokenizer(s, ",");
				String activity = st.nextToken();
				if (activity.startsWith("\""))
					activity = activity.substring(1, activity.length()-1);
		        st.nextToken(); // skip employment entry
				activityDollarData.put(activity,   // activity
						Double.parseDouble(st.nextToken()) );  // factor
			}
			br.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return activityDollarData;
	}
	
	private HashMap<String, Double> readTradeDataFile(String filePath) throws IOException {
        HashMap<String, Double> tradeData = new HashMap<String, Double>();
        try {
            BufferedReader br = new BufferedReader(new FileReader(new File(filePath)));
            String s;
            StringTokenizer st;
            //This is the header line
            br.readLine();
            while ((s = br.readLine()) != null) {
                if (s.startsWith("#")) continue;    // skip comment records
                st = new StringTokenizer(s, ",");
                String activity = st.nextToken();
                if (activity.startsWith("\""))
                    activity = activity.substring(1, activity.length()-1);
                tradeData.put(activity,   // activity
                        Double.parseDouble(st.nextToken()) );  // factor
            }
            br.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return tradeData;
    }
	
	private HashMap<String, Double> readGovtDataFile(String filePath) throws IOException {
        HashMap<String, Double> tradeData = new HashMap<String, Double>();
        try {
            BufferedReader br = new BufferedReader(new FileReader(new File(filePath)));
            String s;
            StringTokenizer st;
            //This is the header line
            br.readLine();
            while ((s = br.readLine()) != null) {
                if (s.startsWith("#")) continue;    // skip comment records
                st = new StringTokenizer(s, ",");
                String activity = st.nextToken();
                if (activity.startsWith("\""))
                    activity = activity.substring(1, activity.length()-1);
                tradeData.put(activity,   // activity
                        Double.parseDouble(st.nextToken()) );  // factor
            }
            br.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return tradeData;
    }

	/* This method will read in the FloorspaceI.csv file that was output by ALD
	 * and replace the aglog data with the PIAgForestFloorspace.csv base year data.  This will
	 * happen every year.
	 */
	public void createFloorspaceWFile() {
		logger.info("Creating new FloorspaceW.csv using current ALD AgForestFloorspace.csv file");
		AAStatusLogger.logText("Creating new FloorspaceW.csv using current ALD AgForestFloorspace.csv file");
		//Read in the FloorspaceI.csv file that was produced by ALD
		TableDataSet floorspaceTable = loadTableDataSet("FloorspaceI","ald.input.data");
		if (floorspaceTable == null)
			throw new RuntimeException("floorspaceTable is null.");
		//And the AgForestFloorspace.csv file that was created by Tara
		TableDataSet piAgForTable = loadTableDataSet("AgForestFloorspace", "aa.base.data");
		if (piAgForTable == null)
			throw new RuntimeException("AgForTable is null.");
		// Find the maximum alphazone in the file - we haven't yet read in the FloorspaceZones file
		int azoneCol = piAgForTable.checkColumnPosition("AZone");
		int flrTypeCol = piAgForTable.checkColumnPosition("FLRName");
		int mSqftCol = piAgForTable.checkColumnPosition("BldgMSQFT");
//		int max = 0;
//		for(int row = 1; row <= piAgForTable.getRowCount(); row ++){
//			if((int)piAgForTable.getValueAt(row,azoneCol) > max)
//				max = (int)piAgForTable.getValueAt(row,azoneCol);
//		}
//		//Take the data out of the AgForest file and put it into temporary arrays.
//		float[] agSpaceByZone = new float[max + 1];
//		float[] forSpaceByZone = new float[max + 1];
		Map<Integer, Float> agSpaceByZone = new HashMap<Integer, Float>();
		Map<Integer, Float> forSpaceByZone = new HashMap<Integer, Float>();

		for(int row = 1; row <= piAgForTable.getRowCount(); row ++){
			if(piAgForTable.getStringValueAt(row,flrTypeCol).equalsIgnoreCase("FLR Agriculture")){
				agSpaceByZone.put((int) piAgForTable.getValueAt(row,azoneCol), piAgForTable.getValueAt(row,mSqftCol));
			}
			else if(piAgForTable.getStringValueAt(row,flrTypeCol).equalsIgnoreCase("FLR Logging")){
				forSpaceByZone.put((int) piAgForTable.getValueAt(row,azoneCol), piAgForTable.getValueAt(row,mSqftCol));
			}
			else {
				logger.fatal("Bad floor type name in AgForestFloorspace file");
			    throw new IllegalStateException("Bad floor type name in AgForestFloorspace file");
				//TODO - send to node exception log
			}
		}
		//Now go thru the floorspaceTable and replace the data with the data from our arrays, if bootstrapping from AA needs 'commodity', 'taz', 'quantity' headers but if working with ALD probably needs the other headers
		//flrTypeCol = floorspaceTable.checkColumnPosition("FLRName");
		//azoneCol = floorspaceTable.checkColumnPosition("AZone");
		//mSqftCol = floorspaceTable.checkColumnPosition("BldgMSQFT");
		flrTypeCol = floorspaceTable.checkColumnPosition("commodity");
		azoneCol = floorspaceTable.checkColumnPosition("taz");
		mSqftCol = floorspaceTable.checkColumnPosition("quantity");
		String flrType = null;
		int azone = 0;
		float mSqft = 0;
		int replaceCount = 0;
		for(int row = 1; row <= floorspaceTable.getRowCount(); row++){
			flrType = floorspaceTable.getStringValueAt(row,flrTypeCol);
			if(!flrType.equalsIgnoreCase("FLR Agriculture") && !flrType.equalsIgnoreCase("FLR Logging"))
				continue;  //this row does not need replacing, go to next row
			else {
				azone = (int)floorspaceTable.getValueAt(row,azoneCol);
				if(flrType.equalsIgnoreCase("FLR Agriculture")){
				    Float value = agSpaceByZone.get(azone);
					mSqft = value == null? 0 : value;
					floorspaceTable.setValueAt(row,mSqftCol,mSqft);
					replaceCount++;
				}else if(flrType.equalsIgnoreCase("FLR Logging")){
				    Float value = forSpaceByZone.get(azone);
					mSqft = value == null? 0 : value;
					floorspaceTable.setValueAt(row,mSqftCol, mSqft);
					replaceCount++;
				}
			}
		}
		// Change column headers to match AA's requirements.
		floorspaceTable.setColumnLabels(new String[] {"TAZ", "Commodity", "Quantity"});
		
		if(logger.isDebugEnabled()) {
			logger.debug("Replaced " + replaceCount + " values in the Floorspace Table");
		}
		//Now write out the FloorspaceW.csv file
		String aaCurrentPath = ResourceUtil.getProperty(aaRb, "aa.current.data");
		CSVFileWriter writer = new CSVFileWriter();
		writer.setMyDecimalFormat(DECIMAL_FORMAT);
		try {
			writer.writeFile(floorspaceTable, new File(aaCurrentPath + "FloorspaceW.csv"));
		} catch (IOException e) {
			e.printStackTrace();
		}

	}
	
	/* This method will read in the base year ActivitiesZonalValues file and
	 * update the "Quantity" field with the previous year's construction values from ALD's
	 * Increment.csv file, and the "Quantity" number from the previous year's ActivityLocations.csv
	 * aa file
	 */
	private void createActivitiesZonalValuesWFile(){
		logger.info("Creating new ActivitiesZonalValuesW.csv using current PI and ALD data");
		AAStatusLogger.logText("Creating new ActivitesZonalValuesW.csv using current PI and ALD data");
		
		//Read in the alpha2beta.csv file from the reference directory.  This will set
		//up a look-up array so that we know which alpha zones are in which beta zones
		//It will also set the max alpha and max beta zone which we can "get" from a2bMap.
		TableDataSet alpha2betaTable = loadTableDataSet("FloorspaceZonesI","aa.reference.data");
		AlphaToBeta a2bMap = new AlphaToBeta(alpha2betaTable, "AlphaZone", "PecasZone");
		
		// The resulting zonal values table.
		TableDataSet zonalValuesTable;
		if(ResourceUtil.getBooleanProperty(aaRb, "aa.useActivitiesZonalValuesI", false)) {
			logger.info("Creating ActivitiesZonalValuesW from ActivitiesZonalValuesI and previous year's ActivityLocations");
			zonalValuesTable = createActivitiesZonalValuesWFromZonalValuesI(a2bMap);
		}
		else {
			logger.info("Creating ActivitiesZonalValuesW directly from previous year's ActivityLocations");
			zonalValuesTable = loadTableDataSet("ActivityLocations", "aa.previous.data");
			
			// Decay the zone constants.
			float decay = (float) ResourceUtil.getDoubleProperty(aaRb, "aa.zoneConstantDecayFactor", 0);
			if(decay > 0) {
			    logger.info("Applying a decay factor of " + decay + " to the previous year's zone constants");
			    
			    int constCol = zonalValuesTable.getColumnPosition("ZoneConstant");
		        for(int r=1; r<=zonalValuesTable.getRowCount();r++){
	                float curConst = zonalValuesTable.getValueAt(r, constCol);
	                if(!Float.isInfinite(curConst))
	                    zonalValuesTable.setValueAt(r, constCol, (1 - decay) * curConst);
		        }
			}
		}
		//Now we need to read in the floorspace quantities from the ALD Increments.csv file
		//add up the sqft by betazone and then update the "SizeTerm" in the ZonalValuesTable
		int nFlrNames = 0;                                   //use thess to make sure the increments
		int[] nAddsByZone = new int[a2bMap.getMaxAlphaZone() + 1]; //file only lists each flrType/zone pair once.
		String currentFlrName = "";

		logger.info("\tChecking for current year ALD Increments.csv file");
		TableDataSet incrementsTable = loadTableDataSet("Increments","ald.input.data");
		logger.info("\t\tFound it! We are now updating the construction size terms....");
		float[] mSqftByAZoneRes = new float[a2bMap.getMaxAlphaZone() + 1];
		float[] mSqftByAZoneNRes = new float[a2bMap.getMaxAlphaZone() + 1];
		for(int r=1; r<= incrementsTable.getRowCount(); r++){
			String flrName = incrementsTable.getStringValueAt(r,"FLRName");
			if(!flrName.equals(currentFlrName)){
				nFlrNames++;
				currentFlrName = flrName;
			}
			int zone = (int)incrementsTable.getValueAt(r,"AZone");
			float mSqft = incrementsTable.getValueAt(r,"IncSQFT");
			if(mSqft < 0) mSqft=0;  //don't add up negative numbers
			if (incrementsTable.getStringValueAt(r,"FLRType").equals("Residential"))
			    mSqftByAZoneRes[zone] += mSqft;
			else
			    mSqftByAZoneNRes[zone] += mSqft;
			nAddsByZone[zone]++;
		}
		//Each floor name should have had 1 value for each zone so each element in the nAddsByZone array
		//should be equal to the nFlrNames.  If not, we have a problem with the Increments.csv file
		logger.info("\t\t\tEach non-external zone should have added up " + nFlrNames + " values");
		int[] aZones = a2bMap.getAlphaExternals1Based();
		int[] externals = new WorldZoneExternalZoneUtil(aaRb).getWorldZones();
		Collection<Integer> externalCol = new HashSet<Integer>();
		for (int external : externals)
		    externalCol.add(external);
		for (int i = 1; i < aZones.length; i++){
			if(nAddsByZone[aZones[i]] != nFlrNames && !externalCol.contains(aZones[i])){
				logger.error("\t\t\t\tZone " + aZones[i] + " added up " + nAddsByZone[aZones[i]]);
				logger.error("\t\t\t\tCheck the ald/Increments.csv file - there is an error");
			}
		}
		logger.info("\t\t\tIf no error messages appeared, proceed");

		//Now that we have the total by alpha zone we need to get the total by beta zone
		float[] mSqftByBZoneRes = new float[a2bMap.getMaxBetaZone() + 1];
		float[] mSqftByBZoneNRes = new float[a2bMap.getMaxBetaZone() + 1];
		for(int i=1; i<aZones.length; i++){
			int betaZone = a2bMap.getBetaZone(aZones[i]);
			if(logger.isDebugEnabled()) {
				logger.debug("alphaZone " + aZones[i] + " = betaZone " + betaZone);
			}
			mSqftByBZoneRes[betaZone] += mSqftByAZoneRes[aZones[i]];
			mSqftByBZoneNRes[betaZone] += mSqftByAZoneNRes[aZones[i]];
		}

		//And then update the ZonalValuesTable
		int sizeTermCol = zonalValuesTable.getColumnPosition("Size");
		for(int r=1; r<=zonalValuesTable.getRowCount();r++){
			String activity = zonalValuesTable.getStringValueAt(r,"Activity");
			if (activity.equalsIgnoreCase("CNST_res_xxx")) {
				int zone = (int)zonalValuesTable.getValueAt(r,"ZoneNumber");
				zonalValuesTable.setValueAt(r,sizeTermCol,mSqftByBZoneRes[zone]);
			} else if (activity.equalsIgnoreCase("CNST_nres_xxx")) {
				int zone = (int)zonalValuesTable.getValueAt(r,"ZoneNumber");
				zonalValuesTable.setValueAt(r,sizeTermCol,mSqftByBZoneNRes[zone]);
			}
		} //the SizeTerms have been updated

		//OK, now write out the zonalValuesTable as ActivitiesZonalValuesW.csv into
		//the current aa directory
		logger.info("Writing out the ActivitiesZonalValuesW.csv file to the current aa directory");
		String aaCurrentPath = ResourceUtil.getProperty(aaRb, "aa.current.data");
		CSVFileWriter writer = new CSVFileWriter();
        writer.setMyDecimalFormat(DECIMAL_FORMAT);
		try {
			writer.writeFile(zonalValuesTable, new File(aaCurrentPath + "ActivitiesZonalValuesW.csv"));
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	private TableDataSet createActivitiesZonalValuesWFromZonalValuesI(AlphaToBeta a2bMap) {
		//First read in the t0/pi/ActivitiesZonalValuesI.csv file
		TableDataSet zonalValuesTable = loadTableDataSet("ActivitiesZonalValuesI","aa.base.data");

		//Next read in the previous year's aa/ActivityLocations.csv file.
		// ( if this file doesn't exist we will assume that it is year 1 and we do not
		//   need to integrate the 2 files)
		logger.info("\tChecking for previous year's ActivityLocations.csv file");
		TableDataSet actLocationTable = loadTableDataSet("ActivityLocations","aa.previous.data");
		if(actLocationTable != null){  // null implies that the file was not there so can skip this step
			logger.info("\t\tFound it!  Updating Initial Quantities....");
			HashMap<String, float[]> activityQuantities = new HashMap<String, float[]>();
			for(int r= 1; r<= actLocationTable.getRowCount();r++){
				String activity = actLocationTable.getStringValueAt(r,"Activity"); //get the activityName
				int zone = (int) actLocationTable.getValueAt(r,"ZoneNumber");
				float qty = actLocationTable.getValueAt(r,"Quantity");
				if(!activityQuantities.containsKey(activity)){  //this activity is not yet in the Map
					float[] qtyByZone = new float[a2bMap.getMaxBetaZone() + 1];
					qtyByZone[zone] = qty;
					activityQuantities.put(activity,qtyByZone);
				}else{ //this activity is already in the map, so get the array and put in the value
					activityQuantities.get(activity)[zone]=qty;
				}
			} //next row of table

			//Now that all the info is in the HashMap, go thru the ZonalValuesTable and update
			//the info
			int initQtyCol = zonalValuesTable.getColumnPosition("Quantity");
			int count = 0;
			Set<String> activitiesNotFound = new HashSet<String>();
			for(int r=1; r<= zonalValuesTable.getRowCount(); r++){
				String activity = zonalValuesTable.getStringValueAt(r,"Activity");
				int zone = (int) zonalValuesTable.getValueAt(r,"ZoneNumber");
				if(activityQuantities.containsKey(activity))
				{
					float qty = ((float[])activityQuantities.get(activity))[zone];
					zonalValuesTable.setValueAt(r,initQtyCol,qty);
					count++;
				}
				else
					activitiesNotFound.add(activity);
			}
			for(String activity : activitiesNotFound)
				logger.warn("\t\t\tActivity " + activity + " not found in ActivityLocations");
			logger.info("\t\t\tWe replaced " + count + " quantities in the ZonalValuesTable");
		}//  We are done integrating the ActivityLocations info into ZonalValuesTable
		else logger.info("\tNo base year ActivityLocations file - Do the ConstructionSizeTerm updates");
		
		return zonalValuesTable;
	}
	
	private void fixActivityConstraintsI() {
		// Restore original case of activity names after Visum mangles them.
		TableDataSet actTable = loadTableDataSet("ActivityConstraintsI", "aa.current.data");
		for(int r = 1; r <= actTable.getRowCount(); r++) {
			String activity = actTable.getStringValueAt(r, "activity");
			String[] bits = activity.split("_", 2);
			if(bits.length == 2) {
				activity = bits[0] + "_" + bits[1].toLowerCase();
				actTable.setStringValueAt(r, "activity", activity);
			}
		}
		String path = ResourceUtil.getProperty(aaRb, "aa.current.data");
		CSVFileWriter writer = new CSVFileWriter();
		writer.setMyDecimalFormat(DECIMAL_FORMAT);
		try {
			writer.writeFile(actTable, new File(path + "ActivityConstraintsI.csv"));
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Read in some parameter functions, and adjust the parameters as appropriate
	 *
	 */
	private void setUpMetaParameters() {
		// TODO remove this probably, it looks like it's just related to calibration.
		logger.info("Generating parameters from metaparameters");
		AAStatusLogger.logText("Generating parameters from metaparameters");
		CSVFileReader reader = new CSVFileReader();
		CSVFileWriter writer = new CSVFileWriter();

		
		String aaInputsPath = ResourceUtil.getProperty(aaRb,"aa.base.data");

		// No longer uses a File argument, because it could be a URL
		//        reader.setMyDirectory(new File(piInputsPath));
		reader.setMyDirectory(aaInputsPath);


		writer.setMyDirectory(new File(aaInputsPath));
		writer.setMyDecimalFormat(DECIMAL_FORMAT);
		TableDataSetCollection myCollection = new TableDataSetCollection(reader,writer);
		TableDataSetIndex metaParamIndex = new TableDataSetIndex(myCollection,"MetaParameters");
		String[] temp = {"ParameterName"};
		metaParamIndex.setIndexColumns(temp, new String[0]);
		String[] smallHouseholds = {
				"HH0to5k1to2",
				"HH5to10k1to2",
				"HH10to15k1to2",
				"HH15to20k1to2",
				"HH20to30k1to2",
				"HH30to40k1to2",
				"HH40to50k1to2",
				"HH50to70k1to2",
		"HH70kUp1to2"};
		String[] largeHouseholds = {
				"HH0to5k3plus",
				"HH5to10k3plus",
				"HH10to15k3plus",
				"HH15to20k3plus",
				"HH20to30k3plus",
				"HH30to40k3plus",
				"HH40to50k3plus",
				"HH50to70k3plus",
		"HH70kUp3plus"};

		TableDataSetIndex makeUseIndex = new TableDataSetIndex(myCollection,"MakeUseI");
		String[] temp2 = {"Activity","Commodity","MorU"};
		makeUseIndex.setIndexColumns(temp2, new String[0]);

		String[] parameterName = new String[1];
		int[] nothing = new int[0];
		TableDataSet parameters = metaParamIndex.getMyTableDataSet();
		int column = parameters.checkColumnPosition("ParameterValue");

		// get parameters 1 at a time
		parameterName[0] = "MHConstant";
		int[] rows = metaParamIndex.getRowNumbers(parameterName,nothing);
		double mhConstant = parameters.getValueAt(rows[0],column);
		parameterName[0] = "MFConstant";
		rows = metaParamIndex.getRowNumbers(parameterName,nothing);
		double mfConstant = parameters.getValueAt(rows[0],column);
		parameterName[0] = "ATConstant";
		rows = metaParamIndex.getRowNumbers(parameterName,nothing);
		double atConstant = parameters.getValueAt(rows[0],column);
		parameterName[0] = "RuralConstant";
		rows = metaParamIndex.getRowNumbers(parameterName,nothing);
		double ruralConstant = parameters.getValueAt(rows[0],column);
		parameterName[0] = "SFDLargeHHConstant";
		rows = metaParamIndex.getRowNumbers(parameterName,nothing);
		double sfdLargeHHConstant = parameters.getValueAt(rows[0],column);
		parameterName[0] = "MHIncome";
		rows = metaParamIndex.getRowNumbers(parameterName,nothing);
		double mhIncome = parameters.getValueAt(rows[0],column);
		parameterName[0] = "MFIncome";
		rows = metaParamIndex.getRowNumbers(parameterName,nothing);
		double mfIncome = parameters.getValueAt(rows[0],column);
		parameterName[0] = "ATIncome";
		rows = metaParamIndex.getRowNumbers(parameterName,nothing);
		double atIncome = parameters.getValueAt(rows[0],column);
		parameterName[0] = "RuralIncome";
		rows = metaParamIndex.getRowNumbers(parameterName,nothing);
		double ruralIncome = parameters.getValueAt(rows[0],column);
		parameterName[0] = "SFDLargeHHIncome";
		rows = metaParamIndex.getRowNumbers(parameterName,nothing);
		double sfdLargeHHIncome = parameters.getValueAt(rows[0],column);

		String[] makeUseKeys = new String[3];
		makeUseKeys[2] = "U";
		TableDataSet makeUseTable = makeUseIndex.getMyTableDataSet();
		int constantColumn = makeUseTable.checkColumnPosition("UtilityOffset");
		for (int incomeCat = 0;incomeCat<smallHouseholds.length;incomeCat++) {
			// mobile home constants
			makeUseKeys[0] = smallHouseholds[incomeCat];
			makeUseKeys[1] = "FLR MH";
			rows = makeUseIndex.getRowNumbers(makeUseKeys,nothing);
			makeUseTable.setValueAt(rows[0],constantColumn,(float) (mhConstant+incomeCat*mhIncome));
			makeUseKeys[0] = largeHouseholds[incomeCat];
			rows = makeUseIndex.getRowNumbers(makeUseKeys,nothing);
			makeUseTable.setValueAt(rows[0],constantColumn,(float) (mhConstant+incomeCat*mhIncome));

			// rural mobile home constants
			makeUseKeys[0] = smallHouseholds[incomeCat];
			makeUseKeys[1] = "FLR RRMH";
			rows = makeUseIndex.getRowNumbers(makeUseKeys,nothing);
			makeUseTable.setValueAt(rows[0],constantColumn,(float) (mhConstant+ruralConstant+incomeCat*(mhIncome+ruralIncome)));
			makeUseKeys[0] = largeHouseholds[incomeCat];
			rows = makeUseIndex.getRowNumbers(makeUseKeys,nothing);
			makeUseTable.setValueAt(rows[0],constantColumn,(float) (mhConstant+ruralConstant+incomeCat*(mhIncome+ruralIncome)));

			// multi family constants
			makeUseKeys[0] = smallHouseholds[incomeCat];
			makeUseKeys[1] = "FLR MF";
			rows = makeUseIndex.getRowNumbers(makeUseKeys,nothing);
			makeUseTable.setValueAt(rows[0],constantColumn,(float) (mfConstant+incomeCat*mfIncome));
			makeUseKeys[0] = largeHouseholds[incomeCat];
			rows = makeUseIndex.getRowNumbers(makeUseKeys,nothing);
			makeUseTable.setValueAt(rows[0],constantColumn,(float) (mfConstant+incomeCat*mfIncome));

			// attached home constants
			makeUseKeys[0] = smallHouseholds[incomeCat];
			makeUseKeys[1] = "FLR AT";
			rows = makeUseIndex.getRowNumbers(makeUseKeys,nothing);
			makeUseTable.setValueAt(rows[0],constantColumn,(float) (atConstant+incomeCat*atIncome));
			makeUseKeys[0] = largeHouseholds[incomeCat];
			rows = makeUseIndex.getRowNumbers(makeUseKeys,nothing);
			makeUseTable.setValueAt(rows[0],constantColumn,(float) (atConstant+incomeCat*atIncome));

			// rural sfd constants
			makeUseKeys[0] = smallHouseholds[incomeCat];
			makeUseKeys[1] = "FLR RRSFD";
			rows = makeUseIndex.getRowNumbers(makeUseKeys,nothing);
			makeUseTable.setValueAt(rows[0],constantColumn,(float) (ruralConstant+incomeCat*ruralIncome));
			makeUseKeys[0] = largeHouseholds[incomeCat];
			rows = makeUseIndex.getRowNumbers(makeUseKeys,nothing);
			makeUseTable.setValueAt(rows[0],constantColumn,(float) (ruralConstant+sfdLargeHHConstant+incomeCat*(ruralIncome+sfdLargeHHIncome)));

			// sfd constants
			makeUseKeys[0] = largeHouseholds[incomeCat];
			makeUseKeys[1] = "FLR SFD";
			rows = makeUseIndex.getRowNumbers(makeUseKeys,nothing);
			makeUseTable.setValueAt(rows[0],constantColumn,(float) (sfdLargeHHConstant+incomeCat*sfdLargeHHIncome));

		}
		makeUseIndex.dispose();
		metaParamIndex.dispose();
		myCollection.flush();
		myCollection.close();
	}

	/* (non-Javadoc)
	 * @see com.pb.tlumip.pi.PIPProcessor#setUpTransportConditions(java.lang.String[])
	 */
// Replaced with generic version, i.e. no longer overridden
	/*protected void setUpTransportConditions(String[] skimNames) {
		logger.info("Setting up Transport Conditions");
		String path1 = ResourceUtil.getProperty(aaRb, "pt.input.data");
		String path2 = ResourceUtil.getProperty(aaRb, "ts.input.data");

		SomeSkims someSkims = new SomeSkims(path1, path2);
		TransportKnowledge.globalTransportKnowledge = someSkims;
		for (int s=0;s<skimNames.length;s++) {
			someSkims.addZipMatrix(skimNames[s]);
		}
	} */

	/* (non-Javadoc)
	 * @see com.pb.tlumip.pi.PIPProcessor#writeOutputs()
	 */
	public void writeOutputs() {
		super.writeOutputs();
		writeLaborConsumptionAndProductionFiles(); //writes out laborDollarProduction.csv,laborDollarConsumption.csv
	}

	public void writeOutputs(boolean writeItAll) {
	    super.writeOutputs(writeItAll);
	    writeLaborConsumptionAndProductionFiles();
	}
	
	public void writeLaborConsumptionAndProductionFiles() {
		boolean writeOregonOutputs = ResourceUtil.getBooleanProperty(aaRb, "pi.oregonOutputs", false);

		if (!writeOregonOutputs) {
			logger.info("Not writing Oregon-Specific Outputs (labor consumption and production)");
			return;
		}
		logger.info("Writing labor consumption and production");
		AAStatusLogger.logText("Writing labor consumption and production");

        // Read in occupation types.
        String occupations = ResourceUtil.getProperty(aaRb, "aa.oregonOccupations");
        String[] occarray = occupations.split(",");
        HashMap<String, Integer> occmap = new HashMap<String, Integer>();
        for(int i = 0; i < occarray.length; i++)
            occmap.put(occarray[i], i);
        
		
		writeSimpleMakeUseData(occarray, occmap);
		
		writeDetailedMakeUseData(occarray, occmap);
		}
	
	// Assumes that each zone/occupation type combination appears exactly once, but they
	// do not have to be in the correct order and zone numbers do not have to be contiguous.
	private void writeSimpleMakeUseData(String[] occarray, Map<String, Integer> occmap) {
        int numCols = occarray.length + 2;
        String aaCurrentPath = ResourceUtil.getProperty(aaRb, "aa.current.data");
        BufferedReader makeuse = null;
        BufferedWriter make = null;
        BufferedWriter use = null;
        try {
            makeuse = new BufferedReader(new FileReader(aaCurrentPath + "FloorspaceZoneTotalMakeUse.csv"));
            make = new BufferedWriter(new FileWriter(aaCurrentPath + "laborDollarProductionSum.csv"));
            use = new BufferedWriter(new FileWriter(aaCurrentPath + "laborDollarConsumptionSum.csv"));
            TreeMap<Integer, String[]> productionSums = new TreeMap<Integer, String[]>();
            TreeMap<Integer, String[]> consumptionSums = new TreeMap<Integer, String[]>();
            String[] header = new String[numCols];
            header[0] = "zoneNumber";
            header[1] = "No_Occupation";
            System.arraycopy(occarray, 0, header, 2, occarray.length);
            productionSums.put(-1, header);
            consumptionSums.put(-1, header);
            
            String line;
            makeuse.readLine();
            do {
                line = makeuse.readLine();
                if(line != null) {
                    String[] fields = line.split(",");
                    String occ = fields[0];
                    int zone = Integer.parseInt(fields[1]);
                    String made = fields[2];
                    String used = fields[3];
                    if(occmap.containsKey(occ)) {
                        if(!productionSums.containsKey(zone)) {
                            String[] row = new String[numCols];
                            Arrays.fill(row, "0");
                            productionSums.put(zone, row);
                        }
                        productionSums.get(zone)[occmap.get(occ) + 2] = made;
                        if(!consumptionSums.containsKey(zone)) {
                            String[] row = new String[numCols];
                            Arrays.fill(row, "0");
                            consumptionSums.put(zone, row);
                        }
                        // Flip the sign. The new file format uses the opposite sign convention.
                        consumptionSums.get(zone)[occmap.get(occ) + 2] = String.valueOf(-Double.parseDouble(used));
                    }
                }
            } while(line != null);
            
            // Add the first column and write out.
            for(int zone : productionSums.keySet()) {
                String[] row = productionSums.get(zone);
                if(zone > 0)
                    row[0] = String.valueOf(zone);
                make.write(join(row, ","));
                make.newLine();
            }
            for(int zone : consumptionSums.keySet()) {
                String[] row = consumptionSums.get(zone);
                if(zone > 0)
                    row[0] = String.valueOf(zone);
                use.write(join(row, ","));
                use.newLine();
            }
            
        } catch(IOException e) {
            logger.error(e);
            throw new RuntimeException(e);
        } finally {
            try {
                if(makeuse != null)
                    makeuse.close();
                if(make != null)
                    make.close();
                if(use != null)
                    use.close();
            } catch(IOException e) {
                logger.error(e);
                throw new RuntimeException(e);
            }
        }
	}
	
	// Assumes that each zone/occupation type/household category combination appears exactly once,
	// but they do not have to be in the correct order and zone numbers do not have to be contiguous.
	// Household categories are written out in the order in which they are first encountered.
	private void writeDetailedMakeUseData(String[] occarray, Map<String, Integer> occmap) {
        String aaCurrentPath = ResourceUtil.getProperty(aaRb, "aa.current.data");
        BufferedReader input = null;
        BufferedWriter output = null;
	    try {
	        input = new BufferedReader(new FileReader(aaCurrentPath + "TAZDetailedMake.csv"));
	        output = new BufferedWriter(new FileWriter(aaCurrentPath + "laborDollarProduction.csv"));
	        
	        // Read in household categories.
	        String hhcategories = ResourceUtil.getProperty(aaRb, "aa.oregonHHtypes");
	        String[] hharray = hhcategories.split(",");
	        HashMap<String, Integer> hhmap = new HashMap<String, Integer>();
	        for(int i = 0; i < hharray.length; i++)
	            hhmap.put(hharray[i], i);

	        int numCols = hharray.length + 2;
	        String[] colheaders = new String[numCols];
	        colheaders[0] = ("zoneNumber");
	        colheaders[1] = ("occupation");
            System.arraycopy(hharray, 0, colheaders, 2, hharray.length);
            
	        List<TreeMap<Integer, String[]>> detailed = new ArrayList<TreeMap<Integer, String[]>>();
	        // Initialize blocks.
	        for(int i = 0; i < occarray.length; i++)
	            detailed.add(new TreeMap<Integer, String[]>());
	        
	        String line;
	        input.readLine();
            do {
                line = input.readLine();
                if(line != null) {
                    String[] fields = line.split(",");
                    String hhcat = fields[0];
                    int zone = Integer.parseInt(fields[1]);
                    String occ = fields[2];
                    String prod = fields[3];
                    if(occmap.containsKey(occ) && hhmap.containsKey(hhcat)) {
                        TreeMap<Integer, String[]> block = detailed.get(occmap.get(occ));
                        if(!block.containsKey(zone)) {
                            String[] row = new String[numCols];
                            Arrays.fill(row, "0");
                            block.put(zone, row);
	}
                        block.get(zone)[hhmap.get(hhcat) + 2] = prod;
                    }
                }
            } while(line != null);
            
            // Write out the header.
            output.write(join(colheaders, ","));
            output.newLine();
            
            // Add the first two columns and write out.
            for(int occnum = 0; occnum < occarray.length; occnum++) {
                TreeMap<Integer, String[]> block = detailed.get(occnum);
                for(int zone : block.keySet()) {
                    String[] row = block.get(zone);
                    row[0] = String.valueOf(zone);
                    row[1] = occarray[occnum];
                    output.write(join(row, ","));
                    output.newLine();
                }
            }
	        
	    } catch(IOException e) {
            logger.error(e);
            throw new RuntimeException(e);
	    } finally {
	        try {
	            if(input != null)
	                input.close();
	            if(output != null)
	                output.close();
	        } catch(IOException e) {
	            logger.error(e);
	            throw new RuntimeException(e);
	        }
	    }
	}
	
	private String join(String[] array, String sep) {
	    StringBuilder builder = new StringBuilder();
	    for(int i = 0; i < array.length; i++) {
	        builder.append(array[i]);
	        if(i < array.length - 1)
	            builder.append(sep);
	    }
	    return builder.toString();
	}

	public void writeCountyOutputs(String name, Matrix buy, Matrix sell, String goodsOrLabor) {

		if(compressor == null){
			String a2bFilePath = ResourceUtil.getProperty(aaRb,"alpha2beta.file");
			AlphaToBeta beta2CountyMap = new AlphaToBeta(new File(a2bFilePath), "Bzone", "FIPS");
			compressor = new MatrixCompression(beta2CountyMap);
		}

		if(goodsOrLabor.equals("labor")){
			Matrix countySqueeze = compressor.getCompressedMatrix(sell,"SUM");
			File output = new File(getOutputPath() + "CountyFlows_Selling_"+ name+".csv");
			MatrixWriter writer = new CSVMatrixWriter(output);
			writer.writeMatrix(countySqueeze);

		} else {
			Matrix countySqueeze = compressor.getCompressedMatrix(buy,"SUM");
			File output = new File(getOutputPath() + "CountyFlows_Value_"+ name+".csv");
			MatrixWriter writer = new CSVMatrixWriter(output);
			writer.writeMatrix(countySqueeze);
		}
	}
}
