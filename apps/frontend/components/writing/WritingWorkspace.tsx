"use client";
import { useEffect, useRef, useState } from "react";
import {
    ArrowRight,
    BookOpen,
    Check,
    ClipboardCheck,
    Clock3,
    FileText,
    GraduationCap,
    Plus,
    RefreshCw,
    Send,
    UsersRound,
} from "lucide-react";
import { type Project } from "../../lib/api";
import { useWritingApi } from "../../lib/writing/context";
import { errorText, type Session } from "../../lib/parity";
import {
    blankGrade,
    catalog,
    countWords,
    usesBands,
    type Question,
    type Assignment,
    type Draft,
    type Overview,
    type Review,
    type SubmissionView,
} from "../../lib/writing/types";
import AssignmentForm, { CommutingChart } from "./AssignmentForm";
import ReviewPaper from "./ReviewPaper";
import DocumentView from "./DocumentView";
import PromptDocument from './PromptDocument';
import ProjectRubrics from './ProjectRubrics';
import Courses from './Courses';
import WritingAnswer from './WritingAnswer';
import QuestionLibrary from './QuestionLibrary';

type Screen =
    | "practice-bank"
    | "courses"
    | "library"
    | "overview"
    | "assignments"
    | "exams"
    | "create"
    | "practice"
    | "check"
    | "submissions"
    | "results"
    | "grading"
    | "rubric";
const screenTitles: Record<Screen, string> = {
    "practice-bank": "Practice exercises",
    courses: "Courses & groups",
    library: "Question library",
    overview: "Task Writing",
    assignments: "Assigned exercises",
    exams: "Examinations",
    create: "Prepare an exercise",
    practice: "Writing practice",
    check: "Check and submit",
    submissions: "Submissions",
    results: "Results & feedback",
    grading: "Read & assess",
    rubric: "Assessment criteria",
};
const date = (value?: string | null) =>
    value ? new Date(value).toLocaleString("en-GB") : "No deadline";

