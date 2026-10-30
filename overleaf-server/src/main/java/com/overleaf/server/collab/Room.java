package com.overleaf.server.collab;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * M4: Quản lý phòng làm việc nhóm (Room) tại phía máy chủ.
 */
public class Room {
    private final String roomId;
    private final Set<String> activeUsers;
    private String currentDocumentContent;

    public Room(String roomId) {
        this.roomId = roomId;
        this.activeUsers = Collections.newSetFromMap(new ConcurrentHashMap<>());
        this.currentDocumentContent = "";
    }

    public String getRoomId() {
        return roomId;
    }

    public synchronized void addUser(String username) {
        activeUsers.add(username);
    }

    public synchronized void removeUser(String username) {
        activeUsers.remove(username);
    }

    public Set<String> getActiveUsers() {
        return Collections.unmodifiableSet(activeUsers);
    }

    public synchronized String getCurrentDocumentContent() {
        return currentDocumentContent;
    }

    public synchronized void updateDocumentContent(String newContent) {
        this.currentDocumentContent = newContent;
    }
}
