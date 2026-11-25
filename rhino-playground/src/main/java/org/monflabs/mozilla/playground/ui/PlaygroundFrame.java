/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.monflabs.mozilla.playground.ui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.io.Serial;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.function.Consumer;
import java.util.prefs.Preferences;
import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JToolBar;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import org.monflabs.mozilla.api.debugger.Debugger;
import org.monflabs.mozilla.api.debugger.InspectOptions;
import org.monflabs.mozilla.debugger.CdpServer;
import org.monflabs.mozilla.debugger.inprocess.InProcessCdpServer;
import org.monflabs.mozilla.debugger.ui.DebuggerPanel;
import org.monflabs.mozilla.playground.Sample;
import org.monflabs.mozilla.playground.SampleLibrary;
import org.monflabs.mozilla.playground.ScriptRunner;

/**
 * The window: the samples on the left; the editor beside the console, over the README, on the
 * right; the actions across the top.
 */
public final class PlaygroundFrame extends JFrame {
    @Serial private static final long serialVersionUID = 1L;
    private static final Path SCRATCH_FILE =
            Path.of(System.getProperty("user.home"), ".rhino-playground", "scratch.js");
    private static final String SCRATCH_DEFAULT =
            "// Scratchpad - your own code goes here, and is kept between sessions.\n"
                    + "var greeting = 'Hello';\nprint(greeting + ', Rhino!');\n";
    private static final String COMPILED = "Compiled";
    private static final String INTERPRETED = "Interpreted";

    private final transient Preferences prefs =
            Preferences.userNodeForPackage(PlaygroundFrame.class);
    private final transient ScriptRunner runner = new ScriptRunner();
    private final SampleTree tree;
    private final EditorPane editor;
    private final ConsolePane console;
    private final ReadmePane readme;
    private final JSplitPane consoleSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
    private final JSplitPane readmeSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
    private final JButton runButton = new JButton("Run");
    private final JButton stopButton = new JButton("Stop");
    private final JButton debugButton = new JButton("Debug");
    private final JButton externalButton = new JButton("External debugger");
    private final JComboBox<ScriptRunner.Version> version =
            new JComboBox<>(ScriptRunner.Version.values());
    private final JComboBox<String> mode = new JComboBox<>(new String[] {COMPILED, INTERPRETED});
    private final JCheckBox autoRun = new JCheckBox("Auto-run", true);
    private final JCheckBox wordWrap = new JCheckBox("Word wrap", true);
    private final JCheckBox preserve = new JCheckBox("Preserve console", false);
    private final Timer autoRunTimer = new Timer(500, e -> run());
    private final Timer scratchTimer = new Timer(1000, e -> saveScratch());
    private final transient Font mono;
    private final boolean dark;
    private transient AutoCloseable debugServer;
    private transient JFrame debugWindow;
    private transient Runnable debugWindowClose;
    private transient Sample sample;
    private transient Sample scratch;
    private transient Future<?> queued;
    private transient String initial;
    private transient Consumer<Sample> sampleShown = s -> {};

    /**
     * Creates the window.
     *
     * @param library the samples
     * @param dark whether the theme is dark
     */
    public PlaygroundFrame(SampleLibrary library, boolean dark) {
        super("Rhino Playground");
        this.mono = new Font(Font.MONOSPACED, Font.PLAIN, 13);
        this.dark = dark;
        scratch = loadScratch();
        tree = new SampleTree(library, scratch);
        editor = new EditorPane(this, mono, dark);
        console = new ConsolePane(mono);
        readme = new ReadmePane(dark);
        autoRunTimer.setRepeats(false);
        scratchTimer.setRepeats(false);

        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        add(toolbar(), BorderLayout.NORTH);
        // the console beside the script
        consoleSplit.setLeftComponent(editor);
        consoleSplit.setRightComponent(titled("Console", console));
        consoleSplit.setResizeWeight(0.55);
        readmeSplit.setTopComponent(consoleSplit);
        readmeSplit.setBottomComponent(titled("README", readme));
        readmeSplit.setResizeWeight(0.75);
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, tree, readmeSplit);
        split.setResizeWeight(0.0);
        tree.setMinimumSize(new Dimension(200, 100));
        tree.setPreferredSize(new Dimension(280, 600));
        add(split, BorderLayout.CENTER);

