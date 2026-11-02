package com.overleaf.server.net;

import com.overleaf.common.protocol.MessageCodec;
import com.overleaf.common.protocol.ResyncTransfer;
import com.overleaf.common.protocol.SyncMessage;
import com.overleaf.server.collab.SyncBroadcaster.ClientSession;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.Socket;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.net.ssl.SSLSocket;

/** Socket adapter for M4. A bounded writer queue keeps slow peers off collaboration threads. */
public final class ServerSession implements ClientSession, AutoCloseable {
    private final Socket socket;
    private final OverleafServer server;
    private final BlockingQueue<byte[]> outgoing;
    private final AtomicBoolean closed = new AtomicBoolean();
    private volatile Thread writer;
    volatile String userId = "";
    String userName = "";
    String roomId;
    boolean authenticated;

    ServerSession(Socket socket, OverleafServer server, int queueCapacity) {
        this.socket = socket;
        this.server = server;
        outgoing = new ArrayBlockingQueue<>(queueCapacity);
    }

    @Override
    public String getUserId() {
        return userId;
    }

    @Override
    public boolean isOpen() {
        return !closed.get();
    }

    @Override
    public void disconnect() {
        close();
    }

    void readLoop() {
        try {
            if (socket instanceof SSLSocket ssl) ssl.startHandshake();
            ResyncTransfer.Receiver transfer = new ResyncTransfer.Receiver();
            SyncMessage message;
            while (isOpen() && (message = MessageCodec.read(socket.getInputStream())) != null) {
                if (!authenticated) server.handle(this, message);
                else {
                    SyncMessage complete = transfer.accept(message);
                    if (complete != null) server.handle(this, complete);
                }
            }
        } catch (IOException | RuntimeException e) {
            // Malformed frames, idle timeouts and connection resets affect this peer only.
        } finally {
            close();
        }
    }

    void writeLoop() {
        writer = Thread.currentThread();
        try {
            while (isOpen()) MessageCodec.writeFrame(socket.getOutputStream(), outgoing.take());
        } catch (IOException e) {
            // Closing the socket also unblocks its reader.
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            close();
        }
    }

    @Override
    public void send(SyncMessage message) {
        try {
            if (!isOpen()) return;
            if (!outgoing.offer(MessageCodec.encode(message))) {
                close();
                throw new IllegalStateException("Slow client: outbound queue full");
            }
        } catch (IOException e) {
            close();
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) return;
        try {
            socket.close();
        } catch (IOException ignored) {
        }
        Thread thread = writer;
        if (thread != null) thread.interrupt();
        outgoing.clear();
        server.disconnected(this);
    }
}
