package com.pb.common.matrix;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.SortedSet;
import java.util.TreeSet;

import org.apache.log4j.Logger;

public class SQLMatrixReader extends MatrixReader {

	Connection conn;
	private String queryString;
	private String destinationQueryString;
	private String originQueryString;
	private static Logger logger = Logger.getLogger(SQLMatrixReader.class);
	
	public SQLMatrixReader(Connection conn, String queryString , String originQuery, String destinationQuery ) {
		super();
		this.conn= conn;
		this.queryString = queryString;
		this.originQueryString = originQuery;
		this.destinationQueryString = destinationQuery;
	}

	@Override
	public Matrix[] readMatrices() throws MatrixException {
		try {
			boolean autoCommit = conn.getAutoCommit();
			conn.setAutoCommit(false); // need this for cursor based result sets, so we can fetch only 100 rows at a time
			Statement s = conn.createStatement();
			SortedSet<Integer> origins = new TreeSet<Integer>();
			SortedSet<Integer> destinations = new TreeSet<Integer>();
			ResultSet res = s.executeQuery(originQueryString);
			while (res.next()) {
				origins.add(res.getInt(1));
			}
			res.close();
			
			res = s.executeQuery(destinationQueryString);
			while (res.next()) {
				destinations.add(res.getInt(1));
			}
			res.close();
			
			int[] originsArray = new int[origins.size()+1];
			int o = 1;
			for (Integer i : origins) {originsArray[o++] = i.intValue();}
			int[] destinationsArray = new int[destinations.size()+1];
			int d = 1;
			for (Integer i : destinations) {destinationsArray[d++] = i.intValue();}

			s.setFetchSize(100000);
			res = s.executeQuery(queryString);
			ResultSetMetaData rsmd = res.getMetaData(); 
			int numColumns = rsmd.getColumnCount();
			Matrix[] ms = new Matrix[numColumns-2];
			for (int m = 0;m<ms.length;m++) {
				ms[m] = new Matrix(origins.size(),destinations.size());
				ms[m].setExternalNumbers(originsArray,destinationsArray);
				ms[m].setName(rsmd.getColumnName(m+3));
			}
			int rowCount = 0;
			while (res.next()) {
				int origin = res.getInt(1);
				int destination = res.getInt(2);
				for (int m = 0; m<ms.length;m++) {
					ms[m].setValueAt(origin, destination, res.getFloat(m+3));
				}
				if ((++rowCount)%10000==0) logger.info("Read row "+rowCount+" of SQL Matrices "+queryString);
			}
			res.close();

			s.close();
			conn.setAutoCommit(autoCommit);
			return ms;
		} catch (SQLException e) {
			logger.fatal("Can't read SQL Matrices "+queryString,e);
			throw new RuntimeException("Can't read SQL Matrices "+queryString,e);
		}
	}

	@Override
	public Matrix readMatrix(String name) throws MatrixException {
		throw new MatrixException(SQLMatrixReader.class.getCanonicalName()+" cant read a single matrix by name yet, use readMatrices() instead");
	}

	@Override
	public Matrix readMatrix() throws MatrixException {
		throw new MatrixException("Need a matrix name to read a matrix using "+SQLMatrixReader.class.getCanonicalName()+" use readMatrix(String name) instead");
	}

}
