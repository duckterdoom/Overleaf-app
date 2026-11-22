"use client";
import { useEffect, useRef, useState } from "react";
import { Archive, Download, MoreHorizontal, Pencil, Tags, Trash2 } from "lucide-react";
import { request, type Project } from "../../lib/api";
import { action, errorText } from "../../lib/parity";

export default function ProjectMenu({project, onDone}:{project:Project;onDone:()=>Promise<void>}) {
    const [open,setOpen]=useState(false),[mode,setMode]=useState<'menu'|'rename'|'tags'|'trash'>('menu');
    const [value,setValue]=useState(''),[busy,setBusy]=useState(false),[error,setError]=useState('');
    const root=useRef<HTMLDivElement>(null),trigger=useRef<HTMLButtonElement>(null);
    const owned=!project.shared&&(!project.permission||project.permission==='owner');
    useEffect(()=>{
        if(!open)return;
        const outside=(e:PointerEvent)=>{if(!root.current?.contains(e.target as Node)&&!busy)setOpen(false);};
        const escape=(e:KeyboardEvent)=>{if(e.key==='Escape'&&!busy){setOpen(false);trigger.current?.focus();}};
        document.addEventListener('pointerdown',outside);document.addEventListener('keydown',escape);
        return()=>{document.removeEventListener('pointerdown',outside);document.removeEventListener('keydown',escape);};
    },[open,busy]);
    async function run(kind:'rename'|'tags'|'archive'|'trash'|'download') {
        if(busy||(!owned&&kind!=='download'))return;
        setBusy(true);setError('');
        try {
            if(kind==='download') {
                const response=await request('/projects/archive',{method:'POST',body:JSON.stringify({projects:[project.id]})});
                const url=URL.createObjectURL(await response.blob());
                const a=document.createElement('a');a.href=url;a.download=project.title.replace(/[<>:"/\\|?*]/g,'_')+'.zip';a.click();
                setTimeout(()=>URL.revokeObjectURL(url),1000);
            } else {
                await action(kind==='trash'?'project-delete':'project-update',{project:project.id,
                    ...(kind==='rename'?{title:value.trim()}:kind==='tags'?{tags:value.split(',').map(s=>s.trim()).filter(Boolean),tagMode:'replace'}:kind==='archive'?{archived:!project.archived}:{})});
                await onDone();
            }
            setOpen(false);trigger.current?.focus();
        } catch(e){setError(errorText(e));}finally{setBusy(false);}
    }
    return <div className="project-row-actions" ref={root}>
        <button ref={trigger} type="button" aria-label={`Project actions ${project.title}`} aria-expanded={open} aria-controls={`project-menu-${project.id}`} disabled={busy} onClick={()=>{setOpen(!open);setMode('menu');setError('');}}><MoreHorizontal size={18}/></button>
        {open&&<div id={`project-menu-${project.id}`} className="project-row-menu" role="group" aria-label={`Actions for ${project.title}`}>
            {mode==='menu'?<>
                <button disabled={busy} onClick={()=>void run('download')}><Download size={15}/>Download ZIP</button>
                {owned&&<>
                    <button onClick={()=>{setValue(project.title);setMode('rename');}}><Pencil size={15}/>Rename</button>
                    <button onClick={()=>{setValue((project.tags||[]).join(', '));setMode('tags');}}><Tags size={15}/>Edit tags</button>
                    <button onClick={()=>void run('archive')}><Archive size={15}/>{project.archived?'Unarchive':'Archive'}</button>
                    <button className="danger" onClick={()=>setMode('trash')}><Trash2 size={15}/>Trash project</button>
                </>}
            </>:mode==='trash'?<>
                <strong>Move this project to trash?</strong><p>{project.title}</p>
                <button disabled={busy} className="danger" onClick={()=>void run('trash')}>Confirm trash</button>
                <button disabled={busy} onClick={()=>setMode('menu')}>Cancel</button>
            </>:<form onSubmit={e=>{e.preventDefault();void run(mode);}}>
                <label>{mode==='rename'?'Project name':'Project tags'}<input autoFocus aria-label={mode==='rename'?'Project name':'Project tags'} value={value} maxLength={mode==='rename'?120:1000} required={mode==='rename'} onChange={e=>setValue(e.target.value)}/></label>
                <div><button disabled={busy||(mode==='rename'&&!value.trim())}>Save</button><button type="button" disabled={busy} onClick={()=>setMode('menu')}>Cancel</button></div>
            </form>}
            {error&&<p role="alert" className="form-error">{error}</p>}
            {busy&&<small role="status">Working...</small>}
        </div>}
    </div>;
}
