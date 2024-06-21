package io.github.amithkoujalgi.ollama4j.core;

import io.github.amithkoujalgi.ollama4j.core.exceptions.OllamaBaseException;
import io.github.amithkoujalgi.ollama4j.core.models.*;
import io.github.amithkoujalgi.ollama4j.core.models.chat.OllamaChatMessage;
import io.github.amithkoujalgi.ollama4j.core.models.chat.OllamaChatRequestBuilder;
import io.github.amithkoujalgi.ollama4j.core.models.chat.OllamaChatRequestModel;
import io.github.amithkoujalgi.ollama4j.core.models.chat.OllamaChatResult;
import io.github.amithkoujalgi.ollama4j.core.models.embeddings.OllamaEmbeddingResponseModel;
import io.github.amithkoujalgi.ollama4j.core.models.embeddings.OllamaEmbeddingsRequestModel;
import io.github.amithkoujalgi.ollama4j.core.models.generate.OllamaGenerateRequestModel;
import io.github.amithkoujalgi.ollama4j.core.models.request.*;
import io.github.amithkoujalgi.ollama4j.core.utils.Options;
import io.github.amithkoujalgi.ollama4j.core.utils.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * The base Ollama API class.
 */
@SuppressWarnings("DuplicatedCode")
public class OllamaAPI {

    private static final Logger logger = LoggerFactory.getLogger(OllamaAPI.class);
    private final String host;
    private long requestTimeoutSeconds = 10;
    private boolean verbose = true;
    private BasicAuth basicAuth;

    /**
     * Instantiates the Ollama API.
     *
     * @param host the host address of Ollama server
     */
    public OllamaAPI(String host) {
        if (host.endsWith("/")) {
            this.host = host.substring(0, host.length() - 1);
        } else {
            this.host = host;
        }
    }

    /**
     * Set request timeout in seconds. Default is 3 seconds.
     *
     * @param requestTimeoutSeconds the request timeout in seconds
     */
    public void setRequestTimeoutSeconds(long requestTimeoutSeconds) {
        this.requestTimeoutSeconds = requestTimeoutSeconds;
    }

    /**
     * Set/unset logging of responses
     *
     * @param verbose true/false
     */
    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    /**
     * Set basic authentication for accessing Ollama server that's behind a reverse-proxy/gateway.
     *
     * @param username the username
     * @param password the password
     */
    public void setBasicAuth(String username, String password) {
        this.basicAuth = new BasicAuth(username, password);
    }

