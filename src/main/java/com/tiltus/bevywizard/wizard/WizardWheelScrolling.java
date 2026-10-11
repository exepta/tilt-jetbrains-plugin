package com.tiltus.bevywizard.wizard;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;

/** Keeps wheel scrolling inside the list or page under the pointer. */
final class WizardWheelScrolling {
    private static final String OWNER = WizardWheelScrolling.class.getName() + ".owner";

    private WizardWheelScrolling() {}

    /** Attaches wheel handling to the viewport content and its current children. */
    static void install(JScrollPane scrollPane) {
        scrollPane.setWheelScrollingEnabled(false);
        installHandler(scrollPane, scrollPane);
        Component view = scrollPane.getViewport().getView();
        if (view != null) install(view, scrollPane);
    }

    private static void install(Component component, JScrollPane scrollPane) {
        if (component instanceof JScrollPane) return;
        if (component instanceof JComponent swingComponent) installHandler(swingComponent, scrollPane);
        if (component instanceof Container container) {
            for (Component child : container.getComponents()) install(child, scrollPane);
        }
    }

    private static void installHandler(JComponent component, JScrollPane scrollPane) {
        Object oldOwner = component.getClientProperty(OWNER);
        if (oldOwner == scrollPane) return;
        if (oldOwner instanceof JScrollPane previous)
            component.removeMouseWheelListener(handler(previous));
        component.addMouseWheelListener(handler(scrollPane));
        component.putClientProperty(OWNER, scrollPane);
    }

    private static WheelHandler handler(JScrollPane scrollPane) {
        String key = WizardWheelScrolling.class.getName() + ".handler";
        WheelHandler handler = (WheelHandler) scrollPane.getClientProperty(key);
        if (handler == null) {
            handler = new WheelHandler(scrollPane);
            scrollPane.putClientProperty(key, handler);
        }
        return handler;
    }

    private static final class WheelHandler implements MouseWheelListener {
        private final JScrollPane scrollPane;
        private double remainder;

        WheelHandler(JScrollPane scrollPane) { this.scrollPane = scrollPane; }

        @Override public void mouseWheelMoved(MouseWheelEvent event) {
            if (event.isConsumed() || event.isControlDown() || event.isShiftDown()) return;
            for (JScrollPane candidate = scrollPane; candidate != null;
                 candidate = (JScrollPane) SwingUtilities.getAncestorOfClass(JScrollPane.class, candidate.getParent())) {
                JScrollBar bar = candidate.getVerticalScrollBar();
                if (bar == null || !bar.isVisible()) continue;
                int oldValue = bar.getValue();
                double movement = event.getPreciseWheelRotation() * Math.max(24, bar.getUnitIncrement()) + remainder;
                int pixels = (int) movement;
                if (pixels == 0) return;
                bar.setValue(oldValue + pixels);
                if (bar.getValue() == oldValue) continue;
                remainder = movement - pixels;
                event.consume();
                return;
            }
        }
    }
}
