package com.tiltus.bevywizard.template;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Locates the source tree configured by the application instead of assuming src-ui. */
public final class TiltUiSourceRoot {
    private static final Pattern CALL = Pattern.compile("\\.with_source_root\\s*\\(\\s*(concat!|\"[^\"]+\"|[A-Za-z_][A-Za-z_0-9]*)");
    private static final Pattern CONSTANT = Pattern.compile("(?s)\\bconst\\s+%s\\s*:[^=]+?=\\s*(.*?);");
    private static final Pattern QUOTED = Pattern.compile("\"([^\"]+)\"");

    private TiltUiSourceRoot() {}

    public static VirtualFile find(PsiFile file) {
        VirtualFile source = file.getVirtualFile();
        if (source == null) return null;
        VirtualFile packageRoot = packageRoot(source);
        if (packageRoot == null) return ancestorNamed(source, "src-ui");
        String configured = configuredPath(packageRoot, file.getProject());
        if (configured == null) return null; // A dynamic configuration must not silently resolve to src-ui.
        VirtualFile ui = packageRoot.findFileByRelativePath(configured);
        return ui != null && ui.isDirectory() ? ui : null;
    }

    public static VirtualFile packageRoot(VirtualFile source) {
        for (VirtualFile directory = source.isDirectory() ? source : source.getParent();
             directory != null; directory = directory.getParent())
            if (directory.findChild("Cargo.toml") != null) return directory;
        return null;
    }

    public static boolean contains(PsiFile file) {
        VirtualFile source = file.getVirtualFile(), root = find(file);
        return source != null && root != null && (source.equals(root) || source.getPath().startsWith(root.getPath() + "/"));
    }

    public static String configuredRelativePath(PsiFile file) {
        VirtualFile source = file.getVirtualFile();
        VirtualFile root = source == null ? null : packageRoot(source);
        return root == null ? "src-ui" : configuredPath(root, file.getProject());
    }

    private static VirtualFile ancestorNamed(VirtualFile source, String name) {
        for (VirtualFile parent = source.getParent(); parent != null; parent = parent.getParent())
            if (parent.getName().equals(name)) return parent;
        return null;
    }

    private static String configuredPath(VirtualFile packageRoot, Project project) {
        VirtualFile src = packageRoot.findChild("src");
        if (src != null) for (String entry : new String[]{"main.rs", "lib.rs"}) {
            VirtualFile rust = src.findChild(entry);
            if (rust == null) continue;
            PsiFile psi = PsiManager.getInstance(project).findFile(rust);
            if (psi == null) continue;
            String text = psi.getText();
            Matcher call = CALL.matcher(text);
            if (!call.find()) continue;
            String expression = call.group(1);
            if (expression.startsWith("\"")) return safePath(expression.substring(1, expression.length() - 1));
            String declaration = expression.equals("concat!") ? balancedCall(text, call.end(1))
                    : constantValue(text, expression);
            if (declaration == null) return null;
            Matcher strings = QUOTED.matcher(declaration);
            String candidate = null;
            while (strings.find()) if (!strings.group(1).equals("CARGO_MANIFEST_DIR")) candidate = strings.group(1);
            return safePath(candidate);
        }
        return "src-ui";
    }

    private static String constantValue(String text, String name) {
        Matcher constant = Pattern.compile(CONSTANT.pattern().formatted(Pattern.quote(name))).matcher(text);
        return constant.find() ? constant.group(1) : null;
    }

    private static String balancedCall(String text, int afterName) {
        int open = text.indexOf('(', afterName);
        if (open < 0) return null;
        int depth = 0;
        for (int i = open; i < text.length(); i++) {
            if (text.charAt(i) == '(') depth++;
            else if (text.charAt(i) == ')' && --depth == 0) return text.substring(open + 1, i);
        }
        return null;
    }

    private static String safePath(String path) {
        if (path == null) return null;
        String normalized = path.replace('\\', '/').replaceFirst("^/+", "");
        if (normalized.isBlank() || normalized.startsWith("../") || normalized.contains("/../")
                || normalized.equals("..") || normalized.contains("://")) return null;
        return normalized;
    }
}
