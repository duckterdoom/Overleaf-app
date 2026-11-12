"use client";
import { useRef, useState, type ReactNode } from "react";
import { Code2, FileText, FileUp, RefreshCw } from "lucide-react";
import { compile, type Project } from "../../lib/api";
import { useWritingApi } from "../../lib/writing/context";
import { errorText } from "../../lib/parity";
import PdfViewer from "../PdfViewer";

export default function DocumentView({
    assignmentId,
    submissionId,
    children,
    sourceOverride,
    onSourceChange,
    onOpenProject,
    beforeAction,
    language,
    sourceSignal,
    focused = false,
}: {
    assignmentId?: string;
    submissionId?: string;
    children: ReactNode;
    sourceOverride?: string;
    onSourceChange?: (source: string) => void;
    onOpenProject: (project: Project) => Promise<void>;
    beforeAction: () => Promise<boolean>;
    language: string;
    sourceSignal: string;
    focused?: boolean;
}) {
    const { json, request } = useWritingApi();
    const [tab, setTab] = useState<"text" | "source" | "pdf">("text"),
        [source, setSource] = useState(""),
        [pdf, setPdf] = useState<Uint8Array | null>(null),
        [busy, setBusy] = useState(false),
        [error, setError] = useState(""),
        [pdfSignal, setPdfSignal] = useState("");
    const latestSignal = useRef(sourceSignal);
    latestSignal.current = sourceSignal;
    const context = { assignmentId, submissionId };
    async function generate() {
        if (!(await beforeAction())) return;
        setBusy(true);
        setError("");
        try {
            const data = await json<{ source: string }>("/writing/latex", {
                method: "POST",
                body: JSON.stringify(context),
            });
            setSource(data.source);
            onSourceChange?.(data.source);
        } catch (e) {
            setError(errorText(e));
        } finally {
            setBusy(false);
        }
    }
    async function makeProject(report = false, open = false) {
        if (!(await beforeAction())) return;
        setBusy(true);
        setError("");
        const ticket = latestSignal.current;
        try {
            const data = await json<{ source: string; project: Project; pdf?:string; artifact?:boolean }>(
                "/writing/latex",
                {
                    method: "POST",
                    body: JSON.stringify({
                        ...context,
                        report,
                        createProject: true,
                        useDraftSource: !!assignmentId,
                    }),
                },
            );
            if(data.artifact && data.pdf){
                setSource(data.source);
                setPdf(Uint8Array.from(atob(data.pdf),c=>c.charCodeAt(0)));
                setPdfSignal(ticket);setTab('pdf');return;
            }
            if (open) {
                await onOpenProject(data.project);
                return;
            }
            let pending: Promise<void> = Promise.resolve();
            let failed = "";
            await compile(
                data.project.id,
                "pdflatex",
                (event) => {
                    if (event.type === "error")
                        failed = event.message || "Biên dịch chưa thành công.";
                    if (event.type === "done" && event.snapshot)
                        pending = (async () => {
                            const response = await request(
                                `/projects/${data.project.id}/preview/${event.snapshot}`,
                            );
                            setPdf(
                                new Uint8Array(await response.arrayBuffer()),
                            );
                            setPdfSignal(ticket);
                        })();
                },
                "main.tex",
            );
            await pending;
            if (failed) throw Error(failed);
        } catch (e) {
            setError(errorText(e));
        } finally {
            setBusy(false);
        }
    }
    async function choose(next: typeof tab) {
        setTab(next);
        if (next === "source" && !source && !sourceOverride) await generate();
        if (next === "pdf" && (!pdf || pdfSignal !== sourceSignal))
            await makeProject();
    }
    if (focused) return <div className="writing-document-view writing-focused-document">{children}</div>;
    return (
        <div className="writing-document-view">
            <div
                className="writing-view-tabs"
                role="tablist"
                aria-label="Dạng xem IELTS"
            >
                <button
                    role="tab"
                    aria-selected={tab === "text"}
                    onClick={() => void choose("text")}
                >
                    <FileText size={15} />
                    Văn bản
                </button>
                <button
                    role="tab"
                    aria-selected={tab === "source"}
                    onClick={() => void choose("source")}
                >
                    <Code2 size={15} />
                    LaTeX source
                </button>
                <button
                    role="tab"
                    aria-selected={tab === "pdf"}
                    onClick={() => void choose("pdf")}
                >
                    PDF preview
                </button>
                <span />
                <button
                    disabled={busy}
                    onClick={() => void makeProject(false, true)}
                >
                    <FileUp size={15} />
                    Mở trong editor LaTeX
                </button>
            </div>
            {error && (
                <p className="writing-error" role="alert">
                    {error}
                </p>
            )}
            {tab === "text" && children}
            {tab === "source" && (
                <div className="writing-source-panel">
                    <div className="writing-notice">
                        {onSourceChange
                            ? "Source là nhánh trình bày riêng. Sửa LaTeX không tự đổi câu trả lời tiếng Anh dùng để chấm."
                            : "Source được tạo từ văn bản snapshot; không thực thi mã tự viết của học viên khi Tutor mở bản nộp."}
                    </div>
                    <button disabled={busy} onClick={() => void generate()}>
                        <RefreshCw size={14} />
                        Tạo lại source từ văn bản
                    </button>
                    <textarea
                        aria-label="LaTeX của bài IELTS"
                        spellCheck={false}
                        readOnly={!onSourceChange}
                        value={
                            sourceOverride !== undefined
                                ? sourceOverride
                                : source
                        }
                        onChange={(e) => {
                            setSource(e.target.value);
                            onSourceChange?.(e.target.value);
                        }}
                    />
                </div>
            )}
            {tab === "pdf" && (
                <div className="writing-pdf-panel">
                    <div className="writing-notice">
                        {assignmentId
                            ? "PDF từ nhánh LaTeX của bản viết; phần văn bản vẫn là nội dung nộp/chấm."
                            : "PDF trình bày từ đúng văn bản trong snapshot đã nộp."}{" "}
                        <button
                            disabled={busy}
                            onClick={() => void makeProject()}
                        >
                            {busy ? "Đang biên dịch…" : "Biên dịch lại PDF"}
                        </button>
                    </div>
                    <PdfViewer
                        data={pdf}
                        stale={pdfSignal !== sourceSignal}
                        language={language}
                    />
                </div>
            )}
        </div>
    );
}
