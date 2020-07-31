package com.hbaspecto.pecas.sd.estimation;

import java.util.Arrays;

import org.apache.log4j.Logger;

public class SpaceGroupDemolitionTarget extends DemolitionTarget
        implements ExpectedValue {
    static Logger logger = Logger.getLogger(SpaceGroupDemolitionTarget.class);

    private int[] spaceTypes;
    public static final String NAME = "demotarg";

    public SpaceGroupDemolitionTarget(int... spacetypes) {
        this.spaceTypes = Arrays.copyOf(spacetypes, spacetypes.length);
    }

    public SpaceGroupDemolitionTarget(String... pieces) {
        spaceTypes = parseSpaceTypeStrings(pieces);
    }

    public int[] getSpaceTypes() {
        return Arrays.copyOf(spaceTypes, spaceTypes.length);
    }

    @Override
    public boolean appliesToCurrentParcel() {
        return true;
    }

    @Override
    public String getName() {
        return joinHyphens(NAME, spaceTypes);
    }

    @Override
    public double getModelledDemolishQuantityForParcel(int checkSpaceType,
            double quantity) {
        return in(checkSpaceType, spaceTypes) ? quantity : 0;
    }

    @Override
    public double getModelledDemolishDerivativeForParcel(int checkSpaceType,
            double quantity) {
        return in(checkSpaceType, spaceTypes) ? checkSpaceType : 0;
    }

}
