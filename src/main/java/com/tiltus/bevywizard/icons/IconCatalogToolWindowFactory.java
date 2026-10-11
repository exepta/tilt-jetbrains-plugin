package com.tiltus.bevywizard.icons;

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import com.intellij.ui.jcef.JBCefApp;
import com.intellij.ui.jcef.JBCefBrowser;
import com.intellij.ui.jcef.JBCefBrowserBase;
import com.intellij.ui.jcef.JBCefJSQuery;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.BorderLayout;
import java.awt.datatransfer.StringSelection;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;

/** Shows TiltUI's bundled SVG icon catalog beside the editor for icon-enabled projects. */
public final class IconCatalogToolWindowFactory implements ToolWindowFactory {
    private static final Pattern TILT_UI_DEPENDENCY = Pattern.compile("(?m)^\\s*(?:tilt-ui\\s*=|\\[dependencies\\.tilt-ui\\])");
    private static final Pattern ICON_FEATURE = Pattern.compile("(?:\\btilt-icons\\b|tilt-ui/tilt-icons)");
    private static final Pattern ICON_CRATE = Pattern.compile("(?m)^\\s*tilt-ui-icons\\s*=");

    /** Registers the stripe button before Cargo is imported; availability is updated after project startup. */
    @Override public boolean isApplicable(@NotNull Project project) {
        return true;
    }

    /** Returns true when the project's Cargo manifest enables TiltUI icons. */
    public static boolean hasIcons(@NotNull Project project) {
        String base = project.getBasePath();
        if (base == null) return false;
        Path cargo = Path.of(base, "Cargo.toml");
        try {
            String manifest = Files.readString(cargo);
            return usesIcons(manifest);
        } catch (IOException ignored) {
            return false;
        }
    }

    static boolean usesIcons(String manifest) {
        String active = manifest.lines().map(line -> line.replaceFirst("#.*$", ""))
                .collect(java.util.stream.Collectors.joining("\n"));
        return ICON_CRATE.matcher(active).find()
                || TILT_UI_DEPENDENCY.matcher(active).find() && ICON_FEATURE.matcher(active).find();
    }

    /** Creates the catalog view and releases its browser when the tool window closes. */
    @Override public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
        JComponent component;
        Runnable cleanup = () -> {};
        if (JBCefApp.isSupported()) {
            JBCefBrowser browser = new JBCefBrowser();
            JBCefJSQuery copy = JBCefJSQuery.create((JBCefBrowserBase) browser);
            copy.addHandler(snippet -> {
                CopyPasteManager.getInstance().setContents(new StringSelection(snippet));
                return new JBCefJSQuery.Response(null);
            });
            browser.loadHTML(catalogHtml(copy.inject("snippet")));
            java.beans.PropertyChangeListener themeListener = event -> {
                if (!"lookAndFeel".equals(event.getPropertyName())) return;
                SwingUtilities.invokeLater(() -> {
                    if (!browser.isDisposed()) browser.getCefBrowser().executeJavaScript(
                            "window.applyIdeTheme(" + ideTheme() + ")", browser.getCefBrowser().getURL(), 0);
                });
            };
            UIManager.addPropertyChangeListener(themeListener);
            component = browser.getComponent();
            cleanup = () -> { UIManager.removePropertyChangeListener(themeListener); copy.dispose(); browser.dispose(); };
        } else {
            component = fallbackPanel();
        }
        Content content = ContentFactory.getInstance().createContent(component, "Catalog", false);
        content.setDisposer(cleanup::run);
        toolWindow.getContentManager().addContent(content);
    }

    /** Reads the snapshot exported from the TiltUI example app. */
    static JsonArray icons() {
        return JsonParser.parseString(resource("/icon-catalog.json")).getAsJsonArray();
    }

    private static String catalogHtml(String copyCall) {
        return resource("/icon-catalog.html")
                .replace("__CATALOG__", icons().toString())
                .replace("__COPY_CALLBACK__", copyCall)
                .replace("__IDE_THEME__", ideTheme());
    }

    /** Provides current IntelliJ look-and-feel colors to the embedded catalog. */
    private static String ideTheme() {
        java.awt.Color background = color("Panel.background", new java.awt.Color(0xf7f8fb));
        java.awt.Color surface = color("TextField.background", background);
        java.awt.Color text = color("Label.foreground", java.awt.Color.BLACK);
        java.awt.Color muted = color("Label.disabledForeground", text);
        java.awt.Color border = color("Component.borderColor", muted);
        boolean dark = background.getRed() * 0.2126 + background.getGreen() * 0.7152
                + background.getBlue() * 0.0722 < 128;
        return "{dark:" + dark + ",background:'" + hex(background) + "',surface:'" + hex(surface)
                + "',text:'" + hex(text) + "',muted:'" + hex(muted) + "',border:'" + hex(border) + "'}";
    }

    private static java.awt.Color color(String key, java.awt.Color fallback) {
        java.awt.Color value = UIManager.getColor(key);
        return value == null ? fallback : value;
    }

    private static String hex(java.awt.Color color) {
        return String.format("#%02x%02x%02x", color.getRed(), color.getGreen(), color.getBlue());
    }

    private static String resource(String name) {
        try (InputStream stream = IconCatalogToolWindowFactory.class.getResourceAsStream(name)) {
            if (stream == null) throw new IllegalStateException("Missing plugin resource " + name);
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException error) {
            throw new IllegalStateException("Could not read " + name, error);
        }
    }

    private static JComponent fallbackPanel() {
        List<String> names = icons().asList().stream()
                .map(icon -> icon.getAsJsonObject().get("name").getAsString()).toList();
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        JTextField search = new JTextField();
        search.putClientProperty("JTextField.placeholderText", "Search icons");
        DefaultListModel<String> model = new DefaultListModel<>();
        names.forEach(model::addElement);
        JList<String> list = new JList<>(model);
        search.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            private void update() {
                model.clear();
                names.stream().filter(name -> name.contains(search.getText().trim().toLowerCase()))
                        .forEach(model::addElement);
            }
            @Override public void insertUpdate(javax.swing.event.DocumentEvent e) { update(); }
            @Override public void removeUpdate(javax.swing.event.DocumentEvent e) { update(); }
            @Override public void changedUpdate(javax.swing.event.DocumentEvent e) { update(); }
        });
        JButton copy = new JButton("Copy <icon> snippet");
        copy.addActionListener(event -> {
            if (list.getSelectedValue() != null) CopyPasteManager.getInstance().setContents(
                    new StringSelection("<icon name=\"" + list.getSelectedValue() + "\" size=\"32\"></icon>"));
        });
        panel.add(search, BorderLayout.NORTH);
        panel.add(new JScrollPane(list), BorderLayout.CENTER);
        panel.add(copy, BorderLayout.SOUTH);
        return panel;
    }
}
