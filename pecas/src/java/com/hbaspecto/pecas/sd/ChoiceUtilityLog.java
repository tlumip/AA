package com.hbaspecto.pecas.sd;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.apache.log4j.Logger;

import com.hbaspecto.pecas.FormatLogger;

/**
 * Class that records a detailed breakdown of the utilities encountered in the
 * SD choice model.
 * 
 * Note: after starting a log for a parcel, one of the event log methods (
 * {@code logDevelopment}, {@code logDemolition}, etc.) must be called for that
 * parcel. If the event is not being logged, the method {@code clearWithoutLog}
 * must be called. Failure to call one of these methods causes the data to
 * remain in memory indefinitely, leading to possible memory leaks.
 * 
 * @author Graham Hill
 */
public class ChoiceUtilityLog {

    static Logger logger = Logger.getLogger(ChoiceUtilityLog.class);
    private static FormatLogger loggerf = new FormatLogger(logger);

    static BufferedWriter detailedLogBuffer;
    
    private List<Integer> availableSpace;
    
    private ConcurrentMap<Long, UtilityRecord> records = new ConcurrentHashMap<>();

    public void open(String fileNameAndPath, List<Integer> availableSpace) {
        this.availableSpace = new ArrayList<Integer>(availableSpace);
        try {
            detailedLogBuffer = new BufferedWriter(new FileWriter(
                    fileNameAndPath));
            StringBuilder newTypeUtilHeaders = new StringBuilder("");
            StringBuilder newTypeProbHeaders = new StringBuilder("");
            for (int stid : availableSpace) {
                SpaceTypesI st = SpaceTypesI.getAlreadyCreatedSpaceTypeBySpaceTypeID(stid);
                newTypeUtilHeaders.append(",new_space_" + st.get_SpaceTypeId() + "_utility");
                newTypeProbHeaders.append(",new_space_" + st.get_SpaceTypeId() + "_prob");
            }
            detailedLogBuffer.write(
                    "old_pecas_parcel_num," +
                    "new_pecas_parcel_num," +
                    "actual_event_type," +
                    "no_change_utility," +
                    "demolish_utility," +
                    "derelict_utility," +
                    "renovate_utility," +
                    "add_space_utility" +
                    newTypeUtilHeaders + "," +
                    "change_computil," +
                    "decay_computil," +
                    "growth_computil," +
                    "build_computil," +
                    "new_space_computil," +
                    "no_change_prob," +
                    "demolish_prob," +
                    "derelict_prob," +
                    "renovate_prob," +
                    "add_space_prob" +
                    newTypeProbHeaders + "\n");
        } catch (IOException e) {
            loggerf.throwFatal(e, "Can't open detailed development log");
        }
    }
    public void close() {
        try {
            if (detailedLogBuffer != null)
                detailedLogBuffer.close();
        } catch (IOException e) {
            logger.error("Can't close stream");
            e.printStackTrace();
        }
    }
    public void flush() {
        try {
            detailedLogBuffer.flush();
        } catch (IOException e) {
            logger.error("Can't close stream");
            e.printStackTrace();
        }
    }
    
    private UtilityRecord getRecord(long pecasParcelNum) {
        UtilityRecord newRecord = new UtilityRecord(availableSpace);
        UtilityRecord existing = records.putIfAbsent(pecasParcelNum, newRecord);
        return existing == null? newRecord : existing;
    }
    
    public void logNoChangeUtility(long pecasParcelNum, double utility) {
        getRecord(pecasParcelNum).noChangeUtility = utility;
    }
    
    public void logDemolishUtility(long pecasParcelNum, double utility) {
        getRecord(pecasParcelNum).demolishUtility = utility;
    }
    
    public void logDerelictUtility(long pecasParcelNum, double utility) {
        getRecord(pecasParcelNum).derelictUtility = utility;
    }
    
    public void logRenovateUtility(long pecasParcelNum, double utility) {
        getRecord(pecasParcelNum).renovateUtility = utility;
    }
    
