package com.overleaf.server;

/**
 * M3 entry point for the TCP networking backend.
 */
public class ServerApp {
    public static void main(String[] args) {
        try {
            if (args.length > 1) throw new IllegalArgumentException("Usage: ServerApp [port]");
            int port = args.length == 0 ? OverleafServer.DEFAULT_PORT : Integer.parseInt(args[0]);
            OverleafServer server = new OverleafServer(port);
            Runtime.getRuntime().addShutdownHook(new Thread(server::close, "overleaf-shutdown"));
            server.start();
            System.out.println("Overleaf TCP server listening on port " + server.getPort());
        } catch (java.io.IOException | IllegalArgumentException e) {
            System.err.println("Cannot start server: " + e.getMessage());
            System.exit(1);
        }
    }
}
