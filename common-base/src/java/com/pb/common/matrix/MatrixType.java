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

import java.io.Serializable;

import org.apache.log4j.Logger;

/**
 * Defines a type-safe enumeration of matrix types supported in the matrix package.
 *
 * @author    Tim Heier
 * @version   1.0, 1/11/2003
 */
public final class MatrixType implements Serializable {
    
    static final Logger logger = Logger.getLogger(MatrixType.class);

    public static final MatrixType BINARY = new MatrixType("Binary");
    public static final MatrixType ZIP = new MatrixType("ZIP");
    public static final MatrixType CSV = new MatrixType("CSV");
    public static final MatrixType EMME2 = new MatrixType("Emme2");
    public static final MatrixType D311 = new MatrixType("D311");
    public static final MatrixType TPPLUS = new MatrixType("TPPlus");
    public static final MatrixType TRANSCAD = new MatrixType("Transcad");
    public static final MatrixType H5 = new MatrixType("H5");
    public static final MatrixType SQUARECSV = new MatrixType("SQUARECSV");
    
    private String id;

    /** Keep this class from being created with "new".
     *
     */
    private MatrixType(String id) {
        this.id = id;
    }

    public String toString() {
        return this.id;
    }

    public boolean equals(MatrixType type) {
        boolean result = false;

        int index = type.toString().indexOf(this.id);
        if (index == 0)
            result = true;

        return result;
    }

    public static MatrixType lookUpMatrixType(String matrixTypeName) {
        if (BINARY.toString().equalsIgnoreCase(matrixTypeName)) return BINARY;
        if (ZIP.toString().equalsIgnoreCase(matrixTypeName)) return ZIP;
        if (CSV.toString().equalsIgnoreCase(matrixTypeName)) return CSV;
        if (SQUARECSV.toString().equalsIgnoreCase(matrixTypeName)) return SQUARECSV;
        if (EMME2.toString().equalsIgnoreCase(matrixTypeName)) return EMME2;
        if (D311.toString().equalsIgnoreCase(matrixTypeName)) return D311;
        if (TPPLUS.toString().equalsIgnoreCase(matrixTypeName)) return TPPLUS;
        if (TRANSCAD.toString().equalsIgnoreCase(matrixTypeName)) return TRANSCAD;
        if (H5.toString().equalsIgnoreCase(matrixTypeName)) return H5;
        logger.error("Matrix type "+matrixTypeName+" is not defined");
        return null;
    }

    public static MatrixType[] values() {
        return new MatrixType[]{BINARY,ZIP,CSV,EMME2,D311,TPPLUS,TRANSCAD,H5,SQUARECSV};
    }
}
