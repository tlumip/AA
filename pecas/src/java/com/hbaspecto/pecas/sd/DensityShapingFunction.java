package com.hbaspecto.pecas.sd;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.log4j.Logger;

import com.hbaspecto.pecas.FormatLogger;

import no.uib.cipr.matrix.DenseMatrix;
import no.uib.cipr.matrix.DenseVector;
import no.uib.cipr.matrix.Matrix;
import no.uib.cipr.matrix.Vector;

/**
 * This function models the way the utility of building space changes with the
 * floor area ratio (FAR). It is piecewise linear, and is defined by the
 * following parameters:
 * <ul>
 * <li><code>utilityPerUnitSpace</code>: The base utility per unit of space
 * area, before any adjustments; acts as the base <i>slope</i> of the DSF.
 * Usually this includes expected profits and construction costs.</li>
 * <li><code>utilityPerUnitLand</code>: The base utility per unit of land area
 * in the parcel, before any adjustments; acts as the base <i>y-intercept</i> of
 * the DSF, since the land area is the same regardless of the amount of space
 * built. Usually this includes fixed costs such as development fees and site
 * preparation costs.</li>
 * <li><code>intensityPoints</code>: An array containing the FAR values that
 * mark the ends of the linear subranges of the DSF. The first and last elements
 * are the minimum and maximum allowed FAR while the other points are the
 * boundaries between the different subranges. The boundary points must be
 * properly ordered - i.e. each boundary point must be no less than the previous
 * boundary point. The minimum and maximum, however, may be out of order with
 * respect to the boundary points, meaning that some of the ranges are
 * disallowed entirely. Results are undefined if the minimum intensity is
 * greater than the maximum intensity. The length of this array must be one
 * greater than the number of subranges.</li>
 * <li><code>perSpaceAdjustments</code>: An array containing the adjustments to
 * add to the utility per unit space (slope) of each subrange. These adjustments
 * are not cumulative. The length of this array must be equal to the number of
 * subranges.</li>
 * <li><code>perLandAdjustments</code>: An array containing the heights of the
 * jump discontinuities between the subranges. The first element is applied at
 * <code>intensityPoints[0]</code> (the minimum intensity), and gives the DSF a
 * value of
 * <code>utilityPerUnitSpace * intensityPoints[0] + utilityPerUnitLand + perLandAdjustments[0]</code>
 * at that point. The other elements are applied at the corresponding intensity
 * points, and cause a jump discontinuity of the given size; in particular, if
 * all adjustments except the first are zero, the DSF is continuous
 * everywhere.</li>
 * </ul>
 * The two remaining parameters are <code>dispersionParameter</code>, which is
 * the dispersion parameter used in the logit model that chooses the intensity,
 * and <code>landArea</code>, which is the absolute size of the current parcel.
 */
public class DensityShapingFunction {
    private static FormatLogger logger = new FormatLogger(
            Logger.getLogger(DensityShapingFunction.class));

    // If the per space utility is less than this value in absolute, it will be
    // considered equal to zero.
    // This strange value of epsilon balances between the various sensitive
    // methods, providing acceptable error in all of them.
    // (if epsilon is too high, the approximation of utility = 0 is too rough
    // and causes errors in some outputs;
    // if epsilon is too low, some methods suffer large roundoff error).
    private static final double epsilon = 8e-6;

    private static final int NUM_INT_PARAMS = 5;
    private static final int IND_US = 0;
    private static final int IND_UA = 1;
    private static final int IND_SMIN = 2;
    private static final int IND_SMAX = 3;
    private static final int IND_DISP = 4;

    private double dispersionParameter;
    private double landArea;
    private double utilityPerUnitLand;
    private double utilityPerUnitSpace;
    private double[] originalIntensityPoints;
    private double[] originalPerSpaceAdjustments;
    private double[] properIntensityPoints;
    private double[] properPerSpaceAdjustments;
    private double[] properPerLandAdjustments;

    private int numRanges;
    private IntensityRange[] ranges;

    public DensityShapingFunction(double dispersionParameter, double landArea,
            double utilityPerUnitSpace, double utilityPerUnitLand,
            double[] intensityPoints, double[] perSpaceAdjustments,
            double[] perLandAdjustments) {

        List<Double> intensityPointsList = toList(intensityPoints);
        List<Double> perSpaceAdjustmentsList = toList(perSpaceAdjustments);
        List<Double> perLandAdjustmentsList = toList(perLandAdjustments);

        checkRanges(intensityPointsList, perSpaceAdjustmentsList,
                perLandAdjustmentsList);

        this.dispersionParameter = dispersionParameter;
        this.landArea = landArea;
        this.utilityPerUnitSpace = utilityPerUnitSpace;
        this.utilityPerUnitLand = utilityPerUnitLand;
        this.originalIntensityPoints = copy(intensityPoints);
        this.originalPerSpaceAdjustments = copy(perSpaceAdjustments);
        // this.originalPerLandAdjustments = copy(perLandAdjustments); Unused
        this.properIntensityPoints = toArray(intensityPointsList);
        this.properPerSpaceAdjustments = toArray(perSpaceAdjustmentsList);
        this.properPerLandAdjustments = toArray(perLandAdjustmentsList);

        numRanges = properIntensityPoints.length - 1;
        ranges = new IntensityRange[numRanges];
        double currentLandUtility = utilityPerUnitLand;

        for (int i = 0; i < numRanges; i++) {
            double perSpace = utilityPerUnitSpace
                    + properPerSpaceAdjustments[i];

            currentLandUtility += properPerLandAdjustments[i];
            if (i > 0)
                currentLandUtility += (properPerSpaceAdjustments[i - 1]
                        - properPerSpaceAdjustments[i])
                        * properIntensityPoints[i];

            double minFAR = properIntensityPoints[i];
            double maxFAR = properIntensityPoints[i + 1];

            ranges[i] = new IntensityRange(perSpace, currentLandUtility, minFAR,
                    maxFAR);
        }
    }

