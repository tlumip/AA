package com.pb.common.matrix;

import java.io.IOException;
import java.io.Serializable;
import java.lang.reflect.InvocationTargetException;
import java.rmi.NotBoundException;

import gnu.cajo.invoke.Remote;

public class RmiMatrixReader extends MatrixReader implements Serializable {

    String connectString = "//localhost:1198/com.pb.common.matrix.RemoteMatrixDataServer";

    public void testRemote(String name) throws MatrixException {

        Object[] objArray = { this.type, this.file, name };
        try {
            Object obj = Remote.getItem(connectString);
            Remote.invoke(obj, "testRemote", objArray);
        }
        catch (UnsatisfiedLinkError e) {
            System.out.println ("Error in RMI call to testRemote().");
            throw e;
        }
        catch (InvocationTargetException e) {
            System.out.println ("Error in RMI call to testRemote().");
            throw new MatrixException(e);
        }
        catch (InstantiationException e) {
            throw new MatrixException(e);
        }
        catch (NotBoundException e) {
            throw new MatrixException(e);
        }
        catch (ClassNotFoundException e) {
            throw new MatrixException(e);
        }
        catch (IllegalAccessException e) {
            throw new MatrixException(e);
        }
        catch (IOException e) {
            throw new MatrixException(e);
        }
        catch (Exception e) {
            System.out.println ("Other Error in RMI call to testRemote");
            System.out.println ("using connectString: " + connectString);
            System.out.println ("file type: " + this.type.toString());
            System.out.println ("file name: " + this.file.getName());
            System.out.println ("table name: " + name);
            throw new RuntimeException(e);
        }
    }

    public Matrix readMatrix(String name) throws MatrixException {

        Object[] objArray = { this.type, this.file, name };
        try {
            Object obj = Remote.getItem(connectString);
            Matrix matrix = (Matrix) Remote.invoke(obj, "readMatrix", objArray);
            return matrix;
        }
        catch (UnsatisfiedLinkError e) {
            System.out.println ("Error in RMI call to read matrix.  Could not load native dll.");
            throw e;
        }
        catch (InvocationTargetException e) {
            throw new MatrixException(e);
        }
        catch (InstantiationException e) {
            throw new MatrixException(e);
        }
        catch (NotBoundException e) {
            throw new MatrixException(e);
        }
        catch (ClassNotFoundException e) {
            throw new MatrixException(e);
        }
        catch (IllegalAccessException e) {
            throw new MatrixException(e);
        }
        catch (IOException e) {
            throw new MatrixException(e);
        }
        catch (RuntimeException e) {
            System.out.println ("RuntimeException in RMI call to read matrix");
            System.out.println ("using connectString: " + connectString);
            System.out.println ("file type: " + this.type.toString());
            System.out.println ("file name: " + this.file.getName());
            System.out.println ("table name: " + name);
            throw e;
        }
        catch (Exception e) {
            System.out.println ("Other Error in RMI call to read matrix");
            System.out.println ("using connectString: " + connectString);
            System.out.println ("file type: " + this.type.toString());
            System.out.println ("file name: " + this.file.getName());
            System.out.println ("table name: " + name);
            throw new RuntimeException(e);
        }
    }

    public Matrix readMatrix() throws MatrixException {
        throw new RuntimeException("method has not been implemented");
    }

    public Matrix[] readMatrices() throws MatrixException {
        throw new RuntimeException("method has not been implemented");
    }

    public void setConnectString(String connectString) {
        this.connectString = connectString;
    }
    
}
