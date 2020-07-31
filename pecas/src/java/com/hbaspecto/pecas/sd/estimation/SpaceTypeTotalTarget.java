package com.hbaspecto.pecas.sd.estimation;

public class SpaceTypeTotalTarget extends StandardConstructionTarget {
    public static final String NAME = "tottarg";

    public SpaceTypeTotalTarget(int... spaceTypes) {
        super(SpaceTypeFilter.of(spaceTypes), GeographicFilter.all());
    }

    public SpaceTypeTotalTarget(String... pieces) {
        this(parseSpaceTypeStrings(pieces));
    }

    public int[] getSpaceTypes() {
        return spaceTypeFilter().acceptedSpaceTypeNumbers();
    }

    @Override
    public String getName() {
        return joinHyphens(NAME, getSpaceTypes());
    }
}
