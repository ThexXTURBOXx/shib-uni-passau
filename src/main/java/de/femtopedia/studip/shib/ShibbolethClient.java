package de.femtopedia.studip.shib;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import oauth.signpost.exception.OAuthException;
import org.apache.http.HttpEntity;
import org.apache.http.NameValuePair;
import org.apache.http.client.CookieStore;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.impl.client.AbstractHttpClient;
import org.apache.http.impl.cookie.BasicClientCookie;
import org.apache.http.message.BasicNameValuePair;

/**
 * A simple wrapper for the communication with Uni Passau's Shibboleth SSO.
 *
 * @deprecated This class is still functional, but you should use
 * OAuth Authentication (see {@link OAuthClient}) instead.
 */
@Deprecated
public class ShibbolethClient extends CustomAccessClient {

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
		super(keyStore, password);
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
		try {
			if (!this.isSessionValid())
				this.authenticateIntern(username, password);
		} catch (OAuthException e) {
			e.printStackTrace();
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
		CustomAccessHttpResponse response = null;
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
		} catch (OAuthException e) {
			e.printStackTrace();
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
	 * @param url        The URL to get
	 * @param headerKeys An array containing keys for the headers to send with the request
	 * @param headerVals An array containing values for the headers to send with the request (size must be the same as headerKeys.length)
	 * @return A {@link CustomAccessHttpResponse} representing the result
	 * @throws IOException              if reading errors occur
	 * @throws IllegalArgumentException if the header values are broken
	 * @throws IllegalAccessException   if the session isn't valid
	 */
	public CustomAccessHttpResponse get(String url, String[] headerKeys, String[] headerVals) throws IOException, IllegalArgumentException, IllegalAccessException {
		HttpGet httpGet = new HttpGet(url);
		return executeRequest(httpGet, headerKeys, headerVals);
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
	 */
	public CustomAccessHttpResponse post(String url, String[] headerKeys, String[] headerVals, @Nullable List<NameValuePair> nvps) throws IOException, IllegalArgumentException, IllegalAccessException {
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
	 * @param url        The URL to put
	 * @param headerKeys An array containing keys for the headers to send with the request
	 * @param headerVals An array containing values for the headers to send with the request (size must be the same as headerKeys.length)
	 * @return A {@link CustomAccessHttpResponse} representing the result
	 * @throws IOException              if reading errors occur
	 * @throws IllegalArgumentException if the header values are broken
	 * @throws IllegalAccessException   if the session isn't valid
	 */
	public CustomAccessHttpResponse put(String url, String[] headerKeys, String[] headerVals) throws IOException, IllegalArgumentException, IllegalAccessException {
		HttpPut httpPut = new HttpPut(url);
		return executeRequest(httpPut, headerKeys, headerVals);
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
	 */
	public CustomAccessHttpResponse delete(String url, String[] headerKeys, String[] headerVals) throws IOException, IllegalArgumentException, IllegalAccessException {
		HttpDelete httpDelete = new HttpDelete(url);
		return executeRequest(httpDelete, headerKeys, headerVals);
	}

	@Override
	public boolean isErrorCode(int statusCode) {
		return statusCode == 401;
	}

	/**
	 * Shuts the client down.
	 */
	public void shutdown() {
		this.client.shutdown();
	}

}
