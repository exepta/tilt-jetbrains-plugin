package com.tiltus.bevywizard.template;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Scans TiltUI syntax while preserving HTML, comment, string, and block boundaries. */
final class TemplateScanner {
    enum Kind { DIRECTIVE, ACTION, IDENTIFIER, USE_TARGET }
    record Token(int start, int end, String text, Kind kind) {}
    record Use(int start, int end, String target, String alias) {}
    record Region(int start, int end, boolean event) {
        boolean contains(int offset) { return start <= offset && offset <= end; }
    }
    record Local(String name, String expression, int declaration, int visibleFrom, int visibleUntil,
                 boolean element) {
        boolean visibleAt(int offset) { return visibleFrom <= offset && offset < visibleUntil; }
    }
    private record Span(int start, int end) {
        boolean contains(int offset) { return start <= offset && offset < end; }
    }
    private record Block(int start, int end) {}
    private record PendingLocal(String name, String expression, int declaration, int visibleFrom,
                                int blockStart, int blockEnd, boolean element) {}
    private record Parsed(List<Token> tokens, List<Use> uses, List<Region> regions,
                          List<Local> locals, List<Span> blocked, List<Span> ignoredMarkup,
                          List<Span> tagNames) {}
    private static final Set<String> DIRECTIVES = Set.of("if", "else", "for", "match", "let", "use");
    private static final Set<String> ACTIONS = Set.of("set", "add", "sub", "min", "mul", "div", "clamp", "toggle", "append");

    private TemplateScanner() {}
    static boolean isTemplate(String name) { return name.endsWith(".component.html"); }
    static List<Token> tokens(String source) { return parse(source).tokens(); }
    static List<Use> uses(String source) { return parse(source).uses(); }
    static List<Region> regions(String source) { return parse(source).regions(); }
    static List<Local> locals(String source) { return parse(source).locals(); }

    static Token tokenAt(String source, int offset) {
        for (Token token : tokens(source)) if (token.start() <= offset && offset < token.end()) return token;
        return null;
    }

    static Region regionAt(String source, int offset) {
        Region best = null;
        for (Region region : regions(source)) {
            if (region.contains(offset) && (best == null || region.end() - region.start() < best.end() - best.start()))
                best = region;
        }
        return best;
    }

    /** Allows @ keyword completion only in template text, including nested control blocks. */
    static boolean directivePosition(String source, int at) {
        if (at < 0 || at >= source.length() || source.charAt(at) != '@') return false;
        if (at > 0 && (wordPart(source.charAt(at - 1)) || source.charAt(at - 1) == '@')) return false;
        Parsed parsed = parse(source);
        if (parsed.blocked().stream().anyMatch(span -> span.contains(at))) return false;
        return parsed.regions().stream().noneMatch(region -> region.start() <= at && at < region.end());
    }

    static boolean ignoredMarkupAt(String source, int offset) {
        return parse(source).ignoredMarkup().stream().anyMatch(span -> span.contains(offset));
    }

    static boolean tagNameAt(String source, int offset) {
        return parse(source).tagNames().stream().anyMatch(span ->
                span.start() <= offset && offset <= span.end());
    }

    /** Returns the unfinished @use string at the caret, when that directive is in template text. */
    static String openUsePrefix(String source, int offset) {
        int end = Math.min(offset, source.length());
        int lineStart = source.lastIndexOf('\n', Math.max(0, end - 1)) + 1;
        int at = lineStart;
        while (at < end && Character.isWhitespace(source.charAt(at))) at++;
        if (at + 4 > end || !source.startsWith("@use", at) || !directivePosition(source, at)) return null;
        int quote = at + 4;
        while (quote < end && Character.isWhitespace(source.charAt(quote))) quote++;
        if (quote >= end || source.charAt(quote) != '"') return null;
        for (int i = quote + 1; i < end; i++) if (source.charAt(i) == '"') return null;
        return source.substring(quote + 1, end);
    }

    private static Parsed parse(String source) { return new Scanner(source).scan(); }

    private static final class Scanner {
        private final String source;
        private final int length;
        private final List<Token> tokens = new ArrayList<>();
        private final List<Use> uses = new ArrayList<>();
        private final List<Region> regions = new ArrayList<>();
        private final List<PendingLocal> pending = new ArrayList<>();
        private final List<Block> blocks = new ArrayList<>();
        private final List<Span> blocked = new ArrayList<>();
        private final List<Span> ignoredMarkup = new ArrayList<>();
        private final List<Span> tagNames = new ArrayList<>();

        Scanner(String source) { this.source = source; length = source.length(); }

