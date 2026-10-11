package com.tiltus.bevywizard.template;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.util.PsiTreeUtil;
import org.rust.lang.core.psi.RsFunction;
import org.rust.lang.core.psi.RsNamedFieldDecl;
import org.rust.lang.core.psi.RsStructItem;
import org.rust.lang.core.psi.RsEnumItem;
import org.rust.lang.core.psi.RsUseItem;
import org.rust.lang.core.psi.RsUseSpeck;

import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Reads the Rust declarations that TiltUI actually exposes to a component template. */
final class RustBindings {
    record Symbol(String name, String kind, String type, PsiElement target, boolean mutableStore) {
        Symbol(String name, String kind, String type, PsiElement target) {
            this(name, kind, type, target, false);
        }
    }

    private static final Pattern TYPE_NAME = Pattern.compile("[A-Za-z_][A-Za-z_0-9]*");
    private record ScopedLocal(TemplateScanner.Local scope, Symbol symbol) {}

    private final Map<String, Symbol> roots = new LinkedHashMap<>();
    private final Map<String, Map<String, Symbol>> fields = new LinkedHashMap<>();
    private final Map<String, Symbol> methods = new LinkedHashMap<>();
    private final List<ScopedLocal> locals = new ArrayList<>();
    private RustSourceCatalog catalog;
    private PsiFile template;

    private RustBindings() {}

    /** Extracts exposed stores, shared resources, methods, and handlers from the companion Rust file. */
    static RustBindings forTemplate(PsiFile template) {
        RustBindings result = new RustBindings();
        result.template = template;
        VirtualFile html = template.getVirtualFile();
        if (html == null || html.getParent() == null || !TemplateScanner.isTemplate(html.getName())) return result;
        String rustName = html.getName().substring(0, html.getName().length() - ".html".length()) + ".rs";
        VirtualFile source = html.getParent().findChild(rustName);
        PsiFile rust = source == null ? null : PsiManager.getInstance(template.getProject()).findFile(source);
        if (rust != null) result.readCompanion(rust, html);
        result.readImports(template);
        result.readTemplateLocals(template);
        return result;
    }

    private void readCompanion(PsiFile rust, VirtualFile html) {
        for (RsStructItem item : PsiTreeUtil.findChildrenOfType(rust, RsStructItem.class)) {
            String name = item.getName();
            if (name == null) continue;
            if (item.getBlockFields() != null) {
                Map<String, Symbol> members = new LinkedHashMap<>();
                for (RsNamedFieldDecl field : item.getBlockFields().getNamedFieldDeclList()) {
                    if (field.getName() == null) continue;
                    String type = field.getTypeReference() == null ? "" : field.getTypeReference().getText();
                    members.put(field.getName(), new Symbol(field.getName(), "Rust field", type, field));
                }
                fields.put(name, members);
            }
            boolean store = TiltRustAttributes.derives(item.getOuterAttrList(), "UiStore");
            if (TiltRustAttributes.has(item.getOuterAttrList(), "html_shared")
                    || TiltRustAttributes.has(item.getOuterAttrList(), "html_use") || store) {
                String alias = Character.toLowerCase(name.charAt(0)) + name.substring(1);
                roots.put(alias, new Symbol(alias, store ? "UI store" : "Shared resource", name, item,
                        store && TiltRustAttributes.mutableStore(item.getOuterAttrList())));
            }
        }
        for (RsEnumItem item : PsiTreeUtil.findChildrenOfType(rust, RsEnumItem.class)) {
            String name = item.getName();
            if (name == null) continue;
            fields.put(name, Map.of());
            boolean store = TiltRustAttributes.derives(item.getOuterAttrList(), "UiStore");
            if (TiltRustAttributes.has(item.getOuterAttrList(), "html_shared")
                    || TiltRustAttributes.has(item.getOuterAttrList(), "html_use") || store) {
                String alias = Character.toLowerCase(name.charAt(0)) + name.substring(1);
                roots.put(alias, new Symbol(alias, store ? "UI store" : "Shared resource", name, item,
                        store && TiltRustAttributes.mutableStore(item.getOuterAttrList())));
            }
        }
        for (RsUseItem item : PsiTreeUtil.findChildrenOfType(rust, RsUseItem.class)) {
            if (!TiltRustAttributes.has(item.getOuterAttrList(), "html_shared")
                    && !TiltRustAttributes.has(item.getOuterAttrList(), "html_use")) continue;
            RsUseSpeck use = item.getUseSpeck();
            if (use == null || use.getPath() == null) continue;
            String path = use.getPath().getText();
            String name = use.getAlias() != null && use.getAlias().getName() != null
                    ? use.getAlias().getName() : path.substring(path.lastIndexOf("::") + 2);
            if (name.isBlank()) continue;
            PsiElement resolved = use.getPath().getReference() == null ? null : use.getPath().getReference().resolve();
            if (resolved instanceof RsStructItem struct && struct.getName() != null)
                fields.putIfAbsent(struct.getName(), fieldsOf(struct));
            String type = resolved instanceof RsStructItem struct && struct.getName() != null
                    ? struct.getName() : path.substring(path.lastIndexOf("::") + 2);
            if (!fields.containsKey(type)) {
                if (catalog == null) catalog = RustSourceCatalog.get(rust.getProject());
                Map<String, Symbol> importedFields = catalog.fieldsAt(path);
                if (importedFields != null) fields.put(type, importedFields);
            }
            String alias = Character.toLowerCase(name.charAt(0)) + name.substring(1);
            roots.putIfAbsent(alias, new Symbol(alias, "Shared resource", type,
                    resolved == null ? item : resolved));
        }
        String component = html.getName().substring(0, html.getName().length() - ".component.html".length());
        for (RsFunction function : PsiTreeUtil.findChildrenOfType(rust, RsFunction.class)) {
            if (function.getName() == null) continue;
            List<String> handler = TiltRustAttributes.stringArguments(function.getOuterAttrList(), "html_fn");
            if (handler.size() == 1) {
                String alias = handler.getFirst();
                roots.put(alias, new Symbol(alias, "Event handler", function.getName() + "()", function));
            }
            List<String> method = TiltRustAttributes.stringArguments(function.getOuterAttrList(), "html_method");
            if (method.size() == 2 && method.getFirst().equals(component)) {
                String path = method.get(1);
                methods.put(path, new Symbol(path.substring(path.lastIndexOf('.') + 1),
                        "Template method", function.getName() + "()", function));
            }
        }
    }

