package com.overleaf.server.collaboration;

import com.overleaf.common.sync.*;
import com.overleaf.server.collaboration.SyncBroadcaster.ClientSession;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Benchmark cho M4 (Collaboration Logic).
 *
 * ĐO GÌ: tốc độ xử lý của bộ máy M4 (Document Sync Manager, CRDT Engine, Operation History,
 * Sync Broadcaster, Collaboration Thread Pool) trong MỘT tiến trình Java.
 * KHÔNG ĐO: độ trễ mạng thật giữa hai máy (M3 chưa có). Vì vậy "độ trễ" ở đây là thời gian
 * từ lúc client gửi thao tác đến lúc nhận ACK ngay trong bộ nhớ.
 *
 * Mỗi phép đo chạy 3 lần (sau 1 lần chạy khởi động) và lấy lần có thông lượng ở giữa.
 * Sau mỗi lần chạy, chương trình kiểm tra văn bản cuối có đủ ký tự (không mất thao tác).
 *
 * CHẠY: Run 'BenchmarkM4.main()' trong IntelliJ (mất khoảng 1 phút). Kết quả in ra màn hình
 * và ghi vào file benchmark-results.md ở thư mục đang chạy. Hãy chạy trên máy của bạn,
 * khi không mở nhiều chương trình khác, rồi dùng số liệu của máy bạn trong báo cáo.
 */
public class BenchmarkM4 {

    /** Máy khách giả đo thời gian. */
    static class BClient implements ClientSession {
        final String id;
        final Semaphore acks = new Semaphore(0);
        final Semaphore edits = new Semaphore(0);
        final AtomicLong clock = new AtomicLong(0);
        volatile long lastAckNanos;
        volatile CountDownLatch checkpointLatch;

        BClient(String id) { this.id = id; }

        public String getUserId() { return id; }
        public boolean isOpen() { return true; }
        public void send(SyncMessage m) {
            switch (m.getMessageType()) {
                case ACK:
                    lastAckNanos = System.nanoTime();
                    acks.release();
                    break;
                case TEXT_EDIT:
                    CRDTOperation op = OperationCodec.fromJson(m.getPayload());
                    clock.accumulateAndGet(op.getLamportTimestamp(), Math::max);
                    edits.release();
                    break;
                case CHECKPOINT:
                    CountDownLatch l = checkpointLatch;
                    if (l != null) l.countDown();
                    break;
                default:
            }
        }
    }

    static class Env {
        final CollaborationThreadPool pool = new CollaborationThreadPool();
        final SyncBroadcaster bc = new SyncBroadcaster(pool);
        final DocumentSyncManager mgr = new DocumentSyncManager(pool, bc, new OperationHistory());
        void close() { pool.shutdown(); }
    }

    /** Kết quả một lần chạy. */
    static class Result {
        double opsPerSec;
        double p50, p95, p99, max; // mili giây
        long ops;
        boolean correct;
        double seconds;
    }

    static final StringBuilder md = new StringBuilder();
    static void out(String s) { System.out.println(s); md.append(s).append('\n'); }

    // ------------------------------------------------------------------------
    // Tiện ích
    // ------------------------------------------------------------------------

    static void join(Env e, BClient c, String room) throws Exception {
        e.mgr.handleMessage(c, SyncMessage.createJoinRoom(room, c.id, c.id));
        // chờ FULL_SYNC/CHECKPOINT đến nơi
        Thread.sleep(30);
    }

    static SyncMessage insertMsg(BClient c, String room, int pos) {
        CRDTOperation op = CRDTOperation.createInsert(pos, "x", c.id, c.clock.incrementAndGet());
        return SyncMessage.createTextEdit(room, c.id, c.id, OperationCodec.toJson(op));
    }

    static Result summarize(List<long[]> latencies, long ops, double seconds, boolean correct) {
        int total = 0;
        for (long[] a : latencies) total += a.length;
        long[] all = new long[total];
        int k = 0;
        for (long[] a : latencies) { System.arraycopy(a, 0, all, k, a.length); k += a.length; }
        Arrays.sort(all);
        Result r = new Result();
        r.ops = ops;
        r.seconds = seconds;
        r.opsPerSec = ops / seconds;
        r.p50 = all[(int) (all.length * 0.50)] / 1e6;
        r.p95 = all[(int) (all.length * 0.95)] / 1e6;
        r.p99 = all[Math.min(all.length - 1, (int) (all.length * 0.99))] / 1e6;
        r.max = all[all.length - 1] / 1e6;
        r.correct = correct;
        return r;
    }

    static Result median(List<Result> runs) {
        List<Result> sorted = new ArrayList<>(runs);
        sorted.sort((a, b) -> Double.compare(a.opsPerSec, b.opsPerSec));
        return sorted.get(sorted.size() / 2);
    }

    // ------------------------------------------------------------------------
    // B1 + B2: nhiều client, nhiều phòng, vòng lặp đóng (gửi → chờ ACK → gửi tiếp)
    // ------------------------------------------------------------------------

