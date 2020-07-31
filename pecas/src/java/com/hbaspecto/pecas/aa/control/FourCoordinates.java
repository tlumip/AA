package com.hbaspecto.pecas.aa.control;

import java.util.Collection;

import com.hbaspecto.pecas.aa.control.FlowMatrixCompressor.FourCoordinatesI;

class FourCoordinates implements FourCoordinatesI {
	double orig_x;
	double orig_y;
	double dest_x;
	double dest_y;
	double weight;
	public double getOrigX() {return orig_x;}
	public double getOrigY() {return orig_y;}
	public double getDestX() {return dest_x;}
	public double getDestY() {return dest_y;}
	public double getWeight() {return weight;}

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
	
}