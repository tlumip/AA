package com.pb.common.matrix;

import java.io.FileWriter;
import java.io.IOException;
import java.util.Iterator;

import org.apache.log4j.Logger;

import com.inro.modeller.Session;

public class EmmeApiMatrixWriter extends MatrixWriter {
	
	private Session mySession = null;
	private static Logger logger = Logger.getLogger(EmmeApiMatrixWriter.class);
	
	

	/*
	 * @param initials users initials
	 * @param bank emme databank
	 * @param path path to emme databank
	 * @param iks internet key server
	 * @param shared whether the databank is shared
	 * @throws IOException
	 
	public EmmeApiMatrixWriter(String initials, String bank, String path, String iks, boolean shared) throws IOException {
		mySession = new Session(initials, bank, path, iks, shared);
	}
	*/
	
	/**
	 * @param initials users initials
	 * @param bank the emme databank
	 * @throws IOException 
	 */
	public EmmeApiMatrixWriter(String initials, String bank) throws IOException {
		mySession = new Session(initials, bank);
	}

	
	/**
	 * Uses the session from the reader.   Note that if you close the reader the writer's 
	 * session will close, and vice versa.  It doesn't create a duplicate session.
	 * @param emmeApiMatrixReader
	 */
	public EmmeApiMatrixWriter(EmmeApiMatrixReader emmeApiMatrixReader) {
		mySession = emmeApiMatrixReader.getSession();
	}

	public void close() throws IOException {
		try {
			mySession.close();
		} finally {
			mySession=null;
		}
	}
	
	protected void finalize() {
		if (mySession !=null) {
			try {
				mySession.close();
			} catch (IOException e) {
				logger.error("Coulnd't properly close emme session in finalizer");
			} finally {
				mySession = null;
			}
		}
		try {
			super.finalize();
		} catch (Throwable e) {
			// nothing?
		}
	}

	@Override
	public void writeMatrix(Matrix m) throws MatrixException {
		writeMatrix(m.name,m);
	}

	@Override
	public void writeMatrix(String name, Matrix m) throws MatrixException {
		if (mySession==null) {
			logger.fatal("Tried to read matrix "+name+" from an unopened emme matrix session");
			throw new MatrixException("Tried to read matrix "+name+" from an unopened emme matrix session");
		}
		try {
			StringBuffer csv = new StringBuffer();
			if (m.nCols>1) {
				csv.append("p/q/[val]");
				Iterator<Integer> it = m.getExternalNumberIterator();
				while (it.hasNext()) {
					int colnum = it.next();
					csv.append(",");
					csv.append(colnum);				
				}
			} else {
				csv.append("index,value");
			}
			csv.append("\n");
			for (int row=0;row<m.nRows;row++) {
				int orig = m.getExternalRowNumber(row);
				csv.append(orig);
				for (int col=0;col<m.nCols;col++) {
					int dest = m.getExternalColumnNumber(col);
					csv.append(",");
					csv.append(m.getValueAt(orig,dest));
				}
				csv.append("\n");
			}
			//FileWriter fstream = new FileWriter("csv.txt");
			//fstream.write(csv.toString());
			//fstream.close();
			mySession.matrixFromCSV(name, csv.toString());
			
			
//			String[] lines = csv.split("\n");
//			int rows = lines.length-1;
//			String[] headers = lines[0].split(",");
//			int cols = headers.length-1;
//			int[] originNums = null;
//			if (rows>1) originNums = new int[rows];
//			int[] destinationNums = null;
//			if (cols >1) {
//				destinationNums = new int[cols];
//				for (int colHeader = 0;colHeader <destinationNums.length;colHeader++) {
//					destinationNums[colHeader]=Integer.valueOf(headers[colHeader+1]);
//				}
//				float[][] values = new float[rows][cols];
//				for (int row=1; row<=rows;row++) {
//					String[] vals = lines[row].split(",");
//					if (originNums!=null) {
//						originNums[row-1]=Integer.valueOf(vals[0]);
//						for (int c=1;c<vals.length;c++) {
//							values[row-1][c-1]=Float.valueOf(vals[c]);
//						}
//					} else {
//						String msg = "Don't know how to deal with single row matrices yet, fortunately this error should never occur because emme reports md's as column matrices too";
//						logger.fatal(msg);
//						throw new RuntimeException(msg);
//					}
//				}
//				m= new Matrix(name, name, values);
//				m.setExternalNumbersZeroBased(originNums, destinationNums);
//			} else {
//				float[] values = new float[rows];
//				for (int row=1; row<=rows;row++) {
//					String[] vals = lines[row].split(",");
//					originNums[row-1]=Integer.valueOf(vals[0]);
//					values[row-1]=Float.valueOf(vals[1]);
//				}
//				m= new ColumnVector(values);
//				m.setName(name);
//				m.setExternalNumbersZeroBased(originNums);
//			}
//			
		} catch (IOException e) {
			logger.error("Could not write matrix "+name);
			throw new MatrixException("Could not write matrix "+name, e);
		} catch (Exception e) {
			logger.error("Problem trying to write matrix "+name+" in Emme", e);
			throw new RuntimeException("Problem trying to write matrix "+name+" in Emme", e);
		}
	}

	@Override
	public void writeMatrices(String[] names, Matrix[] m)
			throws MatrixException {
		for (int i=0;i<names.length;i++) {
			writeMatrix(names[i],m[i]);
		}
		
	}

}
