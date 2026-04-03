package com.burpext.postmantoburp.logic;

import com.burpext.postmantoburp.model.CollectionNode;
import com.burpext.postmantoburp.model.RequestItem;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Parses an OpenAPI v3 specification (JSON or YAML) from a file or a remote URL
 * into a tree of CollectionNodes grouped by the first path segment (tag/resource).
 *
 * Supports:
 *   - File import (.json / .yaml / .yml)
 *   - Remote URL download (http/https)
 *   - path parameters ({id} → :id in display)
 *   - requestBody example extraction for POST/PUT/PATCH
 *   - query parameter injection for GET requests
 */
public class OpenApiParser {

    // ─── Entry points ─────────────────────────────────────────────────────────

    public CollectionNode parseFile(File file) throws IOException {
        boolean yaml = file.getName().toLowerCase().endsWith(".yaml")
                    || file.getName().toLowerCase().endsWith(".yml");
        ObjectMapper mapper = yaml ? new ObjectMapper(new YAMLFactory()) : new ObjectMapper();
        JsonNode root = mapper.readTree(file);
        return parseRoot(root, file.getName());
    }

    public CollectionNode parseUrl(String urlStr) throws IOException {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Accept", "application/json, application/x-yaml, text/yaml, */*");
        conn.setConnectTimeout(10_000);
        conn.setReadTimeout(15_000);
        conn.connect();

        int code = conn.getResponseCode();
        if (code < 200 || code >= 300) {
            throw new IOException("Server returned HTTP " + code + " for: " + urlStr);
        }

        String contentType = conn.getContentType() != null ? conn.getContentType() : "";
        boolean yaml = contentType.contains("yaml") || urlStr.toLowerCase().endsWith(".yaml")
                    || urlStr.toLowerCase().endsWith(".yml");

        ObjectMapper mapper = yaml ? new ObjectMapper(new YAMLFactory()) : new ObjectMapper();
        try (InputStream is = conn.getInputStream()) {
            JsonNode root = mapper.readTree(is);
            return parseRoot(root, urlStr);
        }
    }

    // ─── Core parsing ─────────────────────────────────────────────────────────

    private CollectionNode parseRoot(JsonNode root, String sourceName) {
        // Collection name
        String title = "OpenAPI Collection";
        JsonNode info = root.get("info");
        if (info != null && info.has("title")) {
            title = info.get("title").asText();
            String version = info.has("version") ? info.get("version").asText() : null;
            if (version != null) title += " (" + version + ")";
        }

        // Server base URL
        String protocol = "https";
        String serverHost = "localhost";
        int serverPort = 443;
        String basePath = "";

        JsonNode servers = root.get("servers");
        if (servers != null && servers.isArray() && servers.size() > 0) {
            String serverUrl = servers.get(0).has("url") ? servers.get(0).get("url").asText() : "";
            try {
                URL parsed = new URL(serverUrl);
                protocol   = parsed.getProtocol();
                serverHost = parsed.getHost();
                serverPort = parsed.getPort() == -1 ? parsed.getDefaultPort() : parsed.getPort();
                basePath   = parsed.getPath();
                if (basePath.endsWith("/")) basePath = basePath.substring(0, basePath.length() - 1);
            } catch (Exception ignored) {}
        }

        CollectionNode rootNode = new CollectionNode(title);

        // Folder map: first path segment → folder node
        Map<String, CollectionNode> folders = new LinkedHashMap<>();

        JsonNode paths = root.get("paths");
        if (paths == null) return rootNode;

        Iterator<Map.Entry<String, JsonNode>> pathIter = paths.fields();
        while (pathIter.hasNext()) {
            Map.Entry<String, JsonNode> pathEntry = pathIter.next();
            String apiPath = pathEntry.getKey();   // e.g.  /users/{id}
            JsonNode pathItem = pathEntry.getValue();

            // Determine which folder this path belongs to
            String folderName = getFolderName(apiPath);
            CollectionNode folder = folders.computeIfAbsent(folderName, n -> {
                CollectionNode fn = new CollectionNode(n);
                rootNode.add(fn);
                return fn;
            });

            // One CollectionNode per HTTP method
            String[] httpMethods = {"get", "post", "put", "delete", "patch", "head", "options"};
            for (String method : httpMethods) {
                if (!pathItem.has(method)) continue;
                JsonNode operation = pathItem.get(method);

                RequestItem req = buildRequest(
                        operation, method.toUpperCase(), apiPath,
                        protocol, serverHost, serverPort, basePath
                );
                folder.add(new CollectionNode(req.getName(), req));
            }
        }

        return rootNode;
    }

    // ─── Request builder ──────────────────────────────────────────────────────

    private RequestItem buildRequest(
            JsonNode op, String method, String apiPath,
            String protocol, String host, int port, String basePath) {

        // Name: operationId or "METHOD /path"
        String opId = op.has("operationId") ? op.get("operationId").asText()
                : (op.has("summary") ? op.get("summary").asText() : method + " " + apiPath);

        RequestItem req = new RequestItem(opId);
        req.setSource("openapi");
        req.setMethod(method);
        req.setProtocol(protocol);
        req.setHost(host);
        req.setPort(port);

        // Build path with query string from GET parameters
        String fullPath = basePath + apiPath;  // e.g. /v1/users/{id}
        StringBuilder queryString = new StringBuilder();

        if (op.has("parameters") && op.get("parameters").isArray()) {
            for (JsonNode param : op.get("parameters")) {
                String in   = param.has("in")   ? param.get("in").asText()   : "";
                String name = param.has("name") ? param.get("name").asText() : "";
                if ("query".equals(in) && !name.isEmpty()) {
                    String example = extractExample(param);
                    if (queryString.length() > 0) queryString.append("&");
                    queryString.append(name).append("=").append(example.isEmpty() ? "value" : example);
                }
                if ("header".equals(in) && !name.isEmpty()) {
                    String example = extractExample(param);
                    req.addHeader(name, example.isEmpty() ? "value" : example);
                }
            }
        }

        req.setPath(fullPath + (queryString.length() > 0 ? "?" + queryString : ""));

        // Default headers
        req.addHeader("Accept", "application/json");

        // Request body
        if (op.has("requestBody")) {
            JsonNode rb = op.get("requestBody");
            JsonNode content = rb.get("content");
            if (content != null) {
                // Prefer application/json
                String[] preferredTypes = {"application/json", "application/x-www-form-urlencoded",
                        "multipart/form-data", "text/plain"};
                for (String ct : preferredTypes) {
                    if (content.has(ct)) {
                        req.addHeader("Content-Type", ct);
                        JsonNode mediaType = content.get(ct);
                        // Try to extract example
                        String body = extractBodyExample(mediaType);
                        req.setBody(body);
                        break;
                    }
                }
            }
        }

        return req;
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    /** Returns the first meaningful path segment to use as a folder name. */
    private String getFolderName(String apiPath) {
        if (apiPath == null || apiPath.isEmpty() || apiPath.equals("/")) return "root";
        String[] parts = apiPath.split("/");
        for (String part : parts) {
            if (!part.isEmpty() && !part.startsWith("{")) return part;
        }
        return "endpoints";
    }

    /** Extracts an example value from a parameter node. */
    private String extractExample(JsonNode param) {
        if (param.has("example")) return param.get("example").asText();
        if (param.has("examples") && param.get("examples").isObject()) {
            Iterator<JsonNode> examples = param.get("examples").elements();
            if (examples.hasNext()) {
                JsonNode ex = examples.next();
                if (ex.has("value")) return ex.get("value").asText();
            }
        }
        if (param.has("schema")) {
            JsonNode schema = param.get("schema");
            if (schema.has("example")) return schema.get("example").asText();
            if (schema.has("default")) return schema.get("default").asText();
        }
        return "";
    }

    /** Tries to extract a body example from an OpenAPI mediaType node. */
    private String extractBodyExample(JsonNode mediaType) {
        try {
            ObjectMapper mapper = new ObjectMapper();

            if (mediaType.has("example")) {
                JsonNode ex = mediaType.get("example");
                return ex.isTextual() ? ex.asText() : mapper.writerWithDefaultPrettyPrinter().writeValueAsString(ex);
            }

            if (mediaType.has("examples") && mediaType.get("examples").isObject()) {
                Iterator<JsonNode> examples = mediaType.get("examples").elements();
                if (examples.hasNext()) {
                    JsonNode ex = examples.next();
                    if (ex.has("value")) {
                        JsonNode val = ex.get("value");
                        return val.isTextual() ? val.asText() : mapper.writerWithDefaultPrettyPrinter().writeValueAsString(val);
                    }
                }
            }

            // Generate skeleton from schema
            if (mediaType.has("schema")) {
                JsonNode schema = mediaType.get("schema");
                Object skeleton = generateSkeleton(schema);
                return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(skeleton);
            }
        } catch (Exception ignored) {}
        return "";
    }

    /** Generates a simple skeleton JSON object from an OpenAPI schema. */
    private Object generateSkeleton(JsonNode schema) {
        if (schema == null) return Map.of();
        String type = schema.has("type") ? schema.get("type").asText() : "object";

        return switch (type) {
            case "object" -> {
                Map<String, Object> obj = new LinkedHashMap<>();
                JsonNode props = schema.get("properties");
                if (props != null) {
                    Iterator<Map.Entry<String, JsonNode>> fields = props.fields();
                    while (fields.hasNext()) {
                        Map.Entry<String, JsonNode> field = fields.next();
                        obj.put(field.getKey(), generateSkeleton(field.getValue()));
                    }
                }
                yield obj;
            }
            case "array"   -> new Object[]{ generateSkeleton(schema.get("items")) };
            case "integer", "number" -> {
                if (schema.has("example")) yield schema.get("example").asInt();
                yield 0;
            }
            case "boolean" -> false;
            case "string"  -> {
                if (schema.has("example"))  yield schema.get("example").asText();
                if (schema.has("format"))   yield "<" + schema.get("format").asText() + ">";
                if (schema.has("enum") && schema.get("enum").isArray())
                    yield schema.get("enum").get(0).asText();
                yield "string";
            }
            default -> null;
        };
    }
}
