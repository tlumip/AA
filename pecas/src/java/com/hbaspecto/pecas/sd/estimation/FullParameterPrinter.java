package com.hbaspecto.pecas.sd.estimation;

import java.util.LinkedHashMap;
import java.util.Map;

import com.hbaspecto.discreteChoiceModelling.Coefficient;

public class FullParameterPrinter implements ParameterPrinter {

    @Override
    public String asString(Coefficient param) {
        String name = param.getType().getTypeName();
        if (param instanceof DensityShapingFunctionParameter) {
            DensityShapingFunctionParameter dsfp = (DensityShapingFunctionParameter) param;
            return String.format("%s(to %d step %d)", name, dsfp.getSpacetype(),
                    dsfp.getStepPointNumber());
        } else if (param instanceof SpaceTypeCoefficient) {
            SpaceTypeCoefficient stc = (SpaceTypeCoefficient) param;
            String field = stc.getType().isTo() ? "to" : "from";
            return String.format("%s(%s %d)", name, field, stc.getSpacetype());
        } else if (param instanceof TransitionConstant) {
            TransitionConstant tc = (TransitionConstant) param;
            return String.format("%s(from %d to %d)", name,
                    tc.getOldSpaceType(), tc.getNewSpaceType());
        } else if (param instanceof TazGroupCoefficient) {
            TazGroupCoefficient tgc = (TazGroupCoefficient) param;
            return String.format("%s(tazgroup %d)", name, tgc.getTazGroup());
        } else if (param instanceof SpaceTypeTazGroupCoefficient) {
            SpaceTypeTazGroupCoefficient tgc = (SpaceTypeTazGroupCoefficient) param;
            return String.format("%s(to %d tazgroup %d)", name,
                    tgc.getSpacetype(), tgc.getTazGroup());
        } else {
            return "UnknownParameter";
        }
    }

    @Override
    public Map<Field, String> asFields(Coefficient param) {
        Map<Field, String> result = new LinkedHashMap<>();
        result.put(Field.string(TablePriorReader.PARAM_TYPE_COL),
                param.getType().getTypeName());
        if (param instanceof DensityShapingFunctionParameter) {
            DensityShapingFunctionParameter dsfp = (DensityShapingFunctionParameter) param;
            result.put(Field.number(TablePriorReader.TO_SPACE_TYPE_COL),
                    String.valueOf(dsfp.getSpacetype()));
            result.put(Field.number(TablePriorReader.STEP_POINT_NUMBER_COL),
                    String.valueOf(dsfp.getStepPointNumber()));
        } else if (param instanceof SpaceTypeCoefficient) {
            SpaceTypeCoefficient stc = (SpaceTypeCoefficient) param;
            String field = stc.getType().isTo()
                    ? TablePriorReader.TO_SPACE_TYPE_COL
                    : TablePriorReader.FROM_SPACE_TYPE_COL;
            result.put(Field.number(field), String.valueOf(stc.getSpacetype()));
        } else if (param instanceof TransitionConstant) {
            TransitionConstant tc = (TransitionConstant) param;
            result.put(Field.number(TablePriorReader.FROM_SPACE_TYPE_COL),
                    String.valueOf(tc.getOldSpaceType()));
            result.put(Field.number(TablePriorReader.TO_SPACE_TYPE_COL),
                    String.valueOf(tc.getNewSpaceType()));
        } else if (param instanceof TazGroupCoefficient) {
            TazGroupCoefficient tgc = (TazGroupCoefficient) param;
            result.put(Field.number(TablePriorReader.TAZ_GROUP_COL),
                    String.valueOf(tgc.getTazGroup()));
        } else if (param instanceof SpaceTypeTazGroupCoefficient) {
            SpaceTypeTazGroupCoefficient tgc = (SpaceTypeTazGroupCoefficient) param;
            result.put(Field.number(TablePriorReader.TO_SPACE_TYPE_COL),
                    String.valueOf(tgc.getSpacetype()));
            result.put(Field.number(TablePriorReader.TAZ_GROUP_COL),
                    String.valueOf(tgc.getTazGroup()));
        } else {
            result.clear();
            result.put(Field.string(TablePriorReader.PARAM_TYPE_COL),
                    "UnknownParameter");
        }
        return result;
    }

}
