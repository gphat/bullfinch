package iinteractive.bullfinch;

import java.io.StringReader;
import java.util.HashMap;
import java.util.Iterator;
import java.util.concurrent.TimeoutException;

import net.rubyeye.xmemcached.MemcachedClient;
import net.rubyeye.xmemcached.exception.MemcachedException;

import org.json.simple.JSONObject;

public abstract class QueueMonitoringMinion extends Minion {

	public QueueMonitoringMinion(PerformanceCollector collector, MemcachedClient client, String queueName, Integer timeout) {

		super(collector, client, queueName, timeout);
	}

	/**
	 * Run the thread.  This method will call a get() on the queue, waiting on
	 * the timeout.  When it gets a message it will pass it off to the worker
	 * to handle.
	 */
	@Override
	public void run() {

		logger.debug("Began minion");

		while(this.shouldContinue()) {
			try {
				logger.debug("Opening item from queue");
				// We're adding 1000 (1 second) to the queue timeout to let
				// xmemcached have some breathing room. Kestrel will timeout
				// by itself.
				String val = this.kestrel.get(this.queueName + "/t=" + this.timeout + "/open", this.timeout);

				if (val != null) {
					try {
						process(val);
					} catch (ProcessTimeoutException e) {
						// ignore a timeout exception
					}
					// confirm the item we took off the queue.
					logger.debug("Closing item from queue");
					this.kestrel.get(this.queueName + "/close");
				}
			} catch (TimeoutException e) {
				logger.debug("Timeout expired, cycling");
			} catch (MemcachedException e) {
				logger.error("Caught exception from memcached", e);
				/* Lets sleep for 5 seconds so as not to hammer the xmemcached
				 * library.
				 */
				try { Thread.sleep(5000); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
			} catch(RuntimeException e) {
				/* Rethrow RTE */
				throw(e);
			} catch (Exception e) {
				logger.error("Unknown exception in processing loop", e);
				/* Sleep for longer since we have no idea what's broken. */
				try { Thread.sleep(3000); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
			}
		}
	}

	/**
	 * Handle a request. Classes extending QueueMonitoring minion should
	 * implement this method.
	 *
	 * @param collector A PerformanceCollector instance
	 * @param request The request!
	 * @return An iterator of strings, suitable for returning to the caller.
	 * @throws Exception
	 */
	public abstract Iterator<String> handle(PerformanceCollector collector, HashMap<String,Object> request) throws ProcessTimeoutException;

	private void process(String val) throws ProcessTimeoutException {
		JSONObject request = null;

		logger.debug("Got item from queue:\n" + val);

		try {
			request = (JSONObject) parser.parse(new StringReader(val));
		} catch (Error e) {
			logger.warn("unable to parse input, ignoring");
			return;
		} catch (Exception e) {
			logger.warn("unable to parse input, ignoring");
			return;
		}

		if(request == null) {
			logger.warn("Failed to parse request, ignoring");
			return;
		}

		// Try and get the response queue.
		String responseQueue = (String) request.get("response_queue");
		if(responseQueue == null) {
			logger.debug("request did not contain a response queue");
			return;
		}
		logger.debug("Response will go to " + responseQueue);

		// Get a list of items back from the worker
		@SuppressWarnings("unchecked")
		Iterator<String> items = this.handle(collector, request);

		// Send those items back into the queue

		long start = System.currentTimeMillis();
		while(items.hasNext()) {
			sendMessage(responseQueue, items.next());
		}
		collector.add(
			"ResultSet iteration and queue insertion",
			System.currentTimeMillis() - start,
			(String) request.get("tracer")
		);
		// Top if off with an EOF.
		sendMessage(responseQueue, "{ \"EOF\":\"EOF\" }");
	}
}
