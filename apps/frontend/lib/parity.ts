import { json } from "./api";
export type Session = { name: string; email: string; offline: boolean };
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
export async function native(
    name: string,
    ...parameters: unknown[]
): Promise<NativeResult> {
    const api = window.pywebview?.api as unknown as
        | Record<string, (...args: unknown[]) => Promise<NativeResult>>
        | undefined;
    if (!api?.[name])
        throw new Error(
            "Thao tác này cần cửa sổ desktop. Chạy launcher Python.",
        );
    const result = await api[name](...parameters);
    if (result?.error) throw new Error(result.error);
    return result;
}
export function translator(language: string) {
    return (vi: string, en: string) => (language === "en" ? en : vi);
}
