package com.tiltus.bevywizard.template;

import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.rust.lang.doc.psi.RsDocComment;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Reads Rust item documentation so template symbols show the same comments as their declarations. */
final class RustDocComments {
    private static final Parser PARSER = Parser.builder().build();
    private static final HtmlRenderer RENDERER = HtmlRenderer.builder().escapeHtml(true).build();

    private RustDocComments() {}

    /** Returns rendered outer {@code ///} or {@code /** ... *&#47;} comments for a Rust declaration. */
    static String html(PsiElement declaration) {
        if (declaration == null || declaration.getContainingFile() == null) return "";
        List<RsDocComment> comments = new ArrayList<>();
        for (RsDocComment comment : PsiTreeUtil.findChildrenOfType(declaration.getContainingFile(), RsDocComment.class)) {
            if (declaration.equals(comment.getOwner())) comments.add(comment);
        }
        if (comments.isEmpty()) return "";
        comments.sort(Comparator.comparingInt(PsiElement::getTextOffset));
        StringBuilder markdown = new StringBuilder();
        for (RsDocComment comment : comments) {
            String raw = comment.getText();
            if (raw.startsWith("///") || raw.startsWith("//!")) markdown.append(raw.substring(3).stripLeading());
            else if (raw.startsWith("/**") || raw.startsWith("/*!")) {
                String block = raw.substring(3, raw.endsWith("*/") ? raw.length() - 2 : raw.length());
                for (String line : block.split("\\R", -1)) markdown.append(line.stripLeading().replaceFirst("^\\* ?", "")).append('\n');
                continue;
            } else continue;
            markdown.append('\n');
        }
        return markdown.isEmpty() ? "" : RENDERER.render(PARSER.parse(markdown.toString()));
    }
}
