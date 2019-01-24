package de.femtopedia.studip.shib;

import com.google.api.client.http.apache.ApacheHttpTransport;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.CookieStore;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.AbstractHttpClient;
import org.apache.http.impl.cookie.BasicClientCookie;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.params.HttpConnectionParams;

/**
 * A simple wrapper for the communication with Uni Passau's Shibboleth SSO.
 */
public class ShibbolethClient {

	/**
	 * The instance of the Apache HTTP Client to use.
	 */
	public ApacheHttpTransport client;

	/**
	 * Initializes a default {@link ShibbolethClient} instance.
	 */
	public ShibbolethClient() {
		this.client = constructHttpClient();
	}

	/**
	 * Helper method for constructing an Apache HTTP Client.
	 *
	 * @return A default Apache HTTP Client
	 */
	private static ApacheHttpTransport constructHttpClient() {
		ApacheHttpTransport.Builder builder = new ApacheHttpTransport.Builder();
		HttpConnectionParams.setConnectionTimeout(builder.getHttpParams(), 30000);
		HttpConnectionParams.setSoTimeout(builder.getHttpParams(), 30000);
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
	 * Helper method for reading lines as {@link String}s from an {@link InputStream} using UTF-8.
	 *
	 * @param input The InputStream to read
	 * @return The read lines
	 * @throws IOException if the reading process fails
	 */
	public static List<String> readLines(InputStream input) throws IOException {
		return readLines(input, StandardCharsets.UTF_8.name());
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
	 */
	public void authenticate(String username, String password) throws IOException, IllegalArgumentException, IllegalAccessException, IllegalStateException {
		if (!this.isSessionValid())
			this.authenticateIntern(username, password);
	}

	/**
	 * Returns, whether the current session is valid or you need to re-login using {@link ShibbolethClient#authenticate(String, String)}.
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
		if (headerKeys.length != headerVals.length)
			throw new IllegalArgumentException("headerVals has different length than headerKeys!");
		HttpGet httpGet = new HttpGet(url);
		for (int i = 0; i < headerKeys.length; i++)
			httpGet.addHeader(headerKeys[i], headerVals[i]);
		HttpResponse response = client.getHttpClient().execute(httpGet);
		if (response.getStatusLine().getStatusCode() == 401)
			throw new IllegalAccessException("Session is not valid!");
		return new ShibHttpResponse(response, httpGet);
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
	 * @param url        The URL to post
	 * @param headerKeys An array containing keys for the headers to send with the request
	 * @param headerVals An array containing values for the headers to send with the request (size must be the same as headerKeys.length)
	 * @param nvps       An optional list containing {@link NameValuePair}s.
	 * @return A {@link ShibHttpResponse} representing the result
	 * @throws IOException              if reading errors occur
	 * @throws IllegalArgumentException if the header values are broken
	 * @throws IllegalAccessException   if the session isn't valid
	 */
	public ShibHttpResponse post(String url, String[] headerKeys, String[] headerVals, @Nullable List<NameValuePair> nvps) throws IOException, IllegalArgumentException, IllegalAccessException {
		if (headerKeys.length != headerVals.length)
			throw new IllegalArgumentException("headerVals has different length than headerKeys!");
		HttpPost httpPost = new HttpPost(url);
		for (int i = 0; i < headerKeys.length; i++)
			httpPost.addHeader(headerKeys[i], headerVals[i]);
		if (nvps != null) {
			httpPost.setEntity(new UrlEncodedFormEntity(nvps));
			httpPost.addHeader("Content-Type", "application/x-www-form-urlencoded");
		}
		HttpResponse response = client.getHttpClient().execute(httpPost);
		if (response.getStatusLine().getStatusCode() == 401)
			throw new IllegalAccessException("Session is not valid!");
		return new ShibHttpResponse(response, httpPost);
	}

	/**
	 * Shuts the client down.
	 */
	public void shutdown() {
		this.client.shutdown();
	}

}
