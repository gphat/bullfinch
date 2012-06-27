package test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import iinteractive.bullfinch.PerformanceCollector;
import iinteractive.bullfinch.minion.JDBCTableScanner;

import java.io.InputStreamReader;
import java.net.URL;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.HashMap;

import net.rubyeye.xmemcached.MemcachedClient;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.junit.Before;
import org.junit.Test;

import test.util.FakeKestrel;

public class TableScanner {

	private Connection conn;
	private JDBCTableScanner worker;
	private PerformanceCollector pc = new PerformanceCollector("test", false);
	private String responseQueue = "test-table-scanner";
	private MemcachedClient kestrelClient = new FakeKestrel();

	@Before
	public void createDatabase() {

		try {
			Connection conn = DriverManager.getConnection("jdbc:hsqldb:mem:tmp/tmp;shutdown=true", "SA", "");

			Statement stMakeTable = conn.createStatement();
			stMakeTable.execute("CREATE TABLE PUBLIC.TEST_TABLE (an_int INTEGER, a_float FLOAT, scanned INTEGER)");
			stMakeTable.close();

			Statement stAddOne = conn.createStatement();
			stAddOne.execute("INSERT INTO PUBLIC.TEST_TABLE (an_int, a_float, scanned) VALUES (12, 3.14, 0)");
			stAddOne.close();

			Statement stAddTwo = conn.createStatement();
			stAddTwo.execute("INSERT INTO PUBLIC.TEST_TABLE (an_int, a_float, scanned) VALUES (13, 2.14, 0)");
			stAddTwo.close();

			this.conn = conn;

			worker = new JDBCTableScanner(
				new PerformanceCollector("foo", false)
			);

			JSONParser parser = new JSONParser();

			URL configFile = new URL("file:conf/bullfinch.json");
            JSONObject config = (JSONObject) parser.parse(
            	new InputStreamReader(configFile.openStream())
            );
            JSONArray workerList = (JSONArray) config.get("workers");

    		@SuppressWarnings("unchecked")
    		HashMap<String,Object> workConfig = (HashMap<String,Object>) workerList.get(1);

    		@SuppressWarnings("unchecked")
    		HashMap<String,Object> workerConfig = (HashMap<String,Object>) workConfig.get("options");

    		worker.setClient(this.kestrelClient);
			worker.configure(workerConfig);

		} catch(Exception e) {
			e.printStackTrace();
			fail(e.toString());
		}
	}

	@Test
	/**
	 * Test that a statement with no params fails when none are passed.
	 */
	public void testScanner() {

		try {
			HashMap<String,Object> request = new HashMap<String,Object>();
			request.put("statement", "getInt");

			worker.sendRows();

			String member = this.kestrelClient.get(responseQueue);

			assertEquals("Got error for missing params", "{\"row_data\":{\"SCANNED\":0,\"A_FLOAT\":2.14,\"AN_INT\":13},\"row_num\":1}", member);

			worker.sendRows();

			String member2 = this.kestrelClient.get(responseQueue);

			assertEquals("Got error for missing params", "{\"row_data\":{\"SCANNED\":0,\"A_FLOAT\":3.14,\"AN_INT\":12},\"row_num\":1}", member2);

		} catch(Exception e) {
			e.printStackTrace();
			fail(e.toString());
		}
	}
}

