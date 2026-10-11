package com.tiltus.bevywizard.component;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.rust.cargo.project.model.CargoProjectServiceKt;

/** Adds New → Tilt Component to project-tree context menus in TiltUI projects. */
public final class NewTiltComponentAction extends AnAction {
    /** Registers the action label and icon displayed in the New menu. */
    public NewTiltComponentAction() {
        super("Tilt Component", "Create a TiltUI component", TiltComponentIcon.INSTANCE);
    }

    /** Checks project files away from the UI thread while the New menu is updated. */
    @Override public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }

    /** Shows the action only for directories in a project containing a TiltUI source tree. */
    @Override public void update(@NotNull AnActionEvent event) {
        Project project = event.getProject();
        Path selected = selectedDirectory(event);
        event.getPresentation().setEnabledAndVisible(project != null && selected != null
                && findTiltProjectRoot(project, selected) != null);
    }

    /** Collects settings, writes the planned files, and opens the new HTML template. */
    @Override public void actionPerformed(@NotNull AnActionEvent event) {
        Project project = event.getProject();
        Path selected = selectedDirectory(event);
        if (project == null || selected == null) return;
        Path projectRoot = findTiltProjectRoot(project, selected);
        if (projectRoot == null) return;
        Path ui = projectRoot.resolve("src-ui");
        Path destination = ComponentScaffolder.targetDirectory(projectRoot, selected);
        TiltComponentDialog dialog = new TiltComponentDialog(project, destination);
        if (!dialog.showAndGet()) return;
        try {
            ComponentScaffolder.Plan plan = ComponentScaffolder.plan(
                    ui, destination, dialog.componentName(), dialog.componentType());
            VirtualFile[] opened = new VirtualFile[1];
            WriteCommandAction.runWriteCommandAction(project, () -> {
                try { opened[0] = apply(ui, plan); }
                catch (IOException failure) { throw new UncheckedIOException(failure); }
            });
            if (opened[0] != null) FileEditorManager.getInstance(project).openFile(opened[0], true);
            CargoProjectServiceKt.getCargoProjects(project).refreshAllProjects(false);
        } catch (IOException failure) {
            Messages.showErrorDialog(project, failure.getMessage(), "Cannot Create Tilt Component");
        } catch (UncheckedIOException failure) {
            Messages.showErrorDialog(project, failure.getCause().getMessage(), "Cannot Create Tilt Component");
        }
    }

    /** Finds the directory selected in the project view or the parent of a selected file. */
    private static Path selectedDirectory(AnActionEvent event) {
        VirtualFile file = event.getData(CommonDataKeys.VIRTUAL_FILE);
        if (file == null && event.getData(CommonDataKeys.PSI_ELEMENT) instanceof PsiDirectory directory)
            file = directory.getVirtualFile();
        if (file == null) return null;
        return Path.of(file.isDirectory() ? file.getPath() : file.getParent().getPath()).toAbsolutePath().normalize();
    }

    /** Finds the nearest Cargo package with a src-ui folder below the open project root. */
    private static Path findTiltProjectRoot(Project project, Path selected) {
        String basePath = project.getBasePath();
        if (basePath == null) return null;
        Path base = Path.of(basePath).toAbsolutePath().normalize();
        if (!selected.startsWith(base)) return null;
        for (Path candidate = selected; candidate != null && candidate.startsWith(base); candidate = candidate.getParent()) {
            if (isTiltUiProject(candidate))
                return candidate;
        }
        return isTiltUiProject(base) ? base : null;
    }

    /** Checks for the TiltUI dependency and source folder in a Cargo package. */
    private static boolean isTiltUiProject(Path directory) {
        Path cargo = directory.resolve("Cargo.toml");
        if (!Files.isRegularFile(cargo) || !Files.isDirectory(directory.resolve("src-ui"))) return false;
        try { return Files.readString(cargo).contains("tilt-ui"); }
        catch (IOException ignored) { return false; }
    }

    /** Applies a creation plan through the IDE virtual file system so changes are undoable. */
    private VirtualFile apply(Path ui, ComponentScaffolder.Plan plan) throws IOException {
        VirtualFile root = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(ui);
        if (root == null) throw new IOException("The src-ui folder is not available in the project view.");
        VirtualFile html = null;
        for (Map.Entry<Path, String> entry : plan.created().entrySet()) {
            Path relative = ui.relativize(entry.getKey());
            VirtualFile parent = ensureDirectory(root, relative.getParent());
            VirtualFile file = parent.createChildData(this, relative.getFileName().toString());
            file.setBinaryContent(entry.getValue().getBytes(StandardCharsets.UTF_8));
            if (entry.getKey().equals(plan.htmlFile())) html = file;
        }
        for (Map.Entry<Path, String> entry : plan.updated().entrySet()) {
            String relative = ui.relativize(entry.getKey()).toString().replace('\\', '/');
            VirtualFile file = root.findFileByRelativePath(relative);
            if (file == null) throw new IOException("Could not update " + relative + ".");
            file.setBinaryContent(entry.getValue().getBytes(StandardCharsets.UTF_8));
        }
        return html;
    }

    /** Creates missing nested directories relative to src-ui. */
    private VirtualFile ensureDirectory(VirtualFile root, Path relative) throws IOException {
        VirtualFile current = root;
        if (relative == null) return current;
        for (Path segment : relative) {
            String name = segment.toString();
            VirtualFile child = current.findChild(name);
            current = child == null ? current.createChildDirectory(this, name) : child;
            if (!current.isDirectory()) throw new IOException(name + " is a file, not a directory.");
        }
        return current;
    }
}
