import type { ReactNode } from "react";
import { FileText, Clock3, ChevronRight } from "lucide-react";
import type { Project } from "../../lib/api";
import { translator } from "../../lib/parity";
export default function ProjectCard({
    project,
    language,
    onOpen,
    actions,
}: {
    project: Project;
    language: string;
    onOpen: () => void;
    actions?: ReactNode;
}) {
    const t = translator(language);
    return (
        <article
            className="project-card"

        >
            <button className="project-card-open" onClick={onOpen}>
                <div
                    className="card-document"
                >
                    <FileText size={39} strokeWidth={1.3} />
                    <span>{project.engine}</span>
                </div>
                <div className="project-card-body">
                    <h3>{project.title}</h3>
                    {project.shared && <small className="shared-project-label">{project.ownerName} · {project.permission === "editor" ? t("Chỉnh sửa", "Editor") : t("Chỉ xem", "Viewer")}</small>}
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
            {actions}
        </article>
    );
}
