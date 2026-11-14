import {chromium,expect} from '@playwright/test';
import {spawn} from 'node:child_process';
import fs from 'node:fs';
import path from 'node:path';

const repo=path.resolve('../..'),python=path.resolve(process.argv[2]),home=path.resolve(process.argv[3]);
if(fs.existsSync(home))throw Error('Use a fresh isolated test home');
fs.mkdirSync(home,{recursive:true});
const server=spawn(python,[path.join(repo,'apps/backend/tests/run_mongo_web.py'),'--home',home,'--port','0','--no-browser'],{cwd:repo,windowsHide:true});
let stdout='',stderr='',browser;
server.stdout.on('data',v=>stdout+=v);server.stderr.on('data',v=>stderr+=v);
async function json(context,url,route,data){
 const r=data?await context.request.post(url+'api/'+route,{data}):await context.request.get(url+'api/'+route);
 if(!r.ok())throw Error(`${r.status()}: ${await r.text()}`);
 return r.json();
}
try{
 for(let i=0;i<400&&!stdout.includes('OVERLEAF_WEB_READY ');i++){
  if(server.exitCode!==null)throw Error(stderr);
  await new Promise(r=>setTimeout(r,100));
 }
 const ready=stdout.split(/\r?\n/).find(v=>v.startsWith('OVERLEAF_WEB_READY '));
 if(!ready)throw Error(stderr);
 const {url}=JSON.parse(ready.slice('OVERLEAF_WEB_READY '.length));
 browser=await chromium.launch({channel:'msedge',headless:true});
 const author=await browser.newContext({viewport:{width:1440,height:1000}});
 const reader=await browser.newContext({viewport:{width:1440,height:1000}});
 for(const [context,email,name] of [[author,'author@example.com','Author Linh'],[reader,'reader@example.com','Reader An']]){
  await json(context,url,'actions/register',{email,name,password:'test-password'});
  await json(context,url,'auth/onboarding',{name,role:'tutor'});
 }
 const course=await json(author,url,'writing/classes',{title:'Library cohort'});
 const settings=await json(author,url,'writing/settings');
 const content={title:'Shared transport',category:'ielts',variant:'academic',tasks:[2],profiles:{'2':'discussion'},prompts:{'2':'Discuss transport options.'},writingScope:'intro',rubricCatalog:{discussion:settings.catalog.discussion}};
 await json(author,url,'writing/questions',{visibility:'public',content});
 await json(author,url,'writing/questions',{visibility:'public',content:{...content,category:'report',tasks:[1],profiles:{'1':'report'},prompts:{'1':'Write a report on transport.'},rubricCatalog:{report:settings.catalog.report}}});
 const page=await author.newPage(),other=await reader.newPage(),errors=[];
 for(const p of [page,other])p.on('pageerror',e=>errors.push(String(e)));
 async function openLibrary(p){
  await p.goto(url);
  await p.locator('.product-navigation').getByRole('button',{name:'Task Writing',exact:true}).click();
  await p.locator('.writing-sidebar').getByRole('button',{name:'Question library',exact:true}).click();
 }
 await openLibrary(page);
 await expect(page.locator('.question-library article')).toHaveCount(2);
 await expect(page.locator('.question-library article').filter({hasText:'IELTS'})).toContainText('Task 2');
 await expect(page.locator('.question-library article').filter({hasText:'Report'})).toContainText('Created by you');
 await page.locator('.question-library article').filter({hasText:'IELTS'}).getByRole('button',{name:'Use for an exercise',exact:true}).click();
 await expect(page.getByLabel('Question visibility')).toHaveValue('private');
 await page.getByLabel('Question visibility').selectOption('public');
 await page.getByLabel('Exercise title').fill('Reusable exercise');
 await page.getByLabel('Course or group').selectOption(course.id);
 await page.getByRole('button',{name:'Assign exercise',exact:true}).last().click();
 await expect(page.locator('.writing-assignment-list')).toContainText('Reusable exercise');
 await page.locator('.writing-sidebar').getByRole('button',{name:'Question library',exact:true}).click();
 await expect(page.locator('.question-library article')).toHaveCount(3);
 await expect(page.locator('.question-library article').filter({hasText:'Reusable exercise'})).toContainText('Saved from an exercise');
 await page.screenshot({path:path.join(home,'my-library.png'),fullPage:true});
 await openLibrary(other);
 await expect(other.locator('.question-library article')).toHaveCount(0);
 await other.getByRole('button',{name:'Public library',exact:true}).click();
 await expect(other.locator('.question-library article')).toHaveCount(3);
 await other.getByLabel('Search by question title').fill('shared TRANSPORT');
 await expect(other.locator('.question-library article')).toHaveCount(2);
 await other.getByLabel('Assessment type',{exact:true}).selectOption('report');
 await expect(other.locator('.question-library article')).toHaveCount(1);
 await expect(other.locator('.question-library article')).toContainText('Report');
 await other.getByRole('button',{name:'Add to my library',exact:true}).click();
 await expect(other.getByRole('button',{name:'Added to My library',exact:true})).toBeDisabled();
 await other.getByRole('button',{name:'My library',exact:true}).click();
 await expect(other.locator('.question-library article')).toHaveCount(1);
 await expect(other.locator('.question-library article')).toContainText('Your copy · Originally by Author Linh');
 await expect(other.locator('.question-library article')).toContainText('Private');
 await other.getByRole('button',{name:'Edit question',exact:true}).click();
 await other.getByLabel('Exercise title').fill('My transport report');
 await other.getByRole('button',{name:'Save question',exact:true}).click();
 await other.getByLabel('Search by question title').fill('');
 await expect(other.locator('.question-library article')).toContainText('My transport report');
 await other.getByLabel('Search by question title').fill('not found');
 await expect(other.getByRole('button',{name:'Clear search and filters'})).toBeVisible();
 await other.getByRole('button',{name:'Clear search and filters'}).click();
 await other.setViewportSize({width:375,height:812});
 await expect(other.locator('.question-library article')).toHaveCount(1);
 await other.screenshot({path:path.join(home,'mobile-library.png'),fullPage:true});
 expect(await other.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true);
 await other.setViewportSize({width:812,height:375});
 await other.emulateMedia({reducedMotion:'reduce'});
 expect(await other.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true);
 expect(errors).toEqual([]);
 console.log('Question library browser checks passed');
}catch(e){fs.writeFileSync(path.join(home,'failure.txt'),String(e)+'\n'+stderr);throw e;}
finally{await browser?.close();server.kill();}
