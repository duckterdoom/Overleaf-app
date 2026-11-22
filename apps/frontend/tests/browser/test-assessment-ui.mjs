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


 await api(tc,'actions/register',{email:'assessment-tutor@example.com',name:'Assessment Tutor',password:'test-password',role:'tutor'});
 const learner=await api(sc,'actions/register',{email:'assessment-student@example.com',name:'Jamie Student',password:'test-password',role:'student'});
 const other=await browser.newContext();
 await api(other,'actions/register',{email:'assessment-second@example.com',name:'Taylor Student',password:'test-password',role:'student'});
 const course=await api(tc,'writing/classes',{title:'Academic Writing 2026'});
 await api(tc,'writing/classes',{title:'Empty seminar'});
 await api(sc,'writing/classes/join',{code:course.joinCode});await api(other,'writing/classes/join',{code:course.joinCode});
 const session=await api(sc,'session');
 const member=await tc.request.patch(url+`api/writing/courses/${course.id}/members/${session.session.id}`,{data:{displayName:'Jamie Student',studentCode:'ST-204',note:'',revision:0}});expect(member.ok()).toBe(true);
 for (const [category,ctx] of [['ielts',sc],['report',sc],['research',other]]) {
  const assignment=await api(tc,'writing/assignments',{title:category+' response',classId:course.id,category,tasks:[1],profiles:{'1':category==='ielts'?'bar':category},prompts:{'1':'Write your response.'},published:true,scoring:{mode:'points',total:10}});
  const route=`writing/assignments/${assignment.id}`;
  const draft=await api(ctx,route+'/draft');
  const updated=await ctx.request.put(url+'api/'+route+'/draft',{data:{revision:draft.revision,answers:{'1':'A thoughtful introduction. Evidence supports the main idea.'}}});expect(updated.ok()).toBe(true);
  await api(ctx,route+'/submit',{revision:(await updated.json()).revision,requestId:crypto.randomUUID()});
 }
 await api(tc,'writing/guides',{title:'Report structure guide',category:'report',task:1,profiles:['report'],content:'# Report structure',classIds:[course.id]});
 await api(tc,'actions/settings',{mode:'dark'});
 await teacher.goto(url);await teacher.locator('.workspace-navigation').getByRole('button',{name:'Assessment',exact:true}).click();
 await expect(teacher.locator('.writing-shell')).toHaveAttribute('data-writing-ready','true');
 await expect(teacher.locator('.writing-page-heading h1')).toHaveText('Assessment');
 await expect(teacher.getByRole('button',{name:'Create question',exact:true})).toHaveCount(0);await shot(teacher,'assessment-no-duplicate-create');
 await teacher.getByRole('searchbox',{name:'Search exercises',exact:true}).fill('report');
 await expect(teacher.locator('.writing-assignment-list>.writing-card')).toHaveCount(1);
 await teacher.getByLabel('Assessment type',{exact:true}).selectOption('ielts');await expect(teacher.locator('.writing-assignment-list>.writing-card')).toHaveCount(0);
 await expect(teacher.getByText('No exercises match these filters.',{exact:false})).toBeVisible();
 await teacher.getByRole('button',{name:'Clear filters',exact:true}).click();await expect(teacher.locator('.writing-assignment-list>.writing-card')).toHaveCount(3);
 await nav(teacher,'Student submissions');
 const table=teacher.locator('.submission-table');await expect(table.locator('tbody tr')).toHaveCount(3);
 for(const heading of ['Course','Student','Student code','Exercise','Assessment type','Task','Attempt','Submitted at','Status'])await expect(table.getByRole('columnheader',{name:heading,exact:true})).toBeVisible();
 const ielts=table.locator('tbody tr').filter({hasText:'ielts response'});await expect(ielts).toContainText('Academic Writing 2026');await expect(ielts).toContainText('ST-204');await expect(ielts).toContainText('Task 1');
 for(const type of ['report','research']){const row=table.locator('tbody tr').filter({hasText:type+' response'});await expect(row).toContainText('Research');await expect(row.locator('td').nth(5)).toBeEmpty();}
 await expect.poll(()=>teacher.locator('.submission-table-wrap').evaluate(el=>el.scrollWidth<=el.clientWidth+1)).toBe(true);
 await teacher.getByRole('searchbox',{name:'Search submissions',exact:true}).fill('ST-204');await expect(table.locator('tbody tr')).toHaveCount(2);
 await teacher.getByLabel('Assessment type',{exact:true}).selectOption('research');await expect(table.locator('tbody tr')).toHaveCount(1);
 await expect(teacher.getByLabel('IELTS task',{exact:true})).toHaveCount(0);
 await teacher.getByLabel('Status',{exact:true}).selectOption('published');await expect(table).toHaveCount(0);
 await expect(teacher.getByText('No submissions match these filters.',{exact:true})).toBeVisible();
 await teacher.getByRole('button',{name:'Clear filters',exact:true}).click();await expect(table.locator('tbody tr')).toHaveCount(3);
 await teacher.getByLabel('Course',{exact:true}).selectOption('none');await expect(table).toHaveCount(0);await teacher.getByRole('button',{name:'Clear filters',exact:true}).click();
 await shot(teacher,'submission-search-filters');
 const noCode=table.locator('tbody tr').filter({hasText:'Taylor Student'});await expect(noCode.locator('td').nth(2)).toBeEmpty();await shot(teacher,'submission-roster-metadata');
 await teacher.setViewportSize({width:1050,height:850});
 await expect.poll(()=>teacher.evaluate(()=>document.documentElement.scrollWidth<=innerWidth+1)).toBe(true);
 await expect.poll(()=>teacher.locator('.submission-table-wrap').evaluate(el=>el.scrollWidth<=el.clientWidth+1)).toBe(true);
 await expect(table.locator('tbody tr').first()).toHaveCSS('display','grid');
 await expect(table.getByRole('button',{name:'Open assessment',exact:true}).first()).toBeVisible();await shot(teacher,'submission-narrow-actions');
 await teacher.setViewportSize({width:1500,height:1000});
 await student.goto(url);await nav(student,'Submission history');
 await expect(student.locator('.submission-table tbody tr')).toHaveCount(2);await expect(student.locator('.submission-table')).not.toContainText('Taylor Student');
 await nav(student,'Assigned exercises');await student.getByLabel('Status',{exact:true}).selectOption('submitted');await expect(student.locator('.writing-assignment-list>.writing-card')).toHaveCount(2);
 await nav(student,'Submission history');await expect(student.getByLabel('Status',{exact:true})).toHaveValue('');
 await nav(teacher,'Courses');
 await teacher.getByRole('searchbox',{name:'Search courses',exact:true}).fill('ACADEMIC');await expect(teacher.locator('.course-summary-card')).toHaveCount(1);
 await teacher.getByLabel('Enrollment',{exact:true}).selectOption('empty');await expect(teacher.locator('.course-summary-card')).toHaveCount(0);
 await teacher.getByRole('button',{name:'Clear filters',exact:true}).click();await expect(teacher.locator('.course-summary-card')).toHaveCount(2);
 await teacher.locator('.course-summary-card').filter({hasText:'Academic Writing 2026'}).getByRole('button',{name:'View course Academic Writing 2026',exact:true}).click();
 await teacher.getByLabel('Student code',{exact:true}).selectOption('without');await expect(teacher.locator('.course-roster-table tbody tr')).toHaveCount(1);await expect(teacher.locator('.course-roster-table')).toContainText('Taylor Student');
 await shot(teacher,'course-student-code-filter');
 await nav(teacher,'Assessment categories');
 await expect(teacher.getByLabel('Rubric profile',{exact:true})).toBeVisible();
 await expect(teacher.getByLabel('Rubric name',{exact:true})).toBeVisible();await teacher.getByLabel('Rubric name',{exact:true}).fill('Unsaved rubric name');
 await teacher.getByRole('searchbox',{name:'Search rubric templates',exact:true}).fill('unmatched-rubric-xyz');
 await expect(teacher.getByText('No rubric templates match these filters.',{exact:true})).toBeVisible();
 await teacher.getByRole('button',{name:'Clear filters',exact:true}).click();await expect(teacher.getByLabel('Rubric name',{exact:true})).toHaveValue('Unsaved rubric name');await shot(teacher,'rubric-search-filters');
 const intro=teacher.locator('.rubric-section-card').filter({has:teacher.getByLabel('Section title intro',{exact:true})});
 await teacher.getByLabel('Criterion intro 1',{exact:true}).fill('Introduce the main subject clearly.');await teacher.getByLabel('Criterion weight intro 1',{exact:true}).fill('3');
 await intro.getByRole('button',{name:'Add criterion',exact:true}).click();const added=intro.locator('.rubric-check-row').last();await expect(added.locator('textarea')).toHaveValue('New criterion');await added.getByRole('button',{name:/Remove criterion/}).click();
 await intro.locator('summary').click();await expect(teacher.getByLabel('Criterion intro 1',{exact:true})).toBeHidden();await intro.locator('summary').click();
 await teacher.getByRole('button',{name:'Save rubric templates',exact:true}).click();await expect(teacher.getByText('Rubric templates saved. Existing exercises retain their original criteria.',{exact:true})).toBeVisible();
 const stored=await api(tc,'writing/settings');expect(stored.catalog.bar.checks.intro[0].weight).toBe(3);expect(stored.catalog.bar.checks.intro[0].text).toBe('Introduce the main subject clearly.');
 await teacher.getByLabel('Criterion intro 1',{exact:true}).scrollIntoViewIfNeeded();await shot(teacher,'rubric-cards-dark');
 await teacher.setViewportSize({width:650,height:900});await expect.poll(()=>teacher.evaluate(()=>document.documentElement.scrollWidth<=innerWidth+1)).toBe(true);await shot(teacher,'rubric-cards-narrow');await teacher.setViewportSize({width:1500,height:1000});
 await api(tc,'actions/settings',{mode:'light'});await teacher.reload();await nav(teacher,'Assessment categories');await teacher.getByLabel('Criterion intro 1',{exact:true}).scrollIntoViewIfNeeded();await shot(teacher,'rubric-cards-light');
 await nav(teacher,'Writing guides');const categories=teacher.getByRole('navigation',{name:'Guide assessment types'});
 await expect(categories.getByRole('button')).toHaveCount(2);await expect(categories.getByRole('button',{name:'Report',exact:true})).toHaveCount(0);
 await categories.getByRole('button',{name:'Research',exact:true}).click();await expect(teacher.getByRole('heading',{name:'Report structure guide',exact:true})).toBeVisible();await shot(teacher,'research-includes-report');
 await teacher.getByRole('button',{name:'Create guide',exact:true}).click();await expect(teacher.getByLabel('Guide assessment type').locator('option')).toHaveCount(2);
 expect(errors).toEqual([]);console.log('ASSESSMENT_UI_OK matching header/sidebar; roster metadata; blank optional codes; IELTS-only tasks; two guide groups with legacy Report');
}catch(e){if(lastPage)await lastPage.screenshot({path:path.join(home,'failure.png')}).catch(()=>{});console.error(stderr);throw e;}
finally{await browser?.close();server.kill();}
