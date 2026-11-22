import { Fragment, type ReactNode } from "react";

export function guideHeadings(content: string) {
    let fence = "";
    return content
        .replace(/\r\n/g, "\n")
        .split("\n")
        .flatMap((line, index) => {
            const marker = /^\s*(`{3,}|~{3,})/.exec(line);
            if (marker) {
                if (!fence) fence = marker[1];
                else if (marker[1].startsWith(fence)) fence = "";
                return [];
            }
            if (fence) return [];
            const match = /^\s{0,3}(#{1,6})\s+(.+?)\s*#*\s*$/.exec(line);
            return match
                ? [{ index, level: match[1].length, title: match[2] }]
                : [];
        });
}
function inline(text: string, depth = 0): ReactNode {
    if (depth > 3) return text;
    const tokens =
        /(\*\*[^*\n]+\*\*|__[^_\n]+__|\*[^*\n]+\*|_[^_\n]+_|`[^`\n]+`|\[[^\]\n]+\]\([^\s)]+\)|\\[\\`*_[\]{}()#+.!|>-])/g;
    return text.split(tokens).map((part, i) => {
        if (/^\\[\\`*_[\]{}()#+.!|>-]$/.test(part))
            return <Fragment key={i}>{part.slice(1)}</Fragment>;
        if (
            (part.startsWith("**") && part.endsWith("**")) ||
            (part.startsWith("__") && part.endsWith("__"))
        )
            return (
                <strong key={i}>{inline(part.slice(2, -2), depth + 1)}</strong>
            );
        if (
            (part.startsWith("*") && part.endsWith("*")) ||
            (part.startsWith("_") && part.endsWith("_"))
        )
            return <em key={i}>{inline(part.slice(1, -1), depth + 1)}</em>;
        if (part.startsWith("`") && part.endsWith("`"))
            return <code key={i}>{part.slice(1, -1)}</code>;
        const link = /^\[([^\]]+)\]\(([^)]+)\)$/.exec(part);
        if (link && /^https?:\/\//i.test(link[2]))
            return (
                <a
                    key={i}
                    href={link[2]}
                    target="_blank"
                    rel="noopener noreferrer"
                >
                    {link[1]}
                </a>
            );
        return <Fragment key={i}>{part}</Fragment>;
    });
}
const cells = (line: string) =>
    line
        .trim()
        .replace(/^\|/, "")
        .replace(/\|$/, "")
        .split(/(?<!\\)\|/)
        .map((c) => c.trim());
const divider = (line: string) =>
    line.includes("|") && cells(line).every((c) => /^:?-{3,}:?$/.test(c));
const listItem = (line: string) => /^(\s*)([-+*]|\d+[.)])\s+(.+)$/.exec(line);

