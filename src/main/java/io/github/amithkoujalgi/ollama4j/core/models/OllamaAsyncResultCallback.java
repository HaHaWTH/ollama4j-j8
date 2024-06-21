package io.github.amithkoujalgi.ollama4j.core.models;

import io.github.amithkoujalgi.ollama4j.core.exceptions.OllamaBaseException;
import io.github.amithkoujalgi.ollama4j.core.models.generate.OllamaGenerateRequestModel;
import io.github.amithkoujalgi.ollama4j.core.models.generate.OllamaGenerateResponseModel;
import io.github.amithkoujalgi.ollama4j.core.utils.Utils;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.util.LinkedList;
import java.util.Queue;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

@Data
@EqualsAndHashCode(callSuper = true)
@SuppressWarnings("unused")
public class OllamaAsyncResultCallback extends Thread {
  private final HttpURLConnection url;
  private final OllamaGenerateRequestModel ollamaRequestModel;
  private final Queue<String> queue = new LinkedList<>();
  private String result;
  private boolean isDone;

  /**
   * -- GETTER -- Returns the status of the request. Indicates if the request was successful or a
   * failure. If the request was a failure, the `getResponse()` method will return the error
   * message.
   */
  @Getter private boolean succeeded;

  @Setter
  private long requestTimeoutSeconds;

  /**
   * -- GETTER -- Returns the HTTP response status code for the request that was made to Ollama
   * server.
   */
  @Getter private int httpStatusCode;

  /** -- GETTER -- Returns the response time in milliseconds. */
  @Getter private long responseTime = 0;

  public OllamaAsyncResultCallback(
          HttpURLConnection url,
          OllamaGenerateRequestModel ollamaRequestModel,
          long requestTimeoutSeconds) {
    this.url = url;
    this.ollamaRequestModel = ollamaRequestModel;
    this.isDone = false;
    this.result = "";
    this.queue.add("");
    this.requestTimeoutSeconds = requestTimeoutSeconds;
  }

  @Override
  public void run() {
    HttpURLConnection connection = null;
    try {
      long startTime = System.currentTimeMillis();
      connection = url;
      connection.setRequestMethod("POST");
      connection.setRequestProperty("Content-Type", "application/json");
      connection.setConnectTimeout((int) (requestTimeoutSeconds * 1000));
      connection.setReadTimeout((int) (requestTimeoutSeconds * 1000));
      connection.setDoOutput(true);
      try (OutputStream os = connection.getOutputStream()) {
        byte[] input = Utils.getObjectMapper().writeValueAsString(ollamaRequestModel).getBytes(StandardCharsets.UTF_8);
        os.write(input, 0, input.length);
      }
      int statusCode = connection.getResponseCode();
      this.httpStatusCode = statusCode;

      InputStream responseBodyStream = (statusCode == 200) ? connection.getInputStream() : connection.getErrorStream();
      try (BufferedReader reader =
                   new BufferedReader(new InputStreamReader(responseBodyStream, StandardCharsets.UTF_8))) {
        String line;
        StringBuilder responseBuffer = new StringBuilder();
        while ((line = reader.readLine()) != null) {
          if (statusCode == 404) {
            OllamaErrorResponseModel ollamaResponseModel =
                    Utils.getObjectMapper().readValue(line, OllamaErrorResponseModel.class);
            queue.add(ollamaResponseModel.getError());
            responseBuffer.append(ollamaResponseModel.getError());
          } else {
            OllamaGenerateResponseModel ollamaResponseModel =
                    Utils.getObjectMapper().readValue(line, OllamaGenerateResponseModel.class);
            queue.add(ollamaResponseModel.getResponse());
            if (!ollamaResponseModel.isDone()) {
              responseBuffer.append(ollamaResponseModel.getResponse());
            }
          }
        }

        this.isDone = true;
        this.succeeded = true;
        this.result = responseBuffer.toString();
        long endTime = System.currentTimeMillis();
        responseTime = endTime - startTime;
      }
      if (statusCode != 200) {
        throw new OllamaBaseException(this.result);
      }
    } catch (IOException | OllamaBaseException e) {
      this.isDone = true;
      this.succeeded = false;
      this.result = "[FAILED] " + e.getMessage();
    } finally {
      if (connection != null) {
        connection.disconnect();
      }
    }
  }

  /**
   * Returns the status of the thread. This does not indicate that the request was successful or a
   * failure, rather it is just a status flag to indicate if the thread is active or ended.
   *
   * @return boolean - status
   */
  public boolean isComplete() {
    return isDone;
  }

  /**
   * Returns the final completion/response when the execution completes. Does not return intermediate results.
   *
   * @return String completion/response text
   */
  public String getResponse() {
    return result;
  }

  public Queue<String> getStream() {
    return queue;
  }

}
