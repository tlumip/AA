package com.hbaspecto.pecas.aa.control;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.hbaspecto.pecas.aa.activities.Activity;
import com.hbaspecto.pecas.aa.activities.ConstrainableActivity;
import com.hbaspecto.pecas.zones.Zone;
import com.hbaspecto.pecas.zones.ZoneGroup;
import com.hbaspecto.pecas.zones.ZoneGroupSystem;

/**
 * This class calculates adjustments to the amount of activity in each group of
 * zones so that they fall within specified bounds. To use this class, define a
 * system of groups of zones (as a {@code ZoneGroupSystem}, each specifying the
 * minimum and maximum amounts of each activity allowed in it. Then call the
 * {@code solve} method to find the new constraints to apply using the
 * {@code expectedAmount} method. The {@code isForced} method indicates whether
 * the forcer needed to force a particular activity in a particular zone group.
 * 
 * For each activity, this class tries to find a minimal set of zone groups that
 * it can force so that all bounds are met. It does not guarantee that the
 * solution is actually minimal, but it does guarantee that, as long as the
 * bounds are consistent with the activity totals, there is always at least one
 * zone group left unforced (i.e. there is always some elasticity left in the
 * system).
 * 
 * @author Graham Hill
 *
 * @param <Z> The type of zone this forcer works on
 */
public class ZoneGroupBoundForcer<Z extends Zone> {
    private static final double DEFAULT_TOLERANCE = 1e-7;

    private final ZoneGroupSystem<Z> system;
    private final Map<Activity<Z>, OneActivityForcer> forcers = new HashMap<>();
    private final double tolerance;

    /**
     * Constructs a forcer that operates on the specified activities and groups
     * of zones. The bounds must already be encoded in the group system. The
     * zone group bounds are considered to be satisfied if all activities are
     * within the specified tolerance of the bounds in all zone groups, as a
     * fraction of the relevant bound.
     */
    public ZoneGroupBoundForcer(List<? extends Activity<Z>> activities,
            ZoneGroupSystem<Z> groups, double tolerance) {
        system = groups;
        this.tolerance = tolerance;
        for (Activity<Z> activity : activities) {
            forcers.put(activity, new OneActivityForcer(activity));
        }
        if (tolerance <= 0) {
            throw new IllegalArgumentException(
                    "Tolerance must be positive, was " + tolerance);
        }
    }

    /**
     * Constructs a forcer that operates on the specified activities and groups
     * of zones. The bounds must already be encoded in the group system.
     */
    public ZoneGroupBoundForcer(List<? extends Activity<Z>> activities,
            ZoneGroupSystem<Z> groups) {
        this(activities, groups, DEFAULT_TOLERANCE);
    }

    /**
     * Performs one iteration of the solution. For each activity that does not
     * satisfy all of its bounds, this method will force exactly one of its zone
     * groups. It may also remove forcing from one or more zone groups.
     */
    public void doOneStep() {
        for (OneActivityForcer forcer : forcers.values()) {
            forcer.doOneStep();
        }
        if (isConverged()) {
            for (OneActivityForcer forcer : forcers.values()) {
                forcer.lockForcedGroups();
            }
        }
    }

    /**
     * Calls {@code doOneStep} until all activities are within the bounds in all
     * groups.
     */
    public void solve() {
        while (!isConverged()) {
            doOneStep();
        }
    }

    public boolean isConverged() {
        for (OneActivityForcer forcer : forcers.values()) {
            if (!forcer.converged) {
                return false;
            }
        }
        return true;
    }

    public List<ZoneGroup<Z>> groups() {
        return Collections.unmodifiableList(system.getAllGroups());
    }

    public boolean isForced(ZoneGroup<Z> group, Activity<Z> act) {
        return forcers.get(act).isForced(group);
    }

    /**
     * Checks whether the specified activity has been forced in the specified
     * zone group since the last time the activity amounts were updated.
     */
    public boolean isNewlyForced(ZoneGroup<Z> group, Activity<Z> act) {
        return forcers.get(act).isNewlyForced(group);
    }

    /**
     * Returns the amount of the specified activity that should exist in the
     * specified zone group if all adjustments are applied. If the
     * group-activity combination is forced, this will always be equal to either
     * the minimum or the maximum for that group-activity combination; otherwise
     * it will be somewhere within the bounds.
     */
    public double expectedAmount(ZoneGroup<Z> group, Activity<Z> act) {
        return forcers.get(act).expectedAmount(group);
    }

    /**
     * Returns the amount of the specified activity that should exist in the
     * specified zone group if all adjustments are applied EXCEPT the one for
     * this group-activity combination itself. For unforced combinations, this
     * is the same as {@code expectedAmount}.
     */
    double releasedAmount(ZoneGroup<Z> group, Activity<Z> act) {
        return forcers.get(act).releasedAmount(group);
    }

