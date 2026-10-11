package com.tiltus.bevywizard.template;

import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.Lookup;
import com.intellij.codeInsight.navigation.actions.GotoDeclarationAction;
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors;

import java.util.Arrays;

/** Exercises real HTML and Rust PSI through the RustRover editor fixture. */
public final class TiltEditorIntegrationTest extends BasePlatformTestCase {
    public void testIconToolWindowIsAvailableForEnabledProject() throws Exception {
        java.nio.file.Path cargo = java.nio.file.Path.of(getProject().getBasePath(), "Cargo.toml");
        java.nio.file.Files.createDirectories(cargo.getParent());
        java.nio.file.Files.writeString(cargo,
                "[dependencies]\ntilt-ui = { version = \"0.1.0\", features = [\"tilt-icons\"] }\n");
        try {
            assertTrue(com.tiltus.bevywizard.icons.IconCatalogToolWindowFactory.hasIcons(getProject()));
        } finally {
            java.nio.file.Files.deleteIfExists(cargo);
        }
    }

    public void testSelfClosingCustomTagHasDocumentationAndNoMissingEndTag() {
        myFixture.addFileToProject("src-ui/components/app-header.component.html",
                "<!-- Displays the application navigation header. -->\n<header>Header</header>");
        myFixture.addFileToProject("src-ui/pages/diag.component.rs", "#[html_fn(\"go_home\")] fn go_home() {}\n");
        PsiFile html = myFixture.addFileToProject("src-ui/pages/diag.component.html", "<app-header/>\n<icon name=\"home\"/>\n<button onclick=\"go_home\">Go</button>\n");
        var tag = ((com.intellij.psi.xml.XmlFile) html).getRootTag();
        assertEquals(com.intellij.xml.XmlElementDescriptor.CONTENT_TYPE_EMPTY, tag.getDescriptor().getContentType());
        TiltDocumentationProvider docs = new TiltDocumentationProvider();
        int tagOffset = html.getText().indexOf("app-header") + 2;
        var hover = docs.getCustomDocumentationElement(null, html, html.findElementAt(tagOffset), tagOffset);
        assertNotNull(hover);
        assertTrue(docs.generateHoverDoc(hover, null).contains("Displays the application navigation header"));
        int iconOffset = html.getText().indexOf("<icon") + 2;
        var iconHover = docs.getCustomDocumentationElement(null, html, html.findElementAt(iconOffset), iconOffset);
        assertNotNull(iconHover);
        assertTrue(docs.generateHoverDoc(iconHover, null).contains("pixel size"));
        myFixture.configureFromExistingVirtualFile(html.getVirtualFile());
        for (var info : myFixture.doHighlighting())
            if (info.getDescription() != null && info.getSeverity().compareTo(com.intellij.lang.annotation.HighlightSeverity.WARNING) >= 0)
                assertFalse(info.getDescription(), info.getDescription().toLowerCase().contains("not closed"));
    }
    public void testEmptyIconSelfClosingTagIsRecognized() {
        PsiFile html = myFixture.addFileToProject("src-ui/pages/empty-icon.component.html", "<icon />");
        assertEquals(com.intellij.xml.XmlElementDescriptor.CONTENT_TYPE_EMPTY,
                ((com.intellij.psi.xml.XmlFile) html).getRootTag().getDescriptor().getContentType());
        myFixture.configureFromExistingVirtualFile(html.getVirtualFile());
        for (var info : myFixture.doHighlighting())
            assertFalse(info.getDescription() + " (" + info.getSeverity() + ")",
                    info.getSeverity().compareTo(com.intellij.lang.annotation.HighlightSeverity.WARNING) >= 0);
    }
    public void testIconInCustomComponentDirectoryIsRecognized() {
        PsiFile html = myFixture.addFileToProject("ui/pages/other.component.html", "<icon />");
        assertNotNull(((com.intellij.psi.xml.XmlFile) html).getRootTag().getDescriptor());
        myFixture.configureFromExistingVirtualFile(html.getVirtualFile());
        assertFalse(myFixture.doHighlighting().stream().anyMatch(info ->
                info.getSeverity().compareTo(com.intellij.lang.annotation.HighlightSeverity.WARNING) >= 0));
    }
    public void testTiltUiTagsAndAttributesFromStarterAreRecognized() {
        myFixture.addFileToProject("src-ui/pages/main.component.rs", "#[html_fn(\"go_home\")] fn go_home() {}\n");
        PsiFile html = myFixture.addFileToProject("src-ui/pages/main.component.html", """
                <hyperlink href="https://bevy.org/" icon="none"><icon name="bevy" size="20" />Bevy</hyperlink>
                <button onclick="go_home">Home</button>
                <router-outlet></router-outlet>
                """);
        var link = ((com.intellij.psi.xml.XmlFile) html).getRootTag();
        assertNotNull(link.getDescriptor());
        assertNotNull(link.getAttribute("href").getDescriptor());
        assertNotNull(link.getAttribute("icon").getDescriptor());
        var icon = link.getSubTags()[0];
        assertNotNull(icon.getAttribute("name").getDescriptor());
        assertNotNull(icon.getAttribute("size").getDescriptor());
        myFixture.configureFromExistingVirtualFile(html.getVirtualFile());
        for (var info : myFixture.doHighlighting())
            assertFalse(info.getDescription(), info.getSeverity().compareTo(com.intellij.lang.annotation.HighlightSeverity.WARNING) >= 0);
    }

    public void testTiltIconAttributeCompletion() {
        PsiFile html = myFixture.addFileToProject("src-ui/pages/icon.component.html", "<icon na />");
        myFixture.configureFromExistingVirtualFile(html.getVirtualFile());
        myFixture.getEditor().getCaretModel().moveToOffset(html.getText().indexOf("na") + 2);
        LookupElement[] choices = myFixture.completeBasic();
        assertTrue(choices == null && myFixture.getFile().getText().contains("name")
                || choices != null && Arrays.stream(choices).anyMatch(item -> item.getLookupString().equals("name")));
    }