    private double[] copy(double[] arr) {
        return Arrays.copyOf(arr, arr.length);
    }

    private List<Double> toList(double[] array) {
        ArrayList<Double> list = new ArrayList<>(array.length);
        for (double element : array)
            list.add(element);
        return list;
    }

    private static double[] toArray(List<Double> list) {
        double[] array = new double[list.size()];
        int i = 0;
        for (double element : list) {
            array[i] = element;
            i++;
        }
        return array;
    }

    // Checks the intensity range parameters for validity. Also modifies the
    // lists in place to eliminate degenerate ranges at either end -
    // this can easily occur in normal operation if zoning rules eliminate an
    // entire range.
    private void checkRanges(List<Double> intensityPoints,
            List<Double> perSpaceAdjustments, List<Double> perLandAdjustments) {
        checkCondition(intensityPoints.size() >= 2,
                "Need to have at least 2 allowed intensities");
        checkCondition(perSpaceAdjustments.size() == intensityPoints.size() - 1,
                "Intensity space adjustments need to be the same as the specified intensity ranges (i.e. needs to be "
                        + (intensityPoints.size() - 1) + ")");
        checkCondition(perLandAdjustments.size() == intensityPoints.size() - 1,
                "Per Land Adjustments need to be one for each specified intensity point below the maximum (i.e. needs to be "
                        + (intensityPoints.size() - 1) + ")");
        int numRanges = intensityPoints.size() - 1;
        checkCondition(intensityPoints.get(0) <= intensityPoints.get(numRanges),
                "Minimum intensity must not be greater than maximum intensity: ");
        // Check for bad range ordering in the middle - this indicates an input
        // error.
        for (int i = 1; i < numRanges - 1; i++)
            checkCondition(intensityPoints.get(i) <= intensityPoints.get(i + 1),
                    String.format(
                            "Intensity points %d and %d are out of order: values are %f and %f",
                            i, i + 1, intensityPoints.get(i),
                            intensityPoints.get(i + 1)));

        // Remove ranges outside the bounds.
        removeInvalidRanges(numRanges, intensityPoints, perSpaceAdjustments,
                perLandAdjustments);
    }

    private void checkCondition(boolean condition, String msg) {
        if (!condition) {
            logger.throwFatal(msg);
        }
    }

    // Once checkRanges has confirmed that there are no errors in the density
    // shaping function specification, this method strips out ranges that fall
    // outside the intensity bounds.
    private void removeInvalidRanges(int numRanges,
            List<Double> intensityPoints, List<Double> perSpaceAdjustments,
            List<Double> perLandAdjustments) {
        ValidRanges vr = new ValidRanges(intensityPoints);

        // Remove the constant "jumps" that are outside the lower bound,
        // coalescing them into the initial jump.
        double initPerLandAdj = perLandAdjustments.get(0);
        for (int i = 0; i < vr.lowestValidRange; i++) {
            initPerLandAdj += perLandAdjustments.get(1)
                    + (perSpaceAdjustments.get(i)
                            - perSpaceAdjustments.get(i + 1))
                            * intensityPoints.get(i + 1);
            perLandAdjustments.remove(1);
        }
        perLandAdjustments.set(0, initPerLandAdj);
        // Remove the constant "jumps" that are outside the upper bound.
        for (int i = perLandAdjustments.size() - 1; i >= vr.numValidRanges; i--)
            perLandAdjustments.remove(i);
        // Remove the slope adjustments for invalid ranges.
        for (int i = 0; i < vr.lowestValidRange; i++)
            perSpaceAdjustments.remove(0);
        for (int i = perSpaceAdjustments.size()
                - 1; i >= vr.numValidRanges; i--)
            perSpaceAdjustments.remove(i);
        // Remove invalid step points.
        for (int i = 0; i < vr.lowestValidRange; i++)
            intensityPoints.remove(1);
        for (int i = intensityPoints.size() - 2; i >= vr.numValidRanges; i--)
            intensityPoints.remove(i);
    }

