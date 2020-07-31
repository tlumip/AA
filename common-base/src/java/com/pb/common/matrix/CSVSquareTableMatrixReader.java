package com.pb.common.matrix;

import org.apache.log4j.Logger;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
//import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;

/**
 * Implementes a MatrixReader to read matrices from a comma separated value file.
 * The matrix is organized by rows - with the first row being a header row which gives
 * the type of values (i.e. TIME, DIST, etc) in the first entry position, and the
 * destination zones in the subsequent entry positions.
 *
 * The second thru .... rows, have the origin zone in the first entry position and the
 * values in the subsequent entry positions.
 *
 * Currently the reader will return a single matrix.  The code assumes that the matrix
 * is square and that the external origin zone numbers = external destination zone numbers
 *
 * ex. The CSV file looks like this:
 * TIME, 1, 2, 3
 * 1,0.2,5.4,7.6
 * 2,1.1,3.5,0.7
 * 3,0.5,3.5,1.0
 *
 * The motivation for this class is to read in matrices that have been exported by TPPlus.

 *
 * @author    John Abraham
 * @version   1.0, 5/23/2006
 */
public class CSVSquareTableMatrixReader extends MatrixReader {
    
    static final Logger logger = Logger.getLogger(CSVSquareTableMatrixReader.class);

    String myDirectory;
    
    public CSVSquareTableMatrixReader(String myDirectory) {
        super();
        this.myDirectory = myDirectory;
    }
    
    public CSVSquareTableMatrixReader(File myFile) {
		super();
		if (myFile.isDirectory()) {
    		this.myDirectory = myFile.getPath();
    	} else {
    		this.file = myFile;
    		this.myDirectory = myFile.getParent();
    	}
    }

    @Override
    public Matrix[] readMatrices() throws MatrixException {
    	// TODO readMatrices should read every CSV file in the directory.
    	throw new RuntimeException("readMatrices not implemented, need to specify a matrix name");
    }

    @Override
    //TODO  This method needs to verify that the matrix is square and that the
    //TODO origin zones are the same as the destination zones.
    //TODO (Although I don't really see why you have to assume a square matrix - why not use
    //TODO the setRowExternals, setColumnExternals and allow the matrix to be non-square)
    //TODO comments by Christi Willison
    public Matrix readMatrix(String index) throws MatrixException {
    	File file = new File (new File(myDirectory) + File.separator + index + ".csv");
    	BufferedReader inStream = null;
    	try {
    		inStream = new BufferedReader( new FileReader(file) );
    	} catch (FileNotFoundException e) {
    		// check for a stream
    		URL url;
    		try {
    			url = new URL(myDirectory+"/"+ index + ".csv");
    			URLConnection urlConn = url.openConnection();
    			DataInputStream dis = new DataInputStream(urlConn.getInputStream());
    			inStream = new BufferedReader(new InputStreamReader(dis));
    		} catch (Exception e1) {
    			logger.info("Cannot read "+index+" matrix from "+myDirectory+", tried both as file and as URL");
    			throw new RuntimeException("Cannot read "+index+" matrix from "+myDirectory+", tried both as file and as URL", e1);
    		}
    	}
    	return readMatrixFromReader(index, inStream);
    }

	/**
	 * @param index
	 * @param inStream
	 * @return
	 */
	private Matrix readMatrixFromReader(String index, BufferedReader inStream) {
		float[][] values;
    	int[] zones;
    	try {
    		String line =inStream.readLine();
    		String[] entries = line.split(",");
    		int numZones = entries.length-1;
    		zones = new int[numZones];
    		int maxZoneNumber = 0;
    		for (int z=0;z<numZones;z++) {
    			zones[z] = Integer.parseInt(entries[z+1]);
    			if (zones[z] > maxZoneNumber) maxZoneNumber = zones[z];
    		}
    		int[] zoneNumberLookup = new int[maxZoneNumber+1];
    		for (int i=0;i<zoneNumberLookup.length;i++) {
    			zoneNumberLookup[i]=-1;
    		}
    		for (int i=0;i<zones.length;i++) {
    			zoneNumberLookup[zones[i]]=i;
    		}
    		values = new float[numZones][numZones];
    		int rowNumber = 0;
    		while ((line=inStream.readLine())!=null) {
    			entries = line.split(",");
    			int origin = Integer.parseInt(entries[0]);
    			if (++rowNumber%100==0) System.out.println("Parsing origin "+origin);
    			int oIndex = zoneNumberLookup[origin];
    			for (int dIndex = 0;dIndex<entries.length-1;dIndex++) {
    				values[oIndex][dIndex] = Float.parseFloat(entries[dIndex+1]);
    			}
    		}
    		// TODO should check for missing values
    	} catch (IOException e) {
    		logger.error("IO Exception reading matrix");
    		throw new RuntimeException("IO Exception reading matrix",e);
    	} 

    	Matrix m = new Matrix(index,"",values);
    	m.setExternalNumbersZeroBased(zones);
    	return m;
	}

    @Override
    public Matrix readMatrix() throws MatrixException {
    	if (file == null) {
    		throw new RuntimeException("readMatrix() not implemented for directories, need to specify a file name and use readMatrix(String)");
    	}
    	BufferedReader inStream = null;
    	try {
    		inStream = new BufferedReader( new FileReader(file) );
    		return readMatrixFromReader(getBaseName(file.getPath()), inStream);
    	} catch (FileNotFoundException e) {
   			throw new RuntimeException("Cannot read  matrix from "+file, e);
    	}
    }

    // Some path utility routines from http://commons.apache.org/proper/commons-io/apidocs/org/apache/commons/io/FilenameUtils.html
    public static String getName(final String filename) {
    	if (filename == null) {
    		return null;
    	}
    	final int index = indexOfLastSeparator(filename);
    	return filename.substring(index + 1);
    }

    public static int indexOfLastSeparator(final String filename) {
    	if (filename == null) {
    		return -1;
    	}
    	return filename.lastIndexOf(File.separatorChar);
    }
    public static String getBaseName(final String filename) {
    	return removeExtension(getName(filename));
    }
    public static String removeExtension(final String filename) {
    	if (filename == null) {
    		return null;
    	}
    	final int index = indexOfExtension(filename);
    	if (index == -1) {
    		return filename;
    	} else {
    		return filename.substring(0, index);
    	}
    }
    public static int indexOfExtension(final String filename) {
    	if (filename == null) {
    		return -1;
    	}
    	final int extensionPos = filename.lastIndexOf('.');
    	final int lastSeparator = indexOfLastSeparator(filename);
    	return lastSeparator > extensionPos ? -1 : extensionPos;
    }


}
