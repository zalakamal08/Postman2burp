package com.burpext.postmantoburp.ui;

import com.burpext.postmantoburp.logic.EnvironmentManager;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.File;
import java.util.Map;
import java.util.Vector;

/**
 * A non-modal dialog that lets the user view, add, edit, delete,
 * and import environment variables. Variables resolve {{placeholders}} at send-time.
 */
public class EnvironmentManagerPanel extends JDialog {

    private final EnvironmentManager envManager;

    private final DefaultTableModel tableModel = new DefaultTableModel(
            new String[]{"Variable", "Value"}, 0);
    private final JTable table = new JTable(tableModel);

    public EnvironmentManagerPanel(Frame parent, EnvironmentManager envManager) {
        super(parent, "🌍  Environment Variables", false);
        this.envManager = envManager;

        setSize(600, 420);
        setLocationRelativeTo(parent);
        setLayout(new BorderLayout(0, 0));

        add(buildHeader(), BorderLayout.NORTH);
        add(buildTablePanel(), BorderLayout.CENTER);
        add(buildButtonBar(), BorderLayout.SOUTH);

        refresh();
    }

    // ─── Panels ───────────────────────────────────────────────────────────────

    private JPanel buildHeader() {
        JPanel header = new JPanel(new BorderLayout());
        header.setBorder(new EmptyBorder(10, 14, 6, 14));
        JLabel title = new JLabel("Live Variables — changes take effect immediately on next send");
        title.setFont(new Font("SansSerif", Font.ITALIC, 12));
        title.setForeground(new Color(100, 100, 100));
        header.add(title, BorderLayout.CENTER);
        return header;
    }

    private JScrollPane buildTablePanel() {
        table.setFont(new Font("Monospaced", Font.PLAIN, 12));
        table.setRowHeight(24);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.getColumnModel().getColumn(0).setPreferredWidth(180);
        table.getColumnModel().getColumn(1).setPreferredWidth(380);

        // Commit edits on focus loss so users don't have to press Enter
        table.putClientProperty("terminateEditOnFocusLost", Boolean.TRUE);

        // Live-write edits back to EnvironmentManager as the user types
        tableModel.addTableModelListener(e -> {
            int row = e.getFirstRow();
            if (row >= 0 && row < tableModel.getRowCount()) {
                Object keyObj = tableModel.getValueAt(row, 0);
                Object valObj = tableModel.getValueAt(row, 1);
                String key = keyObj != null ? keyObj.toString().trim() : "";
                String val = valObj != null ? valObj.toString() : "";
                if (!key.isEmpty()) envManager.set(key, val);
            }
        });

        JScrollPane scroll = new JScrollPane(table);
        scroll.setBorder(BorderFactory.createMatteBorder(1, 0, 1, 0, new Color(200, 200, 200)));
        return scroll;
    }

    private JPanel buildButtonBar() {
        JPanel bar = new JPanel(new BorderLayout());
        bar.setBorder(new EmptyBorder(6, 10, 8, 10));

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        JButton addBtn    = button("+ Add Row");
        JButton delBtn    = button("- Delete Row");
        JButton importBtn = button("📂 Import ENV File");
        JButton clearBtn  = button("🗑 Clear All");

        addBtn.addActionListener(e -> {
            tableModel.addRow(new Object[]{"variable_name", "value"});
            int newRow = tableModel.getRowCount() - 1;
            table.setRowSelectionInterval(newRow, newRow);
            table.scrollRectToVisible(table.getCellRect(newRow, 0, true));
            table.editCellAt(newRow, 0);
        });

        delBtn.addActionListener(e -> {
            int sel = table.getSelectedRow();
            if (sel >= 0) {
                // Remove from manager
                Object key = tableModel.getValueAt(sel, 0);
                if (key != null) envManager.remove(key.toString());
                tableModel.removeRow(sel);
            }
        });

        importBtn.addActionListener(e -> importEnvFile());

        clearBtn.addActionListener(e -> {
            int confirm = JOptionPane.showConfirmDialog(this,
                    "Clear all environment variables?", "Confirm",
                    JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (confirm == JOptionPane.YES_OPTION) {
                envManager.clear();
                refresh();
            }
        });

        left.add(addBtn);
        left.add(delBtn);
        left.add(importBtn);
        left.add(clearBtn);

        JButton closeBtn = button("Close");
        closeBtn.addActionListener(e -> setVisible(false));

        bar.add(left, BorderLayout.WEST);
        bar.add(closeBtn, BorderLayout.EAST);
        return bar;
    }

    // ─── Import ENV file ──────────────────────────────────────────────────────

    private void importEnvFile() {
        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle("Import Postman Environment File");
        fc.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
                "Postman Environment JSON", "json"));
        if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            File f = fc.getSelectedFile();
            try {
                envManager.importFromPostmanEnvFile(f);
                refresh();
                JOptionPane.showMessageDialog(this,
                        "Imported " + envManager.getAll().size() + " variable(s) from: " + f.getName(),
                        "Import Successful", JOptionPane.INFORMATION_MESSAGE);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this,
                        "Failed to import: " + ex.getMessage(),
                        "Import Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    /** Refreshes the table from the live EnvironmentManager state. */
    public void refresh() {
        tableModel.setRowCount(0);
        for (Map.Entry<String, String> e : envManager.getAll().entrySet()) {
            tableModel.addRow(new Object[]{e.getKey(), e.getValue()});
        }
    }

    private JButton button(String text) {
        JButton b = new JButton(text);
        b.setFont(new Font("SansSerif", Font.PLAIN, 12));
        return b;
    }
}
