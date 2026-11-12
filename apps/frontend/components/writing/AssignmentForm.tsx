"use client";
import { useState } from "react";
import { useWritingApi } from "../../lib/writing/context";
import { errorText } from "../../lib/parity";
import {
    catalog,
    type Assignment,
    type Classroom,
    type PromptFile,
} from "../../lib/writing/types";
import ProjectPromptPicker from './ProjectPromptPicker';

export function CommutingChart() {
    return (
        <svg
            viewBox="0 0 420 245"
            role="img"
            aria-label="Biểu đồ luyện tập: Car 60 và 45%, Bus 25 và 30%, Bicycle 15 và 25% năm 2000 và 2020"
            className="writing-chart"
        >
            <text
                x="210"
                y="22"
                textAnchor="middle"
                fontSize="14"
                fill="currentColor"
            >
                Commuting methods in a city (%)
            </text>
            {[0, 20, 40, 60].map((n) => (
                <g key={n}>
                    <path d={`M40 ${200 - n * 2.3}h350`} stroke="#cbd5cf" />
                    <text
                        x="30"
                        y={204 - n * 2.3}
                        textAnchor="end"
                        fontSize="11"
                        fill="currentColor"
                    >
                        {n}
                    </text>
                </g>
            ))}
            {[
                [60, 45],
                [25, 30],
                [15, 25],
            ].map((pair, i) => (
                <g key={i}>
                    <rect
                        x={70 + i * 110}
                        y={200 - pair[0] * 2.3}
                        width="25"
                        height={pair[0] * 2.3}
                        fill="#216e4b"
                    />
                    <rect
                        x={100 + i * 110}
                        y={200 - pair[1] * 2.3}
                        width="25"
                        height={pair[1] * 2.3}
                        fill="#9cbda8"
                    />
                    <text
                        x={98 + i * 110}
                        y="224"
                        fontSize="12"
                        textAnchor="middle"
                        fill="currentColor"
                    >
                        {["Car", "Bus", "Bicycle"][i]}
                    </text>
                </g>
            ))}
            <text x="260" y="47" fill="#216e4b" fontSize="11">
                ■ 2000
            </text>
            <text x="330" y="47" fill="#769d82" fontSize="11">
                ■ 2020
            </text>
        </svg>
    );
}

