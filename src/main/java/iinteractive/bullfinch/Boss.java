package iinteractive.bullfinch;

import iinteractive.kestrel.Client;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.HashMap;
import java.util.Iterator;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
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

	private HashMap<String,HashMap<Minion,Thread>> minionGroups;
	//private Thread kestrelThread;
	private long configRefreshSeconds = 300;

	public static void main(String[] args) {

		if(args.length < 1) {
			System.err.println("Must provide a config file");
			return;
		}

		URL configFile;
		long lastModified;
		try {
			configFile = new URL(args[0]);
			URLConnection urlConn = configFile.openConnection();
			lastModified = urlConn.getLastModified();
			logger.debug("Last modified: " + lastModified);
		} catch (MalformedURLException e) {
			logger.error("Must prode a well-formed url as a config file argument: " + args[0], e);
			return;
		} catch(IOException e) {
			logger.error("Can't open config file", e);
			return;
		}

		try {
			Boss boss = new Boss(configFile);

			// Start all the threads now that we've verified that all were
			// properly readied.
			boss.start();

			while(true) {
				Thread.sleep(boss.getConfigRefreshSeconds() * 1000);

				boolean shouldReload = false;
				try {
					logger.debug("Checking config file age");
					URLConnection urlConn = configFile.openConnection();
					long newModified = urlConn.getLastModified();
		            if(lastModified != newModified) {

		            	logger.debug("Config file has changed, restart.");
		            	lastModified = newModified;
		            	// Configuration has changed, restart!
		            	shouldReload = true;
		            }
				} catch(Exception e) {
					shouldReload = false;
					logger.warn("Error getting config file, ignoring.", e);
				}

				if(shouldReload) {
					logger.info("Restarting due to config file changes");
					shouldReload = false; // Reset!
					boss.stop();
					boss = new Boss(configFile);
					boss.start();
				}
			}
		} catch(Exception e) {
			logger.error("Failed to load worker", e);
		}
	}

	/**
	 * Create a new Boss object.
	 *
	 * @param config Configuration (as a hashmap)
	 */
	public Boss(URL configFile) throws Exception {

		JSONObject config = readConfigFile(configFile);

		if(config == null) {
			logger.error("Failed to load config file.");
			return;
		}

		JSONArray workerList = (JSONArray) config.get("workers");
		if(workerList == null) {
			throw new ConfigurationException("Need a list of workers in the config file.");
		}

		@SuppressWarnings("unchecked")
		Iterator<HashMap<String,Object>> workers = workerList.iterator();

		// Get an empty hashmap to store threads
		this.minionGroups = new HashMap<String,HashMap<Minion,Thread>>();

		// The config has at least one worker in it, so we'll treat iterate
		// over the workers and spin off each one in turn.
		while(workers.hasNext()) {
			HashMap<String,Object> workerConfig = (HashMap<String,Object>) workers.next();
			prepare(workerConfig);
		}
	}

	private void prepare(HashMap<String,Object> workConfig)	throws Exception {

		String name = (String) workConfig.get("name");
		if(name == null) {
			throw new ConfigurationException("Each worker must have a name!");
		}

		String workHost = (String) workConfig.get("kestrel_host");
		if(workHost == null) {
			throw new ConfigurationException("Each worker must have a kestrel_host!");
		}

		Long workPortLng = (Long) workConfig.get("kestrel_port");
		if(workPortLng == null) {
			throw new ConfigurationException("Each worker must have a kestrel_port!");
		}
		int workPort = workPortLng.intValue();

		String workerClass = (String) workConfig.get("worker_class");
		if(workerClass == null) {
			throw new ConfigurationException("Each worker must have a worker_class!");
		}

		String queue = (String) workConfig.get("subscribe_to");
		if(queue == null) {
			throw new ConfigurationException("Each worker must have a subscribe_to!");
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
			throw new ConfigurationException("Each worker must have a timeout!");
		}
		int timeout = timeoutLng.intValue();

		Long retryTimeLng = (Long) workConfig.get("retry_time");
		if(retryTimeLng == null) {
			logger.info("No retry_time specified, defaulting to 20 seconds.");
			retryTimeLng = new Long(20);
		}
		int retryTime = retryTimeLng.intValue();

		Long retryAttemptsLng = (Long) workConfig.get("retry_attempts");
		if(retryAttemptsLng == null) {
			logger.info("No retry_attempts specified, defaulting to 5.");
			retryAttemptsLng = new Long(5);
		}
		int retryAttempts = retryAttemptsLng.intValue();

		// Get the config options to pass to the worker
		@SuppressWarnings("unchecked")
		HashMap<String,Object> workerConfig = (HashMap<String,Object>) workConfig.get("options");

		if(workerConfig == null) {
			throw new ConfigurationException("Each worker must have a worker_config!");
		}

		HashMap<Minion,Thread> workers = new HashMap<Minion,Thread>();
		logger.debug("Created threadgroup for " + name);

		for(int i = 0; i < workerCount; i++) {

			// Create an instance of a worker.
			Worker worker = (Worker) Class.forName(workerClass).newInstance();
			worker.configure(workerConfig);

			// Give it it's very own kestrel connection.
			Client kestrel = new Client(workHost, workPort);
			kestrel.connect();

			// Add the worker to the list so we can run it later.
			Minion minion = new Minion(kestrel, queue, worker, timeout, retryTime, retryAttempts);
			workers.put(minion,	new Thread(minion));
		}

		this.minionGroups.put(name, workers);
		logger.debug("Added worker threads to minion map.");
	}

	/**
	 * Get the number of seconds between config refresh checks.
	 *
	 * @return The number of seconds between config refreshes.
	 */
	private long getConfigRefreshSeconds() {

		return this.configRefreshSeconds;
	}

	/**
	 * Start the worker threads.
	 */
	public void start() {

		Iterator<String> workerNames = this.minionGroups.keySet().iterator();
		// Iterate over each worker "group"...
		while(workerNames.hasNext()) {

			String name = workerNames.next();

			Iterator<Thread> workers = this.minionGroups.get(name).values().iterator();
			while(workers.hasNext()) {
				Thread worker = workers.next();
				worker.start();
			}
		}
	}

	/**
	 * Stop the worker threads
	 */
	public void stop() {

		Iterator<String> workerNames = this.minionGroups.keySet().iterator();
		// Iterate over each worker "group"...
		while(workerNames.hasNext()) {

			String name = workerNames.next();

			// Issue a cancel to each minion so they can stop
			logger.debug("Cancelling minions");
			Iterator<Minion> minions = this.minionGroups.get(name).keySet().iterator();
			while(minions.hasNext()) {
				// And start each thread in the group
				Minion worker = minions.next();
				worker.cancel();
			}

			// Now wait around for each thread to finish in turn.
			logger.debug("Joining threads");
			Iterator<Thread> threads = this.minionGroups.get(name).values().iterator();
			while(threads.hasNext()) {
				Thread thread = threads.next();
				try { thread.join(); } catch(Exception e) { logger.error("Interrupted joining thread."); }
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
	private JSONObject readConfigFile(URL configFile)
		throws ConfigurationException, FileNotFoundException, IOException {

		logger.debug("Attempting to read " + configFile.toString());

		JSONObject config;
        try {
            JSONParser parser = new JSONParser();

            config = (JSONObject) parser.parse(
            	new InputStreamReader(configFile.openStream())
            );

    		Long configRefreshSecondsLng = (Long) config.get("config_refresh_seconds");
    		if(configRefreshSecondsLng == null) {
    			logger.info("No config_refresh_seconds specified, defaulting to 300");
    			configRefreshSecondsLng = new Long(5);
    		}
    		this.configRefreshSeconds = configRefreshSecondsLng.intValue();
    		logger.debug("Config will refresh in " + this.configRefreshSeconds + " seconds");
        }
        catch ( Exception e ) {
            logger.error("Failed to parse config file", e);
            throw new ConfigurationException("Failed to parse config file=(" + configFile.toString() + ")");
        }

        return config;
	}
}
