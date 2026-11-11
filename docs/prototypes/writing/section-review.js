/* Paragraph/section evidence stays separate from IELTS band calculation. */
const reviewStatuses={unreviewed:'Chưa xem',met:'Đạt',partial:'Một phần',missing:'Cần sửa',na:'Không áp dụng'};
const paragraphTexts=t=>(model.submission?.answers[t]||'').split(/\n\s*\n/).map(x=>x.trim()).filter(Boolean);
const submissionScope=t=>`${model.submission?.attempt||0}|${model.submission?.at||''}|${t}`;
function defaultProfile(t){return t===2?'discussion':model.submission?.variant==='general'?'formal':'bar'}
function currentProfile(t=model.task){return model.sectionProfiles?.[submissionScope(t)]||defaultProfile(t)}
function reviewKey(t=model.task){return `${submissionScope(t)}|${currentProfile(t)}`}
function reviewData(t=model.task){
 model.sectionReviews ||= {};model.sectionProfiles ||= {};
 const key=reviewKey(t);if(model.sectionReviews[key])return model.sectionReviews[key];
 const profile=IELTS_PROFILES[currentProfile(t)],n=paragraphTexts(t).length,letter=profile.family==='general';
 const parts=[{id:'intro',kind:'intro',title:letter?'Opening / mục đích thư':'Introduction',paragraphs:n?[0]:[]}];
 if(t===1&&!letter)parts.push({id:'overview',kind:'overview',title:'Overview',paragraphs:n>=4?[1]:[]});
 const first=t===1&&!letter&&n>=4?2:1;
 const last=t===2&&n>=4?n-1:letter&&n>=4?n-1:n;
 const bodyN=Math.max(2,last-first);
 for(let i=0;i<bodyN;i++)parts.push({id:'body-'+(i+1),kind:'body',title:'Body '+(i+1),paragraphs:first+i<last?[first+i]:[]});
 if(t===2)parts.push({id:'conclusion',kind:'conclusion',title:'Conclusion',paragraphs:n>=4?[n-1]:[]});
 if(letter)parts.push({id:'closing',kind:'closing',title:'Closing / kết thư',paragraphs:n>=4?[n-1]:[]});
 parts.push({id:'whole',kind:'whole',title:'Toàn bài / yêu cầu đề',paragraphs:[]});
 const value={catalogVersion:RUBRIC_CATALOG_VERSION,profile:currentProfile(t),parts,active:'intro',items:{},notes:{},evidence:{},purpose:'complaint'};
 model.sectionReviews[key]=value;return value;
}
function reviewItems(r,part){
 const list=sectionChecks(r.profile,part.kind);
 if(IELTS_PROFILES[r.profile].family==='general'&&['body','whole'].includes(part.kind)){
  const goals={complaint:'Mô tả vấn đề và mong muốn xử lý theo đề, tránh công kích người nhận.',request:'Nêu rõ thông tin/hành động muốn được đáp ứng và bối cảnh cần thiết.',apology:'Giải thích, nhận trách nhiệm phù hợp và đề xuất cách khắc phục nếu đề yêu cầu.',invitation:'Nêu lời mời và thông tin cần để người nhận phản hồi.',thanks:'Lời cảm ơn gắn việc cụ thể và ý nghĩa đối với người viết.',advice:'Đề nghị/khuyên phù hợp tình huống và giải thích lý do.',application:'Nêu mục đích ứng tuyển và thông tin phù hợp vị trí/yêu cầu đề.',other:'Tutor ghi lại chức năng thư hoặc yêu cầu kết hợp cần kiểm.'};
  list.push(ci('purpose-function',goals[r.purpose],'TA'));
 }
 return list;
}
const reviewItemKey=(part,item)=>`${part.id}:${item.id}`;
function selectedPart(){const r=reviewData();return r.parts.find(p=>p.id===r.active)||r.parts[0]}
function progress(r){const all=r.parts.flatMap(p=>reviewItems(r,p).map(c=>r.items[reviewItemKey(p,c)]||'unreviewed'));return {reviewed:all.filter(s=>s!=='unreviewed').length,met:all.filter(s=>s==='met').length,total:all.length}}
function checklistPane(){
 const r=reviewData(),p=selectedPart(),profile=IELTS_PROFILES[r.profile],para=paragraphTexts(model.task),n=progress(r);
 return `<header class="section-review-header"><div><strong>Góp ý theo phần</strong><small>Tutor · checklist có minh chứng</small></div><span>${n.reviewed}/${n.total} đã xem</span></header><div class="section-review-controls"><label>Dạng bài<select id="review-profile" aria-label="Dạng bài chấm chi tiết">${profileOptions(model.task,model.submission.variant).map(([id,c])=>`<option value="${id}" ${id===r.profile?'selected':''}>${c.label}</option>`).join('')}</select></label>${profile.family==='general'?`<label>Mục đích thư<select id="letter-purpose" aria-label="Mục đích thư">${Object.entries(LETTER_PURPOSES).map(([id,label])=>`<option value="${id}" ${r.purpose===id?'selected':''}>${label}</option>`).join('')}</select></label>`:''}<p>Dạng bài do Tutor đối chiếu đề thật. Đây là checklist hướng dẫn, không phải bảng quy đổi band.</p><div class="section-part-tabs">${r.parts.map(part=>`<button class="${r.active===part.id?'active':''}" data-review-part="${part.id}">${part.title}</button>`).join('')}</div></div><div class="section-chat-scroll"><article class="review-message"><div class="review-message-author"><span>T</span><div><strong>${p.title}</strong><small>Ghi nhận từng điểm trong phần đang đọc</small></div></div><details class="paragraph-assignment"><summary>Gán đoạn minh chứng (${p.paragraphs.length} đoạn)</summary><p>Phân chia dưới đây chỉ là gợi ý. Một đoạn có thể phục vụ nhiều phần; Overview không buộc nằm ở đoạn 2.</p>${para.map((text,i)=>`<label><input type="checkbox" data-assign-paragraph="${i}" ${p.paragraphs.includes(i)?'checked':''}> Đoạn ${i+1}: ${escape(text.slice(0,55))}…</label>`).join('')||'<p>Không có đoạn để gán.</p>'}</details>${p.paragraphs.length?`<blockquote>${escape(p.paragraphs.map(i=>para[i]).join('\n\n').slice(0,450))}</blockquote>`:p.kind==='whole'?'<p class="section-mini-note">Đối chiếu toàn bài và từng yêu cầu trong đề.</p>':'<p class="section-mini-note warning">Chưa gán đoạn cho phần này. Không tự kết luận học viên thiếu phần; hãy kiểm lại nội dung.</p>'}<div class="section-checklist">${reviewItems(r,p).map(item=>{const key=reviewItemKey(p,item),status=r.items[key]||'unreviewed';return `<div class="section-check-row ${status}"><label><input type="checkbox" data-review-check="${item.id}" ${status==='met'?'checked':''}><span>${escape(item.text)}</span></label><div class="section-check-meta"><div>${item.codes.map(c=>`<button data-criterion-jump="${c}" title="Xem tiêu chí ${c}">${c}</button>`).join('')}</div><select aria-label="Trạng thái ${escape(item.id)}" data-review-status="${item.id}">${Object.entries(reviewStatuses).map(([id,label])=>`<option value="${id}" ${status===id?'selected':''}>${label}</option>`).join('')}</select></div></div>`}).join('')}</div><label class="section-feedback-label">Nhận xét cho ${p.title}<textarea id="section-feedback" placeholder="Điểm mạnh, phần cần sửa và cách cải thiện…">${escape(r.notes[p.id]||'')}</textarea></label><div class="section-evidence"><button data-action="review-quote">＋ Gắn trích đoạn đang chọn</button>${r.evidence[p.id]?`<blockquote>${escape(r.evidence[p.id])}</blockquote>`:'<small>Chọn một đoạn/câu ở bài viết rồi bấm gắn minh chứng.</small>'}</div></article></div><footer class="section-review-footer"><button data-action="review-add-body">＋ Thêm phần Body</button><small>Dấu tích giúp ghi nhận; band vẫn do Tutor đánh giá ở khung cuối.</small></footer>`;
}
function renderReviewOnly(){const pane=$('#section-review-pane');if(!pane)return;const oldTop=pane.querySelector('.section-chat-scroll')?.scrollTop||0;pane.innerHTML=checklistPane();const scroll=pane.querySelector('.section-chat-scroll');scroll.scrollTop=oldTop;highlightParagraphs();}
function highlightParagraphs(){const part=selectedPart();document.querySelectorAll('[data-review-paragraph]').forEach(el=>el.classList.toggle('selected-review-paragraph',part.paragraphs.includes(+el.dataset.reviewParagraph)));}
const detailedBaseRender=render;
render=function(){
 detailedBaseRender();
 if(model.screen==='grading'&&model.submission){
  const layout=$('.ielts-grade-layout');layout.classList.add('detailed-grading');
  const pane=document.createElement('aside');pane.id='section-review-pane';pane.className='section-review-pane';layout.insertBefore(pane,$('.assessment-pane'));pane.innerHTML=checklistPane();
  const r=reviewData(),reading=$('.grade-response .reading-answer');
  if(reading){reading.innerHTML=paragraphTexts(model.task).map((text,i)=>`<section class="review-paragraph" data-review-paragraph="${i}" tabindex="0"><button data-scroll-paragraph="${i}" title="Xem phần gắn với đoạn ${i+1}">Đoạn ${i+1}</button><p>${escape(text)}</p></section>`).join('');}
  $('.assessment-pane h2').insertAdjacentHTML('afterend',`<p class="band-evidence-note">Tổng kết sau khi đọc Introduction/Body và các phần khác. Checklist không tự tạo band.</p>`);
  highlightParagraphs();
 }
 if(model.screen==='results'&&model.published?.sectionReviews){
  const rows=Object.values(model.published.sectionReviews).filter(r=>Object.values(r.notes).some(Boolean));
  if(rows.length){$('.ielts-page').insertAdjacentHTML('beforeend',`<section class="published-section-feedback"><h2>Phản hồi chi tiết theo phần</h2>${rows.map(r=>`<h3>Task ${r.task} · ${escape(IELTS_PROFILES[r.profile].label)}</h3>${r.parts.filter(p=>r.notes[p.id]).map(p=>`<article><strong>${escape(p.title)}</strong><p>${escape(r.notes[p.id])}</p></article>`).join('')}`).join('')}</section>`);}
 }
};
function writeStatus(itemId,status){const r=reviewData(),p=selectedPart();r.items[`${p.id}:${itemId}`]=status;persist();renderReviewOnly();}
document.addEventListener('change',e=>{
 const el=e.target;if(!$('#section-review-pane'))return;
 if(el.id==='review-profile'){model.sectionProfiles[submissionScope(model.task)]=el.value;persist();render();}
 if(el.id==='letter-purpose'){reviewData().purpose=el.value;persist();renderReviewOnly();}
 if(el.dataset.reviewCheck)writeStatus(el.dataset.reviewCheck,el.checked?'met':'unreviewed');
 if(el.dataset.reviewStatus)writeStatus(el.dataset.reviewStatus,el.value);
 if(el.dataset.assignParagraph!==undefined){const p=selectedPart(),i=+el.dataset.assignParagraph;p.paragraphs=el.checked?[...new Set([...p.paragraphs,i])].sort((a,b)=>a-b):p.paragraphs.filter(n=>n!==i);persist();renderReviewOnly();}
});
document.addEventListener('input',e=>{if(e.target.id==='section-feedback'){const r=reviewData();r.notes[r.active]=e.target.value;persist();}});
document.addEventListener('click',e=>{
 const el=e.target.closest('button');if(!el)return;
 if(el.dataset.reviewPart){const r=reviewData();r.active=el.dataset.reviewPart;persist();renderReviewOnly();const i=selectedPart().paragraphs[0];if(i!==undefined)$(`[data-review-paragraph="${i}"]`)?.scrollIntoView({block:'nearest',behavior:'smooth'});}
 if(el.dataset.scrollParagraph!==undefined){const r=reviewData(),i=+el.dataset.scrollParagraph,part=r.parts.find(p=>p.paragraphs.includes(i));if(part){r.active=part.id;persist();renderReviewOnly();}}
 if(el.dataset.criterionJump){const select=document.querySelector(`[aria-label="Band ${el.dataset.criterionJump}"]`);select?.scrollIntoView({block:'nearest'});select?.focus();}
 if(el.dataset.action==='review-quote'){const selection=window.getSelection(),anchor=selection?.anchorNode,focus=selection?.focusNode,root=$('.grade-response'),quote=selection?.toString().trim();if(!quote||!root.contains(anchor)||!root.contains(focus)){toast('Hãy chọn câu hoặc đoạn trong bản nộp trước.');return;}const r=reviewData();r.evidence[r.active]=quote.slice(0,2000);persist();renderReviewOnly();}
 if(el.dataset.action==='review-add-body'){const r=reviewData(),n=r.parts.filter(p=>p.kind==='body').length+1,id='body-'+n,part={id,kind:'body',title:'Body '+n,paragraphs:[]};const index=r.parts.findIndex(p=>['conclusion','closing','whole'].includes(p.kind));r.parts.splice(index<0?r.parts.length:index,0,part);r.active=id;persist();renderReviewOnly();}
 if(el.dataset.action==='publish'&&model.published){const reviews={};for(const task of model.published.tasks){const r=reviewData(task);reviews[task]={...JSON.parse(JSON.stringify(r)),task};}model.published.sectionReviews=reviews;persist();render();}
});
const baseReportTex=reportTex;
reportTex=function(){let source=baseReportTex();if(!model.submission)return source;const parts=[];for(const task of model.published?.tasks||model.submission.tasks){const r=model.published?.sectionReviews?.[task]||reviewData(task);for(const part of r.parts){const reviewed=reviewItems(r,part).filter(c=>r.items[reviewItemKey(part,c)]&&r.items[reviewItemKey(part,c)]!=='unreviewed');if(!reviewed.length&&!r.notes[part.id])continue;parts.push(`\\subsection*{Task ${task} -- ${texEscape(part.title)}}\n${texEscape(r.notes[part.id]||'')}\n${reviewed.length?'\\begin{itemize}\n'+reviewed.map(c=>'\\item '+texEscape(reviewStatuses[r.items[reviewItemKey(part,c)]]+': '+c.text)).join('\n')+'\n\\end{itemize}':''}`);}}if(parts.length)source=source.replace('\\end{document}','\\section*{Góp ý theo từng phần}\n'+parts.join('\n')+'\n\\end{document}');return source;};
render();
