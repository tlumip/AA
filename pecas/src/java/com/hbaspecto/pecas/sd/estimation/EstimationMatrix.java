package com.hbaspecto.pecas.sd.estimation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;

import com.hbaspecto.discreteChoiceModelling.Coefficient;

import no.uib.cipr.matrix.DenseMatrix;
import no.uib.cipr.matrix.DenseVector;
import no.uib.cipr.matrix.Matrix;
import no.uib.cipr.matrix.Vector;

/**
 * A container that accumulates the values needed for model estimation (the
 * expected values and derivatives). This class can be used concurrently by
 * multiple threads.
 * 
 * @author HBA
 * 
 */
public class EstimationMatrix {
    private final Vector expvalues;
    // Rows of targets, columns of coefficients, linearized relationship between
    // targets and coefficients
    private final Map<Thread, Matrix> derivatives = new HashMap<>();

    // Effectively immutable.
    private final List<ExpectedValue> expectedValueVariables;

    // Effectively immutable.
    private final List<Coefficient> coefficients;

    private final ExpectedValueFilter filter;

    public EstimationMatrix(List<ExpectedValue> eVals,
            List<Coefficient> coefficients) {
        filter = new ExpectedValueFilter() {
            @Override
            public List<ExpectedValue> allExpectedValues() {
                return eVals;
            }

            @Override
            public Iterable<ExpectedValue> applicableExpectedValues() {
                return eVals;
            }
        };
        expectedValueVariables = new BackMapList<ExpectedValue>(eVals);
        this.coefficients = new BackMapList<Coefficient>(coefficients);
        expvalues = new DenseVector(eVals.size());
    }

    public EstimationMatrix(ExpectedValueFilter filter,
            List<Coefficient> coefficients) {
        this.filter = filter;
        expectedValueVariables = new BackMapList<ExpectedValue>(
                filter.allExpectedValues());
        this.coefficients = new BackMapList<Coefficient>(coefficients);
        expvalues = new DenseVector(expectedValueVariables.size());
    }

    public void addExpectedValueComponent(Vector component) {
        synchronized (expvalues) {
            expvalues.add(component);
        }
    }

    public void addExpectedValueComponentApplicableToCurrentParcel(
            Vector component) {
        int i = 0;
        synchronized (expvalues) {
            for (ExpectedValue t : filter.applicableExpectedValues()) {
                if (t.appliesToCurrentParcel()) {
                    int j = expectedValueVariables.indexOf(t);
                    expvalues.add(j, component.get(i));
                }
                i++;
            }
        }
    }

    /**
     * Adds the specified values, element by element, to the cumulative totals
     * for the derivatives.
     * 
     * @param component
     * @throws IndexOutOfBoundsException if the given matrix is the wrong size.
     */
    public void addDerivativeComponent(Matrix component) {
        Matrix myDerivs = derivativesForThisThread();
        myDerivs.add(component);
    }

    public void addDerivativeComponentApplicableToCurrentParcel(
            Matrix component) {
        Matrix myDerivs = derivativesForThisThread();
        int i = 0;
        for (ExpectedValue t : filter.applicableExpectedValues()) {
            if (t.appliesToCurrentParcel()) {
                int j = expectedValueVariables.indexOf(t);
                for (int k = 0; k < myDerivs.numColumns(); k++)
                    myDerivs.add(j, k, component.get(i, k));
            }
            i++;
        }
    }

    private Matrix derivativesForThisThread() {
        Thread thread = Thread.currentThread();
        synchronized (derivatives) {
            if (!derivatives.containsKey(thread)) {
                derivatives.put(thread, new DenseMatrix(
                        expectedValueVariables.size(), coefficients.size()));
            }
            return derivatives.get(thread);
        }
    }

    /**
     * Returns an immutable list holding the targets. The list has a back-map
     * from target objects to indices, so calls to <code>indexOf</code> and
     * <code>contains</code> have constant time performance.
     * 
     * @return An immutable list of targets.
     */
    public List<ExpectedValue> getTargets() {
        return expectedValueVariables;
    }

    /**
     * Returns an immutable list (similar to <code>getTargets</code>) containing
     * only those targets that proclaim themselves to be valid on the current
     * parcel.
     * 
     * @return An immutable list of the targets applicable to the current
     *         parcel.
     */
    public List<ExpectedValue> getTargetsApplicableToCurrentParcel() {
        List<ExpectedValue> sublist = new ArrayList<ExpectedValue>();
        for (ExpectedValue t : filter.applicableExpectedValues())
            if (t.appliesToCurrentParcel())
                sublist.add(t);

        return new BackMapList<ExpectedValue>(sublist);
    }

    /**
     * Returns an immutable list holding the coefficients. The list has a
     * back-map from coefficient objects to indices, so calls to
     * <code>indexOf</code> and <code>contains</code> have constant time
     * performance.
     * 
     * @return An immutable list of targets.
     */
    public List<Coefficient> getCoefficients() {
        return coefficients;
    }

    public Vector getExpectedValues() {
        synchronized (expvalues) {
            return expvalues.copy();
        }
    }

    public Vector getTargetValues(List<EstimationTarget> targets) {
        Vector expValues = getExpectedValues();
        // Convert these back to targets.
        for (int i = 0; i < expValues.size(); i++)
            expectedValueVariables.get(i).setModelledValue(expValues.get(i));
        Vector targetValues = new DenseVector(targets.size());
        for (int i = 0; i < targets.size(); i++)
            targetValues.set(i, targets.get(i).getModelledValue());
        return targetValues;
    }

