"use client";
import {useEffect,useState} from 'react';
import {useWritingApi} from '../../lib/writing/context';
import {catalog as defaults,type Assignment,type Classroom,type Profile,type PromptFile,type Question} from '../../lib/writing/types';
import {errorText} from '../../lib/parity';
import ProjectPromptPicker from './ProjectPromptPicker';
import AssignmentRubric from './AssignmentRubric';

export default function ExerciseForm({practice,initial,template,questionMode=false,question,onQuestionSaved,onDone,onCancel}:{
 practice:boolean;initial?:Assignment;template?:Question;questionMode?:boolean;question?:Question;
 onQuestionSaved?:()=>void;onDone:(a:Assignment)=>void;onCancel:()=>void;
}){
 const {json,project}=useWritingApi();
 const base=initial||question?.content||template?.content;
 const [catalog,setCatalog]=useState<Record<string,Profile>>(base?.rubricCatalog||defaults);
 const [classes,setClasses]=useState<Classroom[]>([]),[classId,setClassId]=useState(base?.classId||'');
 const [title,setTitle]=useState(base?.title||''),[category,setCategory]=useState< 'ielts'|'report'|'research'>(base?.category||'ielts');
 const [scope,setScope]=useState<NonNullable<Assignment['writingScope']>>(base?.writingScope||'whole');
 const [purpose,setPurpose]=useState<NonNullable<Assignment['purpose']>>(base?.kind==='exam'?'exam':base?.purpose||'homework');
 const [visibility,setVisibility]=useState<'private'|'public'>(question?.visibility||initial?.libraryVisibility||'private');
 const [mode,setMode]=useState(base?.tasks.join(',')||'2'),[variant,setVariant]=useState(base?.variant||'academic');
 const [prompts,setPrompts]=useState<Record<string,string>>(base?.prompts||{'1':'','2':''});
 const [profiles,setProfiles]=useState<Record<string,string>>(base?.profiles||{'1':'bar','2':'discussion'});
 const [files,setFiles]=useState<Record<string,PromptFile>>(base?.promptFiles||{});
 const [pending,setPending]=useState<Set<number>>(new Set()),[busy,setBusy]=useState(false),[error,setError]=useState('');
 const local=(value?:string|null)=>value?new Date(new Date(value).getTime()-new Date(value).getTimezoneOffset()*60000).toISOString().slice(0,16):'';
 const [due,setDue]=useState(local(base?.dueAt)),[opens,setOpens]=useState(local(base?.opensAt)),[closes,setCloses]=useState(local(base?.closesAt));
 const [minutes,setMinutes]=useState(base?.durationMinutes?.toString()||''),[password,setPassword]=useState('');
 const [allowLate,setAllowLate]=useState(base?.allowLate??true);
 const tasks=category==='ielts'?mode.split(',').map(Number):[1];
 useEffect(()=>{let stopped=false;void Promise.all([json<{catalog:Record<string,Profile>}>('/writing/settings'),json<{classes:Classroom[]}>('/writing/overview')]).then(([settings,overview])=>{
  if(stopped)return;setClasses(overview.classes);setCatalog({...settings.catalog,...base?.rubricCatalog});
 }).catch(e=>{if(!stopped)setError(errorText(e));});return()=>{stopped=true;};},[]);
 useEffect(()=>{setProfiles(old=>{const next={...old};for(const task of tasks){const options=Object.entries(catalog).filter(([,p])=>(p.category||'ielts')===category&&p.task===task&&(category!=='ielts'||task===2||p.family===variant));if(!options.some(([id])=>id===next[String(task)]))next[String(task)]=options[0]?.[0]||'';}return next;});},[category,mode,variant,catalog]);
 async function save(published:boolean){
  if(pending.size){setError('Wait for the source import to finish.');return;}
  if(tasks.some(task=>!prompts[String(task)]?.trim()&&!files[String(task)])){setError('Enter a question or import a source for each task.');return;}
  if(!questionMode&&!practice&&!project&&!classId){setError('Select a course or group.');return;}
  setBusy(true);setError('');
  try{
   const body={title,category,variant,tasks,profiles,prompts,writingScope:scope,purpose,kind:purpose==='exam'?'exam':'assignment',
    classId:classId||null,practice,published,rubricCatalog:catalog,questionId:template?.id,
    ...(!questionMode&&!practice?{libraryVisibility:visibility}:{}),
    promptFiles:Object.fromEntries(tasks.filter(t=>files[String(t)]).map(t=>[String(t),files[String(t)].id])),
    durationMinutes:minutes?Number(minutes):null,allowLate:purpose==='exam'?false:allowLate,
    dueAt:purpose!=='exam'&&due?new Date(due).toISOString():null,opensAt:purpose==='exam'&&opens?new Date(opens).toISOString():null,
    closesAt:purpose==='exam'&&closes?new Date(closes).toISOString():null,examPassword:purpose==='exam'&&password?password:undefined,revision:initial?.revision||0};
   if(questionMode){await json('/writing/questions'+(question?'/'+question.id:''),{method:question?'PUT':'POST',body:JSON.stringify({visibility,revision:question?.revision||0,content:body})});onQuestionSaved?.();}
   else onDone(await json<Assignment>('/writing/assignments'+(initial?'/'+initial.id:''),{method:initial?'PUT':'POST',body:JSON.stringify(body)}));
  }catch(e){setError(errorText(e));}finally{setBusy(false);}
 }
 return <form className="writing-form writing-card" onSubmit={e=>{e.preventDefault();void save(true);}}>
 <div className="writing-form-heading"><div><span className="writing-eyebrow">TASK WRITING</span><h2>{questionMode?'Question library':practice?'Personal practice':initial?'Edit exercise':'Create exercise'}</h2><p>Write a question, choose the section to practise and customize its own criteria. LaTeX is optional.</p></div></div>
 <div className="writing-form-grid"><label>Title<input aria-label="Exercise title" required maxLength={160} value={title} onChange={e=>setTitle(e.target.value)}/></label>
 <label>Writing scope<select aria-label="Writing scope" value={scope} onChange={e=>setScope(e.target.value as typeof scope)}><option value="whole">Whole paper</option><option value="intro">Introduction only</option><option value="body">Body only</option><option value="conclusion">Conclusion only</option></select></label></div>
 <div className="writing-form-grid"><label>Assessment type<select aria-label="Assessment type" value={category} onChange={e=>{setCategory(e.target.value as typeof category);setFiles({});}}><option value="ielts">IELTS tasks</option><option value="report">Report</option><option value="research">Research paper</option></select></label>
 {questionMode?<label>Library visibility<select aria-label="Question visibility" value={visibility} onChange={e=>setVisibility(e.target.value as typeof visibility)}><option value="private">Private — only you</option><option value="public">Public — reusable by all tutors</option></select></label>:!practice&&<label>Purpose<select aria-label="Exercise purpose" value={purpose} onChange={e=>setPurpose(e.target.value as typeof purpose)}><option value="practice">Practice — self-start for enrolled students</option><option value="homework">Assigned exercise</option><option value="exam">Examination</option></select></label>}</div>
 {!questionMode&&!practice&&<><div className="writing-form-grid"><label>Course or group<select aria-label="Course or group" required={!project} value={classId} onChange={e=>setClassId(e.target.value)}><option value="">{project?'All student collaborators in this project':'Select a course or group'}</option>{classes.map(c=><option key={c.id} value={c.id}>{c.title}</option>)}</select></label><label>Library visibility<select aria-label="Question visibility" value={visibility} onChange={e=>setVisibility(e.target.value as typeof visibility)}><option value="private">Private — only you</option><option value="public">Public — reusable by all tutors</option></select></label></div><p className="writing-notice">Assigning saves the question and rubric to Question library → My library. Public also shares them with other tutors. Drafts are not shared. Student access follows the selected course or group.</p></>}
 {category==='ielts'&&<div className="writing-form-grid"><label>Variant<select value={variant} onChange={e=>setVariant(e.target.value as typeof variant)}><option value="academic">Academic</option><option value="general">General Training</option></select></label><label>Tasks<select aria-label="Assigned tasks" value={mode} onChange={e=>setMode(e.target.value)}><option value="1">Task 1</option><option value="2">Task 2</option><option value="1,2">Both tasks</option></select></label></div>}
 {tasks.map(task=><fieldset key={task}><legend>{category==='ielts'?`Task ${task}`:category==='report'?'Report':'Research paper'} · {scope==='whole'?'whole paper':scope}</legend>
 <label>Question / instructions<textarea aria-label={`Question Task ${task}`} rows={5} maxLength={20000} value={prompts[String(task)]||''} onChange={e=>setPrompts(old=>({...old,[task]:e.target.value}))} placeholder="Write the question and instructions, or import a LaTeX/PDF source below."/></label>
 <ProjectPromptPicker task={task} value={files[String(task)]} onBusyChange={value=>setPending(old=>{const next=new Set(old);if(value)next.add(task);else next.delete(task);return next;})} onChange={file=>{setFiles(old=>{const next={...old};if(file)next[String(task)]=file;else delete next[String(task)];return next;});if(file?.kind==='text')setPrompts(old=>({...old,[task]:file.text||''}));}}/>
 {!project&&files[String(task)]&&<p className="writing-notice">Attached source: {files[String(task)].sourcePath} — preserved when this question is reused.</p>}
 <label>Start from a rubric template<select aria-label={`Rubric Task ${task}`} value={profiles[String(task)]||''} onChange={e=>setProfiles(old=>({...old,[task]:e.target.value}))}>{Object.entries(catalog).filter(([,p])=>(p.category||'ielts')===category&&p.task===task&&(category!=='ielts'||task===2||p.family===variant)).map(([id,p])=><option key={id} value={id}>{p.label}</option>)}</select></label>
 {catalog[profiles[String(task)]]&&<AssignmentRubric value={catalog[profiles[String(task)]]} scope={scope} onChange={value=>setCatalog(old=>({...old,[profiles[String(task)]]:value}))}/>}</fieldset>)}
 {!questionMode&&<><div className="writing-form-grid"><label>Duration (minutes{purpose==='exam'?', required':', optional'})<input aria-label="Duration" type="number" min={1} max={180} required={purpose==='exam'} value={minutes} onChange={e=>setMinutes(e.target.value)}/></label>{purpose!=='exam'&&!practice&&<label>Due date<input aria-label="Due date" type="datetime-local" value={due} onChange={e=>setDue(e.target.value)}/></label>}</div>
 {purpose==='exam'&&<fieldset><legend>Exam access</legend><label>Password<input aria-label="Exam password" type="password" autoComplete="new-password" required={initial?.kind!=='exam'} minLength={6} maxLength={128} value={password} onChange={e=>setPassword(e.target.value)} placeholder="Leave blank to keep an existing password"/></label><div className="writing-form-grid"><label>Opens at<input aria-label="Opens at" type="datetime-local" required value={opens} onChange={e=>setOpens(e.target.value)}/></label><label>Closes at<input aria-label="Closes at" type="datetime-local" required value={closes} onChange={e=>setCloses(e.target.value)}/></label></div></fieldset>}
 {purpose!=='exam'&&<label className="writing-inline-check"><input type="checkbox" checked={allowLate} onChange={e=>setAllowLate(e.target.checked)}/>Accept late submissions</label>}</>}
 {error&&<p className="writing-error" role="alert">{error}</p>}
 <footer><button type="button" disabled={busy} onClick={onCancel}>Cancel</button>{!questionMode&&!practice&&<button type="button" disabled={busy||!!pending.size} onClick={()=>void save(false)}>Save draft</button>}<button className="primary" disabled={busy||!!pending.size} type="submit">{busy?'Saving…':questionMode?'Save question':practice?'Start writing':'Assign exercise'}</button></footer>
 </form>;
}
