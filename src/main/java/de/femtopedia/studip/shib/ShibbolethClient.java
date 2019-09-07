package de.femtopedia.studip.shib;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import oauth.signpost.exception.OAuthException;
import okhttp3.Cookie;
import okhttp3.Request;

/**
 * A simple wrapper for the communication with Uni Passau's Shibboleth SSO.
 *
 * @deprecated This class is still functional, but you should use
 * OAuth Authentication (see {@link OAuthClient}) instead.
 */
@Deprecated
public class ShibbolethClient extends CustomAccessClient {

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
	 * UNSAFE! USE {@link #authenticate(String, String)} INSTEAD!
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
			addCookie("https://studip.uni-passau.de", createCookie("cache_session", Long.toString(System.currentTimeMillis()), "studip.uni-passau.de", "/"));
			addCookie("https://studip.uni-passau.de", createCookie("navigation-length", "3", "studip.uni-passau.de", "/studip/"));
			response.close();

			response = get("https://studip.uni-passau.de/studip/index.php?again=yes&sso=shib");
			response.close();

			response = get("https://studip.uni-passau.de/secure/studip-sp.php?target=https%3A%2F%2Fstudip.uni-passau.de%2Fstudip%2Findex.php%3Fagain%3Dyes%26sso%3Dshib");
			String ssoSAML = response.getResponse().header("location");
			response.close();

			response = get(ssoSAML);
			String loc = response.getResponse().header("location");
			response.close();

			response = get("https://sso.uni-passau.de" + loc);
			response.close();

			List<Pair<String, String>> formList = new ArrayList<>();
			formList.add(new Pair<>("_eventId_proceed", ""));
			formList.add(new Pair<>("donotcache", ""));
			formList.add(new Pair<>("donotcache_dummy", "1"));
			formList.add(new Pair<>("j_password", password));
			formList.add(new Pair<>("j_username", username));
			response = post("https://sso.uni-passau.de" + loc,
					new String[]{"Referer", "Upgrade-Insecure-Requests"}, new String[]{"https://sso.uni-passau.de" + loc, "1"},
					formList);
			List<String> lines = response.readLines();
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

			List<Pair<String, String>> formList1 = new ArrayList<>();
			formList1.add(new Pair<>("RelayState", relaystate));
			formList1.add(new Pair<>("SAMLResponse", samlresponse));
			response = post("https://studip.uni-passau.de/Shibboleth.sso/SAML2/POST",
					new String[]{"Referer"}, new String[]{"https://sso.uni-passau.de" + loc}, formList1);
			response.close();

			response = get("https://studip.uni-passau.de/secure/studip-sp.php?target=https%3A%2F%2Fstudip.uni-passau.de%2Fstudip%2Findex.php%3Fagain%3Dyes%26sso%3Dshib");
			String semurl = response.getResponse().header("location");
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
	 * Adds a Cookie to the Cookie Jar.
	 *
	 * @param url    The Cookie's Domain
	 * @param cookie The Cookie to add
	 */
	public void addCookie(String url, Cookie cookie) {
		this.cookieHelper.setCookie(url, cookie);
	}

	/**
	 * Helper method for one-line creation of {@link Cookie}s.
	 *
	 * @param key    The cookie's key
	 * @param value  The cookie's value
	 * @param domain The cookie's validation domain
	 * @param path   The cookie's path
	 * @return the created {@link Cookie}
	 */
	public Cookie createCookie(String key, String value, String domain, String path) {
		return new Cookie.Builder()
				.domain(domain)
				.path(path)
				.name(key)
				.value(value)
				.build();
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
		Request.Builder requestBuilder = new Request.Builder()
				.url(url)
				.get();
		return executeRequest(requestBuilder, headerKeys, headerVals);
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
	 */
	public CustomAccessHttpResponse post(String url, String[] headerKeys, String[] headerVals, List<Pair<String, String>> nvps) throws IOException, IllegalArgumentException, IllegalAccessException {
		Request.Builder requestBuilder = new Request.Builder()
				.url(url);
		return executeRequest(
				requestBuilder.post(getFormBody(nvps)),
				headerKeys, headerVals);
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
	 */
	public CustomAccessHttpResponse put(String url, String[] headerKeys, String[] headerVals, List<Pair<String, String>> nvps) throws IOException, IllegalArgumentException, IllegalAccessException {
		Request.Builder requestBuilder = new Request.Builder()
				.url(url);
		return executeRequest(
				requestBuilder.put(getFormBody(nvps)),
				headerKeys, headerVals);
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
	 */
	public CustomAccessHttpResponse delete(String url, String[] headerKeys, String[] headerVals, List<Pair<String, String>> nvps) throws IOException, IllegalArgumentException, IllegalAccessException {
		Request.Builder requestBuilder = new Request.Builder()
				.url(url);
		return executeRequest(
				requestBuilder.delete(getFormBody(nvps)),
				headerKeys, headerVals);
	}

	@Override
	public boolean isErrorCode(int statusCode) {
		return statusCode == 401;
	}

}
