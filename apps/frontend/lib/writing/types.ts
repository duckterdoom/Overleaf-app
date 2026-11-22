import rawCatalog from "./catalog.json";
export type Check = { id: string; text: string; codes: string[]; weight?: number };
export type Profile = {
    category?: "ielts" | "report" | "research";
    sectionTitles?: Record<string,string>;
    task: number;
    family: string;
    label: string;
    focus: string[];
    checks: Record<string, Check[]>;
};
export function assessmentCategoryTitle(profile:Profile|undefined,key:string){
    const names:Record<string,string>={intro:'Introduction',overview:'Overview',body:'Body',conclusion:'Conclusion',closing:'Closing',whole:'Whole-paper requirements'};
    return profile?.sectionTitles?.[key]||names[key]||key;
}
export const catalog: Record<string, Profile> = rawCatalog;
export type CourseStudent = {id:string;name:string;email?:string;displayName?:string;studentCode?:string;note?:string;revision?:number};
export type Classroom = {
    kind?: 'course'|'group';
    revision?: number;
    id: string;
    title: string;
    tutorId: string;
    tutorName: string;
    joinCode?: string;
    studentCount: number;
    students?: CourseStudent[];
};
export type Assignment = {
    scoring?: {mode:'points';total:number}|null;
    assessmentCategories?: Record<string,string[]>;
    description?: string;
    libraryVisibility?: 'private'|'public'|null;
    writingScope?: 'whole'|'intro'|'body'|'conclusion';
    purpose?: 'practice'|'homework'|'exam';
    questionId?: string;
    kind?: 'assignment'|'exam';
    opensAt?: string|null;
    closesAt?: string|null;
    examEntered?: boolean;
    category?: "ielts" | "report" | "research";
    rubricCatalog?: Record<string, Profile>;
    promptFiles?: Record<string, PromptFile>;
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
export type PromptFile = {
    origin?: "upload";
    id: string; sourcePath: string; projectId: string|null; sourceProjectId?:string; kind: 'pdf' | 'image' | 'text'; mime: string;
    digest: string; pages: number | null; firstPage: number; lastPage: number | null; text?: string;
};
export type Draft = {
    blocks?: Record<string,WritingBlock[]>;
    readOnly?: boolean;
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
export type SentenceAnnotation = {
    id:string;start:number;end:number;quote:string;comment:string;replacement:string;
    kind:'comment'|'underline'|'strike'|'replace';category:string;resolved:boolean;
    authorName?:string;createdAt?:string;
};
export type Grade = {
    annotations?: SentenceAnnotation[];
    criterionScores?: Record<string,number|null>;
    scoreSource?: 'manual' | 'checklist';
    profile: string;
    scores: Record<string, number | null>;
    notes: Record<string, string>;
    overall: string;
    sections: Section[];
};
export type Review = {
    awardedTotal?: number | null;
    calculatedScore?: number;
    totalScore?: number;
    maxScore?: number;
    taskScores?: Record<string,number>;
    scorePercent?: number;
    criteriaCount?: number;
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
    blocks?: Record<string,WritingBlock[]>;
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
    students?: {id:string;name:string}[];
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
        hint: "Task coverage, key information, overview or letter purpose and tone.",
    },
    TR: {
        title: "Task Response",
        hint: "Address all requirements, state a clear position where requested and support ideas.",
    },
    CC: {
        title: "Coherence & Cohesion",
        hint: "Organization, paragraph development and appropriate connections.",
    },
    LR: {
        title: "Lexical Resource",
        hint: "Vocabulary range, precision and appropriateness.",
    },
    GRA: {
        title: "Grammatical Range & Accuracy",
        hint: "Range of sentence structures and grammatical accuracy.",
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
export function checklistScores(profile:Profile, sections:Section[], task:number):Record<string,number>|null {
    const values:Record<string,number[]> = Object.fromEntries(codes(task).map(code=>[code,[]]));
    const grouped=new Map<string,{codes:string[];values:number[]}>();
    for(const section of sections)for(const check of profile.checks[section.kind]||[]){
        const state=section.checks[check.id];
        if(!state||state==='unreviewed')return null;
        if(state==='na')continue;
        const key=section.kind+':'+check.id;
        if(!grouped.has(key))grouped.set(key,{codes:check.codes,values:[]});
        grouped.get(key)!.values.push(state==='met'?1:state==='partial'?0.5:0);
    }
    for(const item of grouped.values())for(const code of item.codes)if(values[code])values[code].push(Math.min(...item.values));
    if(Object.values(values).some(list=>list.length===0))return null;
    return Object.fromEntries(Object.entries(values).map(([code,list])=>[code,rounded(9*list.reduce((sum,n)=>sum+n,0)/list.length)]));
}
export function blankGrade(task: number, assignment: Assignment): Grade {
    return {
        profile: assignment.profiles[String(task)],
        scores: usesBands(assignment) ? Object.fromEntries(codes(task).map((code) => [code, null])) : {},
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
    const isIelts=!assignment.category||assignment.category==='ielts';
    const letter = isIelts && task === 1 && assignment.variant === "general";
    const selectedKinds=assignment.scoring?Object.keys(assignment.rubricCatalog?.[assignment.profiles[String(task)]]?.checks||{}).filter(key=>key!=='whole'):[];
    const kind = selectedKinds.length?selectedKinds[Math.min(index,selectedKinds.length-1)]:assignment.writingScope && assignment.writingScope!=='whole' ? assignment.writingScope :
        index === 0
            ? "intro"
            : isIelts && task === 1 && !letter && index === 1 && length >= 4
              ? "overview"
              : index === length - 1 && length >= 4
                ? letter
                    ? "closing"
                    : task === 2 || !isIelts
                      ? "conclusion"
                      : "body"
                : "body";
    return {
        paragraph: index,
        kind,
        title: (
            {
                intro: letter ? "Opening / letter purpose" : "Introduction",
                overview: "Overview",
                conclusion: "Conclusion",
                closing: "Closing",
                body: `Body ${Math.max(1, index - (task === 1 && !letter && length >= 4 ? 1 : 0))}`,
            } as Record<string, string>
        )[kind] || assignment.rubricCatalog?.[assignment.profiles[String(task)]]?.sectionTitles?.[kind] || kind,
        checks: {},
        note: "",
        quote: "",
    };
}

export const usesBands=(assignment:Assignment)=>!assignment.scoring&&(!assignment.category||assignment.category==='ielts')&&(!assignment.writingScope||assignment.writingScope==='whole');
export type Question={id:string;ownerId:string;ownerName:string;title:string;visibility:'private'|'public';revision:number;content:Assignment;sourceQuestionId?:string;sourceOwnerName?:string;sourceAssignmentId?:string};
export type WritingBlock={heading:string;text:string};
