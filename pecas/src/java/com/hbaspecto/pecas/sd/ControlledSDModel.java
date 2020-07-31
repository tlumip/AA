package com.hbaspecto.pecas.sd;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.hbaspecto.pecas.land.Tazs;
import com.hbaspecto.pecas.sd.estimation.ByZonePrefilter;
import com.hbaspecto.pecas.sd.estimation.ConcurrentLandInventory;
import com.hbaspecto.pecas.sd.estimation.EstimationMatrix;
import com.hbaspecto.pecas.sd.estimation.EstimationTarget;
import com.hbaspecto.pecas.sd.estimation.ExpectedValue;
import com.hbaspecto.pecas.sd.estimation.ExpectedValueFilter;
import com.hbaspecto.pecas.sd.estimation.FullTargetPrinter;
import com.hbaspecto.pecas.sd.estimation.GeographicFilter;
import com.hbaspecto.pecas.sd.estimation.SpaceTypeFilter;
import com.hbaspecto.pecas.sd.estimation.StandardConstructionTarget;
import com.hbaspecto.pecas.sd.estimation.StandardRenovationTarget;
import com.hbaspecto.pecas.sd.orm.Luzs;
import com.hbaspecto.pecas.sd.orm.SpaceTypesGroup;
import com.hbaspecto.pecas.sd.orm.TazGroups;
import com.pb.common.util.ResourceUtil;

import no.uib.cipr.matrix.Vector;

public class ControlledSDModel extends StandardSDModel {

    @Override
    public void simulateDevelopment() {
        try {
            if (Double.isFinite(ResourceUtil.getDoubleProperty(rbSD,
                    "MaxParcelSize", Double.POSITIVE_INFINITY))) {
                loggerf.throwFatal(
                        "Constrained SD does not currently work with pseudoparcelling enabled");
            }

            TargetsWithConstraints targets = prepareTargets();
            List<EstimationTarget> targetList = targets.allTargets();
            
            DeferredAlternatives alts = doExpectedValueAndProbabilityPass(
                    targetList);
            
            File outf = new File(String.valueOf(currentYear), "gs_unscaled_evs.csv");
            
            writeEVs(outf, new FullTargetPrinter(), targetList, "ExpectedValue",
                    "Could not write unscaled expected values");

            if (ResourceUtil.getProperty(rbSD, "MatchmakerControlTotalLevel").equalsIgnoreCase("region")) {                
                scaleToMatchConstraints(targets);
            }
            
            outf = new File(String.valueOf(currentYear), "gs_control_totals.csv");

            writeEVs(outf, new FullTargetPrinter(), targetList, "Constraint",
                    "Could not write constraints");

            Set<DeferredAlternative> chosen = chooseAlternatives(alts, targets);

            doChosenDevelopments(chosen);

            // TODO Add targets from space limits as well.

            land.getDevelopmentLogger().flush();
            land.getChoiceUtilityLogger().flush();
        } finally {
            try {
                land.getDevelopmentLogger().close();
                land.getChoiceUtilityLogger().close();
            } catch (Exception e) {
                logger.fatal(e);
            }
        }
    }

    private TargetsWithConstraints prepareTargets() {
        String constraintLevel = ResourceUtil.getProperty(rbSD, "MatchmakerAllocationLevel");
        List<GeographicFilter> filters = new ArrayList<>();
        if (constraintLevel.equalsIgnoreCase("region")) {
            filters.add(GeographicFilter.all());
        } else if (constraintLevel.equalsIgnoreCase("luz")) {
            for (int luz : Luzs.getZoneNumbers(land.getSession())) {
                filters.add(GeographicFilter.inLuz(luz));
            }
        } else if (constraintLevel.equalsIgnoreCase("tazgroup")) {
            for (int group : TazGroups.getAllTazGroupIds(land.getSession())) {
                filters.add(GeographicFilter.inTazGroup(group));
            }
        } else if (constraintLevel.equalsIgnoreCase("taz")) {
            for (int taz : Tazs.getZoneNumbers(land.getSession())) {
                filters.add(GeographicFilter.inTaz(taz));
            }
        }
        TargetsWithConstraints targets = new TargetsWithConstraints();
        List<SpaceTypesI> spaceTypes = new ArrayList<>(SpaceTypesI.getAllSpaceTypes());
        spaceTypes.sort(Comparator.comparing(SpaceTypesI::get_SpaceTypeId));
        for (SpaceTypesI st : spaceTypes) {
            int stid = st.get_SpaceTypeId();
            SpaceTypeFilter stFilter = SpaceTypeFilter.only(stid);
            for (GeographicFilter geoFilter : filters) {
                targets.addTotalBuiltTarget(new StandardConstructionTarget(stFilter, geoFilter));
                targets.addRenovationTarget(new StandardRenovationTarget(stFilter, geoFilter));
                // We need a new demolition target that only includes direct
                // demolition, not the side effect from a build-new
                // (so that all events count towards at most one target)
                // targets.addDemolitionTarget(st, new
                // SpaceGroupDemolitionTarget(stid));
            }
        }
        return targets;
    }

