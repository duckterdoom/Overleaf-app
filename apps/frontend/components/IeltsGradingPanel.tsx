"use client";

import React, { useState, useEffect, useMemo } from "react";
import {
    GraduationCap,
    Award,
    CheckCircle2,
    Sliders,
    Save,
    FileSpreadsheet,
    Printer,
    UserCheck,
    BookOpen,
    Sparkles,
    ChevronDown,
    ChevronRight,
    X,
    FileText,
    AlertCircle,
    Copy,
    Check,
} from "lucide-react";
import type { Project } from "../lib/api";
import { json, request } from "../lib/api";

export function roundIeltsBand(score: number): number {
    const frac = score - Math.floor(score);
    if (frac < 0.25) return Math.floor(score);
    if (frac < 0.75) return Math.floor(score) + 0.5;
    return Math.floor(score) + 1.0;
}

export const BAND_OPTIONS = [
    9.0, 8.5, 8.0, 7.5, 7.0, 6.5, 6.0, 5.5, 5.0, 4.5, 4.0, 3.5, 3.0, 2.0, 1.0, 0.0,
];

// Official IELTS Band Descriptors from Image 1 (Task 1)
export const IELTS_TASK1_DESCRIPTORS: Record<string, Record<number, string[]>> = {
    TA: {
        9: [
            "Fully satisfies all the requirements of the task",
            "Clearly presents a fully developed response",
        ],
        8: [
            "Covers all requirements of the task sufficiently",
            "Presents, highlights and illustrates key features/bullet points clearly and appropriately",
        ],
        7: [
            "Covers the requirements of the task",
            "Presents a clear overview of main trends, differences or stages",
            "Clearly presents and highlights key features/bullet points but could be more fully extended",
        ],
        6: [
            "Addresses the requirements of the task",
            "Presents an overview with information appropriately selected",
            "Presents and adequately highlights key features/bullet points but details may be irrelevant, inappropriate or inaccurate",
        ],
        5: [
            "Generally addresses the task; the format may be inappropriate in places",
            "Recounts detail mechanically with no clear overview; there may be no data to support the description",
            "Presents, but inadequately covers, key features/bullet points; tendency to focus on details",
        ],
        4: [
            "Attempts to address the task but does not cover all key features/bullet points",
            "Format may be inappropriate; may confuse key features with detail; parts may be unclear or inaccurate",
        ],
        3: [
            "Fails to address the task, which may have been misunderstood",
            "Presents limited ideas which may be largely irrelevant/repetitive",
        ],
        2: ["Answer is barely related to the task"],
        1: ["Answer is completely unrelated to the task"],
        0: ["Does not attend or attempt the task in any way"],
    },
    CC: {
        9: [
            "Uses cohesion in such a way that it attracts no attention",
            "Skilfully manages paragraphing",
        ],
        8: [
            "Sequences information and ideas logically",
            "Manages all aspects of cohesion well",
            "Uses paragraphing sufficiently and appropriately",
        ],
        7: [
            "Logically organises information and ideas; clear progression throughout",
            "Uses a range of cohesive devices appropriately although there may be some under-/over-use",
        ],
        6: [
            "Arranges information and ideas coherently; clear overall progression",
            "Uses cohesive devices effectively, but cohesion within/between sentences may be faulty or mechanical",
            "May not always use referencing clearly or appropriately",
        ],
        5: [
            "Presents information with some organisation but may lack overall progression",
            "Makes inadequate, inaccurate or over-use of cohesive devices",
            "May be repetitive because of lack of referencing and substitution",
        ],
        4: [
            "Presents information and ideas but not arranged coherently; no clear progression",
            "Uses some basic cohesive devices but these may be inaccurate or repetitive",
        ],
        3: [
            "Does not organise ideas logically",
            "May use a very limited range of cohesive devices without logical relationships",
        ],
        2: ["Has very little control of organisational features"],
        1: ["Fails to communicate any message"],
        0: ["Does not attend or attempt the task in any way"],
    },
    LR: {
        9: [
            "Uses a wide range of vocabulary with very natural and sophisticated control",
            "Rare minor errors occur only as 'slips'",
        ],
        8: [
            "Uses a wide range of vocabulary fluently and flexibly to convey precise meanings",
            "Skilfully uses uncommon lexical items with occasional minor inaccuracies in word choice/collocation",
            "Produces rare errors in spelling and/or word formation",
        ],
        7: [
            "Uses a sufficient range of vocabulary to allow some flexibility and precision",
            "Uses less common lexical items with some awareness of style and collocation",
            "May produce occasional errors in word choice, spelling and/or word formation",
        ],
        6: [
            "Uses an adequate range of vocabulary for the task",
            "Attempts to use less common vocabulary but with some inaccuracy",
            "Makes some errors in spelling/word formation, but they do not impede communication",
        ],
        5: [
            "Uses a limited range of vocabulary, minimally adequate for the task",
            "Noticeable errors in spelling and/or word formation that may cause some difficulty",
        ],
        4: [
            "Uses only basic vocabulary repetitively or inappropriately",
            "Limited control of word formation/spelling; errors may cause strain",
        ],
        3: [
            "Very limited range of words and expressions; errors severely distort message",
        ],
        2: [
            "Extremely limited range of vocabulary; essentially no control of word formation/spelling",
        ],
        1: ["Can only use a few isolated words"],
        0: ["Does not attend or attempt the task in any way"],
    },
    GRA: {
        9: [
            "Uses a wide range of structures with full flexibility and accuracy",
            "Rare minor errors occur only as 'slips'",
        ],
        8: [
            "Uses a wide range of structures; majority of sentences are error-free",
            "Makes only very occasional errors or inappropriacies",
        ],
        7: [
            "Uses a variety of complex structures",
            "Produces frequent error-free sentences",
            "Good control of grammar and punctuation but may make a few errors",
        ],
        6: [
            "Uses a mix of simple and complex sentence forms",
            "Makes some errors in grammar and punctuation but they rarely reduce communication",
        ],
        5: [
            "Uses only a limited range of structures",
            "Attempts complex sentences but these tend to be less accurate than simple sentences",
            "Frequent grammatical errors and faulty punctuation causing some difficulty",
        ],
        4: [
            "Very limited range of structures with rare subordinate clauses",
            "Errors predominate, and punctuation is often faulty",
        ],
        3: ["Errors in grammar and punctuation predominate and distort the meaning"],
        2: ["Cannot use sentence forms except in memorised phrases"],
        1: ["Cannot use sentence forms at all"],
        0: ["Does not attend or attempt the task in any way"],
    },
};

