package com.burpext.postmantoburp.ui;

import com.burpext.postmantoburp.logic.EnvironmentManager;
import com.burpext.postmantoburp.model.CollectionNode;
import com.burpext.postmantoburp.model.RequestItem;
import burp.api.montoya.MontoyaApi;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.tree.*;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Left sidebar — displays all imported collections as a foldable JTree.
 * Clicking a request leaf populates the RequestInspectorPanel via callback.
 * Right-clicking shows a context menu with "Send to..." actions.
 */
public class CollectionTreePanel extends JPanel {

    private final MontoyaApi api;
    private final RequestInspectorPanel inspector;
    private final EnvironmentManager envManager;

    private final DefaultMutableTreeNode rootNode =
            new DefaultMutableTreeNode("Collections");
    private final DefaultTreeModel treeModel = new DefaultTreeModel(rootNode);
    private final JTree tree = new JTree(treeModel);

    private int collectionCount = 0;

    // HTTP method badge colors (matching inspector)
    private static final Map<String, Color> METHOD_COLORS = Map.of(
            "GET",     new Color(97,  175, 254),
            "POST",    new Color(73,  204, 144),
            "PUT",     new Color(252, 161, 48),
            "DELETE",  new Color(249, 62,  62),
            "PATCH",   new Color(80,  227, 194),
            "HEAD",    new Color(144, 18,  254),
            "OPTIONS", new Color(144, 18,  254)
    );

    public CollectionTreePanel(MontoyaApi api, RequestInspectorPanel inspector,
                               EnvironmentManager envManager) {
        this.api       = api;
        this.inspector = inspector;
        this.envManager = envManager;
        setLayout(new BorderLayout());
        setBorder(new EmptyBorder(0, 0, 0, 0));
        buildTree();
    }

    // ─── Tree setup ───────────────────────────────────────────────────────────

