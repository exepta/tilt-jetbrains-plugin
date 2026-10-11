package com.tiltus.bevywizard.template;

import com.intellij.codeInsight.completion.CompletionContributor;
import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionProvider;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.PsiFile;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;

/** Completes file paths even when HTML/CSS built-in references own the attribute. */
public final class TiltAssetCompletionContributor extends CompletionContributor {
    public TiltAssetCompletionContributor() {
        extend(CompletionType.BASIC, PlatformPatterns.psiElement(), new CompletionProvider<>() {
            @Override protected void addCompletions(@NotNull CompletionParameters parameters,
                                                     @NotNull ProcessingContext context,
                                                     @NotNull CompletionResultSet result) {
                PsiFile file = parameters.getOriginalFile();
                int offset = parameters.getOffset();
                for (TiltAssetPaths.Link link : TiltAssetPaths.find(file)) {
                    if (offset < link.start() || offset > link.end()) continue;
                    String prefix = file.getText().substring(link.start(), offset);
                    CompletionResultSet matches = result.withPrefixMatcher(prefix);
                    for (String candidate : TiltAssetPaths.variants(file, link))
                        matches.addElement(LookupElementBuilder.create(candidate).withTypeText("TiltUI asset"));
                    return;
                }
            }
        });
    }
}
