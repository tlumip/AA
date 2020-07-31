package com.hbaspecto.pecas.sd.orm;

import com.hbaspecto.pecas.sd.SpaceTypesI;

import simpleorm.dataset.SFieldFlags;
import simpleorm.dataset.SFieldInteger;
import simpleorm.dataset.SRecordInstance;
import simpleorm.dataset.SRecordMeta;
import simpleorm.sessionjdbc.SSessionJdbc;
import simpleorm.utils.SException;

/**
 * Base class of table taz_limit_space_types
 * 
 * @author Graham Hill
 */
@SuppressWarnings("serial")
abstract class TazLimitSpaceTypes_gen extends SRecordInstance {

    public static final SRecordMeta<TazLimitSpaceTypes> meta = new SRecordMeta<TazLimitSpaceTypes>(
            TazLimitSpaceTypes.class, "taz_limit_space_types");

    // Columns in table
    public static final SFieldInteger SpaceTypeId = new SFieldInteger(meta,
            "space_type_id", new SFieldFlags[] {SFieldFlags.SPRIMARY_KEY,
                    SFieldFlags.SMANDATORY});

    public static final SFieldInteger TazLimitGroupId = new SFieldInteger(
            meta, "taz_limit_group_id");

    // Column getters and setters
    public int get_SpaceTypeId() {
        return getInt(SpaceTypeId);
    }

    public void set_SpaceTypeId(int value) {
        setInt(SpaceTypeId, value);
    }

    public int get_TazLimitGroupId() {
        return getInt(TazLimitGroupId);
    }

    public void set_TazLimitGroupId(int value) {
        setInt(TazLimitGroupId, value);
    }

    // Foreign key getters and setters
    public SpaceTypesI get_SPACE_TYPES_I(SSessionJdbc ses) {
        try {
            return ses.findOrCreate(SpaceTypesI.meta,
                    new Object[] {get_SpaceTypeId()});
        } catch (SException e) {
            if (e.getMessage().indexOf("Null Primary key") > 0) {
                return null;
            }
            throw e;
        }
    }

    public void set_SPACE_TYPES_I(SpaceTypesI value) {
        set_SpaceTypeId(value.get_SpaceTypeId());
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

    public void set_TAZ_LIMIT_GROUP_I(TazLimitGroups value) {
        set_TazLimitGroupId(value.get_TazLimitGroupId());
    }

    // Find and create
    public static TazLimitSpaceTypes findOrCreate(SSessionJdbc ses,
            int _SpaceTypeId, int _TazLimitGroupId) {
        return ses.findOrCreate(meta, new Object[] {_SpaceTypeId,
                _TazLimitGroupId});
    }

    public static TazLimitSpaceTypes findOrCreate(SSessionJdbc ses,
            SpaceTypesI _ref, int _TazLimitGroupId) {
        return findOrCreate(ses, _ref.get_SpaceTypeId(), _TazLimitGroupId);
    }

    public static TazLimitSpaceTypes findOrCreate(SSessionJdbc ses,
            int _SpaceTypeId, TazLimitGroups _ref) {
        return findOrCreate(ses, _SpaceTypeId, _ref.get_TazLimitGroupId());
    }

    @Override
    public SRecordMeta<?> getMeta() {
        return meta;
    }
}
