package com.hbaspecto.pecas.sd.estimation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import com.hbaspecto.discreteChoiceModelling.Coefficient;
import com.hbaspecto.discreteChoiceModelling.Coefficient.CoefficientType;
import com.pb.common.datafile.TableDataSet;

public class TablePriorReader implements PriorReader {

    private static final Map<String, CoefficientType> types = new HashMap<>();
    static {
        for (CoefficientType type : SpaceTypeCoefficient.Type.values()) {
            types.put(type.getTypeName().toLowerCase(), type);
        }
        for (CoefficientType type : DensityShapingFunctionParameter.Type.values()) {
            types.put(type.getTypeName().toLowerCase(), type);
        }
        CoefficientType type = TransitionConstant.Type.TYPE;
        types.put(type.getTypeName().toLowerCase(), type);
        type = TazGroupCoefficient.Type.CONSTRUCTION_CONST;
        types.put(type.getTypeName().toLowerCase(), type);
        type = SpaceTypeTazGroupCoefficient.Type.CONSTRUCTION_CONST;
        types.put(type.getTypeName().toLowerCase(), type);
    }

    // Column names
    static final String PARAM_TYPE_COL = "ParameterType";
    static final String FROM_SPACE_TYPE_COL = "FromSpaceType";
    static final String TO_SPACE_TYPE_COL = "ToSpaceType";
    static final String TAZ_GROUP_COL = "TazGroup";
    static final String STEP_POINT_NUMBER_COL = "StepPointNumber";
    private static final String MEAN_COL = "PriorMean";
    private static final String START_COL = "StartValue";
    private static final String TOLERANCE_COL = "Tolerance";
    private static final String CORRELATION_GROUPS_COL = "CorrelationGroups";

    private List<Coefficient> params;
    private Map<Coefficient, ParamRow> rows;

    private CorrelationTableReader<Coefficient> correlReader;

    public static Builder reader(TableDataSet priorTable) {
        return new Builder(priorTable);
    }

    private TablePriorReader(Builder builder) {
        params = new ArrayList<>();
        rows = new IdentityHashMap<>();
        for (int i = 1; i <= builder.priorTable.getRowCount(); i++) {
            ParamRow row = new ParamRow(builder.priorTable, i);
            Coefficient param = param(row.desc);
            params.add(param);
            rows.put(param, row);
        }

        if (builder.correlTable != null) {
            correlReader = new CorrelationTableReader<>(builder.correlTable,
                    rows);
        }
    }

    private static Coefficient param(ParamDesc desc) {
        String lname = desc.name().toLowerCase();
        CoefficientType type = types.get(lname);
        if (type instanceof SpaceTypeCoefficient.Type) {
            SpaceTypeCoefficient.Type stc = (SpaceTypeCoefficient.Type) type;
            int spacetype = stc.isTo() ? desc.toSpaceType()
                    : desc.fromSpaceType();
            return SpaceTypeCoefficient.getConstByType(stc, spacetype);
        } else if (type instanceof DensityShapingFunctionParameter.Type) {
            DensityShapingFunctionParameter.Type dsfp = (DensityShapingFunctionParameter.Type) type;
            return DensityShapingFunctionParameter.getConstByType(dsfp,
                    desc.toSpaceType(), desc.stepPointNumber());
        } else if (type instanceof TransitionConstant.Type) {
            return TransitionConstant.getCoeff(desc.fromSpaceType(),
                    desc.toSpaceType());
        } else if (type instanceof TazGroupCoefficient.Type) {
            return TazGroupCoefficient
                    .getConstructionConstant(desc.tazGroupNumber());
        } else if (type instanceof SpaceTypeTazGroupCoefficient.Type) {
            return SpaceTypeTazGroupCoefficient.getConstructionConstant(
                    desc.toSpaceType(), desc.tazGroupNumber());
        } else {
            throw new IllegalParameterType(desc.rowNumber, desc.name());
        }
    }

    @Override
    public List<Coefficient> parameters() {
        return new ArrayList<>(params);
    }

    @Override
    public double mean(Coefficient param) {
        return rows.get(param).mean();
    }

