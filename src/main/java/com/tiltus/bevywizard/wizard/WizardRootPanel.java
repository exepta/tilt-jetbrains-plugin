package com.tiltus.bevywizard.wizard;

import javax.swing.*;
import java.awt.*;

/** A wizard panel that tracks viewport width to avoid sideways scrolling. */
final class WizardRootPanel extends JPanel implements Scrollable {
    WizardRootPanel() { super(new BorderLayout()); }

    /** Uses the preferred wizard size for its viewport. */
    @Override public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
    /** Returns the vertical step for a wheel movement. */
    @Override public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) { return 24; }
    /** Returns the vertical step for a page movement. */
    @Override public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) { return Math.max(48, visibleRect.height - 48); }
    /** Prevents horizontal scrolling in the wizard. */
    @Override public boolean getScrollableTracksViewportWidth() { return true; }
    /** Lets the wizard content exceed the viewport height. */
    @Override public boolean getScrollableTracksViewportHeight() { return false; }
}
