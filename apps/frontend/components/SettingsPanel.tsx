"use client";
import { useState } from "react";
import { X, FolderOpen, Check, LoaderCircle } from "lucide-react";
import { isServerStorage } from "../lib/api";
import {
    action,
    chooseDirectory,
    native,
    errorText,
    translator,
    type Settings,
} from "../lib/parity";

export default function SettingsPanel({
    settings,
    onDone,
    onClose,
}: {
    settings: Settings;
    onDone: (settings: Settings) => void;
    onClose: () => void;
}) {
    const [draft, setDraft] = useState(settings),
        [tab, setTab] = useState("general"),
        [busy, setBusy] = useState(false),
        [error, setError] = useState("");
    const [tools, setTools] = useState<
        { name: string; available: boolean; path: string; detail: string }[]
    >([]);
    const t = translator(String(draft.language));
    const update = (key: string, value: Settings[string]) =>
        setDraft((valueNow) => ({ ...valueNow, [key]: value }));
    function choice(key: string, title: string, options: [string, string][]) {
        return (
            <label key={key}>
                {title}
                <select
                    aria-label={title}
                    value={String(draft[key])}
                    onChange={(event) => update(key, event.target.value)}
                >
                    {options.map(([value, label]) => (
                        <option key={value} value={value}>
                            {label}
                        </option>
                    ))}
                </select>
            </label>
        );
    }
    function check(key: string, title: string) {
        return (
            <label className="check-row" key={key}>
                <input
                    type="checkbox"
                    checked={Boolean(draft[key])}
                    onChange={(event) => update(key, event.target.checked)}
                />
                {title}
            </label>
        );
    }
    async function save() {
        setBusy(true);
        setError("");
        try {
            onDone(await action<Settings>("settings", draft));
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
                className="modal feature-modal"
                role="dialog"
                aria-modal="true"
                aria-labelledby="settings-title"
            >
                <header>
                    <h2 id="settings-title">{t("Cài đặt", "Settings")}</h2>
                    <button title={t("Đóng", "Close")} onClick={onClose}>
                        <X size={20} />
                    </button>
                </header>
                <div className="feature-layout">
                    <nav>
                        {[
                            ["general", t("Giao diện", "Appearance")],
                            ["editor", t("Trình soạn thảo", "Editor")],
                            ["compile", t("Biên dịch", "Compilation")],
                            ["projects", t("Dự án", "Projects")],
                        ].map(([key, label]) => (
                            <button
                                key={key}
                                className={tab === key ? "selected" : ""}
                                onClick={() => setTab(key)}
                            >
                                {label}
                            </button>
                        ))}
                    </nav>
                    <div className="feature-form">
                        {tab === "general" && (
                            <>
                                {choice("language", t("Ngôn ngữ", "Language"), [
                                    ["vi", "Tiếng Việt"],
                                    ["en", "English"],
                                ])}
                                {choice("mode", t("Chế độ", "Mode"), [
                                    ["light", t("Sáng", "Light")],
                                    ["dark", t("Tối", "Dark")],
                                    ["system", t("Theo hệ thống", "System")],
                                ])}
                                {choice("theme", t("Chủ đề màu", "Theme"), [
                                    ["forest", "Forest"],
                                    ["mint", "Mint"],
                                    ["bee", "Bee"],
                                    ["parchment", "Parchment"],
                                ])}
                                {choice(
                                    "fontStyle",
                                    t("Phông giao diện", "Interface font"),
                                    [
                                        ["sans", "Sans"],
                                        ["serif", "Serif"],
                                    ],
                                )}
                                {choice(
                                    "contentWidth",
                                    t("Độ rộng nội dung", "Content width"),
                                    [
                                        ["compact", t("Gọn", "Compact")],
                                        ["standard", t("Chuẩn", "Standard")],
                                        ["wide", t("Rộng", "Wide")],
                                    ],
                                )}
                            </>
                        )}
                        {tab === "editor" && (
                            <>
                                <label>
                                    {t("Cỡ chữ editor", "Editor font size")}
                                    <input
                                        type="number"
                                        min={10}
                                        max={32}
                                        value={Number(draft.editorFontSize)}
                                        onChange={(event) =>
                                            update(
                                                "editorFontSize",
                                                Math.max(
                                                    10,
                                                    Math.min(
                                                        32,
                                                        Number(
                                                            event.target.value,
                                                        ),
                                                    ),
                                                ),
                                            )
                                        }
                                    />
                                </label>
                                {choice(
                                    "editorFont",
                                    t("Phông mã nguồn", "Code font"),
                                    [
                                        ["mono", "Monospace"],
                                        ["menlo", "Menlo"],
                                        ["courier", "Courier"],
                                    ],
                                )}
                                {check(
                                    "wordWrap",
                                    t("Tự xuống dòng", "Wrap lines"),
                                )}
                                {check(
                                    "autoClose",
                                    t(
                                        "Tự đóng ngoặc",
                                        "Close brackets automatically",
                                    ),
                                )}
                                {check("autoSave", t("Tự lưu", "Auto save"))}
                                {check(
                                    "autoCompile",
                                    t("Tự biên dịch", "Auto compile"),
                                )}
                            </>
                        )}
                        {tab === "compile" && (
                            <>
                                {!isServerStorage() && <label>
                                    latexmk
                                    <input
                                        value={String(draft.latexmkPath)}
                                        placeholder={t(
                                            "Tự tìm trên máy",
                                            "Auto detect",
                                        )}
                                        onChange={(event) =>
                                            update(
                                                "latexmkPath",
                                                event.target.value,
                                            )
                                        }
                                    />
                                </label>}
                                {isServerStorage() && <p className="help-text">{t("Bộ TeX và các gói được quản trị viên cấu hình trên server.", "The server operator configures TeX and its packages.")}</p>}
                                <label>
                                    {t(
                                        "Timeout (giây; 0 = không giới hạn)",
                                        "Timeout (seconds; 0 = unlimited)",
                                    )}
                                    <input
                                        type="number"
                                        min={0}
                                        max={86400}
                                        value={Number(
                                            draft.compileTimeoutSeconds,
                                        )}
                                        onChange={(event) =>
                                            update(
                                                "compileTimeoutSeconds",
                                                Number(event.target.value),
                                            )
                                        }
                                    />
                                </label>
                                {choice(
                                    "compiler",
                                    t("Engine mặc định", "Default engine"),
                                    [
                                        ["pdflatex", "pdfLaTeX"],
                                        ["xelatex", "XeLaTeX"],
                                        ["lualatex", "LuaLaTeX"],
                                    ],
                                )}
                                <button
                                    className="secondary"
                                    disabled={busy}
                                    onClick={async () => {
                                        setBusy(true);
                                        try {
                                            setTools(
                                                await action<typeof tools>(
                                                    "diagnostics",
                                                ),
                                            );
                                        } catch (error) {
                                            setError(errorText(error));
                                        } finally {
                                            setBusy(false);
                                        }
                                    }}
                                >
                                    {t("Kiểm tra công cụ", "Check tools")}
                                </button>
                                {tools.map((tool) => (
                                    <div
                                        className="tool-result"
                                        key={tool.name}
                                    >
                                        <strong>
                                            {tool.available ? "✓" : "!"}{" "}
                                            {tool.name}
                                        </strong>
                                        <code>{tool.path}</code>
                                        <small>{tool.detail}</small>
                                    </div>
                                ))}
                            </>
                        )}
                        {tab === "projects" && (
                            <>
                                {!isServerStorage() && <label>
                                    {t(
                                        "Thư mục dự án mặc định",
                                        "Default project folder",
                                    )}
                                    <div className="inline-controls">
                                        <input
                                            value={String(
                                                draft.projectsDirectory,
                                            )}
                                            onChange={(event) =>
                                                update(
                                                    "projectsDirectory",
                                                    event.target.value,
                                                )
                                            }
                                        />
                                        <button
                                            title={t(
                                                "Chọn thư mục",
                                                "Select folder",
                                            )}
                                            onClick={async () => {
                                                try {
                                                    const folder =
                                                        await chooseDirectory(
                                                            String(
                                                                draft.projectsDirectory ||
                                                                    "",
                                                            ),
                                                            String(
                                                                draft.language,
                                                            ),
                                                        );
                                                    if (folder)
                                                        update(
                                                            "projectsDirectory",
                                                            folder,
                                                        );
                                                } catch (error) {
                                                    setError(errorText(error));
                                                }
                                            }}
                                        >
                                            <FolderOpen size={18} />
                                        </button>
                                    </div>
                                </label>}
                                {isServerStorage() && <p className="help-text">{t("Dữ liệu được lưu trên server. Tải lên ZIP hoặc thư mục để nhập dự án; tải xuống để lấy bản sao về máy.", "Data is saved on the server. Upload a ZIP or folder to import; download to keep a local copy.")}</p>}
                                <label>
                                    {t("Trường / cơ quan", "Affiliation")}
                                    <input
                                        value={String(draft.affiliation)}
                                        onChange={(event) =>
                                            update(
                                                "affiliation",
                                                event.target.value,
                                            )
                                        }
                                    />
                                </label>
                                {!isServerStorage() && <>
                                <button
                                    className="secondary"
                                    disabled={busy}
                                    onClick={async () => {
                                        setBusy(true);
                                        try {
                                            const result =
                                                await native("migrate_java");
                                            if (!result.cancelled)
                                                setError(
                                                    t(
                                                        "Đã nhập dữ liệu Java. Đóng cửa sổ và làm mới danh sách dự án.",
                                                        "Java data imported. Close this dialog and refresh projects.",
                                                    ),
                                                );
                                        } catch (error) {
                                            setError(errorText(error));
                                        } finally {
                                            setBusy(false);
                                        }
                                    }}
                                >
                                    {t(
                                        "Nhập dữ liệu từ bản Java…",
                                        "Import Java data…",
                                    )}
                                </button>
                                <p className="help-text">
                                    {t(
                                        "Chọn thư mục OverleafDesktop cũ. Nhập bản sao dự án, tài khoản và lịch sử; không sửa nguồn cũ.",
                                        "Select your old OverleafDesktop folder. Copies projects, accounts and history without editing the originals.",
                                    )}
                                </p>
                                </>}
                            </>
                        )}
                        {error && (
                            <p role="alert" className="form-error">
                                {error}
                            </p>
                        )}
                    </div>
                </div>
                <footer>
                    <button className="secondary" onClick={onClose}>
                        {t("Hủy", "Cancel")}
                    </button>
                    <button
                        className="secondary"
                        disabled={busy}
                        onClick={async () => {
                            try {
                                const reset =
                                    await action<Settings>("settings-reset");
                                setDraft(reset);
                                onDone(reset);
                            } catch (error) {
                                setError(errorText(error));
                            }
                        }}
                    >
                        {t("Khôi phục mặc định", "Reset defaults")}
                    </button>
                    <button
                        className="primary"
                        disabled={busy}
                        onClick={() => void save()}
                    >
                        {busy ? (
                            <LoaderCircle className="spin" size={16} />
                        ) : (
                            <Check size={16} />
                        )}
                        {t("Áp dụng", "Apply")}
                    </button>
                </footer>
            </section>
        </div>
    );
}
