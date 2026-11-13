"use client";
import {useState} from 'react';
import {type Assignment,type WritingBlock} from '../../lib/writing/types';

export default function WritingAnswer({assignment,task,value,blocks,readOnly,onChange,onSave}:{
 assignment:Assignment;task:number;value:string;blocks?:WritingBlock[];readOnly:boolean;
 onChange:(text:string,blocks?:WritingBlock[])=>void;onSave:()=>void;
}){
 const [structured,setStructured]=useState(!!blocks?.length);
 function start(){
  const paragraphs=value.split(/\n\s*\n/).filter(p=>p.trim());
  const titles=assignment.writingScope&&assignment.writingScope!=='whole'?[assignment.writingScope]:assignment.category==='research'?['Introduction','Methods','Results','Discussion','Conclusion']:['Introduction','Body','Conclusion'];
  const pieces=blocks?.length?blocks:paragraphs.length?paragraphs.map((text,i)=>({heading:i===0?'Introduction':i===paragraphs.length-1?'Conclusion':`Body ${i}`,text})):titles.map(heading=>({heading,text:''}));
  onChange(pieces.filter(p=>p.text.trim()).map(p=>p.text).join('\n\n'),pieces);setStructured(true);
 }
 function edit(next:WritingBlock[]){onChange(next.filter(p=>p.text.trim()).map(p=>p.text).join('\n\n'),next);}
 return <div className="writing-answer-editor"><div className="writing-answer-mode" role="group" aria-label="Answer organization"><button type="button" aria-pressed={!structured} onClick={()=>setStructured(false)}>Whole text</button><button type="button" aria-pressed={structured} disabled={readOnly&&!blocks?.length} onClick={start}>By paragraph / paper section</button></div>
 {structured&&blocks?.length?<div className="writing-blocks">{blocks.map((block,i)=><section key={i}><header><input aria-label={`Paper section ${i+1}`} value={block.heading} readOnly={readOnly} maxLength={160} onChange={e=>edit(blocks.map((p,n)=>n===i?{...p,heading:e.target.value}:p))}/><button type="button" disabled={readOnly||blocks.length===1} onClick={()=>edit(blocks.filter((_,n)=>i!==n))}>Remove</button></header><textarea aria-label={`Section answer ${i+1}`} spellCheck readOnly={readOnly} value={block.text} onChange={e=>edit(blocks.map((p,n)=>i===n?{...p,text:e.target.value}:p))}/></section>)}<button type="button" disabled={readOnly||blocks.length>=100} onClick={()=>edit([...blocks,{heading:'New section',text:''}])}>Add paragraph / section</button></div>:<textarea aria-label={`Answer Task ${task}`} spellCheck readOnly={readOnly} value={value} onChange={e=>onChange(e.target.value)} onKeyDown={e=>{if((e.ctrlKey||e.metaKey)&&e.key==='s'){e.preventDefault();onSave();}}} placeholder={assignment.writingScope&&assignment.writingScope!=='whole'?`Write only the ${assignment.writingScope} requested in this exercise…`:'Develop your answer to the question…'}/>}
 </div>;
}
