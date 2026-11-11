import { chromium, expect as baseExpect } from '@playwright/test';
import { spawn, spawnSync } from 'node:child_process';
import fs from 'node:fs';
import path from 'node:path';
const expect=baseExpect.configure({timeout:20000});
const repo=path.resolve('../..'),python=path.resolve(process.argv[2]),home=path.resolve(process.argv[3]);
if(fs.existsSync(home))throw Error('Use a fresh test home');
fs.mkdirSync(home,{recursive:true});
const factory=process.env.LATEX_TEST_WEB_FACTORY||path.join(repo,'apps/backend/tests/run_mongo_web.py');
const server=spawn(python,[factory,'--home',home,'--port','0','--no-browser'],{cwd:repo,windowsHide:true});
let stdout='',stderr='',edge,chrome;
server.stdout.on('data',v=>stdout+=v);server.stderr.on('data',v=>stderr+=v);
const errors=[];
async function sourceText(page){
    return page.locator('.cm-content').evaluate(node=>{
        const copy=node.cloneNode(true);copy.querySelectorAll('.remote-cursor').forEach(n=>n.remove());
        return [...copy.querySelectorAll('.cm-line')].map(n=>n.textContent).join('\n');
    });
}
async function drag(page,word,during){
    const coords=await page.locator('.cm-line').filter({hasText:word}).first().evaluate((line,word)=>{
        const clone=line.cloneNode(true);clone.querySelectorAll('.remote-cursor').forEach(n=>n.remove());
        const start=clone.textContent.indexOf(word);
        const nodes=[],walker=document.createTreeWalker(line,NodeFilter.SHOW_TEXT);
        while(walker.nextNode())if(!walker.currentNode.parentElement.closest('.remote-cursor'))nodes.push(walker.currentNode);
        function at(offset){
            for(const node of nodes){
                if(offset<=node.textContent.length){const range=document.createRange();range.setStart(node,offset);range.collapse(true);const r=range.getBoundingClientRect();return {x:r.x+.2,y:r.y+r.height/2};}
                offset-=node.textContent.length;
            }
            throw Error('No cursor geometry');
        }
        return {from:at(start),to:at(start+word.length)};
    },word);
    await page.mouse.move(coords.from.x,coords.from.y);await page.mouse.down();
    await page.mouse.move((coords.from.x+coords.to.x)/2,coords.to.y,{steps:8});
    if(during)await during();
    await page.mouse.move(coords.to.x,coords.to.y,{steps:10});await page.mouse.up();
    await expect.poll(()=>page.evaluate(()=>window.getSelection()?.toString())).toBe(word);
}
try{
    for(let i=0;i<300&&!stdout.includes('OVERLEAF_WEB_READY ');i++){if(server.exitCode!==null)throw Error(stderr);await new Promise(r=>setTimeout(r,100));}
    const ready=stdout.split(/\r?\n/).find(v=>v.startsWith('OVERLEAF_WEB_READY '));if(!ready)throw Error(stderr);
    const {url}=JSON.parse(ready.slice('OVERLEAF_WEB_READY '.length));
    edge=await chromium.launch({channel:'msedge',headless:true});chrome=await chromium.launch({channel:'chrome',headless:true});
    const owner=await edge.newContext({viewport:{width:1700,height:1000}}),member=await chrome.newContext({viewport:{width:1700,height:1000}}),viewer=await edge.newContext();
    async function signup(context,email,name){
        expect((await context.request.post(url+'api/actions/register',{data:{name,email,password:'review-password'}})).ok()).toBe(true);
        expect((await context.request.post(url+'api/auth/onboarding',{data:{name,role:'student'}})).ok()).toBe(true);
    }
    await signup(owner,'owner@example.com','Real Owner');await signup(member,'member@example.com','Actual Editor');
    const project=await(await owner.request.post(url+'api/projects',{data:{title:'Selection and shared review'}})).json();
    const source='% alphaBRAVOcharlieDELTAecho\n\\documentclass{article}\n\\begin{document}\nShared editing\n\\end{document}\n';
    expect((await owner.request.put(url+`api/projects/${project.id}/file`,{data:{path:'main.tex',content:source}})).ok()).toBe(true);
    const sharing=url+`api/projects/${project.id}/sharing`,review=url+`api/projects/${project.id}/review`;
    const link=(await(await owner.request.patch(sharing,{data:{mode:'link',permission:'editor'}})).json()).linkToken;
    expect((await member.request.post(url+'api/shares/accept',{data:{token:link}})).ok()).toBe(true);
    await owner.request.post(review,{data:{operation:'create',file:'main.tex',from:2,line:1,quote:'alphaBRAVOcharlieDELTAecho',text:'Existing comment'}});
    const a=await owner.newPage(),b=await member.newPage(),v=await viewer.newPage();
    for(const page of [a,b,v])page.on('pageerror',e=>errors.push(String(e)));
    for(const page of [a,b]){await page.goto(url);await page.locator('.project-card-open').click();}
    await expect(a.locator('.presence-person')).toHaveCount(1);
    await a.getByRole('button',{name:'Nhận xét',exact:true}).click();
    await expect(a.locator('.review-message')).toContainText('Existing comment');
    // This used to leave active set, making every parent render select the whole quote.
    await a.locator('.review-location').first().click();
    await b.locator('.cm-content').click();await b.keyboard.press('Control+End');
    await drag(a,'BRAVO',async()=>{
        await b.keyboard.insertText('% remote update during drag');
        await expect.poll(()=>sourceText(a)).toContain('remote update during drag');
    });
    await a.locator('.cm-line').first().screenshot({path:path.join(home,'local-selection-line.png')});
    await expect(a.locator('.cm-editor')).toHaveAttribute('data-local-selection','true');
    await expect(b.locator('.remote-selection')).toHaveCount(0);
    const pixels=spawnSync(python,['-c',
        'from PIL import Image; import sys; im=Image.open(sys.argv[1]).convert("RGB"); n=sum(1 for r,g,b in im.getdata() if b>r+30 and b>g+15 and b>100); print(n); assert n>50, "Local selection has no visible blue pixels"',
        path.join(home,'local-selection-line.png')],{cwd:repo,windowsHide:true,encoding:'utf8'});
    expect(pixels.status,pixels.stderr).toBe(0);
    await a.keyboard.insertText('REPLACED');
    await expect.poll(()=>sourceText(b)).toContain('alphaREPLACEDcharlieDELTAecho');
    await expect.poll(()=>sourceText(a)).not.toContain('alphaBRAVO');
    // Repeated precise selection/deletion, then select a fresh sub-word for a comment.
    await drag(a,'REPLACED');await a.keyboard.press('Backspace');
    await expect.poll(()=>sourceText(b)).toContain('alphacharlieDELTAecho');
    await drag(b,'DELTA');
    await b.getByRole('button',{name:'Thêm nhận xét',exact:true}).click();
    await expect(b.locator('.review-threads > form blockquote')).toHaveText('DELTA');
    await b.getByLabel('Nội dung nhận xét',{exact:true}).fill('Shared from Chrome');
    await b.getByRole('button',{name:'Đăng nhận xét',exact:true}).click();
    await expect(a.getByText('Shared from Chrome',{exact:true})).toBeVisible();
    const thread=a.locator('.review-threads article').filter({hasText:'Shared from Chrome'});
    await expect(thread.locator('strong')).toContainText('Actual Editor');
    await thread.getByLabel('Trả lời',{exact:true}).fill('Reply from Edge');
    await thread.getByRole('button',{name:'Gửi trả lời',exact:true}).click();
    await expect(b.locator('.review-threads article').filter({hasText:'Shared from Chrome'})).toContainText('Reply from Edge');
    await a.screenshot({path:path.join(home,'shared-comment-reply.png')});
    await thread.getByRole('button',{name:'Đánh dấu đã giải quyết',exact:true}).click();
    await expect(b.locator('.review-threads article').filter({hasText:'Shared from Chrome'})).toHaveCount(0);
    await b.getByLabel('Hiện đã giải quyết').check();
    await expect(b.locator('.review-threads article').filter({hasText:'Shared from Chrome'})).toBeVisible();
    // A viewer reads shared comments but cannot submit/reply/delete them.
    await signup(viewer,'viewer@example.com','Only Viewer');
    const viewerLink=(await(await owner.request.patch(sharing,{data:{mode:'link',permission:'viewer'}})).json()).linkToken;
    await viewer.request.post(url+'api/shares/accept',{data:{token:viewerLink}});
    await v.goto(url);await v.locator('.project-card-open').click();
    await v.getByRole('button',{name:'Nhận xét',exact:true}).click();
    await v.getByLabel('Hiện đã giải quyết').check();
    await expect(v.locator('.review-threads article').filter({hasText:'Shared from Chrome'})).toBeVisible();
    await expect(v.getByRole('button',{name:'Xóa',exact:true}).first()).toBeDisabled();
    await expect(v.getByLabel('Trả lời',{exact:true})).toHaveCount(0);
    // No viewer presence socket: periodic review refresh must still deliver a
    // new comment added AFTER this panel was opened.
    await owner.request.post(review,{data:{operation:'create',file:'main.tex',from:2,line:1,quote:'alpha',text:'New comment for viewer fallback'}});
    await expect(v.getByText('New comment for viewer fallback',{exact:true})).toBeVisible();
    // Delete invalidation must remove the thread remotely as well.
    await a.getByLabel('Hiện đã giải quyết').check();
    a.once('dialog',dialog=>dialog.accept());
    await a.locator('.review-threads article').filter({hasText:'Shared from Chrome'}).getByRole('button',{name:'Xóa',exact:true}).click();
    await expect(b.getByText('Shared from Chrome',{exact:true})).toHaveCount(0);
    await expect(v.getByText('Shared from Chrome',{exact:true})).toHaveCount(0);
    await a.screenshot({path:path.join(home,'shared-comment-and-selection.png')});
    expect(errors).toEqual([]);
    fs.writeFileSync(path.join(home,'report.json'),JSON.stringify({passed:true,checks:['mouse drag selects partial word','remote OT update during drag','replace/delete only selected text','comment captures exact selected quote','create/reply/resolve shared between Edge and Chrome','real authors','viewer read-only review'],errors},null,2));
    console.log('SHARED_REVIEW_SELECTION_OK '+home);
}catch(error){
    fs.writeFileSync(path.join(home,'server.log'),stderr);
    for(const browser of [edge,chrome])if(browser)for(const [i,page]of browser.contexts().flatMap(c=>c.pages()).entries())await page.screenshot({path:path.join(home,`failure-${browser===edge?'edge':'chrome'}-${i}.png`)}).catch(()=>{});
    throw error;
}finally{if(edge)await edge.close();if(chrome)await chrome.close();server.kill();}
