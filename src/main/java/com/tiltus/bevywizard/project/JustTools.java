package com.tiltus.bevywizard.project;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Finds Just and installs it through Cargo when the developer requests it. */
public final class JustTools {
    private static final Pattern VERSION = Pattern.compile("^just\\s+(\\d+\\.\\d+\\.\\d+)", Pattern.CASE_INSENSITIVE);

    /** A working Just executable and its reported version. */
    public record Installed(Path executable, String version) {}

    private JustTools() {}

    /** Searches Cargo's bin directory and PATH, then reads {@code just --version}. */
    public static Optional<Installed> installed() throws IOException {
        Optional<Path> executable = findExecutable("just");
        if (executable.isEmpty()) return Optional.empty();
        String output = run(Duration.ofSeconds(10), List.of(executable.get().toString(), "--version"));
        Matcher match = VERSION.matcher(output.strip());
        if (!match.find()) throw new IOException("Could not read the installed Just version: " + output.strip());
        return Optional.of(new Installed(executable.get(), match.group(1)));
    }

    /** Installs the current Just release with Cargo, then verifies the executable. */
    public static Installed install() throws IOException {
        Path cargo = findExecutable("cargo").orElseThrow(() -> new IOException("Cargo is not installed."));
        run(Duration.ofMinutes(20), List.of(cargo.toString(), "install", "just", "--locked"));
        return installed().orElseThrow(() -> new IOException("Cargo finished, but Just was not found in Cargo's bin directory or PATH."));
    }

    /** Finds an executable without invoking a shell. */
    private static Optional<Path> findExecutable(String name) {
        String suffix = System.getProperty("os.name").toLowerCase().contains("win") ? ".exe" : "";
        Path cargoHome = Path.of(System.getenv().getOrDefault("CARGO_HOME",
                Path.of(System.getProperty("user.home"), ".cargo").toString()));
        Path cargoBin = cargoHome.resolve("bin").resolve(name + suffix);
        if (Files.isExecutable(cargoBin)) return Optional.of(cargoBin);
        String path = System.getenv().getOrDefault("PATH", "");
        for (String directory : path.split(Pattern.quote(System.getProperty("path.separator")))) {
            if (directory.isBlank()) continue;
            Path candidate = Path.of(directory).resolve(name + suffix);
            if (Files.isExecutable(candidate)) return Optional.of(candidate);
        }
        return Optional.empty();
    }

    /** Runs a command with a timeout and captures output without filling process pipes. */
    private static String run(Duration timeout, List<String> command) throws IOException {
        Path outputFile = Files.createTempFile("bevy-just-", ".log");
        Process process = null;
        try {
            process = new ProcessBuilder(new ArrayList<>(command))
                    .redirectErrorStream(true).redirectOutput(outputFile.toFile()).start();
            if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
                throw new IOException(command.getFirst() + " timed out.");
            }
            String output = Files.readString(outputFile, StandardCharsets.UTF_8);
            if (process.exitValue() != 0) throw new IOException(command.getFirst() + " failed: " + output.strip());
            return output;
        } catch (InterruptedException e) {
            if (process != null) process.destroyForcibly();
            Thread.currentThread().interrupt();
            throw new IOException(command.getFirst() + " was interrupted.", e);
        } finally {
            Files.deleteIfExists(outputFile);
        }
    }
}