        Parsed scan() {
            for (int i = 0; i < length;) {
                if (source.startsWith("<!--", i)) {
                    int close = source.indexOf("-->", i + 4);
                    int end = close < 0 ? length : close + 3;
                    blocked.add(new Span(i, end)); ignoredMarkup.add(new Span(i, end)); i = end;
                } else if (source.startsWith("{{", i)) i = interpolation(i, length);
                else if (source.charAt(i) == '<' && i + 1 < length
                        && (Character.isLetter(source.charAt(i + 1)) || "/!?".indexOf(source.charAt(i + 1)) >= 0))
                    i = tag(i);
                else if (source.charAt(i) == '@' && (i == 0 || !wordPart(source.charAt(i - 1))))
                    i = directive(i);
                else if (source.charAt(i) == '{') {
                    int close = balancedEnd(i, '{', '}');
                    if (close >= 0) blocks.add(new Block(i, close));
                    i++;
                }
                else i++;
            }
            List<Local> locals = new ArrayList<>();
            for (PendingLocal local : pending) {
                int end = local.blockEnd();
                if (!local.element()) {
                    for (Block block : blocks) if (block.start() < local.declaration()
                            && local.declaration() < block.end() && block.end() < end) end = block.end();
                }
                locals.add(new Local(local.name(), local.expression(), local.declaration(),
                        local.visibleFrom(), end, local.element()));
            }
            tokens.sort(Comparator.comparingInt(Token::start));
            return new Parsed(List.copyOf(tokens), List.copyOf(uses), List.copyOf(regions),
                    List.copyOf(locals), List.copyOf(blocked), List.copyOf(ignoredMarkup),
                    List.copyOf(tagNames));
        }

        private int tag(int start) {
            int end = tagEnd(start);
            blocked.add(new Span(start, end));
            int i = start + 1;
            while (i < end && source.charAt(i) != '>' && !Character.isWhitespace(source.charAt(i))) i++;
            String tagName = source.substring(start + 1, i).toLowerCase(Locale.ROOT);
            if (!tagName.startsWith("!")) tagNames.add(new Span(
                    start + (tagName.startsWith("/") ? 2 : 1), i));
            while (i < end) {
                while (i < end && Character.isWhitespace(source.charAt(i))) i++;
                int nameStart = i;
                if (i < end && (source.charAt(i) == '[' || source.charAt(i) == '(')) {
                    char closing = source.charAt(i) == '[' ? ']' : ')';
                    i++;
                    while (i < end && source.charAt(i) != closing && source.charAt(i) != '>') i++;
                    if (i < end && source.charAt(i) == closing) i++;
                } else while (i < end && (wordPart(source.charAt(i)) || "-:.".indexOf(source.charAt(i)) >= 0)) i++;
                if (i == nameStart) { i++; continue; }
                String name = source.substring(nameStart, i);
                i = space(i);
                if (i >= end || source.charAt(i) != '=') continue;
                i = space(i + 1);
                if (i >= end || (source.charAt(i) != '"' && source.charAt(i) != '\'')) continue;
                char quote = source.charAt(i++);
                int valueStart = i;
                while (i < end && source.charAt(i) != quote) {
                    if (source.charAt(i) == '\\' && i + 1 < end) i++;
                    i++;
                }
                int valueEnd = Math.min(i, end);
                if (name.startsWith("on") || tagName.equals("form") && name.equals("action")
                        || name.startsWith("[") && name.endsWith("]")
                        || name.startsWith("(") && name.endsWith(")")) {
                    boolean event = name.startsWith("on") || tagName.equals("form") && name.equals("action")
                            || name.startsWith("(");
                    expression(valueStart, valueEnd, event);
                } else {
                    for (int at = valueStart; at < valueEnd;) {
                        if (source.startsWith("{{", at)) at = interpolation(at, valueEnd);
                        else at++;
                    }
                }
                if (i < end && source.charAt(i) == quote) i++;
            }
            if ((tagName.equals("style") || tagName.equals("script")) && end >= 2
                    && source.charAt(end - 2) != '/') {
                String lower = source.toLowerCase(Locale.ROOT);
                int closeStart = lower.indexOf("</" + tagName, end);
                int rawEnd = closeStart < 0 ? length : tagEnd(closeStart);
                blocked.add(new Span(end, rawEnd));
                ignoredMarkup.add(new Span(end, rawEnd));
                return rawEnd;
            }
            return end;
        }

        private int tagEnd(int start) {
            char quote = 0;
            for (int i = start + 1; i < length; i++) {
                char ch = source.charAt(i);
                if (quote != 0) {
                    if (ch == '\\' && i + 1 < length) i++;
                    else if (ch == quote) quote = 0;
                } else if (ch == '"' || ch == '\'') quote = ch;
                else if (ch == '>') return i + 1;
            }
            return length;
        }

        private int interpolation(int start, int limit) {
            int close = interpolationEnd(start + 2, limit);
            expression(start + 2, close < 0 ? limit : close, false);
            return close < 0 ? limit : close + 2;
        }

