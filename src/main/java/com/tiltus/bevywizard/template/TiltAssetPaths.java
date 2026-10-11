package com.tiltus.bevywizard.template;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Finds file-backed UI paths in markup, stylesheets, and TiltUI Rust setup. */
final class TiltAssetPaths {
    private static final Pattern TAG = Pattern.compile("(?is)<(link|img|image|avatar|input|checkbox)\\b[^>]*>");
    private static final Pattern ATTRIBUTE = Pattern.compile("([\\w-]+)\\s*=\\s*(['\"])(.*?)\\2", Pattern.DOTALL);
    private static final Pattern CSS_URL = Pattern.compile("url\\(\\s*(['\"]?)([^'\")]+)\\1\\s*\\)", Pattern.CASE_INSENSITIVE);
    private static final Pattern RUST_DIRECTORY = Pattern.compile("\\.with_(?:themes_path|language_path)\\s*\\(\\s*\"([^\"]+)\"");
    private static final Pattern RUST_CATALOG = Pattern.compile("\\.with_catalog\\s*\\(\\s*\"[^\"]+\"\\s*,\\s*\"([^\"]+)\"");
    private static final Pattern RUST_FILE = Pattern.compile("(?:include_str!|include_bytes!)\\s*\\(\\s*\"([^\"]+)\"");
    private static final Pattern RUST_MODULE = Pattern.compile("#\\[path\\s*=\\s*\"([^\"]+)\"");

    enum Base { UI_ROOT, SOURCE_DIRECTORY, BOTH }
    enum Kind { STYLESHEET, IMAGE, FLUENT, RUST, ANY, DIRECTORY }
    record Link(int start, int end, String path, boolean required, boolean directory, Base base, Kind kind) {}

    private TiltAssetPaths() {}

    static List<Link> find(PsiFile file) {
        return CachedValuesManager.getCachedValue(file,
                () -> CachedValueProvider.Result.create(scan(file), file));
    }

    private static List<Link> scan(PsiFile file) {
        if (file.getVirtualFile() == null) return List.of();
        String name = file.getName();
        if (name.endsWith(".html") && TiltComponentTags.isUiHtml(file)) return html(file.getText());
        if (name.endsWith(".css") && (TiltUiSourceRoot.contains(file) || name.endsWith(".component.css")))
            return css(file.getText());
        if (name.endsWith(".rs") && TiltUiSourceRoot.find(file) != null) return rust(file.getText());
        return List.of();
    }

    private static List<Link> html(String source) {
        List<Link> found = new ArrayList<>();
        Matcher tags = TAG.matcher(source);
        while (tags.find()) {
            String tag = tags.group(1).toLowerCase(Locale.ROOT);
            String content = tags.group();
            boolean stylesheet = tag.equals("link") && Pattern.compile("(?i)\\brel\\s*=\\s*['\"]stylesheet['\"]")
                    .matcher(content).find();
            Matcher attrs = ATTRIBUTE.matcher(content);
            while (attrs.find()) {
                String attribute = attrs.group(1).toLowerCase(Locale.ROOT);
                boolean relevant = stylesheet && attribute.equals("href")
                        || List.of("img", "image", "avatar").contains(tag) && attribute.equals("src")
                        || tag.equals("input") && attribute.equals("icon")
                        || tag.equals("checkbox") && List.of("checked-icon", "unchecked-icon").contains(attribute);
                if (!relevant) continue;
                String path = attrs.group(3).trim();
                if (external(path)) continue;
                boolean rootPath = path.startsWith("tilt-ui://");
                found.add(new Link(tags.start() + attrs.start(3), tags.start() + attrs.end(3), path,
                        true, false, stylesheet || rootPath ? Base.UI_ROOT : Base.BOTH,
                        stylesheet ? Kind.STYLESHEET : Kind.IMAGE));
            }
        }
        return found;
    }

    private static List<Link> css(String source) {
        List<Link> found = new ArrayList<>();
        Matcher urls = CSS_URL.matcher(source);
        while (urls.find()) {
            String path = urls.group(2).trim();
            if (external(path)) continue;
            boolean rootPath = path.startsWith("tilt-ui://");
            found.add(new Link(urls.start(2), urls.end(2), path, true, false,
                    rootPath ? Base.UI_ROOT : Base.BOTH, Kind.IMAGE));
        }
        return found;
    }

