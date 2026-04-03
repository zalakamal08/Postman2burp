package com.burpext.postmantoburp.ui;

import com.burpext.postmantoburp.logic.CurlParser;
import com.burpext.postmantoburp.logic.EnvironmentManager;
import com.burpext.postmantoburp.logic.OpenApiParser;
import com.burpext.postmantoburp.logic.PostmanParser;
import burp.api.montoya.MontoyaApi;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/**
 * Root panel registered as a Burp Suite tab ("API Workbench").
 *
 * Layout:
 *   ┌─────────────────────────────────────────────────────────┐
 *   │  Toolbar: [Import ▼]  [Environments]  [Clear All]       │
 *   ├─────────────────────┬───────────────────────────────────┤
 *   │  CollectionTree     │  RequestInspector                 │
 *   │  (30%)              │  (70%)                            │
 *   └─────────────────────┴───────────────────────────────────┘
 */
public class MainPanel extends JPanel {

    private final MontoyaApi         api;
    private final EnvironmentManager envManager;

    private final RequestInspectorPanel  inspector;
    private final CollectionTreePanel    treePanel;
    private final EnvironmentManagerPanel envDialog;

    private final PostmanParser postmanParser;
    private final CurlParser    curlParser;
    private final OpenApiParser openApiParser;

    public MainPanel(MontoyaApi api, EnvironmentManager envManager) {
        this.api        = api;
        this.envManager = envManager;

        // ─── Logic ────────────────────────────────────────────────────────────
        postmanParser = new PostmanParser(envManager);
        curlParser    = new CurlParser();
        openApiParser = new OpenApiParser();

        // ─── UI components ───────────────────────────────────────────────────
        inspector = new RequestInspectorPanel(api, envManager);
        treePanel = new CollectionTreePanel(api, inspector, envManager);
        envDialog = new EnvironmentManagerPanel(
                JOptionPane.getRootFrame(), envManager);

        // ─── Layout ──────────────────────────────────────────────────────────
        setLayout(new BorderLayout(0, 0));
        add(buildToolbar(), BorderLayout.NORTH);

        JSplitPane split = new JSplitPane(
                JSplitPane.HORIZONTAL_SPLIT, treePanel, inspector);
        split.setDividerLocation(300);
        split.setDividerSize(4);
        split.setResizeWeight(0.25);
        split.setBorder(null);
        add(split, BorderLayout.CENTER);

        add(buildStatusBar(), BorderLayout.SOUTH);
    }

    // ─── Toolbar ─────────────────────────────────────────────────────────────

    private JPanel buildToolbar() {
        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 5));
        toolbar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(210, 210, 210)));

        // Brand label
        JLabel brand = new JLabel("  🔵 API Workbench");
        brand.setFont(new Font("SansSerif", Font.BOLD, 13));
        brand.setForeground(new Color(50, 50, 50));
        toolbar.add(brand);

        // Separator
        toolbar.add(new JSeparator(JSeparator.VERTICAL) {{
            setPreferredSize(new Dimension(1, 20));
        }});

        // ── Import dropdown button ────────────────────────────────────────────
        JButton importBtn = toolbarButton("⬆  Import", "Import from Postman / cURL / OpenAPI");
        importBtn.addActionListener(e -> {
            // Find the parent Frame for the dialog
            Frame parentFrame = (Frame) SwingUtilities.getWindowAncestor(this);
            ImportDialog dialog = new ImportDialog(
                    parentFrame,
                    postmanParser,
                    curlParser,
                    openApiParser,
                    node -> {
                        treePanel.addCollection(node);
                        api.logging().logToOutput(
                                "[API Workbench] Imported: " + node.getDisplayName());
                    });
            dialog.setVisible(true);
        });
        toolbar.add(importBtn);

        // ── Environment manager ───────────────────────────────────────────────
        JButton envBtn = toolbarButton("🌍  Environments", "Manage environment variables ({{placeholders}})");
        envBtn.addActionListener(e -> {
            envDialog.refresh();
            envDialog.setVisible(true);
        });
        toolbar.add(envBtn);

        // ── Clear all ─────────────────────────────────────────────────────────
        JButton clearBtn = toolbarButton("✕  Clear All", "Remove all imported collections");
        clearBtn.addActionListener(e -> {
            int confirm = JOptionPane.showConfirmDialog(
                    this,
                    "Remove all imported collections from the tree?",
                    "Clear All",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE);
            if (confirm == JOptionPane.YES_OPTION) {
                treePanel.clearAll();
            }
        });
        toolbar.add(clearBtn);

        return toolbar;
    }

    // ─── Status bar ──────────────────────────────────────────────────────────

    private JPanel buildStatusBar() {
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 2));
        bar.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(210, 210, 210)));
        JLabel tip = new JLabel(
                "Tip: Right-click a folder to batch-send all requests. "
                + "Use {{variableName}} in any field and set values in Environments.");
        tip.setFont(new Font("SansSerif", Font.ITALIC, 11));
        tip.setForeground(new Color(130, 130, 130));
        bar.add(tip);
        return bar;
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private JButton toolbarButton(String text, String tooltip) {
        JButton b = new JButton(text);
        b.setToolTipText(tooltip);
        b.setFont(new Font("SansSerif", Font.PLAIN, 12));
        b.setFocusPainted(false);
        return b;
    }

    public JPanel getPanel() { return this; }
}
