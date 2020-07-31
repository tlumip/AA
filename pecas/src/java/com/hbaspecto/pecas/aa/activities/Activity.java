package com.hbaspecto.pecas.aa.activities;

import com.hbaspecto.pecas.zones.Zone;

/**
 * Abstract representation of an AA activity.
 * 
 * @author Graham Hill
 * @param <Z> The type of zone that this activity allocates over
 */
public interface Activity<Z extends Zone> {
    public double getQuantity(Z zone);
}
