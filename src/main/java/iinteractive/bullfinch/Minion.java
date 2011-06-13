package iinteractive.bullfinch;

import iinteractive.kestrel.Client;

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

	/**
	 * Create a new minion.
	 *
	 * @param client  Pre-connected Kestrel client
	 * @param queueName Name of the queue to talk to
	 * @param worker The worker instance we're wrapping
	 * @param timeout The timeout for waiting on the queue
	 */
	public Minion(Client client, String queueName, Worker worker, int timeout) {

		this.queueName = queueName;
		this.kestrel = client;
		this.worker = worker;
		this.timeout = timeout;

		this.parser = new JSONParser();
	}

	/**
	 * Run the thread.  This method will call a get() on the queue, waiting on
	 * the timeout.  When it gets a message it will pass it off to the worker
	 * to handle.
	 */
	@SuppressWarnings("unchecked")
	public void run() {

		while(true) {

			try {
				String val = this.kestrel.get(this.queueName, this.timeout);

				if(val != null) {
					logger.debug("Got item from queue:\n" + val);
					JSONObject request = (JSONObject) parser.parse(new StringReader(val));


					Iterator<String> items = this.worker.handle(request);
					while(items.hasNext()) {
						this.kestrel.put("response_queue", items.next()); // XXX
					}

					this.kestrel.confirm(this.queueName, 1);
				}
				logger.debug("Timeout expired, cycling");
			} catch(Exception e) {
				logger.error("Error in worker thread, exiting", e);
				return;
			}
		}
	}
}
