export type Project = {
    workspaceRole?: "tutor" | "student";
    permission?: "owner" | "editor" | "viewer";
    shared?: boolean;
    ownerName?: string;
    id: string;
    title: string;
    engine: string;
    main: string;
    updated: string;
    language: string;
    folder?: string;
    storage?: "server" | "local";
    icon?: string;
    color?: string;
};
export type Entry = { path: string; directory: boolean; editable: boolean };
export type Template = {
    id: string;
    category: string;
    title: { en: string; vi: string };
    description: { en: string; vi: string };
    engine: string;
    tags: string[];
};
export type Catalog = {
    categories: { id: string; en: string; vi: string }[];
    templates: Template[];
};
export type PageGeometry = {
    media: number[];
    crop: number[];
    rotation: number;
};
export type PdfPoint = { page: number; x: number; y: number; nonce?: number };
export type CompileEvent = {
    type: string;
    text?: string;
    message?: string;
    snapshot?: string;
    current?: boolean;
    duration_ms?: number;
    geometry?: PageGeometry[];
    main?: string;
    issues?: {
        file: string;
        line: number;
        message: string;
        title: string;
        why?: string;
        fix: string;
    }[];
};
let token = "";
let launchRoute: Record<string, string> = {};
let sessionReady: Promise<void> | null = null;
let serverStorage = true;
export function isServerStorage() { return serverStorage; }
export function getLaunchRoute() {
    return launchRoute;
}
export function bootstrap() {
    if (sessionReady) return sessionReady;
    const params = new URLSearchParams(location.hash.slice(1));
    launchRoute = Object.fromEntries(params);
    if (params.get("share")) sessionStorage.setItem("latex_pending_share", params.get("share")!);
    token =
        params.get("session") ||
        sessionStorage.getItem("overleaf_session") ||
        token;
    if (token) sessionStorage.setItem("overleaf_session", token);
    history.replaceState(null, "", location.pathname);
    sessionReady = (async () => {
        if (params.get("session")) { serverStorage = false; return; } // Desktop launch links.
        const response = await fetch("/api/web/session", {
            method: "POST",
            credentials: "same-origin",
            headers: token ? { Authorization: `Bearer ${token}` } : {},
        });
        if (response.ok) {
            serverStorage = true;
            token = "";
            sessionStorage.removeItem("overleaf_session");
            return;
        }
        if (token && [404, 405].includes(response.status)) { serverStorage = false; return; }
        throw new Error(
            "Không tạo được phiên local web. Tải lại trang từ địa chỉ launcher hiển thị.",
        );
    })();
    return sessionReady;
}
export async function request(path: string, options: RequestInit = {}) {
    await bootstrap();
    const headers = new Headers(options.headers);
    if (!headers.has("Content-Type"))
        headers.set("Content-Type", "application/json");
    if (token) headers.set("Authorization", `Bearer ${token}`);
    const response = await fetch(`/api${path}`, {
        ...options,
        credentials: "same-origin",
        headers,
    });
    if (!response.ok) {
        const error = await response.json().catch(() => ({}));
        throw Object.assign(new Error(
            error.message ||
                (typeof error.detail === "string"
                    ? error.detail
                    : `Yêu cầu thất bại (${response.status})`),
        ), { status: response.status });
    }
    const permission = response.headers.get("x-latex-permission");
    const project = path.match(/^\/projects\/([0-9a-f]{32})(?:\/|$)/)?.[1];
    if (project && permission) window.dispatchEvent(new CustomEvent("latex-project-permission", { detail: { project, permission } }));
    return response;
}
export async function json<T>(path: string, options?: RequestInit): Promise<T> {
    return (await request(path, options)).json();
}
export async function compile(
    project: string,
    engine: string,
    onEvent: (event: CompileEvent) => void,
    main = "",
) {
    const response = await request(`/projects/${project}/compile`, {
        method: "POST",
        body: JSON.stringify({ engine, main }),
    });
    const reader = response.body!.getReader();
    const decoder = new TextDecoder();
    let buffer = "";
    try {
        while (true) {
            const { value, done } = await reader.read();
            buffer += done
                ? decoder.decode()
                : decoder.decode(value, { stream: true });
            const lines = buffer.split("\n");
            buffer = lines.pop() || "";
            for (const line of lines)
                if (line.trim()) onEvent(JSON.parse(line));
            if (done) break;
        }
        if (buffer.trim()) onEvent(JSON.parse(buffer));
    } finally {
        reader.releaseLock();
    }
}
declare global {
    interface Window {
        pywebview?: {
            api: {
                export_docx: (
                    project: string,
                    snapshot: string,
                ) => Promise<{
                    saved?: boolean;
                    cancelled?: boolean;
                    error?: string;
                }>;
                export_pdf: (
                    project: string,
                    snapshot: string,
                ) => Promise<{
                    saved?: boolean;
                    cancelled?: boolean;
                    error?: string;
                }>;
                pick_directory: () => Promise<string | null>;
            };
        };
        __overleafState?: {
            project: string;
            file: string;
            content: string;
            dirty: boolean;
        };
    }
}
