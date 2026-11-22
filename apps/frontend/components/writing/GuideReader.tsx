"use client";
import { useEffect, useId, useLayoutEffect, useRef, useState } from "react";
import { createPortal } from "react-dom";
import { BookOpen, ExternalLink, X } from "lucide-react";

import { GuideContent, guideHeadings } from "./GuideMarkdown";
export { GuideContent } from "./GuideMarkdown";

export type WritingGuide = {
    category?: "ielts" | "report" | "research";
    id: string;
    title: string;
    summary: string;
    task: 1 | 2;
    profiles: string[];
    revision: number;
    classIds?: string[];
    builtin: boolean;
    ownerName: string;
    sourceUrl: string | null;
    content?: string;
    sampleContent?: string;
    hasSample?: boolean;
};

export function GuideDocument({ guide }: { guide: WritingGuide }) {
    const [tab,setTab]=useState<"guide"|"sample">("guide");
    const text=tab==="sample"&&guide.sampleContent?guide.sampleContent:guide.content||"";
    const source =
        guide.sourceUrl &&
        /^https:\/\/docs\.google\.com\/document\/d\/[\w-]+\/edit$/.test(
            guide.sourceUrl,
        )
            ? guide.sourceUrl
            : null;
    const headings = guideHeadings(text);
    return (
        <>
            <div className="guide-document-heading">
                <span className="guide-badge">
                    {!guide.category || guide.category === "ielts"
                        ? `Task ${guide.task}`
                        : guide.category === "report"
                          ? "Report"
                          : "Research"}{" "}
                    · Teaching reference
                </span>
                <h2>{guide.title}</h2>
                <p>{guide.summary}</p>
                <small>
                    {guide.ownerName} ·{" "}
                    {guide.builtin ? "Default template" : "Tutor template"}
                    {guide.builtin && guide.revision > 0
                        ? " (customized)"
                        : ""}{" "}
                    · This guide does not change assessment criteria or
                    calculate a band.
                </small>
                {source && (
                    <a href={source} target="_blank" rel="noopener noreferrer">
                        <ExternalLink size={14} />
                        Original Google Doc
                    </a>
                )}
            </div>
            <nav className="guide-reading-tabs" aria-label="Teaching resource views"><button aria-pressed={tab==='guide'} onClick={()=>setTab("guide")}><BookOpen size={16}/>Writing guide</button><button disabled={!guide.sampleContent} aria-pressed={tab==='sample'} onClick={()=>setTab("sample")}>Sample writing{!guide.sampleContent?" (not added)":""}</button></nav>
            {headings.length > 0 && (
                <details className="guide-contents">
                    <summary>
                        In this guide · {headings.length} sections
                    </summary>
                    <nav aria-label="Guide contents">
                        {headings.map((h) => (
                            <button
                                key={h.index}
                                onClick={(e) =>
                                    e.currentTarget
                                        .closest(".guide-reading-panel")
                                        ?.querySelector(
                                            `#guide-section-${h.index}`,
                                        )
                                        ?.scrollIntoView({ block: "start" })
                                }
                            >
                                {h.title}
                            </button>
                        ))}
                    </nav>
                </details>
            )}
            <GuideContent key={tab} content={text} />
        </>
    );
}

export function GuideDrawer({
    children,
    onClose,
    title = "Writing guide",
}: {
    children: React.ReactNode;
    onClose: () => void;
    title?: string;
}) {
    const ref = useRef<HTMLElement>(null),
        closeRef = useRef<HTMLButtonElement>(null),
        titleId = useId(),
        callback = useRef(onClose);
    callback.current = onClose;
    const [placement, setPlacement] = useState<{
            left: number;
            top: number;
            width: number;
            height: number;
        } | null>(null),
        reference = useRef(false);
    reference.current = !!placement;
    useLayoutEffect(() => {
        const target = document.querySelector(".writing-practice");
        const measure = () => {
            const box = target?.getBoundingClientRect();
            if (!box || innerWidth <= 1000) {
                setPlacement(null);
                return;
            }
            const top = Math.max(8, box.top);
            const next = {
                left: box.left,
                top,
                width: Math.min(720, box.width / 2 - 6),
                height: Math.max(180, innerHeight - top - 26),
            };
            setPlacement((old) =>
                old &&
                old.left === next.left &&
                old.top === next.top &&
                old.width === next.width &&
                old.height === next.height
                    ? old
                    : next,
            );
        };
        measure();
        const observer = new ResizeObserver(measure);
        if (target) observer.observe(target);
        window.addEventListener("resize", measure);
        document.addEventListener("scroll", measure, true);
        return () => {
            observer.disconnect();
            window.removeEventListener("resize", measure);
            document.removeEventListener("scroll", measure, true);
        };
    }, []);
    useEffect(() => {
        const previous = document.activeElement as HTMLElement | null;
        closeRef.current?.focus();
        const key = (e: KeyboardEvent) => {
            if (e.key === "Escape") {
                e.preventDefault();
                callback.current();
            }
            if (e.key === "Tab" && !reference.current) {
                const nodes = Array.from(
                    ref.current?.querySelectorAll<HTMLElement>(
                        'button:not(:disabled),a[href],input,select,textarea,summary,[tabindex="0"]',
                    ) || [],
                ).filter((n) => n.getClientRects().length);
                const first = nodes[0],
                    last = nodes[nodes.length - 1];
                if (e.shiftKey && document.activeElement === first) {
                    e.preventDefault();
                    last?.focus();
                } else if (!e.shiftKey && document.activeElement === last) {
                    e.preventDefault();
                    first?.focus();
                }
            }
        };
        window.addEventListener("keydown", key);
        return () => {
            window.removeEventListener("keydown", key);
            if (previous?.isConnected) previous.focus({ preventScroll: true });
        };
    }, []);
    return createPortal(
        <div
            className={`guide-drawer-backdrop${placement ? " guide-reference-overlay" : ""}`}
            onClick={(e) => {
                if (!placement && e.target === e.currentTarget) onClose();
            }}
        >
            <section
                ref={ref}
                role="dialog"
                aria-modal={!placement}
                aria-labelledby={titleId}
                className="guide-drawer"
                style={
                    placement ? { position: "fixed", ...placement } : undefined
                }
            >
                <header>
                    <BookOpen size={19} />
                    <h2 id={titleId}>{title}</h2>
                    <button
                        ref={closeRef}
                        aria-label="Close writing guide"
                        onClick={onClose}
                    >
                        <X size={19} />
                    </button>
                </header>
                <div className="guide-drawer-body">{children}</div>
            </section>
        </div>,
        document.body,
    );
}
