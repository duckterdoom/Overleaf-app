"use client";
import {useEffect,useRef,useState} from 'react';
import {createPortal} from 'react-dom';
import {Plus,Settings2,X} from 'lucide-react';
import type {Profile} from '../../lib/writing/types';

const names:Record<string,string>={intro:'Introduction',body:'Body',overview:'Overview',conclusion:'Conclusion',whole:'Whole-paper requirements',closing:'Closing'};
export default function RubricCategories({task,value,selected,allocations,onSelect,onChange}:{task:number;value:Profile;selected?:string[];allocations:Record<string,number>;onSelect:(keys:string[]|undefined)=>void;onChange:(profile:Profile)=>void}){
 const [editing,setEditing]=useState<string|null>(null),dialog=useRef<HTMLDialogElement>(null);
 useEffect(()=>{if(editing!==null)dialog.current?.showModal();else dialog.current?.close();},[editing]);
 const title=(key:string)=>value.sectionTitles?.[key]||names[key]||key;
 function edit(fn:(next:Profile)=>void){const next=structuredClone(value);fn(next);onChange(next);}
 const sections=Object.entries(value.checks),whole=selected===undefined;
 return <section className="rubric-category-selection" aria-label={`Assessment categories Task ${task}`}>
  <div className="rubric-category-heading"><strong>Assessment categories</strong><small>{whole?'All categories included':`${selected.length} categories selected`}</small></div>
  <div className="rubric-category-chips">
   {whole?<div className="rubric-category-chip active"><button type="button" onClick={()=>setEditing('*')}><Settings2 size={14}/>Whole paper</button><button type="button" aria-label={`Remove whole paper Task ${task}`} onClick={()=>onSelect([])}><X size={14}/></button></div>:
    selected.map(key=><div className="rubric-category-chip active" key={key}><button type="button" onClick={()=>setEditing(key)}>{title(key)}</button><button type="button" aria-label={`Remove category ${key} Task ${task}`} onClick={()=>onSelect(selected.filter(k=>k!==key))}><X size={14}/></button></div>)}
  </div>
  {!whole&&<div className="rubric-category-options"><button type="button" onClick={()=>onSelect(undefined)}>Use whole paper</button>{sections.filter(([key])=>!selected.includes(key)).map(([key,checks])=><button type="button" key={key} disabled={!checks.length} onClick={()=>onSelect([...selected,key])}><Plus size={13}/>{title(key)}</button>)}</div>}
  {!whole&&!selected.length&&<p role="alert" className="writing-error">Select at least one category to assign.</p>}
  <p className="writing-muted">Click a selected box to customize its criteria. Remove Whole paper to practise selected categories only.</p>
  {createPortal(<dialog ref={dialog} className="rubric-customize-dialog" onCancel={()=>setEditing(null)} onClose={()=>setEditing(null)} aria-labelledby={`rubric-dialog-title-${task}`}>
   <header><div><h2 id={`rubric-dialog-title-${task}`}>Customize {editing==='*'?'whole paper':editing?title(editing):'criteria'}</h2><p>Changes apply to this exercise. Points are distributed to match your total scale.</p></div><button type="button" aria-label="Close category editor" onClick={()=>setEditing(null)}><X size={20}/></button></header>
   <div className="rubric-customize-body">{sections.filter(([key])=>editing==='*'||key===editing).map(([key,checks])=><section className="rubric-popup-category" key={key}>
    <label>Category name<input aria-label={`Category title ${key}`} maxLength={100} value={title(key)} onChange={e=>edit(p=>{p.sectionTitles={...p.sectionTitles,[key]:e.target.value};})}/></label>
    {checks.map((check,i)=><div className="rubric-popup-criterion" key={check.id}><span>{i+1}</span><label>Criterion<textarea aria-label={`Criterion ${key} ${i+1}`} maxLength={1000} value={check.text} onChange={e=>edit(p=>{p.checks[key][i].text=e.target.value;})}/></label><label>Weight<input aria-label={`Criterion weight ${key} ${i+1}`} type="number" min={.01} max={10000} step="any" value={check.weight??2} onChange={e=>edit(p=>{p.checks[key][i].weight=Number(e.target.value);})}/><small>{(allocations[key+':'+check.id]??0).toFixed(2)} points</small></label><button type="button" aria-label={`Remove criterion ${key} ${i+1}`} onClick={()=>edit(p=>{p.checks[key].splice(i,1);})}><X size={16}/></button></div>)}
    <button type="button" onClick={()=>edit(p=>{p.checks[key].push({id:'check_'+crypto.randomUUID(),text:'New criterion',codes:[],weight:2});})}><Plus size={15}/>Add criterion</button>
   </section>)}</div>
   <footer><span>Default weight: 2. Higher weights receive a larger share of points.</span><button type="button" className="primary" onClick={()=>setEditing(null)}>Done</button></footer>
  </dialog>,document.body)}
 </section>;
}
