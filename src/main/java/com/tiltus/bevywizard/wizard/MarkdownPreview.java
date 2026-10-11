package com.tiltus.bevywizard.wizard;

import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;

import java.util.List;

/** Renders crate Markdown and HTML inside an IDE documentation dialog. */
public final class MarkdownPreview {
    private static final String CSS = "body{max-width:900px;margin:28px auto;padding:0 28px;color:#ddd;background:#202124;font:15px/1.55 system-ui,sans-serif} a{color:#79b8ff} img{max-width:100%} pre{overflow:auto;background:#303136;padding:14px;border-radius:6px} code{font-family:monospace} table{border-collapse:collapse;display:block;overflow:auto} td,th{border:1px solid #666;padding:6px} blockquote{border-left:3px solid #777;padding-left:12px;color:#aaa}";
    private static final Parser PARSER = Parser.builder().extensions(List.of(TablesExtension.create(), StrikethroughExtension.create())).build();
    private static final HtmlRenderer RENDERER = HtmlRenderer.builder().extensions(List.of(TablesExtension.create(), StrikethroughExtension.create())).build();
    private MarkdownPreview() {}

    /** Converts complete GitHub-flavored Markdown to an HTML preview. */
    public static String htmlFromMarkdown(String markdown, String base) {
        return wrap(RENDERER.render(PARSER.parse(markdown)), base);
    }

    /** Wraps README HTML with styling and a base URL. */
    static String wrap(String body, String base) {
        String baseTag = base == null ? "" : "<base href='" + base.replace("&", "&amp;").replace("'", "&#39;").replace("<", "&lt;") + "'>";
        return "<!doctype html><html><head><meta charset='utf-8'>" + baseTag + "<style>" + CSS + "</style></head><body>" + body + "</body></html>";
    }
}