    static Result runEditLoad(int rooms, int clientsPerRoom, int opsPerClient) throws Exception {
        Env e = new Env();
        List<BClient> clients = new ArrayList<>();
        List<String> roomOf = new ArrayList<>();
        for (int r = 0; r < rooms; r++) {
            for (int c = 0; c < clientsPerRoom; c++) {
                BClient bc = new BClient("u" + r + "_" + c);
                clients.add(bc);
                roomOf.add("room" + r);
                join(e, bc, "room" + r);
            }
        }
        List<long[]> lat = new ArrayList<>();
        List<Thread> threads = new ArrayList<>();
        for (int i = 0; i < clients.size(); i++) lat.add(new long[opsPerClient]);
        CountDownLatch start = new CountDownLatch(1);
        for (int i = 0; i < clients.size(); i++) {
            final int idx = i;
            Thread t = new Thread(() -> {
                BClient c = clients.get(idx);
                String room = roomOf.get(idx);
                try {
                    start.await();
                    for (int n = 0; n < opsPerClient; n++) {
                        SyncMessage m = insertMsg(c, room, 0);
                        long t0 = System.nanoTime();
                        e.mgr.handleMessage(c, m);
                        c.acks.acquire();
                        lat.get(idx)[n] = c.lastAckNanos - t0;
                    }
                } catch (InterruptedException ex) { Thread.currentThread().interrupt(); }
            });
            threads.add(t);
            t.start();
        }
        long t0 = System.nanoTime();
        start.countDown();
        for (Thread t : threads) t.join();
        double seconds = (System.nanoTime() - t0) / 1e9;
        Thread.sleep(200);
        boolean ok = true;
        for (int r = 0; r < rooms; r++) {
            String text = e.mgr.getDocumentContent("room" + r);
            ok &= text != null && text.length() == clientsPerRoom * opsPerClient;
        }
        e.close();
        return summarize(lat, (long) clients.size() * opsPerClient, seconds, ok);
    }

    // ------------------------------------------------------------------------
    // B3: phát cho nhiều người nhận
    // ------------------------------------------------------------------------

    static Result runFanout(int receivers, int ops) throws Exception {
        Env e = new Env();
        BClient sender = new BClient("sender");
        join(e, sender, "fan");
        List<BClient> rec = new ArrayList<>();
        for (int i = 0; i < receivers; i++) {
            BClient c = new BClient("r" + i);
            rec.add(c);
            join(e, c, "fan");
        }
        long[] lat = new long[ops];
        long t0all = System.nanoTime();
        for (int n = 0; n < ops; n++) {
            SyncMessage m = insertMsg(sender, "fan", 0);
            long t0 = System.nanoTime();
            e.mgr.handleMessage(sender, m);
            for (BClient c : rec) c.edits.acquire();   // chờ TẤT CẢ người nhận
            lat[n] = System.nanoTime() - t0;
        }
        double seconds = (System.nanoTime() - t0all) / 1e9;
        String text = e.mgr.getDocumentContent("fan");
        boolean ok = text != null && text.length() == ops;
        e.close();
        List<long[]> l = new ArrayList<>();
        l.add(lat);
        return summarize(l, ops, seconds, ok);
    }

    // ------------------------------------------------------------------------
    // B4: đồng bộ lại sau offline
    // ------------------------------------------------------------------------

    /** @return thời gian (ms) từ lúc gửi RESYNC đến lúc nhận CHECKPOINT; âm nếu sai kết quả */
    static double runResync(int pending, int missed) throws Exception {
        Env e = new Env();
        BClient alice = new BClient("alice"), bob = new BClient("bob");
        join(e, alice, "rs");
        join(e, bob, "rs");
        OfflineOperationQueue q = new OfflineOperationQueue("rs", "bob", "bob");
        // Bob "đã thấy" đến CHECKPOINT hiện tại (lịch sử trống)
        q.handleServerMessage(new SyncMessage(MessageType.CHECKPOINT, "rs", "SERVER", "Server", ""));
        e.mgr.handleDisconnect("rs", bob);
        q.onConnectionLost();
        for (int i = 0; i < pending; i++) {
            // Bob gõ nối đuôi văn bản local của mình: vị trí i
            q.prepareEdit(CRDTOperation.createInsert(i, "b", "bob", i + 1));
        }
        for (int i = 0; i < missed; i++) {
            e.mgr.handleMessage(alice, insertMsg(alice, "rs", 0));
            alice.acks.acquire();
        }
        bob.checkpointLatch = new CountDownLatch(1);
        SyncMessage resync = q.onReconnected();
        long t0 = System.nanoTime();
        e.mgr.handleMessage(bob, resync);
        boolean arrived = bob.checkpointLatch.await(60, TimeUnit.SECONDS);
        double ms = (System.nanoTime() - t0) / 1e6;
        Thread.sleep(100);
        String text = e.mgr.getDocumentContent("rs");
        boolean ok = arrived && text != null && text.length() == pending + missed;
        e.close();
        return ok ? ms : -ms;
    }