    /**
     * Causes this forcer to update its knowledge of the activity distributions.
     * This can even be used on a converged forcer to make it no longer
     * converged.
     */
    public void updateActivities() {
        for (OneActivityForcer forcer : forcers.values()) {
            forcer.updateActivity();
            forcer.checkConverged();
        }
    }

    // Works to force one activity independently of the others
    private class OneActivityForcer {
        private final Activity<Z> activity;
        private final Map<ZoneGroup<Z>, GroupStatus> progress = new HashMap<>();
        private double totalQuantity;
        private boolean converged = false;

        private OneActivityForcer(Activity<Z> act) {
            activity = act;
            totalQuantity = 0;
            for (ZoneGroup<Z> group : system.getAllGroups()) {
                double quantityInGroup = findQuantityInGroup(group);
                double constraintInGroup = findConstraintInGroup(group);
                progress.put(group,
                        new GroupStatus(quantityInGroup, constraintInGroup));
                totalQuantity += quantityInGroup;
            }
            checkConverged();
        }

        private void updateActivity() {
            totalQuantity = 0;
            for (ZoneGroup<Z> group : system.getAllGroups()) {
                double quantityInGroup = findQuantityInGroup(group);
                double constraintInGroup = findConstraintInGroup(group);
                GroupStatus status = progress.get(group);
                status.updateAmount(quantityInGroup, constraintInGroup);
                status.isNewlyForced = false;
                totalQuantity += quantityInGroup;
            }
        }

        private double findQuantityInGroup(ZoneGroup<Z> group) {
            double quantityInGroup = 0;
            for (Z zone : group.groupZones()) {
                quantityInGroup += activity.getQuantity(zone);
            }
            return quantityInGroup;
        }

        private double findConstraintInGroup(ZoneGroup<Z> group) {
            double constraintInGroup = 0;
            for (Z zone : group.groupZones()) {
                if (activity instanceof ConstrainableActivity) {
                    ConstrainableActivity<Z> cact = (ConstrainableActivity<Z>) activity;
                    if (cact.isConstrained(zone))
                        constraintInGroup += cact.constrainedAmount(zone);
                }
            }
            return constraintInGroup;
        }

        private Iterable<StatusEntry> progressEntries() {
            List<StatusEntry> result = new ArrayList<StatusEntry>();
            for (Map.Entry<ZoneGroup<Z>, GroupStatus> entry : progress
                    .entrySet()) {
                result.add(new StatusEntry(entry.getKey(), entry.getValue()));
            }
            return result;
        }

        private void doOneStep() {
            if (!converged) {
                ZoneGroup<Z> worst = findWorst();
                progress.get(worst).isForced = true;

                force(worst);
                restoreActivityTotal();
                findReleasedAmounts();

                releaseGroupsThatDoNotNeedForcing();
                restoreActivityTotal();
                findReleasedAmounts();

                checkConverged();
            }
        }

        private ZoneGroup<Z> findWorst() {
            double worstMismatch = 0;
            ZoneGroup<Z> worstGroup = null;

            for (StatusEntry e : progressEntries()) {
                double expectedAmount = e.status.expectedAmount;
                double minimumAmount = e.group.minimum(activity);
                double maximumAmount = e.group.maximum(activity);

                double mismatch = 0;
                if (expectedAmount < minimumAmount) {
                    mismatch = minimumAmount - expectedAmount;
                } else if (expectedAmount > maximumAmount) {
                    mismatch = expectedAmount - maximumAmount;
                }

                if (mismatch > worstMismatch) {
                    worstMismatch = mismatch;
                    worstGroup = e.group;
                }
            }

            return worstGroup;
        }

        private void force(ZoneGroup<Z> group) {
            GroupStatus status = progress.get(group);
            if (status.expectedAmount < group.minimum(activity)) {
                status.isForced = true;
                status.isNewlyForced = true;
                status.expectedAmount = group.minimum(activity);
            } else if (status.expectedAmount > group.maximum(activity)) {
                status.isForced = true;
                status.isNewlyForced = true;
                status.expectedAmount = group.maximum(activity);
            }
        }

        private void restoreActivityTotal() {
            double forcedTotal = 0;
            double unforcedTotal = 0;

            for (GroupStatus status : progress.values()) {
                if (status.isForced) {
                    forcedTotal += status.expectedAmount;
                } else {
                    forcedTotal += status.constrainedAmount;
                    unforcedTotal += status.expectedFreeAmount();
                }
            }

            double requiredUnforcedTotal = totalQuantity - forcedTotal;
            double compensationFactor = requiredUnforcedTotal / unforcedTotal;

            for (GroupStatus status : progress.values()) {
                if (!status.isForced) {
                    status.setExpectedFreeAmount(
                            status.expectedFreeAmount() * compensationFactor);
                }
            }
        }

