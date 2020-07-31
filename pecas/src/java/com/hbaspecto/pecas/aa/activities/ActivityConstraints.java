package com.hbaspecto.pecas.aa.activities;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import com.hbaspecto.pecas.zones.Zone;

/**
 * Constraints on the amount of each activity that must exist in each zone.
 */
public class ActivityConstraints<Z extends Zone, A extends Activity<Z>> {
    private final Map<Z, Map<A, Double>> constraints = new HashMap<>();
    
    /**
     * Adds a constraint on a specific activity in a specific zone. Overwrites
     * any existing constraint on that activity in that zone.
     */
    public void constrain(Z zone, A act, double constraint) {
        if (!constraints.containsKey(zone)) {
            constraints.put(zone, new HashMap<A, Double>());
        }
        constraints.get(zone).put(act, constraint);
    }
    
    /**
     * Removes the constraint, if any, from the specified activity in the
     * spcified zone.
     */
    public void unconstrain(Z zone, A act) {
        if (constraints.containsKey(zone)) {
            constraints.get(zone).remove(act);
        }
    }
    
    /**
     * Returns an unmodifiable view of the activity constraints
     */
    public Map<Z, Map<A, Double>> constraints() {
        Map<Z, Map<A, Double>> result = new HashMap<>();
        for (Map.Entry<Z, Map<A, Double>> entry : constraints.entrySet()) {
            result.put(entry.getKey(), Collections.unmodifiableMap(entry.getValue()));
        }
        return Collections.unmodifiableMap(result);
    }
    
    /**
     * Checks whether the specified activity is constrained in the specified
     * zone.
     */
    public boolean isConstrained(Z zone, A act) {
        return constraints.containsKey(zone)
                && constraints.get(zone).containsKey(act);
    }

    /**
     * Retrieves the constraint on the specified activity in the specified zone.
     * Throws an {@code IllegalArgumentException} if that combination is not
     * constrained.
     */
    public double getConstraint(Z zone, A act) {
        Map<A, Double> zoneMap = constraints.get(zone);
        if (zoneMap != null) {
            Double result = zoneMap.get(act);
            if (result != null) {
                return result;
            }
        }
        throw new IllegalArgumentException(
                "No constraint for activity " + act + " in zone " + zone);
    }

    /**
     * Retrieves the constraint on the specified activity in the specified zone,
     * or the specified default value if that combination is not constrained.
     */
    public double getConstraintDefault(Z zone, A act, double def) {
        if (isConstrained(zone, act)) {
            return getConstraint(zone, act);
        } else {
            return def;
        }
    }
}
