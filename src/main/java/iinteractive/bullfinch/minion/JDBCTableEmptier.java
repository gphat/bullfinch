package iinteractive.bullfinch.minion;

import iinteractive.bullfinch.PerformanceCollector;
import iinteractive.bullfinch.Phrasebook;
import iinteractive.bullfinch.Phrasebook.ParamType;
import iinteractive.bullfinch.util.JSONResultSetWrapper;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.apache.commons.dbcp.BasicDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A worker for executing running a query that finds rows and deletes them
 * after successfully sending them to kestrel.
 *
 * @author gphat
 *
 */
public class JDBCTableEmptier extends KestrelBased {

	static Logger logger = LoggerFactory.getLogger(JDBCTableEmptier.class);

	private long interval = 60000;
	private String publishTo;
	private String selectQuery;
	private String deleteQuery;
	private String deleteKey;

	private Phrasebook statementBook;

	private BasicDataSource ds;

	public JDBCTableEmptier(PerformanceCollector collector) {

		super(collector);
	}

	/**
	 * Configure the worker.
	 *
	 * The configuration is expected to contain statements and (optionally)
	 *
	 *
	 * @param config
	 * @throws Exception
	 */
	public void configure(HashMap<String,Object> config) throws Exception {

		super.configure(config);

		// Not going to try anything fancy here.  If this fails, then
		// the exception will bubble all the way up.
		ds = new BasicDataSource();

		// Only allow one connection, as we're not really using this for the
		// pooling so much as the connection verification and whatnot.
		ds.setMaxActive(1);
		ds.setMaxIdle(1);
		ds.setPoolPreparedStatements(true);
		ds.setTestOnBorrow(true);
		ds.setTestWhileIdle(true);

		@SuppressWarnings("unchecked")
		HashMap<String,String> connConfig = (HashMap<String,String>) config.get("connection");
		if(connConfig == null) {
			throw new Exception("JDBCTableEmptier configuration needs a connection section");
		}

		String driver = connConfig.get("driver");
		if(driver == null) {
			throw new Exception("JDBCTableEmptier configuration needs a connection -> driver");
		}
		ds.setDriverClassName(driver);

		String username = connConfig.get("uid");
		if(username == null) {
			throw new Exception("JDBCTableEmptier configuration needs a connection -> username");
		}
		ds.setUsername(username);

		ds.setPassword(connConfig.get("pwd"));

		String validationQuery = connConfig.get("validation");
		if(validationQuery == null) {
			throw new Exception("JDBCTableEmptier configuration needs a connection -> validation");
		}
		ds.setValidationQuery(validationQuery);

		String dsn = connConfig.get("dsn");
		if(dsn == null) {
			throw new Exception("JDBCTableEmptier configuration needs a connection -> dsn");
		}
		ds.setUrl(dsn);

		Long intervalLng = (Long) config.get("interval");
		if(intervalLng != null) {
			interval = intervalLng.intValue();
		}

		publishTo = (String) config.get("publish_to");
		if(publishTo == null) {
			throw new Exception("JDBCTableEmptier configuration needs publish_to");
		}

		selectQuery = (String) config.get("select_query");
		if(selectQuery == null) {
			throw new Exception("JDBCTableEmptier configuration needs select_query");
		}

		deleteQuery = (String) config.get("delete_query");
		if(selectQuery == null) {
			throw new Exception("JDBCTableEmptier configuration needs delete_query");
		}

		deleteKey = (String) config.get("delete_key");
		if(deleteKey == null) {
			throw new Exception("JDBCTableEmptier configuration needs delete_key");
		}

		// Setup our connection pool
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

		logger.debug("Began JDBCTableEmptier thread with time of " + interval + ".");

		try {
			while(this.shouldContinue()) {

				// Sleep for a bit.
				Thread.sleep(interval);

				logger.debug("Emptier expired.");
				Connection conn = null;
				PreparedStatement selectStatement = null;
				PreparedStatement deleteStatement = null;
				ResultSet rs = null;
				try {
					conn = ds.getConnection();

					// Get our queries ready
					selectStatement = conn.prepareStatement(selectQuery);
					deleteStatement = conn.prepareStatement(deleteQuery);

					selectStatement.execute();
					rs = selectStatement.getResultSet();
					JSONResultSetWrapper wrapper = new JSONResultSetWrapper("", deleteKey, rs); // XXX fix tracer
					while(wrapper.hasNext()) {
						// Do the bind first…
						wrapper.bindKeyToQuery(deleteStatement);
						// Now send and delete as quicly as possible to minimize
						// any failures that could cause the row to be send and
						// not deleted.
						sendMessage(publishTo, wrapper.next());
 						logger.debug("Deleting sent row.");
						deleteStatement.execute();
					}
				} catch(SQLException e) {
					logger.error("Error fetching/deleting rows.", e);
				} finally {
					if(rs != null) {
						rs.close();
					}
					if(selectStatement != null) {
						selectStatement.close();
					}
					if(deleteStatement != null) {
						deleteStatement.close();
					}
					if(conn != null) {
						try {
							conn.close();
						} catch(SQLException e) {
							logger.error("Failed to close connection", e);
						}
					}
				}
			}
		} catch(Exception e) {
			logger.error("Got an Exception, sleeping 30 seconds before retrying", e);
			try { Thread.sleep(30000); } catch(InterruptedException ie) { Thread.currentThread().interrupt(); }
		}
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
