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
public class DensityStepPoints_gen extends SRecordInstance {

    public static final SRecordMeta<DensityStepPoints> meta = new SRecordMeta<>(
            DensityStepPoints.class, "density_step_points");

    // Columns in table
    public static final SFieldInteger SpaceTypeId = new SFieldInteger(meta,
            "space_type_id", new SFieldFlags[] {SFieldFlags.SPRIMARY_KEY,
                    SFieldFlags.SMANDATORY});

    public static final SFieldInteger StepPointNumber = new SFieldInteger(meta,
            "step_point_number", new SFieldFlags[] {SFieldFlags.SPRIMARY_KEY,
                    SFieldFlags.SMANDATORY});

    public static final SFieldDouble StepPointIntensity = new SFieldDouble(meta,
            "step_point_intensity");

    public static final SFieldDouble SlopeAdjustment = new SFieldDouble(meta,
            "slope_adjustment");

    public static final SFieldDouble StepPointAdjustment = new SFieldDouble(
            meta, "step_point_adjustment");

    // Column getters and setters
    public int get_SpaceTypeId() {
        return getInt(SpaceTypeId);
    }

    public void set_SpaceTypeId(int value) {
        setInt(SpaceTypeId, value);
    }

    public int get_StepPointNumber() {
        return getInt(StepPointNumber);
    }

    public void set_StepPointNumber(int value) {
        setInt(StepPointNumber, value);
    }

    public double get_StepPointIntensity() {
        return getDouble(StepPointIntensity);
    }

    public void set_StepPointIntensity(double value) {
        setDouble(StepPointIntensity, value);
    }

    public double get_SlopeAdjustment() {
        return getDouble(SlopeAdjustment);
    }

    public void set_SlopeAdjustment(double value) {
        setDouble(SlopeAdjustment, value);
    }

    public double get_StepPointAdjustment() {
        return getDouble(StepPointAdjustment);
    }

    public void set_StepPointAdjustment(double value) {
        setDouble(StepPointAdjustment, value);
    }

    // Foreign key getters and setters
    public SpaceTypesI get_SPACE_TYPES_I(SSessionJdbc ses) {
        try {
            return ses.findOrCreate(SpaceTypesI.meta,
                    new Object[] {get_SpaceTypeId(),});
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
    public static DensityStepPoints findOrCreate(SSessionJdbc ses,
            int _SpaceTypeId, int _StepPointNumber) {
        return ses.findOrCreate(meta,
                new Object[] {_SpaceTypeId, _StepPointNumber});
    }

    public static DensityStepPoints findOrCreate(SSessionJdbc ses,
            SpaceTypesI _ref, int _StepPointNumber) {
        return ses.findOrCreate(meta,
                new Object[] {_ref.get_SpaceTypeId(), _StepPointNumber});
    }

    @Override
    public SRecordMeta<DensityStepPoints> getMeta() {
        return meta;
    }
}
