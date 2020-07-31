package com.hbaspecto.pecas;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;

import com.pb.common.matrix.CSVMatrixReader;
import com.pb.common.matrix.ZipMatrixWriter;

public class CSVToZipMatrix {

	public static void main(String[] args) throws IOException {
		BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
		File fromFile = new File(in.readLine());
		File toFile = new File(in.readLine());
		
		CSVMatrixReader reader = new CSVMatrixReader(fromFile);
		ZipMatrixWriter writer = new ZipMatrixWriter(toFile);
		
		writer.writeMatrix(reader.readMatrix());
	}

}
