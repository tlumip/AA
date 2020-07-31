package com.hbaspecto.pecas.sd.estimation;

public class SpaceTypeLUZTarget extends StandardConstructionTarget {
    public static final String NAME = "luztarg";
    private final int luz;

    public SpaceTypeLUZTarget(int zone, int... spaceTypes) {
        super(SpaceTypeFilter.of(spaceTypes), GeographicFilter.inLuz(zone));
        luz = zone;
    }

    public int getZone() {
        return luz;
    }

    public int[] getSpaceTypes() {
        return spaceTypeFilter().acceptedSpaceTypeNumbers();
    }

    @Override
    public String getName() {
        return joinHyphens(NAME, luz, getSpaceTypes());
    }
}
