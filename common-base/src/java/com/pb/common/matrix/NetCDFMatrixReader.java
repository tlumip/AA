package com.pb.common.matrix;

import java.io.File;
import java.io.IOException;
import java.util.List;

import org.apache.log4j.Logger;

//import ucar.ma2.Array;
//import ucar.ma2.DataType;
//import ucar.ma2.InvalidRangeException;
//import ucar.ma2.StructureData;
//import ucar.nc2.Attribute;
//import ucar.nc2.Group;
//import ucar.nc2.NetcdfFile;
//import ucar.nc2.Variable;

public class NetCDFMatrixReader extends MatrixReader {
	
	public static void main(String args[]) {
		NetCDFMatrixReader r = new NetCDFMatrixReader(new File("/Volumes/ProjectWork/CSTDM2009 105073x4/Technical/Skims and OD/skims.h5"));
		r.readMatrices();
	}

	static Logger logger = Logger.getLogger(NetCDFMatrixReader.class);

	File theFile;
	
	public NetCDFMatrixReader(File netCDFFile) {
		theFile = netCDFFile;
	}

	@Override
	public Matrix[] readMatrices() throws MatrixException {
//		try {
//			NetcdfFile f = null;
//			try {
//				f = NetcdfFile.open(theFile.getAbsolutePath());
//				List<Variable> variables = f.getVariables();
//				for (Variable v : variables) {
//					logger.info("Reading HD5 skims of type "+v.getName());
//					
//					// datatype
//					 DataType dataType = v.getDataType();
//					 logger.info("Datatype is "+dataType);
//					 
//					 if (dataType == DataType.STRUCTURE) {
//						// nothing
//					 }
//					
//
//					// shape
//					int[] p = v.getShape();
//				    StringBuffer s = new StringBuffer("Shape is ");
//				    for (int i: p) s.append(i+",");
//					logger.info("Shape is "+s);
//					
//					// attributes
//					List<Attribute> attribs = v.getAttributes();
//					logger.info("Attributes are:") ;
//					for (Attribute attribute : attribs) {
//						logger.info("   "+attribute);
//					}
//					//Array r = v.read();
//					//logger.info(" it is of type "+r.getElementType());
//					
//					// first 10 lines
//					int[] origins = new int[] {0};
//					int[] sizes = new int[] {10};
//					try {
//						Array r = v.read(origins, sizes);
//						while (r.hasNext()) {
//							StructureData thing = (StructureData) r.next();
//							logger.info("Java conversion is "+thing.getClass());
//							// how to deal with this structure?
//						}
//						
//					} catch (InvalidRangeException e) {
//						logger.warn("Not 10 lines in "+v.getNameAndDimensions());
//					}
//					
//				}
//			} finally {
//				if (f!=null) f.close();
//			}
//			return null;
//		} catch (IOException e) {
//			String msg = "Can't open NetCDF file "+theFile;
//			logger.fatal(msg);
//			throw new RuntimeException(msg);
//		}
		return null;
	}

	@Override
	public Matrix readMatrix(String name) throws MatrixException {
		// FIXME Auto-generated method stub
		return null;
	}

	@Override
	public Matrix readMatrix() throws MatrixException {
		// FIXME Auto-generated method stub
		return null;
	}

}
