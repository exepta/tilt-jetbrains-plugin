package com.tiltus.bevywizard.project;

import com.tiltus.bevywizard.catalog.BevyCompatibility;
import com.tiltus.bevywizard.catalog.CrateAsset;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** Writes validated Cargo, TiltUI, Trunk, Just, and README files. */
public final class ProjectScaffolder {
    private static final Pattern CRATE_NAME = Pattern.compile("[A-Za-z][A-Za-z0-9_-]*");
    private ProjectScaffolder() {}

    /** Validates wizard settings and writes a complete Bevy project. */
    public static void create(Path directory, BevySettings options) throws IOException {
        boolean withUi = options.tiltUi() && options.kind().hasBinary();
        if (options.useRouting() && !withUi)
            throw new IOException("Routing requires a binary project with TiltUI enabled.");
        String packageName = directory.getFileName().toString().toLowerCase().replaceAll("[^a-z0-9_-]", "-");
        if (!CRATE_NAME.matcher(packageName).matches()) {
            throw new IOException("Project name must start with a letter and contain only letters, numbers, - or _.");
        }
        if (!options.bevyVersion().matches("\\d+\\.\\d+\\.\\d+(?:-rc\\.\\d+)?")) {
            throw new IOException("Choose a published Bevy version.");
        }
        if (withUi && !TiltUiCompatibility.supportsVersion(options.tiltUiVersion()))
            throw new IOException("Unsupported TiltUI release: " + options.tiltUiVersion());
        if (withUi && !TiltUiCompatibility.supportsBevy(options.tiltUiVersion(), options.bevyVersion())) {
            throw new IOException(TiltUiCompatibility.unsupportedBevyMessage(options.tiltUiVersion()));
        }
        if (!RustToolchains.validChannel(options.rustToolchain()) ||
                options.minimumRustVersion() == null || !options.minimumRustVersion().matches("\\d+\\.\\d+\\.\\d+") ||
                options.installedRustVersion() == null || !options.installedRustVersion().matches("\\d+\\.\\d+\\.\\d+") ||
                RustToolchains.compare(options.minimumRustVersion(), RustToolchains.EDITION_MINIMUM) < 0 ||
                (TiltUiCompatibility.isBevySeries(options.bevyVersion()) &&
                        RustToolchains.compare(options.minimumRustVersion(), TiltUiCompatibility.BEVY_MINIMUM_RUST) < 0) ||
                RustToolchains.compare(options.installedRustVersion(), options.minimumRustVersion()) < 0) {
            throw new IOException("Choose an installed Rust toolchain that meets Bevy's minimum Rust version.");
        }
        if (Files.exists(directory.resolve("Cargo.toml"))) {
            throw new IOException("Cargo.toml already exists in the target folder.");
        }

        Map<String, String> files = new LinkedHashMap<>();
        Set<String> uiFeatures = new LinkedHashSet<>();
        if (withUi) {
            try { uiFeatures.addAll(TiltUiCompatibility.featuresFor(
                    options.tiltUiVersion(), options.kind(), options.tiltFeatures())); }
            catch (IllegalArgumentException error) { throw new IOException(error.getMessage(), error); }
        }
        StringBuilder cargo = new StringBuilder("[package]\nname = \"").append(packageName)
                .append("\"\nversion = \"0.1.0\"\nedition = \"2024\"\nrust-version = \"")
                .append(options.minimumRustVersion()).append("\"\n");
        if (withUi) cargo.append("build = \"build.rs\"\n");
        cargo.append("\n[dependencies]\n");
        String bevyDependencyVersion = "=" + options.bevyVersion();
        if (!withUi) {
            cargo.append("bevy = \"").append(bevyDependencyVersion).append("\"\n");
        }
        if (withUi) {
            files.put("build.rs", "fn main() {\n    tilt_ui_build::build().expect(\"TiltUI component discovery failed\");\n}\n");
            files.put("src-ui/index.html", landingDocument());
            files.put("src-ui/styles/site.css", landingStyles());
            files.put("src-ui/pages/main.component.html", mainComponent(uiFeatures.contains("tilt-icons"), options.useRouting()));
            files.put("src-ui/pages/main.component.css", mainComponentStyles(options.useRouting()));
            files.put("src-ui/pages/main.component.rs", mainComponentRust(options.useRouting()));
            if (options.useRouting()) {
                files.put("src-ui/pages/home.component.html", """
                        <div class="home-route">
                          <h2>Home route</h2>
                          <p>This page is rendered by TiltUI routing. Edit home.component.html to get started.</p>
                        </div>
                        """);
                files.put("src-ui/pages/home.component.css", """
                        .home-route {
                            display: flex;
                            flex-direction: column;
                            gap: 10px;
                            width: 100%;
                            padding: 20px;
                            border: 1px solid #dfe4ef;
                            border-radius: 12px;
                            background-color: #f8f9fd;
                        }
                        .home-route h2 { color: #1c2740; font-size: 22px; font-weight: 700; }
                        .home-route p { color: #54627a; font-size: 14px; }
                        """);
                files.put("src-ui/pages/home.component.rs", "//! State and handlers for the home route.\n");
                files.put("src-ui/pages/about.component.html", """
                        <div class="about-route">
                          <h2>About route</h2>
                          <p>You made it to the second page. Use the Home button to return.</p>
                        </div>
                        """);
                files.put("src-ui/pages/about.component.css", """
                        .about-route {
                            display: flex;
                            flex-direction: column;
                            gap: 10px;
                            width: 100%;
                            padding: 20px;
                            border: 1px solid #dfe4ef;
                            border-radius: 12px;
                            background-color: #f8f9fd;
                        }
                        .about-route h2 { color: #1c2740; font-size: 22px; font-weight: 700; }
                        .about-route p { color: #54627a; font-size: 14px; }
                        """);
                files.put("src-ui/pages/about.component.rs", "//! State and handlers for the about route.\n");
                files.put("src-ui/routes/home.route.rs", """
                        use tilt_ui::Routes;

                        pub(super) fn routes() -> Routes {
                            let home = super::super::tilt_ui_component_id("home")
                                .expect("Missing TiltUI home component");
                            let about = super::super::tilt_ui_component_id("about")
                                .expect("Missing TiltUI about component");
                            Routes::new().route("/", home).route("/home", home).route("/about", about)
                        }
                        """);
                files.put("src-ui/routers.rs", """
                        //! Central route registry. Add new route modules through New → Tilt Component.

                        use tilt_ui::{Routes, ui_routes};

                        #[path = "routes/home.route.rs"]
                        mod home;

                        #[ui_routes]
                        pub fn app_routes() -> Routes {
                            home::routes()
                            // tilt-ui:routes
                        }
                        """);
            }
        }
        if (!options.crates().isEmpty()) {
            cargo.append("\n# Community crates selected from https://bevy.org/assets/\n");
            Set<String> crateNames = new LinkedHashSet<>();
            for (CrateAsset asset : options.crates()) {
                if (!asset.hasVerifiedIdentity())
                    throw new IOException("The crates.io package name for " + asset.title() + " is not verified.");
                if (!CRATE_NAME.matcher(asset.name()).matches() || asset.version() == null || !asset.version().matches("\\d+\\.\\d+\\.\\d+(?:[-+][A-Za-z0-9.]+)?")) {
                    throw new IOException("No stable crates.io release is available for " + asset.title() + ".");
                }
                if (!crateNames.add(asset.name().replace('-', '_').toLowerCase(java.util.Locale.ROOT)))
                    throw new IOException("The crate " + asset.name() + " was selected more than once.");
                if (!BevyCompatibility.supports(options.bevyVersion(), asset.bevyTags()))
                    throw new IOException(asset.title() + " does not support Bevy " + options.bevyVersion() + ".");
                if (asset.name().equals("lightyear") && options.bevyVersion().matches("0\\.19\\.\\d+")
                        && !asset.version().matches("0\\.28\\.\\d+"))
                    throw new IOException("Lightyear 0.28.x is selected for Bevy 0.19 projects.");
                cargo.append(asset.name()).append(" = \"=").append(asset.version()).append("\"\n");
            }
        }
        if (withUi) {
            appendDependencyTable(cargo, "dependencies.bevy", bevyDependencyVersion, true,
                    List.of("bevy_core_pipeline", "bevy_ui_render", "bevy_winit", "default_font", "png", "x11", "wayland"));
            appendDependencyTable(cargo, "dependencies.tilt-ui", "=" + options.tiltUiVersion(), true, uiFeatures);
        }
        if (withUi) cargo.append("\n[build-dependencies]\ntilt-ui-build = \"=")
                .append(options.tiltUiVersion()).append("\"\n");
        files.put("Cargo.toml", cargo.toString());
        files.put("rust-toolchain.toml", "[toolchain]\nchannel = \"" + options.rustToolchain() + "\"\n");
        if (options.initializeGit() && options.includeGitignore())
            files.put(".gitignore", GitProjectSetup.ignoreFile(options.kind()));
        if (options.kind() == ProjectKind.LIBRARY) {
            files.put("src/lib.rs", "pub fn hello() -> &'static str {\n    \"Hello, Bevy!\"\n}\n");
        } else if (options.kind().hasBinary()) {
            String uiRoot = options.kind().hasWasm()
                    ? "#[cfg(target_arch = \"wasm32\")]\nconst UI_SOURCE_ROOT: &str = \"src-ui\";\n#[cfg(not(target_arch = \"wasm32\"))]\nconst UI_SOURCE_ROOT: &str = concat!(env!(\"CARGO_MANIFEST_DIR\"), \"/src-ui\");\n\n"
                    : "const UI_SOURCE_ROOT: &str = concat!(env!(\"CARGO_MANIFEST_DIR\"), \"/src-ui\");\n\n";
            String main = withUi
                    ? "use bevy::prelude::*;\nuse tilt_ui::prelude::*;\n\ntilt_ui::include_components!();\n\n" + uiRoot + "fn main() {\n    App::new()\n        .add_plugins(TiltUiPlugin::new(tilt_ui_component_catalog())\n            .with_source_root(UI_SOURCE_ROOT))\n        .add_plugins(DefaultPlugins)\n        .run();\n}\n"
                    : "use bevy::prelude::*;\n\nfn main() {\n    App::new().add_plugins(DefaultPlugins).run();\n}\n";
            files.put("src/main.rs", main);
        } else if (options.kind() == ProjectKind.BLANK) {
            files.put("src/lib.rs", "// Add your Bevy code here.\n");
        }
        if (options.kind().hasWasm()) {
            if (withUi) {
                appendDependencyTable(cargo, "target.'cfg(target_arch = \"wasm32\")'.dependencies.bevy_render",
                        TiltUiCompatibility.BEVY_SERIES, true, List.of("webgl", "webgpu"));
                files.put("Cargo.toml", cargo.toString());
            }
            String copy = withUi ? "    <link data-trunk rel=\"copy-dir\" href=\"src-ui\">\n" : "";
            files.put("index.html", "<!doctype html>\n<html lang=\"en\">\n<head>\n    <meta charset=\"utf-8\">\n    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">\n    <title>" + packageName + "</title>\n    <link data-trunk rel=\"rust\">\n" + copy + "</head>\n<body></body>\n</html>\n");
            files.put("Trunk.toml", "[build]\ntarget = \"index.html\"\ndist = \"dist\"\n");
        }
        if (options.kind().hasBinary() && options.useJust()) files.put("justfile", justfile(options.kind()));
        files.put("README.md", projectReadme(packageName, options, withUi, uiFeatures));

        for (Map.Entry<String, String> file : files.entrySet()) {
            Path target = directory.resolve(file.getKey());
            Files.createDirectories(target.getParent());
            Files.writeString(target, file.getValue(), StandardCharsets.UTF_8);
        }
        if (options.initializeGit()) GitProjectSetup.initialize(directory);
    }

