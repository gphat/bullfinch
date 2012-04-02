package test;

import static org.junit.Assert.fail;
import iinteractive.bullfinch.util.RequestWithResponseParser;

import org.junit.Test;

public class RequestParser {

	@Test
	public void testInvalidJSON() {

		try {
			new RequestWithResponseParser("IM NOT VALID");

			fail("Parser should throw exception on invalid JSON");
		} catch (Error e) {
			// weee!
		} catch (Exception e) {
			// Do nothing, this is expected
		}
	}

	@Test
	public void testMissingResponseQueue() {

		try {
			new RequestWithResponseParser("{\"foo\":\"bar\"");

			fail("Parser should throw exception due to missing response queue");
		} catch (Exception e) {
			// Do nothing, this is expected
		}
	}
}
