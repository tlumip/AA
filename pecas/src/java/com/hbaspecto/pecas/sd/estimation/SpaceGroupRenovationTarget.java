package com.hbaspecto.pecas.sd.estimation;

public class SpaceGroupRenovationTarget extends StandardRenovationTarget {
    public static final String NAME = "renotarg";

    public SpaceGroupRenovationTarget(int... spaceTypes) {
        super(SpaceTypeFilter.of(spaceTypes), GeographicFilter.all());
    }

    public SpaceGroupRenovationTarget(String... pieces) {
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