    /** Writes a readable Cargo dependency table with one feature per line. */
    private static void appendDependencyTable(StringBuilder cargo, String section, String version,
                                              boolean disableDefaults, Iterable<String> features) {
        cargo.append("\n[").append(section).append("]\nversion = \"").append(version).append("\"\n");
        if (disableDefaults) cargo.append("default-features = false\n");
        cargo.append("features = [\n");
        for (String feature : features) cargo.append("    \"").append(feature).append("\",\n");
        cargo.append("]\n");
    }

    /** Builds the TiltUI entry document. */
    private static String landingDocument() {
        return """
                <!doctype html>
                <html lang="en">
                <head>
                  <meta charset="utf-8">
                  <title>Tilt UI</title>
                  <link rel="stylesheet" href="styles/site.css">
                </head>
                <body>
                  <div class="page-shell"><main></main></div>
                </body>
                </html>
                """;
    }

    /** Builds the global light theme and centered page shell. */
    private static String landingStyles() {
        return """
                body {
                    width: 100%;
                    height: 100%;
                    align-items: center;
                    justify-content: center;
                    background-color: #f4f6fb;
                    color: #1c2740;
                }
                .page-shell {
                    display: flex;
                    flex-direction: column;
                    align-items: center;
                    justify-content: center;
                    width: 100%;
                    min-width: 0px;
                }
                main {
                    display: flex;
                    flex-direction: column;
                    align-items: center;
                    justify-content: center;
                    width: 100%;
                    min-width: 0px;
                    padding: 24px;
                }
                """;
    }

