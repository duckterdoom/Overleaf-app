import {chromium,expect as baseExpect} from '@playwright/test';
import {spawn} from 'node:child_process';
import fs from 'node:fs';
import path from 'node:path';
const expect=baseExpect.configure({timeout:20000});
const repo=path.resolve('../..'),python=path.resolve(process.argv[2]),home=path.resolve(process.argv[3]);
if(fs.existsSync(home))throw Error('Use a fresh test home');
fs.mkdirSync(home,{recursive:true});
const factory=process.env.LATEX_TEST_WEB_FACTORY||path.join(repo,'apps/backend/tests/run_mongo_web.py');
const server=spawn(python,[factory,'--home',home,'--port','0','--no-browser'],{cwd:repo,windowsHide:true});
let stdout='',stderr='',browser;
server.stdout.on('data',v=>stdout+=v);server.stderr.on('data',v=>stderr+=v);
try{
    for(let i=0;i<300&&!stdout.includes('OVERLEAF_WEB_READY ');i++){if(server.exitCode!==null)throw Error(stderr);await new Promise(r=>setTimeout(r,100));}
    const ready=stdout.split(/\r?\n/).find(v=>v.startsWith('OVERLEAF_WEB_READY '));if(!ready)throw Error(stderr);
    const {url}=JSON.parse(ready.slice('OVERLEAF_WEB_READY '.length));
    browser=await chromium.launch({channel:'msedge',headless:true});
    const context=await browser.newContext({viewport:{width:1700,height:1050}});
    await context.request.post(url+'api/actions/register',{data:{name:'Outline Tester',email:'outline@example.com',password:'outline-password'}});
    await context.request.post(url+'api/auth/onboarding',{data:{name:'Outline Tester',role:'student'}});
    const project=await(await context.request.post(url+'api/projects',{data:{title:'Outline tree'}})).json();
    const source=String.raw`\documentclass{article}
\begin{document}
\section{Introduction}
Introduction text.
\section{Enhancing CyPROM for Hands-On Training}
\subsection{Hands-on Training Methodology}
Body text.
\subsubsection{Practical exercises}
Details.
\subsection{Actions and Scenarios}
Actions.
\section{Conclusion}
Final paragraph.
\end{document}
`;
    expect((await context.request.put(url+`api/projects/${project.id}/file`,{data:{path:'main.tex',content:source}})).ok()).toBe(true);
    const page=await context.newPage(),errors=[];
    page.on('pageerror',e=>errors.push(String(e)));
    await page.goto(url);await page.locator('.project-card-open').click();
    const tree=page.getByRole('tree',{name:'Cấu trúc tài liệu'});
    await expect(tree).toBeVisible();
    const item=title=>tree.getByRole('treeitem',{name:title,exact:true});
    const parent='Enhancing CyPROM for Hands-On Training',child='Hands-on Training Methodology';
    await expect(item(parent)).toHaveAttribute('aria-expanded','true');
    await expect(item(child)).toHaveAttribute('aria-level','2');
    await expect(item('Practical exercises')).toHaveAttribute('aria-level','3');
    await page.getByRole('button',{name:'Thu gọn '+parent,exact:true}).click();
    await expect(item(parent)).toHaveAttribute('aria-expanded','false');
    await expect(item(child)).toHaveCount(0);
    await expect(item('Conclusion')).toBeVisible();
    await item(parent).focus();await page.keyboard.press('ArrowRight');
    await expect(item(child)).toBeVisible();
    await page.keyboard.press('ArrowRight');await expect(item(child)).toBeFocused();await page.keyboard.press('Enter');
    await expect(item(child)).toHaveAttribute('aria-selected','true');
    await expect.poll(()=>page.evaluate(()=>window.getSelection()?.anchorNode?.parentElement?.closest('.cm-line')?.textContent)).toContain('subsection{Hands-on Training Methodology}');
    // Collapsing must survive edits before the headings (offsets change, IDs do not).
    await page.getByRole('button',{name:'Thu gọn '+parent,exact:true}).click();
    await page.locator('.cm-content').click();await page.keyboard.press('Control+Home');await page.keyboard.insertText('% inserted before headings');await page.keyboard.press('Enter');
    await expect(item(parent)).toHaveAttribute('aria-expanded','false');
    await expect(item(child)).toHaveCount(0);
    await page.getByRole('button',{name:'Mở rộng '+parent,exact:true}).click();
    await item('Conclusion').getByRole('button',{name:'Conclusion',exact:true}).click();
    await expect.poll(()=>page.evaluate(()=>window.getSelection()?.anchorNode?.parentElement?.closest('.cm-line')?.textContent)).toContain('section{Conclusion}');
    await expect(item('Conclusion')).toHaveAttribute('aria-selected','true');
    const header=page.getByRole('button',{name:'Cấu trúc tài liệu',exact:true});
    await header.click();await expect(tree).toBeHidden();await header.click();await expect(tree).toBeVisible();
    const before=await page.locator('.file-tree').boundingBox();
    const divider=await page.getByRole('separator',{name:'Điều chỉnh outline',exact:true}).boundingBox();
    await page.mouse.move(divider.x+divider.width/2,divider.y+divider.height/2);await page.mouse.down();
    await page.mouse.move(divider.x+divider.width/2,divider.y-60,{steps:8});await page.mouse.up();
    expect((await page.locator('.file-tree').boundingBox()).height).toBeLessThan(before.height-25);
    await item(parent).getByRole('button',{name:parent,exact:true}).click();
    await expect(item(parent)).toHaveAttribute('aria-selected','true');
    await expect(item(parent).locator('.outline-title')).toHaveCSS('color','rgb(255, 255, 255)');
    expect((await page.locator('.document-outline').boundingBox()).width).toBeLessThanOrEqual((await page.locator('.file-sidebar').boundingBox()).width);
    fs.writeFileSync(path.join(home,'styles.json'),JSON.stringify(await page.locator('.document-outline').evaluate(root=>{
        return ['.outline','.outline-row.selected','.outline-row.selected .outline-title','.outline-toggle-spacer','.outline-children','.outline-tree-heading'].map(selector=>{
            const el=root.querySelector(selector),s=getComputedStyle(el);
            return {selector,display:s.display,color:s.color,background:s.backgroundColor,width:s.width,border:s.borderRadius,overflow:s.overflow,textOverflow:s.textOverflow,padding:s.padding,margin:s.margin,flex:s.flex,justify:s.justifyContent};
        });
    }),null,2));
    await page.screenshot({path:path.join(home,'outline-tree.png')});
    const originalMode=await page.evaluate(()=>document.documentElement.dataset.mode);
    await page.evaluate(()=>{document.documentElement.dataset.mode='dark';});
    await page.locator('.document-outline').screenshot({path:path.join(home,'outline-tree-dark.png')});
    await page.evaluate(mode=>{document.documentElement.dataset.mode=mode;},originalMode);
    expect(errors).toEqual([]);
    fs.writeFileSync(path.join(home,'report.json'),JSON.stringify({passed:true,checks:['nested heading tree','collapse/expand','keyboard navigation','source navigation after offset changes','active heading','collapse preserved while typing','panel toggle','resizable file/outline divider'],errors},null,2));
    console.log('OUTLINE_TREE_OK '+home);
}catch(error){fs.writeFileSync(path.join(home,'server.log'),stderr);if(browser)for(const page of browser.contexts().flatMap(c=>c.pages()))await page.screenshot({path:path.join(home,'failure.png')}).catch(()=>{});throw error;}
finally{if(browser)await browser.close();server.kill();}
