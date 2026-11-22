"use client";

import { useEffect, useRef, useState, type ReactNode } from "react";
import { Check, ChevronDown, LoaderCircle, Play } from "lucide-react";

function ToolbarMenu({
    label,
    children,
}: {
    label: string;
    children: ReactNode;
}) {
    const [open, setOpen] = useState(false);
    const root = useRef<HTMLDivElement>(null);
    const trigger = useRef<HTMLButtonElement>(null);
    useEffect(() => {
        if (!open) return;
        const outside = (event: PointerEvent) => {
            if (!root.current?.contains(event.target as Node)) setOpen(false);
        };
        const escape = (event: KeyboardEvent) => {
            if (event.key === "Escape") {
                setOpen(false);
                trigger.current?.focus();
            }
        };
        document.addEventListener("pointerdown", outside);
        document.addEventListener("keydown", escape);
        return () => {
            document.removeEventListener("pointerdown", outside);
            document.removeEventListener("keydown", escape);
        };
    }, [open]);
    return (
        <div className="compact-toolbar-menu" ref={root}>
            <button
                ref={trigger}
                type="button"
                aria-label={label}
                aria-expanded={open}
                onClick={() => setOpen((value) => !value)}
            >
                {label}
                <ChevronDown size={14} />
            </button>
            {open && (
                <div
                    className="compact-toolbar-popover"
                    role="group"
                    aria-label={label}
                    onClick={(event) => {
                        if (
                            (event.target as HTMLElement).closest(
                                "[data-close-menu]",
                            )
                        )
                            setOpen(false);
                    }}
                >
                    {children}
                </div>
            )}
        </div>
    );
}

type CompileProps = {
    busy: boolean;
    disabled: boolean;
    engine: string;
    autoCompile: boolean;
    onCompile: () => void;
    onEngine: (engine: string) => void;
    onAutoCompile: (enabled: boolean) => void;
};
export default function CompileOptions(props: CompileProps) {
    return (
        <div className="compile-split-button">
            <button
                type="button"
                className="primary compile-button"
                disabled={props.disabled}
                onClick={props.onCompile}
            >
                {props.busy ? (
                    <LoaderCircle size={15} className="spin" />
                ) : (
                    <Play size={14} fill="currentColor" />
                )}
                {props.busy ? "Compiling" : "Recompile"}
            </button>
            <ToolbarMenu label="Compile options">
                <label className="compile-auto-option">
                    <input
                        type="checkbox"
                        checked={props.autoCompile}
                        onChange={(event) =>
                            props.onAutoCompile(event.target.checked)
                        }
                    />
                    Auto compile
                </label>
                <fieldset disabled={props.busy}>
                    <legend>Compiler</legend>
                    {["pdflatex", "xelatex", "lualatex"].map((engine) => (
                        <label key={engine}>
                            <input
                                type="radio"
                                name="compile-engine"
                                value={engine}
                                checked={props.engine === engine}
                                onChange={() => props.onEngine(engine)}
                            />
                            {engine}
                        </label>
                    ))}
                </fieldset>
            </ToolbarMenu>
        </div>
    );
}

export function PreviewFormatMenu({
    value,
    viewer,
    onChange,
}: {
    value: "pdf" | "word" | "logs";
    viewer: boolean;
    onChange: (value: "pdf" | "word") => void;
}) {
    return (
        <ToolbarMenu
            label={
                value === "word"
                    ? "Word preview"
                    : value === "logs"
                      ? "Preview format"
                      : "PDF preview"
            }
        >
            {(["pdf", "word"] as const).map((format) => (
                <button
                    key={format}
                    type="button"
                    data-close-menu
                    disabled={viewer && format === "word"}
                    aria-pressed={value === format}
                    onClick={() => onChange(format)}
                >
                    {format === "pdf" ? "PDF preview" : "Word preview"}
                    {value === format && <Check size={14} />}
                </button>
            ))}
        </ToolbarMenu>
    );
}
