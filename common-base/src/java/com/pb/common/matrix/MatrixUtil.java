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

/**
 * A collection of matrix utility functions.
 *
 * @author    Tim Heier
 * @version   1.0, 5/11/2003
 *
 */
public class MatrixUtil {

    /**
     * Print the matrix values to system.out.
     *
     * @param matrix matrix to print
     * @param format controls the display format (e.g. 8.3 or 6.0)
     */
    public static void print(Matrix matrix, String format) {
        for (int r = 0; r < matrix.getRowCount(); ++r) {
            System.out.printf("%4d:", matrix.getExternalRowNumber(r));

            for (int c = 0; c < matrix.getColumnCount(); ++c) {
                System.out.printf(format, matrix.values[r][c]);
            }
            System.out.println();
        }
    }
    
    public static double[][] getExternalDoubleMatrix(Matrix m) {
    	int maxZone = 0;
    	for (int i : m.getExternalColumnNumbers()) {
    		maxZone = Math.max(maxZone,i);
    	}
    	for (int i : m.getExternalRowNumbers()) {
    		maxZone = Math.max(maxZone,i);
    	}
    	double[][] values = new double[maxZone+1][maxZone+1];
    	for (int r= 0;r<=maxZone;r++) {
	        int internalRow = m.getInternalRowNumber(r);
    		for (int c=0;c<=maxZone;c++) {
    	        int internalColumn = m.getInternalColumnNumber(c);
    	        if (internalColumn > 0 && internalRow >0) values[r][c]= m.getValueAt(r, c);
    		}
    	}
    	return values;
    }

	public static double[][] getInternalDoubleMatrix(Matrix m) {
		float[][] fValues = m.values;
		double[][] dValues = new double[fValues.length][];
		for (int o = 0; o < fValues.length; o++) {
			dValues[o] = new double[fValues[o].length];
			for (int d = 0;d<fValues[o].length;d++) {
				dValues[o][d] = (double) fValues[o][d];
			}
		}
		return dValues;
	}

}
