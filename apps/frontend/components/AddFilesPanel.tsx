"use client";
import { useEffect, useRef, useState } from "react";
import {
    FilePlus2,
    FolderPlus,
    Upload,
    FolderInput,
    Globe,
    BookOpen,
    X,
    ClipboardPaste,
    LoaderCircle,
} from "lucide-react";
import { json, request, type Entry, type Project } from "../lib/api";
import { action, errorText, native, translator } from "../lib/parity";
type Pending = { name: string; file?: File; directory: boolean };
export default function AddFilesPanel({
    project,
    folder,
    language,
    onClose,
    onDone,
    initial = "file",
}: {
    project: string;
    folder: string;
    language: string;
    onClose: () => void;
    onDone: (file?: string) => void;
    initial?: string;
}) {
    const t = translator(language),
        [tab, setTab] = useState(initial),
        [target, setTarget] = useState(folder),
        [name, setName] = useState("name.tex"),
        [url, setUrl] = useState("");
    const [pending, setPending] = useState<Pending[]>([]),
        [projects, setProjects] = useState<Project[]>([]),
        [sourceProject, setSourceProject] = useState(""),
        [sources, setSources] = useState<Entry[]>([]),
        [source, setSource] = useState(""),
        [output, setOutput] = useState(false),
        [busy, setBusy] = useState(false),
        [progress, setProgress] = useState(""),
        [error, setError] = useState("");
    const directoryInput = useRef<HTMLInputElement>(null),
        controller = useRef<AbortController | null>(null),
        batch = useRef("");
    useEffect(() => {
        directoryInput.current?.setAttribute("webkitdirectory", "");
    }, [tab]);
    useEffect(() => {
        void json<Project[]>("/projects").then((items) =>
            setProjects(items.filter((item) => item.id !== project)),
        );
    }, [project]);
    useEffect(() => {
        if (sourceProject)
            void json<Entry[]>(
                `/projects/${sourceProject}/${output ? "outputs" : "files"}`,
            )
                .then((items) => {
                    setSources(items.filter((item) => !item.directory));
                    setSource("");
                })
                .catch((error) => setError(errorText(error)));
    }, [sourceProject, output]);
    const full = (value: string) =>
        [target.replace(/\/$/, ""), value].filter(Boolean).join("/");
    function files(selected: FileList | null) {
        if (selected)
            setPending((current) => [
                ...current,
                ...Array.from(selected).map((file) => ({
                    name: file.webkitRelativePath || file.name,
                    file,
                    directory: false,
                })),
            ]);
    }
    async function drop(event: React.DragEvent) {
        event.preventDefault();
        const result: Pending[] = [];
        async function walk(entry: FileSystemEntry, prefix = "") {
            const name = prefix + entry.name;
            if (entry.isFile)
                await new Promise<void>((resolve, reject) =>
                    (entry as FileSystemFileEntry).file((file) => {
                        result.push({ name, file, directory: false });
                        resolve();
                    }, reject),
                );
            else {
                result.push({ name, directory: true });
                const reader = (
                    entry as FileSystemDirectoryEntry
                ).createReader();
                while (true) {
                    const children = await new Promise<FileSystemEntry[]>(
                        (resolve, reject) =>
                            reader.readEntries(resolve, reject),
                    );
                    if (!children.length) break;
                    for (const child of children) await walk(child, name + "/");
                }
            }
        }
        try {
            for (const item of Array.from(event.dataTransfer.items)) {
                const entry = item.webkitGetAsEntry();
                if (entry) await walk(entry);
            }
            if (!result.length) files(event.dataTransfer.files);
            else setPending((current) => [...current, ...result]);
        } catch (error) {
            setError(errorText(error));
        }
    }
    function paste(event: React.ClipboardEvent) {
        const values = Array.from(event.clipboardData.files);
        if (values.length) {
            event.preventDefault();
            setPending((current) => [
                ...current,
                ...values.map((file, index) => ({
                    name: file.type.startsWith("image/")
                        ? `clipboard-${Date.now()}-${index}.png`
                        : file.name,
                    file,
                    directory: false,
                })),
            ]);
        }
    }
    async function cancel() {
        controller.current?.abort();
        await action("io-cancel");
        if (batch.current) {
            await request(`/projects/${project}/upload/${batch.current}`, {
                method: "DELETE",
            }).catch(() => {});
            batch.current = "";
        }
    }
    async function submit(event: React.FormEvent) {
        event.preventDefault();
        setError("");
        setBusy(true);
        try {
            if (tab === "file" || tab === "folder") {
                const path = full(
                    tab === "file" && !name.split("/").pop()?.includes(".")
                        ? name + ".tex"
                        : name,
                );
                await json(`/projects/${project}/file`, {
                    method: "POST",
                    body: JSON.stringify({ path, directory: tab === "folder" }),
                });
                onDone(tab === "file" ? path : undefined);
            } else if (tab === "copy") {
                await action("copy-file", {
                    project,
                    sourceProject,
                    source,
                    target: full(name),
                    output,
                });
                onDone();
            } else if (tab === "url") {
                await action("download", { project, url, path: full(name) });
                onDone();
            } else {
                if (!pending.length)
                    throw new Error(
                        t("Chọn tệp để tải lên.", "Select files to upload."),
                    );
                controller.current = new AbortController();
                const started = await json<{ id: string }>(
                    `/projects/${project}/upload/start`,
                    {
                        method: "POST",
                        body: JSON.stringify({
                            manifest: pending.map((item) => ({
                                path: full(item.name),
                                directory: item.directory,
                                size: item.file?.size || 0,
                            })),
                        }),
                    },
                );
                batch.current = started.id;
                for (const item of pending) {
                    if (!item.file) continue;
                    setProgress(item.name);
                    await request(
                        `/projects/${project}/upload/${started.id}/part?path=${encodeURIComponent(full(item.name))}`,
                        {
                            method: "PUT",
                            headers: {
                                "Content-Type": "application/octet-stream",
                            },
                            body: item.file,
                            signal: controller.current.signal,
                        },
                    );
                }
                await json(`/projects/${project}/upload/${started.id}/commit`, {
                    method: "POST",
                });
                batch.current = "";
                onDone();
            }
            onClose();
        } catch (error) {
            setError(errorText(error));
            if (batch.current) await cancel();
        } finally {
            setBusy(false);
            setProgress("");
        }
    }
    const tabs = [
        ["file", FilePlus2, t("Tệp mới", "New file")],
        ["folder", FolderPlus, t("Thư mục mới", "New folder")],
        ["upload", Upload, t("Tải lên", "Upload")],
        ["copy", FolderInput, t("Từ dự án khác", "From another project")],
        ["url", Globe, t("Từ URL bên ngoài", "From external URL")],
        [
            "bibliography",
            BookOpen,
            t("Thư viện tài liệu (.bib)", "Reference library (.bib)"),
        ],
    ] as const;
    return (
        <div className="modal-backdrop">
            <section
                className="modal feature-modal"
                role="dialog"
                aria-modal="true"
                aria-labelledby="add-files-title"
            >
                <header>
                    <h2 id="add-files-title">{t("Thêm tệp", "Add files")}</h2>
                    <button
                        title={t("Đóng", "Close")}
                        disabled={busy}
                        onClick={onClose}
                    >
                        <X size={20} />
                    </button>
                </header>
                <form onSubmit={(event) => void submit(event)}>
                    <div className="feature-layout">
                        <nav>
                            {tabs.map(([key, Icon, label]) => (
                                <button
                                    key={key}
                                    type="button"
                                    disabled={busy}
                                    className={tab === key ? "selected" : ""}
                                    onClick={() => {
                                        setTab(key);
                                        setError("");
                                    }}
                                >
                                    <Icon size={17} />
                                    {label}
                                </button>
                            ))}
                        </nav>
                        <div className="feature-form">
                            <label>
                                {t(
                                    "Thư mục đích (trống = gốc)",
                                    "Destination folder (empty = root)",
                                )}
                                <input
                                    value={target}
                                    onChange={(event) =>
                                        setTarget(event.target.value)
                                    }
                                />
                            </label>
                            {["file", "folder", "url"].includes(tab) && (
                                <label>
                                    {t(
                                        "Tên tệp / thư mục",
                                        "File / folder name",
                                    )}
                                    <input
                                        required
                                        value={name}
                                        onChange={(event) =>
                                            setName(event.target.value)
                                        }
                                    />
                                </label>
                            )}
                            {tab === "url" && (
                                <label>
                                    HTTPS URL
                                    <input
                                        required
                                        type="url"
                                        value={url}
                                        onChange={(event) =>
                                            setUrl(event.target.value)
                                        }
                                    />
                                </label>
                            )}
                            {tab === "copy" && (
                                <>
                                    <label>
                                        {t("Chọn dự án", "Select a project")}
                                        <select
                                            required
                                            value={sourceProject}
                                            onChange={(event) =>
                                                setSourceProject(
                                                    event.target.value,
                                                )
                                            }
                                        >
                                            <option value="">—</option>
                                            {projects.map((item) => (
                                                <option
                                                    key={item.id}
                                                    value={item.id}
                                                >
                                                    {item.title}
                                                </option>
                                            ))}
                                        </select>
                                    </label>
                                    <label className="check-row">
                                        <input
                                            type="checkbox"
                                            checked={output}
                                            onChange={(event) =>
                                                setOutput(event.target.checked)
                                            }
                                        />
                                        {t(
                                            "Chọn từ tệp đầu ra (build)",
                                            "Select from output files (build)",
                                        )}
                                    </label>
                                    <label>
                                        {t("Chọn tệp", "Select a file")}
                                        <select
                                            required
                                            value={source}
                                            onChange={(event) => {
                                                setSource(event.target.value);
                                                setName(
                                                    event.target.value
                                                        .split("/")
                                                        .pop() || "",
                                                );
                                            }}
                                        >
                                            <option value="">—</option>
                                            {sources.map((item) => (
                                                <option key={item.path}>
                                                    {item.path}
                                                </option>
                                            ))}
                                        </select>
                                    </label>
                                    <label>
                                        {t(
                                            "Tên trong dự án này",
                                            "File name in this project",
                                        )}
                                        <input
                                            required
                                            value={name}
                                            onChange={(event) =>
                                                setName(event.target.value)
                                            }
                                        />
                                    </label>
                                </>
                            )}
                            {["upload", "bibliography"].includes(tab) && (
                                <>
                                    {tab === "bibliography" && (
                                        <p className="help-text">
                                            {t(
                                                "Xuất .bib từ ReadCube/Zotero/Mendeley rồi tải lên. Đây là nhập tệp, không kết nối tài khoản các dịch vụ.",
                                                "Export a .bib file from ReadCube/Zotero/Mendeley and upload it. This imports files, not service accounts.",
                                            )}
                                        </p>
                                    )}
                                    <div
                                        className="drop-zone"
                                        tabIndex={0}
                                        onDragOver={(event) =>
                                            event.preventDefault()
                                        }
                                        onDrop={(event) => void drop(event)}
                                        onPaste={paste}
                                    >
                                        <Upload size={32} />
                                        <h3>
                                            {t(
                                                "Thả hoặc dán tệp, thư mục, ảnh vào đây",
                                                "Drop or paste files, folders or images here",
                                            )}
                                        </h3>
                                        <div className="inline-controls">
                                            <label className="secondary">
                                                {t("Chọn tệp", "Select files")}
                                                <input
                                                    hidden
                                                    multiple
                                                    type="file"
                                                    accept={
                                                        tab === "bibliography"
                                                            ? ".bib"
                                                            : undefined
                                                    }
                                                    onChange={(event) =>
                                                        files(
                                                            event.target.files,
                                                        )
                                                    }
                                                />
                                            </label>
                                            <label className="secondary">
                                                {t(
                                                    "Chọn thư mục",
                                                    "Select folder",
                                                )}
                                                <input
                                                    hidden
                                                    ref={directoryInput}
                                                    multiple
                                                    type="file"
                                                    onChange={(event) =>
                                                        files(
                                                            event.target.files,
                                                        )
                                                    }
                                                />
                                            </label>
                                            <button
                                                type="button"
                                                title={t(
                                                    "Tải cả thư mục, kể cả thư mục rỗng",
                                                    "Upload folder including empty folders",
                                                )}
                                                onClick={async () => {
                                                    if (
                                                        !window.pywebview?.api
                                                    ) {
                                                        directoryInput.current?.click();
                                                        return;
                                                    }
                                                    try {
                                                        const result =
                                                            await native(
                                                                "upload_files",
                                                                project,
                                                                target,
                                                                true,
                                                            );
                                                        if (!result.cancelled) {
                                                            onDone();
                                                            onClose();
                                                        }
                                                    } catch (error) {
                                                        setError(
                                                            errorText(error),
                                                        );
                                                    }
                                                }}
                                            >
                                                <FolderPlus size={18} />
                                            </button>
                                        </div>
                                        <small>
                                            {t(
                                                "Ctrl/Cmd+V để dán ảnh từ clipboard.",
                                                "Ctrl/Cmd+V to paste clipboard images.",
                                            )}
                                        </small>
                                    </div>
                                    <div className="upload-queue">
                                        {pending.map((item, index) => (
                                            <div key={index}>
                                                <span>
                                                    {item.directory ? "▸ " : ""}
                                                    {item.name}
                                                </span>
                                                <small>
                                                    {item.file
                                                        ? `${Math.ceil(item.file.size / 1024)} KB`
                                                        : t(
                                                              "Thư mục",
                                                              "Folder",
                                                          )}
                                                </small>
                                                <button
                                                    type="button"
                                                    disabled={busy}
                                                    title={t(
                                                        "Bỏ mục",
                                                        "Remove item",
                                                    )}
                                                    onClick={() =>
                                                        setPending(
                                                            pending.filter(
                                                                (_, i) =>
                                                                    i !== index,
                                                            ),
                                                        )
                                                    }
                                                >
                                                    <X size={14} />
                                                </button>
                                            </div>
                                        ))}
                                    </div>
                                </>
                            )}
                            {progress && (
                                <p>
                                    {t("Đang tải:", "Uploading:")} {progress}
                                </p>
                            )}
                            {error && (
                                <p className="form-error" role="alert">
                                    {error}
                                </p>
                            )}
                        </div>
                    </div>
                    <footer>
                        <button
                            type="button"
                            className="secondary"
                            onClick={() => (busy ? void cancel() : onClose())}
                        >
                            {t("Hủy", "Cancel")}
                        </button>
                        <button className="primary" disabled={busy}>
                            {busy && (
                                <LoaderCircle size={16} className="spin" />
                            )}
                            {["upload", "bibliography"].includes(tab)
                                ? t("Tải lên", "Upload")
                                : t("Tạo", "Create")}
                        </button>
                    </footer>
                </form>
            </section>
        </div>
    );
}
