package test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import iinteractive.bullfinch.Phrasebook.ParamTypes;

import java.util.ArrayList;
import java.util.List;

import org.junit.Before;
import org.junit.Test;

public class Phrasebook {

	private iinteractive.bullfinch.Phrasebook book;

	@Before
	public void setupBook() {

		this.book = new iinteractive.bullfinch.Phrasebook();
	}

	@Test
	public void addPhraseNoParams() {

		this.book.addPhrase("foo", "select foo from dual");

		assertEquals("getPhrase", "select foo from dual", this.book.getPhrase("foo"));

		List<ParamTypes> params = book.getPhraseParams("foo");
		assertTrue("Got null params", params == null);
	}

	@Test
	public void addPhraseWithParams() {

		ArrayList<ParamTypes> params = new ArrayList<ParamTypes>();
		params.add(ParamTypes.STRING);

		this.book.addPhrase("foo", "select foo from dual where bar=?", params);

		assertEquals("getPhrase", "select foo from dual where bar=?", this.book.getPhrase("foo"));

		List<ParamTypes> ps = book.getPhraseParams("foo");
		assertTrue("Got params", ps != null);
		assertTrue("Got 1 param", ps.size() == 1);
		assertTrue("Got correct param", ps.get(0) == ParamTypes.STRING);
	}
}