    public void logAddSpaceUtility(long pecasParcelNum, double utility) {
        getRecord(pecasParcelNum).addSpaceUtility = utility;
    }
    
    public void logNewSpaceUtility(long pecasParcelNum, int spaceType, double utility) {
        getRecord(pecasParcelNum).newSpaceUtilities.put(spaceType, utility);
    }
    
    public void logChangeCompositeUtility(long pecasParcelNum, double utility) {
        getRecord(pecasParcelNum).changeComputil = utility;
    }
    
    public void logDecayCompositeUtility(long pecasParcelNum, double utility) {
        getRecord(pecasParcelNum).decayComputil = utility;
    }
    
    public void logGrowthCompositeUtility(long pecasParcelNum, double utility) {
        getRecord(pecasParcelNum).growthComputil = utility;
    }
    
    public void logBuildCompositeUtility(long pecasParcelNum, double utility) {
        getRecord(pecasParcelNum).buildComputil = utility;
    }
    
    public void logNewSpaceUtility(long pecasParcelNum, double utility) {
        getRecord(pecasParcelNum).newSpaceComputil = utility;
    }
    
    public void logNoChangeProbability(long pecasParcelNum, double probability) {
        getRecord(pecasParcelNum).noChangeProb = probability;
    }
    
    public void logGrowthProbability(long pecasParcelNum, double probability) {
        getRecord(pecasParcelNum).growthProb = probability;
    }
    
    public void logDemolishProbability(long pecasParcelNum, double probability) {
        getRecord(pecasParcelNum).demolishProb = probability;
    }
    
    public void logDerelictProbability(long pecasParcelNum, double probability) {
        getRecord(pecasParcelNum).derelictProb = probability;
    }
    
    public void logRenovateProbability(long pecasParcelNum, double probability) {
        getRecord(pecasParcelNum).renovateProb = probability;
    }
    
    public void logAddSpaceProbability(long pecasParcelNum, double probability) {
        getRecord(pecasParcelNum).addSpaceProb = probability;
    }
    
    public void logNewSpaceProbability(long pecasParcelNum, int spaceType, double probability) {
        getRecord(pecasParcelNum).newSpaceProbs.put(spaceType, probability);
    }
    
    public void logDevelopment(long pecasParcelNum) {
        logEvent(pecasParcelNum, "C");
    }
    
    public void logDevelopmentWithSplit(long oldPecasParcelNum, long newPecasParcelNum) {
        logEvent(oldPecasParcelNum, newPecasParcelNum, "CS");
    }
    
    public void logDemolition(long pecasParcelNum) {
        logEvent(pecasParcelNum, "D");
    }
    
    public void logDemolitionWithSplit(long oldPecasParcelNum, long newPecasParcelNum) {
        logEvent(oldPecasParcelNum, newPecasParcelNum, "DS");
    }
    
    public void logDereliction(long pecasParcelNum) {
        logEvent(pecasParcelNum, "L");
    }
    
    public void logDerelictionWithSplit(long oldPecasParcelNum, long newPecasParcelNum) {
        logEvent(oldPecasParcelNum, newPecasParcelNum, "LS");
    }
    
    public void logRenovation(long pecasParcelNum) {
        logEvent(pecasParcelNum, "R");
    }
    
    public void logRenovationWithSplit(long oldPecasParcelNum, long newPecasParcelNum) {
        logEvent(oldPecasParcelNum, newPecasParcelNum, "RS");
    }
    
    public void logAddition(long pecasParcelNum) {
        logEvent(pecasParcelNum, "A");
    }
    
    public void logAdditionWithSplit(long oldPecasParcelNum, long newPecasParcelNum) {
        logEvent(oldPecasParcelNum, newPecasParcelNum, "AS");
    }
    
    public void logNoChange(long pecasParcelNum) {
        logEvent(pecasParcelNum, "U");
    }
    
    public void logNoChangeWithSplit(long pecasParcelNum) {
        logEvent(pecasParcelNum, "US");
    }

