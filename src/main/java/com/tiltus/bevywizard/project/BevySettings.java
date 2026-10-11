package com.tiltus.bevywizard.project;

import com.tiltus.bevywizard.catalog.CrateAsset;

import java.util.List;
import java.util.Set;

/** The choices collected by the Bevy project wizard. */
public record BevySettings(String bevyVersion, String minimumRustVersion, String rustToolchain,
                           String installedRustVersion, ProjectKind kind, boolean tiltUi, boolean useRouting, boolean useJust,
                           Set<String> tiltFeatures, List<CrateAsset> crates, boolean initializeGit, boolean includeGitignore,
                           String tiltUiVersion) {
    public BevySettings {
        if (tiltUiVersion == null) tiltUiVersion = TiltUiCompatibility.VERSION;
    }

    /** Keeps existing callers on the maintained stable TiltUI release. */
    public BevySettings(String bevyVersion, String minimumRustVersion, String rustToolchain,
                        String installedRustVersion, ProjectKind kind, boolean tiltUi, boolean useRouting, boolean useJust,
                        Set<String> tiltFeatures, List<CrateAsset> crates, boolean initializeGit, boolean includeGitignore) {
        this(bevyVersion, minimumRustVersion, rustToolchain, installedRustVersion, kind, tiltUi, useRouting,
                useJust, tiltFeatures, crates, initializeGit, includeGitignore, TiltUiCompatibility.VERSION);
    }

    public BevySettings withTiltUiVersion(String version) {
        return new BevySettings(bevyVersion, minimumRustVersion, rustToolchain, installedRustVersion, kind,
                tiltUi, useRouting, useJust, tiltFeatures, crates, initializeGit, includeGitignore, version);
    }
    /** Keeps older callers equivalent to leaving Git initialization unchecked. */
    public BevySettings(String bevyVersion, String minimumRustVersion, String rustToolchain,
                        String installedRustVersion, ProjectKind kind, boolean tiltUi, boolean useRouting, boolean useJust,
                        Set<String> tiltFeatures, List<CrateAsset> crates) {
        this(bevyVersion, minimumRustVersion, rustToolchain, installedRustVersion,
                kind, tiltUi, useRouting, useJust, tiltFeatures, crates, false, false);
    }
    /** Keeps older callers equivalent to leaving the routing checkbox unchecked. */
    public BevySettings(String bevyVersion, String minimumRustVersion, String rustToolchain,
                        String installedRustVersion, ProjectKind kind, boolean tiltUi,
                        Set<String> tiltFeatures, List<CrateAsset> crates) {
        this(bevyVersion, minimumRustVersion, rustToolchain, installedRustVersion,
                kind, tiltUi, false, false, tiltFeatures, crates, false, false);
    }

    /** Keeps callers that specify routing equivalent to leaving Just unchecked. */
    public BevySettings(String bevyVersion, String minimumRustVersion, String rustToolchain,
                        String installedRustVersion, ProjectKind kind, boolean tiltUi, boolean useRouting,
                        Set<String> tiltFeatures, List<CrateAsset> crates) {
        this(bevyVersion, minimumRustVersion, rustToolchain, installedRustVersion,
                kind, tiltUi, useRouting, false, tiltFeatures, crates, false, false);
    }
}
