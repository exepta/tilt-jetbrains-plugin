package com.tiltus.bevywizard.template;

import com.intellij.lang.documentation.AbstractDocumentationProvider;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.FakePsiElement;
import org.jetbrains.annotations.Nullable;

/** Supplies quick documentation when hovering over TiltUI syntax and Rust bindings. */
public final class TiltDocumentationProvider extends AbstractDocumentationProvider {
    @Override
    public PsiElement getCustomDocumentationElement(Editor editor, PsiFile file, PsiElement contextElement, int offset) {
        TiltComponentTags.AttributeHit attribute = TiltComponentTags.attributeAt(file, offset);
        if (attribute != null) return new AttributeElement(file, attribute, offset);
        String tag = TiltComponentTags.nameAt(file, offset);
        if (tag != null) return new TagElement(file, tag, offset);
        if (!TemplateScanner.isTemplate(file.getName())) return null;
        TemplateScanner.Token token = TemplateScanner.tokenAt(file.getText(), offset);
        return token == null ? null : new TokenElement(file, token);
    }

    @Override
    public String getQuickNavigateInfo(PsiElement element, PsiElement originalElement) {
        return description(element, false);
    }

    @Override
    public String generateHoverDoc(PsiElement element, PsiElement originalElement) {
        return description(element, true);
    }

    @Override
    public String generateDoc(PsiElement element, PsiElement originalElement) {
        return description(element, true);
    }

    private static String description(PsiElement element, boolean html) {
        if (element instanceof AttributeElement attribute) {
            String details = TiltWidgetCatalog.attributeDescription(attribute.hit.tag(), attribute.hit.name());
            return html ? "<div class='definition'><code>" + escape(attribute.hit.name()) + "</code></div>"
                    + "<div class='content'>" + escape(details) + "</div>"
                    : attribute.hit.name() + " — " + details;
        }
        if (element instanceof TagElement tag) {
            String details = TiltComponentTags.description(tag.file, tag.name);
            if (details == null) return null;
            return html ? "<div class='definition'><code>&lt;" + escape(tag.name) + "&gt;</code></div>"
                    + "<div class='content'>" + escape(details) + "</div>"
                    : "<" + tag.name + "> — " + details;
        }
        if (!(element instanceof TokenElement token)) return null;
        TiltSyntax.Entry builtin = TiltSyntax.get(token.token.text());
        if (builtin != null) {
            String title = escape(builtin.signature());
            String body = escape(builtin.description());
            return html ? "<div class='definition'><code>" + title + "</code></div><div class='content'>" + body + "</div>"
                    : builtin.signature() + " — " + builtin.description();
        }
        RustBindings.Symbol symbol = TemplateIndex.get(token.file).symbolAt(token.token);
        if (symbol == null) return null;
        String title = escape(symbol.name());
        String kind = escape(symbol.kind());
        String type = escape(symbol.type());
        if (symbol.target() == null)
            return html ? "<div class='definition'><code>" + title + "</code></div><div class='content'>"
                    + kind + "</div>" : symbol.name() + " — " + symbol.kind();
        String docs = RustDocComments.html(symbol.target());
        return html ? "<div class='definition'><code>" + title + "</code></div><div class='content'>"
                + kind + (type.isBlank() ? "" : " · <code>" + type + "</code>")
                + "<br>Defined in " + escape(symbol.target().getContainingFile().getName())
                + (docs.isBlank() ? "" : "<div class='sections'>" + docs + "</div>") + "</div>"
                : symbol.name() + " — " + symbol.kind() + (type.isBlank() ? "" : " · " + symbol.type())
                + (docs.isBlank() ? "" : " · " + docs.replaceAll("<[^>]+>", " ").trim());
    }

    private static String escape(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    private static final class TokenElement extends FakePsiElement {
        private final PsiFile file;
        private final TemplateScanner.Token token;

        private TokenElement(PsiFile file, TemplateScanner.Token token) {
            this.file = file;
            this.token = token;
        }

        @Override public PsiElement getParent() { return file; }
        @Override public PsiFile getContainingFile() { return file; }
        @Override public String getName() { return token.text(); }
        @Override public String getText() { return token.text(); }
        @Override public int getTextOffset() { return token.start(); }
        @Override public TextRange getTextRange() { return new TextRange(token.start(), token.end()); }
        @Override public boolean isValid() { return file.isValid(); }
        @Override public @Nullable PsiElement getNavigationElement() { return file.findElementAt(token.start()); }
    }

    /** Represents a TiltUI tag name without replacing IntelliJ's HTML PSI. */
    private static final class TagElement extends FakePsiElement {
        private final PsiFile file;
        private final String name;
        private final int offset;

        private TagElement(PsiFile file, String name, int offset) {
            this.file = file;
            this.name = name;
            this.offset = offset;
        }

        @Override public PsiElement getParent() { return file; }
        @Override public PsiFile getContainingFile() { return file; }
        @Override public String getName() { return name; }
        @Override public String getText() { return name; }
        @Override public int getTextOffset() { return offset; }
        @Override public TextRange getTextRange() { return new TextRange(offset, offset + 1); }
        @Override public boolean isValid() { return file.isValid(); }
    }

    private static final class AttributeElement extends FakePsiElement {
        private final PsiFile file;
        private final TiltComponentTags.AttributeHit hit;
        private final int offset;

        private AttributeElement(PsiFile file, TiltComponentTags.AttributeHit hit, int offset) {
            this.file = file;
            this.hit = hit;
            this.offset = offset;
        }

        @Override public PsiElement getParent() { return file; }
        @Override public PsiFile getContainingFile() { return file; }
        @Override public String getName() { return hit.name(); }
        @Override public String getText() { return hit.name(); }
        @Override public int getTextOffset() { return offset; }
        @Override public TextRange getTextRange() { return new TextRange(offset, offset + 1); }
        @Override public boolean isValid() { return file.isValid(); }
    }
}