    public void testWidgetCatalogOffersTagSpecificStaticAndReactiveAttributes() {
        PsiFile html = myFixture.addFileToProject("src-ui/pages/input.component.html",
                "<input type=\"email\" placeholder=\"Mail\" [value]=\"state.email\" />");
        var input = ((com.intellij.psi.xml.XmlFile) html).getRootTag();
        assertNotNull(input.getDescriptor());
        assertNotNull(input.getAttribute("[value]").getDescriptor());
        assertNull(input.getDescriptor().getAttributeDescriptor("orientation", input));
        assertNull(input.getDescriptor().getAttributeDescriptor("[range-start]", input));
        var type = input.getAttribute("type").getDescriptor();
        assertTrue(type.isEnumerated());
        assertTrue(Arrays.asList(type.getEnumeratedValues()).contains("email"));
        assertNull(type.validateValue(input.getAttribute("type"), "email"));
        assertNotNull(type.validateValue(input.getAttribute("type"), "imaginary"));
        TiltDocumentationProvider documentation = new TiltDocumentationProvider();
        int typeOffset = html.getText().indexOf("type") + 1;
        var typeHelp = documentation.getCustomDocumentationElement(null, html,
                html.findElementAt(typeOffset), typeOffset);
        assertNotNull(typeHelp);
        assertTrue(documentation.generateHoverDoc(typeHelp, null).contains("control's mode"));
        PsiFile sliderFile = myFixture.addFileToProject("src-ui/pages/slider.component.html",
                "<slider type=\"range\" orientation=\"vertical\" [range-start]=\"state.min\" />");
        var slider = ((com.intellij.psi.xml.XmlFile) sliderFile).getRootTag();
        assertNotNull(slider.getAttribute("[range-start]").getDescriptor());
        assertNull(slider.getDescriptor().getAttributeDescriptor("placeholder", slider));
        for (String tag : new String[]{"dialog", "toast", "tooltip", "icon"}) {
            PsiFile widget = myFixture.addFileToProject("src-ui/pages/" + tag + ".component.html",
                    "<" + tag + " />");
            assertNotNull(((com.intellij.psi.xml.XmlFile) widget).getRootTag().getDescriptor());
        }
    }

    public void testWidgetValueAndReactiveAttributeCompletion() {
        PsiFile input = myFixture.addFileToProject("src-ui/pages/input.component.html", "<input type=\"em\" />");
        myFixture.configureFromExistingVirtualFile(input.getVirtualFile());
        myFixture.getEditor().getCaretModel().moveToOffset(input.getText().indexOf("em") + 2);
        LookupElement[] values = myFixture.completeBasic();
        assertTrue(values == null && myFixture.getFile().getText().contains("email")
                || values != null && Arrays.stream(values).anyMatch(item -> item.getLookupString().equals("email")));

        PsiFile slider = myFixture.addFileToProject("src-ui/pages/slider.component.html", "<slider [ori />");
        myFixture.configureFromExistingVirtualFile(slider.getVirtualFile());
        myFixture.getEditor().getCaretModel().moveToOffset(slider.getText().indexOf("[ori") + 4);
        LookupElement[] bindings = myFixture.completeBasic();
        assertTrue(bindings == null && myFixture.getFile().getText().contains("[orientation]")
                || bindings != null && Arrays.stream(bindings).anyMatch(item -> item.getLookupString().equals("[orientation]")));
    }

    public void testTiltCssCompletionDocumentationAndParserSpecificWarnings() {
        myFixture.enableInspections(new TiltCssInspection());
        PsiFile css = myFixture.addFileToProject("src-ui/pages/main.component.css", """
                .card {
                  /* effect-quality: ultra; */
                  content: "effect-quality: ultra";
                  background-filter: blur(4px) grayscale(50%);
                  backdrop-filter: oil-paint(2);
                  animated-filter: bloom(0.8);
                  effect-quality: ultra;
                  border-brush-strength: 120%;
                  animation-duration: 250ms;
                }
                """);
        myFixture.configureFromExistingVirtualFile(css.getVirtualFile());
        var messages = myFixture.doHighlighting().stream().filter(info -> info.getDescription() != null)
                .map(info -> info.getDescription()).toList();
        assertTrue(messages.toString(), messages.stream().anyMatch(text -> text.contains("oil-paint")));
        assertTrue(messages.toString(), messages.stream().anyMatch(text -> text.contains("effect-quality")));
        assertTrue(messages.toString(), messages.stream().anyMatch(text -> text.contains("border-brush-strength")));
        assertEquals(messages.toString(), 1, messages.stream().filter(text -> text.contains("effect-quality")).count());
        assertFalse(messages.toString(), messages.stream().anyMatch(text -> text.contains("animation-duration")));
        TiltCssDocumentationProvider docs = new TiltCssDocumentationProvider();
        int offset = css.getText().indexOf("background-filter") + 3;
        var element = docs.getCustomDocumentationElement(null, css, css.findElementAt(offset), offset);
        assertNotNull(element);
        assertTrue(docs.generateHoverDoc(element, null).contains("cached background"));

        PsiFile suggestions = myFixture.addFileToProject("src-ui/pages/suggestions.component.css", ".card { effect-qu }");
        myFixture.configureFromExistingVirtualFile(suggestions.getVirtualFile());
        myFixture.getEditor().getCaretModel().moveToOffset(suggestions.getText().indexOf("effect-qu") + 9);
        LookupElement[] properties = myFixture.completeBasic();
        assertTrue("language=" + myFixture.getFile().getLanguage() + ", choices=" + Arrays.toString(properties)
                        + ", text=" + myFixture.getFile().getText(),
                properties == null && myFixture.getFile().getText().contains("effect-quality")
                || properties != null && Arrays.stream(properties)
                        .anyMatch(item -> item.getLookupString().equals("effect-quality")));

        PsiFile icon = myFixture.addFileToProject("src-ui/pages/icon.component.css",
                ".card { background-image: url(\"tilt-icon:ho\"); }");
        myFixture.configureFromExistingVirtualFile(icon.getVirtualFile());
        myFixture.getEditor().getCaretModel().moveToOffset(icon.getText().indexOf("tilt-icon:ho") + 12);
        LookupElement[] icons = myFixture.completeBasic();
        assertTrue(Arrays.toString(icons), icons != null && Arrays.stream(icons)
                .anyMatch(item -> item.getLookupString().equals("home@32")));
        int iconOffset = icon.getText().indexOf("tilt-icon") + 2;
        var iconHelp = docs.getCustomDocumentationElement(null, icon, icon.findElementAt(iconOffset), iconOffset);
        assertNotNull(iconHelp);
        assertTrue(docs.generateHoverDoc(iconHelp, null).contains("@32"));
    }

    public void testCatalogSkipsUnsupportedPinnedTiltUiRelease() {
        myFixture.addFileToProject("Cargo.toml", "[dependencies]\ntilt-ui = { version = \"=0.2.0\" }\n");
        PsiFile html = myFixture.addFileToProject("src-ui/pages/new.component.html", "<slider [value]=\"state.value\" />");
        assertNull(TiltUiRelease.forFile(html));
        assertNull(new TiltComponentTags().getDescriptor(((com.intellij.psi.xml.XmlFile) html).getRootTag()));
        PsiFile css = myFixture.addFileToProject("src-ui/pages/new.component.css", ".x { effect-quality: ultra; }");
        myFixture.enableInspections(new TiltCssInspection());
        myFixture.configureFromExistingVirtualFile(css.getVirtualFile());
        assertFalse(myFixture.doHighlighting().stream().anyMatch(info -> info.getDescription() != null
                && info.getDescription().contains("TiltUI effect-quality")));
    }

