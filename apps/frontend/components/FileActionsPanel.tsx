"use client";
import { useState } from "react";
import { X } from "lucide-react";
import { action, errorText, translator } from "../lib/parity";
import type { Entry } from "../lib/api";
export default function FileActionsPanel({
    project,
    entry,
    language,
    onClose,
    onDone,
    onAdd,
}: {
    project: string;
    entry: Entry;
    language: string;
    onClose: () => void;
    onDone: () => void;
    onAdd: (folder: string) => void;
}) {
    const t = translator(language),
        [target, setTarget] = useState(entry.path),
        [confirm, setConfirm] = useState(false),
        [error, setError] = useState("");
    async function run(kind: string) {
        try {
            await action(kind, { project, path: entry.path, target });
            onDone();
            onClose();
        } catch (error) {
            setError(errorText(error));
        }
    }
    return (
        <div className="modal-backdrop">
            <section
                className="modal"
                role="dialog"
                aria-modal="true"
                aria-label={t("Thao tác tệp", "File actions")}
            >
                <header>
                    <h2>{entry.path}</h2>
                    <button title={t("Đóng", "Close")} onClick={onClose}>
                        <X size={20} />
                    </button>
                </header>
                <div className="feature-form">
                    {entry.directory && (
                        <button
                            className="primary"
                            onClick={() => onAdd(entry.path)}
                        >
                            {t(
                                "Thêm tệp / thư mục / tải lên tại đây",
                                "Add files / folder / upload here",
                            )}
                        </button>
                    )}
                    {entry.path !== "main.tex" ? (
                        <>
                            <label>
                                {t("Đường dẫn mới", "New path")}
                                <input
                                    value={target}
                                    onChange={(event) =>
                                        setTarget(event.target.value)
                                    }
                                />
                            </label>
                            <button
                                className="secondary"
                                onClick={() => void run("file-rename")}
                            >
                                {t("Đổi tên", "Rename")}
                            </button>
                            <button
                                className="danger"
                                onClick={() => setConfirm(true)}
                            >
                                {t("Xóa…", "Delete…")}
                            </button>
                            {confirm && (
                                <div className="restore-confirm">
                                    <p>
                                        {t(
                                            "Chuyển mục này vào thùng rác tệp local?",
                                            "Move this item to local file trash?",
                                        )}
                                    </p>
                                    <button
                                        className="danger"
                                        onClick={() => void run("file-delete")}
                                    >
                                        {t("Xác nhận xóa", "Confirm delete")}
                                    </button>
                                </div>
                            )}
                        </>
                    ) : (
                        <p>
                            {t(
                                "main.tex là tệp biên dịch gốc, không đổi tên hoặc xóa.",
                                "main.tex is the compile root and cannot be renamed or deleted.",
                            )}
                        </p>
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
