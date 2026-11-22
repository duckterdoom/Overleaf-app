import {chromium,expect as baseExpect} from '@playwright/test';
import {spawn} from 'node:child_process';
import fs from 'node:fs';
import path from 'node:path';
const expect=baseExpect.configure({timeout:20000}),repo=path.resolve('../..'),home=path.resolve(process.argv[3]);
if(fs.existsSync(home))throw Error('Use a fresh isolated test home');fs.mkdirSync(home,{recursive:true});
const server=spawn(path.resolve(process.argv[2]),[path.join(repo,'apps/backend/tests/support/run_auth_roles_web.py'),'--home',home,'--port','0','--no-browser'],{cwd:repo,windowsHide:true});
let output='',stderr='',browser;server.stdout.on('data',v=>output+=v);server.stderr.on('data',v=>stderr+=v);
try{
 for(let i=0;i<400&&!output.includes('OVERLEAF_WEB_READY ');i++){if(server.exitCode!==null)throw Error(stderr);await new Promise(r=>setTimeout(r,100));}
 const ready=output.split(/\r?\n/).find(v=>v.startsWith('OVERLEAF_WEB_READY '));if(!ready)throw Error(stderr);
 const {url}=JSON.parse(ready.slice('OVERLEAF_WEB_READY '.length));browser=await chromium.launch({channel:'msedge',headless:true});
 const errors=[];
 for(const role of ['student','tutor']){
  const context=await browser.newContext({viewport:{width:1440,height:1050}}),page=await context.newPage();page.on('pageerror',e=>errors.push(String(e)));
  await page.goto(url);await page.getByRole('button',{name:'Register',exact:true}).click();
  await expect(page.locator('.signup-role-options input:checked')).toHaveCount(0);
  await expect(page.getByRole('button',{name:'Register',exact:true})).toBeDisabled();
  await page.getByLabel('Display name',{exact:true}).fill('Email '+role);
  await page.getByLabel('Email',{exact:true}).fill('email-'+role+'@example.com');await page.getByLabel('Password',{exact:true}).fill('test-password');
  await page.getByRole('radio',{name:role==='student'?'Student':'Teacher (Tutor)',exact:true}).check();
  await page.screenshot({path:path.join(home,'email-'+role+'-choice.png')});
  await page.getByRole('button',{name:'Register',exact:true}).click();
  await expect(page.getByRole('heading',{name:role==='student'?'Student workspace.':'Tutor workspace.',exact:true})).toBeVisible();
  expect((await(await context.request.get(url+'api/session')).json()).session.role).toBe(role);
  if(role==='tutor'){
   await page.getByRole('button',{name:'Home',exact:true}).click();await page.getByRole('button',{name:'Open Task Writing',exact:true}).click();
   await expect(page.locator('.writing-sidebar').getByRole('button',{name:'Question library',exact:true})).toBeVisible();
   await page.getByRole('button',{name:'Profile',exact:true}).click();await page.getByLabel('Account role',{exact:true}).selectOption('student');
   await page.screenshot({path:path.join(home,'profile-account-role.png')});
   await page.getByRole('button',{name:'Save profile',exact:true}).click();await expect(page.getByRole('dialog',{name:'Profile',exact:true})).toHaveCount(0);
   await expect(page.locator('.writing-sidebar').getByRole('button',{name:'Assigned exercises',exact:true})).toBeVisible();
   await expect(page.locator('.writing-sidebar').getByRole('button',{name:'Question library',exact:true})).toHaveCount(0);
   await page.screenshot({path:path.join(home,'existing-teacher-switched-to-student.png')});
   await page.reload();expect((await(await context.request.get(url+'api/session')).json()).session.role).toBe('student');
  }
  await context.close();
 }
 for(const role of ['student','tutor']){
  const context=await browser.newContext({viewport:{width:1440,height:1050}}),page=await context.newPage();page.on('pageerror',e=>errors.push(String(e)));
  await page.route('**/api/auth/google/start',async route=>{
   const response=await route.fetch({maxRedirects:0});
   const state=new URL(response.headers().location).searchParams.get('state');
   if(!state)throw Error('OAuth authorization request must include state');
   await route.fulfill({response,headers:{...response.headers(),location:url+`api/auth/google/callback?code=${role}&state=${encodeURIComponent(state)}`}});
  });
  await page.route('https://accounts.google.com/**',route=>route.abort());
  async function google(){await Promise.all([page.waitForResponse(r=>r.url().startsWith(url+'api/auth/google/callback')),page.getByRole('button',{name:'Continue with Google',exact:true}).click()]);await page.waitForURL(url);}
  await page.goto(url);await google();await expect(page.getByRole('heading',{name:'How will you use Latex?',exact:true})).toBeVisible();
  await expect(page.locator('.role-options input:checked')).toHaveCount(0);await expect(page.getByRole('button',{name:'Enter my workspace',exact:true})).toBeDisabled();
  await page.reload();await expect(page.getByRole('heading',{name:'How will you use Latex?',exact:true})).toBeVisible();
  await page.getByRole('radio',{name:new RegExp(role==='student'?'Student':'Teacher')}).check();await page.screenshot({path:path.join(home,'google-'+role+'-choice.png')});
  await page.getByRole('button',{name:'Enter my workspace',exact:true}).click();await expect(page.getByRole('heading',{name:role==='student'?'Student workspace.':'Tutor workspace.',exact:true})).toBeVisible();
  const session=(await(await context.request.get(url+'api/session')).json()).session;expect(session.provider).toBe('google');expect(session.role).toBe(role);
  await context.request.post(url+'api/actions/logout');await page.reload();await google();
  await expect(page.getByRole('heading',{name:role==='student'?'Student workspace.':'Tutor workspace.',exact:true})).toBeVisible();
  await expect(page.locator('.role-card')).toHaveCount(0);await context.close();
 }
 expect(errors).toEqual([]);fs.writeFileSync(path.join(home,'report.json'),JSON.stringify({passed:true,errors,emailRoles:['student','tutor'],googleRoles:['student','tutor'],googleProvider:'mocked token exchange; real callback/state/session routes',existingRoleChange:true},null,2));console.log('Auth role-selection browser checks passed');
}catch(e){fs.writeFileSync(path.join(home,'report.json'),JSON.stringify({passed:false,error:String(e),stderr},null,2));throw e;}finally{await browser?.close();server.kill();}
