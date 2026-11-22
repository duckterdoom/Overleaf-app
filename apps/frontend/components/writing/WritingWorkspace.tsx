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
    assessmentCategoryTitle,
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
import WritingListFilters from "./WritingListFilters";
import {emptyWritingFilters,matchesAssignment,matchesSubmission} from "../../lib/writing/list-filters";
import SubmissionTable from "./SubmissionTable";
import ExerciseDetails from './ExerciseDetails';
import WorkspaceNavigation, {type WritingSection,type WorkspaceNavigationActions} from '../workspace/WorkspaceNavigation';
import ProjectRubrics from './ProjectRubrics';
import Courses from './Courses';
import WritingAnswer from './WritingAnswer';
import QuestionVisual from './QuestionVisual';
import QuestionLibrary from './QuestionLibrary';
import WritingGuides from './WritingGuides';
import {GuideDrawer} from './GuideReader';

type Screen =
    | "practice-bank"
    | "courses"
    | "library"
    | "guides"
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
    courses: "Courses",
    library: "Question library",
    guides: "Writing guides",
    overview: "Task Writing",
    assignments: "Assigned exercises",
    exams: "Examinations",
    create: "Prepare an exercise",
    practice: "Writing practice",
    check: "Check and submit",
    submissions: "Submissions",
    results: "Results & feedback",
    grading: "Read & assess",
    rubric: "Assessment categories",
};
const date = (value?: string | null) =>
    value ? new Date(value).toLocaleString("en-GB") : "No deadline";