    private static List<Link> rust(String source) {
        List<Link> found = new ArrayList<>();
        add(found, source, RUST_DIRECTORY, true, true, Base.UI_ROOT, Kind.DIRECTORY);
        add(found, source, RUST_CATALOG, true, false, Base.UI_ROOT, Kind.FLUENT);
        add(found, source, RUST_FILE, false, false, Base.SOURCE_DIRECTORY, Kind.ANY);
        add(found, source, RUST_MODULE, true, false, Base.SOURCE_DIRECTORY, Kind.RUST);
        return found;
    }

    private static void add(List<Link> found, String source, Pattern pattern, boolean required,
                            boolean directory, Base base, Kind kind) {
        Matcher paths = pattern.matcher(source);
        while (paths.find()) {
            String path = paths.group(1).trim();
            if (!external(path)) found.add(new Link(paths.start(1), paths.end(1), path, required, directory, base, kind));
        }
    }

    static VirtualFile resolve(PsiFile file, Link link) {
        VirtualFile root = TiltUiSourceRoot.find(file), source = file.getVirtualFile();
        if (root == null || source == null) return null;
        String path = link.path().startsWith("tilt-ui://")
                ? link.path().substring("tilt-ui://".length()) : link.path();
        VirtualFile resolved = switch (link.base()) {
            case UI_ROOT -> descend(root, path);
            case SOURCE_DIRECTORY -> descend(source.getParent(), path);
            case BOTH -> {
                VirtualFile local = descend(source.getParent(), path);
                yield local != null ? local : descend(root, path);
            }
        };
        if (resolved == null || link.directory() != resolved.isDirectory()
                || link.base() != Base.SOURCE_DIRECTORY && !within(root, resolved)) return null;
        return resolved;
    }

    private static boolean within(VirtualFile root, VirtualFile target) {
        return target.equals(root) || target.getPath().startsWith(root.getPath() + "/");
    }

    static List<String> variants(PsiFile file, Link link) {
        VirtualFile root = TiltUiSourceRoot.find(file);
        if (root == null) return List.of();
        List<String> found = new ArrayList<>();
        collect(root, "", link, found);
        return found;
    }

    private static void collect(VirtualFile directory, String path, Link link, List<String> found) {
        if (found.size() >= 600) return;
        for (VirtualFile child : directory.getChildren()) {
            String candidate = path + child.getName();
            if (child.isDirectory()) {
                if (link.directory()) found.add(link.path().startsWith("tilt-ui://") ? "tilt-ui://" + candidate : candidate);
                collect(child, candidate + "/", link, found);
            } else if (!link.directory() && eligible(child.getName(), link))
                found.add(link.path().startsWith("tilt-ui://") ? "tilt-ui://" + candidate : candidate);
        }
    }

    private static boolean eligible(String name, Link link) {
        String lower = name.toLowerCase(Locale.ROOT);
        return switch (link.kind()) {
            case STYLESHEET -> lower.endsWith(".css");
            case FLUENT -> lower.endsWith(".ftl");
            case RUST -> lower.endsWith(".rs");
            case IMAGE -> lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg")
                    || lower.endsWith(".svg") || lower.endsWith(".webp") || lower.endsWith(".gif");
            case ANY -> true;
            case DIRECTORY -> false;
        };
    }

    private static VirtualFile descend(VirtualFile base, String path) {
        if (base == null || path.isBlank() || path.startsWith("/")) return null;
        VirtualFile current = base;
        for (String piece : path.replace('\\', '/').split("/")) {
            if (piece.isBlank() || piece.equals(".")) continue;
            if (piece.equals("..")) current = current.getParent();
            else current = current.findChild(piece);
            if (current == null) return null;
        }
        return current;
    }

    private static boolean external(String path) {
        return path.isBlank() || path.startsWith("#") || path.startsWith("/")
                || path.startsWith("data:") || path.startsWith("tilt-icon:")
                || path.contains("://") && !path.startsWith("tilt-ui://");
    }
}
