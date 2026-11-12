"use client";
import {useEffect,useState} from 'react';
import {compile,type Entry} from '../../lib/api';
import {useWritingApi} from '../../lib/writing/context';
import type {PromptFile} from '../../lib/writing/types';
import {errorText} from '../../lib/parity';
import PromptDocument from './PromptDocument';

export default function ProjectPromptPicker({task,value,onChange,onBusyChange}: {
    task:number;value?:PromptFile;onChange:(value:PromptFile|undefined)=>void;
    onBusyChange?:(pending:boolean)=>void;
}) {
    const {project,json}=useWritingApi();
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
                    if(event.type==='error')failed=event.message||'Biên dịch đề thất bại.';
                    if(event.type==='output')setLog(old=>(old+event.text).slice(-12000));
                },path);
                if(failed||!snapshot)throw Error(failed||'Chưa có PDF của đề. Chọn tệp LaTeX gốc có documentclass và đọc log.');
            }
            const result=await json<PromptFile>('/writing/prompt-import',{method:'POST',body:JSON.stringify({path,snapshot,
                firstPage:Number(first)||1,lastPage:last?Number(last):null})});
            onChange(result);
        }catch(e){setError(errorText(e));}finally{setBusy(false);}
    }
    return <div className="writing-project-prompt">
        <strong>Lấy đề Task {task} từ tệp trong dự án</strong>
        <p>Chọn tệp đã soạn ở editor; LaTeX được biên dịch qua cùng bộ compile/PDF. Học viên xem bản đề này, gồm hình và bố cục.</p>
        <label>Tệp đề Task {task}<select aria-label={`Tệp đề Task ${task}`} value={path} disabled={busy} onChange={e=>{setPath(e.target.value);onChange(undefined);}}>
            <option value="">Chọn tệp trong dự án</option>{files.map(f=><option key={f.path} value={f.path}>{f.path}</option>)}
        </select></label>
        <div className="writing-prompt-options">
            {/\.tex$/i.test(path)&&<label>Engine<select aria-label={`Engine đề Task ${task}`} value={engine} onChange={e=>{setEngine(e.target.value);onChange(undefined);}} disabled={busy}>
                <option value="pdflatex">pdfLaTeX</option><option value="xelatex">XeLaTeX</option><option value="lualatex">LuaLaTeX</option>
            </select></label>}
            {/\.(tex|pdf)$/i.test(path)&&<><label>Từ trang<input aria-label={`Trang đầu đề Task ${task}`} type="number" min={1} max={100} value={first} disabled={busy} onChange={e=>{setFirst(e.target.value);onChange(undefined);}}/></label>
            <label>Đến trang<input aria-label={`Trang cuối đề Task ${task}`} type="number" min={1} max={100} value={last} placeholder="Trang cuối" disabled={busy} onChange={e=>{setLast(e.target.value);onChange(undefined);}}/></label></>}
        </div>
        <div className="writing-prompt-options"><button type="button" disabled={busy||!path} onClick={()=>void take()}>{busy?'Đang lấy đề…':`Dùng tệp làm đề Task ${task}`}</button>
            <button type="button" disabled={busy} onClick={()=>void refresh().catch(e=>setError(errorText(e)))}>Tải lại danh sách tệp</button>
            {path&&<button type="button" disabled={busy} onClick={()=>{setPath('');onChange(undefined);}}>Bỏ tệp đề</button>}</div>
        {error&&<p className="writing-error" role="alert">{error}</p>}
        {log&&<details><summary>Log biên dịch đề</summary><pre className="writing-prompt-log">{log}</pre></details>}
        {value&&<PromptDocument file={value}/>}
    </div>;
}
