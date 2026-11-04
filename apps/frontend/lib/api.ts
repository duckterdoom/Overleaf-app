export type Project = {
    id: string;
    title: string;
    engine: string;
    main: string;
    updated: string;
    language: string;
    folder?: string;
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
export function getLaunchRoute() {
    return launchRoute;
}
export function bootstrap() {
    const params = new URLSearchParams(location.hash.slice(1));
    launchRoute = Object.fromEntries(params);
    token =
        params.get("session") ||
        sessionStorage.getItem("overleaf_session") ||
        token;
    if (token) sessionStorage.setItem("overleaf_session", token);
    history.replaceState(null, "", location.pathname);
}
export async function request(path: string, options: RequestInit = {}) {
    const response = await fetch(`/api${path}`, {
        ...options,
        headers: {
            "Content-Type": "application/json",
            ...options.headers,
            Authorization: `Bearer ${token}`,
        },
    });
    if (!response.ok) {
        const error = await response.json().catch(() => ({}));
        throw new Error(
            error.message ||
                (typeof error.detail === "string"
                    ? error.detail
                    : `Yêu cầu thất bại (${response.status})`),
        );
    }
    return response;
}
export async function json<T>(path: string, options?: RequestInit): Promise<T> {
    return (await request(path, options)).json();
}
export async function compile(
    project: string,
    engine: string,
    onEvent: (event: CompileEvent) => void,
) {
    const response = await request(`/projects/${project}/compile`, {
        method: "POST",
        body: JSON.stringify({ engine }),
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
                    revision: string,
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
