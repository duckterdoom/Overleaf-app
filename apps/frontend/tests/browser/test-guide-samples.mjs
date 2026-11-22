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


 await api(tc,'actions/register',{email:'guide-samples-tutor@example.com',name:'Guide Tutor',password:'test-password',role:'tutor'});
 await api(sc,'actions/register',{email:'guide-samples-student@example.com',name:'Guide Student',password:'test-password',role:'student'});
 const course=await api(tc,'writing/classes',{title:'Guide samples course'});await api(sc,'writing/classes/join',{code:course.joinCode});
 await api(tc,'actions/settings',{mode:'dark'});await teacher.goto(url);await nav(teacher,'Writing guides');
 await teacher.getByRole('button',{name:'Read guide Line graphs',exact:true}).click();
 const image=teacher.locator('.guide-topic-visual img');await expect(image).toBeVisible();await expect.poll(()=>image.evaluate(img=>img.complete&&img.naturalWidth>0)).toBe(true);
 await teacher.getByRole('button',{name:'Sample writing',exact:true}).click();await expect(teacher.getByRole('button',{name:'Sample writing',exact:true})).toHaveAttribute('aria-pressed','true');await expect(teacher.getByRole('button',{name:'Writing guide',exact:true})).toHaveAttribute('aria-pressed','false');await expect(teacher.locator('.guide-content')).toContainText('approximately 49%');await expect(image).toBeVisible();
 await shot(teacher,'line-model-dark');await teacher.getByRole('button',{name:'Edit guide',exact:true}).click();
 await teacher.getByLabel('Sample writing content',{exact:true}).fill('# Model answer\n\nA tutor-edited sample for the healthcare graph.');
 await teacher.getByRole('checkbox',{name:'Guide samples course',exact:true}).check();await teacher.getByRole('button',{name:'Save guide',exact:true}).click();
 await teacher.getByRole('button',{name:'Sample writing',exact:true}).click();await expect(teacher.locator('.guide-content')).toContainText('tutor-edited sample');
 await student.goto(url);await nav(student,'Writing guides');await student.getByRole('button',{name:'Read guide Line graphs',exact:true}).click();await student.getByRole('button',{name:'Sample writing',exact:true}).click();await expect(student.locator('.guide-content')).toContainText('tutor-edited sample');await expect(student.getByRole('button',{name:'Edit guide',exact:true})).toHaveCount(0);await shot(student,'student-model-light');
 await teacher.getByRole('button',{name:'All guides',exact:true}).click();await teacher.getByRole('button',{name:'Read guide Pie charts',exact:true}).click();await teacher.getByRole('button',{name:'Sample writing',exact:true}).click();await expect(teacher.locator('.guide-content')).toContainText('Source data note');await expect.poll(()=>teacher.locator('.guide-topic-visual img').evaluate(img=>img.complete&&img.naturalWidth>0)).toBe(true);await shot(teacher,'pie-source-note');
 await teacher.setViewportSize({width:650,height:900});await expect.poll(()=>teacher.evaluate(()=>document.documentElement.scrollWidth<=innerWidth+1)).toBe(true);await shot(teacher,'model-narrow');
 expect(errors).toEqual([]);console.log('GUIDE_SAMPLES_OK topic images, guide/sample tabs, tutor edit, course sharing, student read-only, source notes and responsive themes');
}catch(e){if(lastPage)await lastPage.screenshot({path:path.join(home,'failure.png')}).catch(()=>{});console.error(stderr);throw e;}
finally{await browser?.close();server.kill();}
