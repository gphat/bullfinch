package iinteractive.bullfinch.minion;

import iinteractive.bullfinch.PerformanceCollector;
import iinteractive.bullfinch.util.JSONResultSetWrapper;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;

import org.apache.commons.dbcp.BasicDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A worker for executing running a query that finds rows and marks them
 * after successfully sending them to kestrel.
 *
 * @author gphat
 *
 */
public class JDBCTableScanner extends KestrelBased {

	static Logger logger = LoggerFactory.getLogger(JDBCTableScanner.class);

	private long interval = 60000;
	private String publishTo;
	private String selectQuery;
	private String markQuery;
	private String markKey;

	private BasicDataSource ds;

	public JDBCTableScanner(PerformanceCollector collector) {

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
			throw new Exception("JDBCTableScanner configuration needs a connection section");
		}

		String driver = connConfig.get("driver");
		if(driver == null) {
			throw new Exception("JDBCTableScanner configuration needs a connection -> driver");
		}
		ds.setDriverClassName(driver);

		String username = connConfig.get("uid");
		if(username == null) {
			throw new Exception("JDBCTableScanner configuration needs a connection -> username");
		}
		ds.setUsername(username);

		ds.setPassword(connConfig.get("pwd"));

		String validationQuery = connConfig.get("validation");
		if(validationQuery == null) {
			throw new Exception("JDBCTableScanner configuration needs a connection -> validation");
		}
		ds.setValidationQuery(validationQuery);

		String dsn = connConfig.get("dsn");
		if(dsn == null) {
			throw new Exception("JDBCTableScanner configuration needs a connection -> dsn");
		}
		ds.setUrl(dsn);

		Long intervalLng = (Long) config.get("interval");
		if(intervalLng != null) {
			interval = intervalLng.intValue();
		}

		publishTo = (String) config.get("publish_to");
		if(publishTo == null) {
			throw new Exception("JDBCTableScanner configuration needs publish_to");
		}

		selectQuery = (String) config.get("select_query");
		if(selectQuery == null) {
			throw new Exception("JDBCTableScanner configuration needs select_query");
		}

		markQuery = (String) config.get("mark_query");
		if(selectQuery == null) {
			throw new Exception("JDBCTableScanner configuration needs mark_query");
		}

		markKey = (String) config.get("mark_key");
		if(markKey == null) {
			throw new Exception("JDBCTableScanner configuration needs mark_key");
		}
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

		logger.debug("Began JDBCTableScanner thread with time of " + interval + ".");

		try {
			while(this.shouldContinue()) {

				// Sleep for a bit.
				Thread.sleep(interval);

				logger.debug("JDBCTableScanner expired.");
				sendRows();
			}
		} catch(Exception e) {
			logger.error("Got an Exception, sleeping 30 seconds before retrying", e);
			try { Thread.sleep(30000); } catch(InterruptedException ie) { Thread.currentThread().interrupt(); }
		}
	}

	/**
	 * Written as a separate method to facilitate testing
	 *
	 * @throws SQLException
	 */
	public void sendRows() throws SQLException {

		Connection conn = null;
		PreparedStatement selectStatement = null;
		PreparedStatement markStatement = null;
		ResultSet rs = null;
		try {
			conn = ds.getConnection();

			// Get our queries ready
			selectStatement = conn.prepareStatement(selectQuery);
			markStatement = conn.prepareStatement(markQuery);

			selectStatement.execute();
			rs = selectStatement.getResultSet();
			JSONResultSetWrapper wrapper = new JSONResultSetWrapper(null, markKey, rs);
			while(wrapper.hasNext()) {
				// Do the bind first…
				wrapper.bindKeyToQuery(markStatement);
				// Now send and mark as quickly as possible to minimize
				// any failures that could cause the row to be send and
				// not marked.
				sendMessage(publishTo, wrapper.next());
				logger.debug("Deleting sent row.");
				markStatement.execute();
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
			if(markStatement != null) {
				markStatement.close();
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
}
