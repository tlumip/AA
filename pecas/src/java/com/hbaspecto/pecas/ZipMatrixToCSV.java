package com.hbaspecto.pecas;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;

import com.pb.common.matrix.CSVMatrixWriter;
import com.pb.common.matrix.ZipMatrixReader;

public class ZipMatrixToCSV {

	public static void main(String[] args) throws IOException {
		BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
		File fromFile = new File(in.readLine());
		File toFile = new File(in.readLine());
		
		ZipMatrixReader reader = new ZipMatrixReader(fromFile);
		CSVMatrixWriter writer = new CSVMatrixWriter(toFile);
		
		writer.writeMatrix(reader.readMatrix());
	}

}