    /** Builds the starter component and optional link icons. */
    private static String mainComponent(boolean icons, boolean routing) {
        return """
                @use "StarterView" as starter;
                @let tagline = starter.subtitle;
                <div class="welcome">
                  <p class="eyebrow">YOUR BEVY STARTER</p>
                  <h1>Tilt UI</h1>
                  @if (starter.ready) {
                    <p class="subtitle">{{ tagline }}</p>
                  } @else {
                    <p class="subtitle">Preparing your interface...</p>
                  }
                  <div class="divider"></div>
                  @match (starter.phase) {
                    'ready' => { <p class="caption">Start with one of these resources:</p> },
                    _ => { <p class="caption">Loading resources...</p> }
                  }
                  <div class="links">
                """ + landingLink("GitHub", "https://github.com/exepta/tilt-ui", "github", icons)
                + landingLink("Crates.io", "https://crates.io/crates/tilt-ui", "archive", icons)
                + landingLink("Bevy", "https://bevy.org/", "bevy", icons)
                + """
                  </div>
                  <div class="starter-tips">
                    @for (tip in starter.tips; track tip) {
                      <p class="tip">{{ tip }}</p>
                    }
                  </div>
                """ + (routing ? """
                  <div class="route-nav">
                    <button onclick="go_home">Home</button>
                    <button onclick="go_about">About</button>
                  </div>
                  <div class="route-outlet"><router-outlet></router-outlet></div>
                """ : "")
                + """
                  <p class="footer">Edit src-ui/pages/main.component.html to make this page your own.</p>
                </div>
                """;
    }

