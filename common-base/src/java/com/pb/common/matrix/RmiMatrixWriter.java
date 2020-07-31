package com.pb.common.matrix;

import java.io.Serializable;

import gnu.cajo.invoke.Remote;

public class RmiMatrixWriter extends MatrixWriter implements Serializable {

    String connectString = "//localhost:1198/com.pb.common.matrix.RemoteMatrixDataServer";

    public void writeMatrix(Matrix matrix) throws MatrixException {
        String name = "none";
        writeMatrix(name, matrix);
    }

    public void writeMatrix(String name, Matrix matrix) throws MatrixException {

        Object[] objArray = { this.type, this.file, matrix, name };
        try {
            Object obj = Remote.getItem(connectString);
            Remote.invoke(obj, "writeMatrix", objArray);
        }
        catch (Exception e) {            
            System.out.println( String.format("Error in RMI call to writeMatrix(String name, Matrix matrix) for matrix with name=%s.", name) );
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    public void writeMatrices(String[] name, Matrix[] matrix) throws MatrixException {

        Object[] objArray = { this.type, this.file, matrix, name };
        try {
            Object obj = Remote.getItem(connectString);
            Remote.invoke(obj, "writeMatrices", objArray);
        }
        catch (Exception e) {            
            System.out.println( String.format("Error in RMI call to writeMatrices(String[] name, Matrix[] matrix) for matrix[0] with name=%s.", name[0]) );
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    public void setConnectString(String connectString) {
        this.connectString = connectString;
    }
    
}
