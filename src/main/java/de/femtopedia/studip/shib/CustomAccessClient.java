package de.femtopedia.studip.shib;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import oauth.signpost.exception.OAuthException;
import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.riversun.okhttp3.OkHttp3CookieHelper;

/**
 * Class representing a HttpClient with custom authorization and Sessions.
 */
public abstract class CustomAccessClient {

	public static final String DEFAULT_ENCODING = "UTF-8";

	/**
	 * The instance of the OkHttp-Client to use.
	 */
	public OkHttpClient client;

	/**
	 * A Helper instance for managing Cookies.
	 */
	protected OkHttp3CookieHelper cookieHelper;

	/**
	 * Initializes a default {@link CustomAccessClient} instance.
	 */
	public CustomAccessClient() {
		this.client = constructHttpClient();
	}

	/**
	 * Helper method to convert a List of Strings into a single String.
	 *
	 * @param list The {@link List} to convert.
	 * @return the converted String
	 */
	public static String listToString(List<String> list) {
		StringBuilder result = new StringBuilder();
		for (String line : list)
			result.append(line);
		return result.toString();
	}

	/**
	 * Helper method for reading lines as {@link String}s from an {@link InputStream} using UTF-8.
	 *
	 * @param input The InputStream to read
	 * @return The read lines
	 * @throws IOException if the reading process fails
	 */
	public static List<String> readLines(InputStream input) throws IOException {
		return readLines(input, DEFAULT_ENCODING);
	}

	/**
	 * Helper method for reading lines as {@link String}s from an {@link InputStream}.
	 *
	 * @param input    The InputStream to read
	 * @param encoding The encoding to use when reading
	 * @return The read lines
	 * @throws IOException if the reading process fails
	 */
	public static List<String> readLines(InputStream input, String encoding) throws IOException {
		InputStreamReader reader = new InputStreamReader(input, encoding);
		BufferedReader readers = new BufferedReader(reader);
		List<String> list = new ArrayList<>();
		String line = readers.readLine();
		while (line != null) {
			list.add(line);
			line = readers.readLine();
		}
		return list;
	}

	/**
	 * Helper method for constructing an OkHttp-Client.
	 *
	 * @return A default OkHttp-Client
	 */
	private OkHttpClient constructHttpClient() {
		this.cookieHelper = new OkHttp3CookieHelper();
		return new OkHttpClient.Builder()
				.cookieJar(this.cookieHelper.cookieJar())
				.followRedirects(false)
				.followSslRedirects(false)
				.build();
	}

	/**
	 * Converts a Pair-List into Form Data.
	 *
	 * @param nvps The Pair List to convert
	 * @return The converted {@link FormBody}.
	 */
	protected FormBody getFormBody(List<Pair<String, String>> nvps) {
		FormBody.Builder formBuilder = new FormBody.Builder();
		for (Pair<String, String> p : nvps) {
			formBuilder.add(p.getKey(), p.getValue());
		}
		return formBuilder.build();
	}

	/**
	 * Returns, whether the current session is valid or you need to re-login.
	 *
	 * @return true if and only if the current session is valid
	 * @throws IOException    if reading errors occur
	 * @throws OAuthException if any OAuth errors occur
	 */
	public boolean isSessionValid() throws IOException, OAuthException {
		CustomAccessHttpResponse response = null;
		try {
			response = get("https://studip.uni-passau.de/studip/api.php");
			return true;
		} catch (IllegalAccessException e) {
			return false;
		} finally {
			if (response != null)
				response.close();
		}
	}

	/**
	 * Performs a HTTP GET Request.
	 *
	 * @param url The URL to get
	 * @return A {@link CustomAccessHttpResponse} representing the result
	 * @throws IOException              if reading errors occur
	 * @throws IllegalArgumentException if the header values are broken
	 * @throws IllegalAccessException   if the session isn't valid
	 * @throws OAuthException           if any OAuth errors occur
	 */
	public CustomAccessHttpResponse get(String url)
			throws IOException, IllegalArgumentException, IllegalAccessException, OAuthException {
		return get(url, new String[0], new String[0]);
	}

	/**
	 * Performs a HTTP GET Request.
	 *
	 * @param url        The URL to get
	 * @param headerKeys An array containing keys for the headers to send with the request
	 * @param headerVals An array containing values for the headers to send with the request (size must be the same as headerKeys.length)
	 * @return A {@link CustomAccessHttpResponse} representing the result
	 * @throws IOException              if reading errors occur
	 * @throws IllegalArgumentException if the header values are broken
	 * @throws IllegalAccessException   if the session isn't valid
	 * @throws OAuthException           if any OAuth errors occur
	 */
	public abstract CustomAccessHttpResponse get(String url, String[] headerKeys, String[] headerVals)
			throws IOException, IllegalArgumentException, IllegalAccessException, OAuthException;

	/**
	 * Performs a HTTP POST Request.
	 *
	 * @param url  The URL to post
	 * @param nvps A list containing Pairs for Form Data
	 * @return A {@link CustomAccessHttpResponse} representing the result
	 * @throws IOException              if reading errors occur
	 * @throws IllegalArgumentException if the header values are broken
	 * @throws IllegalAccessException   if the session isn't valid
	 * @throws OAuthException           if any OAuth errors occur
	 */
	public CustomAccessHttpResponse post(String url, List<Pair<String, String>> nvps)
			throws IOException, IllegalArgumentException, IllegalAccessException, OAuthException {
		return post(url, new String[0], new String[0], nvps);
	}

