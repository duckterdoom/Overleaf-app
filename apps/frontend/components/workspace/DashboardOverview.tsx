"use client";
import { useEffect, useState } from "react";
import {
    ArrowRight,
    BookOpen,
    Braces,
    CheckCircle2,
    Clock3,
    FileText,
    GraduationCap,
    Layers3,
    UsersRound,
} from "lucide-react";
import { json, type Project } from "../../lib/api";
import { type Session } from "../../lib/parity";
import { type Overview } from "../../lib/writing/types";

type Props = {
    session: Session;
    projects: Project[];
    onWriting: () => void;
    onCourses: () => void;
    onDocuments: () => void;
    onProject: (project: Project) => void;
};
export default function DashboardOverview({
    session,
    projects,
    onWriting,
    onCourses,
    onDocuments,
    onProject,
}: Props) {
    const [overview, setOverview] = useState<Overview | null>(null),
        [unavailable, setUnavailable] = useState(false);
    useEffect(() => {
        const controller = new AbortController();
        setOverview(null);
        setUnavailable(false);
        void json<Overview>("/writing/overview", { signal: controller.signal })
            .then((value) => {
                if (!controller.signal.aborted) setOverview(value);
            })
            .catch(() => {
                if (!controller.signal.aborted) setUnavailable(true);
            });
        return () => controller.abort();
    }, [session.id, session.role]);
    const tutor = session.role === "tutor";
    const recent = [...projects]
        .sort(
            (a, b) =>
                new Date(b.updated).getTime() - new Date(a.updated).getTime(),
        )
        .slice(0, 3);
    const recentExercises = [...(overview?.assignments || [])]
        .sort(
            (a, b) =>
                new Date(b.createdAt).getTime() -
                new Date(a.createdAt).getTime(),
        )
        .slice(0, 3);
    const counts = [
        {
            label: "Courses",
            value: overview?.classes.length,
            icon: UsersRound,
            tone: "amber",
            action: onCourses,
        },
        {
            label: tutor ? "Assessments" : "Available exercises",
            value: overview?.assignments.length,
            icon: BookOpen,
            tone: "violet",
            action: onWriting,
        },
        {
            label: tutor ? "Student submissions" : "My submissions",
            value: overview?.submissions.length,
            icon: CheckCircle2,
            tone: "teal",
            action: onWriting,
        },
    ];
    return (
        <section className="academic-dashboard" aria-label="Platform overview">
            <header className="dashboard-introduction">
                <div>
                    <span className="dashboard-kicker">
                        YOUR LEARNING & CREATION HUB
                    </span>
                    <h1>{tutor ? "Tutor workspace." : "Student workspace."}</h1>
                    <p>
                        Welcome back, <strong>{session.name}</strong>.{" "}
                        {tutor
                            ? "Bring your teaching, feedback and documents together."
                            : "Make room for your next idea. Learn, practise and create."}
                    </p>
                </div>
                <span className="dashboard-role">
                    <GraduationCap size={16} aria-hidden="true" />
                    {tutor ? "Teacher account" : "Student account"}
                </span>
            </header>
            <div className="dashboard-module-columns">
                <section
                    className="dashboard-module dashboard-writing-module"
                    aria-labelledby="dashboard-writing-title"
                >
                    <header className="dashboard-module-heading">
                        <GraduationCap size={22} aria-hidden="true" />
                        <div>
                            <h2 id="dashboard-writing-title">Task Writing</h2>
                            <p>Courses, writing and assessment</p>
                        </div>
                    </header>
                    <div
                        className="dashboard-stats"
                        aria-label="Task Writing summary"
                    >
                        {counts.map(
                            ({ label, value, icon: Icon, tone, action }) => (
                                <button
                                    key={label}
                                    className={`dashboard-stat tone-${tone}`}
                                    onClick={action}
                                >
                                    <span className="stat-icon">
                                        <Icon size={22} aria-hidden="true" />
                                    </span>
                                    <span>
                                        <strong>{value ?? "—"}</strong>
                                        <small>{label}</small>
                                    </span>
                                    <ArrowRight size={15} aria-hidden="true" />
                                </button>
                            ),
                        )}
                    </div>
                    <article className="workspace-feature writing-feature">
                        <h3>
                            {tutor
                                ? "Great feedback starts here."
                                : "A better writer, one task at a time."}
                        </h3>
                        <p>
                            {tutor
                                ? "Create focused exercises, build a question library and guide every learner with clear criteria."
                                : "Explore your exercises, develop each paragraph and learn from specific tutor feedback."}
                        </p>
                        <div className="feature-capabilities">
                            <span>Paragraph practice</span>
                            <span>Custom criteria</span>
                            <span>Meaningful feedback</span>
                        </div>
                        <button className="feature-action" onClick={onWriting}>
                            Open Task Writing
                            <ArrowRight size={17} aria-hidden="true" />
                        </button>
                        <div
                            className="feature-illustration"
                            aria-hidden="true"
                        >
                            <div>
                                <FileText size={27} />
                                <i />
                                <i />
                                <i />
                            </div>
                            <span>
                                <CheckCircle2 size={22} />
                            </span>
                        </div>
                    </article>
                    <section
                        className="dashboard-module-list"
                        aria-label="Recent writing exercises"
                    >
                        <div className="dashboard-section-heading">
                            <h3>
                                {tutor
                                    ? "Recent assessments"
                                    : "Recent exercises"}
                            </h3>
                            <button onClick={onWriting}>
                                View all <ArrowRight size={15} />
                            </button>
                        </div>
                        {unavailable ? (
                            <p className="dashboard-data-note" role="status">
                                Writing summaries are unavailable right now. You
                                can still open Task Writing.
                            </p>
                        ) : !overview ? (
                            <p className="dashboard-data-note" role="status">
                                Loading writing summary...
                            </p>
                        ) : recentExercises.length ? (
                            recentExercises.map((exercise) => (
                                <article
                                    className="dashboard-exercise"
                                    key={exercise.id}
                                >
                                    <BookOpen size={18} aria-hidden="true" />
                                    <div>
                                        <strong>{exercise.title}</strong>
                                        <small>
                                            {exercise.category &&
                                            exercise.category !== "ielts"
                                                ? "Research"
                                                : "IELTS Task"}{" "}
                                            /{" "}
                                            {exercise.published
                                                ? "Assigned"
                                                : "Draft"}
                                        </small>
                                    </div>
                                </article>
                            ))
                        ) : (
                            <p className="dashboard-data-note">
                                {tutor
                                    ? "Your assessments will appear here. Create a question in Task Writing to get started."
                                    : "Your available exercises will appear here when they are assigned."}
                            </p>
                        )}
                    </section>
                </section>
                <section
                    className="dashboard-module dashboard-latex-module"
                    aria-labelledby="dashboard-latex-title"
                >
                    <header className="dashboard-module-heading">
                        <Braces size={22} aria-hidden="true" />
                        <div>
                            <h2 id="dashboard-latex-title">LaTeX Studio</h2>
                            <p>Documents, publishing and collaboration</p>
                        </div>
                    </header>
                    <div
                        className="dashboard-stats"
                        aria-label="LaTeX Studio summary"
                    >
                        <button
                            className="dashboard-stat tone-blue"
                            onClick={onDocuments}
                        >
                            <span className="stat-icon">
                                <Layers3 size={20} aria-hidden="true" />
                            </span>
                            <span>
                                <strong>{projects.length}</strong>
                                <small>Document projects</small>
                            </span>
                            <ArrowRight size={14} />
                        </button>
                    </div>
                    <article className="workspace-feature latex-feature">
                        <h3>
                            Your ideas.
                            <br />
                            Publication ready.
                        </h3>
                        <p>
                            Write reports and research papers with a powerful
                            editor, live PDF preview and collaborative tools.
                        </p>
                        <div className="feature-capabilities">
                            <span>LaTeX editor</span>
                            <span>PDF & Word</span>
                            <span>Live collaboration</span>
                        </div>
                        <button
                            className="feature-action"
                            onClick={onDocuments}
                        >
                            Open LaTeX Studio
                            <ArrowRight size={17} aria-hidden="true" />
                        </button>
                        <div
                            className="feature-illustration"
                            aria-hidden="true"
                        >
                            <div>
                                <Braces size={27} />
                                <i />
                                <i />
                                <i />
                            </div>
                        </div>
                    </article>{" "}
                    <section className="dashboard-recent">
                        <div className="dashboard-section-heading">
                            <div>
                                <h3>Recent documents</h3>
                            </div>
                            <button onClick={onDocuments}>
                                View all projects <ArrowRight size={15} />
                            </button>
                        </div>
                        {recent.length ? (
                            recent.map((project) => (
                                <button
                                    key={project.id}
                                    className="recent-document"
                                    onClick={() => onProject(project)}
                                >
                                    <span className="recent-document-icon">
                                        <FileText size={20} />
                                    </span>
                                    <span>
                                        <strong>{project.title}</strong>
                                        <small>
                                            <Clock3 size={12} />
                                            {new Date(
                                                project.updated,
                                            ).toLocaleDateString("en-GB")}{" "}
                                            {project.shared
                                                ? " / Shared document"
                                                : ""}
                                        </small>
                                    </span>
                                    <ArrowRight size={16} />
                                </button>
                            ))
                        ) : (
                            <p className="dashboard-data-note">
                                Your documents will appear here. Open LaTeX
                                Studio to create or import a project.
                            </p>
                        )}
                    </section>
                </section>
            </div>
        </section>
    );
}
