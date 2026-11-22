"use client";
import { useEffect, useRef, useState } from "react";
import {
    ArrowLeft,
    ArrowRight,
    BookOpen,
    Braces,
    FileText,
    GraduationCap,
    LayoutTemplate,
    LoaderCircle,
    Mail,
    Presentation,
    Search,
} from "lucide-react";
import { request, type Catalog, type Template } from "../../lib/api";
import { errorText } from "../../lib/parity";
import PdfViewer from "../editor/PdfViewer";

const categoryDescriptions: Record<string, string> = {
    journal: "Articles, conference manuscripts and research papers.",
    books: "Structured books, chapters and long-form writing.",
    letters: "Professional correspondence and formal requests.",
    assignments: "Homework, exercises and course documents.",
    posters: "Present your work with a clear visual hierarchy.",
    presentations: "Slide decks for talks, teaching and conferences.",
    reports: "Reports, technical documentation and project summaries.",
    cv: "CVs, resumes and professional profiles.",
    theses: "Dissertations, theses and graduation projects.",
};
const categoryIcons: Record<string, typeof FileText> = {
    journal: FileText,
    books: BookOpen,
    letters: Mail,
    assignments: GraduationCap,
    presentations: Presentation,
};
type Detail = {
    template: Template & {
        mainFile: string;
        author: string;
        license?: string;
        updated: string;
    };
    files: { path: string; content: string | null }[];
};

function Thumbnail({
    template,
    language,
}: {
    template: Template;
    language: string;
}) {
    const root = useRef<HTMLDivElement>(null);
    const [image, setImage] = useState(""),
        [failed, setFailed] = useState(false);
    useEffect(() => {
        const abort = new AbortController();
        let objectUrl = "";
        setImage("");
        setFailed(false);
        const observer = new IntersectionObserver((entries) => {
            if (!entries.some((entry) => entry.isIntersecting)) return;
            observer.disconnect();
            void request(
                `/templates/${encodeURIComponent(template.id)}/thumbnail?language=${language}`,
                { signal: abort.signal },
            )
                .then((response) => response.blob())
                .then((blob) => {
                    if (!abort.signal.aborted) {
                        objectUrl = URL.createObjectURL(blob);
                        setImage(objectUrl);
                    }
                })
                .catch(() => {
                    if (!abort.signal.aborted) setFailed(true);
                });
        });
        if (root.current) observer.observe(root.current);
        return () => {
            abort.abort();
            observer.disconnect();
            if (objectUrl) URL.revokeObjectURL(objectUrl);
        };
    }, [template.id, language]);
    return (
        <div ref={root} className="template-thumbnail">
            {image ? (
                <img
                    src={image}
                    alt={`${template.title.en} compiled preview`}
                />
            ) : (
                <div>
                    <FileText size={42} />
                    <small>
                        {failed
                            ? "Open details to preview"
                            : "Preparing PDF preview..."}
                    </small>
                </div>
            )}
        </div>
    );
}