    // Method that restores the actual relationships to the original parameters
    // when degenerate ranges are removed.
    private Vector transformDerivativesForValidRanges(Vector derivs) {
        ValidRanges vr = new ValidRanges(toList(originalIntensityPoints));
        // Push minimum range from lowestValidRange back to zero.
        Vector result = derivs;
        for (int currentMin = vr.lowestValidRange; currentMin > 0; currentMin--) {
            int currentNumRanges = vr.highestValidRange - currentMin + 1;
            int newNumRanges = currentNumRanges + 1;

            // Old positions of the parameter types.
            int firstStepPointPos = 0;
            int firstPerSpacePos = currentNumRanges + 1;
            int firstPerLandPos = 2 * currentNumRanges + 1;
            int dispersionPos = 3 * currentNumRanges + 1;

            // New positions of the parameter types.
            int newFirstStepPointPos = 0;
            int newFirstPerSpacePos = newNumRanges + 1;
            int newFirstPerLandPos = 2 * newNumRanges + 1;
            int newDispersionPos = 3 * newNumRanges + 1;

            Vector oldresult = result;
            result = new DenseVector(3 * newNumRanges + 2);

            // The step points.
            result.add(newFirstStepPointPos, oldresult.get(firstStepPointPos));
            for (int i = 1; i <= currentNumRanges; i++)
                result.add(newFirstStepPointPos + i + 1,
                        oldresult.get(firstStepPointPos + i));
            // The per-space adjustments.
            for (int i = 0; i < currentNumRanges; i++)
                result.add(newFirstPerSpacePos + i + 1,
                        oldresult.get(firstPerSpacePos + i));
            // The per-land adjustments.
            for (int i = 0; i < currentNumRanges; i++)
                result.add(newFirstPerLandPos + i + 1,
                        oldresult.get(firstPerLandPos + i));
            // The dispersion parameter.
            result.add(newDispersionPos, oldresult.get(dispersionPos));
            // The transformation dependencies of the new first per-land
            // adjustment.
            result.add(newFirstPerLandPos, oldresult.get(firstPerLandPos));
            result.add(newFirstPerSpacePos, originalIntensityPoints[currentMin]
                    * oldresult.get(firstPerLandPos));
            result.add(newFirstPerSpacePos + 1,
                    -originalIntensityPoints[currentMin]
                            * oldresult.get(firstPerLandPos));
            result.add(newFirstStepPointPos + 1,
                    (originalPerSpaceAdjustments[currentMin - 1]
                            - originalPerSpaceAdjustments[currentMin])
                            * oldresult.get(firstPerLandPos));
        }

        // Push maximum range from highestValidRange back up to numRanges - 1.
        for (int currentMax = vr.highestValidRange; currentMax < vr.numRanges
                - 1; currentMax++) {
            int currentNumRanges = currentMax + 1;
            int newNumRanges = currentNumRanges + 1;

            // Old positions of the parameter types.
            int firstStepPointPos = 0;
            int firstPerSpacePos = currentNumRanges + 1;
            int firstPerLandPos = 2 * currentNumRanges + 1;
            int dispersionPos = 3 * currentNumRanges + 1;

            // New positions of the parameter types.
            int newFirstStepPointPos = 0;
            int newFirstPerSpacePos = newNumRanges + 1;
            int newFirstPerLandPos = 2 * newNumRanges + 1;
            int newDispersionPos = 3 * newNumRanges + 1;

            Vector oldresult = result;
            result = new DenseVector(3 * newNumRanges + 2);

            // The step points.
            for (int i = 0; i < currentNumRanges; i++)
                result.add(newFirstStepPointPos + i,
                        oldresult.get(firstStepPointPos + i));
            result.add(newFirstStepPointPos + newNumRanges,
                    oldresult.get(firstStepPointPos + currentNumRanges));
            // The per-space adjustments.
            for (int i = 0; i < currentNumRanges; i++)
                result.add(newFirstPerSpacePos + i,
                        oldresult.get(firstPerSpacePos + i));
            // The per-land adjustments.
            for (int i = 0; i < currentNumRanges; i++)
                result.add(newFirstPerLandPos + i,
                        oldresult.get(firstPerLandPos + i));
            // The dispersion parameter.
            result.add(newDispersionPos, oldresult.get(dispersionPos));
        }

        return result;
    }
    
    public double getDispersionParameter() {
        return dispersionParameter;
    }
    
    public double getLandArea() {
        return landArea;
    }
    
    public double getUtilityPerUnitSpace() {
        return utilityPerUnitSpace;
    }
    
    public double getUtilityPerUnitLand() {
        return utilityPerUnitLand;
    }
    
    @Override
    public String toString() {
        return "DSF [dispersionParameter="
                + dispersionParameter + ", landArea=" + landArea
                + ", utilityPerUnitLand=" + utilityPerUnitLand
                + ", utilityPerUnitSpace=" + utilityPerUnitSpace
                + ", intensityPoints="
                + Arrays.toString(properIntensityPoints)
                + ", perSpaceAdjustments="
                + Arrays.toString(properPerSpaceAdjustments)
                + ", perLandAdjustments="
                + Arrays.toString(properPerLandAdjustments) + "]";
    }

    /**
     * Evaluates the density shaping function at the given FAR value, yielding
     * the utility of building space at that intensity. If the FAR falls exactly
     * on a boundary between two subranges, the value according to the subrange
     * on the left (i.e. the lower-intensity subrange) is returned. If the FAR
     * is outside the allowed intensity range, negative infinity is returned.
     */
    public double getUtilityAtFAR(double far) {
        // If we're outside the allowed range, this FAR can't be chosen.
        if (far < properIntensityPoints[0]
                || far > properIntensityPoints[numRanges])
            return Double.NEGATIVE_INFINITY;

        // Figure out which range we're in.
        int i = 0;
        while (far > properIntensityPoints[i + 1])
            i++;

        return ranges[i].perSpace * far + ranges[i].perLand;
    }

    /**
     * Samples a random intensity value from the allowed intensity range using a
     * continuous logit model, with the possible intensity values weighted
     * according to the density shaping function.
     */
    public double sampleIntensity() {
        return sampleIntensity(Math.random());
    }

