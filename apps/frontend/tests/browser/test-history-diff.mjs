import {chromium,expect as baseExpect} from '@playwright/test';
import {spawn} from 'node:child_process';
import fs from 'node:fs';
import path from 'node:path';
const expect=baseExpect.configure({timeout:20000}),repo=path.resolve('../..'),home=path.resolve(process.argv[3]);
if(fs.existsSync(home))throw Error('Use a fresh isolated home');fs.mkdirSync(home,{recursive:true});
const server=spawn(path.resolve(process.argv[2]),[path.join(repo,'apps/backend/tests/support/run_mongo_web.py'),'--home',home,'--port','0','--no-browser'],{cwd:repo,windowsHide:true});
let output='',stderr='',browser,page;const errors=[],checks=[];
server.stdout.on('data',v=>output+=v);server.stderr.on('data',v=>stderr+=v);
try {
 for(let i=0;i<400&&!output.includes('OVERLEAF_WEB_READY ');i++){if(server.exitCode!==null)throw Error(stderr);await new Promise(r=>setTimeout(r,100));}
 const ready=output.split(/\r?\n/).find(v=>v.startsWith('OVERLEAF_WEB_READY '));if(!ready)throw Error(stderr);
 const {url}=JSON.parse(ready.slice('OVERLEAF_WEB_READY '.length));browser=await chromium.launch({channel:'msedge',headless:true});
 const context=await browser.newContext({viewport:{width:1600,height:1000}});page=await context.newPage();page.on('pageerror',e=>errors.push(String(e)));
 async function api(route,body,method=body?'POST':'GET'){const r=await context.request.fetch(url+'api/'+route,{method,data:body});if(!r.ok())throw Error(route+': '+await r.text());return r.json();}
 async function shot(name){await page.screenshot({path:path.join(home,name+'.png')});checks.push(name);}
 await api('actions/register',{email:'history@example.com',name:'History Tutor',password:'test-password',role:'tutor'});
 const project=await api('projects',{title:'History comparison test'});
 const before='\\documentclass{article}\n\\begin{document}\n\\section{Introduction}\nThe old title.\nUnchanged middle line.\nRemove this paragraph.\n\\section{Results}\nThe old conclusion.\n\\end{document}\n';
 const after='\\documentclass{article}\n\\begin{document}\n\\section{Introduction}\nThe new title.\nUnchanged middle line.\n\\section{Results}\nThe new conclusion.\nAdded final sentence.\n\\end{document}\n';
 await api(`projects/${project.id}/file`,{path:'main.tex',content:before},'PUT');
 const old=await api('actions/checkpoint',{project:project.id,path:'main.tex',content:before,reason:'Test saved version'});
 await api('actions/history-label',{project:project.id,revision:old.id,label:'Before edits'});
 await api(`projects/${project.id}/file`,{path:'main.tex',content:after},'PUT');
 const latest=await api('actions/checkpoint',{project:project.id,path:'main.tex',content:after,reason:'Test current version'});
 await api('actions/history-label',{project:project.id,revision:latest.id,label:'Current snapshot'});
 await api(`projects/${project.id}/file`,{path:'notes.tex',content:'',directory:false});
 await api(`projects/${project.id}/file`,{path:'notes.tex',content:'Notes only.\n'},'PUT');
 await page.goto(url);await page.locator('.workspace-navigation').getByRole('button',{name:/^All projects/}).click();await page.locator('.project-card-open').click();
 await expect(page.locator('.file-tree .file-selected')).toHaveCSS('background-color','rgb(241, 236, 251)');
 await shot('editor-files-light');await page.getByRole('button',{name:'History',exact:true}).click();
 const revision=(label)=>page.locator('.revision-list > button').filter({hasText:label});
 await revision('Before edits').click();await expect(page.getByRole('checkbox',{name:'Compare with current',exact:true})).toBeChecked();
 const comparison=page.getByRole('region',{name:'Revision comparison',exact:true});await expect(comparison).toBeVisible();
 await expect(comparison.locator('.history-diff-row.modified')).toHaveCount(2);await expect(comparison.locator('.history-diff-row.removed')).toHaveCount(1);await expect(comparison.locator('.history-diff-row.added')).toHaveCount(1);
 const unchanged=comparison.locator('.history-diff-row').filter({hasText:'Unchanged middle line.'});await expect(unchanged.locator('mark')).toHaveCount(0);
 await expect(comparison.locator('.history-change-text.removed').first()).toHaveText('old');await expect(comparison.locator('.history-change-text.added').first()).toHaveText('new');
 await page.getByRole('button',{name:'Next change',exact:true}).click();await expect(comparison.locator('.current-change')).toHaveAttribute('data-change-type','modified');
 await shot('history-detailed-diff-light');expect((await api(`projects/${project.id}/file?path=main.tex`)).content).toBe(after);
 await page.getByRole('checkbox',{name:'Compare with current',exact:true}).uncheck();await expect(page.locator('.history-source')).toContainText('Remove this paragraph.');await expect(comparison).toHaveCount(0);await page.getByRole('checkbox',{name:'Compare with current',exact:true}).check();
 await revision('Current snapshot').click();await expect(page.getByText('No changes compared with the current file.',{exact:true})).toBeVisible();
 // A slower response must not overwrite a newer selection.
 let release,arrived;const gated=new Promise(resolve=>release=resolve),started=new Promise(resolve=>arrived=resolve);
 const oldRoute=`**/api/projects/${project.id}/history/${old.id}`;
 await page.route(oldRoute,async route=>{arrived();await gated;await route.continue();});
 await revision('Before edits').click();await started;await revision('Current snapshot').click();await expect(page.getByText('No changes compared with the current file.',{exact:true})).toBeVisible();release();
 await page.waitForResponse(r=>r.url().endsWith('/history/'+old.id));await expect(page.getByText('No changes compared with the current file.',{exact:true})).toBeVisible();await page.unroute(oldRoute);
 // A failed current-file fetch is not an empty/deleted file.
 const fileRoute=`**/api/projects/${project.id}/file?path=main.tex`;
 await page.route(fileRoute,route=>route.fulfill({status:503,contentType:'application/json',body:JSON.stringify({detail:'Temporary read failure'})}));
 await revision('Before edits').click();await expect(page.locator('.history-load-warning')).toContainText('Could not load the current file');await expect(comparison).toHaveCount(0);await expect(page.locator('.history-source')).toContainText('Remove this paragraph.');await shot('history-current-read-error');await page.unroute(fileRoute);
 await revision('Before edits').click();await expect(comparison).toBeVisible();
 await page.locator('.history-grid > aside').first().getByRole('button',{name:'notes.tex',exact:true}).click();await expect(comparison).toHaveCount(0);await expect(page.locator('.history-meta')).toContainText('Select a revision');await page.locator('.history-grid > aside').first().getByRole('button',{name:'notes.tex',exact:true}).click();
 await revision('Before edits').click();await page.getByLabel('Revision label',{exact:true}).fill('Important revision');await page.getByRole('button',{name:'Save label',exact:true}).click();await expect(revision('Important revision')).toBeVisible();
 await page.evaluate(()=>document.documentElement.dataset.mode='dark');await expect(revision('Important revision')).toHaveCSS('color','rgb(232, 237, 247)');await expect(revision('Important revision')).toHaveCSS('background-color','rgb(51, 43, 77)');await shot('history-detailed-diff-dark');
 await page.setViewportSize({width:390,height:844});expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth+1)).toBe(true);await shot('history-diff-mobile');await page.setViewportSize({width:1600,height:1000});
 await page.getByRole('button',{name:'Back to editor',exact:true}).click();await expect(page.locator('.file-tree .file-selected')).toHaveCSS('background-color','rgb(51, 43, 77)');await expect(page.locator('.file-tree .file-selected')).toHaveCSS('color','rgb(232, 237, 247)');await expect(page.locator('.file-sidebar')).toHaveCSS('background-color','rgb(27, 39, 59)');await shot('editor-files-dark');
 await page.getByRole('button',{name:'History',exact:true}).click();await revision('Important revision').click();await expect(comparison).toBeVisible();await page.getByRole('button',{name:'Restore this file',exact:true}).click();await page.getByRole('button',{name:'Confirm restore',exact:true}).click();await expect.poll(async()=>(await api(`projects/${project.id}/file?path=main.tex`)).content).toBe(before);await expect(page.locator('.cm-content')).toContainText('Remove this paragraph.');await page.getByRole('button',{name:'History',exact:true}).click();await revision('Important revision').click();await expect(page.getByText('No changes compared with the current file.',{exact:true})).toBeVisible();
 expect(errors).toEqual([]);fs.writeFileSync(path.join(home,'report.json'),JSON.stringify({checks,errors},null,2));console.log('History diff passed: '+checks.length+' views');
} catch(error){await page?.screenshot({path:path.join(home,'failure.png')}).catch(()=>{});console.error(stderr);throw error;}
finally{await browser?.close();server.kill();}
