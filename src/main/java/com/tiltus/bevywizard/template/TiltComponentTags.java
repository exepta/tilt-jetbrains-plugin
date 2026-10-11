package com.tiltus.bevywizard.template;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.impl.source.xml.XmlElementDescriptorProvider;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlTag;
import com.intellij.xml.XmlAttributeDescriptor;
import com.intellij.xml.XmlElementDescriptor;
import com.intellij.xml.XmlElementsGroup;
import com.intellij.xml.XmlNSDescriptor;

import java.util.LinkedHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.Map;
import java.util.Set;

/** Makes discovered TiltUI component names first-class HTML tags in component templates. */
public final class TiltComponentTags implements XmlElementDescriptorProvider {
    private static final String SUFFIX = ".component.html";
    private static final Pattern TAG_NAME = Pattern.compile("</?([A-Za-z][A-Za-z0-9-]*)\\b");
    static final Set<String> BUILTIN_TAGS = Set.of("avatar", "badge", "choice-box", "colorpicker",
            "color-picker", "context-menu", "date-picker", "dialog", "divider", "field-set",
            "headline", "hyperlink", "image", "list-box", "paragraph", "icon", "progressbar",
            "progress-bar", "spinner", "toast", "radio-button", "router-outlet", "scroll",
            "scrollbar", "slider", "switch", "switch-button", "table-cell", "toggle",
            "toggle-button", "tooltip", "form", "button", "input", "textarea", "checkbox", "option",
            "body", "div", "fieldset", "h1", "h2", "h3", "h4", "h5", "h6", "a", "img", "label",
            "p", "radio", "select", "table", "td", "th");

    /** Finds component tags from their source templates in this project. */
    static Map<String, PsiFile> discover(Project project) {
        Map<String, PsiFile> result = new LinkedHashMap<>();
        PsiManager manager = PsiManager.getInstance(project);
        for (VirtualFile source : FilenameIndex.getAllFilesByExt(project, "html", GlobalSearchScope.projectScope(project))) {
            if (!source.getName().endsWith(SUFFIX)) continue;
            PsiFile file = manager.findFile(source);
            if (file != null && TiltUiSourceRoot.contains(file))
                result.putIfAbsent(source.getName().substring(0, source.getName().length() - SUFFIX.length()), file);
        }
        return result;
    }

    /** Returns whether an HTML file belongs to a TiltUI source tree. */
    static boolean isUiHtml(PsiFile file) {
        return file.getName().endsWith(".html") && file.getVirtualFile() != null
                && (TiltUiSourceRoot.contains(file) || file.getName().endsWith(".component.html"));
    }

    /** Finds a known TiltUI tag name directly under the hover offset. */
    static String nameAt(PsiFile file, int offset) {
        if (!isUiHtml(file)) return null;
        Matcher names = TAG_NAME.matcher(file.getText());
        while (names.find()) {
            if (names.start(1) <= offset && offset < names.end(1)) {
                String name = names.group(1);
                return BUILTIN_TAGS.contains(name) || discover(file.getProject()).containsKey(name) ? name : null;
            }
        }
        return null;
    }

    /** Checks whether this tag is declared by TiltUI or a project component. */
    static boolean isKnownTag(PsiFile file, String name) {
        return isUiHtml(file) && (BUILTIN_TAGS.contains(name) || discover(file.getProject()).containsKey(name));
    }

    /** Describes built-in tags and user components, using a leading HTML comment when provided. */
    static String description(PsiFile file, String name) {
        if (BUILTIN_TAGS.contains(name)) return TiltWidgetCatalog.description(name);
        PsiFile component = discover(file.getProject()).get(name);
        if (component == null) return null;
        String source = component.getText().stripLeading();
        if (source.startsWith("<!--")) {
            int end = source.indexOf("-->");
            if (end > 4) {
                String comment = source.substring(4, end).strip();
                if (!comment.isEmpty()) return comment;
            }
        }
        return "Reusable TiltUI component defined in " + component.getName() + ".";
    }

    /** Lists known attributes for a TiltUI tag, including common event bindings. */
    static Set<String> attributeNames(String tag) {
        return TiltWidgetCatalog.staticNames(com.tiltus.bevywizard.project.TiltUiCompatibility.VERSION, tag);
    }

