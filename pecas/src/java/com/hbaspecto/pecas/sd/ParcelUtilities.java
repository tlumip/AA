/*
 * Created on 07-Dec-2016
 *
 * Copyright  2016 HBA Specto Incorporated
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.Set;

import no.uib.cipr.matrix.DenseMatrix;
import no.uib.cipr.matrix.DenseVector;
import no.uib.cipr.matrix.Matrices;
import no.uib.cipr.matrix.Matrix;
import no.uib.cipr.matrix.MatrixSingularException;
import no.uib.cipr.matrix.Vector;

import org.apache.log4j.Logger;

import simpleorm.dataset.SQuery;
import simpleorm.dataset.SQueryResult;
import simpleorm.dataset.SQueryTransient;
import simpleorm.dataset.SRecordTransient;
import simpleorm.sessionjdbc.SSessionJdbc;

import com.hbaspecto.discreteChoiceModelling.Coefficient;
import com.hbaspecto.pecas.land.ExchangeResults;
import com.hbaspecto.pecas.land.Parcels;
import com.hbaspecto.pecas.land.PostgreSQLLandInventory;
import com.hbaspecto.pecas.land.SimpleORMLandInventory;
import com.hbaspecto.pecas.land.Tazs;
import com.hbaspecto.pecas.sd.estimation.ConcurrentLandInventory;
import com.hbaspecto.pecas.sd.estimation.DifferentiableModel;
import com.hbaspecto.pecas.sd.estimation.EstimationDataSet;
import com.hbaspecto.pecas.sd.estimation.EstimationReader;
import com.hbaspecto.pecas.sd.estimation.EstimationTarget;
import com.hbaspecto.pecas.sd.estimation.ExpectedTargetModel;
import com.hbaspecto.pecas.sd.estimation.GaussBayesianObjective;
import com.hbaspecto.pecas.sd.estimation.MarquardtMinimizer;
import com.hbaspecto.pecas.sd.estimation.OptimizationException;
import com.hbaspecto.pecas.sd.orm.DevelopmentFees;
import com.hbaspecto.pecas.sd.orm.ObservedDevelopmentEvents;
import com.hbaspecto.pecas.sd.orm.SiteSpecTotals;
import com.hbaspecto.pecas.sd.orm.SpaceTazLimits;
import com.hbaspecto.pecas.sd.orm.SpaceTypesGroup;
import com.hbaspecto.pecas.sd.orm.TazGroups;
import com.hbaspecto.pecas.sd.orm.TazLimitGroups;
import com.hbaspecto.pecas.sd.orm.TransitionCosts;
import com.pb.common.datafile.CSVFileWriter;
import com.pb.common.datafile.GeneralDecimalFormat;
import com.pb.common.datafile.OLD_CSVFileReader;
import com.pb.common.datafile.TableDataFileReader;
import com.pb.common.datafile.TableDataFileWriter;
import com.pb.common.datafile.TableDataSetCollection;
import com.pb.common.util.ResourceUtil;

public class ParcelUtilities extends StandardSDModel {
	protected static transient Logger logger = Logger
			.getLogger(ParcelUtilities.class);

	public static void main(String[] args) {
		boolean worked = true; // assume this is going to work
		rbSD = ResourceUtil.getResourceBundle("sd");
		initOrm();
		SDModel mySD = new ParcelUtilities();
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


	public void simulateDevelopment() {
		BufferedWriter uWriter;
		try {
			uWriter = new BufferedWriter(new FileWriter("ParcelSpaceUtilities.csv"));

			Collection<SpaceTypesI> allST = SpaceTypesI.getAllSpaceTypes();
			ArrayList<DevelopNewAlternative> allNews = new ArrayList<DevelopNewAlternative>();
			ZoningRulesI allZR = null; //new ZoningRulesI();
			uWriter.write("pecas_parcel_num");
			for (SpaceTypesI  st : allST) {
				allNews.add(new DevelopNewAlternative(allZR, st));
				uWriter.write(","+st.get_SpaceTypeName());
			}
			uWriter.write("\n");
			try {
				land.setToBeforeFirst();
				long parcelCounter = 0;
				while (land.advanceToNext()) {
					parcelCounter++;
					if (parcelCounter % 1000 == 0) {
						logger.info("finished parcel " + parcelCounter);
					}
					uWriter.write(String.valueOf(land.getPECASParcelNumber()));
					for (DevelopNewAlternative  dn : allNews) {
						int spaceTypeId = dn.theNewSpaceTypeToBeBuilt.get_SpaceTypeId();
						try {
							DevelopmentFees df = land.getSession().mustFind(DevelopmentFees.meta,  land.get_FeeScheduleId(), dn.theNewSpaceTypeToBeBuilt.get_SpaceTypeId());
							TransitionCosts tc = land.getSession().mustFind(TransitionCosts.meta,  land.get_CostScheduleId(), dn.theNewSpaceTypeToBeBuilt.get_SpaceTypeId());
							double utility = dn.getUtilityPerUnitSpace(tc, df);
							uWriter.write(","+utility);
						} catch (Exception e) {
							logger.error("Problem finding fees or costs for space type "+spaceTypeId+" for fee schedule "+land.get_FeeScheduleId()+" and cost schedule "+land.get_CostScheduleId());
							uWriter.write(",");
							throw e;
						}
					}
					uWriter.write("\n");
				}
				land.getDevelopmentLogger().flush();

			} finally {
				land.getDevelopmentLogger().close();
				land.getChoiceUtilityLogger().close();
				uWriter.flush();
				uWriter.close();
			}
		} catch (IOException e) {
			logger.fatal("Couldn't write or open output file ParcelSpaceUtilities.csv", e);
			throw new RuntimeException(e);
		}
	}

	private void writeOutputs(String iteration) {
		// nothing
	}

}