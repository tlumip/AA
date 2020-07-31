package com.hbaspecto.pecas.sd.estimation;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import com.pb.common.datafile.TableDataSet;

public class CorrelationTableReader<T> {
    private static final String CORREL_TABLE_GROUP_COL = "CorrelationGroup";
    private static final String CORREL_TABLE_COEFF_COL = "CorrelationCoefficient";

    private Map<KeyPair<T>, Float> correl;
    
    public CorrelationTableReader(TableDataSet correlTable, Map<T, ? extends Row> sourceRows) {
        correl = new HashMap<>();

        Map<String, Float> groupCoeffs = new HashMap<>();

        for (int i = 1; i <= correlTable.getRowCount(); i++) {
            CorrelRow row = new CorrelRow(correlTable, i);
            groupCoeffs.put(row.group(), row.correlation());
        }

        Map<String, List<T>> keysByGroup = new HashMap<>();

        for (Map.Entry<T, ? extends Row> entry : sourceRows.entrySet()) {
            T key = entry.getKey();
            Row row = entry.getValue();
            Set<String> groups = parseCorrelationGroups(row.correlationGroups());
            if (!groups.isEmpty()) {
                for (String group : groups) {
                    if (!keysByGroup.containsKey(group)) {
                        keysByGroup.put(group, new ArrayList<>());
                    }
                    keysByGroup.get(group).add(key);
                }
            }
        }

        for (Map.Entry<String, List<T>> entry : keysByGroup
                .entrySet()) {
            String group = entry.getKey();
            List<T> keys = entry.getValue();
            for (int i = 0; i < keys.size(); i++) {
                for (int j = 0; j < i; j++) {
                    KeyPair<T> pair = new KeyPair<>(keys.get(i),
                            keys.get(j));
                    if (correl.containsKey(pair)) {
                        throw new IllegalArgumentException("Parameters "
                                + keys.get(i) + " and " + keys.get(j)
                                + " share more than one correlation group");
                    }
                    correl.put(pair, groupCoeffs.get(group));
                }
            }
        }
    }
    
    private Set<String> parseCorrelationGroups(String text) {
        if (text.toLowerCase().equals("none")) {
            return Collections.emptySet();
        } else {
            return new HashSet<>(
                    Arrays.asList(text.split(Pattern.quote("|"))));
        }
    }

    public double getCorrelation(T key1, T key2) {
        KeyPair<T> pair = new KeyPair<>(key1, key2);
        if (correl.containsKey(pair)) {
            return correl.get(pair);
        } else {
            return 0;
        }
    }
    
    public interface Row {
        public String correlationGroups();
    }

    private class CorrelRow {
        private TableDataSet table;
        private int rowNumber;
        private int groupCol;
        private int correlationCol;

        private CorrelRow(TableDataSet table, int rowNumber) {
            this.table = table;
            this.rowNumber = rowNumber;
            groupCol = table.checkColumnPosition(CORREL_TABLE_GROUP_COL);
            correlationCol = table.checkColumnPosition(CORREL_TABLE_COEFF_COL);
        }

        private String group() {
            return table.getStringValueAt(rowNumber, groupCol);
        }

        private float correlation() {
            return table.getValueAt(rowNumber, correlationCol);
        }
    }

    // An unordered pair of keys
    private static class KeyPair<T> {
        private T key1;
        private T key2;

        private KeyPair(T key1, T key2) {
            this.key1 = key1;
            this.key2 = key2;
        }

        @Override
        public boolean equals(Object o) {
            if (o == this) {
                return true;
            }
            if (!(o instanceof KeyPair)) {
                return false;
            }
            KeyPair<?> p = (KeyPair<?>) o;
            return (key1 == p.key1 && key2 == p.key2)
                    || (key1 == p.key2 && key2 == p.key1);
        }

        @Override
        public int hashCode() {
            int hc1 = System.identityHashCode(key1);
            int hc2 = System.identityHashCode(key2);
            if (hc1 > hc2) {
                int tmp = hc1;
                hc1 = hc2;
                hc2 = tmp;
            }
            return 31 * hc1 + hc2;
        }
    }
}
