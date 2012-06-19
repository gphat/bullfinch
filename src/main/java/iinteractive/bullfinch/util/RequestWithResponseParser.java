package iinteractive.bullfinch.util;

import java.io.StringReader;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

public class RequestWithResponseParser {

	private String responseQueue;
	private JSONObject json;
	private JSONParser parser = new JSONParser();

	public RequestWithResponseParser(String req) throws Exception {

		json = (JSONObject) parser.parse(new StringReader(req));

		if(json == null) {
			throw new Exception("Couldn't parse JSON request");
		}

		// Try and get the response queue.
		responseQueue = (String) json.get("response_queue");
	}

	/**
	 * Get the name of the response queue from the JSON.
	 *
	 * @return
	 */
	public String getResponseQueue() {

		return responseQueue;
	}

	/**
	 * Get the JSONObject gleaned from the string sent in.
	 *
	 * @return
	 */
	public JSONObject getJSON() {

		return json;
	}
}
