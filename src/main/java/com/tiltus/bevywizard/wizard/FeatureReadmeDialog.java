package com.tiltus.bevywizard.wizard;

import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.jcef.JBCefApp;
import com.intellij.ui.jcef.JBCefBrowser;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Displays the crate READMEs associated with a TiltUI feature. */
final class FeatureReadmeDialog extends DialogWrapper {
    private static final Path SOURCE = Path.of("/Users/exepta/RustroverProjects/tiltus/tilt-ui/crates");
    private final TiltFeature feature;
    private final List<JBCefBrowser> browsers = new ArrayList<>();

    FeatureReadmeDialog(TiltFeature feature) {
        super(false);
        this.feature = feature;
        setTitle(feature.title() + " — crate README.md");
        init();
        setSize(1050, 740);
    }

    /** Creates a tab for each crate README related to the feature. */
    @Override protected @Nullable JComponent createCenterPanel() {
        JTabbedPane tabs = new JTabbedPane();
        tabs.setPreferredSize(new Dimension(1000, 660));
        for (String crate : feature.readmeCrates()) {
            try {
                Path local = SOURCE.resolve(crate).resolve("README.md");
                String markdown;
                String base;
                if (Files.isRegularFile(local)) {
                    markdown = Files.readString(local);
                    base = local.getParent().toUri().toString();
                } else {
                    try (InputStream stream = FeatureReadmeDialog.class.getResourceAsStream("/tilt-readmes/" + crate + "/README.md")) {
                        if (stream == null) throw new IOException("README.md is unavailable for " + crate);
                        markdown = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
                    }
                    base = "https://raw.githubusercontent.com/exepta/tilt-ui/main/crates/" + crate + "/";
                }
                String html = MarkdownPreview.htmlFromMarkdown(markdown, base);
                if (JBCefApp.isSupported()) {
                    JBCefBrowser browser = new JBCefBrowser();
                    browsers.add(browser);
                    browser.loadHTML(html, base);
                    tabs.addTab(crate, browser.getComponent());
                } else {
                    JEditorPane pane = new JEditorPane("text/html", html);
                    pane.setEditable(false);
                    tabs.addTab(crate, new JScrollPane(pane));
                }
            } catch (IOException e) {
                tabs.addTab(crate, new JLabel(e.getMessage()));
            }
        }
        return tabs;
    }

    /** Releases all browser tabs when the dialog closes. */
    @Override protected void dispose() {
        for (JBCefBrowser browser : browsers) browser.dispose();
        browsers.clear();
        super.dispose();
    }
}
