package com.hbaspecto.pecas.aa.activities;

import java.io.BufferedWriter;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Map.Entry;
import java.util.TreeMap;
import java.util.TreeSet;

import org.apache.log4j.Logger;

import com.hbaspecto.pecas.aa.commodity.Commodity;
import com.hbaspecto.pecas.aa.commodity.CommodityZUtility;
import com.hbaspecto.pecas.aa.control.AAPProcessor;
import com.hbaspecto.pecas.zones.PECASZone;
import com.pb.common.datafile.TableDataSet;


public class PopSynTargets {

	public class TargetSpec {
		public boolean isAct;
		public String group;
		public String pecasCategoryName;
		public Boolean isUse;
		public String SynthesisColumn;
		public boolean isTAZ;
	}
	
	final TableDataSet targetsSpecificationTable;
	final TableDataSet targetsTDS;
	static Logger logger = Logger.getLogger(PopSynTargets.class);
	final AAPProcessor pp;
	final ArrayList<TargetSpec> targetSpecs = new ArrayList<TargetSpec>();
	final TreeSet<String> activitiesTrackedByTAZ = new TreeSet<String>();
	final TreeSet<String> commoditiesTrackedByTAZ = new TreeSet<String>();
	final TreeSet<String> targetGroups = new TreeSet<String>();
    final TreeMap<String, TreeMap<Integer, Double>> actTAZTargets = new TreeMap<String, TreeMap<Integer, Double>>();
    final TreeMap<String, TreeMap<Integer, Double>> comTAZUseTargets = new TreeMap<String, TreeMap<Integer, Double>>();
    final TreeMap<String, TreeMap<Integer, Double>> comTAZMakeTargets = new TreeMap<String, TreeMap<Integer, Double>>();
    

	public PopSynTargets(AAPProcessor pp) throws FileNotFoundException {
		this.pp = pp;
		
		// Load the connections between the PECAS output and the target file
		TableDataSet try1Spec = pp.loadDataSetFromYearOrAllYears("PopSynConnection", false);
		if (try1Spec == null) {
			logger.warn("No PopSynConnection file, not writing population synthesizer targets");
			throw new FileNotFoundException("Can't find target specification file for population synthesizer");
		}
		targetsSpecificationTable = try1Spec;
		
		
		// Load base target file for modification with PECAS results
		TableDataSet targetsTry1  = pp.loadDataSetFromYearOrAllYears("PopSynBaseTargets",false);
		if (targetsTry1 == null) {
			logger.warn("No PopSyn Base Targets file, not writing population synthesizer targets");
			throw new FileNotFoundException("Can't find base file for updating PopSyn targets for population synthesizer");
		}
		targetsTDS = targetsTry1;
		targetsTDS.setName("PopSynTargets");

		// Store them so we can index them easily 
		for (int row = 1; row <= targetsSpecificationTable.getRowCount(); row++) {
			TargetSpec s = new TargetSpec();
			String CorA = targetsSpecificationTable.getStringValueAt(row, targetsSpecificationTable.checkColumnPosition("ActOrCom"));
			s.isAct = CorA.equalsIgnoreCase("A");
			if (!s.isAct) {
				if (!CorA.equalsIgnoreCase("C")) {
					String msg = "ActOrCom column in PopSynConnection.csv has to have \"C\" or \"A\"";
					logger.fatal(msg);
					throw new RuntimeException(msg);
				}
			}
			s.pecasCategoryName = targetsSpecificationTable.getStringValueAt(row, "PECASCategory");
			s.group = targetsSpecificationTable.getStringValueAt(row, "Group");
			if (!(s.group == null ? true : s.group.equals(""))) {
				targetGroups.add(s.group);
			}
			s.isUse = targetsSpecificationTable.getStringValueAt(row, "MorU").equals("U");
			s.isTAZ = targetsSpecificationTable.getStringValueAt(row, "TAZorLUZ").equalsIgnoreCase("TAZ");
			if (!s.isTAZ) {
				if(!targetsSpecificationTable.getStringValueAt(row,  "TAZorLUZ").equalsIgnoreCase("LUZ")) {
					String msg = "TAZorLUZ column in PopSynConnection.csv has to be TAZ or LUZ, not "+targetsSpecificationTable.getStringValueAt(row,  "TAZorLUZ");
					logger.error(msg);
				}
			}
			if (s.isTAZ) {
				if (s.isAct) {
					activitiesTrackedByTAZ.add(s.pecasCategoryName);
				} else {
					commoditiesTrackedByTAZ.add(s.pecasCategoryName);
				}
			}
			s.SynthesisColumn = targetsSpecificationTable.getStringValueAt(row, "SynthesisColumn");
			targetSpecs.add(s);
		}
	}	


	public void setActivityTAZAmount(String name, int azone, double d) {
		TreeMap<Integer, Double> actTargs = actTAZTargets.get(name);
		if(actTargs == null) {
			actTargs = new TreeMap<Integer, Double>();
			actTAZTargets.put(name, actTargs);
		}
		actTargs.put(azone,  d);
	}

	synchronized public void addToCommodityTAZAmount(String name, boolean isMake, int azone, double d) {
		TreeMap<Integer, Double> comTargs = isMake ? comTAZMakeTargets.get(name) : comTAZUseTargets.get(name);
		if(comTargs == null) {
			comTargs = new TreeMap<Integer, Double>();
			if (isMake) {
				comTAZMakeTargets.put(name, comTargs);
			} else {
				comTAZUseTargets.put(name,  comTargs);
			}
		}
		Double old = comTargs.get(azone);
		if (isMake) {
			comTargs.put(azone,  d + (old == null ? 0 : old));
		} else {
			comTargs.put(azone, - d + (old == null ? 0 : old));
		}
	}

