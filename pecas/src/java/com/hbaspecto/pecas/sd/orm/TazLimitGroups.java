package com.hbaspecto.pecas.sd.orm;

import java.util.HashMap;

import org.apache.log4j.Logger;

import simpleorm.sessionjdbc.SSessionJdbc;

/**
 * Business rules class for table taz_limit_groups.
 * 
 * @author Graham Hill
 */
@SuppressWarnings("serial")
public class TazLimitGroups extends TazLimitGroups_gen {
	
	static Logger logger = Logger.getLogger(TazLimitGroups.class);

    private HashMap<Integer, Double> currentSpaceByTazGroup = new HashMap<Integer, Double>();
    private HashMap<Integer, Double> minSpaceByTazGroup = new HashMap<Integer, Double>();
    private HashMap<Integer, Double> maxSpaceByTazGroup = new HashMap<Integer, Double>();

    private static HashMap<Integer, TazLimitGroups> tazLimitGroupsHash = new HashMap<Integer, TazLimitGroups>();

    /**
     * Retrieves the TAZ limit space type group record for a given space type
     * group ID.
     * 
     * @param spaceGroupID The group ID
     * @return The record, or null if the record does not exist
     */
    public static TazLimitGroups getTazLimitGroupsByID(int spaceGroupID) {
        TazLimitGroups theOne = tazLimitGroupsHash.get(spaceGroupID);
        if (theOne == null) {
            SSessionJdbc session = SSessionJdbc.getThreadLocalSession();
            theOne = session.find(TazLimitGroups.meta, spaceGroupID);
            if (theOne != null)
                tazLimitGroupsHash.put(spaceGroupID, theOne);
        }
        return theOne;
    }

    /**
     * Specifies the current quantity of space from this TAZ limit space type
     * group on the given TAZ group. This may be more than the maximum or less
     * than the minimum.
     * 
     * @param tazGroup The TAZ group
     * @param existingAmount The existing amount of space in the TAZ group
     */
    public void setExistingFloorspace(int tazGroup, double existingAmount) {
        currentSpaceByTazGroup.put(tazGroup, existingAmount);
    }

    /**
     * Imposes a limit on the amount of space in this TAZ limit space type group
     * that is allowed in the given TAZ group. It is legal for maximumAmount to
     * be less than existingAmount - this signifies that the zone is already
     * over the limit. Any attempts to add more space from this group will be
     * denied unless enough space is demolished to bring the amount back under
     * capacity.
     * 
     * @param tazGroup The TAZ group where the limit is being imposed
     * @param maximumAmount The maximum amount of space in this space group
     *            allowed in the TAZ group
     * @throws IllegalArgumentException if there is already a minimum space
     *             imposed that is greater than the specified maximum
     */
    public void setFloorspaceMaximum(int tazGroup, double maximumAmount) {
        Double minimumAmount = minSpaceByTazGroup.get(tazGroup);
        if (minimumAmount != null && minimumAmount > maximumAmount)
            throw new IllegalArgumentException("Maximum " + maximumAmount
                    + " greater than minimum " + minimumAmount);
        maxSpaceByTazGroup.put(tazGroup, maximumAmount);
    }

    /**
     * Imposes a required minimum on the amount of space in this TAZ limit space
     * type group that must exist in the given TAZ group. If the existing space
     * is less than {@code minimumAmount}, then SD will ensure that enough space
     * is built to meet the minimum requirement.
     * 
     * @param tazGroup The TAZ group where the minimum is being imposed
     * @param maximumAmount The minimum amount of space in this space group that
     *            must be in the TAZ group
     * @throws IllegalArgumentException if there is already a maximum space
     *             imposed that is less than the specified minimum
     */
    public void setFloorspaceMinimum(int tazGroup, double minimumAmount) {
        Double maximumAmount = maxSpaceByTazGroup.get(tazGroup);
        if (maximumAmount != null && minimumAmount > maximumAmount)
            throw new IllegalArgumentException("Minimum " + minimumAmount
                    + " greater than maximum " + maximumAmount);
        minSpaceByTazGroup.put(tazGroup, minimumAmount);
    }

    /**
     * Checks the maximum amount of space in this TAZ limit space type group
     * that can legally be added to the specified TAZ group. This may be
     * negative if there is already more space in the TAZ group than allowed.
     * <p>
     * Always returns positive infinity if the specified TAZ does not have a
     * maximum for this group.
     * 
     * @param taz The TAZ group where the limit is being checked
     * @return The amount of space that can be added
     */
    public double allowedNewSpace(int tazGroup) {
        if (maxSpaceByTazGroup.containsKey(tazGroup))
            return maxSpaceByTazGroup.get(tazGroup)
                    - currentSpaceByTazGroup.get(tazGroup);
        else
            return Double.POSITIVE_INFINITY;
    }

    /**
     * Checks the minimum amount of space of this TAZ limit space type group
     * that must still be added to the specified TAZ group to fulfill the
     * requirement. This may be negative if there is already enough space in the
     * TAZ group to satisfy the requirement.
     * <p>
     * Always returns negative infinity if the specified TAZ does not have a
     * minimum for this group.
     * 
     * @param taz The TAZ group where the limit is being checked
     * @return The amount of space that must be added
     */
    public double requiredNewSpace(int tazGroup) {
        if (minSpaceByTazGroup.containsKey(tazGroup))
            return minSpaceByTazGroup.get(tazGroup)
                    - currentSpaceByTazGroup.get(tazGroup);
        else
            return Double.NEGATIVE_INFINITY;
    }

    /**
     * Records that the specified amount of space in this TAZ limit space type
     * group has been added to the specified TAZ group. Demolition should be
     * recorded by passing a negative value for <code>spaceAdded</code>. Adding
     * space is illegal if the amount is greater than {@code allowedNewSpace}.
     * <p>
     * This method has no effect if an initial existing space amount has never
     * been supplied.
     * 
     * @param taz The TAZ group where the space is being added or demolished
     * @param spaceAdded The amount of space added (negative for demolition)
     * @throws IllegalArgumentException if <code>spaceAdded</code> cannot
     *             legally be added to the specified TAZ.
     */
    public void recordSpaceChange(int tazGroup, double spaceAdded) {
    	if (currentSpaceByTazGroup.containsKey(tazGroup)) {
    		double spaceAllowed = allowedNewSpace(tazGroup);
    		if (spaceAdded > 0 && spaceAdded > spaceAllowed) {
    			logger.error("Tried to add " + spaceAdded
    					+ " " + this.get_TazLimitGroupName() + " to TAZ group "
    					+ tazGroup + ", only " + spaceAllowed + " allowed");
    		} 
    		currentSpaceByTazGroup.put(tazGroup,
    				currentSpaceByTazGroup.get(tazGroup) + spaceAdded);

    	}
    }
}
