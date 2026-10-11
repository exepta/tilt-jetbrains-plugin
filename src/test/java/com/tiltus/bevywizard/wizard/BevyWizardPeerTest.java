package com.tiltus.bevywizard.wizard;

import com.tiltus.bevywizard.catalog.BevyRelease;
import com.tiltus.bevywizard.catalog.BevyCompatibility;
import com.tiltus.bevywizard.catalog.CrateAsset;
import com.tiltus.bevywizard.project.ProjectKind;
import com.tiltus.bevywizard.project.RustToolchains;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;

import org.junit.jupiter.api.Test;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class BevyWizardPeerTest {
    @Test void packageDropdownOffersSuggestionsAndAnOtherChoice() {
        CrateAsset guessed = new CrateAsset("Pretty_Plugin", "Pretty Plugin", "", "2D", List.of("^0.19"),
                "https://github.com/example/pretty-plugin", null, -1, -1, null, null);
        assertEquals(List.of("real_package", "other_package", "Other…"),
                BevyWizardPeer.packageNameChoices(guessed, List.of("real_package", "other_package")));
        assertEquals(List.of("Other…"), BevyWizardPeer.packageNameChoices(guessed, List.of()));
        CrateAsset verified = guessed.withIdentity("current_package", CrateAsset.IdentitySource.USER_PROVIDED);
        assertEquals(List.of("current_package", "other_package", "Other…"),
                BevyWizardPeer.packageNameChoices(verified, List.of("other_package", "current_package")));
    }

    @Test void crateCanBeCheckedBeforeMetadataLoadsAndUncheckedDuringVerification() throws Exception {
        CountDownLatch releaseCheck = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        try {
            SwingUtilities.invokeAndWait(() -> {
                try {
                    BevyWizardPeer peer = new BevyWizardPeer(false, (asset, bevyVersion) -> {
                        try { releaseCheck.await(); }
                        catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IOException(e); }
                        return Optional.of(asset.withMetadata("1.2.3", 10, 2, null, null));
                    });
                    CrateAsset pending = new CrateAsset("sample", "Sample", "A sample crate", "2D",
                            List.of("^0.19"), "https://crates.io/crates/sample", null, -1, -1, null, null);
                    peer.acceptCatalogAssets(List.of(pending));
                    @SuppressWarnings("unchecked")
                    JList<CrateAsset> results = (JList<CrateAsset>) field(peer, "catalogList");
                    results.setSize(500, 82);
                    JPanel renderer = (JPanel) results.getCellRenderer()
                            .getListCellRendererComponent(results, pending, 0, false, false);
                    assertTrue(findCheckBox(renderer).isEnabled());
                    JLabel title = findNamedLabel(renderer, "crate-title");
                    assertTrue(title.getText().contains("<u>Sample</u>"));
                    assertTrue(title.getToolTipText().contains("README.md"));
                    assertFalse(findNamedLabel(renderer, "crate-warning").isVisible());
                    assertEquals("", findNamedLabel(renderer, "crate-downloads").getText());
                    assertTrue(findNamedLabel(renderer, "crate-downloads").getIcon().getIconWidth() > 14);
                    assertTrue(findNamedLabel(renderer, "crate-supported-bevy").getText().contains("Bevy: 0.19"));
                    int warningX = 55 + results.getFontMetrics(UIManager.getFont("Label.font"))
                            .stringWidth("Sample") + 7;
                    assertNull(results.getToolTipText(mouseEvent(results, MouseEvent.MOUSE_MOVED,
                            warningX, 16)));
                    results.dispatchEvent(mouseEvent(results, MouseEvent.MOUSE_MOVED, 60, 16));
                    assertEquals(Cursor.HAND_CURSOR, results.getCursor().getType());
                    results.dispatchEvent(mouseEvent(results, MouseEvent.MOUSE_MOVED, 200, 50));
                    assertEquals(Cursor.DEFAULT_CURSOR, results.getCursor().getType());
                    results.dispatchEvent(mouseEvent(results, MouseEvent.MOUSE_CLICKED, 200, 50));
                    assertEquals("Selected crates (1)", ((JLabel) field(peer, "selectedCratesTitle")).getText());
                    assertTrue(peer.isBackgroundJobRunning());
                    assertTrue(peer.getSettings().crates().isEmpty());
                    results.dispatchEvent(mouseEvent(results, MouseEvent.MOUSE_CLICKED, 16, 16));
                    assertEquals("Selected crates (0)", ((JLabel) field(peer, "selectedCratesTitle")).getText());
                    assertFalse(peer.isBackgroundJobRunning());
                    results.dispatchEvent(mouseEvent(results, MouseEvent.MOUSE_CLICKED, 200, 16));
                    assertEquals("Selected crates (1)", ((JLabel) field(peer, "selectedCratesTitle")).getText());
                    results.dispatchEvent(mouseEvent(results, MouseEvent.MOUSE_CLICKED, 200, 16));
                    assertEquals("Selected crates (0)", ((JLabel) field(peer, "selectedCratesTitle")).getText());
                } catch (Throwable error) { failure.set(error); }
            });
        } finally {
            releaseCheck.countDown();
        }
        if (failure.get() != null) throw new AssertionError(failure.get());
    }

    @Test void gitignoreChoiceOnlyAppearsAfterInitializeGit() throws Exception {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            try {
                BevyWizardPeer peer = new BevyWizardPeer(false);
                JCheckBox initialize = (JCheckBox) field(peer, "initializeGit");
                JCheckBox ignore = (JCheckBox) field(peer, "includeGitignore");
                assertFalse(initialize.isSelected());
                assertFalse(ignore.isVisible());
                assertFalse(peer.getSettings().initializeGit());
                assertFalse(peer.getSettings().includeGitignore());
                initialize.doClick();
                assertTrue(ignore.isVisible());
                assertSame(initialize.getParent(), ignore.getParent());
                assertInstanceOf(BoxLayout.class, initialize.getParent().getLayout());
                assertEquals(initialize.getParent().getComponentZOrder(initialize) + 1,
                        ignore.getParent().getComponentZOrder(ignore));
                assertTrue(ignore.isSelected());
                assertTrue(peer.getSettings().initializeGit());
                assertTrue(peer.getSettings().includeGitignore());
                ignore.doClick();
                assertFalse(peer.getSettings().includeGitignore());
                initialize.doClick();
                assertFalse(ignore.isVisible());
            } catch (Throwable error) { failure.set(error); }
        });
        if (failure.get() != null) throw new AssertionError(failure.get());
    }

    @Test void wheelOverContentScrollsItsOwnViewport() throws Exception {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            try {
                VerticalScrollablePanel content = new VerticalScrollablePanel();
                JLabel firstRow = new JLabel("First row");
                content.add(firstRow);
                for (int i = 0; i < 30; i++) content.add(new JLabel("Row " + i));
                JScrollPane scroll = new JScrollPane(content);
                scroll.setSize(220, 100);
                scroll.doLayout();
                WizardWheelScrolling.install(scroll);
                assertTrue(scroll.getVerticalScrollBar().isVisible());
                MouseWheelEvent wheel = new MouseWheelEvent(firstRow, MouseWheelEvent.MOUSE_WHEEL,
                        System.currentTimeMillis(), 0, 8, 8, 0, false,
                        MouseWheelEvent.WHEEL_UNIT_SCROLL, 3, 2);
                firstRow.dispatchEvent(wheel);
                assertTrue(wheel.isConsumed());
                assertTrue(scroll.getVerticalScrollBar().getValue() > 0);
            } catch (Throwable error) { failure.set(error); }
        });
        if (failure.get() != null) throw new AssertionError(failure.get());
    }
    @Test void wheelPassesThroughNonScrollableInnerAreasAndAtTheirEdges() throws Exception {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            try {
                VerticalScrollablePanel outerContent = new VerticalScrollablePanel();
                VerticalScrollablePanel innerContent = new VerticalScrollablePanel();
                JLabel innerRow = new JLabel("Inner row");
                innerContent.add(innerRow);
                JScrollPane inner = new JScrollPane(innerContent);
                inner.setPreferredSize(new Dimension(180, 70));
                inner.setMaximumSize(new Dimension(Integer.MAX_VALUE, 70));
                outerContent.add(inner);
                for (int i = 0; i < 30; i++) outerContent.add(new JLabel("Outer row " + i));
                JScrollPane outer = new JScrollPane(outerContent);
                outer.setSize(220, 110);
                layoutTree(outer);
                WizardWheelScrolling.install(outer);
                WizardWheelScrolling.install(inner);
                assertTrue(outer.getVerticalScrollBar().isVisible());
                assertFalse(inner.getVerticalScrollBar().isVisible());
                MouseWheelEvent through = wheel(innerRow);
                innerRow.dispatchEvent(through);
                assertTrue(through.isConsumed());
                assertTrue(outer.getVerticalScrollBar().getValue() > 0);

                outer.getVerticalScrollBar().setValue(0);
                for (int i = 0; i < 30; i++) innerContent.add(new JLabel("Inner extra " + i));
                innerContent.revalidate();
                layoutTree(outer);
                WizardWheelScrolling.install(inner);
                assertTrue(inner.getVerticalScrollBar().isVisible());
                MouseWheelEvent inside = wheel(innerRow);
                innerRow.dispatchEvent(inside);
                assertTrue(inside.isConsumed());
                assertTrue(inner.getVerticalScrollBar().getValue() > 0);
                assertEquals(0, outer.getVerticalScrollBar().getValue());

                JScrollBar innerBar = inner.getVerticalScrollBar();
                innerBar.setValue(innerBar.getMaximum() - innerBar.getVisibleAmount());
                MouseWheelEvent edge = wheel(innerRow);
                innerRow.dispatchEvent(edge);
                assertTrue(edge.isConsumed());
                assertTrue(outer.getVerticalScrollBar().getValue() > 0);

                JScrollBar outerBar = outer.getVerticalScrollBar();
                outerBar.setValue(outerBar.getMaximum() - outerBar.getVisibleAmount());
                MouseWheelEvent exhausted = wheel(innerRow);
                innerRow.dispatchEvent(exhausted);
                assertFalse(exhausted.isConsumed(), "No container should block the wheel when none can scroll");
            } catch (Throwable error) { failure.set(error); }
        });
        if (failure.get() != null) throw new AssertionError(failure.get());
    }

    private static MouseWheelEvent wheel(Component source) {
        return new MouseWheelEvent(source, MouseWheelEvent.MOUSE_WHEEL, System.currentTimeMillis(),
                0, 8, 8, 0, false, MouseWheelEvent.WHEEL_UNIT_SCROLL, 3, 2);
    }
    @Test void featurePopupAndDependencyPageKeepTheirOwnVerticalScrollAreas() throws Exception {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            try {
                BevyWizardPeer peer = new BevyWizardPeer(false);
                JPanel root = (JPanel) peer.getComponent();
                assertTrue(((Scrollable) root).getScrollableTracksViewportWidth());
                JCheckBox tiltUi = (JCheckBox) field(peer, "tiltUi");
                tiltUi.doClick();
                JScrollPane features = (JScrollPane) field(peer, "featureScroll");
                JScrollPane catalog = (JScrollPane) field(peer, "catalogScroll");
                VerticalScrollablePanel featurePanel = (VerticalScrollablePanel) field(peer, "featurePanel");
                assertTrue(((JButton) field(peer, "configureFeatures")).isVisible());
                assertTrue(featurePanel.getPreferredSize().height > features.getPreferredSize().height);
                assertTrue(featurePanel.getScrollableTracksViewportWidth());
                assertEquals(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER, features.getHorizontalScrollBarPolicy());
                assertEquals(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER, catalog.getHorizontalScrollBarPolicy());
                ((JButton) field(peer, "next")).doClick();
                peer.acceptCatalogAssets(List.of(
                        new CrateAsset("bevy_pancam", "bevy_pancam", "A map-like panning and zooming camera controller for your game", "2D", List.of("^0.19"), "https://crates.io/crates/bevy_pancam", "0.19.0", 112000, 1200, null, null),
                        new CrateAsset("bevy_fog_of_war", "bevy_fog_of_war", "A 2D fog of war plugin for Bevy with flexible rendering options", "2D", List.of("^0.19.0"), "https://crates.io/crates/bevy_fog_of_war", "0.4.0", 2400, 100, null, null),
                        new CrateAsset("some_long_crate", "Some long crate title", "A longer description that should wrap within the available space and never require horizontal scrolling in the project wizard", "Asset Loading", List.of("^0.19"), "https://crates.io/crates/some_long_crate", "1.0.0", 100, 20, null, null)));
                root.setSize(540, 650);
                layoutTree(root);
                JList<?> catalogList = (JList<?>) field(peer, "catalogList");
                assertEquals(3, catalogList.getModel().getSize());
                assertTrue(catalogList.getHeight() > 0);
                assertTrue(catalog.getViewport().getHeight() >= 164,
                        "At least two crate rows must fit at 650px wizard height; actual: " + catalog.getViewport().getHeight());
                assertEquals(250, features.getPreferredSize().height);
                JButton filtersButton = (JButton) field(peer, "filtersButton");
                JTextField search = (JTextField) field(peer, "search");
                JList<?> categories = (JList<?>) field(peer, "categoryList");
                assertNotNull(filtersButton.getIcon());
                assertEquals(1, ((JPanel) field(peer, "filterPopup")).getComponentCount());
                assertEquals(search.getHeight(), filtersButton.getHeight());
                assertTrue(categories.getModel().getSize() >= 3);
                assertTrue(catalog.getWidth() <= root.getWidth());
                assertTrue(catalogList.getWidth() <= catalog.getViewport().getWidth());
            } catch (Throwable e) { failure.set(e); }
        });
        if (failure.get() != null) throw new AssertionError(failure.get());
    }

    @Test void releaseCandidatesRequireCheckboxAndStableRemainsDefault() throws Exception {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            try {
                BevyWizardPeer peer = new BevyWizardPeer(false);
                peer.acceptBevyVersions(List.of("0.20.0-rc.2", "0.19.1", "0.19.0"));
                @SuppressWarnings("unchecked")
                JComboBox<String> version = (JComboBox<String>) field(peer, "version");
                JCheckBox allowRc = (JCheckBox) field(peer, "allowRc");
                assertEquals("0.19.1", version.getSelectedItem());
                assertEquals(2, version.getItemCount());
                allowRc.doClick();
                assertEquals(3, version.getItemCount());
                version.setSelectedItem("0.20.0-rc.2");
                allowRc.doClick();
                assertEquals("0.19.1", version.getSelectedItem());
                assertEquals(2, version.getItemCount());
            } catch (Throwable e) { failure.set(e); }
        });
        if (failure.get() != null) throw new AssertionError(failure.get());
    }

    @Test void tiltUiVersionIsVisibleAndItsOwnRcChoiceReachesReview() throws Exception {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            try {
                BevyWizardPeer peer = new BevyWizardPeer(false);
                peer.acceptInstalledToolchains(List.of(new RustToolchains.Installed(
                        "1.95.0-aarch64-apple-darwin", "1.95.0", false)), null);
                peer.acceptTiltUiVersions(List.of("0.1.0", "0.1.0-rc.2", "0.2.0"));
                @SuppressWarnings("unchecked")
                JComboBox<String> tiltVersion = (JComboBox<String>) field(peer, "tiltUiVersion");
                JCheckBox allowTiltRc = (JCheckBox) field(peer, "allowTiltUiRc");
                JCheckBox allowBevyRc = (JCheckBox) field(peer, "allowRc");
                assertEquals("0.1.0", tiltVersion.getSelectedItem());
                assertEquals(1, tiltVersion.getItemCount());
                ((JCheckBox) field(peer, "tiltUi")).doClick();
                assertTrue(((JPanel) field(peer, "tiltUiVersionRow")).isVisible());
                assertTrue(((JLabel) field(peer, "tiltUiVersionStatus")).getText().contains("0.1.0"));
                allowTiltRc.doClick();
                assertEquals(2, tiltVersion.getItemCount());
                assertFalse(allowBevyRc.isSelected());
                tiltVersion.setSelectedItem("0.1.0-rc.2");
                assertEquals("0.1.0-rc.2", peer.getSettings().tiltUiVersion());
                JButton next = (JButton) field(peer, "next");
                next.doClick();
                next.doClick();
                assertNull(peer.validate());
                assertTrue(allLabels((Container) field(peer, "reviewPanel")).contains("0.1.0-rc.2"));
                ((JButton) field(peer, "back")).doClick();
                ((JButton) field(peer, "back")).doClick();
                allowTiltRc.doClick();
                assertEquals("0.1.0", tiltVersion.getSelectedItem());
                assertEquals("0.1.0", peer.getSettings().tiltUiVersion());
            } catch (Throwable error) { failure.set(error); }
        });
        if (failure.get() != null) throw new AssertionError(failure.get());
    }

    @Test void finalReviewSummarizesChoicesAndIsRequiredBeforeCreation() throws Exception {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            try {
                BevyWizardPeer peer = new BevyWizardPeer(false);
                peer.acceptInstalledToolchains(List.of(new RustToolchains.Installed(
                        "1.95.0-aarch64-apple-darwin", "1.95.0", false)), null);
                ((JCheckBox) field(peer, "tiltUi")).doClick();
                ((JCheckBox) field(peer, "useRouting")).doClick();
                ((JCheckBox) field(peer, "initializeGit")).doClick();
                @SuppressWarnings("unchecked")
                Map<String, JCheckBox> features = (Map<String, JCheckBox>) field(peer, "featureChecks");
                features.get("tilt-icons").doClick();
                assertNotNull(peer.validate());
                JButton next = (JButton) field(peer, "next");
                next.doClick();
                next.doClick();
                assertNull(peer.validate());
                String review = allLabels((Container) field(peer, "reviewPanel"));
                assertTrue(review.contains("Ready to create"));
                assertTrue(review.contains("Binary (App)"));
                assertTrue(review.contains("1.95.0"));
                assertTrue(review.contains("Home and About routes"));
                assertTrue(review.contains("tilt-icons"));
                assertTrue(review.contains("Initialize repository"));
                ((JButton) field(peer, "back")).doClick();
                assertNotNull(peer.validate());
            } catch (Throwable error) { failure.set(error); }
        });
        if (failure.get() != null) throw new AssertionError(failure.get());
    }

    @Test void dependencyPageFiltersAndSummarizesSelectedCrates() throws Exception {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            try {
                BevyWizardPeer peer = new BevyWizardPeer(false);
                peer.acceptInstalledToolchains(List.of(new RustToolchains.Installed(
                        "1.95.0-aarch64-apple-darwin", "1.95.0", false)), null);
                CrateAsset camera = new CrateAsset("bevy_pancam", "Bevy PanCam", "Camera pan and zoom", "2D",
                        List.of("^0.19"), "https://crates.io/crates/bevy_pancam", "0.19.0", 100, 20, null, null)
                        .withIdentity("bevy_pancam", CrateAsset.IdentitySource.CRATES_IO_LINK);
                CrateAsset audio = new CrateAsset("bevy_audio", "Bevy Audio", "Audio tools", "Audio",
                        List.of("^0.19"), "https://crates.io/crates/bevy_audio", "0.2.0", 50, 10, null, null);
                peer.acceptCatalogAssets(List.of(camera, audio));
                ((JButton) field(peer, "next")).doClick();
                @SuppressWarnings("unchecked")
                JList<String> categories = (JList<String>) field(peer, "categoryList");
                categories.setSelectedValue("2D", true);
                JList<?> results = (JList<?>) field(peer, "catalogList");
                assertEquals(1, results.getModel().getSize());
                peer.acceptCompatibleSelection(camera);
                assertEquals(1, peer.getSettings().crates().size());
                assertEquals("Selected crates (1)", ((JLabel) field(peer, "selectedCratesTitle")).getText());
                assertNotNull(findButton((Container) field(peer, "selectedCratesPanel"), "Change package…"));
                ((JButton) field(peer, "next")).doClick();
                assertNull(peer.validate());
                String review = allLabels((Container) field(peer, "reviewPanel"));
                assertTrue(review.contains("Bevy PanCam"));
                assertTrue(review.contains("v0.19.0"));
                assertTrue(review.contains("Cargo package"));
                assertTrue(review.contains("bevy_pancam"));
                assertTrue(review.contains("Bevy support"));
                assertTrue(review.contains("^0.19"));
                assertTrue(review.contains("Source"));
                assertTrue(review.contains("Name verified"));
                assertTrue(review.contains("crates.io link"));
                assertFalse(review.contains("Some crates are not listed correctly"));
                JPanel reviewPanel = (JPanel) field(peer, "reviewPanel");
                assertNull(findLabelWithTooltip(reviewPanel, "Some crates are not listed correctly"));
                JPanel details = findNamedPanel(reviewPanel, "crate-details");
                assertNotNull(details);
                assertFalse(details.isVisible());
                JLabel crateLink = findLabelWithTooltip(reviewPanel, "Bevy PanCam");
                assertNotNull(crateLink);
                assertEquals(Cursor.HAND_CURSOR, crateLink.getCursor().getType());
                assertTrue(crateLink.getToolTipText().contains("README"));
                JButton expand = findButton(reviewPanel, "▸");
                assertNotNull(expand);
                expand.doClick();
                assertTrue(details.isVisible());
                assertEquals("▾", expand.getText());
                expand.doClick();
                assertFalse(details.isVisible());
                JButton edit = findButton(reviewPanel, "Edit selection…");
                assertNotNull(edit);
                edit.doClick();
                assertTrue(((JLabel) field(peer, "stepTitle")).getText().contains("Dependencies"));
            } catch (Throwable error) { failure.set(error); }
        });
        if (failure.get() != null) throw new AssertionError(failure.get());
    }

    @Test void warningAppearsOnlyForCratesResolvedThroughPackageDropdown() throws Exception {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            try {
                BevyWizardPeer peer = new BevyWizardPeer(false);
                peer.acceptInstalledToolchains(List.of(new RustToolchains.Installed(
                        "1.95.0-aarch64-apple-darwin", "1.95.0", false)), null);
                CrateAsset automatic = new CrateAsset("direct", "Direct", "", "2D", List.of("^0.19"),
                        "https://crates.io/crates/direct", "1.0.0", 1, 1, null, null)
                        .withIdentity("direct", CrateAsset.IdentitySource.CRATES_IO_LINK);
                CrateAsset chosen = new CrateAsset("actual-package", "Needs a choice", "", "2D",
                        List.of("^0.19"), "https://github.com/example/package", "1.0.0", 1, 1, null, null)
                        .withIdentity("actual-package", CrateAsset.IdentitySource.USER_PROVIDED);
                peer.acceptCatalogAssets(List.of(automatic, chosen));
                @SuppressWarnings("unchecked")
                JList<CrateAsset> results = (JList<CrateAsset>) field(peer, "catalogList");
                JPanel renderer = (JPanel) results.getCellRenderer()
                        .getListCellRendererComponent(results, automatic, 0, false, false);
                assertFalse(findNamedLabel(renderer, "crate-warning").isVisible());
                renderer = (JPanel) results.getCellRenderer()
                        .getListCellRendererComponent(results, chosen, 1, false, false);
                assertTrue(findNamedLabel(renderer, "crate-warning").isVisible());
                peer.acceptCompatibleSelection(automatic);
                peer.acceptCompatibleSelection(chosen);
                JButton next = (JButton) field(peer, "next");
                next.doClick(); next.doClick();
                JPanel review = (JPanel) field(peer, "reviewPanel");
                assertEquals(1, countLabelsWithTooltip(review, "Some crates are not listed correctly"));
                assertFalse(allLabels(review).contains("Some crates are not listed correctly"));
            } catch (Throwable error) { failure.set(error); }
        });
        if (failure.get() != null) throw new AssertionError(failure.get());
    }

    @Test void distinctCatalogCardsCanShareARepositoryUrl() throws Exception {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            try {
                BevyWizardPeer peer = new BevyWizardPeer(false);
                String repository = "https://github.com/example/bevy-tools";
                CrateAsset first = new CrateAsset("first_tool", "First Tool", "", "2D", List.of("^0.19"),
                        repository, "1.0.0", 1, 1, repository, null);
                CrateAsset second = new CrateAsset("second_tool", "Second Tool", "", "2D", List.of("^0.19"),
                        repository, "1.0.0", 1, 1, repository, null);
                peer.acceptCatalogAssets(List.of(first, second));
                @SuppressWarnings("unchecked")
                JList<CrateAsset> results = (JList<CrateAsset>) field(peer, "catalogList");
                assertEquals(2, results.getModel().getSize());
                peer.acceptCompatibleSelection(first.withIdentity("first_tool", CrateAsset.IdentitySource.REPOSITORY_MATCH));
                assertEquals(2, results.getModel().getSize());
                assertEquals("Selected crates (1)", ((JLabel) field(peer, "selectedCratesTitle")).getText());
                assertEquals("second_tool", results.getModel().getElementAt(1).name());
            } catch (Throwable error) { failure.set(error); }
        });
        if (failure.get() != null) throw new AssertionError(failure.get());
    }

    @Test void libraryAndBlankCannotEnableTiltUi() throws Exception {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            try {
                BevyWizardPeer peer = new BevyWizardPeer(false);
                JCheckBox tiltUi = (JCheckBox) field(peer, "tiltUi");
                @SuppressWarnings("unchecked")
                JComboBox<ProjectKind> kind = (JComboBox<ProjectKind>) field(peer, "kind");
                tiltUi.doClick();
                kind.setSelectedItem(ProjectKind.LIBRARY);
                assertFalse(tiltUi.isEnabled());
                assertFalse(tiltUi.isSelected());
                assertFalse(((JButton) field(peer, "configureFeatures")).isVisible());
                assertFalse(peer.getSettings().tiltUi());
                kind.setSelectedItem(ProjectKind.BLANK);
                assertFalse(tiltUi.isEnabled());
                kind.setSelectedItem(ProjectKind.APP);
                assertTrue(tiltUi.isEnabled());
            } catch (Throwable e) { failure.set(e); }
        });
        if (failure.get() != null) throw new AssertionError(failure.get());
    }

    @Test void routingChoiceRequiresTiltUiAndAProjectWithAnApp() throws Exception {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            try {
                BevyWizardPeer peer = new BevyWizardPeer(false);
                JCheckBox tiltUi = (JCheckBox) field(peer, "tiltUi");
                JCheckBox routing = (JCheckBox) field(peer, "useRouting");
                @SuppressWarnings("unchecked")
                JComboBox<ProjectKind> kind = (JComboBox<ProjectKind>) field(peer, "kind");
                assertFalse(routing.getParent().isVisible());
                tiltUi.doClick();
                assertTrue(routing.getParent().isVisible());
                routing.doClick();
                assertTrue(peer.getSettings().useRouting());
                kind.setSelectedItem(ProjectKind.LIBRARY);
                assertFalse(peer.getSettings().useRouting());
                assertFalse(routing.getParent().isVisible());
            } catch (Throwable e) { failure.set(e); }
        });
        if (failure.get() != null) throw new AssertionError(failure.get());
    }

    @Test void justIsUncheckedByDefaultAndUnavailableForLibraryProjects() throws Exception {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            try {
                BevyWizardPeer peer = new BevyWizardPeer(false);
                JCheckBox just = (JCheckBox) field(peer, "useJust");
                JCheckBox git = (JCheckBox) field(peer, "initializeGit");
                @SuppressWarnings("unchecked")
                JComboBox<ProjectKind> kind = (JComboBox<ProjectKind>) field(peer, "kind");
                assertFalse(just.isSelected());
                assertSame(git.getParent(), just.getParent());
                assertFalse(peer.getSettings().useJust());
                just.doClick();
                assertTrue(peer.getSettings().useJust());
                kind.setSelectedItem(ProjectKind.LIBRARY);
                assertFalse(just.isEnabled());
                assertFalse(peer.getSettings().useJust());
            } catch (Throwable e) { failure.set(e); }
        });
        if (failure.get() != null) throw new AssertionError(failure.get());
    }

    @Test void reviewRechecksCreateButtonAfterWizardNavigation() throws Exception {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            try {
                BevyWizardPeer peer = new BevyWizardPeer(false);
                AtomicBoolean createEnabled = new AtomicBoolean();
                peer.getComponent(new TextFieldWithBrowseButton(),
                        () -> createEnabled.set(peer.validate() == null));
                peer.acceptInstalledToolchains(List.of(new RustToolchains.Installed(
                        "1.95.0-aarch64-apple-darwin", "1.95.0", false)), null);
                assertFalse(createEnabled.get());
                JButton next = (JButton) field(peer, "next");
                next.doClick();
                assertFalse(createEnabled.get());
                next.doClick();
                assertTrue(createEnabled.get(), "Review must enable RustRover's Create action");
                ((JButton) field(peer, "back")).doClick();
                assertFalse(createEnabled.get());
            } catch (Throwable error) { failure.set(error); }
        });
        if (failure.get() != null) throw new AssertionError(failure.get());
    }

    @Test void hotReloadIsDisabledForBothWasmProjectKinds() throws Exception {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            try {
                BevyWizardPeer peer = new BevyWizardPeer(false);
                ((JCheckBox) field(peer, "tiltUi")).doClick();
                @SuppressWarnings("unchecked")
                Map<String, JCheckBox> features = (Map<String, JCheckBox>) field(peer, "featureChecks");
                JCheckBox hotReload = features.get("hot-reload");
                @SuppressWarnings("unchecked")
                JComboBox<ProjectKind> kind = (JComboBox<ProjectKind>) field(peer, "kind");
                hotReload.doClick();
                assertTrue(hotReload.isSelected());
                kind.setSelectedItem(ProjectKind.WASM);
                assertFalse(hotReload.isEnabled());
                assertFalse(hotReload.isSelected());
                assertFalse(peer.getSettings().tiltFeatures().contains("hot-reload"));
                kind.setSelectedItem(ProjectKind.BOTH);
                assertFalse(hotReload.isEnabled());
                kind.setSelectedItem(ProjectKind.APP);
                assertTrue(hotReload.isEnabled());
            } catch (Throwable e) { failure.set(e); }
        });
        if (failure.get() != null) throw new AssertionError(failure.get());
    }

    @Test void selectsCompatibleInstalledToolchainAndRejectsOldRust() throws Exception {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            try {
                BevyWizardPeer peer = new BevyWizardPeer(false);
                var stable = new RustToolchains.Installed("stable-aarch64-apple-darwin", "1.85.0", true);
                var required = new RustToolchains.Installed("1.95.0-aarch64-apple-darwin", "1.95.0", false);
                peer.acceptInstalledToolchains(List.of(stable, required), null);
                @SuppressWarnings("unchecked")
                JComboBox<RustToolchains.Installed> choice = (JComboBox<RustToolchains.Installed>) field(peer, "rustToolchain");
                assertEquals(required, choice.getSelectedItem());
                ((JButton) field(peer, "next")).doClick();
                ((JButton) field(peer, "next")).doClick();
                assertNull(peer.validate());
                choice.setSelectedItem(stable);
                assertNotNull(peer.validate());
                assertTrue(((JButton) field(peer, "updateStable")).isVisible());
                choice.setSelectedItem(required);
                assertEquals("1.95.0", peer.getSettings().minimumRustVersion());
            } catch (Throwable e) { failure.set(e); }
        });
        if (failure.get() != null) throw new AssertionError(failure.get());
    }

    @Test void switchingBevyReleaseUpdatesRequiredRustAndSelection() throws Exception {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            try {
                BevyWizardPeer peer = new BevyWizardPeer(false);
                peer.acceptBevyReleases(List.of(new BevyRelease("0.20.0-rc.2", "1.96.0"),
                        new BevyRelease("0.19.1", "1.95.0")));
                var old = new RustToolchains.Installed("1.95.0-aarch64-apple-darwin", "1.95.0", true);
                var current = new RustToolchains.Installed("1.96.0-aarch64-apple-darwin", "1.96.0", false);
                peer.acceptInstalledToolchains(List.of(old, current), null);
                @SuppressWarnings("unchecked")
                JComboBox<String> releases = (JComboBox<String>) field(peer, "version");
                @SuppressWarnings("unchecked")
                JComboBox<RustToolchains.Installed> choice = (JComboBox<RustToolchains.Installed>) field(peer, "rustToolchain");
                assertEquals(old, choice.getSelectedItem());
                ((JCheckBox) field(peer, "allowRc")).doClick();
                releases.setSelectedItem("0.20.0-rc.2");
                assertEquals(current, choice.getSelectedItem());
                assertEquals("1.96.0", peer.getSettings().minimumRustVersion());
            } catch (Throwable e) { failure.set(e); }
        });
        if (failure.get() != null) throw new AssertionError(failure.get());
    }

    @Test void loadingCrateMetadataDoesNotReplaceOpenFilterModels() throws Exception {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            try {
                BevyWizardPeer peer = new BevyWizardPeer(false);
                CrateAsset pending = new CrateAsset("sample", "Sample", "A sample crate", "2D",
                        List.of("^0.19"), "https://crates.io/crates/sample", null, -1, -1, null, null);
                peer.acceptCatalogAssets(List.of(pending));
                JComboBox<?> filter = (JComboBox<?>) field(peer, "bevyTag");
                filter.setSelectedItem("Bevy 0.19");
                ComboBoxModel<?> originalModel = filter.getModel();
                peer.acceptCatalogAssets(List.of(pending.withMetadata("0.1.0", 100, 10, null, "2026-10-01")));
                assertSame(originalModel, filter.getModel());
                assertEquals("Bevy 0.19", filter.getSelectedItem());
            } catch (Throwable e) { failure.set(e); }
        });
        if (failure.get() != null) throw new AssertionError(failure.get());
    }

    @Test void groupedBevyFilterIncludesPatchTagsOnlyWhenReleaseIsCompatible() throws Exception {
        assertEquals("Bevy 0.19", BevyCompatibility.filterLabel("0.19"));
        assertEquals("Bevy 0.19", BevyCompatibility.filterLabel("0.19.1"));
        assertEquals("Bevy 0.19", BevyCompatibility.filterLabel("^0.19.1"));
        assertEquals("Bevy 0.20", BevyCompatibility.filterLabel("0.20"));
        assertEquals("Bevy 0.20.0-rc.1", BevyCompatibility.filterLabel("^0.20.0-rc.1"));
        AtomicReference<Throwable> failure = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            try {
                BevyWizardPeer peer = new BevyWizardPeer(false);
                List<CrateAsset> assets = List.of(
                        asset("base", "0.19"), asset("patch", "0.19.1"),
                        asset("caret", "^0.19.1"), asset("older", "=0.19.0"),
                        asset("next", "0.20"), asset("candidate", "^0.20.0-rc.1"));
                peer.acceptCatalogAssets(assets);
                @SuppressWarnings("unchecked")
                JComboBox<String> filter = (JComboBox<String>) field(peer, "bevyTag");
                assertEquals(1, java.util.stream.IntStream.range(0, filter.getItemCount())
                        .mapToObj(filter::getItemAt).filter("Bevy 0.19"::equals).count());
                assertTrue(java.util.stream.IntStream.range(0, filter.getItemCount())
                        .mapToObj(filter::getItemAt).toList().containsAll(List.of("Bevy 0.20", "Bevy 0.20.0-rc.1")));
                filter.setSelectedItem("Bevy 0.19");
                @SuppressWarnings("unchecked")
                JList<CrateAsset> results = (JList<CrateAsset>) field(peer, "catalogList");
                assertEquals(List.of("base", "caret", "patch"), java.util.stream.IntStream.range(0, results.getModel().getSize())
                        .mapToObj(i -> results.getModel().getElementAt(i).name()).sorted().toList());
            } catch (Throwable e) { failure.set(e); }
        });
        if (failure.get() != null) throw new AssertionError(failure.get());
    }

    @Test void cardKeepsShortDescriptionAndCompactRightAlignedStats() throws Exception {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            try {
                BevyWizardPeer peer = new BevyWizardPeer(false);
                CrateAsset asset = new CrateAsset("sample", "Sample", "A crate with a description that wraps on narrow wizard panels.",
                        "2D", List.of("^0.19", "0.19.1", "^0.19.2", "^0.20.0-rc.1"),
                        "https://crates.io/crates/sample", "1.2.3", 1234567, 100, null, null);
                peer.acceptCatalogAssets(List.of(asset));
                @SuppressWarnings("unchecked")
                JList<CrateAsset> list = (JList<CrateAsset>) field(peer, "catalogList");
                list.setSize(500, 200);
                JPanel card = (JPanel) list.getCellRenderer().getListCellRendererComponent(list, asset, 0, false, false);
                int wideHeight = card.getPreferredSize().height;
                int wideRowHeight = list.getCellBounds(0, 0).height;
                list.setSize(235, 200);
                list.dispatchEvent(new java.awt.event.ComponentEvent(list, java.awt.event.ComponentEvent.COMPONENT_RESIZED));
                card = (JPanel) list.getCellRenderer().getListCellRendererComponent(list, asset, 0, false, false);
                assertEquals(-1, list.getFixedCellHeight());
                assertTrue(card.getPreferredSize().height > wideHeight);
                assertTrue(list.getCellBounds(0, 0).height > wideRowHeight,
                        "List rows must grow with wrapped card content");
                assertTrue(findNamedLabel(card, "crate-downloads").getText()
                        .contains(String.format("%,d", 1234567)));
                assertNotNull(findNamedLabel(card, "crate-downloads").getIcon());
                String support = findNamedLabel(card, "crate-supported-bevy").getText();
                assertTrue(support.contains("Bevy: 0.19") && support.contains("0.20.0-rc.1"), support);
                assertFalse(support.contains("^0.19"));
                assertFalse(support.contains("…"));
                JLabel shortText = findNamedLabel(card, "crate-description");
                assertNotNull(shortText);
                assertTrue(shortText.getText().split("<br>", -1).length <= 2);
                assertTrue(shortText.getText().contains("…"));
                card.setSize(235, card.getPreferredSize().height);
                layoutTree(card);
                JLabel title = findNamedLabel(card, "crate-title");
                Point titlePoint = SwingUtilities.convertPoint(title, 0, 0, card);
                Point descriptionPoint = SwingUtilities.convertPoint(shortText, 0, 0, card);
                JLabel bevy = findNamedLabel(card, "crate-supported-bevy");
                Point bevyRight = SwingUtilities.convertPoint(bevy, bevy.getWidth(), 0, card);
                JLabel downloads = findNamedLabel(card, "crate-downloads");
                Point downloadsLeft = SwingUtilities.convertPoint(downloads, 0, 0, card);
                Point downloadsRight = SwingUtilities.convertPoint(downloads, downloads.getWidth(), 0, card);
                JPanel surface = (JPanel) card.getComponent(0);
                JPanel body = (JPanel) surface.getComponent(1);
                Point bodyRight = SwingUtilities.convertPoint(body, body.getWidth(), 0, card);
                assertEquals(titlePoint.x, descriptionPoint.x);
                assertTrue(titlePoint.y >= 15, "The title needs breathing room above it");
                assertEquals(bevyRight.y, downloadsLeft.y, "Statistics belong in one footer row");
                assertTrue(bevyRight.x < downloadsLeft.x, "Downloads belong to the right of Bevy support");
                assertEquals(bodyRight.x, downloadsRight.x);
                assertTrue(surface.isOpaque());
                assertInstanceOf(javax.swing.border.CompoundBorder.class, surface.getBorder());
                assertTrue(surface.getX() > 0, "Cards need visible spacing around their borders");
                CrateAsset pending = new CrateAsset("pending", "Pending", "", "2D", List.of(),
                        "https://crates.io/crates/pending", null, -1, -1, null, null);
                card = (JPanel) list.getCellRenderer().getListCellRendererComponent(list, pending, 0, false, false);
                assertEquals("", findNamedLabel(card, "crate-downloads").getText());
                assertTrue(findNamedLabel(card, "crate-supported-bevy").getText().contains("Bevy: -"));
            } catch (Throwable e) { failure.set(e); }
        });
        if (failure.get() != null) throw new AssertionError(failure.get());
    }

    @Test void laterCratesIoMetadataUpdatesAnAlreadySelectedCard() throws Exception {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            try {
                BevyWizardPeer peer = new BevyWizardPeer(false);
                CrateAsset pending = new CrateAsset("sample", "Sample", "", "2D", List.of("^0.19"),
                        "https://crates.io/crates/sample", null, -1, -1, null, null);
                peer.acceptCatalogAssets(List.of(pending));
                CrateAsset selected = pending.withIdentity("sample", CrateAsset.IdentitySource.CRATES_IO_LINK)
                        .withMetadata("1.2.3", -1, -1, null, null);
                peer.acceptCompatibleSelection(selected);
                peer.acceptCatalogAssets(List.of(pending.withMetadata("1.3.0", 9876, 120, null, "2026-10-01")));
                @SuppressWarnings("unchecked")
                JList<CrateAsset> list = (JList<CrateAsset>) field(peer, "catalogList");
                CrateAsset updated = list.getModel().getElementAt(0);
                assertEquals("1.2.3", updated.version());
                assertEquals(9876, updated.downloads());
                JPanel card = (JPanel) list.getCellRenderer().getListCellRendererComponent(list, updated, 0, false, false);
                assertTrue(findNamedLabel(card, "crate-downloads").getText()
                        .contains(String.format("%,d", 9876)));
            } catch (Throwable e) { failure.set(e); }
        });
        if (failure.get() != null) throw new AssertionError(failure.get());
    }

    @Test void pendingDownloadIconRotatesThenFallsBackToDashWhenUnavailable() throws Exception {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            try {
                BevyWizardPeer peer = new BevyWizardPeer(false);
                CrateAsset pending = new CrateAsset("pending", "Pending", "", "2D", List.of("^0.19"),
                        "https://crates.io/crates/pending", null, -1, -1, null, null);
                peer.acceptCatalogAssets(List.of(pending));
                @SuppressWarnings("unchecked")
                JList<CrateAsset> list = (JList<CrateAsset>) field(peer, "catalogList");
                JPanel card = (JPanel) list.getCellRenderer().getListCellRendererComponent(list, pending, 0, false, false);
                JLabel count = findNamedLabel(card, "crate-downloads");
                assertEquals("", count.getText());
                assertEquals(32, count.getIcon().getIconWidth());
                int[] first = iconPixels(count);
                Field frame = peer.getClass().getDeclaredField("downloadSpinnerFrame");
                frame.setAccessible(true);
                frame.setInt(peer, 4);
                assertFalse(java.util.Arrays.equals(first, iconPixels(count)), "The spinner should visibly rotate");
                Field loaded = peer.getClass().getDeclaredField("catalogLoaded");
                loaded.setAccessible(true);
                loaded.setBoolean(peer, true);
                list.getCellRenderer().getListCellRendererComponent(list, pending, 0, false, false);
                assertEquals("-", count.getText());
                assertEquals(14, count.getIcon().getIconWidth());
            } catch (Throwable error) { failure.set(error); }
        });
        if (failure.get() != null) throw new AssertionError(failure.get());
    }

    private static int[] iconPixels(JLabel label) {
        Icon icon = label.getIcon();
        java.awt.image.BufferedImage image = new java.awt.image.BufferedImage(
                icon.getIconWidth(), icon.getIconHeight(), java.awt.image.BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        icon.paintIcon(label, graphics, 0, 0);
        graphics.dispose();
        return image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth());
    }

    private static CrateAsset asset(String name, String tag) {
        return new CrateAsset(name, name, "", "2D", List.of(tag),
                "https://crates.io/crates/" + name, "1.0.0", 10, 1, null, null);
    }

    private static Object field(Object object, String name) throws Exception {
        Field field = object.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(object);
    }

    private static JLabel findNamedLabel(Container container, String name) {
        for (Component child : container.getComponents()) {
            if (child instanceof JLabel label && name.equals(label.getName())) return label;
            if (child instanceof Container nested) {
                JLabel result = findNamedLabel(nested, name);
                if (result != null) return result;
            }
        }
        return null;
    }

    private static JCheckBox findCheckBox(Container container) {
        for (Component child : container.getComponents()) {
            if (child instanceof JCheckBox box) return box;
            if (child instanceof Container nested) {
                JCheckBox result = findCheckBox(nested);
                if (result != null) return result;
            }
        }
        return null;
    }

    private static MouseEvent mouseEvent(JList<?> list, int id, int x, int y) {
        return new MouseEvent(list, id, System.currentTimeMillis(), 0, x, y,
                id == MouseEvent.MOUSE_CLICKED ? 1 : 0, false);
    }

    private static String allLabels(Container container) {
        StringBuilder text = new StringBuilder();
        for (Component child : container.getComponents()) {
            if (child instanceof JLabel label) text.append(label.getText()).append('\n');
            if (child instanceof Container nested) text.append(allLabels(nested));
        }
        return text.toString();
    }

    private static JPanel findNamedPanel(Container container, String name) {
        for (Component child : container.getComponents()) {
            if (child instanceof JPanel panel && name.equals(panel.getName())) return panel;
            if (child instanceof Container nested) {
                JPanel result = findNamedPanel(nested, name);
                if (result != null) return result;
            }
        }
        return null;
    }

    private static JLabel findLabelWithTooltip(Container container, String title) {
        for (Component child : container.getComponents()) {
            if (child instanceof JLabel label && label.getToolTipText() != null
                    && label.getToolTipText().contains(title)) return label;
            if (child instanceof Container nested) {
                JLabel result = findLabelWithTooltip(nested, title);
                if (result != null) return result;
            }
        }
        return null;
    }

    private static int countLabelsWithTooltip(Container container, String phrase) {
        int count = 0;
        for (Component child : container.getComponents()) {
            if (child instanceof JLabel label && label.getToolTipText() != null
                    && label.getToolTipText().contains(phrase)) count++;
            if (child instanceof Container nested) count += countLabelsWithTooltip(nested, phrase);
        }
        return count;
    }

    private static JButton findButton(Container container, String title) {
        for (Component child : container.getComponents()) {
            if (child instanceof JButton button && title.equals(button.getText())) return button;
            if (child instanceof Container nested) {
                JButton result = findButton(nested, title);
                if (result != null) return result;
            }
        }
        return null;
    }

    private static void layoutTree(Container container) {
        container.doLayout();
        for (Component child : container.getComponents()) {
            if (child instanceof Container nested) layoutTree(nested);
        }
    }
}
