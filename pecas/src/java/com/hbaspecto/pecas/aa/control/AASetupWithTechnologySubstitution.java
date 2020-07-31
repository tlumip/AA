/*
 *  Copyright  2007 HBA Specto Incorporated
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
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Map;
import java.util.ResourceBundle;

import com.pb.common.datafile.TableDataSet;
import com.pb.common.util.ResourceUtil;
import com.hbaspecto.pecas.ChoiceModelOverflowException;
import com.hbaspecto.pecas.NoAlternativeAvailable;
import com.hbaspecto.pecas.aa.activities.ActivityInLocationWithLogitTechnologyChoice;
import com.hbaspecto.pecas.aa.activities.AggregateActivity;
import com.hbaspecto.pecas.aa.activities.ProductionActivity;
import com.hbaspecto.pecas.aa.commodity.Commodity;
import com.hbaspecto.pecas.aa.control.AAPProcessor.ZoneQuantityStorage;
import com.hbaspecto.pecas.aa.technologyChoice.LogitTechnologyChoice;
import com.hbaspecto.pecas.aa.technologyChoice.LogitTechnologyChoiceConsumptionFunction;
import com.hbaspecto.pecas.aa.technologyChoice.LogitTechnologyChoiceProductionFunction;
import com.hbaspecto.pecas.aa.technologyChoice.TechnologyOption;
import com.hbaspecto.pecas.zones.AbstractZone;

/**
 * This is a specific version of the Pre Processor which sets up an
 * activity allocation model using, among other things, a spreadsheet of production technology options
 * called "TechnologyOptionsI"
 * 
 * @author John Abraham
 *
 */
public class AASetupWithTechnologySubstitution extends AAPProcessor {

    public void writeTechnologyChoice() {

    	// now write out technology option proportions
    	try {
    		PrintWriter out = new PrintWriter(new FileWriter(getOutputPath()+"TechnologyChoice.csv"));
    		out.println("Activity,Zone,Option,Utility,Constant,BaseUtility,SizeUtility,Size,Probability");
    		for (ProductionActivity p : ProductionActivity.getAllProductionActivities()) {
    			for (int z = 0; z < p.getMyDistribution().length; z++) {
    				ActivityInLocationWithLogitTechnologyChoice zoneChars = (ActivityInLocationWithLogitTechnologyChoice) p.getMyDistribution()[z];
    				int zoneNumber = zoneChars.getMyZone().getZoneUserNumber();
    				zoneChars.writeOutInformationOnEachOption(zoneNumber,out);
    			}
    		}
    		out.close();
    	} catch (IOException e) {
    		logger.error("Can't write out TechnologyChoice.csv files", e);
    	} catch (ChoiceModelOverflowException e) {
    		logger.error("Can't write out TechnologyChoice.csv files", e);
		}
    }

    // Called by reflection in AAControl
    public AASetupWithTechnologySubstitution() {
        super();
        logitTechnologyChoice=true;
    }

    // Never called????
    public AASetupWithTechnologySubstitution(int timePeriod, ResourceBundle aaRb) {
        super(timePeriod, aaRb);
        
    }

