package de.femtopedia.studip.shib;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.util.EntityUtils;

/**
 * A class for easing handling of HTTP Responses.
 */
public class ShibHttpResponse {

	/**
	 * The response from the server.
	 */
	private HttpResponse response;

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
		this.response = response;
		this.request = request;
	}

	/**
	 * Helper method for consuming a {@link HttpEntity}.
	 *
	 * @param entity The {@link HttpEntity} to consume
	 * @throws IOException if miscellaneous error occur
	 */
	private static void consumeEntity(HttpEntity entity) throws IOException {
		if (entity != null) {
			if (entity.isStreaming()) {
				InputStream instream = entity.getContent();
				if (instream != null) {
					instream.close();
				}
			}
		}
	}

	/**
	 * Returns the content of the HttpEntity.
	 *
	 * @return A String, containing the content of the website
	 * @throws IOException when reading errors occur
	 */
	public String readLine() throws IOException {
		return readLine(ShibbolethClient.DEFAULT_ENCODING);
	}

	/**
	 * Returns the content of the HttpEntity.
	 *
	 * @param encoding The encoding to use when reading
	 * @return A String, containing the content of the website
	 * @throws IOException when reading errors occur
	 */
	public String readLine(String encoding) throws IOException {
		return ShibbolethClient.listToString(readLines(encoding));
	}

	/**
	 * Returns the content of the HttpEntity.
	 *
	 * @return A List of Strings, containing the content of the website
	 * @throws IOException when reading errors occur
	 */
	public List<String> readLines() throws IOException {
		return readLines(ShibbolethClient.DEFAULT_ENCODING);
	}

	/**
	 * Returns the content of the HttpEntity.
	 *
	 * @param encoding The encoding to use when reading
	 * @return A List of Strings, containing the content of the website
	 * @throws IOException when reading errors occur
	 */
	public List<String> readLines(String encoding) throws IOException {
		InputStream read = null;
		try {
			read = getResponse().getEntity().getContent();
			return ShibbolethClient.readLines(read, encoding);
		} finally {
			if (read != null)
				read.close();
		}
	}

	/**
	 * Returns the response from the server.
	 *
	 * @return the response from the server
	 */
	public HttpResponse getResponse() {
		return response;
	}

	/**
	 * Closes this HTTP Response and its request.
	 */
	public void close() {
		try {
			EntityUtils.class.getMethod("consume", HttpEntity.class);
			EntityUtils.consume(response.getEntity());
		} catch (IOException e) {
			e.printStackTrace();
		} catch (NoSuchMethodException e) {
			try {
				EntityUtils.class.getMethod("consumeQuietly", HttpEntity.class);
				EntityUtils.consumeQuietly(response.getEntity());
			} catch (NoSuchMethodException e1) {
				try {
					response.getEntity().consumeContent();
					consumeEntity(response.getEntity());
				} catch (IOException e2) {
					e.printStackTrace();
				}
			}
		}
		try {
			HttpRequestBase.class.getMethod("abort");
			request.abort();
		} catch (NoSuchMethodException e) {
			System.out.println("Can't abort connection.");
		}
	}

}
