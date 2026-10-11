package com.tiltus.bevywizard.project;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

import java.util.List;

/** Exercises project diagnostics and focused fixes in a real IDE fixture. */
public final class TiltProjectInspectionTest extends BasePlatformTestCase {
    @Override protected void setUp() throws Exception {
        super.setUp();
        myFixture.enableInspections(new TiltProjectInspection());
    }

    public void testMissingEntryAndBuildScriptCanBeCreated() {
        PsiFile cargo = myFixture.addFileToProject("Cargo.toml", """
                [package]
                name = "example"
                version = "0.1.0"
                [dependencies]
                tilt-ui = "=0.1.0"
                """);
        myFixture.configureFromExistingVirtualFile(cargo.getVirtualFile());
        assertTrue(warnings().stream().anyMatch(message -> message.contains("src-ui/index.html is missing")));
        assertTrue(warnings().stream().anyMatch(message -> message.contains("build script build.rs is missing")));
        apply("Create src-ui/index.html");
        apply("Create build.rs");
        assertNotNull(myFixture.findFileInTempDir("src-ui/index.html"));
        assertNotNull(myFixture.findFileInTempDir("build.rs"));
        assertFalse(warnings().stream().anyMatch(message -> message.contains("is missing")));
    }

    public void testBrokenStylesheetLinkCanBeFixed() {
        myFixture.addFileToProject("Cargo.toml", "[dependencies]\ntilt-ui = \"=0.1.0\"\n");
        PsiFile index = myFixture.addFileToProject("src-ui/index.html", """
                <html><head><link href="styles/site.css" rel="stylesheet"></head><body></body></html>
                """);
        myFixture.configureFromExistingVirtualFile(index.getVirtualFile());
        assertTrue(warnings().stream().anyMatch(message -> message.contains("stylesheet is missing")));
        myFixture.getEditor().getCaretModel().moveToOffset(index.getText().indexOf("<link"));
        apply("Create src-ui/styles/site.css");
        assertNotNull(myFixture.findFileInTempDir("src-ui/styles/site.css"));
        assertFalse(warnings().stream().anyMatch(message -> message.contains("stylesheet is missing")));
    }

    public void testIncompleteComponentCanCreateMissingCompanions() {
        myFixture.addFileToProject("Cargo.toml", "[dependencies]\ntilt-ui = \"=0.1.0\"\n");
        PsiFile html = myFixture.addFileToProject("src-ui/pages/profile.component.html", "<div>Profile</div>\n");
        myFixture.configureFromExistingVirtualFile(html.getVirtualFile());
        assertTrue(warnings().stream().anyMatch(message ->
                message.contains("profile.component.css") && message.contains("profile.component.rs")));
        apply("Create src-ui/pages/profile.component.css");
        apply("Create src-ui/pages/profile.component.rs");
        assertNotNull(myFixture.findFileInTempDir("src-ui/pages/profile.component.css"));
        assertNotNull(myFixture.findFileInTempDir("src-ui/pages/profile.component.rs"));
        assertFalse(warnings().stream().anyMatch(message -> message.contains("component is incomplete")));
    }

    public void testIncorrectBuildScriptAndPluginOrderAreReported() {
        PsiFile cargo = myFixture.addFileToProject("Cargo.toml", "[dependencies]\ntilt-ui = \"=0.1.0\"\n");
        myFixture.addFileToProject("build.rs", "fn main() {}\n");
        myFixture.configureFromExistingVirtualFile(cargo.getVirtualFile());
        assertTrue(warnings().stream().anyMatch(message -> message.contains("does not call tilt_ui_build::build()")));
        PsiFile rust = myFixture.addFileToProject("src/main.rs", """
                fn main() {
                    App::new().add_plugins(DefaultPlugins).add_plugins(TiltUiPlugin::new(catalog())).run();
                }
                """);
        myFixture.configureFromExistingVirtualFile(rust.getVirtualFile());
        assertTrue(warnings().stream().anyMatch(message -> message.contains("before DefaultPlugins")));
    }

    public void testOrdinaryBevyProjectIsIgnored() {
        myFixture.addFileToProject("Cargo.toml", "[dependencies]\nbevy = \"0.19\"\n");
        PsiFile html = myFixture.addFileToProject("src-ui/pages/example.component.html", "<div/>\n");
        myFixture.configureFromExistingVirtualFile(html.getVirtualFile());
        assertFalse(warnings().stream().anyMatch(message -> message.contains("TiltUI component")));
    }

    private List<String> warnings() {
        return myFixture.doHighlighting().stream()
                .filter(info -> info.getSeverity().compareTo(HighlightSeverity.WARNING) >= 0)
                .map(info -> info.getDescription() == null ? "" : info.getDescription()).toList();
    }

    private void apply(String name) {
        IntentionAction action = myFixture.findSingleIntention(name);
        assertNotNull(action);
        myFixture.launchAction(action);
    }
}
