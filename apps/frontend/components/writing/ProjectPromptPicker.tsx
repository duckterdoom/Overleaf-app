"use client";
import {useEffect,useState} from 'react';
import {compile,type Entry,type Project} from '../../lib/api';
import {useWritingApi} from '../../lib/writing/context';
import type {PromptFile} from '../../lib/writing/types';
import {errorText} from '../../lib/parity';
import PromptDocument from './PromptDocument';

export default function ProjectPromptPicker({task,value,onChange,onBusyChange}: {
    task:number;value?:PromptFile;onChange:(value:PromptFile|undefined)=>void;
    onBusyChange?:(pending:boolean)=>void;
}) {
    const {project:contextProject,json}=useWritingApi();
    const [sourceProject,setSourceProject]=useState(''),[projects,setProjects]=useState<Project[]>([]);
    const project=contextProject||sourceProject;
    useEffect(()=>{if(!contextProject)void json<Project[]>('/projects').then(setProjects).catch(e=>setError(errorText(e)));},[contextProject]);
    const [files,setFiles]=useState<Entry[]>([]),[path,setPath]=useState(value?.sourcePath || '');
    const [engine,setEngine]=useState('pdflatex'),[first,setFirst]=useState('1'),[last,setLast]=useState('');
    const [busy,setBusy]=useState(false),[error,setError]=useState(''),[log,setLog]=useState('');
    async function refresh(){
        if(!project)return;
        const entries=await json<Entry[]>(`/projects/${project}/files`);
        setFiles(entries.filter(e=>!e.directory&&/\.(tex|pdf|png|jpe?g|webp|txt|md)$/i.test(e.path)));
    }
    useEffect(()=>{void refresh().catch(e=>setError(errorText(e)));},[project]);
    useEffect(()=>{onBusyChange?.(busy || !!path && !value);},[busy,path,value?.id]);
    useEffect(()=>()=>onBusyChange?.(false),[]);
    async function take(){
        if(!project||!path)return;
        setBusy(true);setError('');setLog('');
        try{
            let snapshot:string|undefined,failed='';
            if(/\.tex$/i.test(path)){
                await compile(project,engine,event=>{
                    if(event.type==='done')snapshot=event.snapshot;
                    if(event.type==='error')failed=event.message||'Question compilation failed.';
                    if(event.type==='output')setLog(old=>(old+event.text).slice(-12000));
                },path);
                if(failed||!snapshot)throw Error(failed||'No question PDF. Select a standalone LaTeX file and inspect the log.');
            }
            const result=await json<PromptFile>('/writing/prompt-import',{method:'POST',body:JSON.stringify({path,snapshot,sourceProjectId:!contextProject?sourceProject:undefined,
                firstPage:Number(first)||1,lastPage:last?Number(last):null})});
            onChange(result);
        }catch(e){setError(errorText(e));}finally{setBusy(false);}
    }
    return <div className="writing-project-prompt">
        <strong>Import question Task {task} from a project source</strong>
        <p>Select a source from LaTeX Studio. Students receive its PDF, including images and layout.</p>
        {!contextProject&&<label>Source project<select aria-label={`Source project Task ${task}`} value={sourceProject} disabled={busy} onChange={e=>{setSourceProject(e.target.value);setPath('');setFiles([]);onChange(undefined);}}><option value="">Optional — choose a LaTeX project</option>{projects.map(p=><option key={p.id} value={p.id}>{p.title}</option>)}</select></label>}
        <label>Question source Task {task}<select aria-label={`Question source Task ${task}`} value={path} disabled={busy} onChange={e=>{setPath(e.target.value);onChange(undefined);}}>
            <option value="">Choose a project source</option>{files.map(f=><option key={f.path} value={f.path}>{f.path}</option>)}
        </select></label>
        <div className="writing-prompt-options">
            {/\.tex$/i.test(path)&&<label>Engine<select aria-label={`Question engine Task ${task}`} value={engine} onChange={e=>{setEngine(e.target.value);onChange(undefined);}} disabled={busy}>
                <option value="pdflatex">pdfLaTeX</option><option value="xelatex">XeLaTeX</option><option value="lualatex">LuaLaTeX</option>
            </select></label>}
            {/\.(tex|pdf)$/i.test(path)&&<><label>From page<input aria-label={`First page Task ${task}`} type="number" min={1} max={100} value={first} disabled={busy} onChange={e=>{setFirst(e.target.value);onChange(undefined);}}/></label>
            <label>To page<input aria-label={`Last page Task ${task}`} type="number" min={1} max={100} value={last} placeholder="Last page" disabled={busy} onChange={e=>{setLast(e.target.value);onChange(undefined);}}/></label></>}
        </div>
        <div className="writing-prompt-options"><button type="button" disabled={busy||!path} onClick={()=>void take()}>{busy?'Importing question…':`Use source for Task ${task}`}</button>
            <button type="button" disabled={busy} onClick={()=>void refresh().catch(e=>setError(errorText(e)))}>Refresh source files</button>
            {path&&<button type="button" disabled={busy} onClick={()=>{setPath('');onChange(undefined);}}>Remove source</button>}</div>
        {error&&<p className="writing-error" role="alert">{error}</p>}
        {log&&<details><summary>Question build log</summary><pre className="writing-prompt-log">{log}</pre></details>}
        {value&&<PromptDocument file={value}/>}
    </div>;
}
