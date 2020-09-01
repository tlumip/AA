package simpleorm.drivers;

public class SDriverMSSqlServer extends SDriverMSSQL {

	public SDriverMSSqlServer() {
		super();
	}
	protected String driverName() {
		return "Microsoft SQL Server JDBC Driver 3.0";
	}
}
