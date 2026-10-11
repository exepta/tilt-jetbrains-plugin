package com.tiltus.bevywizard.template;

import com.intellij.codeInsight.completion.CompletionContributor;
import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionProvider;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.psi.PsiFile;
import com.intellij.util.ProcessingContext;
import com.intellij.patterns.PlatformPatterns;
import org.jetbrains.annotations.NotNull;

/** Adds TiltUI-specific declarations and values without replacing normal CSS completion. */
public final class TiltCssCompletionContributor extends CompletionContributor {
    public TiltCssCompletionContributor() {
        extend(CompletionType.BASIC, PlatformPatterns.psiElement(), new CompletionProvider<>() {
            @Override protected void addCompletions(@NotNull CompletionParameters parameters,
                                                     @NotNull ProcessingContext context,
                                                     @NotNull CompletionResultSet result) {
                PsiFile file = parameters.getOriginalFile();
                if (!TiltCssInspection.isTiltCss(file) || !TiltCssCatalog.supports(TiltUiRelease.forFile(file))) return;
                String source = file.getText();
                int offset = Math.min(parameters.getOffset(), source.length());
                int open = source.lastIndexOf('{', Math.max(0, offset - 1));
                if (open < 0 || source.lastIndexOf('}', Math.max(0, offset - 1)) > open) return;
                int start = Math.max(open, source.lastIndexOf(';', Math.max(0, offset - 1))) + 1;
                int colon = source.indexOf(':', start);
                if (colon < 0 || colon >= offset) {
                    int word = offset;
                    while (word > start && propertyChar(source.charAt(word - 1))) word--;
                    CompletionResultSet properties = result.withPrefixMatcher(source.substring(word, offset));
                    TiltCssCatalog.CUSTOM.forEach((name, description) -> properties.addElement(
                            LookupElementBuilder.create(name).withTypeText("TiltUI CSS")
                                    .withTailText("  " + description, true)));
                    return;
                }
                String property = source.substring(start, colon).trim();
                int icon = source.lastIndexOf("tilt-icon:", offset);
                if (icon > colon && icon + "tilt-icon:".length() <= offset) {
                    int nameStart = icon + "tilt-icon:".length();
                    String prefix = source.substring(nameStart, offset);
                    if (prefix.matches("[a-z0-9-]*")) {
                        CompletionResultSet icons = result.withPrefixMatcher(prefix);
                        for (String name : TiltCssCatalog.iconCompletions(prefix))
                            icons.addElement(LookupElementBuilder.create(name).withTypeText("TiltUI icon"));
                    }
                    return;
                }
                int valueStart = offset;
                while (valueStart > colon + 1 && (propertyChar(source.charAt(valueStart - 1))
                        || Character.isDigit(source.charAt(valueStart - 1)))) valueStart--;
                CompletionResultSet values = result.withPrefixMatcher(source.substring(valueStart, offset));
                for (String value : TiltCssCatalog.completions(property))
                    values.addElement(LookupElementBuilder.create(value).withTypeText("TiltUI " + property));
            }
        });
    }

    private static boolean propertyChar(char ch) { return Character.isLetter(ch) || ch == '-'; }
}
