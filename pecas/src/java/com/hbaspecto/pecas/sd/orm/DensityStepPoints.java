package com.hbaspecto.pecas.sd.orm;

import java.util.List;
import java.util.stream.Collectors;

import com.hbaspecto.pecas.sd.SessionLocalMap;
import com.hbaspecto.pecas.sd.estimation.DensityShapingFunctionParameter.Key;

import simpleorm.dataset.SQuery;
import simpleorm.sessionjdbc.SSessionJdbc;

@SuppressWarnings("serial")
public class DensityStepPoints extends DensityStepPoints_gen {

    private static SessionLocalMap<Key, DensityStepPoints> sessionMap = new SessionLocalMap<Key, DensityStepPoints>() {
        @Override
        protected DensityStepPoints findRecord(SSessionJdbc session, Key key) {
            return session.find(meta, key.spacetype, key.stepPointNumber);
        }

        @Override
        protected List<DensityStepPoints> findAllRecords(SSessionJdbc session) {
            SQuery<DensityStepPoints> qry = new SQuery<>(DensityStepPoints.meta)
                    .ascending(SpaceTypeId).ascending(StepPointNumber);
            return session.query(qry);
        }

        @Override
        protected Key getKeyFromRecord(DensityStepPoints record) {
            return new Key(record.get_SpaceTypeId(),
                    record.get_StepPointNumber());
        }
    };

    public static DensityStepPoints getRecord(int spacetype,
            int stepPointNumber) {
        return sessionMap.getRecord(new Key(spacetype, stepPointNumber));
    }

    /**
     * Returns the step point numbers that have been defined for the specified
     * space type, in ascending order.
     */
    public static List<Integer> getStepPointsForSpaceType(int spacetype) {
        return sessionMap.getAllRecords().stream()
                .filter((dsp) -> (dsp.get_SpaceTypeId() == spacetype))
                .map(DensityStepPoints::get_StepPointNumber)
                .collect(Collectors.toList());
    }
}
