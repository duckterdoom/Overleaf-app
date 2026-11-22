"use client";
import { useEffect, useState } from "react";
import { createPortal } from "react-dom";
import {
    ArrowLeft,
    ArrowRight,
    BookOpen,
    FilePenLine,
    Plus,
    Save,
    Trash2,
} from "lucide-react";
import { useWritingApi } from "../../lib/writing/context";
import {
    catalog as fallbackCatalog,
    type Profile,
    type Classroom,
} from "../../lib/writing/types";
import { assessmentGroup } from "../../lib/writing/assessment";
import { errorText } from "../../lib/parity";
import { GuideContent, GuideDocument, type WritingGuide } from "./GuideReader";

type Props = {
    tutor: boolean;
    courses: Classroom[];
    headerActions?: HTMLDivElement | null;
    readonly?: boolean;
    leaveRef?: React.MutableRefObject<(() => boolean) | null>;
    practice?: {
        category?: "ielts" | "report" | "research";
        task: number;
        profile: string;
        classId?: string | null;
    };
};
type Page = { items: WritingGuide[]; total: number };
const empty = (): WritingGuide => ({
    id: "",
    category: "ielts",
    title: "",
    summary: "",
    task: 1,
    profiles: ["line"],
    content: "# Writing guide\n\nAdd your guidance here.",
    classIds: [],
    revision: 0,
    builtin: false,
    ownerName: "You",
    sourceUrl: null,
});

