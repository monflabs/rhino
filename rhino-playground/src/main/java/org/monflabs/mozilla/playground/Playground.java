/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.monflabs.mozilla.playground;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLightLaf;
import com.formdev.flatlaf.util.SystemInfo;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.function.Consumer;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import org.monflabs.mozilla.playground.ui.PlaygroundFrame;

/**
 * The playground: a library of samples, an editor and a console, with the language version and the
 * execution mode to choose. {@code java -jar rhino-playground-all.jar}.
 */
public final class Playground {
    private Playground() {}

    /**
     * Opens the window.
     *
     * @param args {@code --dark} or {@code --light} to force the theme; otherwise the system's
     */
    public static void main(String[] args) {
        open(args, frame -> {});
    }

    /**
     * Opens the window, letting the caller adjust the frame before it is shown: the browser
     * (CheerpJ) build makes it undecorated and maximized, to fill the page.
     *
     * @param args {@code --dark} or {@code --light} to force the theme; otherwise the system's
     * @param beforeShow called on the event dispatch thread with the frame, before it is shown
     */
    public static void open(String[] args, Consumer<? super PlaygroundFrame> beforeShow) {
        boolean dark = theme(args);
        if (SystemInfo.isMacOS) {
            System.setProperty("apple.laf.useScreenMenuBar", "true");
            System.setProperty("apple.awt.application.name", "Rhino Playground");
            System.setProperty("apple.awt.application.appearance", "system");
        }
        SwingUtilities.invokeLater(
                () -> {
                    if (dark) {
                        FlatDarkLaf.setup();
                    } else {
                        FlatLightLaf.setup();
                    }
                    UIManager.put("TextComponent.arc", 6);
                    try {
                        PlaygroundFrame frame = new PlaygroundFrame(SampleLibrary.load(), dark);
                        beforeShow.accept(frame);
                        frame.setVisible(true);
                    } catch (IOException e) {
                        throw new IllegalStateException("cannot load the samples", e);
                    }
                });
    }

    /** Dark or light: the arguments win, then the system's preference. */
    static boolean theme(String[] args) {
        for (String arg : args) {
            if (arg.equals("--dark")) {
                return true;
            }
            if (arg.equals("--light")) {
                return false;
            }
        }
        String forced = System.getProperty("playground.dark");
        if (forced != null) {
            return Boolean.parseBoolean(forced);
        }
        if (SystemInfo.isMacOS) {
            try {
                Process p =
                        new ProcessBuilder("defaults", "read", "-g", "AppleInterfaceStyle")
                                .redirectErrorStream(true)
                                .start();
                String out =
                        new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
                                .trim()
                                .toLowerCase(Locale.ROOT);
                p.waitFor();
                return out.contains("dark");
            } catch (IOException | InterruptedException e) {
                return false;
            }
        }
        return false;
    }
}
