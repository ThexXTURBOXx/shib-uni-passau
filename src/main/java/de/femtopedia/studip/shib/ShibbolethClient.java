package de.femtopedia.studip.shib;

import com.google.api.client.http.apache.ApacheHttpTransport;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.apache.http.HttpEntity;
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

public class ShibbolethClient {

	public ApacheHttpTransport client;

	public ShibbolethClient() {
		this.client = constructHttpClient();
	}

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

	public static List<String> readLines(InputStream input) throws IOException {
		return readLines(input, StandardCharsets.UTF_8.name());
	}

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

	public void authenticateIfNeeded(String username, String password) throws IOException, IllegalArgumentException, IllegalAccessException, IllegalStateException {
		if (!this.isSessionValid())
			this.authenticate(username, password);
	}

	public boolean isSessionValid() throws IOException {
		ShibHttpResponse response = get("https://studip.uni-passau.de/studip/api.php");
		try {
			return response.getResponse().getStatusLine().getStatusCode() != 401;
		} finally {
			response.close();
		}
	}

	private void authenticate(String username, String password) throws IOException, IllegalArgumentException, IllegalAccessException, IllegalStateException {
		ShibHttpResponse response1 = get("https://studip.uni-passau.de/studip/index.php");
		getCookieStore().addCookie(createCookie("cache_session", Long.toString(System.currentTimeMillis()), "studip.uni-passau.de", "/"));
		getCookieStore().addCookie(createCookie("navigation-length", "3", "studip.uni-passau.de", "/studip/"));
		response1.close();

		ShibHttpResponse response2 = get("https://studip.uni-passau.de/studip/index.php?again=yes&sso=shib");
		response2.close();

		ShibHttpResponse response3 = get("https://studip.uni-passau.de/secure/studip-sp.php?target=https%3A%2F%2Fstudip.uni-passau.de%2Fstudip%2Findex.php%3Fagain%3Dyes%26sso%3Dshib");
		String ssoSAML = response3.getResponse().getFirstHeader("location").getValue();
		response3.close();

		ShibHttpResponse response4 = get(ssoSAML);
		String loc = response4.getResponse().getFirstHeader("location").getValue();
		response4.close();

		ShibHttpResponse response5 = get("https://sso.uni-passau.de" + loc);
		response5.close();

		List<NameValuePair> formList = new ArrayList<>();
		formList.add(new BasicNameValuePair("_eventId_proceed", ""));
		formList.add(new BasicNameValuePair("donotcache", ""));
		formList.add(new BasicNameValuePair("donotcache_dummy", "1"));
		formList.add(new BasicNameValuePair("j_password", password));
		formList.add(new BasicNameValuePair("j_username", username));
		ShibHttpResponse response6 = post("https://sso.uni-passau.de" + loc,
				new String[]{"Referer", "Upgrade-Insecure-Requests"}, new String[]{"https://sso.uni-passau.de" + loc, "1"},
				formList);
		HttpEntity entity6 = response6.getResponse().getEntity();
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
		response6.close();

		List<NameValuePair> formList1 = new ArrayList<>();
		formList1.add(new BasicNameValuePair("RelayState", relaystate));
		formList1.add(new BasicNameValuePair("SAMLResponse", samlresponse));
		ShibHttpResponse response7 = post("https://studip.uni-passau.de/Shibboleth.sso/SAML2/POST",
				new String[]{"Referer"}, new String[]{"https://sso.uni-passau.de" + loc}, formList1);
		response7.close();

		ShibHttpResponse response8 = get("https://studip.uni-passau.de/secure/studip-sp.php?target=https%3A%2F%2Fstudip.uni-passau.de%2Fstudip%2Findex.php%3Fagain%3Dyes%26sso%3Dshib");
		String semurl = response8.getResponse().getFirstHeader("location").getValue();
		response8.close();

		ShibHttpResponse response9 = get(semurl);
		response9.close();
	}

	public CookieStore getCookieStore() {
		return ((AbstractHttpClient) this.client.getHttpClient()).getCookieStore();
	}

	public BasicClientCookie createCookie(String key, String value, String domain, String path) {
		BasicClientCookie c = new BasicClientCookie(key, value);
		c.setDomain(domain);
		c.setPath(path);
		return c;
	}

	public ShibHttpResponse getIfValid(String url) throws IOException, IllegalAccessException {
		if (!isSessionValid()) {
			throw new IllegalAccessException("Session is not valid!");
		}
		return get(url);
	}

	private ShibHttpResponse get(String url) throws IOException, IllegalArgumentException {
		return get(url, new String[0], new String[0]);
	}

	private ShibHttpResponse get(String url, String[] headerKeys, String[] headerVals) throws IOException, IllegalArgumentException {
		if (headerKeys.length != headerVals.length)
			throw new IllegalArgumentException("headerVals has different length than headerKeys!");
		HttpGet httpGet = new HttpGet(url);
		for (int i = 0; i < headerKeys.length; i++)
			httpGet.addHeader(headerKeys[i], headerVals[i]);
		return new ShibHttpResponse(client.getHttpClient().execute(httpGet), httpGet);
	}

	public ShibHttpResponse post(String url) throws IOException, IllegalArgumentException {
		return post(url, new String[0], new String[0]);
	}

	private ShibHttpResponse post(String url, String[] headerKeys, String[] headerVals) throws IOException, IllegalArgumentException {
		return post(url, headerKeys, headerVals, null);
	}

	private ShibHttpResponse post(String url, String[] headerKeys, String[] headerVals, List<NameValuePair> nvps) throws IOException, IllegalArgumentException {
		if (headerKeys.length != headerVals.length)
			throw new IllegalArgumentException("headerVals has different length than headerKeys!");
		HttpPost httpPost = new HttpPost(url);
		for (int i = 0; i < headerKeys.length; i++)
			httpPost.addHeader(headerKeys[i], headerVals[i]);
		if (nvps != null) {
			httpPost.setEntity(new UrlEncodedFormEntity(nvps));
			httpPost.addHeader("Content-Type", "application/x-www-form-urlencoded");
		}
		return new ShibHttpResponse(client.getHttpClient().execute(httpPost), httpPost);
	}

	public void shutdown() {
		this.client.shutdown();
	}

}
