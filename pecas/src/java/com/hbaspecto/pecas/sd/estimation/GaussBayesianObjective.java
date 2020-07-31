package com.hbaspecto.pecas.sd.estimation;

import java.io.BufferedWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.log4j.Logger;

import com.hbaspecto.discreteChoiceModelling.Coefficient;

import no.uib.cipr.matrix.DenseCholesky;
import no.uib.cipr.matrix.DenseMatrix;
import no.uib.cipr.matrix.DenseVector;
import no.uib.cipr.matrix.Matrices;
import no.uib.cipr.matrix.Matrix;
import no.uib.cipr.matrix.Vector;

public class GaussBayesianObjective implements ObjectiveFunction {
    private DifferentiableModel myModel;
    private List<EstimationTarget> myTargets;
    private List<Coefficient> myCoeffs;
    private Matrix myInverseTargetVariance;
    private Vector myMean;
    private Matrix myInversePriorVariance;
    
    private int numParams;
    
    // Values for logging.
    private double currentObjectiveValue;
    private double currentParameterError;
    private double currentTargetError;
    private Vector currentParameterValues;
    private double[] currentModelledValues;
    private Vector currentGradient;
    private Matrix currentHessian;
    
    private boolean previousValuesExist = false;
    private double previousObjectiveValue;
    private Vector previousParameterValues;
    private double[] previousModelledValues;

    public GaussBayesianObjective(DifferentiableModel model, List<Coefficient> coeffs,
            List<EstimationTarget> targets, Matrix targetVariance, Vector priorMean, Matrix priorVariance) {
        numParams = priorMean.size();
        
        myModel = model;
        myTargets = new ArrayList<EstimationTarget>(targets);
        myCoeffs = new ArrayList<Coefficient>(coeffs);
        
        myMean = priorMean.copy();
        
        // Invert the target variance.
        DenseMatrix identity = Matrices.identity(targets.size());
        DenseCholesky cholesky = DenseCholesky.factorize(targetVariance);
        myInverseTargetVariance = cholesky.solve(identity);
        
        // Invert the prior variance.
        identity = Matrices.identity(numParams);
        cholesky = DenseCholesky.factorize(priorVariance);
        myInversePriorVariance = cholesky.solve(identity);
    }
    
    @Override
	public void setParameterValues(Vector params)
    {
    	currentParameterValues = params;
    };

    @Override
    public double getValue() throws OptimizationException {
        Vector modelledValues = myModel.getTargetValues(myTargets, currentParameterValues);
        currentModelledValues = Matrices.getArray(modelledValues);
        Vector targetErrors = modelledValues.copy();
        targetErrors.add(-1, getMyTargetValues());
        Vector paramErrors = currentParameterValues.copy();
        paramErrors.add(-1, myMean);
        
        currentTargetError = quadraticForm(targetErrors, myInverseTargetVariance);
        currentParameterError = quadraticForm(paramErrors, myInversePriorVariance);
        currentObjectiveValue = currentTargetError + currentParameterError;
        return currentObjectiveValue;
    }

    @Override
    public Vector getGradient(Vector params) throws OptimizationException {
        Vector modelledValues = myModel.getTargetValues(myTargets, params);
        Vector targetErrors = modelledValues.copy();
        targetErrors.add(-1, getMyTargetValues());
        targetErrors = myInverseTargetVariance.mult(targetErrors, targetErrors.copy());
        Vector paramErrors = params.copy();
        paramErrors.add(-1, myMean);
        
        Matrix jacobian = myModel.getJacobian(myTargets, params);
        
        currentGradient = new DenseVector(numParams);
        currentGradient = jacobian.transMult(targetErrors, currentGradient);
        currentGradient = myInversePriorVariance.multAdd(paramErrors, currentGradient);
        currentGradient.scale(2);
        
        return currentGradient;
    }

    @Override
    public Matrix getHessian(Vector params) throws OptimizationException {
        Matrix jacobian = myModel.getJacobian(myTargets, params);
        Matrix varianceTimesJacobian = new DenseMatrix(jacobian.numRows(), jacobian.numColumns());
        varianceTimesJacobian = myInverseTargetVariance.mult(jacobian, varianceTimesJacobian);
        
        currentHessian = new DenseMatrix(numParams, numParams);
        currentHessian = jacobian.transAmult(varianceTimesJacobian, currentHessian);
        currentHessian.add(myInversePriorVariance);
        currentHessian.scale(2);
        
        return currentHessian;
    }
    
    private double quadraticForm(Vector x, Matrix a) {
        Matrix xm = new DenseMatrix(x);
        Matrix xmt = new DenseMatrix(1, x.size());
        xmt = xm.transpose(xmt);
        xmt = xmt.mult(a, xmt.copy());
        Matrix result = new DenseMatrix(1, 1);
        result = xmt.mult(xm, result);
        return result.get(0, 0);
    }
    
    private Vector getMyTargetValues() {
        Vector myTargetValues = new DenseVector(myTargets.size());
        int i = 0;
        for(EstimationTarget target : myTargets) {
            myTargetValues.set(i, target.getTargetValue());
            i++;
        }
        
        return myTargetValues;
    }

	public void logParameters(Logger logger) {
	    
        logger.info("Parameter values:");
        int i = 0;
        for(Coefficient coeff : myCoeffs) {
            String line = "Parameter " + coeff.getName()
                    + ": prior mean = " + myMean.get(i)
                    + ", current value = " + currentParameterValues.get(i);
            if(previousValuesExist)
                line = line + ", previous value = " + previousParameterValues.get(i);
            logger.info(line);
            i++;
        }
 	}

