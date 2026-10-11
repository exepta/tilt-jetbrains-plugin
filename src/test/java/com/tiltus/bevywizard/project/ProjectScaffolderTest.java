package com.tiltus.bevywizard.project;

import com.tiltus.bevywizard.catalog.BevyCompatibility;
import com.tiltus.bevywizard.catalog.BevyRelease;
import com.tiltus.bevywizard.catalog.CatalogClient;
import com.tiltus.bevywizard.catalog.CrateAsset;
import com.tiltus.bevywizard.wizard.MarkdownPreview;
import com.tiltus.bevywizard.wizard.TiltFeature;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ProjectScaffolderTest {
    @TempDir Path temp;

    @Test void gitInitializationAndIgnorePatternsFollowProjectKind() throws Exception {
        for (ProjectKind kind : ProjectKind.values()) {
            Path directory = temp.resolve("git-" + kind.name().toLowerCase());
            ProjectScaffolder.create(directory, new BevySettings("0.19.1", "1.95.0", "1.95.0", "1.95.0",
                    kind, false, false, false, Set.of(), List.of(), true, true));
            assertTrue(Files.isDirectory(directory.resolve(".git")), kind.name());
            String ignore = Files.readString(directory.resolve(".gitignore"));
            assertTrue(ignore.contains("/target/"));
            assertEquals(kind == ProjectKind.LIBRARY || kind == ProjectKind.BLANK,
                    ignore.contains("/Cargo.lock"));
            assertEquals(kind.hasWasm(), ignore.contains("/dist/"));
            assertEquals(kind.hasWasm(), ignore.contains("/.trunk/"));
        }
        Path withoutIgnore = temp.resolve("git-no-ignore");
        ProjectScaffolder.create(withoutIgnore, new BevySettings("0.19.1", "1.95.0", "1.95.0", "1.95.0",
                ProjectKind.APP, false, false, false, Set.of(), List.of(), true, false));
        assertTrue(Files.isDirectory(withoutIgnore.resolve(".git")));
        assertFalse(Files.exists(withoutIgnore.resolve(".gitignore")));
        Path withoutGit = temp.resolve("no-git");
        ProjectScaffolder.create(withoutGit, new BevySettings("0.19.1", "1.95.0", "1.95.0", "1.95.0",
                ProjectKind.APP, false, false, false, Set.of(), List.of(), false, false));
        assertFalse(Files.exists(withoutGit.resolve(".git")));
        assertFalse(Files.exists(withoutGit.resolve(".gitignore")));
    }

    @Test void createsWasmTiltUiProjectWithTrunkAndSelectedFeatures() throws Exception {
        Path dir = temp.resolve("my-game");
        ProjectScaffolder.create(dir, new BevySettings("0.19.1", "1.95.0", "1.95.0", "1.95.0", ProjectKind.WASM, true,
                Set.of("component", "fluent"), List.of(new CrateAsset("bevy_pancam", "bevy_pancam", "camera", "2D",
                        List.of("^0.19"), "https://crates.io/crates/bevy_pancam", "0.19.0", 42, 10, null, null)
                        .withIdentity("bevy_pancam", CrateAsset.IdentitySource.CRATES_IO_LINK))));
        String cargo = Files.readString(dir.resolve("Cargo.toml"));
        assertTrue(cargo.contains("bevy_pancam = \"=0.19.0\""));
        assertTrue(cargo.indexOf("bevy_pancam = \"=0.19.0\"") < cargo.indexOf("[build-dependencies]"));
        assertTrue(cargo.contains("version = \"=0.19.1\""));
        assertTrue(cargo.contains("[dependencies.bevy]\nversion = \"=0.19.1\"\ndefault-features = false\nfeatures = [\n    \"bevy_core_pipeline\",\n"));
        assertTrue(cargo.contains("[dependencies.tilt-ui]\nversion = \"=0.1.0\"\ndefault-features = false\nfeatures = [\n"));
        assertFalse(cargo.contains("bevy = {"));
        assertFalse(cargo.contains("tilt-ui = {"));
        assertTrue(cargo.contains("\"fluent\""));
        assertTrue(cargo.contains("[target.'cfg(target_arch = \"wasm32\")'.dependencies.bevy_render]\nversion = \"0.19\""));
        assertTrue(cargo.contains("rust-version = \"1.95.0\""));
        assertEquals("[toolchain]\nchannel = \"1.95.0\"\n", Files.readString(dir.resolve("rust-toolchain.toml")));
        assertTrue(Files.readString(dir.resolve("index.html")).contains("copy-dir"));
        assertTrue(Files.readString(dir.resolve("src/main.rs")).contains("const UI_SOURCE_ROOT: &str = \"src-ui\""));
        assertTrue(Files.isRegularFile(dir.resolve("src-ui/index.html")));
        assertTrue(Files.readString(dir.resolve("src-ui/index.html")).contains("<main></main>"));
        assertTrue(Files.readString(dir.resolve("src-ui/styles/site.css")).contains("background-color: #f4f6fb"));
        String componentCss = Files.readString(dir.resolve("src-ui/pages/main.component.css"));
        assertTrue(componentCss.contains(".welcome {\n    display: flex;\n    flex-direction: column;"));
        assertTrue(componentCss.contains(".links {\n    display: flex;\n    flex-direction: column;"));
        assertTrue(componentCss.contains("background-color: #ffffff"));
        assertFalse(componentCss.contains("letter-spacing"), "TiltUI rejects unknown CSS properties and the whole stylesheet");
        String component = Files.readString(dir.resolve("src-ui/pages/main.component.html"));
        for (String directive : List.of("@use", "@let", "@if", "@else", "@for", "@match"))
            assertTrue(component.contains(directive), directive);
        assertTrue(Files.readString(dir.resolve("src-ui/pages/main.component.rs")).contains("struct StarterView"));
        assertTrue(component.contains("<h1>Tilt UI</h1>"));
        assertTrue(component.contains("GitHub"));
        assertTrue(component.contains("Crates.io"));
        assertTrue(component.contains("Bevy"));
        assertFalse(component.contains("<icon "));
        assertFalse(Files.exists(dir.resolve("justfile")));
        String readme = Files.readString(dir.resolve("README.md"));
        assertTrue(readme.contains("bevy_pancam"));
        assertTrue(readme.contains("main.component.html"));
        assertTrue(readme.contains("```bash\ntrunk serve --open\n```"));
    }

    @Test void wasmTargetsNeverEmitTiltUiHotReload() throws Exception {
        for (ProjectKind kind : List.of(ProjectKind.WASM, ProjectKind.BOTH)) {
            Path dir = temp.resolve("wasm-hot-" + kind.name().toLowerCase());
            ProjectScaffolder.create(dir, new BevySettings("0.19.1", "1.95.0", "1.95.0", "1.95.0",
                    kind, true, Set.of("hot-reload", "fluent"), List.of()));
            assertFalse(Files.readString(dir.resolve("Cargo.toml")).contains("hot-reload"));
            assertFalse(Files.readString(dir.resolve("README.md")).contains("hot-reload"));
            assertTrue(Files.readString(dir.resolve("Cargo.toml")).contains("\"fluent\""));
        }
        Path app = temp.resolve("desktop-hot");
        ProjectScaffolder.create(app, new BevySettings("0.19.1", "1.95.0", "1.95.0", "1.95.0",
                ProjectKind.APP, true, Set.of("hot-reload"), List.of()));
        assertTrue(Files.readString(app.resolve("Cargo.toml")).contains("\"hot-reload\""));
    }

    @Test void selectedTiltUiRcPinsMatchingRuntimeAndBuildRelease() throws Exception {
        BevySettings settings = new BevySettings("0.19.1", "1.95.0", "1.95.0", "1.95.0",
                ProjectKind.APP, true, Set.of(), List.of()).withTiltUiVersion("0.1.0-rc.2");
        Path dir = temp.resolve("tilt-rc");
        ProjectScaffolder.create(dir, settings);
        String cargo = Files.readString(dir.resolve("Cargo.toml"));
        assertTrue(cargo.contains("[dependencies.tilt-ui]\nversion = \"=0.1.0-rc.2\""));
        assertTrue(cargo.contains("features = [\n"));
        assertTrue(cargo.contains("    \"component\",\n"));
        assertTrue(cargo.contains("tilt-ui-build = \"=0.1.0-rc.2\""));
        assertTrue(Files.readString(dir.resolve("README.md")).contains("tilt-ui =0.1.0-rc.2"));
        assertThrows(Exception.class, () -> ProjectScaffolder.create(temp.resolve("tilt-unsupported"),
                settings.withTiltUiVersion("0.2.0")));
        assertFalse(Files.exists(temp.resolve("tilt-unsupported/Cargo.toml")));
    }

    @Test void createsWorkingRoutingStarterOnlyWhenSelected() throws Exception {
        Path dir = temp.resolve("routing-app");
        ProjectScaffolder.create(dir, new BevySettings("0.19.1", "1.95.0", "1.95.0", "1.95.0",
                ProjectKind.APP, true, true, Set.of(), List.of()));
        assertTrue(Files.readString(dir.resolve("src-ui/pages/main.component.html"))
                .contains("<router-outlet></router-outlet>"));
        assertTrue(Files.readString(dir.resolve("src-ui/routers.rs"))
                .contains("#[path = \"routes/home.route.rs\"]"));
        assertTrue(Files.readString(dir.resolve("src-ui/routers.rs")).contains("#[ui_routes]"));
        assertTrue(Files.readString(dir.resolve("src-ui/routes/home.route.rs"))
                .contains(".route(\"/\", home).route(\"/home\", home).route(\"/about\", about)"));
        assertTrue(Files.isRegularFile(dir.resolve("src-ui/pages/home.component.html")));
        assertTrue(Files.isRegularFile(dir.resolve("src-ui/pages/home.component.css")));
        assertTrue(Files.isRegularFile(dir.resolve("src-ui/pages/home.component.rs")));
        assertTrue(Files.isRegularFile(dir.resolve("src-ui/pages/about.component.html")));
        assertTrue(Files.isRegularFile(dir.resolve("src-ui/pages/about.component.css")));
        assertTrue(Files.isRegularFile(dir.resolve("src-ui/pages/about.component.rs")));
        assertTrue(Files.readString(dir.resolve("src-ui/pages/main.component.html"))
                .contains("onclick=\"go_about\""));
        assertTrue(Files.readString(dir.resolve("src-ui/pages/main.component.rs"))
                .contains("router.navigate(\"/about\")"));
        assertTrue(Files.readString(dir.resolve("README.md")).contains("## Routing"));

        Path without = temp.resolve("no-routing-app");
        ProjectScaffolder.create(without, new BevySettings("0.19.1", "1.95.0", "1.95.0", "1.95.0",
                ProjectKind.APP, true, false, Set.of(), List.of()));
        assertFalse(Files.exists(without.resolve("src-ui/routers.rs")));
        assertFalse(Files.readString(without.resolve("src-ui/pages/main.component.html"))
                .contains("<router-outlet"));
    }

    @Test void justfileIsOptionalAndReadmeContainsDirectCommandsOtherwise() throws Exception {
        Path plain = temp.resolve("plain-app");
        ProjectScaffolder.create(plain, new BevySettings("0.19.1", "1.95.0", "1.95.0", "1.95.0",
                ProjectKind.APP, false, false, false, Set.of(), List.of()));
        assertFalse(Files.exists(plain.resolve("justfile")));
        assertTrue(Files.readString(plain.resolve("README.md")).contains("```bash\ncargo run\n```"));

        Path withJust = temp.resolve("just-wasm");
        ProjectScaffolder.create(withJust, new BevySettings("0.19.1", "1.95.0", "1.95.0", "1.95.0",
                ProjectKind.WASM, false, false, true, Set.of(), List.of()));
        assertTrue(Files.readString(withJust.resolve("justfile")).contains("web:\n    trunk serve --open"));
        assertTrue(Files.readString(withJust.resolve("README.md")).contains("just web"));
    }

    @Test void createsEveryProjectKind() throws Exception {
        for (ProjectKind kind : ProjectKind.values()) {
            Path dir = temp.resolve("kind-" + kind.name().toLowerCase());
            ProjectScaffolder.create(dir, new BevySettings("0.19.1", "1.95.0", "1.95.0", "1.95.0", kind, false, Set.of(), List.of()));
            assertTrue(Files.isRegularFile(dir.resolve("Cargo.toml")));
            assertEquals(kind.hasWasm(), Files.exists(dir.resolve("Trunk.toml")));
            assertEquals(kind.hasBinary(), Files.exists(dir.resolve("src/main.rs")));
            assertEquals(kind == ProjectKind.LIBRARY || kind == ProjectKind.BLANK, Files.exists(dir.resolve("src/lib.rs")));
            assertFalse(Files.exists(dir.resolve("justfile")));
            assertFalse(Files.exists(dir.resolve("src-ui")));
        }
    }

    @Test void includesIconsOnlyWhenFeatureIsSelectedAndSkipsUiForLibraryAndBlank() throws Exception {
        Path app = temp.resolve("icons-app");
        ProjectScaffolder.create(app, new BevySettings("0.19.1", "1.95.0", "1.95.0", "1.95.0",
                ProjectKind.APP, true, false, true, Set.of("tilt-icons"), List.of()));
        String component = Files.readString(app.resolve("src-ui/pages/main.component.html"));
        assertTrue(component.contains("<icon name=\"github\""));
        assertTrue(component.contains("<icon name=\"bevy\""));
        assertTrue(component.contains("<icon name=\"github\" size=\"20\"></icon>"));
        assertFalse(component.contains("size=\"20\" />"));
        assertTrue(Files.readString(app.resolve("Cargo.toml")).contains("\"tilt-icons\""));
        assertTrue(Files.readString(app.resolve("justfile")).contains("run:\n    cargo run"));

        for (ProjectKind kind : List.of(ProjectKind.LIBRARY, ProjectKind.BLANK)) {
            Path dir = temp.resolve(kind.name().toLowerCase() + "-ui-selected");
            ProjectScaffolder.create(dir, new BevySettings("0.19.1", "1.95.0", "1.95.0", "1.95.0",
                    kind, true, Set.of("tilt-icons"), List.of()));
            assertFalse(Files.exists(dir.resolve("src-ui")));
            assertFalse(Files.exists(dir.resolve("justfile")));
            assertFalse(Files.readString(dir.resolve("Cargo.toml")).contains("tilt-ui"));
        }
    }

    @Test void pinsLightyear028ForBevy019() throws Exception {
        CrateAsset lightyear = new CrateAsset("lightyear", "Lightyear", "Networking", "Networking",
                List.of("^0.19"), "https://crates.io/crates/lightyear", "0.28.0", 100, 10, null, null)
                .withIdentity("lightyear", CrateAsset.IdentitySource.CRATES_IO_LINK);
        ProjectScaffolder.create(temp.resolve("lightyear-project"), new BevySettings("0.19.1", "1.95.0", "1.95.0", "1.95.0",
                ProjectKind.APP, false, Set.of(), List.of(lightyear)));
        assertTrue(Files.readString(temp.resolve("lightyear-project/Cargo.toml")).contains("lightyear = \"=0.28.0\""));
        assertThrows(Exception.class, () -> ProjectScaffolder.create(temp.resolve("wrong-lightyear"),
                new BevySettings("0.19.1", "1.95.0", "1.95.0", "1.95.0", ProjectKind.APP, false, Set.of(),
                        List.of(lightyear.withMetadata("0.30.1", 100, 10, null, null)))));
        String versions = "{\"versions\":[{\"num\":\"0.30.1\",\"yanked\":false},"
                + "{\"num\":\"0.28.0\",\"yanked\":false},{\"num\":\"0.28.1\",\"yanked\":true}]}";
        assertEquals("0.28.0", CatalogClient.preferredLightyearRelease(versions));
    }

    @Test void preventsUnsupportedTiltUiVersionAndExistingManifest() throws Exception {
        Path dir = temp.resolve("guarded");
        assertThrows(Exception.class, () -> ProjectScaffolder.create(dir,
                new BevySettings("0.20.0", "1.96.0", "1.96.0", "1.96.0", ProjectKind.APP, true, Set.of("component"), List.of())));
        ProjectScaffolder.create(dir, new BevySettings("0.19.1", "1.95.0", "1.95.0", "1.95.0", ProjectKind.APP, false, Set.of(), List.of()));
        assertThrows(Exception.class, () -> ProjectScaffolder.create(dir,
                new BevySettings("0.19.1", "1.95.0", "1.95.0", "1.95.0", ProjectKind.APP, false, Set.of(), List.of())));
    }

    @Test void pinsSelectedBevyRelease() throws Exception {
        Path dir = temp.resolve("rc-game");
        ProjectScaffolder.create(dir, new BevySettings("0.20.0-rc.2", "1.96.0", "1.96.0", "1.96.0", ProjectKind.APP, false, Set.of(), List.of()));
        assertTrue(Files.readString(dir.resolve("Cargo.toml")).contains("bevy = \"=0.20.0-rc.2\""));
        assertThrows(Exception.class, () -> ProjectScaffolder.create(temp.resolve("rc-tilt"),
                new BevySettings("0.20.0-rc.2", "1.96.0", "1.96.0", "1.96.0", ProjectKind.APP, true, Set.of("component"), List.of())));
    }

    @Test void rejectsAssetTaggedForAnotherBevyRelease() {
        CrateAsset incompatible = new CrateAsset("sample_plugin", "Sample", "Plugin", "2D", List.of("^0.18"),
                "https://crates.io/crates/sample_plugin", "1.2.3", 12, 2, null, null)
                .withIdentity("sample_plugin", CrateAsset.IdentitySource.CRATES_IO_LINK);
        assertThrows(Exception.class, () -> ProjectScaffolder.create(temp.resolve("wrong-asset"),
                new BevySettings("0.19.1", "1.95.0", "1.95.0", "1.95.0", ProjectKind.APP,
                        false, Set.of(), List.of(incompatible))));
    }

    @Test void rejectsGuessedCrateNameBeforeWritingManifest() throws Exception {
        CrateAsset guessed = new CrateAsset("Pretty_Plugin", "Pretty Plugin", "Plugin", "2D", List.of("^0.19"),
                "https://github.com/example/pretty-plugin", "1.2.3", 12, 2, null, null);
        Path destination = temp.resolve("unverified-crate");
        assertThrows(Exception.class, () -> ProjectScaffolder.create(destination,
                new BevySettings("0.19.1", "1.95.0", "1.95.0", "1.95.0", ProjectKind.APP,
                        false, Set.of(), List.of(guessed))));
        assertFalse(Files.exists(destination.resolve("Cargo.toml")));

        CrateAsset corrected = guessed.withIdentity("real_package", CrateAsset.IdentitySource.USER_PROVIDED);
        ProjectScaffolder.create(temp.resolve("corrected-crate"),
                new BevySettings("0.19.1", "1.95.0", "1.95.0", "1.95.0", ProjectKind.APP,
                        false, Set.of(), List.of(corrected)));
        String cargo = Files.readString(temp.resolve("corrected-crate/Cargo.toml"));
        assertTrue(cargo.contains("real_package = \"=1.2.3\""));
        assertFalse(cargo.contains("Pretty_Plugin"));
    }

    @Test void assetTagsAndDependenciesRespectSelectedBevyVersion() {
        assertTrue(BevyCompatibility.matches("0.19.1", "^0.19"));
        assertTrue(BevyCompatibility.matches("0.19.1", "0.19.0"));
        assertFalse(BevyCompatibility.matches("0.19.1", "^0.18"));
        assertTrue(BevyCompatibility.matches("0.20.0-rc.2", "^0.20.0-rc.1"));
        assertTrue(BevyCompatibility.matches("0.20.0-rc.10", "^0.20.0-rc.2"));
        assertFalse(BevyCompatibility.matches("0.20.0-rc.1", "^0.20.0-rc.2"));
        String matching = "{\"dependencies\":[{\"crate_id\":\"bevy\",\"req\":\"^0.19\",\"kind\":\"normal\"}]}";
        String mismatching = "{\"dependencies\":[{\"crate_id\":\"bevy\",\"req\":\"^0.18\",\"kind\":\"normal\"}]}";
        assertTrue(CatalogClient.dependenciesSupport(matching, "0.19.1"));
        assertFalse(CatalogClient.dependenciesSupport(mismatching, "0.19.1"));
        String pluginDependency = "{\"dependencies\":[{\"crate_id\":\"bevy\",\"req\":\"^0.19\",\"kind\":\"normal\"},"
                + "{\"crate_id\":\"bevy_egui\",\"req\":\"^0.42\",\"kind\":\"normal\"}]}";
        assertTrue(CatalogClient.dependenciesSupport(pluginDependency, "0.19.1"));
    }

    @Test void rejectsToolchainBelowBevyMinimum() {
        assertThrows(Exception.class, () -> ProjectScaffolder.create(temp.resolve("old-rust"),
                new BevySettings("0.19.1", "1.95.0", "stable", "1.85.0", ProjectKind.APP, false, Set.of(), List.of())));
        assertThrows(Exception.class, () -> ProjectScaffolder.create(temp.resolve("wrong-minimum"),
                new BevySettings("0.19.1", "1.85.0", "1.95.0", "1.95.0", ProjectKind.APP, false, Set.of(), List.of())));
    }

    @Test void comparesInstalledRustVersionsNumerically() {
        assertTrue(RustToolchains.compare("1.100.0", "1.95.0") > 0);
        assertEquals("1.85.0", RustToolchains.minimum("1.75.0"));
        assertEquals("1.95.0", RustToolchains.minimum("1.95.0"));
        List<RustToolchains.Installed> toolchains = List.of(
                new RustToolchains.Installed("stable-aarch64-apple-darwin", "1.94.0", true),
                new RustToolchains.Installed("1.95.0-aarch64-apple-darwin", "1.95.0", false));
        assertEquals("1.95.0-aarch64-apple-darwin", RustToolchains.bestCompatible(toolchains, "1.95.0").channel());
        assertEquals("1.95.0", RustToolchains.projectChannel("1.95.0-aarch64-apple-darwin"));
        assertEquals("stable", RustToolchains.projectChannel("stable-aarch64-apple-darwin"));
    }

    @Test void readsRustMinimumFromCratesIoReleaseMetadata() {
        String json = "{\"versions\":[{\"num\":\"0.19.1\",\"rust_version\":\"1.95.0\",\"yanked\":false}]}";
        assertEquals(new BevyRelease("0.19.1", "1.95.0"), CatalogClient.parseBevyReleases(json).getFirst());
    }

    @Test void keepsOnlyStableAndRcVersionsFromCratesIo() {
        String json = "{\"versions\":[" +
                "{\"num\":\"0.20.0-rc.2\",\"yanked\":false}," +
                "{\"num\":\"0.19.1\",\"yanked\":false}," +
                "{\"num\":\"0.18.0-beta.1\",\"yanked\":false}," +
                "{\"num\":\"0.18.0\",\"yanked\":true}]}";
        assertEquals(List.of("0.20.0-rc.2", "0.19.1"), CatalogClient.parseBevyVersions(json));
        assertTrue(CatalogClient.isReleaseCandidate("0.20.0-rc.2"));
        assertFalse(CatalogClient.isReleaseCandidate("0.19.1"));
    }

    @Test void bundlesReadmeForEachTiltUiFeature() {
        for (TiltFeature feature : TiltFeature.ALL) {
            for (String crate : feature.readmeCrates()) {
                assertNotNull(getClass().getResource("/tilt-readmes/" + crate + "/README.md"), crate);
            }
        }
    }

    @Test void generatorHasBevyNavigationIcon() {
        assertNotNull(getClass().getResource("/icons/bevy.png"));
        var icon = new BevyProjectGenerator().getLogo();
        assertEquals(16, icon.getIconWidth());
        assertEquals(16, icon.getIconHeight());
    }

    @Test void markdownPreviewKeepsCompleteReadmeContent() {
        String markdown = "# Plugin guide\n\nA complete paragraph with **bold text**.\n\n"
                + "| Name | Version |\n| --- | --- |\n| Bevy | 0.19 |\n\n"
                + "![Preview](images/preview.png)\n\n```rust\nfn main() {}\n```\n";
        String html = MarkdownPreview.htmlFromMarkdown(markdown, "https://raw.githubusercontent.com/example/plugin/HEAD/");
        assertTrue(html.contains("A complete paragraph"));
        assertTrue(html.contains("<table>"));
        assertTrue(html.contains("images/preview.png"));
        assertTrue(html.contains("fn main() {}"));
    }

    @Test void resolvesCratesIoReadmeUrlInsteadOfDisplayingJson() {
        String url = "https://static.crates.io/readmes/lightyear/lightyear-0.30.1.html";
        assertEquals(url, CatalogClient.readmeLocation("{\"url\":\"" + url + "\"}"));
        assertNull(CatalogClient.readmeLocation("<h1>Lightyear</h1>"));
        assertNull(CatalogClient.readmeLocation("{\"url\":\"https://example.com/readme\"}"));
    }

    @Test void parsesBevyAssetCardsAndVersionTags() {
        String html = "<h3 class='asset-subsection'>2D</h3><div class='item-grid'>" +
                "<div id='bevy-pancam'><a class='asset-card' href='https://crates.io/crates/bevy_pancam'>" +
                "<div class='asset-card__title'>bevy_pancam</div><div class='asset-card__description'>Camera controls</div>" +
                "<div class='asset-card__bevy-versions'><span class='asset-card__tag'>^0.19</span></div>" +
                "</a></div></div>";
        CrateAsset asset = CatalogClient.parseAssets(html).getFirst();
        assertEquals("bevy_pancam", asset.name());
        assertEquals("2D", asset.category());
        assertEquals("Camera controls", asset.description());
        assertEquals(List.of("^0.19"), asset.bevyTags());
    }
}
