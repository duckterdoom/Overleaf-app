package com.overleaf.client.net;

import com.overleaf.common.protocol.MessageCodec;
import com.overleaf.common.protocol.MessageType;
import com.overleaf.common.protocol.ResyncTransfer;
import com.overleaf.common.protocol.SyncMessage;
import com.overleaf.common.security.TlsContexts;
import java.io.*;
import java.net.*;
import java.util.Objects;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import javax.net.ssl.*;

/** GUI-independent transport. Connect off the UI thread; send is bounded and nonblocking. */
public final class NetworkClient implements AutoCloseable {
    private final Socket socket;
    private final BlockingQueue<List<byte[]>> outgoing = new ArrayBlockingQueue<>(64);
    private final AtomicBoolean closed = new AtomicBoolean();
    private final AtomicBoolean started = new AtomicBoolean();
    private final ScheduledExecutorService heartbeat = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "overleaf-heartbeat"); thread.setDaemon(true); return thread;
    });
    private volatile Consumer<Exception> onDisconnect = e -> { };
    private volatile String room = "";
    private volatile String user = "";
    private final Thread writer;

    public NetworkClient(String host, int port, boolean tls, String token) throws IOException {
        this(host, port, tls, token, tls ? TlsContexts.client(System.getenv()) : null);
    }

    public NetworkClient(String host, int port, boolean tls, String token, SSLContext context) throws IOException {
        socket = tls ? Objects.requireNonNull(context).getSocketFactory().createSocket() : new Socket();
        try {
            socket.connect(new InetSocketAddress(host, port), 5000);
            socket.setSoTimeout(5000);
            socket.setTcpNoDelay(true);
            if (socket instanceof SSLSocket ssl) {
                SSLParameters parameters = ssl.getSSLParameters();
                parameters.setEndpointIdentificationAlgorithm("HTTPS");
                ssl.setSSLParameters(parameters);
                ssl.startHandshake();
            }
            MessageCodec.write(socket.getOutputStream(), new SyncMessage(MessageType.AUTH, "", "", "", token));
            SyncMessage auth = MessageCodec.read(socket.getInputStream());
            if (auth == null || auth.getMessageType() != MessageType.ACK || !auth.getPayload().equals("AUTHENTICATED")) {
                throw new IOException("Server authentication rejected");
            }
            socket.setSoTimeout(45000);
        } catch (IOException | RuntimeException e) { socket.close(); heartbeat.shutdownNow(); throw e; }
        writer = new Thread(this::writeLoop, "overleaf-client-writer");
        writer.setDaemon(true);
        writer.start();
    }

    /** Install callbacks before receiving; callbacks run on the receiver thread. */
    public void start(Consumer<SyncMessage> onMessage, Consumer<Exception> onDisconnect) {
        Objects.requireNonNull(onMessage);
        this.onDisconnect = Objects.requireNonNull(onDisconnect);
        if (!started.compareAndSet(false, true)) throw new IllegalStateException("Receiver already started");
        Thread receiver = new Thread(() -> {
            Exception failure = null;
            try {
                SyncMessage message;
                while (!closed.get() && (message = MessageCodec.read(socket.getInputStream())) != null) {
                    onMessage.accept(message);
                }
            } catch (IOException | RuntimeException e) { failure = e; }
            finally { finish(failure, true); }
        }, "overleaf-client-reader");
        receiver.setDaemon(true);
        heartbeat.scheduleAtFixedRate(() -> {
            if (!room.isEmpty()) {
                try { send(SyncMessage.createHeartbeat(room, user)); }
                catch (IOException e) { finish(e, true); }
            }
        }, 15, 15, TimeUnit.SECONDS);
        receiver.start();
    }

    public synchronized void send(SyncMessage message) throws IOException {
        if (closed.get()) throw new IOException("Connection closed");
        List<byte[]> encoded = ResyncTransfer.encode(message);
        if (!outgoing.offer(encoded)) throw new IOException("Client send queue full; pending edits remain in offline queue");
        if (message.getMessageType() == MessageType.JOIN_ROOM || message.getMessageType() == MessageType.RESYNC) {
            room = message.getRoomId(); user = message.getSenderId();
        } else if (message.getMessageType() == MessageType.LEAVE_ROOM) room = "";
    }

    private void writeLoop() {
        try {
            while (!closed.get()) {
                for (byte[] frame : outgoing.take()) MessageCodec.writeFrame(socket.getOutputStream(), frame);
            }
        } catch (IOException e) { finish(e, true); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    private void finish(Exception error, boolean notify) {
        if (!closed.compareAndSet(false, true)) return;
        try { socket.close(); } catch (IOException ignored) { }
        writer.interrupt();
        heartbeat.shutdownNow();
        outgoing.clear();
        if (notify) onDisconnect.accept(error);
    }

    @Override public void close() { finish(null, false); }
}
