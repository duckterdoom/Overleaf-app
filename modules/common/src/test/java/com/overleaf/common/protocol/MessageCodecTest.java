package com.overleaf.common.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.overleaf.common.sync.OfflineOperationQueue;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class MessageCodecTest {
    private byte[] wire(SyncMessage message) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        MessageCodec.write(out, message);
        return out.toByteArray();
    }

    @Test
    void roundTripsAllFieldsAndUnicode() throws Exception {
        SyncMessage source =
                new SyncMessage(
                        MessageType.FULL_SYNC,
                        "room",
                        "id",
                        "Nguyễn",
                        "LaTeX 🌏\n".repeat(12000),
                        42L,
                        123L);
        SyncMessage result = MessageCodec.read(new ByteArrayInputStream(wire(source)));
        assertEquals(source.getMessageType(), result.getMessageType());
        assertEquals(source.getRoomId(), result.getRoomId());
        assertEquals(source.getSenderId(), result.getSenderId());
        assertEquals(source.getSenderName(), result.getSenderName());
        assertEquals(source.getPayload(), result.getPayload());
        assertEquals(42L, result.getTimestamp());
        assertEquals(123L, result.getLamportTimestamp());
    }

    @Test
    void handlesFragmentationAndConsecutiveFrames() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        MessageCodec.write(out, SyncMessage.createHeartbeat("r", "u"));
        MessageCodec.write(out, SyncMessage.createError("r", "e"));
        InputStream input =
                new FilterInputStream(new ByteArrayInputStream(out.toByteArray())) {
                    @Override
                    public int read(byte[] b, int off, int len) throws IOException {
                        return super.read(b, off, Math.min(1, len));
                    }
                };
        assertEquals(MessageType.HEARTBEAT, MessageCodec.read(input).getMessageType());
        assertEquals(MessageType.ERROR, MessageCodec.read(input).getMessageType());
        assertNull(MessageCodec.read(input));
    }

    @Test
    void rejectsLengthTruncationVersionAndTrailingBytes() throws Exception {
        for (int size : new int[] {-1, 0, MessageCodec.MAX_FRAME_BYTES + 1}) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            new DataOutputStream(out).writeInt(size);
            assertThrows(
                    IOException.class,
                    () -> MessageCodec.read(new ByteArrayInputStream(out.toByteArray())));
        }
        byte[] valid = wire(SyncMessage.createHeartbeat("r", "u"));
        for (int size = 1; size < valid.length; size++) {
            byte[] truncated = Arrays.copyOf(valid, size);
            assertThrows(
                    IOException.class,
                    () -> MessageCodec.read(new ByteArrayInputStream(truncated)));
        }
        byte[] body = MessageCodec.encode(SyncMessage.createHeartbeat("r", "u"));
        body[3] = 9;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        MessageCodec.writeFrame(out, body);
        assertThrows(
                IOException.class,
                () -> MessageCodec.read(new ByteArrayInputStream(out.toByteArray())));
        body[3] = 1;
        out.reset();
        MessageCodec.writeFrame(out, Arrays.copyOf(body, body.length + 1));
        assertThrows(
                IOException.class,
                () -> MessageCodec.read(new ByteArrayInputStream(out.toByteArray())));
        assertThrows(
                IOException.class,
                () ->
                        MessageCodec.encode(
                                SyncMessage.createFullSync(
                                        "r", "x".repeat(MessageCodec.MAX_FRAME_BYTES))));
    }

    @Test
    void rejectsUnknownTypeAndMalformedUtf8() throws Exception {
        byte[] body = MessageCodec.encode(SyncMessage.createHeartbeat("r", "u"));
        body[8] = (byte) 0xff;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        MessageCodec.writeFrame(out, body);
        assertThrows(
                IOException.class,
                () -> MessageCodec.read(new ByteArrayInputStream(out.toByteArray())));
        body[8] = 'Z';
        out.reset();
        MessageCodec.writeFrame(out, body);
        assertThrows(
                IOException.class,
                () -> MessageCodec.read(new ByteArrayInputStream(out.toByteArray())));
    }

    @Test
    void checkpointMakesFreshJoinOnline() {
        OfflineOperationQueue queue = new OfflineOperationQueue("r", "u", "u");
        queue.onConnectionLost();
        queue.handleServerMessage(
                new SyncMessage(MessageType.CHECKPOINT, "r", "SERVER", "Server", ""));
        assertTrue(queue.isOnline());
        assertFalse(queue.isResyncing());
    }

    @Test
    void readsLegacyVersionOneWithoutClock() throws Exception {
        byte[] body =
                MessageCodec.encode(
                        SyncMessage.createFullSync("r", "hello").withLamportTimestamp(123));
        body = Arrays.copyOf(body, body.length - 8);
        body[3] = 1;
        ByteArrayOutputStream wire = new ByteArrayOutputStream();
        MessageCodec.writeFrame(wire, body);
        SyncMessage legacy = MessageCodec.read(new ByteArrayInputStream(wire.toByteArray()));
        assertEquals("hello", legacy.getPayload());
        assertEquals(0, legacy.getLamportTimestamp());
    }
}
