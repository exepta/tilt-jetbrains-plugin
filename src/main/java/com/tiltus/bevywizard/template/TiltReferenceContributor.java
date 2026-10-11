package com.tiltus.bevywizard.template;

import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.editor.Document;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.SmartPointerManager;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceBase;
import com.intellij.psi.PsiReferenceContributor;
import com.intellij.psi.PsiReferenceProvider;
import com.intellij.psi.PsiReferenceRegistrar;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/** Makes Rust bindings in TiltUI HTML navigable and available to Find Usages. */
public final class TiltReferenceContributor extends PsiReferenceContributor {
    @Override
    public void registerReferenceProviders(@NotNull PsiReferenceRegistrar registrar) {
        registrar.registerReferenceProvider(PlatformPatterns.psiElement(), new PsiReferenceProvider() {
            @Override
            public PsiReference @NotNull [] getReferencesByElement(@NotNull PsiElement element,
                                                                    @NotNull ProcessingContext context) {
                if (element.getFirstChild() != null && !(element.getLanguage().getID().equals("JavaScript")
                        && element.getText().matches("[A-Za-z_][A-Za-z_0-9]*")))
                    return PsiReference.EMPTY_ARRAY;
                PsiFile file = element.getContainingFile();
                if (file == null || !TemplateScanner.isTemplate(file.getName())) return PsiReference.EMPTY_ARRAY;
                TemplateIndex index = TemplateIndex.get(file);
                int start = element.getTextRange().getStartOffset();
                int end = element.getTextRange().getEndOffset();
                List<PsiReference> references = new ArrayList<>();
                for (TemplateScanner.Token token : index.tokens) {
                    if (token.start() >= end) break;
                    if (token.start() < start || token.end() > end) continue;
                    RustBindings.Symbol symbol = index.symbolAt(token);
                    if (symbol == null || symbol.target() == null) continue;
                    references.add(reference(element, file, token));
                }
                return references.toArray(PsiReference.EMPTY_ARRAY);
            }
        }, PsiReferenceRegistrar.HIGHER_PRIORITY);
    }

    /** Creates a Rust-backed reference even where the HTML editor supplies a JavaScript PSI reference first. */
    static PsiReference reference(PsiElement element, PsiFile file, TemplateScanner.Token token) {
        int start = element.getTextRange().getStartOffset();
        TextRange range = new TextRange(token.start() - start, token.end() - start);
        RustBindings.Symbol symbol = TemplateIndex.get(file).symbolAt(token);
        var pointer = symbol == null || symbol.target() == null ? null
                : SmartPointerManager.createPointer(symbol.target());
        return new PsiReferenceBase<>(element, range, false) {
            @Override
            public PsiElement resolve() {
                return pointer == null ? null : pointer.getElement();
            }

            @Override public boolean isReferenceTo(@NotNull PsiElement target) {
                PsiElement declaration = resolve();
                return declaration != null && (file.getManager().areElementsEquivalent(declaration, target)
                        || declaration instanceof com.intellij.psi.PsiNameIdentifierOwner named
                        && file.getManager().areElementsEquivalent(named.getNameIdentifier(), target));
            }

            @Override public PsiElement handleElementRename(@NotNull String newElementName) {
                Document document = PsiDocumentManager.getInstance(file.getProject()).getDocument(file);
                if (document == null) return element;
                int from = element.getTextOffset() + getRangeInElement().getStartOffset();
                int to = element.getTextOffset() + getRangeInElement().getEndOffset();
                document.replaceString(from, to, newElementName);
                PsiDocumentManager.getInstance(file.getProject()).commitDocument(document);
                return file.findElementAt(from);
            }
        };
    }
}
