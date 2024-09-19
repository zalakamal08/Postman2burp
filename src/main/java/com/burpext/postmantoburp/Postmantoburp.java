package com.burpext.postmantoburp;

import burp.api.montoya.BurpExtension;
import burp.api.montoya.MontoyaApi;
import java.io.File;

public class Postmantoburp implements BurpExtension {

    private MontoyaApi api;
    private PostmanUI ui;
    private PostmanLogic logic;

    @Override
    public void initialize(MontoyaApi api) {
        this.api = api;
        api.extension().setName("Postman2Burp");
        api.logging().logToOutput("Postmantoburp Initialized");

        // Initialize UI and Logic
        ui = new PostmanUI(api, this);
        logic = new PostmanLogic(api);

        // Register the UI panel
        api.userInterface().registerSuiteTab("Import Postman Collection", ui.getPanel());
    }

    public void loadAndSendPostmanRequests(String filePath) {
        logic.loadAndSendPostmanRequests(filePath);
        ui.setStatus("File imported: " + new File(filePath).getName());
    }
}