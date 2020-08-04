package com.hbaspecto.oregon.pecas.aa;

import static org.junit.Assert.*;

import java.io.BufferedReader;
import java.io.FileReader;

import org.junit.Ignore;
import org.junit.Test;

public class TestPProcessor
{
    @Ignore
	@Test
    public void testActivityTotals() throws Exception
    {
        String controlpath = "\\\\hba-server.hbaspecto.com\\Models\\PECASOregon\\DropboxShare\\InputPreparation\\ActivityTotalsW2009.csv";
        String outputpath = "C:\\Users\\Graham\\Documents\\Synced Documents\\HBA Specto\\Summer 2011\\W00\\2009\\ActivityTotalsW.csv";

        BufferedReader control = new BufferedReader(new FileReader(controlpath));
        BufferedReader output = new BufferedReader(new FileReader(outputpath));

        String controlline = null;
        String outputline = null;
        int i = 1;
        do
        {
            controlline = control.readLine();
            outputline = output.readLine();

            assertFalse(controlline == null ^ outputline == null);
            if(controlline != null && outputline != null)
            {
                String[] controlarr = controlline.split(",");
                String[] outputarr = outputline.split(",");

                assertEquals("Fail in line " + i, controlarr[0], outputarr[0]);
                if(!controlarr[1].equals(outputarr[1]))
                {
                    double expected = Double.parseDouble(controlarr[1]);
                    assertEquals("Fail in line " + i, expected, Double.parseDouble(outputarr[1]),
                            expected * 1E-6);
                }
            }

            i++;
        }
        while(controlline != null || outputline != null);
    }
    
    @Ignore
    @Test
    public void testFloorspace() throws Exception
    {
        String controlpath = "\\\\hba-server.hbaspecto.com\\Models\\PECASOregon\\DropboxShare\\InputPreparation\\FloorspaceWt20-PIAgLog.csv";
        String outputpath = "C:\\Users\\Graham\\Documents\\Synced Documents\\HBA Specto\\Summer 2011\\W00\\2009\\FloorspaceW.csv";

        BufferedReader control = new BufferedReader(new FileReader(controlpath));
        BufferedReader output = new BufferedReader(new FileReader(outputpath));

        String controlline = null;
        String outputline = null;
        int i = 1;
        do
        {
            controlline = control.readLine();
            outputline = output.readLine();

            assertFalse(controlline == null ^ outputline == null);
            if(controlline != null && outputline != null)
            {
                String[] controlarr = controlline.split(",");
                String[] outputarr = outputline.split(",");

                assertEquals("Fail in line " + i, controlarr[0], outputarr[0]);
                assertEquals("Fail in line " + i, controlarr[1], outputarr[1]);
                assertEquals("Fail in line " + i, controlarr[2], outputarr[2]);
                
                if(!controlarr[3].equals(outputarr[3]))
                {
                    double expected = Double.parseDouble(controlarr[3]);
                    assertEquals("Fail in line " + i, expected, Double.parseDouble(outputarr[3]),
                            expected * 1E-7);
                }
            }

            i++;
        }
        while(controlline != null || outputline != null);
    }
    
