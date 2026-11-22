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
 async function api(ctx,route,body,method){const r=await ctx.request.fetch(url+'api/'+route,{method:method||(body?'POST':'GET'),data:body});if(!r.ok())throw Error(route+': '+await r.text());return r.json();}
 async function shot(p,name){lastPage=p;await p.screenshot({path:path.join(home,name+'.png')});checks.push(name);}
 async function nav(p,name){await p.locator('.writing-sidebar').getByRole('button',{name,exact:true}).click();}
 await api(tc,'actions/register',{email:'appearance-teacher@example.com',name:'Alex Teacher',password:'test-password',role:'tutor'});
 await api(sc,'actions/register',{email:'appearance-student@example.com',name:'Jamie Student',password:'test-password',role:'student'});
 const course=await api(tc,'writing/courses',{title:'Appearance course'});
 await api(tc,`writing/courses/${course.id}/members`,{email:'appearance-student@example.com'});
 const settings=await api(tc,'writing/settings');
 const content={title:'Appearance exercise',classId:course.id,category:'ielts',variant:'academic',tasks:[2],profiles:{'2':'discussion'},prompts:{'2':'Discuss both views on public transport and state your opinion.'},purpose:'homework',published:true,scoring:{mode:'points',total:10}};
 const assignment=await api(tc,'writing/assignments',content);
 await api(tc,'writing/questions',{visibility:'public',content:{...content,rubricCatalog:{discussion:settings.catalog.discussion}}});
 const draft=await api(sc,`writing/assignments/${assignment.id}/draft`);
 const saved=await api(sc,`writing/assignments/${assignment.id}/draft`,{revision:draft.revision,answers:{'2':'Public transport provides access to work and education. Reliable services can reduce traffic and improve daily life.'}},'PUT');
 const submission=await api(sc,`writing/assignments/${assignment.id}/submit`,{revision:saved.revision,requestId:crypto.randomUUID()});
 const project=await api(tc,'projects',{title:'Appearance document'});
 const failures=[],audits=[];
 async function contrast(page,view){
  await page.waitForTimeout(350);audits.push(view);
  const found=await page.evaluate(()=>{
   const canvas=document.createElement('canvas'),ctx=canvas.getContext('2d',{willReadFrequently:true});canvas.width=canvas.height=1;
   function rgb(color){ctx.clearRect(0,0,1,1);ctx.fillStyle=color;ctx.fillRect(0,0,1,1);const p=ctx.getImageData(0,0,1,1).data;return [p[0],p[1],p[2],p[3]/255];}
   function mix(top,bottom){const a=top[3];return [0,1,2].map(i=>top[i]*a+bottom[i]*(1-a)).concat(1);}
   function lum(c){return c.slice(0,3).map(v=>v/255).map(v=>v<=.04045?v/12.92:((v+.055)/1.055)**2.4).reduce((a,v,i)=>a+v*[.2126,.7152,.0722][i],0);}
   const issues=[];
   for(const el of document.body.querySelectorAll('*')){
    if(!(el instanceof HTMLElement)||el.closest('.cm-editor,.pdf-sheet,.superdoc,.word-page,svg,canvas,option')||el.matches(':disabled'))continue;
    if(!Array.from(el.childNodes).some(n=>n.nodeType===3&&n.textContent.trim()))continue;
    const box=el.getBoundingClientRect(),style=getComputedStyle(el);if(!box.width||!box.height||style.visibility==='hidden'||box.bottom<0||box.top>innerHeight)continue;
    const parents=[];let opacity=1;for(let p=el;p;p=p.parentElement){const s=getComputedStyle(p);opacity*=Number(s.opacity);parents.push(rgb(s.backgroundColor));}if(opacity<.8)continue;
    let bg=[255,255,255,1];for(const c of parents.reverse())bg=mix(c,bg);
    const fg=mix(rgb(style.color),bg),l1=lum(fg),l2=lum(bg),ratio=(Math.max(l1,l2)+.05)/(Math.min(l1,l2)+.05);
    const large=parseFloat(style.fontSize)>=24||(parseFloat(style.fontSize)>=18.66&&Number(style.fontWeight)>=700);const min=large?3:4.5;
    if(ratio<min-.05)issues.push({text:el.textContent.trim().slice(0,70),class:el.className,tag:el.tagName,ratio:Math.round(ratio*100)/100,min,fg:style.color,bg});
   }return issues;
  });failures.push(...found.map(issue=>({view,...issue})));
 }
 for(const role of ['teacher','student']){
  const p=role==='teacher'?teacher:student,c=role==='teacher'?tc:sc;
  for(const mode of ['light','dark'])for(const theme of ['forest','mint','bee','parchment']){
   await api(c,'actions/settings',{mode,theme,fontStyle:'serif'});await p.goto(url);await p.reload();await expect(p.locator('html')).toHaveAttribute('data-font','serif');
   await expect(p.locator('html')).toHaveAttribute('data-mode',mode);
   await expect(p.locator('body')).toHaveCSS('font-family',/Charter|Sitka|Cambria/);
   await expect(p.locator('.dashboard-introduction h1')).toHaveCSS('font-family',/Charter|Sitka|Cambria/);
   await contrast(p,role+'-'+mode+'-'+theme+'-dashboard');
   if(theme==='forest'){
    for(const name of (role==='teacher'?['Courses','Question library','Writing guides','Assessment categories','Created exercises','Student submissions']:['Courses','Writing guides','Assigned exercises','Results & feedback'])){
     await p.locator('.workspace-navigation').getByRole('button',{name,exact:true}).click();await p.waitForTimeout(150);
     await contrast(p,role+'-'+mode+'-'+name);
    }
    await shot(p,role+'-'+mode);
    if(role==='teacher'){
     await p.getByRole('button',{name:'Open assessment',exact:true}).click();await contrast(p,'assessment-'+mode);
     await p.getByRole('button',{name:'Checklist score',exact:true}).click();await contrast(p,'checklist-'+mode);await shot(p,'checklist-'+mode);
     await p.getByRole('button',{name:'Close checklist',exact:true}).click();
     await p.getByRole('button',{name:'Finish score',exact:true}).click();await contrast(p,'finish-score-'+mode);
     await p.getByRole('button',{name:'Close assessment summary',exact:true}).click();
    }
    if(role==='teacher'){
     await p.locator('.workspace-navigation').getByRole('button',{name:'Courses',exact:true}).click();
     await p.getByRole('button',{name:'Create course',exact:true}).click();await contrast(p,'course-form-'+mode);
     await p.getByRole('button',{name:'Close form',exact:true}).click();
     await p.locator('.workspace-navigation').getByRole('button',{name:'Question library',exact:true}).click();
     await p.getByRole('button',{name:'Create question',exact:true}).click();await contrast(p,'question-form-'+mode);
     await p.getByRole('button',{name:'Assign to students',exact:true}).click();await contrast(p,'delivery-form-'+mode);
     await p.getByRole('button',{name:'Home',exact:true}).click();
     await p.locator('.workspace-navigation').getByRole('button',{name:'Template library',exact:true}).click();
     await expect(p.locator('.project-modal')).toBeVisible();await contrast(p,'templates-'+mode);await shot(p,'templates-'+mode);
     await p.locator('.project-modal').getByRole('button',{name:'Cancel',exact:true}).click();
     await p.getByRole('button',{name:'Settings',exact:true}).click();await contrast(p,'settings-'+mode);
     await p.getByRole('dialog',{name:'Settings',exact:true}).getByRole('button',{name:'Close',exact:true}).click();
     await p.locator('.workspace-navigation').getByRole('button',{name:/^All projects/}).click();
     await p.locator('.project-card-open').first().click();await contrast(p,'editor-'+mode);await shot(p,'editor-'+mode);
     await p.getByRole('button',{name:'Share',exact:true}).click();await contrast(p,'share-'+mode);
     await p.locator('.share-modal').getByRole('button',{name:'Done',exact:true}).click();
     await p.getByRole('button',{name:'History',exact:true}).click();await contrast(p,'history-'+mode);
     await p.getByRole('button',{name:'Back to editor',exact:true}).click();
     await p.getByRole('button',{name:'Profile',exact:true}).click();await contrast(p,'profile-'+mode);
     await p.getByRole('dialog',{name:'Profile',exact:true}).getByRole('button',{name:'Close',exact:true}).click();
     await p.getByRole('button',{name:'Home',exact:true}).click();
    }

   }
  }
 }
 await teacher.getByRole('button',{name:'Settings',exact:true}).click();
 const settingDialog=teacher.getByRole('dialog',{name:'Settings',exact:true});
 await settingDialog.getByLabel('Interface font',{exact:true}).selectOption('sans');
 await settingDialog.getByLabel('Mode',{exact:true}).selectOption('system');
 await settingDialog.getByRole('button',{name:'Apply',exact:true}).click();
 await expect(teacher.locator('body')).toHaveCSS('font-family',/Inter|Segoe UI/);
 await teacher.emulateMedia({colorScheme:'light'});await expect(teacher.locator('html')).toHaveAttribute('data-mode','light');
 await teacher.emulateMedia({colorScheme:'dark'});await expect(teacher.locator('html')).toHaveAttribute('data-mode','dark');
 fs.writeFileSync(path.join(home,'contrast.json'),JSON.stringify(failures,null,2));
 fs.writeFileSync(path.join(home,'audit-report.json'),JSON.stringify({audits,failures,errors},null,2));
 console.log('Appearance audit: '+audits.length+' views; contrast findings: '+failures.length);
 expect(failures).toEqual([]);
 expect(errors).toEqual([]);
}catch(e){if(lastPage)await lastPage.screenshot({path:path.join(home,'failure.png')}).catch(()=>{});console.error(stderr);throw e;}
finally{await browser?.close();server.kill();}
