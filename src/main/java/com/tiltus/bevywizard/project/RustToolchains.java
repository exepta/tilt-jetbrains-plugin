package com.tiltus.bevywizard.project;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Discovers, selects, and installs Rust compiler toolchains. */
public final class RustToolchains {
    public static final String EDITION_MINIMUM = "1.85.0";
    private static final Pattern VERSION = Pattern.compile("\\d+\\.\\d+\\.\\d+");
    private static final Pattern CHANNEL = Pattern.compile("(?:stable|beta|nightly|\\d+\\.\\d+\\.\\d+)(?:-[A-Za-z0-9_-]+)*");

    /** An installed rustup channel with its compiler version. */
    public record Installed(String channel, String rustVersion, boolean isDefault) {
        @Override public String toString() {
            return channel + " · Rust " + rustVersion + (isDefault ? " (default)" : "");
        }
    }

    private RustToolchains() {}

    /** Applies the minimum compiler version for the generated Rust edition. */
    public static String minimum(String bevyMinimum) {
        return bevyMinimum == null || compare(bevyMinimum, EDITION_MINIMUM) < 0 ? EDITION_MINIMUM : bevyMinimum;
    }

    /** Compares Rust compiler versions numerically. */
    public static int compare(String first, String second) {
        int[] a = parts(first);
        int[] b = parts(second);
        for (int i = 0; i < 3; i++) {
            int comparison = Integer.compare(a[i], b[i]);
            if (comparison != 0) return comparison;
        }
        return 0;
    }

    /** Parses a validated Rust compiler version into numeric parts. */
    private static int[] parts(String version) {
        if (version == null || !VERSION.matcher(version).matches()) throw new IllegalArgumentException("Invalid Rust version: " + version);
        String[] values = version.split("\\.");
        return new int[]{Integer.parseInt(values[0]), Integer.parseInt(values[1]), Integer.parseInt(values[2])};
    }

    /** Checks whether rustup accepts a toolchain channel. */
    public static boolean validChannel(String channel) { return channel != null && CHANNEL.matcher(channel).matches(); }

    /** Converts an installed channel name to a portable project channel. */
    public static String projectChannel(String installedChannel) {
        if (!validChannel(installedChannel)) throw new IllegalArgumentException("Invalid Rust toolchain: " + installedChannel);
        if (installedChannel.startsWith("stable")) return "stable";
        if (installedChannel.startsWith("beta")) return "beta";
        if (installedChannel.startsWith("nightly-")) {
            Matcher date = Pattern.compile("^nightly-\\d{4}-\\d{2}-\\d{2}").matcher(installedChannel);
            if (date.find()) return date.group();
        }
        if (installedChannel.startsWith("nightly")) return "nightly";
        Matcher version = VERSION.matcher(installedChannel);
        if (version.lookingAt()) return version.group();
        throw new IllegalArgumentException("Invalid Rust toolchain: " + installedChannel);
    }

    /** Lists installed rustup toolchains and their compiler versions. */
    public static List<Installed> installed() throws IOException {
        List<Installed> result = new ArrayList<>();
        for (String line : run(Duration.ofSeconds(15), "toolchain", "list").split("\\R")) {
            if (line.isBlank()) continue;
            String channel = line.split("\\s+", 2)[0];
            if (!validChannel(channel)) continue;
            String compiler = run(Duration.ofSeconds(15), "run", channel, "rustc", "--version");
            Matcher version = VERSION.matcher(compiler);
            if (version.find()) result.add(new Installed(channel, version.group(), line.contains("default")));
        }
        result.sort(Comparator.comparingInt((Installed item) -> item.isDefault() ? 0 : 1)
                .thenComparing((first, second) -> compare(second.rustVersion(), first.rustVersion())));
        return result;
    }

    /** Selects the preferred installed toolchain meeting a minimum version. */
    public static Installed bestCompatible(List<Installed> installed, String minimum) {
        return installed.stream().filter(item -> compare(item.rustVersion(), minimum) >= 0)
                .max(Comparator.comparing(Installed::isDefault)
                        .thenComparing(Installed::rustVersion, RustToolchains::compare)).orElse(null);
    }

    /** Installs a requested Rust toolchain with rustup. */
    public static void install(String version) throws IOException {
        parts(version);
        run(Duration.ofMinutes(10), "toolchain", "install", version);
    }

    /** Updates the stable Rust toolchain with rustup. */
    public static void updateStable() throws IOException {
        run(Duration.ofMinutes(10), "update", "stable");
    }

    /** Runs rustup with a timeout and captures diagnostic output. */
    private static String run(Duration timeout, String... args) throws IOException {
        List<String> command = new ArrayList<>();
        command.add(executable());
        command.addAll(List.of(args));
        Path outputFile = Files.createTempFile("bevy-rustup-", ".log");
        Process process = null;
        try {
            process = new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(outputFile.toFile()).start();
            boolean finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new IOException("rustup timed out.");
            }
            String output = Files.readString(outputFile, StandardCharsets.UTF_8);
            if (process.exitValue() != 0) throw new IOException("rustup failed: " + output.trim());
            return output;
        } catch (InterruptedException e) {
            if (process != null) process.destroyForcibly();
            Thread.currentThread().interrupt();
            throw new IOException("rustup was interrupted.", e);
        } finally {
            Files.deleteIfExists(outputFile);
        }
    }

    /** Finds rustup in the configured Cargo home. */
    private static String executable() {
        String filename = System.getProperty("os.name").toLowerCase().contains("win") ? "rustup.exe" : "rustup";
        Path cargoHome = Path.of(System.getenv().getOrDefault("CARGO_HOME", Path.of(System.getProperty("user.home"), ".cargo").toString()));
        Path local = cargoHome.resolve("bin").resolve(filename);
        return Files.isExecutable(local) ? local.toString() : filename;
    }
}
