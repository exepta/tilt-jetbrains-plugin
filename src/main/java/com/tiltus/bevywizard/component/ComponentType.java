package com.tiltus.bevywizard.component;

/** The kind of TiltUI component to create. */
public enum ComponentType {
    COMPONENT("Component"), ROUTING("Routing");

    private final String label;

    ComponentType(String label) { this.label = label; }

    /** Returns the label displayed in the New Component dialog. */
    @Override public String toString() { return label; }
}
