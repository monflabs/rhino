/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.monflabs.mozilla.playground.ui;

import java.awt.BorderLayout;
import java.util.Enumeration;
import java.util.Locale;
import java.util.function.Consumer;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.JTree;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;
import org.monflabs.mozilla.playground.Sample;
import org.monflabs.mozilla.playground.SampleLibrary;

/** The library as a tree, with a filter box above it. */
public final class SampleTree extends JPanel {
    private static final long serialVersionUID = 1L;

    /** The scratchpad's id. */
    public static final String SCRATCH_ID = "_scratch";

    private final transient SampleLibrary library;
    private final transient Sample scratch;
    private final JTree tree = new JTree();
    private final JTextField filter = new JTextField();
    private transient Consumer<Sample> onSelect = s -> {};
    private boolean selecting;

    /** A tree node's payload. */
    private record Item(String name, Sample sample) {
        @Override
        public String toString() {
            return name;
        }
    }

    /**
     * Creates the tree.
     *
     * @param library the library
     * @param scratch the scratchpad sample, shown first
     */
    public SampleTree(SampleLibrary library, Sample scratch) {
        super(new BorderLayout());
        this.library = library;
        this.scratch = scratch;
        filter.putClientProperty("JTextField.placeholderText", "Filter samples");
        filter.getDocument()
                .addDocumentListener(
                        new DocumentListener() {
                            @Override
                            public void insertUpdate(DocumentEvent e) {
                                rebuild();
                            }

                            @Override
                            public void removeUpdate(DocumentEvent e) {
                                rebuild();
                            }

                            @Override
                            public void changedUpdate(DocumentEvent e) {
                                rebuild();
                            }
                        });
        add(filter, BorderLayout.NORTH);
        tree.setRootVisible(false);
        tree.setShowsRootHandles(true);
        tree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
        tree.addTreeSelectionListener(
                e -> {
                    if (selecting) {
                        return;
                    }
                    Object node = tree.getLastSelectedPathComponent();
                    if (node instanceof DefaultMutableTreeNode n
                            && n.getUserObject() instanceof Item item
                            && item.sample() != null) {
                        onSelect.accept(item.sample());
                    }
                });
        add(new JScrollPane(tree), BorderLayout.CENTER);
        rebuild();
    }

    /** What to do when a sample is chosen. */
    public void onSelect(Consumer<Sample> action) {
        this.onSelect = action;
    }

    private void rebuild() {
        String needle = filter.getText().trim().toLowerCase(Locale.ROOT);
        DefaultMutableTreeNode root = new DefaultMutableTreeNode(new Item("Samples", null));
        root.add(new DefaultMutableTreeNode(new Item(scratch.title(), scratch)));
        for (SampleLibrary.Node child : library.root().children()) {
            DefaultMutableTreeNode n = build(child, needle);
            if (n != null) {
                root.add(n);
            }
        }
        tree.setModel(new DefaultTreeModel(root));
        if (!needle.isEmpty()) {
            for (int i = 0; i < tree.getRowCount(); i++) {
                tree.expandRow(i);
            }
        } else {
            for (int i = 0; i < root.getChildCount(); i++) {
                tree.expandPath(
                        new TreePath(((DefaultMutableTreeNode) root.getChildAt(i)).getPath()));
            }
        }
    }

    private static DefaultMutableTreeNode build(SampleLibrary.Node node, String needle) {
        if (node.isSample()) {
            return needle.isEmpty()
                            || node.name().toLowerCase(Locale.ROOT).contains(needle)
                            || node.sample().source().toLowerCase(Locale.ROOT).contains(needle)
                    ? new DefaultMutableTreeNode(new Item(node.name(), node.sample()))
                    : null;
        }
        DefaultMutableTreeNode n = new DefaultMutableTreeNode(new Item(node.name(), null));
        for (SampleLibrary.Node child : node.children()) {
            DefaultMutableTreeNode c = build(child, needle);
            if (c != null) {
                n.add(c);
            }
        }
        return n.getChildCount() == 0 ? null : n;
    }

    /**
     * Selects a sample by id, if it is in the tree.
     *
     * @param id the sample id
     * @return true if found
     */
    public boolean select(String id) {
        DefaultMutableTreeNode root = (DefaultMutableTreeNode) tree.getModel().getRoot();
        for (Enumeration<?> e = root.depthFirstEnumeration(); e.hasMoreElements(); ) {
            DefaultMutableTreeNode n = (DefaultMutableTreeNode) e.nextElement();
            if (n.getUserObject() instanceof Item item
                    && item.sample() != null
                    && item.sample().id().equals(id)) {
                TreePath path = new TreePath(n.getPath());
                selecting = true;
                try {
                    tree.setSelectionPath(path);
                    tree.scrollPathToVisible(path);
                } finally {
                    selecting = false;
                }
                onSelect.accept(item.sample());
                return true;
            }
        }
        return false;
    }
}