    private DeferredAlternatives doExpectedValueAndProbabilityPass(
            List<EstimationTarget> targets) {
        loggerf.info("Calculating expected values and probabilities");

        DeferredAlternatives alts = new DeferredAlternatives();

        int queueSize = ResourceUtil.getIntegerProperty(rbSD, "QueueSize", 5);
        ConcurrentLandInventory cli = new ConcurrentLandInventory(land,
                queueSize, rbSD);

        List<ExpectedValue> evs = EstimationTarget
                .convertToExpectedValueObjects(targets);
        
        ExpectedValueFilter filter = new ByZonePrefilter(evs, cli);
        EstimationMatrix matrix = new EstimationMatrix(filter,
                Collections.emptyList());

        LandPassRunner pass = new LandPassRunner(cli, zr -> {
            zr.startCaching(cli);
            zr.addExpectedValuesToMatrix(matrix, cli);
            zr.addAlternatives(alts, cli);
            zr.endCaching(cli);
        });

        int concurrency = ResourceUtil.getIntegerProperty(rbSD,
                "ExpectedValueCalculationThreads");
        pass.calculateConcurrently(concurrency);
        
        Vector expValues = matrix.getExpectedValues();
        List<ExpectedValue> evObjects = matrix.getTargets();
        for (int i = 0; i < expValues.size(); i++) {
            evObjects.get(i).setModelledValue(expValues.get(i));
        }

        return alts;
    }

    private void scaleToMatchConstraints(TargetsWithConstraints targets) {
        loggerf.info("Scaling expected values to match constraints");
        Map<StandardConstructionTarget, SpaceTypesGroup> groups = new HashMap<>();
        Map<SpaceTypesGroup, Double> groupEvTotals = new HashMap<>();
        for (StandardConstructionTarget target : targets.totalBuiltTargets()) {
            SpaceTypesGroup group = null;
            for (SpaceTypesI st : target.spaceTypeFilter().acceptedSpaceTypes()) {
                SpaceTypesGroup newGroup = st.get_SPACE_TYPES_GROUP(land.getSession());
                if (group != null && !newGroup.equals(group)) {
                    throw new AssertionError(String.format(
                            "Space type %d in the filter is group %d, but there was already a space type in group %d",
                            st, newGroup, group));
                }
                group = newGroup;
            }
            if (!groupEvTotals.containsKey(group)) {
                groupEvTotals.put(group, 0.0);
            }
            groupEvTotals.put(group,
                    groupEvTotals.get(group) + target.getModelledValue());
            groups.put(target, group);
        }

        Map<SpaceTypesGroup, Double> scaleFactors = new HashMap<>();
        for (Map.Entry<SpaceTypesGroup, Double> entry : groupEvTotals
                .entrySet()) {
            SpaceTypesGroup group = entry.getKey();
            double targetConstruction = SpaceTypesGroup
                    .getTargetConstructionQuantity(
                            group.get_SpaceTypesGroupId());
            scaleFactors.put(group, targetConstruction / entry.getValue());
        }

        for (StandardConstructionTarget target : targets.totalBuiltTargets()) {
            SpaceTypesGroup group = groups.get(target);
            target.setModelledValue(scaleFactors.get(group) * target.getModelledValue());
        }
    }

    private Set<DeferredAlternative> chooseAlternatives(
            DeferredAlternatives alts, TargetsWithConstraints targets) {
        loggerf.info("Allocating space to parcels");
        Set<HeadReference> looking = alts.allParcelAlternativesInOrder()
                .stream().map(x -> new HeadReference(x))
                .collect(Collectors.toSet());
        Set<DeferredAlternativeInOrder> exhausted = new HashSet<>();
        while (!looking.isEmpty()) {
            loggerf.info("At the beginning of the loop, there are %d unassigned parcels", looking.size());
            // Each parcel chooses its favourite alternative.
            for (Iterator<HeadReference> it = looking.iterator(); it
                    .hasNext();) {
                HeadReference ref = it.next();
                DeferredAlternativeInOrder alt = ref.alt;
                if (alt.isUnconstrained()) {
                    exhausted.add(alt);
                    it.remove();
                } else if (targets.submit(alt)) {
                    it.remove();
                } else {
                    ref.alt = alt.markUnconstrained();
                }
            }
            
            loggerf.info("After the parcels choose, there are %d unassigned parcels", looking.size());

            // Now the targets only accept the most probable alternatives and
            // reject the rest.
            for (DeferredAlternative alt : targets.rejectOverbuild()) {
                DeferredAlternativeInOrder next = ((DeferredAlternativeInOrder) alt).next();
                looking.add(new HeadReference(next));
            }
            loggerf.info("After excess alternatives are rejected, there are %d unassigned parcels" , looking.size());
        }

        Set<DeferredAlternative> result = new HashSet<>();
        result.addAll(targets.acceptedAlternatives());
        result.addAll(exhausted);
        return result;
    }

    private static class HeadReference {
        private DeferredAlternativeInOrder alt;

        private HeadReference(DeferredAlternativeInOrder alt) {
            this.alt = alt;
        }
        
        @Override
        public String toString() {
            return "RefTo(" + alt + ")";
        }
    }

    private void doChosenDevelopments(Set<DeferredAlternative> chosen) {
        loggerf.info("Performing chosen development alternatives");
        Map<Long, DeferredAlternative> chosenByParcel = new HashMap<>();
        for (DeferredAlternative alt : chosen) {
            chosenByParcel.put(alt.parcelNum(), alt);
        }

        ZoningRulesI.land = land;
        
        LandPassRunner pass = new LandPassRunner(land, zr -> {
            long parcelNum = land.getPECASParcelNumber();
            DeferredAlternative alt = chosenByParcel.get(parcelNum);
            if (alt != null) {
                alt.doDevelopment();
            }
        });

        pass.calculateInThisThread();
    }
}
