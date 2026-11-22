"use client";
import { useEffect, useMemo, useRef, useState } from "react";
import { MessageSquare, Plus, Trash2, X } from "lucide-react";
import type { SentenceAnnotation } from "../../lib/writing/types";

export default function AnnotatedSubmission({
    text,
    annotations,
    editable,
    categories,
    onChange,
    onPendingChange,
}: {
    text: string;
    annotations: SentenceAnnotation[];
    editable: boolean;
    categories: Record<string, string>;
    onChange: (notes: SentenceAnnotation[]) => void;
    onPendingChange?: (pending:boolean)=>void;
}) {
    const root = useRef<HTMLDivElement>(null);
    const selecting = useRef(false);
    const [draft, setDraft] = useState<SentenceAnnotation | null>(null),
        [visible, setVisible] = useState<string[] | null>(null),
        [showAll, setShowAll] = useState(false),
        [error, setError] = useState("");
    useEffect(()=>{onPendingChange?.(draft!==null);return()=>onPendingChange?.(false);},[!!draft,onPendingChange]);
    const displayed = useMemo(
        () =>
            draft
                ? [...annotations.filter((a) => a.id !== draft.id), draft]
                : annotations,
        [annotations, draft],
    );
    const cuts = Array.from(
        new Set([
            0,
            text.length,
            ...displayed.flatMap((a) => [a.start, a.end]),
        ]),
    ).sort((a, b) => a - b);
    function select() {
        if (!editable) return;
        const selected = window.getSelection();
        if (!selected || selected.rangeCount !== 1 || selected.isCollapsed)
            return;
        const range = selected.getRangeAt(0),
            paper = root.current;
        if (!paper || !paper.contains(range.commonAncestorContainer)) return;
        const quote = range.toString();
        if (!quote.trim()) return;
        if (quote.length > 10000) {
            setError(
                "Select a sentence or passage of up to 10,000 characters.",
            );
            return;
        }
        const prefix = range.cloneRange();
        prefix.selectNodeContents(paper);
        prefix.setEnd(range.startContainer, range.startOffset);
        const start = prefix.toString().length;
        selecting.current = true;
        setDraft({
            id: crypto.randomUUID(),
            start,
            end: start + quote.length,
            quote,
            comment: "",
            replacement: "",
            kind: "comment",
            category: "",
            resolved: false,
        });
        setVisible(null);
        setError("");
        selected.removeAllRanges();
    }
    function save() {
        if (!draft) return;
        if (draft.kind === "comment" && !draft.comment.trim()) {
            setError("Enter your feedback before adding this comment.");
            return;
        }
        if (draft.kind === "replace" && !draft.replacement.trim()) {
            setError("Enter the suggested correction.");
            return;
        }
        onChange([...annotations.filter((a) => a.id !== draft.id), draft]);
        setVisible([draft.id]);
        setDraft(null);
        setError("");
    }
    const shown = showAll
        ? annotations
        : annotations.filter((a) => visible?.includes(a.id));
    const panel = !!draft || shown.length > 0 || showAll;
    return (
        <section
            className={`annotated-submission${panel ? " with-comments" : ""}`}
            aria-label="Submitted paper and comments"
        >
            <div className="submission-paper-column">
                <div className="submission-paper-tools">
                    <span>
                        {editable
                            ? "Select a sentence to comment or suggest a correction."
                            : "Select highlighted text to read your tutor’s feedback."}
                    </span>
                    <button
                        type="button"
                        aria-pressed={showAll}
                        onClick={() => {
                            setShowAll((v) => !v);
                            setVisible(null);
                            setDraft(null);
                        }}
                    >
                        <MessageSquare size={15} />
                        Comments ({annotations.length})
                    </button>
                </div>
                {error && (
                    <p role="alert" className="writing-error">
                        {error}
                    </p>
                )}
                <div
                    ref={root}
                    className="submission-answer-paper"
                    aria-label="Submitted answer"
                    tabIndex={0}
                    onMouseDown={() => {
                        selecting.current = false;
                    }}
                    onMouseUp={select}
                    onKeyUp={(e) => {
                        if (e.shiftKey) select();
                    }}
                >
                    {cuts.slice(0, -1).map((start, index) => {
                        const end = cuts[index + 1],
                            notes = displayed.filter(
                                (a) => a.start < end && a.end > start,
                            ),
                            chunk = text.slice(start, end);
                        if (!notes.length)
                            return <span key={start}>{chunk}</span>;
                        const ids = notes.map((n) => n.id),
                            strike = notes.some(
                                (n) =>
                                    n.kind === "strike" || n.kind === "replace",
                            ),
                            underline = notes.some(
                                (n) => n.kind === "underline",
                            ),
                            resolved = notes.every((n) => n.resolved);
                        const open = () => {
                            if (selecting.current) return;
                            setVisible(ids);
                            setShowAll(false);
                            if (!draft || !ids.includes(draft.id))
                                setDraft(null);
                        };
                        return (
                            <span
                                role="button"
                                tabIndex={0}
                                key={start}
                                className={`sentence-mark${strike ? " strike" : ""}${underline ? " underline" : ""}${resolved ? " resolved" : ""}`}
                                data-annotation-ids={ids.join(" ")}
                                aria-label={`View feedback: ${chunk}`}
                                onClick={open}
                                onKeyDown={(e) => {
                                    if (e.key === "Enter" || e.key === " ") {
                                        e.preventDefault();
                                        open();
                                    }
                                }}
                            >
                                {chunk}
                            </span>
                        );
                    })}
                </div>
            </div>
            {panel && (
                <aside
                    className="sentence-comment-panel"
                    aria-label="Sentence feedback"
                >
                    <header>
                        <strong>
                            {draft
                                ? "Sentence feedback"
                                : showAll
                                  ? "All feedback"
                                  : "Feedback"}
                        </strong>
                        <button
                            type="button"
                            aria-label="Close sentence feedback"
                            onClick={() => {
                                setDraft(null);
                                setVisible(null);
                                setShowAll(false);
                                setError("");
                            }}
                        >
                            <X size={16} />
                        </button>
                    </header>
                    {draft ? (
                        <div className="sentence-comment-editor">
                            <blockquote>{draft.quote}</blockquote>
                            <label>
                                Correction style
                                <select
                                    aria-label="Correction style"
                                    value={draft.kind}
                                    onChange={(e) => {
                                        const kind = e.target
                                            .value as SentenceAnnotation["kind"];
                                        setDraft({
                                            ...draft,
                                            kind,
                                            replacement:
                                                kind === "replace"
                                                    ? draft.replacement
                                                    : "",
                                        });
                                    }}
                                >
                                    <option value="comment">Comment</option>
                                    <option value="underline">Underline</option>
                                    <option value="strike">
                                        Strikethrough
                                    </option>
                                    <option value="replace">
                                        Suggested replacement
                                    </option>
                                </select>
                            </label>
                            <label>
                                Assessment category
                                <select
                                    aria-label="Comment assessment category"
                                    value={draft.category}
                                    onChange={(e) =>
                                        setDraft({
                                            ...draft,
                                            category: e.target.value,
                                        })
                                    }
                                >
                                    <option value="">General feedback</option>
                                    {Object.entries(categories).map(
                                        ([key, label]) => (
                                            <option key={key} value={key}>
                                                {label}
                                            </option>
                                        ),
                                    )}
                                </select>
                            </label>
                            {draft.kind === "replace" && (
                                <label>
                                    Suggested correction
                                    <textarea
                                        aria-label="Suggested correction"
                                        rows={3}
                                        maxLength={10000}
                                        value={draft.replacement}
                                        onChange={(e) =>
                                            setDraft({
                                                ...draft,
                                                replacement: e.target.value,
                                            })
                                        }
                                    />
                                </label>
                            )}
                            <label>
                                Feedback
                                <textarea
                                    aria-label="Sentence comment"
                                    autoFocus
                                    rows={4}
                                    maxLength={10000}
                                    value={draft.comment}
                                    onChange={(e) =>
                                        setDraft({
                                            ...draft,
                                            comment: e.target.value,
                                        })
                                    }
                                />
                            </label>
                            <footer>
                                <button
                                    type="button"
                                    onClick={() => setDraft(null)}
                                >
                                    Cancel
                                </button>
                                <button
                                    type="button"
                                    className="primary"
                                    onClick={save}
                                >
                                    <Plus size={14} />
                                    {annotations.some((a) => a.id === draft.id)
                                        ? "Update comment"
                                        : "Add comment"}
                                </button>
                            </footer>
                            <small>
                                Save the assessment draft to keep feedback.
                                Students see it after publication.
                            </small>
                        </div>
                    ) : (
                        <>
                            {!shown.length && <p>No sentence feedback yet.</p>}
                            {shown.map((note) => (
                                <article
                                    key={note.id}
                                    className={`sentence-comment-card${note.resolved ? " resolved" : ""}`}
                                >
                                    <strong>
                                        {note.authorName || "Tutor feedback"}
                                    </strong>
                                    <small>
                                        {note.category
                                            ? categories[note.category]
                                            : note.kind}
                                        {note.resolved ? " · Resolved" : ""}
                                    </small>
                                    <blockquote>{note.quote}</blockquote>
                                    {note.replacement && (
                                        <div className="sentence-replacement">
                                            <small>Suggested correction</small>
                                            <p>{note.replacement}</p>
                                        </div>
                                    )}
                                    {note.comment && <p>{note.comment}</p>}
                                    {editable && (
                                        <footer>
                                            <button
                                                type="button"
                                                onClick={() => {
                                                    setDraft({ ...note });
                                                    setVisible(null);
                                                }}
                                            >
                                                Edit comment
                                            </button>
                                            <button
                                                type="button"
                                                onClick={() =>
                                                    onChange(
                                                        annotations.map((a) =>
                                                            a.id === note.id
                                                                ? {
                                                                      ...a,
                                                                      resolved:
                                                                          !a.resolved,
                                                                  }
                                                                : a,
                                                        ),
                                                    )
                                                }
                                            >
                                                {note.resolved
                                                    ? "Reopen"
                                                    : "Resolve"}
                                            </button>
                                            <button
                                                type="button"
                                                aria-label={`Delete comment ${note.id}`}
                                                onClick={() =>
                                                    onChange(
                                                        annotations.filter(
                                                            (a) =>
                                                                a.id !==
                                                                note.id,
                                                        ),
                                                    )
                                                }
                                            >
                                                <Trash2 size={14} />
                                            </button>
                                        </footer>
                                    )}
                                </article>
                            ))}
                        </>
                    )}
                </aside>
            )}
        </section>
    );
}
