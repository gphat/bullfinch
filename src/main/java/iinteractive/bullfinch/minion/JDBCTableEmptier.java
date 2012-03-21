package iinteractive.bullfinch.minion;

import iinteractive.bullfinch.PerformanceCollector;
import iinteractive.bullfinch.Phrasebook;
import iinteractive.bullfinch.Phrasebook.ParamType;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.apache.commons.dbcp.BasicDataSource;
import org.joda.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A worker for executing JDBC statements over kestrel queues.
 *
 * @author gphat
 *
 */
public class JDBCTableEmptier extends KestrelBased {

	static Logger logger = LoggerFactory.getLogger(JDBCTableEmptier.class);

	private String driver;
	private String dsn;
	private String username;
	private String password;
	private String validationQuery;
	private Duration durTTLProcessByDefault;
	private long interval = 60000;

	private Phrasebook statementBook;

	private BasicDataSource ds;

	public JDBCTableEmptier(PerformanceCollector collector) {

		super(collector);
		this.statementBook = new Phrasebook();
	}

	/**
	 * Configure the worker.
	 *
	 * The configuration is expected to contain statements and (optionally)
	 * parameters:
	 *
	 * "statements" : {
     * 		"getAllAddresses" : {
     *          "sql"    : "SELECT * FROM address",
     *       },
     *       "getAllActiveECodesByPage" : {
     *           "sql"    : "select * from EMT_RENTAL_PRODUCT_V where ISACTIVE = 'N' and ROWNUM >= ? and ROWNUM <= ?",
     *           "params" : [ "INTEGER", "INTEGER" ]
     *       }
     *   }
	 *
	 * @param config
	 * @throws Exception
	 */
	public void configure(HashMap<String,Object> config) throws Exception {

		super.configure(config);

		@SuppressWarnings("unchecked")
		HashMap<String,String> connConfig = (HashMap<String,String>) config.get("connection");
		if(connConfig == null) {
			throw new Exception("JDBCTableEmptier configuration needs a 'connection' section");
		}

		this.driver = connConfig.get("driver");
		if(this.driver == null) {
			throw new Exception("JDBCTableEmptier configuration needs a connection -> driver");
		}

		this.dsn = connConfig.get("dsn");
		if(this.dsn == null) {
			throw new Exception("JDBCTableEmptier configuration needs a connection -> dsn");
		}

		this.username = connConfig.get("uid");
		if(this.username == null) {
			throw new Exception("JDBCTableEmptier configuration needs a connection -> username");
		}

		this.password = connConfig.get("pwd");

		this.validationQuery = connConfig.get("validation");
		if(this.validationQuery == null) {
			throw new Exception("JDBCTableEmptier configuration needs a connection -> validation");
		}

		Long intervalLng = (Long) config.get("interval");
		if(intervalLng != null) {
			interval = intervalLng.intValue();
		}

		// Setup our connection pool
		this.ds = connect();

		// Get the statement config
		@SuppressWarnings("unchecked")
		HashMap<String,HashMap<String,Object>> statements = (HashMap<String,HashMap<String,Object>>) config.get("statements");
	}

	/**
	 * Run the thread.  This method will sleep for INTERVAL milliseconds, then
	 * attempt to empty out the table.
	 *
	 * Note: If an Exception is caught in communicating with kestrel then we'll
	 * just keep trying forever.
	 *
	 */
	public void run() {

		logger.debug("Began emitter thread with time of " + interval + ".");

		try {
			while(this.shouldContinue()) {

				// Sleep for a bit.
				Thread.sleep(interval);

				logger.debug("Emptier expired.");
			}
		} catch(Exception e) {
			logger.error("Got an Exception, sleeping 30 seconds before retrying", e);
			try { Thread.sleep(30000); } catch(InterruptedException ie) { Thread.currentThread().interrupt(); }
		}
	}

	private BasicDataSource connect() throws Exception {

		// Not going to try anything fancy here.  If this fails, then
		// the exception will bubble all the way up.
		BasicDataSource ds = new BasicDataSource();

		// Only allow one connection, as we're not really using this for the
		// pooling so much as the connection verification and whatnot.
		ds.setMaxActive(1);
		ds.setMaxIdle(1);
		ds.setPoolPreparedStatements(true);
		ds.setTestOnBorrow(true);
		ds.setTestWhileIdle(true);
		ds.setValidationQuery(this.validationQuery);

		ds.setDriverClassName(this.driver);
		ds.setUsername(this.username);
		ds.setPassword(this.password);
		ds.setUrl(this.dsn);
		return ds;
	}

	/*
	 * Find the query and execute it it.
	 */
	private ResultSet bindAndExecuteQuery(Connection conn, HashMap<String,Object> request) throws Exception {

		// Verify the requested statement exists
		String name = (String) request.get("statement");
		String statement = this.statementBook.getPhrase(name);
		if(statement == null) {
			throw new Exception("Unknown statement " + name);
		}

		PreparedStatement prepStatement = conn.prepareStatement(statement);

		@SuppressWarnings("unchecked")
		ArrayList<Object> rparams = (ArrayList<Object>) request.get("params");
		List<ParamType> reqParams = this.statementBook.getParams(name);
		if(reqParams != null) {

			// Verify we have params if they are needed
			if(rparams == null) {
				throw new Exception("Statement " + name + " requires params");
			}
			if(rparams.size() != reqParams.size()) {
				throw new Exception("Statement expects " + reqParams.size() + " but was given " + rparams.size());
			}

			for(int i = 0; i < reqParams.size(); i++) {
                ParamType paramType = reqParams.get(i);
                switch ( paramType ) {
	                case BOOLEAN :
	                    prepStatement.setBoolean(i + 1, ((Boolean) rparams.get(i)).booleanValue());
	                    break;
	                case NUMBER :
	                	prepStatement.setDouble(i + 1, ((Number) rparams.get(i)).doubleValue());
	                	break;
	                case INTEGER :
                        prepStatement.setInt(i + 1, ((Long) rparams.get(i)).intValue() );
                        break;
                    case STRING :
                        prepStatement.setString(i + 1, (String) rparams.get(i));
                        break;
                    default :
                        throw new Exception ("Don't understand param-type '" + paramType + "'");
                }
			}
		}

		prepStatement.execute();

		return prepStatement.getResultSet();
	}
}
