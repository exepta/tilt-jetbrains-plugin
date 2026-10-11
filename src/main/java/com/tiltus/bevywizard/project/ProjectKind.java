package com.tiltus.bevywizard.project;

/** The source layout and target platforms of a generated project. */
public enum ProjectKind {
    APP("Binary (App)"), WASM("Binary (WASM)"), BOTH("Binary (WASM + App)"), LIBRARY("Library"), BLANK("Blank");

    private final String label;
    ProjectKind(String label) { this.label = label; }
    /** Returns whether this project kind includes a browser target. */
    public boolean hasWasm() { return this == WASM || this == BOTH; }
    /** Returns whether this project kind creates an executable. */
    public boolean hasBinary() { return this == APP || this == WASM || this == BOTH; }
    /** Returns the label shown in the project wizard. */
    @Override public String toString() { return label; }
}