        private int interpolationEnd(int start, int limit) {
            int braces = 0;
            for (int i = start; i < limit;) {
                int skipped = skipLiteralOrComment(i, limit);
                if (skipped != i) { i = skipped; continue; }
                if (i + 1 < limit && source.startsWith("}}", i) && braces == 0) return i;
                char ch = source.charAt(i);
                if (ch == '{') braces++;
                else if (ch == '}' && braces > 0) braces--;
                i++;
            }
            return -1;
        }

        private int directive(int start) {
            int wordEnd = wordEnd(start + 1);
            if (wordEnd == start + 1) return start + 1;
            String name = source.substring(start + 1, wordEnd);
            if (!DIRECTIVES.contains(name)) return wordEnd;
            tokens.add(new Token(start, wordEnd, source.substring(start, wordEnd), Kind.DIRECTIVE));
            int after = space(wordEnd);
            if (name.equals("use")) return use(after);
            if (name.equals("let")) return let(after);
            if (name.equals("else")) {
                if (!source.startsWith("if", after) || after + 2 < length && wordPart(source.charAt(after + 2)))
                    return blockAfter(after);
                after = space(after + 2);
            }
            if (after < length && source.charAt(after) == '(') {
                int close = balancedEnd(after, '(', ')');
                int expressionEnd = close < 0 ? length : close;
                if (name.equals("for")) forCondition(after + 1, expressionEnd, close);
                else expression(after + 1, expressionEnd, false);
                return close < 0 ? length : blockAfter(close + 1);
            }
            if (name.equals("for") || name.equals("match")) {
                int brace = findBlockStart(after);
                expression(after, brace < 0 ? length : brace, false);
                return brace < 0 ? length : blockAfter(brace);
            }
            return blockAfter(after);
        }

        private int blockAfter(int after) {
            int open = space(after);
            if (open < length && source.charAt(open) == '{') {
                int close = balancedEnd(open, '{', '}');
                blocks.add(new Block(open, close < 0 ? length + 1 : close));
                return open + 1;
            }
            return after;
        }

        private int findBlockStart(int from) {
            for (int i = from; i < length; i++) {
                int skipped = skipLiteralOrComment(i, length);
                if (skipped != i) { i = skipped - 1; continue; }
                if (source.charAt(i) == '{') return i;
                if (source.charAt(i) == '<' || source.charAt(i) == '\n') return -1;
            }
            return -1;
        }

        private void forCondition(int start, int end, int close) {
            int variableStart = space(start);
            int variableEnd = wordEnd(variableStart);
            int in = space(variableEnd);
            if (variableEnd == variableStart || !source.startsWith("in", in)
                    || in + 2 < end && wordPart(source.charAt(in + 2))) {
                expression(start, end, false); return;
            }
            int iterableStart = space(in + 2);
            int separator = topLevelSemicolon(iterableStart, end);
            String iterable = source.substring(iterableStart, separator < 0 ? end : separator).trim();
            expression(iterableStart, separator < 0 ? end : separator, false);
            int visibleFrom = close < 0 ? end : close + 1;
            if (separator >= 0) {
                int track = space(separator + 1);
                if (source.startsWith("track", track) && track + 5 <= end
                        && (track + 5 == end || !wordPart(source.charAt(track + 5)))) track = space(track + 5);
                expression(track, end, false);
                visibleFrom = track;
            }
            int open = close < 0 ? -1 : space(close + 1);
            int bodyEnd = open < length && source.charAt(open) == '{'
                    ? balancedEnd(open, '{', '}') : -1;
            pending.add(new PendingLocal(source.substring(variableStart, variableEnd), iterable,
                    variableStart, visibleFrom, open, bodyEnd < 0 ? length + 1 : bodyEnd, true));
        }

        private int topLevelSemicolon(int start, int end) {
            int depth = 0;
            for (int i = start; i < end;) {
                int skipped = skipLiteralOrComment(i, end);
                if (skipped != i) { i = skipped; continue; }
                char ch = source.charAt(i);
                if (ch == '(' || ch == '[' || ch == '{') depth++;
                else if ((ch == ')' || ch == ']' || ch == '}') && depth > 0) depth--;
                else if (ch == ';' && depth == 0) return i;
                i++;
            }
            return -1;
        }

        private int let(int after) {
            int nameStart = after, nameEnd = wordEnd(nameStart);
            if (nameEnd == nameStart) return after;
            int equal = space(nameEnd);
            if (equal >= length || source.charAt(equal) != '=') return nameEnd;
            int valueStart = space(equal + 1);
            int semicolon = topLevelSemicolon(valueStart, length);
            int valueEnd = semicolon < 0 ? length : semicolon;
            expression(valueStart, valueEnd, false);
            if (semicolon >= 0) pending.add(new PendingLocal(source.substring(nameStart, nameEnd),
                    source.substring(valueStart, valueEnd).trim(), nameStart, semicolon + 1, -1, length + 1, false));
            return semicolon < 0 ? length : semicolon + 1;
        }

