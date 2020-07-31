package com.pb.common.matrix;

import gnu.cajo.invoke.Remote;
import gnu.cajo.utils.ItemServer;

import com.pb.common.util.CommandLine;

import java.io.File;

public class RemoteMatrixDataServer {

    public void testRemote(MatrixType type, File file, String matrixName) {
        MatrixReader mr = MatrixReader.createReader(type, file);
        mr.testRemote(matrixName);
    }

    public Matrix readMatrix(MatrixType type, File file, String matrixName) {
        MatrixReader mr = MatrixReader.createReader(type, file);
        return mr.readMatrix(matrixName);
    }

    public void writeMatrix(MatrixType type, File file, Matrix m, String matrixName) {
        MatrixWriter mw = MatrixWriter.createWriter(type, file);
        mw.writeMatrix(matrixName, m);
    }

    public void writeMatrices(MatrixType type, File file, Matrix[] m, String[] matrixName) {
        MatrixWriter mw = MatrixWriter.createWriter(type, file);
        mw.writeMatrices(matrixName, m);
    }

    public static void main(String args[]) throws Exception {

        CommandLine cmdline = new CommandLine(args);

        //Read ipAddress from command-line
        String hostname = "localhost";
        String ipValue = cmdline.value("hostname");
        if ( (ipValue != null) && (ipValue.length() > 0) ){
            hostname = ipValue;
        }

        //Read port from command-line
        int port = 1198;
        String portValue = cmdline.value("port");
        if ( (portValue != null) && (portValue.length() > 0) ){
            port = Integer.parseInt(portValue);
        }

        Remote.config(hostname, port, null, 0);
        ItemServer.bind(new RemoteMatrixDataServer(), "com.pb.common.matrix.RemoteMatrixDataServer");

        System.out.println("RemoteMatrixDataServer started on: " + hostname +":"+ port);
    }
}
