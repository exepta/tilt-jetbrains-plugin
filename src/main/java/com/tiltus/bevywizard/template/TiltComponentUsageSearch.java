package com.tiltus.bevywizard.template;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiReference;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.util.Processor;
import com.intellij.util.QueryExecutor;
import org.jetbrains.annotations.NotNull;

/** Includes custom tags and route IDs in the standard Rename preview. */
public final class TiltComponentUsageSearch implements QueryExecutor<PsiReference, ReferencesSearch.SearchParameters> {
    @Override public boolean execute(@NotNull ReferencesSearch.SearchParameters parameters,
                                     @NotNull Processor<? super PsiReference> consumer) {
        PsiElement target = parameters.getElementToSearch();
        if (!(target instanceof PsiFile component) || !component.getName().endsWith(".component.html")) return true;
        String stem = TiltComponentRenameProcessor.stem(component.getName());
        PsiManager manager = PsiManager.getInstance(component.getProject());
        for (String extension : new String[]{"html", "rs"})
            for (VirtualFile source : FilenameIndex.getAllFilesByExt(component.getProject(), extension,
                    GlobalSearchScope.projectScope(component.getProject()))) {
                if (!parameters.getScopeDeterminedByUser().contains(source)) continue;
                PsiFile file = manager.findFile(source);
                if (file == null || !TiltUiSourceRoot.contains(file)) continue;
                for (TiltComponentUsageReferences.Usage usage : TiltComponentUsageReferences.usages(file)) {
                    if (!stem.equals(usage.name())) continue;
                    PsiReference reference = TiltComponentUsageReferences.reference(file, usage);
                    if (reference != null && !consumer.process(reference)) return false;
                }
            }
        return true;
    }
}
