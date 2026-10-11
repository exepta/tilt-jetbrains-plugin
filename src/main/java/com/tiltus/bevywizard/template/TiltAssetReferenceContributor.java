package com.tiltus.bevywizard.template;

import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceBase;
import com.intellij.psi.PsiReferenceContributor;
import com.intellij.psi.PsiReferenceProvider;
import com.intellij.psi.PsiReferenceRegistrar;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/** Makes local UI asset strings navigable, completable, and rename-aware. */
public final class TiltAssetReferenceContributor extends PsiReferenceContributor {
    @Override public void registerReferenceProviders(@NotNull PsiReferenceRegistrar registrar) {
        registrar.registerReferenceProvider(PlatformPatterns.psiElement(), new PsiReferenceProvider() {
            @Override public PsiReference @NotNull [] getReferencesByElement(@NotNull PsiElement element,
                                                                             @NotNull ProcessingContext context) {
                PsiFile file = element.getContainingFile();
                if (file == null || file.getVirtualFile() == null) return PsiReference.EMPTY_ARRAY;
                List<PsiReference> references = new ArrayList<>();
                for (TiltAssetPaths.Link link : TiltAssetPaths.find(file)) {
                    if (owner(file, link) != element) continue;
                    TextRange range = new TextRange(link.start() - element.getTextOffset(),
                            link.end() - element.getTextOffset());
                    references.add(new AssetReference(element, range, file, link));
                }
                return references.toArray(PsiReference.EMPTY_ARRAY);
            }
        }, PsiReferenceRegistrar.HIGHER_PRIORITY);
    }

    static PsiElement owner(PsiFile file, TiltAssetPaths.Link link) {
        PsiElement element = file.findElementAt(link.start());
        while (element != null && element.getTextRange().getEndOffset() < link.end()) element = element.getParent();
        return element == file ? null : element;
    }

    static PsiReference reference(PsiFile file, TiltAssetPaths.Link link) {
        PsiElement element = owner(file, link);
        return element == null ? null : new AssetReference(element,
                new TextRange(link.start() - element.getTextOffset(), link.end() - element.getTextOffset()), file, link);
    }

    private static final class AssetReference extends PsiReferenceBase<PsiElement> {
        private final PsiFile file;
        private final TiltAssetPaths.Link link;

        private AssetReference(PsiElement element, TextRange range, PsiFile file, TiltAssetPaths.Link link) {
            super(element, range, false);
            this.file = file;
            this.link = link;
        }

        @Override public PsiElement resolve() {
            VirtualFile target = TiltAssetPaths.resolve(file, link);
            if (target == null) return null;
            PsiManager manager = PsiManager.getInstance(file.getProject());
            return target.isDirectory() ? manager.findDirectory(target) : manager.findFile(target);
        }

        @Override public Object @NotNull [] getVariants() {
            return TiltAssetPaths.variants(file, link).stream()
                    .map(path -> LookupElementBuilder.create(path).withTypeText("TiltUI asset"))
                    .toArray();
        }

        @Override public PsiElement handleElementRename(@NotNull String newElementName) {
            String current = link.path();
            int separator = current.lastIndexOf('/');
            String replacement = current.substring(0, separator + 1) + newElementName;
            Document document = PsiDocumentManager.getInstance(file.getProject()).getDocument(file);
            if (document == null) return getElement();
            int start = getElement().getTextOffset() + getRangeInElement().getStartOffset();
            int end = getElement().getTextOffset() + getRangeInElement().getEndOffset();
            document.replaceString(start, end, replacement);
            PsiDocumentManager.getInstance(file.getProject()).commitDocument(document);
            return file.findElementAt(start);
        }
    }
}
