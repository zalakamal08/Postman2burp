package com.burpext.postmantoburp.logic;

import com.burpext.postmantoburp.model.RequestItem;

import java.net.URL;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Parses a raw cURL command string into a RequestItem.
 *
 * Supported flags:
 *   -X / --request          HTTP method
 *   -H / --header           Request header (key: value)
 *   -d / --data / --data-raw / --data-binary / --data-urlencode  Request body
 *   -u / --user             Basic auth (user:pass → Base64)
 *   -b / --cookie           Cookie header
 *   --url                   URL (alternate form)
 *   -k / --insecure         Ignored (noted in host)
 *   -G / --get              Forces GET method
 *   First non-flag argument = URL
 */
public class CurlParser {

    public RequestItem parse(String curlCommand) throws IllegalArgumentException {
        if (curlCommand == null || curlCommand.isBlank()) {
            throw new IllegalArgumentException("Empty cURL command");
        }

        // Normalize: strip leading "curl" keyword variants, remove line continuations
        String normalized = curlCommand.trim()
                .replaceAll("(?m)\\\\[\\r\\n]+\\s*", " ")   // backslash line continuations
                .replaceAll("[\\r\\n]+", " ");               // stray newlines

        // Strip leading "curl" token
        if (normalized.toLowerCase().startsWith("curl ")) {
            normalized = normalized.substring(5).trim();
        } else if (normalized.toLowerCase().equals("curl")) {
            throw new IllegalArgumentException("cURL command has no arguments");
        }

        List<String> tokens = tokenize(normalized);

        RequestItem req = new RequestItem("Imported cURL Request");
        req.setSource("curl");

        String explicitMethod = null;
        boolean hasBody = false;
        boolean forceGet = false;

        for (int i = 0; i < tokens.size(); i++) {
            String t = tokens.get(i);

            // ── URL (non-flag, or --url) ─────────────────────────────────────
            if (!t.startsWith("-")) {
                applyUrl(t, req);
                continue;
            }

            // ── Flags ────────────────────────────────────────────────────────
            switch (t) {
                case "--url" -> {
                    if (i + 1 < tokens.size()) applyUrl(tokens.get(++i), req);
                }
                case "-X", "--request" -> {
                    if (i + 1 < tokens.size()) explicitMethod = tokens.get(++i).toUpperCase();
                }
                case "-G", "--get" -> forceGet = true;
                case "-H", "--header" -> {
                    if (i + 1 < tokens.size()) {
                        parseHeader(tokens.get(++i), req);
                    }
                }
                case "-d", "--data", "--data-raw", "--data-ascii" -> {
                    if (i + 1 < tokens.size()) {
                        req.setBody(tokens.get(++i));
                        req.getHeaders().putIfAbsent("Content-Type", "application/x-www-form-urlencoded");
                        hasBody = true;
                    }
                }
                case "--data-binary" -> {
                    if (i + 1 < tokens.size()) {
                        String val = tokens.get(++i);
                        if (val.startsWith("@")) val = "<binary file: " + val.substring(1) + ">";
                        req.setBody(val);
                        hasBody = true;
                    }
                }
                case "--data-urlencode" -> {
                    if (i + 1 < tokens.size()) {
                        req.setBody(tokens.get(++i));
                        req.getHeaders().putIfAbsent("Content-Type", "application/x-www-form-urlencoded");
                        hasBody = true;
                    }
                }
                case "-u", "--user" -> {
                    if (i + 1 < tokens.size()) {
                        String creds = tokens.get(++i);
                        req.setAuthType("basic");
                        req.setAuthValue(Base64.getEncoder().encodeToString(creds.getBytes()));
                    }
                }
                case "-b", "--cookie" -> {
                    if (i + 1 < tokens.size()) {
                        req.getHeaders().put("Cookie", tokens.get(++i));
                    }
                }
                case "-A", "--user-agent" -> {
                    if (i + 1 < tokens.size()) {
                        req.getHeaders().put("User-Agent", tokens.get(++i));
                    }
                }
                case "--compressed" -> {
                    req.getHeaders().putIfAbsent("Accept-Encoding", "gzip, deflate, br");
                }
                case "-k", "--insecure" -> { /* noted but not enforced */ }
                case "-L", "--location" -> { /* follow redirects — noted */ }
                case "-s", "--silent", "-v", "--verbose", "-i", "--include",
                     "-o", "--output", "-w", "--write-out", "--max-time",
                     "-m", "--connect-timeout" -> {
                    // skip value for flags that take a value argument
                    if (i + 1 < tokens.size() && !tokens.get(i + 1).startsWith("-")) i++;
                }
                default -> {
                    // Handle compact forms like -XPOST or -H"Key: val"
                    if (t.startsWith("-X")) {
                        explicitMethod = t.substring(2).toUpperCase();
                    } else if (t.startsWith("-H") && t.length() > 2) {
                        parseHeader(t.substring(2), req);
                    }
                    // else: unknown flag, skip
                }
            }
        }

        // Resolve method
        if (forceGet) {
            req.setMethod("GET");
        } else if (explicitMethod != null) {
            req.setMethod(explicitMethod);
        } else if (hasBody) {
            req.setMethod("POST");
        } else {
            req.setMethod("GET");
        }

        // Set name from URL path
        if (!req.getPath().equals("/")) {
            String[] parts = req.getPath().split("/");
            req.setName(req.getMethod() + " " + (parts.length > 0 ? "/" + parts[parts.length - 1] : req.getPath()));
        } else {
            req.setName(req.getMethod() + " " + req.getHost());
        }

        return req;
    }

