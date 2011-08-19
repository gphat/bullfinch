package test;

import iinteractive.bullfinch.Boss;

import java.net.URL;

import org.junit.Test;

public class Simple {

	@Test
	public void testInstantiation()
		throws Exception {

		Boss boss = new Boss(new URL("file:conf/bullfinch.json"));
	}
}
