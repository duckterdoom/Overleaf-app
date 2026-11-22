"use client";
import {useEffect, useRef, useState} from 'react';
import {useWritingApi} from '../../lib/writing/context';
import type {PromptFile} from '../../lib/writing/types';
import {errorText} from '../../lib/parity';
import PromptDocument from './PromptDocument';

export default function QuestionImageUpload({task,value,onChange,onBusyChange}: {
    task:number;value?:PromptFile;onChange:(value:PromptFile|undefined)=>void;
    onBusyChange:(busy:boolean)=>void;
}) {
    const {request}=useWritingApi();
    const [busy,setBusy]=useState(false),[error,setError]=useState('');
    const mounted=useRef(true),uploading=useRef(false);
    useEffect(()=>{mounted.current=true;return()=>{mounted.current=false;onBusyChange(false);};},[]);
    async function upload(file?:File){
        if(!file||uploading.current)return;
        setError('');
        if(!['image/png','image/jpeg','image/webp'].includes(file.type)){
            setError('Choose a PNG, JPEG or WebP image.');return;
        }
        if(file.size>12*1024*1024){setError('Choose an image smaller than 12 MiB.');return;}
        uploading.current=true;setBusy(true);onBusyChange(true);
        try{
            const response=await request('/writing/prompt-images?filename='+encodeURIComponent(file.name),{
                method:'POST',headers:{'Content-Type':file.type},body:file,
            });
            const result:PromptFile=await response.json();
            if(mounted.current)onChange(result);
        }catch(e){if(mounted.current)setError(errorText(e));}
        finally{uploading.current=false;if(mounted.current){setBusy(false);onBusyChange(false);}}
    }
    return <section className="writing-question-upload" aria-label={`Question image Task ${task}`} aria-busy={busy}>
        <div className="writing-upload-heading"><div><strong>Question visual <span className="writing-field-hint">Optional</span></strong>
            <p>{task===1?'Attach the chart, map, process or table students will describe.':'Attach a supporting visual if the question needs one.'}</p></div>
            {value&&<button type="button" disabled={busy} onClick={()=>onChange(undefined)}>Remove attachment</button>}</div>
        <div className="writing-image-drop" onDragOver={e=>{e.preventDefault();}}
            onDrop={e=>{e.preventDefault();if(e.dataTransfer.files.length!==1){setError('Choose one question image per task.');return;}void upload(e.dataTransfer.files[0]);}}>
            <label htmlFor={`question-image-${task}`}>{busy?'Uploading image…':value?'Replace image':'Choose an image or drop it here'}</label>
            <input id={`question-image-${task}`} aria-label={`Upload question image Task ${task}`} type="file" accept="image/png,image/jpeg,image/webp" disabled={busy}
                onChange={e=>{void upload(e.target.files?.[0]);e.target.value='';}}/>
            <small>PNG, JPEG or WebP · Up to 12 MiB · One image per task</small>
        </div>
        {error&&<p className="writing-error" role="alert">{error} Your current attachment is kept; choose another image to retry.</p>}
        {value&&<PromptDocument file={value} language="en"/>}
    </section>;
}