    private void buildTree() {
        tree.setRootVisible(false);
        tree.setShowsRootHandles(true);
        tree.setFont(new Font("SansSerif", Font.PLAIN, 12));
        tree.setCellRenderer(new CollectionTreeRenderer());

        // Selection: populate inspector
        tree.addTreeSelectionListener(e -> {
            TreePath path = tree.getSelectionPath();
            if (path == null) return;
            Object last = path.getLastPathComponent();
            if (last instanceof CollectionNode cn && !cn.isFolder()) {
                inspector.populate(cn.getRequestItem());
            }
        });

        // Right-click context menu
        tree.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (SwingUtilities.isRightMouseButton(e)) {
                    TreePath path = tree.getPathForLocation(e.getX(), e.getY());
                    if (path != null) {
                        tree.setSelectionPath(path);
                        showContextMenu(e, path);
                    }
                }
            }
        });

        JScrollPane scroll = new JScrollPane(tree);
        scroll.setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, new Color(200, 200, 200)));
        add(scroll, BorderLayout.CENTER);

        // Toolbar
        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 3));
        toolbar.setBorder(new EmptyBorder(2, 4, 2, 4));
        JButton expandAll  = smallButton("＋", "Expand all");
        JButton collapseAll = smallButton("－", "Collapse all");
        JButton clearBtn   = smallButton("✕", "Remove selected collection");
        expandAll.addActionListener(e -> expandAll());
        collapseAll.addActionListener(e -> collapseAll());
        clearBtn.addActionListener(e -> removeSelected());
        toolbar.add(expandAll);
        toolbar.add(collapseAll);
        toolbar.add(clearBtn);
        add(toolbar, BorderLayout.NORTH);
    }

    // ─── Public API ──────────────────────────────────────────────────────────

    /** Adds a parsed CollectionNode root (from any parser) to the tree. */
    public void addCollection(CollectionNode collectionRoot) {
        SwingUtilities.invokeLater(() -> {
            rootNode.add(collectionRoot);
            treeModel.reload();
            // Auto-expand the newly added collection
            tree.expandPath(new TreePath(new Object[]{rootNode, collectionRoot}));
            collectionCount++;
        });
    }

    /** Clears all collections and resets the tree. */
    public void clearAll() {
        SwingUtilities.invokeLater(() -> {
            rootNode.removeAllChildren();
            treeModel.reload();
            collectionCount = 0;
        });
    }

    // ─── Context menu ─────────────────────────────────────────────────────────

    private void showContextMenu(MouseEvent e, TreePath path) {
        Object node = path.getLastPathComponent();
        JPopupMenu menu = new JPopupMenu();

        if (node instanceof CollectionNode cn) {
            if (cn.isFolder()) {
                // Folder actions
                JMenuItem sendAllRepeater  = new JMenuItem("Send All to Repeater");
                JMenuItem sendAllIntruder  = new JMenuItem("Send All to Intruder");
                sendAllRepeater.addActionListener(ae -> batchSend(cn, RequestInspectorPanel.BurpTool.REPEATER));
                sendAllIntruder.addActionListener(ae -> batchSend(cn, RequestInspectorPanel.BurpTool.INTRUDER));
                menu.add(sendAllRepeater);
                menu.add(sendAllIntruder);
                menu.addSeparator();
                JMenuItem expand = new JMenuItem("Expand All");
                expand.addActionListener(ae -> expandAll(new TreePath(cn.getPath())));
                menu.add(expand);
            } else {
                // Leaf (request) actions
                JMenuItem toRepeater = new JMenuItem("▶  Send to Repeater");
                JMenuItem toIntruder = new JMenuItem("⚡  Send to Intruder");
                JMenuItem toScanner  = new JMenuItem("🔍  Active Scan");
                JMenuItem copyUrl    = new JMenuItem("📋  Copy URL");

                RequestItem item = cn.getRequestItem();
                toRepeater.addActionListener(ae -> inspector.sendRequest(item, RequestInspectorPanel.BurpTool.REPEATER));
                toIntruder.addActionListener(ae -> inspector.sendRequest(item, RequestInspectorPanel.BurpTool.INTRUDER));
                toScanner .addActionListener(ae -> inspector.sendRequest(item, RequestInspectorPanel.BurpTool.SCANNER));
                copyUrl.addActionListener(ae -> {
                    String url = envManager.resolve(item.getFullUrl());
                    Toolkit.getDefaultToolkit().getSystemClipboard()
                            .setContents(new java.awt.datatransfer.StringSelection(url), null);
                });
                menu.add(toRepeater);
                menu.add(toIntruder);
                menu.add(toScanner);
                menu.addSeparator();
                menu.add(copyUrl);
            }
        }

        if (menu.getComponentCount() > 0) {
            menu.show(tree, e.getX(), e.getY());
        }
    }

    // ─── Batch send ───────────────────────────────────────────────────────────

    /**
     * Silently sends ALL requests under the given folder node to the target tool.
     * Runs in a background thread so Burp UI stays responsive.
     */
    private void batchSend(CollectionNode folder, RequestInspectorPanel.BurpTool tool) {
        List<RequestItem> items = collectRequests(folder);
        if (items.isEmpty()) return;

        new Thread(() -> {
            int sent = 0;
            for (RequestItem item : items) {
                try {
                    inspector.sendRequest(item, tool);
                    sent++;
                    Thread.sleep(50); // small pause so Burp doesn't get overwhelmed
                } catch (Exception ex) {
                    api.logging().logToError("Batch send error for [" + item.getName() + "]: " + ex.getMessage());
                }
            }
            final int total = sent;
            SwingUtilities.invokeLater(() ->
                    JOptionPane.showMessageDialog(this,
                            "Sent " + total + " request(s) to " + tool.name().toLowerCase() + ".",
                            "Batch Send Complete", JOptionPane.INFORMATION_MESSAGE));
        }).start();
    }

    /** Recursively collects all RequestItems from a folder and its sub-folders. */
    private List<RequestItem> collectRequests(CollectionNode node) {
        List<RequestItem> result = new ArrayList<>();
        Enumeration<?> children = node.children();
        while (children.hasMoreElements()) {
            Object child = children.nextElement();
            if (child instanceof CollectionNode cn) {
                if (cn.isFolder()) {
                    result.addAll(collectRequests(cn));
                } else if (cn.getRequestItem() != null) {
                    result.add(cn.getRequestItem());
                }
            }
        }
        return result;
    }

    // ─── Expand / Collapse helpers ────────────────────────────────────────────

    private void expandAll() {
        for (int i = 0; i < tree.getRowCount(); i++) tree.expandRow(i);
    }

    private void expandAll(TreePath parent) {
        tree.expandPath(parent);
        TreeNode node = (TreeNode) parent.getLastPathComponent();
        Enumeration<?> children = node.children();
        while (children.hasMoreElements()) {
            TreeNode child = (TreeNode) children.nextElement();
            expandAll(parent.pathByAddingChild(child));
        }
    }

    private void collapseAll() {
        for (int i = tree.getRowCount() - 1; i >= 0; i--) tree.collapseRow(i);
    }

    private void removeSelected() {
        TreePath path = tree.getSelectionPath();
        if (path == null) return;
        DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();
        if (node == rootNode) return; // don't remove the invisible root
        treeModel.removeNodeFromParent(node);
    }

    // ─── Tree cell renderer ───────────────────────────────────────────────────

    /**
     * Custom renderer: folders get a bold label; request leaves get a
     * coloured method badge (JLabel) beside the request name — no HTML needed.
     */
    private class CollectionTreeRenderer extends DefaultTreeCellRenderer {

        // Re-usable row panel (returned as the renderer component for leaf nodes)
        private final JPanel  rowPanel  = new JPanel(new BorderLayout(4, 0));
        private final JLabel  badgeLabel = new JLabel();
        private final JLabel  nameLabel  = new JLabel();

        CollectionTreeRenderer() {
            rowPanel.setOpaque(true);
            rowPanel.add(badgeLabel, BorderLayout.WEST);
            rowPanel.add(nameLabel,  BorderLayout.CENTER);

            badgeLabel.setFont(new Font("Monospaced", Font.BOLD, 11));
            badgeLabel.setBorder(BorderFactory.createEmptyBorder(1, 3, 1, 6));
            nameLabel .setFont(new Font("SansSerif",  Font.PLAIN, 12));
        }

        @Override
        public Component getTreeCellRendererComponent(
                JTree tree, Object value, boolean selected,
                boolean expanded, boolean leaf, int row, boolean hasFocus) {

            // Let super handle selection highlight, focus border, etc.
            super.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus);
            setIcon(null);

            if (!(value instanceof CollectionNode cn)) return this;

            if (cn.isFolder()) {
                // ── Folder row — use the default JLabel ("this") ──────────────
                setText((expanded ? "▼  " : "▶  ") + cn.getDisplayName());
                setFont(new Font("SansSerif", Font.BOLD, 12));
                setForeground(selected ? Color.WHITE : new Color(40, 40, 40));
                return this;
            }

            // ── Request leaf row — use the custom rowPanel ─────────────────
            RequestItem item = cn.getRequestItem();
            if (item == null) return this;

            String method = item.getMethod() == null ? "?" : item.getMethod();
            Color  color  = METHOD_COLORS.getOrDefault(method, new Color(130, 130, 130));

            badgeLabel.setText(String.format("%-7s", method));  // pad to 7 chars for alignment
            badgeLabel.setForeground(color);

            nameLabel.setText(cn.getDisplayName());
            nameLabel.setForeground(selected ? Color.WHITE : new Color(30, 30, 30));

            // Mirror selection background from the default renderer
            Color bg = selected ? getBackgroundSelectionColor() : getBackgroundNonSelectionColor();
            rowPanel.setBackground(bg);

            return rowPanel;
        }
    }

    // ─── Utilities ────────────────────────────────────────────────────────────

    private JButton smallButton(String text, String tooltip) {
        JButton b = new JButton(text);
        b.setToolTipText(tooltip);
        b.setFont(new Font("SansSerif", Font.PLAIN, 11));
        b.setMargin(new Insets(1, 4, 1, 4));
        return b;
    }
}
