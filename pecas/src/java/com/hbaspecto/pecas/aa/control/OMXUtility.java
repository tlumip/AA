package com.hbaspecto.pecas.aa.control;

import java.nio.file.Path;

import com.hbaspecto.pecas.aa.commodity.Commodity;
import com.hbaspecto.pecas.zones.PECASZone;
import com.pb.common.matrix.Matrix;

import omx.OmxFile;
import omx.OmxLookup;
import omx.OmxMatrix;

/**
 * Class that insulates OMX dependency from other code, so the OMX libraries do not need to be available
 * unless aa.omxMatrices is set to true.
 * @author Graham Hill
 */
public class OMXUtility {
    private OmxFile omxFile;
    
    /**
     * Creates the flow file internally, ready to be written to.
     * @param p The path of the file to create
     * @param zones The array of PECAS zones
     */
    public void createOMXFlowFile(Path p, PECASZone[] zones) {
        omxFile = new OmxFile(p);
        int[] userZones = new int[zones.length];
        for (int z = 0; z<zones.length;z++) {
            userZones[zones[z].zoneIndex] = zones[z].zoneUserNumber;
        }
        OmxLookup.OmxIntLookup userZonesLookup = new OmxLookup.OmxIntLookup("zone number",userZones, null);
        int[] shape = new int[2];
        shape[0] = userZones.length;
        shape[1] = userZones.length;
        omxFile.openNew(shape);
        omxFile.addLookup(userZonesLookup);
    }
    
    public void writeFlowOMXMatrices(Commodity com, Matrix b, Matrix s) {
        if (b!=null) {
            OmxMatrix.OmxFloatMatrix mat = new OmxMatrix.OmxFloatMatrix("buying_"+com.name, b.getValues(), null);
            mat.setAttribute("direction", "buying");
            mat.setAttribute("commodity", com.name);
            omxFile.addMatrix(mat);
        }
        
        if (s!=null) {
            OmxMatrix.OmxFloatMatrix mat = new OmxMatrix.OmxFloatMatrix("selling_"+com.name, s.getValues(), null);
            mat.setAttribute("direction", "selling");
            mat.setAttribute("commodity", com.name);
            omxFile.addMatrix(mat);
        }
    }
    
    public void save() {
        omxFile.save();
    }
    
    public String summary() {
        return omxFile.summary();
    }
    
    public void close() {
        omxFile.close();
    }
}
