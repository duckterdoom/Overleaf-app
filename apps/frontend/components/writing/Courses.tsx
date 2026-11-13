"use client";
import {useState} from 'react';
import {type Classroom} from '../../lib/writing/types';
import {useWritingApi} from '../../lib/writing/context';
import {errorText} from '../../lib/parity';

export default function Courses({courses,tutor,editable,onRefresh}:{courses:Classroom[];tutor:boolean;editable:boolean;onRefresh:()=>Promise<void>}){
 const {json}=useWritingApi();
 const [title,setTitle]=useState(''),[kind,setKind]=useState<'course'|'group'>('course'),[editing,setEditing]=useState<Classroom|null>(null);
 const [email,setEmail]=useState(''),[selected,setSelected]=useState(''),[code,setCode]=useState(''),[error,setError]=useState(''),[busy,setBusy]=useState(false);
 async function run(fn:()=>Promise<unknown>){setBusy(true);setError('');try{await fn();await onRefresh();}catch(e){setError(errorText(e));}finally{setBusy(false);}}
 return <div className="writing-courses">{error&&<p role="alert" className="writing-error">{error}</p>}
 {editable&&<form className="writing-card writing-form" onSubmit={e=>{e.preventDefault();void run(async()=>{
  if(tutor){await json('/writing/courses'+(editing?'/'+editing.id:''),{method:editing?'PUT':'POST',body:JSON.stringify({title,kind,revision:editing?.revision||0})});setTitle('');setEditing(null);}
  else {await json('/writing/classes/join',{method:'POST',body:JSON.stringify({code})});setCode('');}
 });}}><h2>{tutor?editing?'Edit course / group':'Create a course or group':'Join a course or group'}</h2>
 {tutor?<div className="writing-form-grid"><label>Name<input aria-label="Course name" required maxLength={160} value={title} onChange={e=>setTitle(e.target.value)}/></label><label>Type<select aria-label="Course type" value={kind} onChange={e=>setKind(e.target.value as typeof kind)}><option value="course">Course</option><option value="group">Group</option></select></label></div>:<label>Join code<input aria-label="Course join code" required value={code} onChange={e=>setCode(e.target.value)}/></label>}
 <footer>{editing&&<button type="button" onClick={()=>{setEditing(null);setTitle('');}}>Cancel edit</button>}<button className="primary" disabled={busy}>{tutor?'Save course / group':'Join'}</button></footer></form>}
 <div className="writing-assignment-list">{courses.map(course=><article className="writing-card" key={course.id}><span className="writing-tag">{course.kind||'course'}</span><h2>{course.title}</h2><p>{course.studentCount} enrolled students · {course.tutorName}</p>
 {tutor&&<><p>Join code: <strong>{course.joinCode}</strong></p><div className="course-members">{course.students?.map(student=><div key={student.id}><span>{student.name}</span>{editable&&<button disabled={busy} onClick={()=>void run(()=>json(`/writing/courses/${course.id}/members/${student.id}`,{method:'DELETE'}))}>Remove</button>}</div>)}</div>
 {editable&&<><form className="course-add-member" onSubmit={e=>{e.preventDefault();void run(async()=>{await json(`/writing/courses/${course.id}/members`,{method:'POST',body:JSON.stringify({email})});setEmail('');});}}><label>Add registered student<input aria-label={`Student email for ${course.title}`} type="email" required value={selected===course.id?email:''} onChange={e=>{setSelected(course.id);setEmail(e.target.value);}} placeholder="student@example.com"/></label><button disabled={busy}>Add student</button></form>
 <footer><button onClick={()=>{setEditing(course);setTitle(course.title);setKind(course.kind||'course');}}>Edit</button><button disabled={busy} onClick={()=>{if(confirm('Delete this empty course / group?'))void run(()=>json(`/writing/courses/${course.id}?revision=${course.revision||0}`,{method:'DELETE'}));}}>Delete</button></footer></>}
 </>}</article>)}</div>{!courses.length&&<p className="writing-empty">No courses or groups yet.</p>}
 </div>;
}
