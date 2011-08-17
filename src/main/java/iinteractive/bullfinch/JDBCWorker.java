package iinteractive.bullfinch;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;

import org.apache.commons.dbcp.BasicDataSource;
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

    public enum ParamTypes {
        BOOLEAN, NUMBER, INTEGER, STRING
    }

	static Logger logger = LoggerFactory.getLogger(JDBCWorker.class);

	private String driver;
	private String dsn;
	private String username;
	private String password;
	private String validationQuery;

	private BasicDataSource ds;

	private HashMap<String,String> statementMap;
	private HashMap<String,ArrayList<String>> statementParamMap;

	private HashMap<String,ArrayList<String>> procedureParamMap;


	public JDBCWorker() {

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

		// Create some empty maps
		this.statementMap = new HashMap<String,String>();
		this.statementParamMap = new HashMap<String,ArrayList<String>>();
		this.procedureParamMap = new HashMap<String,ArrayList<String>>();

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
				this.statementMap.put(key, stmt);

				// If the statement has params, stuff them into a param map
				if(stmtInfo.containsKey("params")) {
					@SuppressWarnings("unchecked")
					ArrayList<String> pList = (ArrayList<String>) stmtInfo.get("params");
					if(pList.size() < 1) {
						logger.warn("statement claims params, but lists none!");
					}
					logger.debug("Statement has " + pList.size() + " params");
					this.statementParamMap.put(key, pList);
				}
			}
		}

		@SuppressWarnings("unchecked")
		HashMap<String,HashMap<String,Object>> procedures = (HashMap<String,HashMap<String,Object>>) config.get("procedures");

		if(procedures != null) {
			// Iterate over each procedure and prepare it, optionally storing it's
			// parameters as well.
			Iterator<String> keys = procedures.keySet().iterator();
			while(keys.hasNext()) {
				String name = keys.next();
				logger.debug("Loading procedure information for " + name);
				HashMap<String,Object> procInfo = procedures.get(name);

				if(procInfo.containsKey("params")) {
					@SuppressWarnings("unchecked")
					ArrayList<String> pList = (ArrayList<String>) procInfo.get("params");
					if(pList.size() < 1) {
						logger.warn("procedure claims params, but lists none!");
					}
					logger.debug("Procedure has " + pList.size() + " params");
					this.procedureParamMap.put(name, pList);
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
	public Iterator<String> handle(HashMap<String,Object> request) throws Exception {

		try {
			// Grab a connection from the pool
			Connection conn = this.ds.getConnection();

			// Get the resultset back and transfer it's content into a list so
			// that we can return an iterator AFTER closing the connection.
			ResultSet rs = bindAndExecuteQuery(conn, request);
			ArrayList<String> list = new ArrayList<String>();
			JSONResultSetWrapper wrapper =  new JSONResultSetWrapper(rs);
			while(wrapper.hasNext()) {
				list.add(wrapper.next());
			}
			conn.close();
			return list.iterator();
		} catch(Exception e) {
			logger.error("Got an exception from SQL execution", e);
			// In the case of an exception, reply back with an ERROR as the
			// key and the message as the value.
			JSONObject obj = new JSONObject();
			obj.put("ERROR", e.getMessage());
			ArrayList<String> list = new ArrayList<String>();
			list.add(obj.toString());
			return list.iterator();
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
		String statement = this.statementMap.get(name);
		if(statement == null) {
			throw new Exception("Unknown statement " + name);
		}

		PreparedStatement prepStatement = conn.prepareStatement(statement);

		@SuppressWarnings("unchecked")
		ArrayList<Object> rparams = (ArrayList<Object>) request.get("params");
		ArrayList<String> reqParams = this.statementParamMap.get(name);
		if(reqParams != null) {

			// Verify we have params if they are needed
			if(rparams == null) {
				throw new Exception("Statement " + name + " requires params");
			}
			if(rparams.size() != reqParams.size()) {
				throw new Exception("Statement expects " + reqParams.size() + " but was given " + rparams.size());
			}

			for(int i = 0; i < reqParams.size(); i++) {
                String paramType = (String) reqParams.get(i);
                switch ( ParamTypes.valueOf( paramType ) ) {
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

		return prepStatement.executeQuery();
	}
}
