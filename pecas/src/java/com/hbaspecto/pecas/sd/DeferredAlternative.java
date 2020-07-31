package com.hbaspecto.pecas.sd;

import com.hbaspecto.pecas.land.Tazs;

/**
 * A DevelopmentAlternative that has not been committed to yet. It wraps up
 * enough state to be able to perform the development action required by the
 * alternative at a later time.
 */
public interface DeferredAlternative {

    /**
     * Performs the development action
     */
    public void doDevelopment();

    /**
     * Returns the probability of the parcel choosing this alternative
     */
    public double probability();

    /**
     * Returns the priority of this alternative type; a higher priority
     * alternative will be performed in preference to a lower priority one if
     * the two have the same probability. The exact value is arbitrary as long
     * as each alternative type has a different one, so that deterministic
     * results are ensured if two alternatives have the same probability.
     */
    public int priority();

    /**
     * Returns the space type that is to be modified by this alternative
     */
    public SpaceTypesI activeType();

    /**
     * Tests whether this alternative primarily constructs space
     */
    public boolean isConstruction();

    /**
     * Tests whether this alternative primarily renovates space
     */
    public boolean isRenovation();

    /**
     * Returns the amount of change done by this alternative
     */
    public double amount();

    /**
     * Tries to force the amount of change done by this alternative to equal
     * {@code amount}. Returns true if this was successful, false if the
     * amount could not be forced because of this alternative's limitations.
     * If this returns true, subsequent calls to {@code amount()} will return
     * this method's argument; otherwise, the alternative is unchanged.
     */
    public boolean tryForceAmount(double amount);

    /**
     * Returns the PECAS parcel number of the parcel that the alternative
     * applies to
     */
    public long parcelNum();
    
    /**
     * Returns the TAZ that this alternative's parcel is in
     */
    public Tazs taz();
}
