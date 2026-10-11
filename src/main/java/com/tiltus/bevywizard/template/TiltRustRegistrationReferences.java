package com.tiltus.bevywizard.template;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceBase;
import com.intellij.psi.SmartPointerManager;
import org.jetbrains.annotations.NotNull;
import org.rust.lang.core.psi.RsFunction;
import org.rust.lang.core.psi.RsOuterAttr;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Treats matching #[html_fn] and #[html_method] string names as rename usages. */
final class TiltRustRegistrationReferences {
    private static final Pattern FN = Pattern.compile("html_fn\\s*\\(\\s*\"([^\"]+)\"");
    private static final Pattern METHOD = Pattern.compile("html_method\\s*\\(\\s*\"[^\"]+\"\\s*,\\s*\"([^\"]+)\"");

    private TiltRustRegistrationReferences() {}

    static boolean renamesRegistration(RsFunction function) {
        if (function.getName() == null) return false;
        for (RsOuterAttr attr : function.getOuterAttrList())
            for (Pattern pattern : new Pattern[]{FN, METHOD}) {
                Matcher matcher = pattern.matcher(attr.getText());
                if (matcher.find() && matcher.group(1).substring(matcher.group(1).lastIndexOf('.') + 1)
                        .equals(function.getName())) return true;
            }
        return false;
    }

    static List<PsiReference> forFunction(RsFunction function) {
        String name = function.getName();
        if (name == null) return List.of();
        List<PsiReference> found = new ArrayList<>();
        for (RsOuterAttr attr : function.getOuterAttrList()) {
            for (Pattern pattern : new Pattern[]{FN, METHOD}) {
                Matcher matcher = pattern.matcher(attr.getText());
                while (matcher.find()) {
                    String value = matcher.group(1);
                    int startInValue = pattern == METHOD ? value.lastIndexOf('.') + 1 : 0;
                    if (!value.substring(startInValue).equals(name)) continue;
                    int start = attr.getTextOffset() + matcher.start(1) + startInValue;
                    int end = attr.getTextOffset() + matcher.end(1);
                    PsiFile file = function.getContainingFile();
                    PsiElement owner = file.findElementAt(start);
                    while (owner != null && owner.getTextRange().getEndOffset() < end) owner = owner.getParent();
                    if (owner != null) found.add(reference(owner, function, start, end));
                }
            }
        }
        return found;
    }

    private static PsiReference reference(PsiElement owner, RsFunction function, int start, int end) {
        var target = SmartPointerManager.createPointer(function);
        return new PsiReferenceBase<>(owner,
                TextRange.create(start - owner.getTextOffset(), end - owner.getTextOffset()), false) {
            @Override public PsiElement resolve() { return target.getElement(); }

            @Override public PsiElement handleElementRename(@NotNull String newElementName) {
                PsiFile file = owner.getContainingFile();
                Document document = PsiDocumentManager.getInstance(owner.getProject()).getDocument(file);
                if (document == null) return owner;
                int from = owner.getTextOffset() + getRangeInElement().getStartOffset();
                int to = owner.getTextOffset() + getRangeInElement().getEndOffset();
                document.replaceString(from, to, newElementName);
                PsiDocumentManager.getInstance(owner.getProject()).commitDocument(document);
                return file.findElementAt(from);
            }
        };
    }
}
