"use client";
import { Fragment, useEffect, useRef } from "react";
import {
    BookOpen,
    ListChecks,
    ClipboardCheck,
    Clock3,
    FileText,
    Folder,
    LayoutDashboard,
    Send,
    UsersRound,
    Library,
    NotebookPen,
    FileStack,
    PanelsTopLeft,
    UserRoundCheck,
} from "lucide-react";

export type WritingSection =
    | "overview"
    | "courses"
    | "library"
    | "guides"
    | "rubric"
    | "create"
    | "assignments"
    | "submissions"
    | "practice-bank"
    | "practice"
    | "exams"
    | "results";
export type WorkspaceNavigationActions = {
    onHome: () => void;
    onProjects: (filter: "all" | "shared") => void;
    onTemplates: () => void;
    projectCount: number;
    sharedCount: number;
    templateOpen: boolean;
};
export default function WorkspaceNavigation({
    tutor,
    active,
    onWriting,
    shell,
    readonly = false,
    loading = false,
    navigatingTo,
}: {
    tutor: boolean;
    active: string;
    onWriting: (section: WritingSection) => void;
    shell: WorkspaceNavigationActions;
    readonly?: boolean;
    loading?: boolean;
    navigatingTo?: string | null;
}) {
    const items = tutor
        ? ([
              ["courses", "Courses", UsersRound],
              ["library", "Question library", Library],
              ["guides", "Writing guides", NotebookPen],
              ["rubric", "Assessment categories", ListChecks],
              ["assignments", "Assessment", FileStack],
              ["submissions", "Student submissions", ClipboardCheck],
          ] as const)
        : ([
              ["courses", "Courses", UsersRound],
              ["guides", "Writing guides", NotebookPen],
              ["practice-bank", "Practice exercises", FileText],
              ["practice", "My writing", FileText],
              ["assignments", "Assigned exercises", BookOpen],
              ["exams", "Examinations", Clock3],
              ["submissions", "Submission history", Send],
              ["results", "Results & feedback", ClipboardCheck],
          ] as const);
    const selected = shell.templateOpen
        ? "templates"
        : active === "overview"
          ? "dashboard"
          : active;
    const sidebar = useRef<HTMLElement>(null);
    useEffect(() => {
        sidebar.current
            ?.querySelector('[aria-current="page"]')
            ?.scrollIntoView({ block: "nearest", inline: "nearest" });
    }, [selected]);
    return (
        <aside
            ref={sidebar}
            className="writing-sidebar workspace-navigation"
            aria-label="Workspace navigation"
        >
            <span className="writing-eyebrow">
                WORKSPACE {tutor ? "TUTOR" : "STUDENT"}
            </span>
            <button
                className={selected === "dashboard" ? "active" : ""}
                aria-current={selected === "dashboard" ? "page" : undefined}
                onClick={shell.onHome}
            >
                <LayoutDashboard size={18} />
                Dashboard
            </button>
            <span className="writing-nav-group workspace-writing-group">
                Task Writing
            </span>
            {items.map(([id, label, Icon]) => (
                <Fragment key={id}>
                    {id === (tutor ? "library" : "guides") && (
                        <span className="workspace-nav-divider" />
                    )}
                    <button
                        disabled={
                            (!loading && readonly &&
                                ["create", "library", "rubric"].includes(id))
                        }
                        className={
                            selected === id ? "active" : ""
                        }
                        aria-current={selected === id ? "page" : undefined}
                        aria-busy={navigatingTo === id}
                        onClick={() => onWriting(id)}
                    >
                        <Icon size={18} />
                        {label}
                    </button>
                </Fragment>
            ))}
            <span className="writing-nav-group workspace-latex-group">
                LaTeX Studio
            </span>
            <button
                className={selected === "projects" ? "active latex-active" : ""}
                aria-current={selected === "projects" ? "page" : undefined}
                onClick={() => shell.onProjects("all")}
            >
                <Folder size={18} />
                All projects
                <span className="workspace-nav-count">
                    {shell.projectCount}
                </span>
            </button>
            <button
                className={selected === "shared" ? "active latex-active" : ""}
                aria-current={selected === "shared" ? "page" : undefined}
                onClick={() => shell.onProjects("shared")}
            >
                <UserRoundCheck size={18} />
                Shared with you
                <span className="workspace-nav-count">{shell.sharedCount}</span>
            </button>
            <button
                className={
                    selected === "templates" ? "active latex-active" : ""
                }
                aria-current={selected === "templates" ? "page" : undefined}
                onClick={shell.onTemplates}
            >
                <PanelsTopLeft size={18} />
                Template library
            </button>
        </aside>
    );
}
