package test;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import iinteractive.bullfinch.JSONResultSetWrapper;

import java.sql.ResultSet;
import java.sql.SQLException;

import org.junit.Test;

public class SimpleRSWrapper {

	@Test
	public void testHasNext() {

		ResultSet rs = mock(ResultSet.class);

		try {
			JSONResultSetWrapper wrapper = new JSONResultSetWrapper(null, rs);
			when(rs.next()).thenReturn(true);

			assertTrue("first hasNext is true", wrapper.hasNext());
			// next should only be called once at this point
			verify(rs, times(1)).next();

			assertTrue("second hasNext is true", wrapper.hasNext());
			// next should NOT have been called, as it was never called on the
			// wrapper
			verify(rs, times(1)).next();

			// Do nothing with this, we just need to call it so that the next
			// hasNext works right
			wrapper.next();

			assertTrue("third hasNext is true", wrapper.hasNext());
			// next should be called again
			verify(rs, times(2)).next();

		} catch(SQLException e) {
			fail("SQLException!!");
		}
	}
}