export default function TemplateLibrary({
    catalog,
    onOpen,
    onNewProject,
}: {
    catalog: Catalog;
    onOpen: (template: Template, language: string) => Promise<void>;
    onNewProject: () => void;
}) {
    const [category, setCategory] = useState(""),
        [selected, setSelected] = useState<Template | null>(null);
    const [query, setQuery] = useState(""),
        [language, setLanguage] = useState("en"),
        [tag, setTag] = useState("");
    const [detail, setDetail] = useState<Detail | null>(null),
        [pdf, setPdf] = useState<Uint8Array | null>(null);
    const [pane, setPane] = useState<"pdf" | "source">("pdf"),
        [sourceFile, setSourceFile] = useState("");
    const [sourceError, setSourceError] = useState(""),
        [pdfError, setPdfError] = useState(""),
        [openError, setOpenError] = useState("");
    const [creating, setCreating] = useState(false),
        [retry, setRetry] = useState(0);
    useEffect(() => {
        setDetail(null);
        setPdf(null);
        setSourceError("");
        setPdfError("");
        setOpenError("");
        if (!selected) return;
        const abort = new AbortController();
        const path = `/templates/${encodeURIComponent(selected.id)}`;
        void request(`${path}/source?language=${language}`, {
            signal: abort.signal,
        })
            .then((response) => response.json())
            .then((data: Detail) => {
                if (!abort.signal.aborted) {
                    setDetail(data);
                    setSourceFile(data.template.mainFile);
                }
            })
            .catch((error) => {
                if (!abort.signal.aborted) setSourceError(errorText(error));
            });
        void request(`${path}/pdf?language=${language}`, {
            signal: abort.signal,
        })
            .then((response) => response.arrayBuffer())
            .then((data) => {
                if (!abort.signal.aborted) setPdf(new Uint8Array(data));
            })
            .catch((error) => {
                if (!abort.signal.aborted) setPdfError(errorText(error));
            });
        return () => abort.abort();
    }, [selected?.id, language, retry]);
    const templates = catalog.templates.filter(
        (template) =>
            (!category || template.category === category) &&
            (!tag || template.tags.includes(tag)) &&
            `${template.title.en} ${template.description.en} ${template.tags.join(" ")}`
                .toLowerCase()
                .includes(query.trim().toLowerCase()),
    );
    const group = catalog.categories.find((value) => value.id === category);
    const back = () => {
        setSelected(null);
        setOpenError("");
    };
    async function create() {
        if (!selected || creating) return;
        setCreating(true);
        setOpenError("");
        try {
            await onOpen(selected, language);
        } catch (error) {
            setOpenError(errorText(error));
        } finally {
            setCreating(false);
        }
    }
    return (
        <section
            className="template-library-page"
            aria-label="Template library"
        >
            <header className="template-library-heading">
                <div>
                    <p className="eyebrow">LATEX STUDIO</p>
                    <h1>
                        {selected
                            ? selected.title.en
                            : group
                              ? `Templates / ${group.en}`
                              : "Template library"}
                    </h1>
                    <p>
                        {selected
                            ? "Preview the document and source before creating your own copy."
                            : group
                              ? categoryDescriptions[group.id]
                              : "Choose a category, explore a template and make it your own."}
                    </p>
                </div>
                <button onClick={onNewProject} disabled={creating}>
                    Blank project <ArrowRight size={15} />
                </button>
            </header>
            {(category || selected) && (
                <button
                    className="template-back"
                    disabled={creating}
                    onClick={() => (selected ? back() : setCategory(""))}
                >
                    <ArrowLeft size={15} />
                    {selected ? "Back to templates" : "All categories"}
                </button>
            )}
            <div className="template-library-filters">
                {!selected && (
                    <label className="template-library-search">
                        <Search size={16} aria-hidden="true" />
                        <input
                            type="search"
                            aria-label="Search templates"
                            placeholder="Search document templates..."
                            value={query}
                            onChange={(event) => setQuery(event.target.value)}
                        />
                    </label>
                )}
                {!selected && (
                    <label>
                        Tag
                        <select
                            aria-label="Filter template tags"
                            value={tag}
                            onChange={(event) => setTag(event.target.value)}
                        >
                            <option value="">All tags</option>
                            {[
                                ...new Set(
                                    catalog.templates
                                        .filter(
                                            (template) =>
                                                !category ||
                                                template.category === category,
                                        )
                                        .flatMap((template) => template.tags),
                                ),
                            ]
                                .sort()
                                .map((value) => (
                                    <option key={value}>{value}</option>
                                ))}
                        </select>
                    </label>
                )}
                <label>
                    Document language
                    <select
                        aria-label="Template document language"
                        value={language}
                        disabled={creating}
                        onChange={(event) => setLanguage(event.target.value)}
                    >
                        <option value="en">English</option>
                        <option value="vi">Vietnamese</option>
                    </select>
                </label>
            </div>
            {selected ? (
                <div className="template-detail-grid">
                    <section
                        className="template-detail-info"
                        aria-label="Template details"
                    >
                        <div className="template-detail-actions">
                            <button
                                className="primary"
                                disabled={creating}
                                onClick={() => void create()}
                            >
                                {creating ? (
                                    <LoaderCircle size={16} className="spin" />
                                ) : (
                                    <LayoutTemplate size={16} />
                                )}
                                Open as Template
                            </button>
                            <button
                                aria-pressed={pane === "source"}
                                onClick={() => setPane("source")}
                            >
                                <Braces size={16} />
                                View Source
                            </button>
                            <button
                                aria-pressed={pane === "pdf"}
                                onClick={() => setPane("pdf")}
                            >
                                <FileText size={16} />
                                View PDF
                            </button>
                        </div>
                        {openError && (
                            <p className="writing-error" role="alert">
                                {openError}
                            </p>
                        )}
                        <dl>
                            <dt>Library</dt>
                            <dd>
                                {detail?.template.author ||
                                    "Latex built-in library"}
                            </dd>
                            <dt>Category</dt>
                            <dd>
                                {
                                    catalog.categories.find(
                                        (value) =>
                                            value.id === selected.category,
                                    )?.en
                                }
                            </dd>
                            <dt>Compiler</dt>
                            <dd>{selected.engine}</dd>
                            <dt>Files updated</dt>
                            <dd>
                                {detail
                                    ? new Date(
                                          detail.template.updated,
                                      ).toLocaleDateString("en-GB")
                                    : "Loading..."}
                            </dd>
                            <dt>License</dt>
                            <dd>
                                {detail?.template.license ||
                                    "Not specified in template metadata"}
                            </dd>
                            <dt>About</dt>
                            <dd>{selected.description.en}</dd>
                        </dl>
                        <div className="template-detail-tags">
                            {selected.tags.map((value) => (
                                <span key={value}>{value}</span>
                            ))}
                        </div>
                        <p className="template-library-note">
                            Open as Template creates a separate project in your
                            account. Your edits keep the original template
                            unchanged.
                        </p>
                    </section>
                    <section
                        className="template-document-pane"
                        aria-label={
                            pane === "pdf" ? "Template PDF" : "Template source"
                        }
                    >
                        {pane === "source" ? (
                            sourceError ? (
                                <p role="alert">{sourceError}</p>
                            ) : !detail ? (
                                <p role="status">Loading source...</p>
                            ) : (
                                <>
                                    <label className="template-source-select">
                                        Source file
                                        <select
                                            aria-label="Template source file"
                                            value={sourceFile}
                                            onChange={(event) =>
                                                setSourceFile(
                                                    event.target.value,
                                                )
                                            }
                                        >
                                            {detail.files
                                                .filter(
                                                    (file) =>
                                                        file.content !== null,
                                                )
                                                .map((file) => (
                                                    <option key={file.path}>
                                                        {file.path}
                                                    </option>
                                                ))}
                                        </select>
                                    </label>
                                    <pre tabIndex={0}>
                                        <code>
                                            {
                                                detail.files.find(
                                                    (file) =>
                                                        file.path ===
                                                        sourceFile,
                                                )?.content
                                            }
                                        </code>
                                    </pre>
                                </>
                            )
                        ) : pdfError ? (
                            <div
                                className="template-preview-error"
                                role="alert"
                            >
                                <p>{pdfError}</p>
                                <button
                                    onClick={() =>
                                        setRetry((value) => value + 1)
                                    }
                                >
                                    Retry preview
                                </button>
                            </div>
                        ) : (
                            <>
                                {!pdf && (
                                    <p
                                        className="template-preview-progress"
                                        role="status"
                                    >
                                        Compiling this template on the server...
                                    </p>
                                )}
                                <PdfViewer
                                    data={pdf}
                                    stale={false}
                                    language="en"
                                />
                            </>
                        )}
                    </section>
                </div>
            ) : (
                <>
                    {!category && !query.trim() && !tag ? (
                        <div className="template-category-grid">
                            {catalog.categories.map((item) => {
                                const Icon =
                                    categoryIcons[item.id] || LayoutTemplate;
                                const count = catalog.templates.filter(
                                    (template) => template.category === item.id,
                                ).length;
                                return (
                                    <button
                                        className="template-category-card"
                                        key={item.id}
                                        onClick={() => {
                                            setCategory(item.id);
                                            setTag("");
                                        }}
                                    >
                                        <div
                                            className="template-category-art"
                                            aria-hidden="true"
                                        >
                                            <Icon size={66} />
                                            <span className="template-category-paper">
                                                <i />
                                                <i />
                                                <i />
                                                <i />
                                            </span>
                                        </div>
                                        <span className="template-category-title">
                                            {item.en}
                                            <ArrowRight size={17} />
                                        </span>
                                        <p>
                                            {categoryDescriptions[item.id] ||
                                                "Ready-to-use LaTeX documents for your next project."}
                                        </p>
                                        <small>{count} templates</small>
                                    </button>
                                );
                            })}
                        </div>
                    ) : (
                        <>
                            <p className="template-results-count" role="status">
                                {templates.length} templates
                            </p>
                            <div className="template-gallery-grid">
                                {templates.map((template) => (
                                    <button
                                        className="template-gallery-card"
                                        key={template.id}
                                        onClick={() => {
                                            setSelected(template);
                                            setPane("pdf");
                                        }}
                                    >
                                        <Thumbnail
                                            template={template}
                                            language={language}
                                        />
                                        <h2>{template.title.en}</h2>
                                        <p>{template.description.en}</p>
                                        <small>
                                            {template.engine} / Built-in
                                            template
                                        </small>
                                    </button>
                                ))}
                            </div>
                            {!templates.length && (
                                <div className="writing-empty">
                                    No templates match these filters.
                                    <button
                                        onClick={() => {
                                            setQuery("");
                                            setTag("");
                                        }}
                                    >
                                        Clear filters
                                    </button>
                                </div>
                            )}
                        </>
                    )}
                </>
            )}
        </section>
    );
}
