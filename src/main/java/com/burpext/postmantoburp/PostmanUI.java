package com.burpext.postmantoburp;

import burp.api.montoya.MontoyaApi;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;

public class PostmanUI {

    private JPanel panel;
    private JButton loadButton;
    private JLabel statusLabel;
    private MontoyaApi api;
    private Postmantoburp extension;

    public PostmanUI(MontoyaApi api, Postmantoburp extension) {
        this.api = api;
        this.extension = extension;
        panel = createUIPanel();
    }

    public JPanel getPanel() {
        return panel;
    }

    private JPanel createUIPanel() {
        // Create the panel
        JPanel panel = new JPanel();
        panel.setLayout(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // Import button
        loadButton = new JButton("Import Postman Collection");
        loadButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                JFileChooser fileChooser = new JFileChooser();
                
                // Set default directory to Downloads folder
                File downloadsFolder = new File(System.getProperty("user.home"), "Downloads");
                fileChooser.setCurrentDirectory(downloadsFolder);
                fileChooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("JSON Files", "json"));

                int returnValue = fileChooser.showOpenDialog(null);

                if (returnValue == JFileChooser.APPROVE_OPTION) {
                    File selectedFile = fileChooser.getSelectedFile();
                    // Use a thread to keep the UI responsive
                    new Thread(() -> {
                        extension.loadAndSendPostmanRequests(selectedFile.getAbsolutePath());
                        SwingUtilities.invokeLater(() -> statusLabel.setText("File imported: " + selectedFile.getName()));
                    }).start();
                }
            }
        });

        // Status label
        statusLabel = new JLabel("");

        // Layout setup
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 1;
        gbc.anchor = GridBagConstraints.CENTER;
        panel.add(loadButton, gbc);

        gbc.gridy = 1;
        panel.add(statusLabel, gbc);

        return panel;
    }

    public void setStatus(String message) {
        statusLabel.setText(message);
    }
}