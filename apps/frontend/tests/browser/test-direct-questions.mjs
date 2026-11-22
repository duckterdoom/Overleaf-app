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
 async function api(ctx,route,body){const r=await ctx.request.fetch(url+'api/'+route,{method:body?'POST':'GET',data:body});if(!r.ok())throw Error(route+': '+await r.text());return r.json();}
 async function shot(p,name){lastPage=p;await p.screenshot({path:path.join(home,name+'.png')});checks.push(name);}
 async function nav(p,name){await p.locator('.writing-sidebar').getByRole('button',{name,exact:true}).click();}
 await api(tc,'actions/register',{email:'direct-teacher@example.com',name:'Alex Teacher',password:'test-password',role:'tutor'});
 await api(sc,'actions/register',{email:'direct-student@example.com',name:'Jamie Student',password:'test-password',role:'student'});
 const course=await api(tc,'writing/courses',{title:'Direct questions course',kind:'course'});
 await api(tc,`writing/courses/${course.id}/members`,{email:'direct-student@example.com'});
 await teacher.goto(url);await teacher.getByRole('button',{name:'Home',exact:true}).click();await teacher.getByRole('button',{name:'Open Task Writing',exact:true}).click();
 await nav(teacher,'Question library');
 await teacher.getByRole('button',{name:'Create question',exact:true}).click();
 await teacher.getByRole('button',{name:'Assign to students',exact:true}).click();
 await expect(teacher.getByLabel('Variant',{exact:true})).toHaveCount(0);
 await expect(teacher.getByRole('option',{name:/Academic|General Training/})).toHaveCount(0);
 await teacher.getByLabel('Exercise title',{exact:true}).fill('Direct chart and discussion');
 await teacher.getByLabel('Exercise description',{exact:true}).fill('Compare the main trends and support your argument with examples.');
 await teacher.getByLabel('Course',{exact:true}).selectOption(course.id);
 await teacher.getByLabel('Assigned tasks',{exact:true}).selectOption('1,2');
 await teacher.getByLabel('Question Task 1',{exact:true}).fill('The chart shows commuting methods. Summarise the main features.');
 await teacher.getByLabel('Question Task 2',{exact:true}).fill('Discuss public transport and road building.');
 await expect(teacher.getByText('Import question Task 1 from a project source',{exact:true})).toHaveCount(0);
 const chart=await teacher.evaluate(()=>{
  const canvas=document.createElement('canvas');canvas.width=640;canvas.height=340;const ctx=canvas.getContext('2d');
  ctx.fillStyle='#ffffff';ctx.fillRect(0,0,640,340);ctx.fillStyle='#17233b';ctx.font='20px sans-serif';ctx.fillText('Commuting methods in a city (%)',40,35);
  for(const [i,v] of [60,35,20].entries()){ctx.fillStyle='#7147c6';ctx.fillRect(95+i*175,290-v*3,50,v*3);ctx.fillStyle='#3454d1';ctx.fillRect(150+i*175,290-(v+10)*3,50,(v+10)*3);ctx.fillStyle='#17233b';ctx.font='16px sans-serif';ctx.fillText(['Car','Bus','Bicycle'][i],110+i*175,318);}
  return canvas.toDataURL('image/png').split(',')[1];
 });const image=Buffer.from(chart,'base64');
 await teacher.getByLabel('Upload question image Task 1',{exact:true}).setInputFiles({name:'chart.png',mimeType:'image/png',buffer:image});
 await expect(teacher.locator('.writing-question-upload img')).toBeVisible();
 await teacher.getByLabel('Upload question image Task 1',{exact:true}).setInputFiles({name:'bad.txt',mimeType:'text/plain',buffer:Buffer.from('invalid')});
 await expect(teacher.locator('.writing-question-upload [role=alert]')).toContainText('Choose a PNG');
 await expect(teacher.locator('.writing-question-upload img')).toBeVisible();
 await teacher.locator('.writing-question-upload').first().scrollIntoViewIfNeeded();await shot(teacher,'tutor-direct-question-image');
 for(const purpose of ['practice','homework','exam']){
  await teacher.getByLabel('Exercise purpose',{exact:true}).selectOption(purpose);
  await expect(teacher.getByLabel('Exercise description',{exact:true})).toHaveValue(/Compare the main trends/);
 }
 await teacher.getByLabel('Exercise purpose',{exact:true}).selectOption('homework');
 await teacher.setViewportSize({width:390,height:844});await shot(teacher,'tutor-direct-mobile');
 expect(await teacher.evaluate(()=>document.documentElement.scrollWidth<=innerWidth+1)).toBe(true);
 await teacher.setViewportSize({width:1500,height:1000});
 await teacher.getByRole('button',{name:'Create and assign',exact:true}).last().click();
 await expect(teacher.getByText('Exercise assigned to students.',{exact:true})).toBeVisible();
 const assigned=(await api(tc,'writing/overview')).assignments.find(a=>a.title==='Direct chart and discussion');
 expect(assigned.description).toContain('Compare the main trends');expect(assigned.promptFiles['1'].origin).toBe('upload');
 await nav(teacher,'Created exercises');await teacher.getByRole('button',{name:'View question & students',exact:true}).click();
 const details=teacher.getByRole('region',{name:'Exercise details',exact:true}),tabs=teacher.getByRole('tablist',{name:'Exercise type',exact:true});
 await expect(details).toBeVisible();for(const name of ['Assigned exercises','Practice','Examinations'])await expect(tabs.getByRole('tab',{name,exact:true})).toBeInViewport();
 await expect(details).toContainText('The chart shows commuting methods.');await expect(details.locator('img')).toBeVisible();
 const detailBounds=await details.boundingBox(),tabBounds=await tabs.boundingBox();expect(detailBounds.y).toBeGreaterThanOrEqual(tabBounds.y+tabBounds.height);
 const body=details.getByLabel('Question and student progress',{exact:true});await body.evaluate(e=>e.scrollTop=e.scrollHeight);await expect(details.getByRole('heading',{name:/Student progress/})).toBeInViewport();await expect(tabs).toBeInViewport();await shot(teacher,'tutor-details-progress-tabs');
 await teacher.setViewportSize({width:390,height:844});await body.evaluate(e=>e.scrollTop=0);await expect(tabs).toBeInViewport();expect(await teacher.evaluate(()=>document.documentElement.scrollWidth<=innerWidth+1)).toBe(true);await shot(teacher,'tutor-exercise-details-mobile');
 await tabs.getByRole('tab',{name:'Practice',exact:true}).click();await expect(details).toHaveCount(0);await tabs.getByRole('tab',{name:'Examinations',exact:true}).click();await expect(details).toHaveCount(0);await tabs.getByRole('tab',{name:'Assigned exercises',exact:true}).click();
 await teacher.setViewportSize({width:1500,height:1000});await teacher.getByRole('button',{name:'View question & students',exact:true}).click();await teacher.getByRole('button',{name:'Close exercise details',exact:true}).click();await expect(details).toHaveCount(0);await expect(teacher.getByRole('button',{name:'View question & students',exact:true})).toBeFocused();

 await student.goto(url);await student.getByRole('button',{name:'Home',exact:true}).click();await student.getByRole('button',{name:'Open Task Writing',exact:true}).click();await nav(student,'Assigned exercises');
 await student.getByRole('button',{name:'Open writing',exact:true}).click();
 await expect(student.locator('.writing-student-description')).toHaveCount(0);await expect(student.locator('.writing-question')).not.toContainText('Compare the main trends');
 await expect(student.locator('.writing-question img')).toBeVisible();
 expect(await student.locator('.writing-question img').evaluate(e=>e.complete&&e.naturalWidth>0)).toBe(true);
 for(const task of [1,2]){
  if(task===2)await student.getByRole('tab',{name:'Task 2',exact:true}).click();
  const answer=student.getByLabel(`Answer Task ${task}`,{exact:true});await answer.fill('A short answer.');
  await expect(student.locator('.writing-word-warning')).toHaveCount(0);
  await expect(student.locator('.writing-answer footer')).not.toContainText(/\/\s*(150|250)/);
  await expect(student.locator('.writing-question')).not.toContainText('At least');
  const long='A detailed sentence. '.repeat(6000);await answer.fill(long);
  await student.getByRole('button',{name:'Save writing',exact:true}).click();
  await expect(student.locator('.writing-answer header')).toContainText('Saved on server');
  expect((await api(sc,`writing/assignments/${assigned.id}/draft`)).answers[String(task)]).toBe(long);
  await shot(student,'student-unlimited-task-'+task);
 }
 await student.getByRole('button',{name:'Check and submit',exact:true}).click();
 await expect(student.locator('.writing-word-warning')).toHaveCount(0);
 await shot(student,'student-submission-check');
 expect(errors).toEqual([]);fs.writeFileSync(path.join(home,'report.json'),JSON.stringify({checks,errors},null,2));console.log('Direct question UI passed: '+checks.length+' views');
}catch(e){if(lastPage)await lastPage.screenshot({path:path.join(home,'failure.png')}).catch(()=>{});console.error(stderr);throw e;}
finally{await browser?.close();server.kill();}
