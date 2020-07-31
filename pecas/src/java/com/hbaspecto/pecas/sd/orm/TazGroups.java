package com.hbaspecto.pecas.sd.orm;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import simpleorm.dataset.SQuery;
import simpleorm.sessionjdbc.SSessionJdbc;

/**
 * Business rules class for table taz_groups.
 * 
 * @author HBA
 */
@SuppressWarnings("serial")
public class TazGroups extends TazGroups_gen {
    private static ThreadLocal<Boolean> isCachedAlready = new ThreadLocal<Boolean>() {
        @Override
        public Boolean initialValue() {
            return false;
        }
    };

    private boolean constructionConstantFound = false;
    private double constructionConstant;
    private Map<Integer, Double> constructionConstantsBySpaceType = new HashMap<>();
    private Object constructionConstantLock = new Object();

    public static ArrayList<Integer> getAllTazGroupIds(SSessionJdbc session) {
        // SSessionJdbc session = SSessionJdbc.getThreadLocalSession();
        List<TazGroups> groups = getAllTazGroups(session);

        ArrayList<Integer> groupIds = new ArrayList<Integer>();

        for (TazGroups group : groups) {
            groupIds.add(group.get_TazGroupId());
        }
        return groupIds;
    }

    private synchronized static List<TazGroups> getAllTazGroups(
            SSessionJdbc session) {

        SQuery<TazGroups> qry = new SQuery<>(TazGroups.meta);
        boolean wasBegun = true;
        if (!session.hasBegun()) {
            session.begin();
            wasBegun = false;
        }
        List<TazGroups> groups = session.query(qry);
        isCachedAlready.set(true);
        if (!wasBegun)
            session.commit();
        return groups;
    }

    private static TazGroups getTazGroup(SSessionJdbc session, int tazGroupId) {

        if (!isCachedAlready.get())
            getAllTazGroups(session);

        TazGroups group = session.getDataSet().find(meta, tazGroupId);
        return group;
    }

    public static TazGroups getTazGroup(int tazGroupId) {
        return getTazGroup(SSessionJdbc.getThreadLocalSession(), tazGroupId);
    }

    public double getConstructionConstant() {
        synchronized (constructionConstantLock) {
            if (!constructionConstantFound) {
                SSessionJdbc session = SSessionJdbc.getThreadLocalSession();
                boolean wasBegun = true;
                if (!session.hasBegun()) {
                    session.begin();
                    wasBegun = false;
                }
                SQuery<TazGroupConstants> qry = new SQuery<TazGroupConstants>(
                        TazGroupConstants.meta).eq(TazGroupConstants.TazGroupId,
                                get_TazGroupId());
                TazGroupConstants result = session.query(qry).oneOrNone();
                if (result == null) {
                    constructionConstant = 0;
                } else {
                    constructionConstant = result.get_ConstructionConstant();
                }
                if (!wasBegun)
                    session.commit();
                constructionConstantFound = true;
            }
            return constructionConstant;
        }
    }

    public void setOrCreateConstructionConstant(double constant) {
        synchronized (constructionConstantLock) {
            constructionConstantFound = true;
            constructionConstant = constant;
            SSessionJdbc session = SSessionJdbc.getThreadLocalSession();
            boolean wasBegun = true;
            if (!session.hasBegun()) {
                session.begin();
                wasBegun = false;
            }
            TazGroupConstants row = TazGroupConstants.findOrCreate(session, this);
            row.set_ConstructionConstant(constant);
            if (!wasBegun) {
                session.commit();
            }
        }
    }

    public double getConstructionConstantForSpaceType(int spaceTypeId) {
        synchronized (constructionConstantLock) {
            if (!constructionConstantsBySpaceType.containsKey(spaceTypeId)) {
                SSessionJdbc session = SSessionJdbc.getThreadLocalSession();
                boolean wasBegun = true;
                if (!session.hasBegun()) {
                    session.begin();
                    wasBegun = false;
                }
                SQuery<TazGroupSpaceConstants> qry = new SQuery<>(
                        TazGroupSpaceConstants.meta)
                                .eq(TazGroupSpaceConstants.TazGroupId,
                                        get_TazGroupId())
                                .eq(TazGroupSpaceConstants.SpaceTypeId,
                                        spaceTypeId);
                TazGroupSpaceConstants result = session.query(qry).oneOrNone();
                if (result == null) {
                    constructionConstantsBySpaceType.put(spaceTypeId, 0.0);
                } else {
                    constructionConstantsBySpaceType.put(spaceTypeId,
                            result.get_ConstructionConstant());
                }
                if (!wasBegun)
                    session.commit();
            }
            return constructionConstantsBySpaceType.get(spaceTypeId);
        }
    }
    
    public void setOrCreateConstructionConstantForSpaceType(int spaceTypeId, double constant) {
        synchronized (constructionConstantLock) {
            constructionConstantsBySpaceType.put(spaceTypeId, constant);
            SSessionJdbc session = SSessionJdbc.getThreadLocalSession();
            boolean wasBegun = true;
            if (!session.hasBegun()) {
                session.begin();
                wasBegun = false;
            }
            TazGroupSpaceConstants row = TazGroupSpaceConstants.findOrCreate(session, this, spaceTypeId);
            row.set_ConstructionConstant(constant);
            if (!wasBegun) {
                session.commit();
            }
        }
    }
}
