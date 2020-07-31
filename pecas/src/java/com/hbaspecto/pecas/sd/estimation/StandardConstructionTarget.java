package com.hbaspecto.pecas.sd.estimation;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import com.hbaspecto.pecas.land.Tazs;

public class StandardConstructionTarget extends EstimationTarget
        implements ExpectedValue {

    private final SpaceTypeFilter stFilter;
    private final GeographicFilter geoFilter;
    private double modelledValue;
    private double[] derivs;

    public StandardConstructionTarget(SpaceTypeFilter stFilter,
            GeographicFilter geoFilter) {
        this.stFilter = stFilter;
        this.geoFilter = geoFilter;
    }

    public SpaceTypeFilter spaceTypeFilter() {
        return stFilter;
    }

    public GeographicFilter geographicFilter() {
        return geoFilter;
    }

    @Override
    public double getModelledValue() {
        return modelledValue;
    }

    @Override
    public double[] getDerivatives() {
        return Arrays.copyOf(derivs, derivs.length);
    }

    @Override
    public List<ExpectedValue> getAssociatedExpectedValues() {
        return Collections.singletonList(this);
    }

    @Override
    public String getName() {
        return "totalcons-" + stFilter + "-" + geoFilter;
    }

    @Override
    public boolean appliesToCurrentParcel() {
        return geoFilter.appliesToCurrentParcel();
    }
    
    @Override
    public Collection<Tazs> applicableTazs() {
        return geoFilter.applicableTazs();
    }

    @Override
    public double getModelledTotalNewValueForParcel(int spacetype,
            double expectedAddedSpace, double expectedNewSpace) {
        if (!geoFilter.appliesToCurrentParcel()) {
            return 0;
        }
        if (!stFilter.accepts(spacetype)) {
            return 0;
        }
        return expectedAddedSpace + expectedNewSpace;
    }

    @Override
    public double getModelledTotalNewDerivativeWRTAddedSpace(int spacetype,
            double expectedAddedSpace, double expectedNewSpace) {
        if (!geoFilter.appliesToCurrentParcel()) {
            return 0;
        }
        if (!stFilter.accepts(spacetype)) {
            return 0;
        }
        return 1;
    }

    @Override
    public double getModelledTotalNewDerivativeWRTNewSpace(int spacetype,
            double expectedAddedSpace, double expectedNewSpace) {
        if (!geoFilter.appliesToCurrentParcel()) {
            return 0;
        }
        if (!stFilter.accepts(spacetype)) {
            return 0;
        }
        return 1;
    }

    @Override
    public void setModelledValue(double value) {
        modelledValue = value;
    }

    @Override
    public void setDerivatives(double[] derivatives) {
        derivs = Arrays.copyOf(derivatives, derivatives.length);
    }

}
