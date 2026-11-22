"use client";
import type { ReactNode } from "react";
import { Search, X } from "lucide-react";

export function FilterSelect({
    label,
    value,
    onChange,
    options,
}: {
    label: string;
    value: string;
    onChange: (value: string) => void;
    options: { value: string; label: string }[];
}) {
    return (
        <label>
            {label}
            <select
                aria-label={label}
                value={value}
                onChange={(event) => onChange(event.target.value)}
            >
                {options.map((option) => (
                    <option key={option.value} value={option.value}>
                        {option.label}
                    </option>
                ))}
            </select>
        </label>
    );
}

export default function ListFilters({
    label,
    query,
    onQuery,
    children,
    active,
    onReset,
    count,
    total,
}: {
    label: string;
    query: string;
    onQuery: (value: string) => void;
    children?: ReactNode;
    active: boolean;
    onReset: () => void;
    count: number;
    total: number;
}) {
    return (
        <section
            className="collection-filters"
            aria-label={label + " and filters"}
        >
            <div className="collection-filter-fields">
                <label className="collection-search">
                    {label}
                    <span>
                        <Search size={16} aria-hidden="true" />
                        <input
                            type="search"
                            value={query}
                            onChange={(event) => onQuery(event.target.value)}
                            placeholder="Type to search..."
                        />
                    </span>
                </label>
                {children}
                {active && (
                    <button
                        type="button"
                        className="collection-reset"
                        onClick={onReset}
                    >
                        <X size={14} />
                        Clear filters
                    </button>
                )}
            </div>
            <small role="status" aria-live="polite">
                {count} of {total} results
            </small>
        </section>
    );
}