    // ─── URL Parsing ──────────────────────────────────────────────────────────

    private void applyUrl(String raw, RequestItem req) {
        // Strip query string into path
        try {
            // Add scheme if missing
            if (!raw.toLowerCase().startsWith("http://") && !raw.toLowerCase().startsWith("https://")) {
                raw = "https://" + raw;
            }
            URL url = new URL(raw);
            req.setProtocol(url.getProtocol());
            req.setHost(url.getHost());
            int port = url.getPort();
            req.setPort(port == -1 ? url.getDefaultPort() : port);
            String path = url.getPath();
            String query = url.getQuery();
            req.setPath(path.isEmpty() ? "/" : path + (query != null ? "?" + query : ""));
        } catch (Exception e) {
            req.setHost(raw); // fallback
        }
    }

    // ─── Header Parsing ───────────────────────────────────────────────────────

    private void parseHeader(String raw, RequestItem req) {
        int colon = raw.indexOf(':');
        if (colon == -1) return;
        String key   = raw.substring(0, colon).trim();
        String value = raw.substring(colon + 1).trim();

        // If it's an Authorization: Bearer header, also set auth fields
        if ("authorization".equalsIgnoreCase(key)) {
            if (value.toLowerCase().startsWith("bearer ")) {
                req.setAuthType("bearer");
                req.setAuthValue(value.substring(7).trim());
            } else if (value.toLowerCase().startsWith("basic ")) {
                req.setAuthType("basic");
                req.setAuthValue(value.substring(6).trim());
            }
        }
        req.addHeader(key, value);
    }

    // ─── Tokenizer ────────────────────────────────────────────────────────────

    /**
     * Shell-like tokenizer: respects single quotes (literal), double quotes
     * (with backslash escapes), and whitespace delimiters.
     */
    private List<String> tokenize(String input) {
        List<String> tokens = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inSingle = false;
        boolean inDouble = false;

        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);

            if (inSingle) {
                if (c == '\'') inSingle = false;
                else current.append(c);

            } else if (inDouble) {
                if (c == '\\' && i + 1 < input.length()) {
                    char next = input.charAt(i + 1);
                    switch (next) {
                        case '"', '\\', '$', '`' -> { current.append(next); i++; }
                        case 'n' -> { current.append('\n'); i++; }
                        case 't' -> { current.append('\t'); i++; }
                        default  -> current.append(c);
                    }
                } else if (c == '"') {
                    inDouble = false;
                } else {
                    current.append(c);
                }

            } else {
                switch (c) {
                    case '\'' -> inSingle = true;
                    case '"'  -> inDouble = true;
                    default -> {
                        if (Character.isWhitespace(c)) {
                            if (!current.isEmpty()) {
                                tokens.add(current.toString());
                                current = new StringBuilder();
                            }
                        } else {
                            current.append(c);
                        }
                    }
                }
            }
        }

        if (!current.isEmpty()) tokens.add(current.toString());
        return tokens;
    }
}
