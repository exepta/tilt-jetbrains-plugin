package com.tiltus.bevywizard.wizard;

import com.tiltus.bevywizard.catalog.CatalogClient;
import com.tiltus.bevywizard.catalog.CrateAsset;

import com.google.gson.JsonObject;
import com.intellij.ide.BrowserUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.jcef.JBCefApp;
import com.intellij.ui.jcef.JBCefBrowser;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import javax.swing.text.html.HTMLDocument;
import java.awt.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/** Displays a selected community crate README inside the IDE. */
final class ReadmeDialog extends DialogWrapper {
    private record Document(String html, String base) {}

    private final CrateAsset asset;
    private JBCefBrowser browser;
    private JEditorPane fallback;

    ReadmeDialog(@Nullable Project project, CrateAsset asset) {
        super(project, false);
        this.asset = asset;
        setTitle(asset.title() + " — README");
        init();
        setSize(1050, 740);
    }

    /** Creates the JCEF or Swing README viewer and starts loading content. */
    @Override protected @Nullable JComponent createCenterPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setPreferredSize(new Dimension(1000, 660));
        if (JBCefApp.isSupported()) {
            browser = new JBCefBrowser();
            panel.add(browser.getComponent(), BorderLayout.CENTER);
            browser.loadHTML(MarkdownPreview.wrap("<p>Loading README…</p>", null));
        } else {
            fallback = new JEditorPane("text/html", "<html><body>Loading README…</body></html>");
            fallback.setEditable(false);
            fallback.setBackground(new Color(0x202124));
            fallback.setForeground(new Color(0xdddddd));
            fallback.addHyperlinkListener(event -> {
                if (event.getEventType() == HyperlinkEvent.EventType.ACTIVATED && event.getURL() != null)
                    BrowserUtil.browse(event.getURL());
            });
            panel.add(new JScrollPane(fallback), BorderLayout.CENTER);
        }
        new SwingWorker<Document, Void>() {
            @Override protected Document doInBackground() {
                CrateAsset readmeAsset = asset.version() == null ? CatalogClient.enrich(asset).orElse(asset) : asset;
                try {
                    CatalogClient.ReadmePage page = CatalogClient.readmePage(readmeAsset);
                    if (!org.jsoup.Jsoup.parse(page.html()).text().isBlank()) {
                        String base = CatalogClient.githubRawBase(readmeAsset);
                        if (base == null) base = page.sourceUrl();
                        return new Document(MarkdownPreview.wrap(page.html(), base), base);
                    }
                } catch (Exception ignored) { /* Try the repository README. */ }
                try {
                    String base = CatalogClient.githubRawBase(readmeAsset);
                    return new Document(MarkdownPreview.htmlFromMarkdown(CatalogClient.rawGithubReadme(readmeAsset), base), base);
                } catch (Exception ignored) { /* Try GitHub's README API. */ }
                try {
                    JsonObject json = CatalogClient.githubReadme(readmeAsset);
                    String markdown = new String(Base64.getMimeDecoder().decode(json.get("content").getAsString()), StandardCharsets.UTF_8);
                    String raw = json.get("download_url").getAsString();
                    String base = raw.substring(0, raw.lastIndexOf('/') + 1);
                    return new Document(MarkdownPreview.htmlFromMarkdown(markdown, base), base);
                } catch (Exception ignored) {
                    String source = asset.repository() != null ? asset.repository() : asset.url();
                    String html = "<h2>README.md is unavailable</h2><p>This asset has no readable README at crates.io or GitHub.</p>"
                            + (source == null ? "" : "<p><a href='" + escape(source) + "'>Open the source page</a></p>");
                    return new Document(MarkdownPreview.wrap(html, null), null);
                }
            }
            @Override protected void done() {
                try {
                    Document document = get();
                    if (browser != null) {
                        if (document.base() == null) browser.loadHTML(document.html());
                        else browser.loadHTML(document.html(), document.base());
                    }
                    if (fallback != null) {
                        fallback.setText(document.html());
                        if (document.base() != null && fallback.getDocument() instanceof HTMLDocument html)
                            html.setBase(URI.create(document.base()).toURL());
                        fallback.setCaretPosition(0);
                    }
                } catch (Exception e) {
                    String html = MarkdownPreview.wrap("<p>Could not load README.md: " + escape(e.getMessage()) + "</p>", null);
                    if (browser != null) browser.loadHTML(html);
                    if (fallback != null) fallback.setText(html);
                }
            }
        }.execute();
        return panel;
    }

    /** Escapes diagnostic text before displaying it as HTML. */
    private static String escape(String value) {
        return String.valueOf(value).replace("&", "&amp;").replace("'", "&#39;")
                .replace("<", "&lt;").replace(">", "&gt;");
    }

    /** Releases the browser when the dialog closes. */
    @Override protected void dispose() {
        if (browser != null) { browser.dispose(); browser = null; }
        super.dispose();
    }
}
