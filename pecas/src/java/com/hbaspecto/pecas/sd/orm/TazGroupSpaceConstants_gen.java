package com.hbaspecto.pecas.sd.orm;

import com.hbaspecto.pecas.sd.SpaceTypesI;

import simpleorm.dataset.SFieldDouble;
import simpleorm.dataset.SFieldFlags;
import simpleorm.dataset.SFieldInteger;
import simpleorm.dataset.SRecordInstance;
import simpleorm.dataset.SRecordMeta;
import simpleorm.sessionjdbc.SSessionJdbc;
import simpleorm.utils.SException;

@SuppressWarnings("serial")
public class TazGroupSpaceConstants_gen extends SRecordInstance {

    public static final SRecordMeta<TazGroupSpaceConstants> meta = new SRecordMeta<TazGroupSpaceConstants>(
            TazGroupSpaceConstants.class, "taz_group_space_constants");

    public static final SFieldInteger TazGroupId = new SFieldInteger(meta,
            "taz_group_id", new SFieldFlags[] {SFieldFlags.SPRIMARY_KEY,
                    SFieldFlags.SMANDATORY});

    public static final SFieldInteger SpaceTypeId = new SFieldInteger(meta,
            "space_type_id", new SFieldFlags[] {SFieldFlags.SPRIMARY_KEY,
                    SFieldFlags.SMANDATORY});

    public static final SFieldDouble ConstructionConstant = new SFieldDouble(
            meta, "construction_constant");
    
    // Column getters and setters
    public int get_TazGroupId() {
        return getInt(TazGroupId);
    }
    
    public void set_TazGroupId(int value) {
        setInt(TazGroupId, value);
    }
    
    public int get_SpaceTypeId() {
        return getInt(SpaceTypeId);
    }
    
    public void set_SpaceTypeId(int value) {
        setInt(SpaceTypeId, value);
    }
    
    public double get_ConstructionConstant() {
        return getDouble(ConstructionConstant);
    }
    
    public void set_ConstructionConstant(double value) {
        setDouble(ConstructionConstant, value);
    }

    @Override
    public SRecordMeta<?> getMeta() {
        return meta;
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
    
    public SpaceTypesI get_SPACE_TYPES_I(SSessionJdbc ses) {
        try {
            return ses.findOrCreate(SpaceTypesI.meta, new Object[] {get_SpaceTypeId()});
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
    
    // Find and create
    public static TazGroupSpaceConstants findOrCreate(SSessionJdbc ses, int _TazGroupId, int _SpaceTypeId) {
        return ses.findOrCreate(meta, _TazGroupId, _SpaceTypeId);
    }
    
    public static TazGroupSpaceConstants findOrCreate(SSessionJdbc ses, TazGroups _ref, int _SpaceTypeId) {
        return findOrCreate(ses, _ref.get_TazGroupId(), _SpaceTypeId);
    }
    
    public static TazGroupSpaceConstants findOrCreate(SSessionJdbc ses, SpaceTypesI _ref, int _TazGroupId) {
        return findOrCreate(ses, _TazGroupId, _ref.get_SpaceTypeId());
    }
}
