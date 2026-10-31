package com.overleaf.server;

public final class ServerApp {
    private ServerApp() { }
    public static void main(String[] args) {
        OverleafServer server = null;
        try {
            ServerConfig config = ServerConfig.fromEnvironment(args);
            server = new OverleafServer(config, config.tls()
                    ? com.overleaf.common.sync.TlsContexts.server(System.getenv()) : null);
            server.start();
            Runtime.getRuntime().addShutdownHook(new Thread(server::close, "overleaf-shutdown"));
            System.out.println("Overleaf TCP listening on " + config.host() + ":" + server.getPort() + ", TLS=" + config.tls());
        } catch (Exception e) {
            if (server != null) server.close();
            System.err.println("Cannot start server: " + e.getMessage());
            System.exit(1);
        }
    }
}
