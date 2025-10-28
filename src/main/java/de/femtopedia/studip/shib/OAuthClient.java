package de.femtopedia.studip.shib;

import java.io.IOException;
import java.util.List;
import lombok.NoArgsConstructor;
import oauth.signpost.exception.OAuthException;
import okhttp3.Request;
import se.akerfeldt.okhttp.signpost.OkHttpOAuthConsumer;
import se.akerfeldt.okhttp.signpost.OkHttpOAuthProvider;

/**
 * A simple wrapper for the communication with Uni Passau's Stud.IP OAuth.
 */
@NoArgsConstructor
public class OAuthClient extends CustomAccessClient {

    /**
     * The used {@link oauth.signpost.OAuthConsumer}.
     */
    private OkHttpOAuthConsumer consumer;

    /**
     * The used {@link oauth.signpost.OAuthProvider}.
     */
    private OkHttpOAuthProvider provider;

    public OAuthClient(String consumerKey, String consumerSecret) {
        setupOAuth(consumerKey, consumerSecret);
    }

    /**
     * Sets up the OAuth API.
     * This is the first function to call in the OAuth process.
     *
     * @param consumerKey    The Consumer Key to use
     * @param consumerSecret The Consumer Secret to use
     */
    public void setupOAuth(String consumerKey, String consumerSecret) {
        consumer = new OkHttpOAuthConsumer(consumerKey, consumerSecret);
        provider = new OkHttpOAuthProvider(
                "https://studip.uni-passau.de/studip/dispatch.php"
                + "/api/oauth/request_token",
                "https://studip.uni-passau.de/studip/dispatch.php"
                + "/api/oauth/access_token",
                "https://studip.uni-passau.de/studip/dispatch.php"
                + "/api/oauth/authorize");
        provider.setOkHttpClient(getClient());
    }

    /**
     * Retrieves a Request Token and returns a authorization URL, which you have
     * to open in a browser and approve access to your account.
     * This is the second function to call in the OAuth process (call
     * {@link OAuthClient#setupOAuth(String, String)} first).
     *
     * @param callback A callback URI or
     *                 {@link oauth.signpost.OAuth#OUT_OF_BAND}.
     * @return The Authorization URL to call.
     * @throws OAuthException if any OAuth errors occur.
     */
    public String getAuthorizationUrl(String callback) throws OAuthException {
        return provider.retrieveRequestToken(consumer, callback);
    }

    /**
     * Retrieves a working Access Token and saves it.
     * This is the third function to call in the OAuth process (call
     * {@link OAuthClient#getAuthorizationUrl(String)}.
     * first and authorize in a browser).
     *
     * @param verificationCode The Verification Code from the Authorization
     *                         process.
     * @throws OAuthException if any OAuth errors occur.
     */
    public void verifyAccess(String verificationCode) throws OAuthException {
        provider.retrieveAccessToken(consumer, verificationCode.trim());
    }

    /**
     * Sets a custom Access Token.
     *
     * @param accessToken       The Access Token to set.
     * @param accessTokenSecret The Access Token Secret to set.
     */
    public void setToken(String accessToken, String accessTokenSecret) {
        consumer.setTokenWithSecret(accessToken, accessTokenSecret);
    }

    /**
     * Returns the used Access Token.
     *
     * @return The currently used Access Token (key) and Access Token Secret
     *         (value).
     */
    public Pair<String, String> getToken() {
        return new Pair<>(consumer.getToken(), consumer.getTokenSecret());
    }

    /**
     * Performs a HTTP GET Request.
     *
     * @param url        The URL to get.
     * @param headerKeys An array containing keys for the headers to send with
     *                   the request.
     * @param headerVals An array containing values for the headers to send with
     *                   the request (size must be the same as
     *                   headerKeys.length).
     * @return A {@link CustomAccessHttpResponse} representing the result.
     * @throws IOException              if reading errors occur.
     * @throws IllegalArgumentException if the header values are broken.
     * @throws IllegalAccessException   if the session isn't valid.
     * @throws OAuthException           if any OAuth errors occur.
     */
    public CustomAccessHttpResponse get(String url, String[] headerKeys,
                                        String[] headerVals)
            throws IOException, IllegalArgumentException,
            IllegalAccessException, OAuthException {
        Request.Builder requestBuilder = new Request.Builder()
                .url(url)
                .get();
        Request request = sign(requestBuilder.build());
        return executeRequest(request.newBuilder(), headerKeys, headerVals);
    }