    /**
     * Samples an intensity value from the allowed intensity range using a
     * continuous logit model, with the possible intensity values weighted
     * according to the density shaping function, using the specified random
     * number (between 0 and 1) as the quantile.
     */
    public double sampleIntensity(double randomNumber) {
        double[] Dplus = new double[properIntensityPoints.length]; // indefinite
        // integral just
        // below
        // boundary
        double[] Dminus = new double[properIntensityPoints.length]; // indefinite
        // integral just
        // above
        // boundary
        double[] D = new double[properIntensityPoints.length];
        double netUtility = utilityPerUnitLand;
        for (int point = 0; point < properIntensityPoints.length; point++) {
            double perSpace = utilityPerUnitSpace;
            {
                perSpace += properPerSpaceAdjustments[Math.max(0, point - 1)];
                if (perSpace == 0) {
                    // have to consider the possibility of costs cancelling out
                    // and being exactly zero
                    Dminus[point] = properIntensityPoints[point] * landArea
                            * Math.exp(dispersionParameter * netUtility);
                } else {
                    double lowPoint = 0;
                    if (point != 0)
                        lowPoint = properIntensityPoints[point - 1];
                    netUtility += (properIntensityPoints[point] - lowPoint)
                            * (utilityPerUnitSpace
                                    + properPerSpaceAdjustments[Math.max(0,
                                            point - 1)]);
                    Dminus[point] = landArea
                            * Math.exp(dispersionParameter * (netUtility))
                            / dispersionParameter / perSpace;
                }
                if (point == 0) {
                    D[point] = 0;
                } else {
                    // definite integral for full region up to
                    // intensityPoints[point]
                    D[point] = Dminus[point] - Dplus[point - 1] + D[point - 1];
                }
            }
            if (point < properPerSpaceAdjustments.length) {
                // no Dplus at the top boundary.
                perSpace = utilityPerUnitSpace
                        + properPerSpaceAdjustments[point];

                netUtility += properPerLandAdjustments[point];
                if (perSpace == 0) {
                    Dplus[point] = properIntensityPoints[point] * landArea
                            * Math.exp(dispersionParameter * netUtility);
                } else {
                    Dplus[point] = landArea
                            * Math.exp(dispersionParameter * (netUtility))
                            / dispersionParameter / perSpace;
                }
            }
        }
        // now work down through boundary points
        double quantity = 0;
        for (int highPoint = properIntensityPoints.length
                - 1; highPoint > 0; highPoint--) {
            if (highPoint < properIntensityPoints.length - 1) {
                // subtract this out in general, but not in the first loop
                // because we never added it in for Dplus at the top boundary
                netUtility -= properPerLandAdjustments[highPoint];
            }
            if (D[highPoint - 1] < randomNumber
                    * D[properIntensityPoints.length - 1]) {
                // it's in this range
                // find the slope of our cost curve
                double perSpace = utilityPerUnitSpace
                        + properPerSpaceAdjustments[highPoint - 1];
                // back out the intercept of our cost curve
                double perLand = netUtility
                        - properIntensityPoints[highPoint] * perSpace;

                if (perSpace == 0) {
                    double samplePoint = randomNumber
                            * D[properIntensityPoints.length - 1]
                            - D[highPoint - 1];
                    double intensity = samplePoint
                            / (D[highPoint] - D[highPoint - 1])
                            * (properIntensityPoints[highPoint]
                                    - properIntensityPoints[highPoint - 1])
                            + properIntensityPoints[highPoint - 1];
                    quantity = intensity * landArea;
                } else {
                    double samplePoint = randomNumber
                            * D[properIntensityPoints.length - 1]
                            - D[highPoint - 1] + Dplus[highPoint - 1];
                    double numerator = Math.log(dispersionParameter * perSpace
                            * samplePoint / landArea);
                    quantity = (numerator / dispersionParameter - perLand)
                            * landArea / perSpace;
                }
                break;
            }
            // back down to next interval
            double lowPoint = 0;
            if (highPoint > 0)
                lowPoint = properIntensityPoints[highPoint - 1];
            netUtility -= (properIntensityPoints[highPoint] - lowPoint)
                    * (utilityPerUnitSpace + properPerSpaceAdjustments[Math
                            .max(0, highPoint - 1)]);
        }
        if (Double.isInfinite(quantity) || Double.isNaN(quantity)) {
            // if (logger.isDebugEnabled())
            logger.warn("truncating sampled intensity at maximum");
            return properIntensityPoints[properIntensityPoints.length - 1];
        }
        if (quantity > properIntensityPoints[properIntensityPoints.length - 1]
                * landArea) {
            // if (logger.isDebugEnabled())
            logger.warn("truncating sampled intensity at maximum");
            return properIntensityPoints[properIntensityPoints.length - 1];
        }
        if (quantity < properIntensityPoints[0] * landArea) {
            // if (logger.isDebugEnabled())
            logger.warn("truncating sampled intensity at minimum");
            return properIntensityPoints[0];
        }
        return quantity / landArea;
    }

    /**
     * Returns the expected maximum utility (composite utility) per unit land
     * area over the allowed intensity range, using a continuous logit model
     * that weights the options according to the density shaping function.
     */
    public double getCompositeUtility() {
        double totalIntegral = 0;

        for (int i = 0; i < numRanges; i++) {
            totalIntegral += ranges[i].integrate();
        }

        return 1 / dispersionParameter * Math.log(totalIntegral);
    }

