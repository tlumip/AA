package com.hbaspecto.pecas.sd.estimation;

import java.util.List;

public interface ExpectedValueFilter {

    /**
     * Returns a list containing all of the expected values managed by this
     * filter.
     */
    public List<ExpectedValue> allExpectedValues();

    /**
     * Returns an iterable of the expected values that could apply to the
     * current parcel; i.e. filtering out any targets that the current parcel
     * cannot possibly contribute to. Must be order-consistent between
     * invocations on the same parcel.
     */
    public Iterable<ExpectedValue> applicableExpectedValues();
}
