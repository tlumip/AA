package com.hbaspecto.pecas.sd.estimation;

import java.util.ArrayList;
import java.util.Collection;

import org.apache.log4j.Logger;

import com.hbaspecto.pecas.FormatLogger;

import no.uib.cipr.matrix.BandMatrix;
import no.uib.cipr.matrix.DenseCholesky;
import no.uib.cipr.matrix.DenseMatrix;
import no.uib.cipr.matrix.DenseVector;
import no.uib.cipr.matrix.Matrices;
import no.uib.cipr.matrix.Matrix;
import no.uib.cipr.matrix.Vector;

public class MarquardtMinimizer {
    private static Logger logger = Logger.getLogger(MarquardtMinimizer.class);
    private static FormatLogger loggerf = new FormatLogger(logger);
    private static final int CONVERGED = 0;
    private static final int MAX_ITERATIONS = 1;
    private static final int INVALID_STEP = 2;
    private static final int NOT_RUN = -1;

    private static final double MINIMUM_LAMBDA = 1E-7;
    private static final double MAXIMUM_LAMBDA = 1E6;

    private Collection<Constraint> constraints;
    private ObjectiveFunction obj;
    private double lambda;
    private double initialLambda;
    private double lambdaIncrement;
    private double lambdaDecrement;
    private double[] maxParamChange;

    private Vector parameters;

    private double currentObjective;

    private int iteration;
    private int termination;
    // private int damper;

    private int penaltyIteration;
    private double alpha;
    private boolean penaltyConverged;

    /**
     * Constructs a new optimizer that minimizes the given objective function.
     * By default, the minimum step size is 0.0001, the maximum step size is 1,
     * and the initial Marquardt weighting factor is 0.01.
     * 
     * @param obj The objective function.
     * @param initialGuess Values of the objective function's parameters to use
     *            as an initial guess.
     * @throws OptimizationException if the initial guess causes an error in the
     *             objective function.
     */
    public MarquardtMinimizer(ObjectiveFunction objective, Vector initialGuess)
            throws OptimizationException {
        constraints = new ArrayList<Constraint>();
        obj = objective;
        lambda = 600;
        initialLambda = lambda;
        lambdaIncrement = 2;
        lambdaDecrement = 0.9;

        parameters = initialGuess.copy();
        initMaxParamChange();

        obj.setParameterValues(parameters);

        try {
            currentObjective = obj.getValue() + getPenaltyFunction(parameters);
        } catch (OptimizationException e) {
            String msg = "Objective function could not be evaluated at guess";
            throw new OptimizationException(msg, e);
        }

        iteration = 0;
        penaltyIteration = 0;
        alpha = 0.001 * currentObjective;
        // damper = 1;
        termination = NOT_RUN;
    }

    /**
     * Readies the optimizer for a new optimization, using the given initial
     * guess.
     * 
     * @param initialGuess The new initial guess.
     * @throws OptimizationException if the initial guess causes an error in the
     *             objective function.
     */
    public void reset(Vector initialGuess) throws OptimizationException {
        parameters = initialGuess.copy();
        initMaxParamChange();
        obj.setParameterValues(parameters);
        try {
            currentObjective = obj.getValue() + getPenaltyFunction(parameters);
        } catch (OptimizationException e) {
            String msg = "Objective function could not be evaluated at guess";
            throw new OptimizationException(msg, e);
        }
        lambda = initialLambda;
        iteration = 0;
        // damper = 1;
    }

    private void initMaxParamChange() {
        if (maxParamChange == null
                || parameters.size() != maxParamChange.length) {
            maxParamChange = new double[parameters.size()];
            for (int i = 0; i < maxParamChange.length; i++) {
                maxParamChange[i] = Double.POSITIVE_INFINITY;
            }
        }
    }

    /**
     * Readies the optimizer for a new constrained optimization, using the given
     * initial guess. This method is similar to <code>reset</code> except that
     * it also resets the constraint looseness and number of penalty function
     * iterations.
     * 
     * @param initialGuess The new initial guess.
     * @throws OptimizationException if the initial guess causes an error in the
     *             objective function.
     */
    public void resetPenalty(Vector initialGuess) throws OptimizationException {
        reset(initialGuess);
        penaltyIteration = 0;
        alpha = 0.001 * currentObjective;
    }

