package test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import iinteractive.bullfinch.PerformanceCollector;
import iinteractive.bullfinch.minion.JDBCQueryRunner;

import java.io.InputStreamReader;
import java.net.URL;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.HashMap;

import net.rubyeye.xmemcached.MemcachedClient;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;
import org.json.simple.parser.JSONParser;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import test.util.FakeKestrel;

public class QueryRunner {

	private Connection conn;
	private JDBCQueryRunner worker;
	private PerformanceCollector pc = new PerformanceCollector("test", false);
	private String responseQueue = "responseQueue";
	private MemcachedClient kestrelClient = new FakeKestrel();

	@Before
	public void createDatabase() {

		try {
			Connection conn = DriverManager.getConnection("jdbc:hsqldb:mem:tmp/tmp;shutdown=true", "SA", "");

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

			JDBCQueryRunner worker = new JDBCQueryRunner(
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

    		worker.setClient(this.kestrelClient);
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

		try {
			kestrelClient.flushAll();
			HashMap<String,Object> request = new HashMap<String,Object>();
			request.put("statement", "getInt");
			
			worker.handle(pc, responseQueue, request);

			String member = this.kestrelClient.get(responseQueue);

			assertEquals("Got error for missing params", "{\"ERROR\":\"Statement getInt requires params\"}", member);

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

		try {
			kestrelClient.flushAll();
			JSONObject request = (JSONObject) JSONValue.parse("{\"statement\":\"getInt\",\"params\":[12]}");

			worker.handle(pc, responseQueue, request);
			String member = this.kestrelClient.get(responseQueue);
			assertEquals("getInt result", "{\"row_data\":{\"AN_INT\":12},\"row_num\":1}", member);
			assertTrue("no more rows", this.kestrelClient.get(responseQueue) == null);

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

		try {
			kestrelClient.flushAll();
			JSONObject request = (JSONObject) JSONValue.parse("{\"statement\":\"getFloat\",\"params\":[3.14]}");

			worker.handle(pc, responseQueue, request);
			String member = this.kestrelClient.get(responseQueue);
			assertEquals("getFloat result", "{\"row_data\":{\"A_FLOAT\":3.14},\"row_num\":1}", member);
			assertTrue("no more rows", this.kestrelClient.get(responseQueue) == null);

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

		try {
			kestrelClient.flushAll();
			JSONObject request = (JSONObject) JSONValue.parse("{\"statement\":\"getBool\",\"params\":[true]}");

			worker.handle(pc, responseQueue, request);
			String member = this.kestrelClient.get(responseQueue);
			assertEquals("getBool result", "{\"row_data\":{\"A_BOOL\":true},\"row_num\":1}", member);
			assertTrue("no more rows", this.kestrelClient.get(responseQueue) == null);

		} catch(Exception e) {
			e.printStackTrace();
			fail(e.getMessage());
		}
	}

	@Test
	/**
	 * Test the getBool statement
	 */
	public void testSmallBool() {

		try {
			kestrelClient.flushAll();
			JSONObject request = (JSONObject) JSONValue.parse("{\"statement\":\"getBool\",\"params\":[true,1,2,3,4]}");

			worker.handle(pc, responseQueue, request);
			String member = this.kestrelClient.get(responseQueue);
			assertEquals("Got error for missing params", "{\"ERROR\":\"Statement expects 1 params but was given 5\"}", member);
			assertTrue("no more rows", this.kestrelClient.get(responseQueue) == null);

		} catch(Exception e) {
			e.printStackTrace();
			fail(e.getMessage());
		}
	}
	
	@Test
	/**
	 * Test the multiple statements
	 */
	public void testMultiple() {

		try {
			kestrelClient.flushAll();
			JSONObject request = (JSONObject) JSONValue.parse("{\"statements\":[\"getBool\",\"getFloat\",\"goodTable\"],\"params\":[[true],[3.14],[]]}");

			worker.handle(pc, responseQueue, request);
			String member = this.kestrelClient.get(responseQueue);
			assertEquals("getBool result", "{\"row_data\":{\"A_BOOL\":true},\"row_num\":1}", member);
			String member2 = kestrelClient.get(responseQueue);
			assertEquals("getFloat result", "{\"row_data\":{\"A_FLOAT\":3.14},\"row_num\":1}", member2);
			String member3 = kestrelClient.get(responseQueue);
			assertEquals("goodTable result", "{\"row_data\":{\"AN_INT\":12},\"row_num\":1}", member3);
			String member4 = kestrelClient.get(responseQueue);
			assertEquals("goodTable result", "{\"row_data\":{\"AN_INT\":13},\"row_num\":2}", member4);
			assertTrue("no more rows", this.kestrelClient.get(responseQueue) == null);

		} catch(Exception e) {
			e.printStackTrace();
			fail(e.getMessage());
		}
	}

	@Test
	/**
	 * Test the multiple statements in a transaction.
	 */
	public void testMultipleTransaction() {

		try {
			kestrelClient.flushAll();
			JSONObject request = (JSONObject) JSONValue.parse("{\"use_transaction\":true,\"statements\":[\"getBool\",\"getFloat\"],\"params\":[[true],[3.14]]}");

			worker.handle(pc, responseQueue, request);
			String member = this.kestrelClient.get(responseQueue);
			assertEquals("getBool result", "{\"row_data\":{\"A_BOOL\":true},\"row_num\":1}", member);
			String member2 = kestrelClient.get(responseQueue);
			assertEquals("getFloat result", "{\"row_data\":{\"A_FLOAT\":3.14},\"row_num\":1}", member2);
			assertTrue("no more rows", this.kestrelClient.get(responseQueue) == null);

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

		try {
			kestrelClient.flushAll();
			JSONObject request = (JSONObject) JSONValue.parse("{\"statement\":\"getString\",\"params\":[\"cory\"]}");

			worker.handle(pc, responseQueue, request);
			String member = this.kestrelClient.get(responseQueue);
			assertEquals("getString result", "{\"row_data\":{\"A_STRING\":\"cory\"},\"row_num\":1}", member);
			String member2 = this.kestrelClient.get(responseQueue);
			assertEquals("getString result", "{\"row_data\":{\"A_STRING\":\"cory\"},\"row_num\":2}", member2);
			assertTrue("no more rows", this.kestrelClient.get(responseQueue) == null);

		} catch(Exception e) {
			e.printStackTrace();
			fail(e.getMessage());
		}
	}

	@Test
	public void testBadTable() {

		try {
			kestrelClient.flushAll();
			JSONObject request = (JSONObject) JSONValue.parse("{\"statement\":\"badTable\"}");
			worker.handle(pc, responseQueue, request);
			String member = this.kestrelClient.get(responseQueue);
			assertEquals("error", "{\"ERROR\":\"Borrow prepareStatement from pool failed\"}", member);
			assertTrue("no more rows", this.kestrelClient.get(responseQueue) == null);
		} catch (Exception e) {
			fail(e.getMessage());
		}

		try {
			kestrelClient.flushAll();
			JSONObject request = (JSONObject) JSONValue.parse("{\"statement\":\"goodTable\"}");
			worker.handle(pc, responseQueue, request);
			String member = this.kestrelClient.get(responseQueue);
			assertTrue("follow up query", member.startsWith("{\"row_data\":"));
		} catch(Exception e) {
			fail(e.getMessage());
		}
	}

	@Test
	public void testInsert() {
		
		try {
			kestrelClient.flushAll();
			JSONObject request = (JSONObject) JSONValue.parse("{\"statement\":\"addOne\"}");
			worker.handle(pc,  responseQueue, request);
			String member = this.kestrelClient.get(responseQueue);
			assertTrue("EOF only", member == null);
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
