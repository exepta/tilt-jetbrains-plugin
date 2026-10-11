package com.tiltus.bevywizard.project;

import com.tiltus.bevywizard.wizard.BevyWizardPeer;

import com.intellij.facet.ui.ValidationResult;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.platform.DirectoryProjectGenerator;
import com.intellij.platform.ProjectGeneratorPeer;
import com.intellij.util.ui.EmptyIcon;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.nio.file.Path;

/** Registers the Bevy project template with RustRover. */
public final class BevyProjectGenerator implements DirectoryProjectGenerator<BevySettings> {
    /** Loads and scales the bundled Bevy icon. */
    private static final Icon LOGO = createLogo();
    /** Returns the project template name in RustRover. */
    @Override public @NotNull String getName() { return "Bevy"; }
    /** Describes the template in the project chooser. */
    @Override public @NotNull String getDescription() { return "Create a Bevy project with optional TiltUI and community crates."; }
    /** Returns the Bevy icon for the project chooser. */
    @Override public @NotNull Icon getLogo() { return LOGO; }

    /** Loads and scales the bundled Bevy icon. */
    private static Icon createLogo() {
        var url = BevyProjectGenerator.class.getResource("/icons/bevy.png");
        if (url == null) return EmptyIcon.create(16);
        return new ImageIcon(new ImageIcon(url).getImage().getScaledInstance(16, 16, Image.SCALE_SMOOTH));
    }
    /** Creates the wizard controls for a new project. */
    @Override public @NotNull ProjectGeneratorPeer<BevySettings> createPeer() { return new BevyWizardPeer(); }

    /** Writes the project and refreshes the IDE file system. */
    @Override public void generateProject(@NotNull Project project, @NotNull VirtualFile baseDir,
                                          @NotNull BevySettings settings, @Nullable Module module) {
        try {
            ProjectScaffolder.create(Path.of(baseDir.getPath()), settings);
            LocalFileSystem.getInstance().refreshAndFindFileByIoFile(Path.of(baseDir.getPath()).toFile());
        } catch (IOException e) {
            throw new IllegalStateException("Unable to create the Bevy project: " + e.getMessage(), e);
        }
    }

    /** Accepts the location selected by RustRover. */
    @Override public @NotNull ValidationResult validate(@NotNull String baseDirPath) {
        return ValidationResult.OK;
    }
}
