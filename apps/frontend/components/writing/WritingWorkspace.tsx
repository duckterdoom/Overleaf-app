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

type Screen =
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
    overview: "Không gian chấm bài",
    assignments: "Bài được giao",
    exams: "Bài kiểm tra",
    create: "Chuẩn bị bài tập",
    practice: "Luyện viết",
    check: "Kiểm tra và nộp",
    submissions: "Bản nộp",
    results: "Kết quả & phản hồi",
    grading: "Đọc và chấm bài",
    rubric: "Tiêu chí chấm",
};
const date = (value?: string | null) =>
    value ? new Date(value).toLocaleString("vi-VN") : "Không có hạn";

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
    const { json, assetUrl } = useWritingApi();
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
    const [examGate,setExamGate]=useState<Assignment|null>(null),[examEntryPassword,setExamEntryPassword]=useState('');
    const [passwordTarget,setPasswordTarget]=useState<Assignment|null>(null),[newExamPassword,setNewExamPassword]=useState('');
    const [loading, setLoading] = useState(true),
        [busy, setBusy] = useState(false),
        [error, setError] = useState(""),
        [notice, setNotice] = useState("Dữ liệu được lưu trên server");
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
                setNotice("Đã xác nhận vai trò dự án; nội dung đã lưu vẫn được giữ trên server.");
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
        onLocationChange?.(`IELTS Writing · ${screenTitles[screen]}${screen === 'practice' || screen === 'grading' ? ` · Task ${task}` : ''}`);
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
                    setNotice("Đã lưu bài viết trên server");
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
                setNotice("Đã lưu nháp chấm; học viên chưa thấy bản nháp.");
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
            if (next === "create") setEditing(undefined);
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
                    "Bản chấm vừa thay đổi; lưu lại trước khi công bố.",
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
            setNotice("Đã công bố kết quả cho học viên.");
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
            setNotice("Đã nộp. Snapshot đã được lưu riêng với bản đang viết.");
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
                    }),
                },
            );
            if(response.pdf){
                const bytes=Uint8Array.from(atob(response.pdf),c=>c.charCodeAt(0));
                const link=document.createElement('a');const url=URL.createObjectURL(new Blob([bytes],{type:'application/pdf'}));
                link.href=url;link.download=report?'ielts-feedback.pdf':'ielts-submission.pdf';link.click();setTimeout(()=>URL.revokeObjectURL(url),1000);
                setNotice('Đã lưu phiếu trong dự án và tải PDF. Không tạo dự án mới.');
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
            (screen !== "results" || s.result) &&
            (!filterAssignment || s.assignmentId === filterAssignment),
    );
    const assignmentKind=tutor?listKind:screen==='exams'?'exam':'assignment';
    const listedAssignments=overview.assignments.filter(a=>(a.kind||'assignment')===assignmentKind);
    const kindTabs=<div className="writing-kind-tabs" role="tablist" aria-label="Phân loại bài"><button role="tab" aria-selected={listKind==='assignment'} onClick={()=>{setListKind('assignment');setFilterAssignment('');setInspect(null);}}>Bài tập</button><button role="tab" aria-selected={listKind==='exam'} onClick={()=>{setListKind('exam');setFilterAssignment('');setInspect(null);}}>Bài kiểm tra</button></div>;
    const examExpired=active?.kind==='exam' && (draft?.readOnly || remaining!==null && remaining<=0);
    const navigation = tutor
        ? ([
              ["overview", "Tổng quan", GraduationCap],
              ["create", "Giao bài", Plus],
              ["assignments", "Đề đã tạo", BookOpen],
              ["submissions", "Bài nộp học viên", ClipboardCheck],
              ["rubric", "Tiêu chí chấm", Check],
          ] as const)
        : ([
              ["overview", "Tổng quan", GraduationCap],
              ["practice", "Luyện viết", FileText],
              ["assignments", "Bài được giao", BookOpen],
              ["exams", "Bài kiểm tra", Clock3],
              ["submissions", "Lịch sử nộp", Send],
              ["results", "Kết quả & phản hồi", ClipboardCheck],
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
            {examGate&&<div className="writing-exam-backdrop"><form className="writing-exam-dialog" role="dialog" aria-modal="true" aria-label="Vào bài kiểm tra" onSubmit={e=>{e.preventDefault();void execute(async()=>{
                const result=await json<{assignment:Assignment}>(`/writing/assignments/${examGate.id}/enter`,{method:'POST',body:JSON.stringify({password:examEntryPassword})});
                setExamEntryPassword('');setExamGate(null);await openAssignment(result.assignment);
            });}}><h2>{examGate.title}</h2><p>Mở: {date(examGate.opensAt)}<br/>Đóng: {date(examGate.closesAt)}<br/>Thời lượng: {examGate.durationMinutes} phút</p><label>Mật khẩu vào bài<input aria-label="Mật khẩu vào bài" autoFocus type="password" autoComplete="off" required maxLength={128} value={examEntryPassword} onChange={e=>setExamEntryPassword(e.target.value)}/></label>{error&&<p role="alert" className="writing-error">{error}</p>}<footer><button type="button" disabled={busy} onClick={()=>{setExamGate(null);setExamEntryPassword('');}}>Hủy</button><button className="primary" disabled={busy} type="submit">Bắt đầu bài kiểm tra</button></footer></form></div>}
            {passwordTarget&&<div className="writing-exam-backdrop"><form className="writing-exam-dialog" role="dialog" aria-modal="true" aria-label="Đổi mật khẩu bài kiểm tra" onSubmit={e=>{e.preventDefault();void execute(async()=>{
                await json(`/writing/assignments/${passwordTarget.id}/exam-password`,{method:'PATCH',body:JSON.stringify({password:newExamPassword,revision:passwordTarget.revision})});
                setPasswordTarget(null);setNewExamPassword('');setNotice('Đã đổi mật khẩu. Người đang làm bài giữ nguyên đồng hồ.');await reload(true);
            });}}><h2>Đổi mật khẩu</h2><p>{passwordTarget.title}</p><label>Mật khẩu mới<input aria-label="Mật khẩu kiểm tra mới" type="password" autoComplete="new-password" minLength={6} maxLength={128} required value={newExamPassword} onChange={e=>setNewExamPassword(e.target.value)}/></label>{error&&<p role="alert" className="writing-error">{error}</p>}<footer><button type="button" disabled={busy} onClick={()=>{setPasswordTarget(null);setNewExamPassword('');}}>Hủy</button><button className="primary" disabled={busy}>Lưu mật khẩu</button></footer></form></div>}
            <div className="writing-layout">
                <aside className="writing-sidebar">
                    <span className="writing-eyebrow">
                        {loading || !overview.role ? "ĐANG XÁC NHẬN VAI TRÒ DỰ ÁN" : `KHÔNG GIAN ${tutor ? "TUTOR" : "HỌC VIÊN"}`}
                    </span>
                    {(loading || !overview.role ? [] : navigation).map(([id, label, Icon]) => (
                        <button
                            key={id}
                            disabled={readonly && id === "create"}
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
                            Viết rõ hơn.
                            <br />
                            Nhận xét cụ thể hơn.
                        </strong>
                        <p>
                            IELTS để luyện viết.
                            <br />
                            LaTeX để trình bày và xuất tài liệu.
                        </p>
                    </div>
                </aside>
                <section className="writing-main">
                    <div className="writing-page-heading">
                        <div>
                            <span className="writing-eyebrow">
                                CHẤM BÀI × LATEX
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
                                        ? "Giao bài mới"
                                        : "Luyện một bài mới"}
                                </button>
                            )}
                            <button
                                title="Tải lại dữ liệu IELTS"
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
                    {overview.role && <div className="writing-notice">Vai trò trong dự án: <strong>{tutor ? "Tutor" : "Học viên"}</strong> · {readonly ? "Chỉ xem — liên hệ chủ sở hữu để được cấp quyền chỉnh sửa." : "Có quyền chỉnh sửa"}. Quyền dùng chung với editor LaTeX.</div>}
                    {recovery && <div className="writing-notice">Quyền/vai trò vừa thay đổi. Bạn có thể giữ bản sửa chưa được lưu: <button onClick={() => {
                        const url = URL.createObjectURL(new Blob([recovery], {type: 'application/json'}));
                        const link = document.createElement('a'); link.href = url; link.download = 'ielts-unsaved-recovery.json'; link.click();
                        setTimeout(() => URL.revokeObjectURL(url), 1000);
                    }}>Tải bản nháp chưa lưu</button></div>}
                    {!readonly && !!((overview.legacy?.classes || 0) + (overview.legacy?.assignments || 0)) &&
                        <div className="writing-notice">Bạn có {overview.legacy?.classes} lớp và {overview.legacy?.assignments} đề IELTS cũ chưa gắn dự án.
                            <button onClick={() => {
                                if (window.confirm('Đưa lớp/đề cũ của bạn và các bản nộp liên quan vào dự án đang mở? Dữ liệu sẽ dùng quyền chia sẻ của dự án này; hãy mời học viên cũ vào dự án để họ tiếp tục truy cập.'))
                                    void execute(async () => { if (!(await beforeLeave())) return; await json('/writing/import-legacy', {method: 'POST'}); await reload(); });
                            }} disabled={busy}>Đưa dữ liệu cũ vào dự án này</button>
                        </div>}
                    {loading ? (
                        <div className="writing-empty">
                            Đang tải không gian IELTS…
                        </div>
                    ) : (
                        <>
                            {screen === "overview" && (
                                <>
                                    <section className="writing-welcome">
                                        <div>
                                            <span className="writing-eyebrow">
                                                {tutor
                                                    ? "ĐỒNG HÀNH CÙNG BÀI VIẾT"
                                                    : "TỪ Ý TƯỞNG ĐẾN BÀI VIẾT"}
                                            </span>
                                            <h2>
                                                {tutor
                                                    ? "Mỗi đoạn viết, một góp ý có ích."
                                                    : "Một nơi để viết và tiến bộ."}
                                            </h2>
                                            <p>
                                                {tutor
                                                    ? "Giao bài, đọc snapshot học viên đã nộp, ghi nhận theo từng đoạn và công bố phản hồi."
                                                    : "Luyện IELTS, Report hoặc Research từ tệp dự án và nhận phản hồi từ Tutor."}
                                            </p>
                                        </div>
                                        <div className="writing-welcome-art">
                                            <FileText size={68} />
                                            <span>
                                                IELTS
                                                <br />
                                                <strong>× LaTeX</strong>
                                            </span>
                                        </div>
                                    </section>
                                    <div className="writing-stats">
                                        {[
                                            [
                                                overview.assignments.length,
                                                "Đề trong dự án",
                                            ],
                                            [
                                                overview.assignments.filter(
                                                    (a) => a.published,
                                                ).length,
                                                "Bài tập",
                                            ],
                                            [
                                                overview.submissions.filter(
                                                    (s) => !s.result,
                                                ).length,
                                                tutor
                                                    ? "Chờ chấm"
                                                    : "Chờ kết quả",
                                            ],
                                            [
                                                overview.submissions.filter(
                                                    (s) => s.result,
                                                ).length,
                                                "Đã công bố",
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
                                                    ? "Giao bài cho thành viên dự án"
                                                    : "Bài tập trong dự án"}
                                            </h2>
                                            <p>
                                                {tutor
                                                    ? "Chọn tệp đề trong editor để giao cho Học viên được chia sẻ dự án."
                                                    : "Mở bài được giao hoặc chọn tệp trong dự án để luyện cá nhân."}
                                            </p>
                                        </div>
                                        <button
                                            onClick={() =>
                                                void navigate(tutor ? "create" : "assignments")
                                            }
                                        >
                                            {tutor
                                                ? "Giao bài"
                                                : "Bài được giao"}
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
                                    onCancel={() =>
                                        void navigate("assignments")
                                    }
                                    onDone={(a) => {
                                        void reload();
                                        if (tutor) {
                                            setListKind(a.kind==='exam'?'exam':'assignment');
                                            setNotice(
                                                a.published
                                                    ? "Đã giao bài cho Học viên trong dự án."
                                                    : "Đã lưu đề nháp.",
                                            );
                                            setScreen("assignments");
                                        } else void openAssignment(a);
                                    }}
                                />
                            )}
                            {(screen === "assignments" || screen==='exams') && (
                                <>
                                    {tutor&&kindTabs}
                                    {tutor && inspect && <section className="writing-card">
                                        <h2>{inspect.title}</h2>
                                        <button onClick={()=>setInspect(null)}>Đóng chi tiết đề</button>
                                        {inspect.tasks.map(number=><div key={number}>
                                            <h3>{!inspect.category||inspect.category==='ielts'?`Task ${number}`:inspect.category==='report'?'Report':'Research'}</h3>
                                            {inspect.promptFiles?.[String(number)] ? <PromptDocument file={inspect.promptFiles[String(number)]} language={language}/> : <p>{inspect.prompts[String(number)] || 'Đề cũ chưa có tệp từ editor. Sửa bài để chọn tệp đề.'}</p>}
                                        </div>)}
                                        <h3>Theo dõi học viên</h3>
                                        <table><thead><tr><th>Học viên</th><th>Trạng thái</th><th>Bản nộp</th></tr></thead><tbody>
                                            {(overview.students || []).map(person=>{
                                                const submissions=overview.submissions.filter(s=>s.assignmentId===inspect.id && s.studentId===person.id);
                                                return <tr key={person.id}><td>{person.name}</td><td>{submissions.length?'Đã nộp':'Chưa nộp'}</td><td>{submissions.length}</td></tr>;
                                            })}
                                        </tbody></table>
                                    </section>}
                                    <div className="writing-list-toolbar">
                                        <p>
                                            {tutor
                                                ? "Đề nháp và bài đã giao trong dự án."
                                                : screen==='exams'?"Bài kiểm tra có mật khẩu và giờ mở/đóng riêng.":"Bài được giao và bài luyện cá nhân."}
                                        </p>
                                        {(tutor||screen!=='exams')&&<button
                                            onClick={() =>
                                                void navigate("create")
                                            }
                                        >
                                            <Plus size={16} />
                                            {tutor
                                                ? "Tạo bài tập"
                                                : "Bài luyện cá nhân"}
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
                                                                ? "Đã giao"
                                                                : "Đề nháp"}
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
                                                                        (!a.category||a.category==='ielts'?"Task " + t:"Tài liệu"),
                                                                )
                                                                .join(
                                                                    " + ",
                                                                )}{" "}
                                                            ·{" "}
                                                            {a.practiceOwner
                                                                ? "Luyện cá nhân"
                                                                : a.tutorName}
                                                        </p>
                                                        <small>
                                                            {a.kind==='exam'?`Mở: ${date(a.opensAt)} · Đóng: ${date(a.closesAt)} · ${a.durationMinutes} phút`:`Hạn: ${date(a.dueAt)}`}
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
                                                                ? "Xem bản nộp"
                                                                : "Sửa đề nháp"
                                                            : a.kind==='exam'?'Vào bài kiểm tra':"Mở bài viết"}
                                                        <ArrowRight size={15} />
                                                    </button>
                                                    {tutor && <div>
                                                        <button onClick={()=>{setInspect(a);}}>Xem đề và học viên</button>
                                                        {!readonly && <>
                                                            {a.kind==='exam'&&<button onClick={()=>{setPasswordTarget(a);setNewExamPassword('');setError('');}}>Đổi mật khẩu</button>}
                                                            <button onClick={()=>{setEditing(a);setScreen('create');}}>Sửa bài tập</button>
                                                            <button onClick={async()=>{
                                                                if(!window.confirm('Xóa bài tập khỏi danh sách? Bản nộp và kết quả đã có vẫn được giữ.'))return;
                                                                try{await json(`/writing/assignments/${a.id}?revision=${a.revision}`,{method:'DELETE'});setInspect(null);await reload();}catch(e){setError(errorText(e));}
                                                            }}>Xóa bài tập</button>
                                                        </>}
                                                    </div>}
                                                </article>
                                            ))}
                                        </div>
                                    ) : (
                                        <div className="writing-empty">
                                            Chưa có bài tập.{" "}
                                            {tutor
                                                ? "Hãy lấy đề từ editor và giao cho dự án."
                                                : "Mở bài được giao hoặc tạo bài luyện cá nhân."}
                                        </div>
                                    )}
                                </>
                            )}
                            {screen === "practice" &&
                                (!active || !draft ? (
                                    <div className="writing-empty">
                                        <BookOpen size={40} />
                                        <h2>Sẵn sàng cho một bài viết mới?</h2>
                                        <button
                                            className="primary"
                                            onClick={() =>
                                                void navigate("create")
                                            }
                                        >
                                            Tạo bài luyện IELTS
                                        </button>
                                        <button
                                            onClick={() =>
                                                void navigate("assignments")
                                            }
                                        >
                                            Xem bài được giao
                                        </button>
                                    </div>
                                ) : (
                                    <div className="writing-practice">
                                        <div className="writing-practice-strip">
                                            <div
                                                className="writing-task-tabs"
                                                role="tablist"
                                                aria-label="Task đang viết"
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
                                                    ? "Không bấm giờ"
                                                    : remaining === 0
                                                      ? active.kind==='exam'?"Hết giờ — chỉ xem bản nháp đã lưu":"Hết giờ — bài vẫn được lưu"
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
                                                Kiểm tra và nộp
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
                                                        ĐỀ BÀI · TASK {task}
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
                                                                    Đề và dữ
                                                                    liệu luyện
                                                                    tập tự biên
                                                                    soạn.
                                                                </small>
                                                            </>
                                                        ) : (
                                                            <img
                                                                src={assetUrl(active.imageId)}
                                                                alt="Hình đề Task 1"
                                                            />
                                                        ))}
                                                    {(!active.category || active.category === "ielts") && <p className="writing-question-note">
                                                        Ít nhất{" "}
                                                        {task === 1 ? 150 : 250}{" "}
                                                        từ ·{" "}
                                                        {task === 1
                                                            ? "Gợi ý 20 phút"
                                                            : "Gợi ý 40 phút"}
                                                        <br />
                                                        Đếm từ là công cụ hỗ
                                                        trợ, không tự quy đổi
                                                        thành band.
                                                    </p>}
                                                </aside>
                                                <section className="writing-answer">
                                                    <header>
                                                        <strong>
                                                            Câu trả lời của bạn
                                                        </strong>
                                                        <span>
                                                            {saving
                                                                ? "Đang lưu…"
                                                                : dirty
                                                                  ? "Chưa lưu"
                                                                  : "Đã lưu trên server"}
                                                        </span>
                                                    </header>
                                                    <textarea
                                                        readOnly={readonly||!!examExpired}
                                                        aria-label={`Câu trả lời Task ${task}`}
                                                        spellCheck
                                                        value={
                                                            draft.answers[
                                                                String(task)
                                                            ] || ""
                                                        }
                                                        onChange={(e) =>
                                                            changeDraft({
                                                                answers: {
                                                                    ...draftRef
                                                                        .current!
                                                                        .answers,
                                                                    [task]: e
                                                                        .target
                                                                        .value,
                                                                },
                                                            })
                                                        }
                                                        onKeyDown={(e) => {
                                                            if (
                                                                (e.ctrlKey ||
                                                                    e.metaKey) &&
                                                                e.key.toLowerCase() ===
                                                                    "s"
                                                            ) {
                                                                e.preventDefault();
                                                                void flush();
                                                            }
                                                        }}
                                                        placeholder={
                                                            task === 1
                                                                ? "Describe the main features and make relevant comparisons…"
                                                                : "Develop your response to all parts of the question…"
                                                        }
                                                    />
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
                                                                (active.category && active.category!=='ielts'?0:task === 1
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
                                                                từ
                                                            </strong>{" "}
                                                            {(!active.category||active.category==='ielts') && ` / ${task===1?150:250}`}
                                                        </span>
                                                        <button
                                                            disabled={
                                                                readonly || !dirty || saving
                                                            }
                                                            onClick={() =>
                                                                void flush()
                                                            }
                                                        >
                                                            Lưu bài
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
                                        SNAPSHOT NỘP BÀI
                                    </span>
                                    <h2>{active.title}</h2>
                                    <p>
                                        Bản nộp được giữ riêng. Sửa working copy
                                        sau này không thay đổi bài Tutor đang
                                        chấm.
                                    </p>
                                    {active.tasks.map((n) => (
                                        <article key={n}>
                                            <h3>
                                                {!active.category||active.category==='ielts'?`Task ${n}`:'Tài liệu'} ·{" "}
                                                {countWords(
                                                    draft.answers[String(n)] ||
                                                        "",
                                                )}{" "}
                                                từ
                                            </h3>
                                            {active.promptFiles?.[String(n)] && <PromptDocument focused file={active.promptFiles[String(n)]} language={language}/>}
                                            {countWords(
                                                draft.answers[String(n)] || "",
                                            ) < (n === 1 ? 150 : 250) && (!active.category||active.category==='ielts') && (
                                                <p className="writing-word-warning">
                                                    Bài dưới số từ khuyến nghị;
                                                    không tự suy ra band.
                                                </p>
                                            )}
                                            <p>
                                                {draft.answers[String(n)] ||
                                                    "Chưa có câu trả lời."}
                                            </p>
                                        </article>
                                    ))}
                                    <footer>
                                        <button
                                            onClick={() =>
                                                setScreen("practice")
                                            }
                                        >
                                            Quay lại viết
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
                                                ? "Đang nộp…"
                                                : "Xác nhận nộp bài"}
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
                                                ? "Chỉ các bản nộp thuộc bài bạn đã giao."
                                                : "Bản nộp và phản hồi đã công bố của bạn."}
                                        </p>
                                        {filterAssignment && (
                                            <button
                                                onClick={() =>
                                                    setFilterAssignment("")
                                                }
                                            >
                                                Xem tất cả bài nộp
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
                                                                ? "Học viên"
                                                                : "Bài tập"}
                                                        </th>
                                                        <th>Task</th>
                                                        <th>
                                                            Lần nộp / thời gian
                                                        </th>
                                                        <th>Trạng thái</th>
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
                                                                          "Luyện cá nhân"}
                                                                </small>
                                                            </td>
                                                            <td>
                                                                {s.assignment.tasks
                                                                    .map(
                                                                        (n) =>
                                                                            `Task ${n}: ${s.wordCounts[String(n)]} từ`,
                                                                    )
                                                                    .join(
                                                                        " · ",
                                                                    )}
                                                            </td>
                                                            <td>
                                                                Lần {s.attempt}
                                                                <small>
                                                                    {date(
                                                                        s.submittedAt,
                                                                    )}
                                                                    {s.late
                                                                        ? " · Quá hạn"
                                                                        : ""}
                                                                </small>
                                                            </td>
                                                            <td>
                                                                <span className="writing-tag">
                                                                    {s.result
                                                                        ? "Đã công bố"
                                                                        : s.tutorId
                                                                          ? "Chờ chấm"
                                                                          : "Luyện cá nhân"}
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
                                                                        ? "Mở bài chấm"
                                                                        : "Xem bản nộp"}
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
                                                ? "Chưa có kết quả được công bố."
                                                : "Chưa có bản nộp."}
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
                <span>{navigatingTo?'Đang lưu và chuyển mục…':notice}</span>
                <span>
                    {dirty
                        ? "Bài viết chưa lưu"
                        : reviewDirty
                          ? "Bản chấm chưa lưu"
                          : "Lưu trên server · MongoDB"}
                </span>
            </footer>
        </section>
    );
}
