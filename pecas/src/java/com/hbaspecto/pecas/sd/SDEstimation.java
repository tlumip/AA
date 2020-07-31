package com.hbaspecto.pecas.sd;

import java.io.File;
import java.util.ResourceBundle;

import com.hbaspecto.pecas.sd.estimation.CSVEstimationReader;
import com.hbaspecto.pecas.sd.estimation.FullParameterPrinter;
import com.hbaspecto.pecas.sd.estimation.FullTargetPrinter;
import com.hbaspecto.pecas.sd.estimation.ParameterPrinter;
import com.hbaspecto.pecas.sd.estimation.PriorReader;
import com.hbaspecto.pecas.sd.estimation.PriorReaderAdapter;
import com.hbaspecto.pecas.sd.estimation.ShortParameterPrinter;
import com.hbaspecto.pecas.sd.estimation.ShortTargetPrinter;
import com.hbaspecto.pecas.sd.estimation.TablePriorReader;
import com.hbaspecto.pecas.sd.estimation.TableTargetReader;
import com.hbaspecto.pecas.sd.estimation.TargetPrinter;
import com.hbaspecto.pecas.sd.estimation.TargetReader;
import com.hbaspecto.pecas.sd.estimation.TargetReaderAdapter;
import com.pb.common.datafile.CSVFileReader;
import com.pb.common.datafile.TableDataFileReader;
import com.pb.common.datafile.TableDataSet;
import com.pb.common.util.ResourceUtil;

public class SDEstimation {

    /**
     * @param args
     */
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

        ResourceBundle rb = ResourceUtil.getResourceBundle("sd_calib");
        StandardSDModel.evs = true;
        StandardSDModel.initOrm(rb);

        double epsilon = ResourceUtil.getDoubleProperty(rb,
                "EstimationConvergence", 1E-4);
        int maxits = ResourceUtil.getIntegerProperty(rb,
                "EstimationMaxIterations", 1);

        StandardSDModel sd = new StandardSDModel();

        PriorReader priorReader;
        TargetReader targetReader;
        ParameterPrinter paramPrinter;
        TargetPrinter targetPrinter;

        if (ResourceUtil.getBooleanProperty(rb, "EstimationOriginalFormat",
                false)) {
            String parameters = ResourceUtil.checkAndGetProperty(rb,
                    "EstimationParameterFile");
            String targets = ResourceUtil.checkAndGetProperty(rb,
                    "EstimationTargetFile");

            boolean paramsDiag = ResourceUtil.getBooleanProperty(rb,
                    "EstimationParameterVarianceAsDiagonal", true);
            boolean targetsDiag = ResourceUtil.getBooleanProperty(rb,
                    "EstimationTargetVarianceAsDiagonal", true);
            CSVEstimationReader csv = new CSVEstimationReader(targets,
                    targetsDiag, parameters, paramsDiag);

            priorReader = new PriorReaderAdapter(csv);
            targetReader = new TargetReaderAdapter(csv);
            paramPrinter = new ShortParameterPrinter();
            targetPrinter = new ShortTargetPrinter();
        } else {
            TableDataFileReader reader = new CSVFileReader();
            String parametersFname = ResourceUtil.checkAndGetProperty(rb,
                    "EstimationParameterFile");
            TableDataSet parameters = reader
                    .readFile(new File(parametersFname));
            String parameterCorrelationsFname = ResourceUtil.getProperty(rb,
                    "EstimationParameterCorrelationsFile");
            TablePriorReader.Builder priorBuilder = TablePriorReader
                    .reader(parameters);
            if (parameterCorrelationsFname != null) {
                TableDataSet parameterCorrelations = reader
                        .readFile(new File(parameterCorrelationsFname));
                priorBuilder.withCorrelations(parameterCorrelations);
            }
            priorReader = priorBuilder.build();

            String targetsFname = ResourceUtil.checkAndGetProperty(rb,
                    "EstimationTargetFile");
            TableDataSet targets = reader.readFile(new File(targetsFname));
            String targetCorrelationsFname = ResourceUtil.getProperty(rb,
                    "EstimationTargetCorrelationsFile");
            String targetSpaceGroupsFname = ResourceUtil.getProperty(rb,
                    "EstimationTargetSpaceGroupsFile");
            String targetCustomTazGroupsFname = ResourceUtil.getProperty(rb,
                    "EstimationTargetCustomTazGroupsFile");
            TableTargetReader.Builder targetBuilder = TableTargetReader
                    .reader(targets);
            if (targetCorrelationsFname != null) {
                TableDataSet targetCorrelations = reader
                        .readFile(new File(targetCorrelationsFname));
                targetBuilder.withCorrelations(targetCorrelations);
            }
            if (targetSpaceGroupsFname != null) {
                TableDataSet targetSpaceGroups = reader
                        .readFile(new File(targetSpaceGroupsFname));
                targetBuilder.withSpaceGroups(targetSpaceGroups);
            }
            if (targetCustomTazGroupsFname != null) {
                TableDataSet targetCustomTazGroups = reader
                        .readFile(new File(targetCustomTazGroupsFname));
                targetBuilder.withTazGroups(targetCustomTazGroups);
            }
            targetReader = targetBuilder.build();
            paramPrinter = new FullParameterPrinter();
            targetPrinter = new FullTargetPrinter();
        }

        sd.calibrateModel(rb, priorReader, paramPrinter, targetReader,
                targetPrinter, ZoningRulesI.baseYear, ZoningRulesI.currentYear,
                epsilon, maxits);
        // There are often threads hanging open at the end, for unknown reasons.
        // TODO figure out the extra threads hanging around
        System.exit(0);
    }
}
