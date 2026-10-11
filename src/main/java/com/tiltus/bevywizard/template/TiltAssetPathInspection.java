package com.tiltus.bevywizard.template;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

/** Reports only missing paths whose location is defined by TiltUI's source-root contract. */
public final class TiltAssetPathInspection extends LocalInspectionTool {
    @Override public @NotNull String getDisplayName() { return "TiltUI asset paths"; }
    @Override public @NotNull String getShortName() { return "TiltAssetPaths"; }

    @Override public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
        return new PsiElementVisitor() {
            @Override public void visitFile(@NotNull PsiFile file) {
                for (TiltAssetPaths.Link path : TiltAssetPaths.find(file)) {
                    if (!path.required() || TiltAssetPaths.resolve(file, path) != null) continue;
                    holder.registerProblem(file, new TextRange(path.start(), path.end()),
                            "TiltUI " + (path.directory() ? "directory" : "asset") + " not found: " + path.path());
                }
            }
        };
    }
}
