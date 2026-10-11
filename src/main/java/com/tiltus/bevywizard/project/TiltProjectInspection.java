package com.tiltus.bevywizard.project;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.PsiManager;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.tiltus.bevywizard.template.TiltUiSourceRoot;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Checks the files needed to build and run a TiltUI project from the editor. */
public final class TiltProjectInspection extends LocalInspectionTool {
    private static final Pattern TILT_DEPENDENCY = Pattern.compile("(?m)^\\s*(?:tilt-ui\\s*=|\\[dependencies\\.tilt-ui])");
    private static final Pattern BUILD_PATH = Pattern.compile("(?m)^\\s*build\\s*=\\s*\"([^\"]+)\"");
    private static final Pattern BUILD_DISABLED = Pattern.compile("(?m)^\\s*build\\s*=\\s*false\\b");
    private static final Pattern LINK = Pattern.compile("<link\\b[^>]*>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern ATTRIBUTE = Pattern.compile("([\\w-]+)\\s*=\\s*(['\"])(.*?)\\2", Pattern.DOTALL);
    private static final Pattern ADD_PLUGIN = Pattern.compile("\\.add_plugins\\s*\\(\\s*(TiltUiPlugin|DefaultPlugins)\\b");
    private static final List<String> COMPONENT_EXTENSIONS = List.of("html", "css", "rs");

    @Override public @NotNull String getDisplayName() { return "TiltUI project setup"; }
    @Override public @NotNull String getShortName() { return "TiltProjectSetup"; }

    @Override public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
        return new PsiElementVisitor() {
            @Override public void visitFile(@NotNull PsiFile file) {
                inspect(file, holder);
            }
        };
    }

    private static void inspect(PsiFile file, ProblemsHolder holder) {
        VirtualFile virtualFile = file.getVirtualFile();
        if (virtualFile == null) return;
        VirtualFile root = tiltRoot(virtualFile, file.getProject());
        if (root == null) return;
        String relative = root.getPath().equals(virtualFile.getPath()) ? "" :
                virtualFile.getPath().substring(root.getPath().length() + 1);
        String uiPath = TiltUiSourceRoot.configuredRelativePath(file);
        if (relative.equals("Cargo.toml")) inspectCargo(file, root, uiPath, holder);
        else if (uiPath != null && relative.equals(uiPath + "/index.html")) inspectStylesheets(file, root, uiPath, holder);
        else if (uiPath != null && relative.startsWith(uiPath + "/") && isComponentFile(file.getName()))
            inspectComponent(file, root, holder);
        else if (relative.equals("src/main.rs") || relative.equals("src/lib.rs"))
            inspectPluginOrder(file, holder);
    }

    /** Finds a package whose Cargo dependencies explicitly include TiltUI. */
    private static VirtualFile tiltRoot(VirtualFile file, Project project) {
        for (VirtualFile directory = file.getParent(); directory != null; directory = directory.getParent()) {
            VirtualFile cargo = directory.findChild("Cargo.toml");
            if (cargo != null && !cargo.isDirectory()) {
                PsiFile cargoPsi = PsiManager.getInstance(project).findFile(cargo);
                if (cargoPsi != null && TILT_DEPENDENCY.matcher(cargoPsi.getText()).find()) return directory;
                return null;
            }
        }
        return null;
    }

    private static void inspectCargo(PsiFile cargo, VirtualFile root, String uiPath, ProblemsHolder holder) {
        if (uiPath == null) return;
        VirtualFile ui = root.findFileByRelativePath(uiPath);
        if (ui == null || ui.isDirectory() && ui.findChild("index.html") == null)
            report(holder, cargo, 0, "TiltUI entry document " + uiPath + "/index.html is missing.",
                    new CreateFileFix(uiPath + "/index.html", "<!doctype html>\n<html><head></head><body><main></main></body></html>\n"));
        else if (!ui.isDirectory())
            report(holder, cargo, 0, uiPath + " must be a directory containing index.html.");

        Matcher configured = BUILD_PATH.matcher(cargo.getText());
        boolean disabled = BUILD_DISABLED.matcher(cargo.getText()).find();
        String buildPath = configured.find() ? configured.group(1) : "build.rs";
        if (disabled) {
            report(holder, cargo, 0, "Cargo disables the build script required for TiltUI component discovery.");
            return;
        }
        VirtualFile build = root.findFileByRelativePath(buildPath);
        if (build == null) {
            LocalQuickFix[] fixes = buildPath.equals("build.rs")
                    ? new LocalQuickFix[]{new CreateFileFix("build.rs", buildScript())} : LocalQuickFix.EMPTY_ARRAY;
            report(holder, cargo, 0, "TiltUI build script " + buildPath + " is missing.", fixes);
        } else {
            PsiFile buildPsi = PsiManager.getInstance(cargo.getProject()).findFile(build);
            if (buildPsi != null && !buildPsi.getText().contains("tilt_ui_build::build("))
                report(holder, cargo, 0, buildPath + " does not call tilt_ui_build::build() for component discovery.");
        }
    }

    private static String buildScript() {
        return "fn main() {\n    tilt_ui_build::build().expect(\"TiltUI component discovery failed\");\n}\n";
    }

    private static void inspectStylesheets(PsiFile index, VirtualFile root, String uiPath, ProblemsHolder holder) {
        String html = index.getText();
        Matcher links = LINK.matcher(html);
        while (links.find()) {
            String tag = links.group();
            String rel = attribute(tag, "rel");
            if (rel == null || !List.of(rel.toLowerCase().split("\\s+")).contains("stylesheet")) continue;
            String href = attribute(tag, "href");
            if (href == null || href.isBlank() || href.startsWith("#") || href.startsWith("//")
                    || href.matches("(?i)^[a-z][a-z0-9+.-]*:.*")) continue;
            String clean = href.split("[?#]", 2)[0];
            if (clean.isBlank()) continue;
            String relative = clean.startsWith("/") ? clean.substring(1) : clean;
            Path normalized;
            try { normalized = Path.of(uiPath).resolve(relative).normalize(); }
            catch (java.nio.file.InvalidPathException invalid) {
                report(holder, index, links.start(), "Invalid local stylesheet path: " + href);
                continue;
            }
            if (!normalized.startsWith(uiPath)) {
                report(holder, index, links.start(), "Stylesheet path leaves the TiltUI source directory: " + href);
                continue;
            }
            if (root.findFileByRelativePath(normalized.toString().replace('\\', '/')) != null) continue;
            String target = normalized.toString().replace('\\', '/');
            LocalQuickFix[] fixes = target.endsWith(".css")
                    ? new LocalQuickFix[]{new CreateFileFix(target, "/* TiltUI stylesheet */\n")}
                    : LocalQuickFix.EMPTY_ARRAY;
            report(holder, index, links.start(), "Local TiltUI stylesheet is missing: " + href, fixes);
        }
    }

    private static String attribute(String tag, String name) {
        Matcher attributes = ATTRIBUTE.matcher(tag);
        while (attributes.find()) if (attributes.group(1).equalsIgnoreCase(name)) return attributes.group(3);
        return null;
    }

    private static boolean isComponentFile(String name) {
        return COMPONENT_EXTENSIONS.stream().anyMatch(ext -> name.endsWith(".component." + ext));
    }

    private static void inspectComponent(PsiFile file, VirtualFile root, ProblemsHolder holder) {
        String name = file.getName();
        String stem = name.substring(0, name.lastIndexOf(".component."));
        VirtualFile directory = file.getVirtualFile().getParent();
        List<String> missing = new ArrayList<>();
        List<LocalQuickFix> fixes = new ArrayList<>();
        for (String extension : COMPONENT_EXTENSIONS) {
            String companion = stem + ".component." + extension;
            if (directory.findChild(companion) != null) continue;
            missing.add(companion);
            String content = switch (extension) {
                case "html" -> "<div></div>\n";
                case "css" -> "/* Styles for " + stem + " */\n";
                default -> "// Logic for " + stem + "\n";
            };
            String directoryPath = directory.getPath().substring(root.getPath().length() + 1);
            fixes.add(new CreateFileFix(directoryPath + "/" + companion, content));
        }
        if (!missing.isEmpty()) report(holder, file, 0,
                "TiltUI component is incomplete; missing " + String.join(" and ", missing) + ".",
                fixes.toArray(LocalQuickFix[]::new));
    }

    private static void inspectPluginOrder(PsiFile rust, ProblemsHolder holder) {
        Matcher plugins = ADD_PLUGIN.matcher(rust.getText());
        int defaultPosition = -1;
        while (plugins.find()) {
            if (plugins.group(1).equals("DefaultPlugins")) defaultPosition = plugins.start(1);
            else if (defaultPosition >= 0) {
                report(holder, rust, plugins.start(1),
                        "TiltUiPlugin must be added before DefaultPlugins so TiltUI can configure Bevy UI.");
                return;
            }
        }
    }

    private static void report(ProblemsHolder holder, PsiFile file, int offset, String message, LocalQuickFix... fixes) {
        if (file.getTextLength() == 0) return;
        holder.registerProblem(file, TextRange.from(Math.min(offset, file.getTextLength() - 1), 1), message, fixes);
    }

    /** Adds only a missing file, preserving every existing project file. */
    private record CreateFileFix(String relativePath, String content) implements LocalQuickFix {
        @Override public @NotNull String getFamilyName() { return "Create missing TiltUI file"; }
        @Override public @NotNull String getName() { return "Create " + relativePath; }

        @Override public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
            PsiFile context = descriptor.getPsiElement().getContainingFile();
            if (context == null || context.getVirtualFile() == null) return;
            VirtualFile root = tiltRoot(context.getVirtualFile(), project);
            if (root == null) return;
            PsiDirectory directory = PsiManager.getInstance(project).findDirectory(root);
            if (directory == null) return;
            String[] segments = relativePath.split("/");
            for (int i = 0; i < segments.length - 1; i++) {
                PsiDirectory child = directory.findSubdirectory(segments[i]);
                directory = child == null ? directory.createSubdirectory(segments[i]) : child;
            }
            String name = segments[segments.length - 1];
            if (directory.findFile(name) != null) return;
            var type = FileTypeManager.getInstance().getFileTypeByFileName(name);
            directory.add(PsiFileFactory.getInstance(project).createFileFromText(name, type, content));
        }
    }
}
