import {chromium,expect as baseExpect} from '@playwright/test';
import {spawn} from 'node:child_process';
import fs from 'node:fs';
import path from 'node:path';
const expect=baseExpect.configure({timeout:25000}),repo=path.resolve('../..'),home=path.resolve(process.argv[3]);
if(fs.existsSync(home))throw Error('Use a fresh test home');fs.mkdirSync(home,{recursive:true});
const server=spawn(path.resolve(process.argv[2]),[path.join(repo,'apps/backend/tests/support/run_mongo_web.py'),'--home',home,'--port','0','--no-browser'],{cwd:repo,windowsHide:true});
let stdout='',stderr='',browser,lastPage;const checks=[],errors=[];
server.stdout.on('data',v=>stdout+=v);server.stderr.on('data',v=>stderr+=v);
try{
 for(let i=0;i<400&&!stdout.includes('OVERLEAF_WEB_READY ');i++){if(server.exitCode!==null)throw Error(stderr);await new Promise(r=>setTimeout(r,100));}
 const ready=stdout.split(/\r?\n/).find(v=>v.startsWith('OVERLEAF_WEB_READY '));if(!ready)throw Error(stderr);
 const {url}=JSON.parse(ready.slice('OVERLEAF_WEB_READY '.length));browser=await chromium.launch({channel:'msedge',headless:true});
 const tc=await browser.newContext({viewport:{width:1560,height:1100}}),sc=await browser.newContext({viewport:{width:1560,height:1100}}),gc=await browser.newContext();
 const teacher=await tc.newPage(),student=await sc.newPage(),guest=await gc.newPage();for(const p of[teacher,student,guest])p.on('pageerror',e=>errors.push(String(e)));
 async function api(c,route,body,method=body?'POST':'GET'){const response=await c.request.fetch(url+'api/'+route,{method,data:body});if(!response.ok())throw Error(`${route}: ${response.status()} ${await response.text()}`);return response.json();}
 async function shot(p,name){lastPage=p;await p.waitForTimeout(200);if(name==='dark-dashboard')fs.writeFileSync(path.join(home,'dark-style.json'),JSON.stringify(await p.locator('.dashboard-stat').first().evaluate(e=>({style:getComputedStyle(e).cssText,bg:getComputedStyle(e).backgroundColor,color:getComputedStyle(e).color,opacity:getComputedStyle(e).opacity,disabled:e.disabled,parent:getComputedStyle(e.parentElement).opacity})),null,2));await p.screenshot({path:path.join(home,name+'.png'),fullPage:false});checks.push(name);}
 async function nav(p,name){await p.locator('.writing-sidebar').getByRole('button',{name,exact:true}).click();}
 async function mode(c,p,value){await api(c,'actions/settings',{mode:value});await p.reload();await expect(p.locator('html')).toHaveAttribute('data-mode',value);}
 async function noOverflow(p,label){expect(await p.evaluate(()=>document.documentElement.scrollWidth<=innerWidth+1),label).toBe(true);}
 await api(tc,'actions/register',{name:'Alex Morgan',email:'design-teacher@example.com',password:'test-password',role:'tutor'});
 await api(sc,'actions/register',{name:'Jamie Taylor',email:'design-student@example.com',password:'test-password',role:'student'});
 const course=await api(tc,'writing/courses',{title:'Academic Writing · Foundation',kind:'course'});
 await api(tc,`writing/courses/${course.id}/members`,{email:'design-student@example.com'});
 const settings=await api(tc,'writing/settings');
 const content={title:'Introduction · sustainable transport',classId:course.id,category:'ielts',variant:'academic',tasks:[2],profiles:{'2':'discussion'},prompts:{'2':'Some people believe cities should invest in public transport. Others favour building new roads. Introduce both views and state the direction of your response.'},writingScope:'intro',purpose:'practice',published:true};
 const assignment=await api(tc,'writing/assignments',content);
 await api(tc,'writing/questions',{visibility:'public',content:{...content,rubricCatalog:{discussion:settings.catalog.discussion}}});
 const exam=await api(tc,'writing/assignments',{...content,title:'Writing assessment · Week 1',purpose:'exam',kind:'exam',durationMinutes:30,examPassword:'example-exam-password',opensAt:new Date(Date.now()-3600000).toISOString(),closesAt:new Date(Date.now()+86400000).toISOString()});
 let draft=await api(sc,`writing/assignments/${assignment.id}/draft`);
 draft=await api(sc,`writing/assignments/${assignment.id}/draft`,{revision:draft.revision,answers:{'2':'Public transport can improve daily life in growing cities. While some residents support more roads, others believe that frequent buses and trains offer greater long-term value. This introduction considers both perspectives.'}},'PUT');
 const submission=await api(sc,`writing/assignments/${assignment.id}/submit`,{revision:draft.revision,requestId:crypto.randomUUID()});
 const checkStates=Object.fromEntries(settings.catalog.discussion.checks.intro.map(c=>[c.id,'met']));
 await api(tc,`writing/submissions/${submission.id}/review`,{revision:0,tasks:{'2':{profile:'discussion',scores:{},sections:[{paragraph:0,kind:'intro',title:'Introduction',checks:checkStates,note:'Clear context and a balanced introduction. Consider making the direction of your own response more specific.'}]}},summary:'A well-focused introduction with clear organization.',recommendations:'Try a more precise final sentence.'},'PUT');
 await api(tc,`writing/submissions/${submission.id}/publish`,{revision:1});
 const project=await api(tc,'projects',{title:'Sustainable Cities · Research Paper'});await api(tc,'projects');
 await api(tc,`projects/${project.id}/file`,{path:'main.tex',content:String.raw`\documentclass[12pt]{article}
\usepackage[margin=25mm]{geometry}\usepackage{hyperref}
\begin{document}\title{Sustainable Cities}\author{Alex Morgan}\maketitle
\section{Introduction}This paper explores the role of public transport in sustainable cities. See Section~\ref{sec:discussion}.
\section{Methods}We consider daily travel patterns and access to services.
\section{Discussion}Accessible transport connects people with education and employment.
\end{document}`},'PUT');
 await guest.goto(url);await shot(guest,'auth-login');await guest.getByRole('button',{name:'Register',exact:true}).click();await shot(guest,'auth-register');
 const pending=await browser.newContext({viewport:{width:1440,height:1050}});await api(pending,'actions/register',{name:'New learner',email:'design-pending@example.com',password:'test-password'});const rolePage=await pending.newPage();await rolePage.goto(url);await expect(rolePage.locator('.role-options')).toBeVisible();await shot(rolePage,'auth-role-selection');
 await teacher.goto(url);await expect(teacher.locator('.dashboard-stat').first().locator('strong')).toHaveText('1');await shot(teacher,'teacher-dashboard');
 await expect(teacher.locator('.app-header .product-navigation')).toHaveCount(0);
 await expect(teacher.locator('.document-library-heading')).toHaveCount(0);await expect(teacher.locator('.dashboard-activity')).toHaveCount(0);
 await expect(teacher.getByRole('button',{name:'Create a document',exact:true})).toHaveCount(0);
 const identityHeader=await teacher.locator('.app-header').boundingBox();await expect(teacher.locator('.product-navigation')).toHaveCount(0);
 await expect(teacher.locator('.dashboard-home')).toHaveCSS('overflow','hidden');await expect(teacher.locator('.projects-main')).toHaveCSS('overflow','auto');
 await teacher.getByRole('button',{name:'Open LaTeX Studio',exact:true}).click();await expect(teacher.getByRole('heading',{name:'Document library',exact:true})).toBeVisible();await expect(teacher.locator('.academic-dashboard')).toHaveCount(0);
 await teacher.getByRole('button',{name:'Home',exact:true}).click();
 const sharedNav=teacher.locator('.workspace-navigation');await expect(sharedNav).toHaveCount(1);
 await expect(sharedNav.getByRole('button',{name:'Dashboard',exact:true})).toHaveAttribute('aria-current','page');
 await expect(sharedNav).toContainText('Task Writing');await expect(sharedNav).toContainText('LaTeX Studio');
 await sharedNav.getByRole('button',{name:/^Shared with you/}).click();await expect(teacher.getByRole('heading',{name:'No shared documents yet',exact:true})).toBeVisible();await expect(sharedNav.getByRole('button',{name:/^Shared with you/})).toHaveAttribute('aria-current','page');await shot(teacher,'shared-library-unified-sidebar');
 await sharedNav.getByRole('button',{name:'Question library',exact:true}).click();await expect(teacher.getByRole('heading',{name:'Question library',exact:true})).toBeVisible();
 await sharedNav.getByRole('button',{name:/^All projects/}).click();await expect(teacher.locator('.project-card-open')).toHaveCount(1);await expect(teacher.locator('.project-navigation')).toHaveCount(0);
 await sharedNav.getByRole('button',{name:'Template library',exact:true}).click();await expect(teacher.locator('.project-modal')).toBeVisible();await shot(teacher,'templates-unified-sidebar');await teacher.locator('.project-modal > header').getByRole('button',{name:/Close/}).click();
 await sharedNav.getByRole('button',{name:'Dashboard',exact:true}).click();await expect(teacher.getByRole('heading',{name:'Recent documents',exact:true})).toBeVisible();

 await student.goto(url);await expect(student.locator('.dashboard-stat').first().locator('strong')).toHaveText('1');await shot(student,'student-dashboard');await expect(student.locator('.workspace-navigation').getByRole('button',{name:'Question library',exact:true})).toHaveCount(0);await expect(student.locator('.workspace-navigation').getByRole('button',{name:/^All projects/})).toBeVisible();
 await teacher.getByRole('button',{name:'Settings',exact:true}).click();await expect(teacher.getByRole('dialog',{name:'Settings',exact:true})).toBeVisible();await shot(teacher,'settings-general');
 for(const button of await teacher.locator('.feature-layout > nav > button').all()){await button.click();await shot(teacher,'settings-'+(await button.innerText()).toLowerCase().replace(/[^a-z]+/g,'-'));}
 await teacher.getByRole('dialog',{name:'Settings',exact:true}).getByRole('button',{name:'Close',exact:true}).click();
 await teacher.getByRole('button',{name:'Profile',exact:true}).click();await shot(teacher,'profile');await teacher.getByRole('dialog',{name:'Profile',exact:true}).getByRole('button',{name:'Close',exact:true}).click();
 await teacher.getByRole('button',{name:'Home',exact:true}).click();await teacher.locator('.dashboard-stats').getByRole('button',{name:/Courses & groups/}).click();await expect(teacher.locator('.writing-courses')).toContainText(course.title);await shot(teacher,'teacher-courses');
 await nav(teacher,'Overview');await shot(teacher,'teacher-writing-overview');
 await nav(teacher,'Question library');await expect(teacher.getByRole('heading',{name:'Question library',exact:true})).toHaveCount(1);await expect(teacher.locator('.writing-page-heading').getByRole('button',{name:'Create question',exact:true})).toBeVisible();await teacher.locator('.writing-page-heading').getByRole('button',{name:'Create question',exact:true}).click();
 await expect(teacher.locator('.writing-form')).toBeVisible();await expect(teacher.locator('.writing-form-heading')).toHaveCount(0);
 await teacher.locator('.writing-form').getByRole('button',{name:'Cancel',exact:true}).click();
 await expect(teacher.locator('.writing-page-heading').getByRole('button',{name:'Create question',exact:true})).toBeVisible();
 await shot(teacher,'teacher-question-library');
 await nav(teacher,'Assign exercise');await expect(teacher.locator('.writing-form-heading')).toHaveCount(0);
 await expect(teacher.getByLabel('Total score',{exact:true})).toHaveValue('10');
 const setup=teacher.getByRole('region',{name:'Assessment categories Task 2',exact:true});
 await setup.getByRole('button',{name:'Whole paper',exact:true}).click();
 const categoryDialog=teacher.getByRole('dialog',{name:'Customize whole paper',exact:true});
 await expect(categoryDialog.getByLabel('Criterion weight intro 1',{exact:true})).toHaveValue('2');
 await categoryDialog.getByRole('button',{name:'Done',exact:true}).click();await expect(categoryDialog).toBeHidden();
 await shot(teacher,'teacher-exercise-form');

 // Verify that both UI choices create real, independent assignment snapshots.
 for(const custom of [true,false]){
  if(!custom)await nav(teacher,'Assign exercise');
  const title=custom?'Custom checklist assignment':'Default checklist assignment';
  await teacher.getByLabel('Exercise title',{exact:true}).fill(title);
  await teacher.getByRole('button',{name:'Remove whole paper Task 2',exact:true}).click();await teacher.getByRole('region',{name:'Assessment categories Task 2',exact:true}).getByRole('button',{name:'Introduction',exact:true}).click();
  await teacher.getByLabel('Course or group',{exact:true}).selectOption(course.id);
  await teacher.getByLabel('Question Task 2',{exact:true}).fill('Introduce the sustainable transport discussion.');
  const profileId=await teacher.getByLabel('Rubric Task 2',{exact:true}).inputValue();
  if(custom){await teacher.getByRole('region',{name:'Assessment categories Task 2',exact:true}).getByRole('button',{name:'Introduction',exact:true}).click();await teacher.getByRole('dialog').getByLabel('Criterion intro 1',{exact:true}).fill('Assignment-specific evidence criterion');await teacher.getByRole('dialog').getByRole('button',{name:'Done',exact:true}).click();}
  else await expect(teacher.getByRole('region',{name:'Assessment categories Task 2',exact:true})).toContainText('Introduction');
  await teacher.locator('.writing-form').getByRole('button',{name:'Assign exercise',exact:true}).click();
  let assigned;
  await expect.poll(async()=>{assigned=(await api(tc,'writing/overview')).assignments.find(a=>a.title===title);return !!assigned;}).toBe(true);
  const saved=await api(tc,'writing/assignments/'+assigned.id);
  expect(saved.rubricCatalog[profileId].checks.intro[0].text).toBe(custom?'Assignment-specific evidence criterion':settings.catalog[profileId].checks.intro[0].text);
 }
 await nav(teacher,'Assessment criteria');await expect(teacher.locator('.rubric-manager')).toBeVisible();await expect(teacher.getByRole('heading',{name:'Assessment criteria',exact:true})).toHaveCount(1);await expect(teacher.locator('.writing-page-heading').getByRole('button',{name:'Save rubric templates',exact:true})).toBeVisible();await teacher.locator('.writing-page-heading').getByRole('button',{name:'Save rubric templates',exact:true}).click();
 await expect(teacher.getByRole('status')).toContainText('Rubric templates saved');
 await shot(teacher,'teacher-rubrics');await teacher.locator('.rubric-editor textarea').first().hover();await teacher.mouse.wheel(0,20000);await expect(teacher.getByRole('button',{name:'Add assessment section',exact:true})).toBeInViewport();expect(await teacher.locator('.writing-main').evaluate(e=>e.scrollTop)).toBeGreaterThan(0);await shot(teacher,'teacher-rubrics-scrolled');await teacher.locator('.writing-main').evaluate(e=>e.scrollTop=0);
 await nav(teacher,'Created exercises');await teacher.getByRole('tab',{name:'Practice',exact:true}).click();await shot(teacher,'teacher-created-exercises');
 await nav(teacher,'Student submissions');await teacher.getByRole('tab',{name:'Practice',exact:true}).click();await shot(teacher,'teacher-submissions');await teacher.getByRole('button',{name:'Open assessment',exact:true}).click();
 await teacher.getByRole('button',{name:'Open checklist for paragraph 1',exact:true}).click();const checklist=teacher.getByRole('dialog',{name:/Checklist/});await expect(checklist).toBeVisible();const handle=checklist.getByRole('button',{name:'Move checklist'});const box=await handle.boundingBox();const before=await checklist.boundingBox();await teacher.mouse.move(box.x+box.width/2,box.y+box.height/2);await teacher.mouse.down();await teacher.mouse.move(box.x+box.width/2-160,box.y+box.height/2+75,{steps:8});await teacher.mouse.up();expect((await checklist.boundingBox()).x).toBeLessThan(before.x-80);expect((await checklist.boundingBox()).y).toBeGreaterThan(before.y+50);await checklist.locator('input[type=checkbox]').first().uncheck();await checklist.getByLabel('Paragraph feedback 1',{exact:true}).fill('Floating checklist feedback retained after closing.');await checklist.getByRole('button',{name:'Collapse checklist',exact:true}).click();
 await expect(checklist.getByRole('button',{name:'Expand checklist',exact:true})).toHaveAttribute('aria-expanded','false');
 expect((await checklist.boundingBox()).height).toBeLessThan(80);
 const collapsedHandle=await handle.boundingBox();
 await teacher.mouse.move(collapsedHandle.x+30,collapsedHandle.y+15);await teacher.mouse.down();
 await teacher.mouse.move(collapsedHandle.x-80,collapsedHandle.y+220,{steps:8});await teacher.mouse.up();
 expect((await checklist.boundingBox()).x).toBeLessThan(collapsedHandle.x-50);
 expect((await checklist.boundingBox()).y).toBeGreaterThan(collapsedHandle.y+150);
 await expect(checklist.locator('.floating-checklist-content')).toBeHidden();
 await shot(teacher,'floating-checklist-collapsed');
 await checklist.getByRole('button',{name:'Expand checklist',exact:true}).click();
 await expect(checklist.getByLabel('Paragraph feedback 1',{exact:true})).toHaveValue('Floating checklist feedback retained after closing.');
 await expect(checklist.locator('input[type=checkbox]').first()).not.toBeChecked();
 expect(await checklist.evaluate(e=>e.getBoundingClientRect().bottom<=innerHeight)).toBe(true);
 await shot(teacher,'floating-checklist-dragged');
 await teacher.setViewportSize({width:375,height:812});
 await expect.poll(async()=>checklist.evaluate(e=>{const r=e.getBoundingClientRect();return r.left>=0&&r.right<=innerWidth+1&&r.top>=0&&r.bottom<=innerHeight+1;})).toBe(true);
 await shot(teacher,'floating-checklist-mobile');
 await teacher.setViewportSize({width:1560,height:1100});await checklist.getByRole('button',{name:'Close checklist',exact:true}).click();await expect(checklist).toHaveCount(0);await teacher.getByRole('button',{name:'Open checklist for paragraph 1',exact:true}).click();await expect(checklist).toBeVisible();await expect(checklist.locator('input[type=checkbox]').first()).not.toBeChecked();await expect(checklist.getByLabel('Paragraph feedback 1',{exact:true})).toHaveValue('Floating checklist feedback retained after closing.');await checklist.getByRole('button',{name:'Move checklist',exact:true}).focus();const keyboardLeft=(await checklist.boundingBox()).x;await teacher.keyboard.press('ArrowLeft');expect((await checklist.boundingBox()).x).toBeLessThan(keyboardLeft);const keyboardTop=(await checklist.boundingBox()).y;await teacher.keyboard.press('ArrowUp');expect((await checklist.boundingBox()).y).toBeLessThan(keyboardTop);await teacher.keyboard.press('Escape');await expect(checklist).toHaveCount(0);await expect(teacher.getByRole('button',{name:'Open checklist for paragraph 1',exact:true})).toBeFocused();await teacher.getByRole('button',{name:'Save assessment draft',exact:true}).click();await expect.poll(async()=>{const saved=await api(tc,'writing/submissions/'+submission.id);return saved.review?.tasks?.['2']?.sections?.[0]?.note;}).toBe('Floating checklist feedback retained after closing.');await shot(teacher,'teacher-assessment');await teacher.getByRole('button',{name:'Assessment summary',exact:true}).click();await shot(teacher,'teacher-assessment-summary');await teacher.keyboard.press('Escape');
 await student.getByRole('button',{name:'Home',exact:true}).click();await student.locator('.dashboard-stats').getByRole('button',{name:/Courses & groups/}).click();await shot(student,'student-courses');await nav(student,'Overview');await shot(student,'student-writing-overview');
 await nav(student,'Practice exercises');await student.getByRole('button',{name:'Open writing',exact:true}).click();await expect(student.getByLabel('Answer Task 2',{exact:true})).toHaveValue(/Public transport/);await shot(student,'student-writing');
 await nav(student,'Examinations');await student.getByRole('button',{name:'Enter examination',exact:true}).click();await shot(student,'student-exam-entry');await student.getByRole('button',{name:'Cancel',exact:true}).click();
 await nav(student,'Results & feedback');await student.getByRole('tab',{name:'Practice',exact:true}).click();await shot(student,'student-results');
 await mode(tc,teacher,'dark');await teacher.getByRole('button',{name:'Home',exact:true}).click();await shot(teacher,'dark-dashboard');
 await teacher.getByRole('button',{name:'Home',exact:true}).click();await teacher.locator('.dashboard-stats').getByRole('button',{name:/Courses & groups/}).click();await shot(teacher,'dark-courses');
 await mode(tc,teacher,'light');await teacher.getByRole('button',{name:'Home',exact:true}).click();await teacher.getByRole('button',{name:'Open LaTeX Studio',exact:true}).click();await expect(teacher.locator('.academic-dashboard')).toHaveCount(0);await shot(teacher,'document-library');
 await teacher.getByRole('button',{name:'New project',exact:true}).click();const templateCatalog=await api(tc,'templates');
 await expect(teacher.locator('.template-grid strong')).toHaveText(['Blank project',...templateCatalog.templates.map(t=>t.title.en)]);
 await expect(teacher.locator('.template-grid > button > span')).toHaveText(['Start with a blank page.',...templateCatalog.templates.map(t=>t.description.en)]);
 await expect(teacher.getByLabel('Document language',{exact:true})).toHaveValue('vi');
 await expect(teacher.getByLabel('Document language',{exact:true}).locator('option[value=vi]')).toHaveText('Vietnamese');
 await teacher.getByLabel('Document language',{exact:true}).selectOption('en');
 await expect(teacher.locator('.template-grid strong')).toHaveText(['Blank project',...templateCatalog.templates.map(t=>t.title.en)]);
 await teacher.getByLabel('Document language',{exact:true}).selectOption('vi');
 for(const mode of ['light','dark']){
  await teacher.locator('html').evaluate((e,mode)=>e.setAttribute('data-mode',mode),mode);
  await expect.poll(()=>teacher.locator('.template-grid button.chosen').evaluate(e=>{
   const expected=document.createElement('span');expected.style.background='var(--ui-editor-soft)';document.body.appendChild(expected);
   const result=getComputedStyle(e).backgroundColor===getComputedStyle(expected).backgroundColor;expected.remove();return result;
  })).toBe(true);
  await shot(teacher,'document-templates-'+mode);
 }
 await teacher.locator('html').evaluate(e=>e.setAttribute('data-mode','light'));
 await shot(teacher,'document-templates');await teacher.locator('.project-modal > header').getByRole('button',{name:/Close/}).click();
 await teacher.locator('.project-card-open').click();await expect(teacher.locator('.cm-content')).toBeVisible();await shot(teacher,'latex-editor');
 const editorHeader=await teacher.locator('.app-header').boundingBox();expect(editorHeader.height).toBe(identityHeader.height);
 await expect(teacher.locator('.app-header .project-breadcrumb')).toHaveCount(0);await expect(teacher.locator('.project-context-bar .project-breadcrumb')).toBeVisible();
 await teacher.getByRole('button',{name:'Layout',exact:true}).click();await teacher.getByRole('button',{name:/^Focus mode/}).click();await expect(teacher.getByRole('button',{name:'Exit focus mode',exact:true})).toBeVisible();await teacher.keyboard.press('Escape');await expect(teacher.locator('.desktop-shell')).not.toHaveClass(/focus-mode/);

 await teacher.locator('.compile-button').click();await expect(teacher.locator('.pdf-sheet canvas').first()).toBeVisible({timeout:90000});await shot(teacher,'latex-pdf');
 await teacher.locator('.logs-toggle-btn').click();await expect(teacher.locator('.logs-view')).toBeVisible();await shot(teacher,'latex-logs');
 await teacher.getByRole('button',{name:'Share',exact:true}).click();await expect(teacher.locator('.share-modal')).toBeVisible();await shot(teacher,'sharing');await teacher.locator('.share-modal').getByRole('button',{name:'Done',exact:true}).click();
 await teacher.locator('.workspace-activity-bar').getByRole('button',{name:'Review',exact:true}).click();await shot(teacher,'editor-review');await teacher.locator('.workspace-activity-bar').getByRole('button',{name:'Collaborator chat',exact:true}).click();await shot(teacher,'editor-chat');
 await teacher.getByRole('button',{name:'History',exact:true}).click();await expect(teacher.locator('.history-screen')).toBeVisible();await shot(teacher,'history');await teacher.getByRole('button',{name:'Back to editor',exact:true}).click();
 // Existing editor tabs and content survive visual navigation; no PDF/content transforms are applied.
 expect(await teacher.locator('.cm-content').innerText()).toContain('Sustainable Cities');
 await teacher.getByRole('button',{name:'Back to projects (Exit)',exact:true}).click();
 for(const [width,height] of[[375,812],[768,1024],[1024,768],[1440,1000],[812,375]]){
  await teacher.setViewportSize({width,height});await teacher.getByRole('button',{name:'Home',exact:true}).click();await noOverflow(teacher,`Dashboard overflow at ${width}`);await shot(teacher,`responsive-dashboard-${width}`);
  await teacher.getByRole('button',{name:'Home',exact:true}).click();await teacher.locator('.dashboard-stats').getByRole('button',{name:/Courses & groups/}).click();await noOverflow(teacher,`Course overflow at ${width}`);await shot(teacher,`responsive-courses-${width}`);
 }
 await teacher.emulateMedia({reducedMotion:'reduce'});await teacher.setViewportSize({width:1024,height:900});await teacher.getByRole('button',{name:'Home',exact:true}).click();await teacher.evaluate(()=>{document.documentElement.style.fontSize='20px';});await noOverflow(teacher,'Large text overflow');await shot(teacher,'large-text-reduced-motion');
 expect(errors).toEqual([]);fs.writeFileSync(path.join(home,'report.json'),JSON.stringify({passed:true,checks,errors,sourceProject:project.id,exam:exam.id},null,2));console.log('UI redesign audit passed: '+checks.length+' views');
}catch(e){if(lastPage)await lastPage.screenshot({path:path.join(home,'failure.png')}).catch(()=>{});fs.writeFileSync(path.join(home,'report.json'),JSON.stringify({passed:false,error:String(e),checks,stderr,errors},null,2));throw e;}finally{await browser?.close();server.kill();}