    public void testCatalogReadsPinnedReleaseFromCargoDependencyTable() {
        myFixture.addFileToProject("Cargo.toml", "[dependencies.tilt-ui]\nversion = \"=0.1.0-rc.2\"\n");
        PsiFile html = myFixture.addFileToProject("src-ui/pages/rc.component.html", "<slider type=\"range\" />");
        assertEquals("0.1.0-rc.2", TiltUiRelease.forFile(html));
        assertNotNull(new TiltComponentTags().getDescriptor(((com.intellij.psi.xml.XmlFile) html).getRootTag()));
    }

    public void testTiltCssParserContractForMotionAndEffects() {
        assertNull(TiltCssCatalog.invalidValue("animation-duration", "150ms, 1s"));
        assertNull(TiltCssCatalog.invalidValue("animation-timing-function", "ease, linear"));
        assertNull(TiltCssCatalog.invalidValue("background-filter", "blur(4px) oil-paint(2)"));
        assertNotNull(TiltCssCatalog.invalidValue("animation-duration", "fast"));
        assertNotNull(TiltCssCatalog.invalidValue("background-filter", "blur(80px)"));
        assertNotNull(TiltCssCatalog.invalidValue("backdrop-filter", "oil-paint(2)"));
        assertNotNull(TiltCssCatalog.invalidValue("animated-filter", "noise(5)"));
        assertNull(TiltCssCatalog.invalidValue("border-brush-strength", "70%"));
        assertNotNull(TiltCssCatalog.invalidValue("border-brush-strength", "101%"));
    }

    public void testAssetPathsUseConfiguredUiRootForNavigationAndDiagnostics() {
        PsiFile cargo = myFixture.addFileToProject("Cargo.toml", "[dependencies.tilt-ui]\nversion = \"=0.1.0\"\n");
        myFixture.addFileToProject("src/main.rs", """
                const UI_SOURCE_ROOT: &str = concat!(env!("CARGO_MANIFEST_DIR"), "/ui");
                fn main() {
                    TiltUiPlugin::new(catalog).with_source_root(UI_SOURCE_ROOT);
                    UiLocalization::new().with_language_path("locales");
                }
                """);
        PsiFile stylesheet = myFixture.addFileToProject("ui/styles/site.css", ".page { color: red; }");
        PsiFile image = myFixture.addFileToProject("ui/media/photo.svg", "<svg/>");
        myFixture.addFileToProject("ui/locales/en-US.ftl", "hello = Hello\n");
        PsiFile index = myFixture.addFileToProject("ui/index.html", """
                <html><head><link rel="stylesheet" href="styles/site.css"></head>
                <body><img src="tilt-ui://media/photo.svg" /></body></html>
                """);
        assertEquals("ui", TiltUiSourceRoot.find(index).getName());
        myFixture.enableInspections(new com.tiltus.bevywizard.project.TiltProjectInspection());
        myFixture.configureFromExistingVirtualFile(cargo.getVirtualFile());
        assertFalse(myFixture.doHighlighting().stream().anyMatch(info -> info.getDescription() != null
                && info.getDescription().contains("entry document")));
        assertEquals(stylesheet, assetTarget(index, "site.css"));
        assertEquals(image, assetTarget(index, "tilt-ui://media/photo.svg"));
        myFixture.configureFromExistingVirtualFile(index.getVirtualFile());
        int imageOffset = index.getText().indexOf("photo.svg") + 2;
        assertTrue(Arrays.asList(GotoDeclarationAction.findAllTargetElements(
                getProject(), myFixture.getEditor(), imageOffset)).contains(image));
        PsiFile css = myFixture.addFileToProject("ui/pages/card.component.css",
                ".card { background-image: url(\"tilt-ui://media/photo.svg\"); }");
        assertEquals(image, assetTarget(css, "tilt-ui://media/photo.svg"));
        PsiFile rust = myFixture.addFileToProject("src/localization.rs",
                "UiLocalization::new().with_catalog(\"en-US\", \"locales/en-US.ftl\");");
        assertEquals("en-US.ftl", assetTarget(rust, "locales/en-US.ftl").getContainingFile().getName());

        myFixture.enableInspections(new TiltAssetPathInspection());
        PsiFile missing = myFixture.addFileToProject("ui/pages/missing.component.html",
                "<img src=\"tilt-ui://media/absent.svg\" />");
        myFixture.configureFromExistingVirtualFile(missing.getVirtualFile());
        assertTrue(myFixture.doHighlighting().stream().anyMatch(info -> info.getDescription() != null
                && info.getDescription().contains("TiltUI asset not found")));
        PsiFile external = myFixture.addFileToProject("ui/pages/external.component.html",
                "<img src=\"https://example.com/image.svg\" />");
        myFixture.configureFromExistingVirtualFile(external.getVirtualFile());
        assertFalse(myFixture.doHighlighting().stream().anyMatch(info -> info.getDescription() != null
                && info.getDescription().contains("TiltUI asset not found")));
        PsiFile localMissing = myFixture.addFileToProject("ui/pages/local-missing.component.html",
                "<img src=\"images/absent.svg\" />");
        myFixture.configureFromExistingVirtualFile(localMissing.getVirtualFile());
        assertTrue(myFixture.doHighlighting().stream().anyMatch(info -> info.getDescription() != null
                && info.getDescription().contains("TiltUI asset not found")));
    }

