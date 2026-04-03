package com.burpext.postmantoburp.model;

/**
 * A named environment displayed in the environment selector.
 * Actual variable storage is in EnvironmentManager.
 */
public class Environment {

    private String name;

    public Environment(String name) {
        this.name = name;
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    @Override
    public String toString() { return name; }
}
