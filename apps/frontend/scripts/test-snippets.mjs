import ts from "typescript";
import fs from "node:fs";
import assert from "node:assert/strict";
const code = ts.transpileModule(fs.readFileSync("lib/snippets.ts", "utf8"), {
    compilerOptions: {
        module: ts.ModuleKind.ESNext,
        target: ts.ScriptTarget.ES2022,
    },
}).outputText;
const s = await import(
    "data:text/javascript;base64," + Buffer.from(code).toString("base64")
);
assert.equal(s.tex("A&B_%"), String.raw`A\&B\_\%`);
assert.equal(s.citation("a,b,a"), String.raw`\cite{a,b}`);
assert.equal(s.reference("eqref", "eq:a"), String.raw`\eqref{eq:a}`);
assert.throws(() => s.hyperlink("javascript:alert(1)", "x"));
assert.throws(() => s.figure("../escape.png", 0.8, "", ""));
assert.throws(() => s.table(100, 2, "l", "", ""));
const table = s.table(2, 3, "c", "A&B", "tab:test", false);
assert.ok(table.includes(String.raw`Column 1 & Column 2 & Column 3 \\` + "\n"));
assert.ok(table.includes(String.raw`\caption{A\&B}`));
const commented = s.lineEdit("  one\n  two\nthree", 0, 12, "comment");
assert.equal(commented.replacement, "%   one\n%   two");
const un = s.lineEdit(
    commented.replacement,
    0,
    commented.replacement.length,
    "comment",
);
assert.equal(un.replacement, "  one\n  two");
fs.writeFileSync(
    "../../target/parity-snippets.tex",
    String.raw`\documentclass{article}
\usepackage{hyperref,graphicx}
\begin{document}
` +
        table +
        "\n" +
        s.list("enumerate", "One\nTwo") +
        "\n" +
        s.hyperlink("https://example.org/a_b?x=1&y=2", "A&B") +
        "\n" +
        String.raw`\end{document}`,
);
console.log(
    "SNIPPETS_OK escaping, keys, tables, lists, links and multiline comment boundaries",
);