        tree.onSelect(this::show);
        editor.onEdit(this::edited);
        keys();
        loadPrefs();
        split.setDividerLocation(prefs.getInt("divider", 280));
        addWindowListener(
                new WindowAdapter() {
                    @Override
                    public void windowOpened(WindowEvent e) {
                        // the nested splits have their real size only now
                        int consoleDivider = prefs.getInt("consoleDivider", -1);
                        if (consoleDivider > 0) {
                            consoleSplit.setDividerLocation(consoleDivider);
                        } else {
                            consoleSplit.setDividerLocation(0.55);
                        }
                        int readmeDivider = prefs.getInt("readmeDivider", -1);
                        if (readmeDivider > 0) {
                            readmeSplit.setDividerLocation(readmeDivider);
                        } else {
                            readmeSplit.setDividerLocation(0.72);
                        }
                        // the first run, with the settings made before the window was shown
                        Sample asked =
                                initial == null
                                        ? null
                                        : initial.equals(scratch.urlName())
                                                ? scratch
                                                : library.byUrlName(initial);
                        if (asked != null) {
                            tree.select(asked.id());
                        } else if (!tree.select(prefs.get("sample", ""))) {
                            List<Sample> samples = library.samples();
                            if (!samples.isEmpty()) {
                                tree.select(samples.get(0).id());
                            }
                        }
                    }

                    @Override
                    public void windowClosing(WindowEvent e) {
                        prefs.putInt("divider", split.getDividerLocation());
                        prefs.putInt("consoleDivider", consoleSplit.getDividerLocation());
                        prefs.putInt("readmeDivider", readmeSplit.getDividerLocation());
                        saveScratch();
                        savePrefs();
                        runner.close();
                        dispose();
                        System.exit(0);
                    }
                });
    }

    private JComponent toolbar() {
        JToolBar bar = new JToolBar();
        bar.setFloatable(false);
        bar.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));
        runButton.addActionListener(e -> run());
        stopButton.addActionListener(e -> stop());
        stopButton.setEnabled(false);
        debugButton.setToolTipText(
                "Runs the script in the interpreter under a debugger, paused at its first"
                        + " statement");
        debugButton.addActionListener(e -> debug());
        externalButton.setToolTipText(
                "Runs the script paused at its first statement, and serves the Chrome DevTools"
                        + " Protocol on a port, for Chrome DevTools or an IDE");
        externalButton.addActionListener(e -> debugExternal());
        JButton clear = new JButton("Clear console");
        clear.addActionListener(e -> console.clear());
        version.setToolTipText(
                "The language version: ES2026 (Context.VERSION_ECMASCRIPT) or ES6"
                        + " (Context.VERSION_ES6), the behavior of stock Rhino");
        version.setMaximumSize(version.getPreferredSize());
        version.addActionListener(e -> run());
        mode.setToolTipText("Compiled to Java classes, or run by the interpreter");
        mode.setMaximumSize(mode.getPreferredSize());
        mode.addActionListener(e -> run());
        wordWrap.addActionListener(e -> console.setWrap(wordWrap.isSelected()));
        bar.add(runButton);
        bar.add(stopButton);
        bar.add(debugButton);
        bar.add(externalButton);
        bar.addSeparator();
        bar.add(new JLabel("Version "));
        bar.add(version);
        bar.add(Box.createHorizontalStrut(6));
        bar.add(mode);
        bar.addSeparator();
        bar.add(autoRun);
        bar.addSeparator();
        bar.add(clear);
        bar.add(preserve);
        bar.add(wordWrap);
        return bar;
    }

    /** One pane wrapped in a single-tab container, so that it carries a title. */
    private static JTabbedPane titled(String title, JComponent pane) {
        JTabbedPane tab = new JTabbedPane();
        tab.addTab(title, pane);
        return tab;
    }

    private void keys() {
        int menu = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
        JComponent root = getRootPane();
        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, menu), "run");
        root.getActionMap()
                .put(
                        "run",
                        new AbstractAction() {
                            @Serial private static final long serialVersionUID = 1L;

                            @Override
                            public void actionPerformed(ActionEvent e) {
                                run();
                            }
                        });
        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "stop");
        root.getActionMap()
                .put(
                        "stop",
                        new AbstractAction() {
                            @Serial private static final long serialVersionUID = 1L;

                            @Override
                            public void actionPerformed(ActionEvent e) {
                                stop();
                            }
                        });
    }

    // -- samples ----------------------------------------------------------------

    /**
     * Opens a sample when the window opens, instead of the last one shown.
     *
     * @param urlName a name of {@link Sample#urlName()}, or null; an unknown one is ignored
     */
    public void openFirst(String urlName) {
        initial = urlName;
    }

    /**
     * Tells a listener each sample shown, for a host that shows it elsewhere: the browser build
     * puts its name in the page's URL.
     *
     * @param listener called on the event dispatch thread
     */
    public void onSampleShown(Consumer<Sample> listener) {
        sampleShown = listener;
    }

    private void show(Sample s) {
        if (sample != null && sample == scratch) {
            saveScratch();
        }
        sample = s;
        sampleShown.accept(s);
        editor.show(s.source(), s.files(), true);
        readme.show(s.readme() != null ? s.readme() : "## " + s.title());
        setTitle("Rhino Playground - " + s.title());
        prefs.put("sample", s.id());
        if (autoRun.isSelected() && debugWindow == null) {
            run();
        }
    }

    private void edited() {
        if (sample == scratch) {
            scratchTimer.restart();
        }
        if (autoRun.isSelected() && debugWindow == null) {
            autoRunTimer.restart();
        }
    }

    private Sample loadScratch() {
        String text = SCRATCH_DEFAULT;
        try {
            if (Files.isRegularFile(SCRATCH_FILE)) {
                text = Files.readString(SCRATCH_FILE, StandardCharsets.UTF_8);
            }
        } catch (IOException | SecurityException ignored) {
            // the default then
        }
        return new Sample(
                SampleTree.SCRATCH_ID,
                List.of(),
                "Scratchpad",
                text,
                "# Scratchpad\n\nYour own code. It is saved in `"
                        + SCRATCH_FILE
                        + "` and kept between sessions.\n\n"
                        + "Run with **Run** or ⌘/Ctrl-Enter; **Stop** or Esc ends a script"
                        + " that runs away.",
                Map.of());
    }

    private void saveScratch() {
        if (sample != scratch) {
            return;
        }
        String text = editor.getSource();
        scratch = scratch.withSource(text);
        sample = scratch;
        try {
            Files.createDirectories(SCRATCH_FILE.getParent());
            Files.writeString(SCRATCH_FILE, text, StandardCharsets.UTF_8);
        } catch (IOException | SecurityException ignored) {
            // nowhere to keep it
        }
    }

    // -- running ------------------------------------------------------------------

    /**
     * Runs every script in the interpreter, the mode choice disabled: for a runtime that cannot run
     * the classes of the compiler.
     *
     * @param why the tooltip of the disabled choice
     */
    public void interpretedOnly(String why) {
        mode.setSelectedItem(INTERPRETED);
        mode.setEnabled(false);
        mode.setToolTipText(why);
    }

    private ScriptRunner.Settings settings() {
        return new ScriptRunner.Settings(
                (ScriptRunner.Version) version.getSelectedItem(),
                INTERPRETED.equals(mode.getSelectedItem()));
    }

    private void run() {
        closeDebugWindow();
        if (debugServer != null) {
            return;
        }
        start(false, null);
    }

    /** Starts a run; a debug run is given what to do when it ended. */
    private void start(boolean debug, Runnable ended) {
        if (sample == null) {
            return;
        }
        autoRunTimer.stop();
        if (queued != null) {
            queued.cancel(false);
        }
        String source = editor.getSource();
        ScriptRunner.Settings settings = settings();
        if (!preserve.isSelected()) {
            console.clear();
        }
        ScriptRunner.Listener listener =
                new ScriptRunner.Listener() {
                    @Override
                    public void started() {
                        SwingUtilities.invokeLater(
                                () -> {
                                    runButton.setEnabled(false);
                                    externalButton.setEnabled(false);
                                    stopButton.setEnabled(true);
                                    console.status("Running…");
                                });
                    }

                    @Override
                    public void finished(ScriptRunner.Result result) {
                        if (result.failure() != null) {
                            console.err(ScriptRunner.describe(result.failure()) + "\n");
                        }
                        SwingUtilities.invokeLater(
                                () -> {
                                    runButton.setEnabled(true);
                                    externalButton.setEnabled(true);
                                    stopButton.setEnabled(false);
                                    console.status(
                                            (result.terminated()
                                                            ? "Stopped after "
                                                            : result.failure() != null
                                                                    ? "Failed after "
                                                                    : "Done in ")
                                                    + result.millis()
                                                    + " ms ("
                                                    + settings.describe()
                                                    + ")");
                                });
                        if (ended != null) {
                            SwingUtilities.invokeLater(ended);
                        }
                    }
                };
        Sample run = sample.withSource(source);
        queued =
                debug
                        ? runner.debug(run, source, settings, console, listener)
                        : runner.run(run, source, settings, console, listener);
    }

    /** Debugs in a window of this process: no socket, the front end attaches in memory. */
    private void debug() {
        runner.stop();
        closeDebugWindow();
        closeDebugServer();
        autoRunTimer.stop();
        Debugger debugger = runner.debugger();
        debugger.pauseOnStart();
        InProcessCdpServer.Handle handle =
                InProcessCdpServer.open(debugger, InspectOptions.parse("", false));
        DebuggerPanel panel = new DebuggerPanel(mono, dark);
        JFrame window = new JFrame("Rhino Debugger - " + sample.title());
        window.setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        window.add(panel);
        window.setSize(1100, 750);
        window.setLocationRelativeTo(this);
        debugServer = handle;
        debugWindow = window;
        debugWindowClose =
                () -> {
                    panel.close();
                    closeDebugServer();
                    window.dispose();
                };
        boolean[] started = {false};
        panel.onConnectionChange(
                (state, detail) -> {
                    if (state == DebuggerPanel.ConnectionState.CONNECTED && !started[0]) {
                        started[0] = true;
                        start(true, null);
                    }
                });
        window.addWindowListener(
                new WindowAdapter() {
                    @Override
                    public void windowClosing(WindowEvent e) {
                        runner.stop();
                        closeDebugWindow();
                    }
                });
        window.setVisible(true);
        panel.attach(handle.clientChannel());
    }

    /** Closes the debug window of an earlier session, finished or not. */
    private void closeDebugWindow() {
        Runnable close = debugWindowClose;
        debugWindowClose = null;
        debugWindow = null;
        if (close != null) {
            close.run();
        }
    }

    /** Debugs from another tool: the script waits at its first statement for a client. */
    private void debugExternal() {
        if (runner.isRunning() || debugServer != null) {
            return;
        }
        String port =
                JOptionPane.showInputDialog(
                        this, "Port of the DevTools server:", prefs.get("debugPort", "9229"));
        if (port == null) {
            return;
        }
        try {
            prefs.put("debugPort", port.strip());
            Debugger debugger = runner.debugger();
            debugger.pauseOnStart();
            CdpServer.Handle handle =
                    CdpServer.open(
                            debugger, InspectOptions.parse("127.0.0.1:" + port.strip(), false));
            debugServer = handle;
            console.clear();
            console.out(
                    "Waiting for a debugger at the first statement.\n"
                            + "Chrome: chrome://inspect, Configure, add 127.0.0.1:"
                            + port.strip()
                            + "\nWebSocket: "
                            + handle.webSocketUrl()
                            + "\n");
            start(true, this::closeDebugServer);
        } catch (Exception e) {
            closeDebugServer();
            console.err("Cannot start the debugger: " + e.getMessage() + "\n");
        }
    }

    private void closeDebugServer() {
        AutoCloseable server = debugServer;
        debugServer = null;
        if (server != null) {
            try {
                server.close();
            } catch (Exception ignored) {
                // gone already
            }
        }
    }

    private void stop() {
        runner.stop();
    }

    // -- preferences ----------------------------------------------------------------

    private void loadPrefs() {
        setSize(prefs.getInt("width", 1200), prefs.getInt("height", 800));
        int x = prefs.getInt("x", Integer.MIN_VALUE);
        if (x == Integer.MIN_VALUE) {
            setLocationRelativeTo(null);
        } else {
            setLocation(x, prefs.getInt("y", 0));
        }
        autoRun.setSelected(prefs.getBoolean("autoRun", true));
        wordWrap.setSelected(prefs.getBoolean("wordWrap", true));
        console.setWrap(wordWrap.isSelected());
        preserve.setSelected(prefs.getBoolean("preserve", false));
        try {
            version.setSelectedItem(
                    ScriptRunner.Version.valueOf(
                            prefs.get("version", ScriptRunner.Version.ES2026.name())));
        } catch (IllegalArgumentException e) {
            version.setSelectedItem(ScriptRunner.Version.ES2026);
        }
        mode.setSelectedItem(prefs.getBoolean("interpreted", false) ? INTERPRETED : COMPILED);
    }

    private void savePrefs() {
        prefs.putInt("width", getWidth());
        prefs.putInt("height", getHeight());
        prefs.putInt("x", getX());
        prefs.putInt("y", getY());
        prefs.putBoolean("autoRun", autoRun.isSelected());
        prefs.putBoolean("wordWrap", wordWrap.isSelected());
        prefs.putBoolean("preserve", preserve.isSelected());
        prefs.put("version", ((ScriptRunner.Version) version.getSelectedItem()).name());
        prefs.putBoolean("interpreted", INTERPRETED.equals(mode.getSelectedItem()));
    }
}
