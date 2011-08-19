package test;

import iinteractive.bullfinch.Boss;
import iinteractive.bullfinch.ConfigurationException;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;

import org.junit.Test;

public class Simple {

	@Test
	public void testInstantiation()
		throws ConfigurationException, FileNotFoundException, IOException,
		MalformedURLException {

		Boss boss = new Boss(new URL("file:conf/bullfinch.json"));
	}
}
