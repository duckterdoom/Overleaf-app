"use client";
import { useState } from "react";
import type { EditorView } from "@codemirror/view";
import { undo, redo } from "@codemirror/commands";
import {
    Undo2,
    Redo2,
    Bold,
    Italic,
    Sigma,
    Link2,
    MessageSquarePlus,
    Tag,
    Quote,
    ImagePlus,
    Table2,
    List,
    IndentDecrease,
    IndentIncrease,
    Check,
    X,
} from "lucide-react";
import { action, errorText, translator } from "../lib/parity";
import {
    hyperlink,
    reference,
    citation,
    figure,
    table,
    list,
    lineEdit,
} from "../lib/snippets";
type Kind =
    "math" | "link" | "reference" | "citation" | "figure" | "table" | "list";
type Dialog = {
    kind: Kind;
    file: string;
    project: string;
    document: string;
    from: number;
    to: number;
    selected: string;
};
export default function EditorToolbar({
    editor,
    project,
    file,
    language,
    readonly,
    dirty,
    save,
    onError,
    onComment,
}: {
    editor: React.RefObject<EditorView | null>;
    project: string;
    file: string;
    language: string;
    readonly: boolean;
    dirty: boolean;
    save: () => void;
    onError: (error: string) => void;
    onComment: () => void;
}) {
    const t = translator(language),
        [dialog, setDialog] = useState<Dialog | null>(null),
        [form, setForm] = useState<Record<string, string>>({}),
        [symbols, setSymbols] = useState<{
            labels: string[];
            citations: string[];
            images: string[];
        }>({ labels: [], citations: [], images: [] }),
        [error, setError] = useState("");
    function replace(before: string, after = "") {
        const view = editor.current;
        if (!view || readonly) return;
        const range = view.state.selection.main;
        const selected = view.state.sliceDoc(range.from, range.to);
        view.dispatch({
            changes: {
                from: range.from,
                to: range.to,
                insert: before + selected + after,
            },
            selection: {
                anchor: range.from + before.length,
                head: range.from + before.length + selected.length,
            },
        });
        view.focus();
    }
    function lines(mode: "increase" | "decrease" | "comment") {
        const view = editor.current;
        if (!view || readonly) return;
        const range = view.state.selection.main,
            edit = lineEdit(
                view.state.doc.toString(),
                range.anchor,
                range.head,
                mode,
            );
        view.dispatch({
            changes: {
                from: edit.start,
                to: edit.end,
                insert: edit.replacement,
            },
            selection: { anchor: edit.anchor, head: edit.caret },
        });
        view.focus();
    }
    async function open(kind: Kind) {
        const view = editor.current;
        if (!view || readonly) return;
        const range = view.state.selection.main;
        setError("");
        setDialog({
            kind,
            file,
            project,
            document: view.state.doc.toString(),
            from: range.from,
            to: range.to,
            selected: view.state.sliceDoc(range.from, range.to),
        });
        setForm({
            url: "https://",
            text: view.state.sliceDoc(range.from, range.to),
            command: "ref",
            target: "",
            width: "0.8",
            caption: "",
            label: "",
            rows: "2",
            columns: "3",
            alignment: "c",
            math: "inline",
            list: "itemize",
        });
        if (["reference", "citation", "figure"].includes(kind))
            try {
                const found = await action<typeof symbols>("symbols", {
                    project,
                    file,
                    content: view.state.doc.toString(),
                });
                setSymbols(found);
                setForm((form) => ({
                    ...form,
                    target:
                        kind === "reference"
                            ? found.labels[0] || ""
                            : kind === "citation"
                              ? found.citations[0] || ""
                              : found.images[0] || "",
                }));
            } catch (error) {
                setError(errorText(error));
            }
    }
    const titles: Record<Kind, string> = {
        math: t("Chèn công thức", "Insert math"),
        link: t("Chèn liên kết", "Insert link"),
        reference: t("Chèn tham chiếu chéo", "Insert cross reference"),
        citation: t("Chèn trích dẫn", "Insert citation"),
        figure: t("Chèn hình", "Insert figure"),
        table: t("Chèn bảng", "Insert table"),
        list: t("Chèn danh sách", "Insert list"),
    };
    function field(key: string, label: string, type = "text") {
        return (
            <label>
                {label}
                <input
                    type={type}
                    step={type === "number" ? "any" : undefined}
                    value={form[key] || ""}
                    onChange={(event) =>
                        setForm({ ...form, [key]: event.target.value })
                    }
                />
            </label>
        );
    }
    function choice(key: string, label: string, values: string[]) {
        return (
            <label>
                {label}
                <select
                    aria-label={label}
                    value={form[key]}
                    onChange={(event) =>
                        setForm({ ...form, [key]: event.target.value })
                    }
                >
                    {values.map((value) => (
                        <option key={value}>{value}</option>
                    ))}
                </select>
            </label>
        );
    }
    function insert(event: React.FormEvent) {
        event.preventDefault();
        const view = editor.current;
        if (!view || !dialog) return;
        try {
            if (
                readonly ||
                dialog.project !== project ||
                dialog.file !== file ||
                dialog.document !== view.state.doc.toString()
            )
                throw new Error(
                    t(
                        "Tài liệu đã đổi hoặc chỉ đọc; mở lại hộp thoại.",
                        "The document changed or is read-only; reopen the dialog.",
                    ),
                );
            let snippet = "";
            switch (dialog.kind) {
                case "link":
                    snippet = hyperlink(form.url, form.text);
                    break;
                case "reference":
                    snippet = reference(form.command, form.target);
                    break;
                case "citation":
                    snippet = citation(form.target);
                    break;
                case "figure":
                    if (!symbols.images.includes(form.target))
                        throw new Error(
                            t(
                                "Chọn ảnh trong dự án.",
                                "Select a project image.",
                            ),
                        );
                    snippet = figure(
                        form.target,
                        Number(form.width),
                        form.caption,
                        form.label,
                    );
                    break;
                case "table":
                    snippet = table(
                        Number(form.rows),
                        Number(form.columns),
                        form.alignment,
                        form.caption,
                        form.label,
                        language !== "en",
                    );
                    break;
                case "list":
                    snippet = list(form.list, dialog.selected);
                    break;
                case "math":
                    snippet =
                        form.math === "inline"
                            ? `$${dialog.selected}$`
                            : form.math === "display"
                              ? `\\[\n${dialog.selected}\n\\]`
                              : `\\begin{${form.math}}\n${dialog.selected}\n\\end{${form.math}}`;
            }
            view.dispatch({
                changes: { from: dialog.from, to: dialog.to, insert: snippet },
                selection: { anchor: dialog.from + snippet.length },
            });
            view.focus();
            setDialog(null);
        } catch (error) {
            setError(errorText(error));
        }
    }
    return (
        <>
            <div className="editor-toolbar full-toolbar">
                <button
                    title={t("Hoàn tác", "Undo")}
                    disabled={readonly}
                    onClick={() => {
                        if (editor.current) undo(editor.current);
                    }}
                >
                    <Undo2 size={15} />
                </button>
                <button
                    title={t("Làm lại", "Redo")}
                    disabled={readonly}
                    onClick={() => {
                        if (editor.current) redo(editor.current);
                    }}
                >
                    <Redo2 size={15} />
                </button>
                <select
                    aria-label={t("Cỡ chữ LaTeX", "LaTeX text size")}
                    disabled={readonly}
                    value=""
                    onChange={(event) =>
                        replace(`{\\${event.target.value} `, "}")
                    }
                >
                    <option value="">tT</option>
                    {[
                        "tiny",
                        "small",
                        "normalsize",
                        "large",
                        "Large",
                        "LARGE",
                        "huge",
                    ].map((value) => (
                        <option key={value}>{value}</option>
                    ))}
                </select>
                <button
                    id="editor-bold"
                    title={t("In đậm", "Bold")}
                    disabled={readonly}
                    onClick={() => replace("\\textbf{", "}")}
                >
                    <Bold size={15} />
                </button>
                <button
                    id="editor-italic"
                    title={t("In nghiêng", "Italic")}
                    disabled={readonly}
                    onClick={() => replace("\\textit{", "}")}
                >
                    <Italic size={15} />
                </button>
                {(
                    [
                        ["math", Sigma],
                        ["link", Link2],
                    ] as const
                ).map(([kind, Icon]) => (
                    <button
                        id={`editor-insert-${kind}`}
                        title={titles[kind]}
                        key={kind}
                        disabled={readonly}
                        onClick={() => void open(kind)}
                    >
                        <Icon size={16} />
                    </button>
                ))}
                <button
                    id="editor-add-comment"
                    title={t("Thêm nhận xét", "Add comment")}
                    disabled={readonly}
                    onClick={onComment}
                >
                    <MessageSquarePlus size={16} />
                </button>
                {(
                    [
                        ["reference", Tag],
                        ["citation", Quote],
                        ["figure", ImagePlus],
                        ["table", Table2],
                        ["list", List],
                    ] as const
                ).map(([kind, Icon]) => (
                    <button
                        id={`editor-insert-${kind}`}
                        title={titles[kind]}
                        key={kind}
                        disabled={readonly}
                        onClick={() => void open(kind)}
                    >
                        <Icon size={16} />
                    </button>
                ))}
                <button
                    id="editor-decrease-indent"
                    title={t("Giảm thụt lề", "Decrease indent")}
                    disabled={readonly}
                    onClick={() => lines("decrease")}
                >
                    <IndentDecrease size={17} />
                </button>
                <button
                    id="editor-increase-indent"
                    title={t("Tăng thụt lề", "Increase indent")}
                    disabled={readonly}
                    onClick={() => lines("increase")}
                >
                    <IndentIncrease size={17} />
                </button>
                <div className="header-spacer" />
                <button
                    className="save-source"
                    disabled={!dirty || readonly}
                    onClick={save}
                >
                    <Check size={13} />
                    {dirty ? t("Lưu", "Save") : t("Đã lưu", "Saved")}
                </button>
            </div>
            {dialog && (
                <div className="modal-backdrop">
                    <section
                        className="modal insert-modal"
                        role="dialog"
                        aria-modal="true"
                        aria-label={titles[dialog.kind]}
                    >
                        <header>
                            <h2>{titles[dialog.kind]}</h2>
                            <button
                                title={t("Đóng", "Close")}
                                onClick={() => setDialog(null)}
                            >
                                <X size={20} />
                            </button>
                        </header>
                        <form onSubmit={insert}>
                            <div className="feature-form">
                                {dialog.kind === "math" &&
                                    choice(
                                        "math",
                                        t("Dạng công thức", "Math format"),
                                        [
                                            "inline",
                                            "display",
                                            "equation",
                                            "align",
                                        ],
                                    )}
                                {dialog.kind === "link" && (
                                    <>
                                        {field("url", "URL")}
                                        {field(
                                            "text",
                                            t("Chữ hiển thị", "Link text"),
                                        )}
                                        <p className="help-text">
                                            {t(
                                                "Cần package hyperref.",
                                                "Requires hyperref.",
                                            )}
                                        </p>
                                    </>
                                )}
                                {["reference", "citation"].includes(
                                    dialog.kind,
                                ) && (
                                    <>
                                        <label>
                                            {t("Khóa / nhãn", "Key / label")}
                                            <input
                                                list="insert-symbols"
                                                value={form.target}
                                                onChange={(event) =>
                                                    setForm({
                                                        ...form,
                                                        target: event.target
                                                            .value,
                                                    })
                                                }
                                            />
                                            <datalist id="insert-symbols">
                                                {(dialog.kind === "reference"
                                                    ? symbols.labels
                                                    : symbols.citations
                                                ).map((value) => (
                                                    <option key={value}>
                                                        {value}
                                                    </option>
                                                ))}
                                            </datalist>
                                        </label>
                                        {dialog.kind === "reference" ? (
                                            choice(
                                                "command",
                                                t(
                                                    "Lệnh tham chiếu",
                                                    "Reference command",
                                                ),
                                                [
                                                    "ref",
                                                    "eqref",
                                                    "pageref",
                                                    "autoref",
                                                ],
                                            )
                                        ) : (
                                            <p className="help-text">
                                                {t(
                                                    "Nhập các khóa .bib cách nhau bằng dấu phẩy. Cần cấu hình bibliography/BibLaTeX.",
                                                    "Enter comma-separated .bib keys. Configure bibliography/BibLaTeX in the document.",
                                                )}
                                            </p>
                                        )}
                                    </>
                                )}
                                {dialog.kind === "figure" && (
                                    <>
                                        {choice(
                                            "target",
                                            t(
                                                "Ảnh trong dự án",
                                                "Project image",
                                            ),
                                            symbols.images,
                                        )}
                                        {field(
                                            "width",
                                            t(
                                                "Độ rộng 0.05–1 (phần linewidth)",
                                                "Width 0.05–1 (fraction of linewidth)",
                                            ),
                                            "number",
                                        )}
                                        <p className="help-text">
                                            {t(
                                                "Cần package graphicx. Tải ảnh lên dự án trước.",
                                                "Requires graphicx. Upload the image first.",
                                            )}
                                        </p>
                                    </>
                                )}
                                {dialog.kind === "table" && (
                                    <>
                                        {field(
                                            "rows",
                                            t(
                                                "Số hàng dữ liệu (1–50)",
                                                "Data rows (1–50)",
                                            ),
                                            "number",
                                        )}
                                        {field(
                                            "columns",
                                            t(
                                                "Số cột (1–20)",
                                                "Columns (1–20)",
                                            ),
                                            "number",
                                        )}
                                        {choice(
                                            "alignment",
                                            t("Căn lề", "Alignment"),
                                            ["l", "c", "r"],
                                        )}
                                    </>
                                )}
                                {["figure", "table"].includes(dialog.kind) && (
                                    <>
                                        {field(
                                            "caption",
                                            t("Chú thích", "Caption"),
                                        )}
                                        {field("label", t("Nhãn", "Label"))}
                                    </>
                                )}
                                {dialog.kind === "list" &&
                                    choice(
                                        "list",
                                        t("Loại danh sách", "List type"),
                                        ["itemize", "enumerate", "description"],
                                    )}
                                {error && (
                                    <p className="form-error" role="alert">
                                        {error}
                                    </p>
                                )}
                            </div>
                            <footer>
                                <button
                                    type="button"
                                    className="secondary"
                                    onClick={() => setDialog(null)}
                                >
                                    {t("Hủy", "Cancel")}
                                </button>
                                <button
                                    id="editor-insert-confirm"
                                    className="primary"
                                >
                                    {t("Chèn", "Insert")}
                                </button>
                            </footer>
                        </form>
                    </section>
                </div>
            )}
        </>
    );
}
