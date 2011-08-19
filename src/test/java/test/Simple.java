package test;

import static org.junit.Assert.fail;
import iinteractive.bullfinch.Boss;
import iinteractive.bullfinch.ConfigurationException;

import java.net.MalformedURLException;
import java.net.URL;

import org.junit.Test;

public class Simple {

	@Test
	public void testInstantiation() {

		try {
			Boss boss = new Boss(new URL("file:/conf/bullfinch.json"));
		} catch(MalformedURLException e) {
			fail(e.toString());
		} catch(ConfigurationException e) {
			fail(e.toString());
		}
	}
}
