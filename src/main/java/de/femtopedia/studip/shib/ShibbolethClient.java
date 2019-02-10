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
import java.lang.reflect.Method;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.CookieStore;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.conn.ssl.SSLSocketFactory;
import org.apache.http.impl.client.AbstractHttpClient;
import org.apache.http.impl.cookie.BasicClientCookie;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.params.HttpConnectionParams;

/**
 * A simple wrapper for the communication with Uni Passau's Shibboleth SSO.
 */
public class ShibbolethClient {

	public static final String DEFAULT_ENCODING = "UTF-8";

	/**
	 * The instance of the Apache HTTP Client to use.
	 */
	public ApacheHttpTransport client;

	/**
	 * Initializes a default {@link ShibbolethClient} instance.
	 */
	public ShibbolethClient() {
		this(null, "");
	}

	/**
	 * Initializes a default {@link ShibbolethClient} instance.
	 *
	 * @param keyStore A custom KeyStore as {@link InputStream} to set or null
	 * @param password The KeyStore's password
	 */
	public ShibbolethClient(InputStream keyStore, String password) {
		this.client = constructHttpClient(keyStore, password);
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
	@SuppressWarnings({"unchecked", "JavaReflectionInvocation"})
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
			Method m = builder.getClass().getMethod("setSocketFactory", SSLSocketFactory.class);
			m.setAccessible(true);
			m.invoke(builder, co.newInstance(sslContext));
			HttpsURLConnection.setDefaultHostnameVerifier(SSLSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER);
			SSLSocketFactory.getSocketFactory().setHostnameVerifier(SSLSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER);
			builder.getSSLSocketFactory().setHostnameVerifier(SSLSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER);
		} catch (IOException | GeneralSecurityException | InstantiationException | ClassNotFoundException |
				NoSuchMethodException | InvocationTargetException | IllegalAccessException e) {
			e.printStackTrace();
		}
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
	 * Tries to authenticate with the Shibboleth SSO, if there isn't already an account signed in, and save the required cookies.
	 *
	 * @param username The username of the account
	 * @param password The password of the account
	 * @throws IOException              if reading errors occur
	 * @throws IllegalArgumentException if the header values are broken
	 * @throws IllegalAccessException   if the user credentials are not correct
	 * @throws IllegalStateException    if cookies don't match the server
	 * @deprecated This method is still functional, but it's better to use OAuth Authentication (Not implemented yet).
	 */
	@Deprecated
	public void authenticate(String username, String password) throws IOException, IllegalArgumentException, IllegalAccessException, IllegalStateException {
		if (!this.isSessionValid())
			this.authenticateIntern(username, password);
	}