    /** Exposes working state to the template and, when selected, route handlers. */
    private static String mainComponentRust(boolean routing) {
        return """
                use bevy::prelude::*;
                use tilt_ui::{component_init, html_shared};

                #[html_shared]
                #[derive(Resource, tilt_ui::serde::Serialize)]
                #[serde(crate = "tilt_ui::serde")]
                struct StarterView {
                    ready: bool,
                    phase: String,
                    subtitle: String,
                    tips: Vec<String>,
                }

                #[component_init]
                fn initialize_starter(mut commands: Commands) {
                    commands.insert_resource(StarterView {
                        ready: true,
                        phase: "ready".into(),
                        subtitle: "Build your next Bevy interface with reusable components.".into(),
                        tips: vec!["Edit this page and its Rust state.".into(), "Try @if, @for, and @use in your template.".into()],
                    });
                }
                """ + (routing ? """

                use tilt_ui::{HtmlEvent, Router, html_fn};

                #[html_fn("go_home")]
                fn go_home(In(_event): In<HtmlEvent>, mut router: ResMut<Router>) {
                    router.navigate("/");
                }

                #[html_fn("go_about")]
                fn go_about(In(_event): In<HtmlEvent>, mut router: ResMut<Router>) {
                    router.navigate("/about");
                }
                """ : "");
    }

