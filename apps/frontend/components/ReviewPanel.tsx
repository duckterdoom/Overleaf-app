"use client";
import { useEffect, useMemo, useRef, useState } from "react";
import type { EditorView } from "@codemirror/view";
import { isServerStorage, json } from "../lib/api";
import { errorText, translator } from "../lib/parity";
import { setReviewHighlights } from "../lib/reviewHighlights";

export type ReviewAnchor = {
    file: string;
    from: number;
    line: number;
    quote: string;
};
type Thread = ReviewAnchor & {
    id: string;
    resolved: boolean;
    messages: { id: string; author: string; time: string; text: string }[];
};
export default function ReviewPanel({
    project,
    file,
    source,
    language,
    editor,
    draft,
    onDraft,
    onClose,
    openFile,
    visible,
    readonly,
}: {
    project: string;
    file: string;
    language: string;
    source: string;
    editor: React.RefObject<EditorView | null>;
    draft: ReviewAnchor | null;
    onDraft: (value: ReviewAnchor | null) => void;
    onClose: () => void;
    openFile: (path: string) => Promise<void>;
    visible: boolean;
    readonly: boolean;
}) {
    const t = useMemo(() => translator(language), [language]);
    const mutation = useRef(0);
    const [threads, setThreads] = useState<Thread[]>([]),
        [text, setText] = useState(""),
        [reply, setReply] = useState<Record<string, string>>({}),
        [all, setAll] = useState(false),
        [resolved, setResolved] = useState(false),
        [error, setError] = useState(""),
        [busy, setBusy] = useState(false),
        [active, setActive] = useState<Thread | null>(null);
    const route = `/projects/${project}/review`;
    useEffect(() => {
        let alive = true, loading = false, repeat = false;
        let signature = '';
        async function refresh(force = false) {
            if ((!visible && !force) || !alive) return;
            if (loading) { repeat = true; return; }
            loading = true;
            const version = mutation.current;
            try {
                const value = await json<Thread[]>(route);
                const next = JSON.stringify(value);
                if (alive && mutation.current === version && signature !== next) {
                    signature = next;
                    setThreads(value);
                }
            } catch (e) {
                if (alive) setError(errorText(e));
            } finally {
                loading = false;
                if (repeat && alive) { repeat = false; void refresh(true); }
            }
        }
        const changed = (event: Event) => {
            if ((event as CustomEvent).detail?.project === project) void refresh(true);
        };
        const focused = () => { if (visible) void refresh(); };
        void refresh(true);
        const poll = visible ? setInterval(() => void refresh(), readonly ? 1500 : 5000) : undefined;
        window.addEventListener('latex-review-changed', changed);
        window.addEventListener('focus', focused);
        return () => {
            alive = false;
            clearInterval(poll);
            window.removeEventListener('latex-review-changed', changed);
            window.removeEventListener('focus', focused);
        };
    }, [route, project, visible, readonly]);
    useEffect(() => {
        const view = editor.current;
        if (!view) return;
        const doc = view.state.doc.toString();
        const ranges = threads
            .filter(
                (item) => item.file === file && !item.resolved && item.quote,
            )
            .flatMap((item) => {
                let from = item.from;
                if (doc.slice(from, from + item.quote.length) !== item.quote) {
                    from = doc.indexOf(item.quote);
                    if (from < 0 || doc.indexOf(item.quote, from + 1) >= 0)
                        return [];
                }
                return [{ from, to: from + item.quote.length }];
            });
        view.dispatch({ effects: setReviewHighlights.of(ranges) });
        return () => {
            if (editor.current === view)
                view.dispatch({ effects: setReviewHighlights.of([]) });
        };
    }, [threads, file, source, editor]);
    useEffect(() => {
        if (!active || active.file !== file) return;
        // A thread click is a one-shot navigation request. Never reselect its
        // quote on polling, presence, typing or a later mouse drag.
        setActive(null);
        const view = editor.current;
        if (!view) return;
        const doc = view.state.doc.toString();
        let from = active.from;
        if (
            active.quote &&
            doc.slice(from, from + active.quote.length) !== active.quote
        ) {
            from = doc.indexOf(active.quote);
            if (from < 0 || doc.indexOf(active.quote, from + 1) !== -1) {
                setError(
                    t(
                        "Đoạn gốc đã thay đổi hoặc không còn duy nhất; nhận xét vẫn được giữ.",
                        "The original passage changed or is ambiguous; the comment is preserved.",
                    ),
                );
                return;
            }
        }
        from = Math.min(from, doc.length);
        view.dispatch({
            selection: {
                anchor: from,
                head: Math.min(doc.length, from + active.quote.length),
            },
            scrollIntoView: true,
        });
    }, [active, file, editor, t]);
    async function update(data: object) {
        if (readonly) return false;
        mutation.current++;
        setBusy(true);
        setError("");
        try {
            const value = await json<Thread[]>(route, {
                    method: "POST",
                    body: JSON.stringify(data),
                });
            mutation.current++;
            setThreads(value);
            window.dispatchEvent(new CustomEvent('latex-review-changed', { detail: { project } }));
            return true;
        } catch (e) {
            setError(errorText(e));
            return false;
        } finally {
            setBusy(false);
        }
    }
    return (
        <aside className="review-panel" aria-label={t("Nhận xét", "Review")}>
            <header>
                <strong>{t("Nhận xét", "Review")}</strong>
                <button
                    onClick={onClose}
                    aria-label={t("Đóng nhận xét", "Close review")}
                >
                    ×
                </button>
            </header>
            <div className="review-filters">
                <button
                    className={!all ? "active" : ""}
                    onClick={() => setAll(false)}
                >
                    {t("Tệp hiện tại", "Current file")}
                </button>
                <button
                    className={all ? "active" : ""}
                    onClick={() => setAll(true)}
                >
                    {t("Tổng quan", "Overview")}
                </button>
            </div>
            <label className="review-resolved">
                <input
                    type="checkbox"
                    checked={resolved}
                    onChange={(e) => setResolved(e.target.checked)}
                />
                {t("Hiện đã giải quyết", "Show resolved")}
            </label>
            <small>
                {isServerStorage() ? t('Nhận xét được chia sẻ với người có quyền truy cập dự án.', 'Comments are shared with project members.') : t(
                    "Lưu trên máy; chưa đồng bộ nhận xét qua phòng cộng tác.",
                    "Stored locally; review threads are not synced to collaboration rooms.",
                )}
            </small>
            {error && <p role="alert">{error}</p>}
            <div className="review-threads">
                {draft && !readonly && (
                    <form
                        onSubmit={async (e) => {
                            e.preventDefault();
                            if (
                                await update({
                                    operation: "create",
                                    ...draft,
                                    text,
                                })
                            ) {
                                setText("");
                                onDraft(null);
                            }
                        }}
                    >
                        <strong>
                            {draft.file} · {t("Dòng", "Line")} {draft.line}
                        </strong>
                        {draft.quote && <blockquote>{draft.quote}</blockquote>}
                        <textarea
                            autoFocus
                            aria-label={t("Nội dung nhận xét", "Comment text")}
                            value={text}
                            onChange={(e) => setText(e.target.value)}
                            maxLength={10000}
                            placeholder={t(
                                "Viết nhận xét…",
                                "Write a comment…",
                            )}
                        />
                        <button type="submit" disabled={busy || !text.trim()}>
                            {t("Đăng nhận xét", "Post comment")}
                        </button>
                        <button type="button" onClick={() => onDraft(null)}>
                            {t("Hủy", "Cancel")}
                        </button>
                    </form>
                )}
                {threads
                    .filter(
                        (item) =>
                            (all || item.file === file) &&
                            (resolved || !item.resolved),
                    )
                    .map((item) => (
                        <article
                            key={item.id}
                            className={item.resolved ? "resolved" : ""}
                        >
                            <button
                                className="review-location"
                                onClick={async () => {
                                    setError("");
                                    if (item.file !== file)
                                        await openFile(item.file);
                                    setActive(item);
                                }}
                            >
                                {item.file} · {t("Dòng", "Line")} {item.line}
                            </button>
                            {item.quote && (
                                <blockquote>{item.quote}</blockquote>
                            )}
                            {item.messages.map((message) => (
                                <div
                                    className="review-message"
                                    key={message.id}
                                >
                                    <strong>{message.author}</strong>
                                    <time>
                                        {new Date(message.time).toLocaleString(
                                            language === "vi"
                                                ? "vi-VN"
                                                : "en-US",
                                        )}
                                    </time>
                                    <p>{message.text}</p>
                                </div>
                            ))}
                            <button
                                disabled={busy || readonly}
                                onClick={() =>
                                    void update({
                                        operation: "resolve",
                                        id: item.id,
                                        resolved: !item.resolved,
                                    })
                                }
                            >
                                {item.resolved
                                    ? t("Mở lại", "Reopen")
                                    : t("Đánh dấu đã giải quyết", "Resolve")}
                            </button>
                            <button
                                disabled={busy || readonly}
                                onClick={() => {
                                    if (
                                        confirm(
                                            t(
                                                "Xóa toàn bộ nhận xét và trả lời?",
                                                "Delete this thread and replies?",
                                            ),
                                        )
                                    )
                                        void update({
                                            operation: "delete",
                                            id: item.id,
                                        });
                                }}
                            >
                                {t("Xóa", "Delete")}
                            </button>
                            {!item.resolved && !readonly && (
                                <form
                                    onSubmit={async (e) => {
                                        e.preventDefault();
                                        if (
                                            await update({
                                                operation: "reply",
                                                id: item.id,
                                                text: reply[item.id] || "",
                                            })
                                        )
                                            setReply((v) => ({
                                                ...v,
                                                [item.id]: "",
                                            }));
                                    }}
                                >
                                    <textarea
                                        aria-label={t("Trả lời", "Reply")}
                                        value={reply[item.id] || ""}
                                        onChange={(e) =>
                                            setReply((v) => ({
                                                ...v,
                                                [item.id]: e.target.value,
                                            }))
                                        }
                                        maxLength={10000}
                                        placeholder={t("Trả lời…", "Reply…")}
                                    />
                                    <button
                                        disabled={
                                            busy || !reply[item.id]?.trim()
                                        }
                                    >
                                        {t("Gửi trả lời", "Send reply")}
                                    </button>
                                </form>
                            )}
                        </article>
                    ))}
                {!threads.length && !draft && (
                    <p>
                        {t(
                            "Chọn đoạn văn trong editor rồi bấm icon nhận xét để bắt đầu.",
                            "Select a passage in the editor and click Add comment to start.",
                        )}
                    </p>
                )}
            </div>
        </aside>
    );
}
