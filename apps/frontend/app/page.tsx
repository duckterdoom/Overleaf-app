"use client";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import CodeMirror from "@uiw/react-codemirror";
import { StreamLanguage } from "@codemirror/language";
import { stex } from "@codemirror/legacy-modes/mode/stex";
import { EditorView, type ViewUpdate } from "@codemirror/view";
import {
    ArrowLeft,
    ArrowRight,
    BookOpen,
    Braces,
    Check,
    ChevronRight,
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
    House,
    List,
    LoaderCircle,
    Plus,
    Search,
    Settings2,
    Square,
    Terminal,
    UsersRound,
    X,
} from "lucide-react";
import PdfViewer from "../components/editor/PdfViewer";
import WordPreview from "../components/editor/WordDocxPreview";
import WritingWorkspace from "../components/writing/WritingWorkspace";
import DashboardOverview from "../components/workspace/DashboardOverview";
import WorkspaceNavigation, {type WritingSection} from "../components/workspace/WorkspaceNavigation";
import { WritingProjectContext } from "../lib/writing/context";
import type { WordPreviewHandle } from "../lib/word-preview";
import {
    bootstrap,
    isServerStorage,
    compile,
    json,
    request,
    type Catalog,
    type Entry,
    type Project,
    type Template,
    type PageGeometry,
} from "../lib/api";

import AuthView from "../components/auth/AuthView";
import RoleSelection from "../components/auth/RoleSelection";
import SettingsPanel from "../components/workspace/SettingsPanel";
import ProfilePanel from "../components/auth/ProfilePanel";
import HistoryPanel from "../components/editor/HistoryPanel";
import EditorToolbar from "../components/editor/EditorToolbar";
import DocumentOutline from "../components/editor/DocumentOutline";
import AddFilesPanel from "../components/editor/AddFilesPanel";
import NetworkPanel from "../components/editor/NetworkPanel";
import SharePanel from "../components/editor/SharePanel";
import TemplateLibrary from "../components/projects/TemplateLibrary";
import ProjectLibrary from "../components/projects/ProjectLibrary";
import CompileOptions, { PreviewFormatMenu } from "../components/editor/CompileOptions";
import NewProjectExtras from "../components/projects/NewProjectExtras";
import FileActionsPanel from "../components/editor/FileActionsPanel";
import LogsView, { parseAllLogs } from "../components/editor/LogsView";
import useParity from "../lib/useParity";
import { action, native, translator, type Activity } from "../lib/parity";
import { localize } from "../lib/i18n";
import { orderedTree } from "../lib/fileTree";
import ReviewPanel, { type ReviewAnchor } from "../components/editor/ReviewPanel";
import { reviewHighlights } from "../lib/reviewHighlights";
import usePresence, { presenceHighlights } from "../lib/usePresence";
import PresenceAvatars from "../components/editor/PresenceAvatars";
import { LiveSync } from "../lib/liveSync";
const latex = StreamLanguage.define(stex);
const localSelectionVisibility = EditorView.editorAttributes.compute(['selection'], state => ({
    'data-local-selection': state.selection.ranges.some(range => !range.empty) ? 'true' : 'false',
}));
type Dialog = "project" | "file" | "folder" | null;
type DocumentResult = {
    pdf: Uint8Array;
    snapshot: string;
    stale: boolean;
    logs: string;
    geometry: PageGeometry[];
};
const fold = (text: string) =>
    text
        .normalize("NFD")
        .replace(/[\u0300-\u036f]/g, "")
        .replace(/đ/g, "d")
        .toLowerCase();

