/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.monflabs.mozilla.playground.cheerpj;

import javax.swing.JFrame;
import org.monflabs.mozilla.playground.Playground;

/**
 * The playground as CheerpJ runs it in a web page: the desktop application, undecorated and
 * maximized so that it fills the page, and interpreted: the call sites of the compiled classes
 * (invokedynamic, linked by Dynalink) fail there with "uninitialized call site". The page passes
 * {@code --dark} or {@code --light}.
 */
public final class PlaygroundCheerpJ {
    private PlaygroundCheerpJ() {}

    /**
     * Opens the playground over the whole page.
     *
     * @param args {@code --dark} or {@code --light} to force the theme; otherwise the system's
     */
    public static void main(String[] args) {
        Playground.open(
                args,
                frame -> {
                    frame.setUndecorated(true);
                    frame.interpretedOnly(
                            "Interpreted only in the browser: CheerpJ cannot link the call sites"
                                    + " (invokedynamic) of the compiled classes");
                    frame.setExtendedState(JFrame.MAXIMIZED_BOTH);
                });
    }
}
