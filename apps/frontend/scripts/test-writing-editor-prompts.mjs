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
 await expect(tutor.locator('.writing-sidebar').getByRole('button',{name:'Giao bài',exact:true})).toBeVisible();
 // A slow overview must not block navigation between cached screens.
 let heldOverviewRequests=0;let releaseOverview;const heldOverview=new Promise(resolve=>{releaseOverview=resolve;});
 await tutor.route('**/api/writing/overview?**',async route=>{heldOverviewRequests++;await heldOverview;await route.continue();});
 try{
  const started=Date.now();
  await tutor.locator('.writing-sidebar').getByRole('button',{name:'Đề đã tạo',exact:true}).click();
  await expect(tutor.locator('.writing-page-heading h1')).toHaveText('Bài được giao',{timeout:1500});
  const assignmentsMs=Date.now()-started;
  await expect.poll(()=>heldOverviewRequests).toBeGreaterThan(0);
  await tutor.locator('.writing-sidebar').getByRole('button',{name:'Tiêu chí chấm',exact:true}).click();
  await expect(tutor.locator('.rubric-manager')).toBeVisible({timeout:1500});
  fs.writeFileSync(path.join(home,'slow-overview-navigation.json'),JSON.stringify({passed:true,overviewHeldWhileNavigating:true,assignmentsMs},null,2));
 }finally{releaseOverview();await tutor.unrouteAll({behavior:'wait'});}
 await expect(student.locator('.presence-person[title*="IELTS Writing"]')).toHaveCount(1);
 await student.locator('.cm-content').click();await student.keyboard.press('Control+End');
 await student.keyboard.insertText('\n% collaborative update while Tutor is in IELTS\n');
 await expect.poll(async()=> (await(await sc.request.get(url+`api/projects/${project.id}/file?path=main.tex`)).json()).content).toContain('collaborative update');
 await student.getByRole('button',{name:'Chấm điểm IELTS Writing',exact:true}).click();
 await expect(student.locator('.writing-sidebar').getByRole('button',{name:'Bài được giao',exact:true})).toBeVisible();
 await expect(student.getByRole('button',{name:'Giao bài',exact:true})).toHaveCount(0);
 await expect(tutor.locator('.presence-person[title*="Student An"][title*="IELTS Writing"]')).toHaveCount(1);
 await expect(student.locator('.presence-person[title*="Tutor Linh"][title*="IELTS Writing"]')).toHaveCount(1);
 await tutor.getByRole('button',{name:'Tiêu chí chấm',exact:true}).click();
 await tutor.getByLabel('Dạng bài cấu hình').selectOption('bar');
 await expect(tutor.getByLabel('Tiêu chí intro 1',{exact:true})).toBeVisible();
 await tutor.getByLabel('Tiêu chí intro 1',{exact:true}).fill('Project specific introduction criterion');
 await tutor.getByRole('button',{name:'Lưu tiêu chí dự án',exact:true}).click();
 await expect(tutor.locator('.rubric-manager')).toContainText('Đã lưu cấu hình theo dự án');
 await tutor.screenshot({path:path.join(home,'project-rubric-settings.png')});
 await tutor.locator('.writing-sidebar').getByRole('button',{name:'Giao bài',exact:true}).click();
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
 await tutor.locator('.writing-form').getByRole('button',{name:'Giao bài',exact:true}).click();
 await expect(tutor.locator('.writing-assignment-list')).toContainText('Task 1 and 2 from editor');
 await tutor.getByRole('button',{name:'Xem đề và học viên',exact:true}).click();
 await expect(tutor.locator('.writing-card [data-prompt-file]')).toHaveCount(2);
 await expect(tutor.locator('.writing-card table')).toContainText('Student An');
 await expect(tutor.locator('.writing-card table')).toContainText('Chưa nộp');
 await student.locator('.writing-sidebar').getByRole('button',{name:'Bài được giao',exact:true}).click();await student.getByTitle('Tải lại dữ liệu IELTS').click();
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
 await expect(tutor.locator('.writing-sidebar').getByRole('button',{name:'Giao bài',exact:true})).toHaveCount(0);
 await sharing.getByLabel('Vai trò chủ sở hữu trong dự án',{exact:true}).selectOption('tutor');
 await expect(tutor.locator('.writing-sidebar').getByRole('button',{name:'Giao bài',exact:true})).toBeVisible();
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
 await tutor.getByRole('button',{name:'Tổng kết band',exact:true}).click();
 await tutor.getByRole('button',{name:'Tự tổng kết từ checklist',exact:true}).click();
 await expect(tutor.locator('.writing-band-panel [role=alert]')).toContainText('Đánh giá hết');await tutor.keyboard.press('Escape');
 for(const n of [1,2]){
  await tutor.locator('.writing-document-toolbar').getByRole('tab',{name:`Task ${n}`,exact:true}).click();
  for(const index of [0,1]){
   await tutor.locator('.writing-card-toggle').nth(index).click();
   const states=tutor.locator('.writing-check select');
   for(let i=0;i<await states.count();i++)await states.nth(i).selectOption('met');
  }
  await tutor.getByRole('button',{name:'Tổng kết band',exact:true}).click();
  await tutor.getByRole('button',{name:'Tự tổng kết từ checklist',exact:true}).click();
  for(const code of [n===1?'TA':'TR','CC','LR','GRA'])await expect(tutor.getByLabel(`Band ${code}`,{exact:true})).toHaveValue('9');
  await tutor.keyboard.press('Escape');
 }
 await tutor.getByRole('button',{name:'Lưu nháp chấm',exact:true}).click();
 await expect(tutor.getByRole('button',{name:'Lưu nháp chấm',exact:true})).toBeDisabled();
 const reviewVersion=(await(await ok(await tc.request.get(url+`api/writing/submissions/${submitted.id}`+query))).json()).review.revision;
 const grades=Object.fromEntries([1,2].map(n=>[String(n),{profile:n===1?'bar':'discussion',scores:Object.fromEntries([n===1?'TA':'TR','CC','LR','GRA'].map(k=>[k,6])),notes:{},overall:'Tutor overall comments',sections:[]}]));
 const rev=await(await ok(await tc.request.put(url+`api/writing/submissions/${submitted.id}/review`+query,{data:{revision:reviewVersion,tasks:grades,summary:'Published summary',recommendations:'Develop examples'}}))).json();
 const publication=await(await ok(await tc.request.post(url+`api/writing/submissions/${submitted.id}/publish`+query,{data:{revision:rev.revision}}))).json();
 expect(publication.resultFiles).toHaveLength(2);
 for(const file of publication.resultFiles){
  expect(file).toContain('result/Student-An-task');
  const tex=(await(await ok(await sc.request.get(url+`api/projects/${project.id}/file?path=${encodeURIComponent(file)}`))).json()).content;
  expect(tex).toContain('Tutor overall comments');expect(tex).toContain('Published summary');expect(tex).toContain('result/assets/');
  const compiled=await(await ok(await tc.request.post(url+`api/projects/${project.id}/compile`,{data:{engine:'pdflatex',main:file}}))).text();
  expect(compiled).toContain('"type": "done"');
 }
 await student.locator('.writing-sidebar').getByRole('button',{name:'Bài được giao',exact:true}).click();
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
 // Extend the same module to Report and Research without classrooms.
 await tutor.getByRole('button',{name:'Chấm điểm IELTS Writing',exact:true}).click();
 await student.getByRole('button',{name:'Chấm điểm IELTS Writing',exact:true}).click();
 await expect(tutor.locator('.writing-sidebar').getByRole('button',{name:'Lớp học',exact:true})).toHaveCount(0);
 await tutor.getByRole('button',{name:'Tiêu chí chấm',exact:true}).click();
 await tutor.locator('.rubric-category-tabs').getByRole('button',{name:'Report',exact:true}).click();
 await tutor.getByRole('button',{name:'Thêm bộ tiêu chí',exact:true}).click();
 await tutor.getByLabel('Tên bộ tiêu chí',{exact:true}).fill('Temporary rubric');
 tutor.once('dialog',d=>d.accept());await tutor.getByRole('button',{name:'Xóa bộ tiêu chí',exact:true}).click();
 await expect(tutor.getByLabel('Dạng bài cấu hình')).not.toContainText('Temporary rubric');
 await tutor.getByRole('button',{name:'Thêm bộ tiêu chí',exact:true}).click();
 await tutor.getByLabel('Tên bộ tiêu chí',{exact:true}).fill('Custom report criteria');
 const customProfile=await tutor.getByLabel('Dạng bài cấu hình').inputValue();
 await tutor.getByRole('button',{name:'Thêm phần đánh giá',exact:true}).click();
 const newSection=tutor.locator('.rubric-section-card').last();
 await newSection.locator('input').first().fill('Methodology');
 await newSection.getByRole('button',{name:'Thêm tiêu chí con',exact:true}).click();
 await newSection.locator('textarea').fill('Explain how the data was obtained.');
 await tutor.getByRole('button',{name:'Lưu tiêu chí dự án',exact:true}).click();
 await expect(tutor.locator('.rubric-manager')).toContainText('Đã lưu cấu hình');
 await tutor.screenshot({path:path.join(home,'report-rubric-editor.png')});
 for(const category of ['report','research']){
  await tutor.locator('.writing-sidebar').getByRole('button',{name:'Giao bài',exact:true}).click();
  await tutor.getByLabel('Tên bài IELTS').fill(category+' review e2e');
  await tutor.getByLabel('Loại bài đánh giá',{exact:true}).selectOption(category);
  await expect(tutor.getByLabel('Lớp được giao',{exact:true})).toHaveCount(0);
  if(category==='report')await tutor.getByLabel('Dạng Task 1',{exact:true}).selectOption(customProfile);
  await tutor.getByLabel('Tệp đề Task 1',{exact:true}).selectOption('questions.tex');
  await tutor.getByLabel('Trang đầu đề Task 1',{exact:true}).fill('1');await tutor.getByLabel('Trang cuối đề Task 1',{exact:true}).fill('1');
  await tutor.getByRole('button',{name:'Dùng tệp làm đề Task 1',exact:true}).click();
  await expect(tutor.locator('.writing-project-prompt [data-prompt-file]')).toBeVisible();
  await tutor.locator('.writing-form').getByRole('button',{name:'Giao bài',exact:true}).click();
  await expect(tutor.locator('.writing-assignment-list')).toContainText(category+' review e2e');
  await student.locator('.writing-sidebar').getByRole('button',{name:'Bài được giao',exact:true}).click();await student.getByTitle('Tải lại dữ liệu IELTS').click();
  await student.locator('.writing-card').filter({has:student.getByRole('heading',{name:category+' review e2e',exact:true})}).getByRole('button',{name:'Mở bài viết',exact:true}).click();
  await expect(student.locator('.writing-question [data-prompt-file]')).toBeVisible();
  await expect(student.locator('.writing-view-tabs')).toHaveCount(0);
  await student.getByLabel('Câu trả lời Task 1').fill('The study addresses a practical question.\n\nEvidence supports the proposed analysis.\n\nThe conclusion identifies limitations.');
  await student.getByRole('button',{name:'Kiểm tra và nộp',exact:true}).click();await student.getByRole('button',{name:'Xác nhận nộp bài',exact:true}).click();
  await expect(student.getByRole('button',{name:'Tổng kết band',exact:true})).toHaveCount(0);
  await expect(student.getByRole('button',{name:'Tổng kết nhận xét',exact:true})).toBeVisible();
  await tutor.getByRole('button',{name:'Bài nộp học viên',exact:true}).click();await tutor.getByTitle('Tải lại dữ liệu IELTS').click();await tutor.locator('.writing-table tbody tr').filter({hasText:category+' review e2e'}).getByRole('button').click();
  await tutor.getByRole('button',{name:'Tổng kết nhận xét',exact:true}).click();
  await expect(tutor.getByLabel('Band TA',{exact:true})).toHaveCount(0);
  await tutor.getByLabel('Nhận xét chung',{exact:true}).fill('Clear analysis, add stronger references.');
  await tutor.getByLabel('Hướng luyện tiếp',{exact:true}).fill('Document the methodology.');
  await tutor.getByRole('button',{name:'Công bố kết quả',exact:true}).click();
  await expect(tutor.locator('.statusbar')).toContainText('Đã công bố');
  await tutor.screenshot({path:path.join(home,category+'-feedback-without-band.png')});
  await tutor.keyboard.press('Escape');
  const currentOverview=await(await ok(await tc.request.get(url+'api/writing/overview'+query))).json();
  const currentSubmission=currentOverview.submissions.find(s=>s.assignment.title===category+' review e2e');
  expect(currentSubmission.result.taskBands).toEqual({});expect(currentSubmission.result.writingBand).toBeNull();
  const projectFiles=await(await ok(await tc.request.get(url+`api/projects/${project.id}/files`))).json();
  const resultFile=projectFiles.find(f=>f.path.startsWith('result/')&&f.path.includes('-'+category+'-')&&f.path.endsWith('.tex'));
  expect(resultFile).toBeTruthy();
  const resultSource=(await(await ok(await tc.request.get(url+`api/projects/${project.id}/file?path=${encodeURIComponent(resultFile.path)}`))).json()).content;
  expect(resultSource).not.toContain('Estimated task band');expect(resultSource).toContain('Clear analysis');
  const reportCompile=await(await ok(await tc.request.post(url+`api/projects/${project.id}/compile`,{data:{engine:'pdflatex',main:resultFile.path}}))).text();expect(reportCompile).toContain('"type": "done"');
 }
 // Password-protected exam uses the same source picker and grading criteria.
 await tutor.locator('.writing-sidebar').getByRole('button',{name:'Giao bài',exact:true}).click();
 await tutor.getByLabel('Hình thức giao bài',{exact:true}).selectOption('exam');
 await tutor.getByLabel('Tên bài IELTS').fill('Scheduled IELTS exam');
 await tutor.getByLabel('Mật khẩu bài kiểm tra',{exact:true}).fill('exam-test-first');
 const localTime=ms=>new Date(ms-new Date(ms).getTimezoneOffset()*60000).toISOString().slice(0,16);
 await tutor.getByLabel('Giờ mở bài kiểm tra',{exact:true}).fill(localTime(Date.now()-300000));
 await tutor.getByLabel('Giờ đóng bài kiểm tra',{exact:true}).fill(localTime(Date.now()+3600000));
 await tutor.getByLabel('Thời lượng',{exact:true}).fill('30');
 await expect(tutor.getByLabel('Hạn nộp',{exact:true})).toHaveCount(0);
 expect(await tutor.getByLabel('Thời lượng',{exact:true}).getAttribute('required')).not.toBeNull();
 await tutor.getByLabel('Tệp đề Task 2',{exact:true}).selectOption('questions.tex');
 await tutor.getByLabel('Trang đầu đề Task 2',{exact:true}).fill('2');await tutor.getByLabel('Trang cuối đề Task 2',{exact:true}).fill('2');
 await tutor.getByRole('button',{name:'Dùng tệp làm đề Task 2',exact:true}).click();
 await expect(tutor.locator('.writing-project-prompt [data-prompt-file]')).toBeVisible();
 await tutor.screenshot({path:path.join(home,'exam-creation-settings.png')});
 await tutor.locator('.writing-form').getByRole('button',{name:'Giao bài',exact:true}).click();
 await expect(tutor.locator('.writing-kind-tabs').getByRole('tab',{name:'Bài kiểm tra',exact:true})).toHaveAttribute('aria-selected','true');
 await expect(tutor.locator('.writing-assignment-list')).toContainText('Scheduled IELTS exam');
 await student.locator('.writing-sidebar').getByRole('button',{name:'Bài được giao',exact:true}).click();
 await student.getByTitle('Tải lại dữ liệu IELTS').click();
 await expect(student.locator('.writing-assignment-list')).not.toContainText('Scheduled IELTS exam');
 await student.locator('.writing-sidebar').getByRole('button',{name:'Bài kiểm tra',exact:true}).click();
 await expect(student.locator('.writing-assignment-list')).toContainText('Scheduled IELTS exam');
 await expect(student.locator('.writing-assignment-list')).not.toContainText('Task 1 and 2 from editor');
 await student.getByRole('button',{name:'Vào bài kiểm tra',exact:true}).click();
 const gate=student.getByRole('dialog',{name:'Vào bài kiểm tra',exact:true});
 await gate.getByLabel('Mật khẩu vào bài',{exact:true}).fill('incorrect');
 await gate.getByRole('button',{name:'Bắt đầu bài kiểm tra',exact:true}).click();
 await expect(gate.getByRole('alert')).toContainText('không đúng');
 await student.screenshot({path:path.join(home,'exam-password-entry.png')});
 await gate.getByLabel('Mật khẩu vào bài',{exact:true}).fill('exam-test-first');
 await gate.getByRole('button',{name:'Bắt đầu bài kiểm tra',exact:true}).click();
 await expect(student.getByLabel('Câu trả lời Task 2',{exact:true})).toBeVisible();
 const examOverview=await(await ok(await tc.request.get(url+'api/writing/overview'+query))).json();
 const examAssignment=examOverview.assignments.find(a=>a.title==='Scheduled IELTS exam');
 expect(examAssignment.kind).toBe('exam');expect(examAssignment.examSecret).toBeUndefined();expect(examAssignment.examPassword).toBeUndefined();
 const examDraft=await(await ok(await sc.request.get(url+`api/writing/assignments/${examAssignment.id}/draft`+query))).json();
 await tutor.getByRole('button',{name:'Đổi mật khẩu',exact:true}).click();
 const changePassword=tutor.getByRole('dialog',{name:'Đổi mật khẩu bài kiểm tra',exact:true});
 await changePassword.getByLabel('Mật khẩu kiểm tra mới',{exact:true}).fill('exam-test-new');await changePassword.getByRole('button',{name:'Lưu mật khẩu',exact:true}).click();
 await expect(changePassword).toHaveCount(0);
 expect((await(await ok(await sc.request.get(url+`api/writing/assignments/${examAssignment.id}/draft`+query))).json()).deadline).toBe(examDraft.deadline);
 await student.getByLabel('Câu trả lời Task 2',{exact:true}).fill('A timed response with a clear position.');
 await student.getByRole('button',{name:'Kiểm tra và nộp',exact:true}).click();await student.getByRole('button',{name:'Xác nhận nộp bài',exact:true}).click();
 await expect(student.getByRole('button',{name:'Tổng kết band',exact:true})).toBeVisible();
 await tutor.getByRole('button',{name:'Bài nộp học viên',exact:true}).click();
 await tutor.locator('.writing-kind-tabs').getByRole('tab',{name:'Bài kiểm tra',exact:true}).click();await tutor.getByTitle('Tải lại dữ liệu IELTS').click();
 await expect(tutor.locator('.writing-table')).toContainText('Scheduled IELTS exam');await expect(tutor.locator('.writing-table')).not.toContainText('research review e2e');
 const examSubmission=(await(await ok(await tc.request.get(url+'api/writing/overview'+query))).json()).submissions.find(s=>s.assignmentId===examAssignment.id);
 const examGrade={profile:'discussion',scores:{TR:6,CC:6,LR:6,GRA:6},notes:{},sections:[]};
 await ok(await tc.request.put(url+`api/writing/submissions/${examSubmission.id}/review`+query,{data:{revision:0,tasks:{'2':examGrade},summary:'Exam feedback'}}));
 await ok(await tc.request.post(url+`api/writing/submissions/${examSubmission.id}/publish`+query,{data:{revision:1}}));
 await student.locator('.writing-sidebar').getByRole('button',{name:'Kết quả & phản hồi',exact:true}).click();await student.getByTitle('Tải lại dữ liệu IELTS').click();
 await student.locator('.writing-kind-tabs').getByRole('tab',{name:'Bài kiểm tra',exact:true}).click();
 await expect(student.locator('.writing-table')).toContainText('Scheduled IELTS exam');await expect(student.locator('.writing-table')).not.toContainText('research review e2e');
 await student.screenshot({path:path.join(home,'exam-results-separated.png')});
 await student.locator('.writing-kind-tabs').getByRole('tab',{name:'Bài tập',exact:true}).click();
 await expect(student.locator('.writing-table')).not.toContainText('Scheduled IELTS exam');await expect(student.locator('.writing-table')).toContainText('research review e2e');
 expect(errors).toEqual([]);fs.writeFileSync(path.join(home,'report.json'),JSON.stringify({passed:true,oppositeAccountRoles:true,ownerProjectRole:true,presenceEditorAndWriting:true,task1Task2FromEditor:true,realTexCompile:true,pdfPagesAndChart:true,immutablePrompt:true,unchangedDurationAndDeadline:true,studentAnswersPersist:true,submission:true,returnToEditor:true},null,2));
 console.log('WRITING_EDITOR_PROMPTS_OK roles; shared presence; editor LaTeX questions; PDF pages/chart; timers; submit; return editor');
}catch(error){for(const [name,page]of[['tutor',tutor],['student',student]])if(page){console.error(name+': '+(await page.locator('body').innerText()).slice(-3000));await page.screenshot({path:path.join(home,'error-'+name+'.png')}).catch(()=>{});}console.error(stderr.slice(-1500));throw error;}
finally{await browser?.close();if(process.platform==='win32')spawnSync('taskkill',['/PID',String(server.pid),'/T','/F'],{windowsHide:true,stdio:'ignore'});else server.kill('SIGTERM');}
