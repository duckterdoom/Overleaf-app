package com.overleaf.server.net;

import com.overleaf.common.model.CRDTOperation;
import com.overleaf.common.protocol.MessageType;
import com.overleaf.common.protocol.ResyncTransfer;
import com.overleaf.common.protocol.SyncMessage;
import com.overleaf.common.sync.OperationCodec;
import com.overleaf.server.collab.CollaborationThreadPool;
import com.overleaf.server.collab.DocumentSyncManager;
import com.overleaf.server.collab.OperationHistory;
import com.overleaf.server.collab.SyncBroadcaster;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLServerSocketFactory;

/** Deployable M3 TCP transport, routing validated requests into M4. */
public final class OverleafServer implements AutoCloseable {
    private final ServerConfig config;
    private final SSLContext tlsContext;
    private final Set<ServerSession> sessions = new HashSet<>();
    private final Map<String, ServerSession> memberships = new HashMap<>();
    private final ExecutorService workers;
    private final CollaborationThreadPool collaboration = new CollaborationThreadPool();
    private final OperationHistory history = new OperationHistory();
    private final DocumentSyncManager manager =
            new DocumentSyncManager(
                    collaboration, new SyncBroadcaster(collaboration, 128), history);
    private volatile ServerSocket listener;
    private boolean closed;

    public OverleafServer(ServerConfig config) {
        this(config, null);
    }

    public OverleafServer(ServerConfig config, SSLContext tlsContext) {
        this.config = Objects.requireNonNull(config);
        this.tlsContext = tlsContext;
        workers = Executors.newFixedThreadPool(config.maxClients() * 2);
    }

    public synchronized void start() throws IOException {
        if (closed || listener != null)
            throw new IllegalStateException("Server already started or closed");
        ServerSocket socket =
                config.tls()
                        ? (tlsContext == null
                                        ? SSLServerSocketFactory.getDefault()
                                        : tlsContext.getServerSocketFactory())
                                .createServerSocket()
                        : new ServerSocket();
        try {
            if (socket instanceof SSLServerSocket ssl)
                ssl.setEnabledProtocols(new String[] {"TLSv1.3", "TLSv1.2"});
            socket.setReuseAddress(true);
            socket.bind(new InetSocketAddress(config.host(), config.port()));
        } catch (IOException | RuntimeException e) {
            socket.close();
            throw e;
        }
        listener = socket;
        new Thread(this::acceptLoop, "overleaf-accept").start();
    }

    public int getPort() {
        return listener == null ? config.port() : listener.getLocalPort();
    }

    public synchronized int getSessionCount() {
        return sessions.size();
    }

    private void acceptLoop() {
        try {
            while (!listener.isClosed()) {
                Socket socket = listener.accept();
                synchronized (this) {
                    if (closed || sessions.size() >= config.maxClients()) {
                        socket.close();
                        continue;
                    }
                    try {
                        socket.setSoTimeout(config.idleTimeoutMillis());
                        socket.setTcpNoDelay(true);
                        socket.setKeepAlive(true);
                        ServerSession session =
                                new ServerSession(socket, this, config.queueCapacity());
                        sessions.add(session);
                        workers.execute(session::writeLoop);
                        workers.execute(session::readLoop);
                    } catch (IOException e) {
                        socket.close();
                    }
                }
            }
        } catch (IOException e) {
            if (!listener.isClosed()) System.err.println("TCP accept failed: " + e.getMessage());
        } finally {
            close();
        }
    }

    // A synchronous M4 entry point preserves wire order and makes membership changes atomic.
    synchronized void handle(ServerSession session, SyncMessage request) {
        if (closed || !session.isOpen() || !sessions.contains(session)) return;
        if (!session.authenticated) {
            if (request.getMessageType() != MessageType.AUTH
                    || !MessageDigest.isEqual(
                            config.token().getBytes(StandardCharsets.UTF_8),
                            request.getPayload().getBytes(StandardCharsets.UTF_8))) {
                session.close();
                return;
            }
            session.authenticated = true;
            session.send(new SyncMessage(MessageType.ACK, "", "SERVER", "Server", "AUTHENTICATED"));
            return;
        }
        try {
            MessageType type = request.getMessageType();
            if (type == MessageType.JOIN_ROOM || type == MessageType.RESYNC) {
                join(session, request);
                return;
            }
            if (session.roomId == null
                    || !session.roomId.equals(request.getRoomId())
                    || !session.userId.equals(request.getSenderId())) {
                throw new IllegalArgumentException("MEMBERSHIP_REQUIRED");
            }
            SyncMessage trusted =
                    new SyncMessage(
                            type,
                            session.roomId,
                            session.userId,
                            session.userName,
                            request.getPayload());
            switch (type) {
                case LEAVE_ROOM -> leave(session);
                case HEARTBEAT -> {
                    manager.processMessage(session, trusted);
                    session.send(
                            new SyncMessage(
                                    MessageType.HEARTBEAT,
                                    session.roomId,
                                    "SERVER",
                                    "Server",
                                    "pong"));
                }
                case TEXT_EDIT -> {
                    validateOperations(
                            session, List.of(OperationCodec.fromJson(trusted.getPayload())));
                    manager.processMessage(session, trusted);
                }
                case CURSOR_UPDATE -> {
                    String[] parts = trusted.getPayload().split(",", 3);
                    if (parts.length < 2
                            || Integer.parseInt(parts[0]) < 0
                            || Integer.parseInt(parts[1]) < 0) {
                        throw new IllegalArgumentException("INVALID_CURSOR");
                    }
                    manager.processMessage(session, trusted);
                }
                default -> throw new IllegalArgumentException("UNSUPPORTED_CLIENT_MESSAGE");
            }
        } catch (IllegalArgumentException e) {
            session.send(
                    SyncMessage.createError(
                            request.getRoomId(),
                            "ROOM_UNKNOWN".equals(e.getMessage())
                                    ? "ROOM_UNKNOWN"
                                    : "INVALID_REQUEST: " + e.getMessage()));
        }
    }

