package com.tiltus.bevywizard.component;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.net.URL;

/** Loads the TiltUI component icon at the standard project-menu size. */
final class TiltComponentIcon implements Icon {
    static final Icon INSTANCE = new TiltComponentIcon();
    private static final int SIZE = 16;
    private final BufferedImage icon;

    private TiltComponentIcon() {
        icon = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
        URL resource = TiltComponentIcon.class.getResource("/icons/tilt-component.png");
        if (resource == null) return;
        Graphics2D graphics = icon.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.drawImage(new ImageIcon(resource).getImage(), 0, 0, SIZE, SIZE, null);
        } finally {
            graphics.dispose();
        }
    }

    /** Returns the standard project-menu icon width. */
    @Override public int getIconWidth() { return SIZE; }

    /** Returns the standard project-menu icon height. */
    @Override public int getIconHeight() { return SIZE; }

    /** Paints the prepared icon and keeps the badge's plus crisp at sixteen pixels. */
    @Override public void paintIcon(Component component, Graphics graphics, int x, int y) {
        Graphics painted = graphics.create();
        try {
            painted.drawImage(icon, x, y, component);
            painted.setColor(Color.WHITE);
            painted.drawLine(x + 12, y + 11, x + 12, y + 14);
            painted.drawLine(x + 10, y + 12, x + 14, y + 12);
        } finally {
            painted.dispose();
        }
    }
}
