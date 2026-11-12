import {chromium,expect as baseExpect} from '@playwright/test';
import {spawn,spawnSync} from 'node:child_process';
import fs from 'node:fs';
import path from 'node:path';
const expect=baseExpect.configure({timeout:30000});
const repo=path.resolve('../..'),python=path.resolve(process.argv[2]),home=path.resolve(process.argv[3]);
if(fs.existsSync(home))throw Error('Use a fresh test home');fs.mkdirSync(home,{recursive:true});
const factory=process.env.LATEX_TEST_WEB_FACTORY||path.join(repo,'apps/backend/tests/run_mongo_web.py');
const server=spawn(python,[factory,'--home',home,'--port','0','--no-browser'],{cwd:repo,windowsHide:true});
let stdout='',stderr='',browser,tutor,student;
server.stdout.on('data',value=>stdout+=value);server.stderr.on('data',value=>stderr+=value);
const answer1=`The chart compares the proportions of commuters using cars, buses and bicycles in a city in 2000 and 2020.

Overall, cars remained the most common form of transport in both years, although their share fell considerably. By contrast, both buses and bicycles became more popular, with cycling experiencing the larger increase.

In 2000, cars accounted for 60 percent of journeys, which was more than twice the figure for buses, at 25 percent. Bicycles were the least popular option, representing the remaining 15 percent.

By 2020, the proportion travelling by car had declined by 15 percentage points to 45 percent. Bus use rose to 30 percent, while the figure for bicycles reached 25 percent. Consequently, the gap between cars and the other two modes narrowed substantially over the period.`;
const answer2=`Traffic congestion affects many growing cities. Some people believe that improving public transport is the most effective response, while others favour additional roads. In my view, reliable public transport should be the priority, alongside targeted improvements to existing roads.

New roads can address specific weaknesses in a transport network. For example, a bypass may divert delivery vehicles away from residential streets, reducing delays around a town centre. Such improvements can support local businesses. However, road expansion alone rarely offers a lasting solution. If driving becomes easier, more residents may choose private cars and eventually fill the additional space.

Public transport can reduce congestion by moving more passengers in fewer vehicles. Frequent buses and well-connected trains make it possible for commuters to leave their cars at home. Affordable tickets also help people with lower incomes reach jobs and education. Nevertheless, these advantages depend on service quality. Passengers will not change their habits if vehicles are consistently late or connections are inconvenient. Investment therefore needs to improve reliability as well as capacity.

In conclusion, roads remain necessary, especially where existing routes create bottlenecks. Yet cities should prioritise dependable public transport because it can serve more people without requiring everyone to drive. A balanced policy should combine this investment with carefully chosen road improvements.`;
async function ok(response){if(!response.ok())throw Error(`${response.status()}: ${await response.text()}`);return response;}
try{
 for(let i=0;i<400&&!stdout.includes('OVERLEAF_WEB_READY ');i++){if(server.exitCode!==null)throw Error(stderr);await new Promise(r=>setTimeout(r,100));}
 const ready=stdout.split(/\r?\n/).find(line=>line.startsWith('OVERLEAF_WEB_READY '));if(!ready)throw Error(stderr);
 const {url}=JSON.parse(ready.slice('OVERLEAF_WEB_READY '.length));
 browser=await chromium.launch({channel:'msedge',headless:true});
 const tc=await browser.newContext({viewport:{width:1720,height:1080}}),sc=await browser.newContext({viewport:{width:1720,height:1080}});
 const errors=[];tutor=await tc.newPage();student=await sc.newPage();for(const page of[tutor,student])page.on('pageerror',e=>errors.push(String(e)));
 async function signup(context,email,name,role){await ok(await context.request.post(url+'api/actions/register',{data:{email,name,password:'test-password'}}));await ok(await context.request.post(url+'api/auth/onboarding',{data:{name,role}}));}
 await signup(tc,'writing-tutor@example.com','Tutor Linh','tutor');await signup(sc,'writing-student@example.com','Student An','student');
 const hostProject=await(await ok(await tc.request.post(url+'api/projects',{data:{title:'IELTS module host project'}}))).json();
 await ok(await tc.request.get(url+'api/projects'));
 const shareRoute=url+`api/projects/${hostProject.id}/sharing`;
 const invitation=await(await ok(await tc.request.post(shareRoute+'/invitations',{data:{emails:['writing-student@example.com'],permission:'editor',workspaceRole:'student'}}))).json();
 await ok(await sc.request.post(url+'api/shares/accept',{data:{token:invitation.members[0].inviteToken}}));
 const writingQuery='?projectId='+hostProject.id;
 for(const page of[tutor,student]){
  await page.goto(url);await page.locator('.project-card-open').click();
  const header=page.locator('.app-header');
  await expect(header).toHaveClass(/in-editor/);
  const headerHeight=await header.evaluate(node=>node.getBoundingClientRect().height);
  await page.getByRole('button',{name:'Chấm điểm IELTS Writing',exact:true}).click();
  await expect(page.locator('[data-writing-ready="true"]')).toBeVisible();
  await expect(header).toHaveCount(1);await expect(header).toContainText('IELTS module host project');
  await expect(header).not.toContainText('IELTS Writing');
  expect(await header.evaluate(node=>node.getBoundingClientRect().height)).toBe(headerHeight);
  await expect(page.locator('.workspace-activity-bar > button')).toHaveCount(4);
  for(const name of['Lịch sử','Chia sẻ','Bố cục'])await expect(header.getByRole('button',{name,exact:true})).toBeVisible();
  for(const name of['Nhận xét','Chat cộng tác','Tệp và outline']){
   await page.getByRole('button',{name,exact:true}).click();await expect(page.locator('.cm-content')).toBeVisible();
   await expect(header).toContainText('IELTS module host project');
   await page.getByRole('button',{name:'Chấm điểm IELTS Writing',exact:true}).click();await expect(page.locator('[data-writing-ready="true"]')).toBeVisible();
  }
  await page.screenshot({path:path.join(home,page===tutor?'tutor-shared-shell.png':'student-shared-shell.png')});
 }
 // The very same share dialog manages roles and permissions from IELTS and editor.
 await tutor.locator('.app-header').getByRole('button',{name:'Chia sẻ',exact:true}).click();
 const shareDialog=tutor.getByRole('dialog',{name:'IELTS module host project'});
 await expect(shareDialog).toBeVisible();
 await tutor.screenshot({path:path.join(home,'sharing-ielts-roles.png')});
 const memberRole=shareDialog.getByLabel('Vai trò của writing-student@example.com',{exact:true});
 await memberRole.selectOption('tutor');
 await expect(student.locator('.writing-notice').filter({hasText:'Vai trò trong dự án'})).toContainText('Tutor');
 await student.screenshot({path:path.join(home,'shared-tutor-role.png')});
 expect((await(await sc.request.get(url+'api/session')).json()).session.role).toBe('student');
 await memberRole.selectOption('student');
 await expect(student.locator('.writing-notice').filter({hasText:'Vai trò trong dự án'})).toContainText('Học viên');
 await shareDialog.getByRole('button',{name:'Đóng chia sẻ',exact:true}).click();
 await tutor.getByRole('button',{name:'Tệp và outline',exact:true}).click();
 await tutor.locator('.app-header').getByRole('button',{name:'Chia sẻ',exact:true}).click();
 await expect(memberRole).toHaveValue('student');
 const memberPermission=shareDialog.getByLabel('Quyền của writing-student@example.com',{exact:true});
 await memberPermission.selectOption('viewer');
 await expect(student.locator('.writing-notice').filter({hasText:'Vai trò trong dự án'})).toContainText('Chỉ xem');
 await memberPermission.selectOption('editor');
 await expect(student.locator('.writing-notice').filter({hasText:'Vai trò trong dự án'})).toContainText('Có quyền chỉnh sửa');
 await shareDialog.getByRole('button',{name:'Đóng chia sẻ',exact:true}).click();
 await tutor.getByRole('button',{name:'Chấm điểm IELTS Writing',exact:true}).click();
 await tutor.getByRole('button',{name:'Lớp học',exact:true}).click();await tutor.getByLabel('Tên lớp mới',{exact:true}).fill('IELTS Writing cohort');await tutor.getByRole('button',{name:'Tạo lớp',exact:true}).click();
 await expect(tutor.locator('.writing-class-code code')).toBeVisible();const code=await tutor.locator('.writing-class-code code').innerText();
 await student.getByRole('button',{name:'Lớp học',exact:true}).click();await student.getByLabel('Mã lớp',{exact:true}).fill(code);await student.getByRole('button',{name:'Tham gia lớp',exact:true}).click();await expect(student.locator('.writing-class-grid')).toContainText('IELTS Writing cohort');
 await tutor.getByRole('button',{name:'Giao bài IELTS',exact:true}).click();await tutor.getByLabel('Tên bài IELTS').fill('Integrated IELTS Task 1 and Task 2');await tutor.getByLabel('Task được giao').selectOption('1,2');await tutor.getByRole('button',{name:'Dùng đề luyện mẫu',exact:true}).click();await tutor.getByLabel('Thời lượng',{exact:true}).fill('60');await tutor.getByRole('button',{name:'Giao bài',exact:true}).click();await expect(tutor.locator('.writing-assignment-list')).toContainText('Integrated IELTS Task 1 and Task 2');
 await student.getByRole('button',{name:'Bài được giao',exact:true}).click();await student.getByTitle('Tải lại dữ liệu IELTS').click();await student.getByRole('button',{name:'Mở bài viết',exact:true}).click();
 await expect(student.getByRole('img',{name:/Biểu đồ luyện tập/})).toBeVisible();await student.getByLabel('Câu trả lời Task 1').fill(answer1);await student.getByRole('tab',{name:'Task 2',exact:true}).click();await student.getByLabel('Câu trả lời Task 2').fill(answer2);await student.getByRole('tab',{name:'Task 1',exact:true}).click();await expect(student.getByLabel('Câu trả lời Task 1')).toHaveValue(answer1);
 await student.getByRole('button',{name:'Nhận xét',exact:true}).click();
 await expect(student.locator('.cm-content')).toBeVisible();
 await student.getByRole('button',{name:'Chấm điểm IELTS Writing',exact:true}).click();
 await student.getByRole('button',{name:'Bài được giao',exact:true}).click();
 await student.getByRole('button',{name:'Mở bài viết',exact:true}).click();
 await expect(student.getByLabel('Câu trả lời Task 1')).toHaveValue(answer1);
 await student.getByRole('tab',{name:'Task 2',exact:true}).click();
 await expect(student.getByLabel('Câu trả lời Task 2')).toHaveValue(answer2);
 await student.getByRole('tab',{name:'Task 1',exact:true}).click();
 await student.screenshot({path:path.join(home,'student-writing.png')});
 await student.getByRole('tab',{name:'LaTeX source',exact:true}).click();await expect(student.getByLabel('LaTeX của bài IELTS')).toContainText('documentclass');
 await student.getByRole('tab',{name:'PDF preview',exact:true}).click();await expect.poll(()=>student.locator('.pdf-sheet').count(),{timeout:90000}).toBeGreaterThan(0);
 await student.screenshot({path:path.join(home,'student-pdf.png')});
 await student.getByRole('tab',{name:'Văn bản',exact:true}).click();await student.getByRole('button',{name:'Kiểm tra và nộp',exact:true}).click();await student.getByRole('button',{name:'Xác nhận nộp bài',exact:true}).click();await expect(student.getByText('Bài đã nộp. Tutor chưa công bố kết quả; bản chấm nháp không hiển thị cho học viên.')).toBeVisible();
 const overview=await(await ok(await sc.request.get(url+'api/writing/overview'+writingQuery))).json();const submission=overview.submissions[0];
 await tutor.getByRole('button',{name:'Bài nộp học viên',exact:true}).click();await tutor.getByTitle('Tải lại dữ liệu IELTS').click();await tutor.getByRole('button',{name:'Mở bài chấm',exact:true}).click();await expect(tutor.locator('.writing-paragraph').first()).toContainText('The chart compares');
 await tutor.locator('.writing-card-toggle').first().click();await tutor.locator('.writing-card-body input[type=checkbox]').first().check();await tutor.getByLabel('Nhận xét đoạn 1',{exact:true}).fill('Giới thiệu rõ đối tượng so sánh. Có thể nêu đủ hai mốc thời gian.');
 await tutor.locator('.writing-paragraph p').first().evaluate(node=>{const range=document.createRange();range.setStart(node.firstChild,0);range.setEnd(node.firstChild,30);const selection=window.getSelection();selection.removeAllRanges();selection.addRange(range);node.dispatchEvent(new MouseEvent('mouseup',{bubbles:true}));});await tutor.getByRole('button',{name:'Gắn phần chữ đã chọn',exact:true}).click();await tutor.getByRole('button',{name:'Lưu nháp chấm',exact:true}).click();
 await expect(tutor.locator('.statusbar')).toContainText('Đã lưu nháp chấm');
 expect((await(await sc.request.get(url+`api/writing/submissions/${submission.id}`+writingQuery)).json()).review).toBeNull();
 await tutor.screenshot({path:path.join(home,'tutor-inline-checklist.png')});
 await tutor.getByRole('button',{name:'Tổng kết band',exact:true}).click();const band=tutor.getByRole('dialog',{name:'Tổng kết band IELTS'});for(const key of['TA','CC','LR','GRA'])await band.getByLabel('Band '+key,{exact:true}).selectOption('6.5');await band.getByRole('button',{name:'Task 2',exact:true}).click();for(const key of['TR','CC','LR','GRA'])await band.getByLabel('Band '+key,{exact:true}).selectOption('7');await band.getByLabel('Nhận xét chung',{exact:true}).fill('Clear structure and balanced development.');await band.getByLabel('Hướng luyện tiếp',{exact:true}).fill('Practise accurate comparisons and concise conclusions.');await band.getByRole('button',{name:'Công bố kết quả',exact:true}).click();await expect(tutor.locator('.statusbar')).toContainText('Đã công bố kết quả');await tutor.screenshot({path:path.join(home,'tutor-band-summary.png')});await tutor.keyboard.press('Escape');
 await expect.poll(async()=>{const response=await sc.request.get(url+`api/writing/submissions/${submission.id}`+writingQuery);return(await response.json()).published?.writingBand;}).toBe(7);
 await student.getByRole('button',{name:'Tổng kết band',exact:true}).click();await expect(student.getByRole('dialog',{name:'Tổng kết band IELTS'})).toContainText('Clear structure and balanced development.');await expect(student.getByRole('dialog').getByRole('combobox')).toHaveCount(0);await student.keyboard.press('Escape');
 await student.getByRole('button',{name:'Luyện viết',exact:true}).click();await student.getByRole('tab',{name:'Task 1',exact:true}).click();await student.getByLabel('Câu trả lời Task 1').fill('Changed working copy after submission.');await student.getByRole('button',{name:'Lịch sử nộp',exact:true}).click();await student.getByRole('button',{name:'Xem bản nộp',exact:true}).click();await expect(student.locator('.writing-paragraph').first()).toContainText('The chart compares');await expect(student.locator('.writing-review')).not.toContainText('Changed working copy');
 await student.setViewportSize({width:390,height:844});await student.screenshot({path:path.join(home,'student-mobile.png')});expect(await student.evaluate(()=>document.documentElement.scrollWidth<=window.innerWidth)).toBe(true);
 await tutor.evaluate(()=>document.documentElement.dataset.mode='dark');await tutor.screenshot({path:path.join(home,'tutor-dark.png')});await tutor.evaluate(()=>document.documentElement.dataset.mode='light');
 await tutor.getByRole('button',{name:'Tạo phiếu LaTeX',exact:true}).click();await expect(tutor.locator('.cm-content')).toContainText('Tutor feedback');await tutor.locator('.compile-button').click();await expect.poll(()=>tutor.locator('.pdf-sheet').count(),{timeout:90000}).toBeGreaterThan(0);await tutor.screenshot({path:path.join(home,'feedback-latex-editor.png')});
 await student.reload();await student.locator('.project-card-open').filter({hasText:'IELTS module host project'}).click();await student.getByRole('button',{name:'Chấm điểm IELTS Writing',exact:true}).click();await student.getByRole('button',{name:'Kết quả & phản hồi',exact:true}).click();await student.getByRole('button',{name:'Xem bản nộp',exact:true}).click();await expect(student.locator('.writing-paragraph').first()).toContainText('The chart compares');
 await ok(await tc.request.delete(shareRoute+'/members/'+invitation.members[0].id));
 await expect(student.locator('.writing-error')).toContainText('Bạn không có quyền truy cập dự án.');
 await expect(student.locator('.writing-review')).toHaveCount(0);
 expect(errors).toEqual([]);fs.writeFileSync(path.join(home,'report.json'),JSON.stringify({passed:true,sharedHeader:true,allFourWorkspaceTools:true,railNavigation:true,sharedIeltsProject:true,projectRoleChanges:true,accountRoleUnchanged:true,editorAndIeltsShareDialog:true,permissionChanges:true,revocationClearsIelts:true,realApp:true,classJoin:true,task1And2:true,serverDraft:true,latexAndPdf:true,immutableSubmission:true,inlineChecklistEvidence:true,draftPrivacy:true,publishedResult:true,roleAcl:true,mobile:true,dark:true,feedbackProjectCompiled:true,reload:true},null,2));console.log('WRITING_WORKFLOW_OK shared project; Tutor/Student roles; editor/IELTS sharing; permissions/revoke; Task1+2; drafts/PDF; immutable submission; private reviews; published band; report compile; mobile/reload');
}catch(error){console.error(stderr.slice(-3000));for(const [name,page]of[['tutor',tutor],['student',student]])if(page){console.error(name+': '+(await page.locator('body').innerText()).slice(-3000));await page.screenshot({path:path.join(home,`error-${name}.png`)}).catch(()=>{});}throw error;
}finally{await browser?.close();spawnSync('taskkill',['/PID',String(server.pid),'/T','/F'],{windowsHide:true,stdio:'ignore'});}