    private static Map<String, Symbol> fieldsOf(RsStructItem item) {
        Map<String, Symbol> result = new LinkedHashMap<>();
        if (item.getBlockFields() == null) return result;
        for (RsNamedFieldDecl field : item.getBlockFields().getNamedFieldDeclList()) {
            if (field.getName() == null) continue;
            String type = field.getTypeReference() == null ? "" : field.getTypeReference().getText();
            result.put(field.getName(), new Symbol(field.getName(), "Rust field", type, field));
        }
        return result;
    }

    private void readImports(PsiFile template) {
        var uses = TemplateScanner.uses(template.getText());
        if (uses.isEmpty()) return;
        catalog = RustSourceCatalog.get(template.getProject());
        for (Map.Entry<String, Map<String, Symbol>> type : catalog.fieldsByType().entrySet())
            fields.putIfAbsent(type.getKey(), type.getValue());
        for (TemplateScanner.Use use : uses) {
            RustSourceCatalog.Type type = catalog.resolve(use.target());
            if (type == null) {
                PsiElement resolved = RustPathCompletion.resolve(template, use.target());
                if (!(resolved instanceof RsStructItem struct) || struct.getName() == null) continue;
                fields.putIfAbsent(struct.getName(), fieldsOf(struct));
                if (use.alias().equals("*")) {
                    for (Symbol field : fieldsOf(struct).values()) roots.putIfAbsent(field.name(), field);
                } else roots.putIfAbsent(use.alias(), new Symbol(use.alias(), "Imported Rust struct",
                        struct.getName(), struct));
                continue;
            }
            if (use.alias().equals("*")) {
                for (Symbol field : type.fields().values()) roots.putIfAbsent(field.name(), field);
            } else {
                roots.putIfAbsent(use.alias(), new Symbol(use.alias(), "Imported " + type.kind(), type.name(), type.target()));
            }
        }
    }

    /** Adds values declared inside the template, including loop elements from Rust collections. */
    private void readTemplateLocals(PsiFile template) {
        for (TemplateScanner.Local local : TemplateScanner.locals(template.getText()))
            addLocal(template, local);
    }

    private void addLocal(PsiFile template, TemplateScanner.Local local) {
        Symbol origin = resolveAt(local.expression(), local.declaration());
        String type = origin == null ? "" : origin.type();
        if (local.element()) type = collectionElementType(type);
        PsiElement target = template.findElementAt(local.declaration());
        if (target != null) locals.add(new ScopedLocal(local,
                new Symbol(local.name(), local.element() ? "Loop value" : "Template local", type, target)));
    }