export default function AssignmentForm({
    classes,
    practice,
    initial,
    onDone,
    onCancel,
}: {
    classes: Classroom[];
    practice: boolean;
    initial?: Assignment;
    onDone: (a: Assignment) => void;
    onCancel: () => void;
}) {
    const { json, request, assetUrl } = useWritingApi();
    const [title, setTitle] = useState(initial?.title || ""),
        [classId, setClassId] = useState(
            initial?.classId || classes[0]?.id || "",
        );
    const [variant, setVariant] = useState<"academic" | "general">(
            initial?.variant || "academic",
        ),
        [mode, setMode] = useState(initial?.tasks.join(",") || "2");
    const [prompts, setPrompts] = useState<Record<string, string>>(
            initial?.prompts || { "1": "", "2": "" },
        ),
        [profiles, setProfiles] = useState<Record<string, string>>(
            initial?.profiles || { "1": "bar", "2": "discussion" },
        );
    const [imageId, setImageId] = useState(initial?.imageId || ""),
        [due, setDue] = useState(""),
        [minutes, setMinutes] = useState(
            initial?.durationMinutes?.toString() || "",
        ),
        [allowLate, setAllowLate] = useState(initial?.allowLate ?? true);
    const [busy, setBusy] = useState(false),
        [error, setError] = useState("");
    const tasks = mode.split(",").map(Number);
    const [promptFiles,setPromptFiles]=useState<Record<string,PromptFile>>(initial?.promptFiles || {});
    const [imports,setImports]=useState<Set<number>>(new Set());
    const [promptGeneration,setPromptGeneration]=useState(0);
    async function save(published: boolean) {
        if(tasks.some(task=>!promptFiles[String(task)])){
            setError('Chọn và lấy tệp đề từ editor cho từng Task trước khi lưu/giao bài.');return;
        }
        if(imports.size){setError('Chờ lấy tệp đề hoàn tất trước khi giao bài.');return;}
        setBusy(true);
        setError("");
        try {
            const a = await json<Assignment>(
                "/writing/assignments" + (initial ? "/" + initial.id : ""),
                {
                    method: initial ? "PUT" : "POST",
                    body: JSON.stringify({
                        title,
                        classId: practice ? null : classId,
                        variant,
                        tasks,
                        prompts,
                        profiles,
                        imageId: imageId || null,
                        promptFiles: Object.fromEntries(tasks.filter(t=>promptFiles[String(t)]).map(t=>[String(t),promptFiles[String(t)].id])),
                        dueAt: due
                            ? new Date(due).toISOString()
                            : initial?.dueAt || null,
                        durationMinutes: minutes ? Number(minutes) : null,
                        allowLate,
                        published,
                        practice,
                        revision: initial?.revision || 0,
                    }),
                },
            );
            onDone(a);
        } catch (e) {
            setError(errorText(e));
        } finally {
            setBusy(false);
        }
    }
    async function upload(file?: File) {
        if (!file) return;
        setBusy(true);
        try {
            const response = await request("/writing/assets", {
                method: "POST",
                headers: { "Content-Type": file.type },
                body: file,
            });
            setImageId((await response.json()).id);
        } catch (e) {
            setError(errorText(e));
        } finally {
            setBusy(false);
        }
    }
    function sample() {
        setPromptFiles({});
        setPromptGeneration(v=>v+1);
        setTitle(title || "IELTS Writing · Luyện tập");
        setPrompts({
            "1":
                variant === "academic"
                    ? "The chart shows the percentages of commuters using three forms of transport in a city in 2000 and 2020. Summarise the information by selecting and reporting the main features, and make comparisons where relevant."
                    : "You recently attended a course at a local college. You enjoyed the teaching, but there were problems with the facilities. Write to the course organiser: explain what you enjoyed, describe the problems and suggest improvements.",
            "2": "Some people believe that improving public transport is the best way to reduce traffic congestion. Others think that building more roads is more effective. Discuss both views and give your own opinion.",
        });
        if (variant === "academic" && tasks.includes(1))
            setImageId("sample-commuting");
    }
    return (
        <form
            className="writing-form writing-card"
            onSubmit={(e) => {
                e.preventDefault();
                void save(true);
            }}
        >
            <div className="writing-form-heading">
                <div>
                    <span className="writing-eyebrow">
                        {practice ? "KHÔNG GIAN LUYỆN TẬP" : "GIAO BÀI CHO LỚP"}
                    </span>
                    <h2>
                        {practice
                            ? "Bắt đầu một bài viết"
                            : "Tạo bài tập IELTS"}
                    </h2>
                </div>

            </div>
            <p className="writing-muted">
                Đề mẫu do nhóm tự biên soạn; không phải đề thi IELTS chính thức.
            </p>
            <label>
                Tên bài
                <input
                    required
                    aria-label="Tên bài IELTS"
                    value={title}
                    onChange={(e) => setTitle(e.target.value)}
                    maxLength={160}
                />
            </label>
            {!practice && (
                <label>
                    Lớp học
                    <select
                        aria-label="Lớp được giao"
                        required
                        value={classId}
                        onChange={(e) => setClassId(e.target.value)}
                    >
                        <option value="">Chọn lớp</option>
                        {classes.map((c) => (
                            <option key={c.id} value={c.id}>
                                {c.title}
                            </option>
                        ))}
                    </select>
                </label>
            )}
            <div className="writing-form-grid">
                <label>
                    Loại bài
                    <select
                        aria-label="Loại bài IELTS"
                        value={variant}
                        onChange={(e) => {
                            const v = e.target.value as "academic" | "general";
                            setVariant(v);
                            setProfiles((p) => ({
                                ...p,
                                "1": v === "academic" ? "bar" : "formal",
                            }));
                            setImageId("");
                        }}
                    >
                        <option value="academic">Academic</option>
                        <option value="general">General Training</option>
                    </select>
                </label>
                <label>
                    Phạm vi
                    <select
                        aria-label="Task được giao"
                        value={mode}
                        onChange={(e) => setMode(e.target.value)}
                    >
                        <option value="1">Task 1</option>
                        <option value="2">Task 2</option>
                        <option value="1,2">Cả hai task</option>
                    </select>
                </label>
            </div>
            {tasks.map((task) => (
                <fieldset key={task}>
                    <legend>
                        Task {task} · ít nhất {task === 1 ? 150 : 250} từ
                    </legend>
                    <label>
                        Dạng bài
                        <select
                            aria-label={`Dạng Task ${task}`}
                            value={profiles[String(task)]}
                            onChange={(e) =>
                                setProfiles({
                                    ...profiles,
                                    [task]: e.target.value,
                                })
                            }
                        >
                            {Object.entries(catalog)
                                .filter(
                                    ([, p]) =>
                                        p.task === task &&
                                        (task === 2 || p.family === variant),
                                )
                                .map(([id, p]) => (
                                    <option key={id} value={id}>
                                        {p.label}
                                    </option>
                                ))}
                        </select>
                    </label>
                    {<ProjectPromptPicker key={`${task}:${promptGeneration}`} task={task} value={promptFiles[String(task)]} onBusyChange={pending=>setImports(old=>{
                        const next=new Set(old); if(pending)next.add(task);else next.delete(task);return next;
                    })} onChange={file=>{
                        setPromptFiles(old=>{const next={...old};if(file)next[String(task)]=file;else delete next[String(task)];return next;});
                        if(file)setPrompts(old=>({...old,[task]:file.kind==='text' ? file.text || '' : ''}));
                        if(file && task===1)setImageId('');
                    }}/>}
                </fieldset>
            ))}
            <div className="writing-form-grid">
                <label>
                    Thời lượng (phút, không bắt buộc)
                    <input
                        type="number"
                        min="1"
                        max="180"
                        aria-label="Thời lượng"
                        value={minutes}
                        onChange={(e) => setMinutes(e.target.value)}
                    />
                </label>
                {!practice && (
                    <label>
                        Hạn nộp
                        <input
                            type="datetime-local"
                            aria-label="Hạn nộp"
                            value={due}
                            onChange={(e) => setDue(e.target.value)}
                        />
                    </label>
                )}
            </div>
            <label className="writing-inline-check">
                <input
                    type="checkbox"
                    checked={allowLate}
                    onChange={(e) => setAllowLate(e.target.checked)}
                />
                Nhận bài quá hạn và ghi nhận trạng thái nộp muộn
            </label>
            {error && (
                <p className="writing-error" role="alert">
                    {error}
                </p>
            )}
            <footer>
                <button type="button" onClick={onCancel} disabled={busy}>
                    Quay lại
                </button>
                {!practice && (
                    <button
                        type="button"
                        onClick={() => void save(false)}
                        disabled={busy}
                    >
                        Lưu đề nháp
                    </button>
                )}
                <button type="submit" className="primary" disabled={busy || imports.size > 0}>
                    {busy
                        ? "Đang lưu…"
                        : practice
                          ? "Bắt đầu viết"
                          : "Giao bài"}
                </button>
            </footer>
        </form>
    );
}
