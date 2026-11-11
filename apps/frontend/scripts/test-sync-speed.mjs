// End-to-end latency on real Edge + Chrome; factory injects an isolated DB namespace.
import { chromium, expect } from '@playwright/test';
import { spawn } from 'node:child_process';
import fs from 'node:fs';
import path from 'node:path';
const repo = path.resolve('../..'), python = path.resolve(process.argv[2]), home = path.resolve(process.argv[3]);
if (fs.existsSync(home)) throw Error('Use a fresh home');
fs.mkdirSync(home, { recursive: true });
const factory = process.env.LATEX_TEST_WEB_FACTORY || path.join(repo, 'apps/backend/tests/run_mongo_web.py');
const server = spawn(python, [factory, '--home', home, '--port', '0', '--no-browser'], { cwd: repo, windowsHide: true });
let stdout = '', stderr = '', edge, chrome;
server.stdout.on('data', v => stdout += v); server.stderr.on('data', v => stderr += v);
try {
    for (let i=0;i<300&&!stdout.includes('OVERLEAF_WEB_READY ');i++) {
        if (server.exitCode !== null) throw Error(stderr);
        await new Promise(r => setTimeout(r, 100));
    }
    const line = stdout.split(/\r?\n/).find(v=>v.startsWith('OVERLEAF_WEB_READY '));
    if (!line) throw Error(stderr);
    const { url } = JSON.parse(line.slice('OVERLEAF_WEB_READY '.length));
    edge = await chromium.launch({channel:'msedge',headless:true});
    chrome = await chromium.launch({channel:'chrome',headless:true});
    const owner = await edge.newContext(), member = await chrome.newContext();
    for (const [ctx,name] of [[owner,'Speed Owner'],[member,'Speed Member']]) {
        expect((await ctx.request.post(url+'api/actions/register',{data:{name,email:name.replace(' ','')+'@example.com',password:'speed-password'}})).ok()).toBe(true);
        expect((await ctx.request.post(url+'api/auth/onboarding',{data:{name,role:'student'}})).ok()).toBe(true);
    }
    const project = await (await owner.request.post(url+'api/projects',{data:{title:'Sync speed fixture'}})).json();
    const link = await (await owner.request.patch(url+`api/projects/${project.id}/sharing`,{data:{mode:'link',permission:'editor'}})).json();
    expect((await member.request.post(url+'api/shares/accept',{data:{token:link.linkToken}})).ok()).toBe(true);
    const a=await owner.newPage(), b=await member.newPage();
    for (const page of [a,b]) { await page.goto(url); await page.locator('.project-card-open').click(); }
    await expect(a.locator('.presence-person')).toHaveCount(1);
    const samples=[];
    for (let i=0;i<9;i++) {
        const [from,to]=i%2?[b,a]:[a,b];
        await from.locator('.cm-content').click(); await from.keyboard.press('Control+End');
        const token=` SPEED_${i}_${Date.now()} `;
        const start=performance.now();
        await from.keyboard.insertText(token);
        await to.waitForFunction(t=>document.querySelector('.cm-content')?.textContent?.includes(t),token,{timeout:30000,polling:'raf'});
        if (i) samples.push(Math.round(performance.now()-start));
    }
    const sorted=[...samples].sort((a,b)=>a-b);
    const report={passed:true,samplesMs:samples,medianMs:Math.round((sorted[3]+sorted[4])/2),p95Ms:sorted[7],measurement:'keyboard insert to other browser DOM, 1 warmup + 8 alternating samples; localhost web + configured MongoDB'};
    fs.writeFileSync(path.join(home,'speed.json'),JSON.stringify(report,null,2));
    console.log('SYNC_SPEED '+JSON.stringify(report));
} finally { if(edge)await edge.close();if(chrome)await chrome.close();server.kill(); }
