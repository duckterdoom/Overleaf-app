"use client";
import { useState, useRef } from "react";
import { FolderOpen, Upload, LoaderCircle, FileArchive } from "lucide-react";
import type { Project } from "../lib/api";
import { request } from "../lib/api";
import {
    action,
    native,
    errorText,
    translator,
    chooseDirectory,
} from "../lib/parity";
import { createStoredZip } from "../lib/zip";

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
    onTitleChange,
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
    onTitleChange?: (value: string) => void;
}) {
    const t = translator(language);
    const [url, setUrl] = useState("");
    const [busy, setBusy] = useState(false);
    const [statusText, setStatusText] = useState("");
    const [error, setError] = useState("");
    const [isDragging, setIsDragging] = useState(false);
    const dragCounter = useRef(0);

    // Hidden input refs for browser file picking
    const zipInputRef = useRef<HTMLInputElement | null>(null);
    const wordInputRef = useRef<HTMLInputElement | null>(null);
    const mdInputRef = useRef<HTMLInputElement | null>(null);
    const folderInputRef = useRef<HTMLInputElement | null>(null);

    // Native file picker (pywebview)
    async function nativeImport(kind: string) {
        setBusy(true);
        setError("");
        setStatusText(kind.toUpperCase());
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
            if (!("cancelled" in value) || !value.cancelled) {
                onImported(value as Project);
            }
        } catch (err) {
            setError(errorText(err));
        } finally {
            setBusy(false);
            setStatusText("");
        }
    }

    // Direct path import (when native file.path is available from drop)
    async function importFromPath(
        kind: string,
        filePath: string,
        suggestedTitle: string,
    ) {
        setBusy(true);
        setError("");
        setStatusText(suggestedTitle);
        const finalTitle = title.trim() || suggestedTitle;
        if (!title.trim() && onTitleChange) {
            onTitleChange(finalTitle);
        }
        try {
            const value = await action<Project>("import-path", {
                kind,
                path: filePath,
                title: finalTitle,
                parent,
                language: sourceLanguage,
            });
            if (!("cancelled" in value) || !value.cancelled) {
                onImported(value as Project);
            }
        } catch (err) {
            setError(errorText(err));
        } finally {
            setBusy(false);
            setStatusText("");
        }
    }

    // Stream upload import (when running in web browser without native file.path)
    async function uploadImportFile(
        kind: string,
        fileData: Blob | Uint8Array,
        fileName: string,
        suggestedTitle: string,
    ) {
        setBusy(true);
        setError("");
        setStatusText(fileName);
        const finalTitle = title.trim() || suggestedTitle;
        if (!title.trim() && onTitleChange) {
            onTitleChange(finalTitle);
        }
        try {
            const params = new URLSearchParams({
                kind,
                filename: fileName,
                title: finalTitle,
                parent,
                language: sourceLanguage,
            });
            const res = await request(
                `/projects/import-file?${params.toString()}`,
                {
                    method: "POST",
                    headers: { "Content-Type": "application/octet-stream" },
                    body: fileData as BodyInit,
                },
            );
            const project = (await res.json()) as Project;
            onImported(project);
        } catch (err) {
            setError(errorText(err));
        } finally {
            setBusy(false);
            setStatusText("");
        }
    }

    // Process dropped files or directory
    async function processDropped(
        items: DataTransferItemList | null,
        files: FileList | null,
    ) {
        if (!files || files.length === 0) return;

        setError("");
        setBusy(true);

        try {
            // Check if user dropped a directory entry
            const firstItem = items?.[0];
            const entry = firstItem?.webkitGetAsEntry
                ? firstItem.webkitGetAsEntry()
                : null;

            if (entry && entry.isDirectory) {
                // Folder dropped
                const folderName = entry.name;
                const firstFile = files[0] as unknown as { path?: string };

                if (firstFile?.path) {
                    // Native desktop environment with file.path
                    const rawPath = firstFile.path;
                    const normalized = rawPath.replace(/\\/g, "/");
                    const idx = normalized.lastIndexOf(`/${folderName}/`);
                    const folderPath =
                        idx !== -1
                            ? rawPath.slice(0, idx + folderName.length + 1)
                            : firstFile.path.endsWith(folderName)
                              ? firstFile.path
                              : rawPath;

                    await importFromPath("folder", folderPath, folderName);
                    return;
                }

                // Browser environment without native file.path: recursively read directory and zip
                setStatusText(
                    t(
                        `Đang quét thư mục ${folderName}…`,
                        `Scanning folder ${folderName}…`,
                    ),
                );
                const collected: { name: string; data: Uint8Array }[] = [];

                async function walk(dirEntry: FileSystemEntry, prefix = "") {
                    const relativePath = prefix
                        ? `${prefix}/${dirEntry.name}`
                        : dirEntry.name;
                    if (dirEntry.isFile) {
                        const fileObj = await new Promise<File>(
                            (resolve, reject) =>
                                (dirEntry as FileSystemFileEntry).file(
                                    resolve,
                                    reject,
                                ),
                        );
                        const buf = new Uint8Array(await fileObj.arrayBuffer());
                        collected.push({ name: relativePath, data: buf });
                    } else if (dirEntry.isDirectory) {
                        const reader = (
                            dirEntry as FileSystemDirectoryEntry
                        ).createReader();
                        while (true) {
                            const entries = await new Promise<
                                FileSystemEntry[]
                            >((resolve, reject) =>
                                reader.readEntries(resolve, reject),
                            );
                            if (!entries.length) break;
                            for (const child of entries) {
                                await walk(child, relativePath);
                            }
                        }
                    }
                }

                await walk(entry);
                if (collected.length === 0) {
                    throw new Error(
                        t(
                            "Thư mục trống hoặc không thể đọc.",
                            "Folder is empty or unreadable.",
                        ),
                    );
                }

                setStatusText(
                    t(
                        `Đang nén ${folderName} để nhập…`,
                        `Compressing ${folderName} for import…`,
                    ),
                );
                const zipBytes = createStoredZip(collected);
                await uploadImportFile(
                    "zip",
                    zipBytes,
                    `${folderName}.zip`,
                    folderName,
                );
                return;
            }

            // Single file dropped
            if (files.length === 1) {
                const file = files[0];
                const nativePath = (file as unknown as { path?: string })?.path;
                const lower = file.name.toLowerCase();
                const stem = file.name.replace(/\.[^/.]+$/, "");

                let kind = "zip";
                if (lower.endsWith(".docx")) {
                    kind = "word";
                } else if (
                    lower.endsWith(".md") ||
                    lower.endsWith(".markdown")
                ) {
                    kind = "markdown";
                } else if (lower.endsWith(".zip")) {
                    kind = "zip";
                } else if (lower.endsWith(".tex")) {
                    // Single .tex file: bundle into a zip project
                    const buf = new Uint8Array(await file.arrayBuffer());
                    const zipBytes = createStoredZip([
                        {
                            name:
                                file.name.toLowerCase() === "main.tex"
                                    ? "main.tex"
                                    : "main.tex",
                            data: buf,
                        },
                    ]);
                    await uploadImportFile(
                        "zip",
                        zipBytes,
                        `${stem}.zip`,
                        stem,
                    );
                    return;
                } else {
                    throw new Error(
                        t(
                            "Định dạng tệp không được hỗ trợ. Hãy thả tệp .zip, .docx, .md hoặc thư mục dự án.",
                            "Unsupported file format. Please drop a .zip, .docx, .md file or project folder.",
                        ),
                    );
                }

                if (nativePath) {
                    await importFromPath(kind, nativePath, stem);
                } else {
                    await uploadImportFile(kind, file, file.name, stem);
                }
                return;
            }

            // Multiple files dropped together: bundle into a zip project
            setStatusText(
                t(
                    `Đang gom ${files.length} tệp…`,
                    `Packing ${files.length} files…`,
                ),
            );
            const collected: { name: string; data: Uint8Array }[] = [];
            for (let i = 0; i < files.length; i++) {
                const f = files[i];
                const buf = new Uint8Array(await f.arrayBuffer());
                collected.push({ name: f.name, data: buf });
            }
            const projectName =
                files[0].name.replace(/\.[^/.]+$/, "") || "imported-project";
            const zipBytes = createStoredZip(collected);
            await uploadImportFile(
                "zip",
                zipBytes,
                `${projectName}.zip`,
                projectName,
            );
        } catch (err) {
            setError(errorText(err));
            setBusy(false);
            setStatusText("");
        }
    }

    // Drag handlers
    const handleDragEnter = (e: React.DragEvent) => {
        e.preventDefault();
        e.stopPropagation();
        dragCounter.current++;
        if (e.dataTransfer.items && e.dataTransfer.items.length > 0) {
            setIsDragging(true);
        }
    };

    const handleDragOver = (e: React.DragEvent) => {
        e.preventDefault();
        e.stopPropagation();
        e.dataTransfer.dropEffect = "copy";
        if (!isDragging) setIsDragging(true);
    };

    const handleDragLeave = (e: React.DragEvent) => {
        e.preventDefault();
        e.stopPropagation();
        dragCounter.current--;
        if (dragCounter.current <= 0) {
            dragCounter.current = 0;
            setIsDragging(false);
        }
    };

    const handleDrop = async (e: React.DragEvent) => {
        e.preventDefault();
        e.stopPropagation();
        dragCounter.current = 0;
        setIsDragging(false);
        await processDropped(e.dataTransfer.items, e.dataTransfer.files);
    };

    // Button click: desktop pywebview or browser input fallback
    const handleButtonClick = (kind: string) => {
        if (typeof window !== "undefined" && window.pywebview?.api) {
            void nativeImport(kind);
            return;
        }

        // Browser fallback: trigger hidden input
        if (kind === "zip") zipInputRef.current?.click();
        else if (kind === "word") wordInputRef.current?.click();
        else if (kind === "markdown") mdInputRef.current?.click();
        else if (kind === "folder") folderInputRef.current?.click();
    };

    const handleFileInput = (
        e: React.ChangeEvent<HTMLInputElement>,
        kind: string,
    ) => {
        const fileList = e.target.files;
        if (!fileList || fileList.length === 0) return;
        const file = fileList[0];
        const stem = file.name.replace(/\.[^/.]+$/, "");
        void uploadImportFile(kind, file, file.name, stem);
        e.target.value = "";
    };

    const handleFolderInput = (e: React.ChangeEvent<HTMLInputElement>) => {
        const fileList = e.target.files;
        if (!fileList || fileList.length === 0) return;
        void processDropped(null, fileList);
        e.target.value = "";
    };

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
                            const folder = await chooseDirectory(
                                parent,
                                language,
                            );
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

            {/* Drag & Drop Import Box */}
            <div
                className={`import-dropzone ${isDragging ? "dragging" : ""} ${busy ? "busy" : ""}`}
                onDragEnter={handleDragEnter}
                onDragOver={handleDragOver}
                onDragLeave={handleDragLeave}
                onDrop={handleDrop}
            >
                <div className="import-dropzone-icon">
                    {busy ? (
                        <LoaderCircle size={28} className="spin" />
                    ) : isDragging ? (
                        <FileArchive size={28} />
                    ) : (
                        <Upload size={28} />
                    )}
                </div>

                <div className="import-dropzone-text">
                    <h4>
                        {busy
                            ? t(
                                  `Đang nhập dự án: ${statusText || "vui lòng chờ"}…`,
                                  `Importing project: ${statusText || "please wait"}…`,
                              )
                            : isDragging
                              ? t(
                                    "Thả tệp hoặc thư mục vào đây để tạo dự án ngay!",
                                    "Drop file or folder here to import project!",
                                )
                              : t(
                                    "Kéo thả tệp ZIP, Word (.docx), Markdown hoặc thư mục vào đây",
                                    "Drag & drop ZIP, Word (.docx), Markdown, or project folder here",
                                )}
                    </h4>
                    {!busy && (
                        <p>
                            {t(
                                "Tự động nhận diện định dạng và tạo dự án · Hoặc chọn từ máy tính:",
                                "Auto-detects format and creates project · Or choose from computer:",
                            )}
                        </p>
                    )}
                </div>

                <div className="import-project-actions">
                    <button
                        type="button"
                        className="secondary"
                        disabled={busy}
                        onClick={onExample}
                    >
                        {t("Dự án ví dụ", "Example project")}
                    </button>
                    <button
                        type="button"
                        className="secondary"
                        disabled={busy}
                        onClick={() => handleButtonClick("zip")}
                    >
                        <Upload size={14} />
                        ZIP
                    </button>
                    <button
                        type="button"
                        className="secondary"
                        disabled={busy}
                        onClick={() => handleButtonClick("word")}
                    >
                        <Upload size={14} />
                        Word (.docx)
                    </button>
                    <button
                        type="button"
                        className="secondary"
                        disabled={busy}
                        onClick={() => handleButtonClick("markdown")}
                    >
                        <Upload size={14} />
                        Markdown
                    </button>
                    <button
                        type="button"
                        className="secondary"
                        disabled={busy}
                        onClick={() => handleButtonClick("folder")}
                    >
                        <FolderOpen size={14} />
                        {t("Thư mục có sẵn", "Existing folder")}
                    </button>
                </div>

                {busy && (
                    <button
                        className="secondary cancel-import-btn"
                        type="button"
                        onClick={() => void action("io-cancel")}
                    >
                        {t("Hủy nhập", "Cancel import")}
                    </button>
                )}

                {/* Hidden browser file inputs for click fallback */}
                <input
                    ref={zipInputRef}
                    type="file"
                    accept=".zip"
                    hidden
                    onChange={(e) => handleFileInput(e, "zip")}
                />
                <input
                    ref={wordInputRef}
                    type="file"
                    accept=".docx"
                    hidden
                    onChange={(e) => handleFileInput(e, "word")}
                />
                <input
                    ref={mdInputRef}
                    type="file"
                    accept=".md,.markdown"
                    hidden
                    onChange={(e) => handleFileInput(e, "markdown")}
                />
                <input
                    ref={folderInputRef}
                    type="file"
                    // @ts-expect-error webkitdirectory is standard for folder inputs
                    webkitdirectory=""
                    hidden
                    onChange={handleFolderInput}
                />
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
                        onClick={() => void nativeImport("github")}
                    >
                        {t("Nhập repo", "Import repo")}
                    </button>
                </div>
            </label>

            {error && (
                <p className="form-error" role="alert">
                    {error}
                </p>
            )}
        </div>
    );
}
