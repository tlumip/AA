package com.hbaspecto.pecas.sd;

import java.util.List;

public class NullChoiceUtilityLog extends ChoiceUtilityLog {

    @Override
    public void open(String fileNameAndPath, List<Integer> availableSpace) {
    }

    @Override
    public void close() {
    }

    @Override
    public void flush() {
    }

    @Override
    public void logNoChangeUtility(long pecasParcelNum, double utility) {
    }

    @Override
    public void logDemolishUtility(long pecasParcelNum, double utility) {
    }

    @Override
    public void logDerelictUtility(long pecasParcelNum, double utility) {
    }

    @Override
    public void logRenovateUtility(long pecasParcelNum, double utility) {
    }

    @Override
    public void logAddSpaceUtility(long pecasParcelNum, double utility) {
    }

    @Override
    public void logNewSpaceUtility(long pecasParcelNum, int spaceType,
            double utility) {
    }

    @Override
    public void logChangeCompositeUtility(long pecasParcelNum, double utility) {
    }

    @Override
    public void logDecayCompositeUtility(long pecasParcelNum, double utility) {
    }

    @Override
    public void logGrowthCompositeUtility(long pecasParcelNum, double utility) {
    }

    @Override
    public void logBuildCompositeUtility(long pecasParcelNum, double utility) {
    }

    @Override
    public void logNewSpaceUtility(long pecasParcelNum, double utility) {
    }

    @Override
    public void logNoChangeProbability(long pecasParcelNum,
            double probability) {
    }

    @Override
    public void logGrowthProbability(long pecasParcelNum, double probability) {
    }

    @Override
    public void logDemolishProbability(long pecasParcelNum,
            double probability) {
    }

    @Override
    public void logDerelictProbability(long pecasParcelNum,
            double probability) {
    }

    @Override
    public void logRenovateProbability(long pecasParcelNum,
            double probability) {
    }

    @Override
    public void logAddSpaceProbability(long pecasParcelNum,
            double probability) {
    }

    @Override
    public void logNewSpaceProbability(long pecasParcelNum, int spaceType,
            double probability) {
    }

    @Override
    public void logDevelopment(long pecasParcelNum) {
    }

    @Override
    public void logDevelopmentWithSplit(long oldPecasParcelNum,
            long newPecasParcelNum) {
    }

    @Override
    public void logDemolition(long pecasParcelNum) {
    }

    @Override
    public void logDemolitionWithSplit(long oldPecasParcelNum,
            long newPecasParcelNum) {
    }

    @Override
    public void logDereliction(long pecasParcelNum) {
    }

    @Override
    public void logDerelictionWithSplit(long oldPecasParcelNum,
            long newPecasParcelNum) {
    }

    @Override
    public void logRenovation(long pecasParcelNum) {
    }

    @Override
    public void logRenovationWithSplit(long oldPecasParcelNum,
            long newPecasParcelNum) {
    }

    @Override
    public void logAddition(long pecasParcelNum) {
    }

    @Override
    public void logAdditionWithSplit(long oldPecasParcelNum,
            long newPecasParcelNum) {
    }

    @Override
    public void logNoChange(long pecasParcelNum) {
    }

    @Override
    public void logNoChangeWithSplit(long pecasParcelNum) {
    }

    @Override
    public void logUtilitiesWithoutEvent(long pecasParcelNum) {
    }

    @Override
    public void clearWithoutLog(long pecasParcelNum) {
    }

}