    /**
     * Sets the initial Newton/gradient descent factor (large values make the
     * step size smaller and more like gradient descent).
     * 
     * @param initialMarquardtFactor The initial factor.
     */
    public void setInitialMarquardtFactor(double initialMarquardtFactor) {
        lambda = initialMarquardtFactor;
        initialLambda = lambda;
    }

    public void setMarquardtFactorAdjustments(double increment, double decrement) {
        lambdaIncrement = increment;
        lambdaDecrement = decrement;
    }

    /**
     * Adds a constraint to the minimizer, confining the solution into a
     * feasible region.
     * 
     * @param cons The constraint.
     */
    public void addConstraint(Constraint cons) {
        constraints.add(cons);
    }

    /**
     * Returns all the constraints on this minimizer.
     * 
     * @return The constraints.
     */
    public Collection<Constraint> getConstraints() {
        return new ArrayList<Constraint>(constraints);
    }

    /**
     * Removes all constraints on this minimizer.
     */
    public void clearConstraints() {
        constraints.clear();
    }

    /**
     * Sets a maximum absolute change per iteration on each parameter. This
     * maximum applies regardless of the current lambda value.
     * 
     * @param maxChange
     */
    public void setMaxParameterChange(Vector maxChange) {
        maxParamChange = Matrices.getArray(maxChange);
        for (int i = 0; i < maxParamChange.length; i++) {
            maxParamChange[i] = Math.abs(maxParamChange[i]);
        }
    }

    /**
     * Performs a single iteration of the Marquardt method.
     * 
     * @return The new values of the objective function's parameters after the
     *         iteration.
     */
    public Vector doOneIteration() throws OptimizationException {
        return doOneIteration(null, null, 100);
    }

    private Vector doOneIteration(BeforeIterationCallback before,
            AfterIterationCallback after, int maxIts)
            throws OptimizationException {
        Vector gradient = obj.getGradient(parameters).add(
                getPenaltyFunctionGradient(parameters));
        Matrix origHessian = obj.getHessian(parameters).add(
                getPenaltyFunctionHessian(parameters));

        if (before != null)
            before.startIteration(iteration);

        Matrix correction = getMarquardtCorrection(origHessian);

        boolean foundValidStep = false;
        int attempt = 0;

        Vector newparams = null;
        while (!foundValidStep) {
            // add lambda*correction to hessian, to adjust step size (large
            // lambda means small step size and more like steepest descent)
            Matrix hessian = origHessian.copy();
            hessian.add(lambda, correction);

            // Solve the system of equations using Cholesky decomposition.
            logger.info("Solving for the new parameter values...");
            DenseCholesky cholesky = DenseCholesky.factorize(hessian);
            Matrix mstep = cholesky.solve(new DenseMatrix(gradient));
            Vector step = convertColumnMatrixToVector(mstep);
            step = step.scale(-1);
            for (int i = 0; i < step.size(); i++) {
                if (step.get(i) > maxParamChange[i]) {
                    step.set(i, maxParamChange[i]);
                } else if (step.get(i) < -maxParamChange[i]) {
                    step.set(i, -maxParamChange[i]);
                }
            }

            // Find the new parameter values.
            newparams = new DenseVector(parameters);
            newparams.add(step);
            logger.info("Found new parameter values");
            
            obj.setParameterValues(newparams);
            try {
                double newobj = obj.getValue() + getPenaltyFunction(newparams);
                obj.logObjective(logger);
                // Determine if the step is acceptable, readjust parameters for
                // next iteration.
                if (newobj < currentObjective) {
                    // Step may be is acceptable.
                    logger.info("Potentially good step, checking...");
                    Vector oldParameters = parameters; // just in case
                    parameters = newparams;
                    Matrix newHessian = obj.getHessian(parameters).add(
                            getPenaltyFunctionHessian(parameters));
                    if(!isHessianOkay(newHessian)) {
                        // ooops, this is a bad step.
                        parameters = oldParameters;
                        lambda = lambdaIncrement * lambda;
                        logger.info("***Step looked good but Hessian had infinity " + "or NaN in it so we have to back up, lambda set to "
                                + lambda);
                    } else {
                        currentObjective = newobj;
                        lambda = Math.max(lambdaDecrement * lambda,
                                MINIMUM_LAMBDA);
                        logger.info("Found valid step, setting lambda to "
                                + lambda);
                        foundValidStep = true;
                    }
                } else {
                    logger.info("Step leads to worse goodness of fit, backing up");
                    lambda = lambdaIncrement * lambda;
                    logger.info("Interpolated step, now setting lambda to "
                            + lambda);
                }
            } catch (OptimizationException e) {
                logger.info("Overflow error, backing up");
                lambda = lambdaIncrement * lambda;
                logger.info("Setting lambda to " + lambda);
            }

            if (!foundValidStep) {
                attempt++;
                if (after != null)
                    after.finishedFailedIteration(iteration, attempt);
                if (before != null)
                    before.startFailedIteration(iteration, attempt);
            }

            if (lambda > MAXIMUM_LAMBDA) {
                throw new OptimizationException("Cannot find a valid step");
            }
        }

        logger.info("Finished iteration " + iteration + ".");

        iteration++;

        if (after != null)
            after.finishedIteration(iteration);

        return parameters.copy();
    }

