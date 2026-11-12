import {chromium, expect} from '@playwright/test';
import {spawn, spawnSync} from 'node:child_process';
import fs from 'node:fs';
import path from 'node:path';
const repo=path.resolve('../..'),python=path.resolve(process.argv[2]),home=path.resolve(process.argv[3]);
if(fs.existsSync(home))throw Error('Use a fresh test home');
fs.mkdirSync(home,{recursive:true});
const factory=path.join(repo,'apps/backend/tests/run_mongo_web.py');
const server=spawn(python,[factory,'--home',home,'--port','0','--no-browser'],{cwd:repo,windowsHide:true});
let output='',stderr='',browser;
server.stdout.on('data',v=>output+=v);server.stderr.on('data',v=>stderr+=v);
try {
 for(let i=0;i<300&&!output.includes('OVERLEAF_WEB_READY ');i++){if(server.exitCode!==null)throw Error(stderr);await new Promise(r=>setTimeout(r,100));}
 const line=output.split(/\r?\n/).find(v=>v.startsWith('OVERLEAF_WEB_READY '));if(!line)throw Error(stderr);
 const {url}=JSON.parse(line.slice('OVERLEAF_WEB_READY '.length));
 browser=await chromium.launch({channel:'msedge',headless:true});
 const context=await browser.newContext({viewport:{width:1500,height:1000}}),page=await context.newPage(),errors=[];
 page.on('pageerror',e=>errors.push(String(e)));
 expect((await context.request.post(url+'api/actions/register',{data:{email:'tex-ready@example.com',name:'TeX Tester',password:'test-password'}})).ok()).toBe(true);
 expect((await context.request.post(url+'api/auth/onboarding',{data:{name:'TeX Tester',role:'student'}})).ok()).toBe(true);
 await page.goto(url);
 await page.route('**/api/actions/preflight',route=>route.fulfill({json:{missing:[{name:'iftex',package:'iftex'}],bundled:false,toolchainAvailable:false}}));
 await page.getByRole('button',{name:'Dự án mới',exact:true}).click();
 await page.locator('.modal input').first().fill('Server TeX check');
 await page.getByRole('button',{name:'Tạo dự án',exact:true}).click();
 const warning=page.locator('.preflight-warning');
 await expect(warning).toContainText('Máy chủ chưa sẵn sàng biên dịch LaTeX');
 await expect(warning).toContainText('Bạn không cần cài LaTeX trên máy dùng trình duyệt.');
 await expect(warning).not.toContainText('Cài gói bằng trình quản lý TeX của máy rồi thử lại.');
 expect(await warning.locator('details').evaluate(node=>node.open)).toBe(false);
 await warning.locator('summary').click();await expect(warning).toContainText('iftex');
 expect((await(await context.request.get(url+'api/projects')).json()).length).toBe(0);
 await page.screenshot({path:path.join(home,'server-preflight.png')});
 await page.unroute('**/api/actions/preflight');
 await page.getByRole('button',{name:'Tạo dự án',exact:true}).click();
 await expect(page.locator('.cm-content')).toBeVisible();
 await page.locator('.compile-button').click();
 await expect.poll(()=>page.locator('.pdf-sheet').count(),{timeout:90000}).toBeGreaterThan(0);
 expect(errors).toEqual([]);
 fs.writeFileSync(path.join(home,'report.json'),JSON.stringify({passed:true,friendlyServerMessage:true,noClientTexInstall:true,blockedUntilReady:true,realTemplateCreated:true,realPdfCompiled:true},null,2));
 console.log('TEX_PREFLIGHT_OK server message; no client install; real project/PDF after readiness');
} finally {
 await browser?.close();
 if(process.platform==='win32')spawnSync('taskkill',['/PID',String(server.pid),'/T','/F'],{windowsHide:true,stdio:'ignore'});
 else server.kill('SIGTERM');
}
