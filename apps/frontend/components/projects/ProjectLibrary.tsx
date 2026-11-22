"use client";
import { useEffect, useRef, useState } from "react";
import {
    ArrowDown,
    ArrowUp,
    Download,
    Tags,
    Trash2,
    X,
} from "lucide-react";
import { request, type Project } from "../../lib/api";
import { action, errorText } from "../../lib/parity";
import ProjectCard from "./ProjectCard";
import ProjectMenu from "./ProjectMenu";

type Props = {
    projects: Project[];
    view: "list" | "grid";
    language: string;
    onOpen: (p: Project) => void;
    onRefresh: () => Promise<void>;
};
export default function ProjectLibrary({
    projects,
    view,
    language,
    onOpen,
    onRefresh,
}: Props) {
    const [selected, setSelected] = useState<string[]>([]),
        [busy, setBusy] = useState(false),
        [error, setError] = useState(""),
        [notice, setNotice] = useState("");
    const [tagForm, setTagForm] = useState(false),
        [tags, setTags] = useState(""),
        [tagMode, setTagMode] = useState("add"),
        [deleting, setDeleting] = useState(false);
    const [sort, setSort] = useState<"title" | "updated">("updated"),
        [ascending, setAscending] = useState(false),
        [tagFilter, setTagFilter] = useState("");
    const [archiveFilter, setArchiveFilter] = useState("active");
    const all = useRef<HTMLInputElement>(null);
    useEffect(() => {
        setSelected((old) =>
            old.filter((id) => projects.some((p) => p.id === id)),
        );
    }, [projects]);
    const visible = projects
        .filter((p) => archiveFilter === "all" || (archiveFilter === "archived" ? !!p.archived : !p.archived))
        .filter((p) => !tagFilter || p.tags?.includes(tagFilter))
        .slice()
        .sort((a, b) => {
            const comparison =
                sort === "title"
                    ? a.title.localeCompare(b.title)
                    : new Date(a.updated).getTime() -
                      new Date(b.updated).getTime();
            return ascending ? comparison : -comparison;
        });
    const chosen = projects.filter((p) => selected.includes(p.id)),
        owned = chosen.every(
            (p) => !p.shared && (!p.permission || p.permission === "owner"),
        );
    const allChecked =
        visible.length > 0 && visible.every((p) => selected.includes(p.id));
    useEffect(() => {
        if (all.current)
            all.current.indeterminate =
                !allChecked && visible.some((p) => selected.includes(p.id));
    }, [selected, visible, allChecked]);
    const knownTags = [
        ...new Set(projects.flatMap((p) => p.tags || [])),
    ].sort();
    useEffect(() => {
        if (tagFilter && !projects.some((p) => p.tags?.includes(tagFilter)))
            setTagFilter("");
    }, [projects, tagFilter]);

    function toggle(id: string) {
        setSelected((old) =>
            old.includes(id) ? old.filter((x) => x !== id) : [...old, id],
        );
        setDeleting(false);
    }
    function order(key: "title" | "updated") {
        setAscending(sort === key ? !ascending : key === "title");
        setSort(key);
    }
    async function download() {
        setBusy(true);
        setError("");
        try {
            if (selected.length > 100)
                throw Error("Select up to 100 projects per ZIP download.");
            const response = await request("/projects/archive", {
                method: "POST",
                body: JSON.stringify({ projects: selected }),
            });
            const url = URL.createObjectURL(await response.blob());
            const link = document.createElement("a");
            link.href = url;
            link.download = "latex-projects.zip";
            link.click();
            setTimeout(() => URL.revokeObjectURL(url), 1000);
            setNotice("Source ZIP downloaded.");
        } catch (e) {
            setError(errorText(e));
        } finally {
            setBusy(false);
        }
    }
    async function update(remove = false) {
        setBusy(true);
        setError("");
        setNotice("");
        const failed: string[] = [];
        const messages: string[] = [];
        for (const p of chosen) {
            try {
                if (remove) await action("project-delete", { project: p.id });
                else {
                    const entries = tags
                        .split(",")
                        .map((s) => s.trim())
                        .filter(Boolean);
                    await action("project-update", {
                        project: p.id,
                        tags: entries,
                        tagMode,
                    });
                }
            } catch (e) {
                failed.push(p.id);
                messages.push(p.title + ": " + errorText(e));
            }
        }
        try {
            await onRefresh();
        } catch (e) {
            messages.push(errorText(e));
        }
        setSelected(failed);
        setError(messages.join(" / "));
        setNotice(
            messages.length
                ? "Some projects could not be updated."
                : remove
                  ? "Selected projects deleted."
                  : "Project tags saved.",
        );
        setBusy(false);
        setDeleting(false);
        setTagForm(false);
    }
    return (
        <section
            className="project-library"
            aria-label="Document projects"
            aria-busy={busy}
        >
            <label className="project-archive-filter">Projects <select aria-label="Project archive filter" value={archiveFilter} onChange={e=>setArchiveFilter(e.target.value)}><option value="active">Active projects</option><option value="archived">Archived projects</option><option value="all">All projects</option></select></label>
            {(selected.length > 0 || knownTags.length > 0) && (
                <div className="project-selection-tools">
                    <div role="status" aria-live="polite">
                        {busy
                            ? "Working..."
                            : selected.length
                              ? `${selected.length} selected`
                              : ""}
                    </div>
                    {knownTags.length > 0 && (
                        <label>
                            Tag
                            <select
                                aria-label="Filter projects by tag"
                                value={tagFilter}
                                onChange={(e) => {
                                    setTagFilter(e.target.value);
                                    setDeleting(false);
                                }}
                            >
                                <option value="">All tags</option>
                                {knownTags.map((t) => (
                                    <option key={t}>{t}</option>
                                ))}
                            </select>
                        </label>
                    )}
                    {selected.length > 0 && (
                        <div
                            className="project-bulk-actions"
                            aria-label="Selected project actions"
                        >
                            <button
                                disabled={busy}
                                title="Download selected projects as ZIP"
                                aria-label="Download selected projects"
                                onClick={() => void download()}
                            >
                                <Download size={18} />
                            </button>
                            <button
                                disabled={busy || !owned}
                                title={
                                    owned
                                        ? "Add or edit tags"
                                        : "Only project owners can edit tags"
                                }
                                aria-label="Edit selected project tags"
                                onClick={() => {
                                    if (!tagForm) {
                                        setTags(
                                            chosen.length === 1
                                                ? (chosen[0].tags || []).join(
                                                      ", ",
                                                  )
                                                : "",
                                        );
                                        setTagMode(
                                            chosen.length === 1
                                                ? "replace"
                                                : "add",
                                        );
                                    }
                                    setTagForm((v) => !v);
                                    setDeleting(false);
                                }}
                            >
                                <Tags size={18} />
                            </button>
                            <button
                                disabled={busy || !owned}
                                title={
                                    owned
                                        ? "Delete selected projects"
                                        : "Only project owners can delete projects"
                                }
                                aria-label="Delete selected projects"
                                onClick={() => {
                                    setDeleting(true);
                                    setTagForm(false);
                                }}
                            >
                                <Trash2 size={18} />
                            </button>
                            <button
                                disabled={busy}
                                aria-label="Clear project selection"
                                onClick={() => {
                                    setSelected([]);
                                    setTagForm(false);
                                    setDeleting(false);
                                }}
                            >
                                <X size={16} />
                            </button>
                        </div>
                    )}
                </div>
            )}
            {error && (
                <p className="form-error" role="alert">
                    {error}
                </p>
            )}
            {notice && (
                <p className="project-library-notice" role="status">
                    {notice}
                </p>
            )}
            {tagForm && selected.length > 0 && (
                <form
                    className="project-inline-action"
                    onSubmit={(e) => {
                        e.preventDefault();
                        void update();
                    }}
                >
                    <label>
                        Tags (comma separated)
                        <input
                            aria-label="Project tags"
                            value={tags}
                            onChange={(e) => setTags(e.target.value)}
                            maxLength={1000}
                            placeholder="Thesis, Research, Draft"
                        />
                    </label>
                    <label>
                        Action
                        <select
                            aria-label="Tag action"
                            value={tagMode}
                            onChange={(e) => setTagMode(e.target.value)}
                        >
                            <option value="add">Add tags</option>
                            <option value="replace">Replace tags</option>
                            <option value="remove">Remove tags</option>
                        </select>
                    </label>
                    <button disabled={busy} className="primary">
                        Save tags
                    </button>
                    <button
                        type="button"
                        disabled={busy}
                        onClick={() => setTagForm(false)}
                    >
                        Cancel
                    </button>
                </form>
            )}
            {deleting && (
                <div
                    className="project-inline-action"
                    role="group"
                    aria-label="Confirm project deletion"
                >
                    <p>
                        Delete {chosen.length} selected projects? Their source
                        files will leave your library.
                    </p>
                    <button
                        disabled={busy}
                        className="danger"
                        onClick={() => void update(true)}
                    >
                        Confirm delete
                    </button>
                    <button disabled={busy} onClick={() => setDeleting(false)}>
                        Cancel
                    </button>
                </div>
            )}
            {view === "list" ? (
                <div className="project-table-scroll">
                    <table className="project-table">
                        <thead>
                            <tr>
                                <th className="project-select-cell">
                                    <input
                                        ref={all}
                                        type="checkbox"
                                        aria-label="Select all visible projects"
                                        disabled={busy || !visible.length}
                                        checked={allChecked}
                                        onChange={() => {
                                            setSelected((old) =>
                                                allChecked
                                                    ? old.filter(
                                                          (id) =>
                                                              !visible.some(
                                                                  (p) =>
                                                                      p.id ===
                                                                      id,
                                                              ),
                                                      )
                                                    : [
                                                          ...new Set([
                                                              ...old,
                                                              ...visible.map(
                                                                  (p) => p.id,
                                                              ),
                                                          ]),
                                                      ],
                                            );
                                            setDeleting(false);
                                        }}
                                    />
                                </th>
                                {(["title", "owner", "updated"] as const).map(
                                    (key) => (
                                        <th
                                            key={key}
                                            aria-sort={
                                                key === sort
                                                    ? ascending
                                                        ? "ascending"
                                                        : "descending"
                                                    : undefined
                                            }
                                        >
                                            {key === "owner" ? (
                                                "Owner"
                                            ) : (
                                                <button
                                                    onClick={() => order(key)}
                                                >
                                                    {key === "title"
                                                        ? "Title"
                                                        : "Last modified"}
                                                    {sort === key &&
                                                        (ascending ? (
                                                            <ArrowUp
                                                                size={14}
                                                            />
                                                        ) : (
                                                            <ArrowDown
                                                                size={14}
                                                            />
                                                        ))}
                                                </button>
                                            )}
                                        </th>
                                    ),
                                )}
                                <th>
                                    <span className="sr-only">Actions</span>
                                </th>
                            </tr>
                        </thead>
                        <tbody>
                            {visible.map((p) => (
                                <tr
                                    key={p.id}
                                    className={
                                        selected.includes(p.id)
                                            ? "selected"
                                            : ""
                                    }
                                >
                                    <td>
                                        <input
                                            type="checkbox"
                                            aria-label={`Select project ${p.title}`}
                                            checked={selected.includes(p.id)}
                                            disabled={busy}
                                            onChange={() => toggle(p.id)}
                                        />
                                    </td>
                                    <td>
                                        <button
                                            className="project-card-open project-title-open"
                                            onClick={() => onOpen(p)}
                                        >
                                            {p.title}
                                        </button>
                                        {!!p.tags?.length && (
                                            <div className="project-tags">
                                                {p.tags.map((t) => (
                                                    <span key={t}>{t}</span>
                                                ))}
                                            </div>
                                        )}
                                    </td>
                                    <td>
                                        {p.shared
                                            ? p.ownerName || "Project owner"
                                            : "You"}
                                    </td>
                                    <td>
                                        <time
                                            dateTime={p.updated}
                                            title={new Date(
                                                p.updated,
                                            ).toLocaleString()}
                                        >
                                            {new Date(
                                                p.updated,
                                            ).toLocaleDateString("en-GB")}
                                        </time>
                                    </td>
                                    <td>
                                        <ProjectMenu project={p} onDone={onRefresh} />
                                    </td>
                                </tr>
                            ))}
                        </tbody>
                    </table>
                    {!visible.length && <p>No projects match this tag.</p>}
                </div>
            ) : (
                <div className="project-grid">
                    {visible.map((p) => (
                        <div key={p.id} className="selectable-project-card">
                            <input
                                type="checkbox"
                                aria-label={`Select project ${p.title}`}
                                checked={selected.includes(p.id)}
                                disabled={busy}
                                onChange={() => toggle(p.id)}
                            />
                            <ProjectCard
                                project={p}
                                language={language}
                                onOpen={() => onOpen(p)}
                                actions={<ProjectMenu project={p} onDone={onRefresh} />}
                            />
                        </div>
                    ))}
                </div>
            )}
        </section>
    );
}
