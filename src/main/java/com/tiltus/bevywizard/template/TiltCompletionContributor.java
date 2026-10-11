package com.tiltus.bevywizard.template;

import com.intellij.codeInsight.completion.CompletionContributor;
import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionProvider;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.icons.AllIcons;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.PsiFile;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Completes TiltUI directives, inline actions, Rust bindings, and their fields. */
public final class TiltCompletionContributor extends CompletionContributor {
    private static final Pattern ATTRIBUTE_VALUE = Pattern.compile(".*\\s+([\\[\\]A-Za-z-]+)\\s*=\\s*(['\"])([^'\"]*)", Pattern.DOTALL);
    public TiltCompletionContributor() {
        extend(CompletionType.BASIC, PlatformPatterns.psiElement(), new CompletionProvider<>() {
            @Override
            protected void addCompletions(@NotNull CompletionParameters parameters,
                                          @NotNull ProcessingContext context,
                                          @NotNull CompletionResultSet result) {
                PsiFile file = parameters.getOriginalFile();
                if (!TemplateScanner.isTemplate(file.getName()) && !TiltComponentTags.isUiHtml(file)) return;
                String source = file.getText();
                int offset = Math.min(parameters.getOffset(), source.length());
                if (TemplateScanner.ignoredMarkupAt(source, Math.max(0, offset - 1))) return;
                int lineStart = source.lastIndexOf('\n', Math.max(0, offset - 1)) + 1;
                String beforeCaret = source.substring(lineStart, offset);
                if (beforeCaret.matches(".*<[/]?[A-Za-z][A-Za-z0-9-]*")
                        && TemplateScanner.tagNameAt(source, offset)) {
                    int tagStart = beforeCaret.lastIndexOf('<') + 1;
                    if (tagStart < beforeCaret.length() && beforeCaret.charAt(tagStart) == '/') tagStart++;
                    CompletionResultSet tags = result.withPrefixMatcher(beforeCaret.substring(tagStart));
                    for (Map.Entry<String, PsiFile> tag : TiltComponentTags.discover(file.getProject()).entrySet())
                        tags.addElement(LookupElementBuilder.create(tag.getKey())
                                .withIcon(AllIcons.Nodes.Tag)
                                .withTypeText("TiltUI component")
                                .withTailText("  " + tag.getValue().getVirtualFile().getPath(), true));
                    if (TiltWidgetCatalog.supports(TiltUiRelease.forFile(file)))
                        for (String tag : TiltComponentTags.BUILTIN_TAGS)
                            tags.addElement(LookupElementBuilder.create(tag)
                                    .withIcon(AllIcons.Nodes.Tag).withTypeText("TiltUI element"));
                    return;
                }
                int tagStart = beforeCaret.lastIndexOf('<');
                if (tagStart >= 0 && tagStart > beforeCaret.lastIndexOf('>')) {
                    String insideTag = beforeCaret.substring(tagStart + 1);
                    int firstSpace = insideTag.indexOf(' ');
                    if (firstSpace > 0 && insideTag.substring(0, firstSpace).matches("[A-Za-z][A-Za-z0-9-]*")) {
                        String tagName = insideTag.substring(0, firstSpace);
                        boolean builtin = TiltComponentTags.BUILTIN_TAGS.contains(tagName)
                                && TiltWidgetCatalog.supports(TiltUiRelease.forFile(file));
                        if (builtin || TiltComponentTags.discover(file.getProject()).containsKey(tagName)) {
                            String attributesText = insideTag.substring(firstSpace);
                            boolean inQuote = (attributesText.chars().filter(ch -> ch == '"').count() & 1) != 0
                                    || (attributesText.chars().filter(ch -> ch == '\'').count() & 1) != 0;
                            if (inQuote) {
                                Matcher value = ATTRIBUTE_VALUE.matcher(insideTag);
                                if (builtin && value.matches()
                                        && !TiltWidgetCatalog.values(tagName, value.group(1)).isEmpty()) {
                                    CompletionResultSet values = result.withPrefixMatcher(value.group(3));
                                    for (String choice : TiltWidgetCatalog.values(tagName, value.group(1)))
                                        values.addElement(LookupElementBuilder.create(choice)
                                                .withTypeText("TiltUI " + tagName + " value"));
                                    return;
                                }
                            } else if (insideTag.matches("(?s).*\\s+\\[?[A-Za-z-]*")) {
                                int attributeStart = offset;
                                while (attributeStart > 0 && isAttributePart(source.charAt(attributeStart - 1))) attributeStart--;
                                String prefix = source.substring(attributeStart, offset);
                                CompletionResultSet attributes = result.withPrefixMatcher(prefix);
                                for (String attribute : TiltWidgetCatalog.staticNames(TiltUiRelease.forFile(file), tagName))
                                    attributes.addElement(LookupElementBuilder.create(attribute)
                                            .withIcon(AllIcons.Nodes.Field).withTypeText("TiltUI attribute"));
                                if (builtin)
                                    for (String binding : TiltWidgetCatalog.bindingNames(TiltUiRelease.forFile(file), tagName))
                                        attributes.addElement(LookupElementBuilder.create("[" + binding + "]")
                                                .withIcon(AllIcons.Nodes.Field).withTypeText("TiltUI property binding"));
                                return;
                            }
                        }
                    }
                }
                if (!TemplateScanner.isTemplate(file.getName())) return;
                String usePrefix = TemplateScanner.openUsePrefix(source, offset);
                if (usePrefix != null) {
                    CompletionResultSet useResult = result.withPrefixMatcher(usePrefix);
                    for (RustSourceCatalog.Type type : RustSourceCatalog.get(file.getProject()).types()) {
                        useResult.addElement(LookupElementBuilder.create(type.path())
                                .withIcon(AllIcons.Nodes.Class)
                                .withTypeText(type.kind())
                                .withTailText("  " + type.target().getContainingFile().getName(), true));
                    }
                    for (RustPathCompletion.Candidate candidate : RustPathCompletion.complete(file, usePrefix)) {
                        useResult.addElement(LookupElementBuilder.create(candidate.path())
                                .withIcon(candidate.kind().equals("module") ? AllIcons.Nodes.Module
                                        : candidate.kind().equals("function") ? AllIcons.Nodes.Method : AllIcons.Nodes.Class)
                                .withTypeText("Rust " + candidate.kind())
                                .withTailText("  " + candidate.target().getContainingFile().getName(), true));
                    }
                    return;
                }
                int wordStart = offset;
                while (wordStart > 0 && isIdentifierPart(source.charAt(wordStart - 1))) wordStart--;
                char marker = wordStart > 0 ? source.charAt(wordStart - 1) : 0;
                CompletionResultSet tiltResult = result.withPrefixMatcher(source.substring(wordStart, offset));

                if (marker == '@') {
                    if (TemplateScanner.directivePosition(source, wordStart - 1))
                        addBuiltins(tiltResult, TiltSyntax.DIRECTIVES, "TiltUI keyword", true);
                    return;
                }
                TemplateScanner.Region region = TemplateScanner.regionAt(source, offset);
                if (marker == '$' && region != null && region.event()) {
                    addBuiltins(tiltResult, TiltSyntax.ACTIONS, "TiltUI action", false);
                    tiltResult.addElement(LookupElementBuilder.create("event")
                            .withPresentableText("$event").withIcon(AllIcons.Nodes.Field)
                            .withTypeText("Event value"));
                    return;
                }
                if (region == null) return;

                RustBindings bindings = TemplateIndex.get(file).bindings;
                if (marker == '.') {
                    int ownerEnd = wordStart - 1;
                    int ownerStart = ownerEnd;
                    while (ownerStart > 0) {
                        char ch = source.charAt(ownerStart - 1);
                        if (isIdentifierPart(ch) || ch == '.') ownerStart--;
                        else break;
                    }
                    String owner = source.substring(ownerStart, ownerEnd);
                    addSymbols(tiltResult, bindings.membersAt(owner, offset), region.event());
                } else {
                    addSymbols(tiltResult, bindings.rootsAt(offset), region.event());
                }
            }
        });
    }

