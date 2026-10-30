package com.overleaf.client;

import com.overleaf.common.collab.MessageCodec;
import com.overleaf.common.collab.SyncMessage;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/** Call connect/send off the Swing EDT. Callbacks run on the receiver thread. */
public final class NetworkClient implements AutoCloseable {
    private final Socket socket = new Socket();
    private final AtomicBoolean closed = new AtomicBoolean();
    private final Consumer<SyncMessage> onMessage;
    private final Consumer<Exception> onDisconnect;

    public NetworkClient(String host, int port, Consumer<SyncMessage> onMessage,
                         Consumer<Exception> onDisconnect) throws IOException {
        this.onMessage = Objects.requireNonNull(onMessage);
        this.onDisconnect = Objects.requireNonNull(onDisconnect);
        try {
            socket.connect(new InetSocketAddress(host, port), 5000);
            socket.setTcpNoDelay(true);
            socket.setKeepAlive(true);
        } catch (IOException | RuntimeException e) {
            socket.close();
            throw e;
        }
        Thread receiver = new Thread(this::receiveLoop, "overleaf-client-receiver");
        receiver.setDaemon(true);
        receiver.start();
    }

    public synchronized void send(SyncMessage message) throws IOException {
        if (closed.get()) throw new IOException("Client is closed");
        MessageCodec.write(socket.getOutputStream(), message);
    }

    private void receiveLoop() {
        Exception failure = null;
        try {
            SyncMessage message;
            while (!closed.get() && (message = MessageCodec.read(socket.getInputStream())) != null) {
                onMessage.accept(message);
            }
        } catch (IOException | RuntimeException e) {
            failure = e;
        } finally {
            if (closed.compareAndSet(false, true)) {
                try { socket.close(); } catch (IOException ignored) { }
                onDisconnect.accept(failure);
            }
        }
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) return;
        try { socket.close(); } catch (IOException ignored) { }
    }
}
