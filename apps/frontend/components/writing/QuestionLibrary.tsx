"use client";
import {useEffect,useState} from 'react';
import {type Question} from '../../lib/writing/types';
import {useWritingApi} from '../../lib/writing/context';
import {errorText} from '../../lib/parity';
import ExerciseForm from './ExerciseForm';

type LibraryQuestion = Question & {savedToLibrary?:boolean};
const categoryLabels = {ielts:'IELTS',report:'Report',research:'Research paper'};
const scopeLabels = {whole:'Whole paper',intro:'Introduction only',body:'Body only',conclusion:'Conclusion only'};
const purposeLabels = {practice:'Practice',homework:'Assigned exercise',exam:'Examination'};

export default function QuestionLibrary({userId,onUse}:{userId:string;onUse:(q:Question)=>void}){
 const {json}=useWritingApi();
 const [scope,setScope]=useState<'mine'|'public'>('mine');
 const [search,setSearch]=useState(''),[query,setQuery]=useState(''),[category,setCategory]=useState('');
 const [items,setItems]=useState<LibraryQuestion[]>([]),[page,setPage]=useState(0),[total,setTotal]=useState(0);
 const [editing,setEditing]=useState<Question|null|undefined>();
 const [loading,setLoading]=useState(true),[busy,setBusy]=useState<string|null>(null);
 const [error,setError]=useState(''),[notice,setNotice]=useState(''),[refresh,setRefresh]=useState(0);
 useEffect(()=>{
  const timer=setTimeout(()=>{setQuery(search.trim());setPage(0);},300);
  return()=>clearTimeout(timer);
 },[search]);
 useEffect(()=>{
  const controller=new AbortController();setLoading(true);setError('');
  const params=new URLSearchParams({scope,page:String(page),search:query});
  if(category)params.set('category',category);
  void json<{items:LibraryQuestion[];total:number}>(`/writing/questions?${params}`,{signal:controller.signal})
   .then(data=>{if(!controller.signal.aborted){setItems(data.items);setTotal(data.total);}})
   .catch(e=>{if(!controller.signal.aborted)setError(errorText(e));})
   .finally(()=>{if(!controller.signal.aborted)setLoading(false);});
  return()=>controller.abort();
 },[scope,page,query,category,refresh]);
 async function add(q:Question){
  setBusy(q.id);setError('');setNotice('');
  try{
   await json(`/writing/questions/${q.id}/copy`,{method:'POST'});
   setItems(old=>old.map(item=>item.id===q.id?{...item,savedToLibrary:true}:item));
   setNotice(`“${q.title}” added to My library as a private copy. You can edit and assign it there.`);
  }catch(e){setError(errorText(e));}finally{setBusy(null);}
 }
 async function remove(q:Question){
  if(!confirm('Delete this question? Existing assignments keep their snapshots.'))return;
  setBusy(q.id);setError('');
  try{
   await json(`/writing/questions/${q.id}?revision=${q.revision}`,{method:'DELETE'});
   if(items.length===1&&page)setPage(page-1);else setRefresh(n=>n+1);
  }catch(e){setError(errorText(e));}finally{setBusy(null);}
 }
 if(editing!==undefined)return <ExerciseForm key={editing?.id||'new'} practice={false} questionMode question={editing||undefined} onDone={()=>{}} onCancel={()=>setEditing(undefined)} onQuestionSaved={()=>{setEditing(undefined);setRefresh(n=>n+1);}}/>;
 return <section className="question-library">
  <header className="writing-list-toolbar"><div><h2>Question library</h2><p>Keep reusable questions here, or discover questions shared by other tutors.</p></div><button className="primary" onClick={()=>setEditing(null)}>Create question</button></header>
  <nav className="question-library-views" aria-label="Question libraries">
   <button aria-pressed={scope==='mine'} onClick={()=>{setScope('mine');setPage(0);setNotice('');}}>My library</button>
   <button aria-pressed={scope==='public'} onClick={()=>{setScope('public');setPage(0);setNotice('');}}>Public library</button>
  </nav>
  <div className="question-library-heading"><h3>{scope==='mine'?'My library':'Public library'}</h3><p>{scope==='mine'?'Your original questions, questions saved from exercises, and your own copies from the public library.':'Questions shared by other tutors. Add a private copy to My library to edit or assign it.'}</p></div>
  <div className="question-library-filters">
   <label>Search by question title<input type="search" value={search} maxLength={160} placeholder="Enter a title or keyword" onChange={e=>setSearch(e.target.value)}/></label>
   <label>Assessment type<select aria-label="Assessment type" value={category} onChange={e=>{setCategory(e.target.value);setPage(0);}}><option value="">All types</option><option value="ielts">IELTS</option><option value="report">Report</option><option value="research">Research paper</option></select></label>
  </div>
  {notice&&<p className="writing-notice" role="status">{notice}</p>}
  {error&&<p role="alert" className="writing-error">{error} <button onClick={()=>setRefresh(n=>n+1)}>Retry</button></p>}
  <div aria-busy={loading}>
   {loading?<p className="writing-empty" role="status">Loading questions…</p>:!error&&<>
    <div className="writing-assignment-list">{items.map(q=>{
     const c=q.content,assessment=c.category||'ielts',own=q.ownerId===userId;
     const profiles=c.tasks.map(task=>c.rubricCatalog?.[c.profiles[String(task)]]?.label).filter(Boolean);
     return <article className="writing-card" key={q.id}>
      <div className="question-library-badges"><span className="writing-tag">{categoryLabels[assessment]}</span>{assessment==='ielts'&&<><span className="writing-tag">{c.tasks.map(t=>`Task ${t}`).join(' + ')}</span><span className="writing-tag">{c.variant==='general'?'General Training':'Academic'}</span></>}<span className="writing-tag">{scopeLabels[c.writingScope||'whole']}</span>{c.purpose&&<span className="writing-tag">{purposeLabels[c.purpose]}</span>}<span className="writing-tag">{q.visibility==='public'?'Public':'Private'}</span></div>
      <h3>{q.title}</h3>
      <p className="question-library-owner">{own?(q.sourceQuestionId?`Your copy · Originally by ${q.sourceOwnerName||'another tutor'}`:q.sourceAssignmentId?'Created by you · Saved from an exercise':'Created by you'):`Shared by ${q.ownerName}`}</p>
      {!!profiles.length&&<p><strong>Rubric:</strong> {profiles.join(' · ')}</p>}
      <p className="question-excerpt">{Object.values(c.prompts).join('\n')}</p>
      <footer>{own?<><button className="primary" onClick={()=>onUse(q)}>Use for an exercise</button><button onClick={()=>setEditing(q)}>Edit question</button><button disabled={!!busy} onClick={()=>void remove(q)}>{busy===q.id?'Deleting…':'Delete question'}</button></>:<button className="primary" disabled={!!busy||q.savedToLibrary} onClick={()=>void add(q)}>{busy===q.id?'Adding…':q.savedToLibrary?'Added to My library':'Add to my library'}</button>}</footer>
     </article>;
    })}</div>
    {!items.length&&<div className="writing-empty">{query||category?<><p>No matching questions. Try another keyword or assessment type.</p><button onClick={()=>{setSearch('');setQuery('');setCategory('');setPage(0);}}>Clear search and filters</button></>:<p>{scope==='mine'?'Your library is empty. Create a question, assign an exercise, or add a question from Public library.':'No public questions from other tutors yet.'}</p>}</div>}
    <footer className="writing-list-toolbar"><button disabled={!page} onClick={()=>setPage(p=>p-1)}>Previous</button><span>{total} questions · Page {page+1}</span><button disabled={(page+1)*50>=total} onClick={()=>setPage(p=>p+1)}>Next</button></footer>
   </>}
  </div>
 </section>;
}
