package com.hbaspecto.pecas.sd.estimation;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import org.apache.log4j.Logger;

import com.hbaspecto.discreteChoiceModelling.Coefficient;
import com.hbaspecto.pecas.FormatLogger;

/**
 * Class that reads the estimation inputs from CSV files (see documentation for
 * format). Reads the files in their entirety on construction, returning the
 * appropriate information in the read methods.
 * 
 * @author Graham
 * 
 */
public class CSVEstimationReader implements EstimationReader {
    private List<EstimationTarget> readTargets;
    private List<Coefficient> readCoeffs;

    private Map<Coefficient, Double> coeffMeans;
    private Map<Coefficient, Double> coeffStartingValues;

    private Map<EstimationTarget, Map<EstimationTarget, Double>> targetDevcor;
    private Map<Coefficient, Map<Coefficient, Double>> coeffDevcor;

    private Map<Integer, Set<Integer>> groups = null;

    private Map<Coefficient, Transform> transforms = new HashMap<Coefficient, Transform>();

    public static Logger logger = Logger.getLogger(CSVEstimationReader.class);
    private static FormatLogger loggerf = new FormatLogger(logger);

    public CSVEstimationReader(String targetFileName,
            boolean targetsAsCompactDiagonal, String coefficientFileName,
            boolean coeffsAsCompactDiagonal) throws IOException {
        readTargetsFile(targetFileName, targetsAsCompactDiagonal);
        readCoeffsFile(coefficientFileName, coeffsAsCompactDiagonal);
    }

