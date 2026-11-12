"use client";
import { createContext, useContext } from "react";
import { json, request } from "../api";

export const WritingProjectContext = createContext<string | null>(null);
export function useWritingApi() {
    const project = useContext(WritingProjectContext);
    const path = (value: string) => project && value.startsWith("/writing/")
        ? `${value}${value.includes("?") ? "&" : "?"}projectId=${encodeURIComponent(project)}` : value;
    return {
        json: <T,>(value: string, options?: RequestInit) => json<T>(path(value), options),
        request: (value: string, options?: RequestInit) => request(path(value), options),
        assetUrl: (id: string) => `/api${path(`/writing/assets/${encodeURIComponent(id)}`)}`,
    };
}
