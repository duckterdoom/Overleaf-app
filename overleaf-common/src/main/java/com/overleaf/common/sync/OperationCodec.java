package com.overleaf.common.sync;

import com.overleaf.common.sync.CRDTOperation.OpType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * =========================================================================
 * M4: BỘ CHUYỂN ĐỔI JSON CHO THAO TÁC (Operation Codec)
 * =========================================================================
 *
 * Chuyển {@link CRDTOperation} thành chuỗi JSON và ngược lại, để đặt vào
 * payload của {@link SyncMessage}. Tự viết (không dùng thư viện ngoài) và
 * đặt ở mô-đun Common để cả client và server dùng chung một cách mã hóa.
 *
 * Cũng chứa định dạng payload của thông điệp RESYNC:
 *   dòng 1: mã thao tác cuối cùng client đã thấy (có thể rỗng)
 *   các dòng sau: mỗi dòng là JSON của một thao tác chưa được xác nhận
 * (JSON của một thao tác không bao giờ chứa ký tự xuống dòng thật vì đã escape.)
 */
public final class OperationCodec {

    private OperationCodec() {}

    /** Nội dung đã giải mã của một thông điệp RESYNC. */
    public static final class ResyncRequest {
        private final String lastSeenOperationId;
        private final List<CRDTOperation> pending;

        public ResyncRequest(String lastSeenOperationId, List<CRDTOperation> pending) {
            this.lastSeenOperationId = lastSeenOperationId;
            this.pending = pending;
        }

        public String getLastSeenOperationId() { return lastSeenOperationId; }
        public List<CRDTOperation> getPending() { return pending; }
    }

    // =========================================================================
    // RESYNC PAYLOAD
    // =========================================================================

    public static String encodeResync(String lastSeenOperationId, Collection<CRDTOperation> pending) {
        StringBuilder sb = new StringBuilder();
        sb.append(lastSeenOperationId == null ? "" : lastSeenOperationId);
        for (CRDTOperation op : pending) {
            sb.append('\n').append(toJson(op));
        }
        return sb.toString();
    }

    public static ResyncRequest decodeResync(String payload) {
        if (payload == null) {
            throw new IllegalArgumentException("payload rỗng");
        }
        String[] lines = payload.split("\n", -1);
        List<CRDTOperation> pending = new ArrayList<>();
        for (int i = 1; i < lines.length; i++) {
            if (!lines[i].isBlank()) {
                pending.add(fromJson(lines[i]));
            }
        }
        return new ResyncRequest(lines[0].trim(), pending);
    }

    // =========================================================================
    // JSON CỦA MỘT THAO TÁC
    // =========================================================================

    public static String toJson(CRDTOperation op) {
        return "{\"id\":" + quote(op.getOperationId())
                + ",\"type\":" + quote(op.getType().name())
                + ",\"pos\":" + op.getPosition()
                + ",\"content\":" + quote(op.getContent())
                + ",\"user\":" + quote(op.getUserId())
                + ",\"lamport\":" + op.getLamportTimestamp()
                + ",\"ts\":" + op.getCreatedAt() + "}";
    }

    public static CRDTOperation fromJson(String json) {
        if (json == null || json.isEmpty()) {
            throw new IllegalArgumentException("payload rỗng");
        }
        Map<String, String> f = new HashMap<>();
        int i = json.indexOf('{') + 1;
        int n = json.length();
        while (i < n) {
            int ks = json.indexOf('"', i);
            if (ks < 0) break;
            int[] end = new int[1];
            String key = readString(json, ks, end);
            int colon = json.indexOf(':', end[0]);
            int vs = colon + 1;
            while (vs < n && json.charAt(vs) == ' ') vs++;
            String value;
            if (json.charAt(vs) == '"') {
                value = readString(json, vs, end);
                i = end[0];
            } else {
                int ve = vs;
                while (ve < n && json.charAt(ve) != ',' && json.charAt(ve) != '}') ve++;
                value = json.substring(vs, ve).trim();
                i = ve;
            }
            f.put(key, value);
        }
        return new CRDTOperation(
                f.get("id"),
                OpType.valueOf(f.get("type")),
                Integer.parseInt(f.get("pos")),
                f.get("content"),
                f.get("user"),
                Long.parseLong(f.get("lamport")),
                Long.parseLong(f.get("ts")));
    }

    private static String quote(String s) {
        StringBuilder sb = new StringBuilder("\"");
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"':  sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n");  break;
                case '\r': sb.append("\\r");  break;
                case '\t': sb.append("\\t");  break;
                default:
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
            }
        }
        return sb.append('"').toString();
    }

    /** Đọc chuỗi JSON bắt đầu tại dấu " ở vị trí start; end[0] = vị trí sau dấu " đóng. */
    private static String readString(String s, int start, int[] end) {
        StringBuilder sb = new StringBuilder();
        int i = start + 1;
        while (i < s.length()) {
            char c = s.charAt(i);
            if (c == '"') { end[0] = i + 1; return sb.toString(); }
            if (c == '\\') {
                char e = s.charAt(++i);
                switch (e) {
                    case 'n': sb.append('\n'); break;
                    case 'r': sb.append('\r'); break;
                    case 't': sb.append('\t'); break;
                    case 'u': sb.append((char) Integer.parseInt(s.substring(i + 1, i + 5), 16)); i += 4; break;
                    default:  sb.append(e);
                }
            } else {
                sb.append(c);
            }
            i++;
        }
        throw new IllegalArgumentException("JSON chuỗi không đóng");
    }
}