    /** Builds one resource link for the starter page. */
    private static String landingLink(String label, String url, String icon, boolean icons) {
        return "    <hyperlink href=\"" + url + "\" icon=\"none\">"
                + (icons ? "<icon name=\"" + icon + "\" size=\"20\"></icon>" : "")
                + label + "</hyperlink>\n";
    }

    /** Builds the supported TiltUI component stylesheet. */
    private static String mainComponentStyles(boolean routing) {
        return """
                .welcome {
                    display: flex;
                    flex-direction: column;
                    align-items: center;
                    flex-shrink: 0;
                    gap: 18px;
                    width: 100%;
                    min-width: 0px;
                    max-width: 560px;
                    padding: 44px 38px;
                    border: 1px solid #e0e5ef;
                    border-radius: 20px;
                    background-color: #ffffff;
                    box-shadow: 0 16px 36px #34416b14;
                }
                .eyebrow { width: 100%; color: #6554c0; font-size: 12px; font-weight: 700; text-align: center; }
                h1 { width: 100%; color: #1c2740; font-size: 48px; font-weight: 800; text-align: center; }
                .subtitle { width: 100%; color: #54627a; font-size: 17px; text-align: center; }
                .divider { width: 64px; height: 3px; border-radius: 3px; background-color: #8369df; }
                .caption { width: 100%; color: #66748b; font-size: 14px; text-align: center; }
                .links {
                    display: flex;
                    flex-direction: column;
                    align-items: center;
                    gap: 10px;
                    width: 100%;
                    max-width: 400px;
                }
                .links hyperlink {
                    display: flex;
                    align-items: center;
                    justify-content: center;
                    gap: 8px;
                    width: 100%;
                    min-height: 52px;
                    padding: 12px 16px;
                    border: 1px solid #d7dcec;
                    border-radius: 11px;
                    background-color: #f8f9fd;
                    color: #382d76;
                    font-size: 15px;
                    font-weight: 700;
                }
                .links hyperlink:hover { background-color: #eeeafd; border-color: #b9acef; color: #382d76; }
                .links icon { width: 20px; height: 20px; color: #6554c0; }
                .starter-tips { display: flex; flex-direction: column; gap: 5px; width: 100%; }
                .starter-tips .tip { width: 100%; color: #66748b; font-size: 13px; text-align: center; }
                .footer { width: 100%; margin-top: 8px; color: #7c8a9e; font-size: 12px; text-align: center; }
                @media (max-width: 560px) {
                    .welcome { padding: 30px 22px; }
                    h1 { font-size: 40px; }
                }
                """ + (routing ? """
                .route-nav { display: flex; gap: 10px; width: 100%; }
                .route-nav button {
                    flex-grow: 1;
                    min-height: 42px;
                    padding: 10px 16px;
                    border: 1px solid #b9acef;
                    border-radius: 10px;
                    background-color: #eeeafd;
                    color: #382d76;
                    font-size: 15px;
                    font-weight: 700;
                }
                .route-nav button:hover { background-color: #e3dcfc; }
                .route-outlet { display: flex; flex-direction: column; width: 100%; }
                """ : "");
    }

    /** Builds Just commands for the selected executable targets. */
    private static String justfile(ProjectKind kind) {
        StringBuilder just = new StringBuilder();
        just.append(kind == ProjectKind.WASM ? "default: web\n\n" : "default: run\n\n");
        if (kind == ProjectKind.APP || kind == ProjectKind.BOTH)
            just.append("run:\n    cargo run\n\ncheck:\n    cargo check\n\n");
        if (kind.hasWasm())
            just.append("web:\n    trunk serve --open\n\nweb-build:\n    trunk build\n\n");
        return just.toString();
    }