// Official IELTS Band Descriptors from Image 2 (Task 2)
export const IELTS_TASK2_DESCRIPTORS: Record<string, Record<number, string[]>> = {
    TR: {
        9: [
            "Fully addresses all parts of the task",
            "Presents a fully developed position with relevant, fully extended and well supported ideas",
        ],
        8: [
            "Sufficiently addresses all parts of the task",
            "Presents a well-developed response with relevant, extended and supported ideas",
        ],
        7: [
            "Addresses all parts of the task",
            "Presents a clear position throughout the response",
            "Presents, extends and supports main ideas (may have minor over-generalisations)",
        ],
        6: [
            "Addresses all parts of the task although some parts may be more fully covered than others",
            "Presents a relevant position although conclusions may become unclear or repetitive",
            "Presents relevant main ideas but some may be inadequately developed/unclear",
        ],
        5: [
            "Addresses the task only partially; format may be inappropriate",
            "Expresses a position but development is not always clear; no conclusions drawn",
            "Presents some main ideas but they are limited and not sufficiently developed",
        ],
        4: [
            "Responds to the task only in a minimal way or answer is tangential",
            "Presents an unclear position with difficult-to-identify ideas",
        ],
        3: [
            "Does not adequately address any part of the task",
            "Does not express a clear position; few undeveloped or irrelevant ideas",
        ],
        2: ["Barely responds to the task; does not express a position"],
        1: ["Answer is completely unrelated to the task"],
        0: ["Does not attend or attempt the task in any way"],
    },
    CC: IELTS_TASK1_DESCRIPTORS.CC,
    LR: IELTS_TASK1_DESCRIPTORS.LR,
    GRA: IELTS_TASK1_DESCRIPTORS.GRA,
};

export interface IeltsEvaluationData {
    studentName: string;
    tutorName: string;
    examDate?: string;
    task1: {
        title: string;
        scores: {
            TA: number;
            CC: number;
            LR: number;
            GRA: number;
        };
        feedback: {
            TA: string;
            CC: string;
            LR: string;
            GRA: string;
        };
        overallFeedback: string;
    };
    task2: {
        title: string;
        scores: {
            TR: number;
            CC: number;
            LR: number;
            GRA: number;
        };
        feedback: {
            TR: string;
            CC: string;
            LR: string;
            GRA: string;
        };
        overallFeedback: string;
    };
    generalAssessment: string;
    recommendations: string;
    updatedAt?: string;
}

const DEFAULT_STATE: IeltsEvaluationData = {
    studentName: "IELTS Candidate",
    tutorName: "IELTS Tutor",
    examDate: new Date().toISOString().split("T")[0],
    task1: {
        title: "Task 1: Academic / General Report",
        scores: { TA: 6.5, CC: 6.5, LR: 6.5, GRA: 6.0 },
        feedback: {
            TA: "Well-structured overview. Ensure key statistics are compared accurately.",
            CC: "Good paragraph division and cohesive devices.",
            LR: "Flexible range of vocabulary with good academic collocations.",
            GRA: "Mix of complex sentences, watch out for minor article slips.",
        },
        overallFeedback: "Solid performance in Task 1.",
    },
    task2: {
        title: "Task 2: Discursive Essay",
        scores: { TR: 7.0, CC: 7.0, LR: 6.5, GRA: 6.5 },
        feedback: {
            TR: "Addresses all parts of prompt with a clear position throughout.",
            CC: "Logical progression of ideas with clear topic sentences.",
            LR: "Good thematic vocabulary and idiom awareness.",
            GRA: "Variety of complex structures with frequent error-free clauses.",
        },
        overallFeedback: "Strong arguments and convincing reasoning.",
    },
    generalAssessment:
        "The candidate demonstrates strong command of written English. Focus on minor grammatical accuracy and more varied linking devices to reach Band 7.5+.",
    recommendations:
        "1. Practice timed writing for Task 1 (under 20 minutes).\n2. Expand lexical resource around abstract topics.\n3. Proofread for subject-verb agreement and preposition slips.",
};