    /**
     * Returns the partial derivatives of the composite utility (i.e. the
     * derivatives of {@code getCompositeUtility()}) with respect to each
     * parameter. The derivatives are returned in this order: the boundary
     * points, the per-space adjustments, the per-land adjustments, and finally
     * the dispersion parameter.
     */
    public Vector getUtilityDerivativesWRTParameters() {
        int numCoeffs = 3 * numRanges + 2;
        double totalIntegral = 0;
        Matrix[] dIda = new Matrix[numRanges];

        for (int i = 0; i < numRanges; i++) {
            totalIntegral += ranges[i].integrate();
            dIda[i] = ranges[i].getUtilityDerivatives();
        }

        Matrix[] dadt = getIntegralParameterDerivativesWRTParameters();
        Matrix dIdt = multiplyAndAggregate(numRanges, NUM_INT_PARAMS, numCoeffs,
                dIda, dadt);
        Matrix dUdI = getUtilityDerivativesWRTUtilityIntegrals(totalIntegral);

        Matrix result = new DenseMatrix(1, numCoeffs);
        result = dUdI.mult(dIdt, result);

        // Convert into a vector.
        Vector derivs = new DenseVector(numCoeffs);
        for (int i = 0; i < numCoeffs; i++)
            derivs.set(i, result.get(0, i));

        // Add direct dependency of the composite utility on the dispersion
        // parameter.
        double dUdlambda = -1 / square(dispersionParameter)
                * Math.log(totalIntegral);
        derivs.add(numCoeffs - 1, dUdlambda);

        return transformDerivativesForValidRanges(derivs);
    }

    // Returns for each intensity range a matrix of derivatives of that range's
    // parameters with respect to the overall density shaping parameters. Each
    // matrix has 5 rows and (numRanges * 3 + 2) columns, because each range has
    // 5 parameters (including the dispersion parameter) and the overall DSF has
    // (numRanges * 3 + 2) parameters (numRanges + 1 step points, numRanges
    // per-space adjustments, numRanges per-land adjustments, and the dispersion
    // parameter).
    private Matrix[] getIntegralParameterDerivativesWRTParameters() {
        // Positions of the different parameter types.
        int firstStepPointPos = 0;
        int firstPerSpacePos = numRanges + 1;
        int firstPerLandPos = 2 * numRanges + 1;
        int dispersionPos = 3 * numRanges + 1;

        Matrix[] result = new Matrix[numRanges];
        for (int i = 0; i < numRanges; i++) {
            result[i] = new DenseMatrix(NUM_INT_PARAMS, 3 * numRanges + 2);
            // Utility per space depends on corresponding per-space adjustment.
            result[i].add(IND_US, firstPerSpacePos + i, 1);
            // Minimum intensity depends on corresponding step point.
            result[i].add(IND_SMIN, firstStepPointPos + i, 1);
            // Maximum intensity depends on next step point.
            result[i].add(IND_SMAX, firstStepPointPos + i + 1, 1);
            // Dispersion parameter depends only on dispersion parameter.
            result[i].add(IND_DISP, dispersionPos, 1);
            // Utility per land depends on per-land adjustments...
            for (int j = 0; j <= i; j++)
                result[i].add(IND_UA, firstPerLandPos + j, 1);
            // ... and on all the step points up to the current one...
            for (int j = 0; j < i; j++)
                result[i].add(IND_UA, firstStepPointPos + j + 1,
                        properPerSpaceAdjustments[j]
                                - properPerSpaceAdjustments[j + 1]);
            // ... and on all the per-space adjustments up to the next one.
            for (int j = 0; j < i; j++)
                result[i].add(IND_UA, firstPerSpacePos + j,
                        properIntensityPoints[j + 1]);
            for (int j = 0; j < i; j++)
                result[i].add(IND_UA, firstPerSpacePos + j + 1,
                        -properIntensityPoints[j + 1]);
        }

        return result;
    }

    private Matrix getUtilityDerivativesWRTUtilityIntegrals(
            double integralsum) {
        Matrix result = new DenseMatrix(1, numRanges);
        for (int i = 0; i < numRanges; i++)
            result.set(0, i, 1 / (dispersionParameter * integralsum));

        return result;
    }

    /**
     * Returns the derivative of the composite utility with respect to the
     * {@code utilityPerUnitSpace} parameter
     */
    public double getUtilityDerivativeWRTUtilityPerUnitSpace() {
        double totalIntegral = 0;
        double totalDerivative = 0;

        for (int i = 0; i < numRanges; i++) {
            totalIntegral += ranges[i].integrate();
            totalDerivative += ranges[i].getUtilityDerivativeWRTPerSpace();
        }

        return totalDerivative / (dispersionParameter * totalIntegral);
    }

    /**
     * Returns the expected development intensity over the allowed intensity
     * range, i.e. the expected value of {@code sampleIntensityWithinRanges()}.
     */
    public double getExpectedFAR() {
        double totalEV = 0;
        double totalIntegral = 0;

        for (int i = 0; i < numRanges; i++) {
            totalEV += ranges[i].getRawEV();
            // These integrals are needed to normalize by the total area.
            totalIntegral += ranges[i].integrate();
        }

        return totalEV / totalIntegral;
    }

