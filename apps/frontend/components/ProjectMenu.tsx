"use client";
import { useState } from "react";
import { X, FolderOpen } from "lucide-react";
import { action, errorText, translator } from "../lib/parity";
import type { Project } from "../lib/api";
export default function ProjectMenu({
    project,
    language,
    onClose,
    onDone,
}: {
    project: Project;
    language: string;
    onClose: () => void;
    onDone: () => void;
}) {
    const t = translator(language),
        [title, setTitle] = useState(project.title),
        [parent, setParent] = useState(""),
        [icon, setIcon] = useState(project.icon || "file"),
        [color, setColor] = useState(project.color || "green"),
        [confirm, setConfirm] = useState(false),
        [error, setError] = useState(""),
        [busy, setBusy] = useState(false);
    async function run(name: string, data: object) {
        setBusy(true);
        try {
            await action(name, { project: project.id, ...data });
            onDone();
            onClose();
        } catch (error) {
            setError(errorText(error));
        } finally {
            setBusy(false);
        }
    }
    return (
        <div className="modal-backdrop">
            <section
                className="modal"
                role="dialog"
                aria-modal="true"
                aria-label={t("Quản lý dự án", "Manage project")}
            >
                <header>
                    <h2>{t("Quản lý dự án", "Manage project")}</h2>
                    <button title={t("Đóng", "Close")} onClick={onClose}>
                        <X size={20} />
                    </button>
                </header>
                <div className="feature-form">
                    <label>
                        {t("Tên dự án", "Project name")}
                        <input
                            value={title}
                            maxLength={120}
                            onChange={(event) => setTitle(event.target.value)}
                        />
                    </label>
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
                    <button
                        className="primary"
                        disabled={busy}
                        onClick={() =>
                            void run("project-update", { title, icon, color })
                        }
                    >
                        {t("Lưu thông tin", "Save details")}
                    </button>
                    <label>
                        {t("Thư mục hiện tại", "Current folder")}
                        <input readOnly value={project.folder || ""} />
                    </label>
                    <label>
                        {t("Thư mục cha mới", "New parent folder")}
                        <div className="inline-controls">
                            <input
                                value={parent}
                                onChange={(event) =>
                                    setParent(event.target.value)
                                }
                            />
                            <button
                                title={t("Chọn thư mục", "Select folder")}
                                onClick={async () => {
                                    const value =
                                        await window.pywebview?.api.pick_directory();
                                    if (value) setParent(value);
                                }}
                            >
                                <FolderOpen size={17} />
                            </button>
                        </div>
                    </label>
                    <p className="help-text">
                        {t(
                            "Chuyển bằng bản sao có kiểm tra bytes. Bản nguồn được giữ ở .overleaf-moved-backups.",
                            "Copies and verifies bytes before moving. The original is retained in .overleaf-moved-backups.",
                        )}
                    </p>
                    <button
                        className="secondary"
                        disabled={busy || !parent}
                        onClick={() => void run("project-move", { parent })}
                    >
                        {t("Chuyển thư mục lưu", "Move project folder")}
                    </button>
                    <button
                        className="danger"
                        disabled={busy}
                        onClick={() => setConfirm(true)}
                    >
                        {t("Xóa dự án…", "Delete project…")}
                    </button>
                    {confirm && (
                        <div className="restore-confirm">
                            <p>
                                {t(
                                    "Chuyển dự án vào thùng rác local?",
                                    "Move this project to local trash?",
                                )}
                            </p>
                            <button
                                className="danger"
                                onClick={() => void run("project-delete", {})}
                            >
                                {t("Xác nhận xóa", "Confirm delete")}
                            </button>
                        </div>
                    )}
                    {error && (
                        <p className="form-error" role="alert">
                            {error}
                        </p>
                    )}
                </div>
            </section>
        </div>
    );
}
