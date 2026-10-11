package com.tiltus.bevywizard.template;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiReference;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.PsiNameIdentifierOwner;
import com.intellij.util.Processor;
import com.intellij.util.QueryExecutor;
import org.jetbrains.annotations.NotNull;

/** Includes component templates when searching usages of a Rust binding or imported type. */
public final class TiltReferencesSearch implements QueryExecutor<PsiReference, ReferencesSearch.SearchParameters> {
    @Override
    public boolean execute(@NotNull ReferencesSearch.SearchParameters parameters,
                           @NotNull Processor<? super PsiReference> consumer) {
        PsiElement target = parameters.getElementToSearch();
        PsiFile rust = target.getContainingFile();
        if (rust == null || !rust.getName().endsWith(".rs")) return true;
        PsiManager manager = PsiManager.getInstance(target.getProject());
        for (VirtualFile htmlFile : FilenameIndex.getAllFilesByExt(target.getProject(), "html",
                GlobalSearchScope.projectScope(target.getProject()))) {
            if (!TemplateScanner.isTemplate(htmlFile.getName())
                    || !parameters.getScopeDeterminedByUser().contains(htmlFile)) continue;
            PsiFile html = manager.findFile(htmlFile);
            if (html == null) continue;
            TemplateIndex index = TemplateIndex.get(html);
            for (TemplateScanner.Token token : index.tokens) {
                RustBindings.Symbol symbol = index.symbolAt(token);
                if (symbol == null || symbol.target() == null) continue;
                boolean same = manager.areElementsEquivalent(target, symbol.target());
                if (!same && symbol.target() instanceof PsiNameIdentifierOwner named)
                    same = manager.areElementsEquivalent(target, named.getNameIdentifier());
                if (!same) continue;
                PsiElement usage = html.findElementAt(token.start());
                if (usage != null && !consumer.process(TiltReferenceContributor.reference(usage, html, token))) return false;
            }
        }
        return true;
    }
}