    // ------------------------------------------------------------------------
    // main
    // ------------------------------------------------------------------------

    public static void main(String[] args) throws Exception {
        String outFile = args.length > 0 ? args[0] : "benchmark-results.md";
        Runtime rt = Runtime.getRuntime();

        out("# Kết quả benchmark M4");
        out("");
        out("- Ngày chạy: " + java.time.LocalDateTime.now().withNano(0));
        out("- Java: " + System.getProperty("java.version") + " (" + System.getProperty("java.vm.name") + ")");
        out("- Hệ điều hành: " + System.getProperty("os.name") + " " + System.getProperty("os.version"));
        out("- Số nhân CPU hiển thị cho Java: " + rt.availableProcessors());
        out("- Bộ nhớ tối đa cho Java: " + rt.maxMemory() / (1024 * 1024) + " MB");
        out("- Lưu ý: đo trong một tiến trình, không qua mạng. Thời gian là từ lúc gửi thao tác đến lúc nhận ACK trong bộ nhớ.");
        out("");

        System.out.println("(khởi động JVM...)");
        runEditLoad(1, 4, 1000);
        runFanout(5, 300);

        // ---- B1
        out("## B1. Một phòng, nhiều người cùng gõ");
        out("Tổng 12 000 thao tác chia đều cho các người dùng. Mỗi người gửi một thao tác, chờ ACK rồi mới gửi tiếp.");
        out("");
        out("| Số người | Thao tác/giây | Trung vị (ms) | p95 (ms) | p99 (ms) | Lớn nhất (ms) | Đủ ký tự |");
        out("|---|---|---|---|---|---|---|");
        for (int n : new int[]{1, 2, 4, 8, 16}) {
            List<Result> runs = new ArrayList<>();
            for (int i = 0; i < 3; i++) runs.add(runEditLoad(1, n, 12000 / n));
            Result r = median(runs);
            out(String.format("| %d | %.0f | %.3f | %.3f | %.3f | %.2f | %s |",
                    n, r.opsPerSec, r.p50, r.p95, r.p99, r.max, r.correct ? "đúng" : "SAI"));
        }
        out("");

        // ---- B2
        out("## B2. Nhiều phòng chạy song song");
        out("Mỗi phòng có 4 người, mỗi người gửi 1 500 thao tác.");
        out("");
        out("| Số phòng | Tổng thao tác/giây | Trung vị (ms) | p95 (ms) | p99 (ms) | Đủ ký tự |");
        out("|---|---|---|---|---|---|");
        for (int rooms : new int[]{1, 2, 4, 8}) {
            List<Result> runs = new ArrayList<>();
            for (int i = 0; i < 3; i++) runs.add(runEditLoad(rooms, 4, 1500));
            Result r = median(runs);
            out(String.format("| %d | %.0f | %.3f | %.3f | %.3f | %s |",
                    rooms, r.opsPerSec, r.p50, r.p95, r.p99, r.correct ? "đúng" : "SAI"));
        }
        out("");

        // ---- B3
        out("## B3. Một người gõ, nhiều người nhận");
        out("Một người gửi 1 000 thao tác, mỗi lần chờ cho đến khi TẤT CẢ người nhận đã nhận được.");
        out("");
        out("| Số người nhận | Thao tác/giây | Trung vị (ms) | p95 (ms) | p99 (ms) | Đủ ký tự |");
        out("|---|---|---|---|---|---|");
        for (int k : new int[]{1, 5, 10, 25, 50}) {
            List<Result> runs = new ArrayList<>();
            for (int i = 0; i < 3; i++) runs.add(runFanout(k, 1000));
            Result r = median(runs);
            out(String.format("| %d | %.0f | %.3f | %.3f | %.3f | %s |",
                    k, r.opsPerSec, r.p50, r.p95, r.p99, r.correct ? "đúng" : "SAI"));
        }
        out("");

        // ---- B4
        out("## B4. Đồng bộ lại sau khi offline");
        out("Thời gian từ lúc gửi RESYNC đến lúc nhận CHECKPOINT (trung vị của 3 lần chạy).");
        out("");
        out("| Thao tác Bob viết offline | Thao tác của người khác bỏ lỡ | Thời gian (ms) | Kết quả |");
        out("|---|---|---|---|");
        int[][] cases = {{10, 10}, {100, 100}, {1000, 100}, {100, 1000}, {1000, 1000}};
        for (int[] c : cases) {
            double[] t = new double[3];
            boolean ok = true;
            for (int i = 0; i < 3; i++) {
                double v = runResync(c[0], c[1]);
                ok &= v >= 0;
                t[i] = Math.abs(v);
            }
            Arrays.sort(t);
            out(String.format("| %d | %d | %.1f | %s |", c[0], c[1], t[1], ok ? "đúng" : "SAI"));
        }
        out("");

        try (PrintWriter w = new PrintWriter(outFile, "UTF-8")) {
            w.print(md);
        }
        System.out.println("Đã ghi " + outFile);
        System.exit(0);
    }
}
