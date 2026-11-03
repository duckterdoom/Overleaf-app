"use client";
import { useState } from "react";
import { FolderOpen, Upload, LoaderCircle } from "lucide-react";
import type { Project } from "../lib/api";
import { action, native, errorText, translator } from "../lib/parity";
export default function NewProjectExtras({
    title,
    parent,
    setParent,
    icon,
    setIcon,
    color,
    setColor,
    language,
    sourceLanguage,
    onImported,
    onExample,
}: {
    title: string;
    parent: string;
    setParent: (value: string) => void;
    icon: string;
    setIcon: (value: string) => void;
    color: string;
    setColor: (value: string) => void;
    language: string;
    sourceLanguage: string;
    onImported: (project: Project) => void;
    onExample: () => void;
}) {
    const t = translator(language),
        [url, setUrl] = useState(""),
        [busy, setBusy] = useState(false),
        [error, setError] = useState("");
    async function imported(kind: string) {
        setBusy(true);
        setError("");
        try {
            const value =
                kind === "github"
                    ? await action<Project>("github", {
                          url,
                          title,
                          parent,
                          language: sourceLanguage,
                      })
                    : await native(
                          "import_project",
                          kind,
                          title,
                          parent,
                          sourceLanguage,
                      );
            if (!("cancelled" in value) || !value.cancelled)
                onImported(value as Project);
        } catch (error) {
            setError(errorText(error));
        } finally {
            setBusy(false);
        }
    }
    return (
        <div className="new-project-extras">
            <label>
                {t("Thư mục cha lưu dự án", "Parent folder for project")}
                <div className="inline-controls">
                    <input
                        value={parent}
                        placeholder={t(
                            "Dùng thư mục mặc định",
                            "Use default folder",
                        )}
                        onChange={(event) => setParent(event.target.value)}
                    />
                    <button
                        type="button"
                        title={t("Chọn thư mục", "Choose folder")}
                        onClick={async () => {
                            const folder =
                                await window.pywebview?.api.pick_directory();
                            if (folder) setParent(folder);
                        }}
                    >
                        <FolderOpen size={17} />
                    </button>
                </div>
            </label>
            <div className="inline-controls">
                <label>
                    {t("Biểu tượng", "Icon")}
                    <select
                        value={icon}
                        onChange={(event) => setIcon(event.target.value)}
                    >
                        {[
                            "file",
                            "book",
                            "pen",
                            "calc",
                            "globe",
                            "bulb",
                            "code",
                            "cap",
                        ].map((value) => (
                            <option key={value}>{value}</option>
                        ))}
                    </select>
                </label>
                <label>
                    {t("Màu", "Color")}
                    <select
                        value={color}
                        onChange={(event) => setColor(event.target.value)}
                    >
                        {["green", "blue", "yellow", "purple", "red"].map(
                            (value) => (
                                <option key={value}>{value}</option>
                            ),
                        )}
                    </select>
                </label>
            </div>
            <div className="import-project-actions">
                <button type="button" className="secondary" onClick={onExample}>
                    {t("Dự án ví dụ", "Example project")}
                </button>
                {[
                    ["zip", "ZIP"],
                    ["word", "Word (.docx)"],
                    ["markdown", "Markdown"],
                    ["folder", t("Thư mục có sẵn", "Existing folder")],
                ].map(([kind, label]) => (
                    <button
                        key={kind}
                        type="button"
                        className="secondary"
                        disabled={busy}
                        onClick={() => void imported(kind)}
                    >
                        <Upload size={14} />
                        {label}
                    </button>
                ))}
            </div>
            <label>
                GitHub HTTPS URL
                <div className="inline-controls">
                    <input
                        value={url}
                        placeholder="https://github.com/owner/repo"
                        onChange={(event) => setUrl(event.target.value)}
                    />
                    <button
                        className="secondary"
                        type="button"
                        disabled={busy || !url}
                        onClick={() => void imported("github")}
                    >
                        {t("Nhập repo", "Import repo")}
                    </button>
                </div>
            </label>
            {busy && (
                <p>
                    <LoaderCircle size={15} className="spin" />
                    {t("Đang nhập dự án…", "Importing project…")}
                    <button
                        className="secondary"
                        type="button"
                        onClick={() => void action("io-cancel")}
                    >
                        {t("Hủy", "Cancel")}
                    </button>
                </p>
            )}
            {error && (
                <p className="form-error" role="alert">
                    {error}
                </p>
            )}
        </div>
    );
}
