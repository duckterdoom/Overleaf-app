"use client";
import type { Classroom, Submission } from "../../lib/writing/types";
import {
    assessmentGroup,
    assessmentLabels,
} from "../../lib/writing/assessment";

export default function SubmissionTable({
    submissions,
    courses,
    tutor,
    onOpen,
}: {
    submissions: Submission[];
    courses: Classroom[];
    tutor: boolean;
    onOpen: (id: string) => void;
}) {
    const courseById = new Map(courses.map((course) => [course.id, course]));
    const showTask = submissions.some(
        (row) => assessmentGroup(row.assignment.category) === "ielts",
    );
    return (
        <div
            className="writing-table-wrap submission-table-wrap"
            role="region"
            aria-label="Student submissions"
            tabIndex={0}
        >
            <table className="writing-table submission-table">
                <thead>
                    <tr>
                        <th scope="col">Course</th>
                        <th scope="col">Student</th>
                        <th scope="col">Student code</th>
                        <th scope="col">Exercise</th>
                        <th scope="col">Assessment type</th>
                        {showTask && <th scope="col">Task</th>}
                        <th scope="col">Attempt</th>
                        <th scope="col">Submitted at</th>
                        <th scope="col">Status</th>
                        <th scope="col" aria-label="Actions" />
                    </tr>
                </thead>
                <tbody>
                    {submissions.map((row) => {
                        const course = row.assignment.classId
                            ? courseById.get(row.assignment.classId)
                            : undefined;
                        const member = course?.students?.find(
                            (student) => student.id === row.studentId,
                        );
                        const category = assessmentGroup(
                            row.assignment.category,
                        );
                        return (
                            <tr key={row.id}>
                                <td data-label="Course">{course?.title || ""}</td>
                                <td data-label="Student">
                                    <strong>
                                        {member?.displayName || row.studentName}
                                    </strong>
                                </td>
                                <td data-label="Student code">{member?.studentCode || ""}</td>
                                <td data-label="Exercise">
                                    <strong>{row.assignment.title}</strong>
                                </td>
                                <td data-label="Assessment type">{assessmentLabels[category]}</td>
                                {showTask && (
                                    <td data-label="Task">
                                        {category === "ielts"
                                            ? row.assignment.tasks
                                                  .map((task) => `Task ${task}`)
                                                  .join(", ")
                                            : ""}
                                    </td>
                                )}
                                <td data-label="Attempt">{row.attempt}</td>
                                <td data-label="Submitted at">
                                    <time dateTime={row.submittedAt}>
                                        {new Date(
                                            row.submittedAt,
                                        ).toLocaleString("en-GB")}
                                    </time>
                                    {row.late && <small>Late</small>}
                                </td>
                                <td data-label="Status">
                                    <span className="writing-tag">
                                        {row.result
                                            ? "Published"
                                            : row.tutorId
                                              ? "Awaiting assessment"
                                              : "Personal practice"}
                                    </span>
                                </td>
                                <td data-label="Actions">
                                    <button aria-label={tutor ? "Open assessment" : "View submission"} onClick={() => onOpen(row.id)}>
                                        {tutor ? "Assess" : "View"}
                                    </button>
                                </td>
                            </tr>
                        );
                    })}
                </tbody>
            </table>
        </div>
    );
}