export default function WritingWorkspace({
    session,
    language,
    beforeLeaveRef,
    onLocationChange,
    onOpenProject,
}: {
    session: Session;
    language: string;
    beforeLeaveRef: React.MutableRefObject<(() => Promise<boolean>) | null>;
    onLocationChange?: (location: string) => void;
    onOpenProject: (p: Project) => Promise<void>;
}) {
    const { json, assetUrl, project: writingProject } = useWritingApi();
    const [template,setTemplate]=useState<Question|undefined>();
    const [screen, setScreen] = useState<Screen>("overview"),
        [overview, setOverview] = useState<Overview>({
            classes: [],
            assignments: [],
            submissions: [],
            role: "",
            permission: "viewer",
        });
    const tutor = overview.role === "tutor";
    const readonly = !overview.role || overview.permission === "viewer";
    const roleRef = useRef<Session['role']>(null);
    const [recovery, setRecovery] = useState("");
    const [navigatingTo,setNavigatingTo]=useState<Screen|null>(null);
    const navigationSequence=useRef(0);
    const [inspect, setInspect] = useState<Assignment | null>(null);
    const [listKind,setListKind]=useState<'assignment'|'exam'>('assignment');
    const [practiceOnly,setPracticeOnly]=useState(false);
    const [examGate,setExamGate]=useState<Assignment|null>(null),[examEntryPassword,setExamEntryPassword]=useState('');
    const [passwordTarget,setPasswordTarget]=useState<Assignment|null>(null),[newExamPassword,setNewExamPassword]=useState('');
    const [loading, setLoading] = useState(true),
        [busy, setBusy] = useState(false),
        [error, setError] = useState(""),
        [notice, setNotice] = useState("Data is stored on the server");
    const [editing, setEditing] = useState<Assignment | undefined>(),
        [active, setActive] = useState<Assignment | null>(null),
        [task, setTask] = useState(1);
    const [draft, setDraft] = useState<Draft | null>(null),
        [dirty, setDirty] = useState(false),
        [saving, setSaving] = useState(false),
        [time, setTime] = useState(Date.now());
    const draftRef = useRef<Draft | null>(null),
        activeRef = useRef<Assignment | null>(null),
        revision = useRef(0),
        edit = useRef(0),
        saved = useRef(0),
        queue = useRef<Promise<boolean>>(Promise.resolve(true)),
        failed = useRef(-1),
        clockOffset = useRef(0);
    const submitRequest = useRef<{ assignment: string; id: string } | null>(
        null,
    );
    const [view, setView] = useState<SubmissionView | null>(null),
        [review, setReview] = useState<Review | null>(null),
        [reviewDirty, setReviewDirty] = useState(false);
    const reviewRef = useRef<Review | null>(null),
        reviewRevision = useRef(0),
        reviewEdits = useRef(0),
        reviewSaved = useRef(0),
        reviewQueue = useRef<Promise<boolean>>(Promise.resolve(true));
    const [filterAssignment, setFilterAssignment] = useState("");
    const reloadSequence = useRef(0);
    const reloading = useRef(false);
    draftRef.current = draft;
    activeRef.current = active;
    reviewRef.current = review;
    async function reload(force = false) {
        if (reloading.current && !force) return;
        reloading.current = true;
        const sequence = ++reloadSequence.current;
        try {
            const data = await json<Overview>("/writing/overview");
            if (sequence !== reloadSequence.current) return;
            if ((roleRef.current !== data.role || data.permission === 'viewer') &&
                (edit.current !== saved.current || reviewEdits.current !== reviewSaved.current)) {
                setRecovery(JSON.stringify({answers: draftRef.current?.answers, latexSources: draftRef.current?.latexSources,
                    review: reviewEdits.current !== reviewSaved.current ? reviewRef.current : undefined}, null, 2));
                edit.current = saved.current; reviewEdits.current = reviewSaved.current;
                setDirty(false); setReviewDirty(false);
            }
            if (roleRef.current !== data.role) {
                setScreen("overview"); setView(null); setReview(null); setDraft(null); setActive(null);
                setDirty(false); setReviewDirty(false);
                edit.current = saved.current; reviewEdits.current = reviewSaved.current;
                roleRef.current = data.role as Session['role'];
                setNotice("Access confirmed. Saved work remains on the server.");
            }
            setOverview(data);
        } catch (error) {
            if (sequence !== reloadSequence.current) return;
            if ([401, 403, 404].includes((error as {status?: number}).status || 0)) {
                if (edit.current !== saved.current || reviewEdits.current !== reviewSaved.current)
                    setRecovery(JSON.stringify({answers: draftRef.current?.answers, latexSources: draftRef.current?.latexSources,
                        review: reviewEdits.current !== reviewSaved.current ? reviewRef.current : undefined}, null, 2));
                edit.current = saved.current; reviewEdits.current = reviewSaved.current;
                setDirty(false); setReviewDirty(false);
                setOverview({classes: [], assignments: [], submissions: [], role: "student", permission: "viewer"});
                setView(null); setReview(null); setActive(null); setDraft(null); setScreen("overview");
                setError(errorText(error));
            }
            throw error;
        } finally {
            if (sequence === reloadSequence.current) reloading.current = false;
        }
    }
    useEffect(() => {
        void reload()
            .catch((e) => setError(errorText(e)))
            .finally(() => setLoading(false));
        const timer = setInterval(() => void reload().catch(() => {}), 5000);
        return () => { clearInterval(timer); ++reloadSequence.current; reloading.current = false; };
    }, []);
    useEffect(() => {
        const update = () => void reload(true).catch((e) => setError(errorText(e)));
        window.addEventListener("latex-sharing-updated", update);
        return () => window.removeEventListener("latex-sharing-updated", update);
    }, []);
    useEffect(() => {
        onLocationChange?.(`Task Writing · ${screenTitles[screen]}${screen === 'practice' || screen === 'grading' ? ` · Task ${task}` : ''}`);
    }, [screen, task, onLocationChange]);
    useEffect(() => {
        const timer = setInterval(() => setTime(Date.now()), 1000);
        return () => clearInterval(timer);
    }, []);
    useEffect(() => {
        if (!view || tutor) return;
        const id = view.submission.id;
        const timer = setInterval(
            () =>
                void json<SubmissionView>("/writing/submissions/" + id)
                    .then((data) => {
                        setView((current) =>
                            current?.submission.id === id ? data : current,
                        );
                        setReview(data.review);
                    })
                    .catch(() => {}),
            5000,
        );
        return () => clearInterval(timer);
    }, [view?.submission.id, tutor]);
    useEffect(() => {
        const unload = (event: BeforeUnloadEvent) => {
            if (dirty || reviewDirty) {
                event.preventDefault();
                event.returnValue = "";
            }
        };
        window.addEventListener("beforeunload", unload);
        return () => window.removeEventListener("beforeunload", unload);
    }, [dirty, reviewDirty]);

    async function flush(): Promise<boolean> {
        let attempted = -1;
        const operation = queue.current
            .catch(() => false)
            .then(async () => {
                if (
                    !activeRef.current ||
                    !draftRef.current ||
                    edit.current === saved.current
                )
                    return true;
                const id = activeRef.current.id,
                    body = structuredClone(draftRef.current);
                const ticket = edit.current;
                attempted = ticket;
                setSaving(true);
                try {
                    const result = await json<Draft>(
                        `/writing/assignments/${id}/draft`,
                        {
                            method: "PUT",
                            body: JSON.stringify({
                                revision: revision.current,
                                answers: body.answers,
                                latexSources: body.latexSources,
                                blocks: body.blocks||{},
                            }),
                        },
                    );
                    revision.current = result.revision;
                    saved.current = ticket;
                    failed.current = -1;
                    if (activeRef.current?.id === id)
                        setDraft((current) =>
                            current
                                ? { ...current, revision: result.revision }
                                : current,
                        );
                    setDirty(edit.current !== ticket);
                    setNotice("Writing saved on server");
                    return true;
                } finally {
                    setSaving(false);
                }
            });
        queue.current = operation;
        return operation.catch((e) => {
            failed.current = attempted;
            setError(errorText(e));
            return false;
        });
    }
    useEffect(() => {
        if (!dirty || failed.current === edit.current) return;
        const timer = setTimeout(() => void flush(), 800);
        return () => clearTimeout(timer);
    }, [dirty, draft?.answers, draft?.latexSources]);
    function changeDraft(change: Partial<Draft>) {
        if (readonly) return;
        if (!draftRef.current) return;
        edit.current++;
        submitRequest.current = null;
        const next = { ...draftRef.current, ...change };
        draftRef.current = next;
        setDraft(next);
        setDirty(true);
    }
    async function execute(operation: () => Promise<void>) {
        setBusy(true);
        setError("");
        try {
            await operation();
        } catch (e) {
            setError(errorText(e));
        } finally {
            setBusy(false);
        }
    }
    async function saveReview(): Promise<boolean> {
        const operation = reviewQueue.current
            .catch(() => false)
            .then(async () => {
                if (
                    !view ||
                    !reviewRef.current ||
                    reviewEdits.current === reviewSaved.current
                )
                    return true;
                const body = structuredClone(reviewRef.current),
                    ticket = reviewEdits.current;
                const data = await json<Review>(
                    `/writing/submissions/${view.submission.id}/review`,
                    {
                        method: "PUT",
                        body: JSON.stringify({
                            revision: reviewRevision.current,
                            tasks: body.tasks,
                            summary: body.summary,
                            recommendations: body.recommendations,
                        }),
                    },
                );
                reviewRevision.current = data.revision;
                reviewSaved.current = ticket;
                setReview((current) =>
                    current ? { ...current, revision: data.revision } : current,
                );
                setReviewDirty(reviewEdits.current !== ticket);
                setNotice("Assessment draft saved; students cannot see it yet.");
                return true;
            });
        reviewQueue.current = operation;
        return operation.catch((e) => {
            setError(errorText(e));
            return false;
        });
    }
    async function beforeLeave() {
        return (await flush()) && (!reviewDirty || (await saveReview()));
    }
    async function navigate(next: Screen) {
        if (readonly && next === 'create') return;
        const ticket=++navigationSequence.current;
        setNavigatingTo(next);
        try{
            if (!(await beforeLeave()) || ticket!==navigationSequence.current) return;
            setError("");
            if (next === "create") {setEditing(undefined);setTemplate(undefined);}
            setScreen(next);
            // Render cached lists immediately. Reuse an in-flight refresh instead
            // of making every click wait for a complete MongoDB overview.
            if(['assignments','exams','submissions','results'].includes(next))
                void reload().catch(e=>setError(errorText(e)));
        }finally{if(ticket===navigationSequence.current)setNavigatingTo(null);}
    }
    async function openAssignment(assignment: Assignment) {
        if (!(await beforeLeave())) return;
        await execute(async () => {
            if (tutor) {
                if (assignment.published) {
                    setListKind(assignment.kind==='exam'?'exam':'assignment');
                    setFilterAssignment(assignment.id);
                    setScreen("submissions");
                } else {
                    setEditing(assignment);
                    setScreen("create");
                }
                return;
            }
            if(assignment.kind==='exam' && !assignment.examEntered){setExamEntryPassword('');setError('');setExamGate(assignment);return;}
            setListKind(assignment.kind==='exam'?'exam':'assignment');
            assignment=await json<Assignment>(`/writing/assignments/${assignment.id}`);
            const data = await json<Draft>(
                `/writing/assignments/${assignment.id}/draft`,
            );
            setActive(assignment);
            activeRef.current = assignment;
            setDraft(data);
            draftRef.current = data;
            revision.current = data.revision;
            edit.current = 0;
            saved.current = 0;
            failed.current = -1;
            setDirty(false);
            setTask(assignment.tasks[0]);
            clockOffset.current =
                new Date(data.serverNow).getTime() - Date.now();
            setTime(Date.now());
            setScreen("practice");
        });
    }
    async function openSubmission(id: string) {
        if (!(await beforeLeave())) return;
        await execute(async () => {
            const data = await json<SubmissionView>(
                "/writing/submissions/" + id,
            );
            setView(data);
            const initial = data.review || {
                revision: 0,
                tasks: {},
                summary: "",
                recommendations: "",
            };
            const normalized = tutor
                ? {
                      ...initial,
                      tasks: Object.fromEntries(
                          data.submission.assignment.tasks.map((n) => [
                              String(n),
                              initial.tasks[String(n)] ||
                                  blankGrade(n, data.submission.assignment),
                          ]),
                      ),
                  }
                : data.review;
            setReview(normalized);
            reviewRef.current = normalized;
            reviewRevision.current = initial.revision;
            reviewEdits.current = 0;
            reviewSaved.current = 0;
            setReviewDirty(false);
            setScreen("grading");
        });
    }
    function changeReview(data: Review) {
        if (readonly) return;
        reviewEdits.current++;
        reviewRef.current = data;
        setReview(data);
        setReviewDirty(true);
    }
    async function publish(reason: string) {
        await execute(async () => {
            if (!(await saveReview()) || !view) return;
            if (reviewEdits.current !== reviewSaved.current)
                throw Error(
                    "The assessment changed. Save it before publishing.",
                );
            const result = await json<Review>(
                `/writing/submissions/${view.submission.id}/publish`,
                {
                    method: "POST",
                    body: JSON.stringify({
                        revision: reviewRevision.current,
                        reason,
                    }),
                },
            );
            setView({ ...view, published: result });
            setNotice("Results published to the student.");
            await reload();
        });
    }
    async function submit() {
        await execute(async () => {
            if (!active || !(await flush())) return;
            const request =
                submitRequest.current?.assignment === active.id
                    ? submitRequest.current
                    : { assignment: active.id, id: crypto.randomUUID() };
            submitRequest.current = request;
            const response = await json<{ id: string }>(
                `/writing/assignments/${active.id}/submit`,
                {
                    method: "POST",
                    body: JSON.stringify({
                        revision: revision.current,
                        requestId: request.id,
                    }),
                },
            );
            submitRequest.current = null;
            const latest = await json<Draft>(
                `/writing/assignments/${active.id}/draft`,
            );
            setDraft(latest);
            draftRef.current = latest;
            revision.current = latest.revision;
            await reload();
            const data = await json<SubmissionView>(
                "/writing/submissions/" + response.id,
            );
            setView(data);
            setReview(data.review);
            setScreen("grading");
            setNotice("Submitted. An immutable snapshot has been saved separately from your draft.");
        });
    }
    async function openGenerated(report: boolean) {
        if (!view) return;
        await execute(async () => {
            if (!(await beforeLeave())) return;
            const response = await json<{ project?: Project; pdf?:string }>(
                "/writing/latex",
                {
                    method: "POST",
                    body: JSON.stringify({
                        submissionId: view.submission.id,
                        report,
                        createProject: true,
                        createDocument: false,
                    }),
                },
            );
            if(response.pdf){
                const bytes=Uint8Array.from(atob(response.pdf),c=>c.charCodeAt(0));
                const link=document.createElement('a');const url=URL.createObjectURL(new Blob([bytes],{type:'application/pdf'}));
                link.href=url;link.download=report?'ielts-feedback.pdf':'ielts-submission.pdf';link.click();setTimeout(()=>URL.revokeObjectURL(url),1000);
                setNotice('PDF downloaded. Your writing remains in its original workspace.');
            }else if(response.project) await onOpenProject(response.project);
        });
    }
    const remaining = draft?.deadline
        ? Math.max(
              0,
              Math.ceil(
                  (new Date(draft.deadline).getTime() -
                      time -
                      clockOffset.current) /
                      1000,
              ),
          )
        : null;
    const filtered = overview.submissions.filter(
        (s) =>
            (s.assignment.kind||'assignment')===listKind &&
            (listKind==='exam'||(s.assignment.purpose==='practice')===practiceOnly) &&
            (screen !== "results" || s.result) &&
            (!filterAssignment || s.assignmentId === filterAssignment),
    );
    const assignmentKind=tutor?listKind:screen==='exams'?'exam':'assignment';
    const listedAssignments=overview.assignments.filter(a=>(a.kind||'assignment')===assignmentKind && (assignmentKind==='exam'||(a.purpose==='practice')===(tutor?practiceOnly:screen==='practice-bank')));
    const kindTabs=<div className="writing-kind-tabs" role="tablist" aria-label="Exercise type"><button role="tab" aria-selected={listKind==='assignment'&&!practiceOnly} onClick={()=>{setListKind('assignment');setPracticeOnly(false);setFilterAssignment('');setInspect(null);}}>Assigned exercises</button><button role="tab" aria-selected={listKind==='assignment'&&practiceOnly} onClick={()=>{setListKind('assignment');setPracticeOnly(true);setFilterAssignment('');setInspect(null);}}>Practice</button><button role="tab" aria-selected={listKind==='exam'} onClick={()=>{setListKind('exam');setFilterAssignment('');setInspect(null);}}>Examinations</button></div>;
    const examExpired=active?.kind==='exam' && (draft?.readOnly || remaining!==null && remaining<=0);
    const navigation = tutor
        ? ([
              ["overview", "Overview", GraduationCap],
              ["courses", "Courses & groups", UsersRound],
              ["library", "Question library", BookOpen],
              ["create", "Assign exercise", Plus],
              ["assignments", "Created exercises", BookOpen],
              ["submissions", "Student submissions", ClipboardCheck],
              ["rubric", "Assessment criteria", Check],
          ] as const)
        : ([
              ["overview", "Overview", GraduationCap],
              ["courses", "Courses & groups", UsersRound],
              ["practice-bank", "Practice exercises", FileText],
              ["practice", "My writing", FileText],
              ["assignments", "Assigned exercises", BookOpen],
              ["exams", "Examinations", Clock3],
              ["submissions", "Submission history", Send],
              ["results", "Results & feedback", ClipboardCheck],
          ] as const);
    useEffect(() => {
        beforeLeaveRef.current = beforeLeave;
        return () => { beforeLeaveRef.current = null; };
    });
    return (
        <section
            className={`writing-shell writing-module${screen==='grading'?' writing-grading-mode':''}`}
            data-writing-ready={!loading}
        >
            {examGate&&<div className="writing-exam-backdrop"><form className="writing-exam-dialog" role="dialog" aria-modal="true" aria-label="Enter examination" onSubmit={e=>{e.preventDefault();void execute(async()=>{
                const result=await json<{assignment:Assignment}>(`/writing/assignments/${examGate.id}/enter`,{method:'POST',body:JSON.stringify({password:examEntryPassword})});
                setExamEntryPassword('');setExamGate(null);await openAssignment(result.assignment);
            });}}><h2>{examGate.title}</h2><p>Opens: {date(examGate.opensAt)}<br/>Closes: {date(examGate.closesAt)}<br/>Duration: {examGate.durationMinutes} minutes</p><label>Entry password<input aria-label="Entry password" autoFocus type="password" autoComplete="off" required maxLength={128} value={examEntryPassword} onChange={e=>setExamEntryPassword(e.target.value)}/></label>{error&&<p role="alert" className="writing-error">{error}</p>}<footer><button type="button" disabled={busy} onClick={()=>{setExamGate(null);setExamEntryPassword('');}}>Cancel</button><button className="primary" disabled={busy} type="submit">Start examination</button></footer></form></div>}
            {passwordTarget&&<div className="writing-exam-backdrop"><form className="writing-exam-dialog" role="dialog" aria-modal="true" aria-label="Change exam password" onSubmit={e=>{e.preventDefault();void execute(async()=>{
                await json(`/writing/assignments/${passwordTarget.id}/exam-password`,{method:'PATCH',body:JSON.stringify({password:newExamPassword,revision:passwordTarget.revision})});
                setPasswordTarget(null);setNewExamPassword('');setNotice('Password changed. Current students keep their existing timers.');await reload(true);
            });}}><h2>Change password</h2><p>{passwordTarget.title}</p><label>New password<input aria-label="New exam password" type="password" autoComplete="new-password" minLength={6} maxLength={128} required value={newExamPassword} onChange={e=>setNewExamPassword(e.target.value)}/></label>{error&&<p role="alert" className="writing-error">{error}</p>}<footer><button type="button" disabled={busy} onClick={()=>{setPasswordTarget(null);setNewExamPassword('');}}>Cancel</button><button className="primary" disabled={busy}>Save password</button></footer></form></div>}
            <div className="writing-layout">
                <aside className="writing-sidebar">
                    <span className="writing-eyebrow">
                        {loading || !overview.role ? "CHECKING ACCESS" : `WORKSPACE ${tutor ? "TUTOR" : "STUDENT"}`}
                    </span>
                    {(loading || !overview.role ? [] : navigation).map(([id, label, Icon]) => (
                        <button
                            key={id}
                            disabled={readonly && ['create','library','rubric'].includes(id)}
                            className={(navigatingTo||screen) === id ? "active" : ""}
                            aria-busy={navigatingTo===id}
                            onClick={() => {
                                if (id === "submissions")
                                    setFilterAssignment("");
                                void navigate(id);
                            }}
                        >
                            <Icon size={17} />
                            {label}
                        </button>
                    ))}
                    <div className="writing-sidebar-note">
                        <BookOpen size={24} />
                        <strong>
                            Write with clarity.
                            <br />
                            Give specific feedback.
                        </strong>
                        <p>
                            Task Writing for learning.
                            <br />
                            LaTeX for document publishing.
                        </p>
                    </div>
                </aside>
                <section className="writing-main">
                    <div className="writing-page-heading">
                        <div>
                            <span className="writing-eyebrow">
                                TASK WRITING
                            </span>
                            <h1>{screenTitles[screen]}</h1>
                        </div>
                        <div>
                            {screen === "overview" && !readonly && (
                                <button
                                    className="primary"
                                    onClick={() =>
                                        void navigate(
                                            tutor
                                                ? "create"
                                                : active
                                                  ? "practice"
                                                  : "create",
                                        )
                                    }
                                >
                                    <Plus size={16} />
                                    {tutor
                                        ? "Create exercise"
                                        : "Start a practice exercise"}
                                </button>
                            )}
                            <button
                                title="Refresh Task Writing"
                                disabled={busy}
                                onClick={() => void execute(reload)}
                            >
                                <RefreshCw size={17} />
                            </button>
                        </div>
                    </div>
                    {error && (
                        <div className="writing-error" role="alert">
                            {error}
                        </div>
                    )}
                    {overview.role && <div className="writing-notice">Your role: <strong>{tutor ? "Tutor" : "Student"}</strong> · {readonly ? "Read only — ask the owner for editing access." : "Editing enabled"}. Access is checked on the server.</div>}
                    {recovery && <div className="writing-notice">Your role or access changed. Keep a copy of unsaved work: <button onClick={() => {
                        const url = URL.createObjectURL(new Blob([recovery], {type: 'application/json'}));
                        const link = document.createElement('a'); link.href = url; link.download = 'ielts-unsaved-recovery.json'; link.click();
                        setTimeout(() => URL.revokeObjectURL(url), 1000);
                    }}>Download unsaved work</button></div>}
                    {writingProject && !readonly && !!((overview.legacy?.classes || 0) + (overview.legacy?.assignments || 0)) &&
                        <div className="writing-notice">You have {overview.legacy?.classes} courses and {overview.legacy?.assignments} existing exercises outside this project.
                            <button onClick={() => {
                                if (window.confirm('Import your existing courses and exercises into this project? Project sharing will apply; invite enrolled students to retain access.'))
                                    void execute(async () => { if (!(await beforeLeave())) return; await json('/writing/import-legacy', {method: 'POST'}); await reload(); });
                            }} disabled={busy}>Import existing writing data</button>
                        </div>}
                    {loading ? (
                        <div className="writing-empty">
                            Loading Task Writing…
                        </div>
                    ) : (
                        <>
                            {screen === "courses" && <Courses courses={overview.classes} tutor={tutor} editable={!readonly} onRefresh={()=>reload(true)}/>}
                            {screen === "library" && tutor && <QuestionLibrary userId={session.id||''} onUse={q=>{setTemplate(q);setEditing(undefined);setScreen("create");}}/>}
                            {screen === "overview" && (
                                <>
                                    <section className="writing-welcome">
                                        <div>
                                            <span className="writing-eyebrow">
                                                {tutor
                                                    ? "SUPPORT EVERY WRITER"
                                                    : "FROM IDEAS TO WRITING"}
                                            </span>
                                            <h2>
                                                {tutor
                                                    ? "Every paragraph deserves useful feedback."
                                                    : "A place to write and improve."}
                                            </h2>
                                            <p>
                                                {tutor
                                                    ? "Assign exercises, read submitted work, assess each paragraph and publish feedback."
                                                    : "Practise tasks, reports and research papers with feedback from your tutor."}
                                            </p>
                                        </div>
                                        <div className="writing-welcome-art">
                                            <FileText size={68} />
                                            <span>
                                                Task Writing
                                                <br />
                                                <strong>× LaTeX</strong>
                                            </span>
                                        </div>
                                    </section>
                                    <div className="writing-stats">
                                        {[
                                            [
                                                overview.assignments.length,
                                                "Workspace questions",
                                            ],
                                            [
                                                overview.assignments.filter(
                                                    (a) => a.published,
                                                ).length,
                                                "Exercises",
                                            ],
                                            [
                                                overview.submissions.filter(
                                                    (s) => !s.result,
                                                ).length,
                                                tutor
                                                    ? "Awaiting assessment"
                                                    : "Awaiting results",
                                            ],
                                            [
                                                overview.submissions.filter(
                                                    (s) => s.result,
                                                ).length,
                                                "Published",
                                            ],
                                        ].map(([value, label]) => (
                                            <section
                                                className="writing-card"
                                                key={label}
                                            >
                                                <strong>{value}</strong>
                                                <span>{label}</span>
                                            </section>
                                        ))}
                                    </div>
                                    <section className="writing-card writing-start">
                                        <div>
                                            <h2>
                                                {tutor
                                                    ? "Assign exercises"
                                                    : "Workspace exercises"}
                                            </h2>
                                            <p>
                                                {tutor
                                                    ? "Create a question directly or import an existing LaTeX source."
                                                    : "Open an assigned exercise or start personal practice."}
                                            </p>
                                        </div>
                                        <button
                                            onClick={() =>
                                                void navigate(tutor ? "create" : "assignments")
                                            }
                                        >
                                            {tutor
                                                ? "Assign exercise"
                                                : "Assigned exercises"}
                                            <ArrowRight size={16} />
                                        </button>
                                    </section>
                                </>
                            )}
                            {screen === "create" && !readonly && (
                                <AssignmentForm
                                    key={editing?.id || "new"}
                                    practice={!tutor}
                                    initial={editing}
                                    template={template}
                                    onCancel={() =>
                                        void navigate("assignments")
                                    }
                                    onDone={(a) => {
                                        void reload();
                                        if (tutor) {
                                            setListKind(a.kind==='exam'?'exam':'assignment');setPracticeOnly(a.purpose==='practice');setTemplate(undefined);
                                            setNotice(
                                                a.published
                                                    ? "Exercise assigned to students."
                                                    : "Exercise draft saved.",
                                            );
                                            setScreen("assignments");
                                        } else void openAssignment(a);
                                    }}
                                />
                            )}
                            {(screen === "assignments" || screen==='exams' || screen==='practice-bank') && (
                                <>
                                    {tutor&&kindTabs}
                                    {tutor && inspect && <section className="writing-card">
                                        <h2>{inspect.title}</h2>
                                        <button onClick={()=>setInspect(null)}>Close exercise details</button>
                                        {inspect.tasks.map(number=><div key={number}>
                                            <h3>{!inspect.category||inspect.category==='ielts'?`Task ${number}`:inspect.category==='report'?'Report':'Research'}</h3>
                                            {inspect.promptFiles?.[String(number)] ? <PromptDocument file={inspect.promptFiles[String(number)]} language={language}/> : <p>{inspect.prompts[String(number)] || 'No imported source is attached.'}</p>}
                                        </div>)}
                                        <h3>Student progress</h3>
                                        <table><thead><tr><th>Student</th><th>Status</th><th>Submissions</th></tr></thead><tbody>
                                            {(inspect.classId ? overview.classes.find(c=>c.id===inspect.classId)?.students||[] : overview.students || []).map(person=>{
                                                const submissions=overview.submissions.filter(s=>s.assignmentId===inspect.id && s.studentId===person.id);
                                                return <tr key={person.id}><td>{person.name}</td><td>{submissions.length?'Submitted':'Not submitted'}</td><td>{submissions.length}</td></tr>;
                                            })}
                                        </tbody></table>
                                    </section>}
                                    <div className="writing-list-toolbar">
                                        <p>
                                            {tutor
                                                ? "Draft and assigned exercises in this workspace."
                                                : screen==='exams'?"Examinations have a password and opening/closing times.":"Assigned exercises and personal practice."}
                                        </p>
                                        {(tutor||screen!=='exams')&&<button
                                            onClick={() =>
                                                void navigate("create")
                                            }
                                        >
                                            <Plus size={16} />
                                            {tutor
                                                ? "Create exercise"
                                                : "Personal practice"}
                                        </button>}
                                    </div>
                                    {listedAssignments.length ? (
                                        <div className="writing-assignment-list">
                                            {listedAssignments.map((a) => (
                                                <article
                                                    className="writing-card"
                                                    key={a.id}
                                                >
                                                    <div>
                                                        <span className="writing-tag">
                                                            {a.published
                                                                ? "Assigned"
                                                                : "Draft question"}
                                                        </span>
                                                        <h2>{a.title}</h2>
                                                        <p>
                                                            {a.category && a.category!=='ielts'?a.category==='report'?'Report':'Research':a.variant ===
                                                            "academic"
                                                                ? "Academic"
                                                                : "General Training"}{" "}
                                                            ·{" "}
                                                            {a.tasks
                                                                .map(
                                                                    (t) =>
                                                                        (!a.category||a.category==='ielts'?"Task " + t:"Document"),
                                                                )
                                                                .join(
                                                                    " + ",
                                                                )}{" "}
                                                            ·{" "}
                                                            {a.practiceOwner
                                                                ? "Personal practice"
                                                                : a.tutorName}
                                                        </p>
                                                        <small>
                                                            {a.kind==='exam'?`Opens: ${date(a.opensAt)} · Closes: ${date(a.closesAt)} · ${a.durationMinutes} minutes`:`Due: ${date(a.dueAt)}`}
                                                        </small>
                                                    </div>
                                                    <button
                                                        className="primary"
                                                        onClick={() =>
                                                            void openAssignment(
                                                                a,
                                                            )
                                                        }
                                                    >
                                                        {tutor
                                                            ? a.published
                                                                ? "View submissions"
                                                                : "Edit draft"
                                                            : a.kind==='exam'?'Enter examination':"Open writing"}
                                                        <ArrowRight size={15} />
                                                    </button>
                                                    {tutor && <div>
                                                        <button onClick={()=>{setInspect(a);}}>View question & students</button>
                                                        {!readonly && <>
                                                            {a.kind==='exam'&&<button onClick={()=>{setPasswordTarget(a);setNewExamPassword('');setError('');}}>Change password</button>}
                                                            <button onClick={()=>{setEditing(a);setScreen('create');}}>Edit exercise</button>
                                                            <button onClick={async()=>{
                                                                if(!window.confirm('Remove this exercise? Existing submissions and results will be preserved.'))return;
                                                                try{await json(`/writing/assignments/${a.id}?revision=${a.revision}`,{method:'DELETE'});setInspect(null);await reload();}catch(e){setError(errorText(e));}
                                                            }}>Delete exercise</button>
                                                        </>}
                                                    </div>}
                                                </article>
                                            ))}
                                        </div>
                                    ) : (
                                        <div className="writing-empty">
                                            No exercises yet.{" "}
                                            {tutor
                                                ? "Create an exercise directly or reuse a question from the library."
                                                : "Open an assigned exercise or create personal practice."}
                                        </div>
                                    )}
                                </>
                            )}
                            {screen === "practice" &&
                                (!active || !draft ? (
                                    <div className="writing-empty">
                                        <BookOpen size={40} />
                                        <h2>Ready to write?</h2>
                                        <button
                                            className="primary"
                                            onClick={() =>
                                                void navigate("create")
                                            }
                                        >
                                            Start personal practice
                                        </button>
                                        <button
                                            onClick={() =>
                                                void navigate("assignments")
                                            }
                                        >
                                            View assigned exercises
                                        </button>
                                    </div>
                                ) : (
                                    <div className="writing-practice">
                                        <div className="writing-practice-strip">
                                            <div
                                                className="writing-task-tabs"
                                                role="tablist"
                                                aria-label="Active task"
                                            >
                                                {active.tasks.map((n) => (
                                                    <button
                                                        key={n}
                                                        role="tab"
                                                        aria-selected={
                                                            task === n
                                                        }
                                                        onClick={() =>
                                                            setTask(n)
                                                        }
                                                    >
                                                        {!active.category||active.category==='ielts'?`Task ${n}`:active.category==='report'?'Report':'Research'}
                                                    </button>
                                                ))}
                                            </div>
                                            <span>
                                                {active.category && active.category!=='ielts'?active.category==='report'?'Report':'Research':active.variant === "academic"
                                                    ? "Academic"
                                                    : "General Training"}{" "}
                                                ·{" "}
                                                {
                                                    (active.rubricCatalog||catalog)[
                                                        active.profiles[
                                                            String(task)
                                                        ]
                                                    ]?.label
                                                }
                                            </span>
                                            <span className="writing-timer">
                                                <Clock3 size={15} />
                                                {remaining === null
                                                    ? "Untimed"
                                                    : remaining === 0
                                                      ? active.kind==='exam'?"Time is up — saved draft is read only":"Time is up — your work is saved"
                                                      : `${Math.floor(
                                                            remaining / 60,
                                                        )
                                                            .toString()
                                                            .padStart(
                                                                2,
                                                                "0",
                                                            )}:${(remaining % 60).toString().padStart(2, "0")}`}
                                            </span>
                                            <button
                                                disabled={readonly||!!examExpired}
                                                onClick={() =>
                                                    void navigate("check")
                                                }
                                                className="primary"
                                            >
                                                Check and submit
                                            </button>
                                        </div>
                                        <DocumentView
                                            focused
                                            assignmentId={active.id}
                                            sourceOverride={
                                                draft.latexSources[
                                                    String(active.tasks[0])
                                                ]
                                            }
                                            onSourceChange={readonly ? undefined : (value) =>
                                                changeDraft({
                                                    latexSources: {
                                                        ...draftRef.current!
                                                            .latexSources,
                                                        [active.tasks[0]]:
                                                            value,
                                                    },
                                                })
                                            }
                                            onOpenProject={onOpenProject}
                                            beforeAction={flush}
                                            language={language}
                                            sourceSignal={JSON.stringify([
                                                draft.answers,
                                                draft.latexSources,
                                            ])}
                                        >
                                            <div className="writing-practice-panes">
                                                <aside className="writing-question">
                                                    <span className="writing-eyebrow">
                                                        QUESTION · TASK {task}
                                                    </span>
                                                    <h2>{active.title}</h2>
                                                    <div className="writing-prompt-text">
                                                        {
                                                            active.prompts[
                                                                String(task)
                                                            ]
                                                        }
                                                    </div>
                                                    {active.promptFiles?.[String(task)] && <PromptDocument focused file={active.promptFiles[String(task)]} language={language}/>}
                                                    {task === 1 &&
                                                        active.imageId &&
                                                        (active.imageId ===
                                                        "sample-commuting" ? (
                                                            <>
                                                                <CommutingChart />
                                                                <small>
                                                                    Original practice question and data.
                                                                </small>
                                                            </>
                                                        ) : (
                                                            <img
                                                                src={assetUrl(active.imageId)}
                                                                alt="Task 1 visual"
                                                            />
                                                        ))}
                                                    {usesBands(active) && <p className="writing-question-note">
                                                        At least{" "}
                                                        {task === 1 ? 150 : 250}{" "}
                                                        words ·{" "}
                                                        {task === 1
                                                            ? "Suggested: 20 minutes"
                                                            : "Suggested: 40 minutes"}
                                                        <br />
                                                        Word count is a writing aid, not a band conversion.
                                                    </p>}
                                                </aside>
                                                <section className="writing-answer">
                                                    <header>
                                                        <strong>
                                                            Your answer
                                                        </strong>
                                                        <span>
                                                            {saving
                                                                ? "Saving…"
                                                                : dirty
                                                                  ? "Unsaved"
                                                                  : "Saved on server"}
                                                        </span>
                                                    </header>
                                                    <WritingAnswer key={`${active.id}:${task}`} assignment={active} task={task} value={draft.answers[String(task)]||''}
                                                        blocks={draft.blocks?.[String(task)]} readOnly={readonly||!!examExpired}
                                                        onSave={()=>void flush()} onChange={(text,blocks)=>{
                                                            changeDraft({answers:{...draftRef.current!.answers,[task]:text},
                                                                blocks:{...draftRef.current!.blocks,[task]:blocks||[]},
                                                                latexSources:{...draftRef.current!.latexSources,[task]:''}});
                                                        }}/>

                                                    <footer>
                                                        <span
                                                            className={
                                                                countWords(
                                                                    draft
                                                                        .answers[
                                                                        String(
                                                                            task,
                                                                        )
                                                                    ] || "",
                                                                ) <
                                                                (!usesBands(active)?0:task === 1
                                                                    ? 150
                                                                    : 250)
                                                                    ? "writing-word-warning"
                                                                    : ""
                                                            }
                                                        >
                                                            <strong>
                                                                {countWords(
                                                                    draft
                                                                        .answers[
                                                                        String(
                                                                            task,
                                                                        )
                                                                    ] || "",
                                                                )}{" "}
                                                                words
                                                            </strong>{" "}
                                                            {usesBands(active) && ` / ${task===1?150:250}`}
                                                        </span>
                                                        <button
                                                            disabled={
                                                                readonly || !dirty || saving
                                                            }
                                                            onClick={() =>
                                                                void flush()
                                                            }
                                                        >
                                                            Save writing
                                                        </button>
                                                    </footer>
                                                </section>
                                            </div>
                                        </DocumentView>
                                    </div>
                                ))}
                            {screen === "check" && active && draft && (
                                <section className="writing-card writing-check-submit">
                                    <span className="writing-eyebrow">
                                        SUBMISSION SNAPSHOT
                                    </span>
                                    <h2>{active.title}</h2>
                                    <p>
                                        Submissions are saved separately. Editing your draft later does not change the work your tutor assesses.
                                    </p>
                                    {active.tasks.map((n) => (
                                        <article key={n}>
                                            <h3>
                                                {!active.category||active.category==='ielts'?`Task ${n}`:'Document'} ·{" "}
                                                {countWords(
                                                    draft.answers[String(n)] ||
                                                        "",
                                                )}{" "}
                                                words
                                            </h3>
                                            {active.promptFiles?.[String(n)] && <PromptDocument focused file={active.promptFiles[String(n)]} language={language}/>}
                                            {countWords(
                                                draft.answers[String(n)] || "",
                                            ) < (n === 1 ? 150 : 250) && usesBands(active) && (
                                                <p className="writing-word-warning">
                                                    Below the recommended word count;
                                                    this does not determine a band score.
                                                </p>
                                            )}
                                            <p>
                                                {draft.answers[String(n)] ||
                                                    "No answer yet."}
                                            </p>
                                        </article>
                                    ))}
                                    <footer>
                                        <button
                                            onClick={() =>
                                                setScreen("practice")
                                            }
                                        >
                                            Back to writing
                                        </button>
                                        <button
                                            disabled={
                                                readonly || busy || !!examExpired ||
                                                active.tasks.some(
                                                    (n) =>
                                                        !draft.answers[
                                                            String(n)
                                                        ]?.trim(),
                                                )
                                            }
                                            className="primary"
                                            onClick={() => void submit()}
                                        >
                                            <Send size={15} />
                                            {busy
                                                ? "Submitting…"
                                                : "Confirm submission"}
                                        </button>
                                    </footer>
                                </section>
                            )}
                            {(screen === "submissions" ||
                                screen === "results") && (
                                <>
                                    {kindTabs}
                                    <div className="writing-list-toolbar">
                                        <p>
                                            {tutor
                                                ? "Submissions for exercises you have assigned."
                                                : "Your submissions and published feedback."}
                                        </p>
                                        {filterAssignment && (
                                            <button
                                                onClick={() =>
                                                    setFilterAssignment("")
                                                }
                                            >
                                                View all submissions
                                            </button>
                                        )}
                                    </div>
                                    {filtered.length ? (
                                        <div className="writing-table-wrap">
                                            <table className="writing-table">
                                                <thead>
                                                    <tr>
                                                        <th>
                                                            {tutor
                                                                ? "Student"
                                                                : "Exercises"}
                                                        </th>
                                                        <th>Task</th>
                                                        <th>
                                                            Attempt / submitted at
                                                        </th>
                                                        <th>Status</th>
                                                        <th />
                                                    </tr>
                                                </thead>
                                                <tbody>
                                                    {filtered.map((s) => (
                                                        <tr key={s.id}>
                                                            <td>
                                                                <strong>
                                                                    {tutor
                                                                        ? s.studentName
                                                                        : s
                                                                              .assignment
                                                                              .title}
                                                                </strong>
                                                                <small>
                                                                    {tutor
                                                                        ? s
                                                                              .assignment
                                                                              .title
                                                                        : s
                                                                              .assignment
                                                                              .tutorName ||
                                                                          "Personal practice"}
                                                                </small>
                                                            </td>
                                                            <td>
                                                                {s.assignment.tasks
                                                                    .map(
                                                                        (n) =>
                                                                            `Task ${n}: ${s.wordCounts[String(n)]} words`,
                                                                    )
                                                                    .join(
                                                                        " · ",
                                                                    )}
                                                            </td>
                                                            <td>
                                                                Attempt {s.attempt}
                                                                <small>
                                                                    {date(
                                                                        s.submittedAt,
                                                                    )}
                                                                    {s.late
                                                                        ? " · Late"
                                                                        : ""}
                                                                </small>
                                                            </td>
                                                            <td>
                                                                <span className="writing-tag">
                                                                    {s.result
                                                                        ? "Published"
                                                                        : s.tutorId
                                                                          ? "Awaiting assessment"
                                                                          : "Personal practice"}
                                                                </span>
                                                            </td>
                                                            <td>
                                                                <button
                                                                    onClick={() =>
                                                                        void openSubmission(
                                                                            s.id,
                                                                        )
                                                                    }
                                                                >
                                                                    {tutor
                                                                        ? "Open assessment"
                                                                        : "View submissions"}
                                                                </button>
                                                            </td>
                                                        </tr>
                                                    ))}
                                                </tbody>
                                            </table>
                                        </div>
                                    ) : (
                                        <div className="writing-empty">
                                            {screen === "results"
                                                ? "No published results yet."
                                                : "No submissions yet."}
                                        </div>
                                    )}
                                </>
                            )}
                            {screen === "grading" && view && (
                                <DocumentView
                                    focused
                                    key={view.submission.id}
                                    submissionId={view.submission.id}
                                    beforeAction={async () =>
                                        !tutor ||
                                        !reviewDirty ||
                                        (await saveReview())
                                    }
                                    onOpenProject={onOpenProject}
                                    language={language}
                                    sourceSignal={view.submission.id}
                                >
                                    <ReviewPaper
                                        submission={view.submission}
                                        review={review}
                                        onChange={changeReview}
                                        editable={tutor && !readonly}
                                        onSave={saveReview}
                                        onPublish={publish}
                                        busy={busy}
                                        dirty={reviewDirty}
                                        published={view.published}
                                        onDocument={(report) =>
                                            void openGenerated(report)
                                        }
                                    />
                                </DocumentView>
                            )}
                            {screen === "rubric" && (
                                <ProjectRubrics editable={tutor&&!readonly}/>
                            )}
                        </>
                    )}
                </section>
            </div>
            <footer className="statusbar">
                <span>{navigatingTo?'Saving and opening…':notice}</span>
                <span>
                    {dirty
                        ? "Unsaved writing"
                        : reviewDirty
                          ? "Unsaved assessment"
                          : "Stored on server · MongoDB"}
                </span>
            </footer>
        </section>
    );
}
