package com.tiltus.bevywizard.template;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.TextRange;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceBase;
import com.intellij.psi.PsiReferenceContributor;
import com.intellij.psi.PsiReferenceProvider;
import com.intellij.psi.PsiReferenceRegistrar;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** References custom tags and route component IDs to their component template. */
public final class TiltComponentUsageReferences extends PsiReferenceContributor {
    private static final Pattern OPEN_TAG = Pattern.compile("<([a-z][a-z0-9-]*)\\b");
    private static final Pattern ROUTE_ID = Pattern.compile("tilt_ui_component_id\\s*\\(\\s*\"([a-z][a-z0-9-]*)\"");

    @Override public void registerReferenceProviders(@NotNull PsiReferenceRegistrar registrar) {
        registrar.registerReferenceProvider(PlatformPatterns.psiElement(), new PsiReferenceProvider() {
            @Override public PsiReference @NotNull [] getReferencesByElement(@NotNull PsiElement element,
                                                                            @NotNull ProcessingContext context) {
                PsiFile file = element.getContainingFile();
                if (file == null || !TiltUiSourceRoot.contains(file)) return PsiReference.EMPTY_ARRAY;
                List<PsiReference> refs = new ArrayList<>();
                for (Usage usage : usages(file)) if (owner(file, usage) == element)
                    refs.add(reference(element, file, usage));
                return refs.toArray(PsiReference.EMPTY_ARRAY);
            }
        }, PsiReferenceRegistrar.HIGHER_PRIORITY);
    }

    static List<Usage> usages(PsiFile file) {
        Pattern pattern = file.getName().endsWith(".html") ? OPEN_TAG
                : file.getName().endsWith(".rs") ? ROUTE_ID : null;
        if (pattern == null) return List.of();
        List<Usage> found = new ArrayList<>();
        Matcher matcher = pattern.matcher(file.getText());
        var components = TiltComponentTags.discover(file.getProject());
        while (matcher.find()) {
            String name = matcher.group(1);
            if (components.containsKey(name)) found.add(new Usage(matcher.start(1), matcher.end(1), name));
        }
        return found;
    }

    private static PsiElement owner(PsiFile file, Usage usage) {
        PsiElement leaf = file.findElementAt(usage.start());
        if (leaf == null) return null;
        if (file.getName().endsWith(".html")) return PsiTreeUtil.getParentOfType(leaf, XmlTag.class, false);
        while (leaf.getParent() != null && leaf.getTextRange().getEndOffset() < usage.end()) leaf = leaf.getParent();
        return leaf;
    }

    static PsiReference reference(PsiFile file, Usage usage) {
        PsiElement element = owner(file, usage);
        return element == null ? null : reference(element, file, usage);
    }

    private static PsiReference reference(PsiElement element, PsiFile file, Usage usage) {
        return new PsiReferenceBase<>(element,
                new TextRange(usage.start() - element.getTextOffset(), usage.end() - element.getTextOffset()), false) {
            @Override public PsiElement resolve() { return TiltComponentTags.discover(file.getProject()).get(usage.name()); }

            @Override public PsiElement handleElementRename(@NotNull String newElementName) {
                String stem = TiltComponentRenameProcessor.stem(newElementName);
                if (stem == null) stem = newElementName;
                if (element instanceof XmlTag tag) { tag.setName(stem); return tag; }
                Document document = PsiDocumentManager.getInstance(file.getProject()).getDocument(file);
                if (document == null) return element;
                int start = element.getTextOffset() + getRangeInElement().getStartOffset();
                int end = element.getTextOffset() + getRangeInElement().getEndOffset();
                document.replaceString(start, end, stem);
                PsiDocumentManager.getInstance(file.getProject()).commitDocument(document);
                return file.findElementAt(start);
            }
        };
    }

    record Usage(int start, int end, String name) {}
}
