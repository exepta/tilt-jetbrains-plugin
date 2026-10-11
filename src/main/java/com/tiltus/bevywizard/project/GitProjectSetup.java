package com.tiltus.bevywizard.project;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

/** Creates a Git repository and a project-kind-specific ignore file when requested. */
final class GitProjectSetup {
    private GitProjectSetup() {}

    /** Keeps generated files out of Git while preserving source files and binary lockfiles. */
    static String ignoreFile(ProjectKind kind) {
        StringBuilder content = new StringBuilder("""
                # Rust build output
                /target/

                # Local IDE and OS files
                /.idea/
                *.iml
                .DS_Store
                Thumbs.db
                """);
        if (kind == ProjectKind.LIBRARY || kind == ProjectKind.BLANK)
            content.append("\n# Libraries do not commit a generated dependency lockfile\n/Cargo.lock\n");
        if (kind.hasWasm())
            content.append("\n# Trunk browser build output\n/dist/\n/.trunk/\n");
        return content.toString();
    }

    /** Runs Git in the created project directory and reports failures to the wizard. */
    static void initialize(Path directory) throws IOException {
        Process process;
        try {
            process = new ProcessBuilder("git", "init", "--quiet")
                    .directory(directory.toFile()).redirectErrorStream(true).start();
        } catch (IOException error) {
            throw new IOException("Git is unavailable. Install Git or deselect Initialize Git.", error);
        }
        try {
            if (!process.waitFor(20, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new IOException("Git initialization timed out in " + directory + ".");
            }
        } catch (InterruptedException error) {
            process.destroyForcibly();
            Thread.currentThread().interrupt();
            throw new IOException("Git initialization was interrupted.", error);
        }
        if (process.exitValue() != 0)
            throw new IOException("Git initialization failed: "
                    + new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim());
    }
}
