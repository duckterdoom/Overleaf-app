import {chromium,expect} from '@playwright/test';
import {pathToFileURL} from 'node:url';
import path from 'node:path';
import fs from 'node:fs';
const repo=path.resolve('../..'),out=path.join(repo,'target/ielts-task1-demo');fs.mkdirSync(out,{recursive:true});
const browser=await chromium.launch({channel:'msedge',headless:true});
try{
 const page=await browser.newPage({viewport:{width:1700,height:1050}}),errors=[];page.on('pageerror',e=>errors.push(e.message));
 await page.goto(pathToFileURL(path.join(repo,'docs/prototypes/writing/tutor.html')).href);
 await page.getByRole('button',{name:'Mở bài chấm mẫu',exact:true}).first().click();await page.getByRole('dialog').getByRole('button',{name:'Mở bài mẫu',exact:true}).click();
 await page.getByRole('button',{name:'Tổng kết band',exact:true}).click();await page.getByRole('combobox',{name:'Band TR',exact:true}).selectOption('7');
 await page.getByRole('button',{name:'Thử Task 1 · biểu đồ',exact:true}).click();
 await expect(page.locator('.task1-reference')).toBeVisible();await expect(page.getByRole('img',{name:/Biểu đồ tự biên soạn/})).toBeVisible();
 await expect(page.locator('[data-review-part="overview"]')).toBeVisible();await expect(page.locator('[data-review-part="conclusion"]')).toHaveCount(0);
 await page.screenshot({path:path.join(out,'task1-reading.png')});
 await page.locator('[data-review-part="overview"]').click();await page.locator('[data-review-check="big-picture"]').check();await page.locator('#section-feedback').fill('Overview đã bao quát xu hướng chính.');
 await page.screenshot({path:path.join(out,'task1-overview-comment.png')});
 await page.getByRole('button',{name:'Tổng kết band',exact:true}).click();await expect(page.getByRole('combobox',{name:'Band TA',exact:true})).toHaveValue('');await page.getByRole('combobox',{name:'Band TA',exact:true}).selectOption('6.5');
 await page.getByRole('button',{name:'Về bài trước',exact:true}).click();await page.getByRole('button',{name:'Tổng kết band',exact:true}).click();await expect(page.getByRole('combobox',{name:'Band TR',exact:true})).toHaveValue('7');
 await page.goto(pathToFileURL(path.join(repo,'docs/prototypes/writing/tutor-task1.html')).href);await page.getByRole('button',{name:'Tổng kết band',exact:true}).click();await expect(page.getByRole('combobox',{name:'Band TA',exact:true})).toHaveValue('6.5');
 expect(errors).toEqual([]);console.log('TASK1_DEMO_OK chart/answer; intro/overview/body cards; TA grading; Task 2 session preserved; direct shortcut and Task 1 state restored');
}finally{await browser.close()}
