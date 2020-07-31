package com.hbaspecto.pecas.sd.orm;

import java.io.Serializable;

import simpleorm.dataset.SFieldDouble;
import simpleorm.dataset.SFieldFlags;
import simpleorm.dataset.SFieldInteger;
import simpleorm.dataset.SRecordInstance;
import simpleorm.dataset.SRecordMeta;
import simpleorm.sessionjdbc.SSessionJdbc;
import simpleorm.utils.SException;

/**
 * Base class of table space_taz_limits.
 * 
 * @author Graham Hill
 */
@SuppressWarnings("serial")
abstract class SpaceTazLimits_gen extends SRecordInstance implements
        Serializable {

    public static final SRecordMeta<SpaceTazLimits> meta = new SRecordMeta<SpaceTazLimits>(
            SpaceTazLimits.class, "space_taz_limits");

    public static final SFieldInteger TazGroupId = new SFieldInteger(
            meta,
            "taz_group_id",
            new SFieldFlags[] {SFieldFlags.SPRIMARY_KEY, SFieldFlags.SMANDATORY});

    public static final SFieldInteger TazLimitGroupId = new SFieldInteger(meta,
            "taz_limit_group_id", new SFieldFlags[] {SFieldFlags.SPRIMARY_KEY,
                    SFieldFlags.SMANDATORY});

    public static final SFieldDouble MinQuantity = new SFieldDouble(meta,
            "min_quantity");

    public static final SFieldDouble MaxQuantity = new SFieldDouble(meta,
            "max_quantity");

    public static final SFieldInteger YearEffective = new SFieldInteger(meta,
            "year_effective", new SFieldFlags[] {SFieldFlags.SPRIMARY_KEY,
                    SFieldFlags.SMANDATORY});

    // Column getters and setters
    public int get_TazGroupId() {
        return getInt(TazGroupId);
    }

    public void set_TazGroupId(int value) {
        setInt(TazGroupId, value);
    }

    public int get_TazLimitGroupId() {
        return getInt(TazLimitGroupId);
    }

    public void set_TazLimitGroupId(int value) {
        setInt(TazLimitGroupId, value);
    }
    
    public double get_MinQuantity() {
        return getDouble(MinQuantity);
    }
    
    public void set_MinQuantity(double value) {
        setDouble(MinQuantity, value);
    }

    public double get_MaxQuantity() {
        return getDouble(MaxQuantity);
    }

    public void set_MaxQuantity(double value) {
        setDouble(MaxQuantity, value);
    }

    public int get_YearEffective() {
        return getInt(YearEffective);
    }

    public void set_YearEffective(int value) {
        setInt(YearEffective, value);
    }

    // Foreign key getters and setters
    public TazGroups get_TAZ_GROUP(SSessionJdbc ses) {
        try {
            return ses.findOrCreate(TazGroups.meta, new Object[] {get_TazGroupId()});
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

    public TazLimitGroups get_TAZ_LIMIT_GROUP(SSessionJdbc ses) {
        try {
            return ses.findOrCreate(TazLimitGroups.meta,
                    new Object[] {get_TazLimitGroupId()});
        } catch (SException e) {
            if (e.getMessage().indexOf("Null Primary key") > 0) {
                return null;
            }
            throw e;
        }
    }

    public void set_TAZ_LIMIT_GROUP(TazLimitGroups value) {
        set_TazLimitGroupId(value.get_TazLimitGroupId());
    }

    // Find and create
    public static SpaceTazLimits findOrCreate(SSessionJdbc ses, int _TazGroupId,
            int _TazLimitGroupId, int _YearEffective) {
        return ses.findOrCreate(meta, new Object[] {_TazGroupId, _TazLimitGroupId,
                _YearEffective});
    }

    public static SpaceTazLimits findOrCreate(SSessionJdbc ses, TazGroups _ref,
            int _TazLimitGroupId, int _YearEffective) {
        return findOrCreate(ses, _ref.get_TazGroupId(), _TazLimitGroupId,
                _YearEffective);
    }

    public static SpaceTazLimits findOrCreate(SSessionJdbc ses,
            TazLimitGroups _ref, int _Taz, int _YearEffective) {
        return findOrCreate(ses, _Taz, _ref.get_TazLimitGroupId(),
                _YearEffective);
    }

    @Override
    public SRecordMeta<SpaceTazLimits> getMeta() {
        return meta;
    }
}
