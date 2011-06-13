package iinteractive.bullfinch;

import iinteractive.kestrel.Client;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

import org.json.simple.JSONArray;
import org.json.simple.parser.JSONParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Main class that drives workers from a Kestrel queue.
 *
 * @author gphat
 *
 */
public class Boss {

	static Logger logger = LoggerFactory.getLogger(Boss.class);

	private HashMap<String,ArrayList<Thread>> minionGroups;

	public static void main(String[] args) {

		if(args.length < 1) {
			System.err.println("Must provide a config file");
			return;
		}

		URL configFile;
		try {
			configFile = new URL(args[0]);
		} catch (MalformedURLException e) {
			System.err.println("Must prode a well-formed url as a config file argument: " + args[0]);
			return;
		}

		JSONArray config;
		try {
			config = readConfigFile(configFile);

		} catch(Exception e) {
			logger.error("Failed to parse config file", e);
			return;
		}

		try {
			Boss boss = new Boss();

			@SuppressWarnings("unchecked")
			Iterator<HashMap<String,Object>> workers = config.iterator();

			// The config has at least one worker in it, so we'll treat iterate
			// over the workers and spin off each one in turn.
			while(workers.hasNext()) {
				HashMap<String,Object> worker = (HashMap<String,Object>) workers.next();
				@SuppressWarnings("unchecked")
				HashMap<String,Object> workerConfig = (HashMap<String,Object>) worker.get("worker_config");
				boss.prepare(workerConfig);
			}

			// Start all the threads now that we've verified that all were
			// properly readied.
			boss.start();

		} catch(Exception e) {
			logger.error("Failed to load worker", e);
		}
	}

	/**
	 * Create a new Boss object.
	 *
	 * @param config Configuration (as a hashmap)
	 */
	public Boss() {

	}

	public void prepare(HashMap<String,Object> workConfig) throws Exception {

		String name = (String) workConfig.get("name");
		if(name == null) {
			throw new Exception("Each worker must have a name!");
		}

		String workHost = (String) workConfig.get("kestrel_host");
		if(workHost == null) {
			throw new Exception("Each worker must have a kestrel_host!");
		}

		Long workPortLng = (Long) workConfig.get("kestrel_port");
		if(workPortLng == null) {
			throw new Exception("Each worker must have a kestrel_port!");
		}
		int workPort = workPortLng.intValue();

		String workerClass = (String) workConfig.get("worker_class");
		if(workerClass == null) {
			throw new Exception("Each worker must have a worker_class!");
		}

		String queue = (String) workConfig.get("subscribe_to");
		if(queue == null) {
			throw new Exception("Each worker must have a subscribe_to!");
		}

		Long workerCountLng = (Long) workConfig.get("worker_count");
		// Default to a single worker
		int workerCount = 1;
		if(workerCountLng != null) {
			// But allow it to be overridden.
			workerCount = workerCountLng.intValue();
		}

		Long timeoutLng = (Long) workConfig.get("timeout");
		if(timeoutLng == null) {
			throw new Exception("Each worker must have a timeout!");
		}
		int timeout = timeoutLng.intValue();

		// Get the config options to pass to the worker
		@SuppressWarnings("unchecked")
		HashMap<String,Object> workerConfig = (HashMap<String,Object>) workConfig.get("worker_config");

		if(workerConfig == null) {
			throw new Exception("Each worker must have a worker_config!");
		}

		// First, create a threadgroup to contain this worker's threads
		ThreadGroup tgroup = new ThreadGroup(name);
		// We're using our own threadgroup because threadgroups are pretty much
		// useless.  We're using them only because it's nice to group them
		// logically.
		ArrayList<Thread> workerThreads = new ArrayList<Thread>();
		logger.debug("Created threadgroup for " + name);

		for(int i = 0; i < workerCount; i++) {

			// Spin up a thread for each worker we were told ot make.
			try {

				// Create an instance of a worker.
				Worker worker = (Worker) Class.forName(workerClass).newInstance();
				worker.configure(workerConfig);

				// Give it it's very own kestrel connection.
				Client kestrel = new Client(workHost, workPort);
				kestrel.connect();

				// Create the thread.
				Runnable workerInstance = new Minion(kestrel, queue, worker, timeout);
				Thread workerThread = new Thread(tgroup, workerInstance);
				workerThreads.add(workerThread);

				logger.debug("Readied thread (" + tgroup.getName() + "): " + i);
			} catch(Exception e) {
				logger.error("Failed to ready worker thread", e);
			}
		}
		this.minionGroups.put(tgroup.getName(), workerThreads);
		logger.debug("Added worker threads to minion map.");
	}

	/**
	 * Start the worker threads.
	 */
	public void start() {

		Iterator<String> workerNames = this.minionGroups.keySet().iterator();
		// Iterate over each worker "group"...
		while(workerNames.hasNext()) {
			String name = workerNames.next();
			List<Thread> threads = this.minionGroups.get(name);
			Iterator<Thread> workers = threads.iterator();
			while(workers.hasNext()) {
				// And start each thread in the group
				Thread worker = workers.next();
				worker.start();
			}
		}
	}

	/**
	 * Read the config file.
	 *
	 * @param path The location to find the file.
	 * @return A JSONObject of the config file.
	 * @throws Exception
	 * @throws FileNotFoundException
	 * @throws IOException
	 */
	private static JSONArray readConfigFile(URL configFile)
		throws Exception, FileNotFoundException, IOException {

		JSONArray config;
        try {
            JSONParser parser = new JSONParser();

            config = (JSONArray) parser.parse(
            	new InputStreamReader(configFile.openStream())
            );
        }
        catch ( Exception e ) {
            logger.error("Failed to parse config file", e);
            throw new Exception("Failed to parse config file=(" + configFile.toString() + ")");
        }

        return config;
	}
}
