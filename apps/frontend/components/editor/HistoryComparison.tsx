"use client";
import { useEffect, useMemo, useRef, useState } from "react";
import { ChevronDown, ChevronUp } from "lucide-react";
import {
    buildHistoryDiff,
    type HistoryLine,
    type HistoryRow,
} from "../../lib/history-diff";
import { translator } from "../../lib/parity";

export default function HistoryComparison({
    before,
    after,
    language,
}: {
    before: string;
    after: string;
    language: string;
}) {
    const t = translator(language);
    const diff = useMemo(
        () => buildHistoryDiff(before, after),
        [before, after],
    );
    const changes = useMemo(
        () =>
            diff.rows.flatMap((row, index) =>
                row.kind === "same" ? [] : [index],
            ),
        [diff],
    );
    const [active, setActive] = useState(-1);
    const root = useRef<HTMLDivElement>(null);
    useEffect(() => setActive(-1), [before, after]);
    function jump(step: number) {
        if (!changes.length) return;
        const next =
            active < 0
                ? step > 0
                    ? 0
                    : changes.length - 1
                : (active + step + changes.length) % changes.length;
        setActive(next);
        const row = root.current?.querySelector<HTMLElement>(
            `[data-diff-row="${changes[next]}"]`,
        );
        row?.focus({ preventScroll: true });
        row?.scrollIntoView({ block: "center", inline: "nearest" });
    }
    function cell(
        line: HistoryLine | undefined,
        side: "before" | "after",
        row: HistoryRow,
    ) {
        const sign =
            row.kind === "same"
                ? ""
                : side === "before" && line
                  ? row.kind === "modified"
                      ? "~"
                      : "−"
                  : side === "after" && line
                    ? row.kind === "modified"
                        ? "~"
                        : "+"
                    : "";
        return (
            <div className={`history-diff-cell ${side}${line ? "" : " empty"}`}>
                <span className="history-line-number" aria-hidden="true">
                    {line?.number || ""}
                </span>
                <span className="history-change-sign" aria-hidden="true">
                    {sign}
                </span>
                <code>
                    {line?.segments.map((part, index) =>
                        part.changed ? (
                            <mark
                                key={index}
                                className={`history-change-text ${side === "before" ? "removed" : "added"}`}
                            >
                                {part.text}
                            </mark>
                        ) : (
                            <span key={index}>{part.text}</span>
                        ),
                    )}
                    {line && !line.segments.some((part) => part.text) && " "}
                    {row.endingChanged && (
                        <span
                            className="history-line-ending"
                            title={t(
                                "Đổi ký tự xuống dòng",
                                "Line ending changed",
                            )}
                        >
                            {" "}
                            ↵
                        </span>
                    )}
                </code>
            </div>
        );
    }
    return (
        <div
            className="history-comparison"
            aria-label={t("So sánh phiên bản", "Revision comparison")}
            role="region"
            ref={root}
        >
            <div className="history-change-toolbar">
                <div className="history-change-legend">
                    <span className="added">
                        + {diff.counts.added} {t("thêm", "added")}
                    </span>
                    <span className="removed">
                        − {diff.counts.removed} {t("xóa", "removed")}
                    </span>
                    <span className="modified">
                        ~ {diff.counts.modified} {t("sửa", "modified")}
                    </span>
                </div>
                <div className="history-change-navigation">
                    <span aria-live="polite">
                        {active < 0 ? "" : `${active + 1} / ${changes.length}`}
                    </span>
                    <button
                        type="button"
                        disabled={!changes.length}
                        onClick={() => jump(-1)}
                        aria-label={t("Thay đổi trước", "Previous change")}
                    >
                        <ChevronUp size={16} />
                    </button>
                    <button
                        type="button"
                        disabled={!changes.length}
                        onClick={() => jump(1)}
                        aria-label={t("Thay đổi tiếp", "Next change")}
                    >
                        <ChevronDown size={16} />
                    </button>
                </div>
            </div>
            {!changes.length && (
                <p className="history-no-changes" role="status">
                    {t(
                        "Không có thay đổi so với tệp hiện tại.",
                        "No changes compared with the current file.",
                    )}
                </p>
            )}
            {diff.approximate && (
                <p className="history-no-changes">
                    {t(
                        "Tài liệu thay đổi lớn; một số thay đổi được nhóm lại.",
                        "Large document changes: some changed passages are grouped.",
                    )}
                </p>
            )}
            <div className="history-comparison-head">
                <strong>{t("Bản đã lưu", "Saved version")}</strong>
                <strong>{t("Tệp hiện tại", "Current file")}</strong>
            </div>
            <div className="history-comparison-rows">
                {diff.rows.map((row, index) => (
                    <div
                        key={index}
                        className={`history-diff-row ${row.kind}${changes[active] === index ? " current-change" : ""}`}
                        data-diff-row={index}
                        data-change-type={row.kind}
                        tabIndex={-1}
                        aria-label={
                            row.kind === "same"
                                ? undefined
                                : `${row.kind}: ${row.before?.number || "–"} → ${row.after?.number || "–"}`
                        }
                    >
                        {cell(row.before, "before", row)}
                        {cell(row.after, "after", row)}
                    </div>
                ))}
            </div>
        </div>
    );
}
