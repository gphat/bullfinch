package test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import net.rubyeye.xmemcached.MemcachedClient;

import org.junit.Test;

import test.util.FakeKestrel;

public class FakeKestrelVerifier {

	@Test
	public void makeSureItWorks() {

		MemcachedClient client = new FakeKestrel();
		try {
			assertTrue("non-existent queue is null", client.get("asasdaskljadljksd") == null);

			client.add("foobar", 100, "foobar1");
			client.add("foobar", 100, "foobar2");

			assertEquals("getting first item from client", client.get("foobar"), "foobar1");
			assertEquals("getting second item from client", client.get("foobar"), "foobar2");

			assertTrue("empty get is null", client.get("foobar") == null);
		} catch(Exception e) {
			fail("add should not throw an exception");
		}
	}
}
