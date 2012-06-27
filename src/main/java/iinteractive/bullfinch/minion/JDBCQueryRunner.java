package iinteractive.bullfinch.minion;

import iinteractive.bullfinch.PerformanceCollector;
import iinteractive.bullfinch.Phrasebook;
import iinteractive.bullfinch.Phrasebook.ParamType;
import iinteractive.bullfinch.ProcessTimeoutException;
import iinteractive.bullfinch.util.JSONResultSetWrapper;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.TreeSet;

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
public class JDBCQueryRunner extends QueueMonitor {

	static Logger logger = LoggerFactory.getLogger(JDBCQueryRunner.class);

	private String driver;
	private String dsn;
	private String username;
	private String password;
	private String validationQuery;
	private Duration durTTLProcessByDefault;

	private HashMap<String, Boolean> executedWorkerPrequels;
	private Phrasebook statementBook;

	private BasicDataSource ds;

	public JDBCQueryRunner(PerformanceCollector collector) {

		super(collector);
		this.statementBook = new Phrasebook();
		this.executedWorkerPrequels = new HashMap<String, Boolean>();
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
		try {
			String ttl = (String) config.get("default_process_by_ttl");
			if(ttl == null) {
				ttl = "PT300S";
			}
			this.durTTLProcessByDefault = Duration.parse(ttl);
		} catch (Exception e) {
			throw new Exception("JDBCMinion configuration contains invalid default_process_by_ttl");
		}

		@SuppressWarnings("unchecked")
		HashMap<String,String> connConfig = (HashMap<String,String>) config.get("connection");
		if(connConfig == null) {
			throw new Exception("JDBCMinion configuration needs a 'connection' section");
		}

		this.driver = connConfig.get("driver");
		if(this.driver == null) {
			throw new Exception("JDBCMinion configuration needs a connection -> driver");
		}

		this.dsn = connConfig.get("dsn");
		if(this.dsn == null) {
			throw new Exception("JDBCMinion configuration needs a connection -> dsn");
		}

		this.username = connConfig.get("uid");
		if(this.username == null) {
			throw new Exception("JDBCMinion configuration needs a connection -> username");
		}

		this.password = connConfig.get("pwd");

		this.validationQuery = connConfig.get("validation");
		if(this.validationQuery == null) {
			throw new Exception("JDBCMinion configuration needs a connection -> validation");
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
				logger.debug("Loading statement information for {}", key);
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

					logger.debug("Statement has {} params", pList.size());
					statementBook.addPhrase(key, stmt, pList);
				} else {
					statementBook.addPhrase(key, stmt);
				}
			}
		}
	}

	/**
	 * Handle a request.
	 *
	 * @param collector Instance of PerformanceCollector
	 * @param responseQueue queue name to send response to
	 * @param request The request as a post-json-parsed-hashmap.
	 * @return An iterator for sending the response back.
	 */
	public void handle(PerformanceCollector collector, String responseQueue, HashMap<String,Object> request) throws ProcessTimeoutException {

		String tracer = (String) request.get("tracer");
		
		Object protoTrans = request.get("use_transaction");
		boolean useTransaction = false;
		if(protoTrans instanceof Boolean) {
			useTransaction = (Boolean) protoTrans;
		}
		if(protoTrans instanceof Integer) {
			if(((Integer) protoTrans) > 0) {
				useTransaction = true;
			}
		}
		if(protoTrans instanceof String) {
			if(((String) protoTrans).equalsIgnoreCase("true")) {
				useTransaction = true;
			}
		}

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

			// Get the list of params early so we can use it when building up the
			// list of work.
			ArrayList<ArrayList<Object>> params = new ArrayList<ArrayList<Object>>();
			ArrayList<Object> rparams = (ArrayList<Object>) request.get("params");
			
			// First check for a standalone statement
			ArrayList<String> statements = new ArrayList<String>();
			String rname = (String) request.get("statement");
			if(rname == null) {
				// Now try a list of statements
				List<String> names = (List<String>) request.get("statements");
				if(names == null) {
					throw new Exception("Request must have either statement or statements.");
				}
				statements.addAll(names);
			} else {
				statements.add(rname);
			}
					
			// Make sure we have some statements.
			if(statements.size() < 1) {
				throw new Exception("Request has no statements");
			}
			if(statements.size() > 1) {
				if(!(rparams.get(0) instanceof ArrayList)) {
					throw new Exception("Multiple statements require multiple lists of params!");
				}
				if(rparams.size() != statements.size()) {
					throw new Exception("Multiple statements require a matching number of parameter lists.");
				}
			}
			
			// Validate that all of the incoming statements are in the
			// Phrasebook and have the appropriate number of parameters.
			for(int i = 0; i < statements.size(); i++) {
				String s = statements.get(i);
				logger.debug("Validating incoming statement {}", s);				

				// Verify the statement is legit.
				if(statementBook.getPhrase(s) == null) {
					throw new Exception("Statement " + s + " does not exist!");
				}
				List<ParamType> reqParams = statementBook.getParams(s);
				if(reqParams != null) {

					if(rparams == null || (rparams.size() < 1)) {
						throw new Exception("Statement " + s + " requires params");
					}
					// Grab the params we got in the request for the statement
					// at this index.
					Object sparam = rparams.get(i); // Let index exception handle things that don't exist
					
					if(statements.size() > 1) {
						// For multiple statements we will cast the param item
						// as an ArrayList and add it to the param listing.
						ArrayList<Object> newlist = (ArrayList<Object>) sparam;
						// First make sure we got as many params as we need.
						if(newlist.size() != reqParams.size()) {
							throw new Exception("Statement expects "+ reqParams.size() + " params but was given " + newlist.size());
						}
						// Add it to the list at the same index as the statement.
						params.add(newlist);
					} else {
						// We'll just add this as one paramlist to the overall
						// list.
						if(rparams.size() != reqParams.size()) {
							throw new Exception("Statement expects "+ reqParams.size() + " params but was given " + rparams.size());
						}
						params.add(rparams);
					}
				} else {
					// Add an empty param list so we don't have to deal with null
					params.add(new ArrayList<Object>());
				}
			}
			
			logger.debug("Have {} statements and {} parameters.", statements.size(), params.size());
			if(params.size() != statements.size()) {
				throw new Exception("Number of statements does not match number of parameters!");
			}
			
			// Grab a connection from the pool
			long connStart = System.currentTimeMillis();
			conn = this.ds.getConnection();
			collector.add(
				"Connection retrieval",
				System.currentTimeMillis() - connStart,
				tracer
			);
			if(useTransaction) {
				// We are to use a transaction, set auto-commit to false.
				conn.setAutoCommit(false);
			}
			
			// Execute each statement in turn, sending it's results back as
			// we get them.
			for(int i = 0; i < statements.size(); i++) {
				long start = System.currentTimeMillis();
				String s = statements.get(i);
				
				PreparedStatement ps = null;
				ResultSet rs = null;
				
				ArrayList<Object> sparams = params.get(i);
				String sql = statementBook.getPhrase(s);
				
				// Execute each statement in it's own try to protect it's
				// ps and rs, guaranteeing they are closed.
				try {
					ps = bindAndExecuteQuery(conn, s, sql, sparams);
					rs = ps.getResultSet();

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
							sendMessage(responseQueue, wrapper.next());
						}
					}
				} finally {
					if(ps != null) {
						try { ps.close(); } catch(SQLException e) { logger.error("Failed to close statement", e); }
					}
					if(rs != null) {
						try { rs.close(); } catch(SQLException e) { logger.error("Failed to close resultset", e); }
					}
				}
				
			}

			// Check the process timeout again
			if (dtProcessBy.isBefore(DateTime.now()))
				throw new ProcessTimeoutException("process-by time exceeded");

			if(useTransaction) {
				// Commit our transaction
				logger.debug("Committing work.");
				conn.commit(); 
			}
			
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
			// We need to send back an error create a new list to get an
			// iterator from.
			sendMessage(responseQueue, obj.toString());
		} finally {
			if(conn != null) {
				try { conn.setAutoCommit(true); } catch(SQLException e) { logger.error("Failed to set auto-commit back to true"); }
				try { conn.close(); } catch(SQLException e) { logger.error("Failed to close connection", e); }
			}
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
	private PreparedStatement bindAndExecuteQuery(Connection conn, String name, String statement, ArrayList<Object> rparams) throws Exception {

		PreparedStatement prepStatement = conn.prepareStatement(statement);
		List<ParamType> reqParams = this.statementBook.getParams(name);
		if(reqParams != null) {

			// Verify we have params if they are needed
			if(rparams == null) {
				throw new Exception("Statement " + name + " requires params");
			}
			if(rparams.size() != reqParams.size()) {
				throw new Exception("Statement expects " + reqParams.size() + " params but was given " + rparams.size());
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

		return prepStatement;
	}
}