export default function WritingWorkspace({
    session,
    initialScreen="assignments",
    language,
    beforeLeaveRef,
    onLocationChange,
    onOpenProject,
    shellNavigation,
}: {
    session: Session;
    initialScreen?: WritingSection;
    shellNavigation: WorkspaceNavigationActions;
    language: string;
    beforeLeaveRef: React.MutableRefObject<(() => Promise<boolean>) | null>;
    onLocationChange?: (location: string) => void;
    onOpenProject: (p: Project) => Promise<void>;
}) {
    const { json, assetUrl, project: writingProject } = useWritingApi();
    const [libraryCreate,setLibraryCreate]=useState(initialScreen==='create'&&session.role==='tutor');
    const [template,setTemplate]=useState<Question|undefined>();
    const [screen, setScreen] = useState<Screen>(initialScreen==='overview'?'assignments':initialScreen),
        [overview, setOverview] = useState<Overview>({
            classes: [],
            assignments: [],
            submissions: [],
            role: "",
            permission: "viewer",
        });
    const tutor = (overview.role || session.role) === "tutor";
    const readonly = !overview.role || overview.permission === "viewer";
    const roleRef = useRef<Session['role']>(null);
    const [recovery, setRecovery] = useState("");
    const [navigatingTo,setNavigatingTo]=useState<Screen|null>(null);
    const [headerActions,setHeaderActions]=useState<HTMLDivElement|null>(null);
    const guideLeaveRef=useRef<(()=>boolean)|null>(null);
    const [showGuide,setShowGuide]=useState(false);
    const navigationSequence=useRef(0);
    const [inspect, setInspect] = useState<Assignment | null>(null);
    const mainArea=useRef<HTMLElement>(null);
    const inspectTrigger=useRef<HTMLButtonElement|null>(null);
    const inspectScroll=useRef(0);
    useEffect(()=>{
        if(!inspect)return;
        mainArea.current?.scrollTo({top:0});
        mainArea.current?.querySelector<HTMLElement>('.exercise-inspection-body')?.focus({preventScroll:true});
    },[inspect?.id]);
    function closeInspect(){
        setInspect(null);
        requestAnimationFrame(()=>{
            if(mainArea.current)mainArea.current.scrollTop=inspectScroll.current;
            if(inspectTrigger.current?.isConnected)inspectTrigger.current.focus({preventScroll:true});
        });
    }
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
    const pendingFeedbackRef=useRef(false);
    const [view, setView] = useState<SubmissionView | null>(null),
        [review, setReview] = useState<Review | null>(null),
        [reviewDirty, setReviewDirty] = useState(false);
    const reviewRef = useRef<Review | null>(null),
        reviewRevision = useRef(0),
        reviewEdits = useRef(0),
        reviewSaved = useRef(0),
        reviewQueue = useRef<Promise<boolean>>(Promise.resolve(true));
    const [assignmentFilterState,setAssignmentFilterState]=useState<Partial<Record<Screen,ReturnType<typeof emptyWritingFilters>>>>({});
    const [submissionFilterState,setSubmissionFilterState]=useState<Partial<Record<Screen,ReturnType<typeof emptyWritingFilters>>>>({});
    const assignmentFilters=assignmentFilterState[screen]||emptyWritingFilters();
    const submissionFilters=submissionFilterState[screen]||emptyWritingFilters();
    const setAssignmentFilters=(value:ReturnType<typeof emptyWritingFilters>)=>setAssignmentFilterState(old=>({...old,[screen]:value}));
    const setSubmissionFilters=(value:ReturnType<typeof emptyWritingFilters>)=>setSubmissionFilterState(old=>({...old,[screen]:value}));
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
                // Initial permission loading must not undo a newer sidebar choice.
                if (roleRef.current !== null) setScreen("assignments");
                else setScreen(current =>
                    (data.role !== 'tutor' && ['library', 'rubric'].includes(current)) ||
                    (data.permission === 'viewer' && current === 'create')
                        ? 'assignments' : current);
                setView(null); setReview(null); setDraft(null); setActive(null);
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
                setView(null); setReview(null); setActive(null); setDraft(null); setScreen("assignments");
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
        window.addEventListener("latex-account-role-updated", update);
        return () => {window.removeEventListener("latex-sharing-updated", update);window.removeEventListener("latex-account-role-updated", update);};
    }, []);
    useEffect(() => {
        onLocationChange?.(`Task Writing · ${screenTitles[screen]}${screen === 'practice' || screen === 'grading' ? ` · Task ${task}` : ''}`);
    }, [screen, task, onLocationChange]);
    useEffect(()=>{setShowGuide(false);},[screen,task,active?.id]);
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
            if (dirty || reviewDirty || pendingFeedbackRef.current) {
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
    async function saveReview(automatic = false): Promise<boolean> {
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
                            awardedTotal: body.awardedTotal ?? null,
                        }),
                    },
                );
                reviewRevision.current = data.revision;
                reviewSaved.current = ticket;
                setReview((current) =>
                    current ? reviewEdits.current===ticket?data:{ ...current, revision: data.revision } : current,
                );
                setReviewDirty(reviewEdits.current !== ticket);
                if(!automatic)setNotice("Assessment draft saved; students cannot see it yet.");
                return true;
            });
        reviewQueue.current = operation;
        return operation.catch((e) => {
            setError(errorText(e));
            return false;
        });
    }
    useEffect(() => {
        if(!reviewDirty || readonly || screen !== 'grading')return;
        const timer=setTimeout(()=>{void saveReview(true);},900);
        return()=>clearTimeout(timer);
    },[review,reviewDirty,readonly,screen]);
    async function beforeLeave() {
        if(pendingFeedbackRef.current&&!confirm('Discard unfinished sentence feedback before leaving?'))return false;
        if(guideLeaveRef.current&&!guideLeaveRef.current())return false;
        return (await flush()) && (!reviewDirty || (await saveReview()));
    }
    async function navigate(next: Screen) {
        if (readonly && next === 'create') return;
        const ticket=++navigationSequence.current;
        setNavigatingTo(next);
        try{
            if (!(await beforeLeave()) || ticket!==navigationSequence.current) return;
            setError("");
            if (next === "create") {
                setEditing(undefined);setTemplate(undefined);
                if(tutor){setLibraryCreate(true);setScreen('library');return;}
            }
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
                        createDocument: report,
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
    const scopedSubmissions = overview.submissions.filter(
        (s) =>
            (s.assignment.kind||'assignment')===listKind &&
            (listKind==='exam'||(s.assignment.purpose==='practice')===practiceOnly) &&
            (screen !== "results" || s.result) &&
            (!filterAssignment || s.assignmentId === filterAssignment),
    );
    const assignmentKind=tutor?listKind:screen==='exams'?'exam':'assignment';
    const scopedAssignments=overview.assignments.filter(a=>(a.kind||'assignment')===assignmentKind && (assignmentKind==='exam'||(a.purpose==='practice')===(tutor?practiceOnly:screen==='practice-bank')));
    const courseById = new Map(overview.classes.map(course=>[course.id,course]));
    const submittedIds = new Set(overview.submissions.map(row=>row.assignmentId));
    const listedAssignments=scopedAssignments.filter(row=>matchesAssignment(row,assignmentFilters,courseById.get(row.classId||''),submittedIds.has(row.id)));
    const filtered=scopedSubmissions.filter(row=>matchesSubmission(row,submissionFilters,courseById.get(row.assignment.classId||'')));
    const kindTabs=<div className="writing-kind-tabs" role="tablist" aria-label="Exercise type"><button role="tab" aria-selected={listKind==='assignment'&&!practiceOnly} onClick={()=>{setListKind('assignment');setPracticeOnly(false);setFilterAssignment('');setInspect(null);}}>Assigned exercises</button><button role="tab" aria-selected={listKind==='assignment'&&practiceOnly} onClick={()=>{setListKind('assignment');setPracticeOnly(true);setFilterAssignment('');setInspect(null);}}>Practice</button><button role="tab" aria-selected={listKind==='exam'} onClick={()=>{setListKind('exam');setFilterAssignment('');setInspect(null);}}>Examinations</button></div>;
    const examExpired=active?.kind==='exam' && (draft?.readOnly || remaining!==null && remaining<=0);
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
                <WorkspaceNavigation tutor={tutor} active={screen} loading={loading||!overview.role} readonly={readonly} navigatingTo={navigatingTo} shell={shellNavigation}
                    onWriting={id=>{if(id==='submissions')setFilterAssignment('');void navigate(id);}}/>
                <section ref={mainArea} className={`writing-main${screen==='library'?' writing-library-main':''}`}>
                    <div className="writing-page-heading">
                        <div>
                            <span className="writing-eyebrow">
                                TASK WRITING
                            </span>
                            <h1>{screen === "assignments" && tutor ? "Assessment" : screen === "submissions" ? (tutor ? "Student submissions" : "Submission history") : screenTitles[screen]}</h1>
                            {screen==='library'&&<small className="library-role-label">Tutor · {readonly?'Read only':'Editing enabled'}</small>}
                        </div>
                        <div>
                            <div className="writing-header-actions" ref={setHeaderActions}/>
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
                    {overview.role && screen!=='library' && <div className="writing-access-summary">Your role: <strong>{tutor ? "Tutor" : "Student"}</strong> · {readonly ? "Read only — ask the owner for editing access." : "Editing enabled"}</div>}
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
                            {screen === "library" && tutor && <QuestionLibrary headerActions={headerActions} userId={session.id||''} initialCreate={libraryCreate} onCreateOpened={()=>setLibraryCreate(false)} onAssigned={a=>{setListKind(a.kind==='exam'?'exam':'assignment');setPracticeOnly(a.purpose==='practice');setNotice(a.published?'Exercise assigned to students.':'Exercise draft saved.');void reload();setScreen('assignments');}}/>}
                            {screen === "guides" && <WritingGuides tutor={tutor} readonly={readonly} courses={overview.classes} headerActions={headerActions} leaveRef={guideLeaveRef}/>}
                            {screen === "create" && !readonly && (
                                <AssignmentForm
                                    embedded
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
                                    {tutor && inspect && <ExerciseDetails assignment={inspect} overview={overview} language={language} onClose={closeInspect}/>}
                                    <div className="writing-list-toolbar">
                                        <p>
                                            {tutor
                                                ? "Manage assessments for assigned exercises, practice and examinations."
                                                : screen==='exams'?"Examinations have a password and opening/closing times.":"Assigned exercises and personal practice."}
                                        </p>
                                        {!tutor && screen!=='exams'&&<button
                                            onClick={() =>
                                                void navigate("create")
                                            }
                                        >
                                            <Plus size={16} />
                                            Personal practice
                                        </button>}
                                    </div>
                                    <WritingListFilters value={assignmentFilters} onChange={setAssignmentFilters} courses={overview.classes} mode="assignments" tutor={tutor} count={listedAssignments.length} total={scopedAssignments.length}/>
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
                                                        <h2>{a.title}</h2>{a.scoring&&<span className="writing-tag">Scale: {a.scoring.total} points</span>}
                                                        {a.description&&<p className="writing-exercise-description">{a.description}</p>}
                                                        <p>
                                                            {a.category && a.category!=='ielts'?a.category==='report'?'Report':'Research':"IELTS Task"}{" "}
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
                                                        <button aria-expanded={inspect?.id===a.id} onClick={event=>{inspectTrigger.current=event.currentTarget;inspectScroll.current=mainArea.current?.scrollTop||0;setInspect(a);}}>View question & students</button>
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
                                            {scopedAssignments.length ? "No exercises match these filters." : "No exercises yet."}{" "}
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
                                        <div className="writing-practice-strip">{active.scoring&&<span className="writing-tag">Scale: {active.scoring.total} points</span>}
                                            {active.kind!=='exam'&&<button className="practice-guide-button" onClick={()=>setShowGuide(true)}><BookOpen size={16}/>Writing guide</button>}
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
                                                {active.category && active.category!=='ielts'?active.category==='report'?'Report':'Research':"IELTS Task"}{" "}
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
                                                    <h2>{active.title}</h2>{active.assessmentCategories?.[String(task)]&&<p className="writing-selected-categories">Write: {active.assessmentCategories[String(task)].map(k=>assessmentCategoryTitle(active.rubricCatalog?.[active.profiles[String(task)]],k)).join(" + ")}</p>}

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
                                                            <QuestionVisual src={assetUrl(active.imageId)} alt="Task 1 visual"/>
                                                        ))}
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
                                                            const nextBlocks={...draftRef.current!.blocks};
                                                            if(blocks?.length)nextBlocks[String(task)]=blocks;
                                                            else delete nextBlocks[String(task)];
                                                            changeDraft({answers:{...draftRef.current!.answers,[task]:text},
                                                                blocks:nextBlocks,
                                                                latexSources:{...draftRef.current!.latexSources,[task]:''}});
                                                        }}/>

                                                    <footer>
                                                        <span>{countWords(draft.answers[String(task)] || '')} words</span>
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
                                    <h2>{active.title}</h2>{active.assessmentCategories?.[String(task)]&&<p className="writing-selected-categories">Write: {active.assessmentCategories[String(task)].map(k=>assessmentCategoryTitle(active.rubricCatalog?.[active.profiles[String(task)]],k)).join(" + ")}</p>}

                                    <p>
                                        Submissions are saved separately. Editing your draft later does not change the work your tutor assesses.
                                    </p>
                                    {active.tasks.map((n) => (
                                        <article key={n}>
                                            <h3>
                                                {!active.category||active.category==='ielts'?`Task ${n}`:'Document'} ·{" "}
                                                {countWords(draft.answers[String(n)] || '')} words
                                            </h3>
                                            <div className="submission-check-panes">
                                                <section className="submission-check-question"><h4>Question</h4>
                                                    <div className="writing-prompt-text">{active.prompts[String(n)]}</div>
                                                    {active.promptFiles?.[String(n)] && <PromptDocument focused file={active.promptFiles[String(n)]} language={language}/>}
                                                    {n===1&&active.imageId&&(active.imageId==='sample-commuting'?<CommutingChart/>:<QuestionVisual src={assetUrl(active.imageId)} alt="Task 1 visual"/>)}
                                                </section>
                                                <section className="submission-check-answer"><h4>Your answer</h4><p>{draft.answers[String(n)] || "No answer yet."}</p></section>
                                            </div>
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
                                    <WritingListFilters value={submissionFilters} onChange={setSubmissionFilters} courses={overview.classes} mode="submissions" tutor={tutor} count={filtered.length} total={scopedSubmissions.length}/>
                                    {filtered.length ? (
                                        <SubmissionTable submissions={filtered} courses={overview.classes} tutor={tutor} onOpen={id=>void openSubmission(id)}/>
                                    ) : (
                                        <div className="writing-empty">
                                            {scopedSubmissions.length ? "No submissions match these filters." : screen === "results"
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
                                        pendingFeedbackRef={pendingFeedbackRef}
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
                                <ProjectRubrics headerActions={headerActions} editable={tutor&&!readonly}/>
                            )}
                        </>
                    )}
                </section>
            </div>
            {showGuide&&screen==='practice'&&active&&active.kind!=='exam'&&<GuideDrawer onClose={()=>setShowGuide(false)} title="Practice reference"><WritingGuides key={`${active.id}:${task}`} tutor={false} courses={overview.classes} practice={{category:active.category||'ielts',task,profile:active.profiles[String(task)]||'',classId:active.classId}}/></GuideDrawer>}
            <div className="sr-only" role="status" aria-live="polite">{notice}</div>
        </section>
    );
}
