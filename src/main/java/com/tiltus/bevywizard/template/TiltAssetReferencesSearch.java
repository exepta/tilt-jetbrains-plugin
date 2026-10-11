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

/** Finds UI asset references, including route module paths, during file rename. */
public final class TiltAssetReferencesSearch implements QueryExecutor<PsiReference, ReferencesSearch.SearchParameters> {
    @Override public boolean execute(@NotNull ReferencesSearch.SearchParameters parameters,
                                     @NotNull Processor<? super PsiReference> consumer) {
        PsiElement target = parameters.getElementToSearch();
        if (!(target instanceof PsiFile || target instanceof com.intellij.psi.PsiDirectory)) return true;
        VirtualFile virtualTarget = target instanceof PsiFile file ? file.getVirtualFile()
                : ((com.intellij.psi.PsiDirectory) target).getVirtualFile();
        if (virtualTarget == null) return true;
        PsiManager manager = PsiManager.getInstance(target.getProject());
        for (String extension : new String[]{"html", "css", "rs"})
            for (VirtualFile source : FilenameIndex.getAllFilesByExt(target.getProject(), extension,
                    GlobalSearchScope.projectScope(target.getProject()))) {
                if (!parameters.getScopeDeterminedByUser().contains(source)) continue;
                PsiFile file = manager.findFile(source);
                if (file == null) continue;
                for (TiltAssetPaths.Link link : TiltAssetPaths.find(file)) {
                    if (!virtualTarget.equals(TiltAssetPaths.resolve(file, link))) continue;
                    PsiReference reference = TiltAssetReferenceContributor.reference(file, link);
                    if (reference != null && !consumer.process(reference)) return false;
                }
            }
        return true;
    }
}
