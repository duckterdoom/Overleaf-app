package com.overleaf.client.compiler;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Fingerprints local project inputs; compiler output in build/ is deliberately excluded. */
public final class ProjectInputs {
    private ProjectInputs() {}

    public static String fingerprint(Path project) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            Path root = project.toAbsolutePath().normalize();
            try (var paths = Files.walk(root)) {
                for (Path file :
                        paths.filter(Files::isRegularFile)
                                .filter(p ->
                                        !p.startsWith(root.resolve("build"))
                                                && !p.startsWith(root.resolve(".git")))
                                .sorted()
                                .toList()) {
                    digest.update(
                            root.relativize(file).toString().getBytes(StandardCharsets.UTF_8));
                    digest.update((byte) 0);
                    MessageDigest content = MessageDigest.getInstance("SHA-256");
                    try (var input = Files.newInputStream(file)) {
                        byte[] buffer = new byte[65536];
                        int count;
                        while ((count = input.read(buffer)) != -1) content.update(buffer, 0, count);
                    }
                    digest.update(content.digest());
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException impossible) {
            throw new AssertionError(impossible);
        }
    }
}