	public void logCurrentValues(Logger logger) {
    	logParameters(logger);
    	logTargetAndObjective(logger);
    }
	
	public void logTargetAndObjective(Logger logger) {
	    logObjective(logger);
        logger.info("Target values:");
        int i = 0;
        for(EstimationTarget target : myTargets) {
            String line = "Target " + target.getName()
                    + ": target value = " + target.getTargetValue()
                    + ", modelled value = " + currentModelledValues[i];
            if(previousValuesExist)
                line = line + ", previous value = " + previousModelledValues[i];
            logger.info(line);
            i++;
        }
 	}
	
	@Override
	public void logObjective(Logger logger) {
        logger.info("Current value of objective function = " + currentObjectiveValue);
        if(previousValuesExist)
            logger.info("Previous value of objective function = " + previousObjectiveValue);
        logger.info("Contribution from parameters = " + currentParameterError);
        logger.info("Contribution from targets = " + currentTargetError);
	}
    
    @Override
    public void storePreviousValues() {
        previousParameterValues = currentParameterValues;
        previousObjectiveValue = currentObjectiveValue;
        previousModelledValues = currentModelledValues;
        previousValuesExist = true;
    }
    
    public void printParameters(BufferedWriter writer, ParameterPrinter printer) throws IOException {
    	List<Field> header = printer.getCommonFields(myCoeffs);
    	writer.write(header.stream().map(Field::getName).collect(Collectors.joining(",")));
    	writer.write(",PriorMean,CurValue");
    	if(previousValuesExist)
    		writer.write(",PrevValue");
    	writer.newLine();
    	int i = 0;
        for(Coefficient coeff : myCoeffs) {
    	    List<String> row = printer.adaptToFields(coeff, header);
    		writer.write(String.join(",", row));
    		writer.write("," + myMean.get(i));
            writer.write("," + currentParameterValues.get(i));
            if(previousValuesExist)
                writer.write("," + previousParameterValues.get(i));
            writer.newLine();
            i++;
    	}
    }
    
    public void printTargetAndObjective(BufferedWriter writer, TargetPrinter printer) throws IOException {
    	writer.write("CurObj," + currentObjectiveValue);
    	writer.newLine();
        if(previousValuesExist) {
            writer.write("PrevObj," + previousObjectiveValue);
            writer.newLine();
        }
        writer.write("ParamError," + currentParameterError);
        writer.newLine();
        writer.write("TargetError," + currentTargetError);
        writer.newLine();
        
        List<Field> header = printer.getCommonFields(myTargets);
        writer.write(header.stream().map(Field::getName).collect(Collectors.joining(",")));
        writer.write(",TargetValue,CurValue");
        if(previousValuesExist)
        	writer.write(",PrevValue");
        writer.newLine();
        int i = 0;
        for(EstimationTarget target : myTargets) {
            List<String> row = printer.adaptToFields(target, header);
            writer.write(String.join(",", row));
            writer.write("," + target.getTargetValue());
            writer.write("," + currentModelledValues[i]);
            if(previousValuesExist)
                writer.write("," + previousModelledValues[i]);
            writer.newLine();
            i++;
        }
    }
	
	public void printGradient(BufferedWriter writer, ParameterPrinter printer) throws IOException {
	    List<Field> header = printer.getCommonFields(myCoeffs);
	    writer.write(header.stream().map(Field::getName).collect(Collectors.joining(",")));
		writer.write(",Derivative");
		writer.newLine();
		int i = 0;
		for(Coefficient coeff : myCoeffs) {
		    List<String> row = printer.adaptToFields(coeff, header);
			writer.write(String.join(",", row));
			writer.write("," + currentGradient.get(i));
			writer.newLine();
			i++;
		}
	}
	
    public void printHessian(BufferedWriter writer, ParameterPrinter printer) throws IOException {
    	// Prints the Hessian matrix in a nice table format
    	// First, the parameter names across the top.
    	for(int j = 0; j < myCoeffs.size(); j++)
    		writer.write("," + printer.asString(myCoeffs.get(j)));
    	writer.newLine();
    	for(int i = 0; i < currentHessian.numRows(); i++) {
    		writer.write(printer.asString(myCoeffs.get(i)));
    		for(int j = 0; j < myCoeffs.size(); j++)
    			writer.write("," + currentHessian.get(i, j));
    		writer.newLine();
    	}
    }
    
    public void printStdError(BufferedWriter writer, ParameterPrinter printer) throws IOException {
        for (Coefficient coeff : myCoeffs)
            writer.write("," + printer.asString(coeff));
        writer.newLine();
        int dim = myCoeffs.size();
        // The standard error is the inverse of half the Hessian for this objective function.
        Matrix stdError = currentHessian.copy().scale(0.5).solve(Matrices.identity(dim), new DenseMatrix(dim, dim));
        for (int i = 0; i < dim; i++) {
            writer.write(printer.asString(myCoeffs.get(i)));
            for (int j = 0; j < dim; j++) {
                if(i == j)
                    writer.write("," + Math.sqrt(stdError.get(i, i)));
                else
                    writer.write("," + stdError.get(i, j) / Math.sqrt(stdError.get(i, i) * stdError.get(j, j)));
            }
            writer.newLine();
        }
    }
}
