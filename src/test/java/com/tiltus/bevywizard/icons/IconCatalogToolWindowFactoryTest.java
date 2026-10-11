package com.tiltus.bevywizard.icons;

import org.junit.jupiter.api.Test;

import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.*;

/** Checks that the bundled gallery matches TiltUI's complete icon catalog. */
final class IconCatalogToolWindowFactoryTest {
    @Test void detectsIconFeatureAndDirectCrate() {
        assertTrue(IconCatalogToolWindowFactory.usesIcons("tilt-ui = { version = \"0.1.0\", default-features = false, features = [\"component\", \"tilt-icons\"] }"));
        assertTrue(IconCatalogToolWindowFactory.usesIcons("tilt-ui-icons = \"0.1.0\""));
        assertTrue(IconCatalogToolWindowFactory.usesIcons("[dependencies.tilt-ui]\nversion = \"0.1.0\"\nfeatures = [\"tilt-icons\"]"));
        assertFalse(IconCatalogToolWindowFactory.usesIcons("tilt-ui = { version = \"0.1.0\", features = [\"component\"] }"));
        assertFalse(IconCatalogToolWindowFactory.usesIcons("tilt-ui = \"0.1.0\"\n# features = [\"tilt-icons\"]"));
    }

    @Test void catalogContainsEveryDistinctIconWithPreview() {
        var icons = IconCatalogToolWindowFactory.icons();
        assertEquals(537, icons.size());
        var names = new HashSet<String>();
        for (var item : icons) {
            var icon = item.getAsJsonObject();
            assertTrue(names.add(icon.get("name").getAsString()));
            assertFalse(icon.get("group").getAsString().isBlank());
            assertTrue(icon.get("svg").getAsString().startsWith("<svg "));
            assertTrue(icon.get("svg").getAsString().endsWith("</svg>"));
        }
        assertTrue(names.contains("home"));
        assertTrue(names.contains("bevy"));
    }
}
