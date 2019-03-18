package de.femtopedia.studip.shib;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import javax.annotation.Nullable;
import oauth.signpost.commonshttp.CommonsHttpOAuthConsumer;
import oauth.signpost.commonshttp.CommonsHttpOAuthProvider;
import oauth.signpost.exception.OAuthException;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpRequestBase;

/**
 * A simple wrapper for the communication with Uni Passau's Stud.IP OAuth.
 */
public class OAuthClient extends CustomAccessClient {

	/**
	 * The used {@link oauth.signpost.OAuthConsumer}.
	 */
	private CommonsHttpOAuthConsumer consumer;

	/**
	 * The used {@link oauth.signpost.OAuthProvider}.
	 */
	private CommonsHttpOAuthProvider provider;

	/**
	 * Initializes a default {@link OAuthClient} instance.
	 */
	public OAuthClient() {
		this(null, "");
	}

	/**
	 * Initializes a {@link OAuthClient} instance with a custom KeyStore.
	 *
	 * @param keyStore A custom KeyStore as {@link InputStream} to set or null
	 * @param password The KeyStore's password
	 */
	public OAuthClient(InputStream keyStore, String password) {
		super(keyStore, password);
	}

	/**
	 * Sets up the OAuth API.
	 * This is the first function to call in the OAuth process.
	 *
	 * @param consumerKey    The Consumer Key to use
	 * @param consumerSecret The Consumer Secret to use
	 */
	public void setupOAuth(String consumerKey, String consumerSecret) {
		consumer = new CommonsHttpOAuthConsumer(consumerKey, consumerSecret);
		provider = new CommonsHttpOAuthProvider(
				"https://studip.uni-passau.de/studip/dispatch.php/api/oauth/request_token",
				"https://studip.uni-passau.de/studip/dispatch.php/api/oauth/access_token",
				"https://studip.uni-passau.de/studip/dispatch.php/api/oauth/authorize");
		provider.setHttpClient(client.getHttpClient());
	}

	/**
	 * Retrieves a Request Token and returns a authorization URL, which you have to open in a browser
	 * and approve access to your account.
	 * This is the second function to call in the OAuth process (call {@link OAuthClient#setupOAuth(String, String)} first).
	 *
	 * @param callback A callback URI or {@link oauth.signpost.OAuth#OUT_OF_BAND}.
	 * @return The Authorization URL to call
	 * @throws OAuthException if any OAuth errors occur
	 */
	public String getAuthorizationUrl(String callback) throws OAuthException {
		return provider.retrieveRequestToken(consumer, callback);
	}

	/**
	 * Retrieves a working Access Token and saves it.
	 * This is the third function to call in the OAuth process (call {@link OAuthClient#getAuthorizationUrl(String)}
	 * first and authorize in a browser).
	 *
	 * @param verificationCode The Verification Code from the Authorization process.
	 * @throws OAuthException if any OAuth errors occur
	 */
	public void verifyAccess(String verificationCode) throws OAuthException {
		provider.retrieveAccessToken(consumer, verificationCode.trim());
	}

	/**
	 * Sets a custom Access Token.
	 *
	 * @param accessToken       The Access Token to set
	 * @param accessTokenSecret The Access Token Secret to set
	 */
	public void setToken(String accessToken, String accessTokenSecret) {
		consumer.setTokenWithSecret(accessToken, accessTokenSecret);
	}

	/**
	 * Returns the used Access Token.
	 *
	 * @return The currently used Access Token (index 0) and Access Token Secret (index 1).
	 */
	public String[] getToken() {
		return new String[]{consumer.getToken(), consumer.getTokenSecret()};
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
	public CustomAccessHttpResponse get(String url, String[] headerKeys, String[] headerVals)
			throws IOException, IllegalArgumentException, IllegalAccessException, OAuthException {
		HttpGet httpGet = sign(new HttpGet(url));
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
	 * @throws OAuthException           if any OAuth errors occur
	 */
	public CustomAccessHttpResponse post(String url, String[] headerKeys, String[] headerVals, @Nullable List<NameValuePair> nvps)
			throws IOException, IllegalArgumentException, IllegalAccessException, OAuthException {
		HttpPost httpPost = sign(new HttpPost(url));
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
	 * @throws OAuthException           if any OAuth errors occur
	 */
	public CustomAccessHttpResponse put(String url, String[] headerKeys, String[] headerVals)
			throws IOException, IllegalArgumentException, IllegalAccessException, OAuthException {
		HttpPut httpPut = sign(new HttpPut(url));
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
	 * @throws OAuthException           if any OAuth errors occur
	 */
	public CustomAccessHttpResponse delete(String url, String[] headerKeys, String[] headerVals)
			throws IOException, IllegalArgumentException, IllegalAccessException, OAuthException {
		HttpDelete httpDelete = sign(new HttpDelete(url));
		return executeRequest(httpDelete, headerKeys, headerVals);
	}

	/**
	 * Signs a HTTP Request using the OAuth data.
	 *
	 * @param request The Request to sign
	 * @param <T>     The Request's type (GET, POST etc.)
	 * @return the signed Request
	 * @throws OAuthException if any OAuth errors occur
	 */
	@SuppressWarnings("unchecked")
	public <T extends HttpRequestBase> T sign(T request) throws OAuthException {
		if (request.getURI().getHost().equals("studip.uni-passau.de"))
			return (T) consumer.sign(request).unwrap();
		return request;
	}

	@Override
	public boolean isErrorCode(int statusCode) {
		return statusCode == 401 || statusCode == 500;
	}

}
