package com.overleaf.server;

import com.overleaf.common.collab.*;
import com.overleaf.server.collab.Room;
import com.overleaf.server.collab.RoomManager;
import java.io.IOException;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

/** M3 transport and room routing; document conflict resolution belongs to M4. */
public final class OverleafServer implements AutoCloseable {
    public static final int DEFAULT_PORT = 8080;
    private final int port;
    private final int maxClients;
    private final RoomManager rooms;
    private final Map<String, ServerSession> sessions = new LinkedHashMap<>();
    private final ExecutorService workers;
    private ServerSocket listener;
    private boolean closed;

    public OverleafServer(int port) { this(port, 64, new RoomManager()); }

    public OverleafServer(int port, int maxClients, RoomManager rooms) {
        if (port < 0 || port > 65535 || maxClients < 1) throw new IllegalArgumentException("Invalid server limits");
        this.port = port;
        this.maxClients = maxClients;
        this.rooms = Objects.requireNonNull(rooms);
        workers = Executors.newFixedThreadPool(maxClients * 2);
    }

    /** Binds synchronously, then accepts on a background thread. Port 0 is useful for tests. */
    public synchronized void start() throws IOException {
        if (closed || listener != null) throw new IllegalStateException("Server already started or closed");
        ServerSocket bound = new ServerSocket();
        try {
            bound.setReuseAddress(true);
            bound.bind(new InetSocketAddress(port));
        } catch (IOException e) {
            bound.close();
            throw e;
        }
        listener = bound;
        new Thread(this::acceptLoop, "overleaf-accept").start();
    }

    public synchronized int getPort() { return listener == null ? port : listener.getLocalPort(); }
    public synchronized int getSessionCount() { return sessions.size(); }

    private void acceptLoop() {
        try {
            while (!listener.isClosed()) {
                Socket socket = listener.accept();
                synchronized (this) {
                    if (closed || sessions.size() >= maxClients) {
                        socket.close();
                        continue;
                    }
                    try {
                        socket.setTcpNoDelay(true);
                        socket.setKeepAlive(true);
                        ServerSession session = new ServerSession(socket, this);
                        sessions.put(session.getId(), session);
                        workers.execute(session::writeLoop);
                        workers.execute(session::readLoop);
                    } catch (IOException | RejectedExecutionException e) {
                        socket.close();
                    }
                }
            }
        } catch (IOException e) {
            synchronized (this) {
                if (!closed) System.err.println("TCP accept failed: " + e.getMessage());
            }
        } finally {
            close();
        }
    }

    // This lock serializes join snapshots, updates and broadcasts in server arrival order.
    synchronized void handle(ServerSession session, SyncMessage request) throws IOException {
        if (closed || !sessions.containsKey(session.getId())) return;
        if (request.getType() == MessageType.JOIN_ROOM) {
            join(session, request);
            return;
        }
        if (session.roomId == null || !session.roomId.equals(request.getRoomId())) {
            error(session, "Join the requested room first");
            return;
        }
        // Never trust the sender supplied by a client after joining.
        SyncMessage event = new SyncMessage(request.getType(), session.username, session.roomId, request.getContent());
        event.setLine(request.getLine());
        event.setColumn(request.getColumn());
        switch (request.getType()) {
            case LEAVE_ROOM -> leave(session);
            case TEXT_UPDATE -> {
                if (request.getContent() == null) { error(session, "Text content is required"); return; }
                byte[] frame = MessageCodec.encode(event);
                rooms.getRoom(session.roomId).updateDocumentContent(request.getContent());
                broadcast(session.roomId, frame, null);
            }
            case CURSOR_MOVE -> {
                if (event.getLine() < 0 || event.getColumn() < 0) { error(session, "Cursor must be nonnegative"); return; }
                broadcast(session.roomId, MessageCodec.encode(event), session);
            }
            case CHAT_MESSAGE, COMPILE_NOTIFY -> broadcast(session.roomId, MessageCodec.encode(event), null);
            case USER_LIST -> send(session, userList(session.roomId));
            default -> error(session, "Unsupported client message: " + request.getType());
        }
    }

    private void join(ServerSession session, SyncMessage request) throws IOException {
        if (!validId(request.getSender()) || !validId(request.getRoomId())) {
            error(session, "Username and room ID must be 1..128 characters without control characters");
            return;
        }
        Room room = rooms.getOrCreateRoom(request.getRoomId());
        boolean duplicate = sessions.values().stream().anyMatch(other -> other != session
                && request.getRoomId().equals(other.roomId) && request.getSender().equals(other.username));
        if (duplicate) { error(session, "Username already in use in this room"); return; }
        leave(session);
        session.username = request.getSender();
        session.roomId = request.getRoomId();
        room.addUser(session.username);
        String joinedRoom = request.getRoomId();
        send(session, new SyncMessage(MessageType.JOIN_ROOM, "server", joinedRoom, session.getId()));
        send(session, new SyncMessage(MessageType.TEXT_UPDATE, "server", joinedRoom, room.getCurrentDocumentContent()));
        broadcast(joinedRoom, MessageCodec.encode(userList(joinedRoom)), null);
    }

    private static boolean validId(String value) {
        return value != null && !value.isBlank() && value.length() <= 128
                && value.chars().noneMatch(Character::isISOControl);
    }

    private SyncMessage userList(String roomId) {
        List<String> users = new ArrayList<>(rooms.getRoom(roomId).getActiveUsers());
        Collections.sort(users);
        return new SyncMessage(MessageType.USER_LIST, "server", roomId, String.join("\n", users));
    }

    private void broadcast(String roomId, byte[] frame, ServerSession excluded) {
        for (ServerSession target : new ArrayList<>(sessions.values())) {
            if (target != excluded && roomId.equals(target.roomId)) target.send(frame);
        }
    }

    private void send(ServerSession session, SyncMessage event) throws IOException {
        session.send(MessageCodec.encode(event));
    }

    private void error(ServerSession session, String reason) throws IOException {
        send(session, new SyncMessage(MessageType.ERROR, "server", session.roomId, reason));
    }

    private void leave(ServerSession session) throws IOException {
        String oldRoom = session.roomId;
        if (oldRoom == null) return;
        Room room = rooms.getRoom(oldRoom);
        room.removeUser(session.username);
        session.roomId = null;
        session.username = null;
        if (!closed) broadcast(oldRoom, MessageCodec.encode(userList(oldRoom)), null);
        // Keep the snapshot for later reconnects until the server shuts down.
    }

    synchronized void disconnected(ServerSession session) {
        sessions.remove(session.getId());
        try { leave(session); } catch (IOException ignored) { }
    }

    @Override
    public synchronized void close() {
        if (closed) return;
        closed = true;
        if (listener != null) try { listener.close(); } catch (IOException ignored) { }
        for (ServerSession session : new ArrayList<>(sessions.values())) session.close();
        workers.shutdownNow();
    }
}
