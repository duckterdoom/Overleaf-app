"use client";
import type { Classroom } from "../../lib/writing/types";
import {
    emptyWritingFilters,
    type WritingFilters,
} from "../../lib/writing/list-filters";
import ListFilters, { FilterSelect } from "./ListFilters";

export default function WritingListFilters({
    value,
    onChange,
    courses,
    mode,
    tutor,
    count,
    total,
}: {
    value: WritingFilters;
    onChange: (value: WritingFilters) => void;
    courses: Classroom[];
    mode: "assignments" | "submissions";
    tutor: boolean;
    count: number;
    total: number;
}) {
    const change = (key: keyof WritingFilters, next: string) =>
        onChange({
            ...value,
            [key]: next,
            ...(key === "category" && next === "research" ? { task: "" } : {}),
        });
    const statuses =
        mode === "submissions"
            ? [
                  { value: "awaiting", label: "Awaiting assessment" },
                  { value: "published", label: "Published" },
                  { value: "personal", label: "Personal practice" },
              ]
            : tutor
              ? [
                    { value: "draft", label: "Draft" },
                    { value: "assigned", label: "Assigned" },
                ]
              : [
                    { value: "submitted", label: "Submitted" },
                    { value: "not-submitted", label: "Not submitted" },
                ];
    return (
        <ListFilters
            label={
                mode === "submissions"
                    ? "Search submissions"
                    : "Search exercises"
            }
            query={value.query}
            onQuery={(query) => change("query", query)}
            active={Object.values(value).some(Boolean)}
            onReset={() => onChange(emptyWritingFilters())}
            count={count}
            total={total}
        >
            <FilterSelect
                label="Assessment type"
                value={value.category}
                onChange={(next) => change("category", next)}
                options={[
                    { value: "", label: "All types" },
                    { value: "ielts", label: "IELTS Task" },
                    { value: "research", label: "Research" },
                ]}
            />
            <FilterSelect
                label="Course"
                value={value.course}
                onChange={(next) => change("course", next)}
                options={[
                    { value: "", label: "All courses" },
                    { value: "none", label: "No course" },
                    ...courses.map((course) => ({
                        value: course.id,
                        label: course.title,
                    })),
                ]}
            />
            {value.category !== "research" && (
                <FilterSelect
                    label="IELTS task"
                    value={value.task}
                    onChange={(next) => change("task", next)}
                    options={[
                        { value: "", label: "All tasks" },
                        { value: "1", label: "Task 1" },
                        { value: "2", label: "Task 2" },
                    ]}
                />
            )}
            <FilterSelect
                label="Status"
                value={value.status}
                onChange={(next) => change("status", next)}
                options={[{ value: "", label: "All statuses" }, ...statuses]}
            />
            {mode === "submissions" && (
                <FilterSelect
                    label="Submitted"
                    value={value.period}
                    onChange={(next) => change("period", next)}
                    options={[
                        { value: "", label: "Any time" },
                        { value: "7", label: "Last 7 days" },
                        { value: "30", label: "Last 30 days" },
                    ]}
                />
            )}
        </ListFilters>
    );
}
