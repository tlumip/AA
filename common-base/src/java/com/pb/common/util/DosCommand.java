package com.pb.common.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Serializable;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.apache.log4j.Logger;


public class DosCommand implements Serializable {

    protected static Logger logger = Logger.getLogger(DosCommand.class);

    String libpath = null;
    String classpath = null;
    String javaHome32 = null;
    String java32Port = null;
    private boolean consoleWatcher = true;
    private Process process;
    private StringBuilder output = new StringBuilder();
    
    public DosCommand () {

        // JAVA_HOME_32 must be set to identify the directory location for 32 bit jre.

        // Check FIRST IN system properties to see if it was set with -DJAVA_HOME_32 on java command line.
        // Also get classpath and java.library.path from parent process environment
        Properties sysProps = System.getProperties();
        Enumeration names = sysProps.propertyNames();
        while (names.hasMoreElements()) {
            String key = (String) names.nextElement();
            String value = (String) sysProps.get(key);
            if ( key.equalsIgnoreCase("JAVA_32_PORT") )
                java32Port = value;
            if ( key.equalsIgnoreCase("JAVA_HOME_32") )
                javaHome32 = value;
            if ( key.equalsIgnoreCase("java.class.path") )
                classpath = value;
            if ( key.equalsIgnoreCase("java.library.path") )
                libpath = value;
        }

        // If not set with -D, check windows environment variables to see if JAVA_HOME_32 was set as an environment variable
        if ( javaHome32 == null ) {
            
            Map<String, String> envMap = System.getenv();
            Set keys = envMap.keySet();
            Iterator it = keys.iterator();
            while (it.hasNext()) {
                String key = (String) it.next();
                String value = (String) envMap.get(key);
                if ( key.equalsIgnoreCase("JAVA_HOME_32") ) {
                    javaHome32 = value;
                    break;
                }
            }

        }


    }

    public void runJavaClassIn32BitJRE (String... command) {

        String[] commandList = null;
        
        if ( javaHome32 != null ) {
            
            // copy the 32 bit jre command into the commandList followed by the command arguments
            int nArgs = command.length;
            int offset = 5;
            commandList = new String[nArgs + offset];
            commandList[0] = javaHome32 + "/bin/java";
            commandList[1] = "-Xmx512m";
            commandList[2] = "-Djava.library.path=" + libpath;
            commandList[3] = "-classpath";
            commandList[4] = classpath;
            
            for ( int i=0; i < offset; i++ ) {
                System.out.println("[COMMAND] " + commandList[i]);
            }
            for ( int i=0; i < nArgs; i++ ) {
                commandList[i+offset] = command[i];
                System.out.println("[COMMAND] " + commandList[i+offset]);
            }
            
        }
        else {
            logger.error ( "JAVA_HOME_32 environment variable not set." );
            logger.error ( "set JAVA_HOME_32 in Windows environment variables or" );
            logger.error ( "include -DJAVA_HOME_32=<directory for 32 bit JRE> on java command line." );
        }
        
        
        try {
            
            execute(commandList);

        }
        catch (Exception e) {
            String commandString = "";
            for ( String s : commandList )
              commandString += " " + s + "\n";
            logger.error("exception caught executing command: " + commandString);
            throw new RuntimeException(e);
        }


    }

    private void execute( String[] commandList ) throws Exception {
        ProcessBuilder processBuilder = new ProcessBuilder(commandList);
        processBuilder.redirectErrorStream(true);
        process = processBuilder.start();
        
        collectOutput();
        
    }

    private void collectOutput() {
        Runnable runnable = new Runnable() {
            public void run() {        
                try {
                    collectOutput(process.getInputStream(), output);
                }
                catch (IOException e) {
                    output.append(e.getMessage());
                }
            }
        };
        new Thread(runnable).start();
    }

    private void collectOutput(InputStream inputStream, StringBuilder collector) throws IOException {
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new InputStreamReader(inputStream));
            String line = "";
            while ( (line = reader.readLine()) != null)
                collector.append( "[CONSOLE] " + line + "\n" );
        }
        finally {
            reader.close();
        }
    }

    public String getConsoleOutput() {
        while ( output.length() == 0 && consoleWatcher )
            try {
                TimeUnit.SECONDS.sleep(1);
            }
            catch (InterruptedException e){
            }
        String returnString = output.toString();
        output.delete(0,returnString.length());
        return returnString;
    }
    
    public void destroy() {
        consoleWatcher = false;
        process.destroy();
    }
    
    public String getJava32Port() {
        return java32Port;
    }


    public static Process runDOSCommand (String dosCommandString, String command) {
        
        String fullCommand = dosCommandString + " " + command;

        Process proc = null;
        try {

            String s;
            logger.info( "issuing command to OS: " + fullCommand );
            proc = Runtime.getRuntime().exec( fullCommand );
                
            BufferedReader stdout = new BufferedReader(new InputStreamReader(proc.getInputStream()));
            BufferedReader stderr = new BufferedReader(new InputStreamReader(proc.getErrorStream()));
     
            while ((s = stdout.readLine()) != null) {
              logger.info(s);
            }

            while ((s = stderr.readLine()) != null) {
              logger.info(s);
            }
        }
        catch (IOException e) {
            logger.error ( String.format( "Interrupted exception ocurred for %s to start a new WINDOWS process.", fullCommand ) );
            throw new RuntimeException(e);
        }

        return proc;
    }


    public static void main(String[] args) {
        
        String myCommand = "-version";
        DosCommand dosCmd = new DosCommand();
        dosCmd.runJavaClassIn32BitJRE(myCommand);

        String consoleOutput = dosCmd.getConsoleOutput();
        if ( consoleOutput.length() > 0 )
            logger.info( consoleOutput );
        
        dosCmd.destroy();
        logger.info( "done" );
    }

}
