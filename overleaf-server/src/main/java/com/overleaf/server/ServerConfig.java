package com.overleaf.server;

import java.net.InetAddress;
import java.util.Map;

/** Deployment configuration. Secrets come from the environment, never command-line arguments. */
public record ServerConfig(String host, int port, int maxClients, int idleTimeoutMillis,
                           String token, boolean tls, int queueCapacity, int maxDocumentChars) {
    public ServerConfig {
        if (host == null || host.isBlank() || port < 0 || port > 65535 || maxClients < 1 || maxClients > 1024
                || idleTimeoutMillis < 100 || queueCapacity < 1 || maxDocumentChars < 1 || maxDocumentChars > 200000) {
            throw new IllegalArgumentException("Invalid server configuration");
        }
        token = token == null ? "" : token;
        if (!token.isEmpty() && token.length() < 24) throw new IllegalArgumentException("Token must have at least 24 characters");
        try {
            if (!InetAddress.getByName(host).isLoopbackAddress() && (!tls || token.isEmpty())) {
                throw new IllegalArgumentException("External bind requires TLS and OVERLEAF_TOKEN (at least 24 characters)");
            }
        } catch (java.net.UnknownHostException e) {
            throw new IllegalArgumentException("Invalid bind host", e);
        }
    }

    // Avoid exposing credentials through the record's generated toString().
    @Override public String toString() { return "ServerConfig[host=" + host + ", port=" + port + ", tls=" + tls + "]"; }

    public static ServerConfig fromEnvironment(String[] args) {
        if (args.length > 1) throw new IllegalArgumentException("Usage: java -jar overleaf-server.jar [port]");
        Map<String, String> env = System.getenv();
        String tls = env.getOrDefault("OVERLEAF_TLS", "false");
        if (!tls.equals("true") && !tls.equals("false")) throw new IllegalArgumentException("OVERLEAF_TLS must be true or false");
        return new ServerConfig(env.getOrDefault("OVERLEAF_HOST", "127.0.0.1"),
                Integer.parseInt(args.length == 1 ? args[0] : env.getOrDefault("OVERLEAF_PORT", "8080")),
                Integer.parseInt(env.getOrDefault("OVERLEAF_MAX_CLIENTS", "64")),
                Integer.parseInt(env.getOrDefault("OVERLEAF_IDLE_TIMEOUT_MS", "60000")),
                env.getOrDefault("OVERLEAF_TOKEN", ""), Boolean.parseBoolean(tls),
                Integer.parseInt(env.getOrDefault("OVERLEAF_QUEUE_CAPACITY", "32")),
                Integer.parseInt(env.getOrDefault("OVERLEAF_MAX_DOCUMENT_CHARS", "100000")));
    }
}