export default function Workspace() {
    const [appMode,setAppMode]=useState<'editor'|'writing'>('editor');
    const [writingStart,setWritingStart]=useState<WritingSection>('assignments');
    const [writingEntry,setWritingEntry]=useState(0);
    const [homeTab,setHomeTab]=useState<'dashboard'|'documents'|'templates'>('dashboard');
    const [writingLocation, setWritingLocation] = useState('Task Writing');
    const writingLeave = useRef<(() => Promise<boolean>) | null>(null);
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
    const [search, setSearch] = useState("");
    const [projectView, setProjectView] = useState<"list" | "grid">("list");
    const [projectFilter, setProjectFilter] = useState<"all" | "shared">("all");
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
    const [parent, setParent] = useState("");
    const [fileAction, setFileAction] = useState<Entry | null>(null),
        [layoutMenu, setLayoutMenu] = useState(false);
    const [preflight, setPreflight] = useState<{
            missing: { name: string; package: string }[];
            bundled: boolean;
        } | null>(null),
        [installing, setInstalling] = useState(false);
    const [collapsed, setCollapsed] = useState<Set<string>>(new Set());
    const [sidePanel, setSidePanel] = useState<
        "files" | "review" | "chat" | null
    >("files");
    const [reviewDraft, setReviewDraft] = useState<ReviewAnchor | null>(null);
    const [previewTab, setPreviewTab] = useState<"pdf" | "word" | "logs">(
        "pdf",
    );
    const [downloadMenu, setDownloadMenu] = useState(false);
    const [wordExporting, setWordExporting] = useState(false);
    const [sourceTabs, setSourceTabs] = useState<string[]>([]);
    const [fileLoading, setFileLoading] = useState(false);
    const [compileTarget, setCompileTarget] = useState("");
    const results = useRef<Record<string, DocumentResult>>({});
    const positions = useRef<
        Record<
            string,
            { anchor: number; head: number; top: number; left: number }
        >
    >({});
    const wordPreview = useRef<WordPreviewHandle | null>(null);
    useEffect(() => {
        setSidePanel("files");
        setReviewDraft(null);
        setPreviewTab("pdf");
        setDownloadMenu(false);
    }, [project?.id]);
    useEffect(() => {
        setSourceTabs((tabs) =>
            tabs.filter(
                (path) =>
                    path === file ||
                    entries.some(
                        (entry) => !entry.directory && entry.path === path,
                    ),
            ),
        );
    }, [entries, file]);
    const outlinePane = useRef<HTMLDivElement>(null);
    const outlineCursor = useRef<(position: number) => void>(() => {});
    const revision = useRef(0),
        view = useRef<EditorView | null>(null),
        logPane = useRef<HTMLPreElement>(null);
    const savedText = useRef("");
    const liveRef = useRef<LiveSync | null>(null);
    if (!liveRef.current) liveRef.current = new LiveSync({
        saved: () => savedText.current, dirty: () => false, synced: () => {}, error: setError,
    });
    const live = liveRef.current;
    live.callbacks = {
        saved: () => savedText.current, dirty: () => dirty,
        synced: (value, pending) => {
            savedText.current = value;
            if (view.current) setContent(view.current.state.doc.toString());
            setDirty(pending);
            setStale(true);
            for (const result of Object.values(results.current)) result.stale = true;
        },
        error: setError,
    };
    const buildInFlight = useRef(false);
    const fileOperation = useRef(0);
    const openingFile = useRef(false);
    const text = (message: unknown) =>
        message instanceof Error ? message.message : String(message);
    useEffect(() => {
        void bootstrap()
            .then(() =>
                Promise.all([
                    json<Project[]>("/projects"),
                    json<Catalog>("/templates"),
                    json<{ mode: string }>("/health"),
                ]),
            )
            .then(([projects, catalog, health]) => {
                document.title = "Latex";
                setProjects(projects);
                setCatalog({...catalog,
                    categories:catalog.categories.filter(category=>category.id!=='ielts'),
                    templates:catalog.templates.filter(template=>template.category!=='ielts')});
                setReady(true);
                setNotice(isServerStorage() ? "Web workspace · Sẵn sàng" : "Local workspace · Sẵn sàng");
            })
            .catch((error) => {
                setError(text(error));
                setNotice(
                    "Không kết nối được backend. Khởi chạy lại local web hoặc ứng dụng desktop.",
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
        if (isServerStorage()) return (!dirty && !live.pending) || live.flush();
        const ticket = revision.current;
        try {
            await json(`/projects/${project.id}/file`, {
                method: "PUT",
                body: JSON.stringify({ path: file, content, expected: savedText.current }),
            });
            savedText.current = content;
            await action("editing", {
                project: project.id,
                dirty: ticket !== revision.current,
            });
            void refreshActivity();
            if (ticket === revision.current) {
                setDirty(false);
                setNotice(isServerStorage() ? "Đã lưu thay đổi trên server." : "Đã lưu thay đổi trên máy.");
            }
            return ticket === revision.current;
        } catch (error) {
            setError(text(error));
            return false;
        }
    }
    useEffect(() => {
        const handler = (event: KeyboardEvent) => {
            if(appMode==='writing')return;
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
        if (wordPreview.current && !(await wordPreview.current.flush())) return;
        fileOperation.current++;
        try {
            await ext.leaveBeforeOpen(project.id);
            const [entries, source] = await Promise.all([
                json<Entry[]>(`/projects/${project.id}/files`),
                json<{ content: string }>(
                    `/projects/${project.id}/file?path=${encodeURIComponent(project.main)}`,
                ),
            ]);
            setProject(project);
            results.current = {};
            positions.current = {};
            setSourceTabs([project.main]);
            setFileLoading(false);
            openingFile.current = false;
            setCompileTarget(project.main);
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
            setNotice(isServerStorage() ? "Dữ liệu được lưu trên server." : "Dữ liệu được lưu trên máy của bạn.");
        } catch (error) {
            setError(text(error));
        }
    }
    async function openFile(entry: Entry) {
        if (busy || wordExporting) return;
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
        if (wordPreview.current && !(await wordPreview.current.flush())) return;
        const operation = ++fileOperation.current;
        const sourceRevision = revision.current;
        openingFile.current = true;
        setFileLoading(true);
        try {
            const source = await json<{ content: string }>(
                `/projects/${project!.id}/file?path=${encodeURIComponent(entry.path)}`,
            );
            if (operation !== fileOperation.current) return;
            if (buildInFlight.current) return;
            if (revision.current !== sourceRevision) return;
            const editor = view.current;
            if (editor && file)
                positions.current[file] = {
                    anchor: editor.state.selection.main.anchor,
                    head: editor.state.selection.main.head,
                    top: editor.scrollDOM.scrollTop,
                    left: editor.scrollDOM.scrollLeft,
                };
            setFile(entry.path);
            revision.current++;
            setSourceTabs((tabs) =>
                tabs.includes(entry.path) ? tabs : [...tabs, entry.path],
            );
            setContent(source.content);
            savedText.current = source.content;
            setDirty(false);
            if (
                /\.tex$/i.test(entry.path)
            )
                activateTarget(entry.path);
            setFolder(
                entry.path.includes("/")
                    ? entry.path.slice(0, entry.path.lastIndexOf("/"))
                    : "",
            );
            return true;
        } catch (error) {
            setError(text(error));
        } finally {
            if (operation === fileOperation.current) {
                openingFile.current = false;
                setFileLoading(false);
            }
        }
    }
    function change(value: string) {
        if (ext.readonly) return;
        if (!live.applying) ext.changed(value, content);
        revision.current++;
        setContent(value);
        setDirty(value !== savedText.current);
        setStale(true);
        for (const result of Object.values(results.current))
            result.stale = true;
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
                        icon: "file",
                        color: "blue",
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
            openingFile.current ||
            busy ||
            buildInFlight.current ||
            (ext.readonly && project.permission !== "viewer") ||
            ext.detached
        )
            return;
        ext.buildStarted();
        buildInFlight.current = true;
        setBusy(true);
        if (wordPreview.current && !(await wordPreview.current.flush())) {
            buildInFlight.current = false;
            setBusy(false);
            setNotice("Chưa đồng bộ được Word sang LaTeX. Kiểm tra thông báo trong Word preview.");
            return;
        }
        if (!(await save())) {
            buildInFlight.current = false;
            setBusy(false);
            return;
        }
        const ticket = revision.current;
        setBusy(true);
        setStale(true);
        setError("");
        setLogs(`[BUILD] ${engine} | ${compileTarget}\n`);
        setNotice("Đang biên dịch…");
        let complete: Promise<void> = Promise.resolve();
        try {
            await compile(
                project.id,
                engine,
                (event) => {
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
                            setPdf(
                                new Uint8Array(await response.arrayBuffer()),
                            );
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
                },
                compileTarget,
            );
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
        if (
            !project ||
            !snapshot ||
            stale ||
            dirty ||
            busy ||
            openingFile.current
        )
            return;
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
                anchor.download = (
                    compileTarget.split("/").pop() || "main.tex"
                ).replace(/\.tex$/i, ".pdf");
                anchor.click();
                setTimeout(() => URL.revokeObjectURL(url), 1000);
            }
        } catch (error) {
            setError(text(error));
            setStale(true);
        }
    }
    async function exportWord() {
        if (
            !project ||
            !snapshot ||
            stale ||
            dirty ||
            busy ||
            openingFile.current ||
            wordExporting
        )
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
                anchor.download = (
                    compileTarget.split("/").pop() || "main.tex"
                ).replace(/\.tex$/i, ".docx");
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
        compileTarget,
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
        setCompileTarget,
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
        live,
    });
    const presence = usePresence(project?.id, file, ext.session?.id,
        ready && isServerStorage() && !!ext.session && !ext.readonly && !!project, view, live, appMode, writingLocation);
    const editorHandlers = useRef({ change, update: (_: ViewUpdate) => {} });
    editorHandlers.current = { change, update: (update) => {
        if (update.selectionSet || update.docChanged) outlineCursor.current(update.state.selection.main.head);
        if (update.docChanged) live.changed();
        if (update.selectionSet || update.docChanged || update.focusChanged) presence.update();
        if (update.selectionSet && ext.network.joined) {
            const head = update.state.selection.main.head;
            const line = update.state.doc.lineAt(head);
            void action('network-cursor', { line: line.number, column: head - line.from });
        }
    }};
    const onEditorChange = useCallback((value: string) => editorHandlers.current.change(value), []);
    const onEditorUpdate = useCallback((update: ViewUpdate) => editorHandlers.current.update(update), []);
    // Presence snapshots must not reconfigure CodeMirror's mouse-selection
    // handlers in the middle of a drag.
    const editorExtensions = useMemo(() => [latex, reviewHighlights, presenceHighlights, localSelectionVisibility, live.extension,
        ...(ext.settings.wordWrap ? [EditorView.lineWrapping] : [])], [live, ext.settings.wordWrap]);
    const editorSetup = useMemo(() => ({ lineNumbers: true, foldGutter: true,
        autocompletion: true, closeBrackets: Boolean(ext.settings.autoClose), searchKeymap: true }), [ext.settings.autoClose]);
    useEffect(() => {
        if (compileTarget && pdf && snapshot)
            results.current[compileTarget] = {
                pdf,
                snapshot,
                stale,
                logs,
                geometry: ext.geometry,
            };
    }, [compileTarget, pdf, snapshot, stale, logs, ext.geometry]);
    function activateTarget(main: string) {
        if (main === compileTarget) return;
        const cached = results.current[main];
        setCompileTarget(main);
        setPdf(cached?.pdf || null);
        setSnapshot(cached?.snapshot || "");
        setStale(true);
        setLogs(cached?.logs || "");
        ext.setGeometry(cached?.geometry || []);
        ext.setIssues([]);
        setError("");
    }
    async function closeSource(path: string) {
        if (busy || openingFile.current || sourceTabs.length <= 1) return;
        if (path === file) {
            const next =
                sourceTabs[sourceTabs.indexOf(path) - 1] ||
                sourceTabs.find((item) => item !== path)!;
            const entry = entries.find((item) => item.path === next);
            if (!entry) return;
            if (!(await openFile(entry))) return;
        }
        setSourceTabs((tabs) => tabs.filter((item) => item !== path));
    }
    const uiLanguage = "en",
        t = translator(uiLanguage);
    const logEntries = useMemo(
        () => parseAllLogs(ext.issues, logs, compileTarget || file, uiLanguage),
        [ext.issues, logs, compileTarget, file, uiLanguage],
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
    // The server resolves ACLs before including shared projects in this list.
    const visibleProjects = projectFilter === "shared" ? projects.filter(item => item.shared) : projects;
    const filtered = visibleProjects.filter((item) =>
        fold(item.title+' '+(item.tags||[]).join(' ')).includes(fold(search)),
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
    if (ext.session.needsRole)
        return <main data-desktop-ready="true"><RoleSelection session={ext.session} language={uiLanguage} onDone={ext.authenticated} onLogout={ext.logout} /></main>;
    async function enterWriting(screen:WritingSection='assignments'){
        if(busy||working)return;
        if(appMode==='writing'&&writingLeave.current&&!(await writingLeave.current()))return;
        if(wordPreview.current&&!(await wordPreview.current.flush()))return;
        if(project&&(dirty||live.pending)&&!(await save()))return;
        view.current=null;
        setWritingStart(screen);setWritingEntry(n=>n+1);
        setAppMode('writing');
    }

    if (project && ext.panel === "history")
        return (
            <HistoryPanel
                project={project.id}
                language={uiLanguage}
                locked={ext.network.joined}
                onClose={ext.closeHistory}
                onRestore={(path, value) => {
                    setFile(path);
                    setSourceTabs((tabs) =>
                        tabs.includes(path) ? tabs : [...tabs, path],
                    );
                    if (
                        /\.tex$/i.test(path)
                    )
                        activateTarget(path);
                    setContent(value);
                    savedText.current = value;
                    revision.current++;
                    setDirty(false);
                    setStale(true);
                    ext.closeHistory();
                }}
            />
        );
    async function leaveWriting() {
        return appMode !== 'writing' || !writingLeave.current || await writingLeave.current();
    }
    async function goHome(destination:'dashboard'|'documents'|'templates'=project?'documents':'dashboard') {
        if(working)return false;
        if(wordPreview.current&&!(await wordPreview.current.flush()))return false;
        if(project&&dirty&&!(await save()))return false;
        if(!(await leaveWriting()))return false;
        if(project){
            await ext.leaveBeforeOpen("");
            setProject(null);
            await refreshProjects();
        }
        setHomeTab(destination);
        setAppMode('editor');
        return true;
    }
    async function openProjectLibrary(filter:'all'|'shared') {
        if(!(await goHome('documents')))return;
        setProjectFilter(filter);setSearch('');setHomeTab('documents');
    }
    async function showTemplateLibrary() {
        if(!(await goHome('templates')))return;
        setDialog(null);setHomeTab('templates');
    }
    async function createFromTemplate(template:Template,documentLanguage:string) {
        if(working)return;
        setWorking(true);
        try {
            const created=await json<Project>('/projects',{method:'POST',body:JSON.stringify({title:template.title.en,template:template.id,language:documentLanguage,icon:'file',color:'blue'})});
            await refreshProjects();
            await open(created);
        } finally {setWorking(false);}
    }
    async function selectWorkspacePanel(panel: 'files' | 'review' | 'chat') {
        if (!(await leaveWriting())) return;
        setSidePanel(appMode === 'writing' ? panel : sidePanel === panel ? null : panel);
        setAppMode('editor');
    }
    const shellNavigation={
        onHome:()=>{void goHome('dashboard');},
        onProjects:(filter:'all'|'shared')=>{void openProjectLibrary(filter);},
        onTemplates:()=>{void showTemplateLibrary();},
        projectCount:projects.length,sharedCount:projects.filter(item=>item.shared).length,
        templateOpen:homeTab==='templates'&&appMode==='editor'&&!project,
    };
    const projectHeaderTools = project ? <div className="editor-project-tools">
                        <span className="editor-project-title" title={project.title}>{project.title}</span>
                        {isServerStorage() && !ext.readonly && <PresenceAvatars project={project.id}
                            peers={presence.peers} connected={presence.connected} language={uiLanguage}
                            selfId={ext.session?.id} file={file} digest={presence.digest} />}
                        <button
                            className="quiet"
                            disabled={busy}
                            onClick={async () => {
                                if (
                                    !wordPreview.current ||
                                    (await wordPreview.current.flush())
                                )
                                    if (await leaveWriting()) await ext.showHistory();
                            }}
                        >
                            <Clock3 size={17} />
                            {t("Lịch sử", "History")}
                        </button>
                        <button
                            className="quiet"
                            onClick={async () => {
                                if (appMode === 'writing' && isServerStorage()) {
                                    if (await leaveWriting()) ext.setPanel('share');
                                } else await ext.connect();
                            }}
                        >
                            {isServerStorage() ? <><UsersRound size={17} />{t("Chia sẻ", "Share")}</> : ext.network.joined
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
                                                if (
                                                    wordPreview.current &&
                                                    !(await wordPreview.current.flush())
                                                )
                                                    return;
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
</div> : null;
    const workspaceTools = <nav className="workspace-activity-bar" aria-label={t("Thanh công cụ workspace", "Workspace tools")}>
        {([
            ['files', t('Tệp và outline', 'Files and outline'), FileText],
            ['review', t('Nhận xét', 'Review'), MessageSquareText],
            ['chat', t('Chat cộng tác', 'Collaborator chat'), MessagesSquare],
        ] as const).map(([panel, label, Icon]) => <button key={panel} title={label} aria-label={label}
            aria-pressed={appMode === 'editor' && sidePanel === panel}
            onClick={() => void selectWorkspacePanel(panel)}><Icon size={21}/></button>)}
        <div className="workspace-account-tools"><button title="Settings" aria-label="Settings" onClick={()=>ext.setPanel('settings')}><Settings2 size={20}/></button><button className="avatar" title="Profile" aria-label="Profile" onClick={()=>ext.setPanel('profile')}>{ext.session.name.slice(0,1).toUpperCase()}</button></div>
    </nav>;
    return (
        <main
            data-desktop-ready={ready && ext.loaded}
            data-workspace={appMode==='writing'?'writing':project?'editor':'dashboard'}
            data-account-role={ext.session.role||'pending'}
            data-home-section={homeTab}
            className={`desktop-shell ${ext.focus ? "focus-mode" : ""} ${ext.detached ? "detached-pdf" : ""}`}
        >
            <header className={`app-header ${project ? "in-editor" : ""}`}>
                <button className="brand" type="button" aria-label="Home" title="Back to home" disabled={busy} onClick={()=>void goHome()}>
                    <span className="brand-mark">
                        <Leaf size={23} className="brand-leaf-icon"/><House size={23} className="brand-home-icon"/>
                    </span>
                    <strong>Latex</strong>
                </button>
                {projectHeaderTools}
                {!(project && appMode==='editor')&&<>
                <div className="header-spacer" />
                <span className="local-pill"><span />{isServerStorage()?'Web workspace':'Local workspace'}</span>
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
                </>}
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
            {appMode === 'writing' ? (
                <div className="workspace-grid writing-workspace-grid">
                    <WritingProjectContext.Provider value={project?.id || null}>
                        <WritingWorkspace key={`${project?.id||'account'}:${writingEntry}`} initialScreen={writingStart} shellNavigation={shellNavigation} session={ext.session} language={uiLanguage} beforeLeaveRef={writingLeave}
                            onLocationChange={setWritingLocation}
                            onOpenProject={async value=>{setAppMode('editor');await refreshProjects();await open(value);}}/>
                    </WritingProjectContext.Provider>
                </div>
            ) : !project ? (
                <div className="dashboard dashboard-home unified-workspace-home">
                    <WorkspaceNavigation tutor={ext.session.role==='tutor'} active={homeTab==='dashboard'?'dashboard':homeTab==='templates'?'templates':projectFilter==='shared'?'shared':'projects'} shell={shellNavigation} onWriting={id=>void enterWriting(id)}/>
                    <section className="projects-main">
                        {homeTab==='dashboard'&&<DashboardOverview session={ext.session} projects={projects}
                            onWriting={()=>void enterWriting()} onCourses={()=>void enterWriting('courses')}
                            onDocuments={()=>void openProjectLibrary('all')} onProject={value=>void open(value)}/>}
                        {homeTab==='templates'&&<TemplateLibrary catalog={catalog} onOpen={createFromTemplate} onNewProject={()=>{setSelectedTemplate(null);setName('');setPreflight(null);setDialog('project');}}/>}
                        {homeTab==='documents'&&<>
                        <div className="page-heading document-library-heading" id="document-library">
                            <div><p className="eyebrow">LATEX STUDIO</p><h2>Document library</h2><p>All your projects, shared documents and templates in one place.</p></div>
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
                        <div className="project-tools home-project-tools">
                            <strong className="library-scope">{projectFilter==='shared'?'Shared with you':'All projects'} <span className="project-count">{visibleProjects.length}</span></strong>
                            <div className="project-list-controls">
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
                            <div className="project-view-toggle" role="group" aria-label={t("Kiểu hiển thị dự án", "Project view")}>
                                <button title={t("Dạng danh sách", "List view")} aria-label={t("Dạng danh sách", "List view")} aria-pressed={projectView === "list"} onClick={() => setProjectView("list")}><List size={18} /></button>
                                <button title={t("Dạng lưới", "Grid view")} aria-label={t("Dạng lưới", "Grid view")} aria-pressed={projectView === "grid"} onClick={() => setProjectView("grid")}><Grid2X2 size={18} /></button>
                            </div>
                            </div>
                        </div>
                        {filtered.length ? (
                            <ProjectLibrary projects={filtered} view={projectView} language={uiLanguage} onOpen={item=>void open(item)} onRefresh={refreshProjects}/>

                        ) : (
                            <div className="empty-projects">
                                <div>
                                    {projectFilter === "shared" ? <UsersRound size={28} strokeWidth={1.3} /> : <FolderPlus size={28} strokeWidth={1.3} />}
                                </div>
                                <h3>
                                    {projectFilter === "shared" ? t("Chưa có tài liệu được chia sẻ", "No shared documents yet") : search
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
                                    {projectFilter === "shared" ? t("Mở liên kết chia sẻ hoặc lời mời từ chủ sở hữu để thêm tài liệu vào đây.", "Open a sharing link or an invitation from the owner to add a document here.") : search
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

                        </>}
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
                    {workspaceTools}
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
                                disabled={ext.readonly}
                                onClick={() => {
                                    ext.setPanel("add-file");
                                }}
                            >
                                <FilePlus2 size={17} />
                            </button>
                            <button
                                title={localize("Thêm thư mục", uiLanguage)}
                                disabled={ext.readonly}
                                onClick={() => {
                                    ext.setPanel("add-folder");
                                }}
                            >
                                <FolderPlus size={17} />
                            </button>
                            <button
                                title={t("Tải lên", "Upload")}
                                disabled={ext.readonly}
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
                                                if (ext.readonly) return;
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
                            <DocumentOutline key={`${project.id}:${file}`} source={content}
                                editor={view} cursor={outlineCursor} language={uiLanguage} />
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
                            visible={sidePanel === 'review'}
                            readonly={ext.readonly}
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
                                <div
                                    className="document-tabs source-tabs"
                                    role="tablist"
                                    aria-label={t(
                                        "Các tệp đang mở",
                                        "Open source files",
                                    )}
                                >
                                    {sourceTabs.map((path) => (
                                        <div
                                            key={path}
                                            className={`document-tab ${file === path ? "active" : ""}`}
                                        >
                                            <button
                                                role="tab"
                                                aria-selected={file === path}
                                                title={path}
                                                disabled={busy}
                                                onClick={() => {
                                                    const entry = entries.find(
                                                        (item) =>
                                                            item.path === path,
                                                    );
                                                    if (entry)
                                                        void openFile(entry);
                                                }}
                                            >
                                                <FileText size={14} />
                                                <span>{path}</span>
                                                {file === path && dirty && (
                                                    <i className="unsaved-dot" />
                                                )}
                                            </button>
                                            {sourceTabs.length > 1 && (
                                                <button
                                                    className="close-document-tab"
                                                    aria-label={t(
                                                        `Đóng ${path}`,
                                                        `Close ${path}`,
                                                    )}
                                                    disabled={busy}
                                                    onClick={() =>
                                                        void closeSource(path)
                                                    }
                                                >
                                                    <X size={13} />
                                                </button>
                                            )}
                                        </div>
                                    ))}
                                    <small className="source-encoding">
                                        UTF-8 · LaTeX
                                    </small>
                                </div>
                                <EditorToolbar
                                    editor={view}
                                    project={project.id}
                                    file={file}
                                    language={uiLanguage}
                                    readonly={ext.readonly || fileLoading}
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
                                            key={`${project.id}:${file}`}
                                            className="source-editor"
                                            value={content}
                                            height="100%"
                                            theme={
                                                ext.resolvedDark
                                                    ? "dark"
                                                    : "light"
                                            }
                                            readOnly={
                                                ext.readonly || fileLoading
                                            }
                                            extensions={editorExtensions}
                                            onChange={onEditorChange}
                                            onCreateEditor={(editor) => {
                                                view.current = editor;
                                                presence.update();
                                                const saved =
                                                    positions.current[file];
                                                if (saved) {
                                                    editor.dispatch({
                                                        selection: {
                                                            anchor: Math.min(
                                                                saved.anchor,
                                                                editor.state.doc
                                                                    .length,
                                                            ),
                                                            head: Math.min(
                                                                saved.head,
                                                                editor.state.doc
                                                                    .length,
                                                            ),
                                                        },
                                                    });
                                                    requestAnimationFrame(
                                                        () => {
                                                            editor.scrollDOM.scrollTop =
                                                                saved.top;
                                                            editor.scrollDOM.scrollLeft =
                                                                saved.left;
                                                        },
                                                    );
                                                }
                                            }}
                                            onUpdate={onEditorUpdate}
                                            basicSetup={editorSetup}
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
                                        <CompileOptions busy={busy} disabled={busy || fileLoading || (ext.readonly && project.permission !== "viewer")}
                                            engine={engine} autoCompile={Boolean(ext.settings.autoCompile)} onCompile={()=>void build()}
                                            onEngine={value=>{setEngine(value);setStale(true);}}
                                            onAutoCompile={autoCompile=>{
                                                ext.setSettings(value=>({...value,autoCompile}));
                                                void action("settings",{autoCompile});
                                            }}/>
                                        {busy && (
                                            <button
                                                title={localize(
                                                    "Dừng biên dịch",
                                                    uiLanguage,
                                                )}
                                                onClick={() => void ext.stop()}
                                            >
                                                <Square size={13} />
                                            </button>
                                        )}
                                        <button
                                            className={`logs-toggle-btn ${logBadgeClass} ${previewTab === "logs" ? "active" : ""}`}
                                            title={t(
                                                "Xem nhật ký biên dịch và lỗi",
                                                "Logs and output files",
                                            )}
                                            aria-label={t(
                                                "Nhật ký và lỗi",
                                                "Logs and errors",
                                            )}
                                            onClick={async () => {
                                                if (previewTab === "logs") {
                                                    setPreviewTab("pdf");
                                                } else {
                                                    if (
                                                        !wordPreview.current ||
                                                        (await wordPreview.current.flush())
                                                    ) {
                                                        setPreviewTab("logs");
                                                    }
                                                }
                                            }}
                                        >
                                            <div className="logs-doc-icon">
                                                <svg
                                                    width="16"
                                                    height="18"
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
                                        <PreviewFormatMenu value={previewTab} viewer={project.permission === "viewer"}
                                            onChange={async value=>{
                                                if (!wordPreview.current || await wordPreview.current.flush()) setPreviewTab(value);
                                            }}/>
                                    </div>
                                    <div className="compile-toolbar-right">
                                        <button
                                            className="compile-action-btn"
                                            title="SyncTeX: source → PDF"
                                            disabled={
                                                stale ||
                                                dirty ||
                                                busy ||
                                                !snapshot
                                            }
                                            onClick={() => void ext.forward()}
                                        >
                                            <ArrowRight size={15} />
                                        </button>
                                        <div className="download-options">
                                            <button
                                                className="compile-action-btn"
                                                title={t(
                                                    "Tải xuống",
                                                    "Download",
                                                )}
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
                                                <Download size={15} />
                                            </button>
                                            {downloadMenu && (
                                                <div
                                                    className="download-menu"
                                                    role="menu"
                                                    onKeyDown={(e) => {
                                                        if (e.key === "Escape")
                                                            setDownloadMenu(
                                                                false,
                                                            );
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
                                                            setDownloadMenu(
                                                                false,
                                                            );
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
                                                            setDownloadMenu(
                                                                false,
                                                            );
                                                            void exportWord();
                                                        }}
                                                    >
                                                        Export Word (.docx)
                                                    </button>
                                                </div>
                                            )}
                                        </div>
                                    </div>
                                </div>
                                {previewTab === "logs" ? (
                                    <LogsView
                                        project={project}
                                        file={compileTarget || file}
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
                                                key={
                                                    project.id +
                                                    ":" +
                                                    compileTarget
                                                }
                                                ref={wordPreview}
                                                project={project.id}
                                                main={compileTarget}
                                                defaultMain={project.main}
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
                                                    // Native Word commits are broadcast as OT updates.
                                                    // Do not replace a collab document through React props;
                                                    // that would echo the remote edit as a new local edit.
                                                    if (live.active) {
                                                        setStale(true);
                                                        for (const result of Object.values(results.current)) result.stale = true;
                                                        setNotice("Đã đồng bộ Word sang LaTeX. Compile lại để cập nhật PDF.");
                                                        if (ext.settings.autoCompile) setTimeout(() => void build(), 0);
                                                        return;
                                                    }
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
                                                        if (ext.settings.autoCompile) setTimeout(() => void build(), 0);
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
            <div className="sr-only" role="status" aria-live="polite">{notice}</div>

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
                    beforeRoleChange={leaveWriting}
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
            {ext.panel === "share" && project && <SharePanel project={project} language={uiLanguage} onClose={() => ext.setPanel(null)} />}
            {fileAction && project && !ext.readonly && (
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
                                    <section className="new-project-template-choice">
                                        <div><strong>{selectedTemplate?.title.en||'Blank project'}</strong><p>Start with a blank document, import files above, or browse the template library.</p></div>
                                        <button type="button" onClick={()=>void showTemplateLibrary()}>Browse templates <ArrowRight size={15}/></button>
                                    </section>
                                    <label>Document language<select aria-label="New project document language" value={language} onChange={event=>setLanguage(event.target.value)}><option value="en">English</option><option value="vi">Vietnamese</option></select></label>
                                    {preflight &&
                                        preflight.missing.length > 0 && (
                                            <div
                                                className="preflight-warning"
                                                role="alert"
                                            >
                                                <strong>
                                                    {t(
                                                        isServerStorage() ? "Máy chủ chưa sẵn sàng biên dịch LaTeX" : "Thiếu công cụ / gói LaTeX:",
                                                        isServerStorage() ? "Server LaTeX environment is not ready" : "Missing tools / packages:",
                                                    )}
                                                </strong>
                                                {isServerStorage() && <p>{t("Bạn không cần cài LaTeX trên máy dùng trình duyệt. Môi trường biên dịch phải được chuẩn bị trên máy chủ. Nếu đang dev/test local, chạy lại launcher để tự chuẩn bị bộ TeX; nếu dùng web chung, báo người quản trị.", "You do not need LaTeX on your browser device. The compile environment must be prepared on the server. For local dev/test, rerun the launcher to prepare TeX; on a hosted site, contact the administrator.")}</p>}
                                                <details open={!isServerStorage()}><summary>{t("Chi tiết kiểm tra môi trường", "Environment check details")}</summary><p>
                                                    {preflight.missing
                                                        .map(
                                                            (item) => item.name,
                                                        )
                                                        .join(", ")}
                                                </p></details>
                                                {preflight.bundled && !isServerStorage() ? (
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
                                                ) : !isServerStorage() ? (
                                                    <p>
                                                        {t(
                                                            "Cài gói bằng trình quản lý TeX của máy rồi thử lại.",
                                                            "Install these packages with your TeX manager, then retry.",
                                                        )}
                                                    </p>
                                                ) : null}
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
                                              isServerStorage() ? "Dữ liệu được tạo trên server." : "Dữ liệu được tạo trên máy của bạn.",
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
