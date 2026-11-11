import { useCallback, useEffect, useRef, useState, type RefObject } from "react";
import { StateEffect, StateField } from "@codemirror/state";
import { Decoration, EditorView, WidgetType, type DecorationSet } from "@codemirror/view";
import type { LiveSync } from "./liveSync";

export type Peer = {
    connectionId: string; userId: string; name: string; color: string;
    file: string; anchor: number; head: number; digest: string; active: boolean;
};
const setPeers = StateEffect.define<Peer[]>();
class Cursor extends WidgetType {
    constructor(readonly peer: Peer) { super(); }
    eq(other: Cursor) { return this.peer.name === other.peer.name && this.peer.color === other.peer.color; }
    toDOM() {
        const cursor = document.createElement("span");
        cursor.className = "remote-cursor";
        cursor.style.setProperty("--peer-color", this.peer.color);
        cursor.setAttribute("aria-label", this.peer.name);
        const label = cursor.appendChild(document.createElement("span"));
        label.className = "remote-cursor-label";
        label.textContent = this.peer.name;
        return cursor;
    }
}
export const presenceHighlights = StateField.define<DecorationSet>({
    create: () => Decoration.none,
    update(value, tr) {
        // A cursor is valid only for the identical document digest, never map a stale cursor.
        if (tr.docChanged) value = Decoration.none;
        for (const effect of tr.effects) if (effect.is(setPeers)) {
            const ranges = effect.value.flatMap(peer => {
                if (peer.head > tr.state.doc.length) return [];
                const cursor = Decoration.widget({ widget: new Cursor(peer), side: 1 }).range(peer.head);
                return [cursor];
            });
            value = Decoration.set(ranges, true);
        }
        return value;
    },
    provide: field => EditorView.decorations.from(field),
});

export default function usePresence(project: string | undefined, file: string,
    userId: string | undefined, enabled: boolean, editor: RefObject<EditorView | null>, live: LiveSync) {
    const [peers, setMembers] = useState<Peer[]>([]);
    const [connected, setConnected] = useState(false);
    const [digest, setDigest] = useState("");
    const current = useRef({ file, userId });
    current.current = { file, userId };
    const socket = useRef<WebSocket | null>(null);
    const members = useRef<Peer[]>([]);
    const sequence = useRef(0);
    const cache = useRef({ text: "", digest: "" });
    const timer = useRef<ReturnType<typeof setTimeout> | undefined>(undefined);
    const renderAndSend = useCallback(async (send = true) => {
        const view = editor.current, ws = socket.current;
        if (!view || !ws || ws.readyState !== WebSocket.OPEN || !current.current.file) return;
        live.bind(ws, current.current.file, view, current.current.userId || "");
        const ticket = ++sequence.current;
        const text = view.state.doc.toString(), path = current.current.file;
        let digest = cache.current.text === text ? cache.current.digest : "";
        if (!digest) {
            const bytes = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(text));
            digest = Array.from(new Uint8Array(bytes), n => n.toString(16).padStart(2, "0")).join("");
        }
        if (ticket !== sequence.current || editor.current !== view || current.current.file !== path
            || view.state.doc.toString() !== text || ws !== socket.current || ws.readyState !== WebSocket.OPEN) return;
        cache.current = { text, digest };
        setDigest(digest);
        const selection = view.state.selection.main;
        if (send) ws.send(JSON.stringify({ type: "cursor", file: path, digest, anchor: selection.head,
            head: selection.head, active: view.hasFocus && !document.hidden }));
        view.dispatch({ effects: setPeers.of(members.current.filter(peer => peer.userId !== current.current.userId
            && peer.file === path && peer.digest === digest)) });
    }, [editor, live]);
    const update = useCallback(() => {
        clearTimeout(timer.current);
        timer.current = setTimeout(() => { void renderAndSend(); }, 80);
    }, [renderAndSend]);
    useEffect(() => { update(); }, [file, update]);
    useEffect(() => {
        if (!enabled || !project || !userId) return;
        let stopped = false, retry: ReturnType<typeof setTimeout>;
        let backoff = 1000;
        const clear = () => {
            setConnected(false); setMembers([]); members.current = [];
            editor.current?.dispatch({ effects: setPeers.of([]) });
        };
        const connect = () => {
            const ws = new WebSocket(`${location.protocol === "https:" ? "wss:" : "ws:"}//${location.host}/api/projects/${project}/presence`);
            socket.current = ws;
            ws.onopen = () => {
                backoff = 1000; setConnected(true); update();
                window.dispatchEvent(new CustomEvent('latex-review-changed', { detail: { project } }));
            };
            ws.onmessage = event => {
                if (stopped) return;
                const data = JSON.parse(event.data);
                if (data.type === 'review-changed' && data.project === project) {
                    window.dispatchEvent(new CustomEvent('latex-review-changed', { detail: { project } }));
                    return;
                }
                if (data.type.startsWith("sync-")) {
                    if (data.file === current.current.file && live.view === editor.current) live.message(data);
                    update();
                    return;
                }
                if (data.type === "presence") {
                    members.current = data.members;
                    setMembers(data.members);
                    void renderAndSend(false);
                }
            };
            ws.onclose = () => {
                if (stopped) return;
                clear();
                live.disconnect();
                retry = setTimeout(connect, backoff);
                backoff = Math.min(backoff * 2, 15000);
            };
            ws.onerror = () => ws.close();
        };
        connect();
        const heartbeat = setInterval(() => {
            if (socket.current?.readyState === WebSocket.OPEN)
                socket.current.send(JSON.stringify({ type: "ping" }));
            update();
        }, 15000);
        window.addEventListener("focus", update);
        window.addEventListener("blur", update);
        document.addEventListener("visibilitychange", update);
        return () => {
            stopped = true; ++sequence.current;
            clearTimeout(retry); clearTimeout(timer.current); clearInterval(heartbeat);
            window.removeEventListener("focus", update); window.removeEventListener("blur", update);
            document.removeEventListener("visibilitychange", update);
            socket.current?.close(); socket.current = null; clear();
            live.disconnect();
        };
    }, [enabled, project, userId, editor, update, renderAndSend, live]);
    return { peers, connected, update, digest };
}
