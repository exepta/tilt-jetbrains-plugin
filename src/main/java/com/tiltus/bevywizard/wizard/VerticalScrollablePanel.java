package com.tiltus.bevywizard.wizard;

import javax.swing.*;
import java.awt.*;

/** A list panel that fills the viewport width and scrolls vertically. */
final class VerticalScrollablePanel extends JPanel implements Scrollable {
    VerticalScrollablePanel() { setLayout(new BoxLayout(this, BoxLayout.Y_AXIS)); }

    /** Uses the preferred panel size for its viewport. */
    @Override public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
    /** Returns the vertical step for a wheel movement. */
    @Override public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) { return 24; }
    /** Returns the vertical step for a page movement. */
    @Override public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) { return Math.max(48, visibleRect.height - 48); }
    /** Keeps list content inside the available viewport width. */
    @Override public boolean getScrollableTracksViewportWidth() { return true; }
    /** Lets the list grow beyond the viewport height. */
    @Override public boolean getScrollableTracksViewportHeight() { return false; }
}
