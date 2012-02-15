package iinteractive.bullfinch;

import iinteractive.bullfinch.Phrasebook.ParamType;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

import org.apache.commons.dbcp.BasicDataSource;
import org.joda.time.DateTime;
import org.joda.time.Duration;
import org.json.simple.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A worker for executing JDBC statements over kestrel queues.
 *
 * @author gphat
 *
 */
public class JDBCWorker implements Worker {

	static Logger logger = LoggerFactory.getLogger(JDBCWorker.class);

	private String driver;
	private String dsn;
	private String username;
	private String password;
	private String validationQuery;
	private Duration durTTLProcessByDefault;

	private Phrasebook statementBook;

	private BasicDataSource ds;

	public JDBCWorker() {

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
		try {
			String ttl = (String) config.get("default_process_by_ttl");
			if(ttl == null) {
				ttl = "PT300S";
			}
			this.durTTLProcessByDefault = Duration.parse(ttl);
		} catch (Exception e) {
			throw new Exception("Configuration contains invalid default_process_by_ttl");
		}

		@SuppressWarnings("unchecked")
		HashMap<String,String> connConfig = (HashMap<String,String>) config.get("connection");
		if(connConfig == null) {
			throw new Exception("Configuration needs a 'connection' section");
		}

		this.driver = connConfig.get("driver");
		if(this.driver == null) {
			throw new Exception("Configuration needs a connection -> driver");
		}

		this.dsn = connConfig.get("dsn");
		if(this.dsn == null) {
			throw new Exception("Configuration needs a connection -> dsn");
		}

		this.username = connConfig.get("uid");
		if(this.username == null) {
			throw new Exception("Configuration needs a connection -> username");
		}

		this.password = connConfig.get("pwd");

		this.validationQuery = connConfig.get("validation");
		if(this.validationQuery == null) {
			throw new Exception("Configuration needs a connection -> validation");
		}

		// Setup our connection pool
		this.ds = connect();

		// Get the statement config
		@SuppressWarnings("unchecked")
		HashMap<String,HashMap<String,Object>> statements = (HashMap<String,HashMap<String,Object>>) config.get("statements");

		if(statements != null) {
			// Iterate over each statement and prepare it, optionally storing it's
			// parameters as well.
			Iterator<String> keys = statements.keySet().iterator();
			while(keys.hasNext()) {
				String key = keys.next();
				logger.debug("Loading statement information for " + key);
				// Get the { sql , [ params ] } bits
				HashMap<String,Object> stmtInfo = statements.get(key);

				// Prepare the statement here so we can benefit from it being ready
				// to go later.
				String stmt = (String) stmtInfo.get("sql");

				// If the statement has params, stuff them into a param map
				if(stmtInfo.containsKey("params")) {
					@SuppressWarnings("unchecked")

					// Convert parametrs from string to ParamType
					ArrayList<String> pTypes = (ArrayList<String>) stmtInfo.get("params");
					if(pTypes.size() < 1) {
						logger.warn("statement claims params, but lists none!");
					}
					ArrayList<ParamType> pList = new ArrayList<ParamType>(pTypes.size());

					// Do the actual conversion
					Iterator<String> pTypeIter = pTypes.iterator();
					while(pTypeIter.hasNext()) {
						pList.add(ParamType.valueOf(pTypeIter.next()));
					}

					logger.debug("Statement has " + pList.size() + " params");
					this.statementBook.addPhrase(key, stmt, pList);
				} else {
					this.statementBook.addPhrase(key, stmt);
				}
			}
		}
	}

	/**
	 * Handle a request.
	 *
	 * @param request The request as a post-json-parsed-hashmap.
	 * @return An iterator for sending the response back.
	 */
	public Iterator<String> handle(PerformanceCollector collector, HashMap<String,Object> request) throws ProcessTimeoutException {

		ArrayList<String> list = new ArrayList<String>();

		String tracer = (String) request.get("tracer");

		Connection conn = null;
		try {
			DateTime dtProcessBy;

			try {
				// try to parse the process-by date
				dtProcessBy = DateTime.parse((String) request.get("process-by"));
			} catch (Exception e) {
				// unable to parse the date, use default of now+ttl instead
				dtProcessBy = DateTime.now().withDurationAdded(this.durTTLProcessByDefault, 1);
			}

			if (dtProcessBy.isBefore(DateTime.now()))
				throw new ProcessTimeoutException("process-by time exceeded");

			// Grab a connection from the pool
			long connStart = System.currentTimeMillis();
			conn = this.ds.getConnection();
			collector.add(
				"Connection retrieval",
				System.currentTimeMillis() - connStart,
				tracer
			);

			// Get the resultset back and transfer it's content into a list so
			// that we can return an iterator AFTER closing the connection.
			long start = System.currentTimeMillis();
			ResultSet rs = bindAndExecuteQuery(conn, request);

			if(rs != null) {
				collector.add(
					"Query preparation and execution",
					System.currentTimeMillis() - start,
					tracer
				);

				JSONResultSetWrapper wrapper =  new JSONResultSetWrapper(
					(String) request.get("tracer"), rs
				);
				while(wrapper.hasNext()) {
					list.add(wrapper.next());
				}
            }
            // Check the process timeout again
			if (dtProcessBy.isBefore(DateTime.now()))
				throw new ProcessTimeoutException("process-by time exceeded");

		} catch(ProcessTimeoutException e) {
			logger.error(e.getMessage());
			throw new ProcessTimeoutException(e.getMessage());
		} catch(Exception e) {
			logger.error("Got an exception from SQL execution", e);
			// In the case of an exception, reply back with an ERROR as the
			// key and the message as the value.
			JSONObject obj = new JSONObject();
			obj.put("ERROR", e.getMessage());
			if(tracer != null) {
				obj.put("tracer", tracer);
			}
			list.add(obj.toString());
		} finally {
			if(conn != null) {
				try { conn.close(); } catch(SQLException e) { logger.error("Failed to close connection", e); }
			}
		}

		return list.iterator();
	}

	private BasicDataSource connect() throws Exception {

		// Not going to try anything fancy here.  If this fails, then
		// the exception will bubble all the way up.
		BasicDataSource ds = new BasicDataSource();

		// Only allow one connection, as we're not really using this for the
		// pooling so much as the connection verification and whatnot.
		ds.setMaxActive(1);
		ds.setMaxIdle(1);
		ds.setTestOnBorrow(true);
		ds.setPoolPreparedStatements(true);
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
