package iinteractive.bullfinch.minion;

import iinteractive.bullfinch.ConfigurationException;
import iinteractive.bullfinch.Minion;
import iinteractive.bullfinch.PerformanceCollector;

import java.util.HashMap;
import java.util.concurrent.TimeoutException;

import net.rubyeye.xmemcached.MemcachedClient;
import net.rubyeye.xmemcached.MemcachedClientBuilder;
import net.rubyeye.xmemcached.XMemcachedClientBuilder;
import net.rubyeye.xmemcached.command.KestrelCommandFactory;
import net.rubyeye.xmemcached.exception.MemcachedException;
import net.rubyeye.xmemcached.utils.AddrUtil;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * KestrelBased is a convenience class for writing minions that use Kestrel.
 * It handles reading configuration and provides a private client.
 *
 * @author gphat
 *
 */
public abstract class KestrelBased extends Minion {

	static Logger logger = LoggerFactory.getLogger(KestrelBased.class);
	protected String queueName;
	protected MemcachedClient client;

	public KestrelBased(PerformanceCollector collector) {

		super(collector);
	}

	@Override
	public void configure(HashMap<String,Object> config) throws Exception {

		String workHost = (String) config.get("kestrel_host");
		if(workHost == null) {
			throw new ConfigurationException("Each kestrel-based worker must have a kestrel_host!");
		}

		Long workPortLng = (Long) config.get("kestrel_port");
		if(workPortLng == null) {
			throw new ConfigurationException("Each kestrel-based worker must have a kestrel_port!");
		}
		int workPort = workPortLng.intValue();

		// Give it a kestrel connection.
		MemcachedClientBuilder builder = new XMemcachedClientBuilder(AddrUtil.getAddresses(workHost + ":" + workPort));
		builder.setCommandFactory(new KestrelCommandFactory());
		builder.setFailureMode(true);
		builder.getTranscoder().setCompressionThreshold(1073741824);
		client = builder.build();
		client.setEnableHeartBeat(false);
		client.setPrimitiveAsString(true);
	}

	/*
	 * Convenience method that wraps kestrel.set so that network errors and
	 * whatnot will get handled and responses will get sent.
	 */
	protected void sendMessage(String queue, String message) {

		if(message == null) {
			logger.warn("Ignoring empty response we were suppsoed to send to kestrel");
			return;
		}

		int retries = 0;
		boolean notSent = true;
		while(notSent) {
			try {
				this.client.set(queue, 0, message);
				notSent = false;
			} catch(MemcachedException e) {
				logger.error("Error sending response to kestrel", e);
				try { Thread.sleep(2000); } catch (InterruptedException ie) { logger.warn("Interrupted sleep"); }
			} catch(InterruptedException e) {
				logger.error("Interrupted", e);
				try { Thread.sleep(2000); } catch (InterruptedException ie) { logger.warn("Interrupted sleep"); }
			} catch(TimeoutException e) {
				logger.error("Timed out sending EOF to complete response", e);
				try { Thread.sleep(2000); } catch (InterruptedException ie) { logger.warn("Interrupted sleep"); }
			}
			if(retries >= 20) {
				// We can't try forever.  We have to give up eventually.
				logger.error("Abandoning response to kestrel, couldn't send after 20 tries.");
				logger.error("Response meant for '" + queue + "': " + message);
				notSent = false;
			}
			retries++;
		}
	}
}
