package com.hbaspecto.pecas;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;

import com.pb.common.datafile.CSVFileWriter;
import com.pb.common.datafile.TableDataSet;
import com.pb.common.matrix.Matrix;
import com.pb.common.matrix.ZipMatrixReader;

public class ZipMatrixExtractor {
	
	private static File dir = new File("C:/Users/HBA/Downloads/FlowMatrices140617");
	private static String[] buyingCommods = {"SCTG01_FKP_LVSK",
											 "SCTG02_FKP_AGRI_cereal",
											 "SCTG03_FKP_AGRI_other",
											 "SCTG04_FKP_FEED",
											 "SCTG05_FKP_FOOD_meat",
											 "SCTG06_FKP_AGRI_grain",
											 "SCTG07_FKP_FOOD_prep",
											 "SCTG08_FKP_FOOD_alc",
											 "SCTG10_CMS_CLAY",
											 "SCTG11_CMS_SAND",
											 "SCTG13_CMS_MINE_nonmet",
											 "SCTG14_CMS_MINE_met",
											 "SCTG15_PCC_COAL",
											 "SCTG16_PCC_PETR_crude",
											 "SCTG17_PCC_FUEL",
											 "SCTG18_PCC_PETR_oil",
											 "SCTG19_PCC_COAL_prod",
											 "SCTG20_PCC_CHEM_basic",
											 "SCTG21_PCC_CHEM_pharma",
											 "SCTG22_PCC_CHEM_fert",
											 "SCTG23_PCC_CHEM_prod",
											 "SCTG24_PCC_PETR_plast",
											 "SCTG25_FWP_LOGS",
											 "SCTG26_FWP_WOOD",
											 "SCTG27_PPP_PAPR_puplp",
											 "SCTG28_PPP_PAPR_paper",
											 "SCTG29_PPP_PAPR_print",
											 "SCTG30_OTH_CLTH",
											 "SCTG31_CMS_MIN",
											 "SCTG32_MIT_METL_base",
											 "SCTG33_MIT_METL_prod",
											 "SCTG34_MIT_MACH",
											 "SCTG35_MIT_ELCT",
											 "SCTG36_MIT_TRAN",
											 "SCTG37_MIT_INST_transp",
											 "SCTG38_MIT_INST_prec",
											 "SCTG39_OTH_FURN",
											 "SCTG40_OTH_MISC",
											 "SCTG41_WASTE_SCRAP",
											 "Retail Trade",
											 "Transport",
											 "Wholesale Trade",
											 "Communications and Utilities",
											 "Accommodations",
											 "Personal and Other Services and Amusements",
											 "Entertainment Services",
											 "Food Services",
											 "Teaching K12",
											 "Higher Education",
											 "Health Services",
											 "Government Administration"};
	private static String[] sellingCommods = {"SCTG04_FKP_FEED",
											  "SCTG05_FKP_FOOD_meat",
											  "SCTG06_FKP_AGRI_grain",
											  "SCTG07_FKP_FOOD_prep",
											  "SCTG08_FKP_FOOD_alc",
											  "SCTG17_PCC_FUEL",
											  "SCTG18_PCC_PETR_oil",
											  "SCTG21_PCC_CHEM_pharma",
											  "SCTG23_PCC_CHEM_prod",
											  "SCTG29_PPP_PAPR_print",
											  "SCTG30_OTH_CLTH",
											  "SCTG36_MIT_TRAN",
											  "SCTG39_OTH_FURN",
											  "SCTG40_OTH_MISC",
											  "Energy",
											  "Construction",
											  "Fire Business and Professional Services",
											  "Internal Services Resources",
											  "Internal Services Energy",
											  "Internal Services Construction",
											  "Internal Services Manufacturing",
											  "Internal Services Wholesale",
											  "Internal Services Retail Store",
											  "Internal ServicesTransport",
											  "Internal Services Information",
											  "Internal Services Utilities",
											  "Internal Services Education k12",
											  "Internal Services Government Administration",
											  "A1-Mgmt Bus",
											  "B1-Prof Specialty",
											  "B2-Education",
											  "B3-Health",
											  "B4-Technical Unskilled",
											  "C1-Sales Clerical Professionals",
											  "C2-Sales Service",
											  "C3-Clerical",
											  "C4-Sales Clerical Unskilled",
											  "D1-Production Specialists",
											  "D2-MaintConstRepair Specialists",
											  "D3-ProtectTrans Specialists",
											  "D4-Blue Collar Unskilled"};
	private static int[] rows = {4010, 4025};
	private static int[] cols = rows;
	private static File outFile = new File(dir, "extracted.csv");
	
	public static void main(String[] args) throws IOException {
		TableDataSet table = new TableDataSet();
		table.appendColumn(new String[0], "Commodity");
		table.appendColumn(new String[0], "BuySell");
		table.appendColumn(new int[0], "Row");
		table.appendColumn(new int[0], "Column");
		table.appendColumn(new double[0], "Quantity");
		
		for(String commod : buyingCommods) {
			addRows(table, commod, BuySell.BUY);
		}
		for(String commod : sellingCommods) {
			addRows(table, commod, BuySell.SELL);
		}
		
		CSVFileWriter writer = new CSVFileWriter();
		writer.writeFile(table, outFile);
	}
	
	private static void addRows(TableDataSet table, String commod, BuySell mode) {
		File matFile = new File(dir, mode.prefix + commod + ".zmx");
		Matrix matrix = new ZipMatrixReader(matFile).readMatrix();
		HashMap<String, Object> map = new HashMap<String, Object>();
		for(int row : rows) {
			for(int col : cols) {
				double value = matrix.getValueAt(row, col);
				map.put("Commodity", commod);
				map.put("BuySell", mode.toString());
				map.put("Row", (float) row);
				map.put("Column", (float) col);
				map.put("Quantity", (float) value);
				table.appendRow(map);
			}
		}
	}
	
	private enum BuySell {
		BUY("buying_", "Buy"),
		SELL("selling_", "Sell");
		
		private String prefix;
		private String string;
		
		private BuySell(String prefix, String string) {
			this.prefix = prefix;
			this.string = string;
		}
		
		@Override
		public String toString() {
			return string;
		}
	}
}
