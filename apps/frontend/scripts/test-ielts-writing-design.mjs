import {chromium, expect} from '@playwright/test';
import {pathToFileURL} from 'node:url';
import path from 'node:path';
import fs from 'node:fs';

const repo=path.resolve('../..'), output=path.join(repo,'target/ielts-writing-design');
fs.mkdirSync(output,{recursive:true});
const browser=await chromium.launch({channel:'msedge',headless:true});
try {
  const page=await browser.newPage({viewport:{width:1600,height:1000}}),errors=[];
  page.on('pageerror',e=>errors.push(e.message));
  await page.goto(pathToFileURL(path.join(repo,'docs/prototypes/writing/index.html')).href);
  await expect(page.getByRole('heading',{name:'IELTS Writing',exact:true})).toBeVisible();
  expect(await page.locator('.topbar').evaluate(el=>el.getBoundingClientRect().height)).toBe(42);
  await page.screenshot({path:path.join(output,'student-task2.png')});
  await page.locator('.task-switch [data-task="1"]').click();
  await expect(page.getByRole('img',{name:/Biểu đồ tự biên soạn/})).toBeVisible();
  await page.screenshot({path:path.join(output,'student-task1.png')});
  await page.getByRole('combobox',{name:'Loại bài IELTS'}).selectOption('general');
  await expect(page.getByRole('heading',{name:'Letter writing'})).toBeVisible();
  await expect(page.locator('.prompt-chart')).toHaveCount(0);
  await page.getByRole('combobox',{name:'Loại bài IELTS'}).selectOption('academic');
  await page.getByRole('textbox',{name:'Bài viết Task 1'}).fill('The chart compares the transport choices of commuters.');
  await page.locator('.task-switch [data-task="2"]').click();
  await page.getByRole('textbox',{name:'Bài viết Task 2'}).fill('Public transport can improve mobility.');
  await expect(page.locator('#word-count')).toContainText('5 từ');
  await page.getByRole('button',{name:'Kiểm tra và nộp',exact:true}).click();
  await page.getByRole('button',{name:'Xác nhận nộp',exact:true}).click();
  await expect(page.getByRole('dialog')).toContainText('chưa đủ số từ');
  await page.getByRole('dialog').getByRole('button',{name:'Xác nhận nộp',exact:true}).click();
  await expect(page.locator('#main')).toContainText('Public transport can improve mobility.');
  await page.getByRole('button',{name:'Tiếp tục luyện',exact:true}).click();
  await page.getByRole('textbox',{name:'Bài viết Task 2'}).fill('DIFFERENT WORKING COPY');
  await page.locator('[data-route="submitted"]').click();
  await expect(page.locator('.reading-answer')).toHaveText('Public transport can improve mobility.');
  await page.locator('[data-route="practice"]').click();
  const essay=`Traffic congestion is a major problem in many growing cities. Some residents believe that better public transport is the most effective solution, while others argue that additional roads are essential. Although road improvements can help in particular situations, I believe that investment in reliable public transport should receive greater priority.

Building new roads can offer immediate benefits where the existing network has clear weaknesses. For example, a bypass can keep heavy vehicles away from a crowded residential district. Better connections may also reduce the time that delivery drivers spend travelling between industrial areas. However, these advantages do not mean that every city should continue expanding its roads. When driving becomes easier, more people may choose to use private cars, and the new capacity can eventually become congested again.

Public transport offers a different approach because it moves large numbers of people using relatively little road space. A frequent bus service can make commuting more predictable, especially when buses have their own lanes. Affordable fares and convenient connections also allow people who cannot drive to reach workplaces and educational institutions. These benefits depend on service quality: an unreliable network will not persuade many drivers to change their habits.

In my view, cities should first make public transport a practical alternative to the car. This requires regular services, safe stops and integrated tickets, rather than simply purchasing more vehicles. Targeted road improvements can complement that strategy where there is a specific need. Overall, a balanced policy is necessary, but public transport deserves the larger share of investment because it addresses the movement of people rather than only the movement of cars.`;
  await page.getByRole('textbox',{name:'Bài viết Task 2'}).fill(essay);
  await page.locator('#answer').evaluate(el=>el.scrollTop=0);
  await page.screenshot({path:path.join(output,'student-answer.png')});
  await page.getByRole('button',{name:'Kiểm tra và nộp',exact:true}).click();
  await page.getByRole('button',{name:'Xác nhận nộp',exact:true}).click();
  await page.getByRole('dialog').getByRole('button',{name:'Xác nhận nộp',exact:true}).click();
  await page.getByRole('combobox',{name:'Vai trò'}).selectOption('teacher');
  await page.locator('.side-link[data-route="submissions"]').click();
  await page.getByRole('button',{name:'Chấm bài',exact:true}).click();
  await page.getByRole('button',{name:'Tổng kết band',exact:true}).click();
  await expect(page.getByRole('combobox',{name:'Band TR',exact:true})).toHaveValue('');
  await page.getByRole('button',{name:'Công bố phản hồi',exact:true}).click();
  await expect(page.locator('#toast')).toContainText('chấm đủ 4 tiêu chí');
  for (const [code,value] of [['TR','7'],['CC','6.5'],['LR','7'],['GRA','6.5']]) {
    await page.getByRole('combobox',{name:`Band ${code}`,exact:true}).selectOption(value);
  }
  await page.locator('.band-card .criterion-feedback summary').first().click();
  await page.getByRole('textbox',{name:'Nhận xét TR',exact:true}).fill('Hai quan điểm đã được nêu; có thể phát triển thêm ví dụ cụ thể.');
  await page.locator('#summary').fill('Giữ cách tổ chức rõ ràng; luyện phát triển ví dụ và dùng từ chính xác hơn.');
  await expect(page.locator('#task-band')).toHaveText('7.0');
  await page.locator('.assessment-pane').evaluate(el=>el.scrollTop=0);
  await page.locator('#toast').evaluate(el=>el.style.display='none');
  await page.screenshot({path:path.join(output,'teacher-grading.png')});
  await page.getByRole('combobox',{name:'Vai trò'}).selectOption('student');
  await page.locator('[data-route="results"]').click();
  await expect(page.getByRole('heading',{name:'Chưa có kết quả được công bố'})).toBeVisible();
  await page.getByRole('combobox',{name:'Vai trò'}).selectOption('teacher');
  await page.locator('.side-link[data-route="submissions"]').click();
  await page.getByRole('button',{name:'Chấm bài',exact:true}).click();
  await page.getByRole('button',{name:'Tổng kết band',exact:true}).click();
  await page.getByRole('button',{name:'Công bố phản hồi',exact:true}).click();
  await page.getByRole('dialog').getByRole('button',{name:'Công bố',exact:true}).click();
  await page.getByRole('combobox',{name:'Vai trò'}).selectOption('student');
  await page.locator('[data-route="results"]').click();
  await expect(page.getByRole('heading',{name:'Task 2 band ước tính',exact:true})).toBeVisible();
  await expect(page.locator('.band-number')).toHaveText('7.0');
  await page.screenshot({path:path.join(output,'student-feedback.png')});
  await page.reload();
  await expect(page.locator('.band-number')).toHaveText('7.0');
  await page.locator('[data-route="practice"]').click();
  await page.getByRole('combobox',{name:'Chế độ luyện'}).selectOption('full');
  await page.locator('.task-switch [data-task="1"]').click();
  await expect(page.getByRole('textbox',{name:'Bài viết Task 1'})).toHaveValue('The chart compares the transport choices of commuters.');
  await page.getByRole('button',{name:'Bắt đầu',exact:true}).click();
  await page.locator('.task-switch [data-task="2"]').click();
  await expect(page.locator('#timer')).toHaveText(/^(60:00|59:\d\d)$/);
  await page.getByRole('button',{name:'Kiểm tra và nộp',exact:true}).click();
  await expect(page.locator('.ielts-submit-task')).toHaveCount(2);
  await page.getByRole('button',{name:'Xác nhận nộp',exact:true}).click();
  await page.getByRole('dialog').getByRole('button',{name:'Xác nhận nộp',exact:true}).click();
  await page.getByRole('combobox',{name:'Vai trò'}).selectOption('teacher');
  await page.locator('.side-link[data-route="submissions"]').click();
  await page.getByRole('button',{name:'Chấm bài',exact:true}).click();
  await page.getByRole('button',{name:'Tổng kết band',exact:true}).click();
  for (const code of ['TA','CC','LR','GRA']) await page.getByRole('combobox',{name:`Band ${code}`,exact:true}).selectOption('6');
  await page.locator('.ielts-strip [data-task="2"]').click();
  for (const code of ['TR','CC','LR','GRA']) await page.getByRole('combobox',{name:`Band ${code}`,exact:true}).selectOption('8');
  await page.locator('#summary').fill('Task 2 có trọng số gấp đôi Task 1 trong bản ước tính.');
  await page.getByRole('button',{name:'Công bố phản hồi',exact:true}).click();
  await page.getByRole('dialog').getByRole('button',{name:'Công bố',exact:true}).click();
  await page.getByRole('combobox',{name:'Vai trò'}).selectOption('student');
  await page.locator('[data-route="results"]').click();
  await expect(page.getByRole('heading',{name:'Writing band ước tính',exact:true})).toBeVisible();
  await expect(page.locator('.band-number')).toHaveText('7.5');
  await page.getByRole('button',{name:'Sáng / Tối',exact:true}).click();
  await page.locator('[data-route="practice"]').click();
  await page.screenshot({path:path.join(output,'student-dark.png')});
  await page.setViewportSize({width:390,height:844});
  await page.screenshot({path:path.join(output,'student-mobile.png')});
  expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth+1)).toBe(true);
  expect(await page.locator('#main').evaluate(el=>{el.scrollTop=el.scrollHeight;return el.scrollTop>0})).toBe(true);
  expect(errors).toEqual([]);
  console.log('IELTS_DESIGN_OK Academic chart / GT letter / essay; save per task; word warning; submit without LaTeX; frozen answer; null bands; draft/private; single task band; 1:2 weighted Writing; timer; reload; dark/mobile');
} finally { await browser.close(); }
