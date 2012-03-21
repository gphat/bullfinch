package iinteractive.bullfinch.minion;

import iinteractive.bullfinch.ConfigurationException;
import iinteractive.bullfinch.PerformanceCollector;

import java.util.HashMap;

import net.rubyeye.xmemcached.MemcachedClientBuilder;
import net.rubyeye.xmemcached.XMemcachedClientBuilder;
import net.rubyeye.xmemcached.command.KestrelCommandFactory;
import net.rubyeye.xmemcached.utils.AddrUtil;

import org.json.simple.parser.JSONParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A thread that empties the performance collector to a remote queue.
 *
 * @author gphat
 *
 */
public class PerformanceEmitter extends KestrelBased {

	static Logger logger = LoggerFactory.getLogger(PerformanceEmitter.class);
	private JSONParser parser;

	private int retries = 0;
	private int retryTime = 20;
	private int retryAttempts = 5;

	public void setRetryAttempts(int retryAttempts) {

		this.retryAttempts = retryAttempts;
	}

	public void setRetryTime(int retryTime) {

		this.retryTime = retryTime;
	}

	/**
	 * Create a new emitter.
	 *
	 * @param collector
	 */
	public PerformanceEmitter(PerformanceCollector collector) {

		super(collector);

		this.parser = new JSONParser();
	}

	@Override
	public void configure(HashMap<String,Object> config) throws Exception {

		String workHost = (String) config.get("kestrel_host");
		if(workHost == null) {
			throw new ConfigurationException("Each worker must have a kestrel_host!");
		}

		Long workPortLng = (Long) config.get("kestrel_port");
		if(workPortLng == null) {
			throw new ConfigurationException("Each worker must have a kestrel_port!");
		}
		int workPort = workPortLng.intValue();

		// Give it a kestrel connection.
		MemcachedClientBuilder builder = new XMemcachedClientBuilder(AddrUtil.getAddresses(workHost + ":" + workPort));
		builder.setCommandFactory(new KestrelCommandFactory());
		builder.setFailureMode(true);
		client = builder.build();
		client.setEnableHeartBeat(false);
		client.setPrimitiveAsString(true);
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

		logger.debug("Began emmitter thread with time of XXX, retry time of " + this.retryTime + " and " + this.retryAttempts + " attempts.");

		try {
			// We are using nested tries because we want to attempt to reconnect
			// on some connections.
			try {
				while(!Thread.currentThread().isInterrupted() && !cancelled) {

					// Sleep for a bit.
					Thread.sleep(1000);

					logger.debug("Emitter expired.");

					String item = collector.poll();
					int count = 0;
					while(item != null) {
						logger.debug("Got tick from collector:\n" + item);

						// Put the item in the queue
						this.client.set(this.queueName, 0, item);
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
//				pauseForRetry(e);
			}
		} catch(Exception e) {
			logger.error("Error in worker thread, exiting", e);
			return;
		}
	}

//	private void pauseForRetry(Exception e) throws IOException {
//
//		logger.debug("Currently at " + retries + " retries.");
//
//		// Check if we can retry
//		if(this.retries >= this.retryAttempts) {
//			// Abort! We can't get a solid connection.
//			logger.error("Retry attempts exceeded, exiting", e);
//			throw new IOException(e);
//		}
//
//		// Yield real quick for other threads
//		Thread.yield();
//
//		// Sleep for the prescribed retry time
//		logger.warn("Caught an exception, sleeping for " + this.retryTime + " seconds.");
//		try { Thread.sleep(this.retryTime * 1000); } catch(Exception ex) { logger.error("Retry sleep interrupted"); }
//
//		this.kestrel.disconnect();
//		this.kestrel.connect();
//
//		this.retries++;
//	}
}
