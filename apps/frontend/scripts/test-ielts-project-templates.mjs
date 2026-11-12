import {chromium, expect as baseExpect} from '@playwright/test';
import {spawn,spawnSync} from 'node:child_process';
import fs from 'node:fs';
import path from 'node:path';
const expect=baseExpect.configure({timeout:30000});
const repo=path.resolve('../..'),python=path.resolve(process.argv[2]),home=path.resolve(process.argv[3]);
if(fs.existsSync(home))throw Error('Use a fresh test home');fs.mkdirSync(home,{recursive:true});
const server=spawn(python,[path.join(repo,'apps/backend/tests/run_mongo_web.py'),'--home',home,'--port','0','--no-browser'],{cwd:repo,windowsHide:true});
let output='',stderr='',browser,page;
server.stdout.on('data',v=>output+=v);server.stderr.on('data',v=>stderr+=v);
async function ok(r){if(!r.ok())throw Error(`${r.status()}: ${await r.text()}`);return r;}
try{
 for(let i=0;i<400&&!output.includes('OVERLEAF_WEB_READY ');i++){if(server.exitCode!==null)throw Error(stderr);await new Promise(r=>setTimeout(r,100));}
 const line=output.split(/\r?\n/).find(v=>v.startsWith('OVERLEAF_WEB_READY '));if(!line)throw Error(stderr);
 const {url}=JSON.parse(line.slice('OVERLEAF_WEB_READY '.length));
 browser=await chromium.launch({channel:'msedge',headless:true});
 const context=await browser.newContext({viewport:{width:1650,height:1060}});page=await context.newPage();const errors=[];
 page.on('pageerror',e=>errors.push(String(e)));
 await ok(await context.request.post(url+'api/actions/register',{data:{email:'ielts-templates@example.com',name:'Template Tutor',password:'test-password'}}));
 await ok(await context.request.post(url+'api/auth/onboarding',{data:{name:'Template Tutor',role:'tutor'}}));
 await page.goto(url);
 await page.getByRole('button',{name:'Dự án mới',exact:true}).click();
 await page.getByRole('button',{name:'Đề mẫu IELTS Writing',exact:true}).click();
 await expect(page.getByLabel('Nhóm mẫu',{exact:true})).toHaveValue('ielts');
 await expect(page.locator('.template-grid button').filter({hasText:'IELTS'})).toHaveCount(1);
 await page.getByRole('button',{name:/IELTS Writing · Bộ đề đầy đủ 21 dạng/}).click();
 await expect(page.locator('.template-ielts-help')).toContainText('question-task1-*.tex');
 await page.screenshot({path:path.join(home,'ielts-template-library.png')});

 await page.locator('.modal input').first().fill('My IELTS Academic mock');
 await page.getByRole('button',{name:'Tạo dự án',exact:true}).click();
 await expect(page.locator('.cm-content')).toContainText('IELTS Writing practice bank');
 const projects=await(await ok(await context.request.get(url+'api/projects'))).json();
 const project=projects.find(p=>p.title==='My IELTS Academic mock');expect(project).toBeTruthy();
 const files=await(await ok(await context.request.get(url+`api/projects/${project.id}/files`))).json();
 for(const file of['main.tex','answers.tex','GUIDE.md','question-task1-bar.tex','question-task2-discussion.tex'])expect(files.some(f=>f.path===file)).toBe(true);
 await page.locator('.compile-button').click();
 await expect.poll(()=>page.locator('.pdf-sheet').count(),{timeout:90000}).toBe(23);
 await page.screenshot({path:path.join(home,'academic-mock-pdf.png')});
 await page.getByRole('button',{name:'Chấm điểm IELTS Writing',exact:true}).click();
 await page.getByRole('button',{name:'Lớp học',exact:true}).click();
 await page.getByLabel('Tên lớp mới',{exact:true}).fill('Template class');
 await page.getByRole('button',{name:'Tạo lớp',exact:true}).click();await expect(page.locator('.writing-class-code')).toBeVisible();
 await page.getByRole('button',{name:'Giao bài IELTS',exact:true}).click();
 await page.getByLabel('Tên bài IELTS').fill('Academic template assignment');await page.getByLabel('Task được giao').selectOption('1,2');
 await page.getByLabel('Thời lượng',{exact:true}).fill('60');
 for(const [task,file] of [[1,'question-task1-bar.tex'],[2,'question-task2-discussion.tex']]){
  await page.getByLabel(`Tệp đề Task ${task}`,{exact:true}).selectOption(file);
  await page.getByRole('button',{name:`Dùng tệp làm đề Task ${task}`,exact:true}).click();
  const pane=page.locator('.writing-project-prompt').filter({has:page.getByLabel(`Tệp đề Task ${task}`,{exact:true})});
  await expect(pane.locator('[data-prompt-file]')).toBeVisible();
  await expect.poll(()=>pane.locator('.pdf-sheet').count(),{timeout:90000}).toBe(1);
 }
 await page.screenshot({path:path.join(home,'assign-template-questions.png')});
 await page.getByRole('button',{name:'Giao bài',exact:true}).click();
 await expect(page.locator('.writing-assignment-list')).toContainText('Academic template assignment');
 const overview=await(await ok(await context.request.get(url+'api/writing/overview?projectId='+project.id))).json();
 expect(overview.assignments[0].durationMinutes).toBe(60);expect(Object.keys(overview.assignments[0].promptFiles)).toHaveLength(2);
 expect(errors).toEqual([]);
 fs.writeFileSync(path.join(home,'report.json'),JSON.stringify({passed:true,templates:1,profiles:21,createdViaUI:true,realMockPDF:true,assignBothTemplateQuestions:true,timer60:true},null,2));
 console.log('IELTS_PROJECT_TEMPLATES_OK 1 bank; UI create; real PDF; both template questions assigned');
}catch(e){if(page){console.error((await page.locator('body').innerText()).slice(-3500));await page.screenshot({path:path.join(home,'error.png')}).catch(()=>{});}throw e;}
finally{await browser?.close();if(process.platform==='win32')spawnSync('taskkill',['/PID',String(server.pid),'/T','/F'],{windowsHide:true,stdio:'ignore'});else server.kill('SIGTERM');}
