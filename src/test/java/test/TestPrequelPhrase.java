package test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;
import iinteractive.bullfinch.PrequelPhrase;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import org.json.simple.JSONArray;

import org.junit.Test;

public class TestPrequelPhrase {


	@Test
	public void generateRequest() {

		ArrayList params = new JSONArray();
		params.add("J");
		params.add("B");
		params.add(13);
		params.add("06");
		params.add("14");
		params.add("2012");
		params.add(null);

                PrequelPhrase oPrequelPhrase = new PrequelPhrase();
		ArrayList preq_params = new ArrayList();
		preq_params.add("0");
		preq_params.add("1");
		preq_params.add("4");
		preq_params.add("6");
		Integer order=1;
                oPrequelPhrase.setPrequelPhrase("prequel_sample",order,"statement",preq_params);

                HashMap<String,Object> request=oPrequelPhrase.generateRequest(params);

                assertEquals("request should have param list and tag for size of two", 2,request.size());
                assertTrue("request statment", request.containsKey("statement"));
                assertEquals("request statment", request.get("statement"),"prequel_sample");
                assertTrue("request statment", request.containsKey("params"));
		JSONArray request_params= (JSONArray)request.get("params");
                assertEquals("request params", request_params.size(),4);
                assertEquals("request params", request_params.get(0),"J");
                assertEquals("request params", request_params.get(1),"B");
                assertEquals("request params", request_params.get(2),"14");
                assertEquals("request params", request_params.get(3),null);



	}

}
