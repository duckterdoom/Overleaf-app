import {chromium,expect as baseExpect} from '@playwright/test';
import {spawn} from 'node:child_process';
import fs from 'node:fs';
import path from 'node:path';
const expect=baseExpect.configure({timeout:20000});
const repo=path.resolve('../..'),home=path.resolve(process.argv[3]);
if(fs.existsSync(home))throw Error('Use a fresh test home');
fs.mkdirSync(home,{recursive:true});
const server=spawn(path.resolve(process.argv[2]),[path.join(repo,'apps/backend/tests/support/run_docx_web.py'),'--home',home,'--port','0','--no-browser'],{cwd:repo,windowsHide:true,env:{...process.env,...(process.argv[4]?{LATEX_TEST_PDF_SAMPLE:path.resolve(process.argv[4])}:{})}});
let output='',stderr='',browser,lastPage;const errors=[],checks=[];
server.stdout.on('data',v=>output+=v);server.stderr.on('data',v=>stderr+=v);
try{
 for(let i=0;i<400&&!output.includes('OVERLEAF_WEB_READY ');i++){if(server.exitCode!==null)throw Error(stderr);await new Promise(r=>setTimeout(r,100));}
 const line=output.split(/\r?\n/).find(s=>s.startsWith('OVERLEAF_WEB_READY '));if(!line)throw Error(stderr);
 const {url}=JSON.parse(line.slice('OVERLEAF_WEB_READY '.length));
 browser=await chromium.launch({channel:'msedge',headless:true});
 const tc=await browser.newContext({viewport:{width:1500,height:1000}}),sc=await browser.newContext({viewport:{width:1500,height:1000}});
 const teacher=await tc.newPage(),student=await sc.newPage();lastPage=teacher;
 for(const p of [teacher,student])p.on('pageerror',e=>errors.push(String(e)));
 async function api(ctx,route,body){const r=await ctx.request.fetch(url+'api/'+route,{method:body?'POST':'GET',data:body});if(!r.ok())throw Error(route+': '+await r.text());return r.json();}
 async function shot(p,name){lastPage=p;await p.screenshot({path:path.join(home,name+'.png')});checks.push(name);}
 async function nav(p,name){await p.locator('.writing-sidebar').getByRole('button',{name,exact:true}).click();}
 await api(tc,'actions/register',{email:'docx-tutor@example.com',name:'Docx Tutor',password:'test-password',role:'tutor'});
 const project=await api(tc,'projects',{title:'DOCX round trip'});
 const source=String.raw`\documentclass{article}
\begin{document}
Hello original world.

Another unique paragraph remains intact.
\newpage
Final page keeps its own text.
\end{document}`;
 await tc.request.put(url+`api/projects/${project.id}/file`,{data:{path:'main.tex',content:source}});
 await api(tc,'actions/settings',{mode:'dark',autoCompile:false});
 await teacher.goto(url);await teacher.getByRole('button',{name:'Open LaTeX Studio',exact:true}).click();
 await teacher.locator('.project-card-open').click();await expect(teacher.locator('.cm-content')).toBeVisible();
 await teacher.getByRole('button',{name:'Recompile',exact:true}).click();
 await expect(teacher.locator('.pdf-sheet').first()).toBeVisible();
 await teacher.locator('.compile-toolbar').getByRole('button',{name:'PDF preview',exact:true}).click();await teacher.getByRole('group',{name:'PDF preview',exact:true}).getByRole('button',{name:'Word preview',exact:true}).click();
 await expect(teacher.locator('[data-docx-ready="true"]')).toBeVisible({timeout:60000});
 await teacher.waitForTimeout(500);
 fs.writeFileSync(path.join(home,'word-dom.html'),await teacher.locator('.native-word-host').innerHTML());
 await shot(teacher,'word-dark-before');
 const pages=teacher.locator('.native-word-host .superdoc-page');
 await pages.first().evaluate(el=>{el.style.setProperty('--sd-layout-page-bg','#1b273b');el.style.setProperty('--sd-layout-page-color','#e8edf7');});
 await expect(pages.first()).toHaveCSS('background-color','rgb(255, 255, 255)');await expect(pages.first()).toHaveCSS('color','rgb(17, 17, 17)');
 if(process.argv[4]){expect(await pages.count()).toBeGreaterThan(1);expect(await teacher.evaluate(()=>document.documentElement.scrollWidth<=innerWidth+1)).toBe(true);await shot(teacher,'original-converter-sample');console.log('Original converter sample rendered: '+await pages.count()+' pages');}else{
 const text=teacher.locator('.native-word-host').getByText('Hello original world.',{exact:true}).first();
 await expect(text).toBeVisible();await expect(text).toHaveCSS('color',/rgb\((?:0, 0, 0|17, 17, 17)\)/);await expect(teacher.locator('.native-word-host .superdoc-page img')).toHaveCount(1);await text.click();await teacher.keyboard.press('Home');await teacher.keyboard.press('Shift+End');await teacher.keyboard.type('Hello revised world.');
 const savedResponse=teacher.waitForResponse(r=>r.request().method()==='PUT'&&r.url().includes('/docx-preview/'));
 await teacher.getByRole('button',{name:'Save Word',exact:true}).click();
 const metadata=await(await savedResponse).json();
 const bytes=await tc.request.get(url+`api/projects/${project.id}/docx-preview/${metadata.revision}?main=main.tex`);expect(bytes.ok()).toBe(true);fs.writeFileSync(path.join(home,'edited.docx'),await bytes.body());
 await expect.poll(async()=>{const r=await tc.request.get(url+`api/projects/${project.id}/file?path=main.tex`);return (await r.json()).content;},{timeout:20000}).toContain('Hello revised world.');
 await expect(teacher.locator('.cm-content')).toContainText('Hello revised world.');
 await teacher.getByRole('button',{name:'Recompile',exact:true}).click();
 await teacher.locator('.compile-toolbar').getByRole('button',{name:'Word preview',exact:true}).click();await teacher.getByRole('group',{name:'Word preview',exact:true}).getByRole('button',{name:'PDF preview',exact:true}).click();await expect(teacher.locator('.pdf-sheet').first()).toBeVisible();
 const saved=await tc.request.get(url+`api/projects/${project.id}/file?path=main.tex`);expect((await saved.json()).content).toContain('Hello revised world.');
 await teacher.locator('.compile-toolbar').getByRole('button',{name:'PDF preview',exact:true}).click();await teacher.getByRole('group',{name:'PDF preview',exact:true}).getByRole('button',{name:'Word preview',exact:true}).click();await expect(teacher.locator('[data-docx-ready="true"]')).toBeVisible();
 await expect(teacher.locator('.native-word-host')).toContainText('Hello revised world.');
 expect(await teacher.evaluate(()=>document.documentElement.scrollWidth<=innerWidth+1)).toBe(true);await shot(teacher,'word-synced');
 await expect(pages).toHaveCount(2);await expect(teacher.locator('.native-word-host .superdoc-page img')).toHaveCount(1);
 await teacher.getByRole('button',{name:'Settings',exact:true}).click();const settingsDialog=teacher.getByRole('dialog',{name:'Settings',exact:true});await settingsDialog.getByLabel('Mode',{exact:true}).selectOption('light');await settingsDialog.getByRole('button',{name:'Apply',exact:true}).click();
 await expect(pages.first()).toHaveCSS('background-color','rgb(255, 255, 255)');await shot(teacher,'word-light');expect(errors).toEqual([]);console.log('DOCX browser round trip passed');
}
}catch(e){if(lastPage)await lastPage.screenshot({path:path.join(home,'failure.png')}).catch(()=>{});if(lastPage)fs.writeFileSync(path.join(home,'failure.html'),await lastPage.content());console.error(stderr);console.error(errors);throw e;}
finally{await browser?.close();server.kill();}
