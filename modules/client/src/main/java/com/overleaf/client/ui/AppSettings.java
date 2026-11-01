package com.overleaf.client.ui;

import com.overleaf.client.ui.backend.LocalBackend;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * User preferences shown in the Settings page. Values are small strings kept in the local settings
 * file; listeners hear about every change so the open screens can restyle themselves without a
 * restart.
 */
public final class AppSettings {

    public static final String MODE = "mode"; // light | dark | system
    public static final String THEME = "theme"; // forest | mint | bee | parchment
    public static final String FONT_STYLE = "fontStyle"; // sans | serif
    public static final String CONTENT_WIDTH = "contentWidth"; // compact | standard | wide
    public static final String EDITOR_FONT_SIZE = "editorFontSize";
    public static final String EDITOR_FONT = "editorFont"; // mono | menlo | courier
    public static final String WORD_WRAP = "wordWrap";
    public static final String AUTO_CLOSE = "autoClose";
    public static final String AUTO_SAVE = "autoSave";
    public static final String COMPILER = "compiler";
    public static final String LATEXMK_PATH = "latexmkPath";
    public static final String COMPILE_TIMEOUT = "compileTimeoutSeconds";
    public static final String OUTLINE_SPLIT = "outlineSplit";
    public static final String LANGUAGE = "language"; // en | vi

    private static final String[][] DEFAULTS = {
        {MODE, "light"},
        {THEME, "forest"},
        {FONT_STYLE, "sans"},
        {CONTENT_WIDTH, "standard"},
        {EDITOR_FONT_SIZE, "13"},
        {EDITOR_FONT, "mono"},
        {WORD_WRAP, "false"},
        {AUTO_CLOSE, "true"},
        {AUTO_SAVE, "false"},
        {COMPILER, "pdfLaTeX"},
        {LANGUAGE, "en"},
        {LATEXMK_PATH, ""},
        {COMPILE_TIMEOUT, "120"},
        {OUTLINE_SPLIT, "0.60"}
    };

    public com.overleaf.client.compiler.LatexCompilerService.Options compileOptions() {
        long timeout;
        try {
            timeout = Long.parseLong(get(COMPILE_TIMEOUT));
        } catch (NumberFormatException invalid) {
            timeout = 120;
        }
        return new com.overleaf.client.compiler.LatexCompilerService.Options(
                com.overleaf.client.compiler.LatexToolchain.resolve(get(LATEXMK_PATH)), timeout);
    }

    private final LocalBackend store;
    private final List<Consumer<String>> listeners = new ArrayList<>();

    public AppSettings(LocalBackend store) {
        this.store = store;
    }

    public com.overleaf.client.ui.backend.ProjectGateway projects() {
        return store;
    }

    public String get(String key) {
        String fallback = "";
        for (String[] pair : DEFAULTS) {
            if (pair[0].equals(key)) fallback = pair[1];
        }
        try {
            return store.preference(key, fallback);
        } catch (RuntimeException unreadable) {
            return fallback;
        }
    }

    public boolean bool(String key) {
        return Boolean.parseBoolean(get(key));
    }

    public int integer(String key) {
        try {
            return Integer.parseInt(get(key));
        } catch (NumberFormatException bad) {
            return 13;
        }
    }

    public void set(String key, String value) {
        if (value.equals(get(key))) return;
        try {
            store.setPreference(key, value);
        } catch (RuntimeException unwritable) {
            System.err.println(
                    "[WARN] Could not save setting " + key + ": " + unwritable.getMessage());
        }
        for (Consumer<String> listener : List.copyOf(listeners)) {
            listener.accept(key);
        }
    }

    public void set(String key, boolean value) {
        set(key, Boolean.toString(value));
    }

    public void resetAll() {
        for (String[] pair : DEFAULTS) {
            set(pair[0], pair[1]);
        }
    }

    /** Called with the key that changed, on the thread that changed it. */
    public void onChange(Consumer<String> listener) {
        listeners.add(listener);
    }
}
