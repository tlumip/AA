package simpleorm.quickstart;

import java.io.File;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.util.Iterator;

import org.apache.commons.sql.io.JdbcModelReader;
import org.apache.commons.sql.model.Database;
import org.apache.commons.sql.model.Table;



/**
 * This class generates the SimpleORM mapping files for a (in this example)
 * Sybase schema.
 * 
 * Designed to run as an ANT task with the following system paramters:
 * <ul>
 * <li>database.url=jdbc:interbase://localhost/C:/Source/Java/ORM/SimpleORM/simpleorm/temp/test.gdb
 * <li>database.username=sysdba
 * <li>database.password=masterkey
 * <li>database.driver=interbase.interclient.Driver
 * <li>database.schema=
 * <li>database.catelog=
 * <li>quickstart.file=.
 * <li>quickstart.packagename=simpleorm.dbtest
 * <li>quickstart.getters_and_setters=true
 * <li>quickstart.use_one_package=true
 * <li>quickstart.INiceNameFormatter=simpleorm.quickstart.DefaultFormatter
 * <ul>
 * 
 * To run.
 * <ol>
 * <li>Insure that log4j.jar is in your classpath, along with your JDBC driver.
 * <li>Set the system properties.
 * <li>Create an instance of SimpleORMGenerate
 * <li>Call method execute()
 * </ol>
 * 
 * @author <a href="mailto:richard.schmidt@inform6.com">Richard Schmidt</a>
 *         Original version Joe McDaniel.
 * @version $Id: $
 */
public class SimpleORMGenerator implements PropertyProvider {

	private String dbDriver, dbUrl, dbUser, dbPassword, dbCatolog, dbSchema;

	private File rootDir;

	private String packageName;

	String classNameForFormatter;

	private INiceNameFormatter niceName;

	private PropertyProvider pp = this;

	private void loadProperties() {

		dbDriver = pp.getProperty("database.driver");
		dbUrl = pp.getProperty("database.url");
		dbUser = pp.getProperty("database.user");
		dbPassword = pp.getProperty("database.password");
		dbSchema = pp.getProperty("database.schema", null);
		dbCatolog = pp.getProperty("database.catelog", null);
		rootDir = new File(pp.getProperty("quickstart.file", "."));
		packageName = pp.getProperty("quickstart.packagename");
		classNameForFormatter = pp.getProperty("quickstart.INiceNameFormatter",
				"simpleorm.quickstart.HBAFormatter");
	}

	public SimpleORMGenerator() {

	}

	public SimpleORMGenerator(PropertyProvider pp) {
		this.pp = pp;
	}

	public String getProperty(String key, String def) {
		return System.getProperty(key, def);
	}

	public String getProperty(String key) {
		return System.getProperty(key);
	}



	public void internalExecute() throws Exception {

		loadProperties();
		System.out.println("SimpleOrm code generator starting\n");
		System.out.println("Your DB settings are:");
		System.out.println("driver       : " + dbDriver);
		System.out.println("URL          : " + dbUrl);
		System.out.println("user         : " + dbUser);
		System.out.println("password     : " + dbPassword);
		System.out.println("schema       : " + dbSchema);
		System.out.println("catelog      : " + dbCatolog);
		System.out.println();
		System.out.println("Other settings are");
		System.out.println("directory    : " + rootDir.getAbsolutePath());
		System.out.println("package name : " + packageName);
		System.out.println("INiceName    : " + classNameForFormatter);
		System.out.println();

		// Load up the INiceName

		try {
			niceName = (INiceNameFormatter) Class
					.forName(classNameForFormatter).getConstructor(
							(Class<?>[]) null).newInstance((Object[]) null);

		} catch (Exception e) {
			System.err
					.println("Could not load INiceNameFormatter, will use DefaultFormatter instead");
			e.printStackTrace();
			niceName = new DefaultFormatter();
		}
		// Add on the package directory
		rootDir = new File(rootDir.getAbsolutePath() + File.separatorChar
				+ packageName.replace('.', File.separatorChar)
				+ File.separatorChar);

		if (!rootDir.exists()) {
			rootDir.mkdirs();
		}

		// Load the database Driver.
		Class.forName(dbDriver);

		// Attemtp to connect to a database.
		Connection con = DriverManager.getConnection(dbUrl, dbUser, dbPassword);
		printTables(con);
		JdbcModelReader reader = new JdbcModelReader(con);
		reader.setCatalog(null);
		reader.setSchema(dbSchema);
		// Get the metta data from the database
		Database db = reader.getDatabase();

		// Itterate trhough the tables, generating the files.
		Iterator tablesEnum = db.getTables().iterator();
		while (tablesEnum.hasNext()) {
			try {
				Table table = (Table) tablesEnum.next();
				System.err.println("Processing table " + table.getName());
				TableGenerate genTable = new TableGenerate(db, table, niceName,
						packageName, rootDir);

				genTable.generateBaseClass();
				genTable.generateBRClass();
				System.out.println("Generated table "
						+ genTable.table.getName());
			} catch (Exception e) {
				e.printStackTrace();
			}
		}

		System.out.println("done");
	}

	static void printTables(Connection con) throws Exception {
		DatabaseMetaData dbmd = con.getMetaData();
		ResultSet tableData = dbmd.getTables(null, null, "%", new String[] {
				"TABLE", "VIEW" });
		System.err.println("=== Just Print Tables ===");
		while (tableData.next()) {
			System.err
					.println("=== TABLE " + tableData.getString("TABLE_SCHEM"));
		}
		tableData.close();

		if (!con.getAutoCommit())
			con.commit();
	}
	private static void printUsage() {

		System.out.println();
		System.out.println("===== Simple Orm Generator ===== ");
		System.out
				.println("Generates code from database. Uses system properties ");
		System.out.println("usage:	java -jar SimpleORMGenerator.jar");
		System.out
				.println("Make sure to have your database driver on classpath");
	}

	public static void main(String[] args) throws Exception {

		printUsage();
		
		System.setProperty("database.driver","org.postgresql.Driver");
		//System.setProperty("database.url","jdbc:postgresql://localhost:5433/rmwb");    	

		System.setProperty("database.user","usrPostgres");            	
		System.setProperty("database.password","usrPostgres");            			
		System.setProperty("database.schema","worker_allocation");
		//System.setProperty("database.catelog",null);
		System.setProperty("quickstart.file",".");
		System.setProperty("quickstart.packagename","com.hbaspecto.rmwb.wa.orm");
		
		System.setProperty("database.url","jdbc:postgresql://localhost:5433/rmwb");		
		
		//System.setProperty("quickstart.INiceNameFormatter","simpleorm.quickstart.DefaultFormatter");
		System.setProperty("quickstart.INiceNameFormatter","simpleorm.quickstart.HBAFormatter");		
		//System.setProperty("quickstart.INiceNameFormatter","foursee.ots.orm.tables.OtsFormatter");
		//System.setProperty("quickstart.packagename","foursee.ots.simpleorm");						
		
		SimpleORMGenerator instance = new SimpleORMGenerator();
		instance.internalExecute();
	}

}
