package iinteractive.bullfinch;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.json.simple.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * PerformanceMonitor is a trivial wrapper around a ConcurrentLinkedQueue. If
 * instantiated with a false, then calls to this class do nothing.
 *
 * The intent is to maintain a queue of performance metrics for flushing out to
 * somewhere else.  The ability to create one that does nothing allows it to
 * be called in code with no ramifications if desired.
 *
 * This queue holds JSONObjects, one for each "tick" of performance.  The ticks
 * should be in the form of:
 *
 * {
 * 	"name": "name-of-machine",
 *  "occurred": "2011-09-16T23:09:09", // An ISO8601 date
 * 	"activity": "thing i did", // a name for it
 * 	"elapsed": 1234, // milliseconds
 *  "args": [ { "foo": "bar" }, // this can be whatever, maybe args for a db call, etc
 * 	"tracer": "uuid" // an optional tracer uuid
 * }
 *
 * @author gphat
 *
 */
public class PerformanceCollector {

	static Logger logger = LoggerFactory.getLogger(PerformanceCollector.class);
	private String name;
	private boolean enabled = false;
	private ConcurrentLinkedQueue<JSONObject> perfQueue;
	public final static SimpleDateFormat iso8601 = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ");

	/**
	 * Create a new PerformanceCollector.
	 *
	 * @param name		The name of this collector (usually a hostname)
	 * @param enabled 	Flag determining if this collector will actually collect
	 * 					data or just be a null-op.
	 */
	public PerformanceCollector(String name, boolean enabled) {

		this.name = name;
		this.enabled = enabled;
		this.perfQueue = new ConcurrentLinkedQueue<JSONObject>();
	}

	/**
	 * Add a performance "tick" to the collector.  It is not necessary to
	 * supply a date, as the current time is used.
	 *
	 * @param name The name of this tick
	 * @param millis The number of milliseconds elapsed
	 * @param uuid The (optional) tracer uuid
	 */
	@SuppressWarnings("unchecked")
	public void add(String activity, long millis, String uuid) {

		if(!this.enabled) {
			return;
		}

		Calendar now = Calendar.getInstance();

		JSONObject obj = new JSONObject();
		obj.put("name", this.name);
		obj.put("occurred", iso8601.format(now.getTime()));
		obj.put("activity", activity);
		obj.put("elapsed", millis);
		if(uuid != null) {
			obj.put("tracer", uuid);
		}

		logger.debug("Tick added to collector: \"" + activity + "\", " + millis + "ms elapsed (" + uuid + ")");
		this.perfQueue.add(obj);
	}

	/**
	 * Remove a tick from this collector
	 *
	 * @return A performance "tick"
	 */
	public String poll() {

		// Do nothing if not enabled.
		if(!this.enabled) {
			return null;
		}

		JSONObject tick = this.perfQueue.poll();
		if(tick == null) {
			return null;
		}
		logger.debug("Tick retrieved to collector");
		return tick.toString();
	}
}
