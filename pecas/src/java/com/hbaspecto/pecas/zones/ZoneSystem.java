package com.hbaspecto.pecas.zones;

import java.util.List;

/**
 * This defines all the zones that appear in the model. No two zones may have
 * the same index or the same user number.
 * 
 * @author Graham Hill
 * 
 * @param <Z> The type of zone in this system
 */
public interface ZoneSystem<Z extends Zone> {
    public Z getZoneByIndex(int index);

    public Z getZoneByUserNumber(int userNumber);

    /**
     * Returns all the zones in the zone system, ordered by index.
     */
    public List<Z> getAllZones();
}
