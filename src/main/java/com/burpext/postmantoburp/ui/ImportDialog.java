package com.burpext.postmantoburp.ui;

import com.burpext.postmantoburp.logic.CurlParser;
import com.burpext.postmantoburp.logic.OpenApiParser;
import com.burpext.postmantoburp.logic.PostmanParser;
import com.burpext.postmantoburp.model.CollectionNode;
import com.burpext.postmantoburp.model.RequestItem;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.File;
import java.util.function.Consumer;

/**
 * 3-tab import dialog:
 *   Tab 1 — Postman Collection JSON
 *   Tab 2 — Raw cURL command
 *   Tab 3 — OpenAPI v3 (file or remote URL)
 *
 * When import succeeds, calls the onImportSuccess callback with the parsed CollectionNode.
 */
public class ImportDialog extends JDialog {

    private final PostmanParser postmanParser;
    private final CurlParser    curlParser;
    private final OpenApiParser openApiParser;

    /** Callback → adds tree node to CollectionTreePanel */
    private final Consumer<CollectionNode> onImportSuccess;

    // ─── Postman tab ─────────────────────────────────────────────────────────
    private final JTextField postmanFileField = new JTextField();

    // ─── cURL tab ────────────────────────────────────────────────────────────
    private final JTextArea curlArea = new JTextArea();
    private final JTextField curlNameField = new JTextField("Imported cURL Request");

    // ─── OpenAPI tab ─────────────────────────────────────────────────────────
    private final JTextField openApiFileField = new JTextField();
    private final JTextField openApiUrlField  = new JTextField("https://");

    private final JTabbedPane tabs = new JTabbedPane();

    public ImportDialog(Frame parent,
                        PostmanParser postmanParser,
                        CurlParser curlParser,
                        OpenApiParser openApiParser,
                        Consumer<CollectionNode> onImportSuccess) {
        super(parent, "Import Requests", true);
        this.postmanParser   = postmanParser;
        this.curlParser      = curlParser;
        this.openApiParser   = openApiParser;
        this.onImportSuccess = onImportSuccess;

        setSize(640, 420);
        setLocationRelativeTo(parent);
        setLayout(new BorderLayout());
        setResizable(true);

        tabs.addTab("📦 Postman Collection", buildPostmanTab());
        tabs.addTab("⚡ cURL Command",       buildCurlTab());
        tabs.addTab("📘 OpenAPI / Swagger",  buildOpenApiTab());

        add(tabs, BorderLayout.CENTER);
        add(buildFooter(), BorderLayout.SOUTH);
    }

    // ─── Tab 1: Postman ───────────────────────────────────────────────────────

