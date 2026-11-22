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


 await api(tc,'actions/register',{email:'finish-tutor@example.com',name:'Tutor',password:'test-password',role:'tutor'});
 await api(sc,'actions/register',{email:'finish-student@example.com',name:'Learner',password:'test-password',role:'student'});
 const course=await api(tc,'writing/classes',{title:'Marking course'});await api(sc,'writing/classes/join',{code:course.joinCode});
 const assignment=await api(tc,'writing/assignments',{title:'Final review test',classId:course.id,category:'research',tasks:[1],profiles:{'1':'research'},prompts:{'1':'Write an introduction.'},published:true,scoring:{mode:'points',total:10},rubricCatalog:{research:{category:'research',family:'research',task:1,label:'Research',focus:[],checks:{intro:[{id:'opening',text:'Clear opening',weight:2},{id:'purpose',text:'Clear purpose',weight:2}]}}}});
 const route=`writing/assignments/${assignment.id}`,draft=await api(sc,route+'/draft');
 const saved=await sc.request.put(url+'api/'+route+'/draft',{data:{revision:draft.revision,answers:{'1':'A clear introduction with purpose.'}}});expect(saved.ok()).toBe(true);
 const submission=await api(sc,route+'/submit',{revision:(await saved.json()).revision,requestId:crypto.randomUUID()});
 const seed=await tc.request.put(url+`api/writing/submissions/${submission.id}/review`,{data:{revision:0,tasks:{'1':{profile:'research',scores:{},criterionScores:{},annotations:[{id:'feedback1',start:0,end:7,quote:'A clear',kind:'replace',category:'intro',replacement:'A focused',comment:'Make the subject more precise.'}]}}}});expect(seed.ok()).toBe(true);
 await teacher.goto(url);await nav(teacher,'Student submissions');await teacher.getByRole('button',{name:'Open assessment',exact:true}).click();
 await expect(teacher.getByRole('button',{name:'Finish score',exact:true})).toHaveCount(0);
 await expect(teacher.getByRole('button',{name:'Save assessment draft',exact:true})).toHaveCount(0);
 await teacher.getByRole('button',{name:'Checklist score',exact:true}).click();
 await expect(teacher.getByRole('button',{name:'Finish score',exact:true})).toHaveCount(0);
 await teacher.getByLabel('Points intro:opening',{exact:true}).fill('4');await teacher.getByLabel('Points intro:purpose',{exact:true}).fill('3');
 await teacher.getByLabel('Awarded total score',{exact:true}).fill('8.25');
 await expect.poll(async()=>{const d=await api(tc,`writing/submissions/${submission.id}`);return d.review?.awardedTotal;}).toBe(8.25);
 const serverDraft=await api(tc,`writing/submissions/${submission.id}`);expect(serverDraft.review.tasks['1'].criterionScores['intro:opening']).toBe(4);
 await teacher.getByRole('button',{name:'Finish score',exact:true}).click();
 const review=teacher.getByRole('region',{name:'Finish score',exact:true});await expect(review).toBeVisible();await expect(review).toContainText('Make the subject more precise.');await expect(review).toContainText('A focused');await expect(teacher.locator('.writing-band-backdrop')).toHaveCount(0);await expect(teacher.getByRole('button',{name:'Checklist score',exact:true})).toBeHidden();
 await expect(teacher.getByLabel('Final awarded total',{exact:true})).toHaveValue('8.25');await teacher.getByLabel('Overall feedback',{exact:true}).fill('Good effort, strengthen the evidence.');
 await shot(teacher,'finish-score-review');await teacher.getByRole('button',{name:'Back to marking',exact:true}).click();await teacher.getByRole('button',{name:'Checklist score',exact:true}).click();await teacher.getByRole('button',{name:'Finish score',exact:true}).click();
 await expect(teacher.getByLabel('Overall feedback',{exact:true})).toHaveValue('Good effort, strengthen the evidence.');await teacher.getByRole('button',{name:'Publish results',exact:true}).click();
 await expect.poll(async()=>{const d=await api(sc,`writing/submissions/${submission.id}`);return d.published?.totalScore;}).toBe(8.25);
 const result=await api(sc,`writing/submissions/${submission.id}`);expect(result.published.maxScore).toBe(10);expect(result.published.calculatedScore).toBe(7);expect(result.published.summary).toContain('Good effort');
 const beforeExport=(await api(tc,'projects')).length;
 await teacher.getByRole('button',{name:'Export LaTeX document',exact:true}).click();
 await expect(teacher.locator('.file-tree').getByRole('button',{name:'question.tex',exact:true})).toBeVisible();
 for(const file of ['answer.tex','feedback.tex','main.tex'])await expect(teacher.locator('.file-tree').getByRole('button',{name:file,exact:true})).toBeVisible();
 const projects=await api(tc,'projects');expect(projects.length).toBe(beforeExport+1);const generated=projects.find(p=>p.title.includes('Final review test'));
 const feedback=await api(tc,`projects/${generated.id}/file?path=feedback.tex`);expect(feedback.content).toContain('Make the subject more precise.');expect(feedback.content).toContain('8.25 / 10');
 await shot(teacher,'export-latex-four-files');
 expect(errors).toEqual([]);console.log('FINISH_SCORE_OK autosave, complete-only finish, standalone review, manual awarded total, fixed scale and publish');
}catch(e){if(lastPage)await lastPage.screenshot({path:path.join(home,'failure.png')}).catch(()=>{});console.error(stderr);throw e;}
finally{await browser?.close();server.kill();}
