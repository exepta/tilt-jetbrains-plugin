package com.tiltus.bevywizard.template;

import com.intellij.codeInspection.InspectionSuppressor;
import com.intellij.codeInspection.SuppressQuickFix;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

/** Keeps JavaScript unresolved-reference inspections out of TiltUI Rust event attributes. */
public final class TiltJsInspectionSuppressor implements InspectionSuppressor {
    @Override
    public boolean isSuppressedFor(@NotNull PsiElement element, @NotNull String toolId) {
        String id = toolId.toLowerCase(java.util.Locale.ROOT);
        if (!id.contains("unresolved") && !id.contains("undefined")) return false;
        TiltEventContext.Location location = TiltEventContext.locate(element, element.getTextOffset());
        return location != null && location.isEvent();
    }

    @Override
    public SuppressQuickFix @NotNull [] getSuppressActions(@NotNull PsiElement element, @NotNull String toolId) {
        return SuppressQuickFix.EMPTY_ARRAY;
    }
}
