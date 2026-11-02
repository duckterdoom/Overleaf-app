package com.overleaf.client.ui.backend;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDate;
import java.util.*;
import java.util.regex.*;

/** Resource-backed, immutable catalog. Adding a template does not require Java content code. */
public final class ProjectTemplates {
    public record Category(String id, String en, String vi) {
        public String title(boolean vietnamese) {
            return vietnamese ? vi : en;
        }
    }

    public record Text(String en, String vi) {
        public String value(boolean vietnamese) {
            return vietnamese ? vi : en;
        }
    }

    public record Template(
            String id,
            String category,
            String en,
            String vi,
            Text description,
            List<String> tags,
            String engine,
            String mainFile,
            List<String> requiredPackages,
            boolean needsBiber,
            List<String> paths,
            String resource,
            boolean sharedPreamble) {
        public String title(boolean vietnamese) {
            return vietnamese ? vi : en;
        }

        public String description(boolean vietnamese) {
            return description.value(vietnamese);
        }
    }

    private record Catalog(List<Category> categories, Map<String, Template> templates) {}

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{([A-Z_]+)}}");
    private static final Catalog CATALOG = load();

    private ProjectTemplates() {}

    public static List<Category> categories() {
        return CATALOG.categories();
    }

    public static List<Template> all() {
        return CATALOG.templates().values().stream()
                .filter(t -> !t.category().equals("internal"))
                .toList();
    }

    public static Template get(String id) {
        String key = id == null ? "blank" : id;
        Template template = CATALOG.templates().get(key);
        if (template == null)
            template =
                    all().stream().filter(t -> t.category().equals(key)).findFirst().orElse(null);
        if (template == null) throw new IllegalArgumentException("Unknown template: " + id);
        return template;
    }

    public static List<Template> search(String query, String category, String tag) {
        String needle = normalize(query);
        return all().stream()
                .filter(t ->
                        category == null || category.isBlank() || t.category().equals(category))
                .filter(t -> tag == null || tag.isBlank() || t.tags().contains(tag))
                .filter(t ->
                        normalize(
                                        t.en()
                                                + " "
                                                + t.vi()
                                                + " "
                                                + t.description().en()
                                                + " "
                                                + t.description().vi()
                                                + " "
                                                + String.join(" ", t.tags()))
                                .contains(needle))
                .toList();
    }

    private static String normalize(String text) {
        return java.text.Normalizer.normalize(
                        text == null ? "" : text, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replace('đ', 'd')
                .replace('Đ', 'D')
                .toLowerCase(Locale.ROOT)
                .strip();
    }

    /** Text-only compatibility API. Binary assets are copied by materialize(). */
    public static Map<String, String> files(
            String id, String title, boolean vi, String affiliation) {
        Template template = get(id);
        LinkedHashMap<String, String> result = new LinkedHashMap<>();
        Map<String, String> values =
                Map.of(
                        "TITLE",
                        escape(title),
                        "AUTHOR",
                        escape(vi ? "Tác giả Mẫu" : "Example Author"),
                        "AFFILIATION",
                        escape(
                                affiliation == null || affiliation.isBlank()
                                        ? (vi ? "Dự án cá nhân" : "Independent project")
                                        : affiliation),
                        "DATE",
                        escape(LocalDate.now().toString()));
        for (String name : template.paths())
            if (isText(name))
                result.put(
                        name,
                        substitute(
                                new String(
                                        resource(
                                                template.resource()
                                                        + "/"
                                                        + (vi ? "vi" : "en")
                                                        + "/"
                                                        + name),
                                        StandardCharsets.UTF_8),
                                values));
        if (template.sharedPreamble())
            result.put(
                    "preamble.tex",
                    substitute(
                            new String(
                                    resource("common/" + (vi ? "vi" : "en") + "/preamble.tex"),
                                    StandardCharsets.UTF_8),
                            values));
        return Collections.unmodifiableMap(result);
    }

    public static void materialize(
            Path directory, String id, String title, boolean vi, String affiliation)
            throws IOException {
        Template template = get(id);
        for (var entry : files(id, title, vi, affiliation).entrySet()) {
            ProjectFiles.checkCancelled();
            Path path = safe(directory, entry.getKey());
            Files.createDirectories(path.getParent());
            Files.writeString(path, entry.getValue(), StandardCharsets.UTF_8);
        }
        for (String name : template.paths())
            if (!isText(name)) {
                ProjectFiles.checkCancelled();
                Path path = safe(directory, name);
                Files.createDirectories(path.getParent());
                Files.write(
                        path,
                        resource(template.resource() + "/" + (vi ? "vi" : "en") + "/" + name));
            }
        Properties metadata = new Properties();
        metadata.setProperty("id", template.id());
        metadata.setProperty("engine", template.engine());
        metadata.setProperty("mainFile", template.mainFile());
        try (var out = Files.newOutputStream(directory.resolve(".overleaf-template.properties"))) {
            metadata.store(out, "Bundled template defaults");
        }
    }

    public static String standaloneBlank(String title) {
        Map<String, String> files = files("blank", title, false, "");
        return files.get("main.tex").replace("\\input{preamble}", files.get("preamble.tex"));
    }

    public static boolean isText(String name) {
        return name.matches(".*\\.(tex|bib|csv|txt|md)$");
    }

    private static Path safe(Path root, String name) {
        validatePath(name);
        return root.resolve(name);
    }

    private static void validatePath(String path) {
        if (path == null
                || !path.matches("[A-Za-z0-9_./-]+")
                || path.startsWith("/")
                || path.contains("..")
                || path.contains("//"))
            throw new IllegalArgumentException("Invalid template resource path: " + path);
    }

    private static String substitute(String source, Map<String, String> values) {
        Matcher matcher = PLACEHOLDER.matcher(source);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            String value = values.get(matcher.group(1));
            if (value == null)
                throw new IllegalStateException("Unknown placeholder " + matcher.group());
            matcher.appendReplacement(out, Matcher.quoteReplacement(value));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    private static Catalog load() {
        try {
            JsonObject index =
                    JsonParser.parseString(
                                    new String(resource("catalog.json"), StandardCharsets.UTF_8))
                            .getAsJsonObject();
            List<Category> groups = new ArrayList<>();
            for (JsonElement value : index.getAsJsonArray("categories")) {
                JsonObject c = value.getAsJsonObject();
                groups.add(new Category(string(c, "id"), string(c, "en"), string(c, "vi")));
            }
            Set<String> categories = new HashSet<>();
            for (Category c : groups)
                if (!categories.add(c.id()))
                    throw new IllegalArgumentException("Duplicate category");
            Map<String, Template> templates = new LinkedHashMap<>();
            for (JsonElement value : index.getAsJsonArray("templates")) {
                String prefix = value.getAsString();
                validatePath(prefix);
                JsonObject data =
                        JsonParser.parseString(
                                        new String(
                                                resource(prefix + "/template.json"),
                                                StandardCharsets.UTF_8))
                                .getAsJsonObject();
                String id = string(data, "id"),
                        category = string(data, "category"),
                        engine = string(data, "engine"),
                        main = string(data, "mainFile");
                if (!id.matches("[a-z0-9-]+")
                        || !prefix.equals(category + "/" + id)
                        || (!categories.contains(category) && !category.equals("internal")))
                    throw new IllegalArgumentException("Invalid template identity " + id);
                if (!List.of("pdflatex", "xelatex", "lualatex").contains(engine))
                    throw new IllegalArgumentException("Invalid engine " + id);
                Text title = text(data, "title"), description = text(data, "description");
                List<String> paths = strings(data, "files"),
                        packages = strings(data, "requiredPackages"),
                        tags = strings(data, "tags");
                if (!paths.contains(main)
                        || paths.size()
                                != new HashSet<>(
                                                paths.stream()
                                                        .map(p -> p.toLowerCase(Locale.ROOT))
                                                        .toList())
                                        .size())
                    throw new IllegalArgumentException("Invalid manifest " + id);
                for (String name : paths) {
                    validatePath(name);
                    for (String language : List.of("en", "vi"))
                        resource(prefix + "/" + language + "/" + name);
                }
                for (String pkg : packages)
                    if (!pkg.matches("[a-z0-9-]+"))
                        throw new IllegalArgumentException("Invalid package " + pkg);
                Template template =
                        new Template(
                                id,
                                category,
                                title.en(),
                                title.vi(),
                                description,
                                List.copyOf(tags),
                                engine,
                                main,
                                List.copyOf(packages),
                                data.get("needsBiber").getAsBoolean(),
                                List.copyOf(paths),
                                prefix,
                                data.get("sharedPreamble").getAsBoolean());
                if (templates.putIfAbsent(id, template) != null)
                    throw new IllegalArgumentException("Duplicate template id " + id);
            }
            return new Catalog(List.copyOf(groups), Collections.unmodifiableMap(templates));
        } catch (RuntimeException failure) {
            throw new IllegalStateException("Invalid bundled template catalog", failure);
        }
    }

    private static String string(JsonObject data, String key) {
        String value = data.get(key).getAsString();
        if (value.isBlank()) throw new IllegalArgumentException("Empty " + key);
        return value;
    }

    private static Text text(JsonObject data, String key) {
        JsonObject value = data.getAsJsonObject(key);
        return new Text(string(value, "en"), string(value, "vi"));
    }

    private static List<String> strings(JsonObject data, String key) {
        List<String> values = new ArrayList<>();
        for (JsonElement value : data.getAsJsonArray(key)) values.add(value.getAsString());
        return values;
    }

    private static byte[] resource(String name) {
        try (var in = ProjectTemplates.class.getResourceAsStream("/templates/" + name)) {
            if (in == null) throw new IllegalArgumentException("Missing resource " + name);
            return in.readAllBytes();
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    public static String escape(String value) {
        StringBuilder out = new StringBuilder();
        for (char c : (value == null ? "" : value).toCharArray())
            out.append(
                    switch (c) {
                        case '\\' -> "\\textbackslash{}";
                        case '{' -> "\\{";
                        case '}' -> "\\}";
                        case '&', '%', '$', '#', '_' -> "\\" + c;
                        case '~' -> "\\textasciitilde{}";
                        case '^' -> "\\textasciicircum{}";
                        case '\n', '\r' -> " ";
                        default -> String.valueOf(c);
                    });
        return out.toString();
    }
}
