package com.hbaspecto.pecas.sd.orm;

import simpleorm.dataset.SFieldFlags;
import simpleorm.dataset.SFieldInteger;
import simpleorm.dataset.SFieldString;
import simpleorm.dataset.SRecordInstance;
import simpleorm.dataset.SRecordMeta;
import simpleorm.sessionjdbc.SSessionJdbc;

/**
 * Base class of the table taz_limit_groups.
 * 
 * @author Graham Hill
 */
@SuppressWarnings("serial")
abstract class TazLimitGroups_gen extends SRecordInstance {

    public static final SRecordMeta<TazLimitGroups> meta = new SRecordMeta<TazLimitGroups>(
            TazLimitGroups.class, "taz_limit_groups");

    // Columns in table
    public static final SFieldInteger TazLimitGroupId = new SFieldInteger(
            meta, "taz_limit_group_id", new SFieldFlags[] {
                    SFieldFlags.SPRIMARY_KEY, SFieldFlags.SMANDATORY});

    public static final SFieldString TazLimitGroupName = new SFieldString(
            meta, "taz_limit_group_name", 2147483647);

    // Column getters and setters
    public int get_TazLimitGroupId() {
        return getInt(TazLimitGroupId);
    }

    public void set_TazLimitGroupId(int value) {
        setInt(TazLimitGroupId, value);
    }

    public String get_TazLimitGroupName() {
        return getString(TazLimitGroupName);
    }

    public void set_TazLimitGroupName(String value) {
        setString(TazLimitGroupName, value);
    }

    // Find and create
    public static TazLimitGroups findOrCreate(SSessionJdbc ses,
            int _TazLimitGroupId) {
        return ses.findOrCreate(meta, new Object[] {_TazLimitGroupId});
    }

    @Override
    public SRecordMeta<TazLimitGroups> getMeta() {
        return meta;
    }
}