    /**
     * Logs the choice utilities for the specified parcel even though no event
     * has occurred.
     * 
     * @param pecasParcelNum The parcel number to log
     */
    public void logUtilitiesWithoutEvent(long pecasParcelNum) {
        logEvent(pecasParcelNum, "N");
    }

    /**
     * Indicates to the choice utility logger that this parcel will not be
     * logged at all, allowing its data to be removed from memory.
     * 
     * @param pecasParcelNum The parcel number to clear
     */
    public void clearWithoutLog(long pecasParcelNum) {
        records.remove(pecasParcelNum);
    }
    
    private void logEvent(long pecasParcelNum, String eventType) {
        logEvent(pecasParcelNum, pecasParcelNum, eventType);
    }
    
    private void logEvent(long oldPecasParcelNum, long newPecasParcelNum, String eventType) {
        try {
            UtilityRecord record = records.remove(oldPecasParcelNum);
            
            if (record == null) 
                record = new UtilityRecord(availableSpace);
            
            // Convert the conditional probabilities into absolute probabilities.
            double changeProb = 1 - record.noChangeProb;
            double decayProb = changeProb * (1 - record.growthProb);
            double growthProb = changeProb * record.growthProb;
            double buildProb = growthProb * (1 - record.renovateProb);
            double buildNewProb = buildProb * (1 - record.addSpaceProb);
            
            double demolishProb = decayProb * record.demolishProb;
            double derelictProb = decayProb * record.derelictProb;
            double renovateProb = growthProb * record.renovateProb;
            double addSpaceProb = buildProb * record.addSpaceProb;
            
            StringBuffer newSpaceUtilities = new StringBuffer("");
            StringBuffer newSpaceProbs = new StringBuffer("");
            for (int stid : availableSpace) {
                newSpaceUtilities.append("," + str(record.newSpaceUtilities.get(stid)));
                double newSpaceProb = buildNewProb * record.newSpaceProbs.get(stid);
                newSpaceProbs.append("," + str(newSpaceProb));
            }
            
            detailedLogBuffer.write(
                    oldPecasParcelNum + "," +
                    newPecasParcelNum + "," +
                    eventType + "," +
                    str(record.noChangeUtility) + "," +
                    str(record.demolishUtility) + "," +
                    str(record.derelictUtility) + "," +
                    str(record.renovateUtility) + "," +
                    str(record.addSpaceUtility) +
                    newSpaceUtilities + "," +
                    str(record.changeComputil) + "," +
                    str(record.decayComputil) + "," +
                    str(record.growthComputil) + "," +
                    str(record.buildComputil) + "," +
                    str(record.newSpaceComputil) + "," +
                    str(record.noChangeProb) + "," +
                    str(demolishProb) + "," +
                    str(derelictProb) + "," +
                    str(renovateProb) + "," +
                    str(addSpaceProb) +
                    newSpaceProbs + "\n");
        } catch (IOException e) {
            loggerf.throwFatal(e, "Can't write out choice utility details");
        }
    }
    
    private static final double NINF = Double.NEGATIVE_INFINITY;
    
    private String str(double arg) {
        if (arg == NINF) {
            return "neginf";
        } else {
            return String.format("%.3G", arg);
        }
    }
    
    private static class UtilityRecord {
        double noChangeUtility = NINF;
        double demolishUtility = NINF;
        double derelictUtility = NINF;
        double renovateUtility = NINF;
        double addSpaceUtility = NINF;
        Map<Integer, Double> newSpaceUtilities = new HashMap<>();
        double changeComputil = NINF;
        double decayComputil = NINF;
        double growthComputil = NINF;
        double buildComputil = NINF;
        double newSpaceComputil = NINF;
        double noChangeProb = 0;
        double growthProb = 0;
        double demolishProb = 0;
        double derelictProb = 0;
        double renovateProb = 0;
        double addSpaceProb = 0;
        Map<Integer, Double> newSpaceProbs = new HashMap<>();
        
        UtilityRecord(List<Integer> availableSpace) {
            for (int stid : availableSpace) {
                newSpaceUtilities.put(stid, NINF);
                newSpaceProbs.put(stid, 0.0);
            }
        }
    }
}
