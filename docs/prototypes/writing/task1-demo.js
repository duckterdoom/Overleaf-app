/* Separate demo slots preserve the previous essay, learner drafts and assessment. */
const SAMPLE_SLOTS='latex-ielts-sample-slots-v1';
const task1Sample=`The bar chart compares the percentages of commuters who travelled by car, bus and bicycle in a city in 2000 and 2020.

Overall, cars remained the most common means of transport in both years, although their share fell considerably. By contrast, both buses and bicycles became more popular, with cycling showing the larger increase.

In 2000, cars accounted for 60% of commuting journeys, which was more than twice the figure for buses, at 25%. Bicycles were the least widely used option, representing the remaining 15%. Twenty years later, the proportion of people travelling by car had dropped to 45%, a decline of 15 percentage points. Despite this reduction, car use was still higher than either of the other two individual categories.

The share of bus users rose from 25% to 30% over the period, while the figure for cyclists climbed from 15% to 25%. Consequently, the gap between buses and bicycles narrowed from ten to five percentage points. Taken together, these two alternatives accounted for 55% of journeys in 2020, compared with 40% in 2000.`;
function demoSlots(){try{return JSON.parse(localStorage.getItem(SAMPLE_SLOTS)||'{}')}catch{return {}}}
function currentSlot(){return model.demoFixture||(model.submission?.demo&&model.submission.tasks.length===1&&model.submission.tasks[0]===2?'task2':'previous')}
function loadTask1Sample(){
 const slots=demoSlots(),appearance=model.appearance;
 slots[currentSlot()]=JSON.parse(JSON.stringify(model));
 if(currentSlot()!=='task1')slots.returnTo=currentSlot();
 try{localStorage.setItem(SAMPLE_SLOTS,JSON.stringify(slots))}catch{toast('Không lưu được bản demo trước; chưa chuyển bài mẫu để giữ dữ liệu.');return;}
 const existing=slots.task1;
 if(existing)model=existing;
 else{
  model=seed();model.demoFixture='task1';model.task=1;model.variant='academic';model.title='Task 1 Academic · Commuting methods';model.attempt=1;model.answers[1]=task1Sample;
  model.submission={tasks:[1],answers:{1:task1Sample,2:''},variant:'academic',attempt:1,at:new Date().toLocaleString('vi-VN'),demo:true,latexSources:{1:answerTex(task1Sample,1)}};
 }
 model.demoFixture='task1';model.appearance=appearance;model.role='teacher';model.screen='grading';
 commentUi.bands=false;
 persist();render();$('#main').scrollTop=0;
}
function restorePreviousDemo(){
 const slots=demoSlots(),previous=slots[slots.returnTo]||slots.task2||slots.previous;if(!previous){toast('Chưa có phiên trước đó. Mở Tổng quan Tutor để tạo bài mẫu Task 2.');return;}
 slots[currentSlot()]=JSON.parse(JSON.stringify(model));
 const appearance=model.appearance;model=JSON.parse(JSON.stringify(previous));model.appearance=appearance;model.role='teacher';model.screen=model.submission?'grading':'tutor';
 localStorage.setItem(SAMPLE_SLOTS,JSON.stringify(slots));commentUi.bands=false;persist();render();
}
const renderBeforeTask1=render;
render=function(){
 renderBeforeTask1();
 const title=$('.project-name')?.lastChild;if(title?.nodeType===Node.TEXT_NODE)title.nodeValue=model.title;
 if(model.role==='teacher'&&['tutor','submissions','grading'].includes(model.screen)){
  const actions=$('.ielts-head .ielts-actions');
  actions?.insertAdjacentHTML('afterbegin',button('Thử Task 1 · biểu đồ','task1-sample')+(model.demoFixture==='task1'?button('Về bài trước','task1-restore'):''));
 }
 if(model.screen==='grading'&&model.submission?.tasks.includes(1)&&model.task===1&&model.submission.variant==='academic'){
  const summary=$('.inline-review-document .prompt-summary')||$('.grade-response .prompt-summary');
  summary?.insertAdjacentHTML('afterend',`<details class="task1-reference" open><summary>Đề bài & biểu đồ Task 1 · dữ liệu mẫu</summary><div class="task1-reference-layout"><div><p>The chart shows the percentages of commuters using three forms of transport in a city in 2000 and 2020.</p><p><strong>Summarise the information by selecting and reporting the main features, and make comparisons where relevant.</strong></p><small>Đề và bài mẫu tự biên soạn; không phải đề thi hoặc bài band được chứng nhận.</small></div>${chart()}</div></details>`);
  requestAnimationFrame(positionCommentCards);
 }
};
document.addEventListener('click',e=>{const action=e.target.closest('[data-action]')?.dataset.action;if(action==='task1-sample')loadTask1Sample();if(action==='task1-restore')restorePreviousDemo();});
if(new URLSearchParams(location.hash.slice(1)).get('sample')==='task1')loadTask1Sample();else render();
