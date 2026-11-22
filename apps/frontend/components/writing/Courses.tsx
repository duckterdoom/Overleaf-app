"use client";
import { useEffect, useState, type ReactNode } from "react";
import { ArrowLeft, Eye, Plus, UsersRound, X } from "lucide-react";
import { type Classroom, type CourseStudent } from "../../lib/writing/types";
import { useWritingApi } from "../../lib/writing/context";
import ListFilters,{FilterSelect} from "./ListFilters";
import {matchesSearch} from "../../lib/writing/list-filters";
import { errorText } from "../../lib/parity";

function CoursePanel({
    open,
    title,
    busy,
    onClose,
    children,
}: {
    open: boolean;
    title: string;
    busy: boolean;
    onClose: () => void;
    children: ReactNode;
}) {
    if (!open) return null;
    return (
        <section className="course-inline-panel" aria-label={title}>
            <header>
                <h2>{title}</h2>
                <button
                    type="button"
                    aria-label="Close form"
                    disabled={busy}
                    onClick={onClose}
                >
                    <X size={18} />
                </button>
            </header>
            {children}
        </section>
    );
}
export default function Courses({
    courses,
    tutor,
    editable,
    onRefresh,
}: {
    courses: Classroom[];
    tutor: boolean;
    editable: boolean;
    onRefresh: () => Promise<void>;
}) {
    const { json } = useWritingApi();
    const [title, setTitle] = useState(""),
        [editing, setEditing] = useState<Classroom | null>(null),
        [formOpen, setFormOpen] = useState(false);
    const [selected, setSelected] = useState<string | null>(null),
        [email, setEmail] = useState(""),
        [code, setCode] = useState(""),
        [search, setSearch] = useState(""),
        [adding, setAdding] = useState(false);
    const [studentEdit, setStudentEdit] = useState<CourseStudent | null>(null),
        [displayName, setDisplayName] = useState(""),
        [studentCode, setStudentCode] = useState(""),
        [note, setNote] = useState("");
    const [error, setError] = useState(""),
        [notice, setNotice] = useState(""),
        [busy, setBusy] = useState(false);
    const [courseQuery,setCourseQuery]=useState(''),[enrollment,setEnrollment]=useState(''),[codeFilter,setCodeFilter]=useState('');
    const visibleCourses=courses.filter(row=>matchesSearch(courseQuery,row.title,row.tutorName)&&(!enrollment||(enrollment==='enrolled'?row.studentCount>0:row.studentCount===0)));
    const course = courses.find((row) => row.id === selected);
    useEffect(() => {
        if (!editable) {
            setFormOpen(false);
            setStudentEdit(null);
            setAdding(false);
        }
    }, [editable]);
    async function run(fn: () => Promise<unknown>) {
        if (!editable) return false;
        setBusy(true);
        setError("");
        setNotice("");
        try {
            await fn();
            await onRefresh();
            return true;
        } catch (e) {
            setError(errorText(e));
            return false;
        } finally {
            setBusy(false);
        }
    }
    function editCourse(value: Classroom | null) {
        setEditing(value);
        setTitle(value?.title || "");
        setError("");
        setFormOpen(true);
    }
    function editStudent(value: CourseStudent) {
        setStudentEdit(value);
        setDisplayName(value.displayName || value.name);
        setStudentCode(value.studentCode || "");
        setNote(value.note || "");
        setError("");
    }
    const members=(course?.students||[]).filter(student=>matchesSearch(search,student.name,student.displayName,student.email,student.studentCode)&&(!codeFilter||(codeFilter==='with'?!!student.studentCode?.trim():!student.studentCode?.trim())));
    return (
        <div className="writing-courses course-workspace">
            {error && !formOpen && !studentEdit && (
                <p className="writing-error" role="alert">
                    {error}
                </p>
            )}
            {notice && (
                <p className="writing-notice" role="status">
                    {notice}
                </p>
            )}
            <CoursePanel
                open={formOpen}
                title={
                    tutor
                        ? editing
                            ? "Edit course"
                            : "Create course"
                        : "Join course"
                }
                busy={busy}
                onClose={() => setFormOpen(false)}
            >
                <form
                    onSubmit={(e) => {
                        e.preventDefault();
                        void run(async () => {
                            if (tutor)
                                await json(
                                    "/writing/courses" +
                                        (editing ? "/" + editing.id : ""),
                                    {
                                        method: editing ? "PUT" : "POST",
                                        body: JSON.stringify({
                                            title,
                                            kind: "course",
                                            revision: editing?.revision || 0,
                                        }),
                                    },
                                );
                            else
                                await json("/writing/classes/join", {
                                    method: "POST",
                                    body: JSON.stringify({ code }),
                                });
                        }).then((ok) => {
                            if (ok) {
                                setFormOpen(false);
                                setTitle("");
                                setCode("");
                            }
                        });
                    }}
                >
                    {tutor ? (
                        <div className="writing-form-grid">
                            <label>
                                Name
                                <input
                                    aria-label="Course name"
                                    autoFocus
                                    required
                                    maxLength={160}
                                    value={title}
                                    onChange={(e) => setTitle(e.target.value)}
                                />
                            </label>
                        </div>
                    ) : (
                        <label>
                            Join code
                            <input
                                aria-label="Course join code"
                                autoFocus
                                required
                                value={code}
                                onChange={(e) => setCode(e.target.value)}
                            />
                        </label>
                    )}
                    {error && (
                        <p role="alert" className="writing-error">
                            {error}
                        </p>
                    )}
                    <footer>
                        <button
                            type="button"
                            disabled={busy}
                            onClick={() => setFormOpen(false)}
                        >
                            Cancel
                        </button>
                        <button className="primary" disabled={busy}>
                            {tutor ? "Save course" : "Join"}
                        </button>
                    </footer>
                </form>
            </CoursePanel>
            <CoursePanel
                open={!!studentEdit}
                title="Edit student details"
                busy={busy}
                onClose={() => setStudentEdit(null)}
            >
                <form
                    onSubmit={(e) => {
                        e.preventDefault();
                        if (!course || !studentEdit) return;
                        void run(() =>
                            json(
                                `/writing/courses/${course.id}/members/${studentEdit.id}`,
                                {
                                    method: "PATCH",
                                    body: JSON.stringify({
                                        displayName,
                                        studentCode,
                                        note,
                                        revision: studentEdit.revision || 0,
                                    }),
                                },
                            ),
                        ).then((ok) => {
                            if (ok) {
                                setStudentEdit(null);
                                setNotice(
                                    "Student details saved for this course.",
                                );
                            }
                        });
                    }}
                >
                    <p>
                        Course: <strong>{course?.title}</strong>
                    </p>
                    <label>
                        Display name in this course
                        <input
                            aria-label="Student display name"
                            autoFocus
                            maxLength={160}
                            value={displayName}
                            onChange={(e) => setDisplayName(e.target.value)}
                        />
                    </label>
                    <label>
                        Student code
                        <input
                            aria-label="Student code"
                            maxLength={80}
                            value={studentCode}
                            onChange={(e) => setStudentCode(e.target.value)}
                        />
                    </label>
                    <label>
                        Course notes
                        <textarea
                            aria-label="Student course notes"
                            rows={4}
                            maxLength={2000}
                            value={note}
                            onChange={(e) => setNote(e.target.value)}
                        />
                    </label>
                    {error && (
                        <p role="alert" className="writing-error">
                            {error}
                        </p>
                    )}
                    <footer>
                        <button
                            type="button"
                            disabled={busy}
                            onClick={() => setStudentEdit(null)}
                        >
                            Cancel
                        </button>
                        <button className="primary" disabled={busy}>
                            Save student details
                        </button>
                    </footer>
                </form>
            </CoursePanel>
            {course ? (
                <section className="course-detail">
                    <button
                        className="course-back"
                        onClick={() => {
                            setSelected(null);
                            setAdding(false);
                            setSearch("");
                            setError("");
                        }}
                    >
                        <ArrowLeft size={16} />
                        All courses
                    </button>
                    <header className="course-detail-heading">
                        <div>
                            <span className="writing-tag">Course</span>
                            <h2>{course.title}</h2>
                            <p>
                                {course.studentCount} students / Tutor:{" "}
                                {course.tutorName}
                            </p>
                        </div>
                        {tutor && (
                            <div className="course-code">
                                <small>JOIN CODE</small>
                                <strong>{course.joinCode}</strong>
                            </div>
                        )}
                        {editable && tutor && (
                            <button onClick={() => editCourse(course)}>
                                Edit course
                            </button>
                        )}
                    </header>
                    {tutor && (
                        <>
                            <div className="course-roster-toolbar">
                                <div>
                                    <h3>Students</h3>
                                    <p>
                                        Manage enrollment and course-specific
                                        student details.
                                    </p>
                                </div>
                                <input
                                    type="search"
                                    aria-label="Search students"
                                    placeholder="Search name, email or student code"
                                    value={search}
                                    onChange={(e) => setSearch(e.target.value)}
                                />
                                <FilterSelect label="Student code" value={codeFilter} onChange={setCodeFilter} options={[{value:'',label:'All students'},{value:'with',label:'With code'},{value:'without',label:'Without code'}]}/>
                                {editable && (
                                    <button
                                        className="primary"
                                        disabled={busy}
                                        onClick={() => setAdding((v) => !v)}
                                    >
                                        <Plus size={16} />
                                        Add student
                                    </button>
                                )}
                            </div>
                            {adding && editable && (
                                <form
                                    className="course-enroll-form"
                                    onSubmit={(e) => {
                                        e.preventDefault();
                                        void run(() =>
                                            json(
                                                `/writing/courses/${course.id}/members`,
                                                {
                                                    method: "POST",
                                                    body: JSON.stringify({
                                                        email,
                                                    }),
                                                },
                                            ),
                                        ).then((ok) => {
                                            if (ok) {
                                                setEmail("");
                                                setAdding(false);
                                                setNotice(
                                                    "Student added to this course.",
                                                );
                                            }
                                        });
                                    }}
                                >
                                    <label>
                                        Registered student email
                                        <input
                                            aria-label={`Student email for ${course.title}`}
                                            type="email"
                                            required
                                            value={email}
                                            onChange={(e) =>
                                                setEmail(e.target.value)
                                            }
                                            placeholder="student@example.com"
                                        />
                                    </label>
                                    <button className="primary" disabled={busy}>
                                        Add registered student
                                    </button>
                                    <button
                                        type="button"
                                        disabled={busy}
                                        onClick={() => setAdding(false)}
                                    >
                                        Cancel
                                    </button>
                                </form>
                            )}
                            <div className="course-roster-table">
                                <table>
                                    <thead>
                                        <tr>
                                            <th>Student</th>
                                            <th>Student code</th>
                                            <th>Course notes</th>
                                            <th>Actions</th>
                                        </tr>
                                    </thead>
                                    <tbody>
                                        {members.map((student) => (
                                            <tr key={student.id}>
                                                <td>
                                                    <strong>
                                                        {student.displayName ||
                                                            student.name}
                                                    </strong>
                                                    <small>
                                                        {student.email ||
                                                            student.name}
                                                    </small>
                                                    {student.displayName &&
                                                        student.displayName !==
                                                            student.name && (
                                                            <small>
                                                                Account:{" "}
                                                                {student.name}
                                                            </small>
                                                        )}
                                                </td>
                                                <td>
                                                    {student.studentCode || "-"}
                                                </td>
                                                <td className="course-note-cell">
                                                    {student.note || "-"}
                                                </td>
                                                <td>
                                                    {editable && (
                                                        <div className="course-student-actions">
                                                            <button
                                                                disabled={busy}
                                                                onClick={() =>
                                                                    editStudent(
                                                                        student,
                                                                    )
                                                                }
                                                                aria-label={`Edit student ${student.name}`}
                                                            >
                                                                Edit
                                                            </button>
                                                            <button
                                                                disabled={busy}
                                                                className="danger"
                                                                aria-label={`Remove student ${student.name}`}
                                                                onClick={() => {
                                                                    if (
                                                                        confirm(
                                                                            `Remove ${student.name} from this course?`,
                                                                        )
                                                                    )
                                                                        void run(
                                                                            () =>
                                                                                json(
                                                                                    `/writing/courses/${course.id}/members/${student.id}`,
                                                                                    {
                                                                                        method: "DELETE",
                                                                                    },
                                                                                ),
                                                                        );
                                                                }}
                                                            >
                                                                Remove
                                                            </button>
                                                        </div>
                                                    )}
                                                </td>
                                            </tr>
                                        ))}
                                    </tbody>
                                </table>
                                {!members.length && (
                                    <p className="writing-empty">
                                        {search || codeFilter
                                            ? "No students match your search."
                                            : "No students enrolled yet."}
                                    </p>
                                )}
                            </div>
                        </>
                    )}
                </section>
            ) : (
                <>
                    <div className="course-list-toolbar">
                        <p>{courses.length} courses</p>
                        {editable && (
                            <button
                                className="primary"
                                onClick={() => editCourse(null)}
                            >
                                <Plus size={16} />
                                {tutor ? "Create course" : "Join course"}
                            </button>
                        )}
                    </div>
                    <ListFilters label="Search courses" query={courseQuery} onQuery={setCourseQuery} count={visibleCourses.length} total={courses.length} active={!!courseQuery||!!enrollment} onReset={()=>{setCourseQuery('');setEnrollment('');}}>
                        <FilterSelect label="Enrollment" value={enrollment} onChange={setEnrollment} options={[{value:'',label:'All courses'},{value:'enrolled',label:'With students'},{value:'empty',label:'No students'}]}/>
                    </ListFilters>
                    <div className="course-card-grid">
                        {visibleCourses.map((item) => (
                            <article
                                className="course-summary-card"
                                key={item.id}
                            >
                                <div className="course-card-icon">
                                    <UsersRound size={23} />
                                </div>
                                <span className="writing-tag">Course</span>
                                <h2>{item.title}</h2>
                                <p>
                                    {item.studentCount} students /{" "}
                                    {item.tutorName}
                                </p>
                                <footer>
                                    <button
                                        className="primary"
                                        onClick={() => {
                                            setSelected(item.id);
                                            setSearch("");
                                            setError("");
                                        }}
                                        aria-label={`View course ${item.title}`}
                                    >
                                        <Eye size={16} />
                                        View
                                    </button>
                                    {editable && tutor && (
                                        <>
                                            <button
                                                onClick={() => editCourse(item)}
                                            >
                                                Edit
                                            </button>
                                            <button
                                                disabled={busy}
                                                className="danger"
                                                onClick={() => {
                                                    if (
                                                        confirm(
                                                            "Delete this course?",
                                                        )
                                                    )
                                                        void run(() =>
                                                            json(
                                                                `/writing/courses/${item.id}?revision=${item.revision || 0}`,
                                                                {
                                                                    method: "DELETE",
                                                                },
                                                            ),
                                                        );
                                                }}
                                            >
                                                Delete
                                            </button>
                                        </>
                                    )}
                                </footer>
                            </article>
                        ))}
                    </div>
                    {!visibleCourses.length && (
                        <p className="writing-empty">{courses.length ? "No courses match these filters." : "No courses yet."}</p>
                    )}
                </>
            )}
        </div>
    );
}
