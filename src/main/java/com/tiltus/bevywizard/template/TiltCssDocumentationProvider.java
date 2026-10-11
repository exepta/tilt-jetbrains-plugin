package com.tiltus.bevywizard.template;

import com.intellij.lang.documentation.AbstractDocumentationProvider;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.FakePsiElement;

/** Short hover help for TiltUI CSS extensions and supported motion/layout syntax. */
public final class TiltCssDocumentationProvider extends AbstractDocumentationProvider {
    @Override public PsiElement getCustomDocumentationElement(Editor editor, PsiFile file,
                                                               PsiElement contextElement, int offset) {
        if (!TiltCssInspection.isTiltCss(file) || !TiltCssCatalog.supports(TiltUiRelease.forFile(file))) return null;
        String source = file.getText();
        if (offset < 0 || offset >= source.length()) return null;
        int start = offset, end = offset;
        while (start > 0 && (Character.isLetter(source.charAt(start - 1)) || source.charAt(start - 1) == '-')) start--;
        while (end < source.length() && (Character.isLetter(source.charAt(end)) || source.charAt(end) == '-')) end++;
        String name = source.substring(start, end);
        return TiltCssCatalog.documentation(name) == null ? null : new CssWord(file, name, start, end);
    }

    @Override public String getQuickNavigateInfo(PsiElement element, PsiElement originalElement) {
        return element instanceof CssWord word ? word.name + " — " + TiltCssCatalog.documentation(word.name) : null;
    }

    @Override public String generateHoverDoc(PsiElement element, PsiElement originalElement) {
        return generateDoc(element, originalElement);
    }

    @Override public String generateDoc(PsiElement element, PsiElement originalElement) {
        if (!(element instanceof CssWord word)) return null;
        return "<div class='definition'><code>" + word.name + "</code></div><div class='content'>"
                + TiltCssCatalog.documentation(word.name) + "</div>";
    }

    private static final class CssWord extends FakePsiElement {
        private final PsiFile file;
        private final String name;
        private final int start, end;

        private CssWord(PsiFile file, String name, int start, int end) {
            this.file = file; this.name = name; this.start = start; this.end = end;
        }
        @Override public PsiElement getParent() { return file; }
        @Override public PsiFile getContainingFile() { return file; }
        @Override public String getName() { return name; }
        @Override public String getText() { return name; }
        @Override public int getTextOffset() { return start; }
        @Override public TextRange getTextRange() { return new TextRange(start, end); }
        @Override public boolean isValid() { return file.isValid(); }
    }
}
