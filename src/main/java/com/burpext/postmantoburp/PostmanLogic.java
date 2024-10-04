package com.burpext.postmantoburp;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.http.HttpService;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.repeater.Repeater;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.IOException;
import java.util.*;

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
            String protocol = urlObject.has("protocol") ? urlObject.get("protocol").asText() : "https";  // Default to https
            String path = resolvePath(urlObject);
            String host = resolveHost(urlObject);
            int port = protocol.equalsIgnoreCase("http") ? 80 : 443;

            // Extract and resolve headers
            Map<String, String> headersMap = extractAndResolveHeaders(request, host);

            // **Newly Added: Handle Authorization Header**
            addAuthorizationHeader(request, headersMap);

            // Extract and resolve body
            String body = extractAndResolveBody(request, headersMap);

            // Build the request headers string
            String headers = buildHeadersString(headersMap);

            // Create HttpService
            HttpService service = HttpService.httpService(host, port, protocol.equalsIgnoreCase("https"));

            // Create HttpRequest with the service and formatted request
            HttpRequest httpRequest = HttpRequest.httpRequest(service, method + " " + path + " HTTP/1.1\r\n" +
                    headers + "\r\n" +
                    (body != null ? body : ""));

            // Send the request to Repeater
            sendRequestToRepeater(httpRequest, item.get("name").asText());

        } catch (Exception e) {
            errorRequestCount++;
            api.logging().logToOutput("Error processing request: " + e.getMessage());
        }
    }

    // **New Method to Add Authorization Header**
    private void addAuthorizationHeader(JsonNode request, Map<String, String> headersMap) {
        if (request.has("auth")) {
            JsonNode auth = request.get("auth");
            if (auth.has("type") && "bearer".equalsIgnoreCase(auth.get("type").asText())) {
                if (auth.has("bearer") && auth.get("bearer").isArray()) {
                    for (JsonNode bearer : auth.get("bearer")) {
                        String tokenValue = replaceVariables(bearer.get("value").asText());
                        headersMap.put("Authorization", "Bearer " + tokenValue);
                    }
                }
            }
        }
    }

    // Extract only the path and join with "/"
    private String resolvePath(JsonNode urlObject) {
        StringBuilder pathBuilder = new StringBuilder();

        if (urlObject.has("path")) {
            for (JsonNode segment : urlObject.get("path")) {
                pathBuilder.append("/").append(segment.asText());
            }
        }
        return pathBuilder.toString();
    }

  
    // Extract only the host and join with "."
    private String resolveHost(JsonNode urlObject) {
        StringBuilder hostBuilder = new StringBuilder();

        if (urlObject.has("host") && urlObject.get("host").isArray()) {
            Iterator<JsonNode> hostParts = urlObject.get("host").elements();
            while (hostParts.hasNext()) {
                hostBuilder.append(replaceVariables(hostParts.next().asText()));
                if (hostParts.hasNext()) {
                    hostBuilder.append(".");
                }
            }
        }

        return hostBuilder.toString();
    }

    private Map<String, String> extractAndResolveHeaders(JsonNode request, String host) {
        Map<String, String> headersMap = new LinkedHashMap<>();

        // Add Host header
        headersMap.put("Host", host);

        // Extract and resolve other headers
        JsonNode headers = request.get("header");
        if (headers != null && headers.isArray()) {
            for (JsonNode header : headers) {
                if (header.has("disabled") && header.get("disabled").asBoolean()) {
                    continue;
                }

                String key = header.get("key").asText();
                String value = replaceVariables(header.get("value").asText());

                if (!key.isEmpty() && !value.isEmpty()) {
                    headersMap.put(key, value);
                }
            }
        }

        return headersMap;
    }

    private String buildHeadersString(Map<String, String> headersMap) {
        StringBuilder headersBuilder = new StringBuilder();
        for (Map.Entry<String, String> header : headersMap.entrySet()) {
            headersBuilder.append(header.getKey()).append(": ").append(header.getValue()).append("\r\n");
        }
        return headersBuilder.toString();
    }

    private String extractAndResolveBody(JsonNode request, Map<String, String> headersMap) {
        JsonNode body = request.get("body");
        if (body != null) {
            String mode = body.has("mode") ? body.get("mode").asText() : "";

            switch (mode) {
                case "raw":
                    String rawBody = body.get("raw").asText();
                    return replaceVariables(rawBody);
                case "urlencoded":
                    headersMap.putIfAbsent("Content-Type", "application/x-www-form-urlencoded");
                    return extractUrlEncodedBody(body.get("urlencoded"));
                case "formdata":
                    String boundary = "----BurpBoundary" + System.currentTimeMillis();
                    headersMap.putIfAbsent("Content-Type", "multipart/form-data; boundary=" + boundary);
                    return extractFormDataBody(body.get("formdata"), boundary);
                default:
                    api.logging().logToOutput("Unsupported body mode: " + mode);
            }
        }
        return null;
    }

    // Handles extracting and formatting URL-encoded body data
    private String extractUrlEncodedBody(JsonNode urlEncodedBody) {
        StringBuilder bodyBuilder = new StringBuilder();
        if (urlEncodedBody != null && urlEncodedBody.isArray()) {
            for (JsonNode param : urlEncodedBody) {
                String key = param.get("key").asText();
                String value = replaceVariables(param.get("value").asText());
                if (bodyBuilder.length() > 0) {
                    bodyBuilder.append("&"); // URL-encoded separator
                }
                bodyBuilder.append(key).append("=").append(value);
            }
        }
//         api.logging().logToOutput(bodyBuilder.toString());
        return bodyBuilder.toString();
    }

    // Handles extracting and formatting form-data body
    private String extractFormDataBody(JsonNode formData, String boundary) {
        StringBuilder bodyBuilder = new StringBuilder();
        if (formData != null && formData.isArray()) {
            for (JsonNode param : formData) {
                String key = param.get("key").asText();
                boolean isFile = param.has("type") && param.get("type").asText().equals("file");
                String value = param.has("value") ? replaceVariables(param.get("value").asText()) : "";

                bodyBuilder.append("--").append(boundary).append("\r\n");
                if (isFile) {
                    // Handle file upload (Note: Actual file content handling would require file reading)
                    String filename = param.has("src") ? param.get("src").asText() : "filename";
                    bodyBuilder.append("Content-Disposition: form-data; name=\"").append(key)
                            .append("\"; filename=\"").append(filename).append("\"\r\n");
                    bodyBuilder.append("Content-Type: application/octet-stream\r\n\r\n");
                    bodyBuilder.append("File content goes here").append("\r\n"); // Placeholder
                } else {
                    bodyBuilder.append("Content-Disposition: form-data; name=\"").append(key).append("\"\r\n\r\n");
                    bodyBuilder.append(value).append("\r\n");
                }
            }
            bodyBuilder.append("--").append(boundary).append("--").append("\r\n");
        }
        return bodyBuilder.toString();
    }


    private String replaceVariables(String input) {
        if (input == null) return "";
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
