package test.jdbcworker;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.fail;
import iinteractive.bullfinch.JDBCWorker;
import iinteractive.bullfinch.Phrasebook.ParamTypes;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;

import org.json.simple.JSONObject;
import org.json.simple.JSONValue;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class Simple {

	private Connection conn;
	private JDBCWorker worker;

	@Before
	public void createDatabase() {

		try {
			Connection conn = DriverManager.getConnection("jdbc:hsqldb:file:tmp/tmp;shutdown=true", "SA", "");

			Statement stMakeTable = conn.createStatement();
			stMakeTable.execute("CREATE TABLE PUBLIC.TEST_TABLE (an_int INTEGER, a_float FLOAT, a_bool BOOLEAN, a_string VARCHAR(32))");
			stMakeTable.close();

			Statement stAddOne = conn.createStatement();
			stAddOne.execute("INSERT INTO PUBLIC.TEST_TABLE (an_int, a_float, a_bool, a_string) VALUES (12, 3.14, true, 'cory')");
			stAddOne.close();

			Statement stAddTwo = conn.createStatement();
			stAddTwo.execute("INSERT INTO PUBLIC.TEST_TABLE (an_int, a_float, a_bool, a_string) VALUES (13, 2.14, false, 'cory')");
			stAddTwo.close();

			this.conn = conn;

			HashMap<String,String> connection = new HashMap<String,String>();
			connection.put("dsn", "jdbc:hsqldb:file:tmp/tmp;shutdown=true");
			connection.put("driver", "org.hsqldb.jdbcDriver");
			connection.put("uid", "SA");
			connection.put("validation", "SELECT current_timestamp FROM PUBLIC.TEST_TABLE");

			HashMap<String,HashMap<String,Object>> statements = new HashMap<String,HashMap<String,Object>>();

			HashMap<String,Object> getInt = new HashMap<String,Object>();
			getInt.put("sql", "SELECT an_int FROM PUBLIC.TEST_TABLE WHERE an_int=?");
			ArrayList<ParamTypes> getIntParams = new ArrayList<ParamTypes>();
			getIntParams.add(ParamTypes.INTEGER);
			getInt.put("params", getIntParams);
			statements.put("getInt", getInt);

			HashMap<String,Object> getFloat = new HashMap<String,Object>();
			getFloat.put("sql", "SELECT a_float FROM PUBLIC.TEST_TABLE WHERE a_float=?");
			ArrayList<ParamTypes> getFloatParams = new ArrayList<ParamTypes>();
			getFloatParams.add(ParamTypes.NUMBER);
			getFloat.put("params", getFloatParams);
			statements.put("getFloat", getFloat);

			HashMap<String,Object> getBool = new HashMap<String,Object>();
			getBool.put("sql", "SELECT a_bool FROM PUBLIC.TEST_TABLE WHERE a_bool=?");
			ArrayList<ParamTypes> getBoolParams = new ArrayList<ParamTypes>();
			getBoolParams.add(ParamTypes.BOOLEAN);
			getBool.put("params", getBoolParams);
			statements.put("getBool", getBool);

			HashMap<String,Object> getString = new HashMap<String,Object>();
			getString.put("sql", "SELECT a_string FROM PUBLIC.TEST_TABLE WHERE a_string=?");
			ArrayList<ParamTypes> getStringParams = new ArrayList<ParamTypes>();
			getStringParams.add(ParamTypes.STRING);
			getString.put("params", getStringParams);
			statements.put("getString", getString);

			HashMap<String,Object> config = new HashMap<String,Object>();
			config.put("connection", connection);
			config.put("statements", statements);

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

		JDBCWorker worker = this.worker;

		try {
			HashMap<String,Object> request = new HashMap<String,Object>();
			request.put("statement", "getInt");

			Iterator<String> iter = worker.handle(request);

			assertEquals("Got error for missing params", "{\"ERROR\":\"Statement getInt requires params\"}", iter.next());

		} catch(Exception e) {
			e.printStackTrace();
			fail(e.toString());
		}
	}

	@Test
	/**
	 * Test the getInt statement
	 */
	public void testInt() {

		JDBCWorker worker = this.worker;

		try {
			JSONObject request = (JSONObject) JSONValue.parse("{\"statement\":\"getInt\",\"params\":[12]}");

			Iterator<String> iter = worker.handle(request);
			assertEquals("getInt result", "{\"row_data\":{\"AN_INT\":12},\"row_num\":1}", iter.next());
			assertFalse("no more rows", iter.hasNext());

		} catch(Exception e) {
			e.printStackTrace();
			fail(e.getMessage());
		}
	}

	@Test
	/**
	 * Test the getFloat statement
	 */
	public void testFloat() {

		JDBCWorker worker = this.worker;

		try {
			JSONObject request = (JSONObject) JSONValue.parse("{\"statement\":\"getFloat\",\"params\":[3.14]}");

			Iterator<String> iter = worker.handle(request);
			assertEquals("getFloat result", "{\"row_data\":{\"A_FLOAT\":3.14},\"row_num\":1}", iter.next());
			assertFalse("no more rows", iter.hasNext());

		} catch(Exception e) {
			e.printStackTrace();
			fail(e.getMessage());
		}
	}

	@Test
	/**
	 * Test the getBool statement
	 */
	public void testBool() {

		JDBCWorker worker = this.worker;

		try {
			JSONObject request = (JSONObject) JSONValue.parse("{\"statement\":\"getBool\",\"params\":[true]}");

			Iterator<String> iter = worker.handle(request);
			assertEquals("getBool result", "{\"row_data\":{\"A_BOOL\":true},\"row_num\":1}", iter.next());
			assertFalse("no more rows", iter.hasNext());

		} catch(Exception e) {
			e.printStackTrace();
			fail(e.getMessage());
		}
	}

	@Test
	/**
	 * Test the getString statement
	 */
	public void testString() {

		JDBCWorker worker = this.worker;

		try {
			JSONObject request = (JSONObject) JSONValue.parse("{\"statement\":\"getString\",\"params\":[\"cory\"]}");

			Iterator<String> iter = worker.handle(request);
			assertEquals("getString result", "{\"row_data\":{\"A_STRING\":\"cory\"},\"row_num\":1}", iter.next());
			assertEquals("getString result", "{\"row_data\":{\"A_STRING\":\"cory\"},\"row_num\":2}", iter.next());
			assertFalse("no more rows", iter.hasNext());

		} catch(Exception e) {
			e.printStackTrace();
			fail(e.getMessage());
		}
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