    public void testAssetPathCompletionInHtmlAndCss() {
        myFixture.addFileToProject("src-ui/styles/site.css", ".x {}\n");
        myFixture.addFileToProject("src-ui/media/photo.svg", "<svg/>\n");
        PsiFile html = myFixture.addFileToProject("src-ui/index.html",
                "<link rel=\"stylesheet\" href=\"styles/si\"/><img src=\"tilt-ui://media/ph\"/>");
        assertTrue(TiltAssetPaths.variants(html, TiltAssetPaths.find(html).getFirst()).contains("styles/site.css"));
        myFixture.configureFromExistingVirtualFile(html.getVirtualFile());
        myFixture.getEditor().getCaretModel().moveToOffset(html.getText().indexOf("styles/si") + 9);
        LookupElement[] styles = myFixture.completeBasic();
        assertTrue(Arrays.toString(styles) + ": " + myFixture.getFile().getText(),
                styles == null && myFixture.getFile().getText().contains("styles/site.css")
                        || styles != null && Arrays.stream(styles)
                        .anyMatch(item -> item.getLookupString().equals("styles/site.css")));
        myFixture.getEditor().getCaretModel().moveToOffset(myFixture.getFile().getText().indexOf("tilt-ui://media/ph") + 18);
        LookupElement[] images = myFixture.completeBasic();
        assertTrue(Arrays.toString(images) + ": " + myFixture.getFile().getText(),
                images == null && myFixture.getFile().getText().contains("tilt-ui://media/photo.svg")
                        || images != null && Arrays.stream(images)
                        .anyMatch(item -> item.getLookupString().equals("tilt-ui://media/photo.svg")));
        PsiFile css = myFixture.addFileToProject("src-ui/pages/picture.component.css",
                ".picture { background-image: url(\"tilt-ui://media/ph\"); }");
        assertTrue(TiltAssetPaths.variants(css, TiltAssetPaths.find(css).getFirst())
                .contains("tilt-ui://media/photo.svg"));
        myFixture.configureFromExistingVirtualFile(css.getVirtualFile());
        myFixture.getEditor().getCaretModel().moveToOffset(css.getText().indexOf("tilt-ui://media/ph") + 18);
        LookupElement[] cssImages = myFixture.completeBasic();
        assertTrue(Arrays.toString(cssImages) + ": " + myFixture.getFile().getText(),
                cssImages == null && myFixture.getFile().getText().contains("tilt-ui://media/photo.svg")
                        || cssImages != null && Arrays.stream(cssImages)
                        .anyMatch(item -> item.getLookupString().equals("tilt-ui://media/photo.svg")));
    }

    public void testComponentRenameUpdatesTripletTagsAndRouteReferences() {
        PsiFile template = myFixture.addFileToProject("src-ui/pages/old-card.component.html", "<p>Card</p>");
        myFixture.addFileToProject("src-ui/pages/old-card.component.css", ".card {}\n");
        myFixture.addFileToProject("src-ui/pages/old-card.component.rs", "// component\n");
        myFixture.addFileToProject("src-ui/pages/old-card.route.rs",
                "fn route() { tilt_ui_component_id(\"old-card\"); }\n");
        PsiFile registry = myFixture.addFileToProject("src-ui/routers.rs",
                "#[path = \"pages/old-card.route.rs\"]\nmod route_old_card;\n");
        PsiFile index = myFixture.addFileToProject("src-ui/index.html",
                "<body><old-card></old-card><old-card/></body>");
        assertTrue(ReferencesSearch.search(template).findAll().stream().anyMatch(ref ->
                ref.getElement().getContainingFile().equals(index)));
        myFixture.renameElement(template, "new-card.component.html");
        assertNotNull(myFixture.findFileInTempDir("src-ui/pages/new-card.component.html"));
        assertNotNull(myFixture.findFileInTempDir("src-ui/pages/new-card.component.css"));
        assertNotNull(myFixture.findFileInTempDir("src-ui/pages/new-card.component.rs"));
        PsiFile route = com.intellij.psi.PsiManager.getInstance(getProject()).findFile(
                myFixture.findFileInTempDir("src-ui/pages/new-card.route.rs"));
        assertNotNull(route);
        assertTrue(route.getText(), route.getText().contains("tilt_ui_component_id(\"new-card\")"));
        assertTrue(registry.getText(), registry.getText().contains("pages/new-card.route.rs"));
        assertTrue(index.getText(), index.getText().contains("<new-card></new-card><new-card/>"));
    }

    public void testRenamingComponentCssAlsoRenamesTemplateAndTagUses() {
        myFixture.addFileToProject("src-ui/cards/item.component.html", "<p>Item</p>");
        PsiFile css = myFixture.addFileToProject("src-ui/cards/item.component.css", ".item {}\n");
        myFixture.addFileToProject("src-ui/cards/item.component.rs", "// logic\n");
        PsiFile page = myFixture.addFileToProject("src-ui/index.html", "<body><item/></body>");
        myFixture.renameElement(css, "tile.component.css");
        assertNotNull(myFixture.findFileInTempDir("src-ui/cards/tile.component.html"));
        assertNotNull(myFixture.findFileInTempDir("src-ui/cards/tile.component.css"));
        assertNotNull(myFixture.findFileInTempDir("src-ui/cards/tile.component.rs"));
        assertTrue(page.getText(), page.getText().contains("<tile/>"));
    }

    public void testRustFieldAndHandlerRenameUpdatesTemplateReferences() {
        myFixture.addFileToProject("src-ui/pages/profile.component.rs", """
                #[html_shared]
                struct Profile { name: String }
                #[html_fn("greet")]
                fn greet() {}
                """);
        PsiFile html = myFixture.addFileToProject("src-ui/pages/profile.component.html",
                "<button onclick=\"greet\">{{ profile.name }}</button>");
        RustBindings bindings = RustBindings.forTemplate(html);
        assertTrue(ReferencesSearch.search(bindings.resolve("profile.name").target()).findAll().toString(),
                ReferencesSearch.search(bindings.resolve("profile.name").target()).findAll().stream()
                        .anyMatch(ref -> ref.getElement().getContainingFile().equals(html)));
        myFixture.renameElement(bindings.resolve("profile.name").target(), "display_name");
        assertTrue(html.getText(), html.getText().contains("profile.display_name"));
        myFixture.renameElement(RustBindings.forTemplate(html).resolve("greet").target(), "welcome");
        assertTrue(html.getText(), html.getText().contains("onclick=\"welcome\""));
        PsiFile rust = com.intellij.psi.PsiManager.getInstance(getProject()).findFile(
                myFixture.findFileInTempDir("src-ui/pages/profile.component.rs"));
        assertTrue(rust.getText(), rust.getText().contains("#[html_fn(\"welcome\")]"));
    }

    public void testRustHandlerRenameKeepsExplicitTemplateAlias() {
        PsiFile rust = myFixture.addFileToProject("src-ui/pages/alias.component.rs",
                "#[html_fn(\"go_home\")]\nfn navigate_home() {}\n");
        PsiFile html = myFixture.addFileToProject("src-ui/pages/alias.component.html",
                "<button onclick=\"go_home\">Home</button>");
        myFixture.renameElement(RustBindings.forTemplate(html).resolve("go_home").target(), "navigate_start");
        assertTrue(rust.getText(), rust.getText().contains("fn navigate_start()"));
        assertTrue(rust.getText(), rust.getText().contains("#[html_fn(\"go_home\")]"));
        assertTrue(html.getText(), html.getText().contains("onclick=\"go_home\""));
    }

    private com.intellij.psi.PsiElement assetTarget(PsiFile file, String path) {
        int offset = file.getText().indexOf(path) + path.length() - 2;
        return new TiltGotoDeclarationHandler().getGotoDeclarationTargets(file.findElementAt(offset), offset, null)[0];
    }

