package com.hbaspecto.pecas.zones;

import java.util.List;

/**
 * This defines all the zone groups that are defined for a particular type of zone.
 * @author Graham Hill
 * @param <Z> The type of zone in the zone groups
 */
public interface ZoneGroupSystem<Z extends Zone> {
    public ZoneGroup<Z> getZoneGroupByNumber(int groupNumber);
    
    public ZoneGroup<Z> getGroupForZone(Z zone);
    
    /**
     * Returns all the groups in the group system, ordered by group number.
     */
    public List<ZoneGroup<Z>> getAllGroups();
}