    @Override
    protected void setUpMakeAndUse() {
        logger.info("Setting up TechnologyOptionsI table");
        TableDataSet technologyOptions = loadDataSetFromYearOrAllYears("TechnologyOptionsI");

        String optionWeightColumnName = "OptionSize";
        if (ResourceUtil.getBooleanProperty(aaRb,"aa.automaticTechnologySizeTerms", true)) {
        	optionWeightColumnName = "OptionWeight";
        	int columnId = technologyOptions.getColumnPosition(optionWeightColumnName);
        	if (columnId<0) {
        		String msg = "Can't find column "+optionWeightColumnName+" in TechnologyOptions, if you are new to using Automatic Technology SizeTerms you might have to rename the OptionSize column";
        		logger.fatal(msg);
        		throw new RuntimeException(msg);
        		
        	}
        }
        
        for (int techRow = 1; techRow <= technologyOptions.getRowCount(); techRow++) {
            // Retrieve each Activity and make sure the name is valid
            String activityName = technologyOptions.getStringValueAt(techRow, "Activity");
            AggregateActivity activity = (AggregateActivity) AggregateActivity.retrieveProductionActivity(activityName);
            if (activity == null) {
                logger.fatal("Activity "+activityName+" in TechnologyOptionsI is not defined");
                throw new RuntimeException("Activity "+activityName+" in TechnologyOptionsI is not defined");
            }
            // Create the in-memory object for the Activity Technology Option.
            LogitTechnologyChoice choice = ((LogitTechnologyChoiceProductionFunction) activity.getProductionFunction()).myTechnologyChoice;
            String name = technologyOptions.getStringValueAt(techRow,"OptionName");
            TechnologyOption option = new TechnologyOption(choice, 1/choice.getDispersionParameter()*Math.log(technologyOptions.getValueAt(techRow,optionWeightColumnName)),name);
            choice.addAlternative(option);
            
            // Create each TechnicalCoefficient
            for (int column = 1;column <= technologyOptions.getColumnCount();column++) {
                String columnName = technologyOptions.getColumnLabel(column);
                if (!(columnName.equals("Activity")||columnName.equals("OptionName")||columnName.equals(optionWeightColumnName))) {
                    Commodity com = Commodity.retrieveCommodity(columnName);
                    float sign = (float) 1.0;
                    if (com==null) {
                        // look at the format of the column name to figure out which commodity it is, whether it is make or use, etc.
                        int numberShouldBeInField = 1; 
                        String[] strings = columnName.split(":");
                        String commodityName = strings[0];
                        if (strings[0].equalsIgnoreCase("Make")) {
                            commodityName = strings[1];
                            numberShouldBeInField = 2;
                        }
                        if (strings[0].equalsIgnoreCase("Use")) {
                            commodityName = strings[1];
                            numberShouldBeInField = 2;
                            sign = -1;
                        }
                        com = Commodity.retrieveCommodity(commodityName);
                        // if there is another : in the column heading the next part of it should be an integer, check to make sure
                        if (strings.length-1>numberShouldBeInField) {
                            com = null;
                        }
                        if (strings.length-1==numberShouldBeInField) {
                            try {
                                Integer.valueOf(strings[numberShouldBeInField]);
                            } catch (NumberFormatException e) {
                                com = null;
                            }
                        }
                        if (com == null) {
                            String error = "Column \""+columnName+"\" in TechnologyOptionsI does not properly specify the make or use of a commodity -- could be a misspelled commodity name";
                            logger.fatal(error);
                            throw new RuntimeException(error);
                        }
                    }
                    float amount = technologyOptions.getValueAt(techRow,column)*sign;
                    
                    // Finally, after all that parsing and error checking, add the value into the in-memory object for TechnologyOption
                    option.addCommodity(com, amount,1);
                }
            }
        }
    }


    @Override
    protected void setUpProductionActivities() {
        logger.info("Setting up Production Activities");
        readActivitiesAndActivityZonalValues();

        readActivityTotals();
        
    }