    public void setTransformFile(String transformFileName) throws IOException {
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new FileReader(transformFileName));
            String line = null;
            do {
                line = reader.readLine();
                if (line != null) {
                    String[] arrline = line.split(",");
                    Coefficient coeff = coeffForName(arrline[0]);
                    Transform transform = new Transform();
                    transform.type = arrline[1];
                    transform.args = new double[arrline.length - 2];
                    for (int i = 0; i < transform.args.length; i++) {
                        transform.args[i] = Double.parseDouble(arrline[i + 2]);
                    }
                    transforms.put(coeff, transform);
                }
            } while (line != null);
        } finally {
            if (reader != null)
                reader.close();
        }
    }

    private Set<Integer> getGroup(int groupNum) {
        if (groups == null)
            readGroupFile("tazgroups.csv");
        return groups.get(groupNum);
    }

    private void readGroupFile(String filename) {
        BufferedReader reader = null;
        try {
            groups = new TreeMap<Integer, Set<Integer>>();
            try {
                reader = new BufferedReader(new FileReader(filename));
                String line = null;
                do {
                    line = reader.readLine();
                    if (line != null) {
                        String[] arrline = line.split(",");
                        if (arrline.length != 2) {
                            loggerf.throwFatal(
                                    "Error in tazgroups.csv file, in line %s",
                                    line);
                        }
                        int taz = Integer.parseInt(arrline[0]);
                        int groupNum = Integer.parseInt(arrline[1]);
                        if (!groups.containsKey(groupNum)) {
                            groups.put(groupNum, new TreeSet<Integer>());
                        }
                        groups.get(groupNum).add(taz);
                    }
                } while (line != null);
            } finally {
                if (reader != null)
                    reader.close();
            }
        } catch (IOException e) {
            logger.fatal("Can't read TAZ Groups file " + filename
                    + " which is needed since you have a target of type grouptarg");
            throw new RuntimeException(
                    "Can't read TAZ Groups file " + filename
                            + " which is needed since you have a target of type grouptarg",
                    e);
        }

    }

    private void readTargetsFile(String filename, boolean diagonal)
            throws IOException {
        BufferedReader reader = null;
        readTargets = new ArrayList<EstimationTarget>();
        targetDevcor = new HashMap<EstimationTarget, Map<EstimationTarget, Double>>();
        try {
            reader = new BufferedReader(new FileReader(filename));
            String line = null;
            do {
                line = reader.readLine();
                if (line != null) {
                    String[] arrline = line.split(",");
                    EstimationTarget target = targetForName(arrline[0]);
                    target.setTargetValue(Double.parseDouble(arrline[1]));
                    readTargets.add(target);

                    // Put the matrix in a useful format.
                    Map<EstimationTarget, Double> row = new HashMap<EstimationTarget, Double>();
                    targetDevcor.put(target, row);
                    if (diagonal) {
                        double value = Double.parseDouble(arrline[2]);
                        row.put(target, value);
                    } else {
                        for (int i = 0; i < readTargets.size(); i++) {
                            if (i + 2 >= arrline.length)
                                loggerf.throwFatal(
                                        "Not enough entries for target %s",
                                        arrline[0]);
                            double value = Double.parseDouble(arrline[i + 2]);
                            EstimationTarget currTarget = readTargets.get(i);
                            row.put(currTarget, value);
                            targetDevcor.get(currTarget).put(target, value);
                        }
                    }
                }
            } while (line != null);
        } finally {
            if (reader != null)
                reader.close();
        }
    }

    private EstimationTarget targetForName(String name) {
        String[] pieces = name.split("-");
        if (pieces[0].equalsIgnoreCase(SpaceTypeTAZTarget.NAME)) {
            int spacetype = Integer.parseInt(pieces[1]);
            int zone = Integer.parseInt(pieces[2]);
            return new SpaceTypeTAZTarget(zone, spacetype);
        } else if (pieces[0].equalsIgnoreCase(SpaceTypeLUZTarget.NAME)) {
            int spacetype = Integer.parseInt(pieces[1]);
            int zone = Integer.parseInt(pieces[2]);
            return new SpaceTypeLUZTarget(zone, spacetype);
        } else if (pieces[0].equalsIgnoreCase(SpaceTypeTotalTarget.NAME)) {
            int spacetype = Integer.parseInt(pieces[1]);
            return new SpaceTypeTotalTarget(spacetype);
        } else if (pieces[0]
                .equalsIgnoreCase(RedevelopmentIntoSpaceTypeTarget.NAME)) {
            int spacetype = Integer.parseInt(pieces[1]);
            return new RedevelopmentIntoSpaceTypeTarget(spacetype);
        } else if (pieces[0].equalsIgnoreCase(SpaceTypeIntensityTarget.NAME)) {
            int spacetype = Integer.parseInt(pieces[1]);
            return new SpaceTypeIntensityTarget(spacetype);
        } else if (pieces[0].equalsIgnoreCase(SpaceTypeAdHocTazGroupTarget.NAME)) {
            int spacetype = Integer.parseInt(pieces[1]);
            int groupNum = Integer.parseInt(pieces[2]);
            Set<Integer> group = getGroup(groupNum);
            return new SpaceTypeAdHocTazGroupTarget(groupNum, group, spacetype);

        } else if (pieces[0]
                .equalsIgnoreCase(SpaceGroupRenovationTarget.NAME)) {
            return new SpaceGroupRenovationTarget(pieces);
        } else if (pieces[0]
                .equalsIgnoreCase(SpaceGroupDemolitionTarget.NAME)) {
            return new SpaceGroupDemolitionTarget(pieces);
        } else if (pieces[0]
                .equalsIgnoreCase("additiontypes")) {
            return new AdditionIntoSpaceTypesTarget(pieces);
        } else if (pieces[0]
                .equalsIgnoreCase(AdditionIntoSpaceTypesTarget.NAME)) {
            return new AdditionIntoSpaceTypesTarget(pieces); // plural, more
                                                             // than one space
                                                             // type
        } else
            throw new IllegalArgumentException(
                    "Target type not recognized: " + pieces[0]);
    }

    private void readCoeffsFile(String filename, boolean diagonal)
            throws IOException {
        BufferedReader reader = null;
        readCoeffs = new ArrayList<Coefficient>();
        coeffMeans = new HashMap<Coefficient, Double>();
        coeffStartingValues = new HashMap<Coefficient, Double>();
        coeffDevcor = new HashMap<Coefficient, Map<Coefficient, Double>>();
        try {
            reader = new BufferedReader(new FileReader(filename));
            String line = null;
            do {
                line = reader.readLine();
                if (line != null) {
                    String[] arrline = line.split(",");
                    if (diagonal && arrline.length != 4) {
                        loggerf.throwFatal(
                                "Invalid format in coefficient file: \"%s\"",
                                line);
                    }
                    Coefficient coeff = coeffForName(arrline[0]);
                    coeffMeans.put(coeff, new Double(arrline[1]));
                    coeffStartingValues.put(coeff, new Double(arrline[2]));
                    readCoeffs.add(coeff);

                    // Put the matrix in a useful format.
                    Map<Coefficient, Double> row = new HashMap<Coefficient, Double>();
                    coeffDevcor.put(coeff, row);
                    if (diagonal) {
                        double value = Double.parseDouble(arrline[3]);
                        row.put(coeff, value);
                    } else {
                        for (int i = 0; i < readCoeffs.size(); i++) {
                            if (i + 3 >= arrline.length)
                                loggerf.throwFatal(
                                        "Not enough entries for parameter %s",
                                        arrline[0]);
                            double value = Double.parseDouble(arrline[i + 3]);
                            Coefficient currCoeff = readCoeffs.get(i);
                            row.put(currCoeff, value);
                            coeffDevcor.get(currCoeff).put(coeff, value);
                        }
                    }
                }
            } while (line != null);
        } finally {
            if (reader != null)
                reader.close();
        }
    }

    private Coefficient coeffForName(String name) {
        String[] pieces = name.split("-");
        int arg = Integer.parseInt(pieces[1]);
        if (pieces[0]
                .equalsIgnoreCase(DensityShapingFunctionParameter.ABOVE_STEP_POINT_ADJ)) {
            int stepPointNumber = Integer.parseInt(pieces[2]);
            return DensityShapingFunctionParameter.getAboveStepPointAdj(arg, stepPointNumber);
        }
        else if (pieces[0].equalsIgnoreCase(SpaceTypeCoefficient.ADD_NEW_DISP))
            return SpaceTypeCoefficient.getAddNewDisp(arg);
        else if (pieces[0]
                .equalsIgnoreCase(SpaceTypeCoefficient.ADD_TRANSITION_CONST))
            return SpaceTypeCoefficient.getAddTransitionConst(arg);
        else if (pieces[0]
                .equalsIgnoreCase(SpaceTypeCoefficient.CHANGE_OPTIONS_DISP))
            return SpaceTypeCoefficient.getChangeOptionsDisp(arg);
        else if (pieces[0]
                .equalsIgnoreCase(SpaceTypeCoefficient.DEMOLISH_DERELICT_DISP))
            return SpaceTypeCoefficient.getDemolishDerelictDisp(arg);
        else if (pieces[0].equalsIgnoreCase(
                SpaceTypeCoefficient.DEMOLISH_TRANSITION_CONST))
            return SpaceTypeCoefficient.getDemolishTransitionConst(arg);
        else if (pieces[0].equalsIgnoreCase(
                SpaceTypeCoefficient.DERELICT_TRANSITION_CONST))
            return SpaceTypeCoefficient.getDerelictTransitionConst(arg);
        else if (pieces[0]
                .equalsIgnoreCase(SpaceTypeCoefficient.INTENSITY_DISP))
            return SpaceTypeCoefficient.getIntensityDisp(arg);
        else if (pieces[0].equalsIgnoreCase(
                SpaceTypeCoefficient.NEW_FROM_TRANSITION_CONST))
            return SpaceTypeCoefficient.getNewFromTransitionConst(arg);
        else if (pieces[0]
                .equalsIgnoreCase(SpaceTypeCoefficient.NEW_TO_TRANSITION_CONST))
            return SpaceTypeCoefficient.getNewToTransitionConst(arg);
        else if (pieces[0].equalsIgnoreCase(SpaceTypeCoefficient.NEW_TYPE_DISP))
            return SpaceTypeCoefficient.getNewTypeDisp(arg);
        else if (pieces[0]
                .equalsIgnoreCase(SpaceTypeCoefficient.NO_CHANGE_CONST))
            return SpaceTypeCoefficient.getNoChangeConst(arg);
        else if (pieces[0]
                .equalsIgnoreCase(SpaceTypeCoefficient.NO_CHANGE_DISP))
            return SpaceTypeCoefficient.getNoChangeDisp(arg);
        else if (pieces[0]
                .equalsIgnoreCase(SpaceTypeCoefficient.RENOVATE_ADD_NEW_DISP))
            return SpaceTypeCoefficient.getRenovateAddNewDisp(arg);
        else if (pieces[0]
                .equalsIgnoreCase(SpaceTypeCoefficient.RENOVATE_DERELICT_CONST))
            return SpaceTypeCoefficient.getRenovateDerelictConst(arg);
        else if (pieces[0].equalsIgnoreCase(
                SpaceTypeCoefficient.RENOVATE_TRANSITION_CONST))
            return SpaceTypeCoefficient.getRenovateTransitionConst(arg);
        else if (pieces[0].equalsIgnoreCase(DensityShapingFunctionParameter.STEP_POINT)) {
            int stepPointNumber = Integer.parseInt(pieces[2]);
            return DensityShapingFunctionParameter.getStepPoint(arg, stepPointNumber);
        } else if (pieces[0]
                .equalsIgnoreCase(DensityShapingFunctionParameter.STEP_POINT_AMOUNT)) {
            int stepPointNumber = Integer.parseInt(pieces[2]);
            return DensityShapingFunctionParameter.getStepPointAmount(arg, stepPointNumber);
        }
        else if (pieces[0]
                .equalsIgnoreCase(TransitionConstant.TRANSITION_CONST)) {
            int tospace = Integer.parseInt(pieces[2]);
            return TransitionConstant.getCoeff(arg, tospace);
        } else if (pieces[0]
                .equalsIgnoreCase(TazGroupCoefficient.CONSTRUCTION_CONST)) {
            return TazGroupCoefficient.getConstructionConstant(arg);
        } else
            throw new IllegalArgumentException(
                    "Parameter type not recognized: " + pieces[0]);
    }

    @Override
    public List<EstimationTarget> readTargets() {
        return new ArrayList<EstimationTarget>(readTargets);
    }

    @Override
    public double[][] readTargetVariance(List<EstimationTarget> targets) {
        double[][] result = new double[targets.size()][targets.size()];
        int i = 0;
        for (EstimationTarget target1 : targets) {
            int j = 0;
            Map<EstimationTarget, Double> row = targetDevcor.get(target1);
            if (row != null) {
                for (EstimationTarget target2 : targets) {
                    Double value = row.get(target2);
                    if (value == null)
                        result[i][j] = 0;
                    else
                        result[i][j] = value;
                    j++;
                }
            }
            i++;
        }
        result = devcorToVariance(result);
        return result;
    }

    @Override
    public List<Coefficient> readCoeffs() {
        return new ArrayList<Coefficient>(readCoeffs);
    }

    @Override
    public double[] readPriorMeans(List<Coefficient> coeffs) {
        double[] result = new double[coeffs.size()];
        int i = 0;
        for (Coefficient coeff : coeffs) {
            result[i] = coeffMeans.get(coeff);
            i++;
        }

        return result;
    }

    @Override
    public double[] readStartingValues(List<Coefficient> coeffs) {
        double[] result = new double[coeffs.size()];
        int i = 0;
        for (Coefficient coeff : coeffs) {
            result[i] = coeffStartingValues.get(coeff);
            i++;
        }

        return result;
    }

    @Override
    public double[][] readPriorVariance(List<Coefficient> coeffs) {
        double[][] result = new double[coeffs.size()][coeffs.size()];
        int i = 0;
        for (Coefficient coeff1 : coeffs) {
            int j = 0;
            Map<Coefficient, Double> row = coeffDevcor.get(coeff1);
            if (row != null) {
                for (Coefficient coeff2 : coeffs) {
                    Double value = row.get(coeff2);
                    if (value == null)
                        result[i][j] = 0;
                    else
                        result[i][j] = value;
                    j++;
                }
            }
            i++;
        }
        result = devcorToVariance(result);
        return result;
    }

    // Converts a deviation-correlation matrix into a variance matrix
    // (in-place).
    private double[][] devcorToVariance(double[][] devcor) {
        // Convert off-diagonals first.
        double[][] variance = devcor;
        for (int i = 0; i < devcor.length; i++) {
            for (int j = 0; j < devcor[i].length; j++) {
                if (j != i)
                    variance[i][j] = devcor[i][j] * devcor[i][i] * devcor[j][j];
            }
        }
        // Convert diagonals.
        for (int i = 0; i < devcor.length; i++) {
            variance[i][i] = devcor[i][i] * devcor[i][i];
        }
        return variance;
    }

    @Override
    public void applyTransforms(List<Coefficient> coeffs) {
        for (Map.Entry<Coefficient, Transform> entry : transforms.entrySet()) {
            Coefficient coeff = entry.getKey();
            if (coeffs.contains(coeff)) {
                Transform transform = entry.getValue();
                if (transform.type.equalsIgnoreCase("sigmoid")) {
                    if (coeff instanceof DispersionParameter) {
                        ((DispersionParameter) coeff)
                                .useSigmoidTransform(transform.args[0]);
                    } else {
                        loggerf.throwFatal(
                                "Only dispersion parameters can have a sigmoid transform");
                    }
                } else {
                    loggerf.throwFatal("Unrecognized transform \"%s\"",
                            transform.type);
                }
            }
        }
    }

    private static class Transform {
        private String type;
        private double[] args;
    }
}
