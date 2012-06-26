package iinteractive.bullfinch;

import java.util.HashMap;
import java.util.List;

public class Phrasebook {

	private HashMap<String, String> phraseMap;
	private HashMap<String, List<ParamType>> phraseParamMap;
		
	public enum ParamType {

		BOOLEAN, NUMBER, INTEGER, STRING
	}

	public Phrasebook() {

		this.phraseMap = new HashMap<String, String>();
		this.phraseParamMap = new HashMap<String, List<ParamType>>();
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
	}

	public void addPhrase(String name, String phrase, List<ParamType> params) {

		phraseMap.put(name, phrase);

		if (params != null) {
			phraseParamMap.put(name, params);
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
}
