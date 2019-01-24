package de.femtopedia.studip.shib;

import java.io.IOException;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.util.EntityUtils;

/**
 * A class for easing handling of HTTP Responses.
 */
public class ShibHttpResponse {

	/**
	 * The response from the server.
	 */
	private CloseableHttpResponse response;

	/**
	 * The initial HTTP request.
	 */
	private HttpRequestBase request;

	/**
	 * Initializes a new {@link ShibHttpResponse} instance.
	 *
	 * @param response The response from the server
	 * @param request  The initial HTTP request
	 */
	ShibHttpResponse(HttpResponse response, HttpRequestBase request) {
		this.response = (CloseableHttpResponse) response;
		this.request = request;
	}

	/**
	 * Returns the response from the server.
	 *
	 * @return the response from the server
	 */
	public CloseableHttpResponse getResponse() {
		return response;
	}

	/**
	 * Closes this HTTP Response and its request.
	 */
	public void close() {
		try {
			EntityUtils.consume(response.getEntity());
		} catch (IOException e) {
			e.printStackTrace();
		}
		request.releaseConnection();
		request.abort();
		try {
			response.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

}
