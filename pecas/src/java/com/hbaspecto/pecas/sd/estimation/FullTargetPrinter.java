package com.hbaspecto.pecas.sd.estimation;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class FullTargetPrinter implements TargetPrinter {

    @Override
    public String asString(EstimationTarget target) {
        if (target instanceof SpaceTypeTotalTarget) {
            SpaceTypeTotalTarget t = (SpaceTypeTotalTarget) target;
            return String.format("%s(%s)", TableTargetReader.TOTAL_BUILT_TARGET,
                    typesToString(t.getSpaceTypes()));
        } else if (target instanceof SpaceGroupRenovationTarget) {
            SpaceGroupRenovationTarget t = (SpaceGroupRenovationTarget) target;
            return String.format("%s(%s)", TableTargetReader.RENOVATION_TARGET,
                    typesToString(t.getSpaceTypes()));
        } else if (target instanceof AdditionIntoSpaceTypesTarget) {
            AdditionIntoSpaceTypesTarget t = (AdditionIntoSpaceTypesTarget) target;
            return String.format("%s(%s)", TableTargetReader.ADDITION_TARGET,
                    typesToString(t.getSpaceTypes()));
        } else if (target instanceof SpaceGroupDemolitionTarget) {
            SpaceGroupDemolitionTarget t = (SpaceGroupDemolitionTarget) target;
            return String.format("%s(%s)", TableTargetReader.DEMOLITION_TARGET,
                    typesToString(t.getSpaceTypes()));
        } else if (target instanceof RedevelopmentIntoSpaceTypeTarget) {
            RedevelopmentIntoSpaceTypeTarget t = (RedevelopmentIntoSpaceTypeTarget) target;
            return String.format("%s(%s)",
                    TableTargetReader.REDEVELOPMENT_TARGET,
                    typesToString(t.getSpaceTypes()));
        } else if (target instanceof SpaceTypeTAZTarget) {
            SpaceTypeTAZTarget t = (SpaceTypeTAZTarget) target;
            return String.format("%s(%s taz %s)", TableTargetReader.TAZ_TARGET,
                    typesToString(t.getSpaceTypes()), t.getZone());
        } else if (target instanceof SpaceTypeLUZTarget) {
            SpaceTypeLUZTarget t = (SpaceTypeLUZTarget) target;
            return String.format("%s(%s luz %s)", TableTargetReader.LUZ_TARGET,
                    typesToString(t.getSpaceTypes()), t.getZone());
        } else if (target instanceof SpaceTypeTazGroupTarget) {
            SpaceTypeTazGroupTarget t = (SpaceTypeTazGroupTarget) target;
            return String.format("%s(%s tazgroup %s)",
                    TableTargetReader.TAZ_GROUP_TARGET,
                    typesToString(t.getSpaceTypes()), t.getGroupNumber());
        } else if (target instanceof SpaceTypeAdHocTazGroupTarget) {
            SpaceTypeAdHocTazGroupTarget t = (SpaceTypeAdHocTazGroupTarget) target;
            return String.format("%s(%s %s)",
                    TableTargetReader.AD_HOC_TAZ_GROUP_TARGET,
                    typesToString(t.getSpaceTypes()),
                    zonesToString(t.getGroup()));
        } else if (target instanceof SpaceTypeIntensityTarget) {
            SpaceTypeIntensityTarget t = (SpaceTypeIntensityTarget) target;
            return String.format("%s(%s)", TableTargetReader.FAR_TARGET,
                    typesToString(t.getSpaceTypes()));
        } else if (target instanceof StandardConstructionTarget) {
            StandardConstructionTarget t = (StandardConstructionTarget) target;
            return String.format("Construction(%s, %s)", t.spaceTypeFilter(),
                    t.geographicFilter());
        } else if (target instanceof StandardRenovationTarget) {
            StandardRenovationTarget t = (StandardRenovationTarget) target;
            return String.format("Renovation(%s, %s)", t.spaceTypeFilter(),
                    t.geographicFilter());
        } else {
            return "UnknownTarget";
        }
    }

    private String typesToString(int[] types) {
        String field = genTypesToString(types);
        if (types.length == 1) {
            return "type " + field;
        } else {
            return "types " + field;
        }
    }

    private String zonesToString(Set<Integer> zones) {
        String field = genZonesToString(zones);
        if (zones.size() == 1) {
            return "zone " + field;
        } else {
            return "zones " + field;
        }
    }

    @Override
    public Map<Field, String> asFields(EstimationTarget target) {
        Map<Field, String> result = new LinkedHashMap<>();
        String typeCol = TableTargetReader.TARGET_TYPE_COL;
        String sptypesCol = "SpaceTypes";
        if (target instanceof SpaceTypeTotalTarget) {
            SpaceTypeTotalTarget t = (SpaceTypeTotalTarget) target;
            result.put(Field.string(typeCol),
                    TableTargetReader.TOTAL_BUILT_TARGET);
            result.put(Field.string(sptypesCol),
                    typesToField(t.getSpaceTypes()));
        } else if (target instanceof SpaceGroupRenovationTarget) {
            SpaceGroupRenovationTarget t = (SpaceGroupRenovationTarget) target;
            result.put(Field.string(typeCol),
                    TableTargetReader.RENOVATION_TARGET);
            result.put(Field.string(sptypesCol),
                    typesToField(t.getSpaceTypes()));
        } else if (target instanceof AdditionIntoSpaceTypesTarget) {
            AdditionIntoSpaceTypesTarget t = (AdditionIntoSpaceTypesTarget) target;
            result.put(Field.string(typeCol),
                    TableTargetReader.ADDITION_TARGET);
            result.put(Field.string(sptypesCol),
                    typesToField(t.getSpaceTypes()));
        } else if (target instanceof SpaceGroupDemolitionTarget) {
            SpaceGroupDemolitionTarget t = (SpaceGroupDemolitionTarget) target;
            result.put(Field.string(typeCol),
                    TableTargetReader.DEMOLITION_TARGET);
            result.put(Field.string(sptypesCol),
                    typesToField(t.getSpaceTypes()));
        } else if (target instanceof RedevelopmentIntoSpaceTypeTarget) {
            RedevelopmentIntoSpaceTypeTarget t = (RedevelopmentIntoSpaceTypeTarget) target;
            result.put(Field.string(typeCol),
                    TableTargetReader.REDEVELOPMENT_TARGET);
            result.put(Field.string(sptypesCol),
                    typesToField(t.getSpaceTypes()));
        } else if (target instanceof SpaceTypeTAZTarget) {
            SpaceTypeTAZTarget t = (SpaceTypeTAZTarget) target;
            result.put(Field.string(typeCol), TableTargetReader.TAZ_TARGET);
            result.put(Field.string(sptypesCol),
                    typesToField(t.getSpaceTypes()));
            result.put(Field.number(TableTargetReader.TAZ_COL),
                    String.valueOf(t.getZone()));
        } else if (target instanceof SpaceTypeLUZTarget) {
            SpaceTypeLUZTarget t = (SpaceTypeLUZTarget) target;
            result.put(Field.string(typeCol), TableTargetReader.LUZ_TARGET);
            result.put(Field.string(sptypesCol),
                    typesToField(t.getSpaceTypes()));
            result.put(Field.number(TableTargetReader.LUZ_COL),
                    String.valueOf(t.getZone()));
        } else if (target instanceof SpaceTypeTazGroupTarget) {
            SpaceTypeTazGroupTarget t = (SpaceTypeTazGroupTarget) target;
            result.put(Field.string(typeCol),
                    TableTargetReader.TAZ_GROUP_TARGET);
            result.put(Field.string(sptypesCol),
                    typesToField(t.getSpaceTypes()));
            result.put(Field.number(TableTargetReader.TAZ_GROUP_COL),
                    String.valueOf(t.getGroupNumber()));
        } else if (target instanceof SpaceTypeAdHocTazGroupTarget) {
            SpaceTypeAdHocTazGroupTarget t = (SpaceTypeAdHocTazGroupTarget) target;
            result.put(Field.string(typeCol),
                    TableTargetReader.TAZ_GROUP_TARGET);
            result.put(Field.string(sptypesCol),
                    typesToField(t.getSpaceTypes()));
            result.put(Field.number(TableTargetReader.TAZ_GROUP_COL),
                    zonesToField(t.getGroup()));
        } else if (target instanceof SpaceTypeIntensityTarget) {
            SpaceTypeIntensityTarget t = (SpaceTypeIntensityTarget) target;
            result.put(Field.string(typeCol), TableTargetReader.FAR_TARGET);
            result.put(Field.string(sptypesCol),
                    typesToField(t.getSpaceTypes()));
        } else if (target instanceof StandardConstructionTarget) {
            StandardConstructionTarget t = (StandardConstructionTarget) target;
            result.put(Field.string(typeCol), "Construction");
            result.put(Field.string("SpaceTypes"),
                    typesToField(t.spaceTypeFilter().acceptedSpaceTypeNumbers()));
            result.put(Field.string(t.geographicFilter().groupType()),
                    String.valueOf(t.geographicFilter().groupNumber()));
        } else if (target instanceof StandardRenovationTarget) {
            StandardRenovationTarget t = (StandardRenovationTarget) target;
            result.put(Field.string(typeCol), "Renovation");
            result.put(Field.string("SpaceTypes"),
                    typesToField(t.spaceTypeFilter().acceptedSpaceTypeNumbers()));
            result.put(Field.string(t.geographicFilter().groupType()),
                    String.valueOf(t.geographicFilter().groupNumber()));
        } else {
            result.put(Field.string(typeCol), "UnknownTarget");
        }
        return result;
    }

    private String typesToField(int[] types) {
        return "[" + genTypesToString(types) + "]";
    }

    private String genTypesToString(int[] types) {
        String[] strings = new String[types.length];
        for (int i = 0; i < types.length; i++) {
            strings[i] = String.valueOf(types[i]);
        }
        return String.join("/", strings);
    }

    private String zonesToField(Set<Integer> zones) {
        return "[" + genZonesToString(zones) + "]";
    }

    private String genZonesToString(Set<Integer> zones) {
        return zones.stream().sorted().map((Integer n) -> n.toString())
                .collect(Collectors.joining("/"));
    }
}