    private double getPenaltyFunction(Vector params) {
        double penalty = 0.0;
        for (Constraint cons : constraints)
            penalty += cons.getPenaltyFunction(params, alpha);
        return penalty;
    }

    private Vector getPenaltyFunctionGradient(Vector params) {
        Vector gradient = new DenseVector(parameters.size());
        for (Constraint cons : constraints)
            gradient.add(cons.getPenaltyFunctionGradient(params, alpha));
        return gradient;
    }

    private Matrix getPenaltyFunctionHessian(Vector params) {
        Matrix hessian = new DenseMatrix(parameters.size(), parameters.size());
        for (Constraint cons : constraints)
            hessian.add(cons.getPenaltyFunctionHessian(params, alpha));
        return hessian;
    }

    private Vector convertColumnMatrixToVector(Matrix column) {
        int n = column.numRows();
        Vector result = new DenseVector(n);
        for (int i = 0; i < n; i++)
            result.set(i, column.get(i, 0));
        return result;
    }

    private Matrix getMarquardtCorrection(Matrix hessian) {
        int n = hessian.numRows();
        Matrix result = new BandMatrix(n, 0, 0);
        for (int i = 0; i < n; i++) {
            double entry = hessian.get(i, i);
            if (entry == 0)
                entry = 1;
            result.set(i, i, entry);
        }

        return result;
    }

    private boolean isHessianOkay(Matrix hessian) {
        Double hessianNorm = hessian.norm(Matrix.Norm.One);
        return !hessianNorm.isInfinite() && !hessianNorm.isNaN();
    }
    
    private void logInvalidGradient(Vector gradient) {
        loggerf.info("The following gradient elements are invalid:");
        for (int i = 0; i < gradient.size(); i++) {
            double value = gradient.get(i);
            if (Double.isInfinite(value) || Double.isNaN(value)) {
                loggerf.info("Element %d: %f", i, value);
            }
        }
    }

    public static interface BeforeIterationCallback {
        /**
         * Called when an iteration is about to start.
         * 
         * @param iteration The iteration that is about to start. This can range
         *            from 0 to one less than the maximum allowed iterations.
         */
        public abstract void startIteration(int iteration);

        /**
         * Called when an iteration <i>would</i> be about to start, except that
         * it will never actually be completed because the current parameters
         * are invalid.
         * 
         * @param iteration The iteration that is in progress.
         * @param attempt The number of attempts at the iteration that have been
         *            made so far. This is 1 for the first failed attempt and
         *            increments thereafter.
         */
        public abstract void startFailedIteration(int iteration, int attempt);
    }

    public static interface AfterIterationCallback {
        /**
         * Called when an iteration just finished.
         * 
         * @param iteration The next iteration that would start. This can range
         *            from 1 to the maximum allowed iterations, inclusive.
         */
        public abstract void finishedIteration(int iteration);

        /**
         * Called when a failed attempt at an iteration has just finished.
         * 
         * @param iteration The iteration that is in progress.
         * @param attempt The number of attempts at the iteration that have been
         *            made so far. This is 1 for the first failed attempt and
         *            increments thereafter.
         */
        public abstract void finishedFailedIteration(int iteration, int attempt);
    }

