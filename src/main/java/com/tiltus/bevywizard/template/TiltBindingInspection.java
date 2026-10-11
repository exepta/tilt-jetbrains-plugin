package com.tiltus.bevywizard.template;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import org.jetbrains.annotations.NotNull;
import org.rust.lang.core.psi.RsStructItem;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Reports only bindings that can be checked against TiltUI's Rust registrations. */
public final class TiltBindingInspection extends LocalInspectionTool {
    private static final Pattern TARGET = Pattern.compile("\\s*([A-Za-z_][A-Za-z_0-9]*(?:\\.[A-Za-z_][A-Za-z_0-9]*|\\[\\d+])*)");

    @Override public @NotNull String getDisplayName() { return "TiltUI template bindings"; }
    @Override public @NotNull String getShortName() { return "TiltBindingValidation"; }

    @Override public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
        return new PsiElementVisitor() {
            @Override public void visitFile(@NotNull PsiFile file) {
                if (TemplateScanner.isTemplate(file.getName())) inspect(file, holder);
            }
        };
    }

    private static void inspect(PsiFile file, ProblemsHolder holder) {
        TemplateIndex index = TemplateIndex.get(file);
        String source = index.source;
        RustBindings bindings = index.bindings;
        for (TemplateScanner.Token token : index.tokens) {
            if (token.kind() == TemplateScanner.Kind.ACTION) {
                if (token.text().equals("$set") || token.text().equals("$add"))
                    inspectWrite(file, holder, source, bindings, token);
                continue;
            }
            if (token.kind() != TemplateScanner.Kind.IDENTIFIER) continue;
            TemplateScanner.Region region = TemplateScanner.regionAt(source, token.start());
            if (region == null) continue;
            boolean call = nextNonSpace(source, token.end(), region.end()) == '(';
            String path = TemplateIndex.pathAt(source, token.start(), token.end());
            boolean dotted = path.contains(".");
            RustBindings.Symbol resolved = index.symbolAt(token);
            if (dotted && call && resolved == null) {
                String owner = path.substring(0, path.lastIndexOf('.'));
                if (bindings.resolveAt(owner, token.start()) != null && !owner.startsWith("i18n")
                        && !owner.startsWith("$event")) {
                    LocalQuickFix[] fixes = companion(file) == null ? LocalQuickFix.EMPTY_ARRAY
                            : new LocalQuickFix[]{new CreateRegistrationFix(path, false)};
                    problem(holder, file, token.start(), token.end(), "Unknown TiltUI method '" + path + "'.",
                            fixes);
                }
                continue;
            }
            if (region.event() && completeEvent(source, region)
                    && (call || token.start() == region.start() && token.end() == region.end())) {
                if (!dotted && (resolved == null || !resolved.kind().equals("Event handler"))
                        && !token.text().equals("$event")
                        && !token.text().equals("true") && !token.text().equals("false")) {
                    LocalQuickFix[] fixes = companion(file) == null ? LocalQuickFix.EMPTY_ARRAY
                            : new LocalQuickFix[]{new CreateRegistrationFix(token.text(), true)};
                    problem(holder, file, token.start(), token.end(),
                            "Unknown TiltUI handler. Define it with #[html_fn(\"" + token.text()
                                    + "\")] in the matching .component.rs file.",
                            fixes);
                    continue;
                }
            }
            int dot = path.lastIndexOf('.');
            if (dot < 0 || resolved != null) continue;
            String owner = path.substring(0, dot);
            if (owner.equals("i18n") || owner.startsWith("i18n.")
                    || owner.equals("$event") || owner.startsWith("$event.")) continue;
            if (!bindings.canValidateMembers(owner, token.start())) continue;
            Map<String, RustBindings.Symbol> members = bindings.membersAt(owner, token.start());
            String suggestion = nearest(token.text(), members);
            LocalQuickFix[] fixes = suggestion == null ? LocalQuickFix.EMPTY_ARRAY
                    : new LocalQuickFix[]{new ReplaceFieldFix(token.text(), suggestion)};
            problem(holder, file, token.start(), token.end(),
                    "Unknown TiltUI field '" + token.text() + "' on '" + owner + "'.", fixes);
        }
    }

    private static void inspectWrite(PsiFile file, ProblemsHolder holder, String source,
                                     RustBindings bindings, TemplateScanner.Token action) {
        TemplateScanner.Region region = TemplateScanner.regionAt(source, action.start());
        if (region == null || !region.event() || !completeEvent(source, region)) return;
        int open = action.end();
        while (open < region.end() && Character.isWhitespace(source.charAt(open))) open++;
        if (open >= region.end() || source.charAt(open) != '(') return;
        Matcher matcher = TARGET.matcher(source);
        matcher.region(open + 1, region.end());
        if (!matcher.lookingAt()) return;
        int after = matcher.end(1);
        while (after < region.end() && Character.isWhitespace(source.charAt(after))) after++;
        if (after >= region.end() || source.charAt(after) != ',') return;
        String target = matcher.group(1);
        String path = target.replaceAll("\\[\\d+]", "");
        if (bindings.mutableStoreRoot(path, action.start())) return;
        RustBindings.Symbol root = bindings.rootOf(path, action.start());
        LocalQuickFix[] fixes = root != null && root.kind().equals("UI store")
                && root.target() instanceof RsStructItem
                ? new LocalQuickFix[]{new MakeStoreMutableFix(root.name())} : LocalQuickFix.EMPTY_ARRAY;
        problem(holder, file, matcher.start(1), matcher.end(1),
                action.text() + " target '" + target + "' must start with a #[ui_store(mutable)] UiStore.", fixes);
    }

    private static boolean completeEvent(String source, TemplateScanner.Region region) {
        return region.end() < source.length()
                && (source.charAt(region.end()) == '"' || source.charAt(region.end()) == '\'');
    }

    private static char nextNonSpace(String source, int start, int end) {
        while (start < end && Character.isWhitespace(source.charAt(start))) start++;
        return start < end ? source.charAt(start) : 0;
    }

    private static void problem(ProblemsHolder holder, PsiFile file, int start, int end,
                                String message, LocalQuickFix... fixes) {
        holder.registerProblem(file, new TextRange(start, end), message, fixes);
    }

    private static String nearest(String wrong, Map<String, RustBindings.Symbol> members) {
        String match = null;
        int best = 3;
        for (String name : members.keySet()) {
            int distance = editDistance(wrong, name);
            if (distance < best) { best = distance; match = name; }
            else if (distance == best) match = null;
        }
        return match;
    }

    private static int editDistance(String left, String right) {
        int[] previous = new int[right.length() + 1];
        for (int j = 0; j <= right.length(); j++) previous[j] = j;
        for (int i = 1; i <= left.length(); i++) {
            int[] current = new int[right.length() + 1];
            current[0] = i;
            for (int j = 1; j <= right.length(); j++)
                current[j] = Math.min(Math.min(current[j - 1] + 1, previous[j] + 1),
                        previous[j - 1] + (left.charAt(i - 1) == right.charAt(j - 1) ? 0 : 1));
            previous = current;
        }
        return previous[right.length()];
    }

    private static PsiFile companion(PsiFile html) {
        VirtualFile file = html.getVirtualFile();
        if (file == null || file.getParent() == null) return null;
        VirtualFile rust = file.getParent().findChild(file.getName().replaceFirst("\\.html$", ".rs"));
        return rust == null ? null : PsiManager.getInstance(html.getProject()).findFile(rust);
    }

    private static void append(PsiFile file, String text) {
        PsiDocumentManager manager = PsiDocumentManager.getInstance(file.getProject());
        Document document = manager.getDocument(file);
        if (document == null) return;
        document.insertString(document.getTextLength(), (document.getText().endsWith("\n") ? "\n" : "\n\n") + text);
        manager.commitDocument(document);
    }

    private record CreateRegistrationFix(String name, boolean handler) implements LocalQuickFix {
        @Override public @NotNull String getFamilyName() {
            return handler ? "Create TiltUI event handler" : "Create TiltUI method";
        }
        @Override public @NotNull String getName() { return getFamilyName() + " '" + name + "'"; }
        @Override public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
            PsiFile html = descriptor.getPsiElement().getContainingFile();
            PsiFile rust = companion(html);
            if (rust == null) return;
            String component = html.getName().substring(0, html.getName().length() - ".component.html".length());
            String stem = name.substring(name.lastIndexOf('.') + 1).replaceAll("[^A-Za-z_0-9]", "_");
            String function = "tilt_ui_" + stem;
            for (int suffix = 2; rust.getText().matches("(?s).*\\bfn\\s+" + Pattern.quote(function) + "\\s*\\(.*"); suffix++)
                function = "tilt_ui_" + stem + "_" + suffix;
            if (handler) append(rust, "#[tilt_ui::html_fn(\"" + name + "\")]\n"
                    + "fn " + function + "(_: tilt_ui::bevy::prelude::In<tilt_ui::HtmlEvent>) {}\n");
            else append(rust, "#[tilt_ui::html_method(\"" + component + "\", \"" + name + "\")]\n"
                    + "fn " + function + "(_: &tilt_ui::serde_json::Value, _: &[tilt_ui::serde_json::Value])"
                    + " -> Option<tilt_ui::serde_json::Value> { None }\n");
        }
    }

    private record ReplaceFieldFix(String oldName, String newName) implements LocalQuickFix {
        @Override public @NotNull String getFamilyName() { return "Replace with Rust field"; }
        @Override public @NotNull String getName() { return "Replace with '" + newName + "'"; }
        @Override public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
            PsiFile html = descriptor.getPsiElement().getContainingFile();
            Document document = PsiDocumentManager.getInstance(project).getDocument(html);
            TextRange range = descriptor.getTextRangeInElement();
            if (document == null || range.getEndOffset() > document.getTextLength()
                    || !document.getText(range).equals(oldName)) return;
            document.replaceString(range.getStartOffset(), range.getEndOffset(), newName);
            PsiDocumentManager.getInstance(project).commitDocument(document);
        }
    }

    private record MakeStoreMutableFix(String root) implements LocalQuickFix {
        @Override public @NotNull String getFamilyName() { return "Make TiltUI store mutable"; }
        @Override public @NotNull String getName() { return "Make '" + root + "' a mutable UI store"; }
        @Override public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
            PsiFile html = descriptor.getPsiElement().getContainingFile();
            RustBindings.Symbol symbol = RustBindings.forTemplate(html).rootOf(root, descriptor.getStartElement().getTextOffset());
            if (symbol == null || !(symbol.target() instanceof RsStructItem item)
                    || !TiltRustAttributes.derives(item.getOuterAttrList(), "UiStore")
                    || TiltRustAttributes.mutableStore(item.getOuterAttrList())) return;
            PsiFile rust = item.getContainingFile();
            PsiDocumentManager manager = PsiDocumentManager.getInstance(project);
            Document document = manager.getDocument(rust);
            if (document == null) return;
            String derive = TiltRustAttributes.derives(item.getOuterAttrList(), "Deserialize") ? ""
                    : "#[derive(tilt_ui::serde::Deserialize)]\n";
            document.insertString(item.getTextRange().getStartOffset(),
                    "#[ui_store(mutable)]\n" + derive);
            manager.commitDocument(document);
        }
    }
}
