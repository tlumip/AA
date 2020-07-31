package com.hbaspecto.pecas.sd.estimation;

public class SpaceTypeTAZTarget extends StandardConstructionTarget {
    public static final String NAME = "taztarg";
    private final int taz;

    public SpaceTypeTAZTarget(int zone, int... spaceTypes) {
        super(SpaceTypeFilter.of(spaceTypes), GeographicFilter.inTaz(zone));
        taz = zone;
    }

    public int getZone() {
        return taz;
    }

    public int[] getSpaceTypes() {
        return spaceTypeFilter().acceptedSpaceTypeNumbers();
    }

    @Override
    public String getName() {
        return joinHyphens(NAME, taz, getSpaceTypes());
    }
}
