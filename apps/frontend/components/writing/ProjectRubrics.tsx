"use client";
import ListFilters, { FilterSelect } from "./ListFilters";
import { matchesSearch } from "../../lib/writing/list-filters";
import { useEffect, useState } from "react";
import { createPortal } from "react-dom";
import { Plus, Trash2, Save } from "lucide-react";
import { useWritingApi } from "../../lib/writing/context";
import { type Profile } from "../../lib/writing/types";
import { errorText } from "../../lib/parity";
import { assessmentGroup } from "../../lib/writing/assessment";
type Settings = { revision: number; catalog: Record<string, Profile> };
const labels: Record<string, string> = {
    ielts: "IELTS Task",
    research: "Research",
};
const sectionNames: Record<string, string> = {
    intro: "Introduction",
    overview: "Overview",
    body: "Body",
    conclusion: "Conclusion",
    closing: "Closing",
    whole: "Whole paper",
};
export default function ProjectRubrics({
    editable,
    headerActions,
}: {
    editable: boolean;
    headerActions?: HTMLDivElement | null;
}) {
    const { json, project } = useWritingApi();
    const [value, setValue] = useState<Settings | null>(null),
        [profile, setProfile] = useState("bar"),
        [category, setCategory] = useState("ielts"),
        [error, setError] = useState(""),
        [notice, setNotice] = useState(""),
        [busy, setBusy] = useState(false);
    useEffect(() => {
        let stopped = false;
        void json<Settings>("/writing/settings")
            .then((v) => {
                if (!stopped) setValue(v);
            })
            .catch((e) => {
                if (!stopped) setError(errorText(e));
            });
        return () => {
            stopped = true;
        };
    }, [project]);
    function edit(fn: (v: Settings) => void) {
        if (!value) return;
        const next = structuredClone(value);
        fn(next);
        setValue(next);
        setNotice("Unsaved changes");
    }
    async function save() {
        if (!value) return;
        setBusy(true);
        setError("");
        try {
            setValue(
                await json<Settings>("/writing/settings", {
                    method: "PUT",
                    body: JSON.stringify({
                        revision: value.revision,
                        catalog: value.catalog,
                    }),
                }),
            );
            setNotice(
                "Rubric templates saved. Existing exercises retain their original criteria.",
            );
        } catch (e) {
            setError(errorText(e));
        } finally {
            setBusy(false);
        }
    }
    const [query, setQuery] = useState(""),
        [taskFilter, setTaskFilter] = useState("");
    const selected = value?.catalog[profile],
        allItems = Object.entries(value?.catalog || {}).filter(
            ([, p]) => (p.category || "ielts") === category,
        );
    const items = allItems.filter(
        ([, p]) =>
            matchesSearch(
                query,
                p.label,
                ...Object.values(p.checks)
                    .flat()
                    .map((check) => check.text),
                ...Object.values(p.sectionTitles || {}),
            ) &&
            (category !== "ielts" ||
                !taskFilter ||
                p.task === Number(taskFilter)),
    );
    const saveAction = editable && (
        <button
            className="primary"
            disabled={!value || busy}
            onClick={() => void save()}
        >
            <Save size={16} />
            Save rubric templates
        </button>
    );
    return (
        <section className="rubric-manager">
            {headerActions ? (
                <>
                    {createPortal(saveAction, headerActions)}
                    <p className="writing-module-intro">
                        Manage reusable rubric templates. Each exercise keeps
                        its own customizable snapshot.
                    </p>
                </>
            ) : (
                <header>
                    <div>
                        <span className="writing-eyebrow">
                            RUBRIC TEMPLATES
                        </span>
                        <h2>Assessment categories</h2>
                        <p>
                            Manage reusable rubric templates. Each exercise
                            keeps its own customizable snapshot.
                        </p>
                    </div>
                    {saveAction}
                </header>
            )}
            <nav className="rubric-category-tabs">
                {Object.entries(labels).map(([key, label]) => (
                    <button
                        key={key}
                        aria-pressed={assessmentGroup(category) === key}
                        onClick={() => {
                            setCategory(key);
                            setProfile(
                                Object.entries(value?.catalog || {}).find(
                                    ([, p]) => (p.category || "ielts") === key,
                                )?.[0] || "",
                            );
                        }}
                    >
                        {label}
                    </button>
                ))}
            </nav>
            {assessmentGroup(category) === "research" && (
                <label className="research-type-filter">
                    Document type
                    <select
                        aria-label="Research document type"
                        value={category}
                        onChange={(e) => {
                            const next = e.target.value;
                            setCategory(next);
                            setProfile(
                                Object.entries(value?.catalog || {}).find(
                                    ([, p]) => (p.category || "ielts") === next,
                                )?.[0] || "",
                            );
                        }}
                    >
                        <option value="research">Research paper</option>
                        <option value="report">Report</option>
                    </select>
                </label>
            )}
            {error && (
                <p role="alert" className="writing-error">
                    {error}
                </p>
            )}
            {notice && (
                <p role="status" className="writing-notice">
                    {notice}
                </p>
            )}
            <ListFilters
                label="Search rubric templates"
                query={query}
                onQuery={setQuery}
                count={items.length}
                total={allItems.length}
                active={!!query || !!taskFilter}
                onReset={() => {
                    setQuery("");
                    setTaskFilter("");
                }}
            >
                {category === "ielts" && (
                    <FilterSelect
                        label="IELTS task"
                        value={taskFilter}
                        onChange={setTaskFilter}
                        options={[
                            { value: "", label: "All tasks" },
                            { value: "1", label: "Task 1" },
                            { value: "2", label: "Task 2" },
                        ]}
                    />
                )}
            </ListFilters>
            <div className="rubric-manager-layout">
                <aside>
                    <label>
                        Rubric template
                        <select
                            aria-label="Rubric profile"
                            value={profile}
                            onChange={(e) => setProfile(e.target.value)}
                        >
                            {selected &&
                                !items.some(([key]) => key === profile) && (
                                    <option value={profile}>
                                        Current: {selected.label} (outside
                                        filters)
                                    </option>
                                )}
                            {!selected && (
                                <option value="">Choose a rubric</option>
                            )}
                            {items.map(([key, p]) => (
                                <option key={key} value={key}>
                                    {p.label}
                                </option>
                            ))}
                        </select>
                    </label>
                    {items.map(([key, p]) => (
                        <button
                            className={profile === key ? "active" : ""}
                            key={key}
                            onClick={() => setProfile(key)}
                        >
                            {p.label}
                            <small>
                                {Object.values(p.checks).reduce(
                                    (sum, list) => sum + list.length,
                                    0,
                                )}{" "}
                                criteria
                            </small>
                        </button>
                    ))}
                    {!items.length && (
                        <p className="writing-muted">
                            No rubric templates match these filters.
                        </p>
                    )}
                    {editable && (
                        <button
                            onClick={() => {
                                const id =
                                    "profile_" +
                                    crypto.randomUUID().slice(0, 16);
                                edit((v) => {
                                    v.catalog[id] = {
                                        category: category as
                                            "ielts" | "report" | "research",
                                        family:
                                            category === "ielts"
                                                ? "academic"
                                                : category,
                                        task: category === "ielts" ? 2 : 1,
                                        label: "New rubric",
                                        focus: [],
                                        sectionTitles: {
                                            intro: "Introduction",
                                            body: "Body",
                                            whole: "Whole paper",
                                        },
                                        checks: {
                                            intro: [],
                                            body: [],
                                            whole: [],
                                        },
                                    };
                                });
                                setProfile(id);
                            }}
                        >
                            <Plus size={16} />
                            Add rubric
                        </button>
                    )}
                </aside>
                <div className="rubric-editor">
                    {selected ? (
                        <>
                            <div className="rubric-editor-heading">
                                <label>
                                    Rubric name
                                    <input
                                        aria-label="Rubric name"
                                        disabled={!editable || busy}
                                        value={selected.label}
                                        onChange={(e) =>
                                            edit((v) => {
                                                v.catalog[profile].label =
                                                    e.target.value;
                                            })
                                        }
                                    />
                                </label>
                                {editable && (
                                    <button
                                        onClick={() => {
                                            if (
                                                !confirm(
                                                    "Delete this rubric? Existing exercises keep their snapshots.",
                                                )
                                            )
                                                return;
                                            edit((v) => {
                                                delete v.catalog[profile];
                                            });
                                            setProfile(
                                                items.find(
                                                    ([key]) => key !== profile,
                                                )?.[0] || "",
                                            );
                                        }}
                                    >
                                        <Trash2 size={16} />
                                        Delete rubric
                                    </button>
                                )}
                            </div>
                            <div className="rubric-metadata-grid">
                            {category === "ielts" && (
                                <div className="writing-form-grid">
                                    <label>
                                        Task
                                        <select
                                            disabled={!editable}
                                            value={selected.task}
                                            onChange={(e) =>
                                                edit((v) => {
                                                    v.catalog[profile].task =
                                                        Number(e.target.value);
                                                })
                                            }
                                        >
                                            <option value={1}>Task 1</option>
                                            <option value={2}>Task 2</option>
                                        </select>
                                    </label>
                                </div>
                            )}
                            <label>
                                Rubric category
                                <select
                                    aria-label="Rubric category"
                                    disabled={!editable}
                                    value={assessmentGroup(category)}
                                    onChange={(e) => {
                                        const next = e.target.value as
                                            "ielts" | "report" | "research";
                                        if (
                                            !confirm(
                                                "Change rubric category? Codes will reset; assigned exercises retain their snapshots.",
                                            )
                                        )
                                            return;
                                        edit((v) => {
                                            const p = v.catalog[profile];
                                            p.category = next;
                                            p.family =
                                                next === "ielts"
                                                    ? "academic"
                                                    : next;
                                            p.task = 1;
                                            for (const checks of Object.values(
                                                p.checks,
                                            ))
                                                for (const check of checks)
                                                    check.codes = [
                                                        next === "ielts"
                                                            ? "TA"
                                                            : "CONTENT",
                                                    ];
                                        });
                                        setCategory(next);
                                    }}
                                >
                                    {Object.entries(labels).map(
                                        ([key, label]) => (
                                            <option key={key} value={key}>
                                                {label}
                                            </option>
                                        ),
                                    )}
                                </select>
                            </label>
                            </div>
                            <p className="writing-muted">
                                Default criterion weight is 2. Exercise points
                                are distributed to match the total score set by
                                the tutor.
                            </p>
                            {Object.entries(selected.checks).map(
                                ([section, checks]) => (
                                    <details
                                        className="rubric-section-card"
                                        key={section}
                                        open
                                    >
                                        <summary>
                                            <strong>
                                                {selected.sectionTitles?.[
                                                    section
                                                ] ||
                                                    sectionNames[section] ||
                                                    section}
                                            </strong>
                                            <small>
                                                <span>{checks.length} criteria</span>
                                                <span className="rubric-weight-total">{checks.reduce((total, check) => total + (check.weight ?? 2), 0)} total weight</span>
                                            </small>
                                        </summary>
                                        <div className="rubric-section-content">
                                            <div className="rubric-editor-heading">
                                                <input
                                                    aria-label={`Section title ${section}`}
                                                    disabled={!editable}
                                                    value={
                                                        selected
                                                            .sectionTitles?.[
                                                            section
                                                        ] ||
                                                        sectionNames[section] ||
                                                        section
                                                    }
                                                    onChange={(e) =>
                                                        edit((v) => {
                                                            v.catalog[
                                                                profile
                                                            ].sectionTitles = {
                                                                ...v.catalog[
                                                                    profile
                                                                ].sectionTitles,
                                                                [section]:
                                                                    e.target
                                                                        .value,
                                                            };
                                                        })
                                                    }
                                                />
                                                {editable && (
                                                    <button
                                                        onClick={() => {
                                                            if (
                                                                confirm(
                                                                    "Delete this section and its criteria?",
                                                                )
                                                            )
                                                                edit((v) => {
                                                                    delete v
                                                                        .catalog[
                                                                        profile
                                                                    ].checks[
                                                                        section
                                                                    ];
                                                                    if (
                                                                        v
                                                                            .catalog[
                                                                            profile
                                                                        ]
                                                                            .sectionTitles
                                                                    )
                                                                        delete v
                                                                            .catalog[
                                                                            profile
                                                                        ]
                                                                            .sectionTitles![
                                                                            section
                                                                        ];
                                                                });
                                                        }}
                                                    >
                                                        <Trash2 size={14} /> Remove section
                                                    </button>
                                                )}
                                            </div>
                                            {checks.map((check, index) => (
                                                <div
                                                    key={check.id}
                                                    className="rubric-check-row"
                                                >
                                                    <span className="rubric-criterion-number" aria-hidden="true">{String(index + 1).padStart(2, "0")}</span>
                                                    <label className="rubric-criterion-field">
                                                        <span>Criterion {index + 1}</span>
                                                    <textarea
                                                        rows={2}
                                                        aria-label={`Criterion ${section} ${index + 1}`}
                                                        disabled={
                                                            !editable || busy
                                                        }
                                                        value={check.text}
                                                        onChange={(e) =>
                                                            edit((v) => {
                                                                v.catalog[
                                                                    profile
                                                                ].checks[
                                                                    section
                                                                ][index].text =
                                                                    e.target.value;
                                                            })
                                                        }
                                                    />
                                                    </label>
                                                    <div className="rubric-criterion-controls">
                                                        <label>
                                                            Weight
                                                            <input
                                                                aria-label={`Criterion weight ${section} ${index + 1}`}
                                                                type="number"
                                                                disabled={
                                                                    !editable ||
                                                                    busy
                                                                }
                                                                min={0.01}
                                                                max={10000}
                                                                step="any"
                                                                value={
                                                                    check.weight ??
                                                                    2
                                                                }
                                                                onChange={(e) =>
                                                                    edit(
                                                                        (v) => {
                                                                            v.catalog[
                                                                                profile
                                                                            ].checks[
                                                                                section
                                                                            ][
                                                                                index
                                                                            ].weight =
                                                                                Number(
                                                                                    e
                                                                                        .target
                                                                                        .value,
                                                                                );
                                                                        },
                                                                    )
                                                                }
                                                            />
                                                        </label>
                                                        {editable && (
                                                            <button
                                                                aria-label={`Remove criterion ${section} ${index + 1}`}
                                                                onClick={() =>
                                                                    edit(
                                                                        (v) => {
                                                                            v.catalog[
                                                                                profile
                                                                            ].checks[
                                                                                section
                                                                            ].splice(
                                                                                index,
                                                                                1,
                                                                            );
                                                                        },
                                                                    )
                                                                }
                                                            >
                                                                <Trash2
                                                                    size={15}
                                                                />
                                                                Remove
                                                            </button>
                                                        )}
                                                    </div>
                                                </div>
                                            ))}
                                            {editable && (
                                                <button
                                                    onClick={() =>
                                                        edit((v) => {
                                                            v.catalog[
                                                                profile
                                                            ].checks[
                                                                section
                                                            ].push({
                                                                id:
                                                                    "check_" +
                                                                    crypto.randomUUID(),
                                                                text: "New criterion",
                                                                codes: [],
                                                                weight: 2,
                                                            });
                                                        })
                                                    }
                                                >
                                                    <Plus size={15} />
                                                    Add criterion
                                                </button>
                                            )}
                                        </div>
                                    </details>
                                ),
                            )}
                            {editable && (
                                <button
                                    onClick={() => {
                                        const id =
                                            "section_" +
                                            crypto.randomUUID().slice(0, 12);
                                        edit((v) => {
                                            v.catalog[profile].checks[id] = [];
                                            v.catalog[profile].sectionTitles = {
                                                ...v.catalog[profile]
                                                    .sectionTitles,
                                                [id]: "New assessment section",
                                            };
                                        });
                                    }}
                                >
                                    <Plus size={16} />
                                    Add assessment section
                                </button>
                            )}
                        </>
                    ) : (
                        <div className="writing-empty">
                            Select or add a rubric to begin.
                        </div>
                    )}
                </div>
            </div>
        </section>
    );
}
