/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.monflabs.mozilla.debugger.ui.panel;

import java.awt.BorderLayout;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;
import javax.swing.DefaultListModel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;
import org.monflabs.mozilla.debugger.ui.model.ScriptInfo;

/**
 * The list of scripts, one row per url (the newest script id for that url wins), like Chrome's file
 * navigator. Selecting one opens it in the source panel.
 */
final class ScriptNavigatorPanel extends JPanel {
    private static final long serialVersionUID = 1L;

    private final transient Map<String, ScriptInfo> byUrl = new LinkedHashMap<>();
    private final DefaultListModel<ScriptInfo> model = new DefaultListModel<>();
    private final transient JList<ScriptInfo> list = new JList<>(model);
    private transient boolean selecting;

    ScriptNavigatorPanel(final Consumer<ScriptInfo> onOpen) {
        super(new BorderLayout());
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setCellRenderer(new Renderer());
        list.addListSelectionListener(
                e -> {
                    if (!selecting && !e.getValueIsAdjusting() && list.getSelectedValue() != null) {
                        onOpen.accept(list.getSelectedValue());
                    }
                });
        add(new JScrollPane(list), BorderLayout.CENTER);
    }

    /** Selects the row for a script, if present, without re-firing {@code onOpen}. */
    void select(final ScriptInfo script) {
        if (script == null || script.url() == null) {
            return;
        }
        for (int i = 0; i < model.size(); i++) {
            if (model.get(i).url().equals(script.url())) {
                if (i != list.getSelectedIndex()) {
                    selecting = true;
                    try {
                        list.setSelectedIndex(i);
                        list.ensureIndexIsVisible(i);
                    } finally {
                        selecting = false;
                    }
                }
                return;
            }
        }
    }

    /** Adds or refreshes a script row, keyed by url. */
    void add(final ScriptInfo script) {
        if (script.url() == null) {
            return;
        }
        final boolean known = byUrl.containsKey(script.url());
        byUrl.put(script.url(), script);
        if (!known) {
            model.addElement(script);
        } else {
            for (int i = 0; i < model.size(); i++) {
                if (model.get(i).url().equals(script.url())) {
                    model.set(i, script);
                    break;
                }
            }
        }
    }

    /** Clears the list (a fresh attach). */
    void clear() {
        byUrl.clear();
        model.clear();
    }

    private static final class Renderer extends javax.swing.DefaultListCellRenderer {
        private static final long serialVersionUID = 1L;

        @Override
        public java.awt.Component getListCellRendererComponent(
                final JList<?> list,
                final Object value,
                final int index,
                final boolean selected,
                final boolean focus) {
            super.getListCellRendererComponent(list, value, index, selected, focus);
            if (value instanceof ScriptInfo script) {
                setText(script.shortName());
                setToolTipText(script.url());
            }
            return this;
        }
    }
}
