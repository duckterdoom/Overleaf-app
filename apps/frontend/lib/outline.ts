export type OutlineNode = {
    id: string;
    title: string;
    start: number;
    level: number;
    children: OutlineNode[];
};
const levels = ["part", "chapter", "section", "subsection", "subsubsection", "paragraph", "subparagraph"];
function escaped(text: string, at: number) {
    let count = 0;
    while (at > 0 && text[--at] === "\\") count++;
    return count % 2 === 1;
}
function balanced(text: string, at: number, open: string, close: string) {
    if (text[at] !== open) return null;
    let depth = 1;
    for (let index = at + 1; index < text.length; index++) {
        if (escaped(text, index)) continue;
        if (text[index] === open) depth++;
        else if (text[index] === close && --depth === 0)
            return { value: text.slice(at + 1, index), end: index + 1 };
    }
    return null;
}
function titleText(raw: string) {
    return raw.replace(/\\(?:label|index)\s*\{[^{}]*\}/g, "")
        .replace(/\\([&%_$#{}])/g, "$1")
        .replace(/\\(?:LaTeX|TeX)\b/g, match => match.slice(1))
        .replace(/\\[a-zA-Z]+\*?(?:\[[^\]]*\])?/g, "")
        .replace(/[{}]/g, "").replace(/~/g, " ").replace(/\s+/g, " ").trim();
}
export function parseOutline(source: string): OutlineNode[] {
    // Keep positions in UTF-16 exactly as CodeMirror expects. Mask comments and
    // literal code blocks rather than deleting them and shifting all offsets.
    let masked = source.split("\n").map(line => {
        for (let i = 0; i < line.length; i++)
            if (line[i] === "%" && !escaped(line, i)) return line.slice(0, i) + " ".repeat(line.length - i);
        return line;
    }).join("\n");
    masked = masked.replace(/\\begin\{(verbatim\*?|lstlisting|minted)\}[\s\S]*?\\end\{\1\}/g,
        value => value.replace(/[^\n]/g, " "));
    const roots: OutlineNode[] = [], stack: OutlineNode[] = [];
    const counts = new Map<string, number>();
    const pattern = /\\(part|chapter|section|subsection|subsubsection|paragraph|subparagraph)(?![a-zA-Z])\*?\s*/g;
    for (let match; (match = pattern.exec(masked));) {
        if (escaped(masked, match.index)) continue;
        let at = pattern.lastIndex;
        if (masked[at] === "[") {
            const short = balanced(masked, at, "[", "]");
            if (!short) continue;
            at = short.end;
            while (/\s/.test(masked[at] || "!") && at < masked.length) at++;
        }
        const title = balanced(masked, at, "{", "}");
        if (!title) continue;
        pattern.lastIndex = title.end;
        const level = levels.indexOf(match[1]);
        while (stack.length && stack[stack.length - 1].level >= level) stack.pop();
        const parent = stack[stack.length - 1];
        const text = titleText(title.value) || title.value.trim();
        if (!text) continue;
        const key = `${parent?.id || "root"}/${match[1]}:${encodeURIComponent(text)}`;
        const occurrence = (counts.get(key) || 0) + 1;
        counts.set(key, occurrence);
        const node: OutlineNode = { id: `${key}:${occurrence}`, title: text, start: match.index, level, children: [] };
        (parent ? parent.children : roots).push(node);
        stack.push(node);
    }
    return roots;
}
export function flattenOutline(nodes: OutlineNode[]): OutlineNode[] {
    return nodes.flatMap(node => [node, ...flattenOutline(node.children)]);
}
