import {chromium,expect as baseExpect} from '@playwright/test';
import {spawn} from 'node:child_process';
import fs from 'node:fs';
import path from 'node:path';

const expect=baseExpect.configure({timeout:20000});
const repo=path.resolve('../..'),home=path.resolve(process.argv[3]);
if(fs.existsSync(home))throw Error('Use a fresh isolated test home');
fs.mkdirSync(home,{recursive:true});
const server=spawn(path.resolve(process.argv[2]),[path.join(repo,'apps/backend/tests/support/run_mongo_web.py'),'--home',home,'--port','0','--no-browser'],{cwd:repo,windowsHide:true});
let output='',stderr='',browser,lastPage;const errors=[],checks=[];
server.stdout.on('data',v=>output+=v);server.stderr.on('data',v=>stderr+=v);
try{
 for(let i=0;i<400&&!output.includes('OVERLEAF_WEB_READY ');i++){if(server.exitCode!==null)throw Error(stderr);await new Promise(r=>setTimeout(r,100));}
 const line=output.split(/\r?\n/).find(s=>s.startsWith('OVERLEAF_WEB_READY '));if(!line)throw Error(stderr);
 const {url}=JSON.parse(line.slice('OVERLEAF_WEB_READY '.length));
 browser=await chromium.launch({channel:'msedge',headless:true});
 const tc=await browser.newContext({viewport:{width:1500,height:1050}}),sc=await browser.newContext({viewport:{width:1500,height:1050}});
 const teacher=await tc.newPage(),student=await sc.newPage();
 for(const p of [teacher,student])p.on('pageerror',e=>errors.push(String(e)));
 async function api(context,route,body,method=body?'POST':'GET'){
  const r=await context.request.fetch(url+'api/'+route,{method,data:body});if(!r.ok())throw Error(`${route}: ${r.status()} ${await r.text()}`);return r.json();
 }
 async function nav(p,name){await p.locator('.writing-sidebar').getByRole('button',{name,exact:true}).click();}
 async function shot(p,name){lastPage=p;await p.waitForTimeout(200);await p.screenshot({path:path.join(home,name+'.png')});checks.push(name);}
 await api(tc,'actions/register',{email:'guide-teacher@example.com',name:'Alex Teacher',password:'test-password',role:'tutor'});
 await api(sc,'actions/register',{email:'guide-student@example.com',name:'Jamie Student',password:'test-password',role:'student'});
 const course=await api(tc,'writing/courses',{title:'Writing Studio · Foundation',kind:'course'});
 await api(tc,`writing/courses/${course.id}/members`,{email:'guide-student@example.com'});
 const practiceAssignment=await api(tc,'writing/assignments',{title:'Guided introduction',classId:course.id,category:'ielts',tasks:[2],profiles:{'2':'discussion'},prompts:{'2':'Discuss public transport and roads.'},writingScope:'intro',purpose:'practice',published:true});
 for(const p of [teacher,student]){await p.goto(url);await p.getByRole('button',{name:'Home',exact:true}).click();await p.getByRole('button',{name:'Open Task Writing',exact:true}).click();await nav(p,'Writing guides');}
 await expect(teacher.locator('.guide-card')).toHaveCount(12);
 await expect(teacher.getByRole('heading',{name:'Writing guides',exact:true})).toHaveCount(1);
 await expect(student.locator('.guide-card')).toHaveCount(0);
 await shot(teacher,'teacher-default-library');await shot(student,'student-private-defaults-hidden');
 const guideTypes=teacher.getByRole('navigation',{name:'Guide assessment types',exact:true});
 for(const type of ['Report','Research']){await guideTypes.getByRole('button',{name:type,exact:true}).click();await expect(teacher.locator('.guide-card')).toHaveCount(0);await expect(teacher.getByRole('heading',{name:`No ${type.toLowerCase()} templates yet`,exact:true})).toBeVisible();await shot(teacher,'empty-'+type.toLowerCase()+'-guides');}
 await guideTypes.getByRole('button',{name:'Report',exact:true}).click();
 await teacher.getByRole('button',{name:'Create guide',exact:true}).click();
 await expect(teacher.getByLabel('Guide assessment type',{exact:true})).toHaveValue('report');
 await teacher.getByLabel('Guide title',{exact:true}).fill('Report guide test draft');
 await teacher.getByLabel('Guide content',{exact:true}).fill('# Report structure\n\n## Evidence\n\nDescribe the evidence.');
 await teacher.getByRole('button',{name:'Save guide',exact:true}).click();
 await expect(teacher.locator('.guide-content')).toContainText('Describe the evidence.');
 await shot(teacher,'custom-report-guide');
 teacher.once('dialog',d=>d.accept());await teacher.getByRole('button',{name:'Delete guide',exact:true}).click();
 await expect(teacher.getByRole('heading',{name:'No report templates yet',exact:true})).toBeVisible();
 await guideTypes.getByRole('button',{name:'IELTS',exact:true}).click();
 await teacher.getByRole('button',{name:'Read guide Bar charts',exact:true}).click();
 await expect(teacher.locator('.guide-content table')).not.toHaveCount(0);
 await expect(teacher.locator('.guide-content ol')).not.toHaveCount(0);
 await expect(teacher.locator('.guide-content em')).not.toHaveCount(0);
 await teacher.locator('.guide-content table').first().scrollIntoViewIfNeeded();await shot(teacher,'bar-chart-markdown-tables');
 await teacher.getByRole('button',{name:'Edit guide',exact:true}).click();
 await teacher.getByRole('button',{name:'Save guide',exact:true}).click();
 await teacher.getByRole('button',{name:'All guides',exact:true}).click();
 await expect(teacher.locator('.guide-card').filter({has:teacher.getByRole('heading',{name:'Bar charts',exact:true})})).toContainText('Default template');
 await expect(teacher.locator('.writing-guides')).not.toContainText('Private version');
 await teacher.getByRole('button',{name:'Read guide Line graphs',exact:true}).click();
 await expect(teacher.locator('.guide-content')).toContainText('3W');
 await teacher.getByRole('button',{name:'Edit guide',exact:true}).click();
 const original=await teacher.getByLabel('Guide content',{exact:true}).inputValue();
 await teacher.getByLabel('Guide content',{exact:true}).fill(original+'\n\nTeacher adaptation for our class.');
 await teacher.getByRole('checkbox',{name:course.title,exact:true}).check();
 await teacher.getByRole('button',{name:'Save guide',exact:true}).click();
 await expect(teacher.locator('.writing-guides .writing-notice[role=status]')).toContainText('Guide saved');
 await shot(teacher,'teacher-adapted-guide');
 await nav(student,'Overview');await nav(student,'Writing guides');
 await expect(student.locator('.guide-card')).toHaveCount(1);
 await student.getByRole('button',{name:'Read guide Line graphs',exact:true}).click();
 await expect(student.locator('.guide-content')).toContainText('Teacher adaptation for our class.');
 await expect(student.getByRole('button',{name:'Edit guide',exact:true})).toHaveCount(0);
 await expect(student.getByRole('button',{name:'Create guide',exact:true})).toHaveCount(0);
 await shot(student,'student-read-only-guide');
 // New guide for Task 2, including raw HTML to verify the safe renderer.
 await teacher.getByRole('button',{name:'Create guide',exact:true}).click();
 await teacher.getByLabel('Guide title',{exact:true}).fill('Our discussion blueprint');
 await teacher.getByLabel('Guide task',{exact:true}).selectOption('2');
 await teacher.getByRole('checkbox',{name:'Opinion / agree–disagree',exact:true}).uncheck();
 await teacher.getByRole('checkbox',{name:'Discuss both views (+ opinion)',exact:true}).check();
 await teacher.getByRole('checkbox',{name:course.title,exact:true}).check();
 const guideText='# Discussion blueprint\n\n## Introduction\nIntroduce both views and your position.\n\n| Step | Action |\n| --- | --- |\n| 1 | *Explain* both sides |\n\n1. First idea\n2. Second idea\n\n## Body\n\nExplain, support and compare.\n\n<img src=x onerror="window.__guideInjected=true">';
 await teacher.getByLabel('Guide content',{exact:true}).fill(guideText);
 await shot(teacher,'teacher-guide-editor');
 teacher.once('dialog',d=>d.dismiss());await nav(teacher,'Overview');
 await expect(teacher.getByLabel('Guide title',{exact:true})).toHaveValue('Our discussion blueprint');
 await teacher.getByRole('button',{name:'Save guide',exact:true}).click();
 await expect(teacher.getByRole('heading',{name:'Our discussion blueprint',exact:true})).toBeVisible();
 const guide=(await api(tc,'writing/guides?search=Our')).items.find(g=>g.title==='Our discussion blueprint');
 const saved=await api(tc,'writing/guides/'+guide.id);expect(saved.content).toBe(guideText);expect(saved.classIds).toEqual([course.id]);
 await nav(student,'Practice exercises');await student.getByRole('button',{name:'Open writing',exact:true}).click();
 const answer=student.getByLabel('Answer Task 2',{exact:true});
 await answer.fill('My introduction remains intact while I consult the guide.');
 await student.getByRole('button',{name:'Writing guide',exact:true}).click();
 const drawer=student.getByRole('dialog',{name:'Practice reference',exact:true});
 await expect(drawer.locator('.guide-card')).toHaveCount(1);
 await drawer.getByRole('button',{name:'Read guide Our discussion blueprint',exact:true}).click();
 await expect(drawer.locator('.guide-content')).toContainText('Introduce both views');
 expect(await student.evaluate(()=>window.__guideInjected)).toBeUndefined();
 await expect(drawer.locator('img')).toHaveCount(0);await expect(drawer.locator('table')).toHaveCount(1);await expect(drawer.locator('ol')).toHaveCount(1);
 await expect(drawer).toHaveAttribute('aria-modal','false');
 await answer.fill('My introduction remains intact while I consult the guide. Updated while reading.');
 await shot(student,'student-practice-reference');
 await student.setViewportSize({width:375,height:812});await expect(drawer).toHaveAttribute('aria-modal','true');await shot(student,'student-practice-reference-mobile');
 await student.setViewportSize({width:1500,height:1050});await expect(drawer).toHaveAttribute('aria-modal','false');
 await student.keyboard.press('Escape');await expect(drawer).toHaveCount(0);
 await expect(answer).toHaveValue('My introduction remains intact while I consult the guide. Updated while reading.');
 // Unshare; the next request must not reveal the cached guide as a new reference.
 await teacher.getByRole('button',{name:'Edit guide',exact:true}).click();
 await teacher.getByRole('checkbox',{name:course.title,exact:true}).uncheck();
 await teacher.getByRole('button',{name:'Save guide',exact:true}).click();
 await expect(teacher.locator('.writing-guides .writing-notice[role=status]')).toContainText('Guide saved');
 await student.getByRole('button',{name:'Writing guide',exact:true}).click();
 await expect(drawer.locator('.guide-card')).toHaveCount(0);
 await expect(drawer).toContainText('No guides available');await student.keyboard.press('Escape');
 teacher.once('dialog',d=>d.accept());await teacher.getByRole('button',{name:'Delete guide',exact:true}).click();
 await expect(teacher.locator('.writing-guides .writing-notice[role=status]')).toContainText('Guide deleted');
 expect((await tc.request.get(url+'api/writing/guides/'+guide.id)).status()).toBe(404);
 await teacher.getByLabel('Filter guide task',{exact:true}).selectOption('1');
 await expect(teacher.locator('.guide-card')).toHaveCount(7);
 await teacher.getByLabel('Filter guide task',{exact:true}).selectOption('');
 for(const mode of ['dark','light']){
  await api(tc,'actions/settings',{mode});await teacher.reload();
  await teacher.getByRole('button',{name:'Home',exact:true}).click();await teacher.getByRole('button',{name:'Open Task Writing',exact:true}).click();await nav(teacher,'Writing guides');
  await expect(teacher.locator('.guide-card')).toHaveCount(12);await shot(teacher,'guide-library-'+mode);
 }
 await api(tc,'writing/assignments',{title:'Closed-book writing test',classId:course.id,category:'ielts',tasks:[2],profiles:{'2':'discussion'},prompts:{'2':'Discuss public transport and roads.'},writingScope:'intro',purpose:'exam',kind:'exam',published:true,durationMinutes:30,examPassword:'test-entry-password',opensAt:new Date(Date.now()-60000).toISOString(),closesAt:new Date(Date.now()+3600000).toISOString()});
 lastPage=student;expect((await api(sc,'writing/overview')).assignments.map(a=>a.title)).toContain('Closed-book writing test');await nav(student,'Examinations');await expect(student.locator('.writing-page-heading h1')).toHaveText('Examinations');await student.getByRole('button',{name:'Enter examination',exact:true}).click();
 await student.getByLabel('Entry password',{exact:true}).fill('test-entry-password');await student.getByRole('button',{name:'Start examination',exact:true}).click();
 await expect(student.getByLabel('Answer Task 2',{exact:true})).toBeVisible();await expect(student.getByRole('button',{name:'Writing guide',exact:true})).toHaveCount(0);
 const savedPractice=await api(sc,'writing/assignments/'+practiceAssignment.id+'/draft');expect(savedPractice.answers['2']).toBe('My introduction remains intact while I consult the guide. Updated while reading.');expect(savedPractice.blocks?.['2']).toBeUndefined();await shot(student,'examination-no-reference');
 await teacher.setViewportSize({width:375,height:812});await shot(teacher,'guide-library-mobile');
 expect(await teacher.evaluate(()=>document.documentElement.scrollWidth<=innerWidth+1)).toBe(true);
 expect(errors).toEqual([]);
 fs.writeFileSync(path.join(home,'report.json'),JSON.stringify({passed:true,checks,errors},null,2));
 console.log('Writing guides browser checks passed: '+checks.length+' views');
}catch(e){if(lastPage){await lastPage.screenshot({path:path.join(home,'failure.png')}).catch(()=>{});fs.writeFileSync(path.join(home,'failure.html'),await lastPage.content());}fs.writeFileSync(path.join(home,'report.json'),JSON.stringify({passed:false,error:String(e),checks,errors,stderr},null,2));throw e;}
finally{await browser?.close();server.kill();}
