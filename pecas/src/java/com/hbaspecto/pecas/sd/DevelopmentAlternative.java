package com.hbaspecto.pecas.sd;

import java.util.ArrayList;
import java.util.List;

import org.apache.log4j.Logger;

import com.hbaspecto.discreteChoiceModelling.ParameterSearchAlternative;
import com.hbaspecto.pecas.FormatLogger;
import com.hbaspecto.pecas.land.LandInventory;
import com.hbaspecto.pecas.land.LandInventory.NotSplittableException;
import com.hbaspecto.pecas.land.ParcelInterface;

import no.uib.cipr.matrix.DenseMatrix;
import no.uib.cipr.matrix.DenseVector;
import no.uib.cipr.matrix.Matrix;
import no.uib.cipr.matrix.Vector;

/**
 * Abstract base class for types of development activity in the SD model.
 * 
 * @author John Abraham, Graham Hill
 */
public abstract class DevelopmentAlternative implements
        ParameterSearchAlternative {
    
    static Logger logger = Logger.getLogger(DevelopmentAlternative.class);
    private static FormatLogger loggerf = new FormatLogger(logger);
    
    @Deprecated
    private static final double epsilon = 8e-6;
    @Deprecated
    private static final int NUM_INT_PARAMS = 5;
    @Deprecated
    private static final int IND_US = 0;
    @Deprecated
    private static final int IND_UA = 1;
    @Deprecated
    private static final int IND_SMIN = 2;
    @Deprecated
    private static final int IND_SMAX = 3;
    @Deprecated
    private static final int IND_DISP = 4;

    @Deprecated
    private static double square(double arg) {
        return arg * arg;
    }

    @Deprecated
    private static double cube(double arg) {
        return arg * arg * arg;
    }
    
    @Deprecated
    private static double diffexp(double a, double b) {
        if (Math.abs(a) < 1 && Math.abs(b) < 1) {
            return Math.expm1(a) - Math.expm1(b);
        } else {
            return Math.exp(a) - Math.exp(b);
        }
    }

    @Deprecated
    private static Matrix multiplyAndAggregate(int k, int m, int n,
            Matrix[] vectors, Matrix[] matrices) {
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

    @Deprecated
    private static List<Double> toList(double[] array) {
        ArrayList<Double> list = new ArrayList<>(array.length);
        for (double element : array)
            list.add(element);
        return list;
    }

    @Deprecated
    private static double[] toArray(List<Double> list) {
        double[] array = new double[list.size()];
        int i = 0;
        for (double element : list) {
            array[i] = element;
            i++;
        }
        return array;
    }

    @Deprecated
    private static void checkRanges(List<Double> intensityPoints,
            List<Double> perSpaceAdjustments, List<Double> perLandAdjustments) {
        checkCondition(intensityPoints.size() >= 2,
                "Need to have at least 2 allowed intensities");
        checkCondition(
                perSpaceAdjustments.size() == intensityPoints.size() - 1,
                "Intensity space adjustments need to be the same as the specified intensity ranges (i.e. needs to be "
                        + (intensityPoints.size() - 1) + ")");
        checkCondition(
                perLandAdjustments.size() == intensityPoints.size() - 1,
                "Per Land Adjustments need to be one for each specified intensity point below the maximum (i.e. needs to be "
                        + (intensityPoints.size() - 1) + ")");
        int numRanges = intensityPoints.size() - 1;
        checkCondition(
                intensityPoints.get(0) <= intensityPoints.get(numRanges),
                "Minimum intensity must not be greater than maximum intensity: ");
        // Check for bad range ordering in the middle - this indicates an input
        // error.
        for (int i = 1; i < numRanges - 1; i++)
            checkCondition(
                    intensityPoints.get(i) <= intensityPoints.get(i + 1),
                    String.format(
                            "Intensity points %d and %d are out of order: values are %f and %f",
                            i, i + 1, intensityPoints.get(i),
                            intensityPoints.get(i + 1)));

        // Remove ranges outside the bounds.
        removeInvalidRanges(numRanges, intensityPoints, perSpaceAdjustments,
                perLandAdjustments);
    }

    @Deprecated
    private static void checkCondition(boolean condition, String msg) {
        if (!condition) {
            logger.fatal(msg);
            throw new RuntimeException(msg);
        }
    }

    @Deprecated
    private static void removeInvalidRanges(int numRanges,
            List<Double> intensityPoints, List<Double> perSpaceAdjustments,
            List<Double> perLandAdjustments) {
        ValidRanges vr = new ValidRanges(intensityPoints);

        // Remove the constant "jumps" that are outside the lower bound,
        // coalescing them into the initial jump.
        double initPerLandAdj = perLandAdjustments.get(0);
        for (int i = 0; i < vr.lowestValidRange; i++) {
            initPerLandAdj += perLandAdjustments.get(1)
                    + (perSpaceAdjustments.get(i) - perSpaceAdjustments
                            .get(i + 1)) * intensityPoints.get(i + 1);
            perLandAdjustments.remove(1);
        }
        perLandAdjustments.set(0, initPerLandAdj);
        // Remove the constant "jumps" that are outside the upper bound.
        for (int i = perLandAdjustments.size() - 1; i >= vr.numValidRanges; i--)
            perLandAdjustments.remove(i);
        // Remove the slope adjustments for invalid ranges.
        for (int i = 0; i < vr.lowestValidRange; i++)
            perSpaceAdjustments.remove(0);
        for (int i = perSpaceAdjustments.size() - 1; i >= vr.numValidRanges; i--)
            perSpaceAdjustments.remove(i);
        // Remove invalid step points.
        for (int i = 0; i < vr.lowestValidRange; i++)
            intensityPoints.remove(1);
        for (int i = intensityPoints.size() - 2; i >= vr.numValidRanges; i--)
            intensityPoints.remove(i);
    }

    @Deprecated
    private static Vector transformDerivativesForValidRanges(Vector derivs,
            double[] perSpaceAdjustments, double[] perLandAdjustments,
            double[] stepPoints, double dispersion) {
        ValidRanges vr = new ValidRanges(toList(stepPoints));
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
            result.add(newFirstPerSpacePos,
                    stepPoints[currentMin] * oldresult.get(firstPerLandPos));
            result.add(newFirstPerSpacePos + 1, -stepPoints[currentMin]
                    * oldresult.get(firstPerLandPos));
            result.add(
                    newFirstStepPointPos + 1,
                    (perSpaceAdjustments[currentMin - 1] - perSpaceAdjustments[currentMin])
                            * oldresult.get(firstPerLandPos));
        }

        // Push maximum range from highestValidRange back up to numRanges - 1.
        for (int currentMax = vr.highestValidRange; currentMax < vr.numRanges - 1; currentMax++) {
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

    @Deprecated
    protected static double getUtilityAtFAR(double far,
            double dispersionParameter, double landArea,
            double utilityPerUnitSpace, double utilityPerUnitLand,
            double[] intensityPoints, double[] perSpaceAdjustments,
            double[] perLandAdjustments) {
        int numRanges = intensityPoints.length - 1;
        // If we're outside the allowed range, this FAR can't be chosen.
        if(far < intensityPoints[0] || far > intensityPoints[numRanges])
            return Double.NEGATIVE_INFINITY;
        // Figure out which range we're in.
        int range = 0;
        while (far > intensityPoints[range + 1])
            range++;
        double slope = utilityPerUnitSpace + perSpaceAdjustments[range];
        double intercept = utilityPerUnitLand;
        for (int i = 0; i <= range; i++) {
            intercept += perLandAdjustments[i];
            if (i > 0)
                intercept += (perSpaceAdjustments[i - 1] - perSpaceAdjustments[i])
                        * intensityPoints[i];
        }
        return slope * far + intercept;
    }

    @Deprecated
    protected static double integrateOverIntensityRange(double perSpace,
            double perLand, double landSize, double minIntensity,
            double maxIntensity, double dispersion) {
        double atQmax = maxIntensity;
        double atQmin = minIntensity;
        double idp = dispersion;
        if (Math.abs(perSpace) > epsilon) {
            atQmax = Math.exp(idp * (perSpace * maxIntensity));
            atQmax = atQmax / idp / perSpace;
            atQmin = Math.exp(idp * (perSpace * minIntensity));
            atQmin = atQmin / idp / perSpace;
        } // else {
          // simplifies to maxIntensity and minIntensity
        double result = atQmax - atQmin;
        result = result * Math.exp(idp * perLand);
        return result;
    }

    @Deprecated
    protected static double sampleIntensityWithinRanges(
            double dispersionParameter, double landArea,
            double utilityPerUnitSpace, double utilityPerUnitLand,
            double[] intensityPoints, double[] perSpaceAdjustments,
            double[] perLandAdjustments) {
        return sampleIntensityWithinRanges(dispersionParameter, landArea,
                utilityPerUnitSpace, utilityPerUnitLand, intensityPoints,
                perSpaceAdjustments, perLandAdjustments, Math.random());
    }

    @Deprecated
    protected static double sampleIntensityWithinRanges(
            double dispersionParameter, double landArea,
            double utilityPerUnitSpace, double utilityPerUnitLand,
            double[] intensityPoints, double[] perSpaceAdjustments,
            double[] perLandAdjustments, double randomNumber) {

        List<Double> intensityPointsList = toList(intensityPoints);
        List<Double> perSpaceAdjustmentsList = toList(perSpaceAdjustments);
        List<Double> perLandAdjustmentsList = toList(perLandAdjustments);

        checkRanges(intensityPointsList, perSpaceAdjustmentsList,
                perLandAdjustmentsList);

        return sampleIntensityProperRanges(dispersionParameter, landArea,
                utilityPerUnitSpace, utilityPerUnitLand,
                toArray(intensityPointsList), toArray(perSpaceAdjustmentsList),
                toArray(perLandAdjustmentsList), randomNumber);
    }

    @Deprecated
    private static double sampleIntensityProperRanges(
            double dispersionParameter, double landArea,
            double utilityPerUnitSpace, double utilityPerUnitLand,
            double[] intensityPoints, double[] perSpaceAdjustments,
            double[] perLandAdjustments, double randomNumber) {
        double[] Dplus = new double[intensityPoints.length]; // indefinite
                                                             // integral just
                                                             // below
                                                             // boundary
        double[] Dminus = new double[intensityPoints.length]; // indefinite
                                                              // integral just
                                                              // above
                                                              // boundary
        double[] D = new double[intensityPoints.length];
        double netUtility = utilityPerUnitLand;
        for (int point = 0; point < intensityPoints.length; point++) {
            double perSpace = utilityPerUnitSpace;
            {
                perSpace += perSpaceAdjustments[Math.max(0, point - 1)];
                if (perSpace == 0) {
                    // have to consider the possibility of costs cancelling out
                    // and being exactly zero
                    Dminus[point] = intensityPoints[point] * landArea
                            * Math.exp(dispersionParameter * netUtility);
                } else {
                    double lowPoint = 0;
                    if (point != 0)
                        lowPoint = intensityPoints[point - 1];
                    netUtility += (intensityPoints[point] - lowPoint)
                            * (utilityPerUnitSpace + perSpaceAdjustments[Math
                                    .max(0, point - 1)]);
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
            if (point < perSpaceAdjustments.length) {
                // no Dplus at the top boundary.
                perSpace = utilityPerUnitSpace + perSpaceAdjustments[point];

                netUtility += perLandAdjustments[point];
                if (perSpace == 0) {
                    Dplus[point] = intensityPoints[point] * landArea
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
        for (int highPoint = intensityPoints.length - 1; highPoint > 0; highPoint--) {
            if (highPoint < intensityPoints.length - 1) {
                // subtract this out in general, but not in the first loop
                // because we never added it in for Dplus at the top boundary
                netUtility -= perLandAdjustments[highPoint];
            }
            if (D[highPoint - 1] < randomNumber
                    * D[intensityPoints.length - 1]) {
                // it's in this range
                // find the slope of our cost curve
                double perSpace = utilityPerUnitSpace
                        + perSpaceAdjustments[highPoint - 1];
                // back out the intercept of our cost curve
                double perLand = netUtility - intensityPoints[highPoint]
                        * perSpace;

                if (perSpace == 0) {
                    double samplePoint = randomNumber
                            * D[intensityPoints.length - 1] - D[highPoint - 1];
                    double intensity = samplePoint
                            / (D[highPoint] - D[highPoint - 1])
                            * (intensityPoints[highPoint] - intensityPoints[highPoint - 1])
                            + intensityPoints[highPoint - 1];
                    quantity = intensity * landArea;
                } else {
                    double samplePoint = randomNumber
                            * D[intensityPoints.length - 1] - D[highPoint - 1]
                            + Dplus[highPoint - 1];
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
                lowPoint = intensityPoints[highPoint - 1];
            netUtility -= (intensityPoints[highPoint] - lowPoint)
                    * (utilityPerUnitSpace + perSpaceAdjustments[Math.max(0,
                            highPoint - 1)]);
        }
        if (Double.isInfinite(quantity) || Double.isNaN(quantity)) {
            // if (logger.isDebugEnabled())
            logger.warn("truncating sampled intensity at maximum");
            return intensityPoints[intensityPoints.length - 1];
        }
        if (quantity > intensityPoints[intensityPoints.length - 1] * landArea) {
            // if (logger.isDebugEnabled())
            logger.warn("truncating sampled intensity at maximum");
            return intensityPoints[intensityPoints.length - 1];
        }
        if (quantity < intensityPoints[0] * landArea) {
            // if (logger.isDebugEnabled())
            logger.warn("truncating sampled intensity at minimum");
            return intensityPoints[0];
        }
        return quantity / landArea;
    }

    @Deprecated
    protected static double getCompositeUtilityTwoRangesWithAdjustments(
            double perSpace, double perLand, double landSize, double stepPoint,
            double stepPointAdjustment, double belowStepPointAdjustment,
            double aboveStepPointAdjustment, double minIntensity,
            double maxIntensity, double intensityDispersion) {
        return getCompositeUtility(intensityDispersion, landSize, perSpace,
                perLand,
                new double[] { minIntensity, stepPoint, maxIntensity },
                new double[] { belowStepPointAdjustment,
                        aboveStepPointAdjustment }, new double[] { 0,
                        stepPointAdjustment });
    }

    @Deprecated
    private static double getCompositeUtilityProperRanges(
            double dispersionParameter, double landArea,
            double utilityPerUnitSpace, double utilityPerUnitLand,
            double[] intensityPoints, double[] perSpaceAdjustments,
            double[] perLandAdjustments) {

        int numRanges = intensityPoints.length - 1;
        double currentLandUtility = utilityPerUnitLand;
        double result = 0;

        for (int i = 0; i < numRanges; i++) {
            double perSpace = utilityPerUnitSpace + perSpaceAdjustments[i];
            currentLandUtility += perLandAdjustments[i];
            if (i > 0)
                currentLandUtility += (perSpaceAdjustments[i - 1] - perSpaceAdjustments[i])
                        * intensityPoints[i];

            double minFAR = intensityPoints[i];
            double maxFAR = intensityPoints[i + 1];

            result += integrateOverIntensityRange(perSpace, currentLandUtility,
                    landArea, minFAR, maxFAR, dispersionParameter);
        }

        result = 1 / dispersionParameter * Math.log(result);

        return result;
    }

    @Deprecated
    protected static double getCompositeUtility(double dispersionParameter,
            double landArea, double utilityPerUnitSpace,
            double utilityPerUnitLand, double[] intensityPoints,
            double[] perSpaceAdjustments, double[] perLandAdjustments) {

        List<Double> intensityPointsList = toList(intensityPoints);
        List<Double> perSpaceAdjustmentsList = toList(perSpaceAdjustments);
        List<Double> perLandAdjustmentsList = toList(perLandAdjustments);

        checkRanges(intensityPointsList, perSpaceAdjustmentsList,
                perLandAdjustmentsList);

        return getCompositeUtilityProperRanges(dispersionParameter, landArea,
                utilityPerUnitSpace, utilityPerUnitLand,
                toArray(intensityPointsList), toArray(perSpaceAdjustmentsList),
                toArray(perLandAdjustmentsList));
    }

    @Deprecated
    private static double getUtilityDerivativeWRTMaxIntensity(double perSpace,
            double perLand, double minIntensity, double maxIntensity,
            double dispersion) {
        double landweight = Math.exp(dispersion * perLand);
        double maxweight = Math.exp(dispersion * perSpace * maxIntensity);
        return landweight * maxweight;
    }

    @Deprecated
    private static double getUtilityDerivativeWRTMinIntensity(double perSpace,
            double perLand, double minIntensity, double maxIntensity,
            double dispersion) {
        double landweight = Math.exp(dispersion * perLand);
        double minweight = Math.exp(dispersion * perSpace * minIntensity);
        return -landweight * minweight;
    }

    @Deprecated
    private static double getUtilityDerivativeWRTPerSpace(double perSpace,
            double perLand, double minIntensity, double maxIntensity,
            double dispersion) {
        if (Math.abs(perSpace) <= epsilon) {
            double landweight = Math.exp(dispersion * perLand);
            return dispersion * landweight
                    * (square(maxIntensity) - square(minIntensity)) / 2;
        } else {
            double landweight = Math.exp(dispersion * perLand);
            double maxutil = dispersion * maxIntensity * perSpace;
            double minutil = dispersion * minIntensity * perSpace;
            double maxweight = Math.exp(maxutil);
            double minweight = Math.exp(minutil);
            double weightdiff = diffexp(minutil, maxutil);
            double utildiff = maxutil * maxweight - minutil * minweight;
            return landweight / (dispersion * square(perSpace))
                    * (weightdiff + utildiff);
        }
    }

    @Deprecated
    private static double getUtilityDerivativeWRTPerLand(double perSpace,
            double perLand, double minIntensity, double maxIntensity,
            double dispersion) {
        if (Math.abs(perSpace) <= epsilon) {
            double landweight = Math.exp(dispersion * perLand);
            return dispersion * landweight * (maxIntensity - minIntensity);
        } else {
            double landweight = Math.exp(dispersion * perLand);
            double maxweight = Math.exp(dispersion * maxIntensity * perSpace);
            double minweight = Math.exp(dispersion * minIntensity * perSpace);
            return landweight / perSpace * (maxweight - minweight);
        }
    }

    @Deprecated
    private static double getUtilityDerivativeWRTDispersion(double perSpace,
            double perLand, double minIntensity, double maxIntensity,
            double dispersion) {
        if (Math.abs(perSpace) <= epsilon) {
            double landweight = Math.exp(dispersion * perLand);
            return perLand * landweight * (maxIntensity - minIntensity);
        } else {
            double landutil = dispersion * perLand;
            double maxutil = dispersion * maxIntensity * perSpace;
            double minutil = dispersion * minIntensity * perSpace;
            double landweight = Math.exp(landutil);
            double maxweight = Math.exp(maxutil);
            double minweight = Math.exp(minutil);
            return landweight
                    / (square(dispersion) * perSpace)
                    * ((maxutil + landutil - 1) * maxweight - (minutil
                            + landutil - 1)
                            * minweight);
        }
    }

    @Deprecated
    private static Matrix getUtilityDerivativesWRTUtilityIntegrals(
            int numRanges, double[] integrals, double dispersion) {
        double integralsum = 0;
        for (int i = 0; i < numRanges; i++)
            integralsum += integrals[i];

        Matrix result = new DenseMatrix(1, numRanges);
        for (int i = 0; i < numRanges; i++)
            result.set(0, i, 1 / (dispersion * integralsum));

        return result;
    }

    @Deprecated
    private static Matrix[] getUtilityDerivativesWRTIntegralParameters(
            int numRanges, double[] perSpaces, double[] perLands,
            double[] minIntensities, double[] maxIntensities, double dispersion) {
        Matrix[] result = new Matrix[numRanges];
        for (int i = 0; i < numRanges; i++) {
            double[] row = new double[NUM_INT_PARAMS];
            row[IND_US] = getUtilityDerivativeWRTPerSpace(perSpaces[i],
                    perLands[i], minIntensities[i], maxIntensities[i],
                    dispersion);
            row[IND_UA] = getUtilityDerivativeWRTPerLand(perSpaces[i],
                    perLands[i], minIntensities[i], maxIntensities[i],
                    dispersion);
            row[IND_SMIN] = getUtilityDerivativeWRTMinIntensity(perSpaces[i],
                    perLands[i], minIntensities[i], maxIntensities[i],
                    dispersion);
            row[IND_SMAX] = getUtilityDerivativeWRTMaxIntensity(perSpaces[i],
                    perLands[i], minIntensities[i], maxIntensities[i],
                    dispersion);
            row[IND_DISP] = getUtilityDerivativeWRTDispersion(perSpaces[i],
                    perLands[i], minIntensities[i], maxIntensities[i],
                    dispersion);

            Matrix m = new DenseMatrix(1, 5);
            result[i] = new DenseMatrix(new DenseVector(row)).transpose(m);
        }
        return result;
    }

    @Deprecated
    private static Matrix[] getIntegralParameterDerivativesWRTParameters(
            int numRanges, double[] perSpaceAdjustments,
            double[] perLandAdjustments, double[] stepPoints, double dispersion) {
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
                        perSpaceAdjustments[j] - perSpaceAdjustments[j + 1]);
            // ... and on all the per-space adjustments up to the next one.
            for (int j = 0; j < i; j++)
                result[i].add(IND_UA, firstPerSpacePos + j, stepPoints[j + 1]);
            for (int j = 0; j < i; j++)
                result[i].add(IND_UA, firstPerSpacePos + j + 1,
                        -stepPoints[j + 1]);
        }

        return result;
    }

    @Deprecated
    protected static Vector getTwoRangeUtilityDerivativesWRTParameters(
            double perSpace, double perLand, double landSize, double stepPoint,
            double stepPointAdjustment, double belowStepPointAdjustment,
            double aboveStepPointAdjustment, double minIntensity,
            double maxIntensity, double intensityDispersion) {
        return getUtilityDerivativesWRTParameters(intensityDispersion,
                landSize, perSpace, perLand, new double[] { minIntensity,
                        stepPoint, maxIntensity }, new double[] {
                        belowStepPointAdjustment, aboveStepPointAdjustment },
                new double[] { 0, stepPointAdjustment });
    }

    @Deprecated
    private static Vector getUtilityDerivativesWRTParametersProperRanges(
            double dispersionParameter, double landArea,
            double utilityPerUnitSpace, double utilityPerUnitLand,
            double[] intensityPoints, double[] perSpaceAdjustments,
            double[] perLandAdjustments) {

        int numRanges = intensityPoints.length - 1;
        int numCoeffs = 3 * numRanges + 2;

        double currentLandUtility = utilityPerUnitLand;
        double[] perSpaces = new double[numRanges];
        double[] perLands = new double[numRanges];
        double[] minFARs = new double[numRanges];
        double[] maxFARs = new double[numRanges];
        double[] integrals = new double[numRanges];

        // Find the modified parameters for each range.
        for (int i = 0; i < numRanges; i++) {
            perSpaces[i] = utilityPerUnitSpace + perSpaceAdjustments[i];
            currentLandUtility += perLandAdjustments[i];
            if (i > 0)
                currentLandUtility += (perSpaceAdjustments[i - 1] - perSpaceAdjustments[i])
                        * intensityPoints[i];
            perLands[i] = currentLandUtility;

            minFARs[i] = intensityPoints[i];
            maxFARs[i] = intensityPoints[i + 1];

            integrals[i] = integrateOverIntensityRange(perSpaces[i],
                    perLands[i], landArea, minFARs[i], maxFARs[i],
                    dispersionParameter);
        }

        Matrix[] dadt = getIntegralParameterDerivativesWRTParameters(numRanges,
                perSpaceAdjustments, perLandAdjustments, intensityPoints,
                dispersionParameter);
        Matrix[] dIda = getUtilityDerivativesWRTIntegralParameters(numRanges,
                perSpaces, perLands, minFARs, maxFARs, dispersionParameter);
        Matrix dIdt = multiplyAndAggregate(numRanges, NUM_INT_PARAMS,
                numCoeffs, dIda, dadt);
        Matrix dUdI = getUtilityDerivativesWRTUtilityIntegrals(numRanges,
                integrals, dispersionParameter);

        Matrix result = new DenseMatrix(1, numCoeffs);
        result = dUdI.mult(dIdt, result);

        // Convert into a vector.
        Vector vector = new DenseVector(numCoeffs);
        for (int i = 0; i < numCoeffs; i++)
            vector.set(i, result.get(0, i));

        // Add direct dependency of the composite utility on the dispersion
        // parameter.
        double integralsum = 0;
        for (int i = 0; i < numRanges; i++)
            integralsum += integrals[i];
        double dUdlambda = -1 / square(dispersionParameter)
                * Math.log(integralsum);
        vector.add(numCoeffs - 1, dUdlambda);

        return vector;
    }

    @Deprecated
    protected static Vector getUtilityDerivativesWRTParameters(
            double dispersionParameter, double landArea,
            double utilityPerUnitSpace, double utilityPerUnitLand,
            double[] intensityPoints, double[] perSpaceAdjustments,
            double[] perLandAdjustments) {

        List<Double> intensityPointsList = toList(intensityPoints);
        List<Double> perSpaceAdjustmentsList = toList(perSpaceAdjustments);
        List<Double> perLandAdjustmentsList = toList(perLandAdjustments);

        checkRanges(intensityPointsList, perSpaceAdjustmentsList,
                perLandAdjustmentsList);

        Vector result = getUtilityDerivativesWRTParametersProperRanges(
                dispersionParameter, landArea, utilityPerUnitSpace,
                utilityPerUnitLand, toArray(intensityPointsList),
                toArray(perSpaceAdjustmentsList),
                toArray(perLandAdjustmentsList));

        // The above vector will be too small. Add zeroes to positions
        // corresponding to invalid ranges (since these parameters
        // will not affect the utility).
        Vector restored = transformDerivativesForValidRanges(result,
                perSpaceAdjustments, perLandAdjustments, intensityPoints,
                dispersionParameter);
        return restored;
    }
    
    @Deprecated
    private static double getUtilityDerivativeWRTUtilityPerUnitSpaceProperRanges(
            double dispersionParameter, double landArea,
            double utilityPerUnitSpace, double utilityPerUnitLand,
            double[] intensityPoints, double[] perSpaceAdjustments,
            double[] perLandAdjustments) {
        
        int numRanges = intensityPoints.length - 1;
        double currentLandUtility = utilityPerUnitLand;
        double totalIntegral = 0;
        double totalDerivative = 0;
        
        for (int i = 0; i < numRanges; i++) {
            double perSpace = utilityPerUnitSpace + perSpaceAdjustments[i];
            currentLandUtility = perLandAdjustments[i];
            if (i > 0)
                currentLandUtility += (perSpaceAdjustments[i - 1] - perSpaceAdjustments[i]) * intensityPoints[i];
            
            double minFAR = intensityPoints[i];
            double maxFAR = intensityPoints[i + 1];

            totalIntegral += integrateOverIntensityRange(perSpace,
                    currentLandUtility, landArea, minFAR, maxFAR,
                    dispersionParameter);
            totalDerivative += getUtilityDerivativeWRTPerSpace(perSpace,
                    currentLandUtility, minFAR, maxFAR,
                    dispersionParameter);
        }
        
        return totalDerivative / (dispersionParameter * totalIntegral);
    }
    
    @Deprecated
    protected static double getUtilityDerivativeWRTUtilityPerUnitSpace(
            double dispersionParameter, double landArea,
            double utilityPerUnitSpace, double utilityPerUnitLand,
            double[] intensityPoints, double[] perSpaceAdjustments,
            double[] perLandAdjustments) {
        List<Double> intensityPointsList = toList(intensityPoints);
        List<Double> perSpaceAdjustmentsList = toList(perSpaceAdjustments);
        List<Double> perLandAdjustmentsList = toList(perLandAdjustments);

        checkRanges(intensityPointsList, perSpaceAdjustmentsList,
                perLandAdjustmentsList);

        return getUtilityDerivativeWRTUtilityPerUnitSpaceProperRanges(
                dispersionParameter, landArea, utilityPerUnitSpace,
                utilityPerUnitLand, toArray(intensityPointsList),
                toArray(perSpaceAdjustmentsList),
                toArray(perLandAdjustmentsList));
    }

    @Deprecated
    protected static double getExpectedFARSum(double perSpace, double perLand,
            double landSize, double minIntensity, double maxIntensity,
            double dispersion) {
        if (Math.abs(perSpace) <= epsilon) {
            double landweight = Math.exp(dispersion * perLand);
            return landweight / 2
                    * (square(maxIntensity) - square(minIntensity));
        } else {
            double maxutil = dispersion * maxIntensity * perSpace;
            double minutil = dispersion * minIntensity * perSpace;
            double landweight = Math.exp(dispersion * perLand);
            double maxweight = Math.exp(maxutil);
            double minweight = Math.exp(minutil);
            double denominator = square(dispersion) * square(perSpace);
            double weightdiff = diffexp(minutil, maxutil);
            double utildiff = maxutil * maxweight - minutil * minweight;

            return landweight / denominator * (weightdiff + utildiff);
        }
    }

    @Deprecated
    protected static double getExpectedFARTwoRangesWithAdjustments(
            double perSpace, double perLand, double landSize, double stepPoint,
            double stepPointAdjustment, double belowStepPointAdjustment,
            double aboveStepPointAdjustment, double minIntensity,
            double maxIntensity, double intensityDispersion) {
        return getExpectedFAR(intensityDispersion, landSize, perSpace, perLand,
                new double[] { minIntensity, stepPoint, maxIntensity },
                new double[] { belowStepPointAdjustment,
                        aboveStepPointAdjustment }, new double[] { 0,
                        stepPointAdjustment });
    }

    @Deprecated
    private static double getExpectedFARProperRanges(
            double dispersionParameter, double landArea,
            double utilityPerUnitSpace, double utilityPerUnitLand,
            double[] intensityPoints, double[] perSpaceAdjustments,
            double[] perLandAdjustments) {
        int numRanges = intensityPoints.length - 1;
        double currentLandUtility = utilityPerUnitLand;
        double result = 0;
        double integral = 0;

        for (int i = 0; i < numRanges; i++) {
            double perSpace = utilityPerUnitSpace + perSpaceAdjustments[i];
            currentLandUtility += perLandAdjustments[i];
            if (i > 0)
                currentLandUtility += (perSpaceAdjustments[i - 1] - perSpaceAdjustments[i])
                        * intensityPoints[i];

            double minFAR = intensityPoints[i];
            double maxFAR = intensityPoints[i + 1];

            result += getExpectedFARSum(perSpace, currentLandUtility, landArea,
                    minFAR, maxFAR, dispersionParameter);
            // These integrals are needed to normalize by the total area.
            integral += integrateOverIntensityRange(perSpace,
                    currentLandUtility, landArea, minFAR, maxFAR,
                    dispersionParameter);
        }

        return result / integral;
    }

    @Deprecated
    protected static double getExpectedFAR(double dispersionParameter,
            double landArea, double utilityPerUnitSpace,
            double utilityPerUnitLand, double[] intensityPoints,
            double[] perSpaceAdjustments, double[] perLandAdjustments) {

        List<Double> intensityPointsList = toList(intensityPoints);
        List<Double> perSpaceAdjustmentsList = toList(perSpaceAdjustments);
        List<Double> perLandAdjustmentsList = toList(perLandAdjustments);

        checkRanges(intensityPointsList, perSpaceAdjustmentsList,
                perLandAdjustmentsList);

        return getExpectedFARProperRanges(dispersionParameter, landArea,
                utilityPerUnitSpace, utilityPerUnitLand,
                toArray(intensityPointsList), toArray(perSpaceAdjustmentsList),
                toArray(perLandAdjustmentsList));
    }

    @Deprecated
    private static Matrix getExpectedFARDerivativesWRTExpectedFARSums(
            int numRanges) {
        // The total expected FAR is simply the sum of the FAR sum in each
        // range, so these are all 1.
        double[] result = new double[numRanges];
        for (int i = 0; i < numRanges; i++)
            result[i] = 1;
        Matrix m = new DenseMatrix(1, numRanges);
        return new DenseMatrix(new DenseVector(result)).transpose(m);
    }

    @Deprecated
    private static Matrix getExpectedFARSumDerivativesWRTUtilityIntegrals(
            int numRanges, double[] integrals, double[] perSpaces,
            double[] perLands, double[] minIntensities,
            double[] maxIntensities, double dispersion) {
        double[][] result = new double[numRanges][numRanges];
        for (int i = 0; i < numRanges; i++) {
            double derivative;
            double landweight = Math.exp(dispersion * perLands[i]);
            double integralsum = 0;
            for (int j = 0; j < numRanges; j++)
                integralsum += integrals[j];
            if (Math.abs(perSpaces[i]) <= epsilon) {
                double intensityterm = square(maxIntensities[i])
                        - square(minIntensities[i]);
                derivative = -landweight / (2 * square(integralsum))
                        * intensityterm;
            } else {
                double maxutil = dispersion * maxIntensities[i] * perSpaces[i];
                double minutil = dispersion * minIntensities[i] * perSpaces[i];
                double maxweight = Math.exp(maxutil);
                double minweight = Math.exp(minutil);

                double denominator = square(dispersion) * square(perSpaces[i])
                        * square(integralsum);
                double weightdiff = diffexp(minutil, maxutil);
                double utildiff = maxutil * maxweight - minutil * minweight;
                derivative = -landweight / denominator
                        * (weightdiff + utildiff);
            }
            for (int j = 0; j < numRanges; j++)
                // Derivative is the same with respect to all utility integrals.
                result[i][j] = derivative;
        }

        return new DenseMatrix(result);
    }

    @Deprecated
    private static double getExpectedFARSumDerivativeWRTMaxIntensity(
            double integralsum, double perSpace, double perLand,
            double minIntensity, double maxIntensity, double dispersion) {
        double landweight = Math.exp(dispersion * perLand);
        double maxweight = Math.exp(dispersion * maxIntensity * perSpace);
        return landweight / integralsum * maxIntensity * maxweight;
    }

    @Deprecated
    private static double getExpectedFARSumDerivativeWRTMinIntensity(
            double integralsum, double perSpace, double perLand,
            double minIntensity, double maxIntensity, double dispersion) {
        double landweight = Math.exp(dispersion * perLand);
        double minweight = Math.exp(dispersion * perSpace * minIntensity);
        return -landweight / integralsum * minIntensity * minweight;
    }

    @Deprecated
    private static double getExpectedFARSumDerivativeWRTPerSpace(
            double integralsum, double perSpace, double perLand,
            double minIntensity, double maxIntensity, double dispersion) {
        if (Math.abs(perSpace) <= epsilon) {
            double landweight = Math.exp(dispersion * perLand);
            return dispersion * landweight / (3 * integralsum)
                    * (cube(maxIntensity) - cube(minIntensity));
        } else {
            double landweight = Math.exp(dispersion * perLand);
            double denominator = square(dispersion) * cube(perSpace)
                    * integralsum;
            double maxutil = dispersion * maxIntensity * perSpace;
            double minutil = dispersion * minIntensity * perSpace;
            double maxweight = Math.exp(maxutil);
            double minweight = Math.exp(minutil);

            double squarediff = square(maxutil) * maxweight
                    - square(minutil) * minweight;
            double utildiff = 2 * minutil * minweight - 2 * maxutil * maxweight;
            double weightdiff = 2 * diffexp(maxutil, minutil);

            return landweight / denominator
                    * (squarediff + utildiff + weightdiff);
        }
    }

    @Deprecated
    private static double getExpectedFARSumDerivativeWRTPerLand(
            double integralsum, double perSpace, double perLand,
            double minIntensity, double maxIntensity, double dispersion) {
        if (Math.abs(perSpace) <= epsilon) {
            double landweight = Math.exp(dispersion * perLand);
            return dispersion * landweight / (2 * integralsum)
                    * (square(maxIntensity) - square(minIntensity));
        } else {
            double landweight = Math.exp(dispersion * perLand);
            double denominator = dispersion * square(perSpace) * integralsum;
            double maxutil = dispersion * maxIntensity * perSpace;
            double minutil = dispersion * minIntensity * perSpace;
            double maxweight = Math.exp(maxutil);
            double minweight = Math.exp(minutil);

            double utildiff = maxutil * maxweight - minutil * minweight;
            double weightdiff = diffexp(minutil, maxutil);

            return landweight / denominator * (utildiff + weightdiff);
        }
    }

    @Deprecated
    private static double getExpectedFARSumDerivativeWRTDispersion(
            double integralsum, double perSpace, double perLand,
            double minIntensity, double maxIntensity, double dispersion) {
        if (Math.abs(perSpace) <= epsilon) {
            double landweight = Math.exp(dispersion * perLand);
            return perLand * landweight / (2 * integralsum)
                    * (square(maxIntensity) - square(minIntensity));
        } else {
            double landutil = dispersion * perLand;
            double denominator = square(perSpace) * cube(dispersion)
                    * integralsum;
            double maxutil = dispersion * maxIntensity * perSpace;
            double minutil = dispersion * minIntensity * perSpace;
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

    @Deprecated
    private static Matrix[] getExpectedFARSumDerivativesWRTIntegralParameters(
            int numRanges, double[] integrals, double[] perSpaces,
            double[] perLands, double[] minIntensities,
            double[] maxIntensities, double dispersion) {
        Matrix[] result = new Matrix[numRanges];

        double integralsum = 0;
        for (int i = 0; i < numRanges; i++)
            integralsum += integrals[i];

        for (int i = 0; i < numRanges; i++) {
            double[] row = new double[NUM_INT_PARAMS];
            row[IND_US] = getExpectedFARSumDerivativeWRTPerSpace(integralsum,
                    perSpaces[i], perLands[i], minIntensities[i],
                    maxIntensities[i], dispersion);
            row[IND_UA] = getExpectedFARSumDerivativeWRTPerLand(integralsum,
                    perSpaces[i], perLands[i], minIntensities[i],
                    maxIntensities[i], dispersion);
            row[IND_SMIN] = getExpectedFARSumDerivativeWRTMinIntensity(
                    integralsum, perSpaces[i], perLands[i], minIntensities[i],
                    maxIntensities[i], dispersion);
            row[IND_SMAX] = getExpectedFARSumDerivativeWRTMaxIntensity(
                    integralsum, perSpaces[i], perLands[i], minIntensities[i],
                    maxIntensities[i], dispersion);
            row[IND_DISP] = getExpectedFARSumDerivativeWRTDispersion(
                    integralsum, perSpaces[i], perLands[i], minIntensities[i],
                    maxIntensities[i], dispersion);

            Matrix m = new DenseMatrix(1, 5);
            result[i] = new DenseMatrix(new DenseVector(row)).transpose(m);
        }
        return result;
    }

    @Deprecated
    protected static Vector getTwoRangeExpectedFARDerivativesWRTParameters(
            double perSpace, double perLand, double landSize, double stepPoint,
            double stepPointAdjustment, double belowStepPointAdjustment,
            double aboveStepPointAdjustment, double minIntensity,
            double maxIntensity, double intensityDispersion) {
        return getExpectedFARDerivativesWRTParameters(intensityDispersion,
                landSize, perSpace, perLand, new double[] { minIntensity,
                        stepPoint, maxIntensity }, new double[] {
                        belowStepPointAdjustment, aboveStepPointAdjustment },
                new double[] { 0, stepPointAdjustment });
    }

    @Deprecated
    private static Vector getExpectedFARDerivativesWRTParametersProperRanges(
            double dispersionParameter, double landArea,
            double utilityPerUnitSpace, double utilityPerUnitLand,
            double[] intensityPoints, double[] perSpaceAdjustments,
            double[] perLandAdjustments) {
        int numRanges = intensityPoints.length - 1;
        int numCoeffs = 3 * numRanges + 2;

        double currentLandUtility = utilityPerUnitLand;
        double[] perSpaces = new double[numRanges];
        double[] perLands = new double[numRanges];
        double[] minFARs = new double[numRanges];
        double[] maxFARs = new double[numRanges];
        double[] integrals = new double[numRanges];

        // Find the modified parameters for each range.
        for (int i = 0; i < numRanges; i++) {
            perSpaces[i] = utilityPerUnitSpace + perSpaceAdjustments[i];
            currentLandUtility += perLandAdjustments[i];
            if (i > 0)
                currentLandUtility += (perSpaceAdjustments[i - 1] - perSpaceAdjustments[i])
                        * intensityPoints[i];
            perLands[i] = currentLandUtility;

            minFARs[i] = intensityPoints[i];
            maxFARs[i] = intensityPoints[i + 1];

            integrals[i] = integrateOverIntensityRange(perSpaces[i],
                    perLands[i], landArea, minFARs[i], maxFARs[i],
                    dispersionParameter);
        }

        // Component of partial derivatives through dependency on utility
        // integrals.
        Matrix[] dadt = getIntegralParameterDerivativesWRTParameters(numRanges,
                perSpaceAdjustments, perLandAdjustments, intensityPoints,
                dispersionParameter);
        Matrix[] dIda = getUtilityDerivativesWRTIntegralParameters(numRanges,
                perSpaces, perLands, minFARs, maxFARs, dispersionParameter);
        Matrix dIdt = multiplyAndAggregate(numRanges, NUM_INT_PARAMS,
                numCoeffs, dIda, dadt);
        Matrix dIedI = getExpectedFARSumDerivativesWRTUtilityIntegrals(
                numRanges, integrals, perSpaces, perLands, minFARs, maxFARs,
                dispersionParameter);
        Matrix dIedt = new DenseMatrix(numRanges, numCoeffs);
        dIedt = dIedI.mult(dIdt, dIedt);

        // Component of partial derivatives through direct dependency of
        // expected value on parameters.
        Matrix[] dIeda = getExpectedFARSumDerivativesWRTIntegralParameters(
                numRanges, integrals, perSpaces, perLands, minFARs, maxFARs,
                dispersionParameter);
        dIedt.add(multiplyAndAggregate(numRanges, NUM_INT_PARAMS, numCoeffs,
                dIeda, dadt));

        // Multiply by the dependency of expected value on the expected value
        // for each range.
        Matrix dEdIe = getExpectedFARDerivativesWRTExpectedFARSums(numRanges);
        Matrix result = new DenseMatrix(1, numCoeffs);
        result = dEdIe.mult(dIedt, result);

        // Convert into a vector.
        Vector vector = new DenseVector(numCoeffs);
        for (int i = 0; i < numCoeffs; i++)
            vector.set(i, result.get(0, i));

        return vector;
    }

    @Deprecated
    protected static Vector getExpectedFARDerivativesWRTParameters(
            double dispersionParameter, double landArea,
            double utilityPerUnitSpace, double utilityPerUnitLand,
            double[] intensityPoints, double[] perSpaceAdjustments,
            double[] perLandAdjustments) {

        List<Double> intensityPointsList = toList(intensityPoints);
        List<Double> perSpaceAdjustmentsList = toList(perSpaceAdjustments);
        List<Double> perLandAdjustmentsList = toList(perLandAdjustments);

        checkRanges(intensityPointsList, perSpaceAdjustmentsList,
                perLandAdjustmentsList);

        Vector result = getExpectedFARDerivativesWRTParametersProperRanges(
                dispersionParameter, landArea, utilityPerUnitSpace,
                utilityPerUnitLand, toArray(intensityPointsList),
                toArray(perSpaceAdjustmentsList),
                toArray(perLandAdjustmentsList));

        // The above vector will be too small. Add zeroes to positions
        // corresponding to invalid ranges (since these parameters
        // will not affect the utility).
        Vector restored = transformDerivativesForValidRanges(result,
                perSpaceAdjustments, perLandAdjustments, intensityPoints,
                dispersionParameter);
        return restored;
    }
    
    @Deprecated
    private static double getExpectedFARDerivativeWRTUtilityPerUnitSpaceProperRanges(
            double dispersionParameter, double landArea,
            double utilityPerUnitSpace, double utilityPerUnitLand,
            double[] intensityPoints, double[] perSpaceAdjustments,
            double[] perLandAdjustments) {

        int numRanges = intensityPoints.length - 1;
        double currentLandUtility = utilityPerUnitLand;
        double totalFar = 0;
        double totalUtil = 0;
        double totalFarDerivative = 0;
        double totalUtilDerivative = 0;

        for (int i = 0; i < numRanges; i++) {
            double perSpace = utilityPerUnitSpace + perSpaceAdjustments[i];
            currentLandUtility += perLandAdjustments[i];
            if (i > 0)
                currentLandUtility += (perSpaceAdjustments[i - 1]
                        - perSpaceAdjustments[i]) * intensityPoints[i];

            double minFAR = intensityPoints[i];
            double maxFAR = intensityPoints[i + 1];

            totalFar += getExpectedFARSum(perSpace, currentLandUtility, landArea,
                    minFAR, maxFAR, dispersionParameter);
            totalUtil += integrateOverIntensityRange(perSpace,
                    currentLandUtility, landArea, minFAR, maxFAR,
                    dispersionParameter);
            totalFarDerivative += getExpectedFARSumDerivativeWRTPerSpace(
                    perSpace, currentLandUtility, landArea, minFAR, maxFAR,
                    dispersionParameter);
            totalUtilDerivative += getUtilityDerivativeWRTPerSpace(
                    perSpace, currentLandUtility, minFAR, maxFAR,
                    dispersionParameter);
        }
        
        return (totalUtil * totalFarDerivative - totalFar * totalUtilDerivative) / square(totalUtil);
    }
    
    @Deprecated
    protected static double getExpectedFARDerivativeWRTUtilityPerUnitSpace(
            double dispersionParameter, double landArea,
            double utilityPerUnitSpace, double utilityPerUnitLand,
            double[] intensityPoints, double[] perSpaceAdjustments,
            double[] perLandAdjustments) {
        List<Double> intensityPointsList = toList(intensityPoints);
        List<Double> perSpaceAdjustmentsList = toList(perSpaceAdjustments);
        List<Double> perLandAdjustmentsList = toList(perLandAdjustments);

        checkRanges(intensityPointsList, perSpaceAdjustmentsList,
                perLandAdjustmentsList);

        return getExpectedFARDerivativeWRTUtilityPerUnitSpaceProperRanges(
                dispersionParameter, landArea, utilityPerUnitSpace,
                utilityPerUnitLand, toArray(intensityPointsList),
                toArray(perSpaceAdjustmentsList),
                toArray(perLandAdjustmentsList));
    }

    @Deprecated
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

    public abstract void doDevelopment();

    /**
     * Returns the number of equally-sized pseudoparcels that should be created
     * on the current parcel.
     * 
     * @param inv The land inventory
     * @return The number of pseudoparcels
     */
    public int numSplits(LandInventory inv) {
        return ((int) (inv.getLandArea() / inv.getMaxParcelSize())) + 1;
    }
    
    /**
     * Splits an appropriately-sized pseudoparcel off of the current parcel,
     * throwing a RuntimeException if the parcel couldn't be split.
     * 
     * @param inv The land inventory
     * @return The new pseudoparcel
     */
    protected ParcelInterface splitParcel(LandInventory inv) {
        double parcelSizes = inv.getLandArea() / numSplits(inv);
        ParcelInterface newBit = null;
        try {
            newBit = inv.splitParcel(parcelSizes);
        } catch (NotSplittableException e) {
            loggerf.throwFatal(e, "Can't split parcel");
        }
        return newBit;
    }
}
