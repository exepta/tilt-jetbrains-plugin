package com.tiltus.bevywizard.project;

import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TiltUiCompatibilityTest {
    @Test void releaseSupportsOnlyStablePatchVersionsOfItsBevySeries() {
        assertTrue(TiltUiCompatibility.supportsBevy("0.19.0"));
        assertTrue(TiltUiCompatibility.supportsBevy("0.19.1"));
        assertFalse(TiltUiCompatibility.supportsBevy("0.19.2-rc.1"));
        assertFalse(TiltUiCompatibility.supportsBevy("0.20.0"));
        assertTrue(TiltUiCompatibility.isBevySeries("0.19.2-rc.1"));
    }

    @Test void starterFeaturesAreValidatedAndTargetAware() {
        assertEquals(Set.of("component", "html", "css", "fluent", "tilt-icons"),
                TiltUiCompatibility.featuresFor(ProjectKind.WASM,
                        Set.of("fluent", "hot-reload", "tilt-icons")));
        assertTrue(TiltUiCompatibility.featuresFor(ProjectKind.APP, Set.of("hot-reload"))
                .contains("hot-reload"));
        assertThrows(IllegalArgumentException.class,
                () -> TiltUiCompatibility.featuresFor(ProjectKind.APP, Set.of("unknown-feature")));
    }

    @Test void onlyMaintainedPublishedVersionsCanBeSelected() {
        assertTrue(TiltUiCompatibility.supportsVersion("0.1.0"));
        assertTrue(TiltUiCompatibility.supportsVersion("0.1.0-rc.2"));
        assertFalse(TiltUiCompatibility.supportsVersion("0.2.0"));
        assertEquals(List.of("0.1.0"), TiltUiCompatibility.visibleVersions(
                List.of("0.2.0", "0.1.0", "0.1.0-rc.2"), false));
        assertEquals(List.of("0.1.0", "0.1.0-rc.2"), TiltUiCompatibility.visibleVersions(
                List.of("0.2.0", "0.1.0", "0.1.0-rc.2"), true));
        assertThrows(IllegalArgumentException.class, () -> TiltUiCompatibility.featuresFor(
                "0.2.0", ProjectKind.APP, Set.of()));
    }
}
