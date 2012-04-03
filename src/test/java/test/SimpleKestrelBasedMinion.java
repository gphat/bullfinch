package test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import iinteractive.bullfinch.Minion;
import iinteractive.bullfinch.PerformanceCollector;
import net.rubyeye.xmemcached.MemcachedClient;

import org.junit.Test;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import test.util.FakeKestrelWorker;

public class SimpleKestrelBasedMinion {

	@Test
	/**
	 * Tests that the Minion properly inserts a single EOF message in response
	 * to worker results that have NO entries in their iterator.
	 *
	 * This test verifies that things like INSERT statements – which have nothing
	 * to return – get an appropriate confirmation back.
	 */
	public void testNoResults() {

		MemcachedClient mockClient = mock(MemcachedClient.class);
		try {
			// Mock the basic JSON stuff in the queue.
			when(mockClient.get(anyString(), anyInt())).thenReturn("{\"response_queue\":\"whatever\"}");

			// Verify that we ONLY get the EOF and throw an Exception to stop
			doAnswer(new Answer() {
				public Object answer(InvocationOnMock invocation) {
					Object[] args = invocation.getArguments();
					assertEquals("EOF", "{ \"EOF\":\"EOF\" }", args[2]);
					throw new RuntimeException();
				}
			}).when(mockClient).set(anyString(), anyInt(), anyString());
		} catch(Exception e) {
			e.printStackTrace();
			fail("Failed to stub get method for kestrel client");
		}

		PerformanceCollector pc = new PerformanceCollector("foo", false);
		Minion worker = new FakeKestrelWorker(pc);

		try {
			worker.run();
		} catch(Exception e) {
			worker.cancel();
		}
	}
}
