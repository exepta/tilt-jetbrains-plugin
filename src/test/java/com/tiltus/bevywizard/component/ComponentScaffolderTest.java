package com.tiltus.bevywizard.component;

import com.tiltus.bevywizard.project.BevySettings;
import com.tiltus.bevywizard.project.ProjectKind;
import com.tiltus.bevywizard.project.ProjectScaffolder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ComponentScaffolderTest {
    @TempDir Path temp;

    @Test void createsNestedComponentFilesWithoutChangingTheEntryPage() throws Exception {
        Path project = starter();
        Path ui = project.resolve("src-ui");
        var plan = ComponentScaffolder.plan(ui, ui, "example/example", ComponentType.COMPONENT);
        Path folder = ui.resolve("example");
        assertEquals(folder.resolve("example.component.html"), plan.htmlFile());
        assertEquals(Set.of(folder.resolve("example.component.html"), folder.resolve("example.component.css"),
                folder.resolve("example.component.rs")), plan.created().keySet());
        assertTrue(plan.updated().isEmpty());
        assertTrue(plan.created().get(plan.htmlFile()).contains("<h2>Example</h2>"));
        for (String directive : List.of("@use", "@let", "@if", "@else", "@for"))
            assertTrue(plan.created().get(plan.htmlFile()).contains(directive), directive);
        assertTrue(plan.created().get(folder.resolve("example.component.rs")).contains("struct ExampleState"));
        assertTrue(plan.created().get(folder.resolve("example.component.css")).contains("flex-direction: column"));
    }

    @Test void routingRegistersItsModuleAndAddsOneOutlet() throws Exception {
        Path project = starter();
        Path ui = project.resolve("src-ui");
        var first = ComponentScaffolder.plan(ui, ui, "example/example", ComponentType.ROUTING);
        Path route = ui.resolve("example/example.route.rs");
        assertTrue(first.created().get(route).contains(".route(\"/example/example\", page)"));
        assertTrue(first.created().get(route).contains(".route(\"/\", page)"));
        assertTrue(first.created().get(ui.resolve("routers.rs")).contains("#[path = \"example/example.route.rs\"]"));
        assertTrue(first.created().get(ui.resolve("routers.rs")).contains("#[ui_routes]"));
        assertFalse(first.created().get(route).contains("#[ui_routes]"));
        assertTrue(first.updated().get(ui.resolve("pages/main.component.html")).contains("<router-outlet></router-outlet>"));
        write(first);

        var second = ComponentScaffolder.plan(ui, ui, "settings", ComponentType.ROUTING);
        assertTrue(second.updated().get(ui.resolve("routers.rs")).contains("#[path = \"settings.route.rs\"]"));
        assertFalse(second.created().get(ui.resolve("settings.route.rs")).contains(".route(\"/\", page)"));
        assertFalse(second.updated().containsKey(ui.resolve("pages/main.component.html")));
        assertTrue(second.updated().get(ui.resolve("routers.rs")).contains(".merge("));
    }

    @Test void extendsWizardRoutingRegistryWithoutAddingAnotherRootRoute() throws Exception {
        Path project = temp.resolve("routing-starter");
        ProjectScaffolder.create(project, new BevySettings("0.19.1", "1.95.0", "1.95.0", "1.95.0",
                ProjectKind.APP, true, true, Set.of(), List.of()));
        Path ui = project.resolve("src-ui");
        var plan = ComponentScaffolder.plan(ui, ui, "example/example", ComponentType.ROUTING);
        String route = plan.created().get(ui.resolve("example/example.route.rs"));
        String registry = plan.updated().get(ui.resolve("routers.rs"));
        assertTrue(route.contains(".route(\"/example/example\", page)"));
        assertFalse(route.contains(".route(\"/\", page)"));
        assertFalse(route.contains("#[ui_routes]"));
        assertTrue(registry.contains("#[path = \"example/example.route.rs\"]"));
        assertTrue(registry.contains(".merge("));
        assertTrue(registry.contains("home::routes()"));
        assertFalse(plan.updated().containsKey(ui.resolve("pages/main.component.html")));
    }

    @Test void validatesNamesAndExistingFiles() throws Exception {
        Path project = starter();
        Path ui = project.resolve("src-ui");
        assertNull(ComponentScaffolder.validateName("example/example"));
        assertNotNull(ComponentScaffolder.validateName("../outside"));
        assertNotNull(ComponentScaffolder.validateName("Example"));
        assertNotNull(ComponentScaffolder.validateName("a//b"));
        assertNotNull(ComponentScaffolder.validateName("example_name"));
        assertNotNull(ComponentScaffolder.validateName("bad--name"));
        assertNotNull(ComponentScaffolder.validateName("button"));
        assertThrows(Exception.class, () -> ComponentScaffolder.plan(ui, ui, "../outside", ComponentType.COMPONENT));
        assertThrows(Exception.class, () -> ComponentScaffolder.plan(ui, ui.resolve("pages"), "main", ComponentType.COMPONENT));
        assertThrows(Exception.class, () -> ComponentScaffolder.plan(ui, ui.resolve("example"), "main", ComponentType.COMPONENT));
        assertEquals(ui.resolve("feature"), ComponentScaffolder.targetDirectory(project, project.resolve("src/feature")));
    }

    private Path starter() throws Exception {
        Path project = temp.resolve("starter");
        ProjectScaffolder.create(project, new BevySettings("0.19.1", "1.95.0", "1.95.0", "1.95.0",
                ProjectKind.APP, true, Set.of(), List.of()));
        return project;
    }

    private static void write(ComponentScaffolder.Plan plan) throws Exception {
        for (Map.Entry<Path, String> entry : plan.created().entrySet()) {
            Files.createDirectories(entry.getKey().getParent());
            Files.writeString(entry.getKey(), entry.getValue());
        }
        for (Map.Entry<Path, String> entry : plan.updated().entrySet())
            Files.writeString(entry.getKey(), entry.getValue());
    }
}
