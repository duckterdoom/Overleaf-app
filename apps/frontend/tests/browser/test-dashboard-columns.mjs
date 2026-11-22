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


 await api(tc,'actions/register',{email:'dashboard-tutor@example.com',name:'Alex Tutor',password:'test-password',role:'tutor'});
 await api(sc,'actions/register',{email:'dashboard-student@example.com',name:'Jamie Student',password:'test-password',role:'student'});
 const course=await api(tc,'writing/classes',{title:'Academic writing'});await api(sc,'writing/classes/join',{code:course.joinCode});
 await api(tc,'writing/assignments',{title:'Writing task sample',classId:course.id,category:'ielts',tasks:[1],profiles:{'1':'bar'},prompts:{'1':'Describe the chart.'},published:true});
 for(const [ctx,page,role,mode] of [[tc,teacher,'tutor','dark'],[sc,student,'student','light']]) {
  const project=await api(ctx,'projects',{title:role+' Physics document'});
  await api(ctx,'actions/settings',{mode,autoCompile:false});
  await page.goto(url);
  const left=page.getByRole('region',{name:'Task Writing',exact:true}),right=page.getByRole('region',{name:'LaTeX Studio',exact:true});
  await expect(left.getByText('Writing task sample',{exact:true})).toBeVisible();
  await expect(right).toContainText(project.title);
  await expect(left).not.toContainText(project.title);await expect(right).not.toContainText('Writing task sample');
  await expect(left).not.toContainText('Document projects');await expect(right).not.toContainText('Student submissions');
  const a=await left.boundingBox(),b=await right.boundingBox();expect(a.x+a.width).toBeLessThan(b.x);expect(Math.abs(a.y-b.y)).toBeLessThan(3);
  await shot(page,role+'-two-column-dashboard');
  await left.getByRole('button',{name:/Courses/}).click();await expect(page.locator('.writing-page-heading h1')).toHaveText('Courses');
  await page.locator('.workspace-navigation').getByRole('button',{name:'Dashboard',exact:true}).click();
  await page.getByRole('button',{name:'Open LaTeX Studio',exact:true}).click();await expect(page.locator('.project-table')).toBeVisible();
  await page.locator('.workspace-navigation').getByRole('button',{name:'Dashboard',exact:true}).click();
  await page.locator('.dashboard-latex-module .recent-document').first().click();await expect(page.locator('.desktop-shell')).toHaveAttribute('data-workspace','editor');
  await page.getByRole('button',{name:'Home',exact:true}).click();
  await page.getByRole('button',{name:'Open Task Writing',exact:true}).click();await expect(page.locator('.writing-page-heading h1')).toHaveText(role==='tutor'?'Assessment':'Assigned exercises');
  await page.locator('.workspace-navigation').getByRole('button',{name:'Dashboard',exact:true}).click();
  await page.setViewportSize({width:900,height:1000});
  const c=await left.boundingBox(),d=await right.boundingBox();expect(c.y+c.height).toBeLessThanOrEqual(d.y);expect(Math.abs(c.x-d.x)).toBeLessThan(3);
  expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth+1)).toBe(true);await shot(page,role+'-stacked-dashboard');
 }
 expect(errors).toEqual([]);console.log('DASHBOARD_COLUMNS_OK Tutor/Student; writing left, documents right; responsive stacking; all existing entry actions');
}catch(e){if(lastPage)await lastPage.screenshot({path:path.join(home,'failure.png')}).catch(()=>{});console.error(stderr);throw e;}
finally{await browser?.close();server.kill();}
