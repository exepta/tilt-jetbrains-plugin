package com.tiltus.bevywizard.wizard;

import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

/** Forwards Swing document changes to a callback. */
final class SimpleDocumentListener implements DocumentListener {
    private final Runnable change;
    SimpleDocumentListener(Runnable change) { this.change = change; }
    /** Runs the change callback after text insertion. */
    @Override public void insertUpdate(DocumentEvent e) { change.run(); }
    /** Runs the change callback after text removal. */
    @Override public void removeUpdate(DocumentEvent e) { change.run(); }
    /** Runs the change callback after an attribute update. */
    @Override public void changedUpdate(DocumentEvent e) { change.run(); }
}
