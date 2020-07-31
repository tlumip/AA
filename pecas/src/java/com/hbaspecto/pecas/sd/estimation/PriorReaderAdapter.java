package com.hbaspecto.pecas.sd.estimation;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import com.hbaspecto.discreteChoiceModelling.Coefficient;

public class PriorReaderAdapter implements PriorReader {

    private EstimationReader delegate;

    public PriorReaderAdapter(EstimationReader reader) {
        delegate = reader;
    }

    @Override
    public List<Coefficient> parameters() {
        return delegate.readCoeffs();
    }

    @Override
    public double mean(Coefficient param) {
        return delegate.readPriorMeans(Collections.singletonList(param))[0];
    }

    @Override
    public double[] means(List<Coefficient> params) {
        return delegate.readPriorMeans(params);
    }

    @Override
    public double startValue(Coefficient param) {
        return delegate.readStartingValues(Collections.singletonList(param))[0];
    }

    @Override
    public double[] startValues(List<Coefficient> params) {
        return delegate.readStartingValues(params);
    }

    @Override
    public double variance(Coefficient param) {
        return delegate
                .readPriorVariance(Collections.singletonList(param))[0][0];
    }

    @Override
    public double covariance(Coefficient param1, Coefficient param2) {
        if (param1 == param2) {
            return variance(param1);
        } else {
            return delegate
                    .readPriorVariance(Arrays.asList(param1, param2))[0][1];
        }
    }

    @Override
    public double[][] variance(List<Coefficient> params) {
        return delegate.readPriorVariance(params);
    }

}
