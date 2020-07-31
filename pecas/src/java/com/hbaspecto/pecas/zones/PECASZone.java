/*
 *  Copyright 2005 HBA Specto Incorporated
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import com.pb.common.datafile.TableDataSet;

/**
 * A class that represents a transport analysis zone -  a higher level amount of land.
 *
 * @author J. Abraham
 */
public class PECASZone extends AbstractZone implements UnitOfLand {

    public int zoneUserNumber;
    String zoneName;
    private boolean external;
    final Float xCoordinate;
    final Float yCoordinate;
    
    private static final Map<Integer, PECASZone> indexMap = new TreeMap<>();
    private static final Map<Integer, PECASZone> userNumberMap = new HashMap<>();

    /**
     * private constructor to ensure that only one zone of each zone number is created
     */
    private PECASZone(int index, int zUserNumber, String zname, boolean external, Float x, Float y) {
        super(index, zUserNumber);
        zoneUserNumber = zUserNumber;
        zoneName = zname;
        this.setExternal(external);
        xCoordinate = x;
        yCoordinate = y;
    }


    public static PECASZone createZone(int zoneIndex, int zoneUserNumber, String zoneName, boolean external, Float xCoordinate, Float yCoordinate) {
        AbstractZone zones[] = getAllZones();
        if (zoneIndex >= zones.length || zoneIndex < 0)
            throw new Error("Need to index zones consecutively within the allocated array size");
        if (zones[zoneIndex] != null) throw new Error("Attempt to create zone with index" + zoneIndex + " more than once");
        PECASZone zone = new PECASZone(zoneIndex, zoneUserNumber, zoneName,external, xCoordinate, yCoordinate);
        indexMap.put(zoneIndex, zone);
        userNumberMap.put(zoneUserNumber, zone);
        return zone;
    }

    public int getZoneUserNumber() {
        return zoneUserNumber;
    }
    
    public static PECASZone getPECASZoneByIndex(int index) {
        return indexMap.get(index);
    }
    
    public static PECASZone getPECASZoneByUserNumber(int userNumber) {
        return userNumberMap.get(userNumber);
    }
    
    public static List<PECASZone> getAllPECASZones() {
        return new ArrayList<>(indexMap.values());
    }
    
    public Float getXCoordinate() {
    	return xCoordinate;
    }

    public Float getYCoordinate() {
    	return yCoordinate;
    }
    
    public boolean equals(Object o) {
        if (!(o instanceof PECASZone)) {
            return false;
        }
        PECASZone other = (PECASZone) o;
        if (other.zoneIndex == this.zoneIndex) {
            return true;
        }
        return false;
    }
    
    public int hashCode() {
        return zoneIndex;
    }

    public static void setUpZones(TableDataSet ztab) {
        //inner class to set up the name/number pair of each exchange zone
        class NumberName {
            String zoneName;
            int zoneNumber;
            boolean external;
            Float xCoord = null;
            Float yCoord = null;

            public NumberName(int znum, String zname, boolean external) {
                zoneName = zname;
                zoneNumber = znum;
                this.external = external;
            }
        };
        //Reads the betazone table, creates a NumberName object that
        //is temporarily stored in an array list.  Seems like
        //this step could be skipped, the tempZoneStorage.size() = ztab.getRowCount()
        //so you could create the AbstractZone array, then read in each row and create
        //the PECASZone on the fly and store it in the array.  That would eliminate
        //the call to the array list for the NumberName object.
        ArrayList<NumberName> tempZoneStorage = new ArrayList<>();
        int externalColumn = ztab.getColumnPosition("External");
        if (externalColumn==-1) {
            logger.warn("No External column in PECASZonesI -- flows will be allowed between all zone pairs and histograms will include all zones");
        } else {
            logger.info("External column found in PECASZonesI -- flows will be disallowed between pairs of external zones");
        }
        int xCoordColumn = ztab.getColumnPosition("XCoordinate");
        int yCoordColumn = ztab.getColumnPosition("YCoordinate");
        if (xCoordColumn == -1 || yCoordColumn == -1) {
        	logger.warn("No xCoordinate or yCoordinate found in PECASZonesI, PECAS will not be able to write out clustered flow visualizations even if aa.createCompressedFlowVisuals is set to true");
        }
        for (int row = 1; row <= ztab.getRowCount(); row++) {
            String zoneName = ztab.getStringValueAt(row, "ZoneName");
            int zoneNumber = (int) ztab.getValueAt(row, "ZoneNumber");
            boolean external = false;
            if (externalColumn !=-1) external = ztab.getBooleanValueAt(row, externalColumn);
            NumberName nn = new NumberName(zoneNumber, zoneName,external);
            if (xCoordColumn >=0) {nn.xCoord = ztab.getValueAt(row, xCoordColumn);}
            if (yCoordColumn >=0) {nn.yCoord = ztab.getValueAt(row, yCoordColumn);}
            tempZoneStorage.add(nn);
        }
        //this creates an empty AbstractZone[] which is in fact an array of land use zone (luz)s which
        //are the exchange zones refered to throughout the AA Model
        PECASZone.createZoneArray(tempZoneStorage.size());
        for (int z = 0; z < tempZoneStorage.size(); z++) {
            NumberName nn = (NumberName) tempZoneStorage.get(z);
            //this creates a PECASZone object and stores it in the AbstractZone array
            PECASZone.createZone(z, nn.zoneNumber, nn.zoneName, nn.external, nn.xCoord, nn.yCoord);
        }
    }

    /**
     * Gets a list of the external zones.   Returns null if there are no external zones
     * @return list of external zones, or null if no external zones
     */
    public static int[] getOverrideExternalZones() {
        AbstractZone[] zones = AbstractZone.getAllZones();
        ArrayList<Integer> externalZones = new ArrayList<Integer>();
        for (int z= 0;z< zones.length;z++) {
            if (zones[z] instanceof PECASZone) {
                if (((PECASZone) zones[z]).isExternal()) {
                    externalZones.add(new Integer(zones[z].getZoneUserNumber()));
                }
            }
        }
        if (externalZones.size()==0) return null;
        int[] theOnes = new int[externalZones.size()];
        for (int i =0;i<theOnes.length;i++) {
            theOnes[i] = externalZones.get(i).intValue();
        }
        return theOnes;
    }

	public void setExternal(boolean external) {
		this.external = external;
	}

	public boolean isExternal() {
		return external;
	}
}

