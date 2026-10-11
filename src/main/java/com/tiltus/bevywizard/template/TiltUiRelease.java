package com.tiltus.bevywizard.template;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.tiltus.bevywizard.project.TiltUiCompatibility;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Resolves the project's pinned TiltUI release before offering release-specific editor features. */
final class TiltUiRelease {
    private static final Pattern DEPENDENCY = Pattern.compile(
            "(?m)^\\s*tilt-ui\\s*=\\s*(?:\\{[^}]*?\\bversion\\s*=\\s*)?['\"]=?([^'\"]+)['\"]");
    private static final Pattern TABLE = Pattern.compile(
            "(?ms)^\\s*\\[dependencies\\.tilt-ui]\\s*(.*?)(?=^\\s*\\[|\\z)");
    private static final Pattern VERSION = Pattern.compile("(?m)^\\s*version\\s*=\\s*['\"]=?([^'\"]+)['\"]");

    private TiltUiRelease() {}

    static String forFile(PsiFile file) {
        VirtualFile source = file.getVirtualFile();
        if (source == null) return TiltUiCompatibility.VERSION;
        for (VirtualFile directory = source.getParent(); directory != null; directory = directory.getParent()) {
            VirtualFile cargo = directory.findChild("Cargo.toml");
            if (cargo == null) continue;
            PsiFile manifest = PsiManager.getInstance(file.getProject()).findFile(cargo);
            if (manifest == null) return null;
            String sourceText = manifest.getText();
            Matcher dependency = DEPENDENCY.matcher(sourceText);
            String version;
            if (dependency.find()) version = dependency.group(1).trim();
            else {
                Matcher table = TABLE.matcher(sourceText);
                if (!table.find()) return null;
                Matcher versionField = VERSION.matcher(table.group(1));
                if (!versionField.find()) return null;
                version = versionField.group(1).trim();
            }
            return TiltUiCompatibility.supportsVersion(version) ? version : null;
        }
        return TiltUiCompatibility.VERSION; // Detached component files keep useful editor support.
    }
}
