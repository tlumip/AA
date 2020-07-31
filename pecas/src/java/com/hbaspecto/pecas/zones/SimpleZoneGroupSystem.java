package com.hbaspecto.pecas.zones;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class SimpleZoneGroupSystem<Z extends Zone> implements ZoneGroupSystem<Z> {
    private Map<Integer, ZoneGroup<Z>> groupsByNumber;
    private Map<Z, ZoneGroup<Z>> groupsByZone;
    
    public SimpleZoneGroupSystem(List<ZoneGroup<Z>> groups) {
        groupsByNumber = new TreeMap<>();
        groupsByZone = new HashMap<>();
        for (ZoneGroup<Z> group : groups) {
            groupsByNumber.put(group.groupNumber, group);
            for (Z zone : group.groupZones()) {
                groupsByZone.put(zone, group);
            }
        }
    }

    @Override
    public ZoneGroup<Z> getZoneGroupByNumber(int groupNumber) {
        return groupsByNumber.get(groupNumber);
    }

    @Override
    public ZoneGroup<Z> getGroupForZone(Z zone) {
        return groupsByZone.get(zone);
    }

    @Override
    public List<ZoneGroup<Z>> getAllGroups() {
        return new ArrayList<>(groupsByNumber.values());
    }
}