    /**
     * Performs a HTTP POST Request.
     *
     * @param url        The URL to post.
     * @param headerKeys An array containing keys for the headers to send with
     *                   the request.
     * @param headerVals An array containing values for the headers to send with
     *                   the request (size must be the same as
     *                   headerKeys.length).
     * @param nvps       A list containing Pairs for Form Data.
     * @return A {@link CustomAccessHttpResponse} representing the result.
     * @throws IOException              if reading errors occur.
     * @throws IllegalArgumentException if the header values are broken.
     * @throws IllegalAccessException   if the session isn't valid.
     * @throws OAuthException           if any OAuth errors occur.
     */
    public CustomAccessHttpResponse post(String url, String[] headerKeys,
                                         String[] headerVals,
                                         List<Pair<String, String>> nvps)
            throws IOException, IllegalArgumentException,
            IllegalAccessException, OAuthException {
        Request.Builder requestBuilder = new Request.Builder()
                .url(url);
        Request request = sign(requestBuilder.post(getFormBody(nvps)).build());
        return executeRequest(
                request.newBuilder(),
                headerKeys, headerVals);
    }

    /**
     * Performs a HTTP PUT Request.
     *
     * @param url        The URL to put.
     * @param headerKeys An array containing keys for the headers to send with
     *                   the request.
     * @param headerVals An array containing values for the headers to send with
     *                   the request (size must be the same as
     *                   headerKeys.length).
     * @param nvps       A list containing Pairs for Form Data.
     * @return A {@link CustomAccessHttpResponse} representing the result.
     * @throws IOException              if reading errors occur.
     * @throws IllegalArgumentException if the header values are broken.
     * @throws IllegalAccessException   if the session isn't valid.
     * @throws OAuthException           if any OAuth errors occur.
     */
    public CustomAccessHttpResponse put(String url, String[] headerKeys,
                                        String[] headerVals,
                                        List<Pair<String, String>> nvps)
            throws IOException, IllegalArgumentException,
            IllegalAccessException, OAuthException {
        Request.Builder requestBuilder = new Request.Builder()
                .url(url);
        Request request = sign(requestBuilder.put(getFormBody(nvps)).build());
        return executeRequest(
                request.newBuilder(),
                headerKeys, headerVals);
    }

    /**
     * Performs a HTTP DELETE Request.
     *
     * @param url        The URL to delete.
     * @param headerKeys An array containing keys for the headers to send with
     *                   the request.
     * @param headerVals An array containing values for the headers to send with
     *                   the request (size must be the same as
     *                   headerKeys.length).
     * @param nvps       A list containing Pairs for Form Data.
     * @return A {@link CustomAccessHttpResponse} representing the result.
     * @throws IOException              if reading errors occur.
     * @throws IllegalArgumentException if the header values are broken.
     * @throws IllegalAccessException   if the session isn't valid.
     * @throws OAuthException           if any OAuth errors occur.
     */
    public CustomAccessHttpResponse delete(String url, String[] headerKeys,
                                           String[] headerVals,
                                           List<Pair<String, String>> nvps)
            throws IOException, IllegalArgumentException,
            IllegalAccessException, OAuthException {
        Request.Builder requestBuilder = new Request.Builder()
                .url(url);
        Request request = sign(requestBuilder.delete(getFormBody(nvps))
                .build());
        return executeRequest(
                request.newBuilder(),
                headerKeys, headerVals);
    }

    /**
     * Signs a HTTP Request using the OAuth data.
     *
     * @param request The Request to sign.
     * @return the signed Request.
     * @throws OAuthException if any OAuth errors occur.
     */
    public Request sign(Request request) throws OAuthException {
        if (request.url().host().equalsIgnoreCase("studip.uni-passau.de")) {
            return (Request) consumer.sign(request).unwrap();
        }
        return request;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isErrorCode(int statusCode) {
        return statusCode == 401 || statusCode == 500;
    }

}