    static AttributeHit attributeAt(PsiFile file, int offset) {
        if (!isUiHtml(file) || offset < 0 || offset >= file.getTextLength()) return null;
        XmlAttribute attribute = PsiTreeUtil.getParentOfType(file.findElementAt(offset), XmlAttribute.class);
        if (attribute == null || attribute.getNameElement() == null
                || !attribute.getNameElement().getTextRange().containsOffset(offset)) return null;
        XmlTag tag = attribute.getParent();
        if (tag == null || !BUILTIN_TAGS.contains(tag.getName())) return null;
        return new AttributeHit(tag.getName(), attribute.getName());
    }

    record AttributeHit(String tag, String name) {}

    @Override
    public XmlElementDescriptor getDescriptor(XmlTag tag) {
        PsiFile file = tag.getContainingFile();
        if (file == null || !isUiHtml(file)) return null;
        boolean selfClosing = tag.isEmpty() || tag.getText().stripTrailing().endsWith("/>");
        String release = TiltUiRelease.forFile(file);
        if (BUILTIN_TAGS.contains(tag.getName()) && TiltWidgetCatalog.supports(release))
            return new Descriptor(tag.getName(), tag, selfClosing, release, true);
        PsiFile declaration = discover(file.getProject()).get(tag.getName());
        return declaration == null ? null : new Descriptor(tag.getName(), declaration, selfClosing, release, false);
    }

    private record Descriptor(String name, PsiElement declaration, boolean selfClosing,
                              String release, boolean builtin) implements XmlElementDescriptor {
        @Override public String getQualifiedName() { return name; }
        @Override public String getDefaultName() { return name; }
        @Override public XmlElementDescriptor[] getElementsDescriptors(XmlTag context) { return EMPTY_ARRAY; }
        @Override public XmlElementDescriptor getElementDescriptor(XmlTag child, XmlTag context) { return null; }
        @Override public XmlAttributeDescriptor[] getAttributesDescriptors(XmlTag context) {
            if (!builtin) return new XmlAttributeDescriptor[0];
            java.util.LinkedHashSet<String> names = new java.util.LinkedHashSet<>(TiltWidgetCatalog.staticNames(release, name));
            TiltWidgetCatalog.bindingNames(release, name).forEach(binding -> names.add("[" + binding + "]"));
            return names.stream().map(attribute -> new Attribute(attribute, context, name)).toArray(XmlAttributeDescriptor[]::new);
        }
        @Override public XmlAttributeDescriptor getAttributeDescriptor(String attributeName, XmlTag context) {
            return attributeName.isBlank() || builtin && !TiltWidgetCatalog.accepts(release, name, attributeName)
                    ? null : new Attribute(attributeName, context, name);
        }
        @Override public XmlAttributeDescriptor getAttributeDescriptor(XmlAttribute attribute) {
            return getAttributeDescriptor(attribute.getName(), attribute.getParent());
        }
        @Override public XmlNSDescriptor getNSDescriptor() { return null; }
        @Override public XmlElementsGroup getTopGroup() { return null; }
        @Override public int getContentType() { return selfClosing ? CONTENT_TYPE_EMPTY : CONTENT_TYPE_ANY; }
        @Override public String getDefaultValue() { return null; }
        @Override public PsiElement getDeclaration() { return declaration; }
        @Override public String getName(PsiElement context) { return name; }
        @Override public String getName() { return name; }
        @Override public void init(PsiElement element) {}
    }

    /** Describes both common and tag-specific TiltUI attributes to the HTML editor. */
    private record Attribute(String name, PsiElement declaration, String tag) implements XmlAttributeDescriptor {
        @Override public boolean isRequired() { return false; }
        @Override public boolean isFixed() { return false; }
        @Override public boolean hasIdType() { return name.equals("id"); }
        @Override public boolean hasIdRefType() { return false; }
        @Override public String getDefaultValue() { return null; }
        @Override public boolean isEnumerated() { return !TiltWidgetCatalog.values(tag, name).isEmpty(); }
        @Override public String[] getEnumeratedValues() { return TiltWidgetCatalog.values(tag, name).toArray(String[]::new); }
        @Override public String validateValue(com.intellij.psi.xml.XmlElement element, String value) {
            java.util.List<String> allowed = TiltWidgetCatalog.values(tag, name);
            return allowed.isEmpty() || value.isBlank() || allowed.contains(value) ? null
                    : "Unsupported " + tag + " " + name + " value for this TiltUI release: " + value;
        }
        @Override public PsiElement getDeclaration() { return declaration; }
        @Override public String getName(PsiElement context) { return name; }
        @Override public String getName() { return name; }
        @Override public void init(PsiElement element) {}
    }
}
