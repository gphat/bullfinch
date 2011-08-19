package iinteractive.bullfinch;

import java.util.HashMap;
import java.util.List;

public class Phrasebook {

	private HashMap<String,String> phraseMap;
	private HashMap<String,List<ParamTypes>> phraseParamMap;

    public enum ParamTypes {
        BOOLEAN, NUMBER, INTEGER, STRING
    }

    public Phrasebook() {

    	this.phraseMap = new HashMap<String,String>();
    	this.phraseParamMap = new HashMap<String,List<ParamTypes>>();
    }

	public void addPhrase(String name, String phrase) {

		addPhrase(name, phrase, null);
	}

	/**
	 * Add a phrase to the Phrasebook.
	 *
	 * @param name 		The name of the phrase
	 * @param phrase	The phrase
	 * @param params	A list of parameters that will be used for this phrase. Can be null
	 */
	public void addPhrase(String name, String phrase, List<ParamTypes> params) {

		phraseMap.put(name, phrase);

		if(params != null) {
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
	public List<ParamTypes> getPhraseParams(String name) {

		return this.phraseParamMap.get(name);
	}
}
