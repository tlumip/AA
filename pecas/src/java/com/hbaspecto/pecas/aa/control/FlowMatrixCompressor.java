package com.hbaspecto.pecas.aa.control;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Random;

import org.apache.commons.math3.stat.clustering.Cluster;
import org.apache.commons.math3.stat.clustering.Clusterable;
import org.apache.commons.math3.stat.clustering.KMeansPlusPlusClusterer;
import org.apache.log4j.Logger;

import com.pb.common.datafile.CSVFileWriter;
import com.pb.common.datafile.NEW_CSVFileReader;
import com.pb.common.datafile.TableDataFileReader;
import com.pb.common.datafile.TableDataSet;
import com.pb.common.matrix.Matrix;
import com.pb.common.matrix.MatrixReader;


public class FlowMatrixCompressor {

	static Logger logger = Logger.getLogger(FlowMatrixCompressor.class);

	interface FourCoordinatesI extends Clusterable<FourCoordinatesI> {
		double getOrigX();
		double getOrigY();
		double getDestX();
		double getDestY();
		double getWeight();
	};
	
	public static void main(String[] args) {
		// Test method to build a compressed matrix
		String matrixFileName = args[0];
		String centroidCoordinatesFile = args[1];
		String outputFileName = args[2];
		Path p = Paths.get(outputFileName);
		String flowName = p.getFileName().toString();
		if(flowName.contains(".")) flowName = flowName.substring(0, flowName.lastIndexOf('.'));
		int numFlows = Integer.valueOf(args[3]);
		int iterations = 100;
		if (args.length > 4) {
			iterations = Integer.valueOf(args[4]);
		}
		
		FlowMatrixCompressor me = new FlowMatrixCompressor();

		Matrix b = null;
		TableDataSet coords=null;
		try {
			b = MatrixReader.readMatrix(new File(matrixFileName), null);
			TableDataFileReader myReader = new NEW_CSVFileReader();
			coords = myReader.readFile(new File(centroidCoordinatesFile));
		} catch (IOException e) {
			logger.error("Problem reading or writing data in test routine");
			e.printStackTrace();
		}
		
		coords.buildIndex(1);
		ArrayList<Collection<FourCoordinatesI>> flows = new ArrayList<Collection<FourCoordinatesI>>();
		flows.add(me.buildFlowArray(b,coords));
		int[] numFlowArray = new int[2];
		numFlowArray[0] = b.getRowCount()*b.getRowCount();
		Collection<FourCoordinatesI> results = me.compress(flows.get(0),numFlows,iterations);
		flows.add(results);
		numFlowArray[1] = numFlows;
		//if (outputFileName.endsWith("gml")) {
			int srid_epsg = 3857;
			try {
				srid_epsg = Integer.valueOf(args[5]);
			} catch (Exception e) {	
				// just use 3857
			}
			writeGMLFlows(outputFileName, flowName, flows, srid_epsg, outputFileName, numFlowArray);
		//} else {
			//writeCSVCompressedFlows(outputFileName, results[1]);
		//}

	}

	private static void writeGMLFlows(String outputFileName, String flowName, Collection<Collection<FourCoordinatesI>> results, int srid_epsg, String name, int[] count) {
		try {
			BufferedWriter os = new BufferedWriter(new FileWriter(outputFileName));
			os.write(			
					"<?xml version=\"1.0\" encoding=\"utf-8\" ?>\n<Root>\n");
			int featureNumber = 0;
			String fixedName = flowName.replace(' ', '_');
			try {
				// write out  flows, note c.worked is a FutureObject so this will block until the compression is done
				int i = 0;
				for (Collection<FourCoordinatesI> result : results) {
					i++;
					double totalWeight = 0;
					Double xMin = null;
					Double xMax = null;
					Double yMin = null;
					Double yMax = null;
					for (FourCoordinatesI r : result) {
						totalWeight += r.getWeight();
						if (xMin == null) xMin = r.getDestX();
						if (xMax == null) xMax = r.getOrigX();
						if (yMin == null) yMin = r.getDestY();
						if (yMax == null) yMax = r.getOrigY();
						xMin = Math.min(xMin,(Math.min(r.getDestX(),  r.getOrigX())));
						xMax = Math.max(xMax,(Math.max(r.getDestX(),  r.getOrigX())));
						yMin = Math.min(yMin,(Math.min(r.getDestY(),  r.getOrigY())));
						yMax = Math.max(yMax,(Math.max(r.getDestY(),  r.getOrigY())));
					}    				


					os.write(			
							"<ogr:FeatureCollection\n"+
									"    xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n"+
									"    xsi:schemaLocation=\"http://ho.hbaspecto.com http://ho.hbaspecto.com/public/econflows.xsd\""+
									"    xmlns:ogr=\"http://ogr.maptools.org/\"\n"+
							"    xmlns:gml=\"http://www.opengis.net/gml\">\n");


					os.write(  
							"<gml:boundedBy>\n"+
									"  <gml:Box>\n"+
									"	<gml:coord><gml:X>"+xMin+"</gml:X><gml:Y>"+yMin+"</gml:Y></gml:coord>\n"+
									"   <gml:coord><gml:X>"+xMax+"</gml:X><gml:Y>"+yMax+"</gml:Y></gml:coord>\n"+
							" </gml:Box>\n</gml:boundedBy>\n");
					int writeCounter =0;
					for (FourCoordinatesI r : result) {
						os.write("       <gml:featureMember>\n");
						os.write("        <ogr:"+fixedName+"_"+count[i-1]+" fid=\"F"+featureNumber+"\">\n");
						featureNumber++;
						os.write("         <ogr:geometryProperty>\n");
						os.write("           <gml:LineString  srsName=\"epsg:" + srid_epsg + "\"><gml:coordinates>"+r.getOrigX()+","+r.getOrigY()+" "+r.getDestX()+","+r.getDestY()+"</gml:coordinates></gml:LineString>\n");
						//os.write("       <feature:geometry>\n");
						os.write("         </ogr:geometryProperty>\n");
						os.write("         <ogr:FLOW>"+flowName+"</ogr:FLOW>\n");
						os.write("         <ogr:CLUSTERCOUNT>"+count[i-1]+"</ogr:CLUSTERCOUNT>\n");
						os.write("         <ogr:VALUE>"+r.getWeight()+"</ogr:VALUE>\n");
						os.write("         <ogr:PERCENTVALUE>"+r.getWeight() / totalWeight+"</ogr:PERCENTVALUE>\n");
						os.write("        </ogr:"+fixedName+"_"+count[i-1]+">\n");
						os.write("       </gml:featureMember>\n");
					    writeCounter ++;
						//if (writeCounter > 10) break; // for testing
					}
					os.write("</ogr:FeatureCollection>\n");
				}
			} catch (Exception e) {
				logger.error("Problem writing compressed flows", e);
			} finally {
				//os.write("</feature:feature>\n");
			}
			os.write("</Root>\n");
			os.close();
		} catch (IOException e) {
			logger.error("Problem opening or closing files", e);
		}

	}
	
