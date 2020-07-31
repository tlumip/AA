package com.hbaspecto.pecas.sd.orm;

import simpleorm.dataset.SQuery;
import simpleorm.sessionjdbc.SSessionJdbc;

/**
 * Business rules class for table most_recent_taz_limit_year.
 * 
 * @author Graham Hill
 */
@SuppressWarnings("serial")
public class MostRecentTazLimitYear extends MostRecentTazLimitYear_gen {
    /**
     * Retrieves the record corresponding to the specified TAZ group.
     * 
     * @param ses The session
     * @param groupId The TAZ group ID number
     * @return The unique record for that group, or null if no limit exists.
     */
    public static MostRecentTazLimitYear getMostRecentYearForTazGroup(
            SSessionJdbc ses, int groupId) {
        SQuery<MostRecentTazLimitYear> qry = new SQuery<MostRecentTazLimitYear>(
                meta).eq(TazGroupId, groupId);
        return ses.query(qry).oneOrNone();
    }
}
