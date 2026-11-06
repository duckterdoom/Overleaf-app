"use client";
import { useEffect, useRef, useState } from "react";
import type {
    PDFDocumentProxy,
    PDFDocumentLoadingTask,
    RenderTask,
} from "pdfjs-dist";
import { ChevronDown, ChevronUp, FileText, Minus, Plus } from "lucide-react";
import type { PageGeometry, PdfPoint } from "../lib/api";
import { translator } from "../lib/parity";
import { toViewport, fromViewport } from "../lib/pdfGeometry";

type Link = {
    x: number;
    y: number;
    w: number;
    h: number;
    dest: string | unknown[];
};
function Sheet({
    pdf,
    page,
    width,
    size,
    root,
    go,
    geometry,
    marker,
    inverse,
    language,
}: {
    pdf: PDFDocumentProxy;
    page: number;
    width: number;
    size: [number, number];
    root: React.RefObject<HTMLDivElement | null>;
    go: (destination: string | unknown[]) => void;
    geometry?: PageGeometry;
    marker?: PdfPoint;
    inverse?: (point: PdfPoint) => void;
    language: string;
}) {
    const wrapper = useRef<HTMLDivElement>(null),
        canvas = useRef<HTMLCanvasElement>(null);
    const [visible, setVisible] = useState(false),
        [links, setLinks] = useState<Link[]>([]);
    const [error, setError] = useState("");
    const [highlight, setHighlight] = useState<{ x: number; y: number } | null>(
        null,
    );
    const height = (width * size[1]) / size[0];
    useEffect(() => {
        let disposed = false;
        setHighlight(null);
        if (marker && geometry)
            void pdf.getPage(page + 1).then((source) => {
                const viewport = source.getViewport({
                    scale: width / source.getViewport({ scale: 1 }).width,
                });
                const [x, y] = toViewport(viewport, geometry, marker);
                if (!disposed) setHighlight({ x, y });
            });
        return () => {
            disposed = true;
        };
    }, [pdf, page, width, marker, geometry]);
    useEffect(() => {
        const observer = new IntersectionObserver(
            ([entry]) => setVisible(entry.isIntersecting),
            { root: root.current, rootMargin: "180px" },
        );
        if (wrapper.current) observer.observe(wrapper.current);
        return () => observer.disconnect();
    }, [root]);
    useEffect(() => {
        let disposed = false,
            task: RenderTask | undefined;
        setLinks([]);
        setError("");
        if (!visible) {
            if (canvas.current) {
                canvas.current.width = 0;
                canvas.current.height = 0;
            }
            return;
        }
        void (async () => {
            const source = await pdf.getPage(page + 1);
            if (disposed || !canvas.current) return;
            const scale = width / source.getViewport({ scale: 1 }).width;
            const ratio = Math.max(
                0.25,
                Math.min(
                    devicePixelRatio || 1,
                    2,
                    Math.sqrt(10_000_000 / (width * height)),
                ),
            );
            const viewport = source.getViewport({ scale: scale * ratio });
            canvas.current.width = Math.ceil(viewport.width);
            canvas.current.height = Math.ceil(viewport.height);
            task = source.render({ canvas: canvas.current, viewport });
            await task.promise;
            if (disposed) return;
            const annotations = await source.getAnnotations();
            const css = source.getViewport({ scale });
            setLinks(
                annotations
                    .filter((item) => item.subtype === "Link" && item.dest)
                    .map((item) => {
                        const [x1, y1] = css.convertToViewportPoint(
                            item.rect[0],
                            item.rect[1],
                        );
                        const [x2, y2] = css.convertToViewportPoint(
                            item.rect[2],
                            item.rect[3],
                        );
                        return {
                            x: Math.min(x1, x2),
                            y: Math.min(y1, y2),
                            w: Math.abs(x1 - x2),
                            h: Math.abs(y1 - y2),
                            dest: item.dest,
                        };
                    }),
            );
        })().catch((error) => {
            if (!disposed) setError(String(error));
        });
        return () => {
            disposed = true;
            task?.cancel();
            if (canvas.current) {
                canvas.current.width = 0;
                canvas.current.height = 0;
            }
        };
    }, [pdf, page, visible, width, height]);
    return (
        <div
            ref={wrapper}
            data-pdf-page={page}
            className="pdf-sheet"
            style={{ width, height }}
            onDoubleClick={async (event) => {
                if (
                    !inverse ||
                    !geometry ||
                    (event.target as HTMLElement).closest(".pdf-link")
                )
                    return;
                const rect = event.currentTarget.getBoundingClientRect();
                const x = event.clientX - rect.left,
                    y = event.clientY - rect.top;
                const source = await pdf.getPage(page + 1),
                    viewport = source.getViewport({
                        scale: width / source.getViewport({ scale: 1 }).width,
                    });
                inverse(fromViewport(viewport, geometry, page, x, y));
            }}
        >
            <span className="page-placeholder">
                {error || `${language === "en" ? "Page" : "Trang"} ${page + 1}`}
            </span>
            <canvas
                ref={canvas}
                aria-label={`${language === "en" ? "PDF page" : "Trang PDF"} ${page + 1}`}
                style={{ width, height }}
            />
            {links.map((link, i) => (
                <button
                    key={i}
                    aria-label={
                        language === "en"
                            ? "Go to PDF destination"
                            : "Đến mục trong PDF"
                    }
                    className="pdf-link"
                    style={{
                        left: link.x,
                        top: link.y,
                        width: link.w,
                        height: link.h,
                    }}
                    onClick={() => go(link.dest)}
                />
            ))}
            {highlight && (
                <span
                    className="pdf-sync-marker"
                    style={{
                        left: Math.max(0, highlight.x - 4),
                        top: Math.max(0, highlight.y - 13),
                    }}
                />
            )}
        </div>
    );
}

