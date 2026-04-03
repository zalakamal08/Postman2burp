package com.burpext.postmantoburp.model;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Unified request model. Populated by PostmanParser, CurlParser, or OpenApiParser.
 * Variables ({{name}}) are stored as-is and resolved at send-time by EnvironmentManager.
 */
public class RequestItem {

    private String name;
    private String method = "GET";
    private String protocol = "https";
    private String host = "";
    private int port = 443;
    private String path = "/";
    private Map<String, String> headers = new LinkedHashMap<>();
    private String body = "";
    private String authType = "none";   // none | bearer | basic
    private String authValue = "";      // raw token or Base64(user:pass)
    private String source = "postman";  // postman | curl | openapi

    public RequestItem(String name) {
        this.name = name;
    }

    // ─── Name ────────────────────────────────────────────────────────────────
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    // ─── Method ──────────────────────────────────────────────────────────────
    public String getMethod() { return method; }
    public void setMethod(String method) {
        this.method = (method != null) ? method.toUpperCase().trim() : "GET";
    }

    // ─── Protocol ────────────────────────────────────────────────────────────
    public String getProtocol() { return protocol; }
    public void setProtocol(String protocol) {
        this.protocol = (protocol != null) ? protocol.toLowerCase().trim() : "https";
        // sync default port only when port is still at its default value
        if (this.port == 443 || this.port == 80) {
            this.port = "http".equals(this.protocol) ? 80 : 443;
        }
    }

    // ─── Host ────────────────────────────────────────────────────────────────
    public String getHost() { return host; }
    public void setHost(String host) { this.host = host != null ? host.trim() : ""; }

    // ─── Port ────────────────────────────────────────────────────────────────
    public int getPort() { return port; }
    public void setPort(int port) { this.port = port; }
    public boolean isSecure() { return "https".equalsIgnoreCase(protocol); }

    // ─── Path ────────────────────────────────────────────────────────────────
    public String getPath() { return path; }
    public void setPath(String path) {
        if (path == null || path.trim().isEmpty()) {
            this.path = "/";
        } else {
            this.path = path.trim().startsWith("/") ? path.trim() : "/" + path.trim();
        }
    }

    // ─── Headers ─────────────────────────────────────────────────────────────
    public Map<String, String> getHeaders() { return headers; }
    public void setHeaders(Map<String, String> headers) {
        this.headers = (headers != null) ? headers : new LinkedHashMap<>();
    }
    public void addHeader(String key, String value) {
        if (key != null && !key.trim().isEmpty()) {
            headers.put(key.trim(), value != null ? value : "");
        }
    }

    // ─── Body ────────────────────────────────────────────────────────────────
    public String getBody() { return body; }
    public void setBody(String body) { this.body = body != null ? body : ""; }

    // ─── Auth ────────────────────────────────────────────────────────────────
    public String getAuthType() { return authType; }
    public void setAuthType(String authType) {
        this.authType = (authType != null) ? authType.toLowerCase().trim() : "none";
    }

    public String getAuthValue() { return authValue; }
    public void setAuthValue(String authValue) {
        this.authValue = authValue != null ? authValue : "";
    }

    // ─── Source ──────────────────────────────────────────────────────────────
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    /** Returns the full display URL (without variable resolution). */
    public String getFullUrl() {
        boolean nonDefaultPort = ("https".equalsIgnoreCase(protocol) && port != 443)
                || ("http".equalsIgnoreCase(protocol) && port != 80);
        String portPart = nonDefaultPort ? ":" + port : "";
        return protocol + "://" + host + portPart + path;
    }

    @Override
    public String toString() { return name; }
}
