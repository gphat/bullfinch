package iinteractive.bullfinch;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;

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
        STRING, INTEGER, BOOLEAN
    }

	static Logger logger = LoggerFactory.getLogger(JDBCWorker.class);
	private Connection conn;

	private HashMap<String,PreparedStatement> statementMap;
	private HashMap<String,ArrayList<String>> paramMap;


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

		// Make the database connection
		Class.forName(connConfig.get("driver")).newInstance();
		this.conn = DriverManager.getConnection(
			connConfig.get("dsn"), connConfig.get("uid"), connConfig.get("pwd")
		);

		// Create some empty maps
		this.statementMap = new HashMap<String,PreparedStatement>();
		this.paramMap = new HashMap<String,ArrayList<String>>();

		// Get the statement config
		@SuppressWarnings("unchecked")
		HashMap<String,HashMap<String,Object>> statements = (HashMap<String,HashMap<String,Object>>) config.get("statements");
		if(statements == null) {
			throw new Exception("Configuration needs a 'statements' section");
		}

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
			this.statementMap.put(key, conn.prepareStatement(stmt));

			// If the statement has params, stuff them into a param map
			if(stmtInfo.containsKey("params")) {
				@SuppressWarnings("unchecked")
				ArrayList<String> pList = (ArrayList<String>) stmtInfo.get("params");
				this.paramMap.put(key, pList);
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


		ResultSet rs = bindAndExecuteQuery(request);

		return new JSONResultSetWrapper(rs);
	}

	/*
	 * Find the query and execute it it.
	 */
	private ResultSet bindAndExecuteQuery(HashMap<String,Object> request) throws Exception {

		// Verify the requested statement exists
		String stmt = (String) request.get("statement");
		PreparedStatement prepStatement = this.statementMap.get(stmt);
		if(prepStatement == null) {
			throw new Exception("Unknown statement " + stmt);
		}

		@SuppressWarnings("unchecked")
		ArrayList<Object> rparams = (ArrayList<Object>) request.get("params");
		ArrayList<String> reqParams = this.paramMap.get(stmt);
		if(reqParams != null) {

			// Verify we have params if they are needed
			if(rparams == null) {
				throw new Exception("Statement " + stmt + " requires params");
			}
			if(rparams.size() != reqParams.size()) {
				throw new Exception("Statement expects " + reqParams.size() + " but was given " + rparams.size());
			}

			for(int i = 0; i < reqParams.size(); i++) {
                String paramType = (String) reqParams.get(i);
                switch ( ParamTypes.valueOf( paramType ) ) {
                    case INTEGER :
                        prepStatement.setInt(i + 1, ((Long) rparams.get(i)).intValue() );
                        break;
                    case STRING :
                        prepStatement.setString(i + 1, (String) rparams.get(i));
                        break;
                    case BOOLEAN :
                        prepStatement.setBoolean(i + 1, ((Boolean) rparams.get(i)).booleanValue());
                        break;
                    default :
                        throw new Exception ("Don't understand param-type '" + paramType + "'");
                }
			}
		}

		return prepStatement.executeQuery();
	}
}
