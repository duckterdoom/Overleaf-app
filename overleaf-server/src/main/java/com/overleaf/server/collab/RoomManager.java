package com.overleaf.server.collab;

import java.util.concurrent.ConcurrentHashMap;

/**
 * M4: Bộ quản lý đa phòng làm việc nhóm (RoomManager) đảm bảo an toàn đa luồng.
 */
public class RoomManager {
    private final ConcurrentHashMap<String, Room> rooms = new ConcurrentHashMap<>();

    public Room getOrCreateRoom(String roomId) {
        return rooms.computeIfAbsent(roomId, Room::new);
    }

    public Room getRoom(String roomId) {
        return rooms.get(roomId);
    }

    public void removeRoomIfEmpty(String roomId) {
        rooms.computeIfPresent(roomId, (id, room) -> room.getActiveUsers().isEmpty() ? null : room);
    }

    public int getTotalRooms() {
        return rooms.size();
    }
}
