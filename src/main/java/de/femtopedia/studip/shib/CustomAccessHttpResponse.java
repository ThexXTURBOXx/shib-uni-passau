package de.femtopedia.studip.shib;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import okhttp3.Response;

/**
 * A class for easing handling of HTTP Responses.
 */
public class CustomAccessHttpResponse {

	/**
	 * The response from the server.
	 */
	private Response response;

	/**
	 * Initializes a new {@link CustomAccessHttpResponse} instance.
	 *
	 * @param response The response from the server
	 */
	CustomAccessHttpResponse(Response response) {
		this.response = response;
	}

	/**
	 * Returns the content of the HttpEntity.
	 *
	 * @return A String, containing the content of the website
	 * @throws IOException when reading errors occur
	 */
	public String readLine() throws IOException {
		return readLine(CustomAccessClient.DEFAULT_ENCODING);
	}

	/**
	 * Returns the content of the HttpEntity.
	 *
	 * @param encoding The encoding to use when reading
	 * @return A String, containing the content of the website
	 * @throws IOException when reading errors occur
	 */
	public String readLine(String encoding) throws IOException {
		return CustomAccessClient.listToString(readLines(encoding));
	}

	/**
	 * Returns the content of the HttpEntity.
	 *
	 * @return A List of Strings, containing the content of the website
	 * @throws IOException when reading errors occur
	 */
	public List<String> readLines() throws IOException {
		return readLines(CustomAccessClient.DEFAULT_ENCODING);
	}

	/**
	 * Returns the content of the HttpEntity.
	 *
	 * @param encoding The encoding to use when reading
	 * @return A List of Strings, containing the content of the website
	 * @throws IOException when reading errors occur
	 */
	public List<String> readLines(String encoding) throws IOException {
		try (InputStream read = getResponse().body().byteStream()) {
			return CustomAccessClient.readLines(read, encoding);
		}
	}

	/**
	 * Returns the response from the server.
	 *
	 * @return the response from the server
	 */
	public Response getResponse() {
		return response;
	}

	/**
	 * Closes this HTTP Response and its request.
	 */
	public void close() {
		response.close();
	}

}