    /**
     * Performs iterations of the Marquardt method until convergence occurs.
     * Convergence is deemed to have occurred if the coefficients in three
     * successive iterations differ by less than <code>epsilon</code>. This
     * method will find the minimum for a single weighting of any penalty
     * functions; use <code>minimize()</code> to do the full solution, including
     * reducing the weighting of the penalty function until the solution is
     * found.
     * 
     * @param epsilon The threshold at which the optimization is deemed to have
     *            converged. There is a separate epsilon for each coefficient,
     *            so that coefficients with different orders of magnitude can be
     *            handled properly; the epsilons must be given in the same order
     *            as the coefficients.
     * @param maxIterations The maximum number of iterations - after that number
     *            of iterations, the method will return its results even if it
     *            has not converged.
     * @return The values of the parameters at the optimum.
     */
    public Vector iterateToConvergence(Vector epsilon, int maxIterations) {
        return iterateToConvergence(epsilon, maxIterations, null, null);
    }

    /**
     * Performs iterations of the Marquardt method until convergence occurs.
     * Convergence is deemed to have occurred if the coefficients in three
     * successive iterations differ by less than <code>epsilon</code>. This
     * method will find the minimum for a single weighting of any penalty
     * functions; use <code>minimize()</code> to do the full solution, including
     * reducing the weighting of the penalty function until the solution is
     * found.
     * 
     * @param epsilon The threshold at which the optimization is deemed to have
     *            converged. There is a separate epsilon for each coefficient,
     *            so that coefficients with different orders of magnitude can be
     *            handled properly; the epsilons must be given in the same order
     *            as the coefficients.
     * @param maxIterations The maximum number of iterations - after that number
     *            of iterations, the method will return its results even if it
     *            has not converged.
     * @param before A callback to execute each iteration once the
     *            gradient/hessian is computed but before the new parameters are
     *            chosen.
     * @param after A callback to execute after each iteration.
     * @return The values of the parameters at the optimum.
     */
    public Vector iterateToConvergence(Vector epsilon, int maxIterations,
            BeforeIterationCallback before, AfterIterationCallback after) {
        iteration = 0;
        Vector previousParameters = parameters;
        boolean lastTwoIterationsConverged = false;
        boolean converged = false;
        logger.info("Starting optimization - max iterations = " + maxIterations);
        obj.logObjective(logger);
        obj.storePreviousValues();
        
        try {
            Matrix hessian = obj.getHessian(parameters).add(getPenaltyFunctionHessian(parameters));
            
            if (!isHessianOkay(hessian)) {
                logger.info("Initial guess led to infinity or NaN in Hessian");
                logInvalidGradient(obj.getGradient(parameters));
                if (before != null)
                    before.startIteration(iteration);
                termination = INVALID_STEP;
                return parameters;
            }
            
            while (!converged && iteration < maxIterations) {
                doOneIteration(before, after, maxIterations);
                boolean thisIterationConverged = checkConvergence(parameters,
                        previousParameters, epsilon);
                obj.storePreviousValues();
                previousParameters = parameters;
                converged = thisIterationConverged
                        && lastTwoIterationsConverged;
                lastTwoIterationsConverged = thisIterationConverged;
            }
            if (iteration < maxIterations)
                termination = CONVERGED;
            else
                termination = MAX_ITERATIONS;
        } catch (OptimizationException e) {
            termination = INVALID_STEP;
        }

        penaltyIteration++;
        alpha = alpha / 10;

        return parameters.copy();
    }

    private boolean checkConvergence(Vector currentParameters,
            Vector previousParameters, Vector epsilon) {
        for (int i = 0; i < currentParameters.size(); i++) {
            double diff = Math.abs(currentParameters.get(i)
                    - previousParameters.get(i));
            if (diff >= epsilon.get(i))
                return false;
        }
        return true;
    }