        private void findReleasedAmounts() {
            for (GroupStatus status : progress.values()) {
                if (status.isForced && !status.isLocked) {
                    double forcedTotal = 0;
                    double unforcedTotal = 0;

                    for (GroupStatus status2 : progress.values()) {
                        if (status2 != status && status2.isForced) {
                            forcedTotal += status2.expectedAmount;
                        } else {
                            forcedTotal += status2.constrainedAmount;
                            unforcedTotal += status2.originalFreeAmount();
                        }
                    }

                    double requiredUnforcedTotal = totalQuantity - forcedTotal;
                    double compensationFactor = requiredUnforcedTotal
                            / unforcedTotal;

                    status.setReleasedFreeAmount(
                            status.originalFreeAmount() * compensationFactor);

                } else {
                    status.releasedAmount = status.expectedAmount;
                }
            }
        }

        private void releaseGroupsThatDoNotNeedForcing() {
            for (StatusEntry e : progressEntries()) {
                if (!e.status.isLocked) {
                    if (e.status.isForced) {
                        double releasedAmount = e.status.releasedAmount;
                        double minimumAmount = e.group.minimum(activity);
                        double maximumAmount = e.group.maximum(activity);
                        if (releasedAmount >= minimumAmount
                                && releasedAmount <= maximumAmount) {
                            e.status.isForced = false;
                            e.status.expectedAmount = e.status.originalAmount;
                        }
                    } else {
                        // The groups that were not forced to begin with still
                        // need
                        // to be reset to their original values so that they
                        // will be
                        // in proportion to the newly released groups.
                        e.status.expectedAmount = e.status.originalAmount;
                    }
                }
            }
        }

        private void checkConverged() {
            converged = true;
            for (StatusEntry e : progressEntries()) {
                double expectedAmount = e.status.expectedAmount;
                double minimumAmount = e.group.minimum(activity);
                double maximumAmount = e.group.maximum(activity);
                if (expectedAmount < minimumAmount * (1 - tolerance)
                        || expectedAmount > maximumAmount * (1 + tolerance)) {
                    converged = false;
                    break;
                }
            }
        }

        private void lockForcedGroups() {
            for (GroupStatus status : progress.values()) {
                if (status.isForced) {
                    status.isLocked = true;
                }
            }
        }

        private boolean isForced(ZoneGroup<Z> group) {
            return progress.get(group).isForced;
        }

        private boolean isNewlyForced(ZoneGroup<Z> group) {
            return progress.get(group).isNewlyForced;
        }

        private double expectedAmount(ZoneGroup<Z> group) {
            return progress.get(group).expectedAmount;
        }

        private double releasedAmount(ZoneGroup<Z> group) {
            return progress.get(group).releasedAmount;
        }
    }

    private class StatusEntry {
        private ZoneGroup<Z> group;
        private GroupStatus status;

        private StatusEntry(ZoneGroup<Z> group, GroupStatus status) {
            this.group = group;
            this.status = status;
        }

        @Override
        public String toString() {
            return String.format("StatusEntry (%s, %s)", group, status);
        }
    }

    private static class GroupStatus {
        private double originalAmount;
        private double constrainedAmount;
        private double expectedAmount;
        private double releasedAmount;
        private boolean isForced = false;
        private boolean isNewlyForced = false;
        private boolean isLocked = false;

        private GroupStatus(double originalAmount, double constrainedAmount) {
            this.constrainedAmount = constrainedAmount;
            this.originalAmount = originalAmount;
            expectedAmount = originalAmount;
            releasedAmount = originalAmount;
        }

        private void updateAmount(double newAmount, double constrainedAmount) {
            this.constrainedAmount = constrainedAmount;
            originalAmount = newAmount;
            expectedAmount = newAmount;
        }

        private double originalFreeAmount() {
            return originalAmount - constrainedAmount;
        }

        private double expectedFreeAmount() {
            return expectedAmount - constrainedAmount;
        }

        private double releasedFreeAmount() {
            return releasedAmount - constrainedAmount;
        }

        private void setOriginalFreeAmount(double amount) {
            originalAmount = amount + constrainedAmount;
        }

        private void setExpectedFreeAmount(double amount) {
            expectedAmount = amount + constrainedAmount;
        }

        private void setReleasedFreeAmount(double amount) {
            releasedAmount = amount + constrainedAmount;
        }

        @Override
        public String toString() {
            return String.format(
                    "(constrained=%.3g, original=%.3g, expected=%.3g, released=%.3g, forced=%b, locked=%b)",
                    constrainedAmount, originalAmount, expectedAmount,
                    releasedAmount, isForced, isLocked);
        }
    }
}
