"use client";
import {useState} from 'react';
import {type Profile} from '../../lib/writing/types';
import AssignmentRubric from './AssignmentRubric';

function sameChecklist(value:Profile,baseline:Profile,scope:string){
 const scoped=(profile:Profile)=>({...profile,checks:Object.fromEntries(Object.entries(profile.checks).filter(([key])=>scope==='whole'||key===scope))});
 return JSON.stringify(scoped(value))===JSON.stringify(scoped(baseline));
}
export default function ExerciseChecklist({value,defaultValue,scope,task,onChange}:{value:Profile;defaultValue?:Profile;scope:string;task:number;onChange:(profile:Profile)=>void}){
 const [custom,setCustom]=useState(!defaultValue||!sameChecklist(value,defaultValue,scope));
 const sections=Object.entries(value.checks).filter(([key])=>scope==='whole'||key===scope);
 function restore(){
  if(!defaultValue)return false;
  if(!sameChecklist(value,defaultValue,scope)&&!confirm('Replace this exercise’s customized checklist with the default template?'))return false;
  onChange(structuredClone(defaultValue));return true;
 }
 return <section className="exercise-checklist-setup" aria-label={`Checklist Task ${task}`}>
  <fieldset className="checklist-mode"><legend>Assessment checklist</legend>
   <label><input type="radio" name={`checklist-mode-${task}`} checked={!custom} disabled={!defaultValue} onChange={()=>{if(restore())setCustom(false);}}/>Use default checklist</label>
   <label><input type="radio" name={`checklist-mode-${task}`} checked={custom} onChange={()=>setCustom(true)}/>Customize checklist</label>
  </fieldset>
  <p className="writing-muted">{custom?'Changes apply only to this exercise. Assigned exercises keep their saved criteria.':'The selected template is used automatically. You can review it below or customize it before assigning.'}</p>
  {!defaultValue&&<p className="writing-notice">The original template is no longer available. This exercise’s saved checklist is preserved.</p>}
  {custom?<><AssignmentRubric value={value} scope={scope} onChange={onChange}/>{defaultValue&&<button type="button" onClick={restore}>Restore default checklist</button>}</>:<details className="checklist-default-preview"><summary>Preview default checklist · {sections.reduce((sum,[,checks])=>sum+checks.length,0)} criteria</summary>{sections.map(([key,checks])=><section key={key}><h4>{value.sectionTitles?.[key]||key}</h4><ul>{checks.map(check=><li key={check.id}>{check.text}</li>)}</ul></section>)}</details>}
 </section>;
}
