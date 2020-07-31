package com.hbaspecto.pecas.sd.estimation;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public interface TargetPrinter {

    /**
     * Returns a string representation of the specified target.
     */
    public String asString(EstimationTarget target);

    /**
     * Returns a representation of the specified target as an ordered map
     * between field definitions and field values.
     */
    public Map<Field, String> asFields(EstimationTarget target);

    /**
     * Finds a common list of fields that can be used to hold all of the
     * fields in the {@code asFields} representations of the specified targets.
     */
    public default List<Field> getCommonFields(List<EstimationTarget> targets) {
        Set<Field> header = new LinkedHashSet<>();
        for (EstimationTarget target : targets) {
            header.addAll(asFields(target).keySet());
        }
        return new ArrayList<>(header);
    }
    
    public default List<String> adaptToFields(EstimationTarget target, List<Field> fields) {
        List<String> result = new ArrayList<>();
        Map<Field, String> targetFields = asFields(target);
        for (Field field : fields) {
            result.add(targetFields.getOrDefault(field, field.defaultValue()));
        }
        return result;
    }
}
