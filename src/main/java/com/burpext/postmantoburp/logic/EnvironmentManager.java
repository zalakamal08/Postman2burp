package com.burpext.postmantoburp.logic;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Holds the live variable map for the current session.
 * Variables are resolved at send-time — never at import-time —
 * so users can change {{baseUrl}} after importing collections.
 */
public class EnvironmentManager {

    private static final Pattern VAR_PATTERN = Pattern.compile("\\{\\{([^}]+)}}");

    private final Map<String, String> variables = new LinkedHashMap<>();

    // ─── Resolution ──────────────────────────────────────────────────────────

    /**
     * Replaces all {{varName}} occurrences in {@code input} with live values.
     * Unknown variables are left as-is (not replaced).
     */
    public String resolve(String input) {
        if (input == null || input.isEmpty()) return input == null ? "" : input;
        Matcher m = VAR_PATTERN.matcher(input);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String key = m.group(1).trim();
            String val = variables.getOrDefault(key, m.group(0)); // keep {{x}} if unknown
            m.appendReplacement(sb, Matcher.quoteReplacement(val));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    // ─── CRUD ────────────────────────────────────────────────────────────────

    public void set(String key, String value) {
        if (key != null && !key.trim().isEmpty()) {
            variables.put(key.trim(), value != null ? value : "");
        }
    }

    public void remove(String key) {
        if (key != null) variables.remove(key.trim());
    }

    public Map<String, String> getAll() {
        return variables;
    }

    public boolean contains(String key) {
        return key != null && variables.containsKey(key.trim());
    }

    public void clear() {
        variables.clear();
    }

    // ─── Postman Variable Import ──────────────────────────────────────────────

    /**
     * Merges variables from a Postman collection's top-level "variable" array
     * into the live map. Existing keys are overwritten.
     */
    public void importFromPostmanCollection(JsonNode collectionRoot) {
        JsonNode vars = collectionRoot.get("variable");
        if (vars != null && vars.isArray()) {
            for (JsonNode v : vars) {
                String key   = v.has("key")   ? v.get("key").asText()   : null;
                String value = v.has("value") ? v.get("value").asText() : "";
                if (key != null && !key.isEmpty()) {
                    variables.put(key, value);
                }
            }
        }
    }

    /**
     * Imports variables from a standalone Postman Environment export file
     * (the .json file you get from Postman: Manage Environments → Export).
     */
    public void importFromPostmanEnvFile(File f) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(f);

        // Support both Postman v2 env format ("values" array) and simple map
        JsonNode values = root.get("values");
        if (values != null && values.isArray()) {
            for (JsonNode v : values) {
                boolean enabled = !v.has("enabled") || v.get("enabled").asBoolean(true);
                if (!enabled) continue;
                String key   = v.has("key")   ? v.get("key").asText()   : null;
                String value = v.has("value") ? v.get("value").asText() : "";
                if (key != null && !key.isEmpty()) {
                    variables.put(key, value);
                }
            }
        }
    }
}
