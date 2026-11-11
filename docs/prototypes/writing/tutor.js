/* Tutor perspective for the standalone design, not production authorization. */
labels.tutor='Không gian Tutor';
const tutorRender=render,tutorSidebar=sidebar;
sidebar=function(){
  if(model.role!=='teacher'){tutorSidebar();return;}
  const links=[['tutor','graduation','Tổng quan Tutor'],['create','file','Giao bài IELTS'],['submissions','history','Bài nộp & chấm'],['rubric','review','Tiêu chí IELTS']];
  $('#sidebar').innerHTML=`<div class="sidebar-heading">${icon('graduation')}Tutor · IELTS Writing</div><div class="side-title">Nguyễn Minh · Tutor<br><small>Lớp IELTS · W01</small></div>${links.map(([id,g,text])=>`<button class="side-link ${model.screen===id?'active':''}" data-route="${id}">${icon(g)}${text}</button>`).join('')}<div class="sidebar-outline"><p class="eyebrow">THAO TÁC CHẤM</p><button data-route="submissions">1. Chọn bài của học viên</button><button data-action="tutor-sample">2. Đọc và nhận xét theo tiêu chí</button><button data-action="tutor-sample">3. Lưu nháp → công bố</button></div><div class="side-note"><strong>Góc nhìn Tutor</strong>Đề bài · học viên · band & phản hồi. Học viên không xem được bản chấm nháp.</div>`;
};
function tutorOverview(){
  return header('Không gian Tutor','Quản lý bài IELTS, đọc bài học viên và đưa phản hồi có hướng dẫn.',button('Giao bài IELTS','create','primary'))+`<div class="ielts-page"><div class="tutor-stats"><section><small>Học viên demo</small><strong>01</strong><span>Lớp W01 · Trần An</span></section><section><small>Chờ chấm</small><strong>${model.submission&&!model.published?'01':'00'}</strong><span>Bản nộp trong prototype</span></section><section><small>Đã trả phản hồi</small><strong>${model.published?'01':'00'}</strong><span>Chỉ tính phiếu đã công bố</span></section></div><div class="card table-card"><div class="table-head"><h3>Học viên và bài viết</h3>${badge('IELTS Writing')}</div><table><thead><tr><th>Học viên</th><th>Bài được giao</th><th>Trạng thái</th><th>Thao tác</th></tr></thead><tbody><tr><td><strong>Trần An</strong><small>Lớp W01 · cá nhân</small></td><td>${escape(model.title)}<br><small>${model.submission?model.submission.tasks.map(t=>'Task '+t).join(' + '):'Chưa có bản nộp'}</small></td><td>${badge(model.published?'Đã trả phản hồi':model.submission?'Chờ chấm':'Đang làm',model.submission?'':'amber')}</td><td>${button(model.submission?'Mở bài chấm':'Mở bài chấm mẫu','tutor-sample','primary')}</td></tr></tbody></table></div><div class="tutor-guide"><section class="card"><h2>Quy trình của Tutor</h2><ol><li>Giao đề Task 1 hoặc Task 2 cho học viên.</li><li>Đọc đúng bản đã nộp, không sửa bài gốc.</li><li>Chọn band TA/TR, CC, LR, GRA và nhận xét.</li><li>Lưu nháp để xem lại; công bố khi đã hoàn tất.</li></ol></section><section class="card"><h2>Thử màn chấm ngay</h2><p class="subtle">Nếu chưa có bài nộp, có thể tạo một bài mẫu do prototype cung cấp. Thao tác này không thay nội dung học viên đang gõ trong working copy.</p>${button('Mở bài chấm mẫu','tutor-sample','secondary')}<p class="note">Vai trò ở footer chỉ để xem thiết kế, không phải quyền tài khoản thật.</p></section></div></div>`;
}
render=function(){
  const overview=model.role==='teacher'&&model.screen==='tutor';
  tutorRender();
  $('#role option[value="teacher"]').textContent='Tutor (Giảng viên)';
  if(model.role==='teacher'){
    $('#avatar').textContent='TU';
    $('#screen-map').innerHTML=['tutor','create','submissions','grading','rubric'].map(s=>`<option value="${s}" ${s===model.screen?'selected':''}>${labels[s]}</option>`).join('');
  }
  if(overview){$('#main').innerHTML=tutorOverview();$('#screen-label').textContent='IELTS Writing · Góc nhìn Tutor';}
};
document.addEventListener('change',e=>{if(e.target.id==='role'&&e.target.value==='teacher')go('tutor')});
document.addEventListener('click',e=>{
  const action=e.target.closest('[data-action]')?.dataset.action;
  if(action==='tutor-sample'){
    if(model.submission){model.task=model.submission.tasks[0];go('grading');return;}
    openModal('Mở bài chấm mẫu cho Tutor?','<p>Tạo snapshot Task 2 với bài essay tự biên soạn để bạn xem màn chấm. Điểm ban đầu để trống; không có chấm AI.</p><p>Không ghi đè working copy của học viên. Không gửi dữ liệu lên server.</p>','tutor-seed','Mở bài mẫu');
  }
  if(action==='tutor-seed'){
    const answer=`Traffic congestion is a major problem in many growing cities. Some people believe that better public transport is the most effective response, while others argue that additional roads are necessary. In my view, cities should prioritise reliable public transport while making targeted improvements to existing roads.

New roads can help where the transport network has specific weaknesses. For example, a bypass may direct delivery vehicles away from residential streets and reduce delays around a town centre. Such improvements can also support businesses that depend on moving goods. However, road expansion alone is unlikely to provide a lasting solution. If driving becomes easier, more residents may choose to use private cars, which can eventually fill the additional space.

Public transport can address congestion by moving more passengers in fewer vehicles. Frequent buses and well-connected train services make it possible for commuters to leave their cars at home. Affordable tickets are also important because they allow people with lower incomes to reach jobs and education. Nevertheless, these advantages depend on the quality of the service. Passengers will not change their travel habits if vehicles are consistently late or connections are inconvenient.

I therefore believe that the most useful investment is a coordinated public transport network with regular services, safe stops and simple ticketing. Road projects should support that network rather than compete with it. For instance, dedicated bus lanes can improve journey times without encouraging every traveller to drive. Overall, both approaches have a role, but public transport should receive greater priority because it improves access for a wider range of residents and uses limited urban space more efficiently.`;
    model.attempt++;model.submission={tasks:[2],answers:{1:'',2:answer},variant:'academic',attempt:model.attempt,at:new Date().toLocaleString('vi-VN'),demo:true};
    model.scores={1:[null,null,null,null],2:[null,null,null,null]};model.notes={1:['','','',''],2:['','','','']};model.summary='';model.published=null;model.task=2;persist();$('#modal').close();go('grading');
  }
});
const route=new URLSearchParams(location.hash.slice(1));
if(route.get('role')==='tutor'){model.role='teacher';model.screen='tutor';persist();}
render();