        private int use(int after) {
            if (after >= length || source.charAt(after) != '"') return after;
            int end = quotedEnd(after, length);
            if (end < 0) { blocked.add(new Span(after, length)); return length; }
            int cursor = space(end + 1);
            String target = source.substring(after + 1, end);
            String alias = target.substring(Math.max(0, target.lastIndexOf("::") + 2));
            if (source.startsWith("as", cursor) && cursor + 2 < length && Character.isWhitespace(source.charAt(cursor + 2))) {
                cursor = space(cursor + 2);
                if (cursor < length && source.charAt(cursor) == '*') { alias = "*"; cursor++; }
                else {
                    int aliasEnd = wordEnd(cursor);
                    if (aliasEnd > cursor) alias = source.substring(cursor, aliasEnd);
                    cursor = aliasEnd;
                }
                cursor = space(cursor);
            }
            if (cursor < length && source.charAt(cursor) == ';') {
                uses.add(new Use(after + 1, end, target, alias));
                tokens.add(new Token(after + 1, end, target, Kind.USE_TARGET));
                blocked.add(new Span(after, end + 1));
                return cursor + 1;
            }
            blocked.add(new Span(after, end + 1));
            return end + 1;
        }

        private int balancedEnd(int open, char left, char right) {
            int depth = 0;
            for (int i = open; i < length;) {
                int skipped = skipLiteralOrComment(i, length);
                if (skipped != i) { i = skipped; continue; }
                if (source.startsWith("<!--", i)) {
                    int close = source.indexOf("-->", i + 4);
                    i = close < 0 ? length : close + 3; continue;
                }
                if (source.charAt(i) == left) depth++;
                else if (source.charAt(i) == right && --depth == 0) return i;
                i++;
            }
            return -1;
        }

        private int skipLiteralOrComment(int i, int end) {
            char ch = source.charAt(i);
            if (ch == '"' || ch == '\'') {
                if (ch == '\'' && i > 0 && wordPart(source.charAt(i - 1))) return i;
                int close = quotedEnd(i, end);
                return close < 0 ? end : close + 1;
            }
            if (ch == '/' && i + 1 < end && source.charAt(i + 1) == '/') {
                int close = source.indexOf('\n', i + 2);
                return close < 0 || close >= end ? end : close;
            }
            if (ch == '/' && i + 1 < end && source.charAt(i + 1) == '*') {
                int close = source.indexOf("*/", i + 2);
                return close < 0 || close >= end ? end : close + 2;
            }
            return i;
        }

        private int quotedEnd(int open, int end) {
            char quote = source.charAt(open);
            for (int i = open + 1; i < end; i++) {
                if (source.charAt(i) == '\\' && i + 1 < end) i++;
                else if (source.charAt(i) == quote) return i;
            }
            return -1;
        }

        private void expression(int start, int end, boolean event) {
            if (start > end) return;
            regions.add(new Region(start, end, event));
            for (int i = start; i < end;) {
                int skipped = skipLiteralOrComment(i, end);
                if (skipped != i) { i = skipped; continue; }
                char ch = source.charAt(i);
                if (event && ch == '$' && i + 1 < end && wordStart(source.charAt(i + 1))) {
                    int wordEnd = wordEnd(i + 1);
                    if (ACTIONS.contains(source.substring(i + 1, wordEnd)))
                        tokens.add(new Token(i, wordEnd, source.substring(i, wordEnd), Kind.ACTION));
                    else if (source.substring(i + 1, wordEnd).equals("event"))
                        tokens.add(new Token(i, wordEnd, "$event", Kind.IDENTIFIER));
                    i = wordEnd; continue;
                }
                if (wordStart(ch)) {
                    int wordEnd = wordEnd(i);
                    if (!source.substring(i, wordEnd).equals("in") && !source.substring(i, wordEnd).equals("track"))
                        tokens.add(new Token(i, wordEnd, source.substring(i, wordEnd), Kind.IDENTIFIER));
                    i = wordEnd;
                } else i++;
            }
        }

        private int space(int i) {
            while (i < length && Character.isWhitespace(source.charAt(i))) i++;
            return i;
        }
        private int wordEnd(int i) {
            if (i >= length || !wordStart(source.charAt(i))) return i;
            i++;
            while (i < length && wordPart(source.charAt(i))) i++;
            return i;
        }
    }

    private static boolean wordStart(char ch) { return Character.isLetter(ch) || ch == '_'; }
    private static boolean wordPart(char ch) { return Character.isLetterOrDigit(ch) || ch == '_'; }
}
