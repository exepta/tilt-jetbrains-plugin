package com.tiltus.bevywizard.template;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.HighlightInfoFilter;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;

/** Hides JavaScript unresolved-name warnings for Rust handlers registered in a TiltUI template. */
public final class TiltHtmlHighlightFilter implements HighlightInfoFilter {
    @Override
    public boolean accept(@NotNull HighlightInfo info, @NotNull PsiFile file) {
        if (info.getSeverity().compareTo(HighlightSeverity.WARNING) < 0) return true;
        String message = info.getDescription();
        if (message == null) return true;
        String lower = message.toLowerCase(Locale.ROOT);
        int start = info.getStartOffset();
        PsiElement at = start >= 0 && start < file.getTextLength() ? file.findElementAt(start) : null;
        if (at == null) return true;
        TiltEventContext.Location location = TiltEventContext.locate(at, start);
        if (location != null && location.isEvent()
                && (lower.contains("unresolved") || lower.contains("cannot resolve") || lower.contains("not defined")))
            return false;
        if (!(lower.contains("not closed") || lower.contains("unclosed") || lower.contains("closing tag")
                || lower.contains("end tag") || lower.contains("self-closing") || lower.contains("self closing")))
            return true;
        PsiFile host = location == null ? file : location.template();
        if (!TiltComponentTags.isUiHtml(host)) return true;
        PsiElement hostAt = host.findElementAt(location == null ? start : location.offset());
        XmlTag tag = hostAt == null ? null : PsiTreeUtil.getParentOfType(hostAt, XmlTag.class);
        return tag == null || !tag.getText().stripTrailing().endsWith("/>")
                || !TiltComponentTags.isKnownTag(host, tag.getName());
    }
}
