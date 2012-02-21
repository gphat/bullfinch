package iinteractive.bullfinch;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Properties;

import net.rubyeye.xmemcached.MemcachedClient;
import net.rubyeye.xmemcached.MemcachedClientBuilder;
import net.rubyeye.xmemcached.XMemcachedClientBuilder;
import net.rubyeye.xmemcached.command.KestrelCommandFactory;
import net.rubyeye.xmemcached.utils.AddrUtil;

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
	private long configRefreshSeconds = 300;
	private PerformanceCollector collector;

	private boolean collecting = false;
	private Thread emitterThread;
	private PerformanceEmitter emitter;
	private ArrayList<URL> configURLs;
	private ArrayList<Long> configTimestamps;

	public static void main(String[] args) {

		if(args.length < 1) {
			System.err.println("Must provide a config file");
			return;
		}

		try {
			Boss boss = new Boss(args[0]);

			// Start all the threads now that we've verified that all were
			// properly readied.
			boss.start();

			while(true) {
				Thread.sleep(boss.getConfigRefreshSeconds() * 1000);

				if (boss.isConfigStale()) {
					logger.info("Restarting due to config file changes");

					boss.stop();
					boss = new Boss(args[0]);
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
	 * @param config Configuration file URL
	 */
	public Boss(String configFile) throws Exception {
		configURLs = new ArrayList<URL> ();
		configTimestamps = new ArrayList<Long> ();

		JSONObject config = readConfigFile(configFile);

		if(config == null) {
			logger.error("Failed to load config file.");
			return;
		}

		Long configRefreshSecondsLng = (Long) config.get("config_refresh_seconds");
		if(configRefreshSecondsLng == null) {
			logger.info("No config_refresh_seconds specified, defaulting to 300");
			configRefreshSecondsLng = new Long(300);
		}
		this.configRefreshSeconds = configRefreshSecondsLng.intValue();
		logger.debug("Config will refresh in " + this.configRefreshSeconds + " seconds");

		HashMap<String,Object> perfConfig = (HashMap<String,Object>) config.get("performance");
		if(perfConfig != null) {
			this.collecting = (Boolean) perfConfig.get("collect");
		}

		InetAddress addr = InetAddress.getLocalHost();
		this.collector = new PerformanceCollector(addr.getHostName(), this.collecting);

//		if(this.collecting) {
//			// Prepare the emitter thread
//			if(this.collecting) {
//				Client kestrel = new Client((String) perfConfig.get("kestrel_host"), ((Long) perfConfig.get("kestrel_port")).intValue());
//				kestrel.connect();
//				this.emitter = new PerformanceEmitter(this.collector, kestrel, (String) perfConfig.get("queue"));
//				if(perfConfig.containsKey("timeout")) {
//					this.emitter.setTimeout(((Long) perfConfig.get("timeout")).intValue());
//				}
//				if(perfConfig.containsKey("retry_time")) {
//					this.emitter.setRetryTime(((Long) perfConfig.get("retry_time")).intValue());
//				}
//				if(perfConfig.containsKey("retry_attempts")) {
//					this.emitter.setRetryAttempts(((Long) perfConfig.get("retry_attempts")).intValue());
//				}
//				this.emitterThread = new Thread(this.emitter);
//			}
//		}

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
			prepareWorker(workerConfig);
		}

	}

	/**
	 * Prepare a worker.
	 *
	 * @param workConfig The workers config.
	 * @throws Exception
	 */
	private void prepareWorker(HashMap<String,Object> workConfig)	throws Exception {
		String ref = (String) workConfig.get("$ref");
		if (ref != null) {
			workConfig = readConfigFile(ref);
		}

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
			MemcachedClientBuilder builder = new XMemcachedClientBuilder(AddrUtil.getAddresses(workHost + ":" + workPort));
			builder.setCommandFactory(new KestrelCommandFactory());
			MemcachedClient client = builder.build();
			client.setPrimitiveAsString(true);
			client.setEnableHeartBeat(false);
			client.setOpTimeout(timeout);

			// Add the worker to the list so we can run it later.
			Minion minion = new Minion(this.collector, client, queue, worker, timeout);

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

		if(this.collecting) {
			this.emitterThread.start();
		}

		Properties buildProps = new Properties();
		try {
			buildProps.load(Boss.class.getResource("build.properties").openStream());
			String version = buildProps.getProperty("version", "0.0");
			logger.info("Started Bullfinch " + version);
		} catch(Exception e) {
			logger.warn("Couldn't find build.properties. Continuing.");
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
			if(this.collecting) {
				this.emitter.cancel();
			}

			// Now wait around for each thread to finish in turn.
			logger.debug("Joining threads");
			Iterator<Thread> threads = this.minionGroups.get(name).values().iterator();
			while(threads.hasNext()) {
				Thread thread = threads.next();
				try { thread.join(); } catch(Exception e) { logger.error("Interrupted joining thread."); }
			}
			if(this.collecting) {
				try { this.emitterThread.join(); } catch(Exception e) { logger.error("Interrupted joining emitter thread."); }
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
	private JSONObject readConfigFile(String configFile)
		throws ConfigurationException, FileNotFoundException, IOException {

		URL url = new URL(configFile);

		logger.debug("Attempting to read " + url.toString());

		JSONObject config;
        try {
            JSONParser parser = new JSONParser();

			URLConnection conn = url.openConnection();
			logger.debug("Last modified: " + conn.getLastModified());

            config = (JSONObject) parser.parse(
            	new InputStreamReader(url.openStream())
            );

			configURLs.ensureCapacity(configURLs.size() + 1);
			configTimestamps.ensureCapacity(configTimestamps.size() + 1);

			configURLs.add(url);
			configTimestamps.add(new Long (conn.getLastModified()));
        }
        catch ( Exception e ) {
            logger.error("Failed to parse config file", e);
            throw new ConfigurationException("Failed to parse config file=(" + url.toString() + ")");
        }

        return config;
	}

	/**
	 * Check all config file timestamps.
	 *
	 * @return A boolean value indicating whether or not the configuration is stale
	 */
	private boolean isConfigStale() {
		boolean stale = false;

		try {
			for (int i = 0; i < configURLs.size() && !stale; i++)
				if (configURLs.get(i).openConnection().getLastModified() > configTimestamps.get(i).longValue())
					stale = true;
		} catch (Exception e) {
			logger.warn("Error getting config file, ignoring.", e);
			stale = false;
		}

		return stale;
	}
}
