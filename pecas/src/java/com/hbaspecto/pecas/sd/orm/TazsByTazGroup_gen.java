package com.hbaspecto.pecas.sd.orm;

import simpleorm.dataset.SFieldFlags;
import simpleorm.dataset.SFieldInteger;
import simpleorm.dataset.SRecordInstance;
import simpleorm.dataset.SRecordMeta;
import simpleorm.sessionjdbc.SSessionJdbc;
import simpleorm.utils.SException;

import com.hbaspecto.pecas.land.Tazs;

/**
 * Base class of table tazs_by_taz_group
 * 
 * @author Graham Hill
 */
@SuppressWarnings("serial")
abstract class TazsByTazGroup_gen extends SRecordInstance {

    public static final SRecordMeta<TazsByTazGroup> meta = new SRecordMeta<TazsByTazGroup>(
            TazsByTazGroup.class, "tazs_by_taz_group");

    // Columns in table
    public static final SFieldInteger TazNumber = new SFieldInteger(meta,
            "taz_number", new SFieldFlags[] {SFieldFlags.SPRIMARY_KEY,
                    SFieldFlags.SMANDATORY});

    public static final SFieldInteger TazGroupId = new SFieldInteger(meta,
            "taz_group_id");

    // Column getters and setters
    public int get_TazNumber() {
        return getInt(TazNumber);
    }

    public void set_TazNumber(int value) {
        setInt(TazNumber, value);
    }

    public int get_TazGroupId() {
        return getInt(TazGroupId);
    }

    public void set_TazGroupId(int value) {
        setInt(TazGroupId, value);
    }

    // Foreign key getters and setters
    public Tazs get_TAZ(SSessionJdbc ses) {
        try {
            return ses.findOrCreate(Tazs.meta, new Object[] {get_TazNumber()});
        } catch (SException e) {
            if (e.getMessage().indexOf("Null Primary key") > 0) {
                return null;
            }
            throw e;
        }
    }

    public void set_TAZ(Tazs value) {
        set_TazNumber(value.get_TazNumber());
    }

    public TazGroups get_TAZ_GROUP(SSessionJdbc ses) {
        try {
            return ses.findOrCreate(TazGroups.meta,
                    new Object[] {get_TazGroupId()});
        } catch (SException e) {
            if (e.getMessage().indexOf("Null Primary key") > 0) {
                return null;
            }
            throw e;
        }
    }

    public void set_TAZ_GROUP(TazGroups value) {
        set_TazGroupId(value.get_TazGroupId());
    }

    // Find and create
    public static TazsByTazGroup findOrCreate(SSessionJdbc ses, int _TazNumber) {
        return ses.findOrCreate(meta, _TazNumber);
    }
    
    public static TazsByTazGroup findOrCreate(SSessionJdbc ses, Tazs _ref) {
        return findOrCreate(ses, _ref.get_TazNumber());
    }
    
    @Override
    public SRecordMeta<?> getMeta() {
        return meta;
    }
}