export interface IeltsGradingPanelProps {
    project: Project;
    file: string;
    source: string;
    language: string;
    editor?: React.RefObject<any>;
    onClose: () => void;
    onFileCreated?: (path: string) => Promise<void>;
}

export default function IeltsGradingPanel({
    project,
    file,
    source,
    language,
    editor,
    onClose,
    onFileCreated,
}: IeltsGradingPanelProps) {
    const isEn = language === "en";
    const [mode, setMode] = useState<"tutor" | "student">("tutor");
    const [activeTab, setActiveTab] = useState<"task1" | "task2" | "summary">(
        "task1",
    );
    const [data, setData] = useState<IeltsEvaluationData>(DEFAULT_STATE);
    const [loading, setLoading] = useState(true);
    const [saving, setSaving] = useState(false);
    const [generating, setGenerating] = useState(false);
    const [saveStatus, setSaveStatus] = useState<string | null>(null);
    const [expandedDescriptors, setExpandedDescriptors] = useState<
        Record<string, boolean>
    >({});

    // Live word count from current document source
    const wordCount = useMemo(() => {
        if (!source) return 0;
        // Clean comments and LaTeX commands for approximate word counting
        const clean = source
            .replace(/%.*$/gm, "")
            .replace(/\\[a-zA-Z]+(\[[^\]]*\])?(\{[^}]*\})?/g, " ")
            .replace(/[{}$_#&^~]/g, " ")
            .trim();
        const words = clean.match(/\b[A-Za-z0-9'-]+\b/g);
        return words ? words.length : 0;
    }, [source]);

    // Load saved IELTS data from backend
    useEffect(() => {
        let isMounted = true;
        async function loadData() {
            setLoading(true);
            try {
                const res = await json<IeltsEvaluationData>(
                    `/projects/${project.id}/ielts`,
                );
                if (isMounted && res && res.task1 && res.task2) {
                    setData(res);
                }
            } catch {
                // Keep default state
            } finally {
                if (isMounted) setLoading(false);
            }
        }
        void loadData();
        return () => {
            isMounted = false;
        };
    }, [project.id]);

    // Calculations
    const task1Band = useMemo(() => {
        const { TA, CC, LR, GRA } = data.task1.scores;
        return roundIeltsBand((TA + CC + LR + GRA) / 4);
    }, [data.task1.scores]);

    const task2Band = useMemo(() => {
        const { TR, CC, LR, GRA } = data.task2.scores;
        return roundIeltsBand((TR + CC + LR + GRA) / 4);
    }, [data.task2.scores]);

    const overallBand = useMemo(() => {
        // Task 2 has double the weight of Task 1
        const weighted = (task1Band * 1 + task2Band * 2) / 3;
        return roundIeltsBand(weighted);
    }, [task1Band, task2Band]);

    const handleScoreChange = (
        task: "task1" | "task2",
        criterion: string,
        val: number,
    ) => {
        if (mode !== "tutor") return;
        setData((prev) => ({
            ...prev,
            [task]: {
                ...prev[task],
                scores: {
                    ...prev[task].scores,
                    [criterion]: val,
                },
            },
        }));
    };

    const handleFeedbackChange = (
        task: "task1" | "task2",
        criterion: string,
        text: string,
    ) => {
        if (mode !== "tutor") return;
        setData((prev) => ({
            ...prev,
            [task]: {
                ...prev[task],
                feedback: {
                    ...prev[task].feedback,
                    [criterion]: text,
                },
            },
        }));
    };

    const handleSave = async () => {
        setSaving(true);
        setSaveStatus(null);
        try {
            await request(`/projects/${project.id}/ielts`, {
                method: "POST",
                body: JSON.stringify(data),
            });
            setSaveStatus(
                isEn ? "Evaluation saved successfully!" : "Đã lưu kết quả chấm điểm!",
            );
            setTimeout(() => setSaveStatus(null), 3000);
        } catch (err) {
            setSaveStatus(
                isEn ? "Failed to save evaluation." : "Lưu đánh giá thất bại.",
            );
        } finally {
            setSaving(false);
        }
    };

    const handleGenerateReport = async () => {
        setGenerating(true);
        setSaveStatus(null);
        try {
            const res = await json<{ success: boolean; file: string }>(
                `/projects/${project.id}/ielts/report`,
                {
                    method: "POST",
                    body: JSON.stringify(data),
                },
            );
            if (res.success && res.file) {
                setSaveStatus(
                    isEn
                        ? `Report generated: ${res.file}`
                        : `Đã tạo báo cáo: ${res.file}`,
                );
                if (onFileCreated) {
                    await onFileCreated(res.file);
                }
            }
        } catch (err) {
            setSaveStatus(
                isEn ? "Failed to generate report." : "Không thể tạo báo cáo.",
            );
        } finally {
            setGenerating(false);
        }
    };

    const toggleDescriptor = (key: string) => {
        setExpandedDescriptors((prev) => ({
            ...prev,
            [key]: !prev[key],
        }));
    };

    const getBandBadgeColor = (band: number) => {
        if (band >= 8.0) return "badge-gold";
        if (band >= 7.0) return "badge-emerald";
        if (band >= 6.0) return "badge-blue";
        if (band >= 5.0) return "badge-amber";
        return "badge-rose";
    };

    const task1Criteria = [
        {
            key: "TA",
            name: isEn ? "Task Achievement (TA)" : "Task Achievement (Độ hoàn thành)",
            desc: isEn
                ? "Covers all key features, provides overview, details accurate"
                : "Nêu đủ đặc điểm chính, có cái nhìn tổng quan, số liệu chuẩn xác",
            descriptors: IELTS_TASK1_DESCRIPTORS.TA,
        },
        {
            key: "CC",
            name: isEn
                ? "Coherence & Cohesion (CC)"
                : "Coherence & Cohesion (Tính mạch lạc)",
            desc: isEn
                ? "Logical organisation, paragraphing, cohesive devices"
                : "Bố cục logic, phân đoạn hợp lý, liên kết câu từ tự nhiên",
            descriptors: IELTS_TASK1_DESCRIPTORS.CC,
        },
        {
            key: "LR",
            name: isEn ? "Lexical Resource (LR)" : "Lexical Resource (Từ vựng)",
            desc: isEn
                ? "Range of vocabulary, collocations, precision, spelling"
                : "Độ đa dạng từ vựng, kết hợp từ (collocations), độ chuẩn xác chính tả",
            descriptors: IELTS_TASK1_DESCRIPTORS.LR,
        },
        {
            key: "GRA",
            name: isEn
                ? "Grammatical Range & Accuracy (GRA)"
                : "Grammar & Accuracy (Ngữ pháp)",
            desc: isEn
                ? "Variety of complex structures, error-free sentences, punctuation"
                : "Đa dạng cấu trúc câu phức, tỷ lệ câu không lỗi, dấu câu",
            descriptors: IELTS_TASK1_DESCRIPTORS.GRA,
        },
    ];

    const task2Criteria = [
        {
            key: "TR",
            name: isEn ? "Task Response (TR)" : "Task Response (Trả lời câu hỏi)",
            desc: isEn
                ? "Addresses all parts of prompt, clear position, supported ideas"
                : "Trả lời đầy đủ các phần câu hỏi, lập trường rõ ràng, phát triển ý",
            descriptors: IELTS_TASK2_DESCRIPTORS.TR,
        },
        {
            key: "CC",
            name: isEn
                ? "Coherence & Cohesion (CC)"
                : "Coherence & Cohesion (Tính mạch lạc)",
            desc: isEn
                ? "Logical progression, topic sentences, linking devices"
                : "Luận điểm phát triển logic, câu chủ đề rõ ràng, từ nối linh hoạt",
            descriptors: IELTS_TASK2_DESCRIPTORS.CC,
        },
        {
            key: "LR",
            name: isEn ? "Lexical Resource (LR)" : "Lexical Resource (Từ vựng)",
            desc: isEn
                ? "Advanced academic vocabulary, style, natural collocations"
                : "Vốn từ học thuật phong phú, ngữ phong tự nhiên, ít lỗi diễn đạt",
            descriptors: IELTS_TASK2_DESCRIPTORS.LR,
        },
        {
            key: "GRA",
            name: isEn
                ? "Grammatical Range & Accuracy (GRA)"
                : "Grammar & Accuracy (Ngữ pháp)",
            desc: isEn
                ? "Complex clauses, flexibility, accuracy, punctuation"
                : "Cấu trúc mệnh đề phức, độ chuẩn xác ngữ pháp, chấm phẩy",
            descriptors: IELTS_TASK2_DESCRIPTORS.GRA,
        },
    ];

    return (
        <div className="ielts-panel">
            {/* Header */}
            <header className="ielts-header">
                <div className="ielts-header-title">
                    <GraduationCap size={22} className="text-emerald" />
                    <div>
                        <strong>{isEn ? "IELTS Writing Evaluation" : "Chấm điểm IELTS Writing"}</strong>
                        <div className="ielts-sub">
                            {isEn
                                ? "Public Band Descriptors & Diagnostics"
                                : "Tiêu chí chấm điểm & Nhận xét của Tutor"}
                        </div>
                    </div>
                </div>
                <div className="ielts-header-actions">
                    <button
                        className={`mode-toggle-btn ${mode === "tutor" ? "tutor-active" : ""}`}
                        title={
                            mode === "tutor"
                                ? isEn
                                    ? "Switch to Student View"
                                    : "Chuyển sang chế độ Học viên"
                                : isEn
                                  ? "Switch to Tutor View"
                                  : "Chuyển sang chế độ Giáo viên"
                        }
                        onClick={() =>
                            setMode((m) => (m === "tutor" ? "student" : "tutor"))
                        }
                    >
                        <UserCheck size={14} />
                        <span>{mode === "tutor" ? "Tutor Mode" : "Student View"}</span>
                    </button>
                    <button
                        className="ielts-close-btn"
                        onClick={onClose}
                        title={isEn ? "Close panel" : "Đóng bảng chấm điểm"}
                    >
                        <X size={16} />
                    </button>
                </div>
            </header>

            {/* Live word count indicator */}
            <div className="ielts-meta-bar">
                <div className="ielts-candidate-input">
                    <label>{isEn ? "Candidate:" : "Học viên:"}</label>
                    {mode === "tutor" ? (
                        <input
                            type="text"
                            value={data.studentName}
                            onChange={(e) =>
                                setData((d) => ({
                                    ...d,
                                    studentName: e.target.value,
                                }))
                            }
                            placeholder="Tên học viên"
                        />
                    ) : (
                        <span>{data.studentName}</span>
                    )}
                </div>
                <div className="ielts-word-counter">
                    <FileText size={13} />
                    <span>
                        {isEn ? "Current Doc:" : "Số từ:"}{" "}
                        <strong>{wordCount}</strong>{" "}
                        {activeTab === "task1"
                            ? "/ 150+ words"
                            : activeTab === "task2"
                              ? "/ 250+ words"
                              : "words"}
                    </span>
                    {activeTab === "task1" && (
                        <span
                            className={`wc-pill ${wordCount >= 150 ? "wc-pass" : "wc-warn"}`}
                        >
                            {wordCount >= 150 ? "✓ Đạt độ dài" : "Thiếu từ"}
                        </span>
                    )}
                    {activeTab === "task2" && (
                        <span
                            className={`wc-pill ${wordCount >= 250 ? "wc-pass" : "wc-warn"}`}
                        >
                            {wordCount >= 250 ? "✓ Đạt độ dài" : "Thiếu từ"}
                        </span>
                    )}
                </div>
            </div>

            {/* Overall Score Banner (Architecture Image 3) */}
            <div className="ielts-score-banner">
                <div className="banner-item">
                    <span className="banner-label">Task 1 (1/3)</span>
                    <strong className="banner-val">{task1Band.toFixed(1)}</strong>
                </div>
                <div className="banner-divider" />
                <div className="banner-item">
                    <span className="banner-label">Task 2 (2/3)</span>
                    <strong className="banner-val">{task2Band.toFixed(1)}</strong>
                </div>
                <div className="banner-divider" />
                <div className="banner-item overall-item">
                    <span className="banner-label">OVERALL WRITING</span>
                    <div className={`overall-badge ${getBandBadgeColor(overallBand)}`}>
                        <Award size={18} />
                        <span>Band {overallBand.toFixed(1)}</span>
                    </div>
                </div>
            </div>

            {/* Tabs for Task 1, Task 2, and Summary */}
            <div className="ielts-tabs" role="tablist">
                <button
                    role="tab"
                    aria-selected={activeTab === "task1"}
                    className={`ielts-tab ${activeTab === "task1" ? "active" : ""}`}
                    onClick={() => setActiveTab("task1")}
                >
                    <span>Task 1 (Report)</span>
                    <span className="tab-band-pill">{task1Band.toFixed(1)}</span>
                </button>
                <button
                    role="tab"
                    aria-selected={activeTab === "task2"}
                    className={`ielts-tab ${activeTab === "task2" ? "active" : ""}`}
                    onClick={() => setActiveTab("task2")}
                >
                    <span>Task 2 (Essay)</span>
                    <span className="tab-band-pill">{task2Band.toFixed(1)}</span>
                </button>
                <button
                    role="tab"
                    aria-selected={activeTab === "summary"}
                    className={`ielts-tab ${activeTab === "summary" ? "active" : ""}`}
                    onClick={() => setActiveTab("summary")}
                >
                    <span>{isEn ? "Summary & Report" : "Tổng quan & Xuất PDF"}</span>
                </button>
            </div>

            {/* Main Content Area */}
            <div className="ielts-content">
                {/* TASK 1 PANEL */}
                {activeTab === "task1" && (
                    <div className="task-section">
                        <div className="task-header-info">
                            <h4>{isEn ? "IELTS Writing Task 1 Assessment" : "Đánh giá IELTS Writing Task 1"}</h4>
                            <p>
                                {isEn
                                    ? "Minimum 150 words. Graded on Task Achievement, Coherence & Cohesion, Lexical Resource, and Grammar."
                                    : "Báo cáo tối thiểu 150 từ (Biểu đồ, bản đồ, quy trình). Đánh giá theo 4 tiêu chí chuẩn quốc tế."}
                            </p>
                        </div>

                        {task1Criteria.map((c) => {
                            const score =
                                data.task1.scores[
                                    c.key as keyof typeof data.task1.scores
                                ];
                            const wholeBand = Math.floor(score);
                            const descriptorBullets =
                                c.descriptors[wholeBand] ||
                                c.descriptors[6] ||
                                [];
                            const isDescOpen =
                                expandedDescriptors[`t1-${c.key}`] ?? true;

                            return (
                                <div key={c.key} className="criteria-card">
                                    <div className="criteria-header">
                                        <div className="criteria-title-wrap">
                                            <strong>{c.name}</strong>
                                            <span className="criteria-desc">{c.desc}</span>
                                        </div>
                                        <div className="criteria-score-box">
                                            <span className="score-val">
                                                {score.toFixed(1)}
                                            </span>
                                        </div>
                                    </div>

                                    {/* Score Selector for Tutor */}
                                    {mode === "tutor" && (
                                        <div className="score-selector-row">
                                            <span className="selector-label">
                                                {isEn ? "Set Band:" : "Chấm điểm:"}
                                            </span>
                                            <div className="band-chips">
                                                {BAND_OPTIONS.slice(0, 12).map(
                                                    (b) => (
                                                        <button
                                                            key={b}
                                                            type="button"
                                                            className={`band-chip ${score === b ? "active" : ""}`}
                                                            onClick={() =>
                                                                handleScoreChange(
                                                                    "task1",
                                                                    c.key,
                                                                    b,
                                                                )
                                                            }
                                                        >
                                                            {b.toFixed(1)}
                                                        </button>
                                                    ),
                                                )}
                                            </div>
                                        </div>
                                    )}

                                    {/* Official Band Descriptors Box matching Image 1 */}
                                    <div className="descriptors-box">
                                        <div
                                            className="descriptors-header"
                                            onClick={() =>
                                                toggleDescriptor(`t1-${c.key}`)
                                            }
                                        >
                                            <div className="desc-title-row">
                                                <BookOpen size={13} />
                                                <span>
                                                    Band {wholeBand} Descriptor
                                                    (Official)
                                                </span>
                                            </div>
                                            {isDescOpen ? (
                                                <ChevronDown size={14} />
                                            ) : (
                                                <ChevronRight size={14} />
                                            )}
                                        </div>
                                        {isDescOpen && (
                                            <ul className="descriptors-list">
                                                {descriptorBullets.map(
                                                    (bullet, bIdx) => (
                                                        <li key={bIdx}>
                                                            {bullet}
                                                        </li>
                                                    ),
                                                )}
                                            </ul>
                                        )}
                                    </div>

                                    {/* Tutor Feedback Field */}
                                    <div className="feedback-field">
                                        <label>
                                            {isEn
                                                ? "Tutor Feedback & Suggestions:"
                                                : "Lời phê của Tutor cho tiêu chí này:"}
                                        </label>
                                        {mode === "tutor" ? (
                                            <textarea
                                                rows={2}
                                                value={
                                                    data.task1.feedback[
                                                        c.key as keyof typeof data.task1.feedback
                                                    ]
                                                }
                                                onChange={(e) =>
                                                    handleFeedbackChange(
                                                        "task1",
                                                        c.key,
                                                        e.target.value,
                                                    )
                                                }
                                                placeholder={
                                                    isEn
                                                        ? "Enter feedback, vocabulary corrections, or guidance..."
                                                        : "Ghi nhận xét cụ thể, sửa lỗi từ vựng/ngữ pháp..."
                                                }
                                            />
                                        ) : (
                                            <div className="student-feedback-box">
                                                {data.task1.feedback[
                                                    c.key as keyof typeof data.task1.feedback
                                                ] || (
                                                    <em>
                                                        {isEn
                                                            ? "No comment yet."
                                                            : "Chưa có lời phê."}
                                                    </em>
                                                )}
                                            </div>
                                        )}
                                    </div>
                                </div>
                            );
                        })}

                        {/* Task 1 Overall Note */}
                        <div className="task-summary-card">
                            <strong>
                                {isEn
                                    ? "Task 1 Overall Tutor Note:"
                                    : "Nhận xét tổng thể Task 1:"}
                            </strong>
                            {mode === "tutor" ? (
                                <textarea
                                    rows={2}
                                    value={data.task1.overallFeedback}
                                    onChange={(e) =>
                                        setData((prev) => ({
                                            ...prev,
                                            task1: {
                                                ...prev.task1,
                                                overallFeedback: e.target.value,
                                            },
                                        }))
                                    }
                                    placeholder="Đánh giá chung về Task 1..."
                                />
                            ) : (
                                <p className="summary-text">
                                    {data.task1.overallFeedback}
                                </p>
                            )}
                        </div>
                    </div>
                )}

                {/* TASK 2 PANEL */}
                {activeTab === "task2" && (
                    <div className="task-section">
                        <div className="task-header-info">
                            <h4>{isEn ? "IELTS Writing Task 2 Assessment" : "Đánh giá IELTS Writing Task 2"}</h4>
                            <p>
                                {isEn
                                    ? "Minimum 250 words. Graded on Task Response, Coherence & Cohesion, Lexical Resource, and Grammar."
                                    : "Bài luận nghị luận tối thiểu 250 từ. Chiếm 2/3 tổng điểm phần thi Writing."}
                            </p>
                        </div>

                        {task2Criteria.map((c) => {
                            const score =
                                data.task2.scores[
                                    c.key as keyof typeof data.task2.scores
                                ];
                            const wholeBand = Math.floor(score);
                            const descriptorBullets =
                                c.descriptors[wholeBand] ||
                                c.descriptors[7] ||
                                [];
                            const isDescOpen =
                                expandedDescriptors[`t2-${c.key}`] ?? true;

                            return (
                                <div key={c.key} className="criteria-card">
                                    <div className="criteria-header">
                                        <div className="criteria-title-wrap">
                                            <strong>{c.name}</strong>
                                            <span className="criteria-desc">{c.desc}</span>
                                        </div>
                                        <div className="criteria-score-box">
                                            <span className="score-val">
                                                {score.toFixed(1)}
                                            </span>
                                        </div>
                                    </div>

                                    {/* Score Selector for Tutor */}
                                    {mode === "tutor" && (
                                        <div className="score-selector-row">
                                            <span className="selector-label">
                                                {isEn ? "Set Band:" : "Chấm điểm:"}
                                            </span>
                                            <div className="band-chips">
                                                {BAND_OPTIONS.slice(0, 12).map(
                                                    (b) => (
                                                        <button
                                                            key={b}
                                                            type="button"
                                                            className={`band-chip ${score === b ? "active" : ""}`}
                                                            onClick={() =>
                                                                handleScoreChange(
                                                                    "task2",
                                                                    c.key,
                                                                    b,
                                                                )
                                                            }
                                                        >
                                                            {b.toFixed(1)}
                                                        </button>
                                                    ),
                                                )}
                                            </div>
                                        </div>
                                    )}

                                    {/* Official Band Descriptors Box matching Image 2 */}
                                    <div className="descriptors-box">
                                        <div
                                            className="descriptors-header"
                                            onClick={() =>
                                                toggleDescriptor(`t2-${c.key}`)
                                            }
                                        >
                                            <div className="desc-title-row">
                                                <BookOpen size={13} />
                                                <span>
                                                    Band {wholeBand} Descriptor
                                                    (Official)
                                                </span>
                                            </div>
                                            {isDescOpen ? (
                                                <ChevronDown size={14} />
                                            ) : (
                                                <ChevronRight size={14} />
                                            )}
                                        </div>
                                        {isDescOpen && (
                                            <ul className="descriptors-list">
                                                {descriptorBullets.map(
                                                    (bullet, bIdx) => (
                                                        <li key={bIdx}>
                                                            {bullet}
                                                        </li>
                                                    ),
                                                )}
                                            </ul>
                                        )}
                                    </div>

                                    {/* Tutor Feedback Field */}
                                    <div className="feedback-field">
                                        <label>
                                            {isEn
                                                ? "Tutor Feedback & Suggestions:"
                                                : "Lời phê của Tutor cho tiêu chí này:"}
                                        </label>
                                        {mode === "tutor" ? (
                                            <textarea
                                                rows={2}
                                                value={
                                                    data.task2.feedback[
                                                        c.key as keyof typeof data.task2.feedback
                                                    ]
                                                }
                                                onChange={(e) =>
                                                    handleFeedbackChange(
                                                        "task2",
                                                        c.key,
                                                        e.target.value,
                                                    )
                                                }
                                                placeholder={
                                                    isEn
                                                        ? "Enter feedback, vocabulary corrections, or guidance..."
                                                        : "Ghi nhận xét cụ thể, sửa lỗi luận điểm/từ vựng/ngữ pháp..."
                                                }
                                            />
                                        ) : (
                                            <div className="student-feedback-box">
                                                {data.task2.feedback[
                                                    c.key as keyof typeof data.task2.feedback
                                                ] || (
                                                    <em>
                                                        {isEn
                                                            ? "No comment yet."
                                                            : "Chưa có lời phê."}
                                                    </em>
                                                )}
                                            </div>
                                        )}
                                    </div>
                                </div>
                            );
                        })}

                        {/* Task 2 Overall Note */}
                        <div className="task-summary-card">
                            <strong>
                                {isEn
                                    ? "Task 2 Overall Tutor Note:"
                                    : "Nhận xét tổng thể Task 2:"}
                            </strong>
                            {mode === "tutor" ? (
                                <textarea
                                    rows={2}
                                    value={data.task2.overallFeedback}
                                    onChange={(e) =>
                                        setData((prev) => ({
                                            ...prev,
                                            task2: {
                                                ...prev.task2,
                                                overallFeedback: e.target.value,
                                            },
                                        }))
                                    }
                                    placeholder="Đánh giá chung về Task 2..."
                                />
                            ) : (
                                <p className="summary-text">
                                    {data.task2.overallFeedback}
                                </p>
                            )}
                        </div>
                    </div>
                )}

                {/* SUMMARY & PDF REPORT (Image 3 Architecture) */}
                {activeTab === "summary" && (
                    <div className="summary-section">
                        <div className="report-card">
                            <div className="report-card-header">
                                <Award size={20} className="text-gold" />
                                <div>
                                    <strong>
                                        {isEn
                                            ? "Complete IELTS Assessment Summary"
                                            : "Bảng tổng kết kết quả đánh giá IELTS"}
                                    </strong>
                                    <span>
                                        {data.studentName} · Evaluator:{" "}
                                        {data.tutorName}
                                    </span>
                                </div>
                            </div>

                            <table className="score-summary-table">
                                <thead>
                                    <tr>
                                        <th>{isEn ? "Component" : "Phần thi"}</th>
                                        <th>TA / TR</th>
                                        <th>CC</th>
                                        <th>LR</th>
                                        <th>GRA</th>
                                        <th>{isEn ? "Band Score" : "Điểm Band"}</th>
                                    </tr>
                                </thead>
                                <tbody>
                                    <tr>
                                        <td>
                                            <strong>Task 1 (Report)</strong>
                                        </td>
                                        <td>{data.task1.scores.TA.toFixed(1)}</td>
                                        <td>{data.task1.scores.CC.toFixed(1)}</td>
                                        <td>{data.task1.scores.LR.toFixed(1)}</td>
                                        <td>{data.task1.scores.GRA.toFixed(1)}</td>
                                        <td>
                                            <strong className="band-highlight">
                                                {task1Band.toFixed(1)}
                                            </strong>
                                        </td>
                                    </tr>
                                    <tr>
                                        <td>
                                            <strong>Task 2 (Essay)</strong>
                                        </td>
                                        <td>{data.task2.scores.TR.toFixed(1)}</td>
                                        <td>{data.task2.scores.CC.toFixed(1)}</td>
                                        <td>{data.task2.scores.LR.toFixed(1)}</td>
                                        <td>{data.task2.scores.GRA.toFixed(1)}</td>
                                        <td>
                                            <strong className="band-highlight">
                                                {task2Band.toFixed(1)}
                                            </strong>
                                        </td>
                                    </tr>
                                </tbody>
                                <tfoot>
                                    <tr>
                                        <td colSpan={5}>
                                            <strong>
                                                {isEn
                                                    ? "OVERALL IELTS WRITING BAND"
                                                    : "ĐIỂM TỔNG KẾT IELTS WRITING"}
                                            </strong>
                                        </td>
                                        <td>
                                            <span className="final-band-badge">
                                                Band {overallBand.toFixed(1)}
                                            </span>
                                        </td>
                                    </tr>
                                </tfoot>
                            </table>
                        </div>

                        {/* General Assessment & Action Plan */}
                        <div className="assessment-fields">
                            <div className="general-feedback-block">
                                <strong>
                                    {isEn
                                        ? "General Evaluation & Diagnostic:"
                                        : "Nhận xét tổng quát năng lực viết:"}
                                </strong>
                                {mode === "tutor" ? (
                                    <textarea
                                        rows={3}
                                        value={data.generalAssessment}
                                        onChange={(e) =>
                                            setData((d) => ({
                                                ...d,
                                                generalAssessment:
                                                    e.target.value,
                                            }))
                                        }
                                        placeholder="Nhận xét tổng thể phong cách viết, tư duy lập luận..."
                                    />
                                ) : (
                                    <p className="student-assessment-p">
                                        {data.generalAssessment}
                                    </p>
                                )}
                            </div>

                            <div className="general-feedback-block">
                                <strong>
                                    {isEn
                                        ? "Tutor Key Recommendations & Action Plan:"
                                        : "Kế hoạch cải thiện & Lời khuyên của Tutor:"}
                                </strong>
                                {mode === "tutor" ? (
                                    <textarea
                                        rows={3}
                                        value={data.recommendations}
                                        onChange={(e) =>
                                            setData((d) => ({
                                                ...d,
                                                recommendations: e.target.value,
                                            }))
                                        }
                                        placeholder="1. Luyện viết mở bài nhanh... 2. Đa dạng câu phức..."
                                    />
                                ) : (
                                    <p className="student-assessment-p">
                                        {data.recommendations}
                                    </p>
                                )}
                            </div>
                        </div>

                        {/* LaTeX Engine -> PDF Report Generator (Image 3) */}
                        <div className="latex-report-box">
                            <div className="report-box-info">
                                <Printer size={20} className="text-emerald" />
                                <div>
                                    <strong>
                                        {isEn
                                            ? "LaTeX Engine -> PDF Evaluation Report"
                                            : "Xuất Báo cáo Kết quả PDF qua LaTeX"}
                                    </strong>
                                    <p>
                                        {isEn
                                            ? "Compile an official diagnostic report document into your project root with professional formatting."
                                            : "Tự động sinh mã nguồn LaTeX và biên dịch ra tệp PDF báo cáo chấm điểm chuyên nghiệp."}
                                    </p>
                                </div>
                            </div>
                            <button
                                className="generate-report-btn"
                                disabled={generating}
                                onClick={handleGenerateReport}
                            >
                                <Sparkles size={15} />
                                <span>
                                    {generating
                                        ? isEn
                                            ? "Generating LaTeX..."
                                            : "Đang tạo báo cáo..."
                                        : isEn
                                          ? "Generate LaTeX & PDF Report"
                                          : "Tạo Báo Cáo LaTeX & PDF"}
                                </span>
                            </button>
                        </div>
                    </div>
                )}
            </div>

            {/* Bottom Footer Actions */}
            <footer className="ielts-footer">
                {saveStatus && (
                    <span className="ielts-save-status">
                        <Check size={14} /> {saveStatus}
                    </span>
                )}
                <div className="ielts-footer-buttons">
                    <button
                        className="ielts-save-btn"
                        disabled={saving}
                        onClick={handleSave}
                    >
                        <Save size={14} />
                        <span>{saving ? "Đang lưu..." : "Lưu đánh giá"}</span>
                    </button>
                    <button
                        className="ielts-pdf-btn"
                        disabled={generating}
                        onClick={handleGenerateReport}
                    >
                        <FileSpreadsheet size={14} />
                        <span>{isEn ? "Export Report" : "Xuất báo cáo PDF"}</span>
                    </button>
                </div>
            </footer>
        </div>
    );
}
