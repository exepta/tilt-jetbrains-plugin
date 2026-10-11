package com.tiltus.bevywizard.template;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.PsiReference;
import com.intellij.refactoring.rename.RenamePsiElementProcessor;
import org.jetbrains.annotations.NotNull;
import org.rust.lang.core.psi.RsFunction;
import org.rust.lang.core.psi.RsNamedFieldDecl;

import java.util.Map;
import java.util.Collection;
import java.util.ArrayList;

/** Renames the component's companion files in the same IDE refactoring transaction. */
public final class TiltComponentRenameProcessor extends RenamePsiElementProcessor {
    private static final String[] SUFFIXES = {".component.html", ".component.css", ".component.rs", ".route.rs"};

    @Override public boolean canProcessElement(@NotNull PsiElement element) {
        if (element instanceof PsiFile file)
            return stem(file.getName()) != null && TiltUiSourceRoot.contains(file);
        PsiFile file = element.getContainingFile();
        return file != null && file.getName().endsWith(".component.rs") && TiltUiSourceRoot.contains(file)
                && (element instanceof RsNamedFieldDecl || element instanceof RsFunction);
    }

    @Override public void prepareRenaming(@NotNull PsiElement element, @NotNull String newName,
                                          @NotNull Map<PsiElement, String> allRenames, @NotNull SearchScope scope) {
        if (!(element instanceof PsiFile file)) return;
        String oldStem = stem(file.getName());
        String newStem = stem(newName);
        if (newStem == null || oldStem == null || oldStem.equals(newStem)) return;
        VirtualFile directory = file.getVirtualFile().getParent();
        PsiManager manager = PsiManager.getInstance(file.getProject());
        for (String suffix : SUFFIXES) {
            VirtualFile sibling = directory.findChild(oldStem + suffix);
            if (sibling == null || sibling.equals(file.getVirtualFile())) continue;
            PsiFile companion = manager.findFile(sibling);
            if (companion != null) allRenames.put(companion, newStem + suffix);
        }
    }

    @Override public Collection<PsiReference> findReferences(PsiElement element, SearchScope scope, boolean searchInComments) {
        // Rust may limit a private field/function to its .rs file. TiltUI templates are outside that scope.
        SearchScope effective = element instanceof RsNamedFieldDecl || element instanceof RsFunction
                ? GlobalSearchScope.projectScope(element.getProject()) : scope;
        Collection<PsiReference> references = new ArrayList<>(super.findReferences(element, effective, searchInComments));
        if (element instanceof RsFunction function) {
            if (!TiltRustRegistrationReferences.renamesRegistration(function))
                references.removeIf(reference -> reference.getElement().getContainingFile().getName().endsWith(".html"));
            references.addAll(TiltRustRegistrationReferences.forFunction(function));
        }
        return references;
    }

    static String stem(String name) {
        for (String suffix : SUFFIXES) if (name.endsWith(suffix)) return name.substring(0, name.length() - suffix.length());
        return null;
    }
}
