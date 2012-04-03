package test.util;

import static org.mockito.Mockito.mock;
import iinteractive.bullfinch.PerformanceCollector;
import iinteractive.bullfinch.ProcessTimeoutException;
import iinteractive.bullfinch.minion.QueueMonitor;

import java.util.HashMap;

import net.rubyeye.xmemcached.MemcachedClient;

public class FakeKestrelWorker extends QueueMonitor {

	public FakeKestrelWorker(PerformanceCollector collector) {

		super(collector);
	}

	@Override
	public void configure(HashMap<String,Object> config) throws Exception {

		this.client = mock(MemcachedClient.class);
	}

	@Override
	public void run() {
		// TODO Auto-generated method stub

	}

	public void handle(PerformanceCollector collector, String responseQueue, HashMap<String,Object> request) throws ProcessTimeoutException {

		// Do absolutely nothing
	}
}
