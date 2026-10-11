package com.tiltus.bevywizard.icons;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/** Keeps the right-hand catalog button in sync with Cargo features after project creation. */
public final class IconCatalogAvailability implements StartupActivity.DumbAware {
    /** Watches Cargo manifests and updates the catalog button without reopening the project. */
    @Override public void runActivity(@NotNull Project project) {
        project.getMessageBus().connect(project).subscribe(VirtualFileManager.VFS_CHANGES, new BulkFileListener() {
            @Override public void after(@NotNull List<? extends VFileEvent> events) {
                if (events.stream().anyMatch(event -> event.getPath().endsWith("/Cargo.toml"))) update(project);
            }
        });
        update(project);
    }

    private static void update(Project project) {
        ApplicationManager.getApplication().invokeLater(() -> {
            if (project.isDisposed()) return;
            ToolWindow window = ToolWindowManager.getInstance(project).getToolWindow("Tilt UI Icons");
            if (window != null) window.setAvailable(IconCatalogToolWindowFactory.hasIcons(project));
        });
    }
}
