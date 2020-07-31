package com.hbaspecto.pecas.sd.estimation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.common.collect.Iterables;
import com.hbaspecto.pecas.land.LandInventory;
import com.hbaspecto.pecas.land.Tazs;

public class ByZonePrefilter implements ExpectedValueFilter {
    
    private final LandInventory land;
    
    private final List<ExpectedValue> allEvs = new ArrayList<>();
    // Cache which EVs are restricted to particular TAZs
    private final Map<Tazs, List<ExpectedValue>> evsByTaz = new HashMap<>();
    // And these ones are not restricted to particular TAZs
    private final List<ExpectedValue> unrestrictedEvs = new ArrayList<>();

    public ByZonePrefilter(List<ExpectedValue> eVals, LandInventory land) {
        this.land = land;
        allEvs.addAll(eVals);
        for (ExpectedValue ev : eVals) {
            Collection<Tazs> applicableTazs = ev.applicableTazs();
            if (applicableTazs.isEmpty()) {
                unrestrictedEvs.add(ev);
            } else {
                for (Tazs taz : applicableTazs) {
                    if (!evsByTaz.containsKey(taz)) {
                        evsByTaz.put(taz, new ArrayList<>());
                    }
                    evsByTaz.get(taz).add(ev);
                }
            }
        }
    }

    @Override
    public List<ExpectedValue> allExpectedValues() {
        return Collections.unmodifiableList(allEvs);
    }
    
    @Override
    public Iterable<ExpectedValue> applicableExpectedValues() {
        Tazs taz = Tazs.getTazRecord(land.getTaz());
        return Iterables.concat(evsByTaz.getOrDefault(taz, Collections.emptyList()), unrestrictedEvs);
    }

}
