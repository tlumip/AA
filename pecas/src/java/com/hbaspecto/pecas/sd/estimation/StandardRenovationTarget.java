package com.hbaspecto.pecas.sd.estimation;

import java.util.Collection;

import com.hbaspecto.pecas.land.Tazs;

public class StandardRenovationTarget extends RenovationTarget
        implements ExpectedValue {
    
    private final SpaceTypeFilter stFilter;
    private final GeographicFilter geoFilter;
    
    public StandardRenovationTarget(SpaceTypeFilter stFilter,
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
    public String getName() {
        return "renocons-" + stFilter + "-" + geoFilter;
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
    public double getModelledRenovateQuantityForParcel(int spacetype,
            double quantity) {
        if (!geoFilter.appliesToCurrentParcel()) {
            return 0;
        }
        if (!stFilter.accepts(spacetype)) {
            return 0;
        }
        return quantity;
    }

    @Override
    public double getModelledRenovateDerivativeForParcel(int spacetype,
            double quantity) {
        if (!geoFilter.appliesToCurrentParcel()) {
            return 0;
        }
        if (!stFilter.accepts(spacetype)) {
            return 0;
        }
        return 1;
    }

}
