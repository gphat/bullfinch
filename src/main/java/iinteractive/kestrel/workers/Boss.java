package iinteractive.kestrel.workers;

import iinteractive.kestrel.Client;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Main class that drives workers for a Kestrel queue.
 *
 * @author gphat
 *
 */
public class Boss {

	static Logger logger = LoggerFactory.getLogger(Boss.class);

	private String workHost;
	private int workPort;
	private String workerClass;
	private int workerCount;
	private String queue;
	private int timeout;

	public static void main(String[] args) {

		if(args.length < 1) {
			System.err.println("Must provide a config file");
			return;
		}

		String configFile = args[0];
		JSONObject config;
		try {
			config = readConfigFile(configFile);
		} catch(Exception e) {
			logger.error("Failed to parse config file", e);
			return;
		}

		try {
			@SuppressWarnings("unchecked")
			Boss boss = new Boss(config);

			@SuppressWarnings("unchecked")
			HashMap<String,Object> workerConfig = (HashMap<String,Object>) config.get("worker_config");
			boss.start(workerConfig);
		} catch(Exception e) {
			logger.error("Failed to load worker", e);
		}
	}

	/**
	 * Create a new Boss object.
	 *
	 * @param config Configuration (as a hashmap)
	 * @throws ClassNotFoundException
	 * @throws IllegalAccessException
	 */
	public Boss(HashMap<String,Object> config)
		throws ClassNotFoundException, IllegalAccessException {

		this.workHost = (String) config.get("work_host");
		this.workPort = ((Long) config.get("work_port")).intValue();
		this.workerClass = (String) config.get("worker_class");
		this.queue = (String) config.get("subscribe_to");
		this.workerCount = ((Long) config.get("worker_count")).intValue();
		this.timeout = ((Long) config.get("timeout")).intValue();
	}

	/**
	 * Start the worker threads.
	 *
	 * @param workerConfig The worker configuration from the config file.
	 */
	public void start(HashMap<String,Object> workerConfig) {

		for(int i = 0; i < this.workerCount; i++) {

			// Spin up a thread for each worker we were told ot make.
			try {

				// Create an instance of a worker.
				Worker worker = (Worker) Class.forName(workerClass).newInstance();
				worker.configure(workerConfig);

				// Give it it's very own kestrel connection.
				Client kestrel = new Client(this.workHost, this.workPort);
				kestrel.connect();

				// Spin up the thread.
				Thread workThread = new Minion(kestrel, this.queue, worker, this.timeout);
				workThread.start();
				logger.debug("Started thread " + i);
			} catch(Exception e) {
				logger.error("Failed to spawn worker thread", e);
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
	private static JSONObject readConfigFile(String path)
		throws Exception, FileNotFoundException, IOException {

		JSONObject config;
        try {
            JSONParser parser = new JSONParser();
            config = (JSONObject) parser.parse( new FileReader(path) );
        }
        catch ( Exception e ) {
            logger.error("Failed to parse config file", e);
            throw new Exception("Failed to parse config file=(" + path + ")");
        }

        return config;
	}
}
