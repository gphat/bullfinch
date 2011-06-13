package iinteractive.kestrel.workers;

import java.util.HashMap;
import java.util.Iterator;

/**
 * A Worker, or a class that does things in response to a message coming in.
 * @author gphat
 *
 */
public interface Worker {

	/**
	 * Configure the worker.
	 *
	 * @param config
	 * @throws Exception
	 */
	public void configure(HashMap<String,Object> config) throws Exception;

	/**
	 * Handle a request.
	 *
	 * @param request The request!
	 * @return An iterator of strings, suitable for returning to the caller.
	 * @throws Exception
	 */
	public Iterator<String> handle(HashMap<String,Object> request) throws Exception;
}
