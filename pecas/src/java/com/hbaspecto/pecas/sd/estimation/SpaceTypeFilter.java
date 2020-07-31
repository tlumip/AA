package com.hbaspecto.pecas.sd.estimation;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

import com.hbaspecto.pecas.sd.SpaceTypesI;

public interface SpaceTypeFilter {
    public boolean accepts(int spaceType);

    public Collection<SpaceTypesI> acceptedSpaceTypes();
    
    public default int[] acceptedSpaceTypeNumbers() {
        Collection<SpaceTypesI> sts = acceptedSpaceTypes();
        int[] result = new int[sts.size()];
        int i = 0;
        for (SpaceTypesI st : sts) {
            result[i] = st.get_SpaceTypeId();
            i++;
        }
        return result;
    }

    public static SpaceTypeFilter only(int onlyType) {
        return new SpaceTypeFilter() {
            @Override
            public boolean accepts(int spaceType) {
                return spaceType == onlyType;
            }

            @Override
            public Collection<SpaceTypesI> acceptedSpaceTypes() {
                return Collections.singletonList(SpaceTypesI
                        .getAlreadyCreatedSpaceTypeBySpaceTypeID(onlyType));
            }
            
            @Override
            public String toString() {
                return "filter(st" + onlyType + ")";
            }
        };
    }

    public static SpaceTypeFilter of(int... types) {
        return new SpaceTypeFilter() {
            private Set<Integer> spaceTypes = Arrays.stream(types)
                    .mapToObj(i -> i).collect(Collectors
                            .toCollection(() -> new LinkedHashSet<>()));

            @Override
            public boolean accepts(int spaceType) {
                return spaceTypes.contains(spaceType);
            }

            @Override
            public Collection<SpaceTypesI> acceptedSpaceTypes() {
                return spaceTypes.stream()
                        .map(SpaceTypesI::getAlreadyCreatedSpaceTypeBySpaceTypeID)
                        .collect(Collectors.toList());
            }
            
            @Override
            public String toString() {
                return "filter(st" + Arrays.toString(types) + ")";
            }
        };
    }
}
