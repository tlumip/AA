package com.hbaspecto.pecas.sd;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

public class DeferredAlternatives {

    Map<Long, ArrayList<DeferredAlternative>> altMap = new HashMap<>();

    public Collection<DeferredAlternativeInOrder> allParcelAlternativesInOrder() {
        synchronized (altMap) {
            Collection<DeferredAlternativeInOrder> result = new ArrayList<>();
            Comparator<DeferredAlternative> comparator = Comparator
                    .comparing(DeferredAlternative::probability).reversed()
                    .thenComparing(DeferredAlternative::priority)
                    .thenComparing(alt -> alt.activeType().get_SpaceTypeId());

            for (ArrayList<DeferredAlternative> altsForParcel : altMap
                    .values()) {
                altsForParcel.sort(comparator);
                DeferredAlternativeInOrder head = new DeferredAlternativeInOrder(
                        altsForParcel.get(0));
                DeferredAlternativeInOrder end = head;
                for (int i = 1; i < altsForParcel.size(); i++) {
                    DeferredAlternativeInOrder newEnd = new DeferredAlternativeInOrder(
                            altsForParcel.get(i));
                    end.setNext(newEnd);
                    end = newEnd;
                }
                result.add(head);
            }

            return result;
        }
    }

    /**
     * Adds the specified alternative to the list. This method has no effect if
     * the probability of the alternative is zero.
     */
    public void add(DeferredAlternative alt) {
        if (alt.probability() > 0) {
            long parcelNum = alt.parcelNum();
            synchronized (altMap) {
                if (!altMap.containsKey(parcelNum)) {
                    altMap.put(parcelNum, new ArrayList<>());
                }
                altMap.get(parcelNum).add(alt);
            }
        }
    }

}
