"use client";
import {useEffect,useState} from 'react';
import {useWritingApi} from '../../lib/writing/context';
import {catalog as defaults,type Profile} from '../../lib/writing/types';
import {errorText} from '../../lib/parity';
type Settings={revision:number;catalog:Record<string,Profile>};
export default function ProjectRubrics({editable}:{editable:boolean}){
 const {json,project}=useWritingApi();
 const [value,setValue]=useState<Settings|null>(null),[profile,setProfile]=useState('bar'),[error,setError]=useState(''),[notice,setNotice]=useState(''),[busy,setBusy]=useState(false);
 useEffect(()=>{let stopped=false;setValue(null);void json<Settings>('/writing/settings').then(v=>{if(!stopped)setValue(v);}).catch(e=>{if(!stopped)setError(errorText(e));});return()=>{stopped=true;};},[project]);
 function change(section:string,index:number,text:string|null){
  if(!value)return;const next=structuredClone(value);const list=next.catalog[profile].checks[section];
  if(text===null)list.splice(index,1);else list[index].text=text;setValue(next);setNotice('Chưa lưu');
 }
 async function save(){if(!value)return;setBusy(true);setError('');try{setValue(await json<Settings>('/writing/settings',{method:'PUT',body:JSON.stringify({revision:value.revision,checks:Object.fromEntries(Object.entries(value.catalog).map(([key,p])=>[key,p.checks]))})}));setNotice('Đã lưu cấu hình theo dự án. Áp dụng cho bài tập tạo/cập nhật sau này; bản nộp cũ giữ tiêu chí ban đầu.');}catch(e){setError(errorText(e));}finally{setBusy(false);}}
 return <section className="writing-card writing-project-rubrics"><h2>Tiêu chí chấm của dự án</h2>
 <p>Tutor có thể thêm, sửa, xóa mục checklist theo từng phần. Bốn tiêu chí band IELTS và cách tính trọng số vẫn được giữ; checklist không tự quy đổi thành band. Cấu hình không thuộc tài khoản cá nhân.</p>
 {error&&<p role="alert">{error}</p>}{notice&&<p role="status">{notice}</p>}
 <label>Dạng bài<select aria-label="Dạng bài cấu hình" value={profile} onChange={e=>setProfile(e.target.value)}>{Object.entries(defaults).map(([key,p])=><option key={key} value={key}>Task {p.task} · {p.label}</option>)}</select></label>
 {value&&Object.entries(value.catalog[profile].checks).map(([section,checks])=><fieldset key={section} disabled={!editable||busy}><legend>{section}</legend>
 {checks.map((check,index)=><div key={check.id} className="writing-rubric-edit-row"><textarea aria-label={`Tiêu chí ${section} ${index+1}`} value={check.text} onChange={e=>change(section,index,e.target.value)}/><span>{check.codes.join(' / ')}</span>{editable&&<button type="button" onClick={()=>change(section,index,null)}>Xóa tiêu chí</button>}</div>)}
 {editable&&<button type="button" onClick={()=>{const next=structuredClone(value);next.catalog[profile].checks[section].push({id:'custom_'+crypto.randomUUID(),text:'Tiêu chí mới',codes:[value.catalog[profile].task===1?'TA':'TR']});setValue(next);setNotice('Chưa lưu');}}>Thêm tiêu chí</button>}
 </fieldset>)}
 {editable&&<button className="primary" disabled={!value||busy} onClick={()=>void save()}>Lưu tiêu chí dự án</button>}
 </section>;
}