    private static String collectionElementType(String type) {
        int open = type.indexOf('<');
        int close = type.lastIndexOf('>');
        return open >= 0 && close > open ? type.substring(open + 1, close).trim() : type;
    }

    /** Resolves the target string of an {@code @use} directive to its Rust type. */
    Symbol resolveUseTarget(String target) {
        RustSourceCatalog.Type type = catalog == null ? null : catalog.resolve(target);
        if (type != null) return new Symbol(target, type.kind(), type.name(), type.target());
        var resolved = template == null ? null : RustPathCompletion.resolve(template, target);
        return resolved == null ? null : new Symbol(target, "Rust item", target, resolved);
    }

    /** Returns names available at the start of a template expression. */
    Map<String, Symbol> roots() { return rootsAt(Integer.MAX_VALUE); }

    Map<String, Symbol> rootsAt(int offset) {
        Map<String, Symbol> result = new LinkedHashMap<>(roots);
        result.putIfAbsent("i18n", new Symbol("i18n", "Localization", "", null));
        for (ScopedLocal local : locals) if (local.scope().visibleAt(offset))
            result.put(local.symbol().name(), local.symbol());
        return result;
    }

    /** Returns fields and registered methods available after a dotted path. */
    Map<String, Symbol> members(String path) { return membersAt(path, Integer.MAX_VALUE); }

    Map<String, Symbol> membersAt(String path, int offset) {
        Symbol root = resolveAt(path, offset);
        Map<String, Symbol> result = new LinkedHashMap<>();
        if (root != null) result.putAll(membersOfType(root.type()));
        if (root != null && isStringType(root.type())) {
            for (String name : List.of("equals", "equalsIgnoreCase", "startWith", "endsWith", "contains"))
                result.putIfAbsent(name, new Symbol(name, "Built-in string method", "", null));
        }
        String prefix = path + ".";
        for (Map.Entry<String, Symbol> method : methods.entrySet()) {
            if (method.getKey().startsWith(prefix) && !method.getKey().substring(prefix.length()).contains("."))
                result.put(method.getValue().name(), method.getValue());
        }
        return result;
    }

    /** Resolves a dotted template path to its Rust declaration. */
    Symbol resolve(String path) { return resolveAt(path, Integer.MAX_VALUE); }

    Symbol resolveAt(String path, int offset) {
        if (path.equals("i18n") || path.startsWith("i18n.")
                || path.equals("$event") || path.startsWith("$event."))
            return new Symbol(path, "Built-in value", "", null);
        Symbol method = methods.get(path);
        if (method != null) return method;
        String[] parts = path.split("\\.");
        if (parts.length == 0) return null;
        Symbol current = rootsAt(offset).get(parts[0]);
        for (int i = 1; i < parts.length && current != null; i++) {
            Map<String, Symbol> members = membersOfType(current.type());
            Symbol next = members.get(parts[i]);
            if (next == null && parts[i].equals("length") && collectionOrString(current.type()))
                next = new Symbol("length", "Built-in length", "usize", null);
            if (next == null && isStringType(current.type())
                    && List.of("equals", "equalsIgnoreCase", "startWith", "endsWith", "contains").contains(parts[i]))
                next = new Symbol(parts[i], "Built-in string method", "", null);
            current = next;
        }
        return current;
    }

    boolean canValidateMembers(String path, int offset) {
        Symbol owner = resolveAt(path, offset);
        if (owner == null || owner.target() == null) return false;
        if (collectionOrString(owner.type())) return true;
        if (fields.containsKey(owner.type())) return true;
        Matcher names = TYPE_NAME.matcher(owner.type());
        while (names.find()) if (fields.containsKey(names.group())) return true;
        return false;
    }

    boolean mutableStoreRoot(String path, int offset) {
        String root = path.split("[.\\[]", 2)[0];
        Symbol symbol = rootsAt(offset).get(root);
        return symbol != null && symbol.kind().equals("UI store") && symbol.mutableStore();
    }

    Symbol rootOf(String path, int offset) {
        return rootsAt(offset).get(path.split("[.\\[]", 2)[0]);
    }

    private static boolean isStringType(String type) {
        return type.equals("String") || type.equals("str") || type.equals("&str");
    }

    private static boolean collectionOrString(String type) {
        return isStringType(type) || type.startsWith("Vec<") || type.startsWith("[") || type.startsWith("&[");
    }

    private Map<String, Symbol> membersOfType(String type) {
        if (fields.containsKey(type)) return fields.get(type);
        Matcher names = TYPE_NAME.matcher(type);
        while (names.find()) if (fields.containsKey(names.group())) return fields.get(names.group());
        return Map.of();
    }
}
