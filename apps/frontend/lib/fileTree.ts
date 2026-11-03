import type { Entry } from "./api";

// Walk siblings together, directories first, before moving to the next branch.
export function orderedTree(entries: Entry[]): Entry[] {
    const directories = new Set(entries.filter((e) => e.directory).map((e) => e.path));
    return [...entries].sort((a, b) => {
        const left = a.path.split("/"), right = b.path.split("/");
        for (let i = 0; i < Math.min(left.length, right.length); i++) {
            if (left[i] === right[i]) continue;
            const ld = directories.has(left.slice(0, i + 1).join("/")) || i < left.length - 1;
            const rd = directories.has(right.slice(0, i + 1).join("/")) || i < right.length - 1;
            return ld !== rd ? (ld ? -1 : 1) : left[i].localeCompare(right[i], undefined, { numeric: true });
        }
        return left.length - right.length;
    });
}
