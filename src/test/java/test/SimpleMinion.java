package test;

import static org.junit.Assert.fail;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyMapOf;
import static org.mockito.Matchers.anyObject;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import iinteractive.bullfinch.Minion;
import iinteractive.bullfinch.PerformanceCollector;
import iinteractive.bullfinch.Worker;
import iinteractive.kestrel.Client;

import java.util.ArrayList;
import java.util.HashMap;

import org.junit.Test;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

public class SimpleMinion {

	@Test
	/**
	 * Tests that the worker "skips" invalid JSON messages by confirming them
	 * without put()ing anything back into kestrel.
	 */
	public void testInvalidJSON() {

		String queueName = "foobar";

		Client mockClient = mock(Client.class);
		try {
			// Mock up the queue to return invalid JSON
			when(mockClient.get(anyString(), anyInt())).thenReturn("IM NOT VALID");

			// Mock up confirm to throw an RTE so that execution stops.
			doThrow(new RuntimeException()).when(mockClient).confirm(anyString(), anyInt());

		} catch(Exception e) {
			e.printStackTrace();
			fail("Failed to stub get method for kestrel client");
		}

		PerformanceCollector pc = new PerformanceCollector("foo", false);

		// Mock up an empty worker
		Worker mockWorker = mock(Worker.class);

		Minion m = new Minion(pc, mockClient, queueName, mockWorker, 1000);
		try {
			m.run();
		} catch(Exception e) {
			m.cancel();
		}

		// Verify that put was NEVER called.
		try {
			verify(mockClient, never()).put(anyString(), anyString());
		} catch(Exception e) {
			fail("Got weird exception from mock put. Wtf?");
		}
	}

	//@Test
	public void testNoResults() {

		String queueName = "foobar";

		Client mockClient = mock(Client.class);
		try {
			when(mockClient.get(anyString(), anyInt())).thenReturn("first");

			doAnswer(new Answer() {
				public Object answer(InvocationOnMock invocation) {
					System.out.println("ASDASDDS");
					return null;
				}
			}).when(mockClient).put(anyString(), anyString());
		} catch(Exception e) {
			e.printStackTrace();
			fail("Failed to stub get method for kestrel client");
		}

		PerformanceCollector pc = new PerformanceCollector("foo", false);

		Worker mockWorker = mock(Worker.class);
		try {
			when(mockWorker.handle((PerformanceCollector) anyObject(), (HashMap) anyMapOf(String.class, Object.class))).thenReturn(
				new ArrayList<String>().iterator()
			);
		} catch(Exception e) {
			e.printStackTrace();
			fail("Failed to stub handle method for worker");
		}

		Minion m = new Minion(pc, mockClient, queueName, mockWorker, 1000);
		m.run();
	}
}
