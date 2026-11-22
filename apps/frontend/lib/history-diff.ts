export type HistorySegment = { text: string; changed: boolean };
export type HistoryLine = {
    number: number;
    text: string;
    segments: HistorySegment[];
};
export type HistoryRow = {
    kind: "same" | "added" | "removed" | "modified";
    before?: HistoryLine;
    after?: HistoryLine;
    endingChanged?: boolean;
};
type Operation = { kind: "same" | "add" | "remove"; text: string };
type Budget = { cells: number; approximate: boolean };

/** Unique-line anchors partition large documents; bounded LCS handles repeated lines. */
function diffTokens(a: string[], b: string[], budget: Budget): Operation[] {
    const output: Operation[] = [];
    function visit(
        a0: number,
        a1: number,
        b0: number,
        b1: number,
        depth: number,
    ) {
        while (a0 < a1 && b0 < b1 && a[a0] === b[b0]) {
            output.push({ kind: "same", text: a[a0++] });
            b0++;
        }
        let suffix = 0;
        while (
            a0 < a1 - suffix &&
            b0 < b1 - suffix &&
            a[a1 - 1 - suffix] === b[b1 - 1 - suffix]
        )
            suffix++;
        const ae = a1 - suffix,
            be = b1 - suffix,
            n = ae - a0,
            m = be - b0;
        if (!n)
            for (let j = b0; j < be; j++)
                output.push({ kind: "add", text: b[j] });
        else if (!m)
            for (let i = a0; i < ae; i++)
                output.push({ kind: "remove", text: a[i] });
        else if ((n + 1) * (m + 1) <= Math.min(300000, budget.cells)) {
            budget.cells -= (n + 1) * (m + 1);
            const width = m + 1,
                table = new Uint32Array((n + 1) * width);
            for (let i = n - 1; i >= 0; i--)
                for (let j = m - 1; j >= 0; j--)
                    table[i * width + j] =
                        a[a0 + i] === b[b0 + j]
                            ? 1 + table[(i + 1) * width + j + 1]
                            : Math.max(
                                  table[(i + 1) * width + j],
                                  table[i * width + j + 1],
                              );
            let i = 0,
                j = 0;
            while (i < n || j < m) {
                if (i < n && j < m && a[a0 + i] === b[b0 + j]) {
                    output.push({ kind: "same", text: a[a0 + i++] });
                    j++;
                } else if (
                    i < n &&
                    (j === m ||
                        table[(i + 1) * width + j] >= table[i * width + j + 1])
                )
                    output.push({ kind: "remove", text: a[a0 + i++] });
                else output.push({ kind: "add", text: b[b0 + j++] });
            }
        } else {
            const left = new Map<string, number>(),
                right = new Map<string, number>();
            for (let i = a0; i < ae; i++)
                left.set(a[i], left.has(a[i]) ? -1 : i);
            for (let j = b0; j < be; j++)
                right.set(b[j], right.has(b[j]) ? -1 : j);
            const candidates: { a: number; b: number; previous: number }[] = [],
                tails: number[] = [];
            if (depth < 16)
                for (let i = a0; i < ae; i++) {
                    const position = right.get(a[i]);
                    if (
                        left.get(a[i]) !== i ||
                        position === undefined ||
                        position < 0
                    )
                        continue;
                    let low = 0,
                        high = tails.length;
                    while (low < high) {
                        const middle = (low + high) >>> 1;
                        if (candidates[tails[middle]].b < position)
                            low = middle + 1;
                        else high = middle;
                    }
                    candidates.push({
                        a: i,
                        b: position,
                        previous: low ? tails[low - 1] : -1,
                    });
                    tails[low] = candidates.length - 1;
                }
            if (tails.length) {
                const anchors: { a: number; b: number }[] = [];
                for (
                    let k = tails[tails.length - 1];
                    k >= 0;
                    k = candidates[k].previous
                )
                    anchors.push(candidates[k]);
                let i = a0,
                    j = b0;
                for (const anchor of anchors.reverse()) {
                    visit(i, anchor.a, j, anchor.b, depth + 1);
                    output.push({ kind: "same", text: a[anchor.a] });
                    i = anchor.a + 1;
                    j = anchor.b + 1;
                }
                visit(i, ae, j, be, depth + 1);
            } else {
                budget.approximate = true;
                for (let i = a0; i < ae; i++)
                    output.push({ kind: "remove", text: a[i] });
                for (let j = b0; j < be; j++)
                    output.push({ kind: "add", text: b[j] });
            }
        }
        for (let i = a1 - suffix; i < a1; i++)
            output.push({ kind: "same", text: a[i] });
    }
    visit(0, a.length, 0, b.length, 0);
    return output;
}
const lines = (text: string) => text.match(/[^\n]*\n|[^\n]+$/g) || [];
const visible = (text: string) => text.replace(/\r?\n$/, "");
const words = (text: string) =>
    text.match(/\s+|[\p{L}\p{M}\p{N}_]+|[^\s\p{L}\p{M}\p{N}_]/gu) || [];
function append(segments: HistorySegment[], text: string, changed: boolean) {
    if (!text) return;
    const last = segments[segments.length - 1];
    if (last?.changed === changed) last.text += text;
    else segments.push({ text, changed });
}
export function buildHistoryDiff(before: string, after: string) {
    const budget: Budget = { cells: 2000000, approximate: false };
    const operations = diffTokens(lines(before), lines(after), budget),
        rows: HistoryRow[] = [];
    let oldNumber = 0,
        newNumber = 0;
    function line(
        text: string,
        side: "before" | "after",
        changed: boolean,
    ): HistoryLine {
        return {
            number: side === "before" ? ++oldNumber : ++newNumber,
            text,
            segments: [{ text: visible(text), changed }],
        };
    }
    for (let i = 0; i < operations.length;) {
        const operation = operations[i];
        if (operation.kind === "same") {
            rows.push({
                kind: "same",
                before: line(operation.text, "before", false),
                after: line(operation.text, "after", false),
            });
            i++;
            continue;
        }
        const removed: string[] = [],
            added: string[] = [];
        while (i < operations.length && operations[i].kind !== "same") {
            const item = operations[i++];
            (item.kind === "remove" ? removed : added).push(item.text);
        }
        for (let j = 0; j < Math.max(removed.length, added.length); j++) {
            const oldLine =
                j < removed.length
                    ? line(removed[j], "before", true)
                    : undefined;
            const newLine =
                j < added.length ? line(added[j], "after", true) : undefined;
            if (oldLine && newLine) {
                const tokens = diffTokens(
                    words(visible(oldLine.text)),
                    words(visible(newLine.text)),
                    budget,
                );
                oldLine.segments = [];
                newLine.segments = [];
                for (const token of tokens) {
                    if (token.kind !== "add")
                        append(
                            oldLine.segments,
                            token.text,
                            token.kind === "remove",
                        );
                    if (token.kind !== "remove")
                        append(
                            newLine.segments,
                            token.text,
                            token.kind === "add",
                        );
                }
            }
            rows.push({
                kind:
                    oldLine && newLine
                        ? "modified"
                        : oldLine
                          ? "removed"
                          : "added",
                before: oldLine,
                after: newLine,
                endingChanged:
                    !!oldLine &&
                    !!newLine &&
                    (oldLine.text.match(/\r?\n$/)?.[0] || "") !==
                        (newLine.text.match(/\r?\n$/)?.[0] || ""),
            });
        }
    }
    const counts = { added: 0, removed: 0, modified: 0 };
    for (const row of rows) if (row.kind !== "same") counts[row.kind]++;
    return { rows, counts, approximate: budget.approximate };
}
