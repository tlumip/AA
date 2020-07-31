package com.hbaspecto.pecas.sd.orm;

import java.util.ArrayList;
import java.util.List;

import simpleorm.dataset.SQuery;
import simpleorm.sessionjdbc.SSessionJdbc;

/**
 * Business rules class for table space_taz_limits.
 * 
 * @author Graham Hill
 */
@SuppressWarnings("serial")
public class SpaceTazLimits extends SpaceTazLimits_gen {

    public static List<SpaceTazLimits> getLimitsForCurrentYear(
            SSessionJdbc session) {
        List<SpaceTazLimits> result = new ArrayList<SpaceTazLimits>();
        ArrayList<Integer> tazGroups = TazGroups.getAllTazGroupIds(session);
        for (int group : tazGroups) {
            MostRecentTazLimitYear yearRecord = MostRecentTazLimitYear
                    .getMostRecentYearForTazGroup(session, group);
            if (yearRecord != null) {
                int mostRecentYear = yearRecord.get_CurrentLimitYear();
                SQuery<SpaceTazLimits> qry = new SQuery<SpaceTazLimits>(meta)
                        .eq(TazGroupId, group).eq(YearEffective, mostRecentYear);
                result.addAll(session.query(qry));
            }
        }
        return result;
    }
}
