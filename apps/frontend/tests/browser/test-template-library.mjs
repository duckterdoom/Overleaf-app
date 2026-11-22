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
  await api(ctx,'actions/register',{email:role+'-templates@example.com',name:role+' Templates',password:'test-password',role});
  await api(ctx,'actions/settings',{mode:role==='tutor'?'dark':'light',autoCompile:false});
  await page.goto(url);await nav(page,'Template library');
  await expect(page.locator('.template-category-grid')).toBeVisible();await expect(page.locator('.project-modal')).toHaveCount(0);
  await expect(page.locator('.template-category-grid')).not.toContainText(/IELTS/);await shot(page,role+'-template-categories');
  await page.locator('.template-category-card').filter({hasText:'Journal articles'}).click();
  await expect(page.locator('.template-gallery-card')).toHaveCount(4);
  await expect(page.locator('.template-gallery-card').first().locator('.template-thumbnail img')).toBeVisible();await expect(page.locator('.template-gallery-card').nth(2).locator('.template-thumbnail img')).toBeVisible();await shot(page,role+'-journal-gallery');
  await page.locator('.template-gallery-card').filter({has:page.getByRole('heading',{name:'Research article',exact:true})}).click();
  await page.getByRole('button',{name:'View Source',exact:true}).click();await expect(page.locator('.template-document-pane pre')).toContainText('\\documentclass');
  await page.getByLabel('Template source file',{exact:true}).selectOption('preamble.tex');await expect(page.locator('.template-document-pane pre')).toContainText('usepackage');
  await page.getByRole('button',{name:'View PDF',exact:true}).click();await expect(page.locator('.pdf-sheet canvas').first()).toBeVisible();await expect.poll(()=>page.locator('.pdf-sheet canvas').first().evaluate(canvas=>{const context=canvas.getContext('2d');if(!context||canvas.width<10)return false;const pixels=context.getImageData(0,0,canvas.width,canvas.height).data;let ink=0;for(let i=0;i<pixels.length;i+=16)if(pixels[i+3]>128&&pixels[i]<180&&pixels[i+1]<180&&pixels[i+2]<180)ink++;return ink>10;})).toBe(true);await shot(page,role+'-template-detail');
  expect((await api(ctx,'projects')).length).toBe(0);
  await page.getByLabel('Template document language',{exact:true}).selectOption('vi');
  await page.getByRole('button',{name:'Open as Template',exact:true}).click();await expect(page.locator('.desktop-shell')).toHaveAttribute('data-workspace','editor');
  const projects=await api(ctx,'projects');expect(projects.length).toBe(1);expect(projects[0].language).toBe('vi');
  expect((await api(ctx,`projects/${projects[0].id}/file?path=main.tex`)).content).toContain('documentclass');
  await page.getByRole('button',{name:'Home',exact:true}).click();await nav(page,'Template library');
  await page.getByRole('button',{name:'Blank project',exact:true}).click();await expect(page.locator('.project-modal')).toBeVisible();
  await expect(page.locator('.project-modal .template-grid')).toHaveCount(0);
  await expect(page.locator('.project-modal').getByLabel('Icon',{exact:true})).toHaveCount(0);await expect(page.locator('.project-modal').getByLabel('Color',{exact:true})).toHaveCount(0);
  await page.getByRole('button',{name:'Browse templates',exact:true}).click();await expect(page.locator('.project-modal')).toHaveCount(0);
  await expect(page.locator('.template-category-grid')).toBeVisible();
  await page.setViewportSize({width:900,height:950});expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth+1)).toBe(true);await shot(page,role+'-template-responsive');
 }
 expect(errors).toEqual([]);console.log('TEMPLATE_LIBRARY_OK categories, real source, PDF rendering, create own copy, locale, no preview projects, simplified new-project form and both roles');
}catch(e){if(lastPage)await lastPage.screenshot({path:path.join(home,'failure.png')}).catch(()=>{});console.error(stderr);throw e;}
finally{await browser?.close();server.kill();}
