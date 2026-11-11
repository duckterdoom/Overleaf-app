import { json, request } from "./api";
export type Session = { name: string; email: string; offline: boolean; id?: string; role?: "student" | "tutor" | null; needsRole?: boolean; provider?: "password" | "google" };
export type Settings = Record<string, string | number | boolean>;
export type Boot = {
    session: Session | null;
    lastEmail: string;
    settings: Settings;
    activity: Activity;
};
export type Activity = { edits: Record<string, number>; compiles: number };
export type Network = {
    joined: boolean;
    online: boolean;
    resyncing: boolean;
    pending: number;
    document: string;
    project: string;
    version: number;
    room: string;
    user: string;
    error: string;
    members: { id: string; name: string; role: string; status: string }[];
    cursors: Record<string, { name: string; position: string }>;
};
export const emptyNetwork: Network = {
    joined: false,
    online: false,
    resyncing: false,
    pending: 0,
    document: "",
    project: "",
    version: 0,
    room: "",
    user: "",
    error: "",
    members: [],
    cursors: {},
};
export function action<T = Record<string, unknown>>(
    name: string,
    data: object = {},
): Promise<T> {
    return json<T>(`/actions/${name}`, {
        method: "POST",
        body: JSON.stringify(data),
    });
}
export function errorText(value: unknown) {
    return value instanceof Error ? value.message : String(value);
}
export type NativeResult = {
    cancelled?: boolean;
    error?: string;
    saved?: boolean;
    opened?: boolean;
    [key: string]: unknown;
};
export async function chooseDirectory(
    current: string,
    language: string,
): Promise<string | null> {
    if (window.pywebview?.api) return window.pywebview.api.pick_directory();
    throw new Error(language === "en"
        ? "Project storage is managed by the server. Upload a folder or ZIP instead."
        : "Thư mục lưu do server quản lý. Hãy tải lên thư mục hoặc tệp ZIP.");
}
export async function native(
    name: string,
    ...parameters: unknown[]
): Promise<NativeResult> {
    const api = window.pywebview?.api as unknown as
        | Record<string, (...args: unknown[]) => Promise<NativeResult>>
        | undefined;
    if (!api?.[name]) {
        if (name === "stage_source" || name === "close_pdf_windows") return {};
        if (name === "migrate_java") {
            const folder = await chooseDirectory(
                "",
                document.documentElement.lang.startsWith("en") ? "en" : "vi",
            );
            return folder
                ? action("migrate-java", { folder })
                : { cancelled: true };
        }
        if (name === "detach_pdf") {
            const route = new URLSearchParams({
                project: String(parameters[0]),
                pdf: "true",
                main: String(parameters[1] || ""),
            });
            const tab = window.open("/#" + route.toString(), "_blank");
            if (!tab)
                throw Error(
                    "Trình duyệt chặn tab mới. Cho phép mở tab rồi thử lại.",
                );
            return { opened: true };
        }
        if (name === "focus_source") {
            if (!window.opener)
                throw Error("Mở lại PDF từ tab editor để điều hướng về nguồn.");
            window.opener.postMessage(
                {
                    type: "overleaf-source",
                    project: parameters[0],
                    file: parameters[1],
                    line: parameters[2],
                    column: parameters[3],
                },
                window.location.origin,
            );
            window.opener.focus();
            return { opened: true };
        }
        if (name === "open_binary") {
            const tab = window.open("about:blank", "_blank");
            if (!tab)
                throw Error(
                    "Trình duyệt chặn tab mới. Cho phép mở tab rồi thử lại.",
                );
            tab.opener = null;
            try {
                const response = await request(
                    `/projects/${encodeURIComponent(String(parameters[0]))}/binary?path=${encodeURIComponent(String(parameters[1]))}`,
                );
                const object = URL.createObjectURL(await response.blob());
                if (String(parameters[1]).toLowerCase().endsWith(".svg")) {
                    tab.close();
                    const download = document.createElement("a");
                    download.href = object;
                    download.download =
                        String(parameters[1]).split("/").pop() || "image.svg";
                    download.click();
                    setTimeout(() => URL.revokeObjectURL(object), 30000);
                    return { opened: true };
                }
                tab.location.href = object;
                setTimeout(() => URL.revokeObjectURL(object), 120000);
                return { opened: true };
            } catch (error) {
                tab.close();
                throw error;
            }
        }
        throw new Error(
            "Thao tác này chưa có trên local web. Dùng chức năng upload/import trong trình duyệt.",
        );
    }
    const result = await api[name](...parameters);
    if (result?.error) throw new Error(result.error);
    return result;
}
export function translator(language: string) {
    return (vi: string, en: string) => (language === "en" ? en : vi);
}
