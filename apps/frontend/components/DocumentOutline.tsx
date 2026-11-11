"use client";
import { memo, useEffect, useMemo, useRef, useState, type MutableRefObject, type RefObject } from "react";
import type { EditorView } from "@codemirror/view";
import { ChevronRight } from "lucide-react";
import { flattenOutline, parseOutline, type OutlineNode } from "../lib/outline";

export default memo(function DocumentOutline({ source, editor, cursor, language }: {
    source: string;
    editor: RefObject<EditorView | null>;
    cursor: MutableRefObject<(position: number) => void>;
    language: string;
}) {
    const nodes = useMemo(() => parseOutline(source), [source]);
    const flat = useMemo(() => flattenOutline(nodes), [nodes]);
    const [collapsed, setCollapsed] = useState<Set<string>>(new Set());
    const [open, setOpen] = useState(true), [active, setActive] = useState<string | null>(null);
    const [focused, setFocused] = useState<string | null>(null);
    const rows = useRef(new Map<string, HTMLDivElement>());
    const vi = language !== "en";
    useEffect(() => {
        const follow = (position: number) => {
            let selected: string | null = null;
            for (const node of flat) { if (node.start > position) break; selected = node.id; }
            setActive(value => value === selected ? value : selected);
        };
        cursor.current = follow;
        follow(editor.current?.state.selection.main.head || 0);
        return () => { if (cursor.current === follow) cursor.current = () => {}; };
    }, [cursor, editor, flat]);
    const visible = (items: OutlineNode[]): OutlineNode[] => items.flatMap(item => [item,
        ...(collapsed.has(item.id) ? [] : visible(item.children))]);
    const current = visible(nodes);
    const toggle = (id: string) => setCollapsed(value => {
        const next = new Set(value); next.has(id) ? next.delete(id) : next.add(id); return next;
    });
    const focus = (id: string | undefined) => {
        if (!id) return;
        setFocused(id);
        const row = rows.current.get(id);
        if (row) row.focus();
        else requestAnimationFrame(() => rows.current.get(id)?.focus());
    };
    function navigate(node: OutlineNode) {
        const view = editor.current;
        if (!view) return;
        view.dispatch({ selection: { anchor: Math.min(node.start, view.state.doc.length) }, scrollIntoView: true });
        view.focus();
        setActive(node.id); setFocused(node.id);
    }
    function branch(items: OutlineNode[], parent?: OutlineNode, depth = 1) {
        return <ul role={parent ? "group" : "tree"} aria-label={parent ? undefined : (vi ? "Cấu trúc tài liệu" : "File outline")}
            className={parent ? "outline-children" : "outline-tree-root"}>
            {items.map(node => {
                const hasChildren = node.children.length > 0, expanded = !collapsed.has(node.id);
                const selected = active === node.id || (!expanded && flattenOutline(node.children).some(child => child.id === active));
                const tabbable = current.some(item => item.id === focused) ? focused : (current.some(item => item.id === active) ? active : current[0]?.id);
                return <li role="none" key={node.id}>
                    <div role="treeitem" className={`outline-row${selected ? " selected" : ""}`} title={node.title}
                        aria-label={node.title}
                        aria-level={depth} aria-selected={selected} aria-expanded={hasChildren ? expanded : undefined}
                        tabIndex={tabbable === node.id ? 0 : -1}
                        ref={element => { if (element) rows.current.set(node.id, element); else rows.current.delete(node.id); }}
                        onFocus={() => setFocused(node.id)}
                        onKeyDown={event => {
                            const index = current.findIndex(item => item.id === node.id);
                            if (event.key === "ArrowDown") focus(current[index + 1]?.id);
                            else if (event.key === "ArrowUp") focus(current[index - 1]?.id);
                            else if (event.key === "Home") focus(current[0]?.id);
                            else if (event.key === "End") focus(current[current.length - 1]?.id);
                            else if (event.key === "ArrowRight") { if (hasChildren) { if (!expanded) toggle(node.id); else focus(node.children[0].id); } }
                            else if (event.key === "ArrowLeft") { if (hasChildren && expanded) toggle(node.id); else focus(parent?.id); }
                            else if (event.key === "Enter" || event.key === " ") navigate(node);
                            else return;
                            event.preventDefault(); event.stopPropagation();
                        }}>
                        {hasChildren ? <button className="outline-toggle" tabIndex={-1} aria-label={`${expanded ? (vi ? "Thu gọn" : "Collapse") : (vi ? "Mở rộng" : "Expand")} ${node.title}`}
                            onClick={() => toggle(node.id)}><ChevronRight size={14} className={expanded ? "expanded" : ""} /></button>
                            : <span className="outline-toggle-spacer" />}
                        <button className="outline-title" tabIndex={-1} onClick={() => navigate(node)}>{node.title}</button>
                    </div>
                    {hasChildren && expanded && branch(node.children, node, depth + 1)}
                </li>;
            })}
        </ul>;
    }
    return <section className="outline-panel document-outline">
        <button className="outline-heading outline-tree-heading" aria-expanded={open} onClick={() => setOpen(value => !value)}>
            <ChevronRight size={14} className={open ? "expanded" : ""} />
            {vi ? "Cấu trúc tài liệu" : "File outline"}
        </button>
        <div className="outline" hidden={!open}>
            {nodes.length ? branch(nodes) : <p>{vi ? "Thêm section để thấy cấu trúc." : "Add a section to see the outline."}</p>}
        </div>
    </section>;
});
