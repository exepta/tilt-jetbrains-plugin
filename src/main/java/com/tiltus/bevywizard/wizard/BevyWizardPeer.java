package com.tiltus.bevywizard.wizard;

import com.tiltus.bevywizard.catalog.BevyCompatibility;
import com.tiltus.bevywizard.catalog.BevyRelease;
import com.tiltus.bevywizard.catalog.CatalogClient;
import com.tiltus.bevywizard.catalog.CrateAsset;
import com.tiltus.bevywizard.project.BevySettings;
import com.tiltus.bevywizard.project.JustTools;
import com.tiltus.bevywizard.project.ProjectKind;
import com.tiltus.bevywizard.project.RustToolchains;
import com.tiltus.bevywizard.project.TiltUiCompatibility;

import com.intellij.icons.AllIcons;
import com.intellij.ide.util.projectWizard.SettingsStep;
import com.intellij.ide.util.projectWizard.ModuleNameLocationSettings;
import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.platform.ProjectGeneratorPeer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.HierarchyEvent;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/** Collects Bevy project options and manages asynchronous catalog data. */
public final class BevyWizardPeer implements ProjectGeneratorPeer<BevySettings> {
    private static final int CARD_TITLE_X = 55;
    private static final int CARD_TITLE_Y = 15;
    private static final String CRATE_WARNING = "<html><body style='width:320px'>Some crates are not listed correctly "
            + "in Bevy Assets. They may add incorrect crate names or versions to Cargo.toml. "
            + "Please note that this feature relies on the Bevy Assets catalog and is not perfect.</body></html>";
    private static final Icon DOWNLOAD_ICON = new Icon() {
        @Override public int getIconWidth() { return 14; }
        @Override public int getIconHeight() { return 14; }
        @Override public void paintIcon(Component component, Graphics graphics, int x, int y) {
            Graphics2D pen = (Graphics2D) graphics.create();
            try {
                pen.setColor(component.getForeground());
                pen.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                pen.drawLine(x + 7, y + 1, x + 7, y + 8);
                pen.drawLine(x + 4, y + 6, x + 7, y + 9);
                pen.drawLine(x + 7, y + 9, x + 10, y + 6);
                pen.drawLine(x + 2, y + 10, x + 2, y + 12);
                pen.drawLine(x + 2, y + 12, x + 12, y + 12);
                pen.drawLine(x + 12, y + 12, x + 12, y + 10);
            } finally { pen.dispose(); }
        }
    };
    private final WizardRootPanel root = new WizardRootPanel();
    private final JComboBox<String> version = new JComboBox<>(new String[]{TiltUiCompatibility.TESTED_BEVY});
    private final JCheckBox allowRc = new JCheckBox("Allow release candidates (RC)");
    private final JComboBox<RustToolchains.Installed> rustToolchain = new JComboBox<>();
    /** Creates a secondary explanatory label. */
    private final JLabel rustRequirement = muted("Bevy " + TiltUiCompatibility.TESTED_BEVY + " requires Rust "
            + TiltUiCompatibility.BEVY_MINIMUM_RUST + " or newer.");
    /** Creates a secondary explanatory label. */
    private final JLabel rustStatus = muted("Checking installed Rust toolchains…");
    private final JButton installRust = new JButton("Install Rust " + TiltUiCompatibility.BEVY_MINIMUM_RUST);
    private final JButton updateStable = new JButton("Update stable");
    private final JComboBox<ProjectKind> kind = new JComboBox<>(ProjectKind.values());
    private final JCheckBox useJust = new JCheckBox("Use Just");
    private final JCheckBox initializeGit = new JCheckBox("Create Git Repository");
    private final JCheckBox includeGitignore = new JCheckBox("Include .gitignore", true);
    private final JLabel justStatus = muted("Checking for Just…");
    private final JComboBox<String> justChoice = new JComboBox<>(new String[]{"Install Just", "Create justfile only"});
    private final JButton installJust = new JButton("Install Just");
    private final JPanel justOptions = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
    /** Creates a secondary explanatory label. */
    private final JLabel trunkTip = muted("WASM projects use Trunk for the browser build.");
    private final JCheckBox tiltUi = new JCheckBox("Use TiltUI");
    private final JComboBox<String> tiltUiVersion = new JComboBox<>(new String[]{TiltUiCompatibility.VERSION});
    private final JPanel tiltUiVersionRow = row("TiltUI version", tiltUiVersion);
    private final JCheckBox allowTiltUiRc = new JCheckBox("Allow TiltUI release candidates (RC)");
    private final JLabel tiltUiVersionStatus = muted("TiltUI " + TiltUiCompatibility.VERSION
            + " supports stable Bevy " + TiltUiCompatibility.BEVY_SERIES + ".x.");
    private final JCheckBox useRouting = new JCheckBox("Use Routing");
    private final JPanel routingRow = new JPanel(new BorderLayout(8, 0));
    private final Map<String, JCheckBox> featureChecks = new LinkedHashMap<>();
    private final VerticalScrollablePanel featurePanel = new VerticalScrollablePanel();
    private final JScrollPane featureScroll = new JScrollPane(featurePanel,
            ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
    private final JButton configureFeatures = new JButton("Choose TiltUI features…");
    private final JLabel featureSummary = muted("Components, HTML and CSS are included.");
    private final CardLayout pageLayout = new CardLayout();
    private final JPanel pages = new JPanel(pageLayout);
    private final JButton back = new JButton("Back");
    private final JButton next = new JButton("Next: Dependencies");
    private final JLabel stepTitle = new JLabel("1  Project");
    private final JPanel reviewPanel = new JPanel();
    private final JScrollPane reviewScroll = new JScrollPane(reviewPanel,
            ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
    private int currentPage;
    private final JPanel catalogPanel = new JPanel(new BorderLayout(0, 8));
    private final DefaultListModel<String> categoryModel = new DefaultListModel<>();
    private final JList<String> categoryList = new JList<>(categoryModel);
    private final VerticalScrollablePanel selectedCratesPanel = new VerticalScrollablePanel();
    private final JScrollPane selectedScroll = new JScrollPane(selectedCratesPanel,
            ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
    private final JLabel selectedCratesTitle = new JLabel("Selected crates (0)");
    private final JTextField search = new JTextField();
    private final JButton filtersButton = new JButton(AllIcons.General.Filter);
    private final JPanel filterPopup = new JPanel(new BorderLayout());
    private final JComboBox<String> category = new JComboBox<>(new String[]{"All categories"});
    private final JComboBox<String> bevyTag = new JComboBox<>(new String[]{"All Bevy tags"});
    private final JComboBox<String> sort = new JComboBox<>(new String[]{"Title", "Recently updated", "Most downloads", "Recent downloads"});
    /** Creates a secondary explanatory label. */
    private final JLabel catalogStatus = muted("Open Dependencies to load the Bevy asset catalog.");
    private final DefaultListModel<CrateAsset> listModel = new DefaultListModel<>();
    private final JList<CrateAsset> catalogList = new JList<>(listModel) {
        @Override public boolean getScrollableTracksViewportWidth() { return true; }
        @Override public String getToolTipText(MouseEvent event) {
            int index = locationToIndex(event.getPoint());
            Rectangle bounds = index < 0 ? null : getCellBounds(index, index);
            if (bounds == null || !bounds.contains(event.getPoint())) return null;
            FontMetrics metrics = getFontMetrics(UIManager.getFont("Label.font"));
            int iconX = bounds.x + CARD_TITLE_X + metrics.stringWidth(listModel.get(index).title()) + 5;
            return hasPackageChoiceWarning(listModel.get(index))
                    && event.getX() >= iconX && event.getX() < iconX + 20
                    && event.getY() >= bounds.y + CARD_TITLE_Y && event.getY() < bounds.y + CARD_TITLE_Y + 22
                    ? CRATE_WARNING : null;
        }
    };
    private int downloadSpinnerFrame;
    private final Timer downloadSpinner = new Timer(90, event -> {
        downloadSpinnerFrame = (downloadSpinnerFrame + 1) % 12;
        catalogList.repaint();
    });
    private final Icon loadingDownloadIcon = new Icon() {
        @Override public int getIconWidth() { return 32; }
        @Override public int getIconHeight() { return 14; }
        @Override public void paintIcon(Component component, Graphics graphics, int x, int y) {
            DOWNLOAD_ICON.paintIcon(component, graphics, x, y);
            Graphics2D pen = (Graphics2D) graphics.create();
            try {
                pen.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                pen.setColor(linkColor());
                pen.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                pen.drawArc(x + 19, y + 2, 10, 10, downloadSpinnerFrame * 30, 260);
            } finally { pen.dispose(); }
        }
    };
    private final JScrollPane catalogScroll = new JScrollPane(catalogList,
            ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
    private final Map<String, CrateAsset> allAssets = new LinkedHashMap<>();
    private final Set<String> needsPackageChoice = new LinkedHashSet<>();
    private final Set<String> selected = new LinkedHashSet<>();
    private final Map<String, Integer> resolvingCrates = new LinkedHashMap<>();
    private int selectionRequest;
    private final Map<String, CrateAsset> resolvedCrates = new LinkedHashMap<>();
    /** Distinguishes separate Bevy Assets cards that link to the same repository. */
    private static String assetKey(CrateAsset asset) {
        return asset.url() + "\u0000" + asset.category() + "\u0000" + asset.title();
    }
    private String catalogBevyVersion = TiltUiCompatibility.TESTED_BEVY;
    private boolean catalogLoading;
    private boolean catalogLoaded;
    private boolean catalogFailed;
    private boolean filtersInitialized;
    private List<String> availableBevyVersions = List.of(TiltUiCompatibility.TESTED_BEVY);
    private List<String> availableTiltUiVersions = TiltUiCompatibility.SUPPORTED_VERSIONS;
    private boolean pendingTiltUiVersionChoices;
    private final Map<String, String> bevyRustMinimums = new LinkedHashMap<>(Map.of(
            TiltUiCompatibility.TESTED_BEVY, TiltUiCompatibility.BEVY_MINIMUM_RUST));
    private List<RustToolchains.Installed> installedToolchains = List.of();
    private boolean rustupBusy;
    private String rustupError;
    private boolean pendingVersionChoices;
    private boolean justChecked;
    private boolean justBusy;
    private String justVersion;
    private String justError;
    private final boolean fetchNetwork;
    private final CrateResolver crateResolver;
    private ModuleNameLocationSettings nameLocation;
    private WizardContext wizardContext;
    private Runnable checkValid;

    /** Creates the wizard and starts loading available releases and toolchains. */
    public BevyWizardPeer() { this(true); }

    /** Creates the wizard with optional network loading for tests. */
    BevyWizardPeer(boolean fetchVersions) { this(fetchVersions, (asset, bevyVersion) -> {
        Optional<CrateAsset> identified = CatalogClient.identify(asset);
        if (identified.isEmpty()) {
            List<String> suggestions;
            try { suggestions = CatalogClient.suggestCrateNames(asset); }
            catch (IOException ignored) { suggestions = List.of(); }
            throw new UnverifiedCrateName(suggestions);
        }
        CrateAsset verified = identified.get();
        return CatalogClient.compatibleRelease(verified, bevyVersion);
    }); }

    private static final class UnverifiedCrateName extends IOException {
        private final List<String> suggestions;
        private UnverifiedCrateName(List<String> suggestions) { this.suggestions = suggestions; }
    }

    @FunctionalInterface
    interface CrateResolver {
        Optional<CrateAsset> resolve(CrateAsset asset, String bevyVersion) throws IOException;
    }

    BevyWizardPeer(boolean fetchVersions, CrateResolver crateResolver) {
        fetchNetwork = fetchVersions;
        this.crateResolver = crateResolver;
        version.setEditable(false);
        version.setPrototypeDisplayValue("0.20.0-rc.2");
        category.setPrototypeDisplayValue("Character controllers");
        bevyTag.setPrototypeDisplayValue("Bevy 0.20.0-rc.1");
        sort.setPrototypeDisplayValue("Recent downloads");
        root.setBorder(new EmptyBorder(10, 12, 10, 12));
        root.setPreferredSize(new Dimension(690, 710));
        VerticalScrollablePanel form = new VerticalScrollablePanel();
        form.setBorder(new EmptyBorder(8, 10, 12, 10));
        allowRc.setAlignmentX(Component.LEFT_ALIGNMENT);
        allowRc.setBorder(new EmptyBorder(0, 122, 0, 0));
        tiltUi.setAlignmentX(Component.LEFT_ALIGNMENT);
        allowTiltUiRc.setAlignmentX(Component.LEFT_ALIGNMENT);
        allowTiltUiRc.setBorder(new EmptyBorder(0, 122, 0, 0));
        useRouting.setAlignmentX(Component.LEFT_ALIGNMENT);
        useJust.setAlignmentX(Component.LEFT_ALIGNMENT);
        initializeGit.setAlignmentX(Component.LEFT_ALIGNMENT);
        includeGitignore.setAlignmentX(Component.LEFT_ALIGNMENT);
        form.add(sectionHeader("Project setup", "Choose the Bevy release, Rust toolchain and project type."));
        form.add(gap(10));
        form.add(row("Bevy version", version));
        form.add(allowRc);
        form.add(gap(8));
        form.add(row("Rust toolchain", rustToolchain));
        form.add(rustRequirement);
        form.add(rustStatus);
        JPanel rustActions = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        rustActions.setAlignmentX(Component.LEFT_ALIGNMENT);
        rustActions.add(installRust);
        rustActions.add(updateStable);
        form.add(rustActions);
        form.add(gap(8));
        JPanel projectType = new JPanel(new BorderLayout(8, 0));
        projectType.add(kind, BorderLayout.CENTER);
        form.add(row("Project type", projectType));
        form.add(trunkTip);
        form.add(useJust);
        form.add(justStatus);
        justOptions.setAlignmentX(Component.LEFT_ALIGNMENT);
        justOptions.add(muted("Just is not installed:"));
        justOptions.add(justChoice);
        justOptions.add(installJust);
        form.add(justOptions);
        form.add(gap(8));
        form.add(sectionHeader("Repository", "Initialize version control for the new project."));
        form.add(gap(5));
        form.add(initializeGit);
        includeGitignore.setToolTipText("Exclude generated Rust and Trunk output from the new repository.");
        form.add(includeGitignore);
        form.add(gap(14));
        form.add(sectionHeader("TiltUI", "Build reusable HTML and CSS interfaces for Bevy."));
        form.add(gap(5));
        form.add(tiltUi);
        form.add(muted("TiltUI provides components, templates and styling for Bevy apps."));
        tiltUiVersion.setEditable(false);
        tiltUiVersion.setPrototypeDisplayValue("0.1.0-rc.10");
        form.add(tiltUiVersionRow);
        form.add(allowTiltUiRc);
        form.add(tiltUiVersionStatus);
        featurePanel.setBorder(new EmptyBorder(2, 2, 2, 2));
        featureScroll.setPreferredSize(new Dimension(500, 250));
        featureScroll.setBorder(BorderFactory.createLineBorder(UIManager.getColor("Component.borderColor") != null
                ? UIManager.getColor("Component.borderColor") : Color.GRAY));
        featureScroll.getVerticalScrollBar().setUnitIncrement(24);
        JPanel featureChoice = new JPanel(new BorderLayout(8, 0));
        featureChoice.setAlignmentX(Component.LEFT_ALIGNMENT);
        featureChoice.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
        featureChoice.add(configureFeatures, BorderLayout.WEST);
        featureChoice.add(featureSummary, BorderLayout.CENTER);
        form.add(featureChoice);
        routingRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        routingRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        routingRow.add(useRouting, BorderLayout.WEST);
        routingRow.add(muted("Adds Home and About pages with working navigation buttons."), BorderLayout.CENTER);
        routingRow.setToolTipText("Creates two routes and a router outlet. Add more routes with New → Tilt Component.");
        form.add(routingRow);
        for (TiltFeature feature : TiltFeature.ALL) {
            boolean includedByComponent = TiltUiCompatibility.requiredByStarter(feature.key());
            JCheckBox box = new JCheckBox("", includedByComponent);
            if (includedByComponent) {
                box.setEnabled(false);
                box.setToolTipText("Included by the generated TiltUI component app.");
            }
            featureChecks.put(feature.key(), box);
            JLabel title = new JLabel(feature.title());
            title.setFont(title.getFont().deriveFont(Font.BOLD));
            JTextArea description = new JTextArea(feature.description());
            description.setEditable(false);
            description.setFocusable(false);
            description.setOpaque(false);
            description.setLineWrap(true);
            description.setWrapStyleWord(true);
            description.setFont(description.getFont().deriveFont(description.getFont().getSize2D() - 1f));
            description.setForeground(UIManager.getColor("Label.disabledForeground"));
            JPanel details = new JPanel(new BorderLayout(0, 2));
            details.add(title, BorderLayout.NORTH);
            details.add(description, BorderLayout.CENTER);
            MouseAdapter openReadme = new MouseAdapter() {
                @Override public void mouseClicked(MouseEvent e) { new FeatureReadmeDialog(feature).show(); }
            };
            for (JComponent component : List.of(title, description, details)) {
                component.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                component.setToolTipText("Open the related crate README.md files");
                component.addMouseListener(openReadme);
            }
            JPanel featureRow = new JPanel(new BorderLayout(10, 0));
            featureRow.setBorder(new EmptyBorder(5, 8, 5, 8));
            JComponent selector = box;
            if (includedByComponent) {
                JLabel included = muted("✓");
                included.setHorizontalAlignment(SwingConstants.CENTER);
                included.setToolTipText("Included by the generated template");
                selector = included;
            }
            selector.setPreferredSize(new Dimension(28, 44));
            featureRow.add(selector, BorderLayout.WEST);
            featureRow.add(details, BorderLayout.CENTER);
            featureRow.setPreferredSize(new Dimension(0, 54));
            featureRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 54));
            featureRow.setAlignmentX(Component.LEFT_ALIGNMENT);
            featurePanel.add(featureRow);
        }
        JScrollPane projectScroll = new JScrollPane(form,
                ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        projectScroll.setBorder(BorderFactory.createEmptyBorder());
        projectScroll.getVerticalScrollBar().setUnitIncrement(24);
        WizardWheelScrolling.install(projectScroll);
        pages.add(projectScroll, "project");

        JPanel filters = new JPanel(new GridLayout(2, 1, 0, 8));
        filters.add(labelled("Bevy version", bevyTag));
        filters.add(labelled("Sort", sort));
        filters.setBorder(new EmptyBorder(10, 10, 10, 10));
        filterPopup.add(filters, BorderLayout.CENTER);
        filtersButton.setToolTipText("Filter and sort crates");
        filtersButton.setPreferredSize(new Dimension(34, search.getPreferredSize().height));
        filtersButton.addActionListener(e -> JBPopupFactory.getInstance()
                .createComponentPopupBuilder(filterPopup, bevyTag)
                .setTitle("Filter crates")
                .setRequestFocus(true)
                .setCancelOnClickOutside(true)
                .createPopup()
                .showUnderneathOf(filtersButton));
        JPanel toolbar = new JPanel(new BorderLayout(8, 0));
        toolbar.add(search, BorderLayout.CENTER);
        toolbar.add(filtersButton, BorderLayout.EAST);
        catalogPanel.add(labelled("Search crates", toolbar), BorderLayout.NORTH);
        catalogList.setCellRenderer(new AssetRenderer());
        ToolTipManager.sharedInstance().registerComponent(catalogList);
        catalogList.addHierarchyListener(event -> {
            if ((event.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) != 0) updateDownloadSpinner();
        });
        catalogList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        catalogList.setFixedCellHeight(-1);
        catalogList.addComponentListener(new ComponentAdapter() {
            private int previousWidth;
            @Override public void componentResized(ComponentEvent event) {
                int width = catalogList.getWidth();
                if (width == previousWidth) return;
                previousWidth = width;
                catalogList.setCellRenderer(new AssetRenderer());
            }
        });
        catalogScroll.setBorder(featureScroll.getBorder());
        catalogScroll.getVerticalScrollBar().setUnitIncrement(24);
        WizardWheelScrolling.install(catalogScroll);
        MouseAdapter catalogClicks = new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent event) {
                int index = catalogList.locationToIndex(event.getPoint());
                if (index < 0) return;
                Rectangle bounds = catalogList.getCellBounds(index, index);
                if (bounds == null || !bounds.contains(event.getPoint())) return;
                CrateAsset asset = listModel.get(index);
                if (isCatalogTitleHit(event, bounds, asset)) {
                    new ReadmeDialog(null, resolvedCrates.getOrDefault(assetKey(asset), asset)).show();
                } else toggleSelection(asset);
            }
            @Override public void mouseMoved(MouseEvent event) {
                int index = catalogList.locationToIndex(event.getPoint());
                Rectangle bounds = index < 0 ? null : catalogList.getCellBounds(index, index);
                boolean link = bounds != null && bounds.contains(event.getPoint())
                        && isCatalogTitleHit(event, bounds, listModel.get(index));
                catalogList.setCursor(Cursor.getPredefinedCursor(link ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR));
            }
        };
        catalogList.addMouseListener(catalogClicks);
        catalogList.addMouseMotionListener(catalogClicks);
        catalogPanel.add(catalogScroll, BorderLayout.CENTER);
        catalogPanel.add(catalogStatus, BorderLayout.SOUTH);
        categoryModel.addElement("All categories");
        categoryList.setSelectedIndex(0);
        categoryList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        categoryList.setFixedCellHeight(30);
        categoryList.addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting() && categoryList.getSelectedValue() != null
                    && !categoryList.getSelectedValue().equals(category.getSelectedItem()))
                category.setSelectedItem(categoryList.getSelectedValue());
        });
        JScrollPane categoryScroll = new JScrollPane(categoryList,
                ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        WizardWheelScrolling.install(categoryScroll);
        categoryScroll.setPreferredSize(new Dimension(155, 300));
        JPanel categoryColumn = new JPanel(new BorderLayout(0, 6));
        categoryColumn.add(muted("CATEGORIES"), BorderLayout.NORTH);
        categoryColumn.add(categoryScroll, BorderLayout.CENTER);
        JPanel browser = new JPanel(new BorderLayout(10, 0));
        browser.add(categoryColumn, BorderLayout.WEST);
        browser.add(catalogPanel, BorderLayout.CENTER);
        JPanel dependencies = new JPanel(new BorderLayout(0, 12));
        dependencies.setBorder(new EmptyBorder(10, 10, 10, 10));
        dependencies.add(sectionHeader("Dependencies", "Search categories, inspect READMEs and add compatible crates."), BorderLayout.NORTH);
        dependencies.add(browser, BorderLayout.CENTER);
        selectedCratesPanel.setBorder(new EmptyBorder(4, 4, 4, 4));
        selectedScroll.setPreferredSize(new Dimension(450, 88));
        WizardWheelScrolling.install(selectedScroll);
        JPanel selection = new JPanel(new BorderLayout(0, 5));
        selection.add(selectedCratesTitle, BorderLayout.NORTH);
        selection.add(selectedScroll, BorderLayout.CENTER);
        dependencies.add(selection, BorderLayout.SOUTH);
        pages.add(dependencies, "dependencies");
        reviewPanel.setLayout(new BoxLayout(reviewPanel, BoxLayout.Y_AXIS));
        reviewPanel.setBorder(new EmptyBorder(16, 20, 16, 20));
        reviewScroll.setBorder(BorderFactory.createEmptyBorder());
        reviewScroll.getVerticalScrollBar().setUnitIncrement(24);
        WizardWheelScrolling.install(reviewScroll);
        pages.add(reviewScroll, "review");
        JPanel header = new JPanel(new BorderLayout());
        stepTitle.setFont(stepTitle.getFont().deriveFont(Font.BOLD, stepTitle.getFont().getSize2D() + 2f));
        header.add(stepTitle, BorderLayout.WEST);
        header.add(muted("Project  /  Dependencies  /  Review"), BorderLayout.EAST);
        root.add(header, BorderLayout.NORTH);
        root.add(pages, BorderLayout.CENTER);
        JPanel navigation = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 2));
        navigation.add(back);
        navigation.add(next);
        root.add(navigation, BorderLayout.SOUTH);
        back.addActionListener(e -> showPage(currentPage - 1));
        next.addActionListener(e -> showPage(currentPage + 1));

        allowRc.addActionListener(e -> updateVersionChoices());
        allowTiltUiRc.addActionListener(e -> updateTiltUiVersionChoices());
        tiltUiVersion.addActionListener(e -> {
            updateTiltUiVersionStatus();
            notifyValidationChanged();
        });
        version.addActionListener(e -> {
            updateRustRequirement(true);
            updateCatalogForBevy();
        });
        rustToolchain.addActionListener(e -> updateRustStatus());
        installRust.addActionListener(e -> changeToolchain(false));
        updateStable.addActionListener(e -> changeToolchain(true));
        version.addPopupMenuListener(new PopupMenuListener() {
            @Override public void popupMenuWillBecomeVisible(PopupMenuEvent e) {}
            @Override public void popupMenuWillBecomeInvisible(PopupMenuEvent e) {
                if (pendingVersionChoices) SwingUtilities.invokeLater(BevyWizardPeer.this::updateVersionChoices);
            }
            @Override public void popupMenuCanceled(PopupMenuEvent e) {
                if (pendingVersionChoices) SwingUtilities.invokeLater(BevyWizardPeer.this::updateVersionChoices);
            }
        });
        tiltUiVersion.addPopupMenuListener(new PopupMenuListener() {
            @Override public void popupMenuWillBecomeVisible(PopupMenuEvent e) {}
            @Override public void popupMenuWillBecomeInvisible(PopupMenuEvent e) {
                if (pendingTiltUiVersionChoices) SwingUtilities.invokeLater(BevyWizardPeer.this::updateTiltUiVersionChoices);
            }
            @Override public void popupMenuCanceled(PopupMenuEvent e) {
                if (pendingTiltUiVersionChoices) SwingUtilities.invokeLater(BevyWizardPeer.this::updateTiltUiVersionChoices);
            }
        });
        kind.addActionListener(e -> updateVisibility());
        initializeGit.addActionListener(e -> updateVisibility());
        useJust.addActionListener(e -> {
            updateVisibility();
            if (useJust.isSelected() && !justBusy) checkJust();
        });
        justChoice.addActionListener(e -> updateVisibility());
        installJust.addActionListener(e -> installJust());
        configureFeatures.addActionListener(e -> showFeaturePopup());
        featureChecks.values().forEach(check -> check.addActionListener(e -> updateFeatureSummary()));
        tiltUi.addActionListener(e -> {
            updateVisibility();
            if (tiltUi.isSelected() && root.isShowing()) SwingUtilities.invokeLater(this::showFeaturePopup);
        });
        search.getDocument().addDocumentListener(new SimpleDocumentListener(this::refresh));
        category.addActionListener(e -> {
            if (category.getSelectedItem() != null && !category.getSelectedItem().equals(categoryList.getSelectedValue()))
                categoryList.setSelectedValue(String.valueOf(category.getSelectedItem()), true);
            refresh();
        });
        bevyTag.addActionListener(e -> refresh());
        sort.addActionListener(e -> refresh());
        updateVisibility();
        updateFeatureSummary();
        refreshSelectedCrates();
        showPage(0);
        updateRustRequirement(false);
        if (fetchVersions) {
            loadBevyVersions();
            loadTiltUiVersions();
            loadInstalledToolchains(null);
        }
    }

    /** Shows controls relevant to the selected project kind and options. */
    private void updateVisibility() {
        ProjectKind projectKind = (ProjectKind) kind.getSelectedItem();
        includeGitignore.setVisible(initializeGit.isSelected());
        trunkTip.setVisible(projectKind.hasWasm());
        useJust.setEnabled(projectKind.hasBinary());
        if (!projectKind.hasBinary()) useJust.setSelected(false);
        justStatus.setVisible(projectKind.hasBinary() && useJust.isSelected());
        justOptions.setVisible(projectKind.hasBinary() && useJust.isSelected() && justChecked && justVersion == null);
        installJust.setVisible(justChoice.getSelectedIndex() == 0);
        installJust.setEnabled(!justBusy);
        tiltUi.setEnabled(projectKind.hasBinary());
        if (!projectKind.hasBinary()) tiltUi.setSelected(false);
        boolean showTiltUiVersion = projectKind.hasBinary() && tiltUi.isSelected();
        tiltUiVersionRow.setVisible(showTiltUiVersion);
        allowTiltUiRc.setVisible(showTiltUiVersion);
        tiltUiVersionStatus.setVisible(showTiltUiVersion);
        for (TiltFeature feature : TiltFeature.ALL) {
            if (TiltUiCompatibility.requiredByStarter(feature.key())) continue;
            JCheckBox check = featureChecks.get(feature.key());
            boolean available = TiltUiCompatibility.availableOn(projectKind, feature.key());
            check.setEnabled(available);
            check.setToolTipText(available ? feature.description() : "Unavailable for this project target.");
            if (!available) check.setSelected(false);
        }
        configureFeatures.setVisible(projectKind.hasBinary() && tiltUi.isSelected());
        featureSummary.setVisible(projectKind.hasBinary() && tiltUi.isSelected());
        routingRow.setVisible(projectKind.hasBinary() && tiltUi.isSelected());
        updateFeatureSummary();
        root.revalidate();
        notifyValidationChanged();
    }

    /** Opens the feature chooser without expanding the project form. */
    private void showFeaturePopup() {
        if (!configureFeatures.isShowing() || !configureFeatures.isEnabled()) return;
        JPanel popup = new JPanel(new BorderLayout(0, 8));
        popup.setBorder(new EmptyBorder(8, 8, 8, 8));
        popup.add(featureScroll, BorderLayout.CENTER);
        popup.add(muted("Select features with the checkboxes. Click a title to read its README."), BorderLayout.SOUTH);
        WizardWheelScrolling.install(featureScroll);
        JBPopup featurePopup = JBPopupFactory.getInstance().createComponentPopupBuilder(popup, featureScroll)
                .setTitle("TiltUI features")
                .setRequestFocus(true)
                .setCancelOnClickOutside(true)
                .createPopup();
        featurePopup.showInCenterOf(root);
    }

    /** Summarizes optional TiltUI choices beside the popup trigger. */
    private void updateFeatureSummary() {
        List<String> optional = TiltFeature.ALL.stream().filter(feature ->
                !TiltUiCompatibility.requiredByStarter(feature.key())
                        && featureChecks.containsKey(feature.key()) && featureChecks.get(feature.key()).isSelected())
                .map(TiltFeature::title).toList();
        long included = TiltUiCompatibility.FEATURES.stream().filter(TiltUiCompatibility.Feature::requiredByStarter).count();
        featureSummary.setText(included + " included · " + optional.size() + " optional selected");
        featureSummary.setToolTipText(optional.isEmpty() ? "Components, HTML and CSS are included."
                : String.join(", ", optional));
    }

    /** Moves through the project, dependency and final review pages. */
    private void showPage(int page) {
        if (page < 0 || page > 2) return;
        currentPage = page;
        String[] ids = {"project", "dependencies", "review"};
        String[] titles = {"1  Project", "2  Dependencies", "3  Review"};
        pageLayout.show(pages, ids[page]);
        stepTitle.setText(titles[page]);
        back.setEnabled(page > 0);
        next.setVisible(page < 2);
        next.setText(page == 0 ? "Next: Dependencies" : "Review Project");
        if (page == 1 && fetchNetwork) loadCatalog();
        if (page == 2) renderReview();
        updateDownloadSpinner();
        root.revalidate();
        root.repaint();
        notifyValidationChanged();
    }

    /** Renders the selected settings as the last wizard page. */
    private void renderReview() {
        BevySettings settings = getSettings();
        reviewPanel.removeAll();
        reviewPanel.add(sectionHeader("Ready to create", "Review these choices before clicking Create Project."));
        reviewPanel.add(gap(15));
        reviewPanel.add(sectionHeader("Project", null));
        String name = nameLocation == null ? null : nameLocation.getModuleName();
        if ((name == null || name.isBlank()) && wizardContext != null) name = wizardContext.getProjectName();
        String location = nameLocation == null ? null : nameLocation.getModuleContentRoot();
        if ((location == null || location.isBlank()) && wizardContext != null) location = wizardContext.getProjectFileDirectory();
        if (name != null && !name.isBlank()) reviewPanel.add(summaryRow("Name", name));
        if (location != null && !location.isBlank()) reviewPanel.add(summaryRow("Location", location));
        reviewPanel.add(summaryRow("Type", String.valueOf(settings.kind())));
        reviewPanel.add(summaryRow("Bevy", settings.bevyVersion()));
        if (settings.minimumRustVersion() != null)
            reviewPanel.add(summaryRow("Minimum Rust", settings.minimumRustVersion()));
        reviewPanel.add(summaryRow("Rust", settings.rustToolchain() == null ? "Choose a toolchain"
                : settings.rustToolchain() + " (" + settings.installedRustVersion() + ")"));
        if (settings.kind().hasWasm()) reviewPanel.add(summaryRow("Browser build", "Trunk"));
        reviewPanel.add(summaryRow("Just", settings.useJust() ? "Include justfile" : "Use Cargo commands"));
        reviewPanel.add(gap(12));
        reviewPanel.add(sectionHeader("TiltUI", null));
        reviewPanel.add(summaryRow("Framework", settings.tiltUi() ? "Enabled" : "Not included"));
        if (settings.tiltUi()) {
            reviewPanel.add(summaryRow("Version", settings.tiltUiVersion()));
            reviewPanel.add(summaryRow("Routing", settings.useRouting() ? "Home and About routes" : "Not included"));
            String optional = settings.tiltFeatures().stream()
                    .filter(key -> !TiltUiCompatibility.requiredByStarter(key))
                    .collect(Collectors.joining(", "));
            reviewPanel.add(summaryRow("Features", "Components, HTML, CSS" + (optional.isEmpty() ? "" : ", " + optional)));
        }
        reviewPanel.add(gap(12));
        JPanel cratesHeader = new JPanel(new BorderLayout(8, 0));
        cratesHeader.add(sectionHeader("Third-party crates", null), BorderLayout.CENTER);
        JButton editCrates = new JButton("Edit selection…");
        editCrates.addActionListener(event -> showPage(1));
        cratesHeader.add(editCrates, BorderLayout.EAST);
        cratesHeader.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
        cratesHeader.setAlignmentX(Component.LEFT_ALIGNMENT);
        reviewPanel.add(cratesHeader);
        if (settings.crates().isEmpty()) reviewPanel.add(summaryRow("Dependencies", "None selected"));
        else settings.crates().forEach(crate -> reviewPanel.add(reviewCrateCard(crate)));
        reviewPanel.add(gap(12));
        reviewPanel.add(sectionHeader("Repository", null));
        reviewPanel.add(summaryRow("Git", settings.initializeGit() ? "Initialize repository" : "Not initialized"));
        if (settings.initializeGit()) reviewPanel.add(summaryRow(".gitignore", settings.includeGitignore() ? "Include" : "Skip"));
        reviewPanel.revalidate();
        reviewPanel.repaint();
        WizardWheelScrolling.install(reviewScroll);
    }

    /** Keeps only the crate title visible until its details are expanded. */
    private JPanel reviewCrateCard(CrateAsset crate) {
        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
        JPanel header = new JPanel(new BorderLayout(6, 0));
        header.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
        header.setAlignmentX(Component.LEFT_ALIGNMENT);
        JButton toggle = new JButton("▸");
        toggle.setToolTipText("Show details for " + crate.title());
        toggle.getAccessibleContext().setAccessibleName("Show details for " + crate.title());
        toggle.setPreferredSize(new Dimension(30, 28));
        header.add(toggle, BorderLayout.WEST);
        JLabel title = new JLabel("<html><u>" + escapeHtml(crate.title()) + "</u></html>");
        title.setForeground(linkColor());
        title.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        title.setToolTipText("Open " + crate.title() + " README");
        title.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent event) { new ReadmeDialog(null, crate).show(); }
        });
        JPanel name = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        name.add(title);
        if (hasPackageChoiceWarning(crate)) name.add(crateWarningIcon());
        header.add(name, BorderLayout.CENTER);
        card.add(header);
        JPanel details = new JPanel();
        details.setName("crate-details");
        details.setLayout(new BoxLayout(details, BoxLayout.Y_AXIS));
        details.setAlignmentX(Component.LEFT_ALIGNMENT);
        details.add(summaryRow("Cargo package", crate.name()));
        details.add(summaryRow("Version", "v" + crate.version()));
        details.add(summaryRow("Bevy support", String.join(", ", crate.bevyTags())));
        details.add(summaryRow("Source", crate.url()));
        details.add(summaryRow("Name verified", crate.identitySource().label()));
        details.setVisible(false);
        card.add(details);
        toggle.addActionListener(event -> {
            boolean expanded = !details.isVisible();
            details.setVisible(expanded);
            card.setMaximumSize(new Dimension(Integer.MAX_VALUE,
                    expanded ? 32 + details.getPreferredSize().height : 32));
            toggle.setText(expanded ? "▾" : "▸");
            toggle.setToolTipText((expanded ? "Hide" : "Show") + " details for " + crate.title());
            toggle.getAccessibleContext().setAccessibleName(toggle.getToolTipText());
            reviewPanel.revalidate();
            reviewPanel.repaint();
        });
        return card;
    }

    /** Shows selected dependencies with direct removal controls. */
    private void refreshSelectedCrates() {
        selectedCratesPanel.removeAll();
        selectedCratesTitle.setText("Selected crates (" + selected.size() + ")");
        if (selected.isEmpty()) selectedCratesPanel.add(muted("No crates selected yet."));
        for (String assetKey : List.copyOf(selected)) {
            CrateAsset asset = resolvedCrates.getOrDefault(assetKey, allAssets.get(assetKey));
            JPanel row = new JPanel(new BorderLayout(8, 0));
            String title = asset == null ? assetKey : asset.title();
            String version = resolvingCrates.containsKey(assetKey) || asset == null || asset.version() == null
                    ? "Checking…" : "v" + asset.version();
            JPanel details = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
            JLabel crateTitle = new JLabel("<html><u>" + escapeHtml(title) + "</u></html>");
            crateTitle.setForeground(linkColor());
            crateTitle.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            crateTitle.setToolTipText("Open " + title + " README.md");
            if (asset != null) crateTitle.addMouseListener(new MouseAdapter() {
                @Override public void mouseClicked(MouseEvent event) {
                    new ReadmeDialog(null, resolvedCrates.getOrDefault(assetKey, asset)).show();
                }
            });
            details.add(crateTitle);
            if (asset != null && hasPackageChoiceWarning(asset)) details.add(crateWarningIcon());
            details.add(muted(version));
            row.add(details, BorderLayout.CENTER);
            JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
            JButton changePackage = new JButton("Change package…");
            changePackage.setToolTipText("Choose a crates.io package or enter another name");
            changePackage.setEnabled(asset != null && resolvedCrates.containsKey(assetKey));
            changePackage.addActionListener(event -> {
                if (asset == null) return;
                changePackage.setEnabled(false);
                catalogStatus.setText("Finding crates.io packages for " + asset.title() + "…");
                new SwingWorker<List<String>, Void>() {
                    @Override protected List<String> doInBackground() {
                        try { return CatalogClient.suggestCrateNames(asset); }
                        catch (IOException ignored) { return List.of(); }
                    }
                    @Override protected void done() {
                        changePackage.setEnabled(true);
                        if (!selected.contains(assetKey)) return;
                        List<String> suggestions;
                        try { suggestions = get(); }
                        catch (Exception ignored) { suggestions = List.of(); }
                        String chosen = chooseCrateName(asset, suggestions);
                        if (chosen != null) resolveSelection(asset, chosen);
                        else catalogStatus.setText("Package selection was cancelled.");
                    }
                }.execute();
            });
            actions.add(changePackage);
            JButton remove = new JButton("Remove");
            remove.setToolTipText("Remove " + title);
            remove.addActionListener(event -> {
                selected.remove(assetKey);
                resolvedCrates.remove(assetKey);
                resolvingCrates.remove(assetKey);
                refreshSelectedCrates();
                catalogList.repaint();
            });
            actions.add(remove);
            row.add(actions, BorderLayout.EAST);
            row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
            row.setAlignmentX(Component.LEFT_ALIGNMENT);
            selectedCratesPanel.add(row);
        }
        selectedCratesPanel.revalidate();
        selectedCratesPanel.repaint();
        WizardWheelScrolling.install(selectedScroll);
        notifyValidationChanged();
    }

    /** Gives verified search suggestions first and allows an exact package name under Other. */
    private String chooseCrateName(CrateAsset asset, List<String> suggestions) {
        final String other = "Other…";
        List<String> names = packageNameChoices(asset, suggestions);
        JComboBox<String> choices = new JComboBox<>(names.toArray(String[]::new));
        JTextField customName = new JTextField(asset.name(), 26);
        boolean onlyOther = names.size() == 1;
        if (onlyOther) choices.setSelectedItem(other);
        customName.setEnabled(onlyOther);
        choices.addActionListener(event -> customName.setEnabled(other.equals(choices.getSelectedItem())));
        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.add(new JLabel("Choose the crates.io package for " + asset.title() + ":"));
        content.add(muted(asset.url()));
        content.add(gap(8));
        content.add(choices);
        content.add(gap(6));
        content.add(new JLabel("Package name (for Other):"));
        content.add(customName);
        while (true) {
            int result = JOptionPane.showConfirmDialog(root, content, "Choose Cargo package",
                    JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE);
            if (result != JOptionPane.OK_OPTION) return null;
            String chosen = other.equals(choices.getSelectedItem())
                    ? customName.getText().trim() : (String) choices.getSelectedItem();
            if (chosen != null && chosen.matches("[A-Za-z][A-Za-z0-9_-]*")) return chosen;
            Messages.showErrorDialog(root, "Enter a valid crates.io package name.", "Invalid Cargo package");
        }
    }

    /** Builds the dropdown options without treating a guessed title as a verified package. */
    static List<String> packageNameChoices(CrateAsset asset, List<String> suggestions) {
        LinkedHashSet<String> names = new LinkedHashSet<>();
        if (asset.hasVerifiedIdentity()) names.add(asset.name());
        names.addAll(suggestions);
        names.add("Other…");
        return List.copyOf(names);
    }

    /** Looks for Just after the option is enabled without blocking the wizard. */
    private void checkJust() {
        justBusy = true;
        justChecked = false;
        justStatus.setText("Checking for Just…");
        updateVisibility();
        new SwingWorker<Optional<JustTools.Installed>, Void>() {
            @Override protected Optional<JustTools.Installed> doInBackground() throws Exception {
                return JustTools.installed();
            }
            @Override protected void done() {
                justBusy = false;
                justChecked = true;
                try {
                    justVersion = get().map(JustTools.Installed::version).orElse(null);
                    justError = null;
                } catch (Exception failure) {
                    Throwable cause = failure.getCause() == null ? failure : failure.getCause();
                    justError = cause.getMessage();
                }
                showJustStatus();
            }
        }.execute();
    }

    /** Installs Just only after the developer selects the install option and clicks the button. */
    private void installJust() {
        if (!useJust.isSelected() || justBusy || justChoice.getSelectedIndex() != 0) return;
        justBusy = true;
        justStatus.setText("Installing Just with Cargo…");
        updateVisibility();
        new SwingWorker<JustTools.Installed, Void>() {
            @Override protected JustTools.Installed doInBackground() throws Exception { return JustTools.install(); }
            @Override protected void done() {
                justBusy = false;
                try {
                    justVersion = get().version();
                    justError = null;
                } catch (Exception failure) {
                    Throwable cause = failure.getCause() == null ? failure : failure.getCause();
                    justError = cause.getMessage();
                }
                showJustStatus();
            }
        }.execute();
    }

    /** Displays the installed version or the two choices available when Just is missing. */
    private void showJustStatus() {
        if (justVersion != null) {
            justStatus.setText("✓ Just " + justVersion + " installed");
            justStatus.setForeground(new Color(32, 145, 72));
            justStatus.setToolTipText(null);
        } else if (justError != null) {
            justStatus.setText("Could not check or install Just. Choose an option below.");
            justStatus.setForeground(new Color(180, 55, 55));
            justStatus.setToolTipText(justError);
        } else {
            justStatus.setText("Just was not found. Install it or create the justfile only.");
            justStatus.setForeground(UIManager.getColor("Label.disabledForeground"));
            justStatus.setToolTipText(null);
        }
        updateVisibility();
    }

    /** Clears selected crates when the Bevy release changes. */
    private void updateCatalogForBevy() {
        String current = bevyVersion();
        if (!current.matches("\\d+\\.\\d+\\.\\d+(?:-rc\\.\\d+)?") || current.equals(catalogBevyVersion)) return;
        catalogBevyVersion = current;
        boolean cleared = !selected.isEmpty();
        if (cleared) {
            selected.clear();
            resolvedCrates.clear();
            resolvingCrates.clear();
            refreshSelectedCrates();
        }
        refresh();
        catalogStatus.setText(cleared ? "Bevy version changed. Select compatible crates again."
                : listModel.size() + " matching assets for Bevy " + current + ".");
    }

    /** Limits README navigation to the visible title text; the rest of the card selects the crate. */
    private boolean isCatalogTitleHit(MouseEvent event, Rectangle bounds, CrateAsset asset) {
        FontMetrics metrics = catalogList.getFontMetrics(UIManager.getFont("Label.font"));
        int x = bounds.x + CARD_TITLE_X;
        int y = bounds.y + CARD_TITLE_Y;
        return event.getX() >= x && event.getX() < x + metrics.stringWidth(asset.title()) + 4
                && event.getY() >= y && event.getY() < y + metrics.getHeight() + 2;
    }

    /** Toggles a crate from the checkbox or any non-title part of its card. */
    private void toggleSelection(CrateAsset asset) {
        String key = assetKey(asset);
        if (selected.remove(key)) {
            resolvedCrates.remove(key);
            resolvingCrates.remove(key);
            refreshSelectedCrates();
        } else resolveSelection(asset);
        catalogList.repaint();
    }

    /** Checks a selected crate release on a background worker. */
    private void resolveSelection(CrateAsset asset) { resolveSelection(asset, null); }

    private void resolveSelection(CrateAsset asset, String userName) {
        String targetBevy = bevyVersion();
        int request = ++selectionRequest;
        String key = assetKey(asset);
        selected.add(key);
        resolvingCrates.put(key, request);
        refreshSelectedCrates();
        catalogStatus.setText("Verifying " + asset.title() + " for Bevy " + targetBevy + "…");
        catalogStatus.setToolTipText(null);
        catalogList.repaint();
        notifyValidationChanged();
        new SwingWorker<Optional<CrateAsset>, Void>() {
            @Override protected Optional<CrateAsset> doInBackground() throws Exception {
                if (userName != null) {
                    CrateAsset verified = CatalogClient.identifyUserName(asset, userName);
                    return CatalogClient.compatibleRelease(verified, targetBevy);
                }
                return crateResolver.resolve(asset, targetBevy);
            }
            @Override protected void done() {
                if (!Objects.equals(resolvingCrates.get(key), request)) return;
                resolvingCrates.remove(key);
                if (!targetBevy.equals(bevyVersion()) || !selected.contains(key)) return;
                try {
                    Optional<CrateAsset> compatible = get();
                    if (compatible.isPresent()) {
                        CrateAsset release = compatible.get();
                        boolean duplicate = resolvedCrates.values().stream().anyMatch(existing ->
                                existing.name().replace('-', '_').equalsIgnoreCase(release.name().replace('-', '_'))
                                        && !assetKey(existing).equals(key));
                        if (duplicate) {
                            if (!resolvedCrates.containsKey(key)) selected.remove(key);
                            refreshSelectedCrates();
                            catalogStatus.setText(release.name() + " is already selected from another catalog entry.");
                        } else {
                            acceptCompatibleSelection(release);
                            catalogStatus.setText("Added " + release.name() + " v" + release.version() + " for Bevy " + targetBevy + ".");
                        }
                    } else {
                        if (!resolvedCrates.containsKey(key)) selected.remove(key);
                        refreshSelectedCrates();
                        catalogStatus.setText("No compatible published version found for " + asset.title() + ".");
                    }
                } catch (Exception error) {
                    Throwable cause = error.getCause() == null ? error : error.getCause();
                    if (cause instanceof UnverifiedCrateName unverified) {
                        needsPackageChoice.add(key);
                        catalogList.repaint();
                        String entered = chooseCrateName(asset, unverified.suggestions);
                        if (entered != null) {
                            resolveSelection(asset, entered);
                            return;
                        }
                        catalogStatus.setText("Crate name was not verified; " + asset.title() + " was not added.");
                    } else {
                        catalogStatus.setText("Could not check " + asset.title() + ". Hover for details.");
                        catalogStatus.setToolTipText(cause.getMessage());
                    }
                    if (!resolvedCrates.containsKey(key)) selected.remove(key);
                    refreshSelectedCrates();
                }
                catalogList.repaint();
                notifyValidationChanged();
            }
        }.execute();
    }

    /** Adds a verified crate to the dependency tray and generated project settings. */
    void acceptCompatibleSelection(CrateAsset release) {
        if (!release.hasVerifiedIdentity() || release.version() == null)
            throw new IllegalArgumentException("A verified package and published release are required.");
        String key = assetKey(release);
        if (release.identitySource() != CrateAsset.IdentitySource.USER_PROVIDED)
            needsPackageChoice.remove(key);
        selected.add(key);
        resolvedCrates.put(key, release);
        allAssets.put(key, release);
        for (int i = 0; i < listModel.size(); i++) {
            if (assetKey(listModel.get(i)).equals(key)) {
                listModel.set(i, release);
                break;
            }
        }
        refreshSelectedCrates();
        catalogList.repaint();
    }

    /** Loads available Bevy releases asynchronously. */
    private void loadBevyVersions() {
        new SwingWorker<List<BevyRelease>, Void>() {
            @Override protected List<BevyRelease> doInBackground() throws Exception { return CatalogClient.bevyReleases(); }
            @Override protected void done() {
                try {
                    acceptBevyReleases(get());
                }
                catch (Exception ignored) { /* keep the last known stable default */ }
            }
        }.execute();
    }

    /** Refreshes TiltUI releases shared by the runtime and build crates. */
    private void loadTiltUiVersions() {
        new SwingWorker<List<String>, Void>() {
            @Override protected List<String> doInBackground() throws Exception { return CatalogClient.tiltUiVersions(); }
            @Override protected void done() {
                try { acceptTiltUiVersions(get()); }
                catch (Exception error) {
                    tiltUiVersionStatus.setToolTipText("Could not refresh TiltUI releases: " + error.getMessage());
                }
            }
        }.execute();
    }

    /** Applies published releases while keeping the plugin's maintained compatibility boundary. */
    void acceptTiltUiVersions(List<String> versions) {
        availableTiltUiVersions = versions.stream().filter(TiltUiCompatibility::supportsVersion).distinct().toList();
        updateTiltUiVersionChoices();
    }

    private void updateTiltUiVersionChoices() {
        if (tiltUiVersion.isPopupVisible()) {
            pendingTiltUiVersionChoices = true;
            return;
        }
        pendingTiltUiVersionChoices = false;
        String previous = selectedTiltUiVersion();
        List<String> choices = TiltUiCompatibility.visibleVersions(availableTiltUiVersions, allowTiltUiRc.isSelected());
        tiltUiVersion.setModel(new DefaultComboBoxModel<>(choices.toArray(String[]::new)));
        if (!choices.isEmpty()) tiltUiVersion.setSelectedItem(choices.contains(previous) ? previous : choices.getFirst());
        updateTiltUiVersionStatus();
        notifyValidationChanged();
    }

    private String selectedTiltUiVersion() {
        Object selected = tiltUiVersion.getSelectedItem();
        return selected == null ? null : selected.toString().trim();
    }

    private void updateTiltUiVersionStatus() {
        String selected = selectedTiltUiVersion();
        tiltUiVersionStatus.setText(selected == null
                ? "No supported published TiltUI release is available."
                : "TiltUI " + selected + " supports stable Bevy " + TiltUiCompatibility.BEVY_SERIES + ".x.");
    }

    /** Applies downloaded Bevy release metadata to the wizard. */
    void acceptBevyReleases(List<BevyRelease> releases) {
        for (BevyRelease release : releases) {
            if (release.rustVersion() != null && release.rustVersion().matches("\\d+\\.\\d+\\.\\d+"))
                bevyRustMinimums.put(release.version(), release.rustVersion());
        }
        acceptBevyVersions(releases.stream().map(BevyRelease::version).toList());
    }

    /** Rebuilds version choices while retaining a valid selection where possible. */
    void acceptBevyVersions(List<String> values) {
        if (values.isEmpty()) return;
        availableBevyVersions = List.copyOf(values);
        updateVersionChoices();
    }

    /** Filters release candidates according to the checkbox. */
    private void updateVersionChoices() {
        if (version.isPopupVisible()) {
            pendingVersionChoices = true;
            return;
        }
        pendingVersionChoices = false;
        String previous = bevyVersion();
        List<String> choices = availableBevyVersions.stream()
                .filter(value -> allowRc.isSelected() || !CatalogClient.isReleaseCandidate(value)).toList();
        if (choices.isEmpty()) return;
        version.setModel(new DefaultComboBoxModel<>(choices.toArray(String[]::new)));
        version.setSelectedItem(choices.contains(previous) ? previous : choices.getFirst());
        updateRustRequirement(true);
    }

    /** Returns the currently selected Bevy release. */
    private String bevyVersion() { return String.valueOf(version.getSelectedItem()).trim(); }

    /** Returns the compiler version required by that release. */
    private String minimumRustVersion() {
        String release = bevyVersion();
        String known = bevyRustMinimums.get(release);
        if (known == null && TiltUiCompatibility.isBevySeries(release))
            known = TiltUiCompatibility.BEVY_MINIMUM_RUST;
        return known == null ? null : RustToolchains.minimum(known);
    }

    /** Updates the Rust requirement and compatible toolchain choice. */
    private void updateRustRequirement(boolean chooseCompatible) {
        String minimum = minimumRustVersion();
        rustRequirement.setText(minimum == null
                ? "Rust requirement unavailable for Bevy " + bevyVersion() + "."
                : "Bevy " + bevyVersion() + " requires Rust " + minimum + " or newer.");
        if (chooseCompatible && minimum != null) {
            RustToolchains.Installed current = (RustToolchains.Installed) rustToolchain.getSelectedItem();
            if (current == null || RustToolchains.compare(current.rustVersion(), minimum) < 0) {
                RustToolchains.Installed best = RustToolchains.bestCompatible(installedToolchains, minimum);
                if (best != null) rustToolchain.setSelectedItem(best);
            }
        }
        updateRustStatus();
    }

    /** Discovers installed Rust toolchains asynchronously. */
    private void loadInstalledToolchains(String preferredChannel) {
        rustupBusy = true;
        updateRustStatus();
        new SwingWorker<List<RustToolchains.Installed>, Void>() {
            @Override protected List<RustToolchains.Installed> doInBackground() throws Exception {
                return RustToolchains.installed();
            }
            @Override protected void done() {
                rustupBusy = false;
                try {
                    acceptInstalledToolchains(get(), preferredChannel);
                    rustupError = null;
                } catch (Exception e) { showRustupError(e); }
            }
        }.execute();
    }

    /** Applies installed Rust toolchains and selects a compatible channel. */
    void acceptInstalledToolchains(List<RustToolchains.Installed> toolchains, String preferredChannel) {
        String previous = preferredChannel;
        RustToolchains.Installed selected = (RustToolchains.Installed) rustToolchain.getSelectedItem();
        if (previous == null && selected != null) previous = selected.channel();
        installedToolchains = List.copyOf(toolchains);
        rustToolchain.setModel(new DefaultComboBoxModel<>(toolchains.toArray(RustToolchains.Installed[]::new)));
        String wanted = previous;
        RustToolchains.Installed choice = toolchains.stream().filter(item -> item.channel().equals(wanted) ||
                (wanted != null && RustToolchains.projectChannel(item.channel()).equals(wanted))).findFirst().orElse(null);
        if (choice == null && minimumRustVersion() != null)
            choice = RustToolchains.bestCompatible(toolchains, minimumRustVersion());
        if (choice != null) rustToolchain.setSelectedItem(choice);
        updateRustStatus();
    }

    /** Explains the selected toolchain and installation options. */
    private void updateRustStatus() {
        String minimum = minimumRustVersion();
        RustToolchains.Installed choice = (RustToolchains.Installed) rustToolchain.getSelectedItem();
        if (rustupBusy) rustStatus.setText("Checking or installing Rust toolchains…");
        else if (rustupError != null) rustStatus.setText("Rust toolchain check failed. Hover for details.");
        else if (choice == null) rustStatus.setText("No installed Rust toolchain found. Install the required version below.");
        else if (minimum == null) rustStatus.setText("Rust requirement is not available; choose another Bevy release.");
        else if (RustToolchains.compare(choice.rustVersion(), minimum) < 0)
            rustStatus.setText("Selected Rust " + choice.rustVersion() + " is too old. Choose a newer toolchain or install one.");
        else rustStatus.setText("Selected Rust " + choice.rustVersion() + " meets this requirement.");
        boolean needsVersion = minimum != null && (choice == null || RustToolchains.compare(choice.rustVersion(), minimum) < 0);
        boolean exactInstalled = minimum != null && installedToolchains.stream()
                .anyMatch(item -> item.rustVersion().equals(minimum) && item.channel().startsWith(minimum));
        installRust.setText(minimum == null ? "Install Rust" : "Install Rust " + minimum);
        installRust.setVisible(!rustupBusy && needsVersion && !exactInstalled);
        boolean oldStable = minimum != null && installedToolchains.stream()
                .anyMatch(item -> item.channel().startsWith("stable") && RustToolchains.compare(item.rustVersion(), minimum) < 0);
        updateStable.setVisible(!rustupBusy && needsVersion && oldStable);
        rustStatus.setToolTipText(rustupError);
        root.revalidate();
        notifyValidationChanged();
    }

    /** Displays a rustup failure in the wizard. */
    private void showRustupError(Exception error) {
        rustupBusy = false;
        rustupError = error.getCause() == null ? error.getMessage() : error.getCause().getMessage();
        updateRustStatus();
    }

    /** Installs or updates a Rust toolchain asynchronously. */
    private void changeToolchain(boolean update) {
        String minimum = minimumRustVersion();
        if (minimum == null || rustupBusy) return;
        rustupBusy = true;
        rustupError = null;
        updateRustStatus();
        new SwingWorker<Void, Void>() {
            @Override protected Void doInBackground() throws Exception {
                if (update) RustToolchains.updateStable();
                else RustToolchains.install(minimum);
                return null;
            }
            @Override protected void done() {
                try {
                    get();
                    loadInstalledToolchains(update ? "stable" : minimum);
                } catch (Exception e) { showRustupError(e); }
            }
        }.execute();
    }

    /** Loads Bevy assets and crates.io metadata asynchronously. */
    private void loadCatalog() {
        if (catalogLoading || catalogLoaded) return;
        catalogLoading = true;
        catalogFailed = false;
        catalogStatus.setText("Loading the Bevy asset catalog…");
        new SwingWorker<List<CrateAsset>, CrateAsset>() {
            @Override protected List<CrateAsset> doInBackground() throws Exception {
                List<CrateAsset> assets = CatalogClient.loadAssets();
                publish(assets.toArray(CrateAsset[]::new));
                ExecutorService pool = Executors.newFixedThreadPool(4);
                try {
                    CompletionService<Optional<CrateAsset>> completed = new ExecutorCompletionService<>(pool);
                    for (CrateAsset asset : assets) {
                        completed.submit(() -> CatalogClient.enrich(asset));
                    }
                    for (int i = 0; i < assets.size(); i++) {
                        if (isCancelled()) break;
                        completed.take().get().ifPresent(this::publish);
                    }
                } finally {
                    pool.shutdownNow();
                }
                return assets;
            }
            @Override protected void process(List<CrateAsset> chunks) { acceptCatalogAssets(chunks); }
            @Override protected void done() {
                catalogLoading = false;
                try {
                    get();
                    catalogLoaded = true;
                    refresh();
                } catch (Exception e) {
                    catalogFailed = true;
                    catalogStatus.setText("Could not load bevy.org/assets: " + e.getMessage() + " — toggle the checkbox to retry.");
                    catalogList.repaint();
                    updateDownloadSpinner();
                }
            }
        }.execute();
    }

    /** Merges downloaded asset metadata without replacing active filter controls. */
    void acceptCatalogAssets(List<CrateAsset> chunks) {
        boolean added = false;
        for (CrateAsset asset : chunks) {
            String key = assetKey(asset);
            added |= !allAssets.containsKey(key);
            CrateAsset selectedRelease = resolvedCrates.get(key);
            if (selectedRelease == null) allAssets.put(key, asset);
            else {
                CrateAsset merged = selectedRelease.withMetadata(selectedRelease.version(),
                        asset.downloads() < 0 ? selectedRelease.downloads() : asset.downloads(),
                        asset.recentDownloads() < 0 ? selectedRelease.recentDownloads() : asset.recentDownloads(),
                        asset.repository() == null ? selectedRelease.repository() : asset.repository(),
                        asset.updatedAt() == null ? selectedRelease.updatedAt() : asset.updatedAt());
                resolvedCrates.put(key, merged);
                allAssets.put(key, merged);
            }
        }
        if (!filtersInitialized || added) {
            rebuildFilters();
            filtersInitialized = true;
            refresh();
        } else if (added || sort.getSelectedIndex() != 0) {
            refresh();
        } else {
            for (CrateAsset asset : chunks) {
                for (int i = 0; i < listModel.size(); i++) {
                    if (assetKey(listModel.get(i)).equals(assetKey(asset))) {
                        listModel.set(i, allAssets.get(assetKey(asset)));
                        break;
                    }
                }
            }
        }
        long available = allAssets.values().stream().filter(a -> a.hasVerifiedIdentity() && a.version() != null &&
                BevyCompatibility.supports(bevyVersion(), a.bevyTags())).count();
        catalogStatus.setText(listModel.size() + " matching assets · " + available + " published crates for Bevy " + bevyVersion());
        catalogStatus.setToolTipText("Click a blue title for its README. Select a card to verify its crates.io package and Bevy release.");
        updateDownloadSpinner();
    }

    private void updateDownloadSpinner() {
        boolean pending = currentPage == 1 && catalogList.isShowing() && !catalogLoaded && !catalogFailed;
        if (pending) pending = java.util.stream.IntStream.range(0, listModel.size())
                .mapToObj(listModel::get).anyMatch(asset -> asset.downloads() < 0);
        if (pending) downloadSpinner.start();
        else downloadSpinner.stop();
    }

    /** Adds categories and Bevy tags without replacing an open filter model. */
    private void rebuildFilters() {
        String oldCategory = (String) category.getSelectedItem();
        String oldTag = (String) bevyTag.getSelectedItem();
        Set<String> categories = allAssets.values().stream().map(CrateAsset::category).collect(Collectors.toCollection(java.util.TreeSet::new));
        Set<String> tags = allAssets.values().stream().flatMap(a -> a.bevyTags().stream())
                .map(BevyCompatibility::filterLabel).collect(Collectors.toCollection(java.util.TreeSet::new));
        DefaultComboBoxModel<String> categoryChoices = (DefaultComboBoxModel<String>) category.getModel();
        DefaultComboBoxModel<String> tagChoices = (DefaultComboBoxModel<String>) bevyTag.getModel();
        for (String value : categories) if (categoryChoices.getIndexOf(value) < 0) categoryChoices.addElement(value);
        tagChoices.removeAllElements();
        tagChoices.addElement("All Bevy tags");
        for (String value : tags) tagChoices.addElement(value);
        for (String value : categories) if (!categoryModel.contains(value)) categoryModel.addElement(value);
        if (categories.contains(oldCategory)) category.setSelectedItem(oldCategory);
        if (tags.contains(oldTag)) bevyTag.setSelectedItem(oldTag);
        if (category.getSelectedItem() != null) categoryList.setSelectedValue(String.valueOf(category.getSelectedItem()), true);
    }

    /** Applies search, compatibility, category, and sorting filters. */
    private void refresh() {
        String query = search.getText().trim().toLowerCase();
        String cat = (String) category.getSelectedItem();
        String tag = (String) bevyTag.getSelectedItem();
        List<CrateAsset> assets = allAssets.values().stream().filter(a ->
                BevyCompatibility.supports(bevyVersion(), a.bevyTags())
                && (query.isEmpty() || a.title().toLowerCase().contains(query) || a.description().toLowerCase().contains(query))
                && (cat == null || cat.equals("All categories") || a.category().equals(cat))
                && (tag == null || tag.equals("All Bevy tags") || BevyCompatibility.matchesFilter(a.bevyTags(), tag)))
                .collect(Collectors.toCollection(ArrayList::new));
        switch (sort.getSelectedIndex()) {
            case 1 -> assets.sort(Comparator.comparing(CrateAsset::updatedAt, Comparator.nullsLast(Comparator.reverseOrder())));
            case 2 -> assets.sort(Comparator.comparingLong(CrateAsset::downloads).reversed());
            case 3 -> assets.sort(Comparator.comparingLong(CrateAsset::recentDownloads).reversed());
            default -> assets.sort(Comparator.comparing(CrateAsset::title, String.CASE_INSENSITIVE_ORDER));
        }
        listModel.clear();
        assets.forEach(listModel::addElement);
        updateDownloadSpinner();
    }

    /** Returns the root wizard panel. */
    @Override public @NotNull JComponent getComponent() { return root; }
    /** Receives RustRover's callback for updating the Create button. */
    @Override public @NotNull JComponent getComponent(@NotNull TextFieldWithBrowseButton locationField,
                                                       @NotNull Runnable checkValid) {
        this.checkValid = checkValid;
        return root;
    }
    /** Rechecks project validity when the wizard's own controls change. */
    private void notifyValidationChanged() {
        if (checkValid != null) checkValid.run();
    }
    /** Adds the wizard panel to the project settings step. */
    @Override public void buildUI(@NotNull SettingsStep settingsStep) {
        nameLocation = settingsStep.getModuleNameLocationSettings();
        wizardContext = settingsStep.getContext();
        settingsStep.addSettingsComponent(root);
    }
    /** Collects validated project settings from the controls. */
    @Override public @NotNull BevySettings getSettings() {
        List<CrateAsset> crates = selected.stream().map(resolvedCrates::get)
                .filter(a -> a != null && a.version() != null).toList();
        ProjectKind selectedKind = (ProjectKind) kind.getSelectedItem();
        Set<String> features = featureChecks.entrySet().stream()
                .filter(e -> e.getValue().isSelected() && TiltUiCompatibility.availableOn(selectedKind, e.getKey()))
                .map(Map.Entry::getKey)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        RustToolchains.Installed toolchain = (RustToolchains.Installed) rustToolchain.getSelectedItem();
        return new BevySettings(bevyVersion(), minimumRustVersion(),
                toolchain == null ? null : RustToolchains.projectChannel(toolchain.channel()),
                toolchain == null ? null : toolchain.rustVersion(),
                (ProjectKind) kind.getSelectedItem(), tiltUi.isSelected() && ((ProjectKind) kind.getSelectedItem()).hasBinary(),
                useRouting.isSelected() && tiltUi.isSelected() && ((ProjectKind) kind.getSelectedItem()).hasBinary(),
                useJust.isSelected() && ((ProjectKind) kind.getSelectedItem()).hasBinary(), features, crates,
                initializeGit.isSelected(), initializeGit.isSelected() && includeGitignore.isSelected(),
                selectedTiltUiVersion() == null ? "" : selectedTiltUiVersion());
    }
    /** Rejects incompatible versions and unresolved crate selections. */
    @Override public @Nullable ValidationInfo validate() {
        if (!bevyVersion().matches("\\d+\\.\\d+\\.\\d+(?:-rc\\.\\d+)?"))
            return validationError("Choose a published Bevy version.", version, 0);
        if (CatalogClient.isReleaseCandidate(bevyVersion()) && !allowRc.isSelected())
            return validationError("Enable release candidates to use this Bevy version.", version, 0);
        if (tiltUi.isSelected()) {
            String selectedRelease = selectedTiltUiVersion();
            if (!TiltUiCompatibility.supportsVersion(selectedRelease)
                    || !availableTiltUiVersions.contains(selectedRelease))
                return validationError("Choose a supported published TiltUI version.", tiltUiVersion, 0);
            if (TiltUiCompatibility.isReleaseCandidate(selectedRelease) && !allowTiltUiRc.isSelected())
                return validationError("Enable TiltUI release candidates to use this version.", allowTiltUiRc, 0);
            if (!TiltUiCompatibility.supportsBevy(selectedRelease, bevyVersion()))
                return validationError(TiltUiCompatibility.unsupportedBevyMessage(selectedRelease), version, 0);
        }
        String minimum = minimumRustVersion();
        if (minimum == null) return validationError("Could not determine the Rust version required by Bevy. Choose a release with version metadata.", version, 0);
        if (rustupBusy) return validationError("Wait for the Rust toolchain check or installation to finish.", rustToolchain, 0);
        RustToolchains.Installed toolchain = (RustToolchains.Installed) rustToolchain.getSelectedItem();
        if (toolchain == null) return validationError("Install Rust " + minimum + " to create this project.", rustToolchain, 0);
        if (RustToolchains.compare(toolchain.rustVersion(), minimum) < 0)
            return validationError("Bevy " + bevyVersion() + " requires Rust " + minimum + " or newer. Choose or install a compatible toolchain.", rustToolchain, 0);
        if (useJust.isSelected() && ((ProjectKind) kind.getSelectedItem()).hasBinary()) {
            if (justBusy || !justChecked)
                return validationError("Wait for the Just check or installation to finish.", useJust, 0);
            if (justVersion == null && justChoice.getSelectedIndex() == 0)
                return validationError("Install Just or choose 'Create justfile only'.", justChoice, 0);
        }
        if (!resolvingCrates.isEmpty())
            return validationError("Wait for crate compatibility checks to finish.", catalogList, 1);
        if (selected.stream().anyMatch(key -> !resolvedCrates.containsKey(key) ||
                !resolvedCrates.get(key).hasVerifiedIdentity() ||
                !BevyCompatibility.supports(bevyVersion(), resolvedCrates.get(key).bevyTags())))
            return validationError("Selected crates must support Bevy " + bevyVersion() + ".", catalogList, 1);
        if (currentPage != 2)
            return new ValidationInfo("Review the project summary before creating it.", next);
        return null;
    }
    /** Opens the page containing an invalid value before reporting it to RustRover. */
    private ValidationInfo validationError(String message, JComponent component, int page) {
        if (currentPage != page) showPage(page);
        return new ValidationInfo(message, component);
    }
    /** Returns whether a required background operation is still active. */
    @Override public boolean isBackgroundJobRunning() {
        return rustupBusy || (useJust.isSelected() && justBusy)
                || !resolvingCrates.isEmpty();
    }

    /** Creates a labeled input row. */
    private static JPanel row(String title, JComponent input) {
        JPanel panel = new JPanel(new BorderLayout(8, 0));
        JLabel label = new JLabel(title);
        label.setPreferredSize(new Dimension(110, 28));
        panel.add(label, BorderLayout.WEST);
        panel.add(input, BorderLayout.CENTER);
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);
        return panel;
    }
    /** Creates a compact section heading for each wizard page. */
    private static JPanel sectionHeader(String title, String description) {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        JLabel heading = new JLabel(title);
        heading.setFont(heading.getFont().deriveFont(Font.BOLD, heading.getFont().getSize2D() + 2f));
        heading.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(heading);
        if (description != null) {
            JLabel detail = muted(description);
            panel.add(detail);
        }
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, description == null ? 24 : 43));
        return panel;
    }

    /** Creates one review line with a label and the selected value. */
    private static JPanel summaryRow(String label, String value) {
        JPanel row = new JPanel(new BorderLayout(12, 0));
        JLabel name = muted(label);
        name.setPreferredSize(new Dimension(115, 28));
        row.add(name, BorderLayout.WEST);
        boolean wrap = label.equals("Location") || value.length() > 55;
        if (wrap) {
            JTextArea text = new JTextArea(value);
            text.setEditable(false);
            text.setFocusable(false);
            text.setOpaque(false);
            text.setLineWrap(true);
            text.setWrapStyleWord(true);
            text.setFont(name.getFont().deriveFont(Font.BOLD));
            row.add(text, BorderLayout.CENTER);
        } else row.add(new JLabel(value), BorderLayout.CENTER);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, wrap ? 52 : 32));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        return row;
    }

    /** Creates a compact label and control pair. */
    private static JPanel labelled(String title, JComponent input) {
        JPanel panel = new JPanel(new BorderLayout(0, 2));
        panel.add(muted(title), BorderLayout.NORTH);
        panel.add(input, BorderLayout.CENTER);
        return panel;
    }
    /** Creates a secondary explanatory label. */
    private static JLabel muted(String text) {
        JLabel label = new JLabel(text);
        label.setFont(label.getFont().deriveFont(label.getFont().getSize2D() - 1f));
        label.setForeground(UIManager.getColor("Label.disabledForeground"));
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        return label;
    }
    /** Uses the IDE's link color in both themes. */
    private static Color linkColor() {
        Color link = UIManager.getColor("Link.activeForeground");
        return link == null ? new Color(0x3574F0) : link;
    }
    /** Escapes names supplied by the community asset catalog for Swing HTML labels. */
    private static String escapeHtml(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
    /** Marks packages that actually need a manual name choice. */
    private boolean hasPackageChoiceWarning(CrateAsset asset) {
        return asset.identitySource() == CrateAsset.IdentitySource.USER_PROVIDED
                || needsPackageChoice.contains(assetKey(asset));
    }

    private static JLabel crateWarningIcon() {
        JLabel warning = new JLabel(AllIcons.General.Warning);
        warning.setToolTipText(CRATE_WARNING);
        warning.getAccessibleContext().setAccessibleName("Bevy Assets catalog warning");
        return warning;
    }
    /** Creates vertical spacing between wizard sections. */
    private static JComponent gap(int pixels) {
        JPanel gap = new JPanel(); gap.setOpaque(false);
        gap.setPreferredSize(new Dimension(1, pixels));
        gap.setMaximumSize(new Dimension(Integer.MAX_VALUE, pixels));
        gap.setAlignmentX(Component.LEFT_ALIGNMENT);
        return gap;
    }

    private final class AssetRenderer extends JPanel implements ListCellRenderer<CrateAsset> {
        private final JCheckBox check = new JCheckBox();
        private final JLabel title = new JLabel();
        private final JLabel warning = crateWarningIcon();
        private final JLabel description = new JLabel();
        private final JLabel downloads = muted("");
        private final JLabel supported = muted("");
        private final JPanel surface = new JPanel(new BorderLayout(8, 0));
        private final JPanel content = new JPanel(new BorderLayout(0, 6));
        private final JPanel footer = new JPanel();
        AssetRenderer() {
            super(new BorderLayout());
            setOpaque(false);
            setBorder(new EmptyBorder(4, 5, 4, 5));
            add(surface, BorderLayout.CENTER);
            check.setHorizontalAlignment(SwingConstants.CENTER);
            check.setPreferredSize(new Dimension(32, 32));
            check.setOpaque(false);
            surface.add(check, BorderLayout.WEST);
            content.setOpaque(false);
            JPanel summary = new JPanel(new BorderLayout(0, 2));
            summary.setOpaque(false);
            JPanel name = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
            name.setOpaque(false);
            title.setName("crate-title");
            description.setName("crate-description");
            warning.setName("crate-warning");
            warning.setBorder(new EmptyBorder(0, 5, 0, 0));
            name.add(title);
            name.add(warning);
            summary.add(name, BorderLayout.NORTH);
            description.setVerticalAlignment(SwingConstants.TOP);
            summary.add(description, BorderLayout.CENTER);
            content.add(summary, BorderLayout.NORTH);
            downloads.setName("crate-downloads");
            supported.setName("crate-supported-bevy");
            downloads.setIcon(DOWNLOAD_ICON);
            downloads.setIconTextGap(4);
            downloads.setHorizontalAlignment(SwingConstants.RIGHT);
            supported.setHorizontalAlignment(SwingConstants.RIGHT);
            footer.setOpaque(false);
            footer.setLayout(new BorderLayout(6, 0));
            footer.add(supported, BorderLayout.CENTER);
            footer.add(downloads, BorderLayout.EAST);
            content.add(footer, BorderLayout.SOUTH);
            surface.add(content, BorderLayout.CENTER);
        }
        @Override public Component getListCellRendererComponent(JList<? extends CrateAsset> list, CrateAsset asset,
                                                                   int index, boolean isSelected, boolean hasFocus) {
            String key = assetKey(asset);
            check.setSelected(selected.contains(key));
            check.setEnabled(true);
            title.setText("<html><u>" + escapeHtml(asset.title()) + "</u></html>");
            title.setToolTipText("Open " + asset.title() + " README.md");
            warning.setVisible(hasPackageChoiceWarning(asset));
            int textWidth = Math.max(100, (list.getWidth() > 0 ? list.getWidth() : 530) - 70);
            FontMetrics textMetrics = list.getFontMetrics(description.getFont());
            description.setText(wrapped(asset.description(), textWidth, 2, false, textMetrics));
            description.setVisible(!asset.description().isBlank());
            boolean loading = asset.downloads() < 0 && !catalogLoaded && !catalogFailed;
            downloads.setIcon(loading ? loadingDownloadIcon : DOWNLOAD_ICON);
            downloads.setText(asset.downloads() >= 0 ? String.format("%,d", asset.downloads()) : loading ? "" : "-");
            String versions = asset.bevyTags().stream().map(BevyCompatibility::filterLabel)
                    .map(label -> label.substring("Bevy ".length())).distinct().collect(Collectors.joining(", "));
            int supportWidth = Math.max(55, textWidth - downloads.getPreferredSize().width - 6);
            supported.setText(wrapped("Bevy: " + (versions.isEmpty() ? "-" : versions), supportWidth,
                    0, true, textMetrics));
            Color cardBackground = UIManager.getColor("Panel.background");
            surface.setBackground(cardBackground == null ? list.getBackground() : cardBackground);
            Color border = selected.contains(key) ? linkColor() : UIManager.getColor("Component.borderColor");
            if (border == null) border = UIManager.getColor("Separator.foreground");
            if (border == null) border = new Color(0x8C929A);
            surface.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(border, 1, true), new EmptyBorder(10, 9, 7, 9)));
            title.setForeground(linkColor());
            description.setForeground(list.getForeground());
            Color secondary = UIManager.getColor("Label.disabledForeground");
            downloads.setForeground(secondary);
            supported.setForeground(secondary);
            footer.invalidate();
            content.invalidate();
            surface.invalidate();
            return this;
        }
        /** Limits descriptions to two lines while keeping every compact Bevy version visible. */
        private String wrapped(String text, int width, int maxLines, boolean rightAligned, FontMetrics metrics) {
            List<String> lines = new ArrayList<>();
            StringBuilder line = new StringBuilder();
            for (String word : text.trim().split("\\s+")) {
                if (word.isEmpty()) continue;
                if (!line.isEmpty() && metrics.stringWidth(line + " " + word) > width) {
                    lines.add(line.toString());
                    line.setLength(0);
                }
                if (!line.isEmpty()) line.append(' ');
                for (int at = 0; at < word.length(); at++) {
                    char letter = word.charAt(at);
                    if (!line.isEmpty() && metrics.stringWidth(line.toString() + letter) > width) {
                        lines.add(line.toString());
                        line.setLength(0);
                    }
                    line.append(letter);
                }
            }
            if (!line.isEmpty()) lines.add(line.toString());
            if (maxLines > 0 && lines.size() > maxLines) {
                lines = new ArrayList<>(lines.subList(0, maxLines));
                String last = lines.get(maxLines - 1);
                while (!last.isEmpty() && metrics.stringWidth(last + "…") > width)
                    last = last.substring(0, last.length() - 1);
                lines.set(maxLines - 1, last + "…");
            }
            String content = lines.isEmpty() ? "&nbsp;" : lines.stream().map(BevyWizardPeer::escapeHtml)
                    .collect(Collectors.joining("<br>"));
            return "<html><div align='" + (rightAligned ? "right" : "left") + "'>" + content + "</div></html>";
        }
    }
}
