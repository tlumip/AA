package com.hbaspecto.pecas.aa.control;

import java.util.Collection;

import org.apache.commons.math3.stat.clustering.Clusterable;

import com.hbaspecto.pecas.aa.control.FlowMatrixCompressor.FourCoordinatesI;
import com.pb.common.datafile.TableDataSet;

class FlowValue implements Clusterable<FourCoordinatesI>, FourCoordinatesI {

	FlowValue(int orig,int dest,double flow) {
		orig_zone = orig;
		dest_zone = dest;
		flow_weight= flow;
	}
	int orig_zone, dest_zone;
	double flow_weight;

	// these could be static
	TableDataSet coords;
	
	void setCoords(TableDataSet coords) {
		this.coords = coords;
	}

	public double getOrigX() {
		return coords.getIndexedValueAt(orig_zone,2);
	}

	public double getOrigY() {
		return coords.getIndexedValueAt(orig_zone,3);
	}

	public double getDestX() {
		return coords.getIndexedValueAt(dest_zone,2);
	}

	public double getDestY() {
		return coords.getIndexedValueAt(dest_zone,3);
	}


	@Override
	public FourCoordinatesI centroidOf(Collection<FourCoordinatesI> p) {

		FourCoordinates fc = new FourCoordinates();
		fc.weight = 0;

		for (FourCoordinatesI obj : p) {
			double newWeight = fc.weight+obj.getWeight();
			fc.dest_x = (fc.dest_x*fc.weight + obj.getDestX()*obj.getWeight())/newWeight;
			fc.dest_y = (fc.dest_y*fc.weight + obj.getDestY()*obj.getWeight())/newWeight;
			fc.orig_x = (fc.orig_x*fc.weight + obj.getOrigX()*obj.getWeight())/newWeight;
			fc.orig_y = (fc.orig_y*fc.weight + obj.getOrigY()*obj.getWeight())/newWeight;
			fc.weight = newWeight;
		}
		return fc;
	}

	@Override
	public double distanceFrom(FourCoordinatesI o) {
		return Math.sqrt(
				(getDestX()-o.getDestX())*(getDestX()-o.getDestX()) +
				(getDestY()-o.getDestY())*(getDestY()-o.getDestY()) +					
				(getOrigX()-o.getOrigX())*(getOrigX()-o.getOrigX()) +		
				(getOrigY()-o.getOrigY())*(getOrigY()-o.getOrigY()));	

	}



	@Override
	public double getWeight() {
		return flow_weight;
	}

}