package com.overleaf.client.net;

/** Generation and room guard, evaluated when a delayed UI callback actually runs. */
public final class ConnectionScope {
    private long generation;
    private String room = "";
    public synchronized long begin(String room) { this.room = room; return ++generation; }
    public synchronized void invalidate() { room = ""; generation++; }
    public synchronized boolean isCurrent(long candidate) { return candidate == generation; }
    public synchronized boolean accepts(long candidate, String messageRoom) {
        return candidate == generation && room.equals(messageRoom);
    }
}
