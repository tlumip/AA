package com.hbaspecto.pecas.sd.estimation;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

import com.hbaspecto.pecas.land.Tazs;
import com.hbaspecto.pecas.sd.ZoningRulesI;
import com.hbaspecto.pecas.sd.orm.TazGroups;
import com.hbaspecto.pecas.sd.orm.TazsByTazGroup;

import simpleorm.dataset.SQuery;
import simpleorm.sessionjdbc.SSessionJdbc;

public interface GeographicFilter {
    public boolean appliesToCurrentParcel();

    /**
     * Returns the collection of TAZs included in this filter, or an empty
     * collection if all TAZs are included.
     */
    public Collection<Tazs> applicableTazs();

    /**
     * Returns the collection of TAZs included in this filter. Use this instead
     * of {@code applicableTazs} if you need a list of all TAZs for
     * {@code GeographicFilter.all()} rather than an empty collection.
     */
    public default Collection<Tazs> allApplicableTazs() {
        Collection<Tazs> result = applicableTazs();
        if (result.isEmpty()) {
            SSessionJdbc session = SSessionJdbc.getThreadLocalSession();
            return Tazs.getZoneNumbers(session).stream().map(Tazs::getTazRecord)
                    .collect(Collectors.toList());
        } else {
            return result;
        }
    }
    
    public String groupType();
    
    public int groupNumber();

    public static GeographicFilter all() {
        return new GeographicFilter() {
            @Override
            public boolean appliesToCurrentParcel() {
                return true;
            }

            @Override
            public Collection<Tazs> applicableTazs() {
                return Collections.emptyList();
            }
            
            @Override
            public String toString() {
                return "filter(all)";
            }

            @Override
            public String groupType() {
                return "All";
            }

            @Override
            public int groupNumber() {
                return 0;
            }
        };
    }

    public static GeographicFilter inLuz(int luz) {
        return new GeographicFilter() {
            @Override
            public boolean appliesToCurrentParcel() {
                return Tazs.getTazRecord(ZoningRulesI.land.getTaz())
                        .get_LuzNumber() == luz;
            }

            @Override
            public Collection<Tazs> applicableTazs() {
                SQuery<Tazs> query = new SQuery<>(Tazs.meta).eq(Tazs.LuzNumber,
                        luz);
                SSessionJdbc session = SSessionJdbc.getThreadLocalSession();
                return session.query(query);
            }
            
            @Override
            public String toString() {
                return "filter(luz" + luz + ")";
            }

            @Override
            public String groupType() {
                return "Luz";
            }

            @Override
            public int groupNumber() {
                return luz;
            }
        };
    }

    public static GeographicFilter inTazGroup(int groupNumber) {
        return new GeographicFilter() {
            @Override
            public boolean appliesToCurrentParcel() {
                TazGroups group = Tazs.getTazRecord(ZoningRulesI.land.getTaz())
                        .getTazGroup();
                if (group == null) {
                    return false;
                } else {
                    return groupNumber == group.get_TazGroupId();
                }
            }

            @Override
            public Collection<Tazs> applicableTazs() {
                SQuery<TazsByTazGroup> query = new SQuery<>(TazsByTazGroup.meta)
                        .eq(TazsByTazGroup.TazGroupId, groupNumber);
                SSessionJdbc session = SSessionJdbc.getThreadLocalSession();

                return session.query(query).stream()
                        .map(tbtg -> tbtg.get_TAZ(session))
                        .collect(Collectors.toList());
            }
            
            @Override
            public String toString() {
                return "filter(group" + groupNumber + ")";
            }

            @Override
            public String groupType() {
                return "TazGroup";
            }

            @Override
            public int groupNumber() {
                return groupNumber;
            }
        };
    }

    public static GeographicFilter inAdHocTazGroup(int groupNumber, Collection<Integer> group) {
        return new GeographicFilter() {
            private Set<Integer> groupSet = new LinkedHashSet<>(group);

            @Override
            public boolean appliesToCurrentParcel() {
                return groupSet.contains(ZoningRulesI.land.getTaz());
            }

            @Override
            public Collection<Tazs> applicableTazs() {
                return groupSet.stream().map(z -> Tazs.getTazRecord(z))
                        .collect(Collectors.toList());
            }
            
            @Override
            public String toString() {
                return "filter(group" + groupNumber + " (" + group + "))";
            }

            @Override
            public String groupType() {
                return "AdHocTazGroup";
            }

            @Override
            public int groupNumber() {
                return groupNumber;
            }
        };
    }

    public static GeographicFilter inTaz(int taz) {
        return new GeographicFilter() {
            @Override
            public boolean appliesToCurrentParcel() {
                return ZoningRulesI.land.getTaz() == taz;
            }

            @Override
            public Collection<Tazs> applicableTazs() {
                return Collections.singletonList(Tazs.getTazRecord(taz));
            }
            
            @Override
            public String toString() {
                return "filter(taz" + taz + ")";
            }

            @Override
            public String groupType() {
                return "Taz";
            }

            @Override
            public int groupNumber() {
                return taz;
            }
        };
    }
}