    /**
     * API to check the reachability of Ollama server.
     *
     * @return true if the server is reachable, false otherwise.
     */
    public boolean ping() {
        String url = this.host + "/api/tags";
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setConnectTimeout((int) (requestTimeoutSeconds * 1000));
            int statusCode = connection.getResponseCode();
            return statusCode == 200;
        } catch (IOException e) {
            return false;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    /**
     * List available models from Ollama server.
     *
     * @return the list
     */
    public List<Model> listModels()
            throws OllamaBaseException, IOException, InterruptedException, URISyntaxException {
        String url = this.host + "/api/tags";
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setConnectTimeout((int) (requestTimeoutSeconds * 1000));
            int statusCode = connection.getResponseCode();
            InputStream responseStream = (statusCode == 200) ? connection.getInputStream() : connection.getErrorStream();
            String responseString = readStream(responseStream);
            if (statusCode == 200) {
                return Utils.getObjectMapper()
                        .readValue(responseString, ListModelsResponse.class)
                        .getModels();
            } else {
                throw new OllamaBaseException(statusCode + " - " + responseString);
            }
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    /**
     * Pull a model on the Ollama server from the list of <a
     * href="https://ollama.ai/library">available models</a>.
     *
     * @param modelName the name of the model
     */
    public void pullModel(String modelName)
            throws OllamaBaseException, IOException, URISyntaxException, InterruptedException {
        String url = this.host + "/api/pull";
        String jsonData = new ModelRequest(modelName).toString();
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setConnectTimeout((int) (requestTimeoutSeconds * 1000));
            connection.setDoOutput(true);
            try (OutputStream os = connection.getOutputStream()) {
                byte[] input = jsonData.getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }
            int statusCode = connection.getResponseCode();
            InputStream responseStream = (statusCode == 200) ? connection.getInputStream() : connection.getErrorStream();
            String responseString = readStream(responseStream);
            if (statusCode != 200) {
                throw new OllamaBaseException(statusCode + " - " + responseString);
            }
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    /**
     * Gets model details from the Ollama server.
     *
     * @param modelName the model
     * @return the model details
     */
    public ModelDetail getModelDetails(String modelName)
            throws IOException, OllamaBaseException, InterruptedException, URISyntaxException {
        String url = this.host + "/api/show";
        String jsonData = new ModelRequest(modelName).toString();
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setConnectTimeout((int) (requestTimeoutSeconds * 1000));
            connection.setDoOutput(true);
            try (OutputStream os = connection.getOutputStream()) {
                byte[] input = jsonData.getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }
            int statusCode = connection.getResponseCode();
            InputStream responseStream = (statusCode == 200) ? connection.getInputStream() : connection.getErrorStream();
            String responseBody = readStream(responseStream);
            if (statusCode == 200) {
                return Utils.getObjectMapper().readValue(responseBody, ModelDetail.class);
            } else {
                throw new OllamaBaseException(statusCode + " - " + responseBody);
            }
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    /**
     * Create a custom model from a model file. Read more about custom model file creation <a
     * href="https://github.com/jmorganca/ollama/blob/main/docs/modelfile.md">here</a>.
     *
     * @param modelName     the name of the custom model to be created.
     * @param modelFilePath the path to model file that exists on the Ollama server.
     */
    public void createModelWithFilePath(String modelName, String modelFilePath)
            throws IOException, InterruptedException, OllamaBaseException, URISyntaxException {
        String url = this.host + "/api/create";
        String jsonData = new CustomModelFilePathRequest(modelName, modelFilePath).toString();
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setConnectTimeout((int) (requestTimeoutSeconds * 1000));
            connection.setDoOutput(true);
            try (OutputStream os = connection.getOutputStream()) {
                byte[] input = jsonData.getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }
            int statusCode = connection.getResponseCode();
            InputStream responseStream = (statusCode == 200) ? connection.getInputStream() : connection.getErrorStream();
            String responseString = readStream(responseStream);
            if (statusCode != 200) {
                throw new OllamaBaseException(statusCode + " - " + responseString);
            }
            if (responseString.contains("error")) {
                throw new OllamaBaseException(responseString);
            }
            if (verbose) {
                logger.info(responseString);
            }
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    /**
     * Create a custom model from a model file. Read more about custom model file creation <a
     * href="https://github.com/jmorganca/ollama/blob/main/docs/modelfile.md">here</a>.
     *
     * @param modelName         the name of the custom model to be created.
     * @param modelFileContents the path to model file that exists on the Ollama server.
     */
    public void createModelWithModelFileContents(String modelName, String modelFileContents)
            throws IOException, InterruptedException, OllamaBaseException, URISyntaxException {
        String url = this.host + "/api/create";
        String jsonData = new CustomModelFileContentsRequest(modelName, modelFileContents).toString();
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setConnectTimeout((int) (requestTimeoutSeconds * 1000));
            connection.setDoOutput(true);
            try (OutputStream os = connection.getOutputStream()) {
                byte[] input = jsonData.getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }
            int statusCode = connection.getResponseCode();
            InputStream responseStream = (statusCode == 200) ? connection.getInputStream() : connection.getErrorStream();
            String responseString = readStream(responseStream);
            if (statusCode != 200) {
                throw new OllamaBaseException(statusCode + " - " + responseString);
            }
            if (responseString.contains("error")) {
                throw new OllamaBaseException(responseString);
            }
            if (verbose) {
                logger.info(responseString);
            }
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    /**
     * Delete a model from Ollama server.
     *
     * @param modelName          the name of the model to be deleted.
     * @param ignoreIfNotPresent ignore errors if the specified model is not present on Ollama server.
     */
    public void deleteModel(String modelName, boolean ignoreIfNotPresent)
            throws IOException, InterruptedException, OllamaBaseException, URISyntaxException {
        String url = this.host + "/api/delete";
        String jsonData = new ModelRequest(modelName).toString();
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setRequestMethod("DELETE");
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setConnectTimeout((int) (requestTimeoutSeconds * 1000));
            connection.setDoOutput(true);
            try (OutputStream os = connection.getOutputStream()) {
                byte[] input = jsonData.getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }
            int statusCode = connection.getResponseCode();
            InputStream responseStream = (statusCode == 200) ? connection.getInputStream() : connection.getErrorStream();
            String responseBody = readStream(responseStream);
            if (statusCode == 404 && responseBody.contains("model") && responseBody.contains("not found")) {
                return;
            }
            if (statusCode != 200) {
                throw new OllamaBaseException(statusCode + " - " + responseBody);
            }
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    /**
     * Generate embeddings for a given text from a model
     *
     * @param model  name of model to generate embeddings from
     * @param prompt text to generate embeddings for
     * @return embeddings
     */
    public List<Double> generateEmbeddings(String model, String prompt)
            throws IOException, InterruptedException, OllamaBaseException {
        return generateEmbeddings(new OllamaEmbeddingsRequestModel(model, prompt));
    }

    /**
     * Generate embeddings using a {@link OllamaEmbeddingsRequestModel}.
     *
     * @param modelRequest request for '/api/embeddings' endpoint
     * @return embeddings
     */
    public List<Double> generateEmbeddings(OllamaEmbeddingsRequestModel modelRequest) throws IOException, InterruptedException, OllamaBaseException {
        URI uri = URI.create(this.host + "/api/embeddings");
        String jsonData = modelRequest.toString();
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(uri.toString()).openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setConnectTimeout((int) (requestTimeoutSeconds * 1000));
            connection.setDoOutput(true);
            try (OutputStream os = connection.getOutputStream()) {
                byte[] input = jsonData.getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }
            int statusCode = connection.getResponseCode();
            InputStream responseStream = (statusCode == 200 ? connection.getInputStream() : connection.getErrorStream());
            String responseBody = readStream(responseStream);
            if (statusCode == 200) {
                OllamaEmbeddingResponseModel embeddingResponse =
                        Utils.getObjectMapper().readValue(responseBody, OllamaEmbeddingResponseModel.class);
                return embeddingResponse.getEmbedding();
            } else {
                throw new OllamaBaseException(statusCode + " - " + responseBody);
            }
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    /**
     * Generate response for a question to a model running on Ollama server. This is a sync/blocking call.
     *
     * @param model         the ollama model to ask the question to
     * @param prompt        the prompt/question text
     * @param options       the Options object - <a href="https://github.com/jmorganca/ollama/blob/main/docs/modelfile.md#valid-parameters-and-values">More details on the options</a>
     * @param streamHandler optional callback consumer that will be applied every time a streamed response is received. If not set, the stream parameter of the request is set to false.
     * @return OllamaResult that includes response text and time taken for response
     */
    public OllamaResult generate(String model, String prompt, Options options, OllamaStreamHandler streamHandler)
            throws OllamaBaseException, IOException, InterruptedException {
        OllamaGenerateRequestModel ollamaRequestModel = new OllamaGenerateRequestModel(model, prompt);
        ollamaRequestModel.setOptions(options.getOptionsMap());
        return generateSyncForOllamaRequestModel(ollamaRequestModel, streamHandler);
    }

    /**
     * Convenience method to call Ollama API without streaming responses.
     * <p>
     * Uses {@link #generate(String, String, Options, OllamaStreamHandler)}
     */
    public OllamaResult generate(String model, String prompt, Options options)
            throws OllamaBaseException, IOException, InterruptedException {
        return generate(model, prompt, options, null);
    }

    /**
     * Generate response for a question to a model running on Ollama server and get a callback handle
     * that can be used to check for status and get the response from the model later. This would be
     * an async/non-blocking call.
     *
     * @param model  the ollama model to ask the question to
     * @param prompt the prompt/question text
     * @return the ollama async result callback handle
     */
    public OllamaAsyncResultCallback generateAsync(String model, String prompt) throws IOException {
        OllamaGenerateRequestModel ollamaRequestModel = new OllamaGenerateRequestModel(model, prompt);

        URI uri = URI.create(this.host + "/api/generate");
        OllamaAsyncResultCallback ollamaAsyncResultCallback =
                new OllamaAsyncResultCallback(
                        getRequestBuilderDefault(uri), ollamaRequestModel, requestTimeoutSeconds);
        ollamaAsyncResultCallback.start();
        return ollamaAsyncResultCallback;
    }

    /**
     * With one or more image files, ask a question to a model running on Ollama server. This is a sync/blocking call.
     *
     * @param model         the ollama model to ask the question to
     * @param prompt        the prompt/question text
     * @param imageFiles    the list of image files to use for the question
     * @param options       the Options object - <a href="https://github.com/jmorganca/ollama/blob/main/docs/modelfile.md#valid-parameters-and-values">More details on the options</a>
     * @param streamHandler optional callback consumer that will be applied every time a streamed response is received. If not set, the stream parameter of the request is set to false.
     * @return OllamaResult that includes response text and time taken for response
     */
    public OllamaResult generateWithImageFiles(
            String model, String prompt, List<File> imageFiles, Options options, OllamaStreamHandler streamHandler)
            throws OllamaBaseException, IOException, InterruptedException {
        List<String> images = new ArrayList<>();
        for (File imageFile : imageFiles) {
            images.add(encodeFileToBase64(imageFile));
        }
        OllamaGenerateRequestModel ollamaRequestModel = new OllamaGenerateRequestModel(model, prompt, images);
        ollamaRequestModel.setOptions(options.getOptionsMap());
        return generateSyncForOllamaRequestModel(ollamaRequestModel, streamHandler);
    }

    /**
     * Convenience method to call Ollama API without streaming responses.
     * <p>
     * Uses {@link #generateWithImageFiles(String, String, List, Options, OllamaStreamHandler)}
     */
    public OllamaResult generateWithImageFiles(
            String model, String prompt, List<File> imageFiles, Options options)
            throws OllamaBaseException, IOException, InterruptedException {
        return generateWithImageFiles(model, prompt, imageFiles, options, null);
    }

    /**
     * With one or more image URLs, ask a question to a model running on Ollama server. This is a sync/blocking call.
     *
     * @param model         the ollama model to ask the question to
     * @param prompt        the prompt/question text
     * @param imageURLs     the list of image URLs to use for the question
     * @param options       the Options object - <a href="https://github.com/jmorganca/ollama/blob/main/docs/modelfile.md#valid-parameters-and-values">More details on the options</a>
     * @param streamHandler optional callback consumer that will be applied every time a streamed response is received. If not set, the stream parameter of the request is set to false.
     * @return OllamaResult that includes response text and time taken for response
     */
    public OllamaResult generateWithImageURLs(
            String model, String prompt, List<String> imageURLs, Options options, OllamaStreamHandler streamHandler)
            throws OllamaBaseException, IOException, InterruptedException, URISyntaxException {
        List<String> images = new ArrayList<>();
        for (String imageURL : imageURLs) {
            images.add(encodeByteArrayToBase64(Utils.loadImageBytesFromUrl(imageURL)));
        }
        OllamaGenerateRequestModel ollamaRequestModel = new OllamaGenerateRequestModel(model, prompt, images);
        ollamaRequestModel.setOptions(options.getOptionsMap());
        return generateSyncForOllamaRequestModel(ollamaRequestModel, streamHandler);
    }

    /**
     * Convenience method to call Ollama API without streaming responses.
     * <p>
     * Uses {@link #generateWithImageURLs(String, String, List, Options, OllamaStreamHandler)}
     */
    public OllamaResult generateWithImageURLs(String model, String prompt, List<String> imageURLs,
                                              Options options)
            throws OllamaBaseException, IOException, InterruptedException, URISyntaxException {
        return generateWithImageURLs(model, prompt, imageURLs, options, null);
    }

    /**
     * Ask a question to a model based on a given message stack (i.e. a chat history). Creates a synchronous call to the api 'api/chat'.
     *
     * @param model    the ollama model to ask the question to
     * @param messages chat history / message stack to send to the model
     * @return {@link OllamaChatResult} containing the api response and the message history including the newly acquired assistant response.
     * @throws OllamaBaseException  any response code than 200 has been returned
     * @throws IOException          in case the responseStream can not be read
     * @throws InterruptedException in case the server is not reachable or network issues happen
     */
    public OllamaChatResult chat(String model, List<OllamaChatMessage> messages) throws OllamaBaseException, IOException, InterruptedException {
        OllamaChatRequestBuilder builder = OllamaChatRequestBuilder.getInstance(model);
        return chat(builder.withMessages(messages).build());
    }

    /**
     * Ask a question to a model using an {@link OllamaChatRequestModel}. This can be constructed using an {@link OllamaChatRequestBuilder}.
     * <p>
     * Hint: the OllamaChatRequestModel#getStream() property is not implemented.
     *
     * @param request request object to be sent to the server
     * @return {@link OllamaChatResult}
     * @throws OllamaBaseException  any response code than 200 has been returned
     * @throws IOException          in case the responseStream can not be read
     * @throws InterruptedException in case the server is not reachable or network issues happen
     */
    public OllamaChatResult chat(OllamaChatRequestModel request) throws OllamaBaseException, IOException, InterruptedException {
        return chat(request, null);
    }

    /**
     * Ask a question to a model using an {@link OllamaChatRequestModel}. This can be constructed using an {@link OllamaChatRequestBuilder}.
     * <p>
     * Hint: the OllamaChatRequestModel#getStream() property is not implemented.
     *
     * @param request       request object to be sent to the server
     * @param streamHandler callback handler to handle the last message from stream (caution: all previous messages from stream will be concatenated)
     * @return {@link OllamaChatResult}
     * @throws OllamaBaseException  any response code than 200 has been returned
     * @throws IOException          in case the responseStream can not be read
     * @throws InterruptedException in case the server is not reachable or network issues happen
     */
    public OllamaChatResult chat(OllamaChatRequestModel request, OllamaStreamHandler streamHandler) throws OllamaBaseException, IOException, InterruptedException {
        OllamaChatEndpointCaller requestCaller = new OllamaChatEndpointCaller(host, basicAuth, requestTimeoutSeconds, verbose);
        OllamaResult result;
        if (streamHandler != null) {
            request.setStream(true);
            result = requestCaller.call(request, streamHandler);
        } else {
            result = requestCaller.callSync(request);
        }
        return new OllamaChatResult(result.getResponse(), result.getResponseTime(), result.getHttpStatusCode(), request.getMessages());
    }

// technical private methods //

    private static String encodeFileToBase64(File file) throws IOException {
        return Base64.getEncoder().encodeToString(Files.readAllBytes(file.toPath()));
    }

    private static String encodeByteArrayToBase64(byte[] bytes) {
        return Base64.getEncoder().encodeToString(bytes);
    }

    private OllamaResult generateSyncForOllamaRequestModel(
            OllamaGenerateRequestModel ollamaRequestModel, OllamaStreamHandler streamHandler)
            throws OllamaBaseException, IOException, InterruptedException {
        OllamaGenerateEndpointCaller requestCaller =
                new OllamaGenerateEndpointCaller(host, basicAuth, requestTimeoutSeconds, verbose);
        OllamaResult result;
        if (streamHandler != null) {
            ollamaRequestModel.setStream(true);
            result = requestCaller.call(ollamaRequestModel, streamHandler);
        } else {
            result = requestCaller.callSync(ollamaRequestModel);
        }
        return result;
    }

    /**
     * Get default request builder.
     *
     * @param uri URI to get a HttpRequest.Builder
     * @return HttpURLConnection
     */
    private HttpURLConnection getRequestBuilderDefault(URI uri) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(uri.toString()).openConnection();
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setConnectTimeout((int) (requestTimeoutSeconds * 1000));
        if (isBasicAuthCredentialsSet()) {
            connection.setRequestProperty("Authorization", getBasicAuthHeaderValue());
        }
        return connection;
    }

    /**
     * Get basic authentication header value.
     *
     * @return basic authentication header value (encoded credentials)
     */
    private String getBasicAuthHeaderValue() {
        String credentialsToEncode = basicAuth.getUsername() + ":" + basicAuth.getPassword();
        return "Basic " + Base64.getEncoder().encodeToString(credentialsToEncode.getBytes());
    }

    /**
     * Check if Basic Auth credentials set.
     *
     * @return true when Basic Auth credentials set
     */
    private boolean isBasicAuthCredentialsSet() {
        return basicAuth != null;
    }

    /**
     * Read InputStream as String.
     *
     * @param stream InputStream to be read
     * @return String
     * @throws IOException in case the stream cannot be read
     */
    private static String readStream(InputStream stream) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
        StringBuilder result = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            result.append(line);
        }
        return result.toString();
    }
}