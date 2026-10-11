package com.tiltus.bevywizard.component;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.ui.components.JBTextField;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.nio.file.Path;

/** Collects the name and kind for a new TiltUI component. */
final class TiltComponentDialog extends DialogWrapper {
    private final JBTextField name = new JBTextField();
    private final JComboBox<ComponentType> type = new JComboBox<>(ComponentType.values());
    private final Path destination;

    TiltComponentDialog(Project project, Path destination) {
        super(project, false);
        this.destination = destination;
        setTitle("New Tilt Component");
        init();
        setOKButtonText("Create");
    }

    /** Lays out the component settings and shows the resolved destination. */
    @Override protected @NotNull JComponent createCenterPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.insets = new Insets(5, 5, 5, 5);
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.gridx = 0;
        constraints.gridy = 0;
        panel.add(new JLabel("Name"), constraints);
        constraints.gridx = 1;
        constraints.weightx = 1;
        name.setColumns(28);
        name.getEmptyText().setText("example/example");
        panel.add(name, constraints);
        constraints.gridx = 0;
        constraints.gridy = 1;
        constraints.weightx = 0;
        panel.add(new JLabel("Type"), constraints);
        constraints.gridx = 1;
        panel.add(type, constraints);
        constraints.gridx = 0;
        constraints.gridy = 2;
        constraints.gridwidth = 2;
        JLabel path = new JLabel("Create in: " + destination);
        path.setFont(path.getFont().deriveFont(Font.PLAIN, 11f));
        panel.add(path, constraints);
        return panel;
    }

    /** Keeps focus in the name field when the dialog opens. */
    @Override public @Nullable JComponent getPreferredFocusedComponent() { return name; }

    /** Rejects path traversal and names that TiltUI cannot use as component tags. */
    @Override protected @Nullable ValidationInfo doValidate() {
        String error = ComponentScaffolder.validateName(name.getText());
        return error == null ? null : new ValidationInfo(error, name);
    }

    /** Returns the slash-separated path entered by the developer. */
    String componentName() { return name.getText().strip(); }

    /** Returns the selected component kind. */
    ComponentType componentType() { return (ComponentType) type.getSelectedItem(); }
}
