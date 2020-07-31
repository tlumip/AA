package com.hbaspecto.pecas.sd.estimation;

import java.io.BufferedWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import com.hbaspecto.discreteChoiceModelling.Coefficient;
import com.hbaspecto.pecas.land.LandInventory;
import com.hbaspecto.pecas.sd.LandPassRunner;

import no.uib.cipr.matrix.Matrix;
import no.uib.cipr.matrix.Vector;

public class ExpectedTargetModel implements DifferentiableModel {
    private int concurrency;

    private List<Coefficient> myCoeffs;
    private LandInventory land;

    private List<EstimationTarget> lastTargetValues;
    private Vector lastParamValues;

    private Vector targetValues;
    private Matrix jacobian;

    /**
     * Constructs an <code>ExpectedTargetModel</code> that can be used in
     * estimating the specified coefficients. All calculations will happen in
     * the calling thread.
     * 
     * @param coeffs
     *            The list of coefficients that are being estimated.
     * @param landInventory
     *            The land inventory to operate on.
     */
    public ExpectedTargetModel(List<Coefficient> coeffs,
            LandInventory landInventory) {
        myCoeffs = new ArrayList<Coefficient>(coeffs);
        lastTargetValues = new ArrayList<EstimationTarget>();
        land = landInventory;
        concurrency = 0;
    }

    /**
     * Constructs an <code>ExpectedTargetModel</code> that can be used in
     * estimating the specified coefficients. Calculations will happen in
     * <code>numThreads</code> separate worker threads (not including the
     * calling thread, which will be used for accessing the land inventory).
     * 
     * @param coeffs
     *            The list of coefficients that are being estimated.
     * @param landInventory
     *            The land inventory to operate on.
     * @param numThreads
     *            The number of worker threads to use. Must be at least 1.
     */
    public ExpectedTargetModel(List<Coefficient> coeffs,
            ConcurrentLandInventory landInventory, int numThreads) {
        if (numThreads < 1)
            throw new IllegalArgumentException();
        myCoeffs = new ArrayList<Coefficient>(coeffs);
        lastTargetValues = new ArrayList<EstimationTarget>();
        land = landInventory;
        concurrency = numThreads;
    }

    @Override
    public Vector getTargetValues(List<EstimationTarget> targets, Vector params)
            throws OptimizationException {
        // Do target value and derivative calculations at the same time, only if
        // they
        // have not already been done for this combination of targets and
        // parameters.
        if (!(targets.equals(lastTargetValues) && equals(params,
                lastParamValues)))
            calculateAllValues(targets, params);
        lastTargetValues.clear();
        lastTargetValues.addAll(targets);
        lastParamValues = params.copy();
        return targetValues;
    }

    @Override
    public Matrix getJacobian(List<EstimationTarget> targets, Vector params)
            throws OptimizationException {
        if (!(targets.equals(lastTargetValues) && equals(params,
                lastParamValues)))
            calculateAllValues(targets, params);
        lastTargetValues.clear();
        lastTargetValues.addAll(targets);
        lastParamValues = params.copy();
        return jacobian;
    }

    /**
     * Calculates the calibration values at the current parameter values
     */
    public void calculateAllValues(List<EstimationTarget> targets)
            throws OptimizationException {

        List<ExpectedValue> expValueObjects = EstimationTarget.convertToExpectedValueObjects(targets);
        ExpectedValueFilter filter = new ByZonePrefilter(expValueObjects, land);
        EstimationMatrix matrix = new EstimationMatrix(filter, myCoeffs);

        LandPassRunner pass = new LandPassRunner(land, zr -> {
            zr.startCaching(land);
            zr.addExpectedValuesToMatrix(matrix, land);
            zr.addDerivativesToMatrix(matrix, land);
            zr.endCaching(land);
        });
        
        if (concurrency == 0) {
            pass.calculateInThisThread();
        } else {
            pass.calculateConcurrently(concurrency);
        }
        
        targetValues = matrix.getTargetValues(targets);
        
        jacobian = matrix.getTargetDerivatives(targets);
    }
    
    private void calculateAllValues(List<EstimationTarget> targets,
            Vector params) throws OptimizationException {
        setCoefficients(params);
        calculateAllValues(targets);
    }

    private void setCoefficients(Vector params) {
        int i = 0;
        for (Coefficient coeff : myCoeffs) {
            coeff.setTransformedValue(params.get(i));
            i++;
        }
        land.commitAndStayConnected();
    }

    private boolean equals(Vector v1, Vector v2) {
        if (v1 == null || v2 == null)
            return false;
        if (v1.size() != v2.size())
            return false;
        for (int i = 0; i < v1.size(); i++)
            if (v1.get(i) != v2.get(i))
                return false;
        return true;
    }

    public void printCurrentValues(BufferedWriter writer,
            List<EstimationTarget> targets, TargetPrinter printer)
            throws IOException {
        List<Field> header = printer.getCommonFields(targets);
        writer.write(header.stream().map(Field::getName)
                .collect(Collectors.joining(",")));
        writer.write(",TargetValue,CurValue");
        writer.newLine();
        for (EstimationTarget target : targets) {
            List<String> row = printer.adaptToFields(target, header);
            writer.write(String.join(",", row));
            writer.write("," + target.getTargetValue());
            writer.write("," + target.getModelledValue());
            writer.newLine();
        }
    }

    public void printCurrentDerivatives(BufferedWriter writer,
            ParameterPrinter paramPrinter, TargetPrinter targetPrinter)
            throws IOException {
        // Prints the Jacobian matrix in a nice table format
        // First, the parameter names across the top.
        writer.write(",");
        for (int j = 0; j < myCoeffs.size(); j++)
            writer.write("," + paramPrinter.asString(myCoeffs.get(j)));
        // The current parameter values.
        writer.newLine();
        writer.write(",");
        for (int j = 0; j < lastParamValues.size(); j++)
            writer.write("," + lastParamValues.get(j));
        // The target names, current target values, and derivatives.
        writer.newLine();
        for (int i = 0; i < lastTargetValues.size(); i++) {
            writer.write(targetPrinter.asString(lastTargetValues.get(i)) + ","
                    + lastTargetValues.get(i).getTargetValue());
            for (int j = 0; j < myCoeffs.size(); j++)
                writer.write("," + jacobian.get(i, j));
            writer.newLine();
        }
    }
}
