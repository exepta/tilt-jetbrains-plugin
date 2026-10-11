package com.tiltus.bevywizard.component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** Builds a validated set of files for a new TiltUI component. */
public final class ComponentScaffolder {
    private static final String ROUTE_MARKER = "    // tilt-ui:routes";
    private static final Pattern SEGMENT = Pattern.compile("[a-z][a-z0-9_-]*");
    private static final Pattern COMPONENT_STEM = Pattern.compile("[a-z](?:[a-z0-9]|-[a-z0-9])*");
    private static final Set<String> BUILTIN_TAGS = Set.of(("avatar badge body button checkbox select choice-box "
            + "colorpicker color-picker context-menu date-picker dialog div divider fieldset field-set form "
            + "h1 h2 h3 h4 h5 h6 headline a hyperlink img image input label list-box option p paragraph "
            + "icon progressbar progress-bar spinner toast radio radio-button router-outlet scroll scrollbar "
            + "slider switch switch-button table table-cell td th textarea toggle toggle-button tooltip").split(" "));

    private ComponentScaffolder() {}

    /** Describes files to create and existing files to update in one IDE command. */
    public record Plan(Map<Path, String> created, Map<Path, String> updated, Path htmlFile) {}

    /** Returns the target directory for a selection in the project tree. */
    public static Path targetDirectory(Path projectRoot, Path selected) {
        Path root = projectRoot.toAbsolutePath().normalize();
        Path directory = selected.toAbsolutePath().normalize();
        Path ui = root.resolve("src-ui");
        if (directory.startsWith(ui)) return directory;
        Path rust = root.resolve("src");
        if (directory.startsWith(rust)) return ui.resolve(rust.relativize(directory));
        return ui;
    }

    /** Validates a slash-separated component name such as {@code example/example}. */
    public static String validateName(String name) {
        if (name == null || name.isBlank()) return "Enter a component name.";
        String[] segments = name.strip().split("/", -1);
        for (String segment : segments) {
            if (!SEGMENT.matcher(segment).matches())
                return "Use lowercase letters, numbers, '-' or '_' in each path segment; start with a letter.";
        }
        String stem = segments[segments.length - 1];
        if (!COMPONENT_STEM.matcher(stem).matches())
            return "Component names use lowercase letters, numbers, and single hyphens; no trailing hyphen.";
        if (BUILTIN_TAGS.contains(stem)) return "Choose a name that is not a built-in TiltUI element.";
        return null;
    }

    /** Plans a component without changing the project, and rejects conflicting paths. */
    public static Plan plan(Path srcUi, Path selectedDirectory, String name, ComponentType type) throws IOException {
        String validation = validateName(name);
        if (validation != null) throw new IOException(validation);
        Path ui = srcUi.toAbsolutePath().normalize();
        Path selected = selectedDirectory.toAbsolutePath().normalize();
        if (!Files.isDirectory(ui) || !selected.startsWith(ui))
            throw new IOException("Choose a directory inside a TiltUI project's src-ui folder.");

        String[] parts = name.strip().split("/");
        String stem = parts[parts.length - 1];
        try (var existing = Files.walk(ui)) {
            if (existing.anyMatch(path -> path.getFileName().toString().equals(stem + ".component.rs")))
                throw new IOException("A TiltUI component named '" + stem + "' already exists in src-ui.");
        }
        Path directory = selected;
        for (int i = 0; i < parts.length - 1; i++) directory = directory.resolve(parts[i]);
        String title = title(stem);
        LinkedHashMap<Path, String> created = new LinkedHashMap<>();
        created.put(directory.resolve(stem + ".component.html"), componentHtml(title, type));
        created.put(directory.resolve(stem + ".component.css"), componentCss());
        created.put(directory.resolve(stem + ".component.rs"), componentRust(title));

        LinkedHashMap<Path, String> updated = new LinkedHashMap<>();
        if (type == ComponentType.ROUTING) {
            Path registry = ui.resolve("routers.rs");
            boolean firstRoute = !Files.exists(registry);
            String existingRegistry = firstRoute ? null : Files.readString(registry);
            boolean managedRegistry = firstRoute || existingRegistry.contains(ROUTE_MARKER);
            Path routeFile = directory.resolve(stem + ".route.rs");
            created.put(routeFile, routeRust(stem, name.strip(), firstRoute, !managedRegistry));
            String relative = ui.relativize(routeFile).toString().replace('\\', '/');
            String module = "route_" + relative.replaceAll("[^A-Za-z0-9]", "_").replaceAll("_+", "_")
                    + "_" + Integer.toUnsignedString(relative.hashCode(), 36);
            String declaration = "#[path = \"" + relative + "\"]\nmod " + module + ";\n";
            if (firstRoute) created.put(registry, "//! Central TiltUI route registry.\n\n"
                    + "use tilt_ui::{Routes, ui_routes};\n\n" + declaration + "\n"
                    + "#[ui_routes]\npub fn app_routes() -> Routes {\n    " + module + "::routes()\n"
                    + ROUTE_MARKER + "\n}\n");
            else {
                if (existingRegistry.contains("#[path = \"" + relative + "\"]"))
                    throw new IOException("The route is already registered in src-ui/routers.rs.");
                if (managedRegistry) {
                    String withModule = existingRegistry.replace("#[ui_routes]", declaration + "\n#[ui_routes]");
                    updated.put(registry, withModule.replace(ROUTE_MARKER,
                            "        .merge(" + module + "::routes())\n" + ROUTE_MARKER));
                } else {
                    updated.put(registry, existingRegistry.stripTrailing() + "\n\n" + declaration);
                }
            }
            planOutlet(ui, updated);
        }
        for (Path path : created.keySet()) {
            if (Files.exists(path)) throw new IOException("The file already exists: " + path.getFileName());
        }
        return new Plan(Collections.unmodifiableMap(created), Collections.unmodifiableMap(updated),
                directory.resolve(stem + ".component.html"));
    }

