/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.monflabs.mozilla.playground.ui;

import java.awt.Desktop;
import java.awt.Font;
import java.io.IOException;
import java.net.URISyntaxException;
import javax.swing.JEditorPane;
import javax.swing.JScrollPane;
import javax.swing.event.HyperlinkEvent;
import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;

/** A sample's README, rendered from Markdown. */
public final class ReadmePane extends JScrollPane {
    private static final long serialVersionUID = 1L;

    private final JEditorPane pane = new JEditorPane();
    private final transient Parser parser = Parser.builder().build();
    private final transient HtmlRenderer renderer = HtmlRenderer.builder().build();
    private final boolean dark;

    /**
     * Creates the pane.
     *
     * @param dark whether the theme is dark, for the colours
     */
    public ReadmePane(boolean dark) {
        this.dark = dark;
        pane.setEditable(false);
        pane.setContentType("text/html");
        pane.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, Boolean.TRUE);
        pane.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        pane.addHyperlinkListener(
                e -> {
                    if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED
                            && e.getURL() != null
                            && Desktop.isDesktopSupported()) {
                        try {
                            Desktop.getDesktop().browse(e.getURL().toURI());
                        } catch (IOException
                                | URISyntaxException
                                | UnsupportedOperationException ignored) {
                            // no browser then
                        }
                    }
                });
        setViewportView(pane);
    }

    /**
     * Shows a README.
     *
     * @param markdown the text, or null for none
     */
    public void show(String markdown) {
        Node document = parser.parse(markdown == null ? "" : markdown);
        String fg = dark ? "#d0d0d0" : "#202020";
        String code = dark ? "#2b2b2b" : "#f2f2f2";
        pane.setText(
                "<html><head><style>body{font-family:sans-serif;font-size:13px;color:"
                        + fg
                        + ";margin:10px}"
                        + "code{background:"
                        + code
                        + ";padding:1px 3px}pre{background:"
                        + code
                        + ";padding:8px}"
                        + "h1{font-size:18px}h2{font-size:15px}h3{font-size:13px}</style></head><body>"
                        + renderer.render(document)
                        + "</body></html>");
        pane.setCaretPosition(0);
    }
}
