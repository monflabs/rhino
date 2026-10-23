/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.monflabs.mozilla.playground.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextPane;
import javax.swing.SwingUtilities;
import javax.swing.text.BadLocationException;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import org.monflabs.mozilla.playground.ScriptRunner;

/**
 * The console: output in the text colour, errors in red, and a status line beneath. Fed from the
 * worker thread, painted on the event thread, in batches.
 */
public final class ConsolePane extends JPanel implements ScriptRunner.Console {
    private static final long serialVersionUID = 1L;
    private static final int LIMIT = 400_000;

    /** A pending piece of output. */
    private record Chunk(SimpleAttributeSet style, String text) {}

    private final JTextPane text =
            new JTextPane() {
                private static final long serialVersionUID = 1L;

                @Override
                public boolean getScrollableTracksViewportWidth() {
                    return wrap
                            || getParent() == null
                            || getUI().getPreferredSize(this).width <= getParent().getSize().width;
                }
            };
    private final JLabel status = new JLabel(" ");
    private final SimpleAttributeSet plain = new SimpleAttributeSet();
    private final SimpleAttributeSet error = new SimpleAttributeSet();
    private final transient List<Chunk> pending = new ArrayList<>();
    private boolean flushScheduled;
    private boolean wrap = true;

    /**
     * Creates the pane.
     *
     * @param font the monospaced font
     */
    public ConsolePane(Font font) {
        super(new BorderLayout());
        text.setEditable(false);
        text.setFont(font);
        StyleConstants.setForeground(error, new Color(0xd0, 0x40, 0x40));
        JScrollPane scroll = new JScrollPane(text);
        scroll.setPreferredSize(new Dimension(400, 200));
        add(scroll, BorderLayout.CENTER);
        status.setBorder(BorderFactory.createEmptyBorder(3, 8, 3, 8));
        add(status, BorderLayout.SOUTH);
    }

    @Override
    public void out(String s) {
        append(new Chunk(plain, s));
    }

    @Override
    public void err(String s) {
        append(new Chunk(error, s));
    }

    private void append(Chunk chunk) {
        synchronized (pending) {
            pending.add(chunk);
            if (!flushScheduled) {
                flushScheduled = true;
                SwingUtilities.invokeLater(this::flush);
            }
        }
    }

    private void flush() {
        List<Chunk> chunks;
        synchronized (pending) {
            chunks = new ArrayList<>(pending);
            pending.clear();
            flushScheduled = false;
        }
        StyledDocument doc = text.getStyledDocument();
        try {
            for (Chunk chunk : chunks) {
                doc.insertString(doc.getLength(), chunk.text(), chunk.style());
            }
            if (doc.getLength() > LIMIT) {
                doc.remove(0, doc.getLength() - LIMIT * 3 / 4);
            }
        } catch (BadLocationException e) {
            // the document is ours; cannot happen
        }
        text.setCaretPosition(doc.getLength());
    }

    /** Empties the console. */
    public void clear() {
        synchronized (pending) {
            pending.clear();
        }
        text.setText("");
    }

    /** Sets the status line. */
    public void status(String s) {
        SwingUtilities.invokeLater(() -> status.setText(s));
    }

    /** Whether long lines wrap. */
    public void setWrap(boolean wrap) {
        this.wrap = wrap;
        text.revalidate();
        text.repaint();
    }
}
