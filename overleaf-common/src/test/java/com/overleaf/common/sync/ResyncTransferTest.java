package com.overleaf.common.sync;

import org.junit.jupiter.api.Test;
import java.io.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ResyncTransferTest {
    @Test void assemblesUnicodeWithoutBreakingSurrogatePairs() throws Exception {
        String payload = "x".repeat(65535) + "🌏Tiếng Việt".repeat(10000);
        SyncMessage message = new SyncMessage(MessageType.RESYNC, "r", "u", "User", payload);
        ResyncTransfer.Receiver receiver = new ResyncTransfer.Receiver();
        List<byte[]> frames = ResyncTransfer.encode(message);
        assertTrue(frames.size() > 2);
        for (int i = 0; i < frames.size(); i++) {
            ByteArrayOutputStream wire = new ByteArrayOutputStream();
            MessageCodec.writeFrame(wire, frames.get(i));
            SyncMessage assembled = receiver.accept(MessageCodec.read(new ByteArrayInputStream(wire.toByteArray())));
            if (i < frames.size() - 1) assertNull(assembled);
            else { assertEquals(payload, assembled.getPayload()); assertEquals(MessageType.RESYNC, assembled.getMessageType()); }
        }
    }

    @Test void rejectsIncompleteWrongIdentityAndOversizedUploads() throws Exception {
        ResyncTransfer.Receiver receiver = new ResyncTransfer.Receiver();
        receiver.accept(new SyncMessage(MessageType.RESYNC_BEGIN, "r", "u", "u", "3"));
        assertNull(receiver.accept(new SyncMessage(MessageType.RESYNC_PART, "r", "u", "u", "a")));
        assertThrows(IOException.class, () -> receiver.accept(new SyncMessage(MessageType.RESYNC_END, "r", "u", "u", "")));
        assertThrows(IOException.class, () -> receiver.accept(new SyncMessage(MessageType.RESYNC_PART, "other", "u", "u", "a")));
        assertThrows(IOException.class, () -> new ResyncTransfer.Receiver().accept(
                new SyncMessage(MessageType.RESYNC_BEGIN, "r", "u", "u", Integer.toString(ResyncTransfer.MAX_BYTES + 1))));
        assertThrows(IOException.class, () -> ResyncTransfer.encode(new SyncMessage(MessageType.RESYNC, "r", "u", "u", "x".repeat(ResyncTransfer.MAX_BYTES + 1))));
    }

    @Test void batchAckOnlyRemovesAcknowledgedOperations() {
        OfflineOperationQueue queue = new OfflineOperationQueue("r", "u", "u");
        queue.onConnectionLost();
        for (int i = 0; i < 3; i++) queue.prepareEdit(new CRDTOperation("op" + i,
                CRDTOperation.OpType.INSERT, i, "x", "u", i + 1, 1));
        queue.handleServerMessage(new SyncMessage(MessageType.ACK_BATCH, "r", "SERVER", "Server", "op0\nop1"));
        assertEquals(1, queue.getPendingCount());
        assertEquals("op2", queue.getPendingOperations().get(0).getOperationId());
    }
}