export default function PdfViewer({
    data,
    stale,
    geometry = [],
    target,
    onInverse,
    language = "vi",
}: {
    data: Uint8Array | null;
    stale: boolean;
    geometry?: PageGeometry[];
    target?: PdfPoint | null;
    onInverse?: (point: PdfPoint) => void;
    language?: string;
}) {
    const t = translator(language);
    const root = useRef<HTMLDivElement>(null);
    const [pdf, setPdf] = useState<PDFDocumentProxy | null>(null),
        [sizes, setSizes] = useState<[number, number][]>([]);
    const [available, setAvailable] = useState(600),
        [zoom, setZoom] = useState(1),
        [page, setPage] = useState(0),
        [error, setError] = useState("");
    const back = useRef<number[]>([]),
        [backCount, setBackCount] = useState(0);
    const remember = () => {
        if (root.current) {
            back.current.push(root.current.scrollTop);
            if (back.current.length > 100) back.current.shift();
            setBackCount(back.current.length);
        }
    };
    useEffect(() => {
        if (!pdf || !target || !geometry[target.page] || stale) return;
        let disposed = false;
        void pdf.getPage(target.page + 1).then((source) => {
            if (disposed) return;
            const meta = geometry[target.page],
                viewport = source.getViewport({ scale: 1 });
            const [, y] = toViewport(viewport, meta, target);
            remember();
            navigate(target.page, Math.max(0, y / viewport.height - 0.12));
        });
        return () => {
            disposed = true;
        };
    }, [pdf, target, geometry, stale]);
    useEffect(() => {
        const observer = new ResizeObserver(([entry]) =>
            setAvailable(Math.max(180, entry.contentRect.width - 48)),
        );
        if (root.current) observer.observe(root.current);
        return () => observer.disconnect();
    }, []);
    useEffect(() => {
        let cancelled = false,
            loaded: PDFDocumentProxy | undefined,
            loading: PDFDocumentLoadingTask | undefined;
        setError("");
        setPdf(null);
        setSizes([]);
        setPage(0);
        back.current = [];
        setBackCount(0);
        if (!data) return;
        void (async () => {
            const library = await import("pdfjs-dist");
            if (cancelled) return;
            library.GlobalWorkerOptions.workerSrc = "/pdf.worker.min.mjs";
            loading = library.getDocument({ data: data.slice() });
            loaded = await loading.promise;
            const dimensions: [number, number][] = [];
            for (let p = 1; p <= loaded.numPages; p++) {
                if (cancelled) return;
                const sheet = await loaded.getPage(p),
                    viewport = sheet.getViewport({ scale: 1 });
                dimensions.push([viewport.width, viewport.height]);
            }
            if (!cancelled) {
                setSizes(dimensions);
                setPdf(loaded);
            }
        })().catch((error) => {
            if (!cancelled) setError(String(error));
        });
        return () => {
            cancelled = true;
            void loading?.destroy();
        };
    }, [data]);
    function navigate(index: number, fraction = 0) {
        const target = root.current?.querySelector<HTMLElement>(
            `[data-pdf-page="${index}"]`,
        );
        if (target && root.current) {
            root.current.scrollTo({
                top: target.offsetTop + fraction * target.offsetHeight - 20,
                behavior: "instant",
            });
            setPage(index);
        }
    }
    async function go(destination: string | unknown[]) {
        if (!pdf) return;
        try {
            const dest =
                typeof destination === "string"
                    ? await pdf.getDestination(destination)
                    : destination;
            if (!dest) return;
            const index =
                typeof dest[0] === "number"
                    ? dest[0]
                    : await pdf.getPageIndex(
                          dest[0] as { num: number; gen: number },
                      );
            const target = await pdf.getPage(index + 1),
                viewport = target.getViewport({ scale: 1 });
            const y =
                typeof dest[3] === "number"
                    ? viewport.convertToViewportPoint(0, dest[3])[1] /
                      viewport.height
                    : 0;
            remember();
            navigate(index, y);
        } catch (error) {
            setError(String(error));
        }
    }
    return (
        <div className="pdf-viewer">
            <div className="preview-toolbar">
                <span>
                    <FileText size={15} /> PDF preview
                </span>
                <div className="inline-controls">
                    <button
                        title={
                            language === "en" ? "Back" : "Quay lại vị trí trước"
                        }
                        disabled={!backCount}
                        onClick={() => {
                            const top = back.current.pop();
                            if (root.current && top !== undefined)
                                root.current.scrollTo({
                                    top,
                                    behavior: "auto",
                                });
                            setBackCount(back.current.length);
                        }}
                    >
                        ↶
                    </button>
                    <button
                        title={t("Trang trước", "Previous page")}
                        onClick={() => navigate(Math.max(0, page - 1))}
                    >
                        <ChevronUp size={16} />
                    </button>
                    <span>{pdf ? `${page + 1} / ${pdf.numPages}` : "—"}</span>
                    <button
                        title={t("Trang sau", "Next page")}
                        onClick={() =>
                            navigate(Math.min(sizes.length - 1, page + 1))
                        }
                    >
                        <ChevronDown size={16} />
                    </button>
                    <button
                        title={t("Thu nhỏ", "Zoom out")}
                        onClick={() => setZoom(Math.max(0.5, zoom - 0.1))}
                    >
                        <Minus size={16} />
                    </button>
                    <span>{Math.round(zoom * 100)}%</span>
                    <button
                        title={t("Phóng to", "Zoom in")}
                        onClick={() => setZoom(Math.min(2, zoom + 0.1))}
                    >
                        <Plus size={16} />
                    </button>
                </div>
            </div>
            {data && stale && (
                <div className="stale-note">
                    {t(
                        "Nguồn đã đổi hoặc compile chưa thành công. PDF này là bản trước; hãy biên dịch lại để xuất.",
                        "Source changed or compilation failed. This is the previous PDF; recompile before exporting.",
                    )}
                </div>
            )}
            <div
                className="pdf-scroll"
                ref={root}
                onScroll={() => {
                    if (!root.current) return;
                    let nearest = 0,
                        distance = Infinity;
                    root.current
                        .querySelectorAll<HTMLElement>("[data-pdf-page]")
                        .forEach((sheet) => {
                            const delta = Math.abs(
                                sheet.offsetTop - root.current!.scrollTop,
                            );
                            if (delta < distance) {
                                distance = delta;
                                nearest = Number(sheet.dataset.pdfPage);
                            }
                        });
                    setPage(nearest);
                }}
            >
                {error && (
                    <div role="alert" className="empty-preview">
                        {error}
                    </div>
                )}
                {!pdf && !error && (
                    <div className="empty-preview">
                        <div className="empty-pdf-icon">
                            <FileText size={38} strokeWidth={1.2} />
                        </div>
                        <h3>
                            {data
                                ? t("Đang tải PDF…", "Loading PDF…")
                                : t(
                                      "Trang giấy tiếp theo của bạn",
                                      "Your next page",
                                  )}
                        </h3>
                        <p>
                            {t(
                                "Bấm Biên dịch để xem tài liệu tại đây.",
                                "Recompile to preview your document here.",
                            )}
                        </p>
                    </div>
                )}
                {pdf &&
                    sizes.map((size, index) => (
                        <Sheet
                            key={index}
                            pdf={pdf}
                            language={language}
                            page={index}
                            size={size}
                            width={available * zoom}
                            root={root}
                            go={(destination) => void go(destination)}
                            geometry={geometry[index]}
                            marker={target?.page === index ? target : undefined}
                            inverse={stale ? undefined : onInverse}
                        />
                    ))}
            </div>
        </div>
    );
}
