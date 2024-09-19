package com.burpext.postmantoburp;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.http.HttpService;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.repeater.Repeater;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class PostmanLogic {

    private MontoyaApi api;
    private Map<String, String> variablesMap = new HashMap<>();

    // Counters for different request types
    private int totalRequests = 0;
    private int getRequestCount = 0;
    private int postRequestCount = 0;
    private int putRequestCount = 0;
    private int deleteRequestCount = 0;
    private int errorRequestCount = 0;

    public PostmanLogic(MontoyaApi api) {
        this.api = api;
    }

    public void loadAndSendPostmanRequests(String filePath) {
        new Thread(() -> {
            try {
                // Parse the JSON collection using Jackson ObjectMapper
                ObjectMapper mapper = new ObjectMapper();
                JsonNode postmanCollection = mapper.readTree(new File(filePath));

                // Extract variables and store them in the map
                JsonNode variables = postmanCollection.get("variable");
                api.logging().logToOutput("Loaded Variables: \n");
                if (variables != null) {
                    for (JsonNode variable : variables) {
                        String key = variable.get("key").asText();
                        String value = variable.get("value").asText();
                        api.logging().logToOutput("" + variable);
                        variablesMap.put(key, value);
                    }
                }

                // Extract the "item" array (which contains requests)
                JsonNode items = postmanCollection.get("item");

                // Recursively process each item
                processItems(items);

                // Log final counts for requests
                api.logging().logToOutput("Total requests found: " + totalRequests);
                api.logging().logToOutput("GET requests: " + getRequestCount);
                api.logging().logToOutput("POST requests: " + postRequestCount);
                api.logging().logToOutput("PUT requests: " + putRequestCount);
                api.logging().logToOutput("DELETE requests: " + deleteRequestCount);
                api.logging().logToOutput("Requests with errors: " + errorRequestCount);

            } catch (IOException e) {
                api.logging().logToOutput("Error loading Postman collection: " + e.getMessage());
            }
        }).start();  // Start the processing in a new thread
    }

    private void processItems(JsonNode items) {
        for (JsonNode item : items) {
            // If there's a nested "item", recurse
            if (item.has("item")) {
                processItems(item.get("item"));
            } else {
                // Process the actual request node
                processRequest(item);
            }
        }
    }

    private void processRequest(JsonNode item) {
        try {
            // Extract request object
            JsonNode request = item.get("request");

            // Extract method and categorize it
            String method = request.get("method").asText();
            totalRequests++;
            switch (method.toUpperCase()) {
                case "GET":
                    getRequestCount++;
                    break;
                case "POST":
                    postRequestCount++;
                    break;
                case "PUT":
                    putRequestCount++;
                    break;
                case "DELETE":
                    deleteRequestCount++;
                    break;
                default:
                    api.logging().logToOutput("Unknown HTTP method: " + method);
            }

            // Extract and resolve URL components
            JsonNode urlObject = request.get("url");
             String protocol = urlObject.has("protocol") ? urlObject.get("protocol").asText() : "https";  // Extract protocol (http or https)
            String path = resolvePath(urlObject);  // Extract only the path
            String host = resolveHost(urlObject);  // Extract only the host

            // Determine port based on protocol
            int port = protocol.equalsIgnoreCase("http") ? 80 : 443;

            // Extract and resolve headers
            String headers = extractAndResolveHeaders(request, host);

            // Extract and resolve body
            String body = extractAndResolveBody(request);

            // Create HttpService based on the extracted host and dynamic port
            HttpService service = HttpService.httpService(host, port, protocol.equalsIgnoreCase("https"));

            // Create HttpRequest with the service and formatted request
            HttpRequest httpRequest = HttpRequest.httpRequest(service, method + " " + path + " HTTP/1.1\r\n" +
                    headers + "\r\n" +
                    (body != null ? body + "\r\n" : ""));

            // Send the request to Repeater
            sendRequestToRepeater(httpRequest, item.get("name").asText());

        } catch (Exception e) {
            // Increment error counter and log the error
            errorRequestCount++;
            api.logging().logToOutput("Error processing request: " + e.getMessage());
        }
    }

    // Extract only the path and join with "/"
    private String resolvePath(JsonNode urlObject) {
        StringBuilder pathBuilder = new StringBuilder();

        // Extract and resolve path (this will build the URL path, not the full URL)
        if (urlObject.has("path") && urlObject.get("path").isArray()) {
            Iterator<JsonNode> pathParts = urlObject.get("path").elements();
            while (pathParts.hasNext()) {
                pathBuilder.append("/").append(replaceVariables(pathParts.next().asText()));
            }
        }

        return pathBuilder.toString();  // Return the path part only
    }

    // Extract only the host and join with "."
    private String resolveHost(JsonNode urlObject) {
        StringBuilder hostBuilder = new StringBuilder();

        // Extract and resolve host (e.g., www.googleapis.com)
        if (urlObject.has("host") && urlObject.get("host").isArray()) {
            Iterator<JsonNode> hostParts = urlObject.get("host").elements();
            while (hostParts.hasNext()) {
                hostBuilder.append(replaceVariables(hostParts.next().asText()));
                if (hostParts.hasNext()) {
                    hostBuilder.append(".");
                }
            }
        }

        return hostBuilder.toString();  // Return the host part only
    }

private String extractAndResolveHeaders(JsonNode request, String host) {
    StringBuilder headersBuilder = new StringBuilder();

    // Add Host header
    headersBuilder.append("Host: ").append(host).append("\r\n");

    // Extract and resolve other headers
    JsonNode headers = request.get("header");
    if (headers != null && headers.isArray()) {
        for (JsonNode header : headers) {
            // Check if the header is disabled
            if (header.has("disabled") && header.get("disabled").asBoolean()) {
                continue;  // Skip the disabled header
            }

            String key = header.get("key").asText();
            String value = replaceVariables(header.get("value").asText());

            if (!key.isEmpty() && !value.isEmpty()) {
                headersBuilder.append(key).append(": ").append(value).append("\r\n");
            }
        }
    }

    return headersBuilder.toString();
}

    private String extractAndResolveBody(JsonNode request) {
        JsonNode body = request.get("body");
        if (body != null && body.has("raw")) {
            String rawBody = body.get("raw").asText();
            return replaceVariables(rawBody);
        }
        return null;
    }

    private String replaceVariables(String input) {
        if (input == null) return "";
        // Replace all instances of {{variable}} with corresponding values
        for (Map.Entry<String, String> entry : variablesMap.entrySet()) {
            String variablePlaceholder = "{{" + entry.getKey() + "}}";
            input = input.replace(variablePlaceholder, entry.getValue());
        }
        return input;
    }

    private void sendRequestToRepeater(HttpRequest request, String tabName) {
        try {
            Repeater repeater = api.repeater();

            if (repeater != null) {
                repeater.sendToRepeater(request, tabName);
                api.logging().logToOutput("Request '" + tabName + "' sent to Repeater.");
            } else {
                api.logging().logToOutput("Repeater instance is not available.");
            }
        } catch (Exception e) {
            api.logging().logToOutput("Error sending request to Repeater: " + e.getMessage());
        }
    }
}
