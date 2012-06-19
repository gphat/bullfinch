package test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;
import iinteractive.bullfinch.Phrasebook.ParamType;
import iinteractive.bullfinch.PrequelPhrase;

import java.util.ArrayList;
import java.util.HashMap;
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

		List<ParamType> params = book.getParams("foo");
		assertTrue("Got null params", params == null);
	}

	@Test
	public void addPhraseWithParams() {

		ArrayList<ParamType> params = new ArrayList<ParamType>();
		params.add(ParamType.STRING);

		this.book.addPhrase("foo", "select foo from dual where bar=?", params);

		assertEquals("getPhrase", "select foo from dual where bar=?", this.book.getPhrase("foo"));

		List<ParamType> ps = book.getParams("foo");
		assertTrue("Got params", ps != null);
		assertTrue("Got 1 param", ps.size() == 1);
		assertTrue("Got correct param", ps.get(0) == ParamType.STRING);
		Boolean wit=book.getWrapInTransaction("foo");
		assertFalse("wrap not set",book.getWrapInTransaction("foo"));

		book.WrapInTransaction("foo");
		assertTrue("wrap set",book.getWrapInTransaction("foo"));
		assertEquals("commitWork autoconfigured to default", book.getPhrase("commitWork").toLowerCase(),"commit work".toLowerCase());
	}
	@Test
	public void addPrequelPhrases() {

		ArrayList<ParamType> params = new ArrayList<ParamType>();
		params.add(ParamType.STRING);

		this.book.addPhrase("foo", "select foo from dual where bar=?", params);
                PrequelPhrase prequel_one = new PrequelPhrase();
                prequel_one.setPrequelPhrase("p_one",null,"worker",null);

                PrequelPhrase prequel_two = new PrequelPhrase();
		ArrayList preq_params = new ArrayList();
		preq_params.add("0");
		Integer order=1;
                prequel_two.setPrequelPhrase("p_two",order,"statement",preq_params);
		this.book.addPrequel("foo",prequel_one);
		this.book.addPrequel("foo",prequel_two);


		HashMap<String,PrequelPhrase> hm = book.getPrequels("foo");
		assertTrue("found p_one prequel",hm.containsKey("p_one"));
		assertTrue("found p_two prequel", hm.containsKey("p_two"));

		PrequelPhrase hm_p_one = (PrequelPhrase)hm.get("p_one");
		PrequelPhrase hm_p_two = (PrequelPhrase)hm.get("p_two");

		assertEquals("getPhrase", "select foo from dual where bar=?", this.book.getPhrase("foo"));
		List<ParamType> ps = book.getParams("foo");
		assertTrue("Got params", ps != null);
		assertTrue("Got 1 param", ps.size() == 1);
		assertTrue("Got correct param", ps.get(0) == ParamType.STRING);

                assertEquals("Name prequel one matches p_one" ,"p_one", prequel_one.getName() );
                assertEquals("Scope prequel one matches worker" ,"worker", prequel_one.getScope() );
                assertTrue("Params prequel one matches null" , prequel_one.getParams().isEmpty());
                assertEquals("Name prequel two matches p_two" ,"p_two", prequel_two.getName() );
                assertEquals("Scope prequel two matches statement" ,"statement", prequel_two.getScope() );
                assertEquals("Params prequel two size" ,1, prequel_two.getParams().size());
                assertEquals("Params prequel two value" ,"0", prequel_two.getParams().get(0));

                assertEquals("Name prequel one matches p_one" ,"p_one", hm_p_one.getName() );
                assertEquals("Scope prequel one matches worker" ,"worker", hm_p_one.getScope() );
                assertTrue("Params prequel one matches null" , hm_p_one.getParams().isEmpty());
                assertEquals("Name prequel two matches p_two" ,"p_two", hm_p_two.getName() );
                assertEquals("Scope prequel two matches statement" ,"statement", hm_p_two.getScope() );
                assertEquals("Params prequel two size" ,1, hm_p_two.getParams().size());
                assertEquals("Params prequel two value" ,"0", hm_p_two.getParams().get(0));
	}

}
