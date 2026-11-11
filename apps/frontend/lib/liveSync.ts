import { ChangeSet, Compartment, Text, Transaction } from "@codemirror/state";
import { collab, getSyncedVersion, receiveUpdates, sendableUpdates } from "@codemirror/collab";
import type { EditorView } from "@codemirror/view";

type Callbacks = {
    saved: () => string; dirty: () => boolean;
    synced: (text: string, pending: boolean) => void;
    error: (message: string) => void;
};
const normalize = (text: string) => text.replace(/\r\n?/g, "\n");
export class LiveSync {
    compartment = new Compartment();
    extension = this.compartment.of([]);
    view: EditorView | null = null;
    ws: WebSocket | null = null;
    file = "";
    room = "";
    epoch = "";
    clientID = "";
    base = "";
    initialized = false;
    applying = false;
    blocked = false;
    inFlight = false;
    opening = false;
    timer: ReturnType<typeof setTimeout> | undefined;
    constructor(public callbacks: Callbacks) {}
    get active() { return this.initialized && !this.blocked; }
    get pending() { return !!this.view && this.initialized && sendableUpdates(this.view.state).length > 0; }
    bind(ws: WebSocket, file: string, view: EditorView, userId: string) {
        if (this.room !== ws.url || this.view !== view || this.file !== file || !this.clientID.startsWith(userId + ':')) {
            this.room = ws.url;
            this.view = view; this.file = file; this.epoch = "";
            this.clientID = `${userId}:${crypto.randomUUID()}`;
            this.initialized = false; this.blocked = false; this.base = "";
            this.opening = false;
        }
        const newSocket = this.ws !== ws;
        this.ws = ws;
        if (newSocket || !this.initialized) this.open();
    }
    open() {
        if (this.ws?.readyState !== WebSocket.OPEN || !this.view || !this.file || this.blocked || this.opening) return;
        this.opening = true;
        this.inFlight = false;
        this.ws.send(JSON.stringify({ type: "sync-open", file: this.file, clientID: this.clientID,
            epoch: this.epoch, version: this.initialized ? getSyncedVersion(this.view.state) : 0 }));
    }
    disconnect() { this.ws = null; this.inFlight = false; this.opening = false; clearTimeout(this.timer); this.timer = undefined; }
    changed() {
        if (this.applying) return;
        // Send at most once per animation-sized batch; continuous typing must
        // not keep postponing the first update as a trailing debounce does.
        if (this.timer !== undefined) return;
        this.timer = setTimeout(() => { this.timer = undefined; this.push(); }, 16);
    }
    push() {
        if (!this.active || this.inFlight || !this.view || this.ws?.readyState !== WebSocket.OPEN) return;
        const updates = sendableUpdates(this.view.state).slice(0, 100);
        if (!updates.length) return;
        this.inFlight = true;
        this.ws.send(JSON.stringify({ type: "sync-push", file: this.file, epoch: this.epoch,
            version: getSyncedVersion(this.view.state), updates: updates.map(update => update.changes.toJSON()) }));
    }
    fail(message: string) { this.blocked = true; this.callbacks.error(message); }
    message(data: any) {
        const view = this.view;
        if (!view || data.file !== this.file || this.blocked) return;
        this.opening = false;
        try {
            if (data.type === "sync-snapshot") {
                const local = view.state.doc.toString();
                const pending = this.initialized ? this.pending : this.callbacks.dirty();
                if (pending && local !== data.text && normalize(this.callbacks.saved()) !== data.text) {
                    this.fail("Không thể tự nối lại bản sửa với phiên mới trên server. Nội dung chưa đồng bộ vẫn được giữ trong editor; sao chép bản này trước khi tải lại.");
                    return;
                }
                this.applying = true;
                // Removing then adding ensures a new authority epoch resets version state.
                view.dispatch({ effects: this.compartment.reconfigure([]),
                    changes: local === data.text ? undefined : { from: 0, to: view.state.doc.length, insert: data.text },
                    annotations: Transaction.addToHistory.of(false) });
                view.dispatch({ effects: this.compartment.reconfigure(collab({ startVersion: data.version, clientID: this.clientID })) });
                this.epoch = data.epoch; this.base = data.text; this.initialized = true;
                this.inFlight = false; this.applying = false;
                this.callbacks.synced(this.base, false);
                if (pending && local !== data.text) {
                    // Restore edits typed during initial handshake as a normal local change.
                    let from = 0, endA = data.text.length, endB = local.length;
                    while (from < endA && from < endB && data.text[from] === local[from]) from++;
                    while (endA > from && endB > from && data.text[endA - 1] === local[endB - 1]) { endA--; endB--; }
                    view.dispatch({ changes: { from, to: endA, insert: local.slice(from, endB) } });
                }
                this.push();
            } else if (data.type === "sync-updates") {
                if (!this.initialized || data.epoch !== this.epoch) { this.open(); return; }
                const version = getSyncedVersion(view.state);
                if (data.from > version) { this.open(); return; }
                const updates = data.updates.slice(Math.max(0, version - data.from)).map((update: any) => ({
                    changes: ChangeSet.fromJSON(update.changes), clientID: update.clientID,
                }));
                // Compute the authority baseline independently of unconfirmed local edits.
                let base = Text.of(this.base.split("\n"));
                for (const update of updates) base = update.changes.apply(base);
                this.applying = true;
                if (updates.length) view.dispatch(receiveUpdates(view.state, updates));
                this.base = base.toString(); this.inFlight = false; this.applying = false;
                this.callbacks.synced(this.base, this.pending);
                this.push();
            }
        } catch {
            this.applying = false;
            this.fail("Đồng bộ tạm dừng để bảo vệ bản sửa. Hãy sao chép nội dung chưa lưu trước khi tải lại trang.");
        }
    }
    async flush() {
        this.push();
        const until = Date.now() + 15000;
        while (Date.now() < until) {
            if (this.blocked || !this.ws || this.ws.readyState !== WebSocket.OPEN) break;
            if (this.initialized && !this.pending) return true;
            await new Promise(resolve => setTimeout(resolve, 40));
        }
        this.callbacks.error("Chưa xác nhận lưu đồng bộ với server. Bản sửa vẫn ở editor; kiểm tra kết nối rồi thử lưu lại.");
        return false;
    }
}
