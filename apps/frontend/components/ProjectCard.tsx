import {
    FileText,
    BookOpen,
    PenLine,
    Calculator,
    Globe,
    Lightbulb,
    Code2,
    GraduationCap,
    Clock3,
    ChevronRight,
    MoreHorizontal,
} from "lucide-react";
import type { Project } from "../lib/api";
import { translator } from "../lib/parity";
export default function ProjectCard({
    project,
    language,
    onOpen,
    onManage,
}: {
    project: Project;
    language: string;
    onOpen: () => void;
    onManage: () => void;
}) {
    const t = translator(language),
        Icon =
            (
                {
                    file: FileText,
                    book: BookOpen,
                    pen: PenLine,
                    calc: Calculator,
                    globe: Globe,
                    bulb: Lightbulb,
                    code: Code2,
                    cap: GraduationCap,
                } as const
            )[project.icon as "file"] || FileText;
    return (
        <article
            className="project-card"
            onContextMenu={(event) => {
                event.preventDefault();
                onManage();
            }}
        >
            <button className="project-card-open" onClick={onOpen}>
                <div
                    className={`card-document color-${project.color || "green"}`}
                    style={
                        /^#[a-fA-F0-9]{6}$/.test(project.color || "")
                            ? {
                                  backgroundColor: project.color + "22",
                                  color: project.color,
                              }
                            : undefined
                    }
                >
                    <Icon size={39} strokeWidth={1.3} />
                    <span>{project.engine}</span>
                </div>
                <div className="project-card-body">
                    <h3>{project.title}</h3>
                    <p>
                        <Clock3 size={13} />
                        {new Date(project.updated).toLocaleDateString(
                            language === "en" ? "en-US" : "vi-VN",
                        )}
                    </p>
                    <span>
                        {t("Mở dự án", "Open project")}
                        <ChevronRight size={14} />
                    </span>
                </div>
            </button>
            <button
                className="project-manage"
                title={t("Quản lý dự án", "Manage project")}
                onClick={onManage}
            >
                <MoreHorizontal size={18} />
            </button>
        </article>
    );
}
