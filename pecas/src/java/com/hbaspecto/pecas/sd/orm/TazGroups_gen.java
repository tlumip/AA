package com.hbaspecto.pecas.sd.orm;

import simpleorm.dataset.SFieldFlags;
import simpleorm.dataset.SFieldInteger;
import simpleorm.dataset.SFieldString;
import simpleorm.dataset.SRecordInstance;
import simpleorm.dataset.SRecordMeta;
import simpleorm.sessionjdbc.SSessionJdbc;

/**
 * Base class of the table taz_groups.
 * 
 * @author Graham Hill
 */
@SuppressWarnings("serial")
abstract class TazGroups_gen extends SRecordInstance {

    public static final SRecordMeta<TazGroups> meta = new SRecordMeta<TazGroups>(
            TazGroups.class, "taz_groups");

    // Columns in table
    public static final SFieldInteger TazGroupId = new SFieldInteger(meta,
            "taz_group_id", new SFieldFlags[] {SFieldFlags.SPRIMARY_KEY,
                    SFieldFlags.SMANDATORY});

    public static final SFieldString TazGroupName = new SFieldString(meta,
            "taz_group_name", 2147483647);

    // Column getters and setters
    public int get_TazGroupId() {
        return getInt(TazGroupId);
    }

    public void set_TazGroupId(int value) {
        setInt(TazGroupId, value);
    }

    public String get_TazGroupName() {
        return getString(TazGroupName);
    }

    public void set_TazGroupName(String value) {
        setString(TazGroupName, value);
    }

    // Find and create
    public static TazGroups findOrCreate(SSessionJdbc ses, int _TazGroupId) {
        return ses.findOrCreate(meta, new Object[] {_TazGroupId});
    }

    @Override
    public SRecordMeta<TazGroups> getMeta() {
        return meta;
    }
}
