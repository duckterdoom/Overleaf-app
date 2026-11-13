"use client";
import {type Profile} from '../../lib/writing/types';
import {Plus,Trash2} from 'lucide-react';

export default function AssignmentRubric({value,scope,onChange}:{value:Profile;scope:string;onChange:(value:Profile)=>void}){
 function edit(fn:(next:Profile)=>void){const next=structuredClone(value);fn(next);onChange(next);}
 return <details className="assignment-rubric" open><summary>Customize this exercise’s criteria <small>Changes apply only to this exercise</small></summary>
 {Object.entries(value.checks).filter(([key])=>scope==='whole'||key===scope).map(([section,checks])=><section key={section}>
 <header><input aria-label={`Section title ${section}`} value={value.sectionTitles?.[section]||section} onChange={e=>edit(p=>{p.sectionTitles={...p.sectionTitles,[section]:e.target.value};})}/>
 <button type="button" aria-label={`Remove section ${section}`} onClick={()=>edit(p=>{delete p.checks[section];})}><Trash2 size={15}/></button></header>
 {checks.map((check,i)=><div className="exercise-criterion" key={check.id}><span>{i+1}</span><textarea aria-label={`Criterion ${section} ${i+1}`} value={check.text} maxLength={1000} required onChange={e=>edit(p=>{p.checks[section][i].text=e.target.value;})}/>
 {(value.category||'ielts')==='ielts'&&<select aria-label={`Criterion code ${section} ${i+1}`} value={check.codes.join(',')} onChange={e=>edit(p=>{p.checks[section][i].codes=e.target.value.split(',');})}>{[...new Set([check.codes.join(','),value.task===1?'TA':'TR','CC','LR','GRA'])].map(code=><option key={code}>{code}</option>)}</select>}
 <button type="button" aria-label={`Remove criterion ${section} ${i+1}`} onClick={()=>edit(p=>{p.checks[section].splice(i,1);})}><Trash2 size={15}/></button></div>)}
 <button type="button" onClick={()=>edit(p=>{p.checks[section].push({id:'check_'+crypto.randomUUID(),text:'New criterion',codes:[(p.category||'ielts')==='ielts'?(p.task===1?'TA':'TR'):'CONTENT']});})}><Plus size={14}/>Add criterion</button></section>)}
 {scope!=='whole'&&!value.checks[scope]&&<button type="button" onClick={()=>edit(p=>{p.checks[scope]=[];})}>Add {scope} criteria</button>}
 {scope==='whole'&&<button type="button" onClick={()=>{const id='section_'+crypto.randomUUID().slice(0,10);edit(p=>{p.checks[id]=[];p.sectionTitles={...p.sectionTitles,[id]:'New section'};});}}>Add assessment section</button>}
 </details>;
}
