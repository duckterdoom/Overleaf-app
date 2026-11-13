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
    unreviewed: "Chưa xem",
    met: "Đạt",
    partial: "Một phần",
    missing: "Cần sửa",
    na: "Không áp dụng",
};
const kinds: Record<string, string> = {
    intro: "Introduction / Opening",
    overview: "Overview",
    body: "Body",
    conclusion: "Conclusion",
    closing: "Closing",
    whole: "Toàn bài / yêu cầu đề",
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
    const isIelts=!assignment.category || assignment.category==='ielts';
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
    const kinds=Object.fromEntries(Object.keys(selectedProfile?.checks||{}).map(key=>[key,selectedProfile?.sectionTitles?.[key]||({intro:'Introduction / Opening',overview:'Overview',body:'Body',conclusion:'Conclusion',closing:'Closing',whole:'Toàn bài / yêu cầu đề'} as Record<string,string>)[key]||key]));
    const [autoError,setAutoError]=useState('');
    function autoScore(){
        if(!grade||!selectedProfile)return;
        const sections=[...parts.map((_,i)=>section(i)),section(-1)];
        const scores=checklistScores(selectedProfile,sections,task);
        if(!scores){setAutoError('Đánh giá hết các mục trong từng đoạn và Toàn bài (Đạt / Một phần / Cần sửa / Không áp dụng). Mỗi mã IELTS cần ít nhất một mục có đánh giá.');return;}
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
        const generated=index===-1?{paragraph:-1,kind:'whole',title:kinds.whole||'Toàn bài',checks:{},note:'',quote:''}:defaultSection(task,assignment,index,parts.length);
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
        if(isIelts && grade.scoreSource==='checklist' && selectedProfile){
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
                                ? "Có nhận xét"
                                : `${met}/${checks.length} mục đạt`}
                        </small>
                    </span>
                    <ChevronDown size={15} />
                </button>
                {open && (
                    <div className="writing-card-body">
                        {editable && (
                            <label>
                                Phần của bài
                                <select
                                    aria-label={`Phần đoạn ${index + 1}`}
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
                                                "Trạng thái " + check.text
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
                                Gắn phần chữ đã chọn
                            </button>
                        )}
                        {item.quote && (
                            <blockquote>
                                {item.quote}
                                {editable && (
                                    <button
                                        title="Bỏ minh chứng"
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
                                Nhận xét theo đoạn
                                <textarea
                                    aria-label={`Nhận xét đoạn ${index + 1}`}
                                    rows={4}
                                    value={item.note}
                                    placeholder="Điểm mạnh, điều cần sửa và cách luyện tiếp…"
                                    onChange={(e) =>
                                        changeSection(index, {
                                            note: e.target.value,
                                        })
                                    }
                                />
                            </label>
                        ) : (
                            <p>{item.note || "Không có nhận xét riêng."}</p>
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
                    aria-label="Task trong bản nộp"
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
                    {submission.studentName} · Lần {submission.attempt}
                </span>
                <button onClick={() => setBand(true)}>
                    <ClipboardCheck size={15} />
                    {isIelts?'Tổng kết band':'Tổng kết nhận xét'}
                </button>
                {published && (
                    <button onClick={() => onDocument(true)}>
                        Tạo phiếu LaTeX
                    </button>
                )}
                {editable && (
                    <button
                        className="primary"
                        disabled={busy || !dirty}
                        onClick={() => void onSave()}
                    >
                        {busy ? "Đang lưu…" : "Lưu nháp chấm"}
                    </button>
                )}
            </div>
            {!editable && !published && (
                <div className="writing-notice">
                    Bài đã nộp. Tutor chưa công bố kết quả; bản chấm nháp không
                    hiển thị cho học viên.
                </div>
            )}
            <div className="writing-review-meta">
                <span>
                        {!isIelts?assignment.category==='report'?'Report':'Research':assignment.variant === "academic"
                        ? "Academic"
                        : "General Training"}{" "}
                    · {projectCatalog[assignment.profiles[String(task)]]?.label}
                </span>
                <strong>{submission.wordCounts[String(task)]} từ</strong>
            </div>
            {editable && grade && (
                <label className="writing-profile-select">
                    Dạng bài đối chiếu
                    <select
                        aria-label="Dạng bài chấm"
                        value={grade.profile}
                        onChange={(e) =>
                            changeGrade({ profile: e.target.value,sections:[],scores:isIelts?Object.fromEntries(codes(task).map(code=>[code,null])):{},scoreSource:'manual' })
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
                        Checklist hỗ trợ ghi nhận, không quy đổi dấu tích thành
                        band.
                    </small>
                </label>
            )}
            <div className="writing-review-scroll">
                <div className="writing-paper-grid">
                    <div className="writing-paper-title">
                        <span className="writing-eyebrow">
                            {isIelts?`TASK ${task}`:assignment.category?.toUpperCase()} · BẢN NỘP KHÔNG THAY ĐỔI
                        </span>
                        <p>{assignment.prompts[String(task)]}</p>
                        {assignment.promptFiles?.[String(task)] && <details><summary>{isIelts?`Xem đề bài Task ${task}`:'Xem yêu cầu bài tập'}</summary><PromptDocument focused file={assignment.promptFiles[String(task)]}/></details>}
                        {task === 1 &&
                            assignment.imageId &&
                            (assignment.imageId === "sample-commuting" ? (
                                <CommutingChart />
                            ) : (
                                <img
                                    src={assetUrl(assignment.imageId)}
                                    alt="Hình đề Task 1"
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
                                    {section(index).title} · Đoạn {index + 1}
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
                                    title={`Nhận xét đoạn ${index + 1}`}
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
                    {(editable || published) && (
                        <div className="writing-paragraph-row">
                            <div className="writing-whole-note">
                                <Check size={18} />
                                {isIelts?'Đối chiếu yêu cầu toàn bài trước khi tổng kết band.':'Đối chiếu yêu cầu và minh chứng trước khi công bố nhận xét.'}
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
                        aria-label={isIelts?'Tổng kết band IELTS':'Tổng kết nhận xét'}
                        onClick={(e) => e.stopPropagation()}
                    >
                        <header>
                            <div>
                                <span className="writing-eyebrow">
                                    ĐÁNH GIÁ TOÀN BÀI
                                </span>
                                <h2>{isIelts?'4 tiêu chí IELTS':'Nhận xét và kết luận'}</h2>
                            </div>
                            <button
                                aria-label={isIelts?'Đóng tổng kết band':'Đóng tổng kết nhận xét'}
                                onClick={() => setBand(false)}
                            >
                                <X size={20} />
                            </button>
                        </header>
                        {isIelts && <p className="writing-muted">
                            Band ước tính luyện tập do Tutor đánh giá; không
                            phải kết quả thi IELTS chính thức.
                        </p>}
                        {isIelts && editable && <section className="writing-notice"><p>Điểm checklist = 9 × tỷ lệ đạt theo từng mã, làm tròn 0.5. Đạt = 1, Một phần = 0.5, Cần sửa = 0; Không áp dụng bỏ qua. Mục lặp ở nhiều đoạn lấy mức thấp nhất. Đây là quy ước luyện tập, không phải quy đổi band chính thức.</p><button onClick={autoScore}>Tự tổng kết từ checklist</button>{autoError&&<p role="alert">{autoError}</p>}{grade?.scoreSource==='checklist'&&<small>Điểm được tính từ checklist; Tutor có thể điều chỉnh.</small>}</section>}
                        <div className="writing-task-tabs">
                            {assignment.tasks.map((n) => (
                                <button
                                    key={n}
                                    aria-pressed={task === n}
                                    onClick={() => setTask(n)}
                                >
                                    {isIelts?`Task ${n}`:'Tài liệu'}
                                </button>
                            ))}
                        </div>
                        {(isIelts?codes(task):[]).map((code) => (
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
                                    <summary>Gợi ý đánh giá</summary>
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
                                            "Chưa có nhận xét."}
                                    </p>
                                )}
                            </section>
                        ))}
                        {isIelts && <div className="writing-band-estimate">
                            <span>Task {task} band ước tính</span>
                            <strong>
                                {taskEstimate(grade) === null
                                    ? "—"
                                    : rounded(taskEstimate(grade)!).toFixed(1)}
                            </strong>
                        </div>}
                        {isIelts && assignment.tasks.length === 2 && (
                            <div className="writing-band-estimate">
                                <span>
                                    Writing band ước tính
                                    <br />
                                    <small>Task 2 có trọng số gấp đôi</small>
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
                                    Nhận xét chung
                                    <textarea
                                        aria-label="Nhận xét chung"
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
                                    Hướng luyện tiếp
                                    <textarea
                                        aria-label="Hướng luyện tiếp"
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
                                        Lý do công bố lại
                                        <input
                                            aria-label="Lý do điều chỉnh"
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
                                        Lưu nháp
                                    </button>
                                    <button
                                        className="primary"
                                        disabled={
                                            busy ||
                                            isIelts && estimate(
                                                value,
                                                assignment.tasks,
                                            ) === null
                                        }
                                        onClick={() => void onPublish(reason)}
                                    >
                                        {published
                                            ? "Công bố bản điều chỉnh"
                                            : "Công bố kết quả"}
                                    </button>
                                </footer>
                            </>
                        ) : (
                            <>
                                <h3>Nhận xét chung</h3>
                                <p>{value.summary || "—"}</p>
                                <h3>Hướng luyện tiếp</h3>
                                <p>{value.recommendations || "—"}</p>
                            </>
                        )}
                    </aside>
                </div>
            )}
        </div>
    );
}