	/**
	 * Returns, whether the current session is valid or you need to re-login.
	 *
	 * @return true if and only if the current session cookies are valid
	 * @throws IOException if reading errors occur
	 */
	public boolean isSessionValid() throws IOException {
		ShibHttpResponse response = null;
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
	 * Tries to authenticate with the Shibboleth SSO and save the required cookies.
	 * UNSAFE! DON'T USE!
	 *
	 * @param username The username of the account
	 * @param password The password of the account
	 * @throws IOException              if reading errors occur
	 * @throws IllegalArgumentException if the header values are broken
	 * @throws IllegalAccessException   if the user credentials are not correct
	 * @throws IllegalStateException    if cookies don't match the server
	 */
	private void authenticateIntern(String username, String password) throws IOException, IllegalArgumentException, IllegalAccessException, IllegalStateException {
		ShibHttpResponse response = null;
		try {
			response = get("https://studip.uni-passau.de/studip/index.php");
			getCookieStore().addCookie(createCookie("cache_session", Long.toString(System.currentTimeMillis()), "studip.uni-passau.de", "/"));
			getCookieStore().addCookie(createCookie("navigation-length", "3", "studip.uni-passau.de", "/studip/"));
			response.close();

			response = get("https://studip.uni-passau.de/studip/index.php?again=yes&sso=shib");
			response.close();

			response = get("https://studip.uni-passau.de/secure/studip-sp.php?target=https%3A%2F%2Fstudip.uni-passau.de%2Fstudip%2Findex.php%3Fagain%3Dyes%26sso%3Dshib");
			String ssoSAML = response.getResponse().getFirstHeader("location").getValue();
			response.close();

			response = get(ssoSAML);
			String loc = response.getResponse().getFirstHeader("location").getValue();
			response.close();

			response = get("https://sso.uni-passau.de" + loc);
			response.close();

			List<NameValuePair> formList = new ArrayList<>();
			formList.add(new BasicNameValuePair("_eventId_proceed", ""));
			formList.add(new BasicNameValuePair("donotcache", ""));
			formList.add(new BasicNameValuePair("donotcache_dummy", "1"));
			formList.add(new BasicNameValuePair("j_password", password));
			formList.add(new BasicNameValuePair("j_username", username));
			response = post("https://sso.uni-passau.de" + loc,
					new String[]{"Referer", "Upgrade-Insecure-Requests"}, new String[]{"https://sso.uni-passau.de" + loc, "1"},
					formList);
			HttpEntity entity6 = response.getResponse().getEntity();
			List<String> lines = readLines(entity6.getContent());
			if (lines.size() == 0 || (lines.get(0).equals("") && lines.size() == 1))
				throw new IllegalAccessException("Wrong credentials!");
			String relaystate = null, samlresponse = null;
			for (String line : lines) {
				if (line.contains("name=\"RelayState\""))
					relaystate = line.split("name=\"RelayState\" value=\"")[1].split("\"/>")[0];
				if (line.contains("name=\"SAMLResponse\""))
					samlresponse = line.split("name=\"SAMLResponse\" value=\"")[1].split("\"/>")[0];
			}
			if (relaystate == null || samlresponse == null)
				throw new IllegalStateException("RelayState is " + relaystate + " and SAMLResponse is " + samlresponse);
			response.close();

			List<NameValuePair> formList1 = new ArrayList<>();
			formList1.add(new BasicNameValuePair("RelayState", relaystate));
			formList1.add(new BasicNameValuePair("SAMLResponse", samlresponse));
			response = post("https://studip.uni-passau.de/Shibboleth.sso/SAML2/POST",
					new String[]{"Referer"}, new String[]{"https://sso.uni-passau.de" + loc}, formList1);
			response.close();

			response = get("https://studip.uni-passau.de/secure/studip-sp.php?target=https%3A%2F%2Fstudip.uni-passau.de%2Fstudip%2Findex.php%3Fagain%3Dyes%26sso%3Dshib");
			String semurl = response.getResponse().getFirstHeader("location").getValue();
			response.close();

			response = get(semurl);
			response.close();
		} finally {
			if (response != null)
				response.close();
		}
	}

	/**
	 * Returns the client's {@link CookieStore}, if you want to manually add Cookies to the Client.
	 *
	 * @return the client's {@link CookieStore}
	 */
	public CookieStore getCookieStore() {
		return ((AbstractHttpClient) this.client.getHttpClient()).getCookieStore();
	}

	/**
	 * Helper method for one-line creation of {@link BasicClientCookie}s.
	 *
	 * @param key    The cookie's key
	 * @param value  The cookie's value
	 * @param domain The cookie's validation domain
	 * @param path   The cookie's path
	 * @return the created {@link BasicClientCookie}
	 */
	public BasicClientCookie createCookie(String key, String value, String domain, String path) {
		BasicClientCookie c = new BasicClientCookie(key, value);
		c.setDomain(domain);
		c.setPath(path);
		return c;
	}

	/**
	 * Performs a HTTP GET Request.
	 *
	 * @param url The URL to get
	 * @return A {@link ShibHttpResponse} representing the result
	 * @throws IOException              if reading errors occur
	 * @throws IllegalArgumentException if the header values are broken
	 * @throws IllegalAccessException   if the session isn't valid
	 */
	public ShibHttpResponse get(String url) throws IOException, IllegalArgumentException, IllegalAccessException {
		return get(url, new String[0], new String[0]);
	}

	/**
	 * Performs a HTTP GET Request.
	 *
	 * @param url        The URL to get
	 * @param headerKeys An array containing keys for the headers to send with the request
	 * @param headerVals An array containing values for the headers to send with the request (size must be the same as headerKeys.length)
	 * @return A {@link ShibHttpResponse} representing the result
	 * @throws IOException              if reading errors occur
	 * @throws IllegalArgumentException if the header values are broken
	 * @throws IllegalAccessException   if the session isn't valid
	 */
	public ShibHttpResponse get(String url, String[] headerKeys, String[] headerVals) throws IOException, IllegalArgumentException, IllegalAccessException {
		HttpGet httpGet = new HttpGet(url);
		return executeRequest(httpGet, headerKeys, headerVals);
	}

	/**
	 * Performs a HTTP POST Request.
	 *
	 * @param url The URL to post
	 * @return A {@link ShibHttpResponse} representing the result
	 * @throws IOException              if reading errors occur
	 * @throws IllegalArgumentException if the header values are broken
	 * @throws IllegalAccessException   if the session isn't valid
	 */
	public ShibHttpResponse post(String url) throws IOException, IllegalArgumentException, IllegalAccessException {
		return post(url, new String[0], new String[0]);
	}

	/**
	 * Performs a HTTP POST Request.
	 *
	 * @param url        The URL to post
	 * @param headerKeys An array containing keys for the headers to send with the request
	 * @param headerVals An array containing values for the headers to send with the request (size must be the same as headerKeys.length)
	 * @return A {@link ShibHttpResponse} representing the result
	 * @throws IOException              if reading errors occur
	 * @throws IllegalArgumentException if the header values are broken
	 * @throws IllegalAccessException   if the session isn't valid
	 */
	public ShibHttpResponse post(String url, String[] headerKeys, String[] headerVals) throws IOException, IllegalArgumentException, IllegalAccessException {
		return post(url, headerKeys, headerVals, null);
	}

	/**
	 * Performs a HTTP POST Request.
	 *
	 * @param url  The URL to post
	 * @param nvps A list containing {@link NameValuePair}s
	 * @return A {@link ShibHttpResponse} representing the result
	 * @throws IOException              if reading errors occur
	 * @throws IllegalArgumentException if the header values are broken
	 * @throws IllegalAccessException   if the session isn't valid
	 */
	public ShibHttpResponse post(String url, List<NameValuePair> nvps) throws IOException, IllegalArgumentException, IllegalAccessException {
		return post(url, new String[0], new String[0], nvps);
	}

	/**
	 * Performs a HTTP POST Request.
	 *
	 * @param url        The URL to post
	 * @param headerKeys An array containing keys for the headers to send with the request
	 * @param headerVals An array containing values for the headers to send with the request (size must be the same as headerKeys.length)
	 * @param nvps       An optional list containing {@link NameValuePair}s
	 * @return A {@link ShibHttpResponse} representing the result
	 * @throws IOException              if reading errors occur
	 * @throws IllegalArgumentException if the header values are broken
	 * @throws IllegalAccessException   if the session isn't valid
	 */
	public ShibHttpResponse post(String url, String[] headerKeys, String[] headerVals, @Nullable List<NameValuePair> nvps) throws IOException, IllegalArgumentException, IllegalAccessException {
		HttpPost httpPost = new HttpPost(url);
		if (nvps != null) {
			httpPost.setEntity(new UrlEncodedFormEntity(nvps));
			httpPost.addHeader("Content-Type", "application/x-www-form-urlencoded");
		}
		return executeRequest(httpPost, headerKeys, headerVals);
	}

	/**
	 * Performs a HTTP PUT Request.
	 *
	 * @param url The URL to put
	 * @return A {@link ShibHttpResponse} representing the result
	 * @throws IOException            if reading errors occur
	 * @throws IllegalAccessException if the session isn't valid
	 */
	public ShibHttpResponse put(String url) throws IOException, IllegalAccessException {
		return put(url, new String[0], new String[0]);
	}

	/**
	 * Performs a HTTP PUT Request.
	 *
	 * @param url        The URL to put
	 * @param headerKeys An array containing keys for the headers to send with the request
	 * @param headerVals An array containing values for the headers to send with the request (size must be the same as headerKeys.length)
	 * @return A {@link ShibHttpResponse} representing the result
	 * @throws IOException              if reading errors occur
	 * @throws IllegalArgumentException if the header values are broken
	 * @throws IllegalAccessException   if the session isn't valid
	 */
	public ShibHttpResponse put(String url, String[] headerKeys, String[] headerVals) throws IOException, IllegalArgumentException, IllegalAccessException {
		HttpPut httpPut = new HttpPut(url);
		return executeRequest(httpPut, headerKeys, headerVals);
	}

	/**
	 * Performs a HTTP DELETE Request.
	 *
	 * @param url The URL to delete
	 * @return A {@link ShibHttpResponse} representing the result
	 * @throws IOException            if reading errors occur
	 * @throws IllegalAccessException if the session isn't valid
	 */
	public ShibHttpResponse delete(String url) throws IOException, IllegalAccessException {
		return delete(url, new String[0], new String[0]);
	}

	/**
	 * Performs a HTTP DELETE Request.
	 *
	 * @param url        The URL to delete
	 * @param headerKeys An array containing keys for the headers to send with the request
	 * @param headerVals An array containing values for the headers to send with the request (size must be the same as headerKeys.length)
	 * @return A {@link ShibHttpResponse} representing the result
	 * @throws IOException              if reading errors occur
	 * @throws IllegalArgumentException if the header values are broken
	 * @throws IllegalAccessException   if the session isn't valid
	 */
	public ShibHttpResponse delete(String url, String[] headerKeys, String[] headerVals) throws IOException, IllegalArgumentException, IllegalAccessException {
		HttpDelete httpDelete = new HttpDelete(url);
		return executeRequest(httpDelete, headerKeys, headerVals);
	}

	/**
	 * Executes the given HTTP request.
	 *
	 * @param request    The request to execute
	 * @param headerKeys An array containing keys for the headers to send with the request
	 * @param headerVals An array containing values for the headers to send with the request (size must be the same as headerKeys.length)
	 * @return A {@link ShibHttpResponse} representing the result
	 * @throws IOException              if reading errors occur
	 * @throws IllegalArgumentException if the header values are broken
	 * @throws IllegalAccessException   if the session isn't valid
	 */
	private ShibHttpResponse executeRequest(HttpRequestBase request, String[] headerKeys, String[] headerVals) throws IOException, IllegalArgumentException, IllegalAccessException {
		if (headerKeys.length != headerVals.length)
			throw new IllegalArgumentException("headerVals has different length than headerKeys!");
		for (int i = 0; i < headerKeys.length; i++)
			request.addHeader(headerKeys[i], headerVals[i]);
		HttpResponse response = client.getHttpClient().execute(request);
		if (response.getStatusLine().getStatusCode() == 401)
			throw new IllegalAccessException("Session is not valid!");
		return new ShibHttpResponse(response, request);
	}

	/**
	 * Shuts the client down.
	 */
	public void shutdown() {
		this.client.shutdown();
	}

}
