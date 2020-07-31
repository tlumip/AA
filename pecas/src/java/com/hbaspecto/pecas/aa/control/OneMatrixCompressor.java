package com.hbaspecto.pecas.aa.control;

import java.util.Collection;

import com.hbaspecto.models.FutureObject;
import com.hbaspecto.pecas.aa.commodity.Commodity;
import com.hbaspecto.pecas.aa.control.FlowMatrixCompressor.FourCoordinatesI;
import com.pb.common.datafile.TableDataSet;
import com.pb.common.matrix.Matrix;

class OneMatrixCompressor extends FlowMatrixCompressor implements Runnable {
	
    final Commodity c;
    final char direction;
    final int count;
    final int iterations;
    final Matrix m;
    final TableDataSet coords;
    FutureObject worked = new FutureObject();
    Collection<FourCoordinatesI> clustered = null;
    
    OneMatrixCompressor(Commodity cParam, char direction, int count, int iterations, Matrix m, TableDataSet coords) {
    	this.c = cParam;
    	this.direction = direction;
    	this.count = count;
    	this.iterations = iterations;
    	this.m = m;
    	this.coords = coords;
    }

    public void run() {
        try {
        	// this is the method call where we are telling the commodity object
        	// c that the prices have been set, and hence it should
        	// calculate the CommodityZUtility.lastCalculatedUtility values.
			logger.info("Clustering "+direction+" flows into "+count+" arrows for Commodity "+c.getName()+" for visualization in GIS");
             clustered =  compress(m,coords,count,iterations);
        } catch (Exception e) {
            worked.setValue(e);
        }
        if (!worked.isSet()) {
            worked.setValue(new Boolean(true));
        }
    }
    
}