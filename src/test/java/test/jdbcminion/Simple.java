package test.jdbcminion;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import iinteractive.bullfinch.PerformanceCollector;
import iinteractive.bullfinch.minion.JDBCMinion;

import java.io.InputStreamReader;
import java.net.URL;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Iterator;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;
import org.json.simple.parser.JSONParser;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class Simple {

	private Connection conn;
	private JDBCMinion worker;
	private PerformanceCollector pc = new PerformanceCollector("test", false);

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

			JDBCMinion worker = new JDBCMinion(
				new PerformanceCollector("foo", false)
			);

			JSONParser parser = new JSONParser();

			URL configFile = new URL("file:conf/bullfinch.json");
            JSONObject config = (JSONObject) parser.parse(
            	new InputStreamReader(configFile.openStream())
            );
            JSONArray workerList = (JSONArray) config.get("workers");

    		@SuppressWarnings("unchecked")
    		HashMap<String,Object> workConfig = (HashMap<String,Object>) workerList.get(0);

    		@SuppressWarnings("unchecked")
    		HashMap<String,Object> workerConfig = (HashMap<String,Object>) workConfig.get("options");

			worker.configure(workerConfig);

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

		JDBCMinion worker = this.worker;

		try {
			HashMap<String,Object> request = new HashMap<String,Object>();
			request.put("statement", "getInt");

			Iterator<String> iter = worker.handle(pc, request);

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

		JDBCMinion worker = this.worker;

		try {
			JSONObject request = (JSONObject) JSONValue.parse("{\"statement\":\"getInt\",\"params\":[12]}");

			Iterator<String> iter = worker.handle(pc, request);
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

		JDBCMinion worker = this.worker;

		try {
			JSONObject request = (JSONObject) JSONValue.parse("{\"statement\":\"getFloat\",\"params\":[3.14]}");

			Iterator<String> iter = worker.handle(pc, request);
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

		JDBCMinion worker = this.worker;

		try {
			JSONObject request = (JSONObject) JSONValue.parse("{\"statement\":\"getBool\",\"params\":[true]}");

			Iterator<String> iter = worker.handle(pc, request);
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

		JDBCMinion worker = this.worker;

		try {
			JSONObject request = (JSONObject) JSONValue.parse("{\"statement\":\"getString\",\"params\":[\"cory\"]}");

			Iterator<String> iter = worker.handle(pc, request);
			assertEquals("getString result", "{\"row_data\":{\"A_STRING\":\"cory\"},\"row_num\":1}", iter.next());
			assertEquals("getString result", "{\"row_data\":{\"A_STRING\":\"cory\"},\"row_num\":2}", iter.next());
			assertFalse("no more rows", iter.hasNext());

		} catch(Exception e) {
			e.printStackTrace();
			fail(e.getMessage());
		}
	}

	@Test
	public void testBadTable() {

		JDBCMinion worker = this.worker;
		try {
			JSONObject request = (JSONObject) JSONValue.parse("{\"statement\":\"badTable\"}");
			Iterator<String> iter = worker.handle(pc, request);
			assertEquals("error", "{\"ERROR\":\"Borrow prepareStatement from pool failed\"}", iter.next());
			assertFalse("no more rows", iter.hasNext());
		} catch (Exception e) {
			fail(e.getMessage());
		}

		try {
			JSONObject request = (JSONObject) JSONValue.parse("{\"statement\":\"goodTable\"}");
			Iterator<String> iter = worker.handle(pc, request);
			assertTrue("follow up query", iter.next().startsWith("{\"row_data\":"));
		} catch(Exception e) {
			fail(e.getMessage());
		}
	}

	@Test
	public void testInsert() {
		JDBCMinion worker = this.worker;
		try {
			JSONObject request = (JSONObject) JSONValue.parse("{\"statement\":\"addOne\"}");
			Iterator<String> iter = worker.handle(pc,  request);
			assertTrue("EOF only", !iter.hasNext());
		} catch (Exception e) {
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
