"use client";
import {
    useEffect,
    useRef,
    useState,
    type Dispatch,
    type SetStateAction,
} from "react";
import type { EditorView } from "@codemirror/view";
import type { LiveSync } from "./liveSync";
import {
    getLaunchRoute,
    isServerStorage,
    json,
    request,
    type Project,
    type Entry,
    type PageGeometry,
    type PdfPoint,
} from "./api";
import {
    action,
    native,
    errorText,
    emptyNetwork,
    type Network,
    type Boot,
    type Session,
    type Settings,
    type Activity,
} from "./parity";
type Set<T> = Dispatch<SetStateAction<T>>;
export type Core = {
    live: LiveSync;
    project: Project | null;
    file: string;
    content: string;
    dirty: boolean;
    busy: boolean;
    engine: string;
    snapshot: string;
    compileTarget: string;
    stale: boolean;
    setProject: Set<Project | null>;
    setProjects: Set<Project[]>;
    setEntries: Set<Entry[]>;
    setFile: Set<string>;
    setContent: Set<string>;
    setDirty: Set<boolean>;
    setStale: Set<boolean>;
    setPdf: Set<Uint8Array | null>;
    setSnapshot: Set<string>;
    setCompileTarget: Set<string>;
    setNotice: Set<string>;
    setError: Set<string>;
    setLayout: Set<string>;
    setLanguage: Set<string>;
    setEngine: Set<string>;
    setLogs: Set<string>;
    save: () => Promise<boolean>;
    build: () => Promise<void>;
    open: (project: Project) => Promise<void>;
    openFile: (entry: Entry) => Promise<boolean | undefined>;
    editor: React.RefObject<EditorView | null>;
    saved: React.RefObject<string>;
    revision: React.RefObject<number>;
    buildInFlight: React.RefObject<boolean>;
};
export default function useParity(core: Core) {
    const latest = useRef(core);
    latest.current = core;
    const [loaded, setLoaded] = useState(false),
        [session, setSession] = useState<Session | null>(null),
        [lastEmail, setLastEmail] = useState("");
    const [resolvedDark, setResolvedDark] = useState(false);
    const [settings, setSettings] = useState<Settings>({
        language: "vi",
        mode: "light",
        theme: "forest",
        editorFontSize: 13,
        editorFont: "mono",
        wordWrap: false,
        autoClose: true,
        autoSave: false,
        autoCompile: false,
        workspaceLayout: "split",
        outlineSplit: 0.6,
    });
    const [activity, setActivity] = useState<Activity>({
            edits: {},
            compiles: 0,
        }),
        [panel, setPanel] = useState<string | null>(null),
        [manage, setManage] = useState<Project | null>(null);
    const [network, setNetwork] = useState<Network>(emptyNetwork),
        [focus, setFocus] = useState(false),
        [detached, setDetached] = useState(false);
    useEffect(() => {
        if (!focus) return;
        const exitFocus = (event: KeyboardEvent) => {
            if (event.key === "Escape" && !event.isComposing) {
                event.preventDefault();
                setFocus(false);
            }
        };
        // Capture Escape before CodeMirror or another focused control consumes it.
        window.addEventListener("keydown", exitFocus, true);
        return () => window.removeEventListener("keydown", exitFocus, true);
    }, [focus]);
    const [geometry, setGeometry] = useState<PageGeometry[]>([]),
        [target, setTarget] = useState<PdfPoint | null>(null),
        [issues, setIssues] = useState<
            {
                file: string;
                line: number;
                message: string;
                title: string;
                why?: string;
                fix: string;
            }[]
        >([]);
    const networkRef = useRef(network);
    networkRef.current = network;
    const sentVersion = useRef(-1),
        sending = useRef(0),
        sendQueue = useRef(Promise.resolve()),
        lastAuto = useRef("");
    const timer = useRef<ReturnType<typeof setTimeout> | null>(null),
        watchSignature = useRef(""),
        loadingSnapshot = useRef("");
    const readonly = core.project?.permission === "viewer" ||
        network.joined && (network.resyncing || core.file !== "main.tex");
    async function acceptSharedLink() {
        const token = sessionStorage.getItem("latex_pending_share");
        if (!token || !isServerStorage()) return false;
        sessionStorage.removeItem("latex_pending_share");
        try {
            const project = await json<Project>("/shares/accept", { method: "POST", body: JSON.stringify({ token }) });
            latest.current.setProjects(await json<Project[]>("/projects"));
            await latest.current.open(project);
        } catch (error) { latest.current.setError(errorText(error)); }
        return true;
    }
    const lang = String(settings.language || "vi");
    useEffect(() => {
        const updatePermission = (event: Event) => {
            const { project, permission } = (event as CustomEvent).detail;
            if (!["owner", "editor", "viewer"].includes(permission)) return;
            latest.current.setProject(current => current && current.id === project && current.permission !== permission ? { ...current, permission } : current);
        };
        window.addEventListener("latex-project-permission", updatePermission);
        return () => window.removeEventListener("latex-project-permission", updatePermission);
    }, []);
    async function refreshSession() {
        const value = await json<Boot>("/session");
        setSession(value.session);
        setSettings(value.settings);
        setActivity(value.activity);
        setLastEmail(value.lastEmail);
        return value;
    }
    useEffect(() => {
        void refreshSession()
            .then(async (boot) => {
                const route = getLaunchRoute();
                setDetached(route.pdf === "true");
                core.setLayout(
                    route.pdf === "true"
                        ? "pdf"
                        : String(boot.settings.workspaceLayout || "split"),
                );
                core.setLanguage(String(boot.settings.language));
                setLoaded(true);
                if (boot.session && !boot.session.needsRole && await acceptSharedLink()) return;
                if (route.project && boot.session && !boot.session.needsRole) {
                    const projects = await json<Project[]>("/projects");
                    const project = projects.find(
                        (item) => item.id === route.project,
                    );
                    if (project) {
                        await latest.current.open(project);
                        if (route.main)
                            latest.current.setCompileTarget(route.main);
                    }
                }
            })
            .catch((error) => core.setError(errorText(error)));
    }, []);
    useEffect(() => {
        const root = document.documentElement;
        const query = window.matchMedia("(prefers-color-scheme: dark)");
        const apply = () => {
            root.dataset.mode =
                settings.mode === "system"
                    ? query.matches
                        ? "dark"
                        : "light"
                    : String(settings.mode);
            root.dataset.theme = String(settings.theme);
            setResolvedDark(root.dataset.mode === "dark");
            root.dataset.font = String(settings.fontStyle || "sans");
            root.dataset.width = String(settings.contentWidth || "standard");
            root.style.setProperty(
                "--editor-font-size",
                `${Number(settings.editorFontSize) || 13}px`,
            );
            root.style.setProperty(
                "--editor-font",
                settings.editorFont === "courier"
                    ? '"Courier New",monospace'
                    : settings.editorFont === "menlo"
                      ? "Menlo,monospace"
                      : '"Cascadia Code","SFMono-Regular",Consolas,monospace',
            );
        };
        apply();
        query.addEventListener("change", apply);
        return () => query.removeEventListener("change", apply);
    }, [settings]);
    useEffect(() => {
        window.__overleafState = core.project
            ? {
                  project: core.project.id,
                  file: core.file,
                  content: core.content,
                  dirty: core.dirty,
              }
            : undefined;
        if (core.project && !detached)
            void native(
                "stage_source",
                core.project.id,
                core.file,
                core.content,
                core.dirty,
                Date.now(),
            ).catch(() => {});
    }, [core.project, core.file, core.content, core.dirty]);
    useEffect(() => {
        if (!core.project || core.live.active || readonly || detached || panel === "history")
            return;
        const value = core.content,
            project = core.project.id,
            file = core.file;
        const checkpoint = setTimeout(() => {
            void action("checkpoint", {
                project,
                path: file,
                content: value,
            }).catch((error) =>
                latest.current.setLogs(
                    (log) => log + `\n[HISTORY] ${errorText(error)}`,
                ),
            );
        }, 3000);
        return () => clearTimeout(checkpoint);
    }, [core.project?.id, core.file, core.content, readonly, detached, panel]);
    useEffect(() => {
        if (
            !settings.autoSave ||
            !core.dirty ||
            !core.project ||
            readonly ||
            detached ||
            panel === "history"
        )
            return;
        const save = setTimeout(() => void latest.current.save(), 1000);
        return () => clearTimeout(save);
    }, [
        core.content,
        core.dirty,
        core.project?.id,
        settings.autoSave,
        readonly,
        detached,
        panel,
    ]);
    useEffect(() => {
        if (
            !settings.autoCompile ||
            !core.project ||
            readonly ||
            detached ||
            panel === "history"
        )
            return;
        const key = `${core.project.id}|${core.compileTarget}|${core.engine}|${core.revision.current}`;
        if (lastAuto.current === key) return;
        timer.current = setTimeout(() => {
            if (!latest.current.buildInFlight.current)
                void latest.current.build();
        }, 1000);
        return () => {
            if (timer.current) clearTimeout(timer.current);
        };
    }, [
        core.content,
        core.engine,
        core.compileTarget,
        core.busy,
        core.project?.id,
        settings.autoCompile,
        readonly,
        detached,
        panel,
    ]);
    function buildStarted() {
        const value = latest.current;
        lastAuto.current = `${value.project?.id}|${value.compileTarget}|${value.engine}|${value.revision.current}`;
        setIssues([]);
    }
    async function stop() {
        if (timer.current) clearTimeout(timer.current);
        buildStarted();
        if (core.project)
            await json(`/projects/${core.project.id}/cancel`, {
                method: "POST",
            });
    }
    function layout(mode: string) {
        core.setLayout(mode);
        setSettings((value) => ({ ...value, workspaceLayout: mode }));
        void action("settings", { workspaceLayout: mode }).catch((error) =>
            core.setError(errorText(error)),
        );
    }
    async function leaveBeforeOpen(project: string) {
        if (
            networkRef.current.joined &&
            networkRef.current.project !== project
        ) {
            await sendQueue.current;
            setNetwork(await action<Network>("network-leave"));
        }
        if (!detached)
            try {
                await native("close_pdf_windows");
            } catch {}
        setGeometry([]);
        setTarget(null);
        watchSignature.current = "";
        loadingSnapshot.current = "";
        lastAuto.current = "";
    }
    function acceptNetwork(value: Network) {
        setNetwork(value);
        if (
            value.project !== latest.current.project?.id ||
            !value.joined ||
            value.version === sentVersion.current ||
            sending.current ||
            value.resyncing
        )
            return;
        sentVersion.current = value.version;
        const current = latest.current;
        if (current.content !== value.document) {
            current.revision.current++;
            current.setContent(value.document);
            current.setDirty(value.document !== current.saved.current);
            current.setStale(true);
            void action("editing", {
                project: value.project,
                dirty: value.document !== current.saved.current,
            });
        }
    }
    function changed(value: string, base: string) {
        const current = latest.current;
        if (!current.project) return;
        // Live OT already persists/broadcasts this change; local stale state and
        // the server's source fingerprint protect PDF export. Don't route an
        // extra authenticated HTTP request through Atlas for every keystroke.
        if (current.live.active) return;
        void action("editing", {
            project: current.project.id,
            dirty: value !== current.saved.current,
        }).catch(() => {});
        if (
            networkRef.current.joined &&
            networkRef.current.project === current.project.id &&
            current.file === "main.tex"
        ) {
            sending.current++;
            sendQueue.current = sendQueue.current.then(async () => {
                try {
                    const result = await action<Network>("network-edit", {
                        text: value,
                        base,
                    });
                    setNetwork(result);
                } catch (error) {
                    current.setError(errorText(error));
                } finally {
                    sending.current--;
                }
            });
        }
    }
    useEffect(() => {
        if (!network.joined || detached) return;
        const poll = setInterval(() => {
            void json<Network>("/network")
                .then(acceptNetwork)
                .catch(() => {});
        }, 180);
        return () => clearInterval(poll);
    }, [network.joined, core.project?.id, detached]);
    useEffect(() => {
        if (!core.project) return;
        let disposed = false,
            loading = false;
        let lastPoll = 0;
        const project = core.project.id;
        async function poll() {
            if (loading || (latest.current.live.active && Date.now() - lastPoll < 3000)) return;
            lastPoll = Date.now();
            loading = true;
            const requestedMain = latest.current.compileTarget;
            try {
                const result = await json<{
                    signature: string;
                    files: Entry[];
                    snapshot: string | null;
                    dirty: boolean;
                    current: boolean;
                }>(
                    `/projects/${project}/watch?main=${encodeURIComponent(requestedMain)}`,
                );
                if (disposed || latest.current.compileTarget !== requestedMain)
                    return;
                const current = latest.current;
                if (
                    watchSignature.current &&
                    watchSignature.current !== result.signature
                ) {
                    current.setEntries(result.files);
                    current.setStale(true);
                    if (!current.live.active) try {
                        const value = await json<{ content: string }>(
                            `/projects/${project}/file?path=${encodeURIComponent(current.file)}`,
                        );
                        if (
                            !disposed &&
                            latest.current.file === current.file &&
                            value.content !== current.saved.current
                        ) {
                            if (!current.live.active && !current.dirty && !networkRef.current.joined) {
                                current.saved.current = value.content;
                                current.revision.current++;
                                current.setContent(value.content);
                                current.setDirty(false);
                                current.setNotice(
                                    lang === "en"
                                        ? "Loaded external changes."
                                        : "Đã nạp thay đổi từ bên ngoài.",
                                );
                            } else if (!current.live.active)
                                current.setNotice(
                                    lang === "en"
                                        ? "File changed outside the editor; your unsaved text is preserved."
                                        : "Tệp đổi bên ngoài; nội dung chưa lưu được giữ nguyên.",
                                );
                        }
                    } catch (error) {
                        current.setError(errorText(error));
                    }
                }
                watchSignature.current = result.signature;
                const live = latest.current;
                if (
                    !detached &&
                    !live.dirty &&
                    !live.buildInFlight.current &&
                    result.snapshot &&
                    result.snapshot === live.snapshot
                ) {
                    live.setStale(!result.current || result.dirty);
                }
                if (detached) {
                    current.setStale(!result.current);
                    if (
                        result.snapshot &&
                        loadingSnapshot.current !== result.snapshot
                    ) {
                        loadingSnapshot.current = result.snapshot;
                        const [response, pages] = await Promise.all([
                            request(
                                `/projects/${project}/preview/${result.snapshot}`,
                            ),
                            json<PageGeometry[]>(
                                `/projects/${project}/geometry/${result.snapshot}`,
                            ),
                        ]);
                        const data = new Uint8Array(
                            await response.arrayBuffer(),
                        );
                        if (!disposed) {
                            current.setPdf(data);
                            current.setSnapshot(result.snapshot);
                            setGeometry(pages);
                        }
                    }
                }
            } catch (error) {
                if (!disposed) latest.current.setError(errorText(error));
            } finally {
                loading = false;
        let lastPoll = 0;
            }
        }
        void poll();
        const interval = setInterval(() => void poll(), 750);
        return () => {
            disposed = true;
            clearInterval(interval);
        };
    }, [core.project?.id, detached]);
    async function positionSource(result: {
        file: string;
        line: number;
        column: number;
    }) {
        const current = latest.current;
        if (!current.project) return;
        if (detached) {
            await native(
                "focus_source",
                current.project.id,
                result.file,
                result.line,
                result.column,
            );
            return;
        }
        if (current.file !== result.file)
            await current.openFile({
                path: result.file,
                directory: false,
                editable: true,
            });
        current.setLayout("split");
        requestAnimationFrame(() =>
            requestAnimationFrame(() => {
                const editor = latest.current.editor.current;
                if (!editor) return;
                const line = editor.state.doc.line(
                    Math.min(editor.state.doc.lines, Math.max(1, result.line)),
                );
                editor.dispatch({
                    selection: {
                        anchor: Math.min(
                            line.to,
                            line.from + Math.max(0, result.column),
                        ),
                    },
                    scrollIntoView: true,
                });
                editor.focus();
            }),
        );
    }
    async function forward() {
        const current = latest.current;
        if (!current.project || current.dirty || current.stale || current.busy)
            return;
        const selection = current.editor.current?.state.selection.main;
        if (!selection) return;
        const line = current.editor.current!.state.doc.lineAt(selection.head);
        try {
            const result = await action<PdfPoint>("synctex", {
                project: current.project.id,
                snapshot: current.snapshot,
                mode: "forward",
                file: current.file,
                line: line.number,
                column: selection.head - line.from,
            });
            if (latest.current.snapshot === current.snapshot)
                setTarget({ ...result, nonce: Date.now() });
        } catch (error) {
            current.setError(errorText(error));
        }
    }
    async function inverse(point: PdfPoint) {
        const current = latest.current;
        if (!current.project || current.dirty || current.stale || current.busy)
            return;
        try {
            const result = await action<{
                file: string;
                line: number;
                column: number;
            }>("synctex", {
                project: current.project.id,
                snapshot: current.snapshot,
                mode: "inverse",
                ...point,
            });
            if (latest.current.snapshot === current.snapshot)
                await positionSource(result);
        } catch (error) {
            current.setError(errorText(error));
        }
    }
    useEffect(() => {
        const source = (event: Event) => {
            const detail = (event as CustomEvent).detail;
            if (detail.project === latest.current.project?.id)
                void positionSource(detail);
        };
        const reattach = () => layout("split");
        const shortcut = (event: KeyboardEvent) => {
            if (
                (event.ctrlKey || event.metaKey) &&
                event.shiftKey &&
                event.key.toLowerCase() === "m"
            ) {
                event.preventDefault();
                setFocus((value) => !value);
            }
            if (
                event.key === "F5" ||
                ((event.ctrlKey || event.metaKey) && event.key === "Enter")
            ) {
                if (latest.current.project) {
                    event.preventDefault();
                    void latest.current.build();
                }
            }
        };
        window.addEventListener("synctex-source", source);
        const fromTab = (event: MessageEvent) => {
            if (
                event.origin !== window.location.origin ||
                event.data?.type !== "overleaf-source"
            )
                return;
            const detail = event.data;
            if (
                detail.project === latest.current.project?.id &&
                typeof detail.file === "string" &&
                Number.isFinite(detail.line) &&
                Number.isFinite(detail.column)
            )
                void positionSource(detail);
        };
        window.addEventListener("message", fromTab);
        window.addEventListener("pdf-reattach", reattach);
        window.addEventListener("keydown", shortcut);
        return () => {
            window.removeEventListener("synctex-source", source);
            window.removeEventListener("message", fromTab);
            window.removeEventListener("pdf-reattach", reattach);
            window.removeEventListener("keydown", shortcut);
        };
    }, []);
    async function showHistory() {
        if (!(await core.save())) return;
        try {
            await native("close_pdf_windows");
        } catch {}
        setPanel("history");
    }
    async function connect() {
        if (isServerStorage()) {
            // Opening ACL settings does not require an initialized OT socket
            // when there are no local edits to persist (e.g. returning from IELTS).
            if ((core.dirty || core.live.pending) && !(await core.save())) return;
            setPanel("share"); return;
        }
        if (!(await core.save())) return;
        if (core.file !== "main.tex")
            await core.openFile({
                path: "main.tex",
                directory: false,
                editable: true,
            });
        setPanel("network");
    }
    async function detach() {
        if (!core.project) return;
        try {
            await native("detach_pdf", core.project.id, core.compileTarget);
            layout("editor");
        } catch (error) {
            core.setError(errorText(error));
        }
    }
    async function authenticated(value: Session) {
        setSession(value);
        if (value.needsRole) { core.setProjects([]); return; }
        await refreshSession();
        core.setProjects(await json<Project[]>("/projects"));
        setActivity(await action<Activity>("activity"));
        await acceptSharedLink();
    }
    async function logout() {
        if (!(await core.save())) return;
        await sendQueue.current;
        if (!session?.needsRole) setNetwork(await action<Network>("network-leave"));
        await action("logout");
        setSession(null);
        core.setProject(null);
        core.setPdf(null);
        setPanel(null);
    }
    return {
        loaded,
        session,
        lastEmail,
        settings,
        setSettings,
        resolvedDark,
        activity,
        setActivity,
        panel,
        setPanel,
        manage,
        setManage,
        network,
        setNetwork,
        acceptNetwork,
        readonly,
        focus,
        setFocus,
        detached,
        geometry,
        setGeometry,
        target,
        issues,
        setIssues,
        forward,
        inverse,
        positionSource,
        buildStarted,
        stop,
        layout,
        detach,
        connect,
        showHistory,
        leaveBeforeOpen,
        changed,
        authenticated,
        logout,
        refreshSession,
        closeHistory: () => {
            lastAuto.current = "";
            setPanel(null);
        },
    };
}
