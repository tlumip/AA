package com.hbaspecto.pecas.sd.orm;

import java.io.Serializable;

import simpleorm.dataset.SFieldInteger;
import simpleorm.dataset.SRecordInstance;
import simpleorm.dataset.SRecordMeta;
import simpleorm.sessionjdbc.SSessionJdbc;

/**
 * Base class of table most_recent_taz_limit_year.
 * 
 * @author Graham Hill
 */
@SuppressWarnings("serial")
public abstract class MostRecentTazLimitYear_gen extends SRecordInstance
        implements Serializable {

    public static final SRecordMeta<MostRecentTazLimitYear> meta = new SRecordMeta<MostRecentTazLimitYear>(
            MostRecentTazLimitYear.class, "most_recent_taz_limit_year");

    public static final SFieldInteger TazGroupId = new SFieldInteger(meta, "taz_group_id");

    public static final SFieldInteger CurrentLimitYear = new SFieldInteger(
            meta, "current_limit_year");

    // Column getters and setters
    public int get_TazGroupId() {
        return getInt(TazGroupId);
    }

    public void set_TazGroupId(int value) {
        setInt(TazGroupId, value);
    }

    public int get_CurrentLimitYear() {
        return getInt(CurrentLimitYear);
    }

    public void set_CurrentLimitYear(int value) {
        setInt(CurrentLimitYear, value);
    }

    // Find and create
    public static MostRecentTazLimitYear findOrCreate(SSessionJdbc ses) {
        return ses.findOrCreate(meta, new Object[] {});
    }

    @Override
    public SRecordMeta<MostRecentTazLimitYear> getMeta() {
        return meta;
    }
}
