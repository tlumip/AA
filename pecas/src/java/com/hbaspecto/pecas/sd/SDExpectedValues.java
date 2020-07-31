package com.hbaspecto.pecas.sd;

import java.io.File;
import java.util.ResourceBundle;

import com.hbaspecto.pecas.sd.estimation.FullTargetPrinter;
import com.hbaspecto.pecas.sd.estimation.TableTargetReader;
import com.hbaspecto.pecas.sd.estimation.TargetPrinter;
import com.hbaspecto.pecas.sd.estimation.TargetReader;
import com.pb.common.datafile.CSVFileReader;
import com.pb.common.datafile.TableDataFileReader;
import com.pb.common.datafile.TableDataSet;
import com.pb.common.util.ResourceUtil;

public class SDExpectedValues {

    public static void main(String[] args) throws Exception {
        try {
            ZoningRulesI.baseYear = Integer.valueOf(args[0]);
            ZoningRulesI.currentYear = Integer.valueOf(args[0])
                    + Integer.valueOf(args[1]);
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(
                    "Put base year and time interval on command line"
                            + "\n For example, 1990 1");
        }

        ResourceBundle rb = ResourceUtil.getResourceBundle("sd_ev");
        StandardSDModel.evs = true;
        StandardSDModel.initOrm(rb);
        
        StandardSDModel sd = new StandardSDModel();
        
        String fname = ResourceUtil.checkAndGetProperty(rb, "ExpectedValueFile");
        TableDataFileReader tableReader = new CSVFileReader();
        
        TableDataSet table = tableReader.readFile(new File(fname));
        TargetReader reader = TableTargetReader.reader(table).build();
        TargetPrinter printer = new FullTargetPrinter();
        
        sd.calculateExpectedValues(rb, reader.targets(), printer,
                ZoningRulesI.currentYear, ZoningRulesI.baseYear);
    }

}
