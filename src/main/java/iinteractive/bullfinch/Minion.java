package iinteractive.bullfinch;

import iinteractive.kestrel.Client;

import java.io.IOException;
import java.io.StringReader;
import java.util.Iterator;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A minion is a threadified instance of a Worker that knows how to talk to the
 * kestrel queue.
 *
 * @author gphat
 *
 */
public class Minion implements Runnable {

	static Logger logger = LoggerFactory.getLogger(Minion.class);
	private String queueName;
	private Worker worker;
	private Client kestrel;
	private int timeout;
	private JSONParser parser;
	private int retryTime;
	private int retryAttempts;

	/**
	 * Create a new minion.
	 *
	 * @param client  Pre-connected Kestrel client
	 * @param queueName Name of the queue to talk to
	 * @param worker The worker instance we're wrapping
	 * @param timeout The timeout for waiting on the queue
	 * @param retryTime The amount of time to wait before retrying on an IOException
	 * @param retryAttempts The number of times to retry on an IOException
	 */
	public Minion(Client client, String queueName, Worker worker, int timeout, int retryTime, int retryAttempts) {

		this.queueName = queueName;
		this.kestrel = client;
		this.worker = worker;
		this.timeout = timeout;
		this.retryTime = retryTime;
		this.retryAttempts = retryAttempts;

		this.parser = new JSONParser();
	}

	/**
	 * Run the thread.  This method will call a get() on the queue, waiting on
	 * the timeout.  When it gets a message it will pass it off to the worker
	 * to handle.
	 *
	 * Note: If an IOEXception is caught in communicating with the kestrel queue
	 * then we'll make use of retryTime and retryAttempts.  First, we'll sleep
	 * for retryTime seconds, up to retryAttempts times before we
	 *
	 */
	@SuppressWarnings("unchecked")
	public void run() {

		while(true) {

			int retries = 0;

			try {
				String val = this.kestrel.get(this.queueName, this.timeout);

				if(val != null) {
					logger.debug("Got item from queue:\n" + val);
					JSONObject request = (JSONObject) parser.parse(new StringReader(val));

					// Try and get the response queue.
					String responseQueue = (String) request.get("response_queue");
					if(responseQueue == null) {
						throw new Exception("Requests must contain a response queue!");
					}
					logger.debug("Response will go to " + responseQueue);

					// Get a list of items back from the worker
					Iterator<String> items = this.worker.handle(request);
					// Send those items back into the queue
					while(items.hasNext()) {
						this.kestrel.put(responseQueue, items.next());
					}
					// Top if off with an EOF.
					this.kestrel.put(responseQueue, "{ \"EOF\":\"EOF\" }");
					// Finally, confirm the item we took off the queue.
					this.kestrel.confirm(this.queueName, 1);
				}
				logger.debug("Timeout expired, cycling");
				// Reset the retry counter, since we had a successful cycle.
				retries = 0;

			} catch(IOException e) {
				logger.error("Got an IO Exception");

				// Check if we can retry
				if(retries >= this.retryAttempts) {
					// Abort! We can't get a solid connection.
					logger.error("IOExceptions exceeded retry attempts, exiting", e);
					return;
				}

				// Yield real quick for other threads
				Thread.yield();

				// Sleep for the prescribed retry time
				logger.warn("Caught an exception, sleeping for " + this.retryTime + " seconds.");
				try { Thread.sleep(this.retryTime * 1000); } catch(Exception ex) { logger.error("Retry sleep interrupted"); }

				// Increment retries, since we just slept for a bit.
				retries++;
			} catch(Exception e) {
				logger.error("Error in worker thread, exiting", e);
				return;
			}
		}
	}
}