    /**
     * Returns the partial derivatives of the expected intensity (i.e. the
     * derivatives of {@code getExpectedFAR}) with respect to each parameter.
     */
    public Vector getExpectedFARDerivativesWRTParameters() {
        int numCoeffs = 3 * numRanges + 2;
        double totalIntegral = 0;
        Matrix[] dIda = new Matrix[numRanges];
        Matrix[] dIeda = new Matrix[numRanges];

        // Find the modified parameters for each range.
        for (int i = 0; i < numRanges; i++) {
            totalIntegral += ranges[i].integrate();
            dIda[i] = ranges[i].getUtilityDerivatives();
        }

        // Component of partial derivatives through dependency on utility
        // integrals.
        Matrix[] dadt = getIntegralParameterDerivativesWRTParameters();
        Matrix dIdt = multiplyAndAggregate(numRanges, NUM_INT_PARAMS, numCoeffs,
                dIda, dadt);
        Matrix dIedI = getExpectedFARSumDerivativesWRTUtilityIntegrals(
                totalIntegral);
        Matrix dIedt = new DenseMatrix(numRanges, numCoeffs);
        dIedt = dIedI.mult(dIdt, dIedt);

        // Component of partial derivatives through direct dependency of
        // expected value on parameters.
        for (int i = 0; i < numRanges; i++) {
            dIeda[i] = ranges[i].getEVDerivatives(totalIntegral);
        }
        dIedt.add(multiplyAndAggregate(numRanges, NUM_INT_PARAMS, numCoeffs,
                dIeda, dadt));

        // Multiply by the dependency of expected value on the expected value
        // for each range.
        Matrix dEdIe = getExpectedFARDerivativesWRTExpectedFARSums();
        Matrix result = new DenseMatrix(1, numCoeffs);
        result = dEdIe.mult(dIedt, result);

        // Convert into a vector.
        Vector vector = new DenseVector(numCoeffs);
        for (int i = 0; i < numCoeffs; i++)
            vector.set(i, result.get(0, i));

        return transformDerivativesForValidRanges(vector);
    }

    private Matrix getExpectedFARSumDerivativesWRTUtilityIntegrals(
            double integralsum) {
        double[][] result = new double[numRanges][numRanges];
        for (int i = 0; i < numRanges; i++) {
            double derivative = ranges[i].getEVDerivativeWRTUtilityIntegrals(integralsum);
            for (int j = 0; j < numRanges; j++)
                // Derivative is the same with respect to all utility integrals.
                result[i][j] = derivative;
        }

        return new DenseMatrix(result);
    }

    // Returns a 1 by numRanges matrix.
    private Matrix getExpectedFARDerivativesWRTExpectedFARSums() {
        // The total expected FAR is simply the sum of the FAR sum in each
        // range, so these are all 1.
        double[] result = new double[numRanges];
        for (int i = 0; i < numRanges; i++)
            result[i] = 1;
        Matrix m = new DenseMatrix(1, numRanges);
        return new DenseMatrix(new DenseVector(result)).transpose(m);
    }

    public double getExpectedFARDerivativeWRTUtilityPerUnitSpace() {
        double totalIntegral = 0;
        double totalDerivative = 0;
        
        double totalEVDerivative = 0;

        for (int i = 0; i < numRanges; i++) {
            totalIntegral += ranges[i].integrate();
            totalDerivative += ranges[i].getUtilityDerivativeWRTPerSpace();
        }

        for (int i = 0; i < numRanges; i++) {
            // Indirect derivative
            totalEVDerivative += ranges[i].getEVDerivativeWRTUtilityIntegrals(totalIntegral)
                    * totalDerivative;
            // Direct derivative
            totalEVDerivative += ranges[i]
                    .getEVDerivativeWRTPerSpace(totalIntegral);
        }

        return totalEVDerivative;
    }

    private double square(double arg) {
        return arg * arg;
    }

    private double cube(double arg) {
        return arg * arg * arg;
    }

    // Calculates exp(a) - exp(b) with minimal loss of precision
    private double diffexp(double a, double b) {
        if (Math.abs(a) < 1 && Math.abs(b) < 1) {
            return Math.expm1(a) - Math.expm1(b);
        } else {
            return Math.exp(a) - Math.exp(b);
        }
    }

    // Takes an array of row vectors and an array of matrices (which must be the
    // same length k), multiplies
    // each vector by the corresponding matrix, and aggregates the resulting row
    // vectors into a new matrix.
    // The vectors must be 1 by m, the matrices must be m by n (where n is any
    // integer).
    // Returns a k by n matrix.
    private Matrix multiplyAndAggregate(int k, int m, int n, Matrix[] vectors,
            Matrix[] matrices) {
        Matrix[] products = new Matrix[k];
        for (int i = 0; i < k; i++) {
            products[i] = new DenseMatrix(1, n);
            products[i] = vectors[i].mult(matrices[i], products[i]);
        }

        // Copy the matrix entries into the new matrix.
        Matrix result = new DenseMatrix(k, n);
        for (int i = 0; i < k; i++)
            for (int j = 0; j < n; j++) {
                result.set(i, j, products[i].get(0, j));
            }

        return result;
    }

    // One linear intensity range in the density shaping function. Each range is
    // defined by a minimum and maximum intensity, as well as a utility per land
    // (intercept) and utility per unit space (slope). The dispersion parameter
    // is considered the fifth parameter for an intensity range (even though it
    // is the same for all ranges) because derivatives have to be calculated
    // with respect to it in the same way as the true range parameters.
    private class IntensityRange {
        private double perSpace;
        private double perLand;
        private double minIntensity;
        private double maxIntensity;
        
        private double integral;

        private IntensityRange(double perSpace, double perLand,
                double minIntensity, double maxIntensity) {
            this.perSpace = perSpace;
            this.perLand = perLand;
            this.minIntensity = minIntensity;
            this.maxIntensity = maxIntensity;
            
            // Cache this, it gets used a lot!
            integral = calculateIntegral();
        }

