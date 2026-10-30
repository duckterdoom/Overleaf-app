package com.overleaf.common.collab;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

/** Versioned, big-endian, length-prefixed UTF-8 protocol shared by both peers. */
public final class MessageCodec {
    public static final int MAX_FRAME_BYTES = 1024 * 1024;
    private static final int VERSION = 1;

    private MessageCodec() { }

    public static byte[] encode(SyncMessage message) throws IOException {
        if (message == null || message.getType() == null) {
            throw new IOException("Message type is required");
        }
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        DataOutputStream body = new DataOutputStream(buffer);
        body.writeInt(VERSION);
        writeString(body, message.getType().name());
        writeString(body, message.getSender());
        writeString(body, message.getRoomId());
        writeString(body, message.getContent());
        body.writeInt(message.getLine());
        body.writeInt(message.getColumn());
        body.writeLong(message.getTimestamp());
        if (buffer.size() > MAX_FRAME_BYTES) throw new IOException("Frame too large");
        return buffer.toByteArray();
    }

    public static void write(OutputStream output, SyncMessage message) throws IOException {
        writeFrame(output, encode(message));
    }

    public static void writeFrame(OutputStream output, byte[] frame) throws IOException {
        if (frame.length <= 0 || frame.length > MAX_FRAME_BYTES) throw new IOException("Invalid frame length");
        DataOutputStream stream = new DataOutputStream(output);
        stream.writeInt(frame.length);
        stream.write(frame);
        stream.flush();
    }

    /** Returns null only for clean EOF between frames; truncated frames throw IOException. */
    public static SyncMessage read(InputStream input) throws IOException {
        int first = input.read();
        if (first == -1) return null;
        DataInputStream stream = new DataInputStream(input);
        int length = (first << 24) | (stream.readUnsignedByte() << 16)
                | (stream.readUnsignedByte() << 8) | stream.readUnsignedByte();
        if (length <= 0 || length > MAX_FRAME_BYTES) throw new IOException("Invalid frame length: " + length);
        byte[] bytes = new byte[length];
        stream.readFully(bytes);
        DataInputStream body = new DataInputStream(new ByteArrayInputStream(bytes));
        if (body.readInt() != VERSION) throw new IOException("Unsupported protocol version");
        MessageType type;
        try {
            type = MessageType.valueOf(readString(body));
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new IOException("Unknown message type", e);
        }
        SyncMessage message = new SyncMessage(type, readString(body), readString(body), readString(body));
        message.setLine(body.readInt());
        message.setColumn(body.readInt());
        message.setTimestamp(body.readLong());
        if (body.available() != 0) throw new IOException("Unexpected trailing bytes");
        return message;
    }

    private static void writeString(DataOutputStream stream, String value) throws IOException {
        if (value == null) {
            stream.writeInt(-1);
            return;
        }
        if (value.length() > MAX_FRAME_BYTES) throw new IOException("String too large");
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_FRAME_BYTES) throw new IOException("String too large");
        stream.writeInt(bytes.length);
        stream.write(bytes);
    }

    private static String readString(DataInputStream stream) throws IOException {
        int length = stream.readInt();
        if (length == -1) return null;
        if (length < 0 || length > stream.available()) throw new IOException("Invalid string length");
        byte[] bytes = new byte[length];
        stream.readFully(bytes);
        try {
            return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException e) {
            throw new IOException("Invalid UTF-8", e);
        }
    }
}
