package com.tiltus.bevywizard.template;

import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nullable;

/** Navigates from TiltUI event handlers and bindings to their Rust declarations. */
public final class TiltGotoDeclarationHandler implements GotoDeclarationHandler {
    @Override
    public PsiElement @Nullable [] getGotoDeclarationTargets(@Nullable PsiElement sourceElement,
                                                               int offset,
                                                               Editor editor) {
        if (sourceElement == null) return null;
        PsiFile file = sourceElement.getContainingFile();
        if (file == null) return null;
        for (TiltAssetPaths.Link path : TiltAssetPaths.find(file)) {
            if (offset < path.start() || offset >= path.end()) continue;
            VirtualFile target = TiltAssetPaths.resolve(file, path);
            if (target == null) return null;
            PsiManager manager = PsiManager.getInstance(file.getProject());
            PsiElement declaration = target.isDirectory() ? manager.findDirectory(target) : manager.findFile(target);
            return declaration == null ? null : new PsiElement[]{declaration};
        }
        if (!TemplateScanner.isTemplate(file.getName())) return null;
        TemplateIndex index = TemplateIndex.get(file);
        TemplateScanner.Token token = TemplateScanner.tokenAt(index.source, offset);
        if (token == null) return null;
        RustBindings.Symbol symbol = index.symbolAt(token);
        return symbol == null || symbol.target() == null ? null : new PsiElement[] {symbol.target()};
    }
}
