package com.overleaf.common.collab;

import java.io.Serializable;

/**
 * Shared Module: Gói tin đồng bộ hóa mạng (Serializable) qua luồng Object stream.
 */
public class SyncMessage implements Serializable {
    private static final long serialVersionUID = 1L;

    private MessageType type;
    private String sender;
    private String roomId;
    private String content;
    private int line;
    private int column;
    private long timestamp;

    public SyncMessage() {
        this.timestamp = System.currentTimeMillis();
    }

    public SyncMessage(MessageType type, String sender, String roomId, String content) {
        this.type = type;
        this.sender = sender;
        this.roomId = roomId;
        this.content = content;
        this.timestamp = System.currentTimeMillis();
    }

    public MessageType getType() {
        return type;
    }

    public void setType(MessageType type) {
        this.type = type;
    }

    public String getSender() {
        return sender;
    }

    public void setSender(String sender) {
        this.sender = sender;
    }

    public String getRoomId() {
        return roomId;
    }

    public void setRoomId(String roomId) {
        this.roomId = roomId;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public int getLine() {
        return line;
    }

    public void setLine(int line) {
        this.line = line;
    }

    public int getColumn() {
        return column;
    }

    public void setColumn(int column) {
        this.column = column;
    }

    public long getTimestamp() {
        return timestamp;
    }

    @Override
    public String toString() {
        return String.format("[%s] from %s in %s: %s", type, sender, roomId, 
                content != null && content.length() > 50 ? content.substring(0, 47) + "..." : content);
    }
}
