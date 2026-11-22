"use client";
import {X} from 'lucide-react';
import type {Assignment, Overview} from '../../lib/writing/types';
import PromptDocument from './PromptDocument';

export default function ExerciseDetails({assignment, overview, language, onClose}: {
    assignment: Assignment;
    overview: Overview;
    language: string;
    onClose: () => void;
}) {
    const students = assignment.classId
        ? overview.classes.find(course => course.id === assignment.classId)?.students || []
        : overview.students || [];
    return <section className="exercise-inspection" aria-label="Exercise details">
        <header>
            <div><small>EXERCISE DETAILS</small><h2>{assignment.title}</h2></div>
            <button type="button" onClick={onClose} aria-label="Close exercise details"><X size={18}/> Close</button>
        </header>
        <div className="exercise-inspection-body" tabIndex={0} aria-label="Question and student progress">
            {assignment.description && <p className="exercise-description">{assignment.description}</p>}
            {assignment.tasks.map(number => <section className="exercise-inspection-question" key={number}>
                <h3>{!assignment.category || assignment.category === 'ielts' ? `Task ${number}` : assignment.category === 'report' ? 'Report' : 'Research'}</h3>
                <p>{assignment.prompts[String(number)]}</p>
                {assignment.promptFiles?.[String(number)] && <PromptDocument focused file={assignment.promptFiles[String(number)]} language={language}/>}
            </section>)}
            <section className="exercise-inspection-progress">
                <h3>Student progress <span>{students.length} students</span></h3>
                <div className="exercise-progress-table"><table>
                    <thead><tr><th scope="col">Student</th><th scope="col">Status</th><th scope="col">Submissions</th></tr></thead>
                    <tbody>{students.map(person => {
                        const submissions = overview.submissions.filter(item => item.assignmentId === assignment.id && item.studentId === person.id);
                        return <tr key={person.id}><td>{person.name}</td><td><span className={`exercise-progress-status ${submissions.length ? 'submitted' : ''}`}>{submissions.length ? 'Submitted' : 'Not submitted'}</span></td><td>{submissions.length}</td></tr>;
                    })}</tbody>
                </table></div>
                {!students.length && <p>No students are assigned to this exercise yet.</p>}
            </section>
        </div>
    </section>;
}
