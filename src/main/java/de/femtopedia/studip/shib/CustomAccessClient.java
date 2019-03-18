package de.femtopedia.studip.shib;

import com.google.api.client.http.apache.ApacheHttpTransport;
import com.google.api.client.util.SecurityUtils;
import com.google.api.client.util.SslUtils;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import oauth.signpost.exception.OAuthException;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.conn.ssl.SSLSocketFactory;
import org.apache.http.impl.client.AbstractHttpClient;
import org.apache.http.params.HttpConnectionParams;

/**
 * Class representing a HttpClient with custom authorization and Sessions.
 */
public abstract class CustomAccessClient {

	public static final String DEFAULT_ENCODING = "UTF-8";

	/**
	 * The instance of the Apache HTTP Client to use.
	 */
	public ApacheHttpTransport client;

	/**
	 * Initializes a default {@link CustomAccessClient} instance.
	 *
	 * @param keyStore A custom KeyStore as {@link InputStream} to set or null
	 * @param password The KeyStore's password
	 */
	public CustomAccessClient(InputStream keyStore, String password) {
		this.client = constructHttpClient(keyStore, password);
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
	 * Helper method for constructing an Apache HTTP Client.
	 *
	 * @param keyStore A custom KeyStore as {@link InputStream} to set or null
	 * @param password The KeyStore's password
	 * @return A default Apache HTTP Client
	 */
	private static ApacheHttpTransport constructHttpClient(InputStream keyStore, String password) {
		ApacheHttpTransport.Builder builder = new ApacheHttpTransport.Builder();
		HttpConnectionParams.setConnectionTimeout(builder.getHttpParams(), 30000);
		HttpConnectionParams.setSoTimeout(builder.getHttpParams(), 30000);
		if (keyStore != null)
			trySetKeyStore(builder, keyStore, password);
		ApacheHttpTransport transport = builder.build();
		HttpClient client = transport.getHttpClient();
		if (client instanceof AbstractHttpClient) {
			((AbstractHttpClient) client).setHttpRequestRetryHandler((exception, exCount, ctx) -> {
				if (exCount > 3) {
					System.out.println("Maximum tries reached for Client HTTP Pool (3)");
					return false;
				}
				if (exception instanceof org.apache.http.NoHttpResponseException) {
					System.out.println("No response from server on " + exCount + ". call");
					return true;
				}
				return false;
			});
		}
		return transport;
	}

	/**
	 * Tries to set a custom KeyStore via reflection.
	 *
	 * @param builder  The {@link ApacheHttpTransport.Builder} to use
	 * @param keyStore The KeyStore as {@link InputStream} to set
	 * @param password The KeyStore's password
	 */
	@SuppressWarnings({"unchecked", "deprecation"})
	private static void trySetKeyStore(ApacheHttpTransport.Builder builder, InputStream keyStore, String password) {
		try {
			System.setProperty("ssl.TrustManagerFactory.algorithm", "SunPKIX");
			KeyStore trustStore = KeyStore.getInstance("BKS");
			SecurityUtils.loadKeyStore(trustStore, keyStore, password);
			SSLContext sslContext = SslUtils.getTlsSslContext();
			SslUtils.initSslContext(sslContext, trustStore, SslUtils.getDefaultTrustManagerFactory());
			Class c = Class.forName("com.google.api.client.http.apache.SSLSocketFactoryExtension");
			Constructor co = c.getDeclaredConstructor(SSLContext.class);
			co.setAccessible(true);
			builder.setSocketFactory((SSLSocketFactory) co.newInstance(sslContext));
			HttpsURLConnection.setDefaultHostnameVerifier(SSLSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER);
			SSLSocketFactory.getSocketFactory().setHostnameVerifier(SSLSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER);
			builder.getSSLSocketFactory().setHostnameVerifier(SSLSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER);
		} catch (IOException | GeneralSecurityException | InstantiationException | ClassNotFoundException |
				NoSuchMethodException | InvocationTargetException | IllegalAccessException e) {
			e.printStackTrace();
		}
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
	 * @param url The URL to post
	 * @return A {@link CustomAccessHttpResponse} representing the result
	 * @throws IOException              if reading errors occur
	 * @throws IllegalArgumentException if the header values are broken
	 * @throws IllegalAccessException   if the session isn't valid
	 * @throws OAuthException           if any OAuth errors occur
	 */
	public CustomAccessHttpResponse post(String url)
			throws IOException, IllegalArgumentException, IllegalAccessException, OAuthException {
		return post(url, new String[0], new String[0]);
	}

	/**
	 * Performs a HTTP POST Request.
	 *
	 * @param url        The URL to post
	 * @param headerKeys An array containing keys for the headers to send with the request
	 * @param headerVals An array containing values for the headers to send with the request (size must be the same as headerKeys.length)
	 * @return A {@link CustomAccessHttpResponse} representing the result
	 * @throws IOException              if reading errors occur
	 * @throws IllegalArgumentException if the header values are broken
	 * @throws IllegalAccessException   if the session isn't valid
	 * @throws OAuthException           if any OAuth errors occur
	 */
	public CustomAccessHttpResponse post(String url, String[] headerKeys, String[] headerVals)
			throws IOException, IllegalArgumentException, IllegalAccessException, OAuthException {
		return post(url, headerKeys, headerVals, null);
	}

	/**
	 * Performs a HTTP POST Request.
	 *
	 * @param url  The URL to post
	 * @param nvps A list containing {@link NameValuePair}s
	 * @return A {@link CustomAccessHttpResponse} representing the result
	 * @throws IOException              if reading errors occur
	 * @throws IllegalArgumentException if the header values are broken
	 * @throws IllegalAccessException   if the session isn't valid
	 * @throws OAuthException           if any OAuth errors occur
	 */
	public CustomAccessHttpResponse post(String url, List<NameValuePair> nvps)
			throws IOException, IllegalArgumentException, IllegalAccessException, OAuthException {
		return post(url, new String[0], new String[0], nvps);
	}

	/**
	 * Performs a HTTP POST Request.
	 *
	 * @param url        The URL to post
	 * @param headerKeys An array containing keys for the headers to send with the request
	 * @param headerVals An array containing values for the headers to send with the request (size must be the same as headerKeys.length)
	 * @param nvps       An optional list containing {@link NameValuePair}s
	 * @return A {@link CustomAccessHttpResponse} representing the result
	 * @throws IOException              if reading errors occur
	 * @throws IllegalArgumentException if the header values are broken
	 * @throws IllegalAccessException   if the session isn't valid
	 * @throws OAuthException           if any OAuth errors occur
	 */
	public abstract CustomAccessHttpResponse post(String url, String[] headerKeys, String[] headerVals, @Nullable List<NameValuePair> nvps)
			throws IOException, IllegalArgumentException, IllegalAccessException, OAuthException;

	/**
	 * Performs a HTTP PUT Request.
	 *
	 * @param url The URL to put
	 * @return A {@link CustomAccessHttpResponse} representing the result
	 * @throws IOException            if reading errors occur
	 * @throws IllegalAccessException if the session isn't valid
	 * @throws OAuthException         if any OAuth errors occur
	 */
	public CustomAccessHttpResponse put(String url)
			throws IOException, IllegalAccessException, OAuthException {
		return put(url, new String[0], new String[0]);
	}

	/**
	 * Performs a HTTP PUT Request.
	 *
	 * @param url        The URL to put
	 * @param headerKeys An array containing keys for the headers to send with the request
	 * @param headerVals An array containing values for the headers to send with the request (size must be the same as headerKeys.length)
	 * @return A {@link CustomAccessHttpResponse} representing the result
	 * @throws IOException              if reading errors occur
	 * @throws IllegalArgumentException if the header values are broken
	 * @throws IllegalAccessException   if the session isn't valid
	 * @throws OAuthException           if any OAuth errors occur
	 */
	public abstract CustomAccessHttpResponse put(String url, String[] headerKeys, String[] headerVals)
			throws IOException, IllegalArgumentException, IllegalAccessException, OAuthException;

	/**
	 * Performs a HTTP DELETE Request.
	 *
	 * @param url The URL to delete
	 * @return A {@link CustomAccessHttpResponse} representing the result
	 * @throws IOException            if reading errors occur
	 * @throws IllegalAccessException if the session isn't valid
	 * @throws OAuthException         if any OAuth errors occur
	 */
	public CustomAccessHttpResponse delete(String url)
			throws IOException, IllegalAccessException, OAuthException {
		return delete(url, new String[0], new String[0]);
	}

	/**
	 * Performs a HTTP DELETE Request.
	 *
	 * @param url        The URL to delete
	 * @param headerKeys An array containing keys for the headers to send with the request
	 * @param headerVals An array containing values for the headers to send with the request (size must be the same as headerKeys.length)
	 * @return A {@link CustomAccessHttpResponse} representing the result
	 * @throws IOException              if reading errors occur
	 * @throws IllegalArgumentException if the header values are broken
	 * @throws IllegalAccessException   if the session isn't valid
	 * @throws OAuthException           if any OAuth errors occur
	 */
	public abstract CustomAccessHttpResponse delete(String url, String[] headerKeys, String[] headerVals)
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
	 * @param request    The request to execute
	 * @param headerKeys An array containing keys for the headers to send with the request
	 * @param headerVals An array containing values for the headers to send with the request (size must be the same as headerKeys.length)
	 * @return A {@link CustomAccessHttpResponse} representing the result
	 * @throws IOException              if reading errors occur
	 * @throws IllegalArgumentException if the header values are broken
	 * @throws IllegalAccessException   if the session isn't valid
	 */
	protected CustomAccessHttpResponse executeRequest(HttpRequestBase request, String[] headerKeys, String[] headerVals)
			throws IOException, IllegalArgumentException, IllegalAccessException {
		if (headerKeys.length != headerVals.length)
			throw new IllegalArgumentException("headerVals has different length than headerKeys!");
		for (int i = 0; i < headerKeys.length; i++)
			request.addHeader(headerKeys[i], headerVals[i]);
		HttpResponse response = client.getHttpClient().execute(request);
		if (this.isErrorCode(response.getStatusLine().getStatusCode()))
			throw new IllegalAccessException("Session is not valid!");
		return new CustomAccessHttpResponse(response, request);
	}

}
