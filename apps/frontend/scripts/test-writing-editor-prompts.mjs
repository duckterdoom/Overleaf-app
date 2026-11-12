import {chromium,expect as baseExpect} from '@playwright/test';
import {spawn,spawnSync} from 'node:child_process';
import {createHash} from 'node:crypto';
import fs from 'node:fs';
import path from 'node:path';
const expect=baseExpect.configure({timeout:30000});
const repo=path.resolve('../..'),python=path.resolve(process.argv[2]),home=path.resolve(process.argv[3]);
if(fs.existsSync(home))throw Error('Use a fresh test home');fs.mkdirSync(home,{recursive:true});
const factory=process.env.LATEX_TEST_WEB_FACTORY||path.join(repo,'apps/backend/tests/run_mongo_web.py');
const server=spawn(python,[factory,'--home',home,'--port','0','--no-browser'],{cwd:repo,windowsHide:true});
let output='',stderr='',browser,tutor,student;
server.stdout.on('data',v=>output+=v);server.stderr.on('data',v=>stderr+=v);
async function ok(response){if(!response.ok())throw Error(`${response.status()}: ${await response.text()}`);return response;}
try{
 for(let i=0;i<400&&!output.includes('OVERLEAF_WEB_READY ');i++){if(server.exitCode!==null)throw Error(stderr);await new Promise(r=>setTimeout(r,100));}
 const line=output.split(/\r?\n/).find(v=>v.startsWith('OVERLEAF_WEB_READY '));if(!line)throw Error(stderr);
 const {url}=JSON.parse(line.slice('OVERLEAF_WEB_READY '.length));
 browser=await chromium.launch({channel:'msedge',headless:true});
 const tc=await browser.newContext({viewport:{width:1700,height:1060}}),sc=await browser.newContext({viewport:{width:1700,height:1060}});
 tutor=await tc.newPage();student=await sc.newPage();const errors=[];
 for(const page of[tutor,student])page.on('pageerror',e=>errors.push(String(e)));
 async function signup(context,email,name,role){await ok(await context.request.post(url+'api/actions/register',{data:{email,name,password:'test-password'}}));await ok(await context.request.post(url+'api/auth/onboarding',{data:{name,role}}));}
 await signup(tc,'prompt-tutor@example.com','Tutor Linh','student');
 const project=await(await ok(await tc.request.post(url+'api/projects',{data:{title:'IELTS editor questions'}}))).json();
 const route=url+`api/projects/${project.id}/sharing`,query='?projectId='+project.id;
 await ok(await tc.request.patch(route,{data:{mode:'private',ownerRole:'tutor'}}));
 const invitation=await(await ok(await tc.request.post(route+'/invitations',{data:{emails:['prompt-student@example.com'],permission:'editor',workspaceRole:'student'}}))).json();
 const question=String.raw`\documentclass{article}
\usepackage{tikz}
\begin{document}
\section*{Task 1: Transport chart}
Summarise the main features and make comparisons where relevant.
\begin{tikzpicture}\fill[green!60!black] (0,0) rectangle (1,3);\fill[blue] (2,0) rectangle (3,2);\node at (.5,-.4) {Car};\node at (2.5,-.4) {Bus};\end{tikzpicture}
\newpage
\section*{Task 2: City transport}
Some people support more roads, while others prefer public transport. Discuss both views and give your opinion.
\end{document}`;
 await ok(await tc.request.post(url+`api/projects/${project.id}/file`,{data:{path:'questions.tex'}}));
 await ok(await tc.request.put(url+`api/projects/${project.id}/file`,{data:{path:'questions.tex',content:question}}));
 await signup(sc,'prompt-student@example.com','Student An','tutor');
 await ok(await sc.request.post(url+'api/shares/accept',{data:{token:invitation.members[0].inviteToken}}));
 for(const page of[tutor,student]){await page.goto(url);await page.locator('.project-card-open').click();await expect(page.locator('.cm-content')).toBeVisible();}
 await expect(tutor.locator('.presence-person').filter({hasText:'SA'})).toHaveCount(1);
 await tutor.getByRole('button',{name:'Chấm điểm IELTS Writing',exact:true}).click();
 await expect(tutor.getByRole('button',{name:'Giao bài IELTS',exact:true})).toBeVisible();
 await expect(student.locator('.presence-person[title*="IELTS Writing"]')).toHaveCount(1);
 await student.locator('.cm-content').click();await student.keyboard.press('Control+End');
 await student.keyboard.insertText('\n% collaborative update while Tutor is in IELTS\n');
 await expect.poll(async()=> (await(await sc.request.get(url+`api/projects/${project.id}/file?path=main.tex`)).json()).content).toContain('collaborative update');
 await student.getByRole('button',{name:'Chấm điểm IELTS Writing',exact:true}).click();
 await expect(student.getByRole('button',{name:'Bài được giao',exact:true})).toBeVisible();
 await expect(student.getByRole('button',{name:'Giao bài IELTS',exact:true})).toHaveCount(0);
 await expect(tutor.locator('.presence-person[title*="Student An"][title*="IELTS Writing"]')).toHaveCount(1);
 await expect(student.locator('.presence-person[title*="Tutor Linh"][title*="IELTS Writing"]')).toHaveCount(1);
 await tutor.getByRole('button',{name:'Tiêu chí IELTS',exact:true}).click();
 await tutor.getByLabel('Dạng bài cấu hình').selectOption('bar');
 await expect(tutor.getByLabel('Tiêu chí intro 1')).toBeVisible();
 await tutor.getByLabel('Tiêu chí intro 1').fill('Project specific introduction criterion');
 await tutor.getByRole('button',{name:'Lưu tiêu chí dự án',exact:true}).click();
 await expect(tutor.locator('.writing-project-rubrics')).toContainText('Đã lưu cấu hình theo dự án');
 await tutor.screenshot({path:path.join(home,'project-rubric-settings.png')});
 await tutor.getByRole('button',{name:'Lớp học',exact:true}).click();await tutor.getByLabel('Tên lớp mới',{exact:true}).fill('Editor prompts');await tutor.getByRole('button',{name:'Tạo lớp',exact:true}).click();
 await expect(tutor.locator('.writing-class-code')).toBeVisible();
 await tutor.getByRole('button',{name:'Giao bài IELTS',exact:true}).click();
 await tutor.getByLabel('Tên bài IELTS').fill('Task 1 and 2 from editor');await tutor.getByLabel('Task được giao').selectOption('1,2');
 await tutor.getByLabel('Thời lượng',{exact:true}).fill('60');
 await tutor.getByLabel('Hạn nộp',{exact:true}).fill('2030-01-01T10:00');
 for(const task of[1,2]){
  await tutor.getByLabel(`Tệp đề Task ${task}`,{exact:true}).selectOption('questions.tex');
  await tutor.getByLabel(`Trang đầu đề Task ${task}`,{exact:true}).fill(String(task));
  await tutor.getByLabel(`Trang cuối đề Task ${task}`,{exact:true}).fill(String(task));
  await tutor.getByRole('button',{name:`Dùng tệp làm đề Task ${task}`,exact:true}).click();
  const pane=tutor.locator('.writing-project-prompt').filter({has:tutor.getByLabel(`Tệp đề Task ${task}`,{exact:true})});
  await expect(pane.locator('[data-prompt-file]')).toBeVisible();
  await expect.poll(()=>pane.locator('.pdf-sheet').count(),{timeout:90000}).toBeGreaterThan(0);
 }
 await tutor.locator('.writing-project-prompt').first().getByTitle('Phóng to',{exact:true}).click();
 expect((await(await tc.request.get(url+'api/writing/overview'+query)).json()).assignments).toHaveLength(0);
 await tutor.screenshot({path:path.join(home,'tutor-editor-question-picker.png')});
 await tutor.getByRole('button',{name:'Giao bài',exact:true}).click();
 await expect(tutor.locator('.writing-assignment-list')).toContainText('Task 1 and 2 from editor');
 await tutor.getByRole('button',{name:'Xem đề và học viên',exact:true}).click();
 await expect(tutor.locator('.writing-card [data-prompt-file]')).toHaveCount(2);
 await expect(tutor.locator('.writing-card table')).toContainText('Student An');
 await expect(tutor.locator('.writing-card table')).toContainText('Chưa nộp');
 await student.getByRole('button',{name:'Bài được giao',exact:true}).click();await student.getByTitle('Tải lại dữ liệu IELTS').click();
 await student.getByRole('button',{name:'Mở bài viết',exact:true}).click();
 const overview=await(await ok(await sc.request.get(url+'api/writing/overview'+query))).json(),assignment=overview.assignments[0];
 expect(assignment.durationMinutes).toBe(60);expect(assignment.dueAt).toBeTruthy();expect(assignment.prompts['1']).toBe('');expect(assignment.promptFiles['1'].sourcePath).toBe('questions.tex');
 await expect(student.locator('.writing-question [data-prompt-file]')).toHaveAttribute('data-prompt-file',assignment.promptFiles['1'].id);
 await expect.poll(()=>student.locator('.writing-question .pdf-sheet').count(),{timeout:90000}).toBe(1);
 await expect(student.locator('.writing-timer')).not.toContainText('60:01');
 await expect.poll(()=>student.locator('.writing-question canvas').first().evaluate(c=>c.width)).toBeGreaterThan(100);
 await student.screenshot({path:path.join(home,'student-question-chart.png')});
 const response=await ok(await sc.request.get(url+'api/writing/prompt-files/'+assignment.promptFiles['1'].id+query));
 const pdf=await response.body();expect(createHash('sha256').update(pdf).digest('hex')).toBe(assignment.promptFiles['1'].digest);
 fs.writeFileSync(path.join(home,'student-question-task1.pdf'),pdf);
 await student.getByLabel('Câu trả lời Task 1').fill('The chart compares car and bus journeys.');
 await student.getByRole('tab',{name:'Task 2',exact:true}).click();
 await expect(student.locator('.writing-question [data-prompt-file]')).toHaveAttribute('data-prompt-file',assignment.promptFiles['2'].id);
 await student.getByLabel('Câu trả lời Task 2').fill('Both viewpoints deserve consideration.');
 await expect.poll(()=>student.locator('.writing-question .pdf-sheet').count(),{timeout:90000}).toBe(1);
 await expect.poll(()=>student.locator('.writing-question canvas').first().evaluate(c=>c.width)).toBeGreaterThan(100);
 const before=await(await ok(await sc.request.get(url+`api/writing/assignments/${assignment.id}/draft`+query))).json();
 expect(new Date(before.deadline)-new Date(before.startedAt)).toBe(60*60000);
 await student.screenshot({path:path.join(home,'student-editor-question.png')});
 await expect(student.locator('.writing-practice .writing-view-tabs')).toHaveCount(0);
 await expect(student.locator('.writing-question .preview-toolbar')).not.toBeVisible();
 const panes=await student.locator('.writing-question').boundingBox();expect(panes.width).toBeGreaterThan(450);
 await student.screenshot({path:path.join(home,'focused-question-and-answer.png')});
 // The paper handed out remains the same if the source is edited after publishing.
 await ok(await tc.request.put(url+`api/projects/${project.id}/file`,{data:{path:'questions.tex',content:question+'\n% later revision'}}));
 expect(await(await ok(await sc.request.get(url+'api/writing/prompt-files/'+assignment.promptFiles['1'].id+query))).body()).toEqual(pdf);
 await tutor.locator('.app-header').getByRole('button',{name:'Chia sẻ',exact:true}).click();
 const sharing=tutor.getByRole('dialog',{name:'IELTS editor questions'});
 await sharing.getByLabel('Vai trò chủ sở hữu trong dự án',{exact:true}).selectOption('student');
 await expect(tutor.getByRole('button',{name:'Giao bài IELTS',exact:true})).toHaveCount(0);
 await sharing.getByLabel('Vai trò chủ sở hữu trong dự án',{exact:true}).selectOption('tutor');
 await expect(tutor.getByRole('button',{name:'Giao bài IELTS',exact:true})).toBeVisible();
 await sharing.getByRole('button',{name:'Đóng chia sẻ',exact:true}).click();
 expect((await(await tc.request.get(url+'api/session')).json()).session.role).toBe('student');
 expect((await(await sc.request.get(url+'api/session')).json()).session.role).toBe('tutor');
 await student.getByRole('tab',{name:'Task 1',exact:true}).click();await expect(student.getByLabel('Câu trả lời Task 1')).toHaveValue('The chart compares car and bus journeys.');
 const after=await(await ok(await sc.request.get(url+`api/writing/assignments/${assignment.id}/draft`+query))).json();expect(after.deadline).toBe(before.deadline);
 await student.getByRole('button',{name:'Kiểm tra và nộp',exact:true}).click();await student.getByRole('button',{name:'Xác nhận nộp bài',exact:true}).click();
 await expect(student.getByRole('button',{name:'Tổng kết band',exact:true})).toBeVisible();
 const submitted=(await(await ok(await sc.request.get(url+'api/writing/overview'+query))).json()).submissions[0];
 const countBefore=(await(await tc.request.get(url+'api/projects')).json()).length;
 const artifactResult=await(await ok(await tc.request.post(url+'api/writing/latex'+query,{data:{submissionId:submitted.id,createProject:true}}))).json();
 expect(artifactResult.artifact).toBe(true);expect(Buffer.from(artifactResult.pdf,'base64').subarray(0,4).toString()).toBe('%PDF');
 expect(artifactResult.project).toBeUndefined();
 const again=await(await ok(await tc.request.post(url+'api/writing/latex'+query,{data:{submissionId:submitted.id,createProject:true}}))).json();expect(again.pdf).toBe(artifactResult.pdf);
 expect((await(await tc.request.get(url+'api/projects')).json()).length).toBe(countBefore);
 await tutor.getByRole('button',{name:'Bài nộp học viên',exact:true}).click();
 await tutor.locator('.writing-table tbody button').first().click();
 await expect(tutor.locator('.writing-review')).toBeVisible();
 await expect(tutor.locator('.writing-view-tabs')).toHaveCount(0);
 await expect(tutor.locator('.writing-paper-title details')).not.toHaveAttribute('open','');
 await tutor.screenshot({path:path.join(home,'focused-tutor-grading.png')});
 const grades=Object.fromEntries([1,2].map(n=>[String(n),{profile:n===1?'bar':'discussion',scores:Object.fromEntries([n===1?'TA':'TR','CC','LR','GRA'].map(k=>[k,6])),notes:{},overall:'Tutor overall comments',sections:[]}]));
 const rev=await(await ok(await tc.request.put(url+`api/writing/submissions/${submitted.id}/review`+query,{data:{revision:0,tasks:grades,summary:'Published summary',recommendations:'Develop examples'}}))).json();
 const publication=await(await ok(await tc.request.post(url+`api/writing/submissions/${submitted.id}/publish`+query,{data:{revision:rev.revision}}))).json();
 expect(publication.resultFiles).toHaveLength(2);
 for(const file of publication.resultFiles){
  expect(file).toContain('result/Student-An-task');
  const tex=(await(await ok(await sc.request.get(url+`api/projects/${project.id}/file?path=${encodeURIComponent(file)}`))).json()).content;
  expect(tex).toContain('Tutor overall comments');expect(tex).toContain('Published summary');expect(tex).toContain('result/assets/');
  const compiled=await(await ok(await tc.request.post(url+`api/projects/${project.id}/compile`,{data:{engine:'pdflatex',main:file}}))).text();
  expect(compiled).toContain('"type": "done"');
 }
 await student.getByRole('button',{name:'Bài được giao',exact:true}).click();
 await student.getByRole('button',{name:'Bài luyện cá nhân',exact:true}).click();
 await student.getByLabel('Tên bài IELTS').fill('Personal practice from editor');
 await expect(student.getByLabel('Đề Task 2',{exact:true})).toHaveCount(0);
 await student.getByLabel('Tệp đề Task 2',{exact:true}).selectOption('questions.tex');
 await student.getByLabel('Trang đầu đề Task 2',{exact:true}).fill('2');await student.getByLabel('Trang cuối đề Task 2',{exact:true}).fill('2');
 await student.getByRole('button',{name:'Dùng tệp làm đề Task 2',exact:true}).click();
 await expect(student.locator('.writing-project-prompt [data-prompt-file]')).toBeVisible();
 await student.getByRole('button',{name:'Bắt đầu viết',exact:true}).click();
 await expect(student.getByLabel('Câu trả lời Task 2')).toBeVisible();
 await expect(student.locator('.writing-practice .writing-view-tabs')).toHaveCount(0);
 await expect.poll(()=>student.locator('.writing-question canvas').count()).toBe(1);
 await student.screenshot({path:path.join(home,'personal-practice-from-project.png')});
 await student.getByRole('button',{name:'Tệp và outline',exact:true}).click();
 await expect(student.locator('.cm-content')).toBeVisible();await expect(tutor.locator('.presence-person[title*="Student An"]')).toHaveCount(1);
 await tutor.getByRole('button',{name:'Tệp và outline',exact:true}).click();
 await expect(tutor.locator('.cm-content')).toContainText('collaborative update while Tutor is in IELTS');
 expect(errors).toEqual([]);fs.writeFileSync(path.join(home,'report.json'),JSON.stringify({passed:true,oppositeAccountRoles:true,ownerProjectRole:true,presenceEditorAndWriting:true,task1Task2FromEditor:true,realTexCompile:true,pdfPagesAndChart:true,immutablePrompt:true,unchangedDurationAndDeadline:true,studentAnswersPersist:true,submission:true,returnToEditor:true},null,2));
 console.log('WRITING_EDITOR_PROMPTS_OK roles; shared presence; editor LaTeX questions; PDF pages/chart; timers; submit; return editor');
}catch(error){for(const [name,page]of[['tutor',tutor],['student',student]])if(page){console.error(name+': '+(await page.locator('body').innerText()).slice(-3000));await page.screenshot({path:path.join(home,'error-'+name+'.png')}).catch(()=>{});}console.error(stderr.slice(-1500));throw error;}
finally{await browser?.close();if(process.platform==='win32')spawnSync('taskkill',['/PID',String(server.pid),'/T','/F'],{windowsHide:true,stdio:'ignore'});else server.kill('SIGTERM');}
