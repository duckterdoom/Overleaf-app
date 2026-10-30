package com.overleaf.server;

import com.overleaf.common.collab.MessageCodec;
import com.overleaf.common.collab.SyncMessage;
import java.io.IOException;
import java.net.Socket;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/** One reader and one queued writer per TCP connection. */
public final class ServerSession implements AutoCloseable {
    private static final AtomicLong NEXT_ID = new AtomicLong();
    // This is a connection identifier, not an authentication token.
    private final String id = Long.toString(NEXT_ID.incrementAndGet());
    private final Socket socket;
    private final OverleafServer server;
    private final BlockingQueue<byte[]> outgoing = new ArrayBlockingQueue<>(32);
    private final AtomicBoolean closed = new AtomicBoolean();
    private volatile Thread writer;
    // Membership is accessed under the server's membership lock.
    String username;
    String roomId;

    ServerSession(Socket socket, OverleafServer server) {
        this.socket = socket;
        this.server = server;
    }

    public String getId() { return id; }

    void readLoop() {
        try {
            SyncMessage message;
            while (!closed.get() && (message = MessageCodec.read(socket.getInputStream())) != null) {
                server.handle(this, message);
            }
        } catch (IOException e) {
            // EOF, malformed frames and connection resets all end this session only.
        } finally {
            close();
        }
    }

    void writeLoop() {
        writer = Thread.currentThread();
        try {
            while (!closed.get()) {
                MessageCodec.writeFrame(socket.getOutputStream(), outgoing.take());
            }
        } catch (IOException e) {
            // Disconnect cleanup also removes presence from the room.
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            close();
        }
    }

    void send(byte[] frame) {
        if (!closed.get() && !outgoing.offer(frame)) close();
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) return;
        try { socket.close(); } catch (IOException ignored) { }
        Thread thread = writer;
        if (thread != null) thread.interrupt();
        outgoing.clear();
        server.disconnected(this);
    }
}
