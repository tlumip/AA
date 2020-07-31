package com.hbaspecto.pecas.zones;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.hbaspecto.pecas.aa.activities.Activity;

/**
 * A group of zones that can be separately constrained
 * 
 * @author Graham Hill
 * @param <Z> The type of zone in the group
 */
public class ZoneGroup<Z extends Zone> {

    public final int groupNumber;
    public final String groupName;
    private final Set<Z> groupZones;
    private Map<Activity<Z>, Double> minimums = new HashMap<>();
    private Map<Activity<Z>, Double> maximums = new HashMap<>();

    public ZoneGroup(int groupNumber, String groupName,
            Collection<? extends Z> groupZones) {
        this.groupNumber = groupNumber;
        this.groupName = groupName;
        this.groupZones = Collections
                .unmodifiableSet(new LinkedHashSet<>(groupZones));
    }
    
    public List<Z> groupZones() {
        return new ArrayList<>(groupZones);
    }
    
    public boolean contains(Z zone) {
        return groupZones.contains(zone);
    }

    public double minimum(Activity<Z> act) {
        if (minimums.containsKey(act)) {
            return minimums.get(act);
        } else {
            return 0;
        }
    }

    public double maximum(Activity<Z> act) {
        if (maximums.containsKey(act)) {
            return maximums.get(act);
        } else {
            return Double.POSITIVE_INFINITY;
        }
    }

    public void setMinimum(Activity<Z> act, double minimum) {
        double maximum = maximum(act);
        checkBounds(minimum, maximum);
        minimums.put(act, minimum);
    }

    public void setMaximum(Activity<Z> act, double maximum) {
        double minimum = minimum(act);
        checkBounds(minimum, maximum);
        maximums.put(act, maximum);
    }

    public void setBounds(Activity<Z> act, double minimum, double maximum) {
        checkBounds(minimum, maximum);
        minimums.put(act, minimum);
        maximums.put(act, maximum);
    }
    
    private void checkBounds(double minimum, double maximum) {
        if (maximum < minimum) {
            throw new IllegalArgumentException(String.format(
                    "The zone group maximum must be at least as great as the minimum, "
                            + "but %.3g is smaller than %.3g", maximum, minimum));
        }
    }
    
    public Set<Activity<Z>> boundedActivities() {
        Set<Activity<Z>> result = new HashSet<>(minimums.keySet());
        result.addAll(maximums.keySet());
        return result;
    }
    
    @Override
    public String toString() {
        return String.format("Group %d \"%s\"", groupNumber, groupName);
    }
}
