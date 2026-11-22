import {chromium, expect as baseExpect} from '@playwright/test';
import {spawn, spawnSync} from 'node:child_process';
import fs from 'node:fs';
import path from 'node:path';
const expect = baseExpect.configure({timeout: 30000});
const repo = path.resolve('../..'), python = path.resolve(process.argv[2]), home = path.resolve(process.argv[3]);
const factory = process.env.LATEX_TEST_WEB_FACTORY;
if (!factory?.endsWith('run_atlas_namespace_web.py') || !process.env.LATEX_TEST_COLLECTION_GUARD) throw Error('Requires guarded real-Atlas test factory');
if (fs.existsSync(home)) throw Error('Use a fresh home');
fs.mkdirSync(home, {recursive:true});
const servers = [], browsers = [], pages = [];
const frames = [], errors = [];
async function start(name) {
  const child = spawn(python, [factory,'--home',path.join(home,name),'--port','0','--no-browser'], {cwd:repo,windowsHide:true});
  const state = {child,out:'',err:''}; servers.push(state);
  child.stdout.on('data', value => state.out += value);
  child.stderr.on('data', value => state.err += value);
  for (let i=0;i<450;i++) {
    const ready = state.out.split(/\r?\n/).find(line => line.startsWith('OVERLEAF_WEB_READY '));
    if (ready) return JSON.parse(ready.slice('OVERLEAF_WEB_READY '.length)).url;
    if (child.exitCode !== null) throw Error(state.err);
    await new Promise(resolve=>setTimeout(resolve,100));
  }
  throw Error('Startup timeout: '+state.err);
}
async function ok(response) {
  if (!response.ok()) throw Error('HTTP '+response.status()+': '+await response.text());
  return response;
}
async function source(page) {
  return page.locator('.cm-content').evaluate(node=>{
    const copy=node.cloneNode(true);copy.querySelectorAll('.remote-cursor').forEach(n=>n.remove());
    return [...copy.querySelectorAll('.cm-line')].map(n=>n.textContent).join('\n');
  });
}
try {
  const [urlA,urlB] = await Promise.all([start('Admin'),start('MinhNgoc')]);
  const edge=await chromium.launch({channel:'msedge',headless:true});browsers.push(edge);
  const chrome=await chromium.launch({channel:'chrome',headless:true});browsers.push(chrome);
  const aContext=await edge.newContext({viewport:{width:1700,height:1000}}), bContext=await chrome.newContext({viewport:{width:1700,height:1000}});
  const a=await aContext.newPage(), b=await bContext.newPage();pages.push(a,b);
  for (const [name,page] of [['A',a],['B',b]]) {
    page.on('pageerror',e=>errors.push(String(e)));
    page.on('websocket',ws=>ws.on('framereceived',frame=>{try{const d=JSON.parse(String(frame.payload));if(d.type?.startsWith('sync-'))frames.push({name,data:d});}catch{}}));
  }
  await ok(await aContext.request.post(urlA+'api/actions/register',{data:{name:'Cloud Owner',email:'cloud-owner@example.com',password:'test-password'}}));
  await ok(await aContext.request.post(urlA+'api/auth/onboarding',{data:{name:'Cloud Owner',role:'student'}}));
  const project=await (await ok(await aContext.request.post(urlA+'api/projects',{data:{title:'Shared backend project'}}))).json();
  await ok(await aContext.request.get(urlA+'api/projects'));
  const content='\\documentclass{article}\n\\begin{document}\n\\section{Shared cloud}\nShared source available on both backends.\n\\end{document}\n';
  await ok(await aContext.request.put(urlA+`api/projects/${project.id}/file`,{data:{path:'main.tex',content}}));
  const sharing=await (await ok(await aContext.request.patch(urlA+`api/projects/${project.id}/sharing`,{data:{mode:'link',permission:'editor'}}))).json();
  await ok(await bContext.request.post(urlB+'api/actions/register',{data:{name:'Cloud Editor',email:'cloud-editor@example.com',password:'test-password'}}));
  await ok(await bContext.request.post(urlB+'api/auth/onboarding',{data:{name:'Cloud Editor',role:'student'}}));
  await ok(await bContext.request.post(urlB+'api/shares/accept',{data:{token:sharing.linkToken}}));
  await a.goto(urlA);await b.goto(urlB);
  await a.locator('.project-card-open').click();await b.locator('.project-card-open').click();
  await expect(a.locator('.cm-content')).toContainText('Shared source available');
  await expect(b.locator('.cm-content')).toContainText('Shared source available');
  await a.locator('.cm-content').click();await a.keyboard.press('Control+End');await a.keyboard.insertText('\n% FROM_BACKEND_A');
  await expect(b.locator('.cm-content')).toContainText('FROM_BACKEND_A');
  await b.locator('.cm-content').click();await b.keyboard.press('Control+End');await b.keyboard.insertText('\n% FROM_BACKEND_B');
  await expect(a.locator('.cm-content')).toContainText('FROM_BACKEND_B');
  // Both servers attempt edits against the same source version.
  await Promise.all([a.locator('.cm-content').click(),b.locator('.cm-content').click()]);
  await Promise.all([a.keyboard.press('Control+End'),b.keyboard.press('Control+End')]);
  await Promise.all([a.keyboard.insertText('\n% RACE_A'),b.keyboard.insertText('\n% RACE_B')]);
  for (const page of [a,b]) {
    await expect(page.locator('.cm-content')).toContainText('RACE_A');
    await expect(page.locator('.cm-content')).toContainText('RACE_B');
  }
  await expect.poll(async()=> (await source(a)) === (await source(b))).toBe(true);
  await a.keyboard.press('Control+s');await b.keyboard.press('Control+s');
  for (const page of [a,b]) {
    await page.locator('.compile-button').click();
    await expect(page.locator('.pdf-sheet')).toHaveCount(1);
    await expect(page.locator('.compile-button')).toBeEnabled();
  }
  await b.reload();
  await b.locator('.project-card-open').click();
  await expect(b.locator('.cm-content')).toContainText('RACE_A');
  await expect(b.locator('.cm-content')).toContainText('RACE_B');
  await a.screenshot({path:path.join(home,'backend-a.png')});
  await b.screenshot({path:path.join(home,'backend-b.png')});
  expect(errors).toEqual([]);
  fs.writeFileSync(path.join(home,'report.json'),JSON.stringify({passed:true,twoProcesses:true,independentWorkingCopies:true,sameAtlasDatabase:true,sourceBothDirections:true,concurrentEditsPreserved:true,bothCompiled:true,reload:true},null,2));
  console.log('SHARED_BACKENDS_OK two real processes; Edge/Chrome; Atlas shared files; bidirectional OT; concurrent edits; compile both; reload');
} catch(error) {
  for (let i=0;i<pages.length;i++)await pages[i].screenshot({path:path.join(home,`error-${i}.png`)}).catch(()=>{});
  console.error(servers.map(s=>s.err.slice(-2500)).join('\n'));
  throw error;
} finally {
  fs.writeFileSync(path.join(home,'frames.json'),JSON.stringify(frames,null,2));
  for (const browser of browsers)await browser.close();
  for (const state of servers)spawnSync('taskkill',['/PID',String(state.child.pid),'/T','/F'],{windowsHide:true,stdio:'ignore'});
}
