package com.hbaspecto.pecas.sd.estimation;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class TargetReaderAdapter implements TargetReader {

    private EstimationReader delegate;

    public TargetReaderAdapter(EstimationReader reader) {
        delegate = reader;
    }

    @Override
    public List<EstimationTarget> targets() {
        return delegate.readTargets();
    }

    @Override
    public double variance(EstimationTarget target) {
        return delegate
                .readTargetVariance(Collections.singletonList(target))[0][0];
    }

    @Override
    public double covariance(EstimationTarget target1,
            EstimationTarget target2) {
        if (target1 == target2) {
            return variance(target1);
        } else {
            return delegate
                    .readTargetVariance(Arrays.asList(target1, target2))[0][1];
        }
    }

    @Override
    public double[][] variance(List<EstimationTarget> targets) {
        return delegate.readTargetVariance(targets);
    }
}
