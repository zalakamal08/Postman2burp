package com.burpext.postmantoburp.logic;

import com.burpext.postmantoburp.model.CollectionNode;
import com.burpext.postmantoburp.model.RequestItem;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Parses a Postman Collection v2 / v2.1 JSON file into a tree of CollectionNodes.
 * Does NOT auto-send to Repeater — the user decides where to send each request.
 */
public class PostmanParser {

    private final EnvironmentManager envManager;

    public PostmanParser(EnvironmentManager envManager) {
        this.envManager = envManager;
    }

    /**
     * Parses the given Postman collection file and returns a root
     * CollectionNode (folder) containing all sub-folders and requests.
     */
    public CollectionNode parse(File file) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(file);

        // Extract collection-level variables into the live env map
        envManager.importFromPostmanCollection(root);

        // Collection name
        String collectionName = "Postman Collection";
        JsonNode info = root.get("info");
        if (info != null && info.has("name")) {
            collectionName = info.get("name").asText();
        }

        CollectionNode collectionRoot = new CollectionNode(collectionName);

        JsonNode items = root.get("item");
        if (items != null && items.isArray()) {
            processItems(items, collectionRoot);
        }

        return collectionRoot;
    }

    // ─── Recursive item processing ────────────────────────────────────────────

    private void processItems(JsonNode items, CollectionNode parentNode) {
        for (JsonNode item : items) {
            if (item.has("item")) {
                // It's a folder
                String folderName = item.has("name") ? item.get("name").asText() : "Folder";
                CollectionNode folderNode = new CollectionNode(folderName);
                processItems(item.get("item"), folderNode);
                parentNode.add(folderNode);
            } else {
                // It's a request
                RequestItem req = buildRequest(item);
                if (req != null) {
                    CollectionNode leaf = new CollectionNode(req.getName(), req);
                    parentNode.add(leaf);
                }
            }
        }
    }

    private RequestItem buildRequest(JsonNode item) {
        try {
            String name = item.has("name") ? item.get("name").asText() : "Unnamed Request";
            RequestItem req = new RequestItem(name);
            req.setSource("postman");

            JsonNode request = item.get("request");
            if (request == null) return null;

            // Method
            req.setMethod(request.has("method") ? request.get("method").asText() : "GET");

            // URL
            JsonNode urlNode = request.get("url");
            if (urlNode != null) {
                parseUrl(urlNode, req);
            }

            // Headers
            Map<String, String> headers = new LinkedHashMap<>();
            JsonNode headerArray = request.get("header");
            if (headerArray != null && headerArray.isArray()) {
                for (JsonNode h : headerArray) {
                    if (h.has("disabled") && h.get("disabled").asBoolean()) continue;
                    String key   = h.has("key")   ? h.get("key").asText()   : "";
                    String value = h.has("value") ? h.get("value").asText() : "";
                    if (!key.isEmpty()) headers.put(key, value);
                }
            }
            req.setHeaders(headers);

            // Auth — Bearer token
            if (request.has("auth")) {
                JsonNode auth = request.get("auth");
                String authType = auth.has("type") ? auth.get("type").asText().toLowerCase() : "none";
                if ("bearer".equals(authType) && auth.has("bearer") && auth.get("bearer").isArray()) {
                    for (JsonNode bearer : auth.get("bearer")) {
                        String tokenVal = bearer.has("value") ? bearer.get("value").asText() : "";
                        req.setAuthType("bearer");
                        req.setAuthValue(tokenVal);
                        break;
                    }
                } else if ("basic".equals(authType) && auth.has("basic") && auth.get("basic").isArray()) {
                    String user = "", pass = "";
                    for (JsonNode entry : auth.get("basic")) {
                        String k = entry.has("key") ? entry.get("key").asText() : "";
                        String v = entry.has("value") ? entry.get("value").asText() : "";
                        if ("username".equals(k)) user = v;
                        if ("password".equals(k)) pass = v;
                    }
                    req.setAuthType("basic");
                    req.setAuthValue(java.util.Base64.getEncoder().encodeToString((user + ":" + pass).getBytes()));
                } else {
                    req.setAuthType(authType);
                }
            }

            // Body
            JsonNode body = request.get("body");
            if (body != null) {
                String mode = body.has("mode") ? body.get("mode").asText() : "";
                switch (mode) {
                    case "raw" -> {
                        req.setBody(body.has("raw") ? body.get("raw").asText() : "");
                        // infer Content-Type from options
                        JsonNode opts = body.get("options");
                        if (opts != null && opts.has("raw")) {
                            String lang = opts.get("raw").has("language")
                                    ? opts.get("raw").get("language").asText() : "";
                            if ("json".equalsIgnoreCase(lang)) {
                                req.getHeaders().putIfAbsent("Content-Type", "application/json");
                            }
                        }
                    }
                    case "urlencoded" -> {
                        req.setBody(buildUrlEncoded(body.get("urlencoded")));
                        req.getHeaders().putIfAbsent("Content-Type", "application/x-www-form-urlencoded");
                    }
                    case "formdata" -> {
                        String boundary = "----BurpBoundary" + System.currentTimeMillis();
                        req.setBody(buildFormData(body.get("formdata"), boundary));
                        req.getHeaders().putIfAbsent("Content-Type", "multipart/form-data; boundary=" + boundary);
                    }
                    case "graphql" -> {
                        JsonNode gql = body.get("graphql");
                        if (gql != null) {
                            Map<String, Object> gqlMap = new LinkedHashMap<>();
                            if (gql.has("query"))     gqlMap.put("query", gql.get("query").asText());
                            if (gql.has("variables")) gqlMap.put("variables", gql.get("variables").asText());
                            req.setBody(new ObjectMapper().writeValueAsString(gqlMap));
                        }
                        req.getHeaders().putIfAbsent("Content-Type", "application/json");
                    }
                }
            }

            return req;

        } catch (Exception e) {
            return null;
        }
    }

    // ─── URL parsing ─────────────────────────────────────────────────────────

    private void parseUrl(JsonNode urlNode, RequestItem req) {
        if (urlNode.isTextual()) {
            // Simple string URL
            parseUrlString(urlNode.asText(), req);
            return;
        }

        // Object form
        String protocol = urlNode.has("protocol") ? urlNode.get("protocol").asText() : "https";
        req.setProtocol(protocol);

        // Host (array joined by ".")
        if (urlNode.has("host") && urlNode.get("host").isArray()) {
            StringBuilder hostBuilder = new StringBuilder();
            Iterator<JsonNode> parts = urlNode.get("host").elements();
            while (parts.hasNext()) {
                hostBuilder.append(parts.next().asText());
                if (parts.hasNext()) hostBuilder.append(".");
            }
            req.setHost(hostBuilder.toString());
        }

        // Port
        if (urlNode.has("port") && !urlNode.get("port").asText().isEmpty()) {
            try { req.setPort(Integer.parseInt(urlNode.get("port").asText())); }
            catch (NumberFormatException ignored) {}
        }

        // Path (array joined by "/")
        if (urlNode.has("path") && urlNode.get("path").isArray()) {
            StringBuilder pathBuilder = new StringBuilder();
            for (JsonNode seg : urlNode.get("path")) {
                String s = seg.isTextual() ? seg.asText() : (seg.has("value") ? seg.get("value").asText() : "");
                pathBuilder.append("/").append(s);
            }
            req.setPath(pathBuilder.length() > 0 ? pathBuilder.toString() : "/");
        }

        // Query params appended to path
        if (urlNode.has("query") && urlNode.get("query").isArray()) {
            StringBuilder query = new StringBuilder();
            for (JsonNode q : urlNode.get("query")) {
                if (q.has("disabled") && q.get("disabled").asBoolean()) continue;
                String k = q.has("key")   ? q.get("key").asText()   : "";
                String v = q.has("value") ? q.get("value").asText() : "";
                if (!k.isEmpty()) {
                    if (query.length() > 0) query.append("&");
                    query.append(k).append("=").append(v);
                }
            }
            if (query.length() > 0) {
                req.setPath(req.getPath() + "?" + query);
            }
        }
    }

    private void parseUrlString(String raw, RequestItem req) {
        try {
            java.net.URL url = new java.net.URL(raw);
            req.setProtocol(url.getProtocol());
            req.setHost(url.getHost());
            req.setPort(url.getPort() == -1 ? url.getDefaultPort() : url.getPort());
            String file = url.getFile();
            req.setPath(file.isEmpty() ? "/" : file);
        } catch (Exception ignored) {
            req.setHost(raw);
        }
    }

    // ─── Body helpers ─────────────────────────────────────────────────────────

    private String buildUrlEncoded(JsonNode node) {
        if (node == null || !node.isArray()) return "";
        StringBuilder sb = new StringBuilder();
        for (JsonNode p : node) {
            if (p.has("disabled") && p.get("disabled").asBoolean()) continue;
            String k = p.has("key")   ? p.get("key").asText()   : "";
            String v = p.has("value") ? p.get("value").asText() : "";
            if (sb.length() > 0) sb.append("&");
            sb.append(k).append("=").append(v);
        }
        return sb.toString();
    }

    private String buildFormData(JsonNode node, String boundary) {
        if (node == null || !node.isArray()) return "";
        StringBuilder sb = new StringBuilder();
        for (JsonNode p : node) {
            String key   = p.has("key")  ? p.get("key").asText()  : "";
            boolean isFile = p.has("type") && "file".equals(p.get("type").asText());
            String value = p.has("value") ? p.get("value").asText() : "";
            sb.append("--").append(boundary).append("\r\n");
            if (isFile) {
                String fname = p.has("src") ? p.get("src").asText() : "file";
                sb.append("Content-Disposition: form-data; name=\"").append(key)
                  .append("\"; filename=\"").append(fname).append("\"\r\n");
                sb.append("Content-Type: application/octet-stream\r\n\r\n");
                sb.append("<file content here>\r\n");
            } else {
                sb.append("Content-Disposition: form-data; name=\"").append(key).append("\"\r\n\r\n");
                sb.append(value).append("\r\n");
            }
        }
        sb.append("--").append(boundary).append("--\r\n");
        return sb.toString();
    }
}
