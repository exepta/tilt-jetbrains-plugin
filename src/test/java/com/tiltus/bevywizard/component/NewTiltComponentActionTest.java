package com.tiltus.bevywizard.component;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Guards the threading contract for context-menu updates that inspect project files. */
final class NewTiltComponentActionTest {
    @Test void updatesTheContextMenuOffTheUiThread() {
        assertEquals(ActionUpdateThread.BGT, new NewTiltComponentAction().getActionUpdateThread());
    }

    @Test void usesTheTiltUiLogoWithAVisibleGreenCreationBadge() {
        assertNotNull(getClass().getResource("/icons/tilt-component.png"));
        assertEquals(16, TiltComponentIcon.INSTANCE.getIconWidth());
        BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        var graphics = image.createGraphics();
        TiltComponentIcon.INSTANCE.paintIcon(null, graphics, 0, 0);
        graphics.dispose();
        boolean green = false;
        for (int y = 8; y < 16; y++) for (int x = 8; x < 16; x++) {
            int pixel = image.getRGB(x, y);
            green |= ((pixel >> 8) & 0xff) > ((pixel >> 16) & 0xff) + 40;
        }
        assertTrue(green);
    }
}
