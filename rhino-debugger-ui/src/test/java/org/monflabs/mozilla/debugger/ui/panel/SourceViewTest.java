/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.monflabs.mozilla.debugger.ui.panel;

import static org.monflabs.mozilla.debugger.ui.test.TestNgAsserts.assertEquals;
import static org.monflabs.mozilla.debugger.ui.test.TestNgAsserts.assertTrue;

import java.awt.Font;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.monflabs.mozilla.debugger.ui.model.Breakpoint;

/**
 * The source view headless: the gutter toggle reaches the callback once, the model reconciles dots,
 * and the execution pointer leaves nothing behind.
 */
public class SourceViewTest {

    private static SourceView view(final List<String> toggles) {
        return new SourceView(
                "file:///work/x.js",
                "var a = 1;\nvar b = 2;\nvar c = 3;\n",
                new Font(Font.MONOSPACED, Font.PLAIN, 12),
                false,
                (url, line) -> toggles.add(url + ":" + line));
    }

    @Test
    public void aGutterToggleReachesTheCallbackOnce() throws Exception {
        final List<String> toggles = new ArrayList<>();
        final SourceView view = view(toggles);
        // a user click is what RSTA turns into toggleBookmark; drive it directly
        view.gutter().toggleBookmark(1);
        assertEquals(toggles, List.of("file:///work/x.js:1"));
    }

    @Test
    public void syncingBreakpointsDoesNotLoopBackAsToggles() throws Exception {
        final List<String> toggles = new ArrayList<>();
        final SourceView view = view(toggles);
        view.syncBreakpoints(
                List.of(new Breakpoint("file:///work/x.js", 2, null, true, "1", List.of())));
        // the dot was placed by the model, not the user: no callback
        assertTrue(toggles.isEmpty(), "unexpected toggles: " + toggles);
        assertEquals(view.gutter().getBookmarks().length, 1);
    }

    @Test
    public void aResolvedLineMovesTheDot() throws Exception {
        final List<String> toggles = new ArrayList<>();
        final SourceView view = view(toggles);
        // requested line 0 but the server resolved it to line 1
        view.syncBreakpoints(
                List.of(new Breakpoint("file:///work/x.js", 0, null, true, "1", List.of(1))));
        assertEquals(view.gutter().getBookmarks().length, 1);
        assertEquals(
                view.textArea().getLineOfOffset(view.gutter().getBookmarks()[0].getMarkedOffset()),
                1);
    }

    @Test
    public void aBreakpointOnThePausedLineBecomesOneCombinedGlyph() {
        final List<String> toggles = new ArrayList<>();
        final SourceView view = view(toggles);
        view.setExecutionLine(1);
        // a breakpoint arrives on the very line the engine is paused on: the plain
        // dot is suppressed and a single arrow-over-dot glyph stands for both, so
        // there is exactly one icon on the line rather than two fighting for it
        view.syncBreakpoints(
                List.of(new Breakpoint("file:///work/x.js", 1, null, true, "1", List.of())));
        assertEquals(view.gutter().getBookmarks().length, 0);
        assertTrue(view.hasExecutionMarker(), "the combined execution glyph is missing");
    }

    @Test
    public void aBreakpointOnAnotherLineKeepsItsDotWhilePaused() throws Exception {
        final List<String> toggles = new ArrayList<>();
        final SourceView view = view(toggles);
        view.setExecutionLine(0);
        view.syncBreakpoints(
                List.of(new Breakpoint("file:///work/x.js", 2, null, true, "1", List.of())));
        // the arrow on line 0 and a plain dot on line 2 coexist
        assertTrue(view.hasExecutionMarker());
        assertEquals(view.gutter().getBookmarks().length, 1);
        assertEquals(
                view.textArea().getLineOfOffset(view.gutter().getBookmarks()[0].getMarkedOffset()),
                2);
    }

    @Test
    public void theExecutionPointerSetsAndClears() {
        final SourceView view = view(new ArrayList<>());
        view.setExecutionLine(1);
        view.clearExecutionLine();
        // the arrow lives as a tracking icon, not a bookmark; a cleared pointer leaves the
        // bookmarks alone
        assertEquals(view.gutter().getBookmarks().length, 0);
        // and setting it again does not accumulate
        view.setExecutionLine(2);
        view.setExecutionLine(0);
        view.clearExecutionLine();
        assertEquals(view.gutter().getBookmarks().length, 0);
    }
}
