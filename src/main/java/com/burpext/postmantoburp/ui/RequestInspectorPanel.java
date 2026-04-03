package com.burpext.postmantoburp.ui;

import com.burpext.postmantoburp.logic.EnvironmentManager;
import com.burpext.postmantoburp.model.RequestItem;
import burp.api.montoya.MontoyaApi;
import burp.api.montoya.http.HttpService;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.scanner.AuditConfiguration;
import burp.api.montoya.scanner.BuiltInAuditConfiguration;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.Map;

/**
 * Right panel — shows details of the selected request across 4 tabs:
 *   URL · Headers · Body · Auth
 * and has action buttons to dispatch to Burp tools.
 */
public class RequestInspectorPanel extends JPanel {

    private final MontoyaApi api;
    private final EnvironmentManager envManager;

    // ─── URL bar ─────────────────────────────────────────────────────────────
    private final JLabel  methodBadge  = new JLabel("───");
    private final JTextField urlField  = new JTextField();

    // ─── Tab content ─────────────────────────────────────────────────────────
    private final DefaultTableModel headersTableModel =
            new DefaultTableModel(new String[]{"Header", "Value"}, 0) {
                @Override public boolean isCellEditable(int r, int c) { return false; }
            };
    private final JTextArea bodyArea  = new JTextArea();
    private final JTextArea authArea  = new JTextArea();

    // ─── Status ──────────────────────────────────────────────────────────────
    private final JLabel statusLabel  = new JLabel(" ");

    private RequestItem currentRequest;

    // ─── HTTP method colors ───────────────────────────────────────────────────
    private static final Map<String, Color> METHOD_COLORS = Map.of(
            "GET",     new Color(97,  175, 254),
            "POST",    new Color(73,  204, 144),
            "PUT",     new Color(252, 161, 48),
            "DELETE",  new Color(249, 62,  62),
            "PATCH",   new Color(80,  227, 194),
            "HEAD",    new Color(144, 18,  254),
            "OPTIONS", new Color(144, 18,  254)
    );

    public RequestInspectorPanel(MontoyaApi api, EnvironmentManager envManager) {
        this.api = api;
        this.envManager = envManager;
        setLayout(new BorderLayout(0, 0));
        setBorder(new EmptyBorder(0, 0, 0, 0));
        add(buildUrlBar(),    BorderLayout.NORTH);
        add(buildTabPane(),   BorderLayout.CENTER);
        add(buildActionBar(), BorderLayout.SOUTH);
        showEmpty();
    }

    // ─── Sub-panels ──────────────────────────────────────────────────────────

