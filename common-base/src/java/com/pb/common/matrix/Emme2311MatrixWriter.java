/*
 * Copyright  2005 PB Consult Inc.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */
package com.pb.common.matrix;

import java.io.BufferedOutputStream;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Iterator;

import org.apache.log4j.Logger;

/**
 * Implements a MatrixWriter to write a matrix to a .311 text file.
 *
 * @author    Joel Freedman
 * @version   1.0, 6/2003
 */
public class Emme2311MatrixWriter extends MatrixWriter {

    protected Logger logger = Logger.getLogger("com.pb.common.matrix");
	private BufferedWriter outStream;



    /**
     * @param file represents the physical matrix file
     */
    public Emme2311MatrixWriter(File file) {
        this.file = file;

    }


    /**
     * Write out the matrix to the file.
     */
    public void writeMatrix(Matrix m) throws MatrixException {
    	try {
        	openFile();
        	writeHeader(m);
        	Iterator rowIterator = new ExternalNumberIterator(m.getExternalRowNumbers());
    		while (rowIterator.hasNext()) {
    			int row = ((Integer)rowIterator.next()).intValue();
    			Iterator colIterator = new ExternalNumberIterator(m.getExternalColumnNumbers());
    			while (colIterator.hasNext()) {
    				int col = ((Integer)colIterator.next()).intValue();
    				outStream.write(" "+row+"   "+col+"   :"+m.getValueAt(row,col)+"\n");
    			}
    		}
    		outStream.close();
    	} catch (IOException e) {
    		logger.error("Problem writing out matrix file",e);
    	}
    }

    private void writeHeader(Matrix m) throws IOException {
    	outStream.write("t matrices\n");
    	outStream.write("blank line\nblank line\n");
    	outStream.write("a matrix=mf");
    	if (m.name!=null) {
    		if (m.name.length()!=0) {
    			outStream.write(m.name+" ");
    		} else {
    			outStream.write("1 ");
    		}
    	} else {
    		outStream.write("1 ");
    	}
    	outStream.write("0.0 \n");
	}


	private void openFile() throws IOException {
		try {
			outStream = new BufferedWriter(new FileWriter(file));
		} catch (FileNotFoundException e) {
			logger.error("Can't open skim output file");
			throw new RuntimeException("Can;t open matrix output file"+file);
		}
	}


	public void writeMatrix(String index, Matrix m) throws MatrixException{
        throw new UnsupportedOperationException("Not yet implemented");
    }

	/** Writes all tables of an entire matrix
	 *  (not implemented for this matrix type.)
	 *
	 */
	public void writeMatrices(String[] maxTables, Matrix[] m) throws MatrixException {
        throw new UnsupportedOperationException("Not yet implemented");
	}




}
