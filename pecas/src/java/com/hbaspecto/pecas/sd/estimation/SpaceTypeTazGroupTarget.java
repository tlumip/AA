package com.hbaspecto.pecas.sd.estimation;

public class SpaceTypeTazGroupTarget extends StandardConstructionTarget {
    public static final String NAME = "tazgrouptarg";
    private final int groupNumber;

    public SpaceTypeTazGroupTarget(int groupNumber, int... spaceTypes) {
        super(SpaceTypeFilter.of(spaceTypes), GeographicFilter.inTazGroup(groupNumber));
        this.groupNumber = groupNumber;
    }

    public int getGroupNumber() {
        return groupNumber;
    }

    public int[] getSpaceTypes() {
        return spaceTypeFilter().acceptedSpaceTypeNumbers();
    }
    
    @Override
    public String getName() {
        return joinHyphens(NAME, groupNumber, getSpaceTypes());
    }
}
