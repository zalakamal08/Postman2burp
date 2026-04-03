package com.burpext.postmantoburp;

import burp.api.montoya.BurpExtension;
import burp.api.montoya.MontoyaApi;
import com.burpext.postmantoburp.logic.EnvironmentManager;
import com.burpext.postmantoburp.ui.MainPanel;

/**
 * Burp Suite extension entry point.
 * Registers the "Postman2Burp" tab in Burp's suite.
 */
public class Postmantoburp implements BurpExtension {

    @Override
    public void initialize(MontoyaApi api) {
        api.extension().setName("Postman2Burp");
        api.logging().logToOutput("Postman2Burp loaded successfully.");
        api.logging().logToOutput("Features: Postman Collection importer · cURL parser · OpenAPI v3 loader · Environment Manager");

        EnvironmentManager envManager = new EnvironmentManager();
        MainPanel mainPanel = new MainPanel(api, envManager);

        api.userInterface().registerSuiteTab("Postman2Burp", mainPanel.getPanel());
    }
}