    /** Describes setup steps and exact generated dependencies. */
    private static String projectReadme(String packageName, BevySettings options, boolean withUi, Set<String> uiFeatures) {
        StringBuilder readme = new StringBuilder("# ").append(packageName).append("\n\n")
                .append("A ").append(options.kind()).append(" project using Bevy ").append(options.bevyVersion())
                .append(" and Rust ").append(options.minimumRustVersion()).append(" or newer.\n\n");
        if (options.kind().hasBinary()) {
            readme.append("## Run\n\n");
            if (options.useJust())
                readme.append("This project includes a `justfile`. If Just is not installed yet, run "
                        + "`cargo install just --locked`.\n\n");
            if (options.kind() == ProjectKind.APP || options.kind() == ProjectKind.BOTH)
                readme.append(options.useJust()
                        ? "Desktop app:\n\n```bash\njust run\n```\n\nCheck the project with `just check`.\n\n"
                        : "Desktop app:\n\n```bash\ncargo run\n```\n\nCheck the project:\n\n```bash\ncargo check\n```\n\n");
            if (options.kind().hasWasm()) {
                readme.append("Install the browser target and Trunk first:\n\n"
                        + "```bash\nrustup target add wasm32-unknown-unknown\ncargo install trunk --locked\n```\n\n");
                readme.append(options.useJust()
                        ? "Run the browser app with `just web`, or build it with `just web-build`.\n\n"
                        : "Run the browser app:\n\n```bash\ntrunk serve --open\n```\n\n"
                                + "Build it:\n\n```bash\ntrunk build\n```\n\n");
            }
            if (withUi)
                readme.append("## Your Tilt UI starter\n\n")
                        .append("`src-ui/index.html` mounts `<main></main>`. Edit `src-ui/pages/main.component.html` for the page, "
                                + "`main.component.css` for its styling, and `main.component.rs` for the starter state and handlers. "
                                + "The page demonstrates `@use`, `@let`, `@if`, `@match`, and `@for` with a Rust resource. "
                                + "The three links open GitHub, crates.io, and Bevy. "
                                + "Link icons appear only when the `tilt-icons` feature is enabled.\n\n")
                        .append("Enabled TiltUI features: `").append(String.join("`, `", uiFeatures)).append("`.\n\n");
            if (withUi && options.useRouting())
                readme.append("## Routing\n\nThe Home and About buttons in `src-ui/pages/main.component.html` navigate between `/` and `/about`. "
                        + "Their Rust handlers live in `main.component.rs`. The page's `<router-outlet>` shows "
                        + "`src-ui/pages/home.component.*` or `src-ui/pages/about.component.*`. "
                        + "`src-ui/routers.rs` combines route tables, starting with `src-ui/routes/home.route.rs`. "
                        + "Use **New → Tilt Component → Routing** in RustRover to add another route.\n\n");
        } else {
            readme.append("## Continue\n\n")
                    .append(options.kind() == ProjectKind.LIBRARY
                            ? "Add your public Bevy helpers in `src/lib.rs`, then run `cargo check`.\n\n"
                            : "Start in `src/lib.rs` and add the Bevy systems your project needs. Run `cargo check` when ready.\n\n");
        }
        readme.append("## Included crates\n\n")
                .append("- `bevy =").append(options.bevyVersion()).append("` — the game engine.\n");
        if (withUi) readme.append("- `tilt-ui =").append(options.tiltUiVersion())
                .append("` and `tilt-ui-build =").append(options.tiltUiVersion())
                .append("` — UI runtime and component discovery.\n");
        for (CrateAsset asset : options.crates()) {
            readme.append("- [`").append(asset.name()).append("`](https://crates.io/crates/")
                    .append(asset.name()).append(") `=").append(asset.version()).append("` — ")
                    .append(asset.description().replace('\n', ' ')).append("\n");
        }
        readme.append("\nThe exact crate versions are recorded in `Cargo.toml`. The selected Rust toolchain is recorded in `rust-toolchain.toml`.\n");
        return readme.toString();
    }
}
