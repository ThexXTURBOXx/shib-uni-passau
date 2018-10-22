package de.femtopedia.studip.shib;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import org.apache.http.HttpEntity;
import org.apache.http.NameValuePair;
import org.apache.http.client.CookieStore;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.cookie.BasicClientCookie;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;

public class ShibbolethClient {

	public CloseableHttpClient client;

	public static List<String> readLines(InputStream input) throws IOException {
		InputStreamReader reader = new InputStreamReader(input);
		BufferedReader readers = new BufferedReader(reader);
		List<String> list = new ArrayList<String>();
		String line = readers.readLine();
		while (line != null) {
			list.add(line);
			line = readers.readLine();
		}
		return list;
	}

	public void authenticate(String username, String password) throws IOException, IllegalArgumentException, IllegalAccessException, IllegalStateException {
		CookieStore cookies = new BasicCookieStore();
		client = HttpClientBuilder.create().setDefaultCookieStore(cookies).disableRedirectHandling().build();

		CloseableHttpResponse response1 = get("https://studip.uni-passau.de/studip/index.php");
		HttpEntity entity1 = response1.getEntity();
		cookies.addCookie(createCookie("cache_session", Long.toString(System.currentTimeMillis()), "studip.uni-passau.de", "/"));
		cookies.addCookie(createCookie("navigation-length", "3", "studip.uni-passau.de", "/studip/"));
		EntityUtils.consume(entity1);
		response1.close();

		CloseableHttpResponse response2 = get("https://studip.uni-passau.de/studip/index.php?again=yes&sso=shib");
		HttpEntity entity2 = response2.getEntity();
		EntityUtils.consume(entity2);
		response2.close();

		CloseableHttpResponse response3 = get("https://studip.uni-passau.de/secure/studip-sp.php?target=https%3A%2F%2Fstudip.uni-passau.de%2Fstudip%2Findex.php%3Fagain%3Dyes%26sso%3Dshib");
		HttpEntity entity3 = response3.getEntity();
		String ssoSAML = response3.getFirstHeader("location").getValue();
		EntityUtils.consume(entity3);
		response3.close();

		CloseableHttpResponse response4 = get(ssoSAML);
		HttpEntity entity4 = response4.getEntity();
		String loc = response4.getFirstHeader("location").getValue();
		EntityUtils.consume(entity4);
		response4.close();

		CloseableHttpResponse response5 = get("https://sso.uni-passau.de" + loc);
		HttpEntity entity5 = response5.getEntity();
		EntityUtils.consume(entity5);
		response5.close();

		List<NameValuePair> formList = new ArrayList<>();
		formList.add(new BasicNameValuePair("_eventId_proceed", ""));
		formList.add(new BasicNameValuePair("donotcache", ""));
		formList.add(new BasicNameValuePair("donotcache_dummy", "1"));
		formList.add(new BasicNameValuePair("j_password", password));
		formList.add(new BasicNameValuePair("j_username", username));
		CloseableHttpResponse response6 = post("https://sso.uni-passau.de" + loc,
				new String[]{"Referer", "Upgrade-Insecure-Requests"}, new String[]{"https://sso.uni-passau.de" + loc, "1"},
				formList);
		HttpEntity entity6 = response6.getEntity();
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
		EntityUtils.consume(entity6);
		response6.close();

		List<NameValuePair> formList1 = new ArrayList<>();
		formList1.add(new BasicNameValuePair("RelayState", relaystate));
		formList1.add(new BasicNameValuePair("SAMLResponse", samlresponse));
		CloseableHttpResponse response7 = post("https://studip.uni-passau.de/Shibboleth.sso/SAML2/POST",
				new String[]{"Referer"}, new String[]{"https://sso.uni-passau.de" + loc}, formList1);
		HttpEntity entity7 = response7.getEntity();
		EntityUtils.consume(entity7);
		response7.close();

		CloseableHttpResponse response8 = get("https://studip.uni-passau.de/secure/studip-sp.php?target=https%3A%2F%2Fstudip.uni-passau.de%2Fstudip%2Findex.php%3Fagain%3Dyes%26sso%3Dshib");
		HttpEntity entity8 = response8.getEntity();
		String semurl = response8.getFirstHeader("location").getValue();
		EntityUtils.consume(entity8);
		response8.close();

		CloseableHttpResponse response9 = get(semurl);
		HttpEntity entity9 = response9.getEntity();
		EntityUtils.consume(entity9);
		response9.close();
	}

	public BasicClientCookie createCookie(String key, String value, String domain, String path) {
		BasicClientCookie c = new BasicClientCookie(key, value);
		c.setDomain(domain);
		c.setPath(path);
		return c;
	}

	public CloseableHttpResponse get(String url) throws IOException, IllegalArgumentException {
		return get(url, new String[0], new String[0]);
	}

	public CloseableHttpResponse get(String url, String[] headerKeys, String[] headerVals) throws IOException, IllegalArgumentException {
		if (headerKeys.length != headerVals.length)
			throw new IllegalArgumentException("headerVals has different length than headerKeys!");
		HttpGet httpGet = new HttpGet(url);
		for (int i = 0; i < headerKeys.length; i++)
			httpGet.addHeader(headerKeys[i], headerVals[i]);
		return client.execute(httpGet);
	}

	public CloseableHttpResponse post(String url) throws IOException, IllegalArgumentException {
		return post(url, new String[0], new String[0]);
	}

	public CloseableHttpResponse post(String url, String[] headerKeys, String[] headerVals) throws IOException, IllegalArgumentException {
		return post(url, headerKeys, headerVals, null);
	}

	public CloseableHttpResponse post(String url, String[] headerKeys, String[] headerVals, List<NameValuePair> nvps) throws IOException, IllegalArgumentException {
		if (headerKeys.length != headerVals.length)
			throw new IllegalArgumentException("headerVals has different length than headerKeys!");
		HttpPost httpPost = new HttpPost(url);
		for (int i = 0; i < headerKeys.length; i++)
			httpPost.addHeader(headerKeys[i], headerVals[i]);
		if (nvps != null) {
			httpPost.setEntity(new UrlEncodedFormEntity(nvps));
			httpPost.addHeader("Content-Type", "application/x-www-form-urlencoded");
		}
		return client.execute(httpPost);
	}

}