    @Override
    public double[] means(List<Coefficient> params) {
        double[] result = new double[params.size()];
        int i = 0;
        for (Coefficient param : params) {
            result[i] = mean(param);
            i++;
        }
        return result;
    }

    @Override
    public double startValue(Coefficient param) {
        return rows.get(param).start();
    }

    @Override
    public double[] startValues(List<Coefficient> params) {
        double[] result = new double[params.size()];
        int i = 0;
        for (Coefficient param : params) {
            result[i] = startValue(param);
            i++;
        }
        return result;
    }

    @Override
    public double variance(Coefficient param) {
        double stdev = rows.get(param).tolerance();
        return stdev * stdev;
    }

    @Override
    public double covariance(Coefficient param1, Coefficient param2) {
        if (param1 == param2) {
            return variance(param1);
        } else if (correlReader == null) {
            return 0;
        } else {
            double correl = correlReader.getCorrelation(param1, param2);
            if (correl == 0) {
                return 0;
            } else {
                double stdev1 = rows.get(param1).tolerance();
                double stdev2 = rows.get(param2).tolerance();
                return correl * stdev1 * stdev2;
            }
        }
    }

    @Override
    public double[][] variance(List<Coefficient> params) {
        double[][] result = new double[params.size()][];
        int i = 0;
        for (Coefficient param1 : params) {
            result[i] = new double[params.size()];
            int j = 0;
            for (Coefficient param2 : params) {
                result[i][j] = covariance(param1, param2);
                j++;
            }
            i++;
        }
        return result;
    }

    private class ParamRow implements CorrelationTableReader.Row {
        private TableDataSet table;
        private int rowNumber;
        private ParamDesc desc;
        private int meanCol;
        private int startCol;
        private int toleranceCol;
        private int groupsCol;

        private ParamRow(TableDataSet table, int rowNumber) {
            this.table = table;
            this.rowNumber = rowNumber;

            desc = new ParamDesc(table, rowNumber);
            meanCol = table.checkColumnPosition(MEAN_COL);
            startCol = table.checkColumnPosition(START_COL);
            toleranceCol = table.checkColumnPosition(TOLERANCE_COL);
            groupsCol = table.getColumnPosition(CORRELATION_GROUPS_COL);
        }

        private float mean() {
            return table.getValueAt(rowNumber, meanCol);
        }

        private float start() {
            return table.getValueAt(rowNumber, startCol);
        }

        private float tolerance() {
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

    private class ParamDesc {
        private TableDataSet table;
        private int rowNumber;

        private ParamDesc(TableDataSet table, int rowNumber) {
            this.table = table;
            this.rowNumber = rowNumber;
        }

        public int stepPointNumber() {
            return checkNotZero((int) table.getValueAt(rowNumber, STEP_POINT_NUMBER_COL), STEP_POINT_NUMBER_COL);
        }

        private String name() {
            return table.getStringValueAt(rowNumber, PARAM_TYPE_COL);
        }

        private int fromSpaceType() {
            return checkNotZero((int) table.getValueAt(rowNumber, FROM_SPACE_TYPE_COL), FROM_SPACE_TYPE_COL);
        }

        private int toSpaceType() {
            return checkNotZero((int) table.getValueAt(rowNumber, TO_SPACE_TYPE_COL), TO_SPACE_TYPE_COL);
        }

        public int tazGroupNumber() {
            return checkNotZero((int) table.getValueAt(rowNumber, TAZ_GROUP_COL), TAZ_GROUP_COL);
        }
        
        private int checkNotZero(int value, String columnName) {
            if (value == 0) throw new NullEntryInRequiredColumn(value, columnName);
            return value;
        }
    }

    public static class Builder {
        private TableDataSet priorTable;
        private TableDataSet correlTable;

        private Builder(TableDataSet priorTable) {
            this.priorTable = priorTable;
        }

        public PriorReader build() {
            return new TablePriorReader(this);
        }

        public Builder withCorrelations(TableDataSet correlTable) {
            this.correlTable = correlTable;
            return this;
        }
    }
}
