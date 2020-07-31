package com.hbaspecto.pecas.aa.activities;

import com.hbaspecto.pecas.zones.Zone;

/**
 * An activity that can have constraints applied to its quantities.
 * 
 * @author Graham Hill
 * @param <Z> The type of zone that this activity allocates over
 */
public interface ConstrainableActivity<Z extends Zone> extends Activity<Z> {
    /**
     * Applies a constraint to this activity in the specified zone.
     */
    public void constrain(Z zone, double amount);

    /**
     * Removes the constraint (if any) from this activity in the specified zone.
     */
    public void unconstrain(Z zone);

    /**
     * Tests if this activity is constrained in the specified zone.
     */
    public boolean isConstrained(Z zone);

    /**
     * Retrieves the constraint on this activity in the specified zone. Throws
     * an {@code IllegalArgumentException} if this activity is not constrained
     * in the specified zone.
     */
    public double constrainedAmount(Z zone);
}
