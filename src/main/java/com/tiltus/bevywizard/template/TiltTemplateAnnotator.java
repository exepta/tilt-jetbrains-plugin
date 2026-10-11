package com.tiltus.bevywizard.template;

import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

/** Adds TiltUI keyword, action, and Rust binding colors to component HTML. */
public final class TiltTemplateAnnotator implements Annotator {
    @Override
    public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
        PsiFile file = element.getContainingFile();
        if (file == null) return;
        if (!TemplateScanner.isTemplate(file.getName())) {
            TiltEventContext.Location location = TiltEventContext.locate(element, element.getTextOffset());
            if (location == null || !location.isEvent()) return;
            TemplateIndex hostIndex = TemplateIndex.get(location.template());
            TemplateScanner.Token hostToken = TemplateScanner.tokenAt(hostIndex.source, location.offset());
            RustBindings.Symbol handler = hostToken == null ? null : hostIndex.symbolAt(hostToken);
            if (handler != null && handler.kind().equals("Event handler") && element.getText().equals(hostToken.text()))
                holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
                        .range(element.getTextRange()).textAttributes(DefaultLanguageHighlighterColors.FUNCTION_CALL).create();
            return;
        }
        if (element.getFirstChild() != null) return;
        TemplateIndex index = TemplateIndex.get(file);
        TextRange elementRange = element.getTextRange();
        for (TemplateScanner.Token token : index.tokens) {
            if (token.start() >= elementRange.getEndOffset()) break;
            if (token.start() < elementRange.getStartOffset() || token.end() > elementRange.getEndOffset()) continue;
            TextAttributesKey color;
            if (token.kind() == TemplateScanner.Kind.DIRECTIVE) color = DefaultLanguageHighlighterColors.KEYWORD;
            else if (token.kind() == TemplateScanner.Kind.ACTION) color = DefaultLanguageHighlighterColors.FUNCTION_CALL;
            else if (token.kind() == TemplateScanner.Kind.USE_TARGET) color = DefaultLanguageHighlighterColors.CLASS_NAME;
            else {
                RustBindings.Symbol symbol = index.symbolAt(token);
                if (symbol == null) {
                    continue;
                }
                color = symbol.kind().contains("handler") || symbol.kind().contains("method")
                        ? DefaultLanguageHighlighterColors.FUNCTION_CALL
                        : DefaultLanguageHighlighterColors.INSTANCE_FIELD;
            }
            holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
                    .range(new TextRange(token.start(), token.end())).textAttributes(color).create();
        }
    }
}
