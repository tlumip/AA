/*
 * Created on 28-Oct-2007
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

import java.util.Collection;
import java.util.List;
import java.util.ResourceBundle;
import org.apache.log4j.Logger;
import simpleorm.dataset.SQuery;
import simpleorm.sessionjdbc.SSessionJdbc;
import com.pb.common.datafile.TableDataSet;
import com.pb.common.datafile.TableDataSetCollection;
import com.pb.common.util.ResourceUtil;
import com.hbaspecto.pecas.land.LandInventory;

/**
 * @author Abdel
 *	An example of a SD model independent of what type of land inventory is used.
 * It is an abstract class that has some common code that can be used by lot of different implementation. 
 */
public abstract class SDModel {
    protected static transient Logger logger = Logger.getLogger(SDModel.class);
    protected static ResourceBundle rbSD = null;
    
    protected static int currentYear;
    protected static int baseYear;
    protected static String landDatabaseDriver;
    protected static String landDatabaseSpecifier;
    protected static String logFilePath;

    protected TableDataSetCollection outputDatabase = null;
    protected LandInventory land;
    protected static TableDataSet realDevelopmentTypesI;
    
    protected static boolean evs = false;
    
    public void runSD(int currentYear, int baseYear, ResourceBundle rb) {
        logger.info("Doing development model for year "+ currentYear);
        
        rbSD = rb;
        initZoningScheme(currentYear, baseYear);
        simulateDevelopment();
        
        TableDataSet constructionReport = buildConstructionReport();
        outputDatabase.addTableDataSet(constructionReport);
        
        if (ResourceUtil.getBooleanProperty(rbSD,"ApplyDevelopmentEventsToParcels", true)) {
        	land.applyDevelopmentChanges(); 
        	writeOutInventoryTable(realDevelopmentTypesI);
        } else {
        	logger.warn("ApplyDevelopmentEventsToParcels is set to FALSE, this means that SD **is not** writing out FloorspaceI.csv for use by AA in the next year");
        }
        
        outputDatabase.flush();

        outputDatabase.close();
    }

	/**
	 * @return
	 */
	private TableDataSet buildConstructionReport() {
		TableDataSet constructionReport = new TableDataSet();
		constructionReport.setName("ConstructionProfit");
        
        Collection<SpaceTypesI> spaceTypes = SpaceTypesI.getAllSpaceTypes();
        int size = spaceTypes.size();
        int[] spaceTypeIds = new int[size];
        String[] spaceTypeNames = new String[size];
        double[] cumulBuilt = new double[size];
        double[] cumulProfitNS = new double[size];
        double[] cumulProfitSHP = new double[size];
        
        int i =0;
        for (SpaceTypesI s : spaceTypes) {
        	spaceTypeIds[i] = s.get_SpaceTypeId();
        	spaceTypeNames[i] = s.get_SpaceTypeName();
        	cumulBuilt[i] = s.cumulativeAmountOfDevelopment;
        	cumulProfitNS[i] = s.cumulativeAnnualProfitOnNewDevelopmentNoDensityShaping;
        	cumulProfitSHP[i] = s.cumulativeAnnualProfitOnNewDevelopmentWithDensityShaping;
        	i++;
        }
        
        constructionReport.appendColumn(spaceTypeNames, "SpaceType");
        constructionReport.appendColumn(spaceTypeIds, "SpaceTypeID");
        constructionReport.appendColumn(cumulBuilt, "TotalConstruction");
        constructionReport.appendColumn(cumulProfitNS, "ProfitNoShaping");
        constructionReport.appendColumn(cumulProfitSHP, "Profit");
		return constructionReport;
	}
   
    protected static void initZoningScheme(int currentYear, int baseYear) {
        ZoningRulesI.currentYear = currentYear;
        ZoningRulesI.baseYear = baseYear;
        if (!evs) {
            ZoningRulesI.usePredefinedRandomNumbers = ResourceUtil
                    .getBooleanProperty(rbSD, "UsePredefinedRandomNumbers");
        }
        
        if (ResourceUtil.getProperty(rbSD,"DevelopmentDispersionParameter") != null) {
        	logger.warn("DevelopmentDispersionParameter is set in sd.properties, this is no longer the place to put SD dispersion parameters");
        }

        double interestRate = 0.0722;
        double compounded = Math.pow(1+interestRate,30);
        double amortizationFactor = interestRate *compounded/(compounded -1);
        ZoningRulesI.amortizationFactor=ResourceUtil.getDoubleProperty(rbSD,"AmortizationFactor",amortizationFactor);
    }
    
    public abstract void setUpLandInventory(String className, int year);
        
    public abstract void setUp();
    
    /**
     * Runs through the inventory simulating development
     */
    public abstract void simulateDevelopment();
    
    public void writeOutInventoryTable(TableDataSet landTypes) {
        TableDataSet landInventoryTable = land.summarizeInventory();
        landInventoryTable.setName("FloorspaceI");
        outputDatabase.addTableDataSet(landInventoryTable);
        outputDatabase.flush();
    }

    public SpaceTypesI[] setUpDevelopmentTypes() {
    	
    	SSessionJdbc session = SSessionJdbc.getThreadLocalSession();

    	SQuery<SpaceTypesI> devQry = new SQuery<SpaceTypesI>(SpaceTypesI.meta);                      
    	List<SpaceTypesI> dtypes = session.query(devQry);
    	   	
        SpaceTypesI[] d = new SpaceTypesI[dtypes.size()];
        SpaceTypesI[] dTypes = (SpaceTypesI[]) dtypes.toArray(d);
    
        return dTypes;
    }
}
