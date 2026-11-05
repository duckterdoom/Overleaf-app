"use client";
import { useEffect, useMemo, useRef, useState } from "react";
import CodeMirror from "@uiw/react-codemirror";
import { StreamLanguage } from "@codemirror/language";
import { stex } from "@codemirror/legacy-modes/mode/stex";
import { EditorView } from "@codemirror/view";
import {
    ArrowLeft,
    ArrowRight,
    BookOpen,
    Braces,
    Check,
    ChevronRight,
    Circle,
    Clock3,
    Code2,
    Download,
    FilePlus2,
    FileText,
    Folder,
    FolderPlus,
    GraduationCap,
    MessageSquareText,
    MessagesSquare,
    Grid2X2,
    LayoutPanelLeft,
    Leaf,
    List,
    LoaderCircle,
    Play,
    Plus,
    Search,
    Settings2,
    Square,
    Terminal,
    X,
} from "lucide-react";
import PdfViewer from "../components/PdfViewer";
import WordPreview from "../components/WordDocxPreview";
import type { WordPreviewHandle } from "../lib/word-preview";
import {
    bootstrap,
    compile,
    json,
    request,
    type Catalog,
    type Entry,
    type Project,
    type Template,
} from "../lib/api";

import AuthView from "../components/AuthView";
import SettingsPanel from "../components/SettingsPanel";
import ProfilePanel from "../components/ProfilePanel";
import HistoryPanel from "../components/HistoryPanel";
import ActivityHeatmap from "../components/ActivityHeatmap";
import EditorToolbar from "../components/EditorToolbar";
import AddFilesPanel from "../components/AddFilesPanel";
import NetworkPanel from "../components/NetworkPanel";
import ProjectMenu from "../components/ProjectMenu";
import ProjectCard from "../components/ProjectCard";
import NewProjectExtras from "../components/NewProjectExtras";
import FileActionsPanel from "../components/FileActionsPanel";
import LogsView, { parseAllLogs } from "../components/LogsView";
import IeltsGradingPanel from "../components/IeltsGradingPanel";
import useParity from "../lib/useParity";
import { action, native, translator, type Activity } from "../lib/parity";
import { localize } from "../lib/i18n";
import { orderedTree } from "../lib/fileTree";
import ReviewPanel, { type ReviewAnchor } from "../components/ReviewPanel";
import { reviewHighlights } from "../lib/reviewHighlights";
const latex = StreamLanguage.define(stex);
type Dialog = "project" | "file" | "folder" | null;
const fold = (text: string) =>
    text
        .normalize("NFD")
        .replace(/[\u0300-\u036f]/g, "")
        .replace(/đ/g, "d")
        .toLowerCase();

