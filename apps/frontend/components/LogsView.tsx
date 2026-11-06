"use client";

import React, { useState, useMemo, useRef, useEffect } from "react";
import {
    ChevronRight,
    ChevronDown,
    Trash2,
    Copy,
    Check,
    Sparkles,
    FileText,
    Terminal,
    Download,
    ArrowUpRight,
    AlertCircle,
    AlertTriangle,
    Info,
} from "lucide-react";
import type { Project } from "../lib/api";

export type LogEntry = {
    id: string;
    type: "error" | "warning" | "info";
    title: string;
    message: string;
    file: string;
    line?: number;
    why?: string;
    fix?: string;
};

export function parseAllLogs(
    issues: {
        file: string;
        line: number;
        message: string;
        title: string;
        why?: string;
        fix?: string;
        type?: "error" | "warning" | "info";
    }[],
    rawLogs: string,
    activeFile: string,
    language: string = "vi",
): LogEntry[] {
    const isEn = language === "en";
    const result: LogEntry[] = [];
    const seen = new Set<string>();

    // 1. Process structured issues from explainer / compiler
    for (const [idx, item] of issues.entries()) {
        const titleLower = (item.title || "").toLowerCase();
        const msgLower = (item.message || "").toLowerCase();
        let type: "error" | "warning" | "info" = item.type || "error";
        if (!item.type) {
            if (
                titleLower.includes("warning") ||
                titleLower.includes("cảnh báo") ||
                msgLower.includes("warning") ||
                titleLower.includes("font shape")
            ) {
                type = "warning";
            } else if (
                titleLower.includes("info") ||
                titleLower.includes("thông tin")
            ) {
                type = "info";
            }
        }
        const key = `${type}:${item.file || activeFile}:${item.line}:${item.title}`;
        if (!seen.has(key)) {
            seen.add(key);
            result.push({
                id: `issue-${idx}-${key}`,
                type,
                title: item.title,
                message: item.message,
                file: item.file || activeFile || "main.tex",
                line: item.line,
                why: item.why,
                fix: item.fix,
            });
        }
    }

    // 2. Parse warnings & info from raw logs that might not be in issues
    if (rawLogs) {
        const lines = rawLogs.split(/\r?\n/);
        let i = 0;
        while (i < lines.length) {
            const line = lines[i];

            // Match Font Warning: Font shape '...' undefined
            const fontShapeMatch = line.match(
                /LaTeX Font Warning:\s*(Font shape '[^']+' undefined.*)/i,
            );
            if (fontShapeMatch) {
                const title = fontShapeMatch[1].trim();
                const key = `warning:${title}`;
                if (!seen.has(key)) {
                    seen.add(key);
                    result.push({
                        id: `log-font-${i}`,
                        type: "warning",
                        title,
                        message: line.trim(),
                        file: activeFile || "main.tex",
                        why: isEn
                            ? "The specified font shape is not defined in the loaded font package."
                            : "Kiểu dáng font này không được định nghĩa trong gói font hiện tại.",
                        fix: isEn
                            ? "Check your font packages or use a supported style."
                            : "Kiểm tra gói font hoặc dùng kiểu dáng font tương thích.",
                    });
                }
                i++;
                continue;
            }

            // Match Font Warning: Some font shapes were not available, defaults substituted.
            const fontSubMatch = line.match(
                /LaTeX Font Warning:\s*(Some font shapes were not available.*)/i,
            );
            if (fontSubMatch) {
                const title = fontSubMatch[1].trim();
                const key = `warning:${title}`;
                if (!seen.has(key)) {
                    seen.add(key);
                    result.push({
                        id: `log-fontsub-${i}`,
                        type: "warning",
                        title,
                        message: line.trim(),
                        file: activeFile || "main.tex",
                        why: isEn
                            ? "Some requested font combinations were missing; standard defaults were used instead."
                            : "Một số biến thể font không có sẵn, LaTeX đã tự động thay thế bằng font mặc định.",
                        fix: isEn
                            ? "Review font commands in your document or switch engine to XeLaTeX/LuaLaTeX."
                            : "Kiểm tra các lệnh font hoặc chuyển sang engine XeLaTeX / LuaLaTeX.",
                    });
                }
                i++;
                continue;
            }

            // Match LaTeX Warning: Reference ... undefined
            const refMatch = line.match(
                /LaTeX Warning:\s*(Reference `([^']+)' on page \d+ undefined.*?(?:on input line (\d+))?)/i,
            );
            if (refMatch) {
                const refName = refMatch[2];
                const lineNum = refMatch[3]
                    ? parseInt(refMatch[3], 10)
                    : undefined;
                const title = isEn
                    ? `Reference \`${refName}' undefined`
                    : `Tham chiếu \`${refName}' chưa xác định`;
                const key = `warning:ref:${refName}:${lineNum}`;
                if (!seen.has(key)) {
                    seen.add(key);
                    result.push({
                        id: `log-ref-${i}`,
                        type: "warning",
                        title,
                        message: line.trim(),
                        file: activeFile || "main.tex",
                        line: lineNum,
                        why: isEn
                            ? `Label \\label{${refName}} was not found or document needs recompilation.`
                            : `Nhãn \\label{${refName}} chưa được tìm thấy hoặc cần biên dịch lại một lần nữa.`,
                        fix: isEn
                            ? "Recompile again to resolve cross-references, or verify the label name."
                            : "Bấm Biên dịch lại lần nữa để cập nhật tham chiếu chéo, hoặc kiểm tra tên nhãn.",
                    });
                }
                i++;
                continue;
            }

            // Match LaTeX Warning: Citation ... undefined
            const citeMatch = line.match(
                /LaTeX Warning:\s*(Citation `([^']+)' on page \d+ undefined.*?(?:on input line (\d+))?)/i,
            );
            if (citeMatch) {
                const citeKey = citeMatch[2];
                const lineNum = citeMatch[3]
                    ? parseInt(citeMatch[3], 10)
                    : undefined;
                const title = isEn
                    ? `Citation \`${citeKey}' undefined`
                    : `Trích dẫn \`${citeKey}' chưa xác định`;
                const key = `warning:cite:${citeKey}:${lineNum}`;
                if (!seen.has(key)) {
                    seen.add(key);
                    result.push({
                        id: `log-cite-${i}`,
                        type: "warning",
                        title,
                        message: line.trim(),
                        file: activeFile || "main.tex",
                        line: lineNum,
                        why: isEn
                            ? `Bibliography key \`${citeKey}' not found in the .bib file.`
                            : `Khóa tài liệu trích dẫn \`${citeKey}' không tìm thấy trong tệp .bib.`,
                        fix: isEn
                            ? "Check your bibliography entries and ensure the .bib file is included."
                            : "Kiểm tra tệp .bib và đảm bảo khóa trích dẫn trùng khớp.",
                    });
                }
                i++;
                continue;
            }

            // Match generic Package / Class Warning
            const generalWarnMatch = line.match(
                /(?:Package|Class)?\s*([A-Za-z0-9_-]+)?\s*Warning:\s*(.+)/i,
            );
            if (generalWarnMatch && !line.includes("Font Warning")) {
                const pkg = generalWarnMatch[1];
                const msg = generalWarnMatch[2].trim();
                const lineMatch = msg.match(
                    /(?:on input line|at line)\s*(\d+)/i,
                );
                const lineNum = lineMatch
                    ? parseInt(lineMatch[1], 10)
                    : undefined;
                const title = pkg
                    ? `${pkg} Warning: ${msg.split("\n")[0].slice(0, 80)}`
                    : msg.split("\n")[0].slice(0, 90);
                const key = `warning:${pkg}:${msg.slice(0, 40)}`;
                if (!seen.has(key)) {
                    seen.add(key);
                    result.push({
                        id: `log-warn-${i}`,
                        type: "warning",
                        title,
                        message: line.trim(),
                        file: activeFile || "main.tex",
                        line: lineNum,
                        why: isEn
                            ? "Compiler issued a warning."
                            : "Trình biên dịch đưa ra cảnh báo.",
                        fix: isEn
                            ? "Review the warning details above."
                            : "Xem chi tiết cảnh báo bên trên để xử lý.",
                    });
                }
                i++;
                continue;
            }

            // Match Underfull / Overfull hbox
            const boxMatch = line.match(
                /(Underfull|Overfull) \\(hbox|vbox) \((.*?)\) in paragraph at lines? (\d+)/i,
            );
            if (boxMatch) {
                const kind = boxMatch[1];
                const badness = boxMatch[3];
                const lineNum = parseInt(boxMatch[4], 10);
                const title = `${kind} \\hbox (${badness})`;
                const key = `warning:box:${lineNum}:${badness}`;
                if (!seen.has(key)) {
                    seen.add(key);
                    result.push({
                        id: `log-box-${i}`,
                        type: "warning",
                        title,
                        message: line.trim(),
                        file: activeFile || "main.tex",
                        line: lineNum,
                        why: isEn
                            ? `${kind} line formatting at line ${lineNum}.`
                            : `Định dạng dòng bị ${kind === "Overfull" ? "tràn lề" : "quá thưa"} ở dòng ${lineNum}.`,
                        fix: isEn
                            ? "Try rephrasing or inserting manual hyphenation \\-."
                            : "Thử viết lại câu hoặc dùng dấu ngắt từ \\-.",
                    });
                }
                i++;
                continue;
            }

            i++;
        }
    }

    return result;
}

export interface LogsViewProps {
    project: Project;
    file: string;
    issues: {
        file: string;
        line: number;
        message: string;
        title: string;
        why?: string;
        fix?: string;
        type?: "error" | "warning" | "info";
    }[];
    logs: string;
    setLogs: (val: string) => void;
    logPaneRef: React.RefObject<HTMLPreElement | null>;
    busy: boolean;
    uiLanguage: string;
    onClearCache: () => Promise<void>;
    onJumpToLine?: (file: string, line: number) => void;
    onBackToPdf: () => void;
}

export default function LogsView({
    project,
    file,
    issues,
    logs,
    setLogs,
    logPaneRef,
    busy,
    uiLanguage,
    onClearCache,
    onJumpToLine,
    onBackToPdf,
}: LogsViewProps) {
    const isEn = uiLanguage === "en";
    const [activeTab, setActiveTab] = useState<
        "all" | "errors" | "warnings" | "info"
    >("all");
    const [expandedIds, setExpandedIds] = useState<Set<string>>(() => new Set());
    const [rawLogsOpen, setRawLogsOpen] = useState(false);
    const [copied, setCopied] = useState(false);
    const [otherMenuOpen, setOtherMenuOpen] = useState(false);
    const otherMenuRef = useRef<HTMLDivElement>(null);

    const parsedItems = useMemo(
        () => parseAllLogs(issues, logs, file, uiLanguage),
        [issues, logs, file, uiLanguage],
    );

    const counts = useMemo(() => {
        const errors = parsedItems.filter((i) => i.type === "error").length;
        const warnings = parsedItems.filter((i) => i.type === "warning").length;
        const info = parsedItems.filter((i) => i.type === "info").length;
        return {
            all: parsedItems.length,
            errors,
            warnings,
            info,
        };
    }, [parsedItems]);

    const filteredItems = useMemo(() => {
        if (activeTab === "errors")
            return parsedItems.filter((i) => i.type === "error");
        if (activeTab === "warnings")
            return parsedItems.filter((i) => i.type === "warning");
        if (activeTab === "info")
            return parsedItems.filter((i) => i.type === "info");
        return parsedItems;
    }, [parsedItems, activeTab]);

    const toggleExpand = (id: string) => {
        setExpandedIds((prev) => {
            const next = new Set(prev);
            if (next.has(id)) next.delete(id);
            else next.add(id);
            return next;
        });
    };

    const handleCopyLogs = async () => {
        try {
            await navigator.clipboard.writeText(logs || "");
            setCopied(true);
            setTimeout(() => setCopied(false), 2000);
        } catch {}
    };

    const handleDownloadLog = () => {
        const blob = new Blob([logs || ""], { type: "text/plain;charset=utf-8" });
        const url = URL.createObjectURL(blob);
        const a = document.createElement("a");
        a.href = url;
        a.download = `${project.title || project.id || "build"}.log`;
        a.click();
        URL.revokeObjectURL(url);
        setOtherMenuOpen(false);
    };

    useEffect(() => {
        function handleClickOutside(event: MouseEvent) {
            if (
                otherMenuRef.current &&
                !otherMenuRef.current.contains(event.target as Node)
            ) {
                setOtherMenuOpen(false);
            }
        }
        if (otherMenuOpen) {
            document.addEventListener("mousedown", handleClickOutside);
        }
        return () => {
            document.removeEventListener("mousedown", handleClickOutside);
        };
    }, [otherMenuOpen]);

    return (
        <div className="logs-view">
            {/* Filter Tabs matching Image 4 */}
            <div className="logs-filter-bar" role="tablist">
                <button
                    role="tab"
                    aria-selected={activeTab === "all"}
                    className={`logs-filter-tab ${activeTab === "all" ? "active" : ""}`}
                    onClick={() => setActiveTab("all")}
                >
                    <span>{isEn ? "All logs" : "Tất cả log"}</span>
                    <span className="logs-tab-badge">{counts.all}</span>
                </button>
                <button
                    role="tab"
                    aria-selected={activeTab === "errors"}
                    className={`logs-filter-tab ${activeTab === "errors" ? "active error" : ""}`}
                    onClick={() => setActiveTab("errors")}
                >
                    <span>{isEn ? "Errors" : "Lỗi"}</span>
                    <span className={`logs-tab-badge ${counts.errors > 0 ? "error-badge" : ""}`}>
                        {counts.errors}
                    </span>
                </button>
                <button
                    role="tab"
                    aria-selected={activeTab === "warnings"}
                    className={`logs-filter-tab ${activeTab === "warnings" ? "active warning" : ""}`}
                    onClick={() => setActiveTab("warnings")}
                >
                    <span>{isEn ? "Warnings" : "Cảnh báo"}</span>
                    <span className={`logs-tab-badge ${counts.warnings > 0 ? "warning-badge" : ""}`}>
                        {counts.warnings}
                    </span>
                </button>
                <button
                    role="tab"
                    aria-selected={activeTab === "info"}
                    className={`logs-filter-tab ${activeTab === "info" ? "active info" : ""}`}
                    onClick={() => setActiveTab("info")}
                >
                    <span>{isEn ? "Info" : "Thông tin"}</span>
                    <span className="logs-tab-badge">{counts.info}</span>
                </button>
            </div>

            {/* List of issues & warnings */}
            <div className="logs-list-container">
                {filteredItems.length === 0 && (
                    <div className="logs-empty-state">
                        <Check size={28} className="empty-check-icon" />
                        <p>
                            {isEn
                                ? "No errors or warnings found."
                                : "Không có lỗi hoặc cảnh báo nào trong mục này."}
                        </p>
                    </div>
                )}

                {filteredItems.map((item) => {
                    const isOpen = expandedIds.has(item.id);
                    return (
                        <div
                            key={item.id}
                            className={`log-card log-${item.type} ${isOpen ? "open" : ""}`}
                        >
                            <div
                                className="log-card-header"
                                onClick={() => toggleExpand(item.id)}
                            >
                                <span className="log-chevron">
                                    {isOpen ? (
                                        <ChevronDown size={16} />
                                    ) : (
                                        <ChevronRight size={16} />
                                    )}
                                </span>
                                <div className="log-title-group">
                                    <strong className={`log-title text-${item.type}`}>
                                        {item.title}
                                    </strong>
                                    <div className="log-location">
                                        /{item.file}
                                        {item.line && item.line > 0 ? `:${item.line}` : ""}
                                    </div>
                                </div>
                                <span className="log-card-sparkle" title="AI / Assist">
                                    <Sparkles size={14} />
                                </span>
                            </div>

                            {isOpen && (
                                <div className="log-card-body">
                                    {item.message && item.message !== item.title && (
                                        <pre className="log-msg-code">{item.message}</pre>
                                    )}
                                    {item.why && (
                                        <div className="log-field">
                                            <span className="log-field-label">
                                                {isEn ? "Why:" : "Nguyên nhân:"}
                                            </span>
                                            <p>{item.why}</p>
                                        </div>
                                    )}
                                    {item.fix && (
                                        <div className="log-field log-fix">
                                            <span className="log-field-label">
                                                {isEn ? "Fix:" : "Cách khắc phục:"}
                                            </span>
                                            <p>{item.fix}</p>
                                        </div>
                                    )}
                                    {item.line && item.line > 0 && onJumpToLine && (
                                        <button
                                            className="log-jump-btn"
                                            onClick={(e) => {
                                                e.stopPropagation();
                                                onJumpToLine(item.file, item.line!);
                                            }}
                                        >
                                            <ArrowUpRight size={13} />
                                            {isEn
                                                ? `Jump to line ${item.line}`
                                                : `Đi tới dòng ${item.line}`}
                                        </button>
                                    )}
                                </div>
                            )}
                        </div>
                    );
                })}

                {/* Raw logs accordion matching Image 4 */}
                <div className={`raw-logs-section ${rawLogsOpen ? "open" : ""}`}>
                    <div
                        className="raw-logs-header"
                        onClick={() => setRawLogsOpen((v) => !v)}
                    >
                        <div className="raw-logs-title-wrap">
                            <span className="raw-logs-chevron">
                                {rawLogsOpen ? (
                                    <ChevronDown size={16} />
                                ) : (
                                    <ChevronRight size={16} />
                                )}
                            </span>
                            <strong>{isEn ? "Raw logs" : "Nhật ký thô"}</strong>
                        </div>
                        {rawLogsOpen && (
                            <div
                                className="raw-logs-actions"
                                onClick={(e) => e.stopPropagation()}
                            >
                                <button
                                    className="raw-action-btn"
                                    onClick={handleCopyLogs}
                                    title={isEn ? "Copy raw logs" : "Sao chép nhật ký"}
                                >
                                    {copied ? (
                                        <>
                                            <Check size={12} />
                                            {isEn ? "Copied" : "Đã chép"}
                                        </>
                                    ) : (
                                        <>
                                            <Copy size={12} />
                                            {isEn ? "Copy" : "Sao chép"}
                                        </>
                                    )}
                                </button>
                                <button
                                    className="raw-action-btn"
                                    onClick={() => setLogs("")}
                                    title={isEn ? "Clear output" : "Xóa hiển thị"}
                                >
                                    {isEn ? "Clear" : "Xóa"}
                                </button>
                            </div>
                        )}
                    </div>
                    {rawLogsOpen && (
                        <pre ref={logPaneRef} className="build-output-raw">
                            {logs ||
                                (isEn
                                    ? "Compilation logs will appear here."
                                    : "Nhật ký biên dịch sẽ hiển thị tại đây.")}
                        </pre>
                    )}
                </div>
            </div>

            {/* Bottom Bar matching Image 4 */}
            <div className="logs-bottom-bar">
                <button
                    className="clear-cache-btn"
                    disabled={busy}
                    onClick={() => void onClearCache()}
                    title={
                        isEn
                            ? "Delete aux, log and cached compilation files"
                            : "Xóa các tệp phụ, cache và tệp log biên dịch"
                    }
                >
                    <Trash2 size={14} />
                    <span>{isEn ? "Clear cached files" : "Xóa bộ nhớ đệm"}</span>
                </button>

                <div className="other-logs-dropdown" ref={otherMenuRef}>
                    <button
                        className="other-logs-btn"
                        onClick={() => setOtherMenuOpen((v) => !v)}
                        aria-expanded={otherMenuOpen}
                    >
                        <span>
                            {isEn ? "Other logs and files" : "Nhật ký & tệp khác"}
                        </span>
                        <ChevronDown size={14} />
                    </button>
                    {otherMenuOpen && (
                        <div className="other-logs-menu" role="menu">
                            <button
                                role="menuitem"
                                onClick={handleDownloadLog}
                                disabled={!logs}
                            >
                                <Download size={14} />
                                <span>
                                    {isEn
                                        ? "Download compiler log (.log)"
                                        : "Tải xuống nhật ký (.log)"}
                                </span>
                            </button>
                            <button
                                role="menuitem"
                                onClick={() => {
                                    void handleCopyLogs();
                                    setOtherMenuOpen(false);
                                }}
                            >
                                <Copy size={14} />
                                <span>
                                    {isEn
                                        ? "Copy full compiler logs"
                                        : "Sao chép toàn bộ nhật ký"}
                                </span>
                            </button>
                            <button
                                role="menuitem"
                                onClick={() => {
                                    setOtherMenuOpen(false);
                                    onBackToPdf();
                                }}
                            >
                                <FileText size={14} />
                                <span>
                                    {isEn
                                        ? "View PDF document"
                                        : "Xem tài liệu PDF"}
                                </span>
                            </button>
                        </div>
                    )}
                </div>
            </div>
        </div>
    );
}
