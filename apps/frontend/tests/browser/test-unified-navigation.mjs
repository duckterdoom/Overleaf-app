import {chromium,expect as baseExpect} from '@playwright/test';
import {spawn} from 'node:child_process';
import fs from 'node:fs';
import path from 'node:path';
const expect=baseExpect.configure({timeout:20000});
const repo=path.resolve('../..'),home=path.resolve(process.argv[3]);
if(fs.existsSync(home))throw Error('Use a fresh test home');
fs.mkdirSync(home,{recursive:true});
const server=spawn(path.resolve(process.argv[2]),[path.join(repo,'apps/backend/tests/support/run_mongo_web.py'),'--home',home,'--port','0','--no-browser'],{cwd:repo,windowsHide:true});
let output='',stderr='',browser,lastPage;const errors=[],checks=[];
server.stdout.on('data',v=>output+=v);server.stderr.on('data',v=>stderr+=v);
try{
 for(let i=0;i<400&&!output.includes('OVERLEAF_WEB_READY ');i++){if(server.exitCode!==null)throw Error(stderr);await new Promise(r=>setTimeout(r,100));}
 const line=output.split(/\r?\n/).find(s=>s.startsWith('OVERLEAF_WEB_READY '));if(!line)throw Error(stderr);
 const {url}=JSON.parse(line.slice('OVERLEAF_WEB_READY '.length));
 browser=await chromium.launch({channel:'msedge',headless:true});
 const tc=await browser.newContext({viewport:{width:1500,height:1000}}),sc=await browser.newContext({viewport:{width:1500,height:1000}});
 const teacher=await tc.newPage(),student=await sc.newPage();
 for(const p of [teacher,student])p.on('pageerror',e=>errors.push(String(e)));
 async function api(ctx,route,body){const r=await ctx.request.fetch(url+'api/'+route,{method:body?'POST':'GET',data:body});if(!r.ok())throw Error(route+': '+await r.text());return r.json();}
 async function shot(p,name){lastPage=p;await p.screenshot({path:path.join(home,name+'.png')});checks.push(name);}
 async function nav(p,name){await p.locator('.writing-sidebar').getByRole('button',{name,exact:true}).click();}
 await api(tc,'actions/register',{email:'nav-teacher@example.com',name:'Alex Teacher',password:'test-password',role:'tutor'});
 await api(sc,'actions/register',{email:'nav-student@example.com',name:'Jamie Student',password:'test-password',role:'student'});
 await teacher.goto(url);
 const sidebar=teacher.locator('.workspace-navigation');
 await expect(sidebar.getByRole('button',{name:'Overview',exact:true})).toHaveCount(0);
 await expect(sidebar.getByRole('button',{name:'Dashboard',exact:true})).toHaveCount(1);
 await expect(teacher.getByRole('region',{name:'Task Writing',exact:true})).toBeVisible();
 await expect(teacher.getByRole('region',{name:'LaTeX Studio',exact:true})).toBeVisible();
 await sidebar.getByRole('button',{name:'Courses',exact:true}).click();
 await teacher.getByRole('button',{name:'Create course',exact:true}).click();
 await expect(teacher.locator('dialog[open]')).toHaveCount(0);
 await expect(teacher.getByRole('region',{name:'Create course',exact:true})).toBeVisible();
 await expect(teacher.getByLabel('Course type',{exact:true})).toHaveCount(0);
 await teacher.getByLabel('Course name',{exact:true}).fill('Writing course');
 await teacher.getByRole('button',{name:'Save course',exact:true}).click();
 await teacher.getByRole('button',{name:'View course Writing course',exact:true}).click();
 await teacher.getByRole('button',{name:'Add student',exact:true}).click();
 await teacher.getByLabel('Student email for Writing course',{exact:true}).fill('nav-student@example.com');
 await teacher.getByRole('button',{name:'Add registered student',exact:true}).click();
 await expect(teacher.locator('.course-roster-table')).toContainText('Jamie Student');
 await teacher.getByRole('button',{name:'Edit student Jamie Student',exact:true}).click();
 await expect(teacher.locator('dialog[open]')).toHaveCount(0);
 await teacher.getByRole('textbox',{name:'Student code',exact:true}).fill('ST01');
 await teacher.getByRole('button',{name:'Save student details',exact:true}).click();
 await expect(teacher.locator('.course-roster-table')).toContainText('ST01');
 await shot(teacher,'course-inline-roster');
 await sidebar.getByRole('button',{name:'Dashboard',exact:true}).click();
 await expect(teacher.getByRole('region',{name:'Task Writing',exact:true})).toBeVisible();
 await expect(teacher.getByRole('region',{name:'LaTeX Studio',exact:true})).toBeVisible();
 await shot(teacher,'unified-dashboard');
 await teacher.setViewportSize({width:390,height:844});
 await expect(sidebar.getByRole('button',{name:'Dashboard',exact:true})).toBeVisible();
 await shot(teacher,'mobile-navigation');
 expect(errors).toEqual([]);console.log('Unified navigation and inline course UI passed: '+checks.length+' views');
}catch(e){if(lastPage)await lastPage.screenshot({path:path.join(home,'failure.png')}).catch(()=>{});console.error(stderr);throw e;}
finally{await browser?.close();server.kill();}
