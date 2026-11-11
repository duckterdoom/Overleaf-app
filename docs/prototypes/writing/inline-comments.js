/* Paragraph-anchored review cards; shared rubric and published data stay unchanged. */
const commentUi={open:new Map(),bands:false,options:false};
let commentObserver;
const openComment=()=>commentUi.open.get(reviewKey())||null;
const highlightBeforeComments=highlightParagraphs;
highlightParagraphs=function(){if(!openComment()){document.querySelectorAll('.selected-review-paragraph').forEach(el=>el.classList.remove('selected-review-paragraph'));return;}highlightBeforeComments();};
function partProgress(r,p){const items=reviewItems(r,p);return {done:items.filter(c=>r.items[reviewItemKey(p,c)]&&r.items[reviewItemKey(p,c)]!=='unreviewed').length,total:items.length};}
function cardMarkup(part){
 const r=reviewData(),open=openComment()===part.id,stats=partProgress(r,part),note=r.notes[part.id]||'';
 let content='';
 if(open){
  r.active=part.id;
  const temp=document.createElement('div');temp.innerHTML=checklistPane();
  const message=temp.querySelector('.review-message');
  message.querySelector('.review-message-author').remove();
  // The essay is already next to the card; do not repeat a full paragraph here.
  message.querySelector(':scope > blockquote')?.remove();
  content=`<div class="inline-card-body">${message.innerHTML}<div class="inline-card-actions">${button('Thu gọn','comment-collapse')}</div></div>`;
 }
 return `<article class="paragraph-comment-card ${open?'is-open':''}" data-comment-id="${part.id}" aria-label="Nhận xét ${escape(part.title)}"><button class="comment-card-heading" data-review-part="${part.id}" aria-expanded="${open}"><span class="comment-author-avatar">NM</span><span><strong>${escape(part.title)}</strong><small>Nguyễn Minh · Tutor</small></span><span class="comment-count">${stats.done}/${stats.total}<span>${open?'⌃':'⌄'}</span></span></button>${open?content:`<div class="comment-card-preview">${note?escape(note.slice(0,100)):'Tích tiêu chí và góp ý cho phần này'}${note.length>100?'…':''}</div>`}</article>`;
}
function positionCommentCards(){
 const stage=$('.inline-review-canvas'),lane=$('.paragraph-comment-lane'),doc=$('.inline-review-document');if(!stage||!lane||!doc)return;
 if(matchMedia('(max-width: 900px)').matches){lane.style.height='auto';lane.querySelectorAll('[data-comment-id]').forEach(card=>card.style.top='');return;}
 const r=reviewData(),top=stage.getBoundingClientRect().top;let bottom=0;
 for(const part of r.parts){
  const card=lane.querySelector(`[data-comment-id="${part.id}"]`);if(!card)continue;
  const first=part.paragraphs[0],anchor=first===undefined?null:doc.querySelector(`[data-review-paragraph="${first}"]`);
  const wanted=anchor?anchor.getBoundingClientRect().top-top:Math.max(24,doc.scrollHeight-70);
  const y=Math.max(16,wanted,bottom);card.style.top=y+'px';bottom=y+card.offsetHeight+12;
 }
 lane.style.height=Math.max(doc.scrollHeight,bottom+20)+'px';
}
function installInlineComments(){
 if(model.screen!=='grading'||!model.submission)return;
 const layout=$('.ielts-grade-layout'),response=$('.grade-response');
 layout.classList.add('inline-grading');
 $('#section-review-pane')?.remove();
 const r=reviewData(),raw=document.createElement('div');raw.innerHTML=checklistPane();
 const controls=raw.querySelector('.section-review-controls');controls.querySelector('.section-part-tabs')?.remove();
 const toolbar=document.createElement('div');toolbar.className='inline-review-tools';
 toolbar.innerHTML=`<div><strong>Góp ý ngay bên đoạn viết</strong><small>Bấm đoạn hoặc thẻ bên lề để chấm chi tiết</small></div><button data-action="comment-options" aria-expanded="${commentUi.options}">Dạng bài & phân phần</button><button data-action="comment-whole">Nhận xét toàn bài</button><button class="primary" data-action="comment-bands" aria-expanded="${commentUi.bands}">Tổng kết band</button>`;
 const options=document.createElement('div');options.className='inline-review-options';options.hidden=!commentUi.options;options.append(controls);
 options.insertAdjacentHTML('beforeend',button('＋ Thêm phần Body','review-add-body'));
 const canvas=document.createElement('div');canvas.id='section-review-pane';canvas.className='inline-review-canvas';
 const doc=document.createElement('div');doc.className='inline-review-document';
 while(response.firstChild)doc.append(response.firstChild);
 const lane=document.createElement('aside');lane.className='paragraph-comment-lane';lane.setAttribute('aria-label','Nhận xét theo từng đoạn');
 lane.innerHTML=r.parts.map(cardMarkup).join('');canvas.append(doc,lane);
 response.append(toolbar,options,canvas);
 const active=openComment();r.active=active||'__none__';
 doc.querySelectorAll('[data-review-paragraph]').forEach(el=>{
  const index=+el.dataset.reviewParagraph,part=r.parts.find(p=>p.kind!=='whole'&&p.paragraphs.includes(index));
  const label=el.querySelector('[data-scroll-paragraph]');
  if(label&&part){label.textContent=part.title+' · Đoạn '+(index+1);delete label.dataset.scrollParagraph;label.dataset.inlinePart=part.id;}
  if(part)el.dataset.inlinePart=part.id;
  const text=el.querySelector('p');if(text)text.innerHTML=`<span class="review-highlight">${text.innerHTML}</span>`;
 });
 highlightParagraphs();
 const bands=$('.assessment-pane');bands.classList.add('floating-band-panel');bands.hidden=!commentUi.bands;
 bands.insertAdjacentHTML('afterbegin',`<div class="floating-band-heading"><strong>Tổng kết IELTS</strong><button data-action="comment-close-bands" aria-label="Đóng tổng kết band">×</button></div>`);
 commentObserver?.disconnect();
 commentObserver=new ResizeObserver(()=>requestAnimationFrame(positionCommentCards));
 commentObserver.observe(doc);lane.querySelectorAll('.paragraph-comment-card').forEach(card=>commentObserver.observe(card));
 requestAnimationFrame(positionCommentCards);
}
function refreshInlineCards(){
 const lane=$('.paragraph-comment-lane');if(!lane)return;
 const r=reviewData();lane.innerHTML=r.parts.map(cardMarkup).join('');
 r.active=openComment()||'__none__';highlightParagraphs();
 commentObserver?.disconnect();const doc=$('.inline-review-document');if(doc)commentObserver?.observe(doc);lane.querySelectorAll('.paragraph-comment-card').forEach(card=>commentObserver?.observe(card));
 requestAnimationFrame(positionCommentCards);
}
// All existing checkbox/status/mapping callbacks use this presentation hook.
renderReviewOnly=refreshInlineCards;
const beforeInlineRender=render;
render=function(){commentObserver?.disconnect();if(model.screen!=='grading')commentUi.bands=false;beforeInlineRender();installInlineComments();};
function showInlinePart(id){commentUi.open.set(reviewKey(),id);reviewData().active=id;persist();refreshInlineCards();}
function setBands(open){commentUi.bands=open;const pane=$('.floating-band-panel'),restore=!!pane?.contains(document.activeElement);if(pane)pane.hidden=!open;const trigger=$('[data-action="comment-bands"]');trigger?.setAttribute('aria-expanded',String(open));if(!open&&restore)trigger?.focus();}
document.addEventListener('click',e=>{const button=e.target.closest('.paragraph-comment-card [data-review-part]');if(!button)return;e.preventDefault();e.stopImmediatePropagation();const id=button.dataset.reviewPart;commentUi.open.set(reviewKey(),openComment()===id?null:id);reviewData().active=openComment()||'__none__';persist();refreshInlineCards();},true);
document.addEventListener('click',e=>{
 const target=e.target.closest('button,[data-inline-part]');if(!target||model.screen!=='grading'||!model.submission)return;
 if(target.dataset.reviewPart){const id=target.dataset.reviewPart;commentUi.open.set(reviewKey(),openComment()===id?null:id);refreshInlineCards();return;}
 if(target.dataset.inlinePart&&!window.getSelection()?.toString().trim()){showInlinePart(target.dataset.inlinePart);return;}
 const action=target.dataset.action;
 if(action==='comment-collapse'){commentUi.open.delete(reviewKey());refreshInlineCards();}
 if(action==='comment-whole'){showInlinePart('whole');requestAnimationFrame(()=>document.querySelector('[data-comment-id="whole"]')?.scrollIntoView({block:'start',behavior:'smooth'}));}
 if(action==='comment-options'){commentUi.options=!commentUi.options;$('.inline-review-options').hidden=!commentUi.options;target.setAttribute('aria-expanded',String(commentUi.options));}
 if(action==='comment-bands')setBands(!commentUi.bands);
 if(action==='comment-close-bands')setBands(false);
 if(action==='review-add-body'){showInlinePart(reviewData().parts.filter(p=>p.kind==='body').at(-1).id);render();}
 if(target.dataset.criterionJump){setBands(true);document.querySelector(`[aria-label="Band ${target.dataset.criterionJump}"]`)?.focus();}
});
document.addEventListener('keydown',e=>{if(e.key==='Escape'&&commentUi.bands)setBands(false)});
render();
