import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import vm from 'node:vm';
import {createRequire} from 'node:module';
import {createElement} from 'react';
import {renderToStaticMarkup} from 'react-dom/server';
import ts from 'typescript';

const filename=path.resolve('components/writing/GuideMarkdown.tsx');
const compiled=ts.transpileModule(fs.readFileSync(filename,'utf8'),{compilerOptions:{module:ts.ModuleKind.CommonJS,jsx:ts.JsxEmit.ReactJSX,target:ts.ScriptTarget.ES2022}}).outputText;
const scope={exports:{},require:createRequire(filename)};vm.runInNewContext(compiled,scope,{filename});
const {GuideContent,guideHeadings}=scope.exports;
const content='# Heading\nBody must stay beside the heading.\n\n*Italic* and **bold**.\n\n1. First\n2. Second\n\n| A | B |\n| --- | --- |\n| Kept | Intact |\n\n+-----+-----+\n\n| Box | Value |\n\n| | continuation |\n\n+-----+-----+\n\n```\n# Not a heading\nconst kept = 1;\n```\n\n<script>window.bad=true</script>\n\n<img src=x onerror=bad()>\n\n[Unsafe](javascript:bad)\n\n> Quote kept';
const html=renderToStaticMarkup(createElement(GuideContent,{content}));
for(const text of ['Body must stay beside','continuation','const kept = 1;','Quote kept'])assert.ok(html.includes(text));
for(const tag of ['table','ol','em','strong','pre','blockquote'])assert.ok(html.includes('<'+tag));
assert.equal(guideHeadings(content).length,1);
assert.ok(!html.includes('<script')&&!html.includes('<img')&&!html.includes('href="javascript:'));
assert.ok(html.includes('&lt;script&gt;'));
const sources=path.resolve('../backend/src/latex_backend/resources/writing/guides');
const files=fs.readdirSync(sources).filter(f=>f.endsWith('.md'));assert.equal(files.length,23);
for(const file of files){const md=fs.readFileSync(path.join(sources,file),'utf8');const rendered=renderToStaticMarkup(createElement(GuideContent,{content:md}));assert.ok(rendered.length>1000,file);assert.ok(!rendered.includes('\ufffd'),file);}
console.log('GUIDE_MARKDOWN_OK headings, tables, ASCII boxes, lists, emphasis, code, XSS and all 23 guide/sample sources');

const asset='/api/writing/guide-assets/'+'a'.repeat(64)+'.jpg';
const visual=renderToStaticMarkup(createElement(GuideContent,{content:`![Topic chart](${asset})`}));
assert.ok(visual.includes('<img')&&visual.includes('alt="Topic chart"')&&visual.includes(asset));
for(const url of ['javascript:alert(1)','https://tracking.example/x.png','data:image/svg+xml,evil','/api/writing/guide-assets/../../private.png']){
 const rejected=renderToStaticMarkup(createElement(GuideContent,{content:`![Bad](${url})`}));assert.ok(!rejected.includes('<img'));
}
