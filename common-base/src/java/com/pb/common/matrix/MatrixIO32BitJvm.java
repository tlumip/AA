package com.pb.common.matrix;

import java.io.Serializable;

import com.pb.common.util.DosCommand;


public class MatrixIO32BitJvm implements Serializable {

    private static MatrixIO32BitJvm instance = new MatrixIO32BitJvm();
    
    RmiMatrixReader rmiReader = null;
    RmiMatrixWriter rmiWriter = null;

    static String serverClass = "com.pb.common.matrix.RemoteMatrixDataServer";
    static String hostName = "localhost";
    static String port = "1198";
    
    private static DosCommand dosCmd = null;
    private static ConsoleWatcher consoleWatcher = null;
    private static boolean watching = true;
    
    private MatrixIO32BitJvm() {
    }
    
    /** Returns an instance to the singleton.
     */
    public static MatrixIO32BitJvm getInstance() {
        return instance;
    }
    
    /** Set class to run in new 32 bit JVM
     */
    public void setClassToRun( String className ) {
        MatrixIO32BitJvm.serverClass = className;
    }
    
    /** Set host to run on
     */
    public void setHostToRun( String hostName ) {
        MatrixIO32BitJvm.hostName = hostName;
    }
    
    /** Set port to run on
     */
    public void setPortToRun( String port ) {
        MatrixIO32BitJvm.port = port;
    }
    
    
    public synchronized void startJVM32() {
        
        // multiple calls on this method should not execute if a VM already exists
        if ( dosCmd == null ) {
            
            dosCmd = new DosCommand();
            watching = true;
            consoleWatcher = new ConsoleWatcher();
            consoleWatcher.start();
            
            // get the port number to be used by RMI from the DosCommand object that was set by -Dvar on the command line.
            // setting port number on command line allows multiple VMs to be created on a single host to use RMI to communicate with localhost through different ports.
            // if none was set on command line, use the default MatrixIO32BitJvm.port, which could have been changed by the setPortToRun() method. 
            String java32Port = dosCmd.getJava32Port();
            if ( java32Port == null )
                java32Port = MatrixIO32BitJvm.port;
            else
                MatrixIO32BitJvm.port = java32Port;
            
            
            try {
                
                String[] JRE_ARGS = {
                    MatrixIO32BitJvm.serverClass,
                    "-hostname",
                    MatrixIO32BitJvm.hostName,
                    "-port",
                    java32Port 
                };

                // this is a long running process that doesn't return
                dosCmd.runJavaClassIn32BitJRE( JRE_ARGS );
            }
            catch ( RuntimeException e ) {
                System.out.println ( "caught the exception." );
                throw e;
            }
            
        }

    }
    

    public synchronized void startMatrixDataServer( MatrixType type ) {
        
        String connectString = String.format("//%s:%s/%s", MatrixIO32BitJvm.hostName, MatrixIO32BitJvm.port, MatrixIO32BitJvm.serverClass);

        //These lines will remote any matrix reader call
        while ( rmiReader == null ) {
            rmiReader = new com.pb.common.matrix.RmiMatrixReader();
        }
        rmiReader.setConnectString( connectString );
        MatrixReader.setReaderClassForType( type, rmiReader);
        
        //These lines will remote any matrix writer call
        while ( rmiWriter == null ) {
            rmiWriter = new com.pb.common.matrix.RmiMatrixWriter();
        }
        rmiWriter.setConnectString( connectString );
        MatrixWriter.setWriterClassForType( type, rmiWriter);
        
    }
    

    public synchronized void stopMatrixDataServer() {
        
        //These lines will stop remote matrix reader calls
        MatrixReader.clearReaderClassForType(MatrixType.TPPLUS);
        rmiReader = null;
        
        //These lines will remote any matrix writer call
        MatrixWriter.clearWriterClassForType(MatrixType.TPPLUS);
        rmiWriter = null;
        
    }
    

    public synchronized void stopJVM32() {
        
        if ( dosCmd != null ) {
            watching = false;
            dosCmd.destroy();
            dosCmd = null;
            consoleWatcher = null;
        }
    }
    



    class ConsoleWatcher extends Thread implements Serializable
    {
        ConsoleWatcher() {
        }
        
        public void run() {
            
            while ( watching ) {
                String consoleOutput = dosCmd.getConsoleOutput();
                if ( consoleOutput.length() > 0 )
                    System.out.println( consoleOutput );
            }
            
        }
    }

}

