import rawCatalog from "./catalog.json";
export type Check = { id: string; text: string; codes: string[] };
export type Profile = {
    task: number;
    family: string;
    label: string;
    focus: string[];
    checks: Record<string, Check[]>;
};
export const catalog: Record<string, Profile> = rawCatalog;
export type Classroom = {
    id: string;
    title: string;
    tutorId: string;
    tutorName: string;
    joinCode?: string;
    studentCount: number;
    students?: { id: string; name: string }[];
};
export type Assignment = {
    id: string;
    title: string;
    classId: string | null;
    tutorId: string | null;
    tutorName: string;
    practiceOwner: string | null;
    variant: "academic" | "general";
    tasks: number[];
    prompts: Record<string, string>;
    profiles: Record<string, string>;
    imageId?: string | null;
    dueAt?: string | null;
    durationMinutes?: number | null;
    allowLate: boolean;
    published: boolean;
    revision: number;
    createdAt: string;
};
export type Draft = {
    revision: number;
    answers: Record<string, string>;
    latexSources: Record<string, string>;
    startedAt: string;
    deadline: string | null;
    serverNow: string;
    attempt: number;
};
export type Section = {
    paragraph: number;
    kind: string;
    title: string;
    checks: Record<string, string>;
    note: string;
    quote: string;
};
export type Grade = {
    profile: string;
    scores: Record<string, number | null>;
    notes: Record<string, string>;
    overall: string;
    sections: Section[];
};
export type Review = {
    revision: number;
    tasks: Record<string, Grade>;
    summary: string;
    recommendations: string;
    publishedAt?: string;
    publishedVersion?: number;
    taskBands?: Record<string, number>;
    writingBand?: number | null;
    tutorName?: string;
};
export type Submission = {
    id: string;
    assignmentId: string;
    assignment: Assignment;
    studentId: string;
    studentName: string;
    tutorId: string | null;
    attempt: number;
    submittedAt: string;
    late: boolean;
    wordCounts: Record<string, number>;
    answers?: Record<string, string>;
    latexSources?: Record<string, string>;
    result?: Review | null;
};
export type SubmissionView = {
    submission: Submission;
    review: Review | null;
    published: Review | null;
};
export type Overview = {
    legacy?: {classes: number; assignments: number};
    permission?: "owner" | "editor" | "viewer";
    classes: Classroom[];
    assignments: Assignment[];
    submissions: Submission[];
    role: string;
};
export const codes = (task: number) => [
    task === 1 ? "TA" : "TR",
    "CC",
    "LR",
    "GRA",
];
export const criteria: Record<string, { title: string; hint: string }> = {
    TA: {
        title: "Task Achievement",
        hint: "Đáp ứng đề; lựa chọn thông tin chính, overview hoặc mục đích thư và giọng văn.",
    },
    TR: {
        title: "Task Response",
        hint: "Trả lời đủ yêu cầu, lập trường rõ khi đề hỏi và phát triển ý có căn cứ.",
    },
    CC: {
        title: "Coherence & Cohesion",
        hint: "Tổ chức, tiến triển đoạn và liên kết ý phù hợp.",
    },
    LR: {
        title: "Lexical Resource",
        hint: "Phạm vi, độ chính xác và sự phù hợp của từ vựng.",
    },
    GRA: {
        title: "Grammatical Range & Accuracy",
        hint: "Phạm vi cấu trúc câu và độ chính xác ngữ pháp.",
    },
};
export const countWords = (value: string) =>
    (
        value
            .normalize("NFC")
            .match(/[\p{L}\p{N}]+(?:['’\-][\p{L}\p{N}]+)*/gu) || []
    ).length;
export const paragraphs = (value: string) =>
    value
        .split(/\n\s*\n/)
        .map((p) => p.trim())
        .filter(Boolean);
export const rounded = (value: number) => Math.round(value * 2) / 2;
export function taskEstimate(grade?: Grade) {
    const values = grade ? Object.values(grade.scores) : [];
    return values.length === 4 && values.every((v) => v !== null)
        ? values.reduce<number>((sum, v) => sum + (v || 0), 0) / 4
        : null;
}
export function estimate(review: Review, tasks: number[]) {
    const values = tasks.map((t) => taskEstimate(review.tasks[String(t)]));
    if (values.some((v) => v === null)) return null;
    return rounded(
        tasks.length === 2 ? (values[0]! + 2 * values[1]!) / 3 : values[0]!,
    );
}
export function blankGrade(task: number, assignment: Assignment): Grade {
    return {
        profile: assignment.profiles[String(task)],
        scores: Object.fromEntries(codes(task).map((code) => [code, null])),
        notes: {},
        overall: "",
        sections: [],
    };
}
export function defaultSection(
    task: number,
    assignment: Assignment,
    index: number,
    length: number,
): Section {
    const letter = task === 1 && assignment.variant === "general";
    const kind =
        index === 0
            ? "intro"
            : task === 1 && !letter && index === 1 && length >= 4
              ? "overview"
              : index === length - 1 && length >= 4
                ? letter
                    ? "closing"
                    : task === 2
                      ? "conclusion"
                      : "body"
                : "body";
    return {
        paragraph: index,
        kind,
        title: (
            {
                intro: letter ? "Opening / mục đích thư" : "Introduction",
                overview: "Overview",
                conclusion: "Conclusion",
                closing: "Closing",
                body: `Body ${Math.max(1, index - (task === 1 && !letter && length >= 4 ? 1 : 0))}`,
            } as Record<string, string>
        )[kind],
        checks: {},
        note: "",
        quote: "",
    };
}