    @Ignore
    @Test
    public void testActivitiesZonalValues() throws Exception
    {
        String controlpath = "\\\\hba-server.hbaspecto.com\\Models\\PECASOregon\\DropboxShare\\InputPreparation\\ActivitiesZonalValuesW-t20increments.csv";
        String outputpath = "C:\\Users\\Graham\\Documents\\Synced Documents\\HBA Specto\\Summer 2011\\W00\\2009\\ActivitiesZonalValuesW.csv";

        BufferedReader control = new BufferedReader(new FileReader(controlpath));
        BufferedReader output = new BufferedReader(new FileReader(outputpath));

        String controlline = null;
        String outputline = null;
        int i = 1;
        do
        {
            controlline = control.readLine();
            outputline = output.readLine();

            assertFalse(controlline == null ^ outputline == null);
            if(controlline != null && outputline != null)
            {
                String[] controlarr = controlline.split(",");
                String[] outputarr = outputline.split(",");

                assertEquals("Fail in line " + i, controlarr[0], outputarr[0]);
                assertEquals("Fail in line " + i, controlarr[1], outputarr[1]);
                
                if(!controlarr[2].equals(outputarr[2]))
                {
                    double expected = Double.parseDouble(controlarr[2]);
                    assertEquals("Fail in line " + i, expected, Double.parseDouble(outputarr[2]),
                            expected >= 1E7 || expected <= 1E-3 ? expected * 0.01 : expected * 1E-6);
                }
                
                assertEquals("Fail in line " + i, controlarr[3], outputarr[3]);
                
                if(!controlarr[4].equals(outputarr[4]))
                {
                    double expected = Double.parseDouble(controlarr[4]);
                    assertEquals("Fail in line " + i, expected, Double.parseDouble(outputarr[4]),
                            expected >= 1E7 || expected <= 1E-3 ? expected * 0.01 : expected * 1E-6);
                }
            }

            i++;
        }
        while(controlline != null || outputline != null);
    }
    
    @Ignore
    @Test
    public void testTechnologyOptions() throws Exception
    {
        String controlpath = "\\\\hba-server.hbaspecto.com\\Models\\PECASOregon\\DropboxShare\\InputPreparation\\t21TechnologyOptionsW.csv";
        String outputpath = "C:\\Users\\Graham\\Documents\\Synced Documents\\HBA Specto\\Summer 2011\\W00\\2009\\TechnologyOptionsW.csv";

        BufferedReader control = new BufferedReader(new FileReader(controlpath));
        BufferedReader output = new BufferedReader(new FileReader(outputpath));

        String controlline = null;
        String outputline = null;
        int i = 1;
        do
        {
            controlline = control.readLine();
            outputline = output.readLine();

            assertFalse(controlline == null ^ outputline == null);
            if(controlline != null && outputline != null)
            {
                String[] controlarr = controlline.split(",");
                String[] outputarr = outputline.split(",");

                assertEquals("Fail in line " + i, controlarr[0], outputarr[0]);
                assertEquals("Fail in line " + i, controlarr[1], outputarr[1]);
                
                for(int j = 2; j < controlarr.length || j < outputarr.length; j++) {
                    if(!controlarr[j].equals(outputarr[j]))
                    {
                        double expected = Double.parseDouble(controlarr[j]);
                        assertEquals("Fail in line " + i, expected, Double.parseDouble(outputarr[j]),
                                Math.abs(expected) < 1E-3 ? Math.abs(expected * 0.01) : Math.abs(expected * 1E-5));
                    }
                }
            }

            i++;
        }
        while(controlline != null || outputline != null);
    }
    
    @Test
    public void testUpdateImportExport() throws Exception
    {
    	String controlpath = "E:\\Models\\PECASOregon\\C05-no subversion\\ImportExportControl.csv";
        String outputpath = "E:\\Models\\PECASOregon\\C05-no subversion\\2009\\ActivityTotalsI.csv";

        BufferedReader control = new BufferedReader(new FileReader(controlpath));
        BufferedReader output = new BufferedReader(new FileReader(outputpath));

        String controlline = null;
        String outputline = null;
        int i = 1;
        do
        {
            controlline = control.readLine();
            outputline = output.readLine();

            assertFalse(controlline == null ^ outputline == null);
            if(controlline != null && outputline != null)
            {
                String[] controlarr = controlline.split(",");
                String[] outputarr = outputline.split(",");

                assertEquals("Fail in line " + i, controlarr[0], outputarr[0]);
                
                for(int j = 1; j < controlarr.length || j < outputarr.length; j++) {
                    if(!controlarr[j].equals(outputarr[j]))
                    {
                        double expected = Double.parseDouble(controlarr[j]);
                        assertEquals("Fail in line " + i, expected, Double.parseDouble(outputarr[j]),
                                Math.abs(expected * 1E-5));
                    }
                }
            }

            i++;
        }
        while(controlline != null || outputline != null);
    }
}
