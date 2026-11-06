"use client";
import {
    forwardRef,
    useEffect,
    useImperativeHandle,
    useRef,
    useState,
} from "react";
import { json } from "../lib/api";
import { errorText, translator } from "../lib/parity";
import type { WordPreviewHandle } from "../lib/word-preview";
export type { WordPreviewHandle } from "../lib/word-preview";

type Draft = {
    html: string;
    revision: string;
    fingerprint: string;
    stale: boolean;
    warnings: string[];
    needsRefresh?: boolean;
    imageCount?: number;
    sync?: Record<string, unknown>;
    layout?: {
        columns: number;
        paper: string;
        width: number;
        height: number;
        top: number;
        right: number;
        bottom: number;
        left: number;
        fontFamily?: string;
        fontSize?: number;
    };
};
export default forwardRef<
    WordPreviewHandle,
    {
        project: string;
        language: string;
        sourceDirty: boolean;
        active: boolean;
        sourceSignal: string;
        compiled: boolean;
        onSynced: () => Promise<void>;
        saveSource: () => Promise<boolean>;
    }
>(function WordPreview(
    {
        project,
        language,
        sourceDirty,
        saveSource,
        active: visible,
        sourceSignal,
        compiled,
        onSynced,
    },
    ref,
) {
    const t = translator(language),
        area = useRef<HTMLDivElement>(null),
        model = useRef<Draft | null>(null),
        html = useRef(""),
        dirtyRef = useRef(false),
        queue = useRef<Promise<unknown>>(Promise.resolve());
    const working = useRef(false);
    const [draft, setDraft] = useState<Draft | null>(null),
        [dirty, setDirty] = useState(false),
        [busy, setBusy] = useState(false),
        [ready, setReady] = useState(false),
        [error, setError] = useState("");
    const scroll = useRef<HTMLDivElement>(null);
    const [zoom, setZoom] = useState("fit"),
        [fitZoom, setFitZoom] = useState(1);
    useEffect(() => {
        if (!scroll.current) return;
        const observer = new ResizeObserver(() => {
            const width = scroll.current?.clientWidth || 0;
            if (width)
                setFitZoom(
                    Math.max(
                        0.25,
                        Math.min(
                            1,
                            (width - 40) /
                                (((model.current?.layout?.width || 210) * 96) /
                                    25.4),
                        ),
                    ),
                );
        });
        observer.observe(scroll.current);
        return () => observer.disconnect();
    }, []);
    const route = `/projects/${project}/word`;
    function accept(value: Draft | null) {
        model.current = value;
        setDraft(value);
        html.current = value?.html || "";
        if (area.current) area.current.innerHTML = html.current;
        dirtyRef.current = false;
        setDirty(false);
    }
    useEffect(() => {
        let active = true;
        json<Draft | null>(route)
            .then((value) => {
                if (active) {
                    accept(value);
                    setReady(true);
                }
            })
            .catch((e) => {
                if (active) {
                    setError(errorText(e));
                    setReady(true);
                }
            });
        return () => {
            active = false;
        };
    }, [route]);
    useEffect(() => {
        if (!ready || (!visible && !compiled)) return;
        const timer = setTimeout(
            () => {
                if (dirtyRef.current || working.current) return;
                void task(async () => {
                    if (sourceDirty && !(await saveSource()))
                        throw Error(
                            t(
                                "Không lưu được LaTeX để cập nhật Word.",
                                "Could not save LaTeX to update Word.",
                            ),
                        );
                    const value = await json<Draft | null>(route);
                    if (
                        !value ||
                        value.needsRefresh ||
                        !value.sync ||
                        (value.sync && value.stale)
                    )
                        await generate();
                    else if (value.revision === model.current?.revision) {
                        model.current = value;
                        setDraft(value);
                    }
                });
            },
            sourceDirty ? 1000 : 50,
        );
        return () => clearTimeout(timer);
    }, [ready, visible, compiled, sourceSignal, sourceDirty, route]);
    function persist(): Promise<Draft | null> {
        const pending = queue.current
            .catch(() => {})
            .then(async () => {
                if (!dirtyRef.current || !model.current) return model.current;
                const content = html.current;
                if (model.current.sync && !(await saveSource()))
                    throw Error(
                        t(
                            "LaTeX chưa lưu, chưa thể đồng bộ Word.",
                            "Save LaTeX before syncing Word.",
                        ),
                    );
                const saved = await json<Draft>(route, {
                    method: "POST",
                    body: JSON.stringify({
                        revision: model.current.revision,
                        html: content,
                    }),
                });
                model.current = saved;
                setDraft(saved);
                if (saved.sync) await onSynced();
                if (content === html.current) {
                    dirtyRef.current = false;
                    setDirty(false);
                }
                return saved;
            });
        queue.current = pending;
        return pending;
    }
    useEffect(
        () => () => {
            if (dirtyRef.current)
                void persist().catch((e) =>
                    console.error("Word save failed", e),
                );
        },
        [route],
    );
    useEffect(() => {
        if (!dirty) return;
        const timer = setTimeout(() => {
            void persist().catch((e) => setError(errorText(e)));
        }, 700);
        return () => clearTimeout(timer);
    });
    async function generate() {
        await queue.current.catch(() => {});
        if (!(await saveSource()))
            throw Error(
                t("Cần lưu nguồn LaTeX trước.", "Save the LaTeX source first."),
            );
        const value = await json<Draft>(route + "/generate", {
            method: "POST",
            body: JSON.stringify({ revision: model.current?.revision || null }),
        });
        accept(value);
        return value;
    }
    async function task(work: () => Promise<unknown>) {
        if (working.current) return;
        working.current = true;
        setBusy(true);
        setError("");
        try {
            await work();
        } catch (e) {
            setError(errorText(e));
        } finally {
            working.current = false;
            setBusy(false);
        }
    }
    useImperativeHandle(ref, () => ({
        flush: async () => {
            try {
                await persist();
                return true;
            } catch (error) {
                setError(errorText(error));
                return false;
            }
        },
    }));
    function changed() {
        html.current = area.current?.innerHTML || "";
        dirtyRef.current = true;
        setDirty(true);
    }
    function command(name: string, value?: string) {
        area.current?.focus();
        document.execCommand(name, false, value);
        changed();
    }
    return (
        <div className="word-preview">
            <div className="word-actions">
                <select
                    aria-label={t("Tỉ lệ Word", "Word zoom")}
                    value={zoom}
                    onChange={(event) => setZoom(event.target.value)}
                >
                    <option value="fit">
                        {t("Vừa chiều rộng", "Fit width")}
                    </option>
                    {[0.5, 0.75, 1, 1.25, 1.5].map((value) => (
                        <option key={value} value={value}>
                            {Math.round(value * 100)}%
                        </option>
                    ))}
                </select>
                <button
                    disabled={busy || !ready}
                    onClick={() => {
                        if (
                            !model.current ||
                            confirm(
                                t(
                                    "Tạo lại từ LaTeX sẽ thay thế các chỉnh sửa Word. Tiếp tục?",
                                    "Regenerate from LaTeX and replace Word edits?",
                                ),
                            )
                        )
                            void task(generate);
                    }}
                >
                    {draft
                        ? t("Tạo lại từ LaTeX", "Regenerate from LaTeX")
                        : t("Tạo bản Word", "Create Word draft")}
                </button>
                <button
                    disabled={busy || !dirty}
                    onClick={() => void task(persist)}
                >
                    {t("Lưu Word", "Save Word")}
                </button>
                <span>
                    {busy
                        ? t("Đang xử lý…", "Processing…")
                        : dirty
                          ? t("Chưa lưu", "Unsaved")
                          : draft
                            ? t("Đã lưu Word", "Word saved")
                            : ""}
                </span>
            </div>
            <p className="word-notice">
                {t(
                    draft?.sync
                        ? "Đồng bộ chữ Word ↔ LaTeX. Bấm vào đoạn chữ để sửa; công thức, lệnh tùy biến và đoạn chưa ánh xạ được bảo vệ. Phân trang Word có thể khác PDF."
                        : "Bản Word cũ lưu riêng. Chọn Tạo lại từ LaTeX để bật đồng bộ; bản cũ được sao lưu trước khi thay thế.",
                    draft?.sync
                        ? "Text sync Word ↔ LaTeX. Click mapped text to edit; equations, custom commands and unmapped passages are protected. Word pagination may differ from PDF."
                        : "This legacy Word draft is separate. Regenerate from LaTeX to enable sync; the old draft is backed up first.",
                )}
            </p>
            {(sourceDirty || draft?.stale) && (
                <p className="word-notice">
                    {t(
                        "Nguồn LaTeX đã đổi. Word tự cập nhật khi không có chỉnh sửa Word đang chờ; nếu sửa cả hai cùng lúc, cần đối chiếu trước khi ghi đè.",
                        "LaTeX changed. Word updates automatically when no Word edits are pending; simultaneous edits require resolving the conflict.",
                    )}
                </p>
            )}
            {error && (
                <p className="word-error" role="alert">
                    {error}
                </p>
            )}
            {!!draft?.warnings.length && (
                <details>
                    <summary>
                        {t("Thông báo chuyển đổi", "Conversion messages")}
                    </summary>
                    <pre>{draft.warnings.join("\n")}</pre>
                </details>
            )}
            <div
                className="word-format"
                role="toolbar"
                aria-label={t("Định dạng Word", "Word formatting")}
                title={
                    draft?.sync
                        ? t(
                              "Chế độ đồng bộ hiện hỗ trợ sửa chữ; sửa cấu trúc/định dạng trong editor LaTeX.",
                              "Synced mode supports text edits; change structure/formatting in the LaTeX editor.",
                          )
                        : ""
                }
                onMouseDown={(e) => e.preventDefault()}
            >
                {[
                    ["bold", "B"],
                    ["italic", "I"],
                    ["underline", "U"],
                    ["insertUnorderedList", "• List"],
                    ["insertOrderedList", "1. List"],
                    ["undo", "↶"],
                    ["redo", "↷"],
                ].map(([cmd, label]) => (
                    <button
                        key={cmd}
                        disabled={!draft || busy || !!draft.sync}
                        onClick={() => command(cmd)}
                        title={cmd}
                    >
                        {label}
                    </button>
                ))}
                <button
                    disabled={!draft || busy || !!draft.sync}
                    onClick={() => command("formatBlock", "h2")}
                >
                    {t("Tiêu đề", "Heading")}
                </button>
                <button
                    disabled={!draft || busy || !!draft.sync}
                    onClick={() => command("formatBlock", "p")}
                >
                    {t("Đoạn văn", "Paragraph")}
                </button>
                <button
                    disabled={!draft || busy || !!draft.sync}
                    onClick={() =>
                        command(
                            "insertHTML",
                            "<table><tbody><tr><td>Cell</td><td>Cell</td></tr><tr><td>Cell</td><td>Cell</td></tr></tbody></table><p><br></p>",
                        )
                    }
                >
                    {t("Bảng", "Table")}
                </button>
            </div>
            <div className="word-scroll" ref={scroll}>
                <div
                    ref={area}
                    className="word-document"
                    inert={busy}
                    data-columns={draft?.layout?.columns || 1}
                    style={{
                        width: (draft?.layout?.width || 210) + "mm",
                        minHeight: (draft?.layout?.height || 297) + "mm",
                        padding: `${draft?.layout?.top || 25}mm ${draft?.layout?.right || 20}mm ${draft?.layout?.bottom || 25}mm ${draft?.layout?.left || 20}mm`,
                        zoom: zoom === "fit" ? fitZoom : Number(zoom),
                        maxWidth: "none",
                        fontFamily: `"${draft?.layout?.fontFamily || "Times New Roman"}", "Book Antiqua", Palatino, serif`,
                        fontSize: (draft?.layout?.fontSize || 12) + "pt",
                    }}
                    role="textbox"
                    aria-label={t("Nội dung Word", "Word content")}
                    aria-multiline="true"
                    contentEditable={!!draft && !draft.sync && !busy}
                    suppressContentEditableWarning
                    onKeyDown={(event) => {
                        if (
                            draft?.sync &&
                            (event.ctrlKey || event.metaKey) &&
                            ["b", "i", "u"].includes(event.key.toLowerCase())
                        ) {
                            event.preventDefault();
                            event.stopPropagation();
                            setError(
                                t(
                                    "Sửa định dạng trong editor LaTeX ở chế độ đồng bộ chữ.",
                                    "Change formatting in the LaTeX editor while text sync is enabled.",
                                ),
                            );
                            return;
                        }
                        if (
                            (event.ctrlKey || event.metaKey) &&
                            event.key.toLowerCase() === "s"
                        ) {
                            event.preventDefault();
                            event.stopPropagation();
                            void task(persist);
                        }
                    }}
                    onInput={changed}
                    onBlur={() => {
                        if (dirtyRef.current)
                            void persist().catch((e) => setError(errorText(e)));
                    }}
                    onClick={(e) => {
                        if ((e.target as Element).closest("a"))
                            e.preventDefault();
                    }}
                    onPaste={(e) => {
                        e.preventDefault();
                        document.execCommand(
                            "insertText",
                            false,
                            e.clipboardData.getData("text/plain"),
                        );
                        changed();
                    }}
                />
                {!draft && (
                    <p>
                        {t(
                            busy
                                ? "Đang chuyển LaTeX sang Word…"
                                : "Đang chuẩn bị Word preview…",
                            busy
                                ? "Converting LaTeX to Word…"
                                : "Preparing Word preview…",
                        )}
                    </p>
                )}
            </div>
        </div>
    );
});
