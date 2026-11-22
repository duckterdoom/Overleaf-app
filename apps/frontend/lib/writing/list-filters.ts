import type { Assignment, Classroom, Submission } from "./types";
import { assessmentGroup } from "./assessment";

export type WritingFilters = {
    query: string;
    category: string;
    course: string;
    task: string;
    status: string;
    period: string;
};
export const emptyWritingFilters = (): WritingFilters => ({
    query: "",
    category: "",
    course: "",
    task: "",
    status: "",
    period: "",
});
export function matchesSearch(query: string, ...values: unknown[]): boolean {
    const normalize = (value: unknown) =>
        String(value ?? "")
            .normalize("NFD")
            .replace(/[\u0300-\u036f]/g, "")
            .replace(/đ/g, "d")
            .replace(/Đ/g, "D")
            .toLowerCase();
    const terms = normalize(query).trim().split(/\s+/).filter(Boolean);
    const text = values.map(normalize).join(" ");
    return terms.every((term) => text.includes(term));
}
export function matchesAssignment(
    assignment: Assignment,
    filters: WritingFilters,
    course?: Classroom,
    submitted = false,
): boolean {
    const statuses:Record<string,boolean>={draft:!assignment.published,assigned:assignment.published,submitted,'not-submitted':!submitted};
    return (
        matchesSearch(
            filters.query,
            assignment.title,
            assignment.description,
            assignment.tutorName,
            course?.title,
        ) &&
        (!filters.category ||
            assessmentGroup(assignment.category) === filters.category) &&
        (!filters.course ||
            (filters.course === "none"
                ? !assignment.classId
                : assignment.classId === filters.course)) &&
        (!filters.task ||
            (assessmentGroup(assignment.category) === "ielts" &&
                assignment.tasks.includes(Number(filters.task)))) &&
        (!filters.status || statuses[filters.status] === true)
    );
}
export function matchesSubmission(
    row: Submission,
    filters: WritingFilters,
    course?: Classroom,
    now = Date.now(),
): boolean {
    const member = course?.students?.find(
        (student) => student.id === row.studentId,
    );
    const assignmentFilters = { ...filters, query: "", status: "" };
    const status = row.result
        ? "published"
        : row.tutorId
          ? "awaiting"
          : "personal";
    const days = Number(filters.period);
    const submittedAt = new Date(row.submittedAt).getTime();
    return (
        matchesAssignment(row.assignment, assignmentFilters, course) &&
        matchesSearch(
            filters.query,
            row.studentName,
            member?.displayName,
            member?.studentCode,
            row.assignment.title,
            course?.title,
        ) &&
        (!filters.status || filters.status === status) &&
        (!days ||
            (Number.isFinite(submittedAt) &&
                submittedAt <= now &&
                submittedAt >= now - days * 86400000))
    );
}
