package com.pb.common.matrix;

import java.io.File;
import java.io.IOException;

public class CSVToZipMatrix {
    public static void main(String[] args) throws IOException {
        File fromFile = new File(args[0]);
        File toFile = new File(args[1]);
        
        CSVMatrixReader reader = new CSVMatrixReader(fromFile);
        ZipMatrixWriter writer = new ZipMatrixWriter(toFile);
        
        writer.writeMatrix(reader.readMatrix());
    }
}