    private void join(ServerSession session, SyncMessage request) {
        if (!validId(request.getRoomId())
                || !validId(request.getSenderId())
                || !validId(request.getSenderName())) {
            throw new IllegalArgumentException("INVALID_ID");
        }
        if (!session.userId.isEmpty() && !session.userId.equals(request.getSenderId())) {
            throw new IllegalArgumentException("IDENTITY_CANNOT_CHANGE");
        }
        String target = request.getRoomId();
        String key = target + "\n" + request.getSenderId();
        ServerSession other = memberships.get(key);
        if (other != null && other != session)
            throw new IllegalArgumentException("USER_ALREADY_CONNECTED");
        if (manager.getDocumentContent(target) == null && manager.getRoomCount() >= 256) {
            throw new IllegalArgumentException("ROOM_LIMIT");
        }
        if (request.getMessageType() == MessageType.RESYNC) {
            if (manager.getDocumentContent(target) == null)
                throw new IllegalArgumentException("ROOM_UNKNOWN");
            validateOperations(
                    request.getSenderId(),
                    target,
                    OperationCodec.decodeResync(request.getPayload()).getPending());
        }
        leave(session);
        session.userId = request.getSenderId();
        session.userName = request.getSenderName();
        session.roomId = target;
        memberships.put(key, session);
        manager.processMessage(
                session,
                new SyncMessage(
                        request.getMessageType(),
                        target,
                        session.userId,
                        session.userName,
                        request.getPayload()));
    }

    private static boolean validId(String value) {
        return value != null
                && !value.isBlank()
                && value.length() <= 128
                && value.chars().noneMatch(c -> Character.isISOControl(c) || c == '|');
    }

    private void validateOperations(ServerSession session, List<CRDTOperation> operations) {
        validateOperations(session.userId, session.roomId, operations);
    }

    private void validateOperations(String user, String room, List<CRDTOperation> operations) {
        long size = Optional.ofNullable(manager.getDocumentContent(room)).orElse("").length();
        if (operations.size() > ResyncTransfer.MAX_OPERATIONS)
            throw new IllegalArgumentException("TOO_MANY_OPERATIONS");
        for (CRDTOperation operation : operations) {
            if (!user.equals(operation.getUserId())
                    || !validId(operation.getOperationId())
                    || operation.getPosition() < 0
                    || operation.getLamportTimestamp() < 1
                    || operation.getLamportTimestamp() == Long.MAX_VALUE) {
                throw new IllegalArgumentException("INVALID_OPERATION");
            }
            // Conservative upper bound: deletions do not offset insertions in a RESYNC batch.
            if (operation.isInsert() && !history.contains(room, operation.getOperationId()))
                size += operation.getContentLength();
            if (size > config.maxDocumentChars())
                throw new IllegalArgumentException("DOCUMENT_LIMIT");
        }
    }

    private void leave(ServerSession session) {
        if (session.roomId == null) return;
        String room = session.roomId;
        session.roomId = null;
        memberships.remove(room + "\n" + session.userId, session);
        manager.handleDisconnect(room, session);
    }

    synchronized void disconnected(ServerSession session) {
        sessions.remove(session);
        leave(session);
    }

    @Override
    public void close() {
        List<ServerSession> closing;
        synchronized (this) {
            if (closed) return;
            closed = true;
            if (listener != null)
                try {
                    listener.close();
                } catch (IOException ignored) {
                }
            closing = new ArrayList<>(sessions);
        }
        for (ServerSession session : closing) session.close();
        workers.shutdownNow();
        collaboration.shutdown();
    }
}
