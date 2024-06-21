package io.github.amithkoujalgi.ollama4j.core.models.request;

import io.github.amithkoujalgi.ollama4j.core.OllamaAPI;
import io.github.amithkoujalgi.ollama4j.core.exceptions.OllamaBaseException;
import io.github.amithkoujalgi.ollama4j.core.models.BasicAuth;
import io.github.amithkoujalgi.ollama4j.core.models.OllamaErrorResponseModel;
import io.github.amithkoujalgi.ollama4j.core.models.OllamaResult;
import io.github.amithkoujalgi.ollama4j.core.utils.OllamaRequestBody;
import io.github.amithkoujalgi.ollama4j.core.utils.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Abstract helper class to call the ollama api server.
 */
public abstract class OllamaEndpointCaller {

    private static final Logger LOG = LoggerFactory.getLogger(OllamaAPI.class);

    private String host;
    private BasicAuth basicAuth;
    private long requestTimeoutSeconds;
    private boolean verbose;

    public OllamaEndpointCaller(String host, BasicAuth basicAuth, long requestTimeoutSeconds, boolean verbose) {
        this.host = host;
        this.basicAuth = basicAuth;
        this.requestTimeoutSeconds = requestTimeoutSeconds;
        this.verbose = verbose;
    }

    protected abstract String getEndpointSuffix();

    protected abstract boolean parseResponseAndAddToBuffer(String line, StringBuilder responseBuffer);

    /**
     * Calls the api server on the given host and endpoint suffix asynchronously, aka waiting for the response.
     *
     * @param body POST body payload
     * @return result answer given by the assistant
     * @throws OllamaBaseException  any response code than 200 has been returned
     * @throws IOException          in case the responseStream can not be read
     * @throws InterruptedException in case the server is not reachable or network issues happen
     */
    public OllamaResult callSync(OllamaRequestBody body) throws OllamaBaseException, IOException, InterruptedException {
        long startTime = System.currentTimeMillis();
        URI uri = URI.create(this.host + getEndpointSuffix());
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(uri.toString()).openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setConnectTimeout((int) (requestTimeoutSeconds * 1000));
            connection.setReadTimeout((int) (requestTimeoutSeconds * 1000));
            if (isBasicAuthCredentialsSet()) {
                connection.setRequestProperty("Authorization", getBasicAuthHeaderValue());
            }
            connection.setDoOutput(true);
            try (OutputStream os = connection.getOutputStream()) {
                byte[] input = body.toJsonString().getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }

            int statusCode = connection.getResponseCode();
            InputStream responseBodyStream = (statusCode == 200) ? connection.getInputStream() : connection.getErrorStream();
            StringBuilder responseBuffer = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(responseBodyStream, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (statusCode == 404) {
                        LOG.warn("Status code: 404 (Not Found)");
                        OllamaErrorResponseModel ollamaResponseModel =
                                Utils.getObjectMapper().readValue(line, OllamaErrorResponseModel.class);
                        responseBuffer.append(ollamaResponseModel.getError());
                    } else if (statusCode == 401) {
                        LOG.warn("Status code: 401 (Unauthorized)");
                        OllamaErrorResponseModel ollamaResponseModel =
                                Utils.getObjectMapper()
                                        .readValue("{\"error\":\"Unauthorized\"}", OllamaErrorResponseModel.class);
                        responseBuffer.append(ollamaResponseModel.getError());
                    } else if (statusCode == 400) {
                        LOG.warn("Status code: 400 (Bad Request)");
                        OllamaErrorResponseModel ollamaResponseModel = Utils.getObjectMapper().readValue(line,
                                OllamaErrorResponseModel.class);
                        responseBuffer.append(ollamaResponseModel.getError());
                    } else {
                        boolean finished = parseResponseAndAddToBuffer(line, responseBuffer);
                        if (finished) {
                            break;
                        }
                    }
                }
            }

            if (statusCode != 200) {
                LOG.error("Status code " + statusCode);
                throw new OllamaBaseException(responseBuffer.toString());
            } else {
                long endTime = System.currentTimeMillis();
                OllamaResult ollamaResult =
                        new OllamaResult(responseBuffer.toString().trim(), endTime - startTime, statusCode);
                if (verbose) LOG.info("Model response: " + ollamaResult);
                return ollamaResult;
            }
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    /**
     * Get basic authentication header value.
     *
     * @return basic authentication header value (encoded credentials)
     */
    private String getBasicAuthHeaderValue() {
        String credentialsToEncode = this.basicAuth.getUsername() + ":" + this.basicAuth.getPassword();
        return "Basic " + Base64.getEncoder().encodeToString(credentialsToEncode.getBytes());
    }

    /**
     * Check if Basic Auth credentials set.
     *
     * @return true when Basic Auth credentials set
     */
    private boolean isBasicAuthCredentialsSet() {
        return this.basicAuth != null;
    }
}