    public void testGeneratedStarterHasNoUnknownTiltTags() throws Exception {
        var temporary = java.nio.file.Files.createTempDirectory("tilt-editor-starter-");
        try {
            var projectDirectory = temporary.resolve("starter");
            com.tiltus.bevywizard.project.ProjectScaffolder.create(projectDirectory,
                    new com.tiltus.bevywizard.project.BevySettings("0.19.1", "1.95.0", "1.95.0", "1.95.0",
                            com.tiltus.bevywizard.project.ProjectKind.APP, true, true,
                            java.util.Set.of("tilt-icons"), java.util.List.of()));
            var page = projectDirectory.resolve("src-ui/pages/main.component.html");
            var logic = projectDirectory.resolve("src-ui/pages/main.component.rs");
            myFixture.addFileToProject("src-ui/pages/main.component.rs", java.nio.file.Files.readString(logic));
            PsiFile html = myFixture.addFileToProject("src-ui/pages/main.component.html", java.nio.file.Files.readString(page));
            myFixture.configureFromExistingVirtualFile(html.getVirtualFile());
            for (var info : myFixture.doHighlighting()) {
                if (info.getDescription() == null) continue;
                assertFalse(info.getDescription() + " at " + info.getStartOffset(),
                        info.getSeverity().compareTo(com.intellij.lang.annotation.HighlightSeverity.WARNING) >= 0);
            }
        } finally {
            com.intellij.openapi.util.io.FileUtil.delete(temporary.toFile());
        }
    }
    public void testRustDocCommentsAppearOnHover() {
        myFixture.addFileToProject("src-ui/pages/docs.component.rs", """
                /// Profile shown on this page.
                #[html_shared]
                struct Profile {
                    /// Visible display name.
                    name: String,
                }
                /// Opens the home page.
                #[html_fn("go_home")]
                fn go_home() {}
                """);
        PsiFile html = myFixture.addFileToProject("src-ui/pages/docs.component.html", """
                @use "Profile" as profile;
                <button onclick="go_home">{{ profile.name }}</button>
                """);
        TiltDocumentationProvider docs = new TiltDocumentationProvider();
        for (String[] expectation : new String[][] {
                {"Profile", "Profile shown on this page"},
                {"go_home", "Opens the home page"},
                {"name", "Visible display name"}
        }) {
            int offset = html.getText().indexOf(expectation[0]) + 1;
            var element = docs.getCustomDocumentationElement(null, html, html.findElementAt(offset), offset);
            assertNotNull(element);
            assertTrue(docs.generateHoverDoc(element, null), docs.generateHoverDoc(element, null).contains(expectation[1]));
        }
    }

    public void testTemplateLocalsCompleteAfterLetAndFor() {
        myFixture.addFileToProject("src-ui/pages/locals.component.rs", """
                #[html_shared]
                struct View { title: String, rows: Vec<Row> }
                struct Row { label: String }
                """);
        PsiFile html = myFixture.addFileToProject("src-ui/pages/locals.component.html", """
                @use "View" as view;
                @let title = view.title;
                @for (row in view.rows; track row.label) {
                  <p>{{ row.label }} {{ title }}</p>
                }
                """);
        RustBindings bindings = RustBindings.forTemplate(html);
        int insideLoop = html.getText().indexOf("{{ row.label }}");
        assertEquals("String", bindings.resolveAt("title", insideLoop).type());
        assertEquals("String", bindings.resolveAt("row.label", insideLoop).type());
        assertNull(bindings.resolveAt("row", html.getText().length()));
        myFixture.configureByText("lookup-locals.component.html", """
                @use "View" as view;
                @for (row in view.rows; track row.label) { <p>{{ row.<caret> }}</p> }
                """);
        LookupElement[] choices = myFixture.completeBasic();
        assertNotNull(choices);
        assertTrue(Arrays.stream(choices).anyMatch(item -> item.getLookupString().equals("label")));
    }

    public void testNestedTemplateLocalsDoNotLeakIntoLaterExpressions() {
        myFixture.addFileToProject("src-ui/pages/scopes.component.rs", """
                #[html_shared]
                struct View { title: String, rows: Vec<Row>, ready: bool }
                struct Row { label: String }
                """);
        PsiFile html = myFixture.addFileToProject("src-ui/pages/scopes.component.html", """
                @use "View" as view;
                @if (view.ready) {
                  @let branch = view.title;
                  @for (row in view.rows; track row.label) {
                    <p>{{ row.label }} {{ branch }}</p>
                  }
                  <p>{{ row.label }}</p>
                }
                <p>{{ branch }}</p>
                """);
        RustBindings bindings = RustBindings.forTemplate(html);
        String source = html.getText();
        assertNotNull(bindings.resolveAt("row.label", source.indexOf("{{ row.label }}")));
        assertNull(bindings.resolveAt("row.label", source.lastIndexOf("{{ row.label }}")));
        assertNotNull(bindings.resolveAt("branch", source.indexOf("{{ branch }}")));
        assertNull(bindings.resolveAt("branch", source.lastIndexOf("{{ branch }}")));
        TemplateIndex index = TemplateIndex.get(html);
        int outside = source.lastIndexOf("{{ branch }}") + 3;
        assertNull(index.symbolAt(TemplateScanner.tokenAt(source, outside)));
    }

    public void testProjectComponentTagIsKnownAndCompletes() {
        myFixture.addFileToProject("src-ui/components/app-header.component.html", "<header>Hi</header>");
        PsiFile html = myFixture.addFileToProject("src-ui/pages/page.component.html", "<app-header></app-header>");
        assertNotNull(((com.intellij.psi.xml.XmlFile) html).getRootTag().getDescriptor());
        myFixture.configureFromExistingVirtualFile(html.getVirtualFile());
        assertFalse(myFixture.doHighlighting().stream().anyMatch(info -> info.getDescription() != null
                && info.getDescription().contains("Cannot resolve symbol 'app-header'")));
        PsiFile index = myFixture.addFileToProject("src-ui/index.html", "<body><app-header></app-header></body>");
        assertNotNull(((com.intellij.psi.xml.XmlFile) index).getRootTag().getSubTags()[0].getDescriptor());
        PsiFile completion = myFixture.addFileToProject("src-ui/pages/completion.component.html", "<app-h");
        myFixture.configureFromExistingVirtualFile(completion.getVirtualFile());
        myFixture.getEditor().getCaretModel().moveToOffset(completion.getTextLength());
        LookupElement[] choices = myFixture.completeBasic();
        assertTrue(choices == null && myFixture.getFile().getText().contains("app-header")
                || choices != null && Arrays.stream(choices).anyMatch(item -> item.getLookupString().equals("app-header")));
    }