    private JPanel buildUrlBar() {
        JPanel bar = new JPanel(new BorderLayout(6, 0));
        bar.setBorder(new EmptyBorder(8, 10, 8, 10));

        methodBadge.setFont(new Font("Monospaced", Font.BOLD, 12));
        methodBadge.setOpaque(true);
        methodBadge.setBorder(BorderFactory.createEmptyBorder(3, 8, 3, 8));
        methodBadge.setBackground(Color.LIGHT_GRAY);
        methodBadge.setForeground(Color.WHITE);

        urlField.setEditable(false);
        urlField.setFont(new Font("Monospaced", Font.PLAIN, 13));
        urlField.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(180, 180, 180)),
                BorderFactory.createEmptyBorder(3, 6, 3, 6)));

        bar.add(methodBadge, BorderLayout.WEST);
        bar.add(urlField,    BorderLayout.CENTER);
        return bar;
    }

    private JTabbedPane buildTabPane() {
        JTabbedPane tabs = new JTabbedPane();
        tabs.setFont(new Font("SansSerif", Font.PLAIN, 12));

        // Headers tab
        JTable headersTable = new JTable(headersTableModel);
        headersTable.setFont(new Font("Monospaced", Font.PLAIN, 12));
        headersTable.setRowHeight(22);
        headersTable.getColumnModel().getColumn(0).setPreferredWidth(200);
        headersTable.getColumnModel().getColumn(1).setPreferredWidth(400);
        tabs.addTab("Headers", new JScrollPane(headersTable));

        // Body tab
        bodyArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        bodyArea.setEditable(false);
        bodyArea.setLineWrap(true);
        bodyArea.setWrapStyleWord(false);
        tabs.addTab("Body", new JScrollPane(bodyArea));

        // Auth tab
        authArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        authArea.setEditable(false);
        authArea.setLineWrap(true);
        tabs.addTab("Auth", new JScrollPane(authArea));

        return tabs;
    }

    private JPanel buildActionBar() {
        JPanel outer = new JPanel(new BorderLayout());
        outer.setBorder(new EmptyBorder(0, 0, 0, 0));

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 6));

        JButton toRepeater = styledButton("▶  Send to Repeater",  new Color(73,  204, 144));
        JButton toIntruder = styledButton("⚡  Send to Intruder",  new Color(252, 161, 48));
        JButton toScanner  = styledButton("🔍  Active Scan",       new Color(97,  175, 254));

        toRepeater.addActionListener(e -> sendActive(BurpTool.REPEATER));
        toIntruder.addActionListener(e -> sendActive(BurpTool.INTRUDER));
        toScanner .addActionListener(e -> sendActive(BurpTool.SCANNER));

        buttons.add(toRepeater);
        buttons.add(toIntruder);
        buttons.add(toScanner);

        statusLabel.setFont(new Font("SansSerif", Font.ITALIC, 11));
        statusLabel.setForeground(new Color(120, 120, 120));
        statusLabel.setBorder(new EmptyBorder(0, 10, 0, 0));

        outer.add(buttons, BorderLayout.WEST);
        outer.add(statusLabel, BorderLayout.CENTER);
        return outer;
    }

    // ─── Populate ─────────────────────────────────────────────────────────────

    /** Called by CollectionTreePanel when the user clicks a request node. */
    public void populate(RequestItem item) {
        this.currentRequest = item;

        // URL bar
        String method = item.getMethod();
        methodBadge.setText(method);
        methodBadge.setBackground(METHOD_COLORS.getOrDefault(method, Color.GRAY));
        urlField.setText(item.getFullUrl());

        // Headers table
        headersTableModel.setRowCount(0);
        for (Map.Entry<String, String> h : item.getHeaders().entrySet()) {
            headersTableModel.addRow(new Object[]{h.getKey(), h.getValue()});
        }

        // Body
        bodyArea.setText(item.getBody() != null ? item.getBody() : "");
        bodyArea.setCaretPosition(0);

        // Auth
        String authType = item.getAuthType();
        if ("bearer".equalsIgnoreCase(authType)) {
            authArea.setText("Type:  Bearer\nToken: " + item.getAuthValue());
        } else if ("basic".equalsIgnoreCase(authType)) {
            authArea.setText("Type:   Basic Auth\nBase64: " + item.getAuthValue());
        } else {
            authArea.setText("No auth configured.");
        }
        authArea.setCaretPosition(0);

        statusLabel.setText(" ");
    }

    private void showEmpty() {
        methodBadge.setText("───");
        methodBadge.setBackground(Color.LIGHT_GRAY);
        urlField.setText("Select a request from the collection tree");
        headersTableModel.setRowCount(0);
        bodyArea.setText("");
        authArea.setText("");
    }

    // ─── Send actions ─────────────────────────────────────────────────────────

    public void sendActive(BurpTool tool) {
        if (currentRequest == null) return;
        sendRequest(currentRequest, tool);
    }

    public void sendRequest(RequestItem item, BurpTool tool) {
        new Thread(() -> {
            try {
                HttpRequest httpRequest = buildHttpRequest(item);

                switch (tool) {
                    case REPEATER -> {
                        api.repeater().sendToRepeater(httpRequest, item.getName());
                        SwingUtilities.invokeLater(() ->
                                statusLabel.setText("✔ Sent to Repeater: " + item.getName()));
                    }
                    case INTRUDER -> {
                        api.intruder().sendToIntruder(httpRequest);
                        SwingUtilities.invokeLater(() ->
                                statusLabel.setText("✔ Sent to Intruder: " + item.getName()));
                    }
                    case SCANNER -> {
                        // Resolve and scope the target URL — startAudit operates on in-scope URLs
                        String targetUrl = envManager.resolve(item.getFullUrl());
                        if (!api.scope().isInScope(targetUrl)) {
                            api.scope().includeInScope(targetUrl);
                        }
                        // Fire the actual Burp active scan
                        api.scanner().startAudit(
                            AuditConfiguration.auditConfiguration(BuiltInAuditConfiguration.LEGACY_ACTIVE_AUDIT_CHECKS)
                        );
                        SwingUtilities.invokeLater(() ->
                                statusLabel.setText("✔ Active Scan started: " + item.getName()));
                        api.logging().logToOutput("[Active Scan] Scan queued for: " + item.getFullUrl());
                    }
                }
            } catch (Exception ex) {
                api.logging().logToError("Send failed for [" + item.getName() + "]: " + ex.getMessage());
                SwingUtilities.invokeLater(() ->
                        statusLabel.setText("✖ Error: " + ex.getMessage()));
            }
        }).start();
    }

    /**
     * Builds a Burp HttpRequest from a RequestItem, resolving all env variables.
     *
     * Strategy:
     *   1. Resolve the complete fullUrl string first (handles {{baseUrl}} etc.)
     *   2. Parse it with java.net.URI to cleanly extract host / port / path
     *   3. If any {{variable}} is still present after resolution → clear user error
     *   4. Fall back to item fields only if the URL is not parseable
     */
    public HttpRequest buildHttpRequest(RequestItem item) throws Exception {
        String method = item.getMethod();

        // ── Step 1: resolve the full URL ─────────────────────────────────────
        String resolvedUrl = envManager.resolve(item.getFullUrl());

        // ── Step 2: check for any remaining unresolved {{placeholders}} ──────
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("\\{\\{([^}]+)\\}\\}").matcher(resolvedUrl);
        if (m.find()) {
            throw new IllegalArgumentException(
                    "Unresolved variable: {{" + m.group(1) + "}}. "
                    + "Set it in the Environments panel before sending.");
        }

        // ── Step 2b: fix double-protocol (happens when {{baseUrl}} = "https://...")
        //    e.g. "https://https://api.example.com" → "https://api.example.com"
        resolvedUrl = resolvedUrl.replaceAll("^(https?://)https?://", "$1");

        // ── Step 3: parse the resolved URL ───────────────────────────────────
        java.net.URI uri;
        try {
            uri = new java.net.URI(resolvedUrl);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid URL after resolution: " + resolvedUrl);
        }

        String host     = uri.getHost();
        String protocol = uri.getScheme() != null ? uri.getScheme().toLowerCase() : "https";
        boolean secure  = "https".equals(protocol);
        int port        = uri.getPort();
        if (port <= 0) port = secure ? 443 : 80;

        // Path + query string (Burp wants the full request path including query)
        String path = uri.getRawPath();
        if (path == null || path.isEmpty()) path = "/";
        if (uri.getRawQuery() != null) path = path + "?" + uri.getRawQuery();

        if (host == null || host.isEmpty()) {
            throw new IllegalArgumentException(
                    "Could not determine host from URL: " + resolvedUrl);
        }

        // ── Step 4: build the raw HTTP request ──────────────────────────────
        HttpService service = HttpService.httpService(host, port, secure);

        StringBuilder raw = new StringBuilder();
        raw.append(method).append(" ").append(path).append(" HTTP/1.1\r\n");
        raw.append("Host: ").append(host).append("\r\n");

        // Headers (skip Host, it's already added above)
        for (Map.Entry<String, String> h : item.getHeaders().entrySet()) {
            if ("host".equalsIgnoreCase(h.getKey())) continue;
            raw.append(h.getKey()).append(": ")
               .append(envManager.resolve(h.getValue())).append("\r\n");
        }

        // Auth injection
        String authType = item.getAuthType();
        if ("bearer".equalsIgnoreCase(authType)) {
            raw.append("Authorization: Bearer ")
               .append(envManager.resolve(item.getAuthValue())).append("\r\n");
        } else if ("basic".equalsIgnoreCase(authType)) {
            raw.append("Authorization: Basic ")
               .append(envManager.resolve(item.getAuthValue())).append("\r\n");
        }

        String body = envManager.resolve(item.getBody());
        if (body != null && !body.isEmpty()) {
            raw.append("Content-Length: ").append(body.getBytes().length).append("\r\n");
            raw.append("\r\n").append(body);
        } else {
            raw.append("\r\n");
        }

        return HttpRequest.httpRequest(service, raw.toString());
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private JButton styledButton(String text, Color color) {
        JButton b = new JButton(text);
        b.setFont(new Font("SansSerif", Font.BOLD, 12));
        b.setForeground(color);
        b.setFocusPainted(false);
        return b;
    }

    public enum BurpTool { REPEATER, INTRUDER, SCANNER }
}
