package com.pb.common.matrix;

import java.io.IOException;

import org.apache.log4j.Logger;

import com.inro.modeller.Session;

public class EmmeApiMatrixReader extends MatrixReader {
	
	private Session mySession = null;
	private static Logger logger = Logger.getLogger(EmmeApiMatrixReader.class);
	
	

	/**
	 * @param initials initials of user
	 * @param bank emme databank
	 * @param path path to emme databank
	 * @param iks internet key server
	 * @param shared whether the connection is shared
	 * @throws IOException
	 */
	/*public EmmeApiMatrixReader(String initials, String bank, String path, String iks, boolean shared) throws IOException {
		// userInitials, emmebank, emmepath, g iks, shared
		mySession = new Session(initials, bank, path, iks, shared);
	}*/

	public EmmeApiMatrixReader(String initials, String bank) throws IOException {
		mySession = new Session(initials, bank);
	}

	@Override
	public Matrix readMatrix(String name) throws MatrixException {
		if (mySession==null) {
			logger.fatal("Tried to read matrix "+name+" from an unopened emme matrix session");
			throw new MatrixException("Tried to read matrix "+name+" from an unopened emme matrix session");
		}
		Matrix m = null;
		// FIXME Emme seems to have "nan" for not-a-number, instead of the Java parseable "NaN", probably this should check for "nan" and parse it as "NaN"
		try {
			String csv = mySession.matrixToCSV(name);
			String[] lines = csv.split("\n");
			int rows = lines.length-1;
			String[] headers = lines[0].split(",");
			int cols = headers.length-1;
			int[] originNums = null;
			if (rows>1) originNums = new int[rows];
			int[] destinationNums = null;
			if (cols >1) {
				destinationNums = new int[cols];
				for (int colHeader = 0;colHeader <destinationNums.length;colHeader++) {
					destinationNums[colHeader]=Integer.valueOf(headers[colHeader+1]);
				}
				float[][] values = new float[rows][cols];
				for (int row=1; row<=rows;row++) {
					String[] vals = lines[row].split(",");
					if (originNums!=null) {
						originNums[row-1]=Integer.valueOf(vals[0]);
						for (int c=1;c<vals.length;c++) {
							try {
								values[row-1][c-1]=Float.valueOf(vals[c]);
							} catch (NumberFormatException e) {
								logger.error("Number format exception row "+row+" col "+c+" emme matrix "+name);
								throw e;
							}
						}
					} else {
						String msg = "Don't know how to deal with single row matrices yet, fortunately this error should never occur because emme reports md's as column matrices too";
						logger.fatal(msg);
						throw new RuntimeException(msg);
					}
				}
				m= new Matrix(name, name, values);
				m.setExternalNumbersZeroBased(originNums, destinationNums);
			} else {
				float[] values = new float[rows];
				for (int row=1; row<=rows;row++) {
					String[] vals = lines[row].split(",");
					originNums[row-1]=Integer.valueOf(vals[0]);
					try {
						values[row-1]=Float.valueOf(vals[1]);
					} catch (NumberFormatException e) {
						logger.error("Number format exception row "+row+" emme matrix "+name);
						throw e;
					}
				}
				m= new ColumnVector(values);
				m.setName(name);
				m.setExternalNumbersZeroBased(originNums,new int[]{1});
			}
			
		} catch (IOException e) {
			logger.error("Could not read matrix "+name);
			throw new MatrixException("Could not read matrix "+name, e);
		} catch (NumberFormatException e) {
			logger.error("Number format exception in reading matrix "+name+" from Emme ", e);
			throw new MatrixException("Number format exception in reading matrix "+name+" from Emme ", e);
		} catch (Exception e) {
			logger.fatal("Problem trying to read matrix "+name+" in Emme", e);
			throw new RuntimeException("Problem trying to read matrix "+name+" in Emme", e);
		}
		return m;
	}

	@Override
	public Matrix readMatrix() throws MatrixException {
		logger.fatal("There is no default matrix for "+EmmeApiMatrixReader.class);
		throw new MatrixException("There is no default matrix for "+EmmeApiMatrixReader.class);
	}

	@Override
	public Matrix[] readMatrices() throws MatrixException {
		logger.fatal("Reading all the matrices is not supported for "+EmmeApiMatrixReader.class);
		throw new MatrixException("Reading all the matrices is not supported for "+EmmeApiMatrixReader.class);
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

	Session getSession() {
		return mySession;
	}

}
