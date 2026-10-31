package com.overleaf.client.net;

import com.overleaf.common.sync.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/** Headless deployment check; positions and Lamport time should be chosen by the caller. */
public final class NetworkDemo {
    private NetworkDemo() { }
    public static void main(String[] args) throws Exception {
        if (args.length != 4) throw new IllegalArgumentException("NetworkDemo host port userId room");
        String user = args[2], room = args[3];
        CountDownLatch joined = new CountDownLatch(1);
        AtomicLong clock = new AtomicLong();
        AtomicLong sequence = new AtomicLong();
        String prefix = Long.toHexString(System.nanoTime());
        try (NetworkClient client = new NetworkClient(args[0], Integer.parseInt(args[1]),
                Boolean.parseBoolean(System.getenv().getOrDefault("OVERLEAF_TLS", "false")), System.getenv("OVERLEAF_TOKEN"))) {
            client.start(message -> {
                System.out.println(message.getMessageType() + ": " + message.getPayload());
                if (message.getMessageType() == MessageType.TEXT_EDIT) {
                    clock.accumulateAndGet(OperationCodec.fromJson(message.getPayload()).getLamportTimestamp(), Math::max);
                }
                if (message.getMessageType() == MessageType.CHECKPOINT) joined.countDown();
            }, error -> System.err.println("Disconnected" + (error == null ? "" : ": " + error.getMessage())));
            client.send(SyncMessage.createJoinRoom(room, user, user));
            if (!joined.await(5, TimeUnit.SECONDS)) throw new IOException("Join did not complete");
            System.out.println("/insert <position> <text> | /delete <position> <text> | /cursor <line> <column> | /quit");
            BufferedReader console = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
            String line;
            while ((line = console.readLine()) != null && !line.equals("/quit")) {
                try {
                    String[] parts = line.split(" ", 3);
                    if (parts.length != 3) throw new IllegalArgumentException("Expected command and two arguments");
                    if (parts[0].equals("/cursor")) {
                        client.send(SyncMessage.createCursorUpdate(room, user, user, parts[1] + "," + parts[2] + ",main.tex"));
                    } else {
                        CRDTOperation.OpType type = switch (parts[0]) {
                            case "/insert" -> CRDTOperation.OpType.INSERT;
                            case "/delete" -> CRDTOperation.OpType.DELETE;
                            default -> throw new IllegalArgumentException("Unknown command");
                        };
                        CRDTOperation operation = new CRDTOperation(user + "-" + prefix + "-" + sequence.incrementAndGet(),
                                type, Integer.parseInt(parts[1]), parts[2], user, clock.incrementAndGet(), System.currentTimeMillis());
                        client.send(SyncMessage.createTextEdit(room, user, user, OperationCodec.toJson(operation)));
                    }
                } catch (IllegalArgumentException e) { System.err.println(e.getMessage()); }
            }
        }
    }
}
