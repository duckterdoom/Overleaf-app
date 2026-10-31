package com.overleaf.common.sync;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

/** M3 wire v1: big-endian length, version, five UTF-8 strings, timestamp. No object deserialization. */
public final class MessageCodec {
    public static final int MAX_FRAME_BYTES = 1024 * 1024;
    private MessageCodec() { }

    public static byte[] encode(SyncMessage message) throws IOException {
        if (message == null) throw new IOException("Missing message");
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        DataOutputStream body = new DataOutputStream(buffer);
        body.writeInt(1);
        for (String value : new String[]{message.getMessageType().name(), message.getRoomId(),
                message.getSenderId(), message.getSenderName(), message.getPayload()}) {
            if (value.length() > MAX_FRAME_BYTES) throw new IOException("String too large");
            byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
            if (bytes.length > MAX_FRAME_BYTES || buffer.size() + bytes.length + 4 > MAX_FRAME_BYTES - 8) {
                throw new IOException("Frame too large");
            }
            body.writeInt(bytes.length);
            body.write(bytes);
        }
        body.writeLong(message.getTimestamp());
        return buffer.toByteArray();
    }

    public static void write(OutputStream output, SyncMessage message) throws IOException {
        writeFrame(output, encode(message));
    }

    public static void writeFrame(OutputStream output, byte[] body) throws IOException {
        if (body.length == 0 || body.length > MAX_FRAME_BYTES) throw new IOException("Invalid frame size");
        DataOutputStream stream = new DataOutputStream(output);
        stream.writeInt(body.length);
        stream.write(body);
        stream.flush();
    }

    /** null is clean EOF between frames; EOF inside a frame is an error. */
    public static SyncMessage read(InputStream input) throws IOException {
        int first = input.read();
        if (first < 0) return null;
        DataInputStream stream = new DataInputStream(input);
        int length = first << 24 | stream.readUnsignedByte() << 16
                | stream.readUnsignedByte() << 8 | stream.readUnsignedByte();
        if (length < 1 || length > MAX_FRAME_BYTES) throw new IOException("Invalid frame size");
        byte[] bytes = new byte[length];
        stream.readFully(bytes);
        DataInputStream body = new DataInputStream(new ByteArrayInputStream(bytes));
        if (body.readInt() != 1) throw new IOException("Unsupported protocol version");
        try {
            MessageType type = MessageType.valueOf(readString(body));
            SyncMessage result = new SyncMessage(type, readString(body), readString(body),
                    readString(body), readString(body), body.readLong());
            if (body.available() != 0) throw new IOException("Trailing bytes");
            return result;
        } catch (IllegalArgumentException e) {
            throw new IOException("Invalid message type", e);
        }
    }

    private static String readString(DataInputStream input) throws IOException {
        int length = input.readInt();
        if (length < 0 || length > input.available()) throw new IOException("Invalid string length");
        byte[] bytes = new byte[length];
        input.readFully(bytes);
        return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
    }
}
