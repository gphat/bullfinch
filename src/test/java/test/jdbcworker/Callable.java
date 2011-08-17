package test.jdbcworker;

import static org.junit.Assert.fail;
import iinteractive.bullfinch.JDBCWorker;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class Callable {

	private Connection conn;
	private JDBCWorker worker;

	@Before
	public void createDatabase() {

		try {
			Connection conn = DriverManager.getConnection("jdbc:hsqldb:file:tmp/tmp;shutdown=true", "SA", "");

			Statement stMakeTable = conn.createStatement();
			stMakeTable.execute("CREATE TABLE PUBLIC.TEST_TABLE (an_int INTEGER, a_float FLOAT, a_bool BOOLEAN, a_string VARCHAR(32))");
			stMakeTable.close();

			this.conn = conn;

			HashMap<String,String> connection = new HashMap<String,String>();
			connection.put("dsn", "jdbc:hsqldb:file:tmp/tmp;shutdown=true");
			connection.put("driver", "org.hsqldb.jdbcDriver");
			connection.put("uid", "SA");
			connection.put("validation", "SELECT current_timestamp FROM PUBLIC.TEST_TABLE");


			HashMap<String,HashMap<String,Object>> procedures = new HashMap<String,HashMap<String,Object>>();

			HashMap<String,Object> getString = new HashMap<String,Object>();
			ArrayList<String> getStringParams = new ArrayList<String>();
			getStringParams.add("NUMBER");
			getString.put("params", getStringParams);
			procedures.put("ABS", getString);

			HashMap<String,Object> config = new HashMap<String,Object>();
			config.put("connection", connection);
			config.put("procedures", procedures);

			JDBCWorker worker = new JDBCWorker();

			worker.configure(config);

			this.worker = worker;

		} catch(Exception e) {
			e.printStackTrace();
			fail(e.toString());
		}
	}

	@Test
	/**
	 * Test that a statement with no params fails when none are passed.
	 */
	public void testMissingParams() {

	}

	@After
	public void dropTable() {

		try {
			Statement dropper = this.conn.createStatement();
			dropper.execute("DROP TABLE PUBLIC.TEST_TABLE");
		} catch(Exception e) {
			e.printStackTrace();
			fail(e.toString());
		}

	}
}
