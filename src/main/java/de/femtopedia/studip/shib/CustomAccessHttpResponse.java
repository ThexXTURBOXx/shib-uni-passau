package de.femtopedia.studip.shib;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * A class for easing handling of HTTP Responses.
 */
@AllArgsConstructor
public class CustomAccessHttpResponse implements Closeable {

    /**
     * The response from the server.
     */
    @Getter
    private final Response response;

    /**
     * Returns the content of the HttpEntity.
     *
     * @return A String, containing the content of the website
     * @throws IOException when reading errors occur
     */
    public String readLine() throws IOException {
        return readLine(CustomAccessClient.DEFAULT_ENCODING);
    }

    /**
     * Returns the content of the HttpEntity.
     *
     * @param encoding The encoding to use when reading
     * @return A String, containing the content of the website
     * @throws IOException when reading errors occur
     */
    public String readLine(String encoding) throws IOException {
        return CustomAccessClient.listToString(readLines(encoding));
    }

    /**
     * Returns the content of the HttpEntity.
     *
     * @return A List of Strings, containing the content of the website
     * @throws IOException when reading errors occur
     */
    public List<String> readLines() throws IOException {
        return readLines(CustomAccessClient.DEFAULT_ENCODING);
    }

    /**
     * Returns the content of the HttpEntity.
     *
     * @param encoding The encoding to use when reading
     * @return A List of Strings, containing the content of the website
     * @throws IOException when reading errors occur
     */
    public List<String> readLines(String encoding) throws IOException {
        try (ResponseBody body = getResponse().body();
             InputStream read = body.byteStream()) {
            return CustomAccessClient.readLines(read, encoding);
        }
    }

    /**
     * Closes this HTTP Response and its request.
     */
    @Override
    public void close() {
        if (response != null) {
            response.close();
        }
    }

}