    /**
     * !!! Note - this method is not currently supported !!! Finds the minimum
     * of the objective function, subject to the constraints provided using the
     * <code>addConstraint</code> method. This method calls
     * <code>iterateToConvergence</code> repeatedly while making the constraints
     * "looser" until that method's output changes by less than
     * <code>penaltyEpsilon</code> for three consecutive iterations. The methods
     * <code>lastRunConverged</code>, <code>lastRunMaxIterations</code>, and
     * <code>lastRunInvalidStep</code> refer to the termination cause for the
     * last call to <code>iterateToConvergence</code> (with the loosest
     * constraint), since it is that iteration's termination that is relevant
     * for this method (e.g. if the last iteration returns normally, this
     * indicates a valid solution, even if an earlier iteration terminated
     * because of an exception). Note that if no constraints have been added,
     * this method does exactly the same thing as
     * <code>iterateToConvergence</code>.
     * 
     * @param epsilon The <code>epsilon</code> parameter to pass to
     *            <code>iterateToConvergence</code>.
     * @param maxIterations The <code>maxIterations</code> parameter to pass to
     *            <code>iterateToConvergence</code>.
     * @param penaltyEpsilon The threshold at which the minimum is deemed to
     *            have been found.
     * @param penaltyMaxIterations The maximum number of calls allowed to
     *            <code>iterateToConvergence</code>.
     * @return The values of the parameters at the optimum.
     * @throws OptimizationException if the current parameters are invalid.
     */
    public Vector minimize(Vector epsilon, int maxIterations,
            Vector penaltyEpsilon, int penaltyMaxIterations)
            throws OptimizationException {
        if (constraints.size() == 0)
            return iterateToConvergence(epsilon, maxIterations);

        Vector previousParameters = parameters;
        boolean lastTwoIterationsConverged = false;
        boolean converged = false;
        while (!converged && penaltyIteration < maxIterations) {
            reset(previousParameters);
            iterateToConvergence(epsilon, maxIterations);
            boolean thisIterationConverged = checkConvergence(parameters,
                    previousParameters, penaltyEpsilon);
            previousParameters = parameters;
            converged = thisIterationConverged && lastTwoIterationsConverged;
            lastTwoIterationsConverged = thisIterationConverged;
        }
        if (iteration < maxIterations)
            penaltyConverged = true;
        else
            penaltyConverged = false;
        return parameters.copy();
    }

    /**
     * Returns the current value of the objective function after the most recent
     * iteration or reset.
     * 
     * @return The current value of the objective function.
     */
    public double getCurrentObjectiveValue() {
        return currentObjective;
    }

    /**
     * Returns the number of iterations performed since the last call to
     * <code>reset()</code>.
     * 
     * @return The number of iterations.
     */
    public int getNumberOfIterations() {
        return iteration;
    }

    /**
     * Returns the number of penalty function iterations performed since the
     * last call to <code>resetPenalty()</code>.
     * 
     * @return The number of penalty function iterations.
     */
    public int getNumberOfPenaltyIterations() {
        return penaltyIteration;
    }

    /**
     * Determines whether the last execution of
     * <code>iterateToConvergence</code> converged on a solution.
     * 
     * @return True if the optimizer converged on a solution, and false
     *         otherwise (including if <code>iterateToConvergence</code> has
     *         never been invoked.
     */
    public boolean lastRunConverged() {
        return termination == CONVERGED;
    }

    /**
     * Determines whether the last execution of
     * <code>iterateToConvergence</code> hit the maximum number of iterations
     * before finding a solution.
     * 
     * @return True if the optimizer ran out of iterations, and false otherwise
     *         (including if <code>iterateToConvergence</code> has never been
     *         invoked.
     */
    public boolean lastRunMaxIterations() {
        return termination == MAX_ITERATIONS;
    }

    /**
     * Determines whether the last execution of <code>minimize</code> converged
     * on a solution
     * 
     * @return True of the optimizer converged on a solution, and false
     *         otherwise (including if <code>minimize</code> has never been
     *         invoked.
     */
    public boolean lastRunPenaltyConverged() {
        return penaltyConverged;
    }

    /**
     * Determines whether the last execution of <code>minimize</code> hit the
     * maximum number of iterations before finding a solution.
     * 
     * @return True if the optimizer ran out of iterations, and false otherwise
     *         (including if <code>minimize</code> has never been invoked.
     */
    public boolean lastRunPenaltyMaxIterations() {
        return !penaltyConverged;
    }

    /**
     * Determines whether the last execution of
     * <code>iterateToConvergence</code> halted because a valid step (i.e. one
     * that decreased the objective function) could not be found. In particular,
     * returns true if the objective function threw an exception for every step
     * tried.
     * 
     * @return True if the optimizer converged on a solution, and false
     *         otherwise (including if <code>iterateToConvergence</code> has
     *         never been invoked.
     */
    public boolean lastRunInvalidStep() {
        return termination == INVALID_STEP;
    }
}
