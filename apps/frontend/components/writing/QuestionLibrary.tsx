"use client";
import {useEffect,useState} from 'react';
import {type Question} from '../../lib/writing/types';
import {useWritingApi} from '../../lib/writing/context';
import {errorText} from '../../lib/parity';
import ExerciseForm from './ExerciseForm';

export default function QuestionLibrary({userId,onUse}:{userId:string;onUse:(q:Question)=>void}){
 const {json}=useWritingApi();
 const [items,setItems]=useState<Question[]>([]),[page,setPage]=useState(0),[total,setTotal]=useState(0),[editing,setEditing]=useState<Question|null|undefined>(),[error,setError]=useState('');
 async function reload(){try{const data=await json<{items:Question[];total:number}>(`/writing/questions?page=${page}`);setItems(data.items);setTotal(data.total);}catch(e){setError(errorText(e));}}
 useEffect(()=>{void reload();},[page]);
 if(editing!==undefined)return <ExerciseForm key={editing?.id||'new'} practice={false} questionMode question={editing||undefined} onDone={()=>{}} onCancel={()=>setEditing(undefined)} onQuestionSaved={()=>{setEditing(undefined);void reload();}}/>;
 return <section className="question-library"><header className="writing-list-toolbar"><div><h2>Question library</h2><p>Private questions belong to you. Public questions can be reused by every tutor in this application.</p></div><button className="primary" onClick={()=>setEditing(null)}>Create question</button></header>
 {error&&<p role="alert" className="writing-error">{error}</p>}
 <div className="writing-assignment-list">{items.map(q=><article className="writing-card" key={q.id}><span className="writing-tag">{q.visibility}</span><h2>{q.title}</h2><p>{q.ownerName} · {q.content.writingScope||'whole'} · {q.content.category||'ielts'}</p><p className="question-excerpt">{Object.values(q.content.prompts).join('\n')}</p><footer><button className="primary" onClick={()=>onUse(q)}>Use for an exercise</button>{q.ownerId===userId&&<><button onClick={()=>setEditing(q)}>Edit question</button><button onClick={async()=>{if(!confirm('Delete this question? Existing assignments keep their snapshots.'))return;try{await json(`/writing/questions/${q.id}?revision=${q.revision}`,{method:'DELETE'});await reload();}catch(e){setError(errorText(e));}}}>Delete question</button></>}</footer></article>)}</div>
 {!items.length&&<p className="writing-empty">No questions yet. Create your first reusable question.</p>}
 <footer className="writing-list-toolbar"><button disabled={!page} onClick={()=>setPage(p=>p-1)}>Previous</button><span>{total} questions · Page {page+1}</span><button disabled={(page+1)*50>=total} onClick={()=>setPage(p=>p+1)}>Next</button></footer></section>;
}
