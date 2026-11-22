"use client";
import { Plus, X } from "lucide-react";

export default function LibraryCategoryFilter({
    value,
    options,
    onChange,
}: {
    value: string[] | null;
    options: { key: string; label: string }[];
    onChange: (value: string[] | null) => void;
}) {
    const labels = new Map(options.map((o) => [o.key, o.label]));
    return (
        <section
            className="rubric-category-selection library-category-filter"
            aria-label="Library assessment categories"
        >
            <div className="rubric-category-heading">
                <strong>Assessment categories</strong>
                <small>
                    {value === null
                        ? "All categories included"
                        : `${value.length} selected · match all`}
                </small>
            </div>
            <div className="rubric-category-chips">
                {value === null ? (
                    <div className="rubric-category-chip active">
                        <span>Whole paper</span>
                        <button
                            type="button"
                            aria-label="Remove whole paper filter"
                            onClick={() => onChange([])}
                        >
                            <X size={14} />
                        </button>
                    </div>
                ) : (
                    value.map((key) => (
                        <div className="rubric-category-chip active" key={key}>
                            <span>{labels.get(key) || key}</span>
                            <button
                                type="button"
                                aria-label={`Remove category filter ${key}`}
                                onClick={() =>
                                    onChange(value.filter((k) => k !== key))
                                }
                            >
                                <X size={14} />
                            </button>
                        </div>
                    ))
                )}
            </div>
            {value !== null && (
                <div className="rubric-category-options">
                    <button type="button" onClick={() => onChange(null)}>
                        Use whole paper
                    </button>
                    {options
                        .filter((o) => !value.includes(o.key))
                        .map((o) => (
                            <button
                                type="button"
                                key={o.key}
                                onClick={() => onChange([...value, o.key])}
                            >
                                <Plus size={13} />
                                {o.label}
                            </button>
                        ))}
                </div>
            )}
            <p className="writing-muted">
                {!options.length
                    ? "No assessment categories configured for this type yet."
                    : value === null
                      ? "Remove Whole paper to filter by one or several categories. No rubric selection is required."
                      : value.length
                        ? "Showing questions that include every selected category."
                        : "Choose categories to combine, or return to Whole paper."}
            </p>
        </section>
    );
}
