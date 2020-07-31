package com.hbaspecto.pecas.sd;

import java.io.File;
import java.util.List;
import java.util.ResourceBundle;

import org.apache.log4j.Logger;

import com.hbaspecto.discreteChoiceModelling.Coefficient;
import com.hbaspecto.pecas.land.SimpleORMLandInventory;
import com.hbaspecto.pecas.sd.estimation.PriorReader;
import com.hbaspecto.pecas.sd.estimation.TablePriorReader;
import com.pb.common.datafile.CSVFileReader;
import com.pb.common.datafile.TableDataFileReader;
import com.pb.common.datafile.TableDataSet;
import com.pb.common.util.ResourceUtil;

import simpleorm.sessionjdbc.SSessionJdbc;

public class SDParameterLoader {

    private static Logger logger = Logger.getLogger(SDParameterLoader.class);
    
    public static void main(String[] args) throws Exception {
        // TODO Auto-generated method stub
        ResourceBundle rb = ResourceUtil.getResourceBundle("sd");
        StandardSDModel.initOrm(rb);
        
        TableDataFileReader reader = new CSVFileReader();
        String parametersFname = "calibrated_parameters.csv";
        TableDataSet parameters = reader.readFile(new File(parametersFname));
        PriorReader priorReader = TablePriorReader.reader(parameters).build();
        
        List<Coefficient> coeffs = priorReader.parameters();
        double[] startValues = priorReader.startValues(coeffs);

        SSessionJdbc ses = SimpleORMLandInventory.prepareSimpleORMSession(rb);
        //ses.attachToThread();
        
        logger.info("Writing parameters to the database");
        
        int i = 0;
        for (Coefficient coeff : coeffs) {
            coeff.setTransformedValue(startValues[i]);
            i++;
        }
        
        logger.info("Parameters written!");
        
        ses.commitAndDetachDataSet();
    }

}
