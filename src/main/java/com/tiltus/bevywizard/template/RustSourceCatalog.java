package com.tiltus.bevywizard.template;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.psi.util.PsiTreeUtil;
import org.rust.lang.core.psi.RsEnumItem;
import org.rust.lang.core.psi.RsNamedFieldDecl;
import org.rust.lang.core.psi.RsStructItem;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Indexes project Rust types that can be imported by a TiltUI {@code @use} directive. */
final class RustSourceCatalog {
    record Type(String path, String name, String kind, PsiElement target, Map<String, RustBindings.Symbol> fields) {}

    private final List<Type> exposed;
    private final Map<String, Map<String, RustBindings.Symbol>> fieldsByType;
    private final Map<String, Map<String, RustBindings.Symbol>> fieldsByPath;

    private RustSourceCatalog(List<Type> exposed, Map<String, Map<String, RustBindings.Symbol>> fieldsByType,
                              Map<String, Map<String, RustBindings.Symbol>> fieldsByPath) {
        this.exposed = List.copyOf(exposed);
        this.fieldsByType = Map.copyOf(fieldsByType);
        this.fieldsByPath = Map.copyOf(fieldsByPath);
    }

    /** Returns a project-wide snapshot, refreshed after Rust PSI changes. */
    static RustSourceCatalog get(Project project) {
        return CachedValuesManager.getManager(project).getCachedValue(project, () -> CachedValueProvider.Result.create(
                scan(project), PsiModificationTracker.getInstance(project)));
    }

    /** Returns all annotated structs and enums from project Rust sources. */
    List<Type> types() { return exposed; }

    /** Returns struct fields, including nested structs that need no separate import. */
    Map<String, Map<String, RustBindings.Symbol>> fieldsByType() { return fieldsByType; }

    /** Finds fields of a local Rust type even when its declaration is not itself annotated. */
    Map<String, RustBindings.Symbol> fieldsAt(String path) { return fieldsByPath.get(path); }

    /** Resolves a full Rust path or its final type-name segment. */
    Type resolve(String target) {
        for (Type type : exposed) if (type.path().equals(target)) return type;
        if (target.contains("::")) return null;
        for (Type type : exposed) if (type.name().equals(target)) return type;
        return null;
    }

    private static RustSourceCatalog scan(Project project) {
        List<Type> result = new ArrayList<>();
        Map<String, Map<String, RustBindings.Symbol>> fieldsByType = new LinkedHashMap<>();
        Map<String, Map<String, RustBindings.Symbol>> fieldsByPath = new LinkedHashMap<>();
        PsiManager manager = PsiManager.getInstance(project);
        for (VirtualFile source : FilenameIndex.getAllFilesByExt(project, "rs", GlobalSearchScope.projectScope(project))) {
            PsiFile file = manager.findFile(source);
            if (file == null) continue;
            String prefix = modulePrefix(source);
            for (RsStructItem item : PsiTreeUtil.findChildrenOfType(file, RsStructItem.class)) {
                if (item.getName() == null) continue;
                Map<String, RustBindings.Symbol> fields = new LinkedHashMap<>();
                if (item.getBlockFields() != null) {
                    for (RsNamedFieldDecl field : item.getBlockFields().getNamedFieldDeclList()) {
                        if (field.getName() == null) continue;
                        String type = field.getTypeReference() == null ? "" : field.getTypeReference().getText();
                        fields.put(field.getName(), new RustBindings.Symbol(field.getName(), "Rust field", type, field));
                    }
                }
                fieldsByType.putIfAbsent(item.getName(), fields);
                fieldsByPath.putIfAbsent(prefix + item.getName(), fields);
                if (exposed(item.getOuterAttrList()))
                    result.add(new Type(prefix + item.getName(), item.getName(), "Rust struct", item, fields));
            }
            for (RsEnumItem item : PsiTreeUtil.findChildrenOfType(file, RsEnumItem.class)) {
                if (item.getName() == null || !exposed(item.getOuterAttrList())) continue;
                result.add(new Type(prefix + item.getName(), item.getName(), "Rust enum", item, Map.of()));
            }
        }
        return new RustSourceCatalog(result, fieldsByType, fieldsByPath);
    }

    private static boolean exposed(List<org.rust.lang.core.psi.RsOuterAttr> attributes) {
        return TiltRustAttributes.has(attributes, "html_shared")
                || TiltRustAttributes.has(attributes, "html_use")
                || TiltRustAttributes.derives(attributes, "UiStore");
    }

    private static String modulePrefix(VirtualFile source) {
        VirtualFile parent = source.getParent();
        List<String> modules = new ArrayList<>();
        VirtualFile root = parent;
        while (root != null && !root.getName().equals("src")) {
            if (root.getName().equals("src-ui")) return "";
            modules.add(0, root.getName().replace('-', '_'));
            root = root.getParent();
        }
        if (root == null) return "";
        String stem = source.getNameWithoutExtension();
        if (!stem.equals("lib") && !stem.equals("main") && !stem.equals("mod")) modules.add(stem.replace('-', '_'));
        return "crate::" + (modules.isEmpty() ? "" : String.join("::", modules) + "::");
    }
}
