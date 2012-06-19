package iinteractive.bullfinch;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import org.json.simple.JSONArray;

/**
 * Storage for sql configuration required to run before another statement
 * Phrasebook
 *
 * PrequelPhrase o_pp = PrequelPrase new();
 * o_pp.setPrequelPhrase("statementFromConfigFile",new Integer(1),"statement");
 * HashMap request=o_pp.generateRequest();
 *
 * @author trey
 */
public class PrequelPhrase {

	private String Name = new String();
	private Integer Order = new Integer(1);
	private String Scope = new String();
	private ArrayList Params = new ArrayList();

	/**
	 *
	 */
	public void PrequelPhrase() {
	}

	/**
	 * sets values on the PrequelPhrase
	 *
	 * @param name name found in the Phrasebook
	 * @param order order of execution in relation to other PrequelPhrases
	 * in same scope on the parent statement
	 * @param scope either worker or statement "worker" means this sql must
	 * be executed 1x per worker "statement" means this sql must be executed
	 * 1x per statement
	 * @param params integer values referring to the parameter position of
	 * the parameters passed into the parent phrase
	 */
	public void setPrequelPhrase(String name, Integer order, String scope, ArrayList params) {

		this.Name = name;

		if (order != null) {
			this.Order = order;
		} else {
			this.Order = 1;
		}

		if ("worker".equals(scope) || "statement".equals(scope)) {
			this.Scope = scope;
		}

		if (params != null) {
			this.Params = params;
		}
	}

	/**
	 * returns Name attribute
	 *
	 * @return String
	 */
	public String getName() {
		return this.Name;
	}

	/**
	 * returns the scope attribute
	 *
	 * @return String
	 */
	public String getScope() {
		return this.Scope;
	}

	/**
	 * returns the Params attribute
	 *
	 * @return ArrayList<Integer>
	 */
	public ArrayList getParams() {
		return this.Params;
	}

	/**
	 * returns the order attribute
	 *
	 * @return Integer
	 */
	public Integer getOrder() {
		return this.Order;
	}

	/**
	 * returns a Json style "Request"
	 *
	 * @param params
	 * @return HashMap "key" : value type statement:String() params:
	 * JSONArray()
	 */
	public HashMap<String, Object> generateRequest(ArrayList params) {
		HashMap<String, Object> return_map = new HashMap();
		return_map.put("statement", this.getName());
		Iterator i = this.getParams().iterator();
		JSONArray return_array = new JSONArray();
		while (i.hasNext()) {
			String position = (String) i.next();
			String value = (String) params.get(Integer.parseInt(position));
			return_array.add(value);
		}
		if (return_array.size() > 0) {
			return_map.put("params", return_array);
		}

		return return_map;
	}
}
