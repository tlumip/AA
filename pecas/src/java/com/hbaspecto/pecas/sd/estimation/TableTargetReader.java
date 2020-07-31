package com.hbaspecto.pecas.sd.estimation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import com.pb.common.datafile.TableDataSet;

public class TableTargetReader implements TargetReader {

    // Target type names
    static final String TOTAL_BUILT_TARGET = "TotalBuilt";
    static final String RENOVATION_TARGET = "Renovation";
    static final String ADDITION_TARGET = "Addition";
    static final String DEMOLITION_TARGET = "Demolition";
    static final String REDEVELOPMENT_TARGET = "Redevelopment";
    static final String TAZ_TARGET = "TazTotalBuilt";
    static final String LUZ_TARGET = "LuzTotalBuilt";
    static final String TAZ_GROUP_TARGET = "TazGroupTotalBuilt";
    static final String AD_HOC_TAZ_GROUP_TARGET = "CustomTazGroupTotalBuilt";
    static final String FAR_TARGET = "AverageFar";

    // Column names
    static final String TARGET_TYPE_COL = "TargetType";
    static final String SPACE_TYPE_COL = "SpaceType";
    static final String SPACE_GROUP_COL = "SpaceTypeGroup";
    static final String TAZ_COL = "TazNumber";
    static final String LUZ_COL = "LuzNumber";
    static final String TAZ_GROUP_COL = "TazGroup";
    private static final String VALUE_COL = "TargetValue";
    private static final String TOLERANCE_COL = "Tolerance";
    private static final String CORRELATION_GROUPS_COL = "CorrelationGroups";

    private static final String SG_GROUP_COL = "GroupName";
    private static final String SG_SPACE_TYPE_COL = "SpaceType";
    private static final String TG_GROUP_COL = "TazGroup";
    private static final String TG_TAZ_COL = "Taz";

    private List<EstimationTarget> targets;
    private Map<EstimationTarget, TargetRow> rows;

    private Map<String, List<Integer>> spaceGroups;
    private Map<Integer, List<Integer>> tazGroups;

    private CorrelationTableReader<EstimationTarget> correlReader;

    public static Builder reader(TableDataSet targetTable) {
        return new Builder(targetTable);
    }

    private TableTargetReader(Builder builder) {
        spaceGroups = new HashMap<>();
        TableDataSet groupTable = builder.spaceGroupTable;
        if (groupTable != null) {
            int groupCol = groupTable.checkColumnPosition(SG_GROUP_COL);
            int typeCol = groupTable.checkColumnPosition(SG_SPACE_TYPE_COL);
            for (int i = 1; i <= groupTable.getRowCount(); i++) {
                String group = groupTable.getStringValueAt(i, groupCol);
                int type = (int) groupTable.getValueAt(i, typeCol);
                if (!spaceGroups.containsKey(group)) {
                    spaceGroups.put(group, new ArrayList<>());
                }
                spaceGroups.get(group).add(type);
            }
        }

        tazGroups = new HashMap<>();
        groupTable = builder.tazGroupTable;
        if (groupTable != null) {
            int groupCol = groupTable.checkColumnPosition(TG_GROUP_COL);
            int tazCol = groupTable.checkColumnPosition(TG_TAZ_COL);
            for (int i = 1; i <= groupTable.getRowCount(); i++) {
                int group = (int) groupTable.getValueAt(i, groupCol);
                int taz = (int) groupTable.getValueAt(i, tazCol);
                if (!tazGroups.containsKey(group)) {
                    tazGroups.put(group, new ArrayList<>());
                }
                tazGroups.get(group).add(taz);
            }
        }

        targets = new ArrayList<>();
        rows = new IdentityHashMap<>();
        TableDataSet targetTable = builder.targetTable;
        for (int i = 1; i <= targetTable.getRowCount(); i++) {
            TargetRow row = new TargetRow(targetTable, i);
            EstimationTarget target = target(row.desc);
            target.setTargetValue(row.value());
            targets.add(target);
            rows.put(target, row);
        }

        if (builder.correlTable != null) {
            correlReader = new CorrelationTableReader<>(builder.correlTable,
                    rows);
        }
    }

    private EstimationTarget target(TargetDesc desc) {
        String name = desc.name();
        if (name.equalsIgnoreCase(TOTAL_BUILT_TARGET)) {
            return new SpaceTypeTotalTarget(desc.spaceTypes());
        } else if (name.equalsIgnoreCase(RENOVATION_TARGET)) {
            return new SpaceGroupRenovationTarget(desc.spaceTypes());
        } else if (name.equalsIgnoreCase(ADDITION_TARGET)) {
            return new AdditionIntoSpaceTypesTarget(desc.spaceTypes());
        } else if (name.equalsIgnoreCase(DEMOLITION_TARGET)) {
            return new SpaceGroupDemolitionTarget(desc.spaceTypes());
        } else if (name.equalsIgnoreCase(REDEVELOPMENT_TARGET)) {
            return new RedevelopmentIntoSpaceTypeTarget(desc.spaceTypes());
        } else if (name.equalsIgnoreCase(TAZ_TARGET)) {
            return new SpaceTypeTAZTarget(desc.taz(), desc.spaceTypes());
        } else if (name.equalsIgnoreCase(LUZ_TARGET)) {
            return new SpaceTypeLUZTarget(desc.luz(), desc.spaceTypes());
        } else if (name.equalsIgnoreCase(TAZ_GROUP_TARGET)) {
            return new SpaceTypeTazGroupTarget(desc.tazGroup(),
                    desc.spaceTypes());
        } else if (name.equalsIgnoreCase(AD_HOC_TAZ_GROUP_TARGET)) {
            return new SpaceTypeAdHocTazGroupTarget(desc.tazGroup(),
                    new HashSet<>(getTazGroup(desc)),
                    desc.spaceTypes());
        } else if (name.equalsIgnoreCase(FAR_TARGET)) {
            return new SpaceTypeIntensityTarget(desc.spaceTypes());
        } else {
            throw new IllegalTargetType(desc.rowNumber, desc.name());
        }
    }
    
