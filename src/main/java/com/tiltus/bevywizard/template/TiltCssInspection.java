package com.tiltus.bevywizard.template;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Checks values whose rejection is certain in the selected TiltUI CSS parser. */
public final class TiltCssInspection extends LocalInspectionTool {
    private static final Pattern DECLARATION = Pattern.compile("(?m)([a-z][a-z-]*)\\s*:\\s*([^;{}]+)(?:;|(?=\\}))");

    @Override public @NotNull String getDisplayName() { return "TiltUI CSS values"; }
    @Override public @NotNull String getShortName() { return "TiltCssValues"; }

    static boolean isTiltCss(PsiFile file) {
        if (!file.getName().endsWith(".css")) return false;
        if (file.getName().endsWith(".component.css")) return true;
        VirtualFile source = file.getVirtualFile();
        for (VirtualFile parent = source == null ? null : source.getParent(); parent != null; parent = parent.getParent())
            if (parent.getName().equals("src-ui")) return true;
        return false;
    }

    @Override public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
        return new PsiElementVisitor() {
            @Override public void visitFile(@NotNull PsiFile file) {
                if (!isTiltCss(file) || !TiltCssCatalog.supports(TiltUiRelease.forFile(file))) return;
                Matcher declarations = DECLARATION.matcher(withoutCommentsOrStrings(file.getText()));
                while (declarations.find()) {
                    String original = file.getText().substring(declarations.start(2), declarations.end(2));
                    String error = TiltCssCatalog.invalidValue(declarations.group(1), original);
                    if (error != null) holder.registerProblem(file,
                            new TextRange(declarations.start(2), declarations.end(2)), error);
                }
            }
        };
    }

    /** Keeps offsets stable while ignoring declarations written inside comments and quoted CSS strings. */
    private static String withoutCommentsOrStrings(String source) {
        char[] masked = source.toCharArray();
        for (int at = 0; at < masked.length; ) {
            if (at + 1 < masked.length && masked[at] == '/' && masked[at + 1] == '*') {
                int end = source.indexOf("*/", at + 2);
                end = end < 0 ? masked.length : end + 2;
                for (int i = at; i < end; i++) if (masked[i] != '\n') masked[i] = ' ';
                at = end;
            } else if (masked[at] == '"' || masked[at] == '\'') {
                char quote = masked[at];
                int end = at + 1;
                while (end < masked.length) {
                    if (source.charAt(end) == '\\') { end += 2; continue; }
                    if (source.charAt(end++) == quote) break;
                }
                end = Math.min(end, masked.length);
                for (int i = at; i < end; i++) if (masked[i] != '\n') masked[i] = ' ';
                at = end;
            } else at++;
        }
        return new String(masked);
    }
}
