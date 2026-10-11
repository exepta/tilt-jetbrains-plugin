package com.tiltus.bevywizard.project;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Maintained release contract for the generated TiltUI starter. Update after release validation. */
public final class TiltUiCompatibility {
    public static final String VERSION = "0.1.0";
    public static final List<String> SUPPORTED_VERSIONS = List.of(VERSION, "0.1.0-rc.2");
    public static final String BEVY_SERIES = "0.19";
    public static final String TESTED_BEVY = "0.19.1";
    public static final String BEVY_MINIMUM_RUST = "1.95.0";

    /** Published tilt-ui feature and its wizard documentation. */
    public record Feature(String key, String title, String description, List<String> readmeCrates,
                          boolean requiredByStarter, boolean availableOnWasm) {}

    public static final List<Feature> FEATURES = List.of(
            new Feature("hot-reload", "Hot reload", "Watch UI asset files while developing. Desktop targets only; unavailable for WASM.",
                    List.of("tilt-ui-runtime"), false, false),
            new Feature("fluent", "Fluent localization", "Load .ftl catalogs for translated UI text.",
                    List.of("tilt-ui-runtime"), false, true),
            new Feature("tilt-icons", "Tilt icons", "Use the built-in SVG icon catalog in templates.",
                    List.of("tilt-ui-icons"), false, true),
            new Feature("component", "Components", "Reusable UI components, routing, and widgets. Required by this template.",
                    List.of("tilt-ui", "tilt-ui-build", "tilt-ui-macros", "tilt-ui-runtime"), true, true),
            new Feature("html", "HTML templates", "Parse entry documents and component markup. Included by Components.",
                    List.of("tilt-ui-html"), true, true),
            new Feature("css", "CSS styling", "Parse stylesheets and apply them to Bevy UI. Included by Components.",
                    List.of("tilt-ui-css"), true, true)
    );

    private TiltUiCompatibility() {}

    public static boolean supportsVersion(String version) {
        return SUPPORTED_VERSIONS.contains(version);
    }

    public static boolean isReleaseCandidate(String version) {
        return version != null && version.matches("\\d+\\.\\d+\\.\\d+-rc\\.\\d+");
    }

    /** Shows only releases whose starter contract is maintained by this plugin. */
    public static List<String> visibleVersions(List<String> published, boolean allowRc) {
        return published.stream().filter(TiltUiCompatibility::supportsVersion)
                .filter(version -> allowRc || !isReleaseCandidate(version)).distinct().toList();
    }

    /** This release supports stable patch releases in one Bevy series. */
    public static boolean supportsBevy(String version) {
        return version != null && version.matches(java.util.regex.Pattern.quote(BEVY_SERIES) + "\\.\\d+");
    }

    public static boolean supportsBevy(String tiltUiVersion, String bevyVersion) {
        return supportsVersion(tiltUiVersion) && supportsBevy(bevyVersion);
    }

    /** Includes release candidates when applying the Bevy series' Rust minimum. */
    public static boolean isBevySeries(String version) {
        return version != null && version.matches(java.util.regex.Pattern.quote(BEVY_SERIES)
                + "\\.\\d+(?:-rc\\.\\d+)?");
    }

    public static String unsupportedBevyMessage() {
        return "TiltUI " + VERSION + " supports stable Bevy " + BEVY_SERIES + ".x only.";
    }

    public static String unsupportedBevyMessage(String tiltUiVersion) {
        return "TiltUI " + tiltUiVersion + " supports stable Bevy " + BEVY_SERIES + ".x only.";
    }

    public static boolean requiredByStarter(String key) {
        return FEATURES.stream().anyMatch(feature -> feature.key().equals(key) && feature.requiredByStarter());
    }

    public static boolean availableOn(ProjectKind kind, String key) {
        return FEATURES.stream().anyMatch(feature -> feature.key().equals(key)
                && (!kind.hasWasm() || feature.availableOnWasm()));
    }

    /** Validates requested feature names and removes options unsupported on the chosen target. */
    public static Set<String> featuresFor(ProjectKind kind, Set<String> requested) {
        LinkedHashSet<String> selected = new LinkedHashSet<>();
        for (Feature feature : FEATURES) if (feature.requiredByStarter()) selected.add(feature.key());
        for (String key : requested) {
            if (FEATURES.stream().noneMatch(feature -> feature.key().equals(key)))
                throw new IllegalArgumentException("Unsupported TiltUI " + VERSION + " feature: " + key);
            if (availableOn(kind, key)) selected.add(key);
        }
        return selected;
    }

    public static Set<String> featuresFor(String version, ProjectKind kind, Set<String> requested) {
        if (!supportsVersion(version)) throw new IllegalArgumentException("Unsupported TiltUI release: " + version);
        return featuresFor(kind, requested);
    }
}
