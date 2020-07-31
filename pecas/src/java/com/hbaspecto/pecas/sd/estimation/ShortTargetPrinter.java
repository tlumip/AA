package com.hbaspecto.pecas.sd.estimation;

import java.util.HashMap;
import java.util.Map;

public class ShortTargetPrinter implements TargetPrinter {

    @Override
    public String asString(EstimationTarget target) {
        return target.getName();
    }

    @Override
    public Map<Field, String> asFields(EstimationTarget target) {
        Map<Field, String> result = new HashMap<>();
        result.put(Field.string("Target"), target.getName());
        return result;
    }
}
