package com.tiltus.bevywizard.project;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.tiltus.bevywizard.catalog.BevyCompatibility;
import com.tiltus.bevywizard.catalog.CatalogClient;
import com.tiltus.bevywizard.catalog.CrateAsset;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/** Explicit release gate: run with ./gradlew validateGeneratedProjects. */
@Tag("release-validation")
class GeneratedProjectReleaseValidationTest {
    @TempDir Path temp;
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    @Test void avianSuggestionsResolveTheRenamedRepository() throws Exception {
        CrateAsset avian = new CrateAsset("avian", "avian", "Physics", "Physics", List.of("^0.19"),
                "https://github.com/Jondolf/avian", null, -1, -1, null, null);
        assertTrue(CatalogClient.identify(avian).isEmpty(), "The reserved avian package must not be selected automatically");
        List<String> suggestions = CatalogClient.suggestCrateNames(avian);
        assertEquals(List.of("avian3d", "avian2d"), suggestions.subList(0, 2));
        assertFalse(suggestions.contains("avian"));
        assertFalse(suggestions.contains("tuna"));
        for (String name : List.of("avian3d", "avian2d")) {
            CrateAsset packageName = CatalogClient.identifyUserName(avian, name);
            assertEquals(name, CatalogClient.compatibleRelease(packageName, TiltUiCompatibility.TESTED_BEVY)
                    .orElseThrow(() -> new AssertionError(name + " needs a Bevy-compatible release")).name());
        }
    }

    @Test void publishedReleaseAndGeneratedProjectsPassCargo() throws Exception {
        for (String release : TiltUiCompatibility.SUPPORTED_VERSIONS) {
            verifyPublishedRelease("tilt-ui", release);
            verifyPublishedRelease("tilt-ui-build", release);
            String dependencies = get("https://crates.io/api/v1/crates/tilt-ui/" + release + "/dependencies");
            assertTrue(JsonParser.parseString(dependencies).getAsJsonObject().getAsJsonArray("dependencies")
                    .asList().stream().map(JsonElement::getAsJsonObject).anyMatch(item ->
                            "bevy".equals(item.get("crate_id").getAsString())
                                    && BevyCompatibility.matches(TiltUiCompatibility.TESTED_BEVY,
                                    item.get("req").getAsString())),
                    "Published TiltUI " + release + " must depend on the supported Bevy series");
        }

        RustToolchains.Installed toolchain = RustToolchains.bestCompatible(RustToolchains.installed(),
                TiltUiCompatibility.BEVY_MINIMUM_RUST);
        assertNotNull(toolchain, "Install a Rust toolchain compatible with the tested Bevy release");
        String channel = RustToolchains.projectChannel(toolchain.channel());
        Path targetDir = temp.resolve("cargo-target");
        Path desktop = temp.resolve("desktop-check");
        ProjectScaffolder.create(desktop, settings(ProjectKind.APP, channel, toolchain.rustVersion()));
        cargoCheck(desktop, targetDir, false);

        for (String release : TiltUiCompatibility.SUPPORTED_VERSIONS) {
            if (!TiltUiCompatibility.isReleaseCandidate(release)) continue;
            Path rcDesktop = temp.resolve("desktop-" + release + "-check");
            ProjectScaffolder.create(rcDesktop, settings(ProjectKind.APP, channel, toolchain.rustVersion())
                    .withTiltUiVersion(release));
            cargoCheck(rcDesktop, targetDir, false);
        }

        String targets = command(temp, Duration.ofSeconds(15), "rustup", "target", "list", "--installed",
                "--toolchain", channel);
        if (Arrays.asList(targets.split("\\R")).contains("wasm32-unknown-unknown")) {
            Path wasm = temp.resolve("wasm-check");
            ProjectScaffolder.create(wasm, settings(ProjectKind.WASM, channel, toolchain.rustVersion()));
            cargoCheck(wasm, targetDir, true);
        } else System.out.println("WASM Cargo check skipped: wasm32-unknown-unknown is not installed for " + channel);
    }

    private static BevySettings settings(ProjectKind kind, String channel, String installedVersion) {
        return new BevySettings(TiltUiCompatibility.TESTED_BEVY, TiltUiCompatibility.BEVY_MINIMUM_RUST,
                channel, installedVersion, kind, true, Set.of(), List.of());
    }

    private static void verifyPublishedRelease(String crate, String release) throws Exception {
        JsonObject version = JsonParser.parseString(get("https://crates.io/api/v1/crates/" + crate + "/"
                + release)).getAsJsonObject().getAsJsonObject("version");
        assertEquals(release, version.get("num").getAsString(), crate);
        assertFalse(version.get("yanked").getAsBoolean(), crate);
        if (crate.equals("tilt-ui")) {
            Set<String> published = version.getAsJsonObject("features").keySet().stream()
                    .filter(key -> !key.equals("default")).collect(Collectors.toSet());
            Set<String> configured = TiltUiCompatibility.FEATURES.stream()
                    .map(TiltUiCompatibility.Feature::key).collect(Collectors.toSet());
            assertEquals(published, configured, "Update the feature catalog for " + release);
        }
    }

    private static String get(String url) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(20))
                .header("User-Agent", "BevyProjectWizard/0.1 (https://github.com/exepta/tilt-ui)").GET().build();
        HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode(), url);
        return response.body();
    }

    private static void cargoCheck(Path project, Path targetDir, boolean wasm) throws Exception {
        if (wasm) command(project, Duration.ofMinutes(20), targetDir, "cargo", "check", "--target", "wasm32-unknown-unknown");
        else command(project, Duration.ofMinutes(20), targetDir, "cargo", "check");
    }

    private static String command(Path cwd, Duration timeout, String... args) throws Exception {
        return command(cwd, timeout, null, args);
    }

    private static String command(Path cwd, Duration timeout, Path targetDir, String... args) throws Exception {
        Path log = Files.createTempFile(cwd, "release-validation-", ".log");
        ProcessBuilder builder = new ProcessBuilder(args).directory(cwd.toFile())
                .redirectErrorStream(true).redirectOutput(log.toFile());
        if (targetDir != null) builder.environment().put("CARGO_TARGET_DIR", targetDir.toString());
        Process process = builder.start();
        boolean finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
        if (!finished) process.destroyForcibly();
        String output = Files.readString(log);
        assertTrue(finished, String.join(" ", args) + " timed out:\n" + tail(output));
        assertEquals(0, process.exitValue(), String.join(" ", args) + " failed:\n" + tail(output));
        return output;
    }

    private static String tail(String output) {
        return output.substring(Math.max(0, output.length() - 12000));
    }
}