export function GuideContent({ content }: { content: string }) {
    const levels = Array.from(new Set(guideHeadings(content).map(h=>h.level))).sort((a,b)=>a-b);
    const lines = content.replace(/\r\n/g, "\n").split("\n"),
        blocks: ReactNode[] = [];
    let i = 0;
    while (i < lines.length) {
        const start = i,
            line = lines[i];
        if (!line.trim()) {
            i++;
            continue;
        }
        const image = /^\s*!\[([^\]]*)\]\((\/api\/writing\/guide-assets\/[a-f0-9]{64}\.(?:png|jpg))\)\s*$/.exec(line);
        if(image){blocks.push(<figure className="guide-topic-visual" key={start}><img src={image[2]} alt={image[1]||'Writing topic illustration'} loading="lazy"/><figcaption>{image[1]}</figcaption></figure>);i++;continue;}
        const fence = /^\s*(`{3,}|~{3,})/.exec(line);
        if (fence) {
            const code: string[] = [];
            i++;
            while (
                i < lines.length &&
                !lines[i].trimStart().startsWith(fence[1])
            )
                code.push(lines[i++]);
            if (i < lines.length) i++;
            blocks.push(
                <pre key={start}>
                    <code>{code.join("\n")}</code>
                </pre>,
            );
            continue;
        }
        const heading = /^\s{0,3}(#{1,6})\s+(.+?)\s*#*\s*$/.exec(line);
        if (heading) {
            const Tag = `h${Math.min(6, 3 + levels.indexOf(heading[1].length))}` as
                "h2" | "h3" | "h4" | "h5" | "h6";
            blocks.push(
                <Tag key={start} id={`guide-section-${start}`}>
                    {inline(heading[2])}
                </Tag>,
            );
            i++;
            continue;
        }
        if (/^\s*\+[-=+]+\+\s*$/.test(line)) {
            // MarkItDown sources contain boxed tables separated by blank lines, rather
            // than GFM separators. Merge wrapped cell lines between horizontal borders.
            const rows: string[][] = [];
            let chunk: string[][] = [];
            const flush = () => {
                if (chunk.length) {
                    const width = Math.max(...chunk.map((row) => row.length));
                    rows.push(
                        Array.from({ length: width }, (_, c) =>
                            chunk
                                .map((row) => row[c] || "")
                                .filter(Boolean)
                                .join("\n"),
                        ),
                    );
                    chunk = [];
                }
            };
            while (i < lines.length) {
                const current = lines[i];
                if (/^\s*\+[-=+]+\+\s*$/.test(current)) {
                    flush();
                    i++;
                } else if (/^\s*\|.*\|\s*$/.test(current)) {
                    chunk.push(cells(current));
                    i++;
                } else if (!current.trim()) {
                    i++;
                } else break;
            }
            flush();
            const width = Math.max(1, ...rows.map((row) => row.length));
            const caption =
                rows.length > 1 && rows[0].length === 1
                    ? rows.shift()?.[0]
                    : null;
            blocks.push(
                rows.length ? (
                    <div className="guide-table-scroll" key={start}>
                        <table>
                            {caption && <caption>{inline(caption)}</caption>}
                            <tbody>
                                {rows.map((row, r) => (
                                    <tr key={r}>
                                        {row.length === 1 ? (
                                            <td colSpan={width}>
                                                {inline(row[0])}
                                            </td>
                                        ) : (
                                            row.map((cell, c) => (
                                                <td key={c}>{inline(cell)}</td>
                                            ))
                                        )}
                                    </tr>
                                ))}
                            </tbody>
                        </table>
                    </div>
                ) : (
                    <pre key={start}>{lines.slice(start, i).join("\n")}</pre>
                ),
            );
            continue;
        }
        if (
            i + 1 < lines.length &&
            divider(lines[i + 1]) &&
            line.includes("|")
        ) {
            const headers = cells(line),
                rows: string[][] = [];
            i += 2;
            while (
                i < lines.length &&
                lines[i].trim() &&
                lines[i].includes("|")
            )
                rows.push(cells(lines[i++]));
            blocks.push(
                <div className="guide-table-scroll" key={start}>
                    <table>
                        <thead>
                            <tr>
                                {headers.map((h, n) => (
                                    <th key={n}>{inline(h)}</th>
                                ))}
                            </tr>
                        </thead>
                        <tbody>
                            {rows.map((row, n) => (
                                <tr key={n}>
                                    {headers.map((_, c) => (
                                        <td key={c}>{inline(row[c] || "")}</td>
                                    ))}
                                </tr>
                            ))}
                        </tbody>
                    </table>
                </div>,
            );
            continue;
        }
        if (listItem(line)) {
            const entries: {
                indent: number;
                ordered: boolean;
                text: string;
            }[] = [];
            while (i < lines.length) {
                const item = listItem(lines[i]);
                if (!item) break;
                entries.push({
                    indent: item[1].replace(/\t/g, "    ").length,
                    ordered: /\d/.test(item[2]),
                    text: item[3],
                });
                i++;
            }
            function renderList(
                from: number,
                indent: number,
            ): { node: ReactNode; end: number } {
                let cursor = from;
                const children: ReactNode[] = [];
                const ordered = entries[from].ordered;
                while (
                    cursor < entries.length &&
                    entries[cursor].indent === indent &&
                    entries[cursor].ordered === ordered
                ) {
                    const current = cursor++;
                    let nested: ReactNode = null;
                    if (
                        cursor < entries.length &&
                        entries[cursor].indent > indent
                    ) {
                        const child = renderList(
                            cursor,
                            entries[cursor].indent,
                        );
                        nested = child.node;
                        cursor = child.end;
                    }
                    children.push(
                        <li key={current}>
                            {inline(entries[current].text)}
                            {nested}
                        </li>,
                    );
                }
                const Tag = ordered ? "ol" : "ul";
                return { node: <Tag key={from}>{children}</Tag>, end: cursor };
            }
            let cursor = 0;
            const lists: ReactNode[] = [];
            while (cursor < entries.length) {
                const item = renderList(cursor, entries[cursor].indent);
                lists.push(item.node);
                cursor = item.end;
            }
            blocks.push(<Fragment key={start}>{lists}</Fragment>);
            continue;
        }
        if (/^\s*>/.test(line)) {
            const quote: string[] = [];
            while (i < lines.length && /^\s*>/.test(lines[i]))
                quote.push(lines[i++].replace(/^\s*>\s?/, ""));
            blocks.push(
                <blockquote key={start}>{inline(quote.join("\n"))}</blockquote>,
            );
            continue;
        }
        if (/^\s*(?:-{3,}|\*{3,}|_{3,})\s*$/.test(line)) {
            blocks.push(<hr key={start} />);
            i++;
            continue;
        }
        const paragraph = [line];
        i++;
        while (
            i < lines.length &&
            lines[i].trim() &&
            !/^\s*(?:#{1,6}\s|```|~~~|>|!\[)/.test(lines[i]) &&
            !listItem(lines[i]) &&
            !(i + 1 < lines.length && divider(lines[i + 1]))
        )
            paragraph.push(lines[i++]);
        blocks.push(<p key={start}>{inline(paragraph.join("\n"))}</p>);
    }
    return <article className="guide-content">{blocks}</article>;
}
