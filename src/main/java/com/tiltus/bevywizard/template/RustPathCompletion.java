package com.tiltus.bevywizard.template;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import org.rust.lang.core.parser.RustParserUtil;
import org.rust.lang.core.psi.RsPath;
import org.rust.lang.core.psi.RsPathCodeFragment;
import org.rust.lang.core.psi.RsEnumItem;
import org.rust.lang.core.psi.RsFunction;
import org.rust.lang.core.psi.RsStructItem;
import org.rust.lang.core.psi.ext.RsElement;
import org.rust.lang.core.resolve.NameResolutionKt;
import org.rust.lang.core.resolve.Namespace;
import org.rust.lang.core.resolve.PathResolutionContext;
import org.rust.lang.core.resolve.RsResolveProcessorBase;
import org.rust.lang.core.resolve.ScopeEntry;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Asks RustRover's resolver for symbols inside a path typed in an {@code @use} string. */
final class RustPathCompletion {
    record Candidate(String path, String kind, RsElement target) {}

    private RustPathCompletion() {}

    /** Completes modules, structs, enums, and other Rust items from the current Cargo workspace. */
    static List<Candidate> complete(PsiFile template, String prefix) {
        RsElement context = rustContext(template);
        if (context == null) return List.of();
        String qualifier = prefix.substring(0, prefix.lastIndexOf("::") + 2);
        RsPathCodeFragment fragment = new RsPathCodeFragment(template.getProject(),
                qualifier + "__tilt_completion__", false, context,
                RustParserUtil.PathParsingMode.TYPE, EnumSet.of(Namespace.Types, Namespace.Values));
        RsPath path = fragment.getPath();
        if (path == null) return List.of();
        PathResolutionContext resolution = new PathResolutionContext(path, true, false, null);
        Map<String, Candidate> candidates = new LinkedHashMap<>();
        NameResolutionKt.processPathResolveVariants(resolution, resolution.classifyPath(path),
                new RsResolveProcessorBase<>() {
                    @Override public Set<String> getNames() { return null; }
                    @Override public boolean process(ScopeEntry entry) {
                        String name = entry.getName();
                        if (!name.isBlank() && !name.startsWith("__"))
                            candidates.putIfAbsent(qualifier + name,
                                    new Candidate(qualifier + name, kind(entry.getElement()), entry.getElement()));
                        return false;
                    }
                });
        return new ArrayList<>(candidates.values());
    }

    /** Resolves a complete Rust path for navigation and documentation. */
    static RsElement resolve(PsiFile template, String target) {
        RsElement context = rustContext(template);
        if (context == null) return null;
        RsPathCodeFragment fragment = new RsPathCodeFragment(template.getProject(), target, false, context,
                RustParserUtil.PathParsingMode.TYPE, EnumSet.of(Namespace.Types, Namespace.Values));
        RsPath path = fragment.getPath();
        return path == null ? null : path.getReference().resolve();
    }

    private static String kind(RsElement element) {
        if (element instanceof RsStructItem) return "struct";
        if (element instanceof RsEnumItem) return "enum";
        if (element instanceof RsFunction) return "function";
        if (element instanceof org.rust.lang.core.psi.ext.RsMod) return "module";
        return "item";
    }

    private static RsElement rustContext(PsiFile template) {
        VirtualFile html = template.getVirtualFile();
        PsiManager manager = PsiManager.getInstance(template.getProject());
        // Files below src-ui are build-script inputs and can be outside Cargo's Rust module tree.
        // A crate root gives RustRover the dependency graph needed for paths such as bevy::prelude.
        for (VirtualFile root = html == null ? null : html.getParent(); root != null; root = root.getParent()) {
            if (root.findChild("Cargo.toml") == null) continue;
            VirtualFile source = root.findChild("src");
            if (source != null) {
                for (String name : List.of("lib.rs", "main.rs")) {
                    VirtualFile candidate = source.findChild(name);
                    if (candidate == null) continue;
                    PsiFile rust = manager.findFile(candidate);
                    if (rust instanceof RsElement element) return element;
                }
            }
            break;
        }
        if (html != null && html.getParent() != null) {
            VirtualFile companion = html.getParent().findChild(html.getName().replaceFirst("\\.html$", ".rs"));
            if (companion != null) {
                PsiFile rust = manager.findFile(companion);
                if (rust instanceof RsElement element) return element;
            }
        }
        for (VirtualFile source : FilenameIndex.getAllFilesByExt(template.getProject(), "rs",
                GlobalSearchScope.projectScope(template.getProject()))) {
            PsiFile rust = manager.findFile(source);
            if (rust instanceof RsElement element) return element;
        }
        return null;
    }
}
