package com.hbaspecto.pecas.sd.estimation;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.hbaspecto.discreteChoiceModelling.Coefficient;

public interface ParameterPrinter {
    
    /**
     * Returns a string representation of the specified parameter.
     */
    public String asString(Coefficient param);

    /**
     * Returns a representation of the specified parameter as an ordered map
     * between field definitions and field values.
     */
    public Map<Field, String> asFields(Coefficient param);
    
    public default List<Field> getCommonFields(List<Coefficient> params) {
        Set<Field> header = new LinkedHashSet<>();
        for (Coefficient param : params) {
            header.addAll(asFields(param).keySet());
        }
        return new ArrayList<>(header);
    }
    
    public default List<String> adaptToFields(Coefficient param, List<Field> fields) {
        List<String> result = new ArrayList<>();
        Map<Field, String> paramFields = asFields(param);
        for (Field field : fields) {
            result.add(paramFields.getOrDefault(field, field.defaultValue()));
        }
        return result;
    }
}
