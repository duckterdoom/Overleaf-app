package com.overleaf.client.ui.backend;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.spec.KeySpec;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

/**
 * Local-first backend for the desktop shell. Everything lives under one folder
 * (default {@code ~/OverleafDesktop}, override with the {@code overleaf.home}
 * system property or {@code OVERLEAF_HOME}):
 *
 * <pre>
 * projects/&lt;name&gt;/main.tex      one folder per project
 * .app/accounts.properties          email -> name, PBKDF2 salt + hash
 * .app/settings.properties          remembered account, last project
 * .app/activity.properties          edits per day, compiles per year
 * .app/avatars/&lt;id&gt;.png|jpg|gif     profile pictures
 * </pre>
 *
 * Accounts here identify the user on this machine; collaboration rooms still
 * use the shared server token (M3). A MongoDB-backed gateway (M5) can replace
 * this class without touching the views.
 */
public class LocalBackend implements AuthGateway, ProjectGateway, ActivityGateway, ProfileGateway {

    public static final String MAIN_FILE = "main.tex";

    private static final int PBKDF2_ITERATIONS = 120_000;
    private static final java.util.regex.Pattern WINDOWS_RESERVED =
            java.util.regex.Pattern.compile("(?i)(con|prn|aux|nul|com[1-9]|lpt[1-9])(\\..*)?");
    private static final int SALT_BYTES = 16;
    private static final int HASH_BITS = 256;
    private static final DateTimeFormatter OLDER = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);

    private final Path home;
    private final Path projectsDir;
    private final Path accountsFile;
    private final Path settingsFile;
    private final Path activityFile;
    private final SecureRandom random = new SecureRandom();
    private final Object lock = new Object();

    public LocalBackend() {
        this(defaultHome());
    }

    public LocalBackend(Path home) {
        this.home = home.toAbsolutePath().normalize();
        this.projectsDir = this.home.resolve("projects");
        Path appDir = this.home.resolve(".app");
        this.accountsFile = appDir.resolve("accounts.properties");
        this.settingsFile = appDir.resolve("settings.properties");
        this.activityFile = appDir.resolve("activity.properties");
    }

    public static Path defaultHome() {
        String configured = System.getProperty("overleaf.home");
        if (configured == null || configured.isBlank()) {
            configured = System.getenv("OVERLEAF_HOME");
        }
        if (configured != null && !configured.isBlank()) {
            return Path.of(configured);
        }
        return Path.of(System.getProperty("user.home"), "OverleafDesktop");
    }

    public Path home() {
        return home;
    }

    public Path projectsDir() {
        return projectsDir;
    }

    // ------------------------------------------------------------------ auth

    @Override
    public CompletableFuture<Session> signIn(String email, String password, boolean remember) {
        return CompletableFuture.supplyAsync(() -> {
            String key = normalizeEmail(email);
            Properties accounts = load(accountsFile);
            String record = accounts.getProperty(key);
            if (record == null) {
                throw new IllegalStateException("No account uses that email yet. Create one first.");
            }
            Account account = Account.parse(record);
            if (!account.matches(password)) {
                throw new IllegalStateException("That password is not correct.");
            }
            rememberAccount(key, remember);
            return new Session(account.name(), key, false);
        });
    }

    @Override
    public CompletableFuture<Session> register(String name, String email, String password, boolean remember) {
        return CompletableFuture.supplyAsync(() -> {
            String key = normalizeEmail(email);
            if (password == null || password.length() < 6) {
                throw new IllegalStateException("Choose a password with at least 6 characters.");
            }
            String resolved = name == null || name.isBlank() ? displayName(key) : name.trim();
            byte[] salt = new byte[SALT_BYTES];
            random.nextBytes(salt);
            Account account = new Account(resolved, PBKDF2_ITERATIONS, salt, hash(password, salt, PBKDF2_ITERATIONS));
            synchronized (lock) {
                Properties accounts = load(accountsFile);
                if (accounts.containsKey(key)) {
                    throw new IllegalStateException("An account with that email already exists. Sign in instead.");
                }
                accounts.setProperty(key, account.format());
                store(accountsFile, accounts, "Overleaf Desktop local accounts (PBKDF2-SHA256)");
            }
            rememberAccount(key, remember);
            return new Session(resolved, key, false);
        });
    }

    @Override
    public Session continueOffline(String name) {
        String resolved = name == null || name.isBlank() ? "Local User" : name.trim();
        return new Session(resolved, "", true);
    }

    /** Session restored from "Keep me signed in", if the account still exists. */
    public Session restoreSession() {
        Properties settings = load(settingsFile);
        String email = settings.getProperty("remembered.email");
        if (email == null || email.isBlank()) {
            return null;
        }
        String record = load(accountsFile).getProperty(email);
        if (record == null) {
            return null;
        }
        return new Session(Account.parse(record).name(), email, false);
    }

    /** Last email used to sign in, for pre-filling the login form. */
    public String lastEmail() {
        return load(settingsFile).getProperty("last.email", "");
    }

    public void forgetSession() {
        updateSettings(settings -> settings.remove("remembered.email"));
    }

    private void rememberAccount(String email, boolean remember) {
        updateSettings(settings -> {
            settings.setProperty("last.email", email);
            if (remember) {
                settings.setProperty("remembered.email", email);
            } else {
                settings.remove("remembered.email");
            }
        });
    }

    // --------------------------------------------------------------- profile

    private static final List<String> AVATAR_TYPES = List.of("png", "jpg", "jpeg", "gif");

    @Override
    public java.util.Optional<Path> avatar(Session session) {
        String id = avatarId(session);
        for (String type : AVATAR_TYPES) {
            Path file = avatarDir().resolve(id + "." + type);
            if (Files.isRegularFile(file)) {
                return java.util.Optional.of(file);
            }
        }
        return java.util.Optional.empty();
    }

    @Override
    public void setAvatar(Session session, Path image) {
        String name = image.getFileName().toString();
        int dot = name.lastIndexOf('.');
        String type = dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
        if (!AVATAR_TYPES.contains(type)) {
            throw new IllegalStateException("Choose a PNG, JPG or GIF image.");
        }
        try {
            if (!Files.isRegularFile(image)) {
                throw new IllegalStateException("That file no longer exists.");
            }
            if (Files.size(image) > MAX_AVATAR_BYTES) {
                throw new IllegalStateException("Choose an image under 5 MB.");
            }
            synchronized (lock) {
                Files.createDirectories(avatarDir());
                Path target = avatarDir().resolve(avatarId(session) + "." + ("jpeg".equals(type) ? "jpg" : type));
                Path tmp = target.resolveSibling(target.getFileName() + ".tmp");
                Files.copy(image, tmp, StandardCopyOption.REPLACE_EXISTING);
                removeAvatar(session);
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Could not save the picture: " + e.getMessage(), e);
        }
    }

    @Override
    public void removeAvatar(Session session) {
        String id = avatarId(session);
        try {
            for (String type : AVATAR_TYPES) {
                Files.deleteIfExists(avatarDir().resolve(id + "." + type));
            }
        } catch (IOException e) {
            throw new IllegalStateException("Could not remove the picture: " + e.getMessage(), e);
        }
    }

    private Path avatarDir() {
        return home.resolve(".app").resolve("avatars");
    }

    /** Stable file name per account; offline sessions are keyed by display name. */
    private static String avatarId(Session session) {
        String key = session.offline() || session.email() == null || session.email().isBlank()
                ? "offline:" + session.name()
                : session.email().toLowerCase(Locale.ROOT);
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(key.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (int i = 0; i < 10; i++) {
                hex.append(String.format("%02x", digest[i]));
            }
            return hex.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    // -------------------------------------------------------------- projects

    @Override
    public List<ProjectSummary> projects() {
        List<ProjectSummary> result = new ArrayList<>();
        if (!Files.isDirectory(projectsDir)) {
            return result;
        }
        record Entry(ProjectSummary summary, Instant modified) {
        }
        List<Entry> entries = new ArrayList<>();
        try (Stream<Path> dirs = Files.list(projectsDir)) {
            for (Path dir : (Iterable<Path>) dirs::iterator) {
                if (Files.isDirectory(dir) && !dir.getFileName().toString().startsWith(".")) {
                    Instant modified = lastModified(dir);
                    entries.add(new Entry(summarize(dir, modified), modified));
                }
            }
        } catch (IOException e) {
            return result;
        }
        entries.sort(Comparator.comparing(Entry::modified).reversed());
        for (Entry entry : entries) {
            result.add(entry.summary());
        }
        return result;
    }

    @Override
    public ProjectSummary create(String name) {
        String folderName = folderName(name);
        try {
            Files.createDirectories(projectsDir);
            Path dir = projectsDir.resolve(folderName);
            for (int n = 2; Files.exists(dir); n++) {
                dir = projectsDir.resolve(folderName + " (" + n + ")");
            }
            Files.createDirectories(dir);
            Files.writeString(dir.resolve(MAIN_FILE), template(dir.getFileName().toString()), StandardCharsets.UTF_8);
            return summarize(dir, Instant.now());
        } catch (IOException e) {
            throw new IllegalStateException("Could not create the project folder: " + e.getMessage(), e);
        }
    }

    @Override
    public ProjectSummary rename(ProjectSummary project, String newName) {
        Path dir = folderOf(project);
        String folderName = folderName(newName);
        Path target = projectsDir.resolve(folderName);
        if (target.equals(dir)) {
            return summarize(dir, lastModified(dir));
        }
        if (Files.exists(target)) {
            throw new IllegalStateException("A project called \"" + folderName + "\" already exists.");
        }
        try {
            Files.move(dir, target);
        } catch (IOException e) {
            throw new IllegalStateException("Could not rename the project: " + e.getMessage(), e);
        }
        updateSettings(settings -> {
            if (project.name().equals(settings.getProperty("last.project"))) {
                settings.setProperty("last.project", folderName);
            }
        });
        return summarize(target, lastModified(target));
    }

    /** Moves the folder to {@code <home>/.trash/} instead of deleting it outright. */
    @Override
    public void delete(ProjectSummary project) {
        Path dir = folderOf(project);
        try {
            Path trash = home.resolve(".trash");
            Files.createDirectories(trash);
            String stamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").format(java.time.LocalDateTime.now());
            Files.move(dir, trash.resolve(dir.getFileName() + " " + stamp));
        } catch (IOException e) {
            throw new IllegalStateException("Could not delete the project: " + e.getMessage(), e);
        }
    }

    public Path trashDir() {
        return home.resolve(".trash");
    }

    private Path folderOf(ProjectSummary project) {
        Path dir = Path.of(project.folder()).toAbsolutePath().normalize();
        if (!dir.getParent().equals(projectsDir) || !Files.isDirectory(dir)) {
            throw new IllegalStateException("That project folder no longer exists.");
        }
        return dir;
    }

    private static String folderName(String name) {
        String trimmed = name == null || name.isBlank() ? "Untitled project" : name.trim();
        String folder = trimmed.replaceAll("[\\\\/:*?\"<>|]", "-").replaceAll("\\s+", " ").trim();
        // Windows rejects names ending in a dot/space and reserved device names.
        folder = folder.replaceAll("[. ]+$", "");
        if (WINDOWS_RESERVED.matcher(folder).matches()) {
            folder = folder + " project";
        }
        return folder.isEmpty() || folder.startsWith(".") ? "Untitled project" : folder;
    }

    @Override
    public String locationFor(String name) {
        String shown = projectsDir.resolve(name == null || name.isBlank() ? "…" : name.trim()).toString();
        String userHome = System.getProperty("user.home");
        return userHome != null && shown.startsWith(userHome) ? "~" + shown.substring(userHome.length()) : shown;
    }

    /** Name of the project opened last, or {@code null}. */
    public String lastProject() {
        return load(settingsFile).getProperty("last.project");
    }

    public void rememberProject(String name) {
        updateSettings(settings -> settings.setProperty("last.project", name));
    }

    /** Creates a starter project the first time the app runs, so Home is never empty. */
    public void ensureStarterProject() {
        if (projects().isEmpty()) {
            create("My first paper");
        }
    }

    public static String template(String title) {
        // Vietnamese works with every engine in the toolbar: T5 fonts for pdfLaTeX,
        // Unicode fonts (fontspec) for XeLaTeX/LuaLaTeX.
        return "\\documentclass{article}\n"
                + "\\usepackage{iftex}\n"
                + "\\ifPDFTeX\n"
                + "  \\usepackage[T5]{fontenc}\n"
                + "  \\usepackage[utf8]{inputenc}\n"
                + "\\else\n"
                + "  \\usepackage{fontspec}\n"
                + "\\fi\n"
                + "\\usepackage[vietnamese,english]{babel}\n"
                + "\\usepackage{amsmath}\n\n"
                + "\\title{" + title.replaceAll("([#$%&_{}])", "\\\\$1") + "}\n"
                + "\\author{}\n"
                + "\\date{\\today}\n\n"
                + "\\begin{document}\n\n"
                + "\\maketitle\n\n"
                + "\\section{Introduction}\n"
                + "Start writing here.\n\n"
                + "\\begin{equation}\n"
                + "    E = mc^2\n"
                + "\\end{equation}\n\n"
                + "\\end{document}\n";
    }

    private ProjectSummary summarize(Path dir, Instant modified) {
        int files = 0;
        try (Stream<Path> walk = Files.walk(dir, 3)) {
            files = (int) walk.filter(Files::isRegularFile)
                    .filter(p -> !p.startsWith(dir.resolve("build")))
                    .filter(p -> {
                        String lower = p.getFileName().toString().toLowerCase(Locale.ROOT);
                        return lower.endsWith(".tex") || lower.endsWith(".bib")
                                || lower.endsWith(".sty") || lower.endsWith(".cls");
                    })
                    .count();
        } catch (IOException ignored) {
            // Count stays 0; the project still opens.
        }
        boolean shared = Files.isRegularFile(dir.resolve(".overleaf-room"));
        return new ProjectSummary(dir.getFileName().toString(), dir.toString(), describeModified(modified), files, shared);
    }

    private static Instant lastModified(Path dir) {
        try (Stream<Path> walk = Files.walk(dir, 3)) {
            return walk.filter(Files::isRegularFile)
                    .filter(p -> !p.startsWith(dir.resolve("build")))
                    .map(p -> {
                        try {
                            return Files.getLastModifiedTime(p).toInstant();
                        } catch (IOException e) {
                            return Instant.EPOCH;
                        }
                    })
                    .max(Comparator.naturalOrder())
                    .orElse(Instant.EPOCH);
        } catch (IOException e) {
            return Instant.EPOCH;
        }
    }

    /** Matches the phrases HomeView knows how to translate. */
    static String describeModified(Instant when) {
        Duration age = Duration.between(when, Instant.now());
        if (age.isNegative() || age.toMinutes() < 1) {
            return "Edited just now";
        }
        long minutes = age.toMinutes();
        if (minutes < 60) {
            return "Edited " + minutes + (minutes == 1 ? " minute ago" : " minutes ago");
        }
        long hours = age.toHours();
        if (hours < 24) {
            return "Edited " + hours + (hours == 1 ? " hour ago" : " hours ago");
        }
        LocalDate day = when.atZone(ZoneId.systemDefault()).toLocalDate();
        long days = java.time.temporal.ChronoUnit.DAYS.between(day, LocalDate.now());
        if (days <= 1) {
            return "Edited yesterday";
        }
        if (days < 7) {
            return "Edited " + days + " days ago";
        }
        if (days < 14) {
            return "Edited last week";
        }
        return "Edited " + OLDER.format(day);
    }

    // -------------------------------------------------------------- activity

    @Override
    public Map<LocalDate, Integer> editsByDay(LocalDate from, LocalDate to) {
        Map<LocalDate, Integer> result = new HashMap<>();
        Properties activity = load(activityFile);
        for (String key : activity.stringPropertyNames()) {
            if (!key.startsWith("edits.")) {
                continue;
            }
            try {
                LocalDate day = LocalDate.parse(key.substring("edits.".length()));
                int value = Integer.parseInt(activity.getProperty(key).trim());
                if (value > 0 && !day.isBefore(from) && !day.isAfter(to)) {
                    result.put(day, value);
                }
            } catch (RuntimeException ignored) {
                // Skip a malformed line rather than losing the whole graph.
            }
        }
        return result;
    }

    @Override
    public int compilesThisYear() {
        return intValue(load(activityFile), "compiles." + LocalDate.now().getYear());
    }

    @Override
    public void recordEdits(int count) {
        if (count <= 0) {
            return;
        }
        addActivity("edits." + LocalDate.now(), count);
    }

    @Override
    public void recordCompile() {
        addActivity("compiles." + LocalDate.now().getYear(), 1);
    }

    private void addActivity(String key, int delta) {
        synchronized (lock) {
            Properties activity = load(activityFile);
            activity.setProperty(key, String.valueOf(intValue(activity, key) + delta));
            store(activityFile, activity, "Overleaf Desktop activity");
        }
    }

    private static int intValue(Properties props, String key) {
        try {
            return Integer.parseInt(props.getProperty(key, "0").trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    // --------------------------------------------------------------- storage

    private void updateSettings(java.util.function.Consumer<Properties> change) {
        synchronized (lock) {
            Properties settings = load(settingsFile);
            change.accept(settings);
            store(settingsFile, settings, "Overleaf Desktop settings");
        }
    }

    private static Properties load(Path file) {
        Properties props = new Properties();
        if (Files.isRegularFile(file)) {
            try (InputStream in = Files.newInputStream(file)) {
                props.load(new java.io.InputStreamReader(in, StandardCharsets.UTF_8));
            } catch (IOException e) {
                throw new IllegalStateException("Could not read " + file.getFileName() + ": " + e.getMessage(), e);
            }
        }
        return props;
    }

    private static void store(Path file, Properties props, String comment) {
        try {
            Files.createDirectories(file.getParent());
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            try (OutputStream out = Files.newOutputStream(tmp)) {
                props.store(new java.io.OutputStreamWriter(out, StandardCharsets.UTF_8), comment);
            }
            try {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Could not save " + file.getFileName() + ": " + e.getMessage(), e);
        }
    }

    private static String normalizeEmail(String email) {
        String key = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
        if (key.isEmpty() || !key.contains("@")) {
            throw new IllegalStateException("Enter a valid email address.");
        }
        return key;
    }

    private static byte[] hash(String password, byte[] salt, int iterations) {
        try {
            KeySpec spec = new PBEKeySpec(password.toCharArray(), salt, iterations, HASH_BITS);
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        } catch (Exception e) {
            throw new IllegalStateException("Password hashing is unavailable: " + e.getMessage(), e);
        }
    }

    static String displayName(String email) {
        String local = email.substring(0, email.indexOf('@') < 0 ? email.length() : email.indexOf('@'));
        StringBuilder out = new StringBuilder();
        for (String part : local.replace('.', ' ').replace('_', ' ').trim().split("\\s+")) {
            if (part.isBlank()) {
                continue;
            }
            if (!out.isEmpty()) {
                out.append(' ');
            }
            out.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return out.isEmpty() ? "Local User" : out.toString();
    }

    private record Account(String name, int iterations, byte[] salt, byte[] hash) {

        private static final Base64.Encoder ENC = Base64.getEncoder();
        private static final Base64.Decoder DEC = Base64.getDecoder();

        static Account parse(String record) {
            String[] parts = record.split("\\|", 4);
            if (parts.length != 4) {
                throw new IllegalStateException("The saved account is damaged. Create it again.");
            }
            return new Account(parts[3], Integer.parseInt(parts[0]), DEC.decode(parts[1]), DEC.decode(parts[2]));
        }

        String format() {
            return iterations + "|" + ENC.encodeToString(salt) + "|" + ENC.encodeToString(hash) + "|" + name;
        }

        boolean matches(String password) {
            if (password == null) {
                return false;
            }
            return MessageDigest.isEqual(hash, LocalBackend.hash(password, salt, iterations));
        }
    }
}