    private List<Integer> getTazGroup(TargetDesc desc) {
        List<Integer> group = tazGroups.get(desc.tazGroup());
        if (group == null) {
            throw new UndefinedGroup(desc.rowNumber, "TAZ", desc.tazGroup());
        }
        return group;
    }

    @Override
    public List<EstimationTarget> targets() {
        return new ArrayList<>(targets);
    }

    @Override
    public double variance(EstimationTarget target) {
        double stdev = rows.get(target).tolerance();
        return stdev * stdev;
    }

    @Override
    public double covariance(EstimationTarget target1,
            EstimationTarget target2) {
        if (target1 == target2) {
            return variance(target1);
        } else if (correlReader == null) {
            return 0;
        } else {
            double correl = correlReader.getCorrelation(target1, target2);
            if (correl == 0) {
                return 0;
            } else {
                double stdev1 = rows.get(target1).tolerance();
                double stdev2 = rows.get(target2).tolerance();
                return correl * stdev1 * stdev2;
            }
        }
    }

    @Override
    public double[][] variance(List<EstimationTarget> targets) {
        double[][] result = new double[targets.size()][];
        int i = 0;
        for (EstimationTarget target1 : targets) {
            result[i] = new double[targets.size()];
            int j = 0;
            for (EstimationTarget target2 : targets) {
                result[i][j] = covariance(target1, target2);
                j++;
            }
            i++;
        }
        return result;
    }

    private class TargetRow implements CorrelationTableReader.Row {
        private TableDataSet table;
        private int rowNumber;
        private TargetDesc desc;
        private int valueCol;
        private int toleranceCol;
        private int groupsCol;

        private TargetRow(TableDataSet table, int rowNumber) {
            this.table = table;
            this.rowNumber = rowNumber;

            desc = new TargetDesc(table, rowNumber);
            valueCol = table.checkColumnPosition(VALUE_COL);
            toleranceCol = table.checkColumnPosition(TOLERANCE_COL);
            groupsCol = table.getColumnPosition(CORRELATION_GROUPS_COL);
        }

        public double value() {
            return table.getValueAt(rowNumber, valueCol);
        }

        public double tolerance() {
            return table.getValueAt(rowNumber, toleranceCol);
        }

        @Override
        public String correlationGroups() {
            if (groupsCol < 0) {
                return "none";
            } else {
                return table.getStringValueAt(rowNumber, groupsCol);
            }
        }
    }

    private class TargetDesc {
        private TableDataSet table;
        private int rowNumber;

        private TargetDesc(TableDataSet table, int rowNumber) {
            this.table = table;
            this.rowNumber = rowNumber;
        }

        private String name() {
            return table.getStringValueAt(rowNumber, TARGET_TYPE_COL);
        }

        private int spaceType() {
            return checkNotZero((int) table.getValueAt(rowNumber, SPACE_TYPE_COL), SPACE_TYPE_COL);
        }

        private int[] spaceTypes() {
            int spaceGroupCol = table.getColumnPosition(SPACE_GROUP_COL);
            if (spaceGroupCol < 0) {
                return new int[] {spaceType()};
            } else {
                String groupName = table.getStringValueAt(rowNumber,
                        SPACE_GROUP_COL);
                if (groupName.isEmpty() || groupName.equalsIgnoreCase("none")) {
                    return new int[] {spaceType()};
                } else {
                    List<Integer> spaceTypes = spaceGroups.get(groupName);
                    if (spaceTypes == null) {
                        throw new SpaceGroupNotFound(rowNumber, groupName);
                    } else {
                        int[] result = new int[spaceTypes.size()];
                        for (int i = 0; i < spaceTypes.size(); i++) {
                            result[i] = spaceTypes.get(i);
                        }
                        return result;
                    }
                }
            }
        }

        private int taz() {
            return checkNotZero((int) table.getValueAt(rowNumber, TAZ_COL), TAZ_COL);
        }

        private int tazGroup() {
            return checkNotZero((int) table.getValueAt(rowNumber, TAZ_GROUP_COL), TAZ_GROUP_COL);
        }

        private int luz() {
            return checkNotZero((int) table.getValueAt(rowNumber, LUZ_COL), LUZ_COL);
        }
        
        private int checkNotZero(int value, String columnName) {
            if (value == 0) throw new NullEntryInRequiredColumn(value, columnName);
            return value;
        }
    }

    public static class Builder {
        private TableDataSet targetTable;
        private TableDataSet spaceGroupTable;
        private TableDataSet correlTable;
        private TableDataSet tazGroupTable;

        private Builder(TableDataSet targetTable) {
            this.targetTable = targetTable;
        }

        public TargetReader build() {
            return new TableTargetReader(this);
        }

        public Builder withSpaceGroups(TableDataSet groupTable) {
            this.spaceGroupTable = groupTable;
            return this;
        }

        public Builder withCorrelations(TableDataSet correlTable) {
            this.correlTable = correlTable;
            return this;
        }

        public Builder withTazGroups(TableDataSet groupTable) {
            this.tazGroupTable = groupTable;
            return this;
        }
    }
}
