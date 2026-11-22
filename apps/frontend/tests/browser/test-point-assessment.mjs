import {chromium,expect as baseExpect} from '@playwright/test';
import {spawn} from 'node:child_process';
import fs from 'node:fs';
import path from 'node:path';
const expect=baseExpect.configure({timeout:20000});
const repo=path.resolve('../..'),home=path.resolve(process.argv[3]);
if(fs.existsSync(home))throw Error('Use a fresh test home');fs.mkdirSync(home,{recursive:true});
const server=spawn(path.resolve(process.argv[2]),[path.join(repo,'apps/backend/tests/support/run_mongo_web.py'),'--home',home,'--port','0','--no-browser'],{cwd:repo,windowsHide:true});
let output='',stderr='',browser,lastPage;const errors=[],checks=[];
server.stdout.on('data',v=>output+=v);server.stderr.on('data',v=>stderr+=v);
try{
 for(let i=0;i<400&&!output.includes('OVERLEAF_WEB_READY ');i++){if(server.exitCode!==null)throw Error(stderr);await new Promise(r=>setTimeout(r,100));}
 const ready=output.split(/\r?\n/).find(l=>l.startsWith('OVERLEAF_WEB_READY '));if(!ready)throw Error(stderr);
 const {url}=JSON.parse(ready.slice('OVERLEAF_WEB_READY '.length));browser=await chromium.launch({channel:'msedge',headless:true});
 const tc=await browser.newContext({viewport:{width:1500,height:1000}}),sc=await browser.newContext({viewport:{width:1500,height:1000}});
 const teacher=await tc.newPage(),student=await sc.newPage();for(const p of [teacher,student])p.on('pageerror',e=>errors.push(String(e)));
 async function api(ctx,route,body){const r=await ctx.request.fetch(url+'api/'+route,{method:body?'POST':'GET',data:body});if(!r.ok())throw Error(route+': '+await r.text());return r.json();}
 async function nav(p,name){await p.locator('.writing-sidebar').getByRole('button',{name,exact:true}).click();}
 async function shot(p,name){lastPage=p;await p.screenshot({path:path.join(home,name+'.png')});checks.push(name);}
 await api(tc,'actions/register',{email:'points-teacher@example.com',name:'Tutor Alex',password:'test-password',role:'tutor'});
 await api(sc,'actions/register',{email:'points-student@example.com',name:'Student Jamie',password:'test-password',role:'student'});
 const course=await api(tc,'writing/courses',{title:'Points class',kind:'course'});await api(tc,`writing/courses/${course.id}/members`,{email:'points-student@example.com'});
 await teacher.goto(url);await teacher.getByRole('button',{name:'Home',exact:true}).click();await teacher.getByRole('button',{name:'Open Task Writing',exact:true}).click();await nav(teacher,'Assign exercise');
 await expect(teacher.getByLabel('Total score',{exact:true})).toHaveValue('10');
 await teacher.getByLabel('Exercise title',{exact:true}).fill('Introduction and overview practice');await teacher.getByLabel('Course or group',{exact:true}).selectOption(course.id);
 await teacher.getByLabel('Assigned tasks',{exact:true}).selectOption('1');await teacher.getByLabel('Question Task 1',{exact:true}).fill('Describe the main trends, introduction and overview only.');
 await expect(teacher.getByLabel('Writing scope',{exact:true})).toHaveCount(0);
 await teacher.getByLabel('Total score',{exact:true}).fill('5');
 const categories=teacher.getByRole('region',{name:'Assessment categories Task 1',exact:true});
 await categories.getByRole('button',{name:'Remove whole paper Task 1',exact:true}).click();
 await expect(categories.getByRole('alert')).toBeVisible();
 for(const name of ['Introduction','Overview'])await categories.getByRole('button',{name,exact:true}).click();
 await categories.getByRole('button',{name:'Introduction',exact:true}).click();
 const popup=teacher.getByRole('dialog',{name:'Customize Introduction',exact:true});
 await expect(popup.getByLabel('Criterion weight intro 1',{exact:true})).toHaveValue('2');
 await expect(popup.locator('input[type=checkbox]')).toHaveCount(0);
 await popup.getByLabel('Criterion weight intro 1',{exact:true}).fill('4');await popup.getByLabel('Criterion intro 1',{exact:true}).fill('Introduce the chart with a precise subject.');
 await shot(teacher,'category-customize-desktop');await teacher.setViewportSize({width:390,height:844});await shot(teacher,'category-customize-mobile');
 expect(await teacher.evaluate(()=>document.documentElement.scrollWidth<=innerWidth+1)).toBe(true);
 await teacher.keyboard.press('Escape');await expect(popup).toBeHidden();await teacher.setViewportSize({width:1500,height:1000});
 await shot(teacher,'selected-categories');await teacher.locator('.writing-form').getByRole('button',{name:'Assign exercise',exact:true}).click();
 await expect(teacher.getByText('Exercise assigned to students.',{exact:true})).toBeVisible();
 const assignment=(await api(tc,'writing/overview')).assignments.find(a=>a.title==='Introduction and overview practice');
 expect(assignment.scoring.total).toBe(5);expect(assignment.assessmentCategories['1']).toEqual(['intro','overview']);
 await student.goto(url);await student.getByRole('button',{name:'Home',exact:true}).click();await student.getByRole('button',{name:'Open Task Writing',exact:true}).click();await nav(student,'Assigned exercises');await student.getByRole('button',{name:'Open writing',exact:true}).click();
 await expect(student.locator('.writing-selected-categories')).toContainText('Introduction');
 await student.getByLabel('Answer Task 1',{exact:true}).fill('The chart illustrates commuting patterns.\n\nOverall, bus use increased.');
 await student.getByRole('button',{name:'Check and submit',exact:true}).click();await student.getByRole('button',{name:'Confirm submission',exact:true}).click();
 await nav(teacher,'Student submissions');await teacher.getByRole('button',{name:'Open assessment',exact:true}).click();
 await expect(teacher.getByRole('button',{name:'Band summary',exact:true})).toHaveCount(0);
 await teacher.getByRole('button',{name:'Checklist score',exact:true}).click();
 const floating=teacher.getByRole('dialog').filter({has:teacher.getByRole('button',{name:'Move checklist',exact:true})});
 await expect(floating.locator('input[type=checkbox]')).toHaveCount(0);await expect(floating.locator('input[type=number]')).not.toHaveCount(0);
 await shot(teacher,'whole-paper-category-checklist');let sum=0;
 for(const tab of await floating.locator('.category-checklist-tabs button').all()){
  await tab.click();for(const input of await floating.locator('.point-assessment input[type=number]').all()){
   const max=Number(await input.getAttribute('max'));sum+=max;await input.fill(String(max));
  }
 }
 expect(Math.round(sum*100)/100).toBe(5);await teacher.keyboard.press('Escape');await teacher.getByRole('button',{name:'Finish score',exact:true}).click();const summary=teacher.getByRole('dialog',{name:'Finish score',exact:true});await expect(summary.locator('.point-assessment-total')).toContainText('5 / 5');
 await shot(teacher,'numeric-score-summary');await summary.getByRole('button',{name:'Publish results',exact:true}).click();
 await expect(teacher.getByText('Results published to the student.',{exact:true})).toBeVisible();
 const sub=(await api(sc,'writing/overview')).submissions[0];expect(sub.result.totalScore).toBe(5);expect(sub.result.maxScore).toBe(5);expect(sub.result.writingBand).toBeNull();
 await nav(student,'Results & feedback');await student.getByRole('button',{name:'View submissions',exact:true}).click();
 await expect(student.getByText('Score: 5 / 5',{exact:true})).toBeVisible();await student.getByRole('button',{name:'Finish score',exact:true}).click();await expect(student.getByRole('dialog').locator('.point-assessment input')).toHaveCount(0);await shot(student,'student-published-points');
 expect(errors).toEqual([]);fs.writeFileSync(path.join(home,'report.json'),JSON.stringify({checks,errors},null,2));console.log('Point assessment passed: '+checks.length+' views');
}catch(e){if(lastPage)await lastPage.screenshot({path:path.join(home,'failure.png')}).catch(()=>{});console.error(stderr);throw e;}
finally{await browser?.close();server.kill();}
