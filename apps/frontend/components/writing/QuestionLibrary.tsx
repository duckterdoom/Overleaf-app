"use client";
import { useEffect, useState } from "react";
import { createPortal } from "react-dom";
import { type Question,type Assignment } from "../../lib/writing/types";
import { useWritingApi } from "../../lib/writing/context";
import { errorText } from "../../lib/parity";
import ExerciseForm from "./ExerciseForm";
import LibraryCategoryFilter from "./LibraryCategoryFilter";
import {assessmentGroup,assessmentLabels,documentLabels} from "../../lib/writing/assessment";

type LibraryQuestion = Question & { savedToLibrary?: boolean };
const categoryLabels=assessmentLabels;
type Facet = {
    key: string;
    label: string;
    category: string;
    task: number;
    sections: { key: string; label: string }[];
};
const sectionNames: Record<string, string> = {
    intro: "Introduction",
    body: "Body",
    overview: "Overview",
    conclusion: "Conclusion",
    whole: "Whole-paper requirements",
    closing: "Closing",
};
const scopeLabels = {
    whole: "Whole paper",
    intro: "Introduction only",
    body: "Body only",
    conclusion: "Conclusion only",
};
const purposeLabels = {
    practice: "Practice",
    homework: "Assigned exercise",
    exam: "Examination",
};

