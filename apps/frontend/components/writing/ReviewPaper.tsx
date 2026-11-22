"use client";
import { useEffect, useRef, useState } from "react";
import {
    Check,
    ChevronDown,
    ChevronUp,
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
import FloatingChecklist from './FloatingChecklist';
import PointAssessment from './PointAssessment';
import AnnotatedSubmission from './AnnotatedSubmission';
import {pointProgress} from '../../lib/writing/points';

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
    pendingFeedbackRef,
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
    pendingFeedbackRef?: React.MutableRefObject<boolean>;
}) {
    const { assetUrl } = useWritingApi();
    const assignment = submission.assignment;
    const hasBands=usesBands(assignment);
    const points=!!assignment.scoring;
    const progress=pointProgress(assignment,review||{revision:0,tasks:{},summary:'',recommendations:''});
    const complete=progress.count>0&&progress.graded===progress.count;
    const [questionCollapsed,setQuestionCollapsed]=useState(editable);
    function scorePoint(key:string,number:number|null){changeGrade({criterionScores:{...grade?.criterionScores,[key]:number}});}
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
    const [checklistCategory,setChecklistCategory]=useState('');
    const [pendingSentence,setPendingSentence]=useState(false);
    useEffect(()=>{if(pendingFeedbackRef)pendingFeedbackRef.current=pendingSentence;return()=>{if(pendingFeedbackRef)pendingFeedbackRef.current=false;};},[pendingSentence,pendingFeedbackRef]);
    function autoScore(){
        if(!grade||!selectedProfile)return;
        const sections=[...parts.map((_,i)=>section(i)),section(-1)];
        const scores=checklistScores(selectedProfile,sections,task);
        if(!scores){setAutoError('Review every criterion in each paragraph and the whole-paper section. Each IELTS code needs at least one applicable rating.');return;}
        setAutoError('');changeGrade({scores,scoreSource:'checklist'});
    }
    useEffect(() => {
        if (!band || points) return;
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
    }, [band,points]);
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
        if(points){const keys=Object.keys(selectedProfile?.checks||{});const category=keys.includes(checklistCategory)?checklistCategory:keys[0];return <FloatingChecklist title="Checklist score" onClose={()=>setActive(null)}><nav className="category-checklist-tabs" aria-label="Assigned assessment categories">{keys.map(key=><button key={key} aria-pressed={category===key} onClick={()=>setChecklistCategory(key)}>{kinds[key]}</button>)}</nav><p className="writing-muted">Score the whole submission against each assigned criterion. Paragraph count does not change the points.</p><PointAssessment assignment={assignment} review={value} task={task} category={category} editable={editable} onScore={scorePoint} onTotal={number=>onChange({...value,awardedTotal:number})}/>{editable&&<footer className="checklist-finish"><small>{complete?'All criteria scored. Ready to review.':'Score every assigned criterion to finish.'}</small>{complete&&<button className="primary" disabled={busy||pendingSentence} onClick={async()=>{if(await onSave()){setActive(null);setBand(true);}}}>Finish score</button>}</footer>}</FloatingChecklist>;}
        const item = section(index),
            open = active === index;
        const checks =
            projectCatalog[grade?.profile || assignment.profiles[String(task)]]
                ?.checks[item.kind] || [];
        if (!open) return null;
        return (
                    <FloatingChecklist key={`${task}:${index}`} title={`${item.title}${index===-1?'':` · Paragraph ${index+1}`}`} onClose={()=>setActive(null)}><div className="writing-card-body">
                        {editable && (
                            <label>
                                Writing section
                                <select
                                    aria-label={`Paragraph section ${index + 1}`}
                                    value={item.kind}
                                    disabled={!points && index === -1}
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
                        <div className="writing-checks">{points&&<PointAssessment assignment={assignment} review={value} task={task} category={item.kind} editable={editable} onScore={scorePoint}/>}
                            {!points&&checks.map((check) => (
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
                    </div></FloatingChecklist>
        );
    }
    return (
        <div className={`writing-review${points?` category-review${questionCollapsed?' question-compact':''}${band?' score-review-page':''}`:''}`}>
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
                            disabled={pendingSentence}
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
                {points&&(editable||published)&&<button aria-expanded={active===-1} onClick={()=>setActive(active===-1?null:-1)}><ClipboardCheck size={15}/>Checklist score</button>}
                {points&&!editable&&published&&<button onClick={()=>{setActive(null);setBand(true);}}>Review results</button>}
                {!points&&<button disabled={pendingSentence} onClick={() => {setActive(null);setBand(true);}}>
                    <ClipboardCheck size={15} />
                    {hasBands?'Band summary':'Assessment summary'}
                </button>}
                {published && (
                    <button onClick={() => onDocument(true)}>
                        Export LaTeX document
                    </button>
                )}
                {published?.totalScore!==undefined&&<span className="writing-section-score">Score: {published.totalScore} / {published.maxScore}</span>}
                {published?.scorePercent!==undefined&&<span className="writing-section-score">Checklist: {published.scorePercent}% · {published.criteriaCount} criteria</span>}
                {editable&&<span className="review-save-state" role="status">{dirty?'Saving changes...':'All changes saved'}</span>}
            </div>
            {!editable && !published && (
                <div className="writing-notice">
                    Submitted. Your tutor has not published the results yet. Assessment drafts remain private.
                </div>
            )}
            <div className="writing-review-meta">
                <span>
                        {!isIelts?assignment.category==='report'?'Research / Report':'Research':'IELTS Task'}{" "}
                    · {projectCatalog[assignment.profiles[String(task)]]?.label}
                </span>
                <strong>{submission.wordCounts[String(task)]} words</strong>
            </div>
            {editable && grade && !points && (
                <label className="writing-profile-select">
                    Assessment profile
                    <select
                        aria-label="Assessment profile"
                        value={grade.profile}
                        disabled={points}
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
                        {points?'Enter points for the saved criteria. The total is calculated on the tutor-defined scale.':'Checklists support evidence-based assessment; ticks alone are not an official band.'}
                    </small>
                </label>
            )}
            {pendingSentence&&<p className="writing-muted sentence-pending-notice" role="status">Finish or cancel your sentence feedback before saving or switching tasks.</p>}
            {points&&<div className="review-question-heading">
                <span>{isIelts?`Task ${task}`:'Document'} <span className="writing-muted">/ Submitted prompt</span></span>
                <button type="button" aria-expanded={!questionCollapsed} aria-controls="review-question-content" onClick={()=>setQuestionCollapsed(v=>!v)}>{questionCollapsed?<ChevronDown size={16}/>:<ChevronUp size={16}/>} {questionCollapsed?'Show question':'Minimize question'}</button>
            </div>}
            <div className="writing-review-scroll">
                <div className="writing-paper-grid">
                    <section className="writing-paper-title" hidden={points&&questionCollapsed}>

                        <div id="review-question-content" hidden={points&&questionCollapsed}>

                        <p>{assignment.prompts[String(task)]}</p>
                        {assignment.promptFiles?.[String(task)] && <PromptDocument focused file={assignment.promptFiles[String(task)]}/>}
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
                    </section>

                    {points?<AnnotatedSubmission key={`${submission.id}:${task}`} text={submission.answers?.[String(task)]||''} annotations={grade?.annotations||[]} editable={editable} onPendingChange={setPendingSentence} categories={kinds} onChange={annotations=>changeGrade({annotations})}/>:<>
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
                                {(editable || published) && <button
                                    className="writing-comment-trigger"
                                    title={`Paragraph feedback ${index + 1}`}
                                    aria-label={`Open checklist for paragraph ${index + 1}`}
                                    aria-expanded={active===index}
                                    onClick={() =>
                                        setActive(
                                            active === index ? null : index,
                                        )
                                    }
                                >
                                    <MessageSquarePlus size={16} />
                                    Checklist
                                </button>}
                            </div>

                        </div>
                    ))}
                    </>}
                    {!points && (editable || published) && selectedProfile?.checks.whole && (
                        <div className="writing-paragraph-row">
                            <div className="writing-whole-note">
                                <Check size={18} />
                                {hasBands?'Review the whole paper against the task before summarizing bands.':'Review requirements and evidence before publishing feedback.'}
                            </div>
                            <button className="writing-whole-checklist" onClick={()=>setActive(-1)}><ClipboardCheck size={16}/>Whole-paper checklist</button>
                        </div>
                    )}
                </div>
            </div>
            {(editable || published) && active !== null && card(active)}
            {band && (
                <div
                    className={points?"score-review-container":"writing-band-backdrop"}
                    onClick={points?undefined:() => setBand(false)}
                >
                    <aside
                        ref={bandRef}
                        className={points?"score-review-content":"writing-band-panel"}
                        role={points?"region":"dialog"}
                        aria-modal={points?undefined:true}
                        aria-label={points?'Finish score':hasBands?'IELTS band summary':'Assessment summary'}
                        onClick={(e) => e.stopPropagation()}
                    >
                        <header>
                            <div>
                                <span className="writing-eyebrow">
                                    ASSESSMENT SUMMARY
                                </span>
                                <h2>{points?'Review before publishing':hasBands?'4 IELTS criteria':'Feedback & conclusion'}</h2>
                            </div>
                            <button
                                aria-label={points?'Back to marking':hasBands?'Close band summary':'Close assessment summary'}
                                onClick={() => setBand(false)}
                            >
                                {points?"Back to marking":<X size={20} />}
                            </button>
                        </header>
                        {published&&<button disabled={busy} onClick={()=>onDocument(true)}>Export LaTeX document</button>}
                        {points&&<section className="finish-score-review">
                            <p>Review all criterion scores, corrections and comments before publishing.</p><label className="review-awarded-total">Awarded total score {editable?<input aria-label="Final awarded total" type="number" min={0} max={assignment.scoring?.total} step={0.01} value={value.awardedTotal??progress.score} onChange={e=>onChange({...value,awardedTotal:e.target.value===''?null:Number(e.target.value)})}/>:value.awardedTotal??progress.score} / {assignment.scoring?.total}<small>Calculated from criteria: {progress.score}. The assignment scale stays unchanged.</small>{editable&&value.awardedTotal!=null&&<button onClick={()=>onChange({...value,awardedTotal:null})}>Use calculated score</button>}</label>
                            {assignment.tasks.map(number=><section key={number}>
                                <h3>{isIelts?`Task ${number}`:'Document'}</h3>
                                <PointAssessment assignment={assignment} review={value} task={number} editable={false} onScore={scorePoint}/>
                                <h4>Corrections & comments</h4>
                                {(value.tasks[String(number)]?.annotations||[]).map(note=><article className="finish-score-comment" key={note.id}>
                                    <blockquote>{note.quote}</blockquote>
                                    {note.replacement&&<p><strong>Suggested correction:</strong> {note.replacement}</p>}
                                    {note.comment&&<p>{note.comment}</p>}
                                    <small>{note.kind}{note.resolved?' - Resolved':''}</small>
                                </article>)}
                                {!value.tasks[String(number)]?.annotations?.length&&<p>No sentence comments.</p>}
                            </section>)}
                            {pointProgress(assignment,value).graded<pointProgress(assignment,value).count&&<p role="status">Score every criterion in Checklist score before publishing.</p>}
                        </section>}
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
                                    {!points&&<button
                                        disabled={busy || !dirty || pendingSentence}
                                        onClick={() => void onSave()}
                                    >
                                        Save draft
                                    </button>}
                                    <button
                                        className="primary"
                                        disabled={
                                            busy || pendingSentence ||
                                            points && pointProgress(assignment,value).graded < pointProgress(assignment,value).count ||
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
