package com.tiltus.bevywizard.template;

import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;

/** Maps JavaScript injected into an HTML event attribute back to its TiltUI template. */
final class TiltEventContext {
    record Location(PsiFile template, int offset) {
        /** Returns whether the host offset belongs to an event expression. */
        boolean isEvent() {
            TemplateScanner.Region region = TemplateScanner.regionAt(template.getText(), offset);
            return region != null && region.event();
        }
    }

    private TiltEventContext() {}

    /** Resolves an injected PSI offset to the matching component template offset. */
    static Location locate(PsiElement element, int offset) {
        PsiFile file = element.getContainingFile();
        if (file == null) return null;
        InjectedLanguageManager manager = InjectedLanguageManager.getInstance(file.getProject());
        PsiFile topLevel = manager.getTopLevelFile(element);
        if (topLevel == null || !TemplateScanner.isTemplate(topLevel.getName())) return null;
        int hostOffset = manager.isInjectedFragment(file) ? manager.injectedToHost(element, offset) : offset;
        if (hostOffset < 0 || hostOffset >= topLevel.getTextLength()) return null;
        return new Location(topLevel, hostOffset);
    }
}
