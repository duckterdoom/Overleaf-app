import {chromium,expect} from '@playwright/test';
import {pathToFileURL} from 'node:url';
import path from 'node:path';
import fs from 'node:fs';
const repo=path.resolve('../..'),out=path.join(repo,'target/ielts-section-review');
fs.mkdirSync(out,{recursive:true});
const browser=await chromium.launch({channel:'msedge',headless:true});
async function openPart(page,id){const active=page.locator('.paragraph-comment-card.is-open');const current=await active.count()?await active.getAttribute('data-comment-id'):null;if(current!==id)await page.locator(`[data-review-part="${id}"]`).click();}
async function options(page){if(!await page.locator('#review-profile').isVisible())await page.getByRole('button',{name:'Dạng bài & phân phần',exact:true}).click();}
try{
 const page=await browser.newPage({viewport:{width:1800,height:1100}}),errors=[];
 page.on('pageerror',e=>errors.push(e.message));
 await page.goto(pathToFileURL(path.join(repo,'docs/prototypes/writing/tutor.html')).href);
 await page.getByRole('button',{name:'Mở bài chấm mẫu',exact:true}).first().click();
 await page.getByRole('dialog').getByRole('button',{name:'Mở bài mẫu',exact:true}).click();
 await openPart(page,'intro');
 await expect(page.locator('#section-review-pane')).toBeVisible();
 await expect(page.locator('.selected-review-paragraph')).toContainText('Traffic congestion');
 await page.locator('[data-review-check="scope"]').check();
 await page.locator('[data-review-status="position"]').selectOption('partial');
 await page.locator('#section-feedback').fill('Introduction: cần làm rõ lập trường và phạm vi.');
 await expect(page.locator('[aria-label="Band TR"]')).toHaveValue('');
 await page.evaluate(()=>{const p=document.querySelector('.review-paragraph p'),text=document.createTreeWalker(p,NodeFilter.SHOW_TEXT).nextNode();const range=document.createRange();range.setStart(text,0);range.setEnd(text,Math.min(38,text.length));const s=getSelection();s.removeAllRanges();s.addRange(range)});
 await page.getByRole('button',{name:'＋ Gắn trích đoạn đang chọn',exact:true}).click();
 await expect(page.locator('.section-evidence blockquote')).toContainText('Traffic congestion');
 await openPart(page,'body-1');
 await expect(page.locator('.selected-review-paragraph')).toContainText('New roads');
 await page.locator('#section-feedback').fill('Body 1: bổ sung minh chứng cụ thể cho ý đường mới.');
 await options(page);await page.getByRole('button',{name:'＋ Thêm phần Body',exact:true}).click();
 await expect(page.locator('[data-review-part="body-3"]')).toBeVisible();
 for(const value of await page.locator('#review-profile option').evaluateAll(nodes=>nodes.map(n=>n.value))){await page.locator('#review-profile').selectOption(value);await openPart(page,'intro');await expect(page.locator('.section-check-row').first()).toBeVisible()}
 await page.locator('#review-profile').selectOption('discussion');
 await openPart(page,'intro');
 await expect(page.locator('[data-review-check="scope"]')).toBeChecked();
 await expect(page.locator('#section-feedback')).toHaveValue('Introduction: cần làm rõ lập trường và phạm vi.');
 await page.reload();
 await page.getByRole('button',{name:'Mở bài chấm',exact:true}).click();
 await openPart(page,'intro');
 await expect(page.locator('[data-review-check="scope"]')).toBeChecked();
 await page.screenshot({path:path.join(out,'tutor-introduction-checklist.png')});
 await page.getByRole('button',{name:'Tổng kết band',exact:true}).click();
 for(const code of ['TR','CC','LR','GRA'])await page.getByRole('combobox',{name:`Band ${code}`,exact:true}).selectOption('7');
 await page.locator('#summary').fill('Đã đọc từng phần; điểm tổng do Tutor đánh giá riêng.');
 await page.getByRole('button',{name:'Công bố phản hồi',exact:true}).click();
 await page.getByRole('dialog').getByRole('button',{name:'Công bố',exact:true}).click();
 await page.locator('#role').selectOption('student');await page.locator('[data-route="results"]').click();
 await expect(page.locator('.published-section-feedback')).toContainText('Introduction: cần làm rõ lập trường và phạm vi.');
 await page.locator('#role').selectOption('teacher');await page.getByRole('button',{name:'Mở bài chấm',exact:true}).click();
 await openPart(page,'intro');
 await page.locator('#section-feedback').fill('PRIVATE NEW DRAFT');
 await page.locator('#role').selectOption('student');await page.locator('[data-route="results"]').click();
 await expect(page.locator('.published-section-feedback')).not.toContainText('PRIVATE NEW DRAFT');
 const reportDownload=page.waitForEvent('download');
 await page.getByRole('button',{name:'Xuất phiếu PDF / LaTeX (thiết kế)',exact:true}).click();
 await page.getByRole('dialog').getByRole('button',{name:'Tải phiếu .tex',exact:true}).click();
 const report=await reportDownload;await report.saveAs(path.join(out,'section-feedback.tex'));
 const tex=fs.readFileSync(await report.path(),'utf8');
 expect(tex).toContain('Góp ý theo từng phần');
 expect(tex).toContain('Introduction: cần làm rõ lập trường và phạm vi.');
 expect(tex).not.toContain('PRIVATE NEW DRAFT');
 await page.getByRole('dialog').getByRole('button',{name:'Quay lại',exact:true}).click();
 // Make a new two-task Academic submission to check Task 1 profiles and isolation.
 await page.locator('[data-route="practice"]').click();
 await page.getByRole('combobox',{name:'Chế độ luyện'}).selectOption('full');
 for(const task of [1,2]){await page.locator(`.task-switch [data-task="${task}"]`).click();await page.locator('#answer').fill('Intro paragraph.\n\nOverview or argument paragraph.\n\nSupporting details.\n\nFinal paragraph.')}
 await page.getByRole('button',{name:'Kiểm tra và nộp',exact:true}).click();await page.getByRole('button',{name:'Xác nhận nộp',exact:true}).click();await page.getByRole('dialog').getByRole('button',{name:'Xác nhận nộp',exact:true}).click();
 await page.locator('#role').selectOption('teacher');await page.getByRole('button',{name:'Mở bài chấm',exact:true}).click();
 await options(page);
 for(const value of await page.locator('#review-profile option').evaluateAll(nodes=>nodes.map(n=>n.value))){await page.locator('#review-profile').selectOption(value);await expect(page.locator('[data-review-part="overview"]')).toBeVisible()}
 await openPart(page,'overview');
 await page.locator('.paragraph-assignment summary').click();
 await page.locator('[data-assign-paragraph="0"]').check();
 await expect(page.locator('.selected-review-paragraph')).toHaveCount(2);
 await page.screenshot({path:path.join(out,'task1-overview-checklist.png')});
 await page.locator('#role').selectOption('student');await page.getByRole('combobox',{name:'Loại bài IELTS'}).selectOption('general');await page.getByRole('combobox',{name:'Chế độ luyện'}).selectOption('single');await page.locator('.task-switch [data-task="1"]').click();
 await page.getByRole('button',{name:'Kiểm tra và nộp',exact:true}).click();await page.getByRole('button',{name:'Xác nhận nộp',exact:true}).click();await page.getByRole('dialog').getByRole('button',{name:'Xác nhận nộp',exact:true}).click();
 await page.locator('#role').selectOption('teacher');await page.getByRole('button',{name:'Mở bài chấm',exact:true}).click();
 await options(page);
 for(const value of ['formal','semiformal','informal']){await page.locator('#review-profile').selectOption(value);await expect(page.locator('[data-review-part="closing"]')).toBeVisible();await expect(page.locator('[data-review-part="overview"]')).toHaveCount(0)}
 await openPart(page,'whole');
 for(const value of await page.locator('#letter-purpose option').evaluateAll(nodes=>nodes.map(n=>n.value))){await page.locator('#letter-purpose').selectOption(value);await expect(page.locator('[data-review-check="purpose-function"]')).toBeVisible()}
 await page.getByRole('button',{name:'Sáng / Tối',exact:true}).click();await page.screenshot({path:path.join(out,'letter-checklist-dark.png')});
 await page.setViewportSize({width:390,height:844});
 expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth+1)).toBe(true);
 expect(await page.locator('#main').evaluate(el=>{el.scrollTop=el.scrollHeight;return el.scrollTop>0})).toBe(true);
 expect(errors).toEqual([]);
 console.log('SECTION_REVIEW_OK 21 profiles, 8 letter purposes; intro/body/overview; checks separate from band; evidence/mapping; reload; published feedback frozen; dark/mobile scrolling');
}finally{await browser.close()}
