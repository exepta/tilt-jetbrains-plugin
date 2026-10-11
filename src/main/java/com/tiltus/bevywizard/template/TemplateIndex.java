package com.tiltus.bevywizard.template;

import com.intellij.psi.PsiFile;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;

import java.util.List;

/** Shares template tokens and companion Rust declarations among editor features. */
final class TemplateIndex {
    final String source;
    final List<TemplateScanner.Token> tokens;
    final RustBindings bindings;

    private TemplateIndex(PsiFile file) {
        source = file.getText();
        tokens = TemplateScanner.tokens(source);
        bindings = RustBindings.forTemplate(file);
    }

    /** Returns a cached view that invalidates when any PSI declaration changes. */
    static TemplateIndex get(PsiFile file) {
        return CachedValuesManager.getCachedValue(file, () -> CachedValueProvider.Result.create(
                new TemplateIndex(file), PsiModificationTracker.getInstance(file.getProject())));
    }

    /** Resolves a scanned identifier token to its Rust symbol, when exposed. */
    RustBindings.Symbol symbolAt(TemplateScanner.Token token) {
        if (token.kind() == TemplateScanner.Kind.USE_TARGET) return bindings.resolveUseTarget(token.text());
        if (token.kind() != TemplateScanner.Kind.IDENTIFIER) return null;
        if (token.text().equals("$event")) return bindings.resolveAt("$event", token.start());
        int left = token.start();
        while (left > 0) {
            char ch = source.charAt(left - 1);
            if (Character.isLetterOrDigit(ch) || ch == '_' || ch == '.' || ch == '-') left--;
            else break;
        }
        if (source.startsWith("i18n.", left) && left < token.end())
            return bindings.resolveAt("i18n", token.start());
        return bindings.resolveAt(pathAt(source, token.start(), token.end()), token.start());
    }

    /** Reads the dotted path ending at one identifier token. */
    static String pathAt(String source, int start, int end) {
        int at = start;
        while (at > 0) {
            char ch = source.charAt(at - 1);
            if (Character.isLetterOrDigit(ch) || ch == '_' || ch == '.' || ch == '$') at--;
            else break;
        }
        return source.substring(at, end);
    }
}
