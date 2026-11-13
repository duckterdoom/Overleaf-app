"use client";
import { useEffect, useRef, useState } from "react";
import {
    Check,
    ChevronDown,
    MessageSquarePlus,
    X,
    ClipboardCheck,
} from "lucide-react";
import {
    usesBands,
    catalog,
    checklistScores,
    codes,
    criteria,
    defaultSection,
    estimate,
    paragraphs,
    rounded,
    taskEstimate,
    type Assignment,
    type Grade,
    type Review,
    type Section,
    type Submission,
} from "../../lib/writing/types";
import { CommutingChart } from "./AssignmentForm";
import { useWritingApi } from "../../lib/writing/context";
import PromptDocument from './PromptDocument';

const states: Record<string, string> = {
    unreviewed: "Unreviewed",
    met: "Met",
    partial: "Partly met",
    missing: "Needs improvement",
    na: "Not applicable",
};
const kinds: Record<string, string> = {
    intro: "Introduction / Opening",
    overview: "Overview",
    body: "Body",
    conclusion: "Conclusion",
    closing: "Closing",
    whole: "Whole paper / task requirements",
};
export default function ReviewPaper({
    submission,
    review,
    onChange,
    editable,
    onSave,
    onPublish,
    busy,
    dirty,
    published,
    onDocument,
}: {
    submission: Submission;
    review: Review | null;
    onChange: (r: Review) => void;
    editable: boolean;
    onSave: () => Promise<boolean>;
    onPublish: (reason: string) => Promise<void>;
    busy: boolean;
    dirty: boolean;
    published: Review | null;
    onDocument: (report: boolean) => void;
}) {
    const { assetUrl } = useWritingApi();
    const assignment = submission.assignment;
    const hasBands=usesBands(assignment);
    const isIelts=!assignment.category||assignment.category==='ielts';
    const projectCatalog = assignment.rubricCatalog || catalog;
    const bandRef = useRef<HTMLElement>(null);
    const [task, setTask] = useState(assignment.tasks[0]),
        [active, setActive] = useState<number | null>(null),
        [band, setBand] = useState(false),
        [reason, setReason] = useState("");
    const [selection, setSelection] = useState<{
        paragraph: number;
        text: string;
    } | null>(null);
    const value = review || {
        revision: 0,
        tasks: {},
        summary: "",
        recommendations: "",
    };
    const grade = value.tasks[String(task)],
        parts = paragraphs(submission.answers?.[String(task)] || "");
    const selectedProfile=projectCatalog[grade?.profile || assignment.profiles[String(task)]];
    const kinds=Object.fromEntries(Object.keys(selectedProfile?.checks||{}).map(key=>[key,selectedProfile?.sectionTitles?.[key]||({intro:'Introduction / Opening',overview:'Overview',body:'Body',conclusion:'Conclusion',closing:'Closing',whole:'Whole paper / task requirements'} as Record<string,string>)[key]||key]));
    const [autoError,setAutoError]=useState('');
    function autoScore(){
        if(!grade||!selectedProfile)return;
        const sections=[...parts.map((_,i)=>section(i)),section(-1)];
        const scores=checklistScores(selectedProfile,sections,task);
        if(!scores){setAutoError('Review every criterion in each paragraph and the whole-paper section. Each IELTS code needs at least one applicable rating.');return;}
        setAutoError('');changeGrade({scores,scoreSource:'checklist'});
    }
    useEffect(() => {
        if (!band) return;
        const previous = document.activeElement as HTMLElement | null;
        const panel = bandRef.current;
        const controls = () => Array.from(panel?.querySelectorAll<HTMLElement>(
            'button:not(:disabled), select:not(:disabled), textarea:not(:disabled), input:not(:disabled), [tabindex="0"]',
        ) || []).filter((node) => node.getClientRects().length > 0);
        controls()[0]?.focus();
        const trap = (event: KeyboardEvent) => {
            if (event.key !== "Tab") return;
            const nodes = controls();
            const first = nodes[0], last = nodes[nodes.length - 1];
            if (event.shiftKey && document.activeElement === first) {
                event.preventDefault(); last?.focus();
            } else if (!event.shiftKey && document.activeElement === last) {
                event.preventDefault(); first?.focus();
            }
        };
        panel?.addEventListener("keydown", trap);
        return () => { panel?.removeEventListener("keydown", trap); previous?.focus(); };
    }, [band]);
    useEffect(() => {
        const key = (e: KeyboardEvent) => {
            if (e.key === "Escape") {
                setBand(false);
                setActive(null);
            }
        };
        window.addEventListener("keydown", key);
        return () => window.removeEventListener("keydown", key);
    }, []);
    function changeGrade(update: Partial<Grade>) {
        if (!grade || !editable) return;
        onChange({
            ...value,
            tasks: { ...value.tasks, [task]: { ...grade, ...update } },
        });
    }
    function section(index: number): Section {
        const existing=grade?.sections.find(item=>item.paragraph===index);
        if(existing)return existing;
        const generated=index===-1?{paragraph:-1,kind:selectedProfile?.checks.whole?'whole':Object.keys(selectedProfile?.checks||{})[0]||'whole',title:kinds.whole||'Whole paper',checks:{},note:'',quote:''}:defaultSection(task,assignment,index,parts.length);
        if(index!==-1 && !selectedProfile?.checks[generated.kind]){
            generated.kind=Object.keys(selectedProfile?.checks||{}).find(key=>key!=='whole')||'whole';
        }
        generated.title=kinds[generated.kind]||generated.title;
        return generated;

    }
    function changeSection(index: number, update: Partial<Section>) {
        if (!grade) return;
        const next = { ...section(index), ...update };
        const sections=[...grade.sections.filter(item=>item.paragraph!==index),next];
        const updates:Partial<Grade>={sections};
        if(hasBands && grade.scoreSource==='checklist' && selectedProfile){
            const full=[...parts.map((_,i)=>sections.find(s=>s.paragraph===i)||section(i)),sections.find(s=>s.paragraph===-1)||section(-1)];
            updates.scores=checklistScores(selectedProfile,full,task)||Object.fromEntries(codes(task).map(code=>[code,null]));
        }
        changeGrade({
            ...updates,
        });
    }
    function card(index: number) {
        const item = section(index),
            open = active === index;
        const checks =
            projectCatalog[grade?.profile || assignment.profiles[String(task)]]
                ?.checks[item.kind] || [];
        const met = checks.filter(
            (check) => item.checks[check.id] === "met",
        ).length;
        return (
            <aside
                className={`writing-margin-card ${open ? "expanded" : ""} ${item.note ? "has-feedback" : ""}`}
                key={"card-" + index}
            >
                <button
                    className="writing-card-toggle"
                    aria-expanded={open}
                    onClick={() => setActive(open ? null : index)}
                >
                    <span className="writing-tutor-dot">
                        {published?.tutorName?.[0] || "T"}
                    </span>
                    <span>
                        <strong>{item.title}</strong>
                        <small>
                            {item.note
                                ? "Feedback added"
                                : `${met}/${checks.length} criteria met`}
                        </small>
                    </span>
                    <ChevronDown size={15} />
                </button>
                {open && (
                    <div className="writing-card-body">
                        {editable && (
                            <label>
                                Writing section
                                <select
                                    aria-label={`Paragraph section ${index + 1}`}
                                    value={item.kind}
                                    disabled={index === -1}
                                    onChange={(e) =>
                                        changeSection(index, {
                                            kind: e.target.value,
                                            title: kinds[e.target.value],
                                        })
                                    }
                                >
                                    {Object.entries(kinds).map(
                                        ([kind, label]) => (
                                            <option key={kind} value={kind}>
                                                {label}
                                            </option>
                                        ),
                                    )}
                                </select>
                            </label>
                        )}
                        <div className="writing-checks">
                            {checks.map((check) => (
                                <div className="writing-check" key={check.id}>
                                    <label>
                                        <input
                                            type="checkbox"
                                            disabled={!editable}
                                            checked={
                                                item.checks[check.id] === "met"
                                            }
                                            onChange={(e) =>
                                                changeSection(index, {
                                                    checks: {
                                                        ...item.checks,
                                                        [check.id]: e.target
                                                            .checked
                                                            ? "met"
                                                            : "missing",
                                                    },
                                                })
                                            }
                                        />
                                        <span>
                                            {check.text}
                                            <small>
                                                {check.codes.join(" / ")}
                                            </small>
                                        </span>
                                    </label>
                                    {editable ? (
                                        <select
                                            aria-label={
                                                "Status " + check.text
                                            }
                                            value={
                                                item.checks[check.id] ||
                                                "unreviewed"
                                            }
                                            onChange={(e) =>
                                                changeSection(index, {
                                                    checks: {
                                                        ...item.checks,
                                                        [check.id]:
                                                            e.target.value,
                                                    },
                                                })
                                            }
                                        >
                                            {Object.entries(states).map(
                                                ([id, label]) => (
                                                    <option key={id} value={id}>
                                                        {label}
                                                    </option>
                                                ),
                                            )}
                                        </select>
                                    ) : item.checks[check.id] &&
                                      item.checks[check.id] !== "unreviewed" ? (
                                        <small className="writing-check-status">
                                            {states[item.checks[check.id]]}
                                        </small>
                                    ) : null}
                                </div>
                            ))}
                        </div>
                        {editable && selection?.paragraph === index && (
                            <button
                                className="writing-evidence-button"
                                onClick={() =>
                                    changeSection(index, {
                                        quote: selection.text.slice(0, 3000),
                                    })
                                }
                            >
                                <MessageSquarePlus size={14} />
                                Attach selected text
                            </button>
                        )}
                        {item.quote && (
                            <blockquote>
                                {item.quote}
                                {editable && (
                                    <button
                                        title="Remove evidence"
                                        onClick={() =>
                                            changeSection(index, { quote: "" })
                                        }
                                    >
                                        <X size={12} />
                                    </button>
                                )}
                            </blockquote>
                        )}
                        {editable ? (
                            <label>
                                Paragraph feedback
                                <textarea
                                    aria-label={`Paragraph feedback ${index + 1}`}
                                    rows={4}
                                    value={item.note}
                                    placeholder="Strengths, improvements and next steps…"
                                    onChange={(e) =>
                                        changeSection(index, {
                                            note: e.target.value,
                                        })
                                    }
                                />
                            </label>
                        ) : (
                            <p>{item.note || "No additional feedback."}</p>
                        )}
                    </div>
                )}
            </aside>
        );
    }
    return (
        <div className="writing-review">
            <div className="writing-document-toolbar">
                <div
                    className="writing-task-tabs"
                    role="tablist"
                    aria-label="Submitted task"
                >
                    {assignment.tasks.map((n) => (
                        <button
                            key={n}
                            role="tab"
                            aria-selected={task === n}
                            onClick={() => {
                                setTask(n);
                                setActive(null);
                            }}
                        >
                            {isIelts?`Task ${n}`:assignment.category==='report'?'Report':'Research'}
                        </button>
                    ))}
                </div>
                <span className="writing-muted">
                    {submission.studentName} · Attempt {submission.attempt}
                </span>
                <button onClick={() => setBand(true)}>
                    <ClipboardCheck size={15} />
                    {hasBands?'Band summary':'Assessment summary'}
                </button>
                {published && (
                    <button onClick={() => onDocument(true)}>
                        Export LaTeX report
                    </button>
                )}
                {published?.scorePercent!==undefined&&<span className="writing-section-score">Checklist: {published.scorePercent}% · {published.criteriaCount} criteria</span>}
                {editable && (
                    <button
                        className="primary"
                        disabled={busy || !dirty}
                        onClick={() => void onSave()}
                    >
                        {busy ? "Saving…" : "Save assessment draft"}
                    </button>
                )}
            </div>
            {!editable && !published && (
                <div className="writing-notice">
                    Submitted. Your tutor has not published the results yet. Assessment drafts remain private.
                </div>
            )}
            <div className="writing-review-meta">
                <span>
                        {!isIelts?assignment.category==='report'?'Report':'Research':assignment.variant === "academic"
                        ? "Academic"
                        : "General Training"}{" "}
                    · {projectCatalog[assignment.profiles[String(task)]]?.label}
                </span>
                <strong>{submission.wordCounts[String(task)]} words</strong>
            </div>
            {editable && grade && (
                <label className="writing-profile-select">
                    Assessment profile
                    <select
                        aria-label="Assessment profile"
                        value={grade.profile}
                        onChange={(e) =>
                            changeGrade({ profile: e.target.value,sections:[],scores:hasBands?Object.fromEntries(codes(task).map(code=>[code,null])):{},scoreSource:'manual' })
                        }
                    >
                        {Object.entries(projectCatalog)
                            .filter(
                                ([, p]) =>
                                    (p.category||'ielts')===(assignment.category||'ielts') && p.task === task &&
                                    (!isIelts || task === 2 ||
                                        p.family === assignment.variant),
                            )
                            .map(([id, p]) => (
                                <option key={id} value={id}>
                                    {p.label}
                                </option>
                            ))}
                    </select>
                    <small>
                        Checklists support evidence-based assessment; ticks alone are not an official
                        band.
                    </small>
                </label>
            )}
            <div className="writing-review-scroll">
                <div className="writing-paper-grid">
                    <div className="writing-paper-title">
                        <span className="writing-eyebrow">
                            {isIelts?`TASK ${task}`:assignment.category?.toUpperCase()} · IMMUTABLE SUBMISSION
                        </span>
                        <p>{assignment.prompts[String(task)]}</p>
                        {assignment.promptFiles?.[String(task)] && <details><summary>{isIelts?`View question Task ${task}`:'View exercise instructions'}</summary><PromptDocument focused file={assignment.promptFiles[String(task)]}/></details>}
                        {task === 1 &&
                            assignment.imageId &&
                            (assignment.imageId === "sample-commuting" ? (
                                <CommutingChart />
                            ) : (
                                <img
                                    src={assetUrl(assignment.imageId)}
                                    alt="Task 1 visual"
                                />
                            ))}
                    </div>
                    <div />
                    {parts.map((text, index) => (
                        <div className="writing-paragraph-row" key={index}>
                            <div
                                className={`writing-paragraph ${active === index ? "selected" : ""}`}
                            >
                                <span className="writing-paragraph-label">
                                    {section(index).title} · Paragraph {index + 1}
                                </span>
                                <p
                                    onMouseUp={(e) => {
                                        const selected = window.getSelection();
                                        if (
                                            selected &&
                                            selected.toString().trim() &&
                                            e.currentTarget.contains(
                                                selected.anchorNode,
                                            ) &&
                                            e.currentTarget.contains(
                                                selected.focusNode,
                                            )
                                        ) {
                                            setSelection({
                                                paragraph: index,
                                                text: selected
                                                    .toString()
                                                    .trim(),
                                            });
                                            setActive(index);
                                        }
                                    }}
                                >
                                    {text}
                                </p>
                                <button
                                    className="writing-comment-trigger"
                                    title={`Paragraph feedback ${index + 1}`}
                                    onClick={() =>
                                        setActive(
                                            active === index ? null : index,
                                        )
                                    }
                                >
                                    <MessageSquarePlus size={16} />
                                </button>
                            </div>
                            {(editable || published) && card(index)}
                        </div>
                    ))}
                    {(editable || published) && selectedProfile?.checks.whole && (
                        <div className="writing-paragraph-row">
                            <div className="writing-whole-note">
                                <Check size={18} />
                                {hasBands?'Review the whole paper against the task before summarizing bands.':'Review requirements and evidence before publishing feedback.'}
                            </div>
                            {card(-1)}
                        </div>
                    )}
                </div>
            </div>
            {band && (
                <div
                    className="writing-band-backdrop"
                    onClick={() => setBand(false)}
                >
                    <aside
                        ref={bandRef}
                        className="writing-band-panel"
                        role="dialog"
                        aria-modal="true"
                        aria-label={hasBands?'IELTS band summary':'Assessment summary'}
                        onClick={(e) => e.stopPropagation()}
                    >
                        <header>
                            <div>
                                <span className="writing-eyebrow">
                                    ASSESSMENT SUMMARY
                                </span>
                                <h2>{hasBands?'4 IELTS criteria':'Feedback & conclusion'}</h2>
                            </div>
                            <button
                                aria-label={hasBands?'Close band summary':'Close assessment summary'}
                                onClick={() => setBand(false)}
                            >
                                <X size={20} />
                            </button>
                        </header>
                        {hasBands && <p className="writing-muted">
                            Practice bands are tutor estimates, not official IELTS examination results.
                        </p>}
                        {hasBands && editable && <section className="writing-notice"><p>Practice score = 9 × mean attainment per code, rounded to 0.5. Met = 1, partly met = 0.5, needs improvement = 0. Not applicable is excluded; repeated criteria use the weakest rating. This is a teaching convention, not an official band conversion.</p><button onClick={autoScore}>Summarize checklist scores</button>{autoError&&<p role="alert">{autoError}</p>}{grade?.scoreSource==='checklist'&&<small>Checklist scores are suggestions; the tutor can adjust them.</small>}</section>}
                        <div className="writing-task-tabs">
                            {assignment.tasks.map((n) => (
                                <button
                                    key={n}
                                    aria-pressed={task === n}
                                    onClick={() => setTask(n)}
                                >
                                    {isIelts?`Task ${n}`:'Document'}
                                </button>
                            ))}
                        </div>
                        {(hasBands?codes(task):[]).map((code) => (
                            <section
                                className="writing-band-card"
                                key={task + code}
                            >
                                <div>
                                    <strong>{code}</strong>
                                    <span>{criteria[code].title}</span>
                                    {editable && grade ? (
                                        <select
                                            aria-label={`Band ${code}`}
                                            value={grade.scores[code] ?? ""}
                                            onChange={(e) =>
                                                changeGrade({
                                                    scoreSource:'manual',
                                                    scores: {
                                                        ...grade.scores,
                                                        [code]:
                                                            e.target.value ===
                                                            ""
                                                                ? null
                                                                : Number(
                                                                      e.target
                                                                          .value,
                                                                  ),
                                                    },
                                                })
                                            }
                                        >
                                            <option value="">—</option>
                                            {Array.from(
                                                { length: 19 },
                                                (_, i) => (
                                                    <option
                                                        key={i}
                                                        value={i / 2}
                                                    >
                                                        {(i / 2).toFixed(1)}
                                                    </option>
                                                ),
                                            )}
                                        </select>
                                    ) : (
                                        <b>{grade?.scores[code] ?? "—"}</b>
                                    )}
                                </div>
                                <details>
                                    <summary>Assessment guidance</summary>
                                    <p>{criteria[code].hint}</p>
                                </details>
                                {editable && grade ? (
                                    <label>
                                        Feedback
                                        <textarea
                                            aria-label={`Feedback ${code}`}
                                            rows={3}
                                            value={grade.notes[code] || ""}
                                            onChange={(e) =>
                                                changeGrade({
                                                    notes: {
                                                        ...grade.notes,
                                                        [code]: e.target.value,
                                                    },
                                                })
                                            }
                                        />
                                    </label>
                                ) : (
                                    <p>
                                        {grade?.notes[code] ||
                                            "No feedback yet."}
                                    </p>
                                )}
                            </section>
                        ))}
                        {hasBands && <div className="writing-band-estimate">
                            <span>Task {task} estimated band</span>
                            <strong>
                                {taskEstimate(grade) === null
                                    ? "—"
                                    : rounded(taskEstimate(grade)!).toFixed(1)}
                            </strong>
                        </div>}
                        {hasBands && assignment.tasks.length === 2 && (
                            <div className="writing-band-estimate">
                                <span>
                                    Writing estimated band
                                    <br />
                                    <small>Task 2 carries twice the weight</small>
                                </span>
                                <strong>
                                    {estimate(value, assignment.tasks)?.toFixed(
                                        1,
                                    ) || "—"}
                                </strong>
                            </div>
                        )}
                        {editable ? (
                            <>
                                <label>
                                    Overall feedback
                                    <textarea
                                        aria-label="Overall feedback"
                                        rows={4}
                                        value={value.summary}
                                        onChange={(e) =>
                                            onChange({
                                                ...value,
                                                summary: e.target.value,
                                            })
                                        }
                                    />
                                </label>
                                <label>
                                    Next steps
                                    <textarea
                                        aria-label="Next steps"
                                        rows={4}
                                        value={value.recommendations}
                                        onChange={(e) =>
                                            onChange({
                                                ...value,
                                                recommendations: e.target.value,
                                            })
                                        }
                                    />
                                </label>
                                {published && (
                                    <label>
                                        Reason for republishing
                                        <input
                                            aria-label="Reason for revision"
                                            value={reason}
                                            onChange={(e) =>
                                                setReason(e.target.value)
                                            }
                                        />
                                    </label>
                                )}
                                <footer>
                                    <button
                                        disabled={busy || !dirty}
                                        onClick={() => void onSave()}
                                    >
                                        Save draft
                                    </button>
                                    <button
                                        className="primary"
                                        disabled={
                                            busy ||
                                            hasBands && estimate(
                                                value,
                                                assignment.tasks,
                                            ) === null
                                        }
                                        onClick={() => void onPublish(reason)}
                                    >
                                        {published
                                            ? "Publish revised results"
                                            : "Publish results"}
                                    </button>
                                </footer>
                            </>
                        ) : (
                            <>
                                <h3>Overall feedback</h3>
                                <p>{value.summary || "—"}</p>
                                <h3>Next steps</h3>
                                <p>{value.recommendations || "—"}</p>
                            </>
                        )}
                    </aside>
                </div>
            )}
        </div>
    );
}
