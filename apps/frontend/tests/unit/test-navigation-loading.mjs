import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import {pathToFileURL} from 'node:url';
import ts from 'typescript';
import React from 'react';
import {renderToStaticMarkup} from 'react-dom/server';

const folder=path.resolve('node_modules/.cache/navigation-test');
fs.mkdirSync(folder,{recursive:true});
const file=path.join(folder,`navigation-${process.pid}.mjs`);
try {
 fs.writeFileSync(file,ts.transpileModule(fs.readFileSync('components/workspace/WorkspaceNavigation.tsx','utf8'),{
  compilerOptions:{module:ts.ModuleKind.ESNext,target:ts.ScriptTarget.ES2022,jsx:ts.JsxEmit.ReactJSX}
 }).outputText);
 const {default:Navigation}=await import(pathToFileURL(file));
 const shell={onHome(){},onProjects(){},onTemplates(){},projectCount:0,sharedCount:0,templateOpen:false};
 function render(tutor,loading,readonly){return renderToStaticMarkup(React.createElement(Navigation,{tutor,loading,readonly,active:'courses',shell,onWriting(){}}));}
 for(const tutor of [false,true]) {
  const html=render(tutor,true,true);
  assert.doesNotMatch(html,/<button[^>]*disabled/, 'Loading permissions must not grey out navigation');
  assert.match(html,tutor?/Question library/:/Submission history/);
 }
 const viewer=render(true,false,true);
 assert.match(viewer,/<button(?=[^>]*disabled)[^>]*>[\s\S]*?Question library<\/button>/);
 assert.match(viewer,/<button(?=[^>]*disabled)[^>]*>[\s\S]*?Assessment categories<\/button>/);
 assert.doesNotMatch(render(true,false,false),/<button[^>]*disabled/);
 console.log('NAVIGATION_LOADING_OK pending navigation remains available; confirmed viewer restrictions retained');
} finally {fs.rmSync(file,{force:true});}
