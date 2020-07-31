/*
 *  Copyright 2005 HBA Specto Incorpoated
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */
package com.hbaspecto.pecas.zones;

import org.apache.log4j.Logger;

import java.util.Random;

abstract public class AbstractZone implements Zone, Comparable<AbstractZone> {

    protected static transient Logger logger =
        Logger.getLogger("com.pb.models.pecas");
    
    //this could be much smaller as the max zone number is less than 5000
    public static final int maxZoneNumber=70000;

    protected AbstractZone(int i,int userNumber) {
        if (userNumber < 0 || userNumber >= maxZoneNumber) {
            logger.fatal("ZoneUserNumber "+userNumber+" too big, max set at "+maxZoneNumber);
            throw new RuntimeException("ZoneUserNumber "+userNumber+" too big, max set at "+maxZoneNumber);
        }
        zoneIndex = i;
        allZones[i] = this;
        allZonesByUserNumber[userNumber] = this;
        priceVacancies = new PriceVacancy[0];
    }

    public static AbstractZone[] getAllZones() {
//      AbstractZone[] otherZoneArray = new AbstractZone[allZones.length];
//      System.arraycopy(allZones,0,otherZoneArray,0,allZones.length);
//      return otherZoneArray;
    	return allZones;
    }
    
    public static AbstractZone getZone(int index) {
        return allZones[index];
    }

    private void checkSpaceAccountingArray(int dtID) {
    	if (priceVacancies.length <= dtID) {
    		PriceVacancy[] oldPriceVacancies = priceVacancies;
    		priceVacancies = new PriceVacancy[dtID+1];
    		System.arraycopy(oldPriceVacancies, 0, priceVacancies, 0, oldPriceVacancies.length);
    	}
    	if(priceVacancies[dtID] ==null) priceVacancies[dtID] = new PriceVacancy();
    } 

    static public AbstractZone findZone(UnitOfLand l) {
        if (l instanceof AbstractZone) {
            return (AbstractZone)l;
        } else {
            return null;
        }
    }

    public int getZoneIndex() { return zoneIndex; }

    public String toString() { return "PECASZone:" + getZoneUserNumber(); };

    public int hashCode() { return zoneIndex; };

    public static void createZoneArray(int numZones) {
        allZones = new AbstractZone[numZones];
        allZonesByUserNumber = new AbstractZone[maxZoneNumber];
    }

    /** Update the price based on the vacancy rate. */
  
     public void updatePrice(int dt, double newPrice) {
    	
    	checkSpaceAccountingArray(dt);
    	priceVacancies[dt].price = newPrice;
    }

    public int compareTo(AbstractZone other){
        if (other.zoneIndex<zoneIndex ) return 1;
        if (other.zoneIndex == zoneIndex ) return 0;
        return -1;
    }

    public static AbstractZone findZoneByUserNumber(int zoneUserNumber) {
        if (zoneUserNumber <0 || zoneUserNumber >= maxZoneNumber) return null;
        return allZonesByUserNumber[zoneUserNumber];
    }

    private static AbstractZone[] allZones;
    private static AbstractZone[] allZonesByUserNumber;
    public final int zoneIndex;
    private PriceVacancy[] priceVacancies;
    static protected Random theRandom = new java.util.Random();

    /**
     * Goes through the grid cells that have the development type and finds out how many vacant parcels
     * or vacant space there is.
     */
    public static class PriceVacancy {
        private double price;
        private double vacancy;
        private double totalSize;

        public PriceVacancy() {
        };
        public String toString() {return "price:"+price+" vacnt:"+vacancy+" size:"+totalSize;}
        /**
         * Returns the price.
         * @return double
         */
        public double getPrice() {
            return price;
        }

        /**
         * Returns the totalSize.
         * @return double
         */
        public double getTotalSize() {
            return totalSize;
        }

        /**
         * Returns the vacancy.
         * @return double
         */
        public double getVacancy() {
            return vacancy;
        }

    };


    @SuppressWarnings("serial")
    public static class CantFindRoomException extends Exception {
      public CantFindRoomException(String s) {
        super(s);
      }
    }
}