export default function Workspace() {
    const [ready, setReady] = useState(false),
        [projects, setProjects] = useState<Project[]>([]),
        [catalog, setCatalog] = useState<Catalog>({
            categories: [],
            templates: [],
        });
    const [project, setProject] = useState<Project | null>(null),
        [entries, setEntries] = useState<Entry[]>([]);
    const [file, setFile] = useState(""),
        [content, setContent] = useState(""),
        [dirty, setDirty] = useState(false);
    const [search, setSearch] = useState(""),
        [category, setCategory] = useState(""),
        [templateSearch, setTemplateSearch] = useState("");
    const [dialog, setDialog] = useState<Dialog>(null),
        [name, setName] = useState(""),
        [selectedTemplate, setSelectedTemplate] = useState<Template | null>(
            null,
        ),
        [language, setLanguage] = useState("vi");
    const [working, setWorking] = useState(false),
        [busy, setBusy] = useState(false),
        [engine, setEngine] = useState("pdflatex"),
        [logs, setLogs] = useState("");
    const [pdf, setPdf] = useState<Uint8Array | null>(null),
        [snapshot, setSnapshot] = useState(""),
        [stale, setStale] = useState(true);
    const [notice, setNotice] = useState("Đang kết nối…"),
        [error, setError] = useState(""),
        [layout, setLayout] = useState("split"),
        [folder, setFolder] = useState("");
    const [parent, setParent] = useState(""),
        [icon, setIcon] = useState("file"),
        [color, setColor] = useState("green"),
        [tag, setTag] = useState("");
    const [fileAction, setFileAction] = useState<Entry | null>(null),
        [layoutMenu, setLayoutMenu] = useState(false);
    const [preflight, setPreflight] = useState<{
            missing: { name: string; package: string }[];
            bundled: boolean;
        } | null>(null),
        [installing, setInstalling] = useState(false);
    const [collapsed, setCollapsed] = useState<Set<string>>(new Set());
    const [sidePanel, setSidePanel] = useState<
        "files" | "review" | "chat" | "ielts" | null
    >("files");
    const [reviewDraft, setReviewDraft] = useState<ReviewAnchor | null>(null);
    const [previewTab, setPreviewTab] = useState<"pdf" | "word" | "logs">(
        "pdf",
    );
    const [downloadMenu, setDownloadMenu] = useState(false);
    const [wordExporting, setWordExporting] = useState(false);
    const wordPreview = useRef<WordPreviewHandle | null>(null);
    useEffect(() => {
        setSidePanel("files");
        setReviewDraft(null);
        setPreviewTab("pdf");
        setDownloadMenu(false);
    }, [project?.id]);
    const outlinePane = useRef<HTMLDivElement>(null);
    const revision = useRef(0),
        view = useRef<EditorView | null>(null),
        logPane = useRef<HTMLPreElement>(null);
    const savedText = useRef("");
    const buildInFlight = useRef(false);
    const text = (message: unknown) =>
        message instanceof Error ? message.message : String(message);
    useEffect(() => {
        bootstrap();
        void Promise.all([
            json<Project[]>("/projects"),
            json<Catalog>("/templates"),
            json("/health"),
        ])
            .then(([projects, catalog]) => {
                setProjects(projects);
                setCatalog(catalog);
                setReady(true);
                setNotice("Local workspace · Sẵn sàng");
            })
            .catch((error) => {
                setError(text(error));
                setNotice(
                    "Không kết nối được backend. Mở lại ứng dụng desktop.",
                );
            });
    }, []);
    useEffect(() => {
        if (logPane.current)
            logPane.current.scrollTop = logPane.current.scrollHeight;
    }, [logs]);
    useEffect(() => {
        const listener = (event: BeforeUnloadEvent) => {
            if (dirty) {
                event.preventDefault();
                event.returnValue = "";
            }
        };
        window.addEventListener("beforeunload", listener);
        return () => window.removeEventListener("beforeunload", listener);
    }, [dirty]);
    async function save() {
        if (!project || !file) return true;
        if (ext.readonly) return !dirty;
        const ticket = revision.current;
        try {
            await json(`/projects/${project.id}/file`, {
                method: "PUT",
                body: JSON.stringify({ path: file, content }),
            });
            savedText.current = content;
            await action("editing", {
                project: project.id,
                dirty: ticket !== revision.current,
            });
            void refreshActivity();
            if (ticket === revision.current) {
                setDirty(false);
                setNotice("Đã lưu thay đổi trên máy.");
            }
            return ticket === revision.current;
        } catch (error) {
            setError(text(error));
            return false;
        }
    }
    useEffect(() => {
        const handler = (event: KeyboardEvent) => {
            if (
                (event.ctrlKey || event.metaKey) &&
                event.key.toLowerCase() === "s"
            ) {
                event.preventDefault();
                void save();
            }
        };
        window.addEventListener("keydown", handler);
        return () => window.removeEventListener("keydown", handler);
    });
    async function open(project: Project) {
        if (busy || (dirty && !(await save()))) return;
        try {
            await ext.leaveBeforeOpen(project.id);
            const [entries, source] = await Promise.all([
                json<Entry[]>(`/projects/${project.id}/files`),
                json<{ content: string }>(
                    `/projects/${project.id}/file?path=${encodeURIComponent(project.main)}`,
                ),
            ]);
            setProject(project);
            setEngine(project.engine);
            setEntries(entries);
            setFile(project.main);
            setContent(source.content);
            savedText.current = source.content;
            revision.current++;
            setDirty(false);
            setPdf(null);
            setSnapshot("");
            setStale(true);
            setLogs("");
            setError("");
            setFolder("");
            setNotice("Dữ liệu được lưu trên máy của bạn.");
        } catch (error) {
            setError(text(error));
        }
    }
    async function openFile(entry: Entry) {
        if (busy) return;
        if (entry.directory) {
            setFolder(entry.path);
            return;
        }
        if (!entry.editable) {
            try {
                await native("open_binary", project!.id, entry.path);
            } catch (error) {
                setError(text(error));
            }
            return;
        }
        if (ext.network.joined && entry.path !== "main.tex") {
            setError(
                t(
                    "Rời phòng trước khi mở tệp khác.",
                    "Leave the room before opening another file.",
                ),
            );
            return;
        }
        if (dirty && !(await save())) return;
        try {
            const source = await json<{ content: string }>(
                `/projects/${project!.id}/file?path=${encodeURIComponent(entry.path)}`,
            );
            setFile(entry.path);
            setContent(source.content);
            savedText.current = source.content;
            setDirty(false);
            setFolder(
                entry.path.includes("/")
                    ? entry.path.slice(0, entry.path.lastIndexOf("/"))
                    : "",
            );
        } catch (error) {
            setError(text(error));
        }
    }
    function change(value: string) {
        if (ext.readonly) return;
        ext.changed(value, content);
        revision.current++;
        setContent(value);
        setDirty(value !== savedText.current);
        setStale(true);
    }
    async function submit(event: React.FormEvent) {
        event.preventDefault();
        setWorking(true);
        setError("");
        try {
            if (dialog === "project") {
                const report = await action<{
                    missing: { name: string; package: string }[];
                    bundled: boolean;
                }>("preflight", { template: selectedTemplate?.id || "blank" });
                setPreflight(report);
                if (report.missing.length) return;
                const created = await json<Project>("/projects", {
                    method: "POST",
                    body: JSON.stringify({
                        title: name,
                        template: selectedTemplate?.id || "blank",
                        language,
                        parent,
                        icon,
                        color,
                    }),
                });
                setProjects(await json<Project[]>("/projects"));
                setDialog(null);
                await open(created);
            } else {
                await json(`/projects/${project!.id}/file`, {
                    method: "POST",
                    body: JSON.stringify({
                        path: name,
                        directory: dialog === "folder",
                    }),
                });
                setEntries(
                    await json<Entry[]>(`/projects/${project!.id}/files`),
                );
                setDialog(null);
                setStale(true);
                if (dialog === "file")
                    await openFile({
                        path: name,
                        directory: false,
                        editable: true,
                    });
                else setFolder(name);
            }
        } catch (error) {
            setError(text(error));
        } finally {
            setWorking(false);
        }
    }
    async function build() {
        if (
            !project ||
            busy ||
            buildInFlight.current ||
            ext.readonly ||
            ext.detached
        )
            return;
        ext.buildStarted();
        buildInFlight.current = true;
        setBusy(true);
        if (!(await save())) {
            buildInFlight.current = false;
            setBusy(false);
            return;
        }
        const ticket = revision.current;
        setBusy(true);
        setStale(true);
        setError("");
        setLogs(`[BUILD] ${engine}\n`);
        setNotice("Đang biên dịch…");
        let complete: Promise<void> = Promise.resolve();
        try {
            await compile(project.id, engine, (event) => {
                if (event.type === "output")
                    setLogs((log) => (log + event.text).slice(-180000));
                if (event.type === "error") {
                    ext.setIssues(event.issues || []);
                    setError(event.message || "Biên dịch thất bại.");
                    setLogs((log) => log + `\n[ERROR] ${event.message}\n`);
                    setNotice("Biên dịch chưa thành công.");
                }
                if (event.type === "done") {
                    complete = (async () => {
                        const response = await request(
                            `/projects/${project.id}/preview/${event.snapshot}`,
                        );
                        setPdf(new Uint8Array(await response.arrayBuffer()));
                        setSnapshot(event.snapshot || "");
                        ext.setGeometry(event.geometry || []);
                        void refreshActivity();
                        const current =
                            Boolean(event.current) &&
                            ticket === revision.current;
                        setStale(!current);
                        setNotice(
                            current
                                ? `Biên dịch thành công · ${event.duration_ms} ms`
                                : "Nguồn đã đổi. Cần biên dịch lại trước khi xuất.",
                        );
                    })();
                    // Attach a handler immediately; the same promise is awaited below to report errors.
                    void complete.catch(() => {});
                }
            });
            await complete;
        } catch (error) {
            setError(text(error));
            setNotice("Biên dịch chưa thành công.");
        } finally {
            setBusy(false);
            buildInFlight.current = false;
        }
    }
    async function exportPdf() {
        if (!project || !snapshot || stale || dirty || busy) return;
        try {
            if (window.pywebview?.api) {
                const result = await window.pywebview.api.export_pdf(
                    project.id,
                    snapshot,
                );
                if (result.error) throw new Error(result.error);
                if (result.saved) setNotice("Đã xuất PDF.");
            } else {
                const response = await request(
                        `/projects/${project.id}/pdf/${snapshot}`,
                    ),
                    blob = await response.blob();
                const url = URL.createObjectURL(blob),
                    anchor = document.createElement("a");
                anchor.href = url;
                anchor.download = "main.pdf";
                anchor.click();
                setTimeout(() => URL.revokeObjectURL(url), 1000);
            }
        } catch (error) {
            setError(text(error));
            setStale(true);
        }
    }
    async function exportWord() {
        if (!project || !snapshot || stale || dirty || busy || wordExporting)
            return;
        setWordExporting(true);
        setError("");
        setNotice(t("Đang chuyển PDF sang Word…", "Converting PDF to Word…"));
        try {
            if (wordPreview.current && !(await wordPreview.current.flush()))
                throw Error(
                    t(
                        "Chưa lưu được bản Word đang sửa.",
                        "Could not save pending Word edits.",
                    ),
                );
            if (window.pywebview?.api) {
                const result = await window.pywebview.api.export_docx(
                    project.id,
                    snapshot,
                );
                if (result.error) throw Error(result.error);
                setNotice(
                    result.saved
                        ? t("Đã xuất Word từ PDF.", "Word exported from PDF.")
                        : t("Đã hủy lưu Word.", "Word save cancelled."),
                );
            } else {
                const response = await request(
                    `/projects/${project.id}/pdf/${snapshot}/docx`,
                );
                const url = URL.createObjectURL(await response.blob()),
                    anchor = document.createElement("a");
                anchor.href = url;
                anchor.download = "main.docx";
                anchor.click();
                setTimeout(() => URL.revokeObjectURL(url), 1000);
                setNotice(t("Đã xuất Word từ PDF.", "Word exported from PDF."));
            }
        } catch (error) {
            setError(text(error));
            setNotice(
                t(
                    "Chưa xuất được Word. Xem thông báo lỗi.",
                    "Word export failed. See the error message.",
                ),
            );
        } finally {
            setWordExporting(false);
        }
    }
    function insert(before: string, after = "") {
        const editor = view.current;
        if (!editor) return;
        const range = editor.state.selection.main,
            selected = editor.state.sliceDoc(range.from, range.to);
        editor.dispatch({
            changes: {
                from: range.from,
                to: range.to,
                insert: before + selected + after,
            },
            selection: {
                anchor: range.from + before.length,
                head: range.from + before.length + selected.length,
            },
        });
        editor.focus();
    }
    const ext = useParity({
        project,
        file,
        content,
        dirty,
        busy,
        engine,
        snapshot,
        stale,
        setProject,
        setProjects,
        setEntries,
        setFile,
        setContent,
        setDirty,
        setStale,
        setPdf,
        setSnapshot,
        setNotice,
        setError,
        setLayout,
        setLanguage,
        setEngine,
        setLogs,
        save,
        build,
        open,
        openFile,
        editor: view,
        saved: savedText,
        revision,
        buildInFlight,
    });
    const uiLanguage = String(ext.settings.language || "vi"),
        t = translator(uiLanguage);
    const logEntries = useMemo(
        () => parseAllLogs(ext.issues, logs, file, uiLanguage),
        [ext.issues, logs, file, uiLanguage],
    );
    const logCounts = useMemo(() => {
        const errors = logEntries.filter((i) => i.type === "error").length;
        const warnings = logEntries.filter((i) => i.type === "warning").length;
        return {
            all: logEntries.length,
            errors,
            warnings,
        };
    }, [logEntries]);
    const logBadgeCount =
        logCounts.errors > 0
            ? logCounts.errors
            : logCounts.warnings > 0
              ? logCounts.warnings
              : logCounts.all;
    const logBadgeClass =
        logCounts.errors > 0
            ? "has-error"
            : logCounts.warnings > 0
              ? "has-warning"
              : "is-clean";
    useEffect(() => {
        if (!ext.focus) setLayoutMenu(false);
    }, [ext.focus]);
    async function refreshActivity() {
        ext.setActivity(await action<Activity>("activity"));
    }
    async function refreshProjects() {
        setProjects(await json<Project[]>("/projects"));
    }
    async function refreshFiles(path?: string) {
        if (!project) return;
        setEntries(await json<Entry[]>(`/projects/${project.id}/files`));
        // Reveal the destination branch after creating/uploading into a folder.
        const reveal = path ? path.split("/").slice(0, -1).join("/") : folder;
        setCollapsed(
            (current) =>
                new Set(
                    [...current].filter(
                        (item) =>
                            item !== reveal && !reveal.startsWith(item + "/"),
                    ),
                ),
        );
        setStale(true);
        if (path) await openFile({ path, directory: false, editable: true });
    }
    function resizeSidebar(event: React.PointerEvent) {
        event.preventDefault();
        const pane = event.currentTarget.parentElement!;
        const rect = pane.getBoundingClientRect();
        const railWidth =
            pane
                .querySelector(".workspace-activity-bar")
                ?.getBoundingClientRect().width || 0;
        let width = Number(ext.settings.sidebarWidth) || 270;
        const move = (event: PointerEvent) => {
            width = Math.round(
                Math.max(
                    200,
                    Math.min(
                        600,
                        rect.width - railWidth - 420,
                        event.clientX - rect.left - railWidth,
                    ),
                ),
            );
            pane.style.setProperty("--sidebar-width", width + "px");
        };
        const end = () => {
            window.removeEventListener("pointermove", move);
            window.removeEventListener("pointerup", end);
            window.removeEventListener("pointercancel", end);
            ext.setSettings((value) => ({ ...value, sidebarWidth: width }));
            void action("settings", { sidebarWidth: width });
        };
        window.addEventListener("pointermove", move);
        window.addEventListener("pointerup", end);
        window.addEventListener("pointercancel", end);
    }
    function resizeOutline(event: React.PointerEvent) {
        const pane = outlinePane.current;
        if (!pane) return;
        event.preventDefault();
        const rect = pane.getBoundingClientRect();
        let ratio = Number(ext.settings.outlineSplit) || 0.6;
        const move = (event: PointerEvent) => {
            ratio = Math.max(
                0.2,
                Math.min(0.85, (event.clientY - rect.top) / rect.height),
            );
            pane.style.setProperty("--outline-split", ratio * 100 + "%");
        };
        const end = () => {
            window.removeEventListener("pointermove", move);
            window.removeEventListener("pointerup", end);
            ext.setSettings((value) => ({ ...value, outlineSplit: ratio }));
            void action("settings", { outlineSplit: ratio });
        };
        window.addEventListener("pointermove", move);
        window.addEventListener("pointerup", end);
    }
    function resizeSplit(event: React.PointerEvent) {
        event.preventDefault();
        const pane = event.currentTarget.parentElement!;
        const rect = pane.getBoundingClientRect();
        let ratio = Number(ext.settings.editorSplit) || 0.5;
        const move = (event: PointerEvent) => {
            ratio = Math.max(
                0.25,
                Math.min(0.75, (event.clientX - rect.left) / rect.width),
            );
            pane.style.setProperty("--editor-split", ratio * 100 + "%");
        };
        const end = () => {
            window.removeEventListener("pointermove", move);
            window.removeEventListener("pointerup", end);
            ext.setSettings((value) => ({ ...value, editorSplit: ratio }));
            void action("settings", { editorSplit: ratio });
        };
        window.addEventListener("pointermove", move);
        window.addEventListener("pointerup", end);
    }
    const outline = useMemo(() => {
        let offset = 0;
        return content.split("\n").flatMap((line) => {
            const start = offset;
            offset += line.length + 1;
            const match = line.match(
                /\\(chapter|section|subsection)\*?\{([^}]+)\}/,
            );
            return match
                ? [{ title: match[2], start, sub: match[1] === "subsection" }]
                : [];
        });
    }, [content]);
    const templates = catalog.templates.filter(
        (item) =>
            (!category || item.category === category) &&
            (!tag || item.tags.includes(tag)) &&
            fold(
                `${item.title.vi} ${item.title.en} ${item.description.vi} ${item.tags.join(" ")}`,
            ).includes(fold(templateSearch)),
    );
    const filtered = projects.filter((item) =>
        fold(item.title).includes(fold(search)),
    );

    if (!ready || !ext.loaded)
        return (
            <main className="auth-screen" data-desktop-ready="false">
                <div className="auth-card">
                    <LoaderCircle className="spin" size={28} />
                    <p>
                        {error ||
                            t(
                                "Đang chuẩn bị không gian làm việc…",
                                "Preparing your workspace…",
                            )}
                    </p>
                    {error && (
                        <button
                            className="secondary"
                            onClick={() => location.reload()}
                        >
                            {t("Thử lại", "Retry")}
                        </button>
                    )}
                </div>
            </main>
        );
    if (!ext.session)
        return (
            <main data-desktop-ready="true">
                <AuthView
                    language={uiLanguage}
                    initialEmail={ext.lastEmail}
                    onDone={ext.authenticated}
                />
            </main>
        );
    if (project && ext.panel === "history")
        return (
            <HistoryPanel
                project={project.id}
                language={uiLanguage}
                locked={ext.network.joined}
                onClose={ext.closeHistory}
                onRestore={(path, value) => {
                    setFile(path);
                    setContent(value);
                    savedText.current = value;
                    revision.current++;
                    setDirty(false);
                    setStale(true);
                    ext.closeHistory();
                }}
            />
        );
    return (
        <main
            data-desktop-ready={ready && ext.loaded}
            className={`desktop-shell ${ext.focus ? "focus-mode" : ""} ${ext.detached ? "detached-pdf" : ""}`}
        >
            <header className="app-header">
                <div className="brand">
                    <span className="brand-mark">
                        <Leaf size={23} />
                    </span>
                    <strong>
                        overleaf<span>desktop</span>
                    </strong>
                </div>
                {project ? (
                    <>
                        <span className="header-divider" />
                        <button
                            className="project-breadcrumb"
                            disabled={busy}
                            onClick={async () => {
                                if (wordPreview.current && !(await wordPreview.current.flush())) return;
                                if (dirty && !(await save())) return;
                                await ext.leaveBeforeOpen("");
                                setProject(null);
                                setProjects(await json<Project[]>("/projects"));
                            }}
                        >
                            <ArrowLeft size={16} />
                            {project.title}
                        </button>
                        <div className="header-spacer" />
                        <span className="local-pill">
                            <span />
                            {localize("Trên máy của bạn", uiLanguage)}
                        </span>
                        <button
                            className="quiet"
                            disabled={busy}
                            onClick={async () => { if (!wordPreview.current || await wordPreview.current.flush()) await ext.showHistory(); }}
                        >
                            <Clock3 size={17} />
                            {t("Lịch sử", "History")}
                        </button>
                        <button
                            className="quiet"
                            onClick={() => void ext.connect()}
                        >
                            {ext.network.joined
                                ? (ext.network.online ? "● " : "○ ") +
                                  ext.network.members.length +
                                  " · " +
                                  ext.network.pending
                                : t("Cộng tác", "Collaborate")}
                        </button>
                        <div className="layout-wrapper">
                            <button
                                className="quiet"
                                onClick={() => setLayoutMenu(!layoutMenu)}
                            >
                                <LayoutPanelLeft size={17} />
                                {t("Bố cục", "Layout")}
                            </button>
                            {layoutMenu && (
                                <div className="layout-menu">
                                    {[
                                        [
                                            "split",
                                            t(
                                                "Chia đôi màn hình",
                                                "Split view",
                                            ),
                                        ],
                                        [
                                            "editor",
                                            t("Chỉ editor", "Editor only"),
                                        ],
                                        ["pdf", t("Chỉ PDF", "PDF only")],
                                    ].map(([value, label]) => (
                                        <button
                                            key={value}
                                            onClick={async () => {
                                                if (wordPreview.current && !(await wordPreview.current.flush())) return;
                                                ext.layout(value);
                                                setLayoutMenu(false);
                                            }}
                                        >
                                            {layout === value ? "✓ " : ""}
                                            {label}
                                        </button>
                                    ))}
                                    <button
                                        onClick={() => {
                                            void ext.detach();
                                            setLayoutMenu(false);
                                        }}
                                    >
                                        {t(
                                            "Mở PDF ở cửa sổ riêng",
                                            "Open PDF in separate window",
                                        )}
                                    </button>
                                    <button
                                        onClick={() => {
                                            ext.setFocus(!ext.focus);
                                            setLayoutMenu(false);
                                        }}
                                    >
                                        {ext.focus
                                            ? t(
                                                  "Thoát chế độ tập trung",
                                                  "Exit focus mode",
                                              ) + " · Esc"
                                            : t(
                                                  "Chế độ tập trung",
                                                  "Focus mode",
                                              ) + " · Ctrl⇧M"}
                                    </button>
                                </div>
                            )}
                        </div>
                        {ext.focus && (
                            <button
                                className="quiet focus-exit-button"
                                aria-label={t(
                                    "Thoát chế độ tập trung",
                                    "Exit focus mode",
                                )}
                                onClick={() => {
                                    ext.setFocus(false);
                                    setLayoutMenu(false);
                                }}
                            >
                                <X size={16} />
                                {t("Thoát tập trung", "Exit focus")} · Esc
                            </button>
                        )}
                    </>
                ) : (
                    <>
                        <span className="nav-current">
                            {localize("Dự án", uiLanguage)}
                        </span>
                        <div className="header-spacer" />
                        <span className="local-pill">
                            <span /> Local workspace
                        </span>
                    </>
                )}
                <button
                    title={t("Cài đặt", "Settings")}
                    onClick={() => ext.setPanel("settings")}
                >
                    <Settings2 size={18} />
                </button>
                <button
                    className="avatar"
                    title={t("Hồ sơ", "Profile")}
                    aria-label={t("Hồ sơ", "Profile")}
                    onClick={() => ext.setPanel("profile")}
                >
                    {ext.session?.name.slice(0, 1).toUpperCase() || "?"}
                </button>
            </header>
            {error && (
                <div className="error-banner" role="alert">
                    <span>{error}</span>
                    <button
                        title={localize("Đóng thông báo", uiLanguage)}
                        onClick={() => setError("")}
                    >
                        <X size={16} />
                    </button>
                </div>
            )}
            {!project ? (
                <div className="dashboard">
                    <aside className="home-sidebar">
                        <p className="eyebrow">
                            {localize("KHÔNG GIAN LÀM VIỆC", uiLanguage)}
                        </p>
                        <button className="side-active">
                            <Folder size={18} />
                            {localize("Tất cả dự án", uiLanguage)}
                            <span>{projects.length}</span>
                        </button>
                        <div className="sidebar-note">
                            <div className="note-icon">
                                <Leaf size={19} />
                            </div>
                            <strong>
                                {localize(
                                    "Tập trung vào điều bạn viết.",
                                    uiLanguage,
                                )}
                            </strong>
                            <p>
                                {localize(
                                    "Tài liệu, bộ biên dịch và PDF đều ở trên máy của bạn.",
                                    uiLanguage,
                                )}
                            </p>
                            <div>
                                <Circle size={7} fill="currentColor" />
                                {localize(
                                    "Không cần tài khoản cloud",
                                    uiLanguage,
                                )}
                            </div>
                        </div>
                        <footer>
                            <div className="avatar">
                                {ext.session?.name.slice(0, 1)}
                            </div>
                            <div>
                                <strong>{ext.session?.name}</strong>
                                <small>
                                    {localize("Dữ liệu local", uiLanguage)}
                                </small>
                            </div>
                        </footer>
                    </aside>
                    <section className="projects-main">
                        <div className="page-heading">
                            <div>
                                <p className="eyebrow">
                                    {localize(
                                        "MỘT Ý TƯỞNG MỚI BẮT ĐẦU Ở ĐÂY",
                                        uiLanguage,
                                    )}
                                </p>
                                <h1>
                                    {localize("Dự án của bạn", uiLanguage)}
                                    <span>.</span>
                                </h1>
                                <p>
                                    {localize(
                                        "Viết, biên dịch và hoàn thiện tài liệu theo cách của bạn.",
                                        uiLanguage,
                                    )}
                                </p>
                            </div>
                            <button
                                className="primary"
                                disabled={!ready}
                                onClick={() => {
                                    setSelectedTemplate(null);
                                    setName("");
                                    setDialog("project");
                                }}
                            >
                                <Plus size={18} />
                                {localize("Dự án mới", uiLanguage)}
                            </button>
                        </div>
                        <div className="welcome-card">
                            <div>
                                <span className="welcome-label">
                                    <Leaf size={14} />
                                    {localize(
                                        "KHÔNG GIAN CHO Ý TƯỞNG",
                                        uiLanguage,
                                    )}
                                </span>
                                <h2>
                                    {localize("Từ dòng đầu tiên", uiLanguage)}
                                    <br />
                                    {localize(
                                        "đến bản PDF hoàn chỉnh.",
                                        uiLanguage,
                                    )}
                                </h2>
                                <p>
                                    {localize(
                                        "Bắt đầu với trang trống, hoặc chọn từ 29 mẫu tài liệu",
                                        uiLanguage,
                                    )}
                                    <br />
                                    {localize(
                                        "có sẵn bằng tiếng Việt và tiếng Anh.",
                                        uiLanguage,
                                    )}
                                </p>
                                <button
                                    onClick={() => {
                                        setSelectedTemplate(null);
                                        setName("");
                                        setDialog("project");
                                    }}
                                >
                                    {localize(
                                        "Khám phá thư viện mẫu",
                                        uiLanguage,
                                    )}{" "}
                                    <ArrowRight size={16} />
                                </button>
                            </div>
                            <div className="paper-art" aria-hidden="true">
                                <div className="back-paper" />
                                <div className="front-paper">
                                    <div className="paper-tag">LaTeX</div>
                                    <div className="paper-title" />
                                    <div className="paper-line short" />
                                    <div className="paper-formula">E = mc²</div>
                                    <div className="paper-line" />
                                    <div className="paper-line" />
                                    <div className="paper-line short" />
                                    <div className="paper-chart">
                                        <i />
                                        <i />
                                        <i />
                                        <i />
                                        <i />
                                    </div>
                                    <div className="paper-line" />
                                </div>
                                <div className="paper-check">
                                    <Check size={24} />
                                </div>
                            </div>
                        </div>
                        <ActivityHeatmap
                            activity={ext.activity}
                            language={uiLanguage}
                        />
                        <div className="project-tools">
                            <h2>
                                {localize("Tất cả dự án", uiLanguage)}
                                <span>{projects.length}</span>
                            </h2>
                            <div className="search-input">
                                <Search size={16} />
                                <input
                                    aria-label={localize(
                                        "Tìm dự án",
                                        uiLanguage,
                                    )}
                                    placeholder={localize(
                                        "Tìm dự án…",
                                        uiLanguage,
                                    )}
                                    value={search}
                                    onChange={(event) =>
                                        setSearch(event.target.value)
                                    }
                                />
                            </div>
                            <Grid2X2 size={18} />
                        </div>
                        {filtered.length ? (
                            <div className="project-grid">
                                {filtered.map((item) => (
                                    <ProjectCard
                                        key={item.id}
                                        project={item}
                                        language={uiLanguage}
                                        onOpen={() => void open(item)}
                                        onManage={() => ext.setManage(item)}
                                    />
                                ))}
                            </div>
                        ) : (
                            <div className="empty-projects">
                                <div>
                                    <FolderPlus size={28} strokeWidth={1.3} />
                                </div>
                                <h3>
                                    {search
                                        ? localize(
                                              "Không tìm thấy dự án",
                                              uiLanguage,
                                          )
                                        : localize(
                                              "Một trang mới đang chờ bạn",
                                              uiLanguage,
                                          )}
                                </h3>
                                <p>
                                    {search
                                        ? localize(
                                              "Thử tìm bằng một tên khác.",
                                              uiLanguage,
                                          )
                                        : localize(
                                              "Tạo dự án đầu tiên để bắt đầu viết.",
                                              uiLanguage,
                                          )}
                                </p>
                            </div>
                        )}
                        <div className="home-bottom">
                            <span>
                                <Check size={13} /> LaTeX local
                            </span>
                            <span>
                                <BookOpen size={13} />
                                {localize("29 mẫu tài liệu", uiLanguage)}
                            </span>
                            <span>
                                <Code2 size={13} />
                                {localize("Tệp nguồn thuộc về bạn", uiLanguage)}
                            </span>
                        </div>
                    </section>
                </div>
            ) : (
                <div
                    className="workspace-grid"
                    style={
                        {
                            "--sidebar-width":
                                (Number(ext.settings.sidebarWidth) || 270) +
                                "px",
                        } as React.CSSProperties
                    }
                >
                    <nav
                        className="workspace-activity-bar"
                        aria-label={t(
                            "Thanh công cụ workspace",
                            "Workspace tools",
                        )}
                    >
                        <button
                            title={t("Tệp và outline", "Files and outline")}
                            aria-label={t(
                                "Tệp và outline",
                                "Files and outline",
                            )}
                            aria-pressed={sidePanel === "files"}
                            onClick={() =>
                                setSidePanel(
                                    sidePanel === "files" ? null : "files",
                                )
                            }
                        >
                            <FileText size={21} />
                        </button>
                        <button
                            title={t("Nhận xét", "Review")}
                            aria-label={t("Nhận xét", "Review")}
                            aria-pressed={sidePanel === "review"}
                            onClick={() =>
                                setSidePanel(
                                    sidePanel === "review" ? null : "review",
                                )
                            }
                        >
                            <MessageSquareText size={21} />
                        </button>
                        <button
                            title={t("Chat cộng tác", "Collaborator chat")}
                            aria-label={t("Chat cộng tác", "Collaborator chat")}
                            aria-pressed={sidePanel === "chat"}
                            onClick={() =>
                                setSidePanel(
                                    sidePanel === "chat" ? null : "chat",
                                )
                            }
                        >
                            <MessagesSquare size={21} />
                        </button>
                        <button
                            title={t(
                                "Chấm điểm IELTS Writing",
                                "IELTS Writing Evaluation",
                            )}
                            aria-label={t(
                                "Chấm điểm IELTS Writing",
                                "IELTS Writing Evaluation",
                            )}
                            aria-pressed={sidePanel === "ielts"}
                            onClick={() =>
                                setSidePanel(
                                    sidePanel === "ielts" ? null : "ielts",
                                )
                            }
                        >
                            <GraduationCap size={21} />
                        </button>
                    </nav>
                    <aside
                        className="file-sidebar"
                        hidden={sidePanel !== "files"}
                    >
                        <div className="files-heading">
                            <Folder size={15} />
                            <strong>{localize("Tệp dự án", uiLanguage)}</strong>
                            <button
                                className="sidebar-close"
                                aria-label={t(
                                    "Đóng tệp và outline",
                                    "Close files and outline",
                                )}
                                onClick={() => setSidePanel(null)}
                            >
                                <X size={17} />
                            </button>
                        </div>
                        <div className="file-actions">
                            <button
                                title={localize("Thêm tệp", uiLanguage)}
                                onClick={() => {
                                    ext.setPanel("add-file");
                                }}
                            >
                                <FilePlus2 size={17} />
                            </button>
                            <button
                                title={localize("Thêm thư mục", uiLanguage)}
                                onClick={() => {
                                    ext.setPanel("add-folder");
                                }}
                            >
                                <FolderPlus size={17} />
                            </button>
                            <button
                                title={t("Tải lên", "Upload")}
                                onClick={() => ext.setPanel("add-upload")}
                            >
                                <Download size={17} />
                            </button>
                            <button
                                title={t("Thư mục gốc", "Root folder")}
                                onClick={() => setFolder("")}
                            >
                                /
                            </button>
                            <span>
                                {
                                    entries.filter((item) => !item.directory)
                                        .length
                                }{" "}
                                {localize("tệp", uiLanguage)}
                            </span>
                        </div>
                        <div
                            ref={outlinePane}
                            className="tree-outline"
                            style={
                                {
                                    "--outline-split":
                                        Number(ext.settings.outlineSplit) *
                                            100 +
                                        "%",
                                } as React.CSSProperties
                            }
                        >
                            <div className="file-tree">
                                {orderedTree(entries)
                                    .filter(
                                        (entry) =>
                                            ![...collapsed].some((folder) =>
                                                entry.path.startsWith(
                                                    folder + "/",
                                                ),
                                            ),
                                    )
                                    .map((entry) => (
                                        <button
                                            key={entry.path}
                                            disabled={busy}
                                            title={entry.path}
                                            style={{
                                                paddingLeft:
                                                    14 +
                                                    22 *
                                                        (entry.path.split("/")
                                                            .length -
                                                            1),
                                            }}
                                            className={
                                                (
                                                    entry.directory
                                                        ? entry.path === folder
                                                        : entry.path === file
                                                )
                                                    ? "file-selected"
                                                    : ""
                                            }
                                            onContextMenu={async (event) => {
                                                event.preventDefault();
                                                if (dirty && !(await save()))
                                                    return;
                                                setFileAction(entry);
                                            }}
                                            onClick={() => {
                                                if (entry.directory)
                                                    setCollapsed((current) => {
                                                        const copy = new Set(
                                                            current,
                                                        );
                                                        copy.has(entry.path)
                                                            ? copy.delete(
                                                                  entry.path,
                                                              )
                                                            : copy.add(
                                                                  entry.path,
                                                              );
                                                        return copy;
                                                    });
                                                void openFile(entry);
                                            }}
                                        >
                                            {entry.directory ? (
                                                <ChevronRight
                                                    size={14}
                                                    className={
                                                        collapsed.has(
                                                            entry.path,
                                                        )
                                                            ? "tree-chevron"
                                                            : "tree-chevron expanded"
                                                    }
                                                />
                                            ) : (
                                                <span className="tree-chevron-spacer" />
                                            )}
                                            {entry.directory ? (
                                                <Folder size={15} />
                                            ) : (
                                                <FileText size={15} />
                                            )}
                                            <span>
                                                {entry.path.split("/").pop()}
                                            </span>
                                        </button>
                                    ))}
                            </div>
                            <div
                                className="outline-divider"
                                role="separator"
                                aria-label={t(
                                    "Điều chỉnh outline",
                                    "Resize outline",
                                )}
                                aria-orientation="horizontal"
                                onPointerDown={resizeOutline}
                            />
                            <div className="outline-panel">
                                <div className="outline-heading">
                                    {localize("CẤU TRÚC TÀI LIỆU", uiLanguage)}
                                </div>
                                <div className="outline">
                                    {outline.length ? (
                                        outline.map((item, index) => (
                                            <button
                                                key={index}
                                                style={{
                                                    paddingLeft: item.sub
                                                        ? 28
                                                        : 15,
                                                }}
                                                onClick={() => {
                                                    view.current?.dispatch({
                                                        selection: {
                                                            anchor: item.start,
                                                        },
                                                        scrollIntoView: true,
                                                    });
                                                    view.current?.focus();
                                                }}
                                            >
                                                {item.title}
                                            </button>
                                        ))
                                    ) : (
                                        <p>
                                            {localize(
                                                "Thêm section để thấy cấu trúc.",
                                                uiLanguage,
                                            )}
                                        </p>
                                    )}
                                </div>
                            </div>
                        </div>
                        <div className="folder-target">
                            {localize("Thư mục:", uiLanguage)}
                            {folder || "/"}
                        </div>
                    </aside>
                    <div
                        className="workspace-sidepanel"
                        hidden={sidePanel !== "review"}
                    >
                        <ReviewPanel
                            key={project.id}
                            project={project.id}
                            file={file}
                            source={content}
                            language={uiLanguage}
                            editor={view}
                            draft={reviewDraft}
                            onDraft={setReviewDraft}
                            onClose={() => setSidePanel(null)}
                            openFile={async (path) => {
                                await openFile({
                                    path,
                                    directory: false,
                                    editable: true,
                                });
                            }}
                        />
                    </div>
                    <aside
                        className="workspace-sidepanel collaborator-chat"
                        hidden={sidePanel !== "chat"}
                        aria-label={t(
                            "Kênh chat cộng tác",
                            "Collaborator chat channel",
                        )}
                    >
                        <header>
                            <strong>
                                {t("Chat cộng tác", "Collaborator chat")}
                            </strong>
                            <button
                                aria-label={t("Đóng chat", "Close chat")}
                                onClick={() => setSidePanel(null)}
                            >
                                <X size={17} />
                            </button>
                        </header>
                        <div className="chat-empty">
                            <MessagesSquare size={42} />
                            <h3>{t("Chưa có tin nhắn", "No messages yet")}</h3>
                            <p>
                                {t(
                                    "Kênh trò chuyện cho các thành viên cùng dự án.",
                                    "A conversation channel for collaborators in this project.",
                                )}
                            </p>
                            <small>
                                {t(
                                    "Sẽ được bật khi tích hợp chat nhiều người dùng.",
                                    "Will be enabled when multiplayer chat is integrated.",
                                )}
                            </small>
                        </div>
                        <footer>
                            <textarea
                                disabled
                                aria-label={t(
                                    "Tin nhắn cộng tác",
                                    "Message collaborators",
                                )}
                                placeholder={t(
                                    "Chat nhiều người dùng chưa được kết nối",
                                    "Multiplayer chat is not connected yet",
                                )}
                            />
                        </footer>
                    </aside>
                    <aside
                        className="workspace-sidepanel ielts-sidepanel-wrapper"
                        hidden={sidePanel !== "ielts"}
                        aria-label={t(
                            "Bảng chấm điểm IELTS Writing",
                            "IELTS Writing Evaluation Panel",
                        )}
                    >
                        <IeltsGradingPanel
                            key={project.id}
                            project={project}
                            file={file}
                            source={content}
                            language={uiLanguage}
                            editor={view}
                            onClose={() => setSidePanel(null)}
                            onFileCreated={async (newPath) => {
                                await refreshFiles(newPath);
                                const entry = entries.find(
                                    (e) =>
                                        e.path === newPath ||
                                        e.path.endsWith("/" + newPath),
                                );
                                if (entry) {
                                    void openFile(entry);
                                }
                            }}
                        />
                    </aside>
                    <div
                        className="sidebar-divider"
                        hidden={!sidePanel}
                        role="separator"
                        aria-label={t(
                            "Điều chỉnh thanh thư mục",
                            "Resize file sidebar",
                        )}
                        aria-orientation="vertical"
                        onPointerDown={resizeSidebar}
                    />
                    <section
                        className={`workspace-content layout-${layout}`}
                        style={
                            {
                                "--editor-split":
                                    (Number(ext.settings.editorSplit) || 0.5) *
                                        100 +
                                    "%",
                            } as React.CSSProperties
                        }
                    >
                        {layout !== "pdf" && (
                            <section className="editor-column">
                                <div className="file-tab">
                                    <span>
                                        <FileText size={14} />
                                        {file}
                                        {dirty && <i />}
                                    </span>
                                    <small>UTF-8 · LaTeX</small>
                                </div>
                                <EditorToolbar
                                    editor={view}
                                    project={project.id}
                                    file={file}
                                    language={uiLanguage}
                                    readonly={ext.readonly}
                                    dirty={dirty}
                                    save={() => void save()}
                                    onError={setError}
                                    onComment={() => {
                                        const editor = view.current;
                                        if (!editor) return;
                                        const range =
                                            editor.state.selection.main;
                                        setReviewDraft({
                                            file,
                                            from: range.from,
                                            line: editor.state.doc.lineAt(
                                                range.from,
                                            ).number,
                                            quote: editor.state
                                                .sliceDoc(range.from, range.to)
                                                .slice(0, 20000),
                                        });
                                        setSidePanel("review");
                                    }}
                                />
                                <div className="editor-review-row">
                                    <div className="editor-area">
                                        <CodeMirror
                                            className="source-editor"
                                            value={content}
                                            height="100%"
                                            theme={
                                                ext.resolvedDark
                                                    ? "dark"
                                                    : "light"
                                            }
                                            readOnly={ext.readonly}
                                            extensions={[
                                                latex,
                                                reviewHighlights,
                                                ...(ext.settings.wordWrap
                                                    ? [EditorView.lineWrapping]
                                                    : []),
                                            ]}
                                            onChange={change}
                                            onCreateEditor={(editor) => {
                                                view.current = editor;
                                            }}
                                            onUpdate={(update) => {
                                                if (
                                                    update.selectionSet &&
                                                    ext.network.joined
                                                )
                                                    void action(
                                                        "network-cursor",
                                                        {
                                                            line: update.state.doc.lineAt(
                                                                update.state
                                                                    .selection
                                                                    .main.head,
                                                            ).number,
                                                            column:
                                                                update.state
                                                                    .selection
                                                                    .main.head -
                                                                update.state.doc.lineAt(
                                                                    update.state
                                                                        .selection
                                                                        .main
                                                                        .head,
                                                                ).from,
                                                        },
                                                    );
                                            }}
                                            basicSetup={{
                                                lineNumbers: true,
                                                foldGutter: true,
                                                autocompletion: true,
                                                closeBrackets: Boolean(
                                                    ext.settings.autoClose,
                                                ),
                                                searchKeymap: true,
                                            }}
                                        />
                                    </div>
                                </div>
                            </section>
                        )}
                        {layout === "split" && (
                            <div
                                className="split-divider"
                                role="separator"
                                aria-label={t(
                                    "Điều chỉnh editor/PDF",
                                    "Resize editor/PDF",
                                )}
                                aria-orientation="vertical"
                                onPointerDown={resizeSplit}
                            />
                        )}
                        {layout !== "editor" && (
                            <section className="preview-column">
                                <div className="compile-toolbar">
                                    <div className="inline-controls">
                                        <select
                                            aria-label={localize(
                                                "Engine biên dịch",
                                                uiLanguage,
                                            )}
                                            value={engine}
                                            disabled={busy}
                                            onChange={(event) => {
                                                setEngine(event.target.value);
                                                setStale(true);
                                            }}
                                        >
                                            {[
                                                "pdflatex",
                                                "xelatex",
                                                "lualatex",
                                            ].map((engine) => (
                                                <option key={engine}>
                                                    {engine}
                                                </option>
                                            ))}
                                        </select>
                                        <button
                                            className="primary compile-button"
                                            disabled={busy || ext.readonly}
                                            onClick={() => void build()}
                                        >
                                            {busy ? (
                                                <LoaderCircle
                                                    size={16}
                                                    className="spin"
                                                />
                                            ) : (
                                                <Play
                                                    size={15}
                                                    fill="currentColor"
                                                />
                                            )}
                                            {busy
                                                ? localize(
                                                      "Đang biên dịch",
                                                      uiLanguage,
                                                  )
                                                : localize(
                                                      "Biên dịch",
                                                      uiLanguage,
                                                  )}
                                        </button>
                                        {busy && (
                                            <button
                                                title={localize(
                                                    "Dừng biên dịch",
                                                    uiLanguage,
                                                )}
                                                onClick={() => void ext.stop()}
                                            >
                                                <Square size={15} />
                                            </button>
                                        )}
                                        {previewTab === "logs" ? (
                                            <button
                                                className="back-to-pdf-btn"
                                                title={t(
                                                    "Quay lại chế độ xem PDF",
                                                    "Back to PDF preview",
                                                )}
                                                onClick={() =>
                                                    setPreviewTab("pdf")
                                                }
                                            >
                                                {t(
                                                    "Quay lại PDF",
                                                    "Back to PDF",
                                                )}
                                            </button>
                                        ) : (
                                            <button
                                                className={`logs-toggle-btn ${logBadgeClass}`}
                                                title={t(
                                                    "Xem nhật ký biên dịch và lỗi",
                                                    "Logs and output files",
                                                )}
                                                aria-label={t(
                                                    "Nhật ký và lỗi",
                                                    "Logs and errors",
                                                )}
                                                onClick={async () => { if (!wordPreview.current || await wordPreview.current.flush()) setPreviewTab("logs"); }}
                                            >
                                                <div className="logs-doc-icon">
                                                    <svg
                                                        width="18"
                                                        height="20"
                                                        viewBox="0 0 18 20"
                                                        fill="none"
                                                        xmlns="http://www.w3.org/2000/svg"
                                                    >
                                                        <path
                                                            d="M3 2C2.44772 2 2 2.44772 2 3V17C2 17.5523 2.44772 18 3 18H15C15.5523 18 16 17.5523 16 17V7L11 2H3Z"
                                                            fill="#ffffff"
                                                            stroke="#b5c0cb"
                                                            strokeWidth="1.2"
                                                        />
                                                        <path
                                                            d="M11 2V7H16"
                                                            stroke="#b5c0cb"
                                                            strokeWidth="1.2"
                                                            strokeLinejoin="round"
                                                        />
                                                    </svg>
                                                    <span className="logs-badge-num">
                                                        {logBadgeCount}
                                                    </span>
                                                </div>
                                            </button>
                                        )}
                                    </div>
                                    <label className="check-row auto-compile">
                                        <input
                                            type="checkbox"
                                            checked={Boolean(
                                                ext.settings.autoCompile,
                                            )}
                                            onChange={(event) => {
                                                const autoCompile =
                                                    event.target.checked;
                                                ext.setSettings((value) => ({
                                                    ...value,
                                                    autoCompile,
                                                }));
                                                void action("settings", {
                                                    autoCompile,
                                                });
                                            }}
                                        />
                                        {t("Tự biên dịch", "Auto compile")}
                                    </label>
                                    <button
                                        title="SyncTeX: source → PDF"
                                        disabled={
                                            stale || dirty || busy || !snapshot
                                        }
                                        onClick={() => void ext.forward()}
                                    >
                                        <ArrowRight size={17} />
                                    </button>
                                    <div className="download-options">
                                        <button
                                            title={t("Tải xuống", "Download")}
                                            aria-label={t(
                                                "Tải xuống",
                                                "Download",
                                            )}
                                            aria-expanded={downloadMenu}
                                            disabled={wordExporting}
                                            onClick={() =>
                                                setDownloadMenu(
                                                    (value) => !value,
                                                )
                                            }
                                        >
                                            <Download size={18} />
                                        </button>
                                        {downloadMenu && (
                                            <div
                                                className="download-menu"
                                                role="menu"
                                                onKeyDown={(e) => {
                                                    if (e.key === "Escape")
                                                        setDownloadMenu(false);
                                                }}
                                            >
                                                <button
                                                    role="menuitem"
                                                    disabled={
                                                        stale ||
                                                        dirty ||
                                                        !snapshot ||
                                                        busy
                                                    }
                                                    onClick={() => {
                                                        setDownloadMenu(false);
                                                        void exportPdf();
                                                    }}
                                                >
                                                    Export PDF (.pdf)
                                                </button>
                                                <button
                                                    role="menuitem"
                                                    disabled={
                                                        busy ||
                                                        stale ||
                                                        dirty ||
                                                        !snapshot ||
                                                        wordExporting
                                                    }
                                                    title={t(
                                                        "Chuyển PDF bằng bộ chuyển đổi gốc ilovepdf-python",
                                                        "Convert PDF using the original ilovepdf-python converter",
                                                    )}
                                                    onClick={() => {
                                                        setDownloadMenu(false);
                                                        void exportWord();
                                                    }}
                                                >
                                                    Export Word (.docx)
                                                </button>
                                            </div>
                                        )}
                                    </div>
                                </div>
                                {previewTab === "logs" ? (
                                    <LogsView
                                        project={project}
                                        file={file}
                                        issues={ext.issues}
                                        logs={logs}
                                        setLogs={setLogs}
                                        logPaneRef={logPane}
                                        busy={busy}
                                        uiLanguage={uiLanguage}
                                        onClearCache={async () => {
                                            try {
                                                await action("clean", {
                                                    project: project.id,
                                                });
                                                setNotice(
                                                    t(
                                                        "Đã dọn cache.",
                                                        "Build cache cleaned.",
                                                    ),
                                                );
                                            } catch (error) {
                                                setError(text(error));
                                            }
                                        }}
                                        onJumpToLine={(targetFile, line) => {
                                            if (
                                                targetFile &&
                                                targetFile !== file
                                            ) {
                                                const entry = entries.find(
                                                    (e) =>
                                                        e.path === targetFile ||
                                                        e.path.endsWith(
                                                            "/" + targetFile,
                                                        ),
                                                );
                                                if (entry) {
                                                    void openFile(entry);
                                                }
                                            }
                                            if (line > 0 && view.current) {
                                                try {
                                                    const ed = view.current;
                                                    const docLine =
                                                        ed.state.doc.line(
                                                            Math.min(
                                                                line,
                                                                ed.state.doc
                                                                    .lines,
                                                            ),
                                                        );
                                                    ed.dispatch({
                                                        selection: {
                                                            anchor: docLine.from,
                                                        },
                                                        scrollIntoView: true,
                                                    });
                                                    ed.focus();
                                                } catch {}
                                            }
                                        }}
                                        onBackToPdf={() => setPreviewTab("pdf")}
                                    />
                                ) : (
                                    <>
                                        <div
                                            className="preview-tabs"
                                            role="tablist"
                                            aria-label={t(
                                                "Định dạng xem trước",
                                                "Preview format",
                                            )}
                                        >
                                            <button
                                                role="tab"
                                                aria-selected={
                                                    previewTab === "pdf"
                                                }
                                                onClick={async () => { if (!wordPreview.current || await wordPreview.current.flush()) setPreviewTab("pdf"); }}
                                            >
                                                PDF preview
                                            </button>
                                            <button
                                                role="tab"
                                                aria-selected={
                                                    previewTab === "word"
                                                }
                                                onClick={() =>
                                                    setPreviewTab("word")
                                                }
                                            >
                                                Word preview
                                            </button>
                                        </div>
                                        <div
                                            className="preview-format-panel"
                                            hidden={previewTab !== "pdf"}
                                        >
                                            <PdfViewer
                                                data={pdf}
                                                stale={stale || dirty}
                                                geometry={ext.geometry}
                                                target={ext.target}
                                                onInverse={ext.inverse}
                                                language={uiLanguage}
                                            />
                                        </div>
                                        <div
                                            className="preview-format-panel"
                                            hidden={previewTab !== "word"}
                                        >
                                            <WordPreview
                                                key={project.id}
                                                ref={wordPreview}
                                                project={project.id}
                                                language={uiLanguage}
                                                sourceDirty={dirty}
                                                snapshot={snapshot}
                                                onCompile={() => void build()}
                                                active={previewTab === "word"}
                                                sourceSignal={
                                                    file + content + snapshot
                                                }
                                                compiled={
                                                    !!snapshot &&
                                                    !stale &&
                                                    !dirty
                                                }
                                                onSynced={async () => {
                                                    if (dirty) return;
                                                    const ticket =
                                                        revision.current;
                                                    const updated = await json<{
                                                        content: string;
                                                    }>(
                                                        `/projects/${project.id}/file?path=${encodeURIComponent(file)}`,
                                                    );
                                                    if (
                                                        ticket !==
                                                        revision.current
                                                    )
                                                        return;
                                                    if (
                                                        updated.content !==
                                                        savedText.current
                                                    ) {
                                                        revision.current += 1;
                                                        setContent(
                                                            updated.content,
                                                        );
                                                        savedText.current =
                                                            updated.content;
                                                        setDirty(false);
                                                        setStale(true);
                                                        setNotice(
                                                            "Đã đồng bộ Word sang LaTeX. Compile lại để cập nhật PDF.",
                                                        );
                                                    }
                                                }}
                                                saveSource={async () =>
                                                    !dirty || !!(await save())
                                                }
                                            />
                                        </div>
                                    </>
                                )}
                            </section>
                        )}
                    </section>
                </div>
            )}
            <div className="statusbar">
                <span>
                    <span className={`status-dot ${busy ? "busy" : ""}`} />
                    {localize(notice, uiLanguage)}
                </span>
                <span>
                    {project
                        ? `${file} · ${dirty ? localize("Chưa lưu", uiLanguage) : localize("Đã lưu", uiLanguage)}`
                        : localize(
                              "Tài liệu của bạn · Lưu trên máy",
                              uiLanguage,
                          )}
                </span>
            </div>
            {ext.panel === "settings" && (
                <SettingsPanel
                    settings={ext.settings}
                    onClose={() => {
                        ext.setPanel(null);
                        void refreshProjects();
                    }}
                    onDone={(value) => {
                        ext.setSettings(value);
                        void refreshProjects();
                    }}
                />
            )}
            {ext.panel === "profile" && ext.session && (
                <ProfilePanel
                    session={ext.session}
                    language={uiLanguage}
                    onDone={ext.authenticated}
                    onClose={() => ext.setPanel(null)}
                    onLogout={() => void ext.logout()}
                />
            )}
            {ext.panel?.startsWith("add-") && project && (
                <AddFilesPanel
                    project={project.id}
                    folder={folder}
                    language={uiLanguage}
                    initial={ext.panel.slice(4)}
                    onClose={() => ext.setPanel(null)}
                    onDone={(path) => void refreshFiles(path)}
                />
            )}
            {ext.panel === "network" && project && (
                <NetworkPanel
                    project={project.id}
                    language={uiLanguage}
                    name={ext.session?.name || ""}
                    state={ext.network}
                    onDone={ext.acceptNetwork}
                    onClose={() => ext.setPanel(null)}
                />
            )}
            {ext.manage && (
                <ProjectMenu
                    project={ext.manage}
                    language={uiLanguage}
                    onClose={() => ext.setManage(null)}
                    onDone={() => {
                        ext.setManage(null);
                        void refreshProjects();
                    }}
                />
            )}
            {fileAction && project && (
                <FileActionsPanel
                    project={project.id}
                    entry={fileAction}
                    language={uiLanguage}
                    onClose={() => setFileAction(null)}
                    onDone={() =>
                        void refreshFiles(
                            fileAction &&
                                (file === fileAction.path ||
                                    file.startsWith(fileAction.path + "/"))
                                ? project.main
                                : undefined,
                        )
                    }
                    onAdd={(path) => {
                        setFolder(path);
                        setFileAction(null);
                        ext.setPanel("add-file");
                    }}
                />
            )}
            {dialog && (
                <div
                    className="modal-backdrop"
                    onKeyDown={(event) => {
                        if (event.key === "Escape" && !working) setDialog(null);
                    }}
                >
                    <section
                        className={`modal ${dialog === "project" ? "project-modal" : ""}`}
                        role="dialog"
                        aria-modal="true"
                        aria-labelledby="dialog-heading"
                    >
                        <header>
                            <div>
                                <p className="eyebrow">
                                    {localize(
                                        "KHÔNG GIAN CHO TÀI LIỆU MỚI",
                                        uiLanguage,
                                    )}
                                </p>
                                <h2 id="dialog-heading">
                                    {dialog === "project"
                                        ? localize("Tạo dự án mới", uiLanguage)
                                        : dialog === "folder"
                                          ? localize("Thêm thư mục", uiLanguage)
                                          : localize("Thêm tệp", uiLanguage)}
                                </h2>
                            </div>
                            <button
                                title={localize("Đóng", uiLanguage)}
                                disabled={working}
                                onClick={() => setDialog(null)}
                            >
                                <X size={20} />
                            </button>
                        </header>
                        <form onSubmit={(event) => void submit(event)}>
                            <label>
                                {dialog === "project"
                                    ? localize("Tên dự án", uiLanguage)
                                    : localize(
                                          "Đường dẫn trong dự án",
                                          uiLanguage,
                                      )}
                                <input
                                    autoFocus
                                    required
                                    maxLength={dialog === "project" ? 120 : 512}
                                    value={name}
                                    placeholder={
                                        dialog === "project"
                                            ? localize(
                                                  "Ví dụ: Luận văn tốt nghiệp",
                                                  uiLanguage,
                                              )
                                            : "chapters/introduction.tex"
                                    }
                                    onChange={(event) =>
                                        setName(event.target.value)
                                    }
                                />
                            </label>
                            {dialog === "project" && (
                                <>
                                    <NewProjectExtras
                                        title={name}
                                        parent={parent}
                                        setParent={setParent}
                                        icon={icon}
                                        setIcon={setIcon}
                                        color={color}
                                        setColor={setColor}
                                        language={uiLanguage}
                                        sourceLanguage={language}
                                        onTitleChange={setName}
                                        onImported={(created) => {
                                            void refreshProjects();
                                            setDialog(null);
                                            void open(created);
                                        }}
                                        onExample={() => {
                                            setSelectedTemplate({
                                                id: "example",
                                                category: "internal",
                                                engine: "pdflatex",
                                                tags: [],
                                                title: {
                                                    vi: "Dự án ví dụ",
                                                    en: "Example project",
                                                },
                                                description: {
                                                    vi: "Ví dụ LaTeX có sẵn",
                                                    en: "A complete LaTeX example",
                                                },
                                            });
                                            setName(
                                                name ||
                                                    t(
                                                        "Dự án ví dụ",
                                                        "Example project",
                                                    ),
                                            );
                                        }}
                                    />
                                    <div className="template-filters">
                                        <div className="search-input">
                                            <Search size={15} />
                                            <input
                                                aria-label={localize(
                                                    "Tìm mẫu",
                                                    uiLanguage,
                                                )}
                                                placeholder={localize(
                                                    "Tìm mẫu tài liệu…",
                                                    uiLanguage,
                                                )}
                                                value={templateSearch}
                                                onChange={(event) =>
                                                    setTemplateSearch(
                                                        event.target.value,
                                                    )
                                                }
                                            />
                                        </div>
                                        <select
                                            aria-label={localize(
                                                "Nhóm mẫu",
                                                uiLanguage,
                                            )}
                                            value={category}
                                            onChange={(event) =>
                                                setCategory(event.target.value)
                                            }
                                        >
                                            <option value="">
                                                {localize(
                                                    "Tất cả nhóm",
                                                    uiLanguage,
                                                )}
                                            </option>
                                            {catalog.categories.map((group) => (
                                                <option
                                                    key={group.id}
                                                    value={group.id}
                                                >
                                                    {uiLanguage === "en"
                                                        ? group.en
                                                        : group.vi}
                                                </option>
                                            ))}
                                        </select>
                                        <select
                                            aria-label={t(
                                                "Nhãn mẫu",
                                                "Template tag",
                                            )}
                                            value={tag}
                                            onChange={(event) =>
                                                setTag(event.target.value)
                                            }
                                        >
                                            <option value="">
                                                {t("Tất cả nhãn", "All tags")}
                                            </option>
                                            {[
                                                ...new Set(
                                                    catalog.templates.flatMap(
                                                        (item) => item.tags,
                                                    ),
                                                ),
                                            ]
                                                .sort()
                                                .map((value) => (
                                                    <option key={value}>
                                                        {value}
                                                    </option>
                                                ))}
                                        </select>
                                        <select
                                            aria-label={localize(
                                                "Ngôn ngữ tài liệu",
                                                uiLanguage,
                                            )}
                                            value={language}
                                            onChange={(event) =>
                                                setLanguage(event.target.value)
                                            }
                                        >
                                            <option value="vi">
                                                {localize(
                                                    "Tiếng Việt",
                                                    uiLanguage,
                                                )}
                                            </option>
                                            <option value="en">English</option>
                                        </select>
                                    </div>
                                    <div className="template-grid">
                                        <button
                                            type="button"
                                            className={
                                                !selectedTemplate
                                                    ? "chosen"
                                                    : ""
                                            }
                                            onClick={() =>
                                                setSelectedTemplate(null)
                                            }
                                        >
                                            <FileText size={22} />
                                            <strong>
                                                {localize(
                                                    "Dự án trống",
                                                    uiLanguage,
                                                )}
                                            </strong>
                                            <span>
                                                {localize(
                                                    "Bắt đầu từ trang giấy đầu tiên.",
                                                    uiLanguage,
                                                )}
                                            </span>
                                        </button>
                                        {templates.map((item) => (
                                            <button
                                                type="button"
                                                key={item.id}
                                                className={
                                                    selectedTemplate?.id ===
                                                    item.id
                                                        ? "chosen"
                                                        : ""
                                                }
                                                onClick={() =>
                                                    setSelectedTemplate(item)
                                                }
                                            >
                                                <BookOpen size={22} />
                                                <strong>
                                                    {language === "vi"
                                                        ? item.title.vi
                                                        : item.title.en}
                                                </strong>
                                                <span>
                                                    {language === "vi"
                                                        ? item.description.vi
                                                        : item.description.en}
                                                </span>
                                                <small>{item.engine}</small>
                                            </button>
                                        ))}
                                    </div>
                                    {preflight &&
                                        preflight.missing.length > 0 && (
                                            <div
                                                className="preflight-warning"
                                                role="alert"
                                            >
                                                <strong>
                                                    {t(
                                                        "Thiếu công cụ / gói LaTeX:",
                                                        "Missing tools / packages:",
                                                    )}
                                                </strong>
                                                <p>
                                                    {preflight.missing
                                                        .map(
                                                            (item) => item.name,
                                                        )
                                                        .join(", ")}
                                                </p>
                                                {preflight.bundled ? (
                                                    <button
                                                        className="primary"
                                                        type="button"
                                                        disabled={installing}
                                                        onClick={async () => {
                                                            setInstalling(true);
                                                            try {
                                                                setPreflight(
                                                                    await json<
                                                                        typeof preflight
                                                                    >(
                                                                        "/install",
                                                                        {
                                                                            method: "POST",
                                                                            body: JSON.stringify(
                                                                                {
                                                                                    template:
                                                                                        selectedTemplate?.id ||
                                                                                        "blank",
                                                                                    confirmed: true,
                                                                                },
                                                                            ),
                                                                        },
                                                                    ),
                                                                );
                                                            } catch (error) {
                                                                setError(
                                                                    text(error),
                                                                );
                                                            } finally {
                                                                setInstalling(
                                                                    false,
                                                                );
                                                            }
                                                        }}
                                                    >
                                                        {t(
                                                            "Đồng ý tải và cài vào TinyTeX đi kèm",
                                                            "Download and install into bundled TinyTeX",
                                                        )}
                                                    </button>
                                                ) : (
                                                    <p>
                                                        {t(
                                                            "Cài gói bằng trình quản lý TeX của máy rồi thử lại.",
                                                            "Install these packages with your TeX manager, then retry.",
                                                        )}
                                                    </p>
                                                )}
                                                {installing && (
                                                    <button
                                                        type="button"
                                                        onClick={() =>
                                                            void json(
                                                                "/install/cancel",
                                                                {
                                                                    method: "POST",
                                                                },
                                                            )
                                                        }
                                                    >
                                                        {t(
                                                            "Hủy cài đặt",
                                                            "Cancel installation",
                                                        )}
                                                    </button>
                                                )}
                                            </div>
                                        )}
                                </>
                            )}
                            <footer>
                                <span>
                                    {dialog === "project"
                                        ? localize(
                                              "Dữ liệu được tạo trên máy của bạn.",
                                              uiLanguage,
                                          )
                                        : localize(
                                              "Đường dẫn tính từ thư mục gốc dự án.",
                                              uiLanguage,
                                          )}
                                </span>
                                <button
                                    type="button"
                                    className="secondary"
                                    disabled={working}
                                    onClick={() => setDialog(null)}
                                >
                                    {localize("Hủy", uiLanguage)}
                                </button>
                                <button
                                    className="primary"
                                    disabled={
                                        working || installing || !name.trim()
                                    }
                                >
                                    {working && (
                                        <LoaderCircle
                                            className="spin"
                                            size={16}
                                        />
                                    )}
                                    {dialog === "project"
                                        ? localize("Tạo dự án", uiLanguage)
                                        : localize("Tạo", uiLanguage)}
                                    <ArrowRight size={15} />
                                </button>
                            </footer>
                        </form>
                    </section>
                </div>
            )}
        </main>
    );
}
