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

 for (const [ctx,page,role] of [[tc,teacher,'tutor'],[sc,student,'student']]) {
  await api(ctx,'actions/register',{email:role+'-navigation@example.com',name:role+' Navigation',password:'test-password',role});
  await api(ctx,'actions/settings',{mode:role==='student'?'dark':'light'});
  await page.goto(url);
  await page.locator('.workspace-navigation').getByRole('button',{name:/^All projects/}).click();
  await expect(page.locator('.projects-main')).toBeVisible();
  let release,arrived=false;
  const gate=new Promise(resolve=>release=resolve);
  await page.route('**/api/writing/overview**',async route=>{
   const response=await route.fetch();arrived=true;await gate;await route.fulfill({response});
  });
  await page.locator('.workspace-navigation').getByRole('button',{name:'Courses',exact:true}).click();
  await expect.poll(()=>arrived).toBe(true);
  const nav=page.locator('.workspace-navigation');
  await expect(nav.getByRole('button',{name:'Writing guides',exact:true})).toBeEnabled();
  await expect(nav.getByRole('button',{name:'Writing guides',exact:true})).toHaveCSS('opacity','1');
  await expect(nav).toContainText(role==='tutor'?'Question library':'Submission history');
  await expect(page.locator('.projects-main')).toHaveCount(0);
  await nav.getByRole('button',{name:'Writing guides',exact:true}).click();
  await expect(page.locator('.writing-page-heading h1')).toHaveText('Writing guides');
  await shot(page,role+'-pending-navigation');
  release();
  await expect(page.locator('.writing-shell')).toHaveAttribute('data-writing-ready','true');
  await expect(page.locator('.writing-page-heading h1')).toHaveText('Writing guides');
  await expect(nav.locator('[aria-current="page"]')).toHaveText('Writing guides');
  await page.unroute('**/api/writing/overview**');
  await shot(page,role+'-ready-navigation');
 }
 expect(errors).toEqual([]);console.log('MODULE_NAVIGATION_OK LaTeX to Task Writing for tutor/student; slow permissions preserve latest selection');
}catch(e){if(lastPage)await lastPage.screenshot({path:path.join(home,'failure.png')}).catch(()=>{});console.error(stderr);throw e;}
finally{await browser?.close();server.kill();}
