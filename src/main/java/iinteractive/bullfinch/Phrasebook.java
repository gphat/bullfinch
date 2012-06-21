package iinteractive.bullfinch;

import java.util.HashMap;
import java.util.List;

public class Phrasebook {

	private HashMap<String, String> phraseMap;
	private HashMap<String, List<ParamType>> phraseParamMap;
	private HashMap<String, HashMap<String, PrequelPhrase>> phrasePrequels;
	private HashMap<String, Boolean> phraseWrapInTransaction;

	public enum ParamType {

		BOOLEAN, NUMBER, INTEGER, STRING
	}

	public Phrasebook() {

		this.phraseMap = new HashMap<String, String>();
		this.phraseParamMap = new HashMap<String, List<ParamType>>();
		this.phrasePrequels = new HashMap<String, HashMap<String, PrequelPhrase>>();
		this.phraseWrapInTransaction = new HashMap<String, Boolean>();
	}

	/**
	 * Add a phrase to the Phrasebook.
	 *
	 * @param name The name of the phrase
	 * @param phrase	The phrase
	 * @param params	A list of parameters that will be used for this phrase.
	 * Can be null
	 */
	public void addPhrase(String name, String phrase) {
		addPhrase(name, phrase, null);
		DontWrapInTransaction(name);
	}

	public void addPhrase(String name, String phrase, List<ParamType> params) {

		phraseMap.put(name, phrase);

		if (params != null) {
			phraseParamMap.put(name, params);
		}
		DontWrapInTransaction(name);
	}

	/**
	 * Flag WrapInTransaction to false for the Phrase name
	 * @param name
	 */
	public void DontWrapInTransaction(String name) {
		this.phraseWrapInTransaction.put(name, false);
	}

	/**
	 * 
	 * Flag WrapInTransaction to true for the Phrase name 
	 * @param name
	 */
	public void WrapInTransaction(String name) {
		this.phraseWrapInTransaction.put(name, true);
		if ( ! this.phraseMap.containsKey("commitWork")) {
		      this.phraseMap.put("commitWork","commit work");
		}
	}

	/**
	 * 
	 * @param name
	 * @param prequels
	 */
	public void addPrequel(String name, PrequelPhrase prequels) {
		if (prequels != null) {
			if (this.phrasePrequels.get(name) == null) {
				HashMap hm = new HashMap();
				hm.put(prequels.getName(), prequels);
				this.phrasePrequels.put(name, hm);
			} else {
				this.phrasePrequels.get(name).put(prequels.getName(), prequels);
			}
		}
	}

	/**
	 * Get the phrase for the specified name.
	 *
	 * @param name The name of the phrase
	 * @return The phrase or null if there is no phrase for the name.
	 */
	public String getPhrase(String name) {

		return this.phraseMap.get(name);
	}

	/**
	 * Get the parameters for the specified name.
	 *
	 * @param name	The name of the phrase
	 * @return The list of params, which may be null
	 */
	public List<ParamType> getParams(String name) {
		return this.phraseParamMap.get(name);
	}

	/**
	 * Get the prequels for the specified name.
	 *
	 * @param name	The name of the phrase
	 * @return The Hashmap of prequel, which may be null
	 */
	public HashMap getPrequels(String name) {
		if (this.phrasePrequels.containsKey(name)) {
			return this.phrasePrequels.get(name);
		}
		HashMap<String, PrequelPhrase> hm = new HashMap();
		return hm;
	}

	/**
	 * Get the wrapInTrasaction status for the specified name.
	 *
	 * @param name	The name of the phrase
	 * @return The Boolean of WrapInTrasaction, which may be true or false
	 */
	public Boolean getWrapInTransaction(String name) {
		return this.phraseWrapInTransaction.get(name);
	}
}
