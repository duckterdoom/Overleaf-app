package com.overleaf.common.security;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.util.Arrays;
import java.util.Map;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

/** PKCS12 keys/trust supplied outside the JAR; no passwords in JVM command-line flags. */
public final class TlsContexts {
    private TlsContexts() {}

    public static SSLContext server(Map<String, String> environment) throws IOException {
        String file = environment.get("OVERLEAF_KEYSTORE");
        if (file == null || file.isBlank())
            throw new IOException("OVERLEAF_KEYSTORE is required for TLS");
        char[] password = environment.getOrDefault("OVERLEAF_KEYSTORE_PASSWORD", "").toCharArray();
        try {
            KeyStore keys = load(file, password);
            KeyManagerFactory managers =
                    KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            managers.init(keys, password);
            SSLContext context = SSLContext.getInstance("TLS");
            context.init(managers.getKeyManagers(), null, null);
            return context;
        } catch (GeneralSecurityException e) {
            throw new IOException("Cannot load TLS server keys", e);
        } finally {
            Arrays.fill(password, '\0');
        }
    }

    public static SSLContext client(Map<String, String> environment) throws IOException {
        String file = environment.get("OVERLEAF_TRUSTSTORE");
        try {
            if (file == null || file.isBlank()) return SSLContext.getDefault();
            char[] password =
                    environment.getOrDefault("OVERLEAF_TRUSTSTORE_PASSWORD", "").toCharArray();
            try {
                TrustManagerFactory managers =
                        TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
                managers.init(load(file, password));
                SSLContext context = SSLContext.getInstance("TLS");
                context.init(null, managers.getTrustManagers(), null);
                return context;
            } finally {
                Arrays.fill(password, '\0');
            }
        } catch (GeneralSecurityException e) {
            throw new IOException("Cannot load TLS client trust", e);
        }
    }

    private static KeyStore load(String file, char[] password)
            throws IOException, GeneralSecurityException {
        KeyStore store = KeyStore.getInstance("PKCS12");
        try (InputStream input = Files.newInputStream(Path.of(file))) {
            store.load(input, password);
        }
        return store;
    }
}
