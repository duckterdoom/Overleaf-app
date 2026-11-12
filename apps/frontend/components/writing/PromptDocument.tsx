"use client";
import {useEffect, useState} from 'react';
import PdfViewer from '../PdfViewer';
import {useWritingApi} from '../../lib/writing/context';
import type {PromptFile} from '../../lib/writing/types';
import {errorText} from '../../lib/parity';

export default function PromptDocument({file, language='vi', focused=false}: {file: PromptFile; language?: string; focused?: boolean}) {
    const {request, promptUrl} = useWritingApi();
    const [pdf, setPdf] = useState<Uint8Array | null>(null), [error,setError]=useState('');
    useEffect(() => {
        let active=true;setPdf(null);setError('');
        if(file.kind==='pdf')void request('/writing/prompt-files/'+file.id).then(r=>r.arrayBuffer()).then(data=>{
            if(active)setPdf(new Uint8Array(data));
        }).catch(e=>{if(active)setError(errorText(e));});
        return()=>{active=false;};
    },[file.id]);
    return <div className={`writing-prompt-document${focused?' writing-focused-prompt':''}`} data-prompt-file={file.id}>
        <p className="writing-notice">Đề từ editor: <strong>{file.sourcePath}</strong>{file.pages ? ` · ${file.pages} trang` : ''}.
            <span> Bản đề khi giao bài được giữ nguyên.</span></p>
        {error && <p className="writing-error" role="alert">{error}</p>}
        {file.kind==='pdf' && <div className="writing-prompt-pdf">{pdf ? <PdfViewer reading={focused} data={pdf} stale={false} language={language}/> : !error && <p role="status">Đang tải đề từ editor…</p>}</div>}
        {file.kind==='image' && <img src={promptUrl(file.id)} alt={'Đề bài từ '+file.sourcePath}/>}
        {file.kind==='text' && <small>Văn bản đã lấy từ tệp nguồn.</small>}
    </div>;
}
