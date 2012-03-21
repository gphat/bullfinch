package iinteractive.bullfinch;

import iinteractive.bullfinch.minion.JDBCQueryRunner;

/**
 * Compatibility class so that configs don't have to change. Real name is
 * JDBCMinion.
 *
 * @author gphat
 *
 */
public class JDBCWorker extends JDBCQueryRunner {

	public JDBCWorker(PerformanceCollector collector) {
		super(collector);
	}

}
