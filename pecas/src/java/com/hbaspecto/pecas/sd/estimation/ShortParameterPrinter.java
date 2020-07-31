package com.hbaspecto.pecas.sd.estimation;

import java.util.HashMap;
import java.util.Map;

import com.hbaspecto.discreteChoiceModelling.Coefficient;

public class ShortParameterPrinter implements ParameterPrinter {
    
    @Override
    public String asString(Coefficient param) {
        return param.getName();
    }

    @Override
    public Map<Field, String> asFields(Coefficient param) {
        Map<Field, String> result = new HashMap<>();
        result.put(Field.string("Parameter"), param.getName());
        return result;
    }
}