    private static void addBuiltins(CompletionResultSet result, Map<String, TiltSyntax.Entry> entries,
                                    String type, boolean directive) {
        entries.values().stream().sorted((a, b) -> a.name().compareTo(b.name())).forEach(entry ->
                result.addElement(LookupElementBuilder.create(entry.name().substring(1))
                        .withPresentableText(entry.name())
                        .withIcon(AllIcons.Nodes.Method)
                        .withTailText("  " + entry.signature(), true)
                        .withTypeText(type)));
    }

    private static void addSymbols(CompletionResultSet result, Map<String, RustBindings.Symbol> symbols,
                                   boolean event) {
        for (RustBindings.Symbol symbol : symbols.values()) {
            if (symbol.kind().equals("Event handler") && !event) continue;
            result.addElement(LookupElementBuilder.create(symbol.name())
                    .withIcon(symbol.kind().contains("handler") || symbol.kind().contains("method")
                            ? AllIcons.Nodes.Method : AllIcons.Nodes.Field)
                    .withTypeText(symbol.kind() + (symbol.type().isBlank() ? "" : " · " + symbol.type())));
        }
    }

    private static boolean isIdentifierPart(char ch) {
        return Character.isLetterOrDigit(ch) || ch == '_';
    }

    private static boolean isAttributePart(char ch) {
        return isIdentifierPart(ch) || ch == '-' || ch == '[';
    }
}