	/**
	 * Performs a HTTP POST Request.
	 *
	 * @param url        The URL to post
	 * @param headerKeys An array containing keys for the headers to send with the request
	 * @param headerVals An array containing values for the headers to send with the request (size must be the same as headerKeys.length)
	 * @param nvps       A list containing Pairs for Form Data
	 * @return A {@link CustomAccessHttpResponse} representing the result
	 * @throws IOException              if reading errors occur
	 * @throws IllegalArgumentException if the header values are broken
	 * @throws IllegalAccessException   if the session isn't valid
	 * @throws OAuthException           if any OAuth errors occur
	 */
	public abstract CustomAccessHttpResponse post(String url, String[] headerKeys, String[] headerVals, List<Pair<String, String>> nvps)
			throws IOException, IllegalArgumentException, IllegalAccessException, OAuthException;

	/**
	 * Performs a HTTP PUT Request.
	 *
	 * @param url  The URL to put
	 * @param nvps A list containing Pairs for Form Data
	 * @return A {@link CustomAccessHttpResponse} representing the result
	 * @throws IOException            if reading errors occur
	 * @throws IllegalAccessException if the session isn't valid
	 * @throws OAuthException         if any OAuth errors occur
	 */
	public CustomAccessHttpResponse put(String url, List<Pair<String, String>> nvps)
			throws IOException, IllegalAccessException, OAuthException {
		return put(url, new String[0], new String[0], nvps);
	}

	/**
	 * Performs a HTTP PUT Request.
	 *
	 * @param url        The URL to put
	 * @param headerKeys An array containing keys for the headers to send with the request
	 * @param headerVals An array containing values for the headers to send with the request (size must be the same as headerKeys.length)
	 * @param nvps       A list containing Pairs for Form Data
	 * @return A {@link CustomAccessHttpResponse} representing the result
	 * @throws IOException              if reading errors occur
	 * @throws IllegalArgumentException if the header values are broken
	 * @throws IllegalAccessException   if the session isn't valid
	 * @throws OAuthException           if any OAuth errors occur
	 */
	public abstract CustomAccessHttpResponse put(String url, String[] headerKeys, String[] headerVals, List<Pair<String, String>> nvps)
			throws IOException, IllegalArgumentException, IllegalAccessException, OAuthException;

	/**
	 * Performs a HTTP DELETE Request.
	 *
	 * @param url  The URL to delete
	 * @param nvps A list containing Pairs for Form Data
	 * @return A {@link CustomAccessHttpResponse} representing the result
	 * @throws IOException            if reading errors occur
	 * @throws IllegalAccessException if the session isn't valid
	 * @throws OAuthException         if any OAuth errors occur
	 */
	public CustomAccessHttpResponse delete(String url, List<Pair<String, String>> nvps)
			throws IOException, IllegalAccessException, OAuthException {
		return delete(url, new String[0], new String[0], nvps);
	}

	/**
	 * Performs a HTTP DELETE Request.
	 *
	 * @param url        The URL to delete
	 * @param headerKeys An array containing keys for the headers to send with the request
	 * @param headerVals An array containing values for the headers to send with the request (size must be the same as headerKeys.length)
	 * @param nvps       A list containing Pairs for Form Data
	 * @return A {@link CustomAccessHttpResponse} representing the result
	 * @throws IOException              if reading errors occur
	 * @throws IllegalArgumentException if the header values are broken
	 * @throws IllegalAccessException   if the session isn't valid
	 * @throws OAuthException           if any OAuth errors occur
	 */
	public abstract CustomAccessHttpResponse delete(String url, String[] headerKeys, String[] headerVals, List<Pair<String, String>> nvps)
			throws IOException, IllegalArgumentException, IllegalAccessException, OAuthException;

	/**
	 * Returns, whether the given Status Code should be treated
	 * as a failed request.
	 *
	 * @param statusCode The Status Code to check
	 * @return true, if the status Code is an Auth-Error-Code
	 */
	public abstract boolean isErrorCode(int statusCode);

	/**
	 * Executes the given HTTP request.
	 *
	 * @param requestBuilder The request to build and execute
	 * @param headerKeys     An array containing keys for the headers to send with the request
	 * @param headerVals     An array containing values for the headers to send with the request (size must be the same as headerKeys.length)
	 * @return A {@link CustomAccessHttpResponse} representing the result
	 * @throws IOException              if reading errors occur
	 * @throws IllegalArgumentException if the header values are broken
	 * @throws IllegalAccessException   if the session isn't valid
	 */
	protected CustomAccessHttpResponse executeRequest(Request.Builder requestBuilder, String[] headerKeys, String[] headerVals)
			throws IOException, IllegalArgumentException, IllegalAccessException {
		if (headerKeys.length != headerVals.length)
			throw new IllegalArgumentException("headerVals has different length than headerKeys!");
		for (int i = 0; i < headerKeys.length; i++) {
			requestBuilder.addHeader(headerKeys[i], headerVals[i]);
		}
		Request request = requestBuilder.build();
		Response response = client.newCall(request).execute();
		if (this.isErrorCode(response.code()))
			throw new IllegalAccessException("Session is not valid!");
		return new CustomAccessHttpResponse(response);
	}

}
