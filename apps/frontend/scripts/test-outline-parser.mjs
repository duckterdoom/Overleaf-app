import assert from 'node:assert/strict';
import {parseOutline,flattenOutline} from '../lib/outline.ts';
const source=String.raw`% \section{Commented out}
\section[Short]{Introduction with \textbf{bold {text}}}
\subsection{Background}
\subsubsection*{Details}
\section{Methods}
\subsection{Training}
\paragraph{Example}
\section{Methods}
\begin{verbatim}
\section{Literal example}
\end{verbatim}
\section{Escaped \% percent}
\section{}
`;
const roots=parseOutline(source);
assert.equal(roots.length,4);
assert.equal(roots[0].title,'Introduction with bold text');
assert.equal(roots[0].children[0].title,'Background');
assert.equal(roots[0].children[0].children[0].title,'Details');
assert.equal(roots[1].children[0].children[0].title,'Example');
assert.notEqual(roots[1].id,roots[2].id);
assert.equal(roots[3].title,'Escaped % percent');
assert.equal(source.slice(roots[1].start).startsWith('\\section{Methods}'),true);
assert.deepEqual(flattenOutline(parseOutline('% inserted before headings\n'+source)).map(n=>n.id),flattenOutline(roots).map(n=>n.id));
assert.equal(parseOutline('\\subsubsection{Orphan child}')[0].title,'Orphan child');
assert.equal(parseOutline('\\section{Not closed').length,0);
assert.equal(parseOutline('\\\\section{Escaped command}').length,0);
console.log('OUTLINE_PARSER_OK hierarchy, nested titles, starred/short headings, comments/literals, stable duplicate IDs and offsets');
