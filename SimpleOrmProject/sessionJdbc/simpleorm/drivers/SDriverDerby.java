package simpleorm.drivers;

import simpleorm.dataset.SFieldLong;
import simpleorm.dataset.SFieldMeta;
import simpleorm.dataset.SFieldScalar;
import simpleorm.dataset.SGeneratorMode;
import simpleorm.dataset.SRecordMeta;
import simpleorm.sessionjdbc.SDriver;
import simpleorm.sessionjdbc.SGenerator;

/**
 * Contains Derby/Cloudscape 10.0 implementation
 * 
 * $Revision: 1.0 $ $Date: Apr 12 2004 14:33:46 $
 * 
 * @author Denis Rodrigues Cassiano deniscassiano@gmail.com
 */
public class SDriverDerby extends SDriver {

	// protected String driverName() {
	// return "IBM DB2 JDBC Universal Driver Architecture"; // new type 4
	// //return "IBM DB2 JDBC 2.0 Type 2"; // old type 2
	// }

	@Override protected String driverName() {
		return "Apache Derby Embedded JDBC Driver";
	}

	/**
	 * Derby supports different locking levels, but no FOR UPDATE with ORDER BY.
	 * So supportsLocking returns false for now ...
	 */
	@Override public boolean supportsLocking() {
		return true;
	} 

	@Override public int maxIdentNameLength() {
		return 18;
	} // Yes, only 18 chars!

	/** DB2 does not allow just NULL. */
	@Override protected void addNull(StringBuffer sql, SFieldScalar fld) {
		if (fld.isPrimary() || fld.isMandatory())
			sql.append(" NOT NULL");
	}

    /** Supports keys created during INSERTion of new records (MSSQL & MySQL Style). */
    @Override public boolean supportsInsertKeyGeneration() {
		return true;
	}

    @Override protected boolean includeGeneratedKeysInInsertValues() {return false;}

    @Override protected String addInsertGenerator(SFieldMeta fld) {
        return " GENERATED ALWAYS AS IDENTITY  "; // see JdbcTrials.java
        // Full: GENERATED ALWAYS AS IDENTITY (START WITH 1, INCREMENT BY 1)
    }

	@Override protected String columnTypeSQL(SFieldScalar field, String defalt) {
		if (field.getGeneratorMode() == SGeneratorMode.SINSERT)
			return "BIGINT"; // Bug in Derby, cannot use NUMERIC
		else if (defalt.equals("BYTES"))
			return "LONG VARCHAR";
		else
			return defalt;
	}
	
	   @Override protected long retrieveInsertedKey(SRecordMeta<?> rec, SFieldScalar keyFld) {
			
			String qry = "values IDENTITY_VAL_LOCAL()";

			Object next = getSession().rawQuerySingle(qry, false);
			
			if (next == null)
				return 0;
			if (next instanceof Number)
				return ((Number) next).longValue();
			else
				return Long.parseLong(next.toString());
		}	
}