export default function WritingGuides({
    tutor,
    courses,
    headerActions,
    readonly = false,
    practice,
    leaveRef,
}: Props) {
    const { json } = useWritingApi();
    const [category, setCategory] = useState<"ielts" | "research">(
        assessmentGroup(practice?.category),
    );
    const [catalog, setCatalog] =
        useState<Record<string, Profile>>(fallbackCatalog);
    useEffect(() => {
        const abort = new AbortController();
        void json<{ catalog: Record<string, Profile> }>("/writing/settings", {
            signal: abort.signal,
        })
            .then((data) => {
                if (!abort.signal.aborted) setCatalog(data.catalog);
            })
            .catch((e) => {
                if (!abort.signal.aborted) setError(errorText(e));
            });
        return () => abort.abort();
    }, []);
    const [items, setItems] = useState<WritingGuide[]>([]),
        [total, setTotal] = useState(0),
        [page, setPage] = useState(0),
        [query, setQuery] = useState(""),
        [search, setSearch] = useState("");
    const [task, setTask] = useState(practice ? String(practice.task) : ""),
        [profile, setProfile] = useState(practice?.profile || ""),
        [course, setCourse] = useState(practice?.classId || "");
    const [selected, setSelected] = useState<WritingGuide | null>(null),
        [draft, setDraft] = useState<WritingGuide | null>(null),
        [preview, setPreview] = useState(false);
    const [loading, setLoading] = useState(true),
        [busy, setBusy] = useState(false),
        [error, setError] = useState(""),
        [refresh, setRefresh] = useState(0),
        [notice, setNotice] = useState("");
    useEffect(() => {
        if (!leaveRef) return;
        leaveRef.current = () =>
            !draft || confirm("Discard unsaved guide changes before leaving?");
        return () => {
            leaveRef.current = null;
        };
    }, [draft, leaveRef]);
    useEffect(() => {
        const timer = setTimeout(() => {
            setSearch(query.trim());
            setPage(0);
        }, 250);
        return () => clearTimeout(timer);
    }, [query]);
    useEffect(() => {
        const controller = new AbortController();
        setLoading(true);
        setError("");
        const params = new URLSearchParams({ page: String(page), search });
        params.set("category", category);
        if (task) params.set("task", task);
        if (profile) params.set("profile", profile);
        if (course) params.set("classId", course);
        void json<Page>("/writing/guides?" + params, {
            signal: controller.signal,
        })
            .then((result) => {
                if (!controller.signal.aborted) {
                    setItems(result.items);
                    setTotal(result.total);
                }
            })
            .catch((e) => {
                if (!controller.signal.aborted) setError(errorText(e));
            })
            .finally(() => {
                if (!controller.signal.aborted) setLoading(false);
            });
        return () => controller.abort();
    }, [page, search, task, profile, course, refresh, category]);
    async function open(item: WritingGuide, edit = false) {
        if (busy) return;
        setBusy(true);
        setError("");
        setNotice("");
        try {
            const full = await json<WritingGuide>("/writing/guides/" + item.id);
            setSelected(full);
            setDraft(edit ? structuredClone(full) : null);
            setPreview(false);
        } catch (e) {
            setError(errorText(e));
        } finally {
            setBusy(false);
        }
    }
    async function save() {
        if (!draft) return;
        setBusy(true);
        setError("");
        try {
            const body = {
                category: assessmentGroup(draft.category),
                title: draft.title,
                summary: draft.summary,
                task: draft.task,
                profiles: draft.profiles,
                content: draft.content,
                sampleContent: draft.sampleContent || "",
                classIds: draft.classIds || [],
                revision: draft.revision,
            };
            const saved = await json<WritingGuide>(
                "/writing/guides" + (draft.id ? "/" + draft.id : ""),
                {
                    method: draft.id ? "PUT" : "POST",
                    body: JSON.stringify(body),
                },
            );
            setDraft(null);
            setSelected(saved);
            setCategory(assessmentGroup(saved.category));
            setRefresh((n) => n + 1);
            setNotice(
                "Guide saved. Only students enrolled in the selected classes can view it.",
            );
        } catch (e) {
            setError(errorText(e));
        } finally {
            setBusy(false);
        }
    }
    async function remove(item: WritingGuide) {
        if (
            !confirm(
                "Delete this guide in this workspace and stop sharing it with students? Packaged defaults and other tutors are unchanged.",
            )
        )
            return;
        setBusy(true);
        setError("");
        try {
            await json(
                "/writing/guides/" + item.id + "?revision=" + item.revision,
                { method: "DELETE" },
            );
            setSelected(null);
            setDraft(null);
            setRefresh((n) => n + 1);
            setNotice("Guide deleted in this workspace.");
        } catch (e) {
            setError(errorText(e));
        } finally {
            setBusy(false);
        }
    }
    const canEdit = tutor && !readonly && !practice;
    const actions = canEdit && !draft && (
        <button
            className="primary"
            disabled={busy}
            onClick={() => {
                setSelected(null);
                setDraft({
                    ...empty(),
                    category,
                    task: 1,
                    profiles: [
                        Object.entries(catalog).find(
                            ([, p]) =>
                                assessmentGroup(p.category) === category &&
                                p.task === 1,
                        )?.[0] || "",
                    ],
                });
                setPreview(false);
                setError("");
            }}
        >
            <Plus size={16} />
            Create guide
        </button>
    );
    const options = Object.entries(catalog).filter(
        ([, p]) =>
            assessmentGroup(p.category) === category &&
            (!task || p.task === Number(task)),
    );
    return (
        <section
            className={`writing-guides${practice ? " guide-practice-reference" : ""}`}
            aria-label="Writing guides"
        >
            {headerActions
                ? createPortal(actions, headerActions)
                : actions && (
                      <div className="guide-local-actions">{actions}</div>
                  )}
            {error && (
                <p role="alert" className="writing-error">
                    {error}{" "}
                    <button onClick={() => setRefresh((n) => n + 1)}>
                        Retry list
                    </button>
                </p>
            )}
            {notice && (
                <p role="status" className="writing-notice">
                    {notice}
                </p>
            )}
            {draft ? (
                <form
                    className="guide-editor"
                    onSubmit={(e) => {
                        e.preventDefault();
                        void save();
                    }}
                >
                    <div className="guide-toolbar">
                        <strong>
                            {draft.id
                                ? "Edit teaching guide"
                                : "New teaching guide"}
                        </strong>
                        <small>
                            Editing draft · Save guide to apply changes
                        </small>
                        <button
                            type="button"
                            aria-pressed={preview}
                            onClick={() => setPreview((p) => !p)}
                        >
                            {preview ? "Edit Markdown" : "Preview"}
                        </button>
                    </div>
                    <div className="writing-form-grid">
                        <label>
                            Guide title
                            <input
                                aria-label="Guide title"
                                maxLength={160}
                                required
                                value={draft.title}
                                onChange={(e) =>
                                    setDraft({
                                        ...draft,
                                        title: e.target.value,
                                    })
                                }
                            />
                        </label>
                        <label>
                            Task
                            <select
                                aria-label="Guide task"
                                value={draft.task}
                                onChange={(e) => {
                                    const t = Number(e.target.value) as 1 | 2;
                                    setDraft({
                                        ...draft,
                                        task: t,
                                        profiles: [
                                            Object.entries(catalog).find(
                                                ([, p]) =>
                                                    assessmentGroup(
                                                        p.category,
                                                    ) ===
                                                        assessmentGroup(
                                                            draft.category,
                                                        ) && p.task === t,
                                            )?.[0] || "",
                                        ],
                                    });
                                }}
                            >
                                <option value={1}>
                                    {!draft.category ||
                                    draft.category === "ielts"
                                        ? "Task 1"
                                        : "Document"}
                                </option>
                                {(!draft.category ||
                                    draft.category === "ielts") && (
                                    <option value={2}>Task 2 · Essay</option>
                                )}
                            </select>
                        </label>
                    </div>
                    <label>
                        Assessment type
                        <select
                            aria-label="Guide assessment type"
                            value={assessmentGroup(draft.category)}
                            onChange={(e) => {
                                const next = e.target.value as
                                    "ielts" | "research";
                                setDraft({
                                    ...draft,
                                    category: next,
                                    task: 1,
                                    profiles: [
                                        Object.entries(catalog).find(
                                            ([, p]) =>
                                                assessmentGroup(p.category) ===
                                                    next && p.task === 1,
                                        )?.[0] || "",
                                    ],
                                });
                            }}
                        >
                            <option value="ielts">IELTS</option>
                            <option value="research">Research</option>
                        </select>
                    </label>
                    <label>
                        Summary
                        <textarea
                            aria-label="Guide summary"
                            rows={2}
                            maxLength={1000}
                            value={draft.summary}
                            onChange={(e) =>
                                setDraft({ ...draft, summary: e.target.value })
                            }
                        />
                    </label>
                    <fieldset>
                        <legend>Matching writing types</legend>
                        <div className="guide-options">
                            {Object.entries(catalog)
                                .filter(
                                    ([, p]) =>
                                        p.task === draft.task &&
                                        assessmentGroup(p.category) ===
                                            assessmentGroup(draft.category),
                                )
                                .map(([id, p]) => (
                                    <label key={id}>
                                        <input
                                            type="checkbox"
                                            checked={draft.profiles.includes(
                                                id,
                                            )}
                                            onChange={(e) =>
                                                setDraft({
                                                    ...draft,
                                                    profiles: e.target.checked
                                                        ? [
                                                              ...draft.profiles,
                                                              id,
                                                          ]
                                                        : draft.profiles.filter(
                                                              (key) =>
                                                                  key !== id,
                                                          ),
                                                })
                                            }
                                        />
                                        {id === "custom"
                                            ? "Custom / mixed requirements"
                                            : p.label}
                                    </label>
                                ))}
                        </div>
                    </fieldset>
                    <fieldset>
                        <legend>Share with classes</legend>
                        <p>
                            Leave all classes unchecked to keep this guide
                            private. Students have read-only access.
                        </p>
                        <div className="guide-options">
                            {courses.map((c) => (
                                <label key={c.id}>
                                    <input
                                        type="checkbox"
                                        checked={
                                            draft.classIds?.includes(c.id) ||
                                            false
                                        }
                                        onChange={(e) =>
                                            setDraft({
                                                ...draft,
                                                classIds: e.target.checked
                                                    ? [
                                                          ...(draft.classIds ||
                                                              []),
                                                          c.id,
                                                      ]
                                                    : (
                                                          draft.classIds || []
                                                      ).filter(
                                                          (id) => id !== c.id,
                                                      ),
                                            })
                                        }
                                    />
                                    {c.title}
                                </label>
                            ))}
                        </div>
                        {!courses.length && (
                            <p>
                                Create a course or group first to share this
                                guide with students.
                            </p>
                        )}
                    </fieldset>
                    {preview ? (
                        <GuideDocument guide={draft} />
                    ) : (
                        <label>
                            Guide content (Markdown)
                            <textarea
                                className="guide-markdown-input"
                                aria-label="Guide content"
                                required
                                maxLength={150000}
                                value={draft.content || ""}
                                onChange={(e) =>
                                    setDraft({
                                        ...draft,
                                        content: e.target.value,
                                    })
                                }
                            />
                        </label>
                    )}
                    {!preview&&<label className="guide-sample-editor">Sample writing (Markdown)<small>Add the question, model answer and explanation. Students can read this alongside the guide once shared with their course.</small><textarea className="guide-markdown-input" aria-label="Sample writing content" maxLength={150000} value={draft.sampleContent||''} onChange={e=>setDraft({...draft,sampleContent:e.target.value})}/></label>}
                    <footer>
                        <button
                            type="button"
                            disabled={busy}
                            onClick={() => {
                                if (confirm("Discard unsaved guide changes?"))
                                    setDraft(null);
                            }}
                        >
                            Cancel editing
                        </button>
                        <button
                            className="primary"
                            disabled={busy || !draft.profiles.length}
                        >
                            <Save size={16} />
                            {busy ? "Saving…" : "Save guide"}
                        </button>
                    </footer>
                </form>
            ) : selected ? (
                <div className="guide-reading-panel">
                    <div className="guide-toolbar">
                        <button
                            onClick={() => {
                                setSelected(null);
                                setNotice("");
                            }}
                        >
                            <ArrowLeft size={16} />
                            All guides
                        </button>
                        {canEdit && (
                            <>
                                <button
                                    disabled={busy}
                                    onClick={() => {
                                        setDraft(structuredClone(selected));
                                        setPreview(false);
                                    }}
                                >
                                    <FilePenLine size={16} />
                                    Edit guide
                                </button>
                                <button
                                    disabled={busy}
                                    onClick={() => void remove(selected)}
                                >
                                    <Trash2 size={16} />
                                    Delete guide
                                </button>
                            </>
                        )}
                    </div>
                    <GuideDocument guide={selected} />
                </div>
            ) : (
                <>
                    {!practice && (
                        <div className="guide-library-intro">
                            <BookOpen size={23} />
                            <div>
                                <strong>
                                    {tutor
                                        ? "A blueprint for every writing type"
                                        : "Your class writing resources"}
                                </strong>
                                <p>
                                    {tutor
                                        ? category === "ielts"
                                            ? "13 default IELTS templates are ready to read and adapt."
                                            : "Research includes report and research-paper guides. Add templates when your content is ready."
                                        : "Read the guides your tutor shared with your enrolled classes. Your answers and assessment criteria stay separate."}
                                </p>
                            </div>
                        </div>
                    )}
                    <nav
                        className="guide-assessment-tabs"
                        aria-label="Guide assessment types"
                    >
                        {(["ielts", "research"] as const).map((key) => (
                            <button
                                key={key}
                                aria-pressed={category === key}
                                onClick={() => {
                                    setCategory(key);
                                    setTask("");
                                    setProfile("");
                                    setPage(0);
                                    setSelected(null);
                                }}
                            >
                                {key === "ielts" ? "IELTS" : "Research"}
                            </button>
                        ))}
                    </nav>
                    <div className="guide-filters">
                        <label>
                            Search guides
                            <input
                                type="search"
                                aria-label="Search guides"
                                value={query}
                                onChange={(e) => setQuery(e.target.value)}
                                placeholder="Title or topic…"
                            />
                        </label>
                        <label>
                            {category === "ielts" ? "Task" : "Document"}
                            <select
                                aria-label="Filter guide task"
                                value={task}
                                onChange={(e) => {
                                    setTask(e.target.value);
                                    setProfile("");
                                    setPage(0);
                                }}
                            >
                                <option value="">
                                    {category === "ielts"
                                        ? "All tasks"
                                        : "All documents"}
                                </option>
                                <option value="1">
                                    {category === "ielts"
                                        ? "Task 1"
                                        : "Document"}
                                </option>
                                {category === "ielts" && (
                                    <option value="2">Task 2</option>
                                )}
                            </select>
                        </label>
                        <label>
                            Writing type
                            <select
                                aria-label="Filter guide profile"
                                value={profile}
                                onChange={(e) => {
                                    setProfile(e.target.value);
                                    setPage(0);
                                }}
                            >
                                <option value="">All writing types</option>
                                {options.map(([id, p]) => (
                                    <option key={id} value={id}>
                                        {id === "custom"
                                            ? "Custom / mixed requirements"
                                            : p.label}
                                    </option>
                                ))}
                            </select>
                        </label>
                        {!practice && (
                            <label>
                                Class
                                <select
                                    aria-label="Filter guide class"
                                    value={course}
                                    onChange={(e) => {
                                        setCourse(e.target.value);
                                        setPage(0);
                                    }}
                                >
                                    <option value="">All my classes</option>
                                    {courses.map((c) => (
                                        <option key={c.id} value={c.id}>
                                            {c.title}
                                        </option>
                                    ))}
                                </select>
                            </label>
                        )}
                    </div>
                    {loading ? (
                        <p role="status" className="writing-empty">
                            Loading writing guides…
                        </p>
                    ) : (
                        <>
                            <p className="guide-result-count">
                                {total} {total === 1 ? "guide" : "guides"}{" "}
                                available · teaching resources, separate from
                                grading rubrics
                            </p>
                            <div className="guide-card-grid">
                                {items.map((item) => (
                                    <article
                                        className="guide-card"
                                        key={item.id}
                                    >
                                        <div>
                                            <span className="guide-badge">
                                                {!item.category ||
                                                item.category === "ielts"
                                                    ? `Task ${item.task}`
                                                    : item.category === "report"
                                                      ? "Research / Report"
                                                      : "Research"}
                                            </span>
                                            <small>
                                                {item.builtin
                                                    ? "Default template"
                                                    : tutor
                                                      ? item.classIds?.length
                                                          ? `Shared · ${item.classIds.length} classes`
                                                          : "Your template"
                                                      : "Shared with your class"}
                                            </small>
                                        </div>
                                        <BookOpen
                                            size={25}
                                            className="guide-card-icon"
                                        />
                                        <h2>{item.title}</h2>
                                        {item.hasSample&&<span className="guide-sample-badge">Guide + model answer</span>}
                                        <p>{item.summary}</p>
                                        <small>{item.ownerName}</small>
                                        <footer>
                                            <button
                                                disabled={busy}
                                                onClick={() => void open(item)}
                                                aria-label={
                                                    "Read guide " + item.title
                                                }
                                            >
                                                Read guide{" "}
                                                <ArrowRight size={15} />
                                            </button>
                                            {canEdit && (
                                                <button
                                                    disabled={busy}
                                                    onClick={() =>
                                                        void open(item, true)
                                                    }
                                                    aria-label={
                                                        "Edit guide " +
                                                        item.title
                                                    }
                                                >
                                                    Customize
                                                </button>
                                            )}
                                        </footer>
                                    </article>
                                ))}
                            </div>
                            {!items.length && (
                                <div className="writing-empty">
                                    <BookOpen size={27} />
                                    <h3>
                                        {category === "ielts"
                                            ? "No guides available"
                                            : `No ${category} templates yet`}
                                    </h3>
                                    <p>
                                        {tutor
                                            ? category === "ielts"
                                                ? "Change the filters or create a teaching guide."
                                                : "This section is ready for your templates. No system content has been added yet."
                                            : "Your tutor has not shared a matching guide with your enrolled class yet."}
                                    </p>
                                </div>
                            )}
                            <div className="guide-pagination">
                                <button
                                    disabled={page === 0 || loading}
                                    onClick={() => setPage((p) => p - 1)}
                                >
                                    Previous guides
                                </button>
                                <span>Page {page + 1}</span>
                                <button
                                    disabled={
                                        (page + 1) * 24 >= total || loading
                                    }
                                    onClick={() => setPage((p) => p + 1)}
                                >
                                    Next guides
                                </button>
                            </div>
                        </>
                    )}
                </>
            )}
        </section>
    );
}