        private double calculateIntegral() {
            double atQmax;
            double atQmin;
            double idp = dispersionParameter;
            if (Math.abs(perSpace) > epsilon) {
                atQmax = Math.exp(idp * (perSpace * maxIntensity));
                atQmax = atQmax / idp / perSpace;
                atQmin = Math.exp(idp * (perSpace * minIntensity));
                atQmin = atQmin / idp / perSpace;
            } else {
                atQmax = maxIntensity;
                atQmin = minIntensity;
            }
            double result = atQmax - atQmin;
            result = result * Math.exp(idp * perLand);
            return result;
        }
        
        // Returns the total utility weight over the intensity range
        private double integrate() {
            return integral;
        }

        // Returns the derivative of the composite utility with respect to the
        // five intensity range parameters including the dispersion parameter.
        private Matrix getUtilityDerivatives() {
            double[] row = new double[NUM_INT_PARAMS];
            row[IND_US] = getUtilityDerivativeWRTPerSpace();
            row[IND_UA] = getUtilityDerivativeWRTPerLand();
            row[IND_SMIN] = getUtilityDerivativeWRTMinIntensity();
            row[IND_SMAX] = getUtilityDerivativeWRTMaxIntensity();
            row[IND_DISP] = getUtilityDerivativeWRTDispersion();

            Matrix m = new DenseMatrix(1, 5);
            return new DenseMatrix(new DenseVector(row)).transpose(m);
        }

        private double getUtilityDerivativeWRTPerSpace() {
            if (Math.abs(perSpace) <= epsilon) {
                double landweight = Math.exp(dispersionParameter * perLand);
                return dispersionParameter * landweight
                        * (square(maxIntensity) - square(minIntensity)) / 2;
            } else {
                double landweight = Math.exp(dispersionParameter * perLand);
                double maxutil = dispersionParameter * maxIntensity * perSpace;
                double minutil = dispersionParameter * minIntensity * perSpace;
                double maxweight = Math.exp(maxutil);
                double minweight = Math.exp(minutil);
                double weightdiff = diffexp(minutil, maxutil);
                double utildiff = maxutil * maxweight - minutil * minweight;
                return landweight / (dispersionParameter * square(perSpace))
                        * (weightdiff + utildiff);
            }
        }

        private double getUtilityDerivativeWRTPerLand() {
            if (Math.abs(perSpace) <= epsilon) {
                double landweight = Math.exp(dispersionParameter * perLand);
                return dispersionParameter * landweight
                        * (maxIntensity - minIntensity);
            } else {
                double landweight = Math.exp(dispersionParameter * perLand);
                double maxweight = Math
                        .exp(dispersionParameter * maxIntensity * perSpace);
                double minweight = Math
                        .exp(dispersionParameter * minIntensity * perSpace);
                return landweight / perSpace * (maxweight - minweight);
            }
        }

        private double getUtilityDerivativeWRTMinIntensity() {
            double landweight = Math.exp(dispersionParameter * perLand);
            double minweight = Math
                    .exp(dispersionParameter * perSpace * minIntensity);
            return -landweight * minweight;
        }

        private double getUtilityDerivativeWRTMaxIntensity() {
            double landweight = Math.exp(dispersionParameter * perLand);
            double maxweight = Math
                    .exp(dispersionParameter * perSpace * maxIntensity);
            return landweight * maxweight;
        }

        private double getUtilityDerivativeWRTDispersion() {
            if (Math.abs(perSpace) <= epsilon) {
                double landweight = Math.exp(dispersionParameter * perLand);
                return perLand * landweight * (maxIntensity - minIntensity);
            } else {
                double landutil = dispersionParameter * perLand;
                double maxutil = dispersionParameter * maxIntensity * perSpace;
                double minutil = dispersionParameter * minIntensity * perSpace;
                double landweight = Math.exp(landutil);
                double maxweight = Math.exp(maxutil);
                double minweight = Math.exp(minutil);
                return landweight / (square(dispersionParameter) * perSpace)
                        * ((maxutil + landutil - 1) * maxweight
                                - (minutil + landutil - 1) * minweight);
            }
        }

        // Returns the expected FAR in one intensity range, not yet normalized
        // by the total area under the allowed range.
        private double getRawEV() {
            if (Math.abs(perSpace) <= epsilon) {
                double landweight = Math.exp(dispersionParameter * perLand);
                return landweight / 2
                        * (square(maxIntensity) - square(minIntensity));
            } else {
                double maxutil = dispersionParameter * maxIntensity * perSpace;
                double minutil = dispersionParameter * minIntensity * perSpace;
                double landweight = Math.exp(dispersionParameter * perLand);
                double maxweight = Math.exp(maxutil);
                double minweight = Math.exp(minutil);
                double denominator = square(dispersionParameter)
                        * square(perSpace);
                double weightdiff = diffexp(minutil, maxutil);
                double utildiff = maxutil * maxweight - minutil * minweight;

                return landweight / denominator * (weightdiff + utildiff);
            }
        }

        private double getEVDerivativeWRTUtilityIntegrals(double integralsum) {
            double derivative;
            double landweight = Math
                    .exp(dispersionParameter * perLand);
            if (Math.abs(perSpace) <= epsilon) {
                double intensityterm = square(maxIntensity)
                        - square(minIntensity);
                derivative = -landweight / (2 * square(integralsum))
                        * intensityterm;
            } else {
                double maxutil = dispersionParameter * maxIntensity
                        * perSpace;
                double minutil = dispersionParameter * minIntensity
                        * perSpace;
                double maxweight = Math.exp(maxutil);
                double minweight = Math.exp(minutil);

                double denominator = square(dispersionParameter)
                        * square(perSpace) * square(integralsum);
                double weightdiff = diffexp(minutil, maxutil);
                double utildiff = maxutil * maxweight - minutil * minweight;
                derivative = -landweight / denominator
                        * (weightdiff + utildiff);
            }
            return derivative;
        }