export default function QuestionLibrary({
    userId,
    onAssigned,
    initialCreate=false,
    onCreateOpened,
    headerActions,
}: {
    userId: string;
    onAssigned: (assignment: Assignment) => void;
    initialCreate?:boolean;
    onCreateOpened?:()=>void;
    headerActions?: HTMLDivElement | null;
}) {
    const { json } = useWritingApi();
    const [scope, setScope] = useState<"mine" | "public">("mine");
    const [search, setSearch] = useState(""),
        [query, setQuery] = useState(""),
        [category, setCategory] = useState("");
    const [task, setTask] = useState(""),
        [profile, setProfile] = useState(""),
        [sections, setSections] = useState<string[] | null>(null),
        [taxonomy, setTaxonomy] = useState<Facet[]>([]);
    const [items, setItems] = useState<LibraryQuestion[]>([]),
        [page, setPage] = useState(0),
        [total, setTotal] = useState(0);
    const [editing, setEditing] = useState<Question | null | undefined>(initialCreate?null:undefined);
    const [assignmentSource,setAssignmentSource]=useState<Question|undefined>();
    const [defaultDelivery,setDefaultDelivery]=useState<'library'|'assign'>('library');
    useEffect(()=>{if(initialCreate){setEditing(null);onCreateOpened?.();}},[initialCreate]);
    function startQuestion(question?:Question){setAssignmentSource(undefined);setDefaultDelivery('library');setEditing(question||null);}
    function startAssignment(question:Question){setAssignmentSource(question);setDefaultDelivery('assign');setEditing(null);}
    const [loading, setLoading] = useState(true),
        [busy, setBusy] = useState<string | null>(null);
    const [error, setError] = useState(""),
        [notice, setNotice] = useState(""),
        [refresh, setRefresh] = useState(0);
    useEffect(() => {
        const timer = setTimeout(() => {
            setQuery(search.trim());
            setPage(0);
        }, 300);
        return () => clearTimeout(timer);
    }, [search]);
    useEffect(() => {
        const controller = new AbortController();
        setLoading(true);
        setError("");
        const params = new URLSearchParams({
            scope,
            page: String(page),
            search: query,
        });
        if (category) params.set("category", category);
        if (task) params.set("task", task);
        if (profile) params.set("profile", profile);
        for (const section of sections || [])
            params.append("sections", section);
        void json<{
            items: LibraryQuestion[];
            total: number;
            taxonomy: Facet[];
        }>(`/writing/questions?${params}`, { signal: controller.signal })
            .then((data) => {
                if (!controller.signal.aborted) {
                    setItems(data.items);
                    setTotal(data.total);
                    setTaxonomy(data.taxonomy || []);
                }
            })
            .catch((e) => {
                if (!controller.signal.aborted) setError(errorText(e));
            })
            .finally(() => {
                if (!controller.signal.aborted) setLoading(false);
            });
        return () => controller.abort();
    }, [scope, page, query, category, task, profile, sections, refresh]);
    async function add(q: Question) {
        setBusy(q.id);
        setError("");
        setNotice("");
        try {
            await json(`/writing/questions/${q.id}/copy`, { method: "POST" });
            setItems((old) =>
                old.map((item) =>
                    item.id === q.id ? { ...item, savedToLibrary: true } : item,
                ),
            );
            setNotice(
                `“${q.title}” added to My library as a private copy. You can edit and assign it there.`,
            );
        } catch (e) {
            setError(errorText(e));
        } finally {
            setBusy(null);
        }
    }
    async function remove(q: Question) {
        if (
            !confirm(
                "Delete this question? Existing assignments keep their snapshots.",
            )
        )
            return;
        setBusy(q.id);
        setError("");
        try {
            await json(`/writing/questions/${q.id}?revision=${q.revision}`, {
                method: "DELETE",
            });
            if (items.length === 1 && page) setPage(page - 1);
            else setRefresh((n) => n + 1);
        } catch (e) {
            setError(errorText(e));
        } finally {
            setBusy(null);
        }
    }
    if (editing !== undefined)
        return (
            <ExerciseForm
                embedded={!!headerActions}
                key={editing?.id || assignmentSource?.id || "new"}
                practice={false}
                questionMode
                template={assignmentSource}
                defaultDelivery={defaultDelivery}
                question={editing || undefined}
                onDone={onAssigned}
                onCancel={() => setEditing(undefined)}
                onQuestionSaved={() => {
                    setEditing(undefined);
                    setRefresh((n) => n + 1);
                }}
            />
        );
    const createAction = (
        <button className="primary" onClick={() => startQuestion()}>
            Create question
        </button>
    );
    return (
        <section className="question-library">
            {headerActions ? (
                createPortal(createAction, headerActions)
            ) : (
                <header className="writing-list-toolbar">
                    <div>
                        <h2>Question library</h2>
                        <p>
                            Keep reusable questions here, or discover questions
                            shared by other tutors.
                        </p>
                    </div>
                    {createAction}
                </header>
            )}
            <div className="question-library-controls">
                <div className="question-library-control-top">
                    <nav
                        className="question-library-views"
                        aria-label="Question libraries"
                    >
                        <button
                            aria-pressed={scope === "mine"}
                            onClick={() => {
                                setScope("mine");
                                setPage(0);
                                setNotice("");
                                setProfile("");
                                setSections(null);
                            }}
                        >
                            My library
                        </button>
                        <button
                            aria-pressed={scope === "public"}
                            onClick={() => {
                                setScope("public");
                                setPage(0);
                                setNotice("");
                                setProfile("");
                                setSections(null);
                            }}
                        >
                            Public library
                        </button>
                    </nav>
                    <span className="question-library-count" aria-live="polite">
                        {loading ? "Loading..." : `${total} questions`}
                    </span>
                </div>
                <div className="question-library-filters">
                    <label>
                        Search questions
                        <input
                            aria-label="Search by question title"
                            type="search"
                            value={search}
                            maxLength={160}
                            placeholder="Enter a title or keyword"
                            onChange={(e) => setSearch(e.target.value)}
                        />
                    </label>
                    <label>
                        Assessment type
                        <select
                            aria-label="Assessment type"
                            value={category}
                            onChange={(e) => {
                                setCategory(e.target.value);
                                setTask("");
                                setProfile("");
                                setSections(null);
                                setPage(0);
                            }}
                        >
                            <option value="">All types</option>
                            <option value="ielts">IELTS Task</option>
                            <option value="research">Research</option>
                        </select>
                    </label>
                    {(!category || category === "ielts") && (
                        <label>
                            Task
                            <select
                                aria-label="Library task"
                                value={task}
                                onChange={(e) => {
                                    setTask(e.target.value);
                                    setProfile("");
                                    setSections(null);
                                    setPage(0);
                                }}
                            >
                                <option value="">All tasks</option>
                                <option value="1">Task 1</option>
                                <option value="2">Task 2</option>
                            </select>
                        </label>
                    )}
                    <label>
                        Question type / rubric
                        <select
                            aria-label="Library rubric"
                            value={profile}
                            onChange={(e) => {
                                setProfile(e.target.value);
                                setPage(0);
                            }}
                        >
                            <option value="">All rubrics</option>
                            {Object.entries(categoryLabels)
                                .filter(
                                    ([key]) => !category || category === key,
                                )
                                .map(([key, label]) => (
                                    <optgroup key={key} label={label}>
                                        {Array.from(
                                            new Map(
                                                taxonomy
                                                    .filter(
                                                        (p) =>
                                                            assessmentGroup(p.category) === key &&
                                                            (!task ||
                                                                p.task ===
                                                                    Number(
                                                                        task,
                                                                    )),
                                                    )
                                                    .map((p) => [p.key, p]),
                                            ).values(),
                                        ).map((p) => (
                                            <option key={p.key} value={p.key}>
                                                {p.category === "ielts"
                                                    ? `Task ${p.task} - `
                                                    : ""}
                                                {p.label}
                                            </option>
                                        ))}
                                    </optgroup>
                                ))}
                        </select>
                    </label>
                </div>
                <LibraryCategoryFilter
                    value={sections}
                    options={Array.from(
                        new Map(
                            taxonomy
                                .filter(
                                    (p) =>
                                        (!category ||
                                            assessmentGroup(p.category) === category) &&
                                        (!task || p.task === Number(task)),
                                )
                                .flatMap((p) => p.sections)
                                .map((s) => [s.key, s]),
                        ).values(),
                    )}
                    onChange={(value) => {
                        setSections(value);
                        setPage(0);
                    }}
                />
                <div className="question-library-filter-note">
                    <span>
                        {scope === "mine"
                            ? "Your questions and private copies, organized by assessment type."
                            : "Shared by other tutors. Add a private copy to edit or assign."}
                    </span>
                    {(query ||
                        category ||
                        task ||
                        profile ||
                        sections !== null) && (
                        <button
                            type="button"
                            onClick={() => {
                                setSearch("");
                                setQuery("");
                                setCategory("");
                                setTask("");
                                setProfile("");
                                setSections(null);
                                setPage(0);
                            }}
                        >
                            Clear filters
                        </button>
                    )}
                </div>
            </div>
            {notice && (
                <p className="writing-notice" role="status">
                    {notice}
                </p>
            )}
            {error && (
                <p role="alert" className="writing-error">
                    {error}{" "}
                    <button onClick={() => setRefresh((n) => n + 1)}>
                        Retry
                    </button>
                </p>
            )}
            <div aria-busy={loading}>
                {loading ? (
                    <p className="writing-empty" role="status">
                        Loading questions…
                    </p>
                ) : (
                    !error && (
                        <>
                            <div className="question-library-results">
                                {Object.entries(categoryLabels)
                                    .filter(([key]) =>
                                        items.some(
                                            (q) =>
                                                assessmentGroup(q.content.category) === key,
                                        ),
                                    )
                                    .map(([key, label]) => (
                                        <section
                                            className="question-library-group"
                                            data-assessment-type={key}
                                            key={key}
                                        >
                                            <h2>
                                                {label}
                                                <small>
                                                    {
                                                        items.filter(
                                                            (q) =>
                                                                assessmentGroup(q.content.category) === key,
                                                        ).length
                                                    }{" "}
                                                    on this page
                                                </small>
                                            </h2>
                                            <div className="writing-assignment-list">
                                                {items
                                                    .filter(
                                                        (q) =>
                                                            assessmentGroup(q.content.category) === key,
                                                    )
                                                    .map((q) => {
                                                        const c = q.content,
                                                            assessment =
                                                                c.category ||
                                                                "ielts",
                                                            own =
                                                                q.ownerId ===
                                                                userId;
                                                        const profiles = c.tasks
                                                            .map(
                                                                (task) =>
                                                                    c
                                                                        .rubricCatalog?.[
                                                                        c
                                                                            .profiles[
                                                                            String(
                                                                                task,
                                                                            )
                                                                        ]
                                                                    ]?.label,
                                                            )
                                                            .filter(Boolean);
                                                        return (
                                                            <article
                                                                className="writing-card question-library-card"
                                                                key={q.id}
                                                            >
                                                                <div className="question-library-card-content">
                                                                    <div className="question-library-badges">
                                                                        <span className="writing-tag">
                                                                            {
                                                                                documentLabels[assessment]
                                                                            }
                                                                        </span>
                                                                        {assessment ===
                                                                            "ielts" && (
                                                                            <>
                                                                                <span className="writing-tag">
                                                                                    {c.tasks
                                                                                        .map(
                                                                                            (
                                                                                                t,
                                                                                            ) =>
                                                                                                `Task ${t}`,
                                                                                        )
                                                                                        .join(
                                                                                            " + ",
                                                                                        )}
                                                                                </span>

                                                                            </>
                                                                        )}
                                                                        <span className="writing-tag">
                                                                            {Object.keys(
                                                                                c.assessmentCategories ||
                                                                                    {},
                                                                            )
                                                                                .length
                                                                                ? "Selected categories"
                                                                                : scopeLabels[
                                                                                      c.writingScope ||
                                                                                          "whole"
                                                                                  ]}
                                                                        </span>
                                                                        {c.purpose && (
                                                                            <span className="writing-tag">
                                                                                {
                                                                                    purposeLabels[
                                                                                        c
                                                                                            .purpose
                                                                                    ]
                                                                                }
                                                                            </span>
                                                                        )}
                                                                        <span className="writing-tag">
                                                                            {q.visibility ===
                                                                            "public"
                                                                                ? "Public"
                                                                                : "Private"}
                                                                        </span>
                                                                    </div>
                                                                    <h3>
                                                                        {
                                                                            q.title
                                                                        }
                                                                    </h3>
                                                                    {c.scoring && (
                                                                        <span className="writing-tag">
                                                                            Scale:{" "}
                                                                            {
                                                                                c
                                                                                    .scoring
                                                                                    .total
                                                                            }{" "}
                                                                            points
                                                                        </span>
                                                                    )}
                                                                    {c.description && (
                                                                        <p className="writing-exercise-description">
                                                                            {
                                                                                c.description
                                                                            }
                                                                        </p>
                                                                    )}
                                                                    <p className="question-library-owner">
                                                                        {own
                                                                            ? q.sourceQuestionId
                                                                                ? `Your copy · Originally by ${q.sourceOwnerName || "another tutor"}`
                                                                                : q.sourceAssignmentId
                                                                                  ? "Created by you · Saved from an exercise"
                                                                                  : "Created by you"
                                                                            : `Shared by ${q.ownerName}`}
                                                                    </p>
                                                                    {!!profiles.length && (
                                                                        <p className="question-classification">
                                                                            <strong>
                                                                                Rubric:
                                                                            </strong>{" "}
                                                                            {profiles.join(
                                                                                " · ",
                                                                            )}
                                                                        </p>
                                                                    )}
                                                                    <div className="question-library-category-labels">
                                                                        {c.tasks.map(
                                                                            (
                                                                                t,
                                                                            ) => {
                                                                                const rubric =
                                                                                    c
                                                                                        .rubricCatalog?.[
                                                                                        c
                                                                                            .profiles[
                                                                                            String(
                                                                                                t,
                                                                                            )
                                                                                        ]
                                                                                    ];
                                                                                const keys =
                                                                                    c
                                                                                        .assessmentCategories?.[
                                                                                        String(
                                                                                            t,
                                                                                        )
                                                                                    ] ||
                                                                                    (c.writingScope &&
                                                                                    c.writingScope !==
                                                                                        "whole"
                                                                                        ? [
                                                                                              c.writingScope,
                                                                                          ]
                                                                                        : Object.keys(
                                                                                              rubric?.checks ||
                                                                                                  {},
                                                                                          ));
                                                                                return (
                                                                                    <span
                                                                                        key={
                                                                                            t
                                                                                        }
                                                                                    >
                                                                                        <strong>
                                                                                            {assessment ===
                                                                                            "ielts"
                                                                                                ? `Task ${t}: `
                                                                                                : "Categories: "}
                                                                                        </strong>
                                                                                        {keys
                                                                                            .map(
                                                                                                (
                                                                                                    k,
                                                                                                ) =>
                                                                                                    rubric
                                                                                                        ?.sectionTitles?.[
                                                                                                        k
                                                                                                    ] ||
                                                                                                    sectionNames[
                                                                                                        k
                                                                                                    ] ||
                                                                                                    k,
                                                                                            )
                                                                                            .join(
                                                                                                " + ",
                                                                                            ) ||
                                                                                            "Whole paper"}
                                                                                    </span>
                                                                                );
                                                                            },
                                                                        )}
                                                                    </div>
                                                                    {Object.values(
                                                                        c.prompts,
                                                                    ).some(
                                                                        (p) =>
                                                                            p.trim(),
                                                                    ) && (
                                                                        <p className="question-excerpt">
                                                                            {Object.values(
                                                                                c.prompts,
                                                                            ).join(
                                                                                "\n",
                                                                            )}
                                                                        </p>
                                                                    )}
                                                                </div>
                                                                <footer>
                                                                    {own ? (
                                                                        <>
                                                                            <button
                                                                                className="primary"
                                                                                onClick={() =>
                                                                                    startAssignment(q)
                                                                                }
                                                                            >
                                                                                Use
                                                                                for
                                                                                an
                                                                                exercise
                                                                            </button>
                                                                            <button
                                                                                onClick={() =>
                                                                                    startQuestion(q)
                                                                                }
                                                                            >
                                                                                Edit
                                                                                question
                                                                            </button>
                                                                            <button
                                                                                disabled={
                                                                                    !!busy
                                                                                }
                                                                                onClick={() =>
                                                                                    void remove(
                                                                                        q,
                                                                                    )
                                                                                }
                                                                            >
                                                                                {busy ===
                                                                                q.id
                                                                                    ? "Deleting…"
                                                                                    : "Delete question"}
                                                                            </button>
                                                                        </>
                                                                    ) : (
                                                                        <button
                                                                            className="primary"
                                                                            disabled={
                                                                                !!busy ||
                                                                                q.savedToLibrary
                                                                            }
                                                                            onClick={() =>
                                                                                void add(
                                                                                    q,
                                                                                )
                                                                            }
                                                                        >
                                                                            {busy ===
                                                                            q.id
                                                                                ? "Adding…"
                                                                                : q.savedToLibrary
                                                                                  ? "Added to My library"
                                                                                  : "Add to my library"}
                                                                        </button>
                                                                    )}
                                                                </footer>
                                                            </article>
                                                        );
                                                    })}
                                            </div>
                                        </section>
                                    ))}
                            </div>
                            {!items.length && (
                                <div className="writing-empty">
                                    {query ||
                                    category ||
                                    task ||
                                    profile ||
                                    sections !== null ? (
                                        <>
                                            <p>
                                                {category
                                                    ? `No ${categoryLabels[category as keyof typeof categoryLabels]} questions match these filters yet. Available question types remain selectable.`
                                                    : "No matching questions. Try another keyword or combination of categories."}
                                            </p>
                                            <button
                                                onClick={() => {
                                                    setSearch("");
                                                    setQuery("");
                                                    setCategory("");
                                                    setTask("");
                                                    setProfile("");
                                                    setSections(null);
                                                    setPage(0);
                                                }}
                                            >
                                                Clear search and filters
                                            </button>
                                        </>
                                    ) : (
                                        <p>
                                            {scope === "mine"
                                                ? "Your library is empty. Create a question, assign an exercise, or add a question from Public library."
                                                : "No public questions from other tutors yet."}
                                        </p>
                                    )}
                                </div>
                            )}
                            <footer className="writing-list-toolbar">
                                <button
                                    disabled={!page}
                                    onClick={() => setPage((p) => p - 1)}
                                >
                                    Previous
                                </button>
                                <span>
                                    {total} questions · Page {page + 1}
                                </span>
                                <button
                                    disabled={(page + 1) * 50 >= total}
                                    onClick={() => setPage((p) => p + 1)}
                                >
                                    Next
                                </button>
                            </footer>
                        </>
                    )
                )}
            </div>
        </section>
    );
}
