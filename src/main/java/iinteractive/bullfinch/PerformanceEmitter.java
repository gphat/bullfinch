package iinteractive.bullfinch;

import iinteractive.kestrel.Client;

import java.io.IOException;

import org.json.simple.parser.JSONParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A thread that empties the performance collector to a remote queue.
 *
 * @author gphat
 *
 */
public class PerformanceEmitter implements Runnable {

	static Logger logger = LoggerFactory.getLogger(PerformanceEmitter.class);
	private PerformanceCollector collector;
	private String queueName;
	private Client kestrel;
	private JSONParser parser;

	private int timeout = 60;
	private int retries = 0;
	private int retryTime = 20;
	private int retryAttempts = 5;

	private volatile boolean cancelled = false;

	public void setRetryAttempts(int retryAttempts) {

		this.retryAttempts = retryAttempts;
	}

	public void setRetryTime(int retryTime) {

		this.retryTime = retryTime;
	}

	public void setTimeout(int timeout) {

		this.timeout = timeout;
	}

	/**
	 * Create a new emitter.
	 *
	 * @param client  Pre-connected Kestrel client
	 * @param queueName Name of the queue to talk to
	 * @param worker The worker instance we're wrapping
	 * @param timeout The timeout for waiting on the queue
	 */
	public PerformanceEmitter(PerformanceCollector collector, Client client, String queueName) {

		this.collector = collector;
		this.queueName = queueName;
		this.kestrel = client;

		this.parser = new JSONParser();
	}

	/**
	 * Run the thread.  This method will sleep for TIMEOUT seconds, then
	 * attempt to empty out the collector.
	 *
	 * Note: If an Exception is caught in communicating with the kestrel queue
	 * then we'll make use of retryTime and retryAttempts.  First, we'll sleep
	 * for retryTime seconds, up to retryAttempts times before we
	 *
	 */
	@SuppressWarnings("unchecked")
	public void run() {

		logger.debug("Began emmitter thread with time of " + this.timeout + ", retry time of " + this.retryTime + " and " + this.retryAttempts + " attempts.");

		try {
			// We are using nested tries because we want to attempt to reconnect
			// on some connections.
			try {
				while(!Thread.currentThread().isInterrupted() && !cancelled) {

					// Sleep for a bit.
					Thread.sleep(this.timeout * 1000);

					logger.debug("Emitter expired.");

					String item = collector.poll();
					int count = 0;
					while(item != null) {
						logger.debug("Got tick from collector:\n" + item);

						// Put the item in the queue
						this.kestrel.put(this.queueName, item);
						// Try and get another item
						count++;
						item = collector.poll();
					}
					if(count > 0) {
						logger.debug("Removed " + count + " items from the queue.");
					}
					logger.debug("Timeout expired, cycling");
					// Reset the retry counter, since we had a successful cycle.
					retries = 0;
				}
			} catch(InterruptedException e) {
				// In case we get an interrupt for whatever reason
				logger.info("Caught interrupt, exiting.");
				return;
			} catch(Exception e) {
				logger.error("Got an Exception, attempting to retry", e);
				pauseForRetry(e);
			}
		} catch(Exception e) {
			logger.error("Error in worker thread, exiting", e);
			return;
		}
	}

	private void pauseForRetry(Exception e) throws IOException {

		logger.debug("Currently at " + retries + " retries.");

		// Check if we can retry
		if(this.retries >= this.retryAttempts) {
			// Abort! We can't get a solid connection.
			logger.error("Retry attempts exceeded, exiting", e);
			throw new IOException(e);
		}

		// Yield real quick for other threads
		Thread.yield();

		// Sleep for the prescribed retry time
		logger.warn("Caught an exception, sleeping for " + this.retryTime + " seconds.");
		try { Thread.sleep(this.retryTime * 1000); } catch(Exception ex) { logger.error("Retry sleep interrupted"); }

		this.kestrel.disconnect();
		this.kestrel.connect();

		this.retries++;
	}

	public void cancel() {

		logger.info("Cancel requested, will exit soon.");
		this.cancelled = true;
	}
}
