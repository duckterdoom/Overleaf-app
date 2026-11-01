package com.overleaf.client.compiler;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/** Finds an installed latexmk; never downloads tools or changes saved preferences. */
public final class LatexToolchain {
    private LatexToolchain() { }

    public static String resolve(String configured) {
        return resolve(configured, System.getenv(), Path.of("").toAbsolutePath(),
                System.getProperty("os.name").startsWith("Windows"), Files::isRegularFile);
    }

    static String resolve(String configured, Map<String, String> environment, Path workspace,
                          boolean windows, Predicate<Path> installed) {
        if (configured != null && !configured.isBlank()) return configured.strip();
        String override = environment.getOrDefault("OVERLEAF_LATEXMK", "");
        if (!override.isBlank()) return override.strip();
        String executable = windows ? "latexmk.exe" : "latexmk";
        String path = environment.entrySet().stream().filter(e -> e.getKey().equalsIgnoreCase("PATH"))
                .map(Map.Entry::getValue).findFirst().orElse("");
        List<Path> candidates = new ArrayList<>();
        for (String entry : path.split(Pattern.quote(windows ? ";" : ":"))) {
            if (!entry.isBlank()) {
                try { candidates.add(Path.of(entry.replace("\"", "")).resolve(executable)); }
                catch (RuntimeException invalid) { /* Ignore a malformed PATH entry. */ }
            }
        }
        // Launching from either the repository root or the client module is supported.
        for (Path root = workspace.toAbsolutePath().normalize(); root != null; root = root.getParent()) {
            candidates.add(root.resolve("target/TinyTeX/bin/" + (windows ? "windows" : "x86_64-linux") + "/" + executable));
        }
        if (windows) {
            add(candidates, environment, "APPDATA", "TinyTeX/bin/windows/latexmk.exe");
            add(candidates, environment, "LOCALAPPDATA", "Programs/MiKTeX/miktex/bin/x64/latexmk.exe");
            add(candidates, environment, "ProgramFiles", "MiKTeX/miktex/bin/x64/latexmk.exe");
        } else {
            candidates.add(Path.of("/Library/TeX/texbin/latexmk"));
        }
        return candidates.stream().filter(installed).findFirst().map(p -> p.toAbsolutePath().normalize().toString())
                .orElse("latexmk");
    }

    private static void add(List<Path> paths, Map<String, String> env, String key, String suffix) {
        String base = env.get(key);
        if (base != null && !base.isBlank()) paths.add(Path.of(base).resolve(suffix));
    }
}
