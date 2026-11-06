"use client";
import {
    forwardRef,
    useEffect,
    useImperativeHandle,
    useRef,
    useState,
} from "react";
import type { SuperDoc } from "superdoc";
import LegacyWordPreview from "./WordPreview";
import type { WordPreviewHandle } from "../lib/word-preview";
import { json, request } from "../lib/api";
import { errorText, translator } from "../lib/parity";
export type { WordPreviewHandle } from "../lib/word-preview";
type Metadata = {
    revision: string;
    fingerprint: string;
    edited: boolean;
    stale: boolean;
    snapshot: string;
};
type Props = {
    project: string;
    main: string;
    defaultMain: string;
    language: string;
    active: boolean;
    compiled: boolean;
    snapshot: string;
    sourceDirty: boolean;
    sourceSignal: string;
    onSynced: () => Promise<void>;
    saveSource: () => Promise<boolean>;
    onCompile: () => void;
};

export default forwardRef<WordPreviewHandle, Props>(
    function WordDocxPreview(props, ref) {
        const t = translator(props.language),
            host = useRef<HTMLDivElement>(null),
            toolbar = useRef<HTMLDivElement>(null),
            instance = useRef<SuperDoc | null>(null),
            metadata = useRef<Metadata | null>(null),
            legacyRef = useRef<WordPreviewHandle | null>(null),
            readyRef = useRef(false),
            edit = useRef(0),
            savedEdit = useRef(0),
            queue = useRef<Promise<unknown>>(Promise.resolve()),
            opening = useRef(false),
            requestedCompile = useRef(false);
        const [legacy, setLegacy] = useState(false),
            [documentFile, setDocumentFile] = useState<File | null>(null),
            [ready, setReady] = useState(false),
            [loading, setLoading] = useState(false),
            [dirty, setDirty] = useState(false),
            [error, setError] = useState(""),
            [current, setCurrent] = useState<Metadata | null>(null),
            [pages, setPages] = useState(0);
        const route = `/projects/${props.project}/docx-preview`;
        const revisionRoute = (revision: string) =>
            route + "/" + revision + "?main=" + encodeURIComponent(props.main);
        const latest = useRef(props);
        latest.current = props;

        async function open(reset = false) {
            if (opening.current) return;
            opening.current = true;
            setLoading(true);
            setError("");
            try {
                const value = await json<Metadata>(route, {
                    method: "POST",
                    body: JSON.stringify({
                        snapshot: latest.current.snapshot,
                        reset,
                        revision: metadata.current?.revision,
                    }),
                });
                if (
                    metadata.current?.revision !== value.revision ||
                    !documentFile
                ) {
                    const response = await request(
                        revisionRoute(value.revision),
                    );
                    metadata.current = value;
                    setCurrent(value);
                    edit.current = 0;
                    savedEdit.current = 0;
                    setDirty(false);
                    setDocumentFile(
                        new File([await response.blob()], "main.docx", {
                            type: "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                        }),
                    );
                } else {
                    metadata.current = value;
                    setCurrent(value);
                }
            } catch (e) {
                setError(errorText(e));
            } finally {
                opening.current = false;
                setLoading(false);
            }
        }

        useEffect(() => {
            // A saved DOCX has a new revision. Reload that revision on return,
            // rather than re-opening the initial File and losing saved edits.
            if (legacy || !props.active) {
                setDocumentFile(null);
                return;
            }
            if (props.snapshot && !dirty) void open();
            else if (!props.snapshot && !requestedCompile.current) {
                requestedCompile.current = true;
                props.onCompile();
            }
        }, [legacy, props.active, props.compiled, props.snapshot, dirty]);

        useEffect(() => {
            if (
                legacy ||
                !documentFile ||
                !host.current ||
                !toolbar.current ||
                !props.active
            )
                return;
            let disposed = false,
                editor: SuperDoc | null = null;
            readyRef.current = false;
            setReady(false);
            setError("");
            void import("superdoc")
                .then(({ SuperDoc }) => {
                    if (disposed || !host.current || !toolbar.current) return;
                    editor = new SuperDoc({
                        selector: host.current,
                        toolbar: toolbar.current,
                        document: documentFile,
                        documentMode: "editing",
                        contained: true,
                        telemetry: { enabled: false },
                        ui: {
                            comments: false,
                        },
                        modules: {
                            comments: false,
                        },
                        zoom: {
                            mode: "fit-width",
                            fitWidth: {
                                min: 25,
                                max: 250,
                            },
                        },
                        workerUrls: {
                            document: new URL(
                                "/superdoc-workers/document-worker.js",
                                window.location.origin,
                            ),
                        },
                        onReady: () => {
                            if (!disposed) {
                                readyRef.current = true;
                                setReady(true);
                                editor?.setZoomMode("fit-width");
                            }
                        },
                        onEditorUpdate: () => {
                            if (readyRef.current && !disposed) {
                                edit.current++;
                                setDirty(true);
                            }
                        },
                        onPaginationUpdate: ({ totalPages }) => {
                            if (!disposed) setPages(totalPages);
                        },
                        onContentError: ({ error }) => {
                            if (!disposed) setError(errorText(error));
                        },
                        onException: ({ error }) => {
                            if (!disposed) setError(errorText(error));
                        },
                    });
                    instance.current = editor;
                })
                .catch((e) => {
                    if (!disposed) setError(errorText(e));
                });
            return () => {
                disposed = true;
                readyRef.current = false;
                editor?.destroy();
                if (instance.current === editor) instance.current = null;
            };
        }, [documentFile, legacy, props.active]);

        function persist(): Promise<boolean> {
            const operation = queue.current
                .catch(() => {})
                .then(async () => {
                    if (edit.current === savedEdit.current) return true;
                    if (
                        !instance.current ||
                        !metadata.current ||
                        !readyRef.current
                    )
                        throw Error(
                            t(
                                "Trình Word chưa sẵn sàng để lưu.",
                                "Word editor is not ready to save.",
                            ),
                        );
                    const ticket = edit.current;
                    const blob = await instance.current.export({
                        triggerDownload: false,
                    });
                    if (!(blob instanceof Blob))
                        throw Error("DOCX export did not return bytes");
                    const value = await json<Metadata>(
                        revisionRoute(metadata.current.revision),
                        {
                            method: "PUT",
                            headers: {
                                "Content-Type":
                                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                            },
                            body: blob,
                        },
                    );
                    metadata.current = value;
                    setCurrent(value);
                    savedEdit.current = ticket;
                    setDirty(edit.current !== ticket);
                    return true;
                });
            queue.current = operation;
            return operation.catch((e) => {
                setError(errorText(e));
                return false;
            });
        }
        useEffect(() => {
            if (!dirty || legacy) return;
            const timer = setTimeout(() => {
                void persist();
            }, 1000);
            return () => clearTimeout(timer);
        });
        useImperativeHandle(ref, () => ({
            flush: () =>
                legacy
                    ? legacyRef.current?.flush() || Promise.resolve(true)
                    : persist(),
        }));
        useEffect(() => {
            const warn = (event: BeforeUnloadEvent) => {
                if (!dirty) return;
                event.preventDefault();
                event.returnValue = "";
            };
            window.addEventListener("beforeunload", warn);
            return () => window.removeEventListener("beforeunload", warn);
        }, [dirty]);

        return (
            <div
                className="native-word-preview"
                onKeyDownCapture={(event) => {
                    if (
                        (event.ctrlKey || event.metaKey) &&
                        event.key.toLowerCase() === "s" &&
                        !legacy
                    ) {
                        event.preventDefault();
                        event.stopPropagation();
                        void persist();
                    }
                }}
            >
                <div className="native-word-actions">
                    <button
                        aria-pressed={!legacy}
                        onClick={async () => {
                            if (!legacy || (await legacyRef.current?.flush()))
                                setLegacy(false);
                        }}
                    >
                        {t("Word phân trang", "Paginated Word")}
                    </button>
                    <button
                        aria-pressed={legacy}
                        disabled={props.main !== props.defaultMain}
                        title={
                            props.main !== props.defaultMain
                                ? t(
                                      "Đồng bộ chữ LaTeX hiện áp dụng cho tệp chính; bản Word phân trang vẫn chỉnh sửa được.",
                                      "LaTeX text sync supports the project main file; paginated Word remains editable.",
                                  )
                                : undefined
                        }
                        onClick={async () => {
                            if (await persist()) setLegacy(true);
                        }}
                    >
                        {t("Đồng bộ chữ LaTeX", "LaTeX text sync")}
                    </button>
                    {!legacy && (
                        <>
                            <button
                                disabled={!ready || !dirty}
                                onClick={() => void persist()}
                            >
                                {t("Lưu Word", "Save Word")}
                            </button>
                            <button
                                disabled={loading || !props.compiled}
                                onClick={async () => {
                                    if (
                                        confirm(
                                            t(
                                                "Tạo lại DOCX từ PDF? Bản hiện tại được giữ làm bản trước đó.",
                                                "Regenerate DOCX from PDF? The current file is kept as the previous version.",
                                            ),
                                        ) &&
                                        (await persist())
                                    )
                                        await open(true);
                                }}
                            >
                                {t("Tạo lại từ PDF", "Regenerate from PDF")}
                            </button>
                            <button
                                disabled={!ready}
                                onClick={() =>
                                    instance.current?.setZoomMode("fit-width")
                                }
                            >
                                {t("Vừa chiều rộng", "Fit width")}
                            </button>
                            <span
                                className="native-word-status"
                                title={t(
                                    "DOCX từ bộ chuyển đổi gốc, chỉnh sửa và lưu trực tiếp. Chỉnh sửa Word được giữ trong DOCX; không ghi đè mã LaTeX.",
                                    "DOCX from the original converter, edited and saved directly. Word edits stay in DOCX and do not overwrite LaTeX.",
                                )}
                            >
                                {loading
                                    ? t("Đang chuyển PDF…", "Converting PDF…")
                                    : !ready && documentFile
                                      ? t("Đang phân trang…", "Paginating…")
                                      : dirty
                                        ? t("Chưa lưu Word", "Unsaved Word")
                                        : ready
                                          ? t("Đã lưu Word", "Word saved")
                                          : ""}
                                {pages > 0
                                    ? ` · ${pages} ${t("trang", "pages")}`
                                    : ""}
                                <span
                                    className="native-word-info-icon"
                                    aria-label="info"
                                >
                                    {" "}
                                    ℹ
                                </span>
                            </span>
                        </>
                    )}
                </div>
                {legacy ? (
                    <LegacyWordPreview {...props} ref={legacyRef} />
                ) : (
                    <>
                        <p className="native-word-note">
                            {t(
                                "DOCX từ bộ chuyển đổi gốc, chỉnh sửa và lưu trực tiếp. Chỉnh sửa Word được giữ trong DOCX; không ghi đè mã LaTeX.",
                                "DOCX from the original converter, edited and saved directly. Word edits stay in DOCX and do not overwrite LaTeX.",
                            )}
                        </p>
                        {(current?.stale || props.sourceDirty) && (
                            <p className="native-word-warning">
                                {t(
                                    "Nguồn/PDF đã đổi. Bản Word đang sửa được giữ; compile rồi chọn Tạo lại từ PDF nếu muốn thay thế.",
                                    "Source/PDF changed. Your Word draft is preserved; compile and regenerate from PDF to replace it.",
                                )}
                            </p>
                        )}
                        {error && (
                            <p className="word-error" role="alert">
                                {error}
                            </p>
                        )}
                        {!documentFile && !loading && (
                            <div className="native-word-empty">
                                <p>
                                    {t(
                                        "Cần PDF compile thành công để tạo Word đúng bộ chuyển đổi.",
                                        "Compile a current PDF to create Word with the original converter.",
                                    )}
                                </p>
                                <button onClick={props.onCompile}>
                                    {t("Biên dịch PDF", "Compile PDF")}
                                </button>
                            </div>
                        )}
                        <div ref={toolbar} className="native-word-toolbar" />
                        <div
                            ref={host}
                            className="native-word-host"
                            data-docx-ready={ready ? "true" : "false"}
                        />
                    </>
                )}
            </div>
        );
    },
);
