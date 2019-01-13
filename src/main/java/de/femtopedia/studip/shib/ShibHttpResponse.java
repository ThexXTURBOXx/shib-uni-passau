package de.femtopedia.studip.shib;

import java.io.IOException;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.util.EntityUtils;

public class ShibHttpResponse {

	private CloseableHttpResponse response;
	private HttpRequestBase request;

	ShibHttpResponse(HttpResponse response, HttpRequestBase request) {
		this.response = (CloseableHttpResponse) response;
		this.request = request;
	}

	public CloseableHttpResponse getResponse() {
		return response;
	}

	public void close() {
		try {
			EntityUtils.consume(response.getEntity());
		} catch (IOException e) {
			e.printStackTrace();
		}
		request.releaseConnection();
		request.abort();
		try {
			response.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

}
