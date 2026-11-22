"use client";
import { useEffect, useMemo, useRef, useState } from "react";
import { ArrowLeft, Tag, RotateCcw, RefreshCw } from "lucide-react";
import { json } from "../../lib/api";
import HistoryComparison from "./HistoryComparison";
import { action, errorText, translator } from "../../lib/parity";
type Revision = {
    id: string;
    file: string;
    author: string;
    reason: string;
    time: string;
    label?: string;
    content?: string;
};
function Source({ text }: { text: string }) {
    return (
        <div className="history-source">
            {text.split("\n").map((line, index) => (
                <div key={index}>
                    <span>{index + 1}</span>
                    <code>{line || " "}</code>
                </div>
            ))}
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
        [compare, setCompare] = useState(true),
        [current, setCurrent] = useState<string | null>(null),
        [label, setLabel] = useState(""),
        [confirm, setConfirm] = useState(false),
        [error, setError] = useState("");
    const [selectionLoading, setSelectionLoading] = useState(false),
        [currentError, setCurrentError] = useState("");
    const selectionTicket = useRef(0),
        refreshTicket = useRef(0);
    const files = useMemo(
        () => [...new Set(revisions.map((item) => item.file))],
        [revisions],
    );
    async function refresh() {
        const ticket = ++refreshTicket.current;
        try {
            const result = await json<Revision[]>(
                `/projects/${project}/history`,
            );
            if (ticket === refreshTicket.current) {
                setRevisions(result);
                setError("");
            }
        } catch (error) {
            if (ticket === refreshTicket.current) setError(errorText(error));
        }
    }
    useEffect(() => {
        setSelected(null);
        setCurrent(null);
        setFile("");
        setConfirm(false);
        void refresh();
        return () => {
            selectionTicket.current++;
            refreshTicket.current++;
        };
    }, [project]);
    async function select(item: Revision) {
        const ticket = ++selectionTicket.current;
        setSelectionLoading(true);
        setError("");
        setCurrentError("");
        setSelected(null);
        setCurrent(null);
        setConfirm(false);
        const [revision, source] = await Promise.allSettled([
            json<Revision>(`/projects/${project}/history/${item.id}`),
            json<{ content: string }>(
                `/projects/${project}/file?path=${encodeURIComponent(item.file)}`,
            ),
        ]);
        if (ticket !== selectionTicket.current) return;
        setSelectionLoading(false);
        if (revision.status === "rejected") {
            setError(errorText(revision.reason));
            return;
        }
        setSelected(revision.value);
        setLabel(revision.value.label || "");
        if (source.status === "fulfilled") setCurrent(source.value.content);
        else setCurrentError(errorText(source.reason));
    }
    function filterFile(name: string) {
        selectionTicket.current++;
        setSelectionLoading(false);
        setSelected(null);
        setCurrent(null);
        setCurrentError("");
        setConfirm(false);
        setFile(file === name ? "" : name);
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
                            onClick={() => filterFile(name)}
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
                    {selectionLoading && (
                        <p className="history-no-changes" role="status">
                            {t("?ang t?i phi?n b?n...", "Loading revision...")}
                        </p>
                    )}
                    {selected?.content !== undefined && (
                        <>
                            {compare && current !== null ? (
                                <HistoryComparison
                                    before={selected.content}
                                    after={current}
                                    language={language}
                                />
                            ) : (
                                <>
                                    {compare && currentError && (
                                        <p
                                            className="history-load-warning"
                                            role="alert"
                                        >
                                            {t(
                                                "Kh?ng t?i ???c t?p hi?n t?i ?? so s?nh. B?n ?? l?u v?n hi?n th? b?n d??i.",
                                                "Could not load the current file for comparison. The saved version is shown below.",
                                            )}{" "}
                                            {currentError}
                                        </p>
                                    )}
                                    <Source text={selected.content} />
                                </>
                            )}
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
                                disabled={locked || selectionLoading}
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
