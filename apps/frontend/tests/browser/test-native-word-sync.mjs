import {selectPreview} from './preview-format.mjs';
import {chromium,expect as baseExpect} from '@playwright/test';
import {spawn} from 'node:child_process';
import fs from 'node:fs';
import path from 'node:path';
const expect=baseExpect.configure({timeout:20000});
const repo=path.resolve('../..'),home=path.resolve(process.argv[3]);
if(fs.existsSync(home))throw Error('Use a fresh test home');
fs.mkdirSync(home,{recursive:true});
const server=spawn(path.resolve(process.argv[2]),[path.join(repo,'apps/backend/tests/support/run_mongo_web.py'),'--home',home,'--port','0','--no-browser'],{cwd:repo,windowsHide:true});
let output='',stderr='',browser,lastPage;const errors=[],checks=[];
server.stdout.on('data',v=>output+=v);server.stderr.on('data',v=>stderr+=v);
try{
 for(let i=0;i<400&&!output.includes('OVERLEAF_WEB_READY ');i++){if(server.exitCode!==null)throw Error(stderr);await new Promise(r=>setTimeout(r,100));}
 const line=output.split(/\r?\n/).find(s=>s.startsWith('OVERLEAF_WEB_READY '));if(!line)throw Error(stderr);
 const {url}=JSON.parse(line.slice('OVERLEAF_WEB_READY '.length));
 browser=await chromium.launch({channel:'msedge',headless:true});
 const tc=await browser.newContext({viewport:{width:1500,height:1000}}),sc=await browser.newContext({viewport:{width:1500,height:1000}});
 const teacher=await tc.newPage(),student=await sc.newPage();
 for(const p of [teacher,student])p.on('pageerror',e=>errors.push(String(e)));
 async function api(ctx,route,body,method){const r=await ctx.request.fetch(url+'api/'+route,{method:method||(body?'POST':'GET'),data:body});if(!r.ok())throw Error(route+': '+await r.text());return r.json();}
 async function shot(p,name){lastPage=p;await p.screenshot({path:path.join(home,name+'.png')});checks.push(name);}
 async function nav(p,name){await p.locator('.writing-sidebar').getByRole('button',{name,exact:true}).click();}
 await api(tc,'actions/register',{email:'word-tutor@example.com',name:'Word Tutor',password:'test-password',role:'tutor'});
 await api(tc,'actions/settings',{mode:'dark',autoCompile:false});
 const project=await api(tc,'projects',{title:'Word regression'});
 await api(tc,`projects/${project.id}/file`,{path:'main.tex',content:String.raw`\documentclass{article}
\begin{document}
Hello original world. This sentence must synchronize correctly.
\newpage
Second page has visible text on white paper.
\end{document}`},'PUT');
 await teacher.goto(url);await teacher.locator('.workspace-navigation').getByRole('button',{name:/^All projects/}).click();await teacher.locator('.project-card-open').click();
 await teacher.locator('.compile-button').click();
 await expect(teacher.locator('.output-tabs').getByRole('tab',{name:'main.pdf',exact:true})).toBeVisible({timeout:120000});
 await expect(teacher.locator('.compile-button')).toBeEnabled({timeout:120000});
 await selectPreview(teacher, 'Word');
 await expect(teacher.locator('[data-docx-ready="true"]')).toBeVisible({timeout:120000});
 await expect(teacher.locator('.native-word-host')).toContainText('Hello original world.');
 fs.writeFileSync(path.join(home,'word-dom.html'),await teacher.locator('.native-word-host').innerHTML());
 fs.writeFileSync(path.join(home,'word-style.json'),JSON.stringify(await teacher.locator('.native-word-host').evaluate(host=>Array.from(host.querySelectorAll('*')).filter(e=>/page|editor|surface/.test(e.className||'')).slice(0,90).map(e=>({tag:e.tagName,class:e.className,bg:getComputedStyle(e).backgroundColor,color:getComputedStyle(e).color,width:e.getBoundingClientRect().width,height:e.getBoundingClientRect().height}))),null,2));
 await shot(teacher,'word-dark-before');
 const run=teacher.locator('.superdoc-text-run').filter({hasText:'Hello original world.'}).first();
 await run.click();await teacher.keyboard.press('Home');await teacher.keyboard.type('WORDSYNC ');
 await expect.poll(async()=>(await api(tc,`projects/${project.id}/file?path=main.tex`)).content,{timeout:30000}).toContain('WORDSYNC');
 await expect(teacher.locator('.cm-content')).toContainText('WORDSYNC');
 await shot(teacher,'word-synced');
 expect(errors).toEqual([]);console.log('Native Word sync passed');
}catch(e){if(lastPage)await lastPage.screenshot({path:path.join(home,'failure.png')}).catch(()=>{});console.error(stderr);throw e;}
finally{await browser?.close();server.kill();}
