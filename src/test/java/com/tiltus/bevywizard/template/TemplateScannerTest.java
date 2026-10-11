package com.tiltus.bevywizard.template;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TemplateScannerTest {
    @Test
    void recognizesDirectivesActionsAndRustPathsInOneTemplate() {
        String source = """
                @if (demoState.count > 0) {
                  <button onclick="$set(demoState.count, $event.value)">{{ demoState.name }}</button>
                } @else { Empty }
                """;
        var tokens = TemplateScanner.tokens(source);
        assertTrue(tokens.stream().anyMatch(token -> token.text().equals("@if")));
        assertTrue(tokens.stream().anyMatch(token -> token.text().equals("@else")));
        assertTrue(tokens.stream().anyMatch(token -> token.text().equals("$set")));
        var count = tokens.stream().filter(token -> token.text().equals("count")).findFirst().orElseThrow();
        assertEquals("demoState.count", TemplateIndex.pathAt(source, count.start(), count.end()));
        assertNotNull(TemplateScanner.regionAt(source, source.indexOf("$set") + 2));
    }

    @Test
    void offersCompletionWhileAttributeOrInterpolationIsUnfinished() {
        String event = "<button onclick=\"$se";
        assertTrue(TemplateScanner.regionAt(event, event.length()).event());
        String interpolation = "<p>{{ profile.na";
        assertNotNull(TemplateScanner.regionAt(interpolation, interpolation.length()));
    }

    @Test
    void ignoresOrdinaryHtmlTextAsRustExpression() {
        String source = "<p>profile.name</p>";
        assertTrue(TemplateScanner.tokens(source).stream()
                .noneMatch(token -> token.kind() == TemplateScanner.Kind.IDENTIFIER));
    }

    @Test
    void recognizesUseTargetsAndRuntimeAliasForms() {
        String source = "@use \"crate::model::Profile\";\n@use \"Profile\" as *;\n";
        var uses = TemplateScanner.uses(source);
        assertEquals(2, uses.size());
        assertEquals("Profile", uses.get(0).alias());
        assertEquals("*", uses.get(1).alias());
        assertEquals("crate::model::Profile", TemplateScanner.tokenAt(source, source.indexOf("model") + 1).text());
    }

    @Test
    void keepsDirectivesOutOfCommentsTagsStringsAndRawElements() {
        String source = """
                <!-- @if (fake.comment) { {{ fake.comment }} } -->
                <div title="@if (fake.attribute)" data-label="{{ view.title }}">
                  <style>.demo::after { content: "@for (fake.style)"; }</style>
                  @if (view.name == "@match (fake.string)") { {{ view.name }} }
                </div>
                """;
        var tokens = TemplateScanner.tokens(source);
        assertEquals(1, tokens.stream().filter(token -> token.text().equals("@if")).count());
        assertFalse(tokens.stream().anyMatch(token -> token.text().equals("fake")));
        assertTrue(tokens.stream().anyMatch(token -> token.text().equals("title")));
        assertFalse(TemplateScanner.directivePosition(source, source.indexOf("@if")));
        assertFalse(TemplateScanner.directivePosition(source, source.indexOf("@if", source.indexOf("<div"))));
        assertNotNull(TemplateScanner.regionAt(source, source.indexOf("view.title") + 2));
        assertTrue(TemplateScanner.ignoredMarkupAt(source, source.indexOf("fake.comment")));
        assertTrue(TemplateScanner.ignoredMarkupAt(source, source.indexOf("fake.style")));
        assertFalse(TemplateScanner.tagNameAt(source, source.indexOf("fake.attribute")));
        assertTrue(TemplateScanner.tagNameAt(source, source.indexOf("<div") + 2));
    }

    @Test
    void nestedBlocksKeepLetAndForValuesInTheirOwnScopes() {
        String source = """
                @let outer = view.title;
                @if (view.ready) {
                  @let branch = view.title;
                  @match (view.state) {
                    'ready' => {
                      @for (row in view.rows; track row.id) {
                        {{ row.label }} {{ branch }}
                      }
                    }
                  }
                }
                {{ outer }} {{ branch }} {{ row.label }}
                """;
        var locals = TemplateScanner.locals(source);
        var outer = locals.stream().filter(local -> local.name().equals("outer")).findFirst().orElseThrow();
        var branch = locals.stream().filter(local -> local.name().equals("branch")).findFirst().orElseThrow();
        var row = locals.stream().filter(local -> local.name().equals("row")).findFirst().orElseThrow();
        int inside = source.indexOf("{{ row.label }}");
        int outside = source.lastIndexOf("{{ row.label }}");
        assertTrue(outer.visibleAt(inside));
        assertTrue(outer.visibleAt(outside));
        assertTrue(branch.visibleAt(inside));
        assertFalse(branch.visibleAt(outside));
        assertTrue(row.visibleAt(inside));
        assertFalse(row.visibleAt(outside));
        assertEquals("view.rows", row.expression());
        assertNotNull(TemplateScanner.regionAt(source, source.indexOf("row.id")));
        assertEquals(5, TemplateScanner.tokens(source).stream()
                .filter(token -> token.kind() == TemplateScanner.Kind.DIRECTIVE).count());
    }

    @Test
    void unfinishedExpressionsStayLocalToTheirActualRegion() {
        String source = "<button onclick=\"$set(view.value, 'x')\">{{ view.name }}";
        assertTrue(TemplateScanner.regionAt(source, source.indexOf("$set") + 1).event());
        assertFalse(TemplateScanner.regionAt(source, source.indexOf("view.name") + 2).event());
        assertNull(TemplateScanner.regionAt(source, source.indexOf("<button") + 1));
        assertNull(TemplateScanner.tokenAt(source, source.indexOf("'x'") + 1));
        String unfinished = "@if (view.ready && call(\"hi )\", view.name";
        assertNotNull(TemplateScanner.regionAt(unfinished, unfinished.length()));
        assertFalse(TemplateScanner.directivePosition("<p title=\"@i\">", 10));
    }

    @Test
    void elseIfAndMatchConditionsRemainSeparateFromMarkup() {
        String source = """
                @if (view.first) { <p>{{ view.one }}</p> }
                @else if (view.second && call("nested ) text")) {
                  @match (view.mode) {
                    'ready' => { <p>{{ view.two }}</p> }
                  }
                } @else { <p>Plain text</p> }
                """;
        assertEquals(2, TemplateScanner.tokens(source).stream()
                .filter(token -> token.text().equals("@else")).count());
        assertEquals(1, TemplateScanner.tokens(source).stream()
                .filter(token -> token.text().equals("@match")).count());
        assertNotNull(TemplateScanner.regionAt(source, source.indexOf("view.second") + 2));
        assertNotNull(TemplateScanner.regionAt(source, source.indexOf("view.mode") + 2));
        assertNull(TemplateScanner.regionAt(source, source.indexOf("Plain text") + 2));
        assertFalse(TemplateScanner.tokens(source).stream().anyMatch(token -> token.text().equals("nested")));
    }

    @Test
    void apostropheInHtmlTextDoesNotExtendTheSurroundingScope() {
        String source = "@if (ready) { <p>It's ready</p> @let message = view.title; {{ message }} } {{ message }}";
        var local = TemplateScanner.locals(source).getFirst();
        assertTrue(local.visibleAt(source.indexOf("{{ message }}")));
        assertFalse(local.visibleAt(source.lastIndexOf("{{ message }}")));
    }

    @Test
    void topLevelLocalRemainsVisibleAtEndOfIncompleteExpression() {
        String source = "@let title = view.title; <p>{{ title.";
        var local = TemplateScanner.locals(source).getFirst();
        assertTrue(local.visibleAt(source.length()));
        assertNotNull(TemplateScanner.regionAt(source, source.length()));
    }
}
