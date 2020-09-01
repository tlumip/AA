package simpleorm.utils;

import org.apache.log4j.Logger;

/**
 * An implementation of SLog to allow Simpleorm to log via log4j.
 * Log level is then controlled by log4j, not by SLog.slog.level.
 * 
 * Mapping between SLog and slf4j log levels is:
 * 
 * To use this logging API, you must:
 * 1) SLog.setSlogClass(SLogLog4j.class); somewhere early in you app (in you servlet init sequence for example)
 * 2) make sure you have log4j in your classpath
 * 
 * @author jabraham@ucalgary.ca based on original code by franck.routier@axege.com
 *
 */
public class SLogLog4j extends SLog {
	
	Logger log = Logger.getLogger(SLogLog4j.class);

	public int level = 10;

	public void error(String msg) { // Rarely used, normally throw instead.
        log.error("#### -ERROR " + sessionToString() + "_" + msg);
	}

	public void warn(String msg) {
        log.warn("## -WARN " + sessionToString() + "_" + msg);
	}

	// Open, begin, commit etc. 
	public void connections(String msg) {
        log.info("  -" + msg);
	}

	// During flushing
	public boolean enableUpdates() {
		return log.isDebugEnabled();
	}

	public void updates(String msg) {
		if (enableUpdates())
            log.debug("    -" + msg);
	}

	// select() or findOrCreate()
	public boolean enableQueries() {
		return log.isDebugEnabled();
	}

	public void queries(String msg) {
		if (enableQueries())
            log.debug("      -" + msg);
	}

	/**
	 * set/get per field. enableFields enables the trace line to be surounded by
	 * an if test which is important as fields are an inner loop trace and the
	 * StringBuffer concatenation can be very expensive!
	 */
	public boolean enableFields() {
		return false;
	}

	public void fields(String msg) {
		if (enableFields())
		    log.debug("        -" + msg);
	}

	/** For detailed temporary traces during development only. */
	public boolean enableDebug() {
		return log.isDebugEnabled();
	}

	public void debug(String msg) { 
        log.debug("          -" + "(" + msg + ")");
	}

	/**
	 * For messages that are the ouput, eg. of unit test programs. Never
	 * disabled.
	 */
	public void message(String msg) {
        log.warn(" ---" + msg);
	}

	public static String arrayToString(Object[] array) {
		if (array == null)
			return "NULL";
		StringBuffer res = new StringBuffer("[Array ");
		for (int ax = 0; ax < array.length; ax++) {
			if (ax > 0)
				res.append("|");
			res.append(array[ax]);
		}
		res.append("]");
		return res.toString();
	}
    
}