	/**
	 * 
	 */
	protected void readActivitiesAndActivityZonalValues() {
		int numMissingZonalValueErrors = 0;
        TableDataSet ptab = null;
        TableDataSet zonalData = null;
        logitTechnologyChoice=true;
     
        ptab 	  = loadTableDataSet("ActivitiesI","aa.base.data",true);
        if (ptab == null) throw new RuntimeException("No ActivitiesI available for input");

        zonalData = loadTableDataSet("ActivitiesZonalValuesW","aa.current.data",false); 
        
        if (zonalData == null) zonalData = loadTableDataSet("ActivitiesZonalValuesI","aa.current.data",false);
        if (zonalData == null) {
            logger.info("no ActivitiesZonalValuesI or ActivitiesZonalValuesW in aa.base.data or aa.current.data, trying to get zonal data from previous run (ActivityLocations in aa.previous.data)");
            zonalData = loadTableDataSet("ActivityLocations", "aa.previous.data",false);
        }
        Map<String, Integer> activityZonalHashtable = new HashMap<>();
        if (zonalData == null) {
            logger.error("No ActivitiesZonalValuesI, ActivitiesZonelValuesW or ActivityLocations for activity zonal constants input");
        } else {
    
            // Store the row number in the ActivitiesZonalValues table associated with each activity/zone number combination, 
            // so we can get it later.
            for (int zRow = 1; zRow <= zonalData.getRowCount(); zRow++) {
                String activityZone = zonalData.getStringValueAt(zRow, "Activity") + "@" + ((int) zonalData.getValueAt(zRow, "ZoneNumber"));
                activityZonalHashtable.put(activityZone, new Integer(zRow));
            }
        }

        int activityNumberColumn = ptab.getColumnPosition("ActivityNumber");
        int importerExporterColumn = ptab.getColumnPosition("ImporterExporter");
        if (importerExporterColumn == -1) {
        	logger.warn("No ImporterExporter column in ActivitiesI, you need to use constraints or size terms to keep your importers and exporters outside of internal zones");
        }
        int constInExchangeSizeColumn = ptab.getColumnPosition("ConstInExchangeSize");
        if (constInExchangeSizeColumn == -1) {
        	logger.warn("No ConstInExchangeSize column in ActivitiesI, all activities will be allocated based on their space usage to calculate buying/selling exchange sizes");
        } else {
        	if(zonalData != null && !zonalData.containsColumn("ZoneConstantForExchangeSize")) {
        		String msg = "You have a ConstInExchangeSize column in ActivitiesI.csv this means you also need a ZoneConstantForExchangeSize column in your input ActivityLocations or ActivitiesZonalValues";
        		logger.fatal(msg);
        		throw new RuntimeException(msg);
        	}
        }
        for (int pRow = 1; pRow <= ptab.getRowCount(); pRow++) {
            String activityName = ptab.getStringValueAt(pRow, "Activity");
            if(logger.isDebugEnabled()) {
                logger.debug("Setting up production activity " + activityName);
            }
            boolean importerExporter = false;
            if (importerExporterColumn != -1) {
            	String importerExporterString = ptab.getStringValueAt(pRow,importerExporterColumn);
            	if (importerExporterString.equalsIgnoreCase("Importer")) {
            		importerExporter = true;
            	} else if (importerExporterString.equalsIgnoreCase("Exporter")) {
            		importerExporter = true;
            	} else if (importerExporterString.equalsIgnoreCase("Neither")) {
            		importerExporter = false;
            	} else {
            		String msg = "Invalid entry "+importerExporterString+" in ImporterExporter column, should be \"Importer\", \"Exporter\", or \"Neither\"";
            		logger.fatal(msg);
            		throw new RuntimeException(msg);
            	}
            }
            // whether to use constants and constraints in exchange size
            boolean constInExchangeSize = false;
            if (constInExchangeSizeColumn != -1) {
            	constInExchangeSize = ptab.getBooleanValueAt(pRow, constInExchangeSizeColumn);
            }
            AggregateActivity aa;
            if(activityNumberColumn == -1) {
                aa = new AggregateActivity(activityName, zones, importerExporter, constInExchangeSize);
            } else {
                int activityNumber = (int) ptab.getValueAt(pRow, activityNumberColumn);
                aa = new AggregateActivity(activityName, activityNumber, zones, importerExporter, constInExchangeSize);
            }
            aa.setLocationDispersionParameter(ptab.getValueAt(pRow, "LocationDispersionParameter"));
            aa.setSizeTermCoefficient(ptab.getValueAt(pRow, "SizeTermCoefficient"));
            LogitTechnologyChoice techChoice = new LogitTechnologyChoice(aa.name, false);
            int scalingColumn = ptab.getColumnPosition("ProductionUtilityScaling");
            if (scalingColumn >=0) {
                if (ptab.getValueAt(pRow,scalingColumn)!=1.0) {
                    String msg="Using LogitTechnologyChoice but ProductionUtilityScaling is not 1.0";
                    logger.fatal(msg);
                    throw new RuntimeException(msg);
                }
            }
            scalingColumn = ptab.getColumnPosition("ConsumptionUtilityScaling");
            if (scalingColumn >=0) {
                if (ptab.getValueAt(pRow,scalingColumn)!=1.0) {
                    String msg="Using LogitTechnologyChoice but ConsumptionUtilityScaling is not 1.0";
                    logger.fatal(msg);
                    throw new RuntimeException(msg);
                }
            }
            double lambda = ptab.getValueAt(pRow,"ProductionSubstitutionNesting");
            int otherLambdaColumn = ptab.getColumnPosition("ConsumptionSubstitutionNesting");
            if (otherLambdaColumn >=0) {
                if (ptab.getValueAt(pRow,otherLambdaColumn)!=lambda) {
                    String msg="Using LogitTechnologyChoice but ConsumptionSubstitutionNesting column is present and is not set to be the same and ProductionSubstitutionNesting";
                    logger.fatal(msg);
                    throw new RuntimeException(msg);
                }
            }
            techChoice.setDispersionParameter(lambda);
            if (ptab.getColumnPosition("NonModelledProduction")!=-1) {
                if (ptab.getBooleanValueAt(pRow, "NonModelledProduction")) {
                    String error = "NonModelledProduction set to TRUE in ActivitiesI.  Ignored because you are using TechnologyOptionsI where you specify all technology options directly.  Remove this column from ActivitiesI";
                    logger.error(error);
                }
            }
            if (ptab.getColumnPosition("NonModelledConsumption")!=-1) {
                if (ptab.getBooleanValueAt(pRow, "NonModelledConsumption")) {
                    String error = "NonModelledConsumption set to TRUE in ActivitiesI.  Ignored because you are using TechnologyOptionsI where you specify all technology options directly.  Remove this column from ActivitiesI";
                    logger.error(error);
                }
            }
            aa.setConsumptionFunction(new LogitTechnologyChoiceConsumptionFunction(techChoice));
            aa.setProductionFunction(new LogitTechnologyChoiceProductionFunction(techChoice));
            for (int z = 0; z < zones.length; z++) {
                String zoneDataKey = activityName + "@" + zones[z].getZoneUserNumber();
                Integer zoneDataIndex = (Integer) activityZonalHashtable.get(zoneDataKey);
                if (zoneDataIndex != null) {
                    float quant;
                    if(zonalData.containsColumn("Quantity"))
                        quant = zonalData.getValueAt(zoneDataIndex.intValue(), "Quantity");
                    else
                        quant = zonalData.getValueAt(zoneDataIndex.intValue(), "InitialQuantity");
                    double constantForSizeTerm = zonalData.containsColumn("ZoneConstantForExchangeSize") ? zonalData.getValueAt(zoneDataIndex.intValue(), "ZoneConstantForExchangeSize") : 0;
                    aa.setDistribution(zones[z], quant,
                            zonalData.getValueAt(zoneDataIndex.intValue(), "Size"),
                            zonalData.getValueAt(zoneDataIndex.intValue(),
                                    "ZoneConstant"),
                                    constantForSizeTerm);

                } else {
                    if (++numMissingZonalValueErrors < 20) {
                        logger.info("Can't locate zonal data for AggregateActivity "+ aa
                                + " zone "+ zones[z].getZoneUserNumber()
                                + " using size term 1.0, quantity 0.0, location ASC 0.0");
                    }
                    if (numMissingZonalValueErrors == 20) logger.warn("Surpressing further errors on missing zonal data");
                    aa.setDistribution(zones[z], 0.0, 1.0, 0.0, 0.0);
                }
            }
        }
	}

