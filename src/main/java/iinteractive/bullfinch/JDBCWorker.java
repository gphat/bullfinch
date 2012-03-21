package iinteractive.bullfinch;

import iinteractive.bullfinch.minion.JDBCMinion;

/**
 * Compatibility class so that configs don't have to change. Real name is
 * JDBCMinion.
 *
 * @author gphat
 *
 */
public class JDBCWorker extends JDBCMinion {

	public JDBCWorker(PerformanceCollector collector) {
		super(collector);
	}

}
