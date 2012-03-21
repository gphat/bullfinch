package iinteractive.bullfinch.minion;

import iinteractive.bullfinch.PerformanceCollector;

import java.util.HashMap;

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

	private long interval = 60000;

	/**
	 * Create a new emitter.
	 *
	 * @param collector
	 */
	public PerformanceEmitter(PerformanceCollector collector) {

		super(collector);
	}

	@Override
	public void configure(HashMap<String,Object> config) throws Exception {

		Long intervalLng = (Long) config.get("interval");
		if(intervalLng != null) {
			interval = intervalLng.intValue();
		}
	}

	/**
	 * Run the thread.  This method will sleep for INTERVAL milliseconds, then
	 * attempt to empty out the collector.
	 *
	 * Note: If an Exception is caught in communicating with kestrel then we'll
	 * just keep trying forever.
	 *
	 */
	public void run() {

		logger.debug("Began emitter thread with time of " + interval + ".");

		try {
			while(this.shouldContinue()) {

				// Sleep for a bit.
				Thread.sleep(interval);

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
			}
		} catch(Exception e) {
			logger.error("Got an Exception, sleeping 30 seconds before retrying", e);
			try { Thread.sleep(30000); } catch(InterruptedException ie) { Thread.currentThread().interrupt(); }
		}
	}
}