	/**
	 * 
	 */
	protected void readActivityTotals() {
		// Set up activity totals out of separate table.
        TableDataSet activityTotalsTable = loadTableDataSet("ActivityTotalsI","aa.current.data");
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
    
    protected  double[][] readFloorspace() {
        logger.info("Reading Floorspace File");
        if (maxAlphaZone == 0) readFloorspaceZones();
        TableDataSet floorspaceTable = loadTableDataSet("FloorspaceW","aa.floorspace.data",false);
        if (floorspaceTable==null) floorspaceTable = loadTableDataSet("FloorspaceI","aa.floorspace.data",false);
        if (floorspaceTable == null) {
            logger.fatal("Can't read in FloorspaceI table");
            throw new RuntimeException("Can't read in FloorspaceI Table");
        }
    	return processFloorspaceTable(floorspaceTable);
    }

	protected double[][] processFloorspaceTable(TableDataSet floorspaceTable)
			throws Error {
		double[][] floorspaceByLUZ = new double[zones.length][Commodity.getAllCommodities().size()];
        floorspaceInventory = new Hashtable();
        int alphaZoneColumn = -1;
        if (ResourceUtil.getProperty(aaRb,"aa.useFloorspaceZones").equalsIgnoreCase("true")) {
	        alphaZoneColumn = floorspaceTable.getColumnPosition("FloorspaceZone");
	        if (alphaZoneColumn == -1) alphaZoneColumn = floorspaceTable.checkColumnPosition("TAZ");
        } else {
        	alphaZoneColumn = floorspaceTable.checkColumnPosition("LUZ");
        }
        int quantityColumn = floorspaceTable.checkColumnPosition("Quantity");
        int floorspaceTypeColumn = floorspaceTable.checkColumnPosition("Commodity");
        for (int row = 1; row <= floorspaceTable.getRowCount(); row++) {
            int alphaZone = (int) floorspaceTable.getValueAt(row,alphaZoneColumn);
            float quantity = floorspaceTable.getValueAt(row,quantityColumn);
            String commodityName = floorspaceTable.getStringValueAt(row,floorspaceTypeColumn);
            Commodity c = Commodity.retrieveCommodity(commodityName);
            if (c==null) throw new Error("Bad commodity name "+commodityName+" in FloorspaceI.csv");
            ZoneQuantityStorage fi = (ZoneQuantityStorage) floorspaceInventory.get(commodityName);
            if (fi==null) {
                fi = new ZoneQuantityStorage(commodityName);
                floorspaceInventory.put(commodityName,fi);
            }
            // old way            fi.inventory[alphaZone]+= quantity;
           // check to make sure FloorspaceZone (alphazone) is valid.
            int betaZone = floorspaceZoneCrossref.getBetaZone(alphaZone);
            if (betaZone == 0) logger.warn("Betazone for FloorspaceZone "+alphaZone+" is 0");
            fi.increaseQuantityForZone(alphaZone,quantity);
            AbstractZone theLUZ = AbstractZone.findZoneByUserNumber(betaZone);
            if (theLUZ==null) {
            	String msg = "TAZ number in FloorspaceI :"+alphaZone+" does not map to any LUZ";
            	logger.fatal(msg);
            	throw new RuntimeException(msg);
            }
            int zoneIndex = AbstractZone.findZoneByUserNumber(betaZone).zoneIndex;
            floorspaceByLUZ[zoneIndex][c.commodityIndex] += quantity;

        }
        return floorspaceByLUZ;
	}

    @Override
	public TableDataSet loadTableDataSet(String tableName, String source, boolean check) {
    	// TODO make sure this is consistent with approach in Ohio and Oregon
    	// use SQL Inputs.
    	// But first check to see if a CSV file exists, and get it instead if it exists.

    	TableDataSet table = null;
    	String fileName = null;

    	// TODO Change to inputstreamreader
    	
        boolean useSQLInputs= ResourceUtil.getBooleanProperty(aaRb, "aa.useSQLInputs",false);

    	String inputPath = ResourceUtil.getProperty(aaRb, source);
    	if (inputPath != null) {
    		fileName = inputPath +tableName + ".csv";
    		try {
    			table = getCsvFileReader().readFile(new File(fileName));
    		} catch (Exception fileFailed) {
    			if (! (fileFailed instanceof FileNotFoundException)) {
    				logger.fatal("Couldn't read the file "+fileName);
    				fileFailed.printStackTrace();
    				throw new RuntimeException(fileFailed);
    			}
				logger.info("Failed to read file "+fileName+", trying as a URL Instead");
    			try {
    				table = getCsvFileReader().readFile(fileName);
    			} catch (Exception streamFailed) {}
    		}
    		if (table !=null) logger.info("Table "+tableName+" exists in "+source+", read text file instead of JDBC input");
    		else if (useSQLInputs)
    			logger.info("Can't read "+fileName+" from "+source+" ("+inputPath+"), trying to read from JDBC instead");
    		else
    		    logFailure("Can't read "+fileName+" from "+source+" ("+inputPath+")", check);

    	} else if (useSQLInputs)
    		logger.info("Source "+source+" is not defined in property file, trying to get table "+tableName+" from JDBC source instead");
    	else
    	    logFailure("Source "+source+" is not defined in property file", check);
    	
    	if (table == null && useSQLInputs)
    	    table = loadTableDataSetFromJDBC(tableName, source, check);
    	
    	return table;
    }

    
}