	private static void writeCSVCompressedFlows(String outputFileName,
			Collection<FourCoordinatesI> results) {
		TableDataSet compressedFlows = new TableDataSet();
		float[] orig_x_col = new float[results.size()];
		float[] orig_y_col = new float[results.size()];
		float[] dest_x_col = new float[results.size()];
		float[] dest_y_col = new float[results.size()];
		float[] flow_col = new float[results.size()];
		
		int row = 0;
		for (FourCoordinatesI f : results) {
			orig_x_col[row] = (float) f.getOrigX();
			orig_y_col[row] = (float) f.getOrigY();
			dest_x_col[row] = (float) f.getDestX();
			dest_y_col[row] = (float) f.getDestY();
			flow_col[row] = (float) f.getWeight();
			row++;
		}
		
		compressedFlows.appendColumn(orig_x_col,"orig_x");
		compressedFlows.appendColumn(orig_y_col,"orig_y");
		compressedFlows.appendColumn(dest_x_col,"dest_x");
		compressedFlows.appendColumn(dest_y_col,"dest_y");
		compressedFlows.appendColumn(flow_col,"flow");
		
		CSVFileWriter writer = new CSVFileWriter();
		try {
			writer.writeFile(compressedFlows, new File(outputFileName));
		} catch (IOException e) {
			logger.error("Couldn't write output file");
			e.printStackTrace();
		}
	}

    Collection<FourCoordinatesI> compress(Matrix b, TableDataSet coords, int numClusters, int iterations) {
		
		Collection<FourCoordinatesI> myFlows = buildFlowArray(b, coords);
		
		return compress(myFlows, numClusters, iterations);
    }
    
    Collection<FourCoordinatesI> compress(Collection<FourCoordinatesI> myFlows, int numClusters, int iterations) {
	    
		if (myFlows.size() == 0) {
		    return null;
		}
		if (myFlows.size() < numClusters) {
		    numClusters = myFlows.size();
		}
		
		KMeansPlusPlusClusterer<FourCoordinatesI> clusterer = new KMeansPlusPlusClusterer<FourCoordinatesI>(new Random());

		List<Cluster<FourCoordinatesI>> clusters = clusterer.cluster(myFlows, numClusters, iterations);

		ArrayList<FourCoordinatesI> resultArrows = new ArrayList<FourCoordinatesI>();
		
		for (Cluster<FourCoordinatesI> cluster : clusters) {
			resultArrows.add(cluster.getCenter());
		}
		return resultArrows;
		
	}

	private Collection<FourCoordinatesI> buildFlowArray(Matrix b,
			TableDataSet coords) {
		coords.buildIndex(1);
		Collection<FourCoordinatesI> myFlows = new ArrayList<FourCoordinatesI>();
		for (int i=1;i<b.externalRowNumbers.length;i++) {
			int x=b.externalRowNumbers[i];			
			try {
				int row = coords.getIndexedRowNumber(x);
				if (row == 0) throw new RuntimeException();
				for (int j = 1; j< b.externalColumnNumbers.length;j++) {
					int y = b.externalColumnNumbers[j];
					try {
						int col = coords.getIndexedRowNumber(y);
						if (col == 0) throw new RuntimeException();
						if (b.getValueAt(x,y)!=0) { // zero values mess up the GIS, waste of time anyways.
							FlowValue newFlow = new FlowValue(x,y,b.getValueAt(x,y));
							newFlow.setCoords(coords);
							myFlows.add(newFlow);
						}
					} catch (Exception e) {
						logger.error("Can't handle column "+y+" in clustering flows",e);
					}
				}
			} catch (Exception e) {
				logger.error("Can't handle row "+x+" in clustering flows",e);
			}
		}
		return myFlows;
	}
	

}