	class ColTargetComponents {
		TargetSpec t;
		TreeMap<Integer, Double> valuesToUse;
	}
	
	public void buildTargetsTDSAndCache() {

		// place for storing the use of each column
		TreeMap<String, TreeSet<Integer>> columnsForEachGroup = new TreeMap<String, TreeSet<Integer>>();
		
		for (int col = 2; col <=  targetsTDS.getColumnCount(); col++) {
			String header = targetsTDS.getColumnLabel(col);
			String group = null;
			ArrayList<ColTargetComponents> colComponents = new ArrayList<ColTargetComponents>();
			for (TargetSpec t : targetSpecs) {
				if (t.SynthesisColumn.equals(header)) {
					ColTargetComponents ctc = new ColTargetComponents();
					ctc.t = t;
					if (t.isTAZ) {
						if (t.isAct) {
							ctc.valuesToUse = actTAZTargets.get(t.pecasCategoryName);
							if (ctc.valuesToUse == null) logger.warn("No PECAS Commodity amounts for Synthetic Population Activity target specified as \""+t.pecasCategoryName+"\"");
						} else {
							if (t.isUse) {
								ctc.valuesToUse = comTAZUseTargets.get(t.pecasCategoryName);
							} else {
								ctc.valuesToUse = comTAZMakeTargets.get(t.pecasCategoryName);
							}
							if (ctc.valuesToUse == null) {
								logger.warn("No PECAS Commodity amounts for Synthetic Population Commodity target specified as \""+t.pecasCategoryName+"\"");
							}
						}
					}
					colComponents.add(ctc);
					if (t.group != null) {
						if (!t.group.equals("")) {
							if (group == null) {
								group = t.group;
							} else {
								if (!t.group.equals(group)) {
									logger.error("Column "+header+" has more than one group for PopSynthesis, "+group+" and "+t.group);
								}
							}
						}
					}
				}
			}
			if (group != null) {
				TreeSet<Integer> columns = columnsForEachGroup.get(group);
				if (columns == null) {
					columns = new TreeSet<Integer>();
					columnsForEachGroup.put(group, columns);
				}
				columns.add(col);
			}
			if (!colComponents.isEmpty()) {
				for (int row = 2; row <= targetsTDS.getRowCount(); row++) {
					String zoneType = targetsTDS.getStringValueAt(row, "TAZorLUZ");
					boolean zoneIsTAZ = zoneType.equalsIgnoreCase("TAZ");
					boolean zoneIsLUZ = zoneType.equalsIgnoreCase("LUZ");
					Integer zone = (int) targetsTDS.getValueAt(row,1);
					double target = 0;
					boolean heyTheresATarget = false;
					for (ColTargetComponents ctc1 : colComponents) {
						if (ctc1.valuesToUse != null && zoneIsTAZ && ctc1.t.isTAZ) {
							Double v = ctc1.valuesToUse.get(zone);
							target += (v == null ? 0 : v);
							heyTheresATarget = true;
						} else if (!ctc1.t.isTAZ && zoneIsLUZ) {
							// LUZ level stuff
							if (ctc1.t.isAct) {
								AmountInZone amt = ProductionActivity.retrieveProductionActivity(ctc1.t.pecasCategoryName).getAmountInUserZone(zone);
								target += amt.quantity;
								heyTheresATarget = true;
							} else {
								PECASZone z = PECASZone.getPECASZoneByUserNumber(zone);
								if (z != null) {
									CommodityZUtility czu = Commodity.retrieveCommodity(ctc1.t.pecasCategoryName).retrieveCommodityZUtility(PECASZone.getPECASZoneByUserNumber(zone), !ctc1.t.isUse);
									target += czu.getQuantity();
									// TODO hgere is where we should the option of scaling make and use amounts by an value that is stored in a column in PopSynConnection
									heyTheresATarget = true;
								} else {
									logger.error("No zone number "+zone);
								}
							}
						}
					}
					if (heyTheresATarget) {
						targetsTDS.setValueAt(row, col, (float) target);
					}
					// TODO divide group target by appropriate group total -- in second pass???
				}
			}
		}
		// Now go through the columns again, dividing each by their group total, exclude negatives because those are pointers to geographic groups
		for (Entry<String, TreeSet<Integer>> e : columnsForEachGroup.entrySet()) {
			for (int row = 2; row <= targetsTDS.getRowCount(); row++) {
				double groupTotal = 0;
				for (Integer col : e.getValue()) {
					float val = targetsTDS.getValueAt(row, col);
					if (val >0) groupTotal += val;
				}
				if (groupTotal > 0) {
					for (Integer col : e.getValue()) {
						float val = targetsTDS.getValueAt(row, col);
						if (val >0) targetsTDS.setValueAt(row,  (int) col, (float) (val/groupTotal) );
					}
				}
			}
		}
		
		
		pp.getTableDataSetCollection().addTableDataSet(targetsTDS);
		pp.getTableDataSetCollection().flushAndForget(targetsTDS);
	}

	public boolean checkIfTAZTargetting(String name) {
		if (activitiesTrackedByTAZ.contains(name)) return true;
		if (commoditiesTrackedByTAZ.contains(name)) return true;
		return false;
	}


}

