package com.pb.common.matrix;

import java.io.File;
import java.io.IOException;

public class ZipMatrixToCSV {
    public static void main(String[] args) throws IOException {
        File fromFile = new File(args[0]);
        File toFile = new File(args[1]);
        
        ZipMatrixReader reader = new ZipMatrixReader(fromFile);
        CSVMatrixWriter writer = new CSVMatrixWriter(toFile);
        
        writer.writeMatrix(reader.readMatrix());
    }
}