        // Returns the derivatives of the expected FAR with respect to the five
        // intensity range parameters including the dispersion parameter.
        private Matrix getEVDerivatives(double integralsum) {
            double[] row = new double[NUM_INT_PARAMS];
            row[IND_US] = getEVDerivativeWRTPerSpace(integralsum);
            row[IND_UA] = getEVDerivativeWRTPerLand(integralsum);
            row[IND_SMIN] = getEVDerivativeWRTMinIntensity(integralsum);
            row[IND_SMAX] = getEVDerivativeWRTMaxIntensity(integralsum);
            row[IND_DISP] = getEVDerivativeWRTDispersion(integralsum);

            Matrix m = new DenseMatrix(1, 5);
            return new DenseMatrix(new DenseVector(row)).transpose(m);
        }

        private double getEVDerivativeWRTPerSpace(double integralsum) {
            if (Math.abs(perSpace) <= epsilon) {
                double landweight = Math.exp(dispersionParameter * perLand);
                return dispersionParameter * landweight / (3 * integralsum)
                        * (cube(maxIntensity) - cube(minIntensity));
            } else {
                double landweight = Math.exp(dispersionParameter * perLand);
                double denominator = square(dispersionParameter)
                        * cube(perSpace) * integralsum;
                double maxutil = dispersionParameter * maxIntensity * perSpace;
                double minutil = dispersionParameter * minIntensity * perSpace;
                double maxweight = Math.exp(maxutil);
                double minweight = Math.exp(minutil);

                double squarediff = square(maxutil) * maxweight
                        - square(minutil) * minweight;
                double utildiff = 2 * minutil * minweight
                        - 2 * maxutil * maxweight;
                double weightdiff = 2 * diffexp(maxutil, minutil);

                return landweight / denominator
                        * (squarediff + utildiff + weightdiff);
            }
        }

        private double getEVDerivativeWRTPerLand(double integralsum) {
            if (Math.abs(perSpace) <= epsilon) {
                double landweight = Math.exp(dispersionParameter * perLand);
                return dispersionParameter * landweight / (2 * integralsum)
                        * (square(maxIntensity) - square(minIntensity));
            } else {
                double landweight = Math.exp(dispersionParameter * perLand);
                double denominator = dispersionParameter * square(perSpace)
                        * integralsum;
                double maxutil = dispersionParameter * maxIntensity * perSpace;
                double minutil = dispersionParameter * minIntensity * perSpace;
                double maxweight = Math.exp(maxutil);
                double minweight = Math.exp(minutil);

                double utildiff = maxutil * maxweight - minutil * minweight;
                double weightdiff = diffexp(minutil, maxutil);

                return landweight / denominator * (utildiff + weightdiff);
            }
        }

        private double getEVDerivativeWRTMinIntensity(double integralsum) {
            double landweight = Math.exp(dispersionParameter * perLand);
            double minweight = Math
                    .exp(dispersionParameter * perSpace * minIntensity);
            return -landweight / integralsum * minIntensity * minweight;
        }

        // integralsum is the total weight from the composite utility
        // calculation.
        private double getEVDerivativeWRTMaxIntensity(double integralsum) {
            double landweight = Math.exp(dispersionParameter * perLand);
            double maxweight = Math
                    .exp(dispersionParameter * maxIntensity * perSpace);
            return landweight / integralsum * maxIntensity * maxweight;
        }

        private double getEVDerivativeWRTDispersion(double integralsum) {
            if (Math.abs(perSpace) <= epsilon) {
                double landweight = Math.exp(dispersionParameter * perLand);
                return perLand * landweight / (2 * integralsum)
                        * (square(maxIntensity) - square(minIntensity));
            } else {
                double landutil = dispersionParameter * perLand;
                double denominator = square(perSpace)
                        * cube(dispersionParameter) * integralsum;
                double maxutil = dispersionParameter * maxIntensity * perSpace;
                double minutil = dispersionParameter * minIntensity * perSpace;
                double landweight = Math.exp(landutil);
                double maxweight = Math.exp(maxutil);
                double minweight = Math.exp(minutil);

                double produtildiff = landutil * maxutil * maxweight
                        - landutil * minutil * minweight;
                double squarediff = square(maxutil) * maxweight
                        - square(minutil) * minweight;
                double landutildiff = landutil * diffexp(minutil, maxutil);
                double spaceutildiff = 2 * minutil * minweight
                        - 2 * maxutil * maxweight;
                double weightdiff = 2 * diffexp(maxutil, minutil);

                return landweight / denominator * (produtildiff + squarediff
                        + landutildiff + spaceutildiff + weightdiff);
            }
        }
    }

    // Class for temporary objects used when validating the intensity ranges.
    private static class ValidRanges {
        int numRanges;
        int lowestValidRange;
        int highestValidRange;
        int numValidRanges;

        private ValidRanges(List<Double> intensityPoints) {
            numRanges = intensityPoints.size() - 1;
            int i = 1;
            while (intensityPoints.get(0) > intensityPoints.get(i))
                i++;
            lowestValidRange = i - 1;

            i = numRanges - 1;
            while (intensityPoints.get(numRanges) < intensityPoints.get(i))
                i--;
            highestValidRange = i;
            numValidRanges = highestValidRange - lowestValidRange + 1;
        }
    }
}
