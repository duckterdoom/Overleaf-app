import {chromium,expect as baseExpect} from '@playwright/test';
import {spawn} from 'node:child_process';
import fs from 'node:fs';
import path from 'node:path';
const expect=baseExpect.configure({timeout:20000});
const repo=path.resolve('../..'),home=path.resolve(process.argv[3]);
if(fs.existsSync(home))throw Error('Use a fresh test home');
fs.mkdirSync(home,{recursive:true});
const server=spawn(path.resolve(process.argv[2]),[path.join(repo,'apps/backend/tests/support/run_docx_web.py'),'--home',home,'--port','0','--no-browser'],{cwd:repo,windowsHide:true});
let output='',stderr='',browser,lastPage;const errors=[],checks=[];
server.stdout.on('data',v=>output+=v);server.stderr.on('data',v=>stderr+=v);
try{
 for(let i=0;i<400&&!output.includes('OVERLEAF_WEB_READY ');i++){if(server.exitCode!==null)throw Error(stderr);await new Promise(r=>setTimeout(r,100));}
 const line=output.split(/\r?\n/).find(s=>s.startsWith('OVERLEAF_WEB_READY '));if(!line)throw Error(stderr);
 const {url}=JSON.parse(line.slice('OVERLEAF_WEB_READY '.length));
 browser=await chromium.launch({channel:'msedge',headless:true});
 const tc=await browser.newContext({viewport:{width:1500,height:1000}}),sc=await browser.newContext({viewport:{width:1500,height:1000}});
 const teacher=await tc.newPage(),student=await sc.newPage();
 const compileRequests=[];teacher.on('request',r=>{if(r.method()==='POST'&&r.url().endsWith('/compile'))compileRequests.push(r.postDataJSON());});
 for(const p of [teacher,student])p.on('pageerror',e=>errors.push(String(e)));
 async function api(ctx,route,body){const r=await ctx.request.fetch(url+'api/'+route,{method:body?'POST':'GET',data:body});if(!r.ok())throw Error(route+': '+await r.text());return r.json();}
 async function shot(p,name){lastPage=p;await p.screenshot({path:path.join(home,name+'.png')});checks.push(name);}
 async function nav(p,name){await p.locator('.writing-sidebar').getByRole('button',{name,exact:true}).click();}
 await api(tc,'actions/register',{email:'library-tutor@example.com',name:'Alex Teacher',password:'test-password',role:'tutor'});
 const first=await api(tc,'projects',{title:'Alpha thesis'}),second=await api(tc,'projects',{title:'Beta paper'});
 await tc.request.post(url+`api/projects/${first.id}/file`,{data:{path:'report.tex',directory:false}});
 for(const [file,word] of [['main.tex','original'],['report.tex','secondary']])await tc.request.put(url+`api/projects/${first.id}/file`,{data:{path:file,content:String.raw`\documentclass{article}
\begin{document}
Hello ${word} world.
\end{document}`}});
 await teacher.goto(url);await teacher.getByRole('button',{name:'Open LaTeX Studio',exact:true}).click();
 await expect(teacher.getByRole('columnheader',{name:'Title',exact:true})).toBeVisible();
 await expect(teacher.getByRole('columnheader',{name:'Owner',exact:true})).toBeVisible();
 await expect(teacher.getByRole('columnheader',{name:'Last modified',exact:true})).toBeVisible();
 await teacher.getByRole('button',{name:'Project actions Beta paper',exact:true}).click();
 await expect(teacher.getByRole('dialog',{name:'Manage project',exact:true})).toHaveCount(0);
 await teacher.getByRole('button',{name:'Rename',exact:true}).click();await teacher.getByLabel('Project name',{exact:true}).fill('Beta renamed');await teacher.getByRole('button',{name:'Save',exact:true}).click();
 await expect(teacher.locator('.project-table tbody')).toContainText('Beta renamed');
 await teacher.getByRole('button',{name:'Project actions Beta renamed',exact:true}).click();await teacher.getByRole('button',{name:'Archive',exact:true}).click();
 await expect(teacher.locator('.project-table tbody tr')).toHaveCount(1);await teacher.getByLabel('Project archive filter',{exact:true}).selectOption('archived');
 await expect(teacher.locator('.project-table tbody')).toContainText('Beta renamed');await teacher.getByRole('button',{name:'Project actions Beta renamed',exact:true}).click();await teacher.getByRole('button',{name:'Unarchive',exact:true}).click();
 await teacher.getByLabel('Project archive filter',{exact:true}).selectOption('active');await teacher.getByRole('button',{name:'Project actions Beta renamed',exact:true}).click();await teacher.getByRole('button',{name:'Rename',exact:true}).click();await teacher.getByLabel('Project name',{exact:true}).fill('Beta paper');await teacher.getByRole('button',{name:'Save',exact:true}).click();
 await teacher.getByRole('button',{name:'Grid view',exact:true}).click();await teacher.getByRole('button',{name:'Project actions Beta paper',exact:true}).click();await teacher.getByRole('button',{name:'Trash project',exact:true}).click();await expect(teacher.getByRole('button',{name:'Confirm trash',exact:true})).toBeVisible();await teacher.getByRole('group',{name:'Actions for Beta paper',exact:true}).getByRole('button',{name:'Cancel',exact:true}).click();await teacher.keyboard.press('Escape');await teacher.getByRole('button',{name:'List view',exact:true}).click();
 await teacher.getByRole('button',{name:'Project actions Beta paper',exact:true}).click();await shot(teacher,'row-actions-menu');await teacher.keyboard.press('Escape');
 await teacher.getByRole('button',{name:'Project actions Beta paper',exact:true}).click();await teacher.keyboard.press('Escape');await expect(teacher.getByRole('group',{name:'Actions for Beta paper',exact:true})).toHaveCount(0);
 await teacher.getByRole('checkbox',{name:'Select project Alpha thesis',exact:true}).check();
 expect(await teacher.getByRole('checkbox',{name:'Select all visible projects',exact:true}).evaluate(e=>e.indeterminate)).toBe(true);
 const event=teacher.waitForEvent('download');await teacher.getByRole('button',{name:'Download selected projects',exact:true}).click();await(await event).saveAs(path.join(home,'selected.zip'));
 await teacher.getByRole('button',{name:'Edit selected project tags',exact:true}).click();await teacher.getByLabel('Project tags',{exact:true}).fill('Research, Draft');await teacher.getByRole('button',{name:'Save tags',exact:true}).click();
 await expect(teacher.locator('.project-tags')).toContainText('Research');await shot(teacher,'project-table-tags');
 await teacher.locator('.home-project-tools .search-input input').fill('Research');await expect(teacher.locator('.project-table tbody tr')).toHaveCount(1);await teacher.locator('.home-project-tools .search-input input').fill('');
 await api(sc,'actions/register',{email:'library-viewer@example.com',name:'Jamie Student',password:'test-password',role:'student'});
 const sharing=await tc.request.patch(url+`api/projects/${first.id}/sharing`,{data:{mode:'link',permission:'viewer'}});await api(sc,'shares/accept',{token:(await sharing.json()).linkToken});
 await student.goto(url);await student.getByRole('button',{name:'Open LaTeX Studio',exact:true}).click();await expect(student.locator('.project-table tbody')).toContainText('Alex Teacher');await student.getByRole('checkbox',{name:'Select project Alpha thesis',exact:true}).check();await expect(student.getByRole('button',{name:'Delete selected projects',exact:true})).toBeDisabled();await expect(student.getByRole('button',{name:'Edit selected project tags',exact:true})).toBeDisabled();await student.getByRole('button',{name:'Project actions Alpha thesis',exact:true}).click();await expect(student.getByRole('group',{name:'Actions for Alpha thesis',exact:true}).getByRole('button',{name:'Rename',exact:true})).toHaveCount(0);await expect(student.getByRole('button',{name:'Download ZIP',exact:true})).toBeVisible();await student.keyboard.press('Escape');await shot(student,'shared-project-owner');

 await teacher.getByRole('checkbox',{name:'Select project Beta paper',exact:true}).check();await teacher.getByRole('button',{name:'Delete selected projects',exact:true}).click();await teacher.getByRole('group',{name:'Confirm project deletion'}).getByRole('button',{name:'Cancel',exact:true}).click();
 expect((await api(tc,'projects')).length).toBe(2);
 await teacher.getByRole('button',{name:'Delete selected projects',exact:true}).click();await teacher.getByRole('button',{name:'Confirm delete',exact:true}).click();
 await expect(teacher.locator('.project-table tbody tr')).toHaveCount(1);
 await teacher.locator('.project-title-open').click();
 await expect(teacher.locator('.app-header')).toHaveCount(1);await expect(teacher.locator('.project-context-bar')).toHaveCount(0);
 for(const label of ['History','Share','Layout'])await expect(teacher.locator('.app-header').getByRole('button',{name:label,exact:true})).toBeVisible();
 for(const label of ['Profile','Settings']){await expect(teacher.locator('.app-header').getByRole('button',{name:label,exact:true})).toHaveCount(0);await expect(teacher.locator('.workspace-account-tools').getByRole('button',{name:label,exact:true})).toBeVisible();}
 await expect(teacher.locator('.statusbar')).toHaveCount(0);await expect(teacher.getByRole('tablist',{name:'Compiled documents',exact:true})).toHaveCount(0);
 await teacher.getByRole('button',{name:'Recompile',exact:true}).click();await expect(teacher.locator('.pdf-sheet').first()).toBeVisible();
 await teacher.locator('.file-tree').getByRole('button',{name:'report.tex',exact:true}).click();await expect(teacher.getByLabel('Compile file',{exact:true})).toHaveCount(0);await expect(teacher.getByRole('tab',{name:'report.tex',exact:true})).toHaveAttribute('aria-selected','true');
 await teacher.getByRole('button',{name:'Recompile',exact:true}).click();await expect(teacher.locator('.pdf-sheet').first()).toBeVisible();
 expect(compileRequests.at(-1).main).toBe('report.tex');
 await teacher.getByRole('tab',{name:'main.tex',exact:true}).click();await expect(teacher.getByRole('tab',{name:'main.tex',exact:true})).toHaveAttribute('aria-selected','true');await expect(teacher.locator('.pdf-sheet').first()).toBeVisible();
 await teacher.getByRole('button',{name:'Layout',exact:true}).click();await teacher.getByRole('button',{name:/^Focus mode/}).click();await expect(teacher.getByRole('button',{name:'Exit focus mode',exact:true})).toBeVisible();await teacher.keyboard.press('Escape');await expect(teacher.locator('.desktop-shell')).not.toHaveClass(/focus-mode/);
 await teacher.getByRole('button',{name:'Compile options',exact:true}).click();
 await teacher.getByRole('checkbox',{name:'Auto compile',exact:true}).check();await teacher.getByRole('checkbox',{name:'Auto compile',exact:true}).uncheck();
 await teacher.getByRole('radio',{name:'xelatex',exact:true}).check();await teacher.keyboard.press('Escape');await expect(teacher.getByRole('button',{name:'Compile options',exact:true})).toBeFocused();
 await teacher.getByRole('button',{name:'Recompile',exact:true}).click();await expect(teacher.locator('.pdf-sheet').first()).toBeVisible();
 expect(compileRequests.at(-1)).toMatchObject({main:'main.tex',engine:'xelatex'});
 await teacher.getByRole('button',{name:'Compile options',exact:true}).click();await expect(teacher.getByRole('radio',{name:'xelatex',exact:true})).toBeChecked();await teacher.keyboard.press('Escape');
 await teacher.setViewportSize({width:1050,height:850});
 await expect.poll(()=>teacher.locator('.compile-toolbar').evaluate(el=>el.scrollWidth<=el.clientWidth+1)).toBe(true);
 await teacher.getByRole('button',{name:'Compile options',exact:true}).click();
 const menuBounds=await teacher.getByRole('group',{name:'Compile options',exact:true}).boundingBox();expect(menuBounds.x).toBeGreaterThanOrEqual(0);expect(menuBounds.x+menuBounds.width).toBeLessThanOrEqual(1051);
 await shot(teacher,'compact-compile-options');await teacher.keyboard.press('Escape');
 await shot(teacher,'compact-narrow-toolbar');await teacher.setViewportSize({width:1500,height:1000});
 await shot(teacher,'compact-editor');await teacher.getByRole('button',{name:'Home',exact:true}).hover();await expect(teacher.locator('.brand-home-icon')).toBeVisible();await teacher.getByRole('button',{name:'Home',exact:true}).click();await expect(teacher.locator('.project-table')).toBeVisible();await expect(teacher.locator('.academic-dashboard')).toHaveCount(0);
 await teacher.locator('.workspace-navigation').getByRole('button',{name:'Template library',exact:true}).click();await expect(teacher.locator('.template-library-page')).toBeVisible();await expect(teacher.locator('.project-modal')).toHaveCount(0);await expect(teacher.locator('.template-category-grid')).not.toContainText(/IELTS/i);await shot(teacher,'document-templates-no-ielts');
 expect(errors).toEqual([]);console.log('Document library and editor shell passed: '+checks.length+' views');
}catch(e){if(lastPage)await lastPage.screenshot({path:path.join(home,'failure.png')}).catch(()=>{});console.error(stderr);throw e;}
finally{await browser?.close();server.kill();}
