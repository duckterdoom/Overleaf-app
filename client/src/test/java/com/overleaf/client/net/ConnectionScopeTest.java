package com.overleaf.client.net;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ConnectionScopeTest {
    @Test void delayedCallbackCannotChangeNewRoomOrReconnectedSession() {
        ConnectionScope scope = new ConnectionScope();
        long old = scope.begin("room-a");
        int[] applied = {0};
        Runnable delayed = () -> { if (scope.accepts(old, "room-a")) applied[0]++; };
        long current = scope.begin("room-b");
        delayed.run();
        assertEquals(0, applied[0]);
        assertFalse(scope.accepts(current, "room-a"));
        assertTrue(scope.accepts(current, "room-b"));
        long reconnect = scope.begin("room-b");
        assertFalse(scope.accepts(current, "room-b"));
        assertTrue(scope.accepts(reconnect, "room-b"));
        scope.invalidate();
        assertFalse(scope.isCurrent(reconnect));
    }
}
