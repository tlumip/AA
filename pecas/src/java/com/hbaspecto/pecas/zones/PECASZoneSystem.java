package com.hbaspecto.pecas.zones;

import java.util.List;

public enum PECASZoneSystem implements ZoneSystem<PECASZone> {
    INSTANCE;
    
    @Override
    public PECASZone getZoneByIndex(int index) {
        return PECASZone.getPECASZoneByIndex(index);
    }

    @Override
    public PECASZone getZoneByUserNumber(int userNumber) {
        return PECASZone.getPECASZoneByUserNumber(userNumber);
    }

    @Override
    public List<PECASZone> getAllZones() {
        return PECASZone.getAllPECASZones();
    }

}
