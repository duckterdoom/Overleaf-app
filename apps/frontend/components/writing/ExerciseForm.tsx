"use client";
import {useEffect,useState} from 'react';
import {useWritingApi} from '../../lib/writing/context';
import {catalog as defaults,type Assignment,type Classroom,type Profile,type PromptFile,type Question} from '../../lib/writing/types';
import {errorText} from '../../lib/parity';
import QuestionImageUpload from './QuestionImageUpload';
import RubricCategories from './RubricCategories';
import {pointRows} from '../../lib/writing/points';
import {assessmentGroup} from '../../lib/writing/assessment';

export default function ExerciseForm({practice,initial,template,questionMode=false,question,onQuestionSaved,onDone,onCancel,embedded=false,defaultDelivery='library'}:{
 defaultDelivery?:'library'|'assign';practice:boolean;initial?:Assignment;template?:Question;questionMode?:boolean;question?:Question;
 onQuestionSaved?:()=>void;onDone:(a:Assignment)=>void;onCancel:()=>void;embedded?:boolean;
}){
 const {json,project}=useWritingApi();
 const base=initial||question?.content||template?.content;
 const [delivery,setDelivery]=useState<'library'|'assign'>(defaultDelivery);
 const assigning=!questionMode||delivery==='assign';
 const [defaultCatalog,setDefaultCatalog]=useState<Record<string,Profile>|null>(null);
 const [catalog,setCatalog]=useState<Record<string,Profile>>(base?.rubricCatalog||defaults);
 const [classes,setClasses]=useState<Classroom[]>([]),[classId,setClassId]=useState(base?.classId||'');
 const [description,setDescription]=useState(base?.description||'');
 const [title,setTitle]=useState(base?.title||''),[category,setCategory]=useState< 'ielts'|'report'|'research'>(base?.category||'ielts');
 const [categories,setCategories]=useState<Record<string,string[]>>((Object.keys(base?.assessmentCategories||{}).length?base?.assessmentCategories:undefined)||Object.fromEntries((base?.tasks||[]).filter(()=>base?.writingScope&&base.writingScope!=='whole').map(task=>[String(task),[base!.writingScope!]])));
 const [total,setTotal]=useState(base?.scoring?.total??10);
 const scope='whole';
 const [purpose,setPurpose]=useState<NonNullable<Assignment['purpose']>>(base?.kind==='exam'?'exam':base?.purpose||'homework');
 const [visibility,setVisibility]=useState<'private'|'public'>(question?.visibility||initial?.libraryVisibility||'private');
 const [mode,setMode]=useState(base?.tasks.join(',')||'2');
 const [prompts,setPrompts]=useState<Record<string,string>>(base?.prompts||{'1':'','2':''});
 const [profiles,setProfiles]=useState<Record<string,string>>(base?.profiles||{'1':'bar','2':'discussion'});
 const [files,setFiles]=useState<Record<string,PromptFile>>(base?.promptFiles||{});
 const [pending,setPending]=useState<Set<number>>(new Set()),[busy,setBusy]=useState(false),[error,setError]=useState('');
 const local=(value?:string|null)=>value?new Date(new Date(value).getTime()-new Date(value).getTimezoneOffset()*60000).toISOString().slice(0,16):'';
 const [due,setDue]=useState(local(base?.dueAt)),[opens,setOpens]=useState(local(base?.opensAt)),[closes,setCloses]=useState(local(base?.closesAt));
 const [minutes,setMinutes]=useState(base?.durationMinutes?.toString()||''),[password,setPassword]=useState('');
 const [allowLate,setAllowLate]=useState(base?.allowLate??true);
 const tasks=category==='ielts'?mode.split(',').map(Number):[1];
 const variant=category==='ielts'&&tasks.includes(1)?catalog[profiles['1']]?.family==='general'?'general':'academic':base?.variant||'academic';
 const allocations=pointRows({tasks,profiles,rubricCatalog:catalog,scoring:{mode:'points',total},assessmentCategories:categories});
 useEffect(()=>{let stopped=false;void Promise.all([json<{catalog:Record<string,Profile>}>('/writing/settings'),json<{classes:Classroom[]}>('/writing/overview')]).then(([settings,overview])=>{
  if(stopped)return;setClasses(overview.classes);setDefaultCatalog(settings.catalog);const merged={...settings.catalog,...base?.rubricCatalog};for(const [id,p] of Object.entries(base?.rubricCatalog||{})){if(base?.tasks.some(task=>base.profiles[String(task)]===id&&(base.assessmentCategories?.[String(task)]||base.writingScope&&base.writingScope!=='whole'))&&settings.catalog[id])merged[id]={...p,checks:{...settings.catalog[id].checks,...p.checks},sectionTitles:{...settings.catalog[id].sectionTitles,...p.sectionTitles}};}setCatalog(merged);
 }).catch(e=>{if(!stopped)setError(errorText(e));});return()=>{stopped=true;};},[]);
 useEffect(()=>{setProfiles(old=>{const next={...old};for(const task of tasks){const options=Object.entries(catalog).filter(([,p])=>(p.category||'ielts')===category&&p.task===task);if(!options.some(([id])=>id===next[String(task)]))next[String(task)]=options[0]?.[0]||'';}return next;});},[category,mode,variant,catalog]);
 async function save(published:boolean){
  if(!defaultCatalog){setError('Wait for the rubric templates to load before saving.');return;}
  if(pending.size){setError('Wait for the image upload to finish.');return;}
  if(tasks.some(task=>categories[String(task)]?.length===0)){setError('Choose at least one assessment category for each task.');return;}
  if(tasks.some(task=>!prompts[String(task)]?.trim()&&!files[String(task)])){setError('Enter a question or attach an image for each task.');return;}
  if(assigning&&!practice&&!project&&!classId){setError('Select a course.');return;}
  setBusy(true);setError('');
  try{
   const body={title,description,scoring:{mode:'points',total},assessmentCategories:Object.fromEntries(tasks.filter(t=>categories[String(t)]).map(t=>[String(t),categories[String(t)]])),category,variant,tasks,profiles,prompts,writingScope:scope,purpose,kind:purpose==='exam'?'exam':'assignment',
    classId:classId||null,practice,published,rubricCatalog:catalog,questionId:template?.id||question?.id,
    ...(assigning&&!practice?{libraryVisibility:visibility}:{}),
    promptFiles:Object.fromEntries(tasks.filter(t=>files[String(t)]).map(t=>[String(t),files[String(t)].id])),
    durationMinutes:minutes?Number(minutes):null,allowLate:purpose==='exam'?false:allowLate,
    dueAt:purpose!=='exam'&&due?new Date(due).toISOString():null,opensAt:purpose==='exam'&&opens?new Date(opens).toISOString():null,
    closesAt:purpose==='exam'&&closes?new Date(closes).toISOString():null,examPassword:purpose==='exam'&&password?password:undefined,revision:initial?.revision||0};
   if(questionMode&&!assigning){await json('/writing/questions'+(question?'/'+question.id:''),{method:question?'PUT':'POST',body:JSON.stringify({visibility,revision:question?.revision||0,content:body})});onQuestionSaved?.();}
   else onDone(await json<Assignment>('/writing/assignments'+(initial?'/'+initial.id:''),{method:initial?'PUT':'POST',body:JSON.stringify(body)}));
  }catch(e){setError(errorText(e));}finally{setBusy(false);}
 }
 return <form className="writing-form writing-card" onSubmit={e=>{e.preventDefault();void save(true);}}>
 {embedded?<p className="writing-module-intro">Enter the question directly, add a visual if needed and choose assessment categories and delivery settings.</p>:<div className="writing-form-heading"><div><span className="writing-eyebrow">TASK WRITING</span><h2>{questionMode?'Question library':practice?'Personal practice':initial?'Edit exercise':'Create exercise'}</h2><p>Enter the question directly, add a visual if needed and choose assessment categories and delivery settings.</p></div></div>}
 {questionMode&&<section className="question-delivery-choice" aria-label="Question delivery"><div><strong>Create question</strong><p>Save a reusable question or deliver it to students with the same setup.</p></div><div role="group" aria-label="Delivery option"><button type="button" aria-pressed={!assigning} onClick={()=>setDelivery('library')}>Save to library</button><button type="button" aria-pressed={assigning} onClick={()=>setDelivery('assign')}>Assign to students</button></div></section>}
 <div className="writing-form-grid"><label>Title<input aria-label="Exercise title" required maxLength={160} value={title} onChange={e=>setTitle(e.target.value)}/></label>
</div>
 <label className="writing-description-field">Exercise description <span className="writing-field-hint">Optional</span><textarea aria-label="Exercise description" rows={3} maxLength={20000} value={description} onChange={e=>setDescription(e.target.value)} placeholder="Describe this exercise for the library and assignment list."/><small>Shown in exercise lists and details, not on the student's writing page.</small></label>
 <div className="writing-form-grid"><label>Assessment type<select aria-label="Assessment type" value={assessmentGroup(category)} onChange={e=>{setCategory(e.target.value as typeof category);setFiles({});setCategories({});}}><option value="ielts">IELTS Task</option><option value="research">Research</option></select></label>
 {!practice&&<label>Purpose<select aria-label="Exercise purpose" value={purpose} onChange={e=>setPurpose(e.target.value as typeof purpose)}><option value="practice">Practice - self-start for enrolled students</option><option value="homework">Assigned exercise</option><option value="exam">Examination</option></select></label>}</div>
 {assessmentGroup(category)==='research'&&<label>Document type<select aria-label="Research document type" value={category} onChange={e=>{setCategory(e.target.value as typeof category);setCategories({});}}><option value="research">Research paper</option><option value="report">Report</option></select></label>}
 {!practice&&<div className="writing-form-grid">{assigning&&<label>Course<select aria-label="Course" required={!project} value={classId} onChange={e=>setClassId(e.target.value)}><option value="">{project?'All student collaborators in this project':'Select a course'}</option>{classes.map(c=><option key={c.id} value={c.id}>{c.title}</option>)}</select></label>}<label>Library visibility<select aria-label="Question visibility" value={visibility} onChange={e=>setVisibility(e.target.value as typeof visibility)}><option value="private">Private - only you</option><option value="public">Public - reusable by all tutors</option></select></label></div>}
 {assigning&&!practice&&<p className="writing-notice">The question and assessment categories are saved to My library when assigned. Student access follows the selected course or project.</p>}

 {category==='ielts'&&<div className="writing-form-grid"><label>Tasks<select aria-label="Assigned tasks" value={mode} onChange={e=>{setMode(e.target.value);setCategories({});}}><option value="1">Task 1</option><option value="2">Task 2</option><option value="1,2">Both tasks</option></select></label></div>}
 <section className="exercise-point-scale"><label>Total score<input aria-label="Total score" type="number" min={.01} max={10000} step={.01} required value={total} onChange={e=>setTotal(Number(e.target.value))}/></label><div><p>Set any scale, such as 5, 10, 20 or 100. Points are shared across all selected criteria and tasks. No IELTS band conversion.</p><small>{allocations.length} criteria: {tasks.map(t=>`${category==='ielts'?'Task '+t:'Document'} = ${(allocations.filter(r=>r.task===t).reduce((sum,r)=>sum+r.max,0)).toFixed(2)} points`).join(' + ')}</small></div></section>
 {tasks.map(task=><fieldset key={task}><legend>{category==='ielts'?`Task ${task}`:category==='report'?'Report':'Research paper'} · assessment setup</legend>
 <label>Question / instructions<textarea aria-label={`Question Task ${task}`} rows={5} maxLength={20000} value={prompts[String(task)]||''} onChange={e=>setPrompts(old=>({...old,[task]:e.target.value}))} placeholder="Enter the complete question and instructions students should answer."/></label>
 <QuestionImageUpload task={task} value={files[String(task)]} onBusyChange={value=>setPending(old=>{const next=new Set(old);if(value)next.add(task);else next.delete(task);return next;})} onChange={file=>setFiles(old=>{const next={...old};if(file)next[String(task)]=file;else delete next[String(task)];return next;})}/>
 <label>Start from a rubric template<select aria-label={`Rubric Task ${task}`} value={profiles[String(task)]||''} onChange={e=>{setProfiles(old=>({...old,[task]:e.target.value}));setCategories(old=>{const next={...old};delete next[String(task)];return next;});}}>{Object.entries(catalog).filter(([,p])=>(p.category||'ielts')===category&&p.task===task).map(([id,p])=><option key={id} value={id}>{p.label}</option>)}</select></label>
 {catalog[profiles[String(task)]]&&<RubricCategories key={profiles[String(task)]} task={task} value={catalog[profiles[String(task)]]} selected={categories[String(task)]} allocations={Object.fromEntries(allocations.filter(row=>row.task===task).map(row=>[row.key,row.max]))} onSelect={keys=>setCategories(old=>{const next={...old};if(keys)next[String(task)]=keys;else delete next[String(task)];return next;})} onChange={value=>setCatalog(old=>({...old,[profiles[String(task)]]:value}))}/>}</fieldset>)}
 {assigning&&<><div className="writing-form-grid"><label>Duration (minutes{purpose==='exam'?', required':', optional'})<input aria-label="Duration" type="number" min={1} max={180} required={purpose==='exam'} value={minutes} onChange={e=>setMinutes(e.target.value)}/></label>{purpose!=='exam'&&!practice&&<label>Due date<input aria-label="Due date" type="datetime-local" value={due} onChange={e=>setDue(e.target.value)}/></label>}</div>
 {purpose==='exam'&&<fieldset><legend>Exam access</legend><label>Password<input aria-label="Exam password" type="password" autoComplete="new-password" required={initial?.kind!=='exam'} minLength={6} maxLength={128} value={password} onChange={e=>setPassword(e.target.value)} placeholder="Leave blank to keep an existing password"/></label><div className="writing-form-grid"><label>Opens at<input aria-label="Opens at" type="datetime-local" required value={opens} onChange={e=>setOpens(e.target.value)}/></label><label>Closes at<input aria-label="Closes at" type="datetime-local" required value={closes} onChange={e=>setCloses(e.target.value)}/></label></div></fieldset>}
 {purpose!=='exam'&&<label className="writing-inline-check"><input type="checkbox" checked={allowLate} onChange={e=>setAllowLate(e.target.checked)}/>Accept late submissions</label>}</>}
 {error&&<p className="writing-error" role="alert">{error}</p>}
 <footer><button type="button" disabled={busy} onClick={onCancel}>Cancel</button>{assigning&&!practice&&<button type="button" disabled={busy||!!pending.size} onClick={()=>void save(false)}>Save draft</button>}<button className="primary" disabled={busy||!!pending.size} type="submit">{busy?'Saving…':questionMode&&!assigning?'Save question':practice?'Start writing':initial?'Save exercise':'Create and assign'}</button></footer>
 </form>;
}
