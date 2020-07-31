package com.hbaspecto.pecas.sd.orm;

import java.io.Serializable;

import simpleorm.dataset.SFieldDouble;
import simpleorm.dataset.SFieldFlags;
import simpleorm.dataset.SFieldInteger;
import simpleorm.dataset.SRecordInstance;
import simpleorm.dataset.SRecordMeta;
import simpleorm.sessionjdbc.SSessionJdbc;
import simpleorm.utils.SException;

@SuppressWarnings("serial")
public abstract class TazGroupConstants_gen extends SRecordInstance
        implements Serializable {

    public static final SRecordMeta<TazGroupConstants> meta = new SRecordMeta<TazGroupConstants>(
            TazGroupConstants.class, "taz_group_constants");

    public static final SFieldInteger TazGroupId = new SFieldInteger(meta,
            "taz_group_id", new SFieldFlags[] {SFieldFlags.SPRIMARY_KEY,
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
    
    public double get_ConstructionConstant() {
        return getDouble(ConstructionConstant);
    }
    
    public void set_ConstructionConstant(double value) {
        setDouble(ConstructionConstant, value);
    }

    @Override
    public SRecordMeta<TazGroupConstants> getMeta() {
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
    
    // Find and create
    public static TazGroupConstants findOrCreate(SSessionJdbc ses, int _TazGroupId) {
        return ses.findOrCreate(meta, _TazGroupId);
    }
    
    public static TazGroupConstants findOrCreate(SSessionJdbc ses, TazGroups _ref) {
        return ses.findOrCreate(meta, _ref.get_TazGroupId());
    }
}
