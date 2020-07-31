package com.hbaspecto.pecas.sd;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;
import com.hbaspecto.pecas.land.Tazs;
import com.hbaspecto.pecas.sd.estimation.EstimationTarget;
import com.hbaspecto.pecas.sd.estimation.GeographicFilter;
import com.hbaspecto.pecas.sd.estimation.SpaceTypeFilter;
import com.hbaspecto.pecas.sd.estimation.StandardConstructionTarget;
import com.hbaspecto.pecas.sd.estimation.StandardRenovationTarget;

public class TargetsWithConstraints {

    private Map<SpaceTypesI, SpaceTypeFilter> spaceTypeFilters = new HashMap<>();
    private Map<Tazs, GeographicFilter> tazFilters = new HashMap<>();

    private Table<SpaceTypeFilter, GeographicFilter, StandardConstructionTarget> totalTargets = HashBasedTable
            .create();
    private Table<SpaceTypeFilter, GeographicFilter, StandardRenovationTarget> renoTargets = HashBasedTable
            .create();

    private Map<EstimationTarget, List<DeferredAlternative>> totalCandidates = new HashMap<>();
    private Map<EstimationTarget, List<DeferredAlternative>> renoCandidates = new HashMap<>();

    private Comparator<DeferredAlternative> probabilityComparator = Comparator
            .comparing(DeferredAlternative::probability).reversed()
            .thenComparing(DeferredAlternative::priority)
            .thenComparing(DeferredAlternative::parcelNum);

    public TargetsWithConstraints() {
    }

    // TODO demolition

    public void addTotalBuiltTarget(StandardConstructionTarget target) {
        insertFilters(target.spaceTypeFilter(), target.geographicFilter());
        totalTargets.put(target.spaceTypeFilter(), target.geographicFilter(),
                target);
        totalCandidates.put(target, new ArrayList<>());
    }

    public void addRenovationTarget(StandardRenovationTarget target) {
        insertFilters(target.spaceTypeFilter(), target.geographicFilter());
        renoTargets.put(target.spaceTypeFilter(), target.geographicFilter(),
                target);
        renoCandidates.put(target, new ArrayList<>());
    }

    private void insertFilters(SpaceTypeFilter spaceTypeFilter,
            GeographicFilter geographicFilter) {
        for (SpaceTypesI st : spaceTypeFilter.acceptedSpaceTypes()) {
            if (spaceTypeFilters.containsKey(st) && spaceTypeFilters.get(st) != spaceTypeFilter) {
                throw new AssertionError("Space type groups overlap on type " + st);
            }
            spaceTypeFilters.put(st, spaceTypeFilter);
        }
        for (Tazs t : geographicFilter.allApplicableTazs()) {
            if (tazFilters.containsKey(t) && tazFilters.get(t) != geographicFilter) {
                throw new AssertionError("TAZ groups overlap on TAZ " + t);
            }
            tazFilters.put(t, geographicFilter);
        }
    }

    /*
     * public void addDemolitionTarget(SpaceTypesI spaceType,
     * SpaceGroupDemolitionTarget target) { // TODO Auto-generated method stub
     * 
     * }
     */

    public List<StandardConstructionTarget> totalBuiltTargets() {
        return new ArrayList<>(totalTargets.values());
    }

    public List<EstimationTarget> allTargets() {
        List<EstimationTarget> result = new ArrayList<>();
        result.addAll(totalTargets.values());
        result.addAll(renoTargets.values());
        return result;
    }

    /**
     * Submits the specified alternative as a candidate. Returns true if the
     * submission was accepted (i.e. the alternative matched one of the
     * targets), false otherwise.
     */
    public boolean submit(DeferredAlternative alt) {
        Map<EstimationTarget, List<DeferredAlternative>> candidates;
        Table<SpaceTypeFilter, GeographicFilter, ? extends EstimationTarget> targets;

        if (alt.isConstruction()) {
            candidates = totalCandidates;
            targets = totalTargets;
        } else if (alt.isRenovation()) {
            candidates = renoCandidates;
            targets = renoTargets;
        }
        // TODO demolition
        else {
            return false;
        }

        SpaceTypesI activeType = alt.activeType();
        SpaceTypeFilter spaceTypeFilter = spaceTypeFilters.get(activeType);
        
        Tazs taz = alt.taz();
        GeographicFilter tazFilter = tazFilters.get(taz);
        
        candidates.get(targets.get(spaceTypeFilter, tazFilter)).add(alt);
        return true;
    }

    public Set<DeferredAlternative> rejectOverbuild() {
        Set<DeferredAlternative> result = new HashSet<>();
        result.addAll(rejectOverbuildFrom(totalCandidates));
        result.addAll(rejectOverbuildFrom(renoCandidates));
        return result;
    }

    private Set<DeferredAlternative> rejectOverbuildFrom(
            Map<EstimationTarget, List<DeferredAlternative>> candidates) {
        Set<DeferredAlternative> rejected = new HashSet<>();
        for (Map.Entry<EstimationTarget, List<DeferredAlternative>> entry : candidates
                .entrySet()) {
            double totalEffect = 0;
            double target = entry.getKey().getModelledValue();
            List<DeferredAlternative> alts = entry.getValue();
            alts.sort(probabilityComparator);

            int i = 0;
            int cutBefore = alts.size();

            for (DeferredAlternative alt : entry.getValue()) {
                double newTotalEffect = totalEffect + alt.amount();
                if (newTotalEffect > target) {
                    if (target > totalEffect && alt.tryForceAmount(target - totalEffect)) {
                        cutBefore = i + 1;
                    } else {
                        cutBefore = i;
                        if (newTotalEffect - target < target - totalEffect) {
                            // If more than half of the last alternative fits in
                            // the target, treat it as fitting, so that the
                            // total development is not biased lower than the
                            // targets.
                            cutBefore++;
                        }
                    }
                    break;
                }
                totalEffect = newTotalEffect;
                i++;
            }

            for (Iterator<DeferredAlternative> it = alts
                    .listIterator(cutBefore); it.hasNext();) {
                DeferredAlternative alt = it.next();
                rejected.add(alt);
                it.remove();
            }
        }
        return rejected;
    }

    public Set<DeferredAlternative> acceptedAlternatives() {
        Set<DeferredAlternative> result = new HashSet<>();
        addAll(result, totalCandidates);
        addAll(result, renoCandidates);
        return result;
    }

    private void addAll(Set<DeferredAlternative> result,
            Map<EstimationTarget, List<DeferredAlternative>> candidates) {
        for (List<DeferredAlternative> list : candidates.values()) {
            result.addAll(list);
        }
    }

}