    public void testRegisteredHtmlFnIsColoredAsFunctionWithoutUnknownWarning() {
        myFixture.addFileToProject("src-ui/pages/events.component.rs", """
                /// Navigates to the landing page.
                #[html_fn("go_home")]
                fn navigate_home() {}
                """);
        PsiFile html = myFixture.addFileToProject("src-ui/pages/events.component.html",
                "<button onclick=\"go_home\">Home</button>");
        myFixture.configureFromExistingVirtualFile(html.getVirtualFile());
        int start = html.getText().indexOf("go_home");
        assertTrue(new TiltJsInspectionSuppressor().isSuppressedFor(
                html.findElementAt(start + 1), "JSUnresolvedReference"));
        assertFalse(new TiltJsInspectionSuppressor().isSuppressedFor(
                html.findElementAt(start + 1), "JSUnusedLocalSymbols"));
        var injected = com.intellij.lang.injection.InjectedLanguageManager.getInstance(getProject())
                .findInjectedElementAt(html, start + 1);
        if (injected != null)
            assertTrue(new TiltJsInspectionSuppressor().isSuppressedFor(injected, "JSUnresolvedReference"));
        var highlights = myFixture.doHighlighting();
        assertTrue(highlights.stream().anyMatch(info -> info.getStartOffset() == start
                && info.getEndOffset() == start + "go_home".length()
                && info.forcedTextAttributesKey == DefaultLanguageHighlighterColors.FUNCTION_CALL));
        assertFalse(highlights.stream().anyMatch(info -> info.getStartOffset() <= start
                && info.getEndOffset() >= start + "go_home".length()
                && info.getDescription() != null && info.getDescription().toLowerCase().contains("unresolved")));
    }

    public void testUnknownEventNamesPointToRustHandlerRegistration() {
        myFixture.enableInspections(new TiltBindingInspection());
        PsiFile html = myFixture.addFileToProject("src-ui/pages/missing.component.html",
                "<button onclick=\"go_missing\">Open</button>");
        myFixture.configureFromExistingVirtualFile(html.getVirtualFile());
        var problems = myFixture.doHighlighting().stream()
                .filter(info -> info.getDescription() != null).map(info -> info.getDescription()).toList();
        assertTrue(problems.toString(), problems.stream().anyMatch(message ->
                message.contains("Unknown TiltUI handler") && message.contains("#[html_fn")));
    }
    public void testCompanionRustBindingsNavigateFromHtml() {
        myFixture.addFileToProject("src-ui/pages/main.component.rs", """
                use tilt_ui::{html_fn, html_method, html_shared, UiStore};
                #[html_shared]
                #[derive(UiStore)]
                struct Profile { name: String, count: u32, address: Address }
                struct Address { city: String }
                #[html_fn("greet")]
                fn greet() {}
                #[html_method("main", "profile.full_name")]
                fn full_name(_: &serde_json::Value, _: &[serde_json::Value]) -> Option<serde_json::Value> { None }
                """);
        PsiFile html = myFixture.addFileToProject("src-ui/pages/main.component.html", """
                @if (profile.count > 0) {
                  <button onclick="greet">{{ profile.name }}</button>
                }
                """);
        RustBindings bindings = RustBindings.forTemplate(html);
        assertEquals("Profile", bindings.resolve("profile").type());
        assertEquals("String", bindings.resolve("profile.name").type());
        assertEquals("String", bindings.resolve("profile.address.city").type());
        assertEquals("Template method", bindings.resolve("profile.full_name").kind());
        assertTrue(bindings.members("profile").containsKey("full_name"));
        assertEquals("Event handler", bindings.resolve("greet").kind());

        int nameOffset = html.getText().indexOf("profile.name") + "profile.".length() + 1;
        PsiReference reference = html.findReferenceAt(nameOffset);
        assertNotNull("HTML field should resolve to its Rust declaration", reference);
        assertEquals("name", ((com.intellij.psi.PsiNamedElement) reference.resolve()).getName());
        int handlerOffset = html.getText().indexOf("greet") + 1;
        var navigation = new TiltGotoDeclarationHandler().getGotoDeclarationTargets(
                html.findElementAt(handlerOffset), handlerOffset, null);
        assertNotNull("HTML event should navigate to its Rust handler", navigation);
        assertEquals("greet", ((com.intellij.psi.PsiNamedElement) navigation[0]).getName());
        myFixture.configureFromExistingVirtualFile(html.getVirtualFile());
        assertTrue("Go to Declaration should include the Rust handler",
                Arrays.stream(GotoDeclarationAction.findAllTargetElements(getProject(), myFixture.getEditor(), handlerOffset))
                        .anyMatch(target -> target.equals(bindings.resolve("greet").target())));
        assertTrue("Find Usages should include the HTML handler",
                ReferencesSearch.search(bindings.resolve("greet").target()).findAll().stream()
                        .anyMatch(usage -> usage.resolve() != null));
        assertTrue("Find Usages should include the HTML reference",
                ReferencesSearch.search(bindings.resolve("profile.name").target()).findAll().stream()
                        .anyMatch(usage -> usage.getElement().getContainingFile().equals(html)));
    }

    public void testCompletesRustFieldsAndTiltKeywords() {
        myFixture.addFileToProject("main.component.rs", """
                #[html_shared]
                struct Profile { name: String, count: u32 }
                """);
        myFixture.configureByText("main.component.html", "<p>{{ profile.<caret> }}</p>");
        LookupElement[] fields = myFixture.completeBasic();
        assertNotNull(fields);
        assertTrue(Arrays.stream(fields).anyMatch(item -> item.getLookupString().equals("name")));

        myFixture.configureByText("other.component.html", "@<caret>");
        LookupElement[] keywords = myFixture.completeBasic();
        assertNotNull(keywords);
        assertTrue(Arrays.stream(keywords).anyMatch(item -> item.getLookupString().equals("if")));

        myFixture.configureByText("events.component.html", "<button onclick=\"$<caret>\"></button>");
        LookupElement[] actions = myFixture.completeBasic();
        assertNotNull(actions);
        assertTrue(Arrays.stream(actions).anyMatch(item -> item.getLookupString().equals("set")));
    }

    public void testHoverDocumentationForKeywordAndAction() {
        PsiFile html = myFixture.addFileToProject("docs.component.html", """
                @if (true) { <button onclick="$set(state.count, 1)">Go</button> }
                """);
        TiltDocumentationProvider docs = new TiltDocumentationProvider();
        int keyword = html.getText().indexOf("@if") + 1;
        var keywordElement = docs.getCustomDocumentationElement(null, html, html.findElementAt(keyword), keyword);
        assertNotNull(keywordElement);
        assertTrue(docs.generateHoverDoc(keywordElement, null).contains("condition"));
        int action = html.getText().indexOf("$set") + 1;
        var actionElement = docs.getCustomDocumentationElement(null, html, html.findElementAt(action), action);
        assertNotNull(actionElement);
        assertTrue(docs.generateHoverDoc(actionElement, null).contains("mutable UI store"));
    }

