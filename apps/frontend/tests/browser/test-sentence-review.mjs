import {chromium,expect as baseExpect} from '@playwright/test';
import {spawn} from 'node:child_process';
import fs from 'node:fs';
import path from 'node:path';
const expect=baseExpect.configure({timeout:20000}),repo=path.resolve('../..'),home=path.resolve(process.argv[3]);
if(fs.existsSync(home))throw Error('Use a fresh isolated test home');fs.mkdirSync(home,{recursive:true});
const server=spawn(path.resolve(process.argv[2]),[path.join(repo,'apps/backend/tests/support/run_mongo_web.py'),'--home',home,'--port','0','--no-browser'],{cwd:repo,windowsHide:true});
let output='',stderr='',browser,lastPage;const errors=[],checks=[];
server.stdout.on('data',v=>output+=v);server.stderr.on('data',v=>stderr+=v);
try{
 for(let i=0;i<400&&!output.includes('OVERLEAF_WEB_READY ');i++){if(server.exitCode!==null)throw Error(stderr);await new Promise(r=>setTimeout(r,100));}
 const ready=output.split(/\r?\n/).find(l=>l.startsWith('OVERLEAF_WEB_READY '));if(!ready)throw Error(stderr);
 const {url}=JSON.parse(ready.slice('OVERLEAF_WEB_READY '.length));browser=await chromium.launch({channel:'msedge',headless:true});
 const tc=await browser.newContext({viewport:{width:1560,height:1100}}),sc=await browser.newContext({viewport:{width:1560,height:1100}});
 const teacher=await tc.newPage(),student=await sc.newPage();for(const p of [teacher,student])p.on('pageerror',e=>errors.push(String(e)));
 async function api(ctx,route,body){const r=await ctx.request.fetch(url+'api/'+route,{method:body?'POST':'GET',data:body});if(!r.ok())throw Error(route+': '+await r.text());return r.json();}
 async function nav(p,name){await p.locator('.writing-sidebar').getByRole('button',{name,exact:true}).click();}
 async function shot(p,name){lastPage=p;await p.screenshot({path:path.join(home,name+'.png')});checks.push(name);}
 await api(tc,'actions/register',{email:'sentence-teacher@example.com',name:'Tutor Alex',password:'test-password',role:'tutor'});
 await api(sc,'actions/register',{email:'sentence-student@example.com',name:'Student Jamie',password:'test-password',role:'student'});
 const course=await api(tc,'writing/courses',{title:'Sentence review',kind:'course'});await api(tc,`writing/courses/${course.id}/members`,{email:'sentence-student@example.com'});
 await teacher.goto(url);
 const chart=await teacher.evaluate(()=>{const c=document.createElement('canvas');c.width=600;c.height=500;const x=c.getContext('2d');x.fillStyle='white';x.fillRect(0,0,600,500);x.fillStyle='#17233b';x.font='24px sans-serif';x.fillText('Number of train passengers',40,45);x.strokeStyle='#3454d1';x.lineWidth=4;x.beginPath();x.moveTo(60,400);x.lineTo(160,350);x.lineTo(260,370);x.lineTo(360,230);x.lineTo(460,180);x.stroke();return c.toDataURL('image/png').split(',')[1];});
 const uploaded=await tc.request.post(url+'api/writing/prompt-images?filename=chart.png',{data:Buffer.from(chart,'base64'),headers:{'Content-Type':'image/png'}});expect(uploaded.ok()).toBe(true);const image=await uploaded.json();
 const assignment=await api(tc,'writing/assignments',{title:'Whole text and sentence review',description:'Metadata only: introductory class exercise.',classId:course.id,tasks:[1],profiles:{'1':'bar'},prompts:{'1':'The chart shows train passengers. Summarise the main features.'},promptFiles:{'1':image.id},scoring:{mode:'points',total:10},assessmentCategories:{'1':['intro','body']},published:true});
 await student.goto(url);await student.getByRole('button',{name:'Home',exact:true}).click();await student.getByRole('button',{name:'Open Task Writing',exact:true}).click();await nav(student,'Assigned exercises');
 await expect(student.locator('.writing-exercise-description')).toContainText('Metadata only');await student.getByRole('button',{name:'Open writing',exact:true}).click();
 await expect(student.locator('.writing-question')).not.toContainText('Metadata only');await expect(student.getByRole('button',{name:'By paragraph / paper section',exact:true})).toHaveCount(0);
 const answer=student.getByLabel('Answer Task 1',{exact:true});await expect(answer).toHaveValue('');await expect(answer).toHaveCSS('background-color','rgb(255, 255, 255)');
 const imageWidth=await student.locator('.writing-question .question-visual img').evaluate(e=>e.getBoundingClientRect().width);const paneWidth=await student.locator('.writing-question').evaluate(e=>e.clientWidth);expect(imageWidth).toBeGreaterThan(paneWidth*.8);
 await expect(student.getByRole('button',{name:'Zoom question image in',exact:true})).toHaveCount(0);
 await shot(student,'fixed-question-and-answer');
 const text='The chart shows a change. The amount are rising.\n\nOverall, the number of passengers increased during the period.'+'\n\nPassenger numbers increased steadily across the years.'.repeat(24);
 await answer.fill(text);await student.getByRole('button',{name:'Check and submit',exact:true}).click();await expect(student.locator('.writing-student-description')).toHaveCount(0);await expect(student.locator('.submission-check-question')).toContainText(assignment.prompts['1']);await expect(student.locator('.submission-check-question img')).toBeVisible();await shot(student,'confirmation-question-and-image');await student.getByRole('button',{name:'Confirm submission',exact:true}).click();
 await teacher.getByRole('button',{name:'Home',exact:true}).click();await teacher.getByRole('button',{name:'Open Task Writing',exact:true}).click();await nav(teacher,'Student submissions');await teacher.getByRole('button',{name:'Open assessment',exact:true}).click();
 await expect(teacher.getByRole('button',{name:'Open checklist for paragraph 1',exact:true})).toHaveCount(0);
 await expect(teacher.getByRole('button',{name:'Show question',exact:true})).toBeVisible();await expect(teacher.locator('.writing-paper-title img')).toBeHidden();
 const toggle=teacher.locator('.review-question-heading button');const compactToggle=await toggle.boundingBox(),compactPaper=await teacher.locator('.submission-answer-paper').boundingBox();
 await toggle.click();await expect(teacher.locator('.writing-paper-title img')).toBeVisible();
 await expect(teacher.locator('.review-question-heading h3')).toHaveCount(0);
 const promptGeometry=await teacher.locator('#review-question-content').evaluate(e=>{const image=e.querySelector('img');return {height:e.clientHeight,scroll:e.scrollHeight,bottom:e.getBoundingClientRect().bottom,imageBottom:image.getBoundingClientRect().bottom};});
 expect(promptGeometry.scroll).toBeLessThanOrEqual(promptGeometry.height+1);expect(promptGeometry.imageBottom).toBeLessThanOrEqual(promptGeometry.bottom+1);
 await expect(teacher.locator('#review-question-content')).toHaveCSS('overflow','visible');

 const expandedToggle=await toggle.boundingBox(),expandedPaper=await teacher.locator('.submission-answer-paper').boundingBox();
 for(const key of ['x','y','width','height'])expect(Math.abs(compactToggle[key]-expandedToggle[key])).toBeLessThan(2);
 expect(Math.abs(compactPaper.width-expandedPaper.width)).toBeLessThan(2);expect(Math.abs(compactPaper.x-expandedPaper.x)).toBeLessThan(2);await shot(teacher,'question-expanded-stable-layout');
 await toggle.click();await expect(teacher.locator('#review-question-content')).toBeHidden();await expect(teacher.locator('.writing-paper-title')).toBeHidden();await expect(teacher.locator('.review-question-preview')).toHaveCount(0);

 await teacher.getByRole('button',{name:'Finish score',exact:true}).click();await expect(teacher.getByRole('dialog',{name:'Finish score',exact:true}).getByRole('button',{name:'Publish results',exact:true})).toBeDisabled();await teacher.getByRole('button',{name:'Close assessment summary',exact:true}).click();await teacher.getByRole('button',{name:'Checklist score',exact:true}).click();
 const checklist=teacher.getByRole('dialog',{name:/Checklist score/});await expect(checklist.locator('.category-checklist-tabs button')).toHaveCount(2);for(const input of await checklist.locator('.point-assessment input').all()){const box=await input.boundingBox();expect(box.width).toBeGreaterThanOrEqual(80);}await shot(teacher,'readable-checklist-score');
 for(const tab of await checklist.locator('.category-checklist-tabs button').all()){
  await tab.click();for(const input of await checklist.locator('.point-assessment input').all())await input.fill(await input.getAttribute('max'));
 }
 await checklist.getByRole('button',{name:'Collapse checklist',exact:true}).click();await expect(checklist.locator('.floating-checklist-content')).toBeHidden();await shot(teacher,'whole-paper-collapsed-checklist');await checklist.getByRole('button',{name:'Close checklist',exact:true}).click();
 const coords=await teacher.locator('.submission-answer-paper').evaluate((root)=>{
  const quote='The amount are rising.',start=root.textContent.indexOf(quote);const walker=document.createTreeWalker(root,NodeFilter.SHOW_TEXT);let node,total=0;
  while(node=walker.nextNode()){if(total+node.textContent.length>start){const r=document.createRange();r.setStart(node,start-total);r.setEnd(node,start-total+1);const a=r.getBoundingClientRect();r.setStart(node,start-total+quote.length-1);r.setEnd(node,start-total+quote.length);const b=r.getBoundingClientRect();return {x1:a.x+.1,y1:a.y+a.height/2,x2:b.right-.1,y2:b.y+b.height/2};}total+=node.textContent.length;}
  throw Error('No quote to select');
 });
 await teacher.mouse.move(coords.x1,coords.y1);await teacher.mouse.down();await teacher.mouse.move(coords.x2,coords.y2,{steps:12});await teacher.mouse.up();
 await expect(teacher.getByLabel('Sentence comment',{exact:true})).toBeVisible();const paperBox=await teacher.locator('.submission-answer-paper').boundingBox(),commentBox=await teacher.locator('.sentence-comment-panel').boundingBox();expect(commentBox.x).toBeGreaterThan(paperBox.x+paperBox.width-2);await expect(teacher.locator('.sentence-comment-panel')).toHaveCSS('position','sticky');
 await teacher.locator('.writing-review-scroll').evaluate(e=>e.scrollTop=500);const pinned=await teacher.locator('.sentence-comment-panel').boundingBox();expect(pinned.y).toBeGreaterThanOrEqual(0);expect(pinned.y).toBeLessThan(450);await expect(teacher.locator('.writing-paper-title')).toBeHidden();await expect(teacher.getByRole('button',{name:'Show question',exact:true})).toBeInViewport();await shot(teacher,'side-by-side-feedback');await teacher.setViewportSize({width:900,height:900});await expect(teacher.locator('.sentence-comment-panel')).toHaveCSS('position','fixed');await expect(teacher.getByLabel('Sentence comment',{exact:true})).toBeInViewport();await shot(teacher,'compact-feedback-tablet');await teacher.setViewportSize({width:1560,height:1100});await teacher.getByLabel('Correction style',{exact:true}).selectOption('replace');await teacher.getByLabel('Comment assessment category',{exact:true}).selectOption('body');
 await teacher.getByLabel('Suggested correction',{exact:true}).fill('The amount is rising.');await teacher.getByLabel('Sentence comment',{exact:true}).fill('Use subject–verb agreement.');await expect(teacher.getByRole('button',{name:'Finish score',exact:true})).toBeDisabled();teacher.once('dialog',d=>d.dismiss());await nav(teacher,'Overview');await expect(teacher.getByLabel('Sentence comment',{exact:true})).toHaveValue('Use subject–verb agreement.');teacher.once('dialog',d=>d.dismiss());await teacher.getByRole('button',{name:'Home',exact:true}).click();await expect(teacher.getByLabel('Sentence comment',{exact:true})).toBeVisible();teacher.once('dialog',d=>d.dismiss());await teacher.locator('.workspace-navigation').getByRole('button',{name:/^Shared with you/}).click();await expect(teacher.getByLabel('Sentence comment',{exact:true})).toBeVisible();await teacher.getByRole('button',{name:'Add comment',exact:true}).click();
 await expect(teacher.locator('.sentence-mark.strike')).toHaveText('The amount are rising.');
 await teacher.getByRole('button',{name:'Save assessment draft',exact:true}).click();
 const sub=(await api(tc,'writing/overview')).submissions[0];let saved;
 await expect.poll(async()=>{saved=await api(tc,'writing/submissions/'+sub.id);return saved.review?.tasks?.['1']?.annotations?.length;}).toBe(1);
 expect(saved.review.tasks['1'].annotations[0].authorName).toBe('Tutor Alex');await expect(teacher.locator('.sentence-comment-card strong')).toHaveText('Tutor Alex');await shot(teacher,'tutor-sentence-correction');expect((await api(sc,'writing/submissions/'+sub.id)).review).toBeNull();
 await teacher.getByRole('button',{name:'Close sentence feedback',exact:true}).click();await teacher.locator('.sentence-mark').first().click();await expect(teacher.locator('.sentence-comment-panel')).toContainText('Use subject–verb agreement.');
 await teacher.getByRole('button',{name:'Finish score',exact:true}).click();const finish=teacher.getByRole('dialog',{name:'Finish score',exact:true});await expect(finish).toContainText('The amount is rising.');await expect(finish.locator('.point-assessment input')).toHaveCount(0);await shot(teacher,'finish-score-review');await finish.getByRole('button',{name:'Publish results',exact:true}).click();await expect(teacher.getByText('Results published to the student.',{exact:true})).toBeVisible();
 await nav(student,'Results & feedback');await student.getByRole('button',{name:'View submissions',exact:true}).click();await student.locator('.sentence-mark').first().click();await expect(student.locator('.sentence-comment-panel')).toContainText('The amount is rising.');await expect(student.getByRole('button',{name:'Edit comment',exact:true})).toHaveCount(0);
 expect((await api(sc,'writing/submissions/'+sub.id)).submission.answers['1']).toBe(text);await shot(student,'student-clicks-correction');
 await student.setViewportSize({width:390,height:844});await expect(student.locator('.submission-answer-paper')).toBeVisible();await expect(student.locator('.writing-paper-title img')).toBeVisible();await shot(student,'student-sentence-feedback-mobile');expect(await student.evaluate(()=>document.documentElement.scrollWidth<=innerWidth+1)).toBe(true);
 expect(errors).toEqual([]);fs.writeFileSync(path.join(home,'report.json'),JSON.stringify({checks,errors},null,2));console.log('Sentence review passed: '+checks.length+' views');
}catch(e){if(lastPage)await lastPage.screenshot({path:path.join(home,'failure.png')}).catch(()=>{});console.error(stderr);throw e;}
finally{await browser?.close();server.kill();}
