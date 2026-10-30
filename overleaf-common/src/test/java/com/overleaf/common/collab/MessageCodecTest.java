package com.overleaf.common.collab;

import org.junit.jupiter.api.Test;
import java.io.*;
import java.util.Arrays;
import static org.junit.jupiter.api.Assertions.*;

class MessageCodecTest {
    @Test
    void roundTripsUnicodeNullsLargeTextAndConsecutiveFrames() throws Exception {
        SyncMessage original = new SyncMessage(MessageType.TEXT_UPDATE, "Nguyễn", "room", "Tiếng Việt 🌏\n".repeat(10000));
        original.setLine(12);
        original.setColumn(34);
        ByteArrayOutputStream wire = new ByteArrayOutputStream();
        MessageCodec.write(wire, original);
        MessageCodec.write(wire, new SyncMessage(MessageType.JOIN_ROOM, null, null, null));
        InputStream input = new ByteArrayInputStream(wire.toByteArray());
        SyncMessage copy = MessageCodec.read(input);
        assertEquals(original.getType(), copy.getType());
        assertEquals(original.getSender(), copy.getSender());
        assertEquals(original.getRoomId(), copy.getRoomId());
        assertEquals(original.getContent(), copy.getContent());
        assertEquals(original.getTimestamp(), copy.getTimestamp());
        assertEquals(12, copy.getLine());
        assertEquals(34, copy.getColumn());
        assertNull(MessageCodec.read(input).getContent());
        assertNull(MessageCodec.read(input));
    }

    @Test
    void acceptsFragmentedTcpReads() throws Exception {
        ByteArrayOutputStream wire = new ByteArrayOutputStream();
        MessageCodec.write(wire, new SyncMessage(MessageType.CHAT_MESSAGE, "a", "r", "hello"));
        InputStream fragments = new FilterInputStream(new ByteArrayInputStream(wire.toByteArray())) {
            @Override public int read(byte[] bytes, int offset, int length) throws IOException {
                return super.read(bytes, offset, Math.min(length, 1));
            }
        };
        assertEquals("hello", MessageCodec.read(fragments).getContent());
    }

    @Test
    void rejectsInvalidLengthsTruncationVersionAndTrailingBytes() throws Exception {
        for (int length : new int[]{-1, 0, MessageCodec.MAX_FRAME_BYTES + 1}) {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            new DataOutputStream(buffer).writeInt(length);
            assertThrows(IOException.class, () -> MessageCodec.read(new ByteArrayInputStream(buffer.toByteArray())));
        }
        byte[] body = MessageCodec.encode(new SyncMessage(MessageType.TEXT_UPDATE, "a", "r", "hi"));
        ByteArrayOutputStream wire = new ByteArrayOutputStream();
        MessageCodec.writeFrame(wire, body);
        byte[] full = wire.toByteArray();
        for (int length = 1; length < full.length; length++) {
            byte[] truncated = Arrays.copyOf(full, length);
            assertThrows(IOException.class, () -> MessageCodec.read(new ByteArrayInputStream(truncated)));
        }
        body[3] = 99;
        wire.reset();
        MessageCodec.writeFrame(wire, body);
        assertThrows(IOException.class, () -> MessageCodec.read(new ByteArrayInputStream(wire.toByteArray())));
        body[3] = 1;
        wire.reset();
        MessageCodec.writeFrame(wire, Arrays.copyOf(body, body.length + 1));
        assertThrows(IOException.class, () -> MessageCodec.read(new ByteArrayInputStream(wire.toByteArray())));
        assertThrows(IOException.class, () -> MessageCodec.encode(new SyncMessage(MessageType.TEXT_UPDATE, "a", "r", "x".repeat(MessageCodec.MAX_FRAME_BYTES))));
    }
}