    public void testKeywordCompletionKeepsItsAtSign() {
        myFixture.configureByText("insert.component.html", "@i<caret>");
        LookupElement[] choices = myFixture.completeBasic();
        assertNotNull(choices);
        var keyword = Arrays.stream(choices).filter(item -> item.getLookupString().equals("if")).findFirst().orElseThrow();
        assertNotNull(myFixture.getLookup());
        myFixture.getLookup().setCurrentItem(keyword);
        myFixture.finishLookup(Lookup.NORMAL_SELECT_CHAR);
        assertEquals("@if", myFixture.getFile().getText());
    }

    public void testActionCompletionKeepsItsDollarSign() {
        myFixture.configureByText("insert-action.component.html", "<button onclick=\"$s<caret>\"></button>");
        LookupElement[] choices = myFixture.completeBasic();
        assertNotNull(choices);
        var action = Arrays.stream(choices).filter(item -> item.getLookupString().equals("set")).findFirst().orElseThrow();
        assertNotNull(myFixture.getLookup());
        myFixture.getLookup().setCurrentItem(action);
        myFixture.finishLookup(Lookup.NORMAL_SELECT_CHAR);
        assertEquals("<button onclick=\"$set\"></button>", myFixture.getFile().getText());
    }

    public void testDirectiveAndActionReceiveDistinctHighlighting() {
        String source = "@if (true) { <button onclick=\"$set(state.count, 1)\">Go</button> }";
        myFixture.configureByText("highlight.component.html", source);
        var highlights = myFixture.doHighlighting();
        assertTrue(highlights.stream().anyMatch(info -> info.getStartOffset() == 0
                && info.getEndOffset() == 3
                && info.forcedTextAttributesKey == DefaultLanguageHighlighterColors.KEYWORD));
        int action = source.indexOf("$set");
        assertTrue(highlights.stream().anyMatch(info -> info.getStartOffset() == action
                && info.getEndOffset() == action + 4
                && info.forcedTextAttributesKey == DefaultLanguageHighlighterColors.FUNCTION_CALL));
    }

    public void testUseFindsSharedStructAndEnumFromSrc() {
        myFixture.addFileToProject("src/model.rs", """
                #[html_shared]
                pub struct Profile { pub name: String, pub address: Address }
                pub struct Address { pub city: String }
                #[html_shared]
                pub enum Mode { Ready, Busy }
                struct Hidden { value: u32 }
                """);
        PsiFile html = myFixture.addFileToProject("src-ui/pages/preview.component.html", """
                @use "crate::model::Profile" as model;
                <p>{{ model.name }}</p>
                """);
        RustSourceCatalog catalog = RustSourceCatalog.get(getProject());
        assertNotNull(catalog.resolve("crate::model::Profile"));
        assertEquals("Rust enum", catalog.resolve("crate::model::Mode").kind());
        assertNull(catalog.resolve("Hidden"));
        RustBindings bindings = RustBindings.forTemplate(html);
        assertEquals("String", bindings.resolve("model.name").type());
        assertEquals("String", bindings.resolve("model.address.city").type());
        PsiFile wildcard = myFixture.addFileToProject("wildcard.component.html", """
                @use "crate::model::Profile";
                @use "crate::model::Profile" as *;
                <p>{{ Profile.name }} {{ name }}</p>
                """);
        RustBindings wildcardBindings = RustBindings.forTemplate(wildcard);
        assertEquals("String", wildcardBindings.resolve("Profile.name").type());
        assertEquals("String", wildcardBindings.resolve("name").type());

        int targetOffset = html.getText().indexOf("crate::model::Profile") + 8;
        PsiReference importReference = html.findReferenceAt(targetOffset);
        assertNotNull(importReference);
        assertEquals(catalog.resolve("crate::model::Profile").target(), importReference.resolve());
        myFixture.configureFromExistingVirtualFile(html.getVirtualFile());
        assertTrue(Arrays.stream(GotoDeclarationAction.findAllTargetElements(getProject(), myFixture.getEditor(), targetOffset))
                .anyMatch(target -> target.equals(catalog.resolve("crate::model::Profile").target())));
        assertTrue(ReferencesSearch.search(catalog.resolve("crate::model::Profile").target()).findAll().stream()
                .anyMatch(usage -> usage.getElement().getContainingFile().equals(html)));

        myFixture.configureByText("lookup.component.html", "@use \"crate::m<caret>\";");
        LookupElement[] choices = myFixture.completeBasic();
        assertNotNull(choices);
        assertTrue(Arrays.stream(choices).anyMatch(item -> item.getLookupString().equals("crate::model::Profile")));
        assertTrue(Arrays.stream(choices).anyMatch(item -> item.getLookupString().equals("crate::model::Mode")));

        myFixture.configureByText("alias.component.html", "@use \"crate::model::Profile\" as model;\n<p>{{ model.<caret> }}</p>");
        LookupElement[] fields = myFixture.completeBasic();
        assertNotNull(fields);
        assertTrue(Arrays.stream(fields).anyMatch(item -> item.getLookupString().equals("name")));
    }

    public void testBindingInspectionRecognizesPsiRegistrationsAndBuiltIns() {
        myFixture.enableInspections(new TiltBindingInspection());
        myFixture.addFileToProject("src-ui/pages/bindings.component.rs", """
                #[tilt_ui::html_shared]
                struct Profile { name: String, rows: Vec<Row> }
                struct Row { label: String }
                #[derive(tilt_ui::UiStore, tilt_ui::serde::Deserialize)]
                #[ui_store(mutable)]
                struct State { count: i32 }
                #[tilt_ui::html_fn("save")]
                fn persist() {}
                #[tilt_ui::html_method("bindings", "profile.full_name")]
                fn full_name() {}
                """);
        PsiFile html = myFixture.addFileToProject("src-ui/pages/bindings.component.html", """
                @use "Profile" as profile;
                @for (row in profile.rows; track row.label) {
                  <button onclick="save($event); $set(state.count, 1)">{{ row.label }} {{ i18n.save-label }} {{ profile.full_name() }}</button>
                }
                """);
        RustBindings bindings = RustBindings.forTemplate(html);
        assertTrue(bindings.mutableStoreRoot("state.count", html.getText().indexOf("$set")));
        assertEquals("Event handler", bindings.resolve("save").kind());
        assertEquals("Template method", bindings.resolve("profile.full_name").kind());
        myFixture.configureFromExistingVirtualFile(html.getVirtualFile());
        assertFalse(myFixture.doHighlighting().stream().anyMatch(info -> info.getDescription() != null
                && info.getDescription().startsWith("Unknown TiltUI")));
    }