    public Matrix getDerivatives() {
        Matrix result = new DenseMatrix(
                expectedValueVariables.size(), coefficients.size());
        synchronized (derivatives) {
            for (Matrix subDerivs : derivatives.values()) {
                result.add(subDerivs);
            }
        }
        return result;
    }

    public Matrix getTargetDerivatives(List<EstimationTarget> targets) {
        Vector expValues;
        Matrix expValuesDerivatives;
        synchronized (derivatives) {
            expValues = getExpectedValues();
            expValuesDerivatives = getDerivatives();
        }
        // Convert these back to targets.
        // loop over all expected values, and internally set their
        // derivatives
        // with respect to coefficients.
        for (int i = 0; i < expValues.size(); i++) {
            expectedValueVariables.get(i).setModelledValue(expValues.get(i));
            double[] derivatives = new double[coefficients.size()];
            for (int j = 0; j < coefficients.size(); j++)
                derivatives[j] = expValuesDerivatives.get(i, j);
            expectedValueVariables.get(i).setDerivatives(derivatives);
        }
        // loop over targets and calculate derivatives with respect to
        // coefficients, note
        // most targets have only one expected value but some don't so
        // that's
        // why
        // it needs to be in this separate loop.
        Matrix targetDerivatives = new DenseMatrix(targets.size(), coefficients.size());
        for (int i = 0; i < targets.size(); i++) {
            double[] derivatives = targets.get(i).getDerivatives();
            for (int j = 0; j < coefficients.size(); j++)
                targetDerivatives.set(i, j, derivatives[j]);
        }

        // Apply the chain rule on the coefficient transformations.
        for (int j = 0; j < coefficients.size(); j++) {
            double derivative = coefficients.get(j)
                    .getInverseTransformationDerivative();
            for (int i = 0; i < targets.size(); i++) {
                targetDerivatives.set(i, j, targetDerivatives.get(i, j) * derivative);
            }
        }
        return targetDerivatives;
    }

    // A wrapper around another list that prevents it from being
    // modified and provides fast lookup via a back-map.
    private class BackMapList<E> implements List<E> {
        private List<E> contents;
        // The integer array contains two indices - the first is the first index
        // of that element (for indexOf()), the second is the last index of that
        // element (for lastIndexOf()).
        private HashMap<E, int[]> backmap;

        private BackMapList(List<E> list) {
            contents = list;
            backmap = new HashMap<E, int[]>();
            Iterator<E> it = list.iterator();
            int i = 0;
            while (it.hasNext()) {
                E next = it.next();
                if (backmap.containsKey(next)) {
                    int[] indices = backmap.get(next);
                    // The last occurrence of the element is now at i.
                    indices[1] = i;
                } else {
                    int[] indices = new int[2];
                    indices[0] = i;
                    indices[1] = i;
                    backmap.put(next, indices);
                }
                i++;
            }
        }

        public boolean add(E e) {
            throw new UnsupportedOperationException();
        }

        public void add(int index, E element) {
            throw new UnsupportedOperationException();
        }

        public boolean addAll(Collection<? extends E> c) {
            throw new UnsupportedOperationException();
        }

        public boolean addAll(int index, Collection<? extends E> c) {
            throw new UnsupportedOperationException();
        }

        public void clear() {
            throw new UnsupportedOperationException();
        }

        public boolean contains(Object o) {
            return backmap.containsKey(o);
        }

        public boolean containsAll(Collection<?> c) {
            return backmap.keySet().containsAll(c);
        }

        @SuppressWarnings("rawtypes")
        public boolean equals(Object o) {
            if (o == this)
                return true;
            if (!(o instanceof BackMapList))
                return false;
            BackMapList other = (BackMapList) o;
            return contents.equals(other.contents);
        }

        public E get(int index) {
            return contents.get(index);
        }

        public int hashCode() {
            return contents.hashCode() + 1;
        }

        public int indexOf(Object o) {
            if (this.contains(o))
                return backmap.get(o)[0];
            else
                return -1;
        }

        public boolean isEmpty() {
            return contents.isEmpty();
        }

        public Iterator<E> iterator() {
            return Collections.unmodifiableList(contents).iterator();
        }

        public int lastIndexOf(Object o) {
            if (this.contains(o))
                return backmap.get(o)[1];
            else
                return -1;
        }

        public ListIterator<E> listIterator() {
            return Collections.unmodifiableList(contents).listIterator();
        }

        public ListIterator<E> listIterator(int index) {
            return Collections.unmodifiableList(contents).listIterator(index);
        }

        public E remove(int index) {
            throw new UnsupportedOperationException();
        }

        public boolean remove(Object o) {
            throw new UnsupportedOperationException();
        }

        public boolean removeAll(Collection<?> c) {
            throw new UnsupportedOperationException();
        }

        public boolean retainAll(Collection<?> c) {
            throw new UnsupportedOperationException();
        }

        public E set(int index, E element) {
            throw new UnsupportedOperationException();
        }

        public int size() {
            return contents.size();
        }

        public List<E> subList(int fromIndex, int toIndex) {
            return new BackMapList<E>(contents.subList(fromIndex, toIndex));
        }

        public Object[] toArray() {
            return contents.toArray();
        }

        public <T> T[] toArray(T[] a) {
            return contents.toArray(a);
        }
    }
}
