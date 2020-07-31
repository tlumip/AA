package com.hbaspecto.pecas;

import java.io.File;

import com.pb.common.matrix.ColumnVector;
import com.pb.common.matrix.Matrix;
import com.pb.common.matrix.ZipMatrixReader;
import com.pb.common.matrix.ZipMatrixWriter;

public class FixBrokenSkims {

	public static void main(String[] args) {
		File skims = new File("E:/BrokenSkims/pktrk1time.zmx");
		Matrix skimm = new ZipMatrixReader(skims).readMatrix();
		File ff = new File("E:/BrokenSkims/pktrk1fftime.zmx");
		Matrix ffm = new ZipMatrixReader(ff).readMatrix();
		float[] column = new float[ffm.getRowCount()];
		ffm.getColumn(4010, column);
		skimm.setColumn(new ColumnVector(column), 4010);
		new ZipMatrixWriter(skims).writeMatrix(skimm);
	}

}