    public void testBindingInspectionReportsUnknownNamesAndImmutableWrites() {
        myFixture.enableInspections(new TiltBindingInspection());
        myFixture.addFileToProject("src-ui/pages/invalid.component.rs", """
                #[html_shared]
                struct Profile { name: String }
                #[derive(UiStore)]
                struct State { count: i32 }
                """);
        PsiFile html = myFixture.addFileToProject("src-ui/pages/invalid.component.html", """
                <button onclick="missing($event); $set(state.count, 2); profile.nope()">{{ profile.nam }}</button>
                """);
        myFixture.configureFromExistingVirtualFile(html.getVirtualFile());
        var messages = myFixture.doHighlighting().stream().filter(info -> info.getDescription() != null)
                .map(info -> info.getDescription()).toList();
        assertTrue(messages.toString(), messages.stream().anyMatch(s -> s.contains("Unknown TiltUI handler") && s.contains("missing")));
        assertTrue(messages.toString(), messages.stream().anyMatch(s -> s.contains("Unknown TiltUI method") && s.contains("profile.nope")));
        assertTrue(messages.toString(), messages.stream().anyMatch(s -> s.contains("Unknown TiltUI field") && s.contains("nam")));
        assertTrue(messages.toString(), messages.stream().anyMatch(s -> s.contains("#[ui_store(mutable)]")));
    }

    public void testHandlerQuickFixAddsCompanionRegistration() {
        myFixture.enableInspections(new TiltBindingInspection());
        PsiFile rust = myFixture.addFileToProject("src-ui/pages/fix.component.rs", "// Component logic\n");
        PsiFile html = myFixture.addFileToProject("src-ui/pages/fix.component.html", "<button onclick=\"missing\">Run</button>");
        myFixture.configureFromExistingVirtualFile(html.getVirtualFile());
        myFixture.doHighlighting();
        myFixture.getEditor().getCaretModel().moveToOffset(html.getText().indexOf("missing") + 2);
        var fix = myFixture.findSingleIntention("Create TiltUI event handler 'missing'");
        assertNotNull(fix);
        myFixture.launchAction(fix);
        assertTrue(rust.getText(), rust.getText().contains("#[tilt_ui::html_fn(\"missing\")]"));
        assertFalse(myFixture.doHighlighting().stream().anyMatch(info -> info.getDescription() != null
                && info.getDescription().contains("Unknown TiltUI handler")));
    }

    public void testFieldQuickFixReplacesOnlyMisspelledMember() {
        myFixture.enableInspections(new TiltBindingInspection());
        myFixture.addFileToProject("src-ui/pages/field.component.rs", "#[html_shared] struct Profile { name: String }\n");
        PsiFile html = myFixture.addFileToProject("src-ui/pages/field.component.html", "<p>{{ profile.nam }}</p>");
        myFixture.configureFromExistingVirtualFile(html.getVirtualFile());
        myFixture.doHighlighting();
        myFixture.getEditor().getCaretModel().moveToOffset(html.getText().indexOf("nam") + 1);
        var fix = myFixture.findSingleIntention("Replace with 'name'");
        assertNotNull(fix);
        myFixture.launchAction(fix);
        assertEquals("<p>{{ profile.name }}</p>", myFixture.getFile().getText());
    }

    public void testMutableStoreQuickFixAddsRequiredAttributes() {
        myFixture.enableInspections(new TiltBindingInspection());
        PsiFile rust = myFixture.addFileToProject("src-ui/pages/store.component.rs", "#[derive(UiStore)]\nstruct State { count: i32 }\n");
        PsiFile html = myFixture.addFileToProject("src-ui/pages/store.component.html",
                "<button onclick=\"$add(state.count, 1)\">Add</button>");
        myFixture.configureFromExistingVirtualFile(html.getVirtualFile());
        myFixture.doHighlighting();
        myFixture.getEditor().getCaretModel().moveToOffset(html.getText().indexOf("state.count") + 2);
        var fix = myFixture.findSingleIntention("Make 'state' a mutable UI store");
        assertNotNull(fix);
        myFixture.launchAction(fix);
        assertTrue(rust.getText(), rust.getText().contains("#[ui_store(mutable)]"));
        assertTrue(rust.getText(), rust.getText().contains("#[derive(tilt_ui::serde::Deserialize)]"));
        assertTrue(RustBindings.forTemplate(html).mutableStoreRoot("state.count", 0));
    }

    public void testMethodQuickFixAddsRegistrationForCurrentComponent() {
        myFixture.enableInspections(new TiltBindingInspection());
        PsiFile rust = myFixture.addFileToProject("src-ui/pages/method.component.rs",
                "#[html_shared] struct Profile { name: String }\n");
        PsiFile html = myFixture.addFileToProject("src-ui/pages/method.component.html",
                "<p>{{ profile.full_name() }}</p>");
        myFixture.configureFromExistingVirtualFile(html.getVirtualFile());
        myFixture.doHighlighting();
        myFixture.getEditor().getCaretModel().moveToOffset(html.getText().indexOf("full_name") + 2);
        var fix = myFixture.findSingleIntention("Create TiltUI method 'profile.full_name'");
        assertNotNull(fix);
        myFixture.launchAction(fix);
        assertTrue(rust.getText(), rust.getText().contains("#[tilt_ui::html_method(\"method\", \"profile.full_name\")]"));
        assertEquals("Template method", RustBindings.forTemplate(html).resolve("profile.full_name").kind());
    }

    public void testHtmlUseImportAndFormActionResolveWithoutWarnings() {
        myFixture.enableInspections(new TiltBindingInspection());
        myFixture.addFileToProject("src/model.rs", "pub struct Profile { pub name: String }\n");
        myFixture.addFileToProject("src-ui/pages/form.component.rs", """
                #[html_use]
                use crate::model::Profile;
                #[html_fn("save")]
                fn save() {}
                """);
        PsiFile html = myFixture.addFileToProject("src-ui/pages/form.component.html",
                "<form action=\"save\"><p>{{ profile.name }}</p></form>");
        RustBindings bindings = RustBindings.forTemplate(html);
        assertNotNull(bindings.resolve("profile"));
        assertEquals("String", bindings.resolve("profile.name").type());
        myFixture.configureFromExistingVirtualFile(html.getVirtualFile());
        assertFalse(myFixture.doHighlighting().stream().anyMatch(info -> info.getDescription() != null
                && info.getDescription().startsWith("Unknown TiltUI")));
    }
}
