"use client";
import type { Assignment, WritingBlock } from "../../lib/writing/types";

export default function WritingAnswer({
    task,
    value,
    readOnly,
    onChange,
    onSave,
}: {
    assignment: Assignment;
    task: number;
    value: string;
    blocks?: WritingBlock[];
    readOnly: boolean;
    onChange: (text: string, blocks?: WritingBlock[]) => void;
    onSave: () => void;
}) {
    return (
        <div className="writing-answer-editor writing-blank-paper">
            <textarea
                className="writing-whole-answer"
                aria-label={`Answer Task ${task}`}
                spellCheck
                readOnly={readOnly}
                value={value}
                onChange={(e) => onChange(e.target.value)}
                onKeyDown={(e) => {
                    if ((e.ctrlKey || e.metaKey) && e.key === "s") {
                        e.preventDefault();
                        onSave();
                    }
                }}
            />
        </div>
    );
}