    private JPanel buildPostmanTab() {
        JPanel p = new JPanel(new BorderLayout(8, 8));
        p.setBorder(new EmptyBorder(16, 16, 12, 16));

        JTextArea info = infoArea(
                "Select a Postman Collection JSON file (v2 / v2.1).\n"
                + "Variables from the collection will be loaded into the Environment Manager.");

        JPanel row = new JPanel(new BorderLayout(6, 0));
        postmanFileField.setEditable(false);
        postmanFileField.setFont(new Font("Monospaced", Font.PLAIN, 12));
        JButton browse = new JButton("Browse...");
        browse.addActionListener(e -> {
            JFileChooser fc = new JFileChooser();
            fc.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
                    "Postman Collection JSON", "json"));
            File def = new File(System.getProperty("user.home"), "Downloads");
            if (def.exists()) fc.setCurrentDirectory(def);
            if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                postmanFileField.setText(fc.getSelectedFile().getAbsolutePath());
            }
        });
        row.add(postmanFileField, BorderLayout.CENTER);
        row.add(browse, BorderLayout.EAST);

        p.add(info, BorderLayout.NORTH);
        p.add(row,  BorderLayout.CENTER);
        return p;
    }

    // ─── Tab 2: cURL ─────────────────────────────────────────────────────────

    private JPanel buildCurlTab() {
        JPanel p = new JPanel(new BorderLayout(8, 8));
        p.setBorder(new EmptyBorder(12, 16, 12, 16));

        JPanel top = new JPanel(new BorderLayout(6, 4));

        JLabel info = new JLabel("Paste a curl command (with or without line continuations \\):");
        info.setFont(new Font("SansSerif", Font.PLAIN, 12));

        JPanel nameRow = new JPanel(new BorderLayout(6, 0));
        nameRow.add(new JLabel("Request name: "), BorderLayout.WEST);
        curlNameField.setFont(new Font("SansSerif", Font.PLAIN, 12));
        nameRow.add(curlNameField, BorderLayout.CENTER);

        top.add(info, BorderLayout.NORTH);
        top.add(nameRow, BorderLayout.SOUTH);

        curlArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        curlArea.setLineWrap(true);
        curlArea.setTabSize(2);
        curlArea.setText(
                "curl -X POST 'https://api.example.com/v1/login' \\\n" +
                "  -H 'Content-Type: application/json' \\\n" +
                "  -H 'Authorization: Bearer {{token}}' \\\n" +
                "  -d '{\"username\":\"admin\",\"password\":\"secret\"}'");

        // Select all on first focus so the placeholder is easy to clear
        curlArea.addFocusListener(new java.awt.event.FocusAdapter() {
            boolean cleared = false;
            @Override public void focusGained(java.awt.event.FocusEvent e) {
                if (!cleared) { curlArea.selectAll(); cleared = true; }
            }
        });

        p.add(top, BorderLayout.NORTH);
        p.add(new JScrollPane(curlArea), BorderLayout.CENTER);
        return p;
    }

    // ─── Tab 3: OpenAPI ───────────────────────────────────────────────────────

    private JPanel buildOpenApiTab() {
        JPanel p = new JPanel(new GridBagLayout());
        p.setBorder(new EmptyBorder(16, 16, 12, 16));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(6, 0, 6, 6);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;

        // Info
        gbc.gridx = 0; gbc.gridy = 0; gbc.gridwidth = 2;
        JTextArea info = infoArea(
                "Import an OpenAPI v3 spec from a local file (.json / .yaml)"
                + " or a remote URL (e.g. https://api.example.com/swagger.json).");
        p.add(info, gbc);

        // File row
        gbc.gridy = 1; gbc.gridwidth = 1; gbc.weightx = 0.0;
        gbc.gridx = 0;
        p.add(new JLabel("File:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1.0;
        JPanel fileRow = new JPanel(new BorderLayout(6, 0));
        openApiFileField.setEditable(false);
        openApiFileField.setFont(new Font("Monospaced", Font.PLAIN, 12));
        JButton browse = new JButton("Browse...");
        browse.addActionListener(e -> {
            JFileChooser fc = new JFileChooser();
            fc.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
                    "OpenAPI JSON/YAML", "json", "yaml", "yml"));
            if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                openApiFileField.setText(fc.getSelectedFile().getAbsolutePath());
                openApiUrlField.setText("https://"); // clear URL if file chosen
            }
        });
        fileRow.add(openApiFileField, BorderLayout.CENTER);
        fileRow.add(browse, BorderLayout.EAST);
        p.add(fileRow, gbc);

        // Divider
        gbc.gridy = 2; gbc.gridx = 0; gbc.gridwidth = 2;
        p.add(new JSeparator(), gbc);

        // URL row
        gbc.gridy = 3; gbc.gridwidth = 1; gbc.weightx = 0.0;
        gbc.gridx = 0;
        p.add(new JLabel("Remote URL:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1.0;
        openApiUrlField.setFont(new Font("Monospaced", Font.PLAIN, 12));
        p.add(openApiUrlField, gbc);

        // Hint
        gbc.gridy = 4; gbc.gridx = 0; gbc.gridwidth = 2;
        JLabel hint = new JLabel("Example: https://petstore3.swagger.io/api/v3/openapi.json");
        hint.setFont(new Font("SansSerif", Font.ITALIC, 11));
        hint.setForeground(new Color(120, 120, 120));
        p.add(hint, gbc);

        // Filler
        gbc.gridy = 5; gbc.weighty = 1.0;
        p.add(new JPanel(), gbc);

        return p;
    }

    // ─── Footer ───────────────────────────────────────────────────────────────

    private JPanel buildFooter() {
        JPanel footer = new JPanel(new BorderLayout());
        footer.setBorder(new EmptyBorder(4, 12, 8, 12));

        JLabel status = new JLabel(" ");
        status.setFont(new Font("SansSerif", Font.ITALIC, 11));
        status.setForeground(new Color(120, 120, 120));

        JPanel btns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        JButton cancel = new JButton("Cancel");
        JButton importBtn = new JButton("  Import  ");
        importBtn.setFont(importBtn.getFont().deriveFont(Font.BOLD));

        cancel.addActionListener(e -> dispose());
        importBtn.addActionListener(e -> doImport(status));

        btns.add(cancel);
        btns.add(importBtn);

        footer.add(status, BorderLayout.CENTER);
        footer.add(btns, BorderLayout.EAST);
        return footer;
    }

    // ─── Import dispatch ──────────────────────────────────────────────────────

    private void doImport(JLabel statusLabel) {
        int selectedTab = tabs.getSelectedIndex();
        statusLabel.setText("Importing…");
        statusLabel.setForeground(new Color(100, 100, 100));

        new Thread(() -> {
            try {
                CollectionNode result = switch (selectedTab) {
                    case 0 -> importPostman();
                    case 1 -> importCurl();
                    case 2 -> importOpenApi();
                    default -> throw new IllegalStateException("Unknown tab");
                };

                onImportSuccess.accept(result);

                SwingUtilities.invokeLater(() -> {
                    statusLabel.setText("✔ Import successful!");
                    statusLabel.setForeground(new Color(60, 160, 80));
                    // Close after short delay
                    Timer t = new Timer(800, ae -> dispose());
                    t.setRepeats(false);
                    t.start();
                });

            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> {
                    statusLabel.setText("✖ " + ex.getMessage());
                    statusLabel.setForeground(new Color(200, 60, 60));
                });
            }
        }).start();
    }

    // ─── Per-tab parsers ──────────────────────────────────────────────────────

    private CollectionNode importPostman() throws Exception {
        String path = postmanFileField.getText().trim();
        if (path.isEmpty()) throw new IllegalArgumentException("Please select a Postman JSON file.");
        return postmanParser.parse(new File(path));
    }

    private CollectionNode importCurl() throws Exception {
        String cmd = curlArea.getText().trim();
        if (cmd.isEmpty()) throw new IllegalArgumentException("Please paste a cURL command.");
        RequestItem item = curlParser.parse(cmd);

        // Override name if user filled in name field
        String customName = curlNameField.getText().trim();
        if (!customName.isEmpty() && !customName.equals("Imported cURL Request")) {
            item.setName(customName);
        }

        // Wrap the single request in a folder named "cURL Imports"
        CollectionNode folder = new CollectionNode("cURL Imports");
        folder.add(new CollectionNode(item.getName(), item));
        return folder;
    }

    private CollectionNode importOpenApi() throws Exception {
        String filePath = openApiFileField.getText().trim();
        String url      = openApiUrlField.getText().trim();

        if (!filePath.isEmpty()) {
            return openApiParser.parseFile(new File(filePath));
        } else if (!url.isEmpty() && !url.equals("https://")) {
            return openApiParser.parseUrl(url);
        } else {
            throw new IllegalArgumentException("Please provide a file or a URL.");
        }
    }
    // ─── Utility for multi-line info text ───────────────────────────────────────

    /**
     * Creates a read-only, borderless, background-transparent JTextArea
     * that looks like a label but wraps naturally to multiple lines.
     * Used instead of HTML JLabels which don't render in Burp's LaF.
     */
    private JTextArea infoArea(String text) {
        JTextArea area = new JTextArea(text);
        area.setEditable(false);
        area.setFocusable(false);
        area.setOpaque(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setFont(new Font("SansSerif", Font.PLAIN, 12));
        area.setBorder(null);
        return area;
    }
}
