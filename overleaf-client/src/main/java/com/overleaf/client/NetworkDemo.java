package com.overleaf.client;

import com.overleaf.common.collab.*;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/** Console demo for M3; leaves ClientApp and the GUI implementation to M1. */
public final class NetworkDemo {
    private NetworkDemo() { }

    public static void main(String[] args) throws Exception {
        if (args.length != 4) {
            System.err.println("Usage: NetworkDemo <host> <port> <username> <room>");
            System.exit(1);
        }
        String username = args[2];
        String room = args[3];
        try (NetworkClient client = new NetworkClient(args[0], Integer.parseInt(args[1]),
                System.out::println, error -> System.err.println("Disconnected" + (error == null ? "" : ": " + error.getMessage())))) {
            client.send(new SyncMessage(MessageType.JOIN_ROOM, username, room, null));
            System.out.println("/text <full document> | /cursor <line> <column> | /chat <message> | /quit");
            BufferedReader console = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
            String line;
            while ((line = console.readLine()) != null) {
                if (line.equals("/quit")) break;
                if (line.startsWith("/text ")) {
                    client.send(new SyncMessage(MessageType.TEXT_UPDATE, username, room, line.substring(6)));
                } else if (line.startsWith("/chat ")) {
                    client.send(new SyncMessage(MessageType.CHAT_MESSAGE, username, room, line.substring(6)));
                } else if (line.startsWith("/cursor ")) {
                    try {
                        String[] coordinates = line.substring(8).trim().split("\\s+");
                        if (coordinates.length != 2) throw new IllegalArgumentException();
                        SyncMessage cursor = new SyncMessage(MessageType.CURSOR_MOVE, username, room, null);
                        cursor.setLine(Integer.parseInt(coordinates[0]));
                        cursor.setColumn(Integer.parseInt(coordinates[1]));
                        client.send(cursor);
                    } catch (IllegalArgumentException e) {
                        System.err.println("Usage: /cursor <line> <column>");
                    }
                }
            }
            client.send(new SyncMessage(MessageType.LEAVE_ROOM, username, room, null));
        }
    }
}