    /** Adds an outlet to the generated starter page, or to the entry document for a custom page. */
    private static void planOutlet(Path ui, Map<Path, String> updated) throws IOException {
        Path main = ui.resolve("pages/main.component.html");
        Path entry = ui.resolve("index.html");
        if (Files.isRegularFile(main)) {
            String html = Files.readString(main);
            if (html.contains("<router-outlet")) return;
            if (html.contains("<div class=\"welcome\">")) {
                int end = html.lastIndexOf("</div>");
                if (end >= 0) {
                    updated.put(main, html.substring(0, end)
                            + "  <div class=\"route-outlet\"><router-outlet></router-outlet></div>\n" + html.substring(end));
                    Path css = ui.resolve("pages/main.component.css");
                    if (Files.isRegularFile(css)) {
                        String styles = Files.readString(css);
                        if (!styles.contains(".route-outlet"))
                            updated.put(css, styles.stripTrailing()
                                    + "\n.route-outlet { display: flex; flex-direction: column; width: 100%; }\n");
                    }
                    return;
                }
            }
        }
        if (!Files.isRegularFile(entry)) throw new IOException("src-ui/index.html is missing.");
        String html = Files.readString(entry);
        if (!html.contains("<router-outlet")) {
            int end = html.indexOf("</body>");
            if (end < 0) throw new IOException("src-ui/index.html has no closing body tag for the router outlet.");
            updated.put(entry, html.substring(0, end)
                    + "  <router-outlet></router-outlet>\n" + html.substring(end));
        }
    }

    /** Creates markup that can be mounted immediately by tag name. */
    private static String componentHtml(String title, ComponentType type) {
        String role = type == ComponentType.ROUTING ? "ROUTE" : "COMPONENT";
        String state = title.replace(" ", "") + "State";
        return "@use \"" + state + "\" as component;\n"
                + "@let introduction = component.message;\n"
                + "<div class=\"tilt-component\">\n"
                + "  <p class=\"eyebrow\">TILT UI / " + role + "</p>\n"
                + "  <h2>" + title + "</h2>\n"
                + "  @if (component.ready) {\n"
                + "    <p>{{ introduction }}</p>\n"
                + "  } @else {\n"
                + "    <p>Preparing this component...</p>\n"
                + "  }\n"
                + "  @for (step in component.steps; track step) {\n"
                + "    <p class=\"step\">{{ step }}</p>\n"
                + "  }\n"
                + "</div>\n";
    }

    /** Creates a compact light theme using properties supported by TiltUI 0.1.0. */
    private static String componentCss() {
        return """
                .tilt-component {
                    display: flex;
                    flex-direction: column;
                    gap: 12px;
                    width: 100%;
                    min-width: 0px;
                    padding: 24px;
                    border: 1px solid #dfe4ef;
                    border-radius: 14px;
                    background-color: #ffffff;
                }
                .tilt-component .eyebrow { color: #6554c0; font-size: 12px; font-weight: 700; }
                .tilt-component h2 { color: #1c2740; font-size: 26px; font-weight: 700; }
                .tilt-component p { color: #54627a; font-size: 14px; }
                .tilt-component .step { color: #66748b; font-size: 13px; }
                """;
    }

    /** Creates working state for the starter directives and room for event handlers. */
    private static String componentRust(String title) {
        String state = title.replace(" ", "") + "State";
        return "//! Logic for the " + title + " TiltUI component.\n\n"
                + "use bevy::prelude::*;\n"
                + "use tilt_ui::{component_init, html_shared};\n\n"
                + "#[html_shared]\n"
                + "#[derive(Resource, tilt_ui::serde::Serialize)]\n"
                + "#[serde(crate = \"tilt_ui::serde\")]\n"
                + "struct " + state + " {\n"
                + "    ready: bool,\n"
                + "    message: String,\n"
                + "    steps: Vec<String>,\n"
                + "}\n\n"
                + "#[component_init]\n"
                + "fn initialize_" + title.toLowerCase().replace(' ', '_') + "(mut commands: Commands) {\n"
                + "    commands.insert_resource(" + state + " {\n"
                + "        ready: true,\n"
                + "        message: \"Edit this component's HTML, CSS, and Rust files to build your interface.\".into(),\n"
                + "        steps: vec![\"Replace this example with your own content.\".into()],\n"
                + "    });\n"
                + "}\n\n"
                + "// Add #[html_fn] event handlers here.\n";
    }

    /** Registers a route and gives the first route a root alias for immediate preview. */
    private static String routeRust(String stem, String path, boolean first, boolean legacyRegistry) {
        String route = "/" + path;
        return (legacyRegistry ? "use tilt_ui::{Routes, ui_routes};\n\n#[ui_routes]\npub fn routes() -> Routes {\n"
                : "use tilt_ui::Routes;\n\npub(super) fn routes() -> Routes {\n")
                + "    let page = super::super::tilt_ui_component_id(\"" + stem + "\")\n"
                + "        .expect(\"Missing TiltUI component: " + stem + "\");\n"
                + "    Routes::new()\n"
                + (first ? "        .route(\"/\", page)\n" : "")
                + "        .route(\"" + route + "\", page)\n"
                + "}\n";
    }

    /** Converts a component stem to a readable starter heading. */
    private static String title(String stem) {
        StringBuilder result = new StringBuilder();
        for (String word : stem.split("[-_]")) {
            if (word.isEmpty()) continue;
            if (!result.isEmpty()) result.append(' ');
            result.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return result.toString();
    }
}
