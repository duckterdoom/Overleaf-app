"use client";
import { useEffect, useMemo, useState } from "react";
import { ArrowLeft, Tag, RotateCcw, RefreshCw } from "lucide-react";
import { json } from "../lib/api";
import { action, errorText, translator } from "../lib/parity";
type Revision = {
    id: string;
    file: string;
    author: string;
    reason: string;
    time: string;
    label?: string;
    content?: string;
};
function Source({ text, other }: { text: string; other?: string }) {
    let start = 0,
        end = text.length;
    if (other !== undefined) {
        while (
            start < Math.min(text.length, other.length) &&
            text[start] === other[start]
        )
            start++;
        let suffix = 0;
        while (
            suffix < text.length - start &&
            suffix < other.length - start &&
            text[text.length - 1 - suffix] === other[other.length - 1 - suffix]
        )
            suffix++;
        end = text.length - suffix;
    }
    let offset = 0;
    return (
        <div className="history-source">
            {text.split("\n").map((line, index) => {
                const begin = offset;
                offset += line.length + 1;
                const a = Math.max(0, start - begin),
                    b = Math.min(line.length, end - begin);
                return (
                    <div key={index}>
                        <span>{index + 1}</span>
                        <code>
                            {other !== undefined && b > a ? (
                                <>
                                    {line.slice(0, a)}
                                    <mark>{line.slice(a, b)}</mark>
                                    {line.slice(b)}
                                </>
                            ) : (
                                line || " "
                            )}
                        </code>
                    </div>
                );
            })}
        </div>
    );
}
export default function HistoryPanel({
    project,
    language,
    locked,
    onClose,
    onRestore,
}: {
    project: string;
    language: string;
    locked: boolean;
    onClose: () => void;
    onRestore: (file: string, content: string) => void;
}) {
    const t = translator(language),
        [revisions, setRevisions] = useState<Revision[]>([]),
        [selected, setSelected] = useState<Revision | null>(null),
        [file, setFile] = useState("");
    const [labels, setLabels] = useState(false),
        [compare, setCompare] = useState(false),
        [current, setCurrent] = useState(""),
        [label, setLabel] = useState(""),
        [confirm, setConfirm] = useState(false),
        [error, setError] = useState("");
    const files = useMemo(
        () => [...new Set(revisions.map((item) => item.file))],
        [revisions],
    );
    async function refresh() {
        try {
            setRevisions(
                await json<Revision[]>(`/projects/${project}/history`),
            );
        } catch (error) {
            setError(errorText(error));
        }
    }
    useEffect(() => {
        void refresh();
    }, [project]);
    async function select(item: Revision) {
        try {
            const [revision, source] = await Promise.all([
                json<Revision>(`/projects/${project}/history/${item.id}`),
                json<{ content: string }>(
                    `/projects/${project}/file?path=${encodeURIComponent(item.file)}`,
                ).catch(() => ({ content: "" })),
            ]);
            setSelected(revision);
            setCurrent(source.content);
            setLabel(revision.label || "");
            setConfirm(false);
        } catch (error) {
            setError(errorText(error));
        }
    }
    return (
        <section className="history-screen">
            <header>
                <button className="secondary" onClick={onClose}>
                    <ArrowLeft size={16} />
                    {t("Quay lại editor", "Back to editor")}
                </button>
                <strong>{t("Lịch sử sửa đổi", "History")}</strong>
                <button
                    title={t("Làm mới", "Refresh")}
                    onClick={() => void refresh()}
                >
                    <RefreshCw size={17} />
                </button>
            </header>
            {error && <p className="form-error">{error}</p>}
            <div className="history-grid">
                <aside>
                    {files.map((name) => (
                        <button
                            className={file === name ? "selected" : ""}
                            key={name}
                            onClick={() => setFile(file === name ? "" : name)}
                        >
                            {name}
                        </button>
                    ))}
                </aside>
                <section>
                    <div className="history-meta">
                        <strong>
                            {selected
                                ? `${selected.file} · ${new Date(selected.time).toLocaleString(language === "en" ? "en-US" : "vi-VN")}`
                                : t(
                                      "Chọn một mốc lịch sử",
                                      "Select a revision",
                                  )}
                        </strong>
                        <label className="check-row">
                            <input
                                type="checkbox"
                                checked={compare}
                                onChange={(event) =>
                                    setCompare(event.target.checked)
                                }
                            />
                            {t("So sánh với hiện tại", "Compare with current")}
                        </label>
                    </div>
                    {selected?.content !== undefined && (
                        <>
                            <div className={compare ? "history-diff" : ""}>
                                <div>
                                    <small>
                                        {t("Bản đã lưu", "Saved version")}
                                    </small>
                                    <Source
                                        text={selected.content}
                                        other={compare ? current : undefined}
                                    />
                                </div>
                                {compare && (
                                    <div>
                                        <small>
                                            {t("Hiện tại", "Current")}
                                        </small>
                                        <Source
                                            text={current}
                                            other={selected.content}
                                        />
                                    </div>
                                )}
                            </div>
                            <div className="history-actions">
                                <input
                                    aria-label={t(
                                        "Nhãn revision",
                                        "Revision label",
                                    )}
                                    maxLength={120}
                                    value={label}
                                    onChange={(event) =>
                                        setLabel(event.target.value)
                                    }
                                />
                                <button
                                    className="secondary"
                                    onClick={async () => {
                                        try {
                                            await action("history-label", {
                                                project,
                                                revision: selected.id,
                                                label,
                                            });
                                            await refresh();
                                        } catch (error) {
                                            setError(errorText(error));
                                        }
                                    }}
                                >
                                    <Tag size={15} />
                                    {t("Lưu nhãn", "Save label")}
                                </button>
                                <button
                                    className="primary"
                                    disabled={locked}
                                    onClick={() => setConfirm(true)}
                                >
                                    <RotateCcw size={15} />
                                    {t(
                                        "Khôi phục tệp này",
                                        "Restore this file",
                                    )}
                                </button>
                            </div>
                            {locked && (
                                <p className="help-text">
                                    {t(
                                        "Rời phòng cộng tác trước khi khôi phục.",
                                        "Leave the collaboration room before restoring.",
                                    )}
                                </p>
                            )}
                        </>
                    )}
                    {confirm && selected && (
                        <div className="restore-confirm">
                            <p>
                                {t(
                                    "Khôi phục revision này? Nội dung hiện tại sẽ được giữ trong history trước khi thay thế.",
                                    "Restore this revision? The current file is captured before replacement.",
                                )}
                            </p>
                            <button
                                className="secondary"
                                onClick={() => setConfirm(false)}
                            >
                                {t("Hủy", "Cancel")}
                            </button>
                            <button
                                className="primary"
                                onClick={async () => {
                                    try {
                                        const result = await action<{
                                            file: string;
                                            content: string;
                                        }>("history-restore", {
                                            project,
                                            revision: selected.id,
                                        });
                                        onRestore(result.file, result.content);
                                    } catch (error) {
                                        setError(errorText(error));
                                    }
                                }}
                            >
                                {t("Xác nhận khôi phục", "Confirm restore")}
                            </button>
                        </div>
                    )}
                </section>
                <aside className="revision-list">
                    <div className="inline-controls">
                        <button
                            className={!labels ? "selected" : ""}
                            onClick={() => setLabels(false)}
                        >
                            {t("Tất cả lịch sử", "All history")}
                        </button>
                        <button
                            className={labels ? "selected" : ""}
                            onClick={() => setLabels(true)}
                        >
                            {t("Nhãn", "Labels")}
                        </button>
                    </div>
                    {revisions
                        .filter(
                            (item) =>
                                (!file || item.file === file) &&
                                (!labels || item.label),
                        )
                        .map((item) => (
                            <button
                                key={item.id}
                                className={
                                    item.id === selected?.id ? "selected" : ""
                                }
                                onClick={() => void select(item)}
                            >
                                <strong>
                                    {new Date(item.time).toLocaleString(
                                        language === "en" ? "en-US" : "vi-VN",
                                    )}
                                </strong>
                                <span>{item.file}</span>
                                <small>
                                    {item.author} · {item.reason}
                                </small>
                                {item.label && <em>{item.label}</em>}
                            </button>
                        ))}
                </aside>
            </div>
        </section>
    );
}
