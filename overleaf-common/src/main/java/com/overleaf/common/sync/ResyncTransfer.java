package com.overleaf.common.sync;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Split an offline upload without independently rebasing its batches. M4 sees one atomic RESYNC. */
public final class ResyncTransfer {
    public static final int MAX_BYTES = 4 * 1024 * 1024;
    public static final int MAX_OPERATIONS = 30000;
    private static final int PART_CHARS = 64 * 1024;
    private ResyncTransfer() { }

    public static List<byte[]> encode(SyncMessage message) throws IOException {
        if (message.getMessageType() != MessageType.RESYNC) return List.of(MessageCodec.encode(message));
        String payload = message.getPayload();
        if (payload.length() > MAX_BYTES || payload.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
            throw new IOException("RESYNC_LIMIT: offline upload exceeds 4 MiB; pending edits retained");
        }
        if (payload.length() <= PART_CHARS) return List.of(MessageCodec.encode(message));
        List<byte[]> frames = new ArrayList<>();
        frames.add(MessageCodec.encode(part(message, MessageType.RESYNC_BEGIN,
                Integer.toString(payload.getBytes(StandardCharsets.UTF_8).length))));
        for (int offset = 0; offset < payload.length();) {
            int end = Math.min(offset + PART_CHARS, payload.length());
            if (end < payload.length() && Character.isHighSurrogate(payload.charAt(end - 1))) end--;
            frames.add(MessageCodec.encode(part(message, MessageType.RESYNC_PART, payload.substring(offset, end))));
            offset = end;
        }
        frames.add(MessageCodec.encode(part(message, MessageType.RESYNC_END, "")));
        return frames;
    }

    private static SyncMessage part(SyncMessage original, MessageType type, String payload) {
        return new SyncMessage(type, original.getRoomId(), original.getSenderId(), original.getSenderName(), payload);
    }

    /** One receiver per authenticated connection. A truncated/disconnected upload never reaches M4. */
    public static final class Receiver {
        private SyncMessage beginning;
        private StringBuilder payload;
        private int expectedBytes;
        private int receivedBytes;
        private int parts;

        public SyncMessage accept(SyncMessage message) throws IOException {
            MessageType type = message.getMessageType();
            if (type == MessageType.RESYNC_BEGIN) {
                if (beginning != null) throw new IOException("RESYNC already in progress");
                try { expectedBytes = Integer.parseInt(message.getPayload()); }
                catch (NumberFormatException e) { throw new IOException("Invalid RESYNC length", e); }
                if (expectedBytes < 1 || expectedBytes > MAX_BYTES) throw new IOException("RESYNC_LIMIT");
                beginning = message;
                payload = new StringBuilder();
                receivedBytes = 0;
                parts = 0;
                return null;
            }
            if (type != MessageType.RESYNC_PART && type != MessageType.RESYNC_END) {
                if (beginning != null) throw new IOException("Interleaved RESYNC upload");
                return message;
            }
            if (beginning == null || !message.getRoomId().equals(beginning.getRoomId())
                    || !message.getSenderId().equals(beginning.getSenderId())
                    || !message.getSenderName().equals(beginning.getSenderName())) throw new IOException("Invalid RESYNC envelope");
            if (type == MessageType.RESYNC_PART) {
                if (++parts > 128) throw new IOException("Too many RESYNC parts");
                receivedBytes += message.getPayload().getBytes(StandardCharsets.UTF_8).length;
                if (receivedBytes > expectedBytes) throw new IOException("RESYNC_LIMIT");
                payload.append(message.getPayload());
                return null;
            }
            if (receivedBytes != expectedBytes || !message.getPayload().isEmpty()) throw new IOException("Incomplete RESYNC upload");
            SyncMessage complete = part(beginning, MessageType.RESYNC, payload.toString());
            beginning = null;
            payload = null;
            return complete;
        }
    }
}
