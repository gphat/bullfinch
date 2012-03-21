package iinteractive.bullfinch;

import java.util.HashMap;
import java.util.concurrent.TimeoutException;

import net.rubyeye.xmemcached.MemcachedClient;
import net.rubyeye.xmemcached.exception.MemcachedException;

import org.json.simple.parser.JSONParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A minion is a threadified worker that knows how to talk to the
 * kestrel queue.
 *
 * This class provides a constructor, abstract configuration method and a
 * simple cancel mechanism.
 *
 * @author gphat
 *
 */
public abstract class Minion implements Runnable {

	static Logger logger = LoggerFactory.getLogger(Minion.class);
	protected PerformanceCollector collector;
	protected String queueName;
	protected MemcachedClient kestrel;
	protected int timeout;
	protected JSONParser parser;

	protected volatile boolean cancelled = false;

	public void setTimeout(int timeout) {

		this.timeout = timeout;
	}

	/**
	 * Create a new minion.
	 *
	 * @param collector A performance collector instance
	 * @param client  Pre-connected Kestrel client
	 * @param queueName Name of the queue to talk to
	 * @param timeout The timeout for waiting on the queue
	 */
	public Minion(PerformanceCollector collector, MemcachedClient client, String queueName, Integer timeout) {

		this.collector = collector;
		this.queueName = queueName;
		this.kestrel = client;
		this.timeout = timeout;

		this.parser = new JSONParser();
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

	/*
	 * Convenience method that wraps kestrel.set so that network errors and
	 * whatnot will get handled and responses will get sent.
	 */
	protected void sendMessage(String queue, String message) {

		boolean notSent = true;
		while(notSent) {
			try {
				this.kestrel.set(queue, 0, message);
				notSent = false;
			} catch(MemcachedException e) {
				logger.error("Error sending EOF to complete response", e);
				try { Thread.sleep(2000); } catch (InterruptedException ie) { logger.warn("Interrupted sleep"); }
			} catch(InterruptedException e) {
				logger.error("Interrupted", e);
				try { Thread.sleep(2000); } catch (InterruptedException ie) { logger.warn("Interrupted sleep"); }
			} catch(TimeoutException e) {
				logger.error("Timed out sending EOF to complete response", e);
				try { Thread.sleep(2000); } catch (InterruptedException ie) { logger.warn("Interrupted sleep"); }
			}
		}
	}

	public boolean shouldContinue() {
		System.out.println("asdasd");
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
