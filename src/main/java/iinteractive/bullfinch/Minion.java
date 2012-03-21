package iinteractive.bullfinch;

import java.util.HashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class provides a constructor, abstract configuration method and a
 * simple cancel mechanism.
 *
 * @author gphat
 *
 */
public abstract class Minion implements Runnable {

	static Logger logger = LoggerFactory.getLogger(Minion.class);
	protected PerformanceCollector collector;

	protected volatile boolean cancelled = false;

	/**
	 * Create a new minion.
	 *
	 * @param collector A performance collector instance
	 */
	public Minion(PerformanceCollector collector) {

		this.collector = collector;
	}

	/**
	 * Configure the worker.
	 *
	 * @param config
	 * @throws Exception
	 */
	public abstract void configure(HashMap<String,Object> config) throws Exception;

	/**
	 * Run the thread.
	 */
	public abstract void run();

	public boolean shouldContinue() {
		// If this thread is interrupted or cancelled is false, it should stop!
		return !Thread.currentThread().isInterrupted() && !cancelled;
	}

	/**
	 * Method for communicating that this thread should stop.
	 */
	public void cancel() {

		logger.info("Cancel requested, will exit soon.");
		this.cancelled = true;
	}
}
