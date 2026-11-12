"""Private IELTS outputs linked to a project; never create user projects."""
import asyncio
import base64
import hashlib
import tempfile
from pathlib import Path
from bson import Binary
from latex_backend.storage.store import Store, Problem, atomic_write
from latex_backend.compilation.compiler import Compiler


async def render(gateway, user, assignment, source, report, submission):
    gateway.accounts.throttle(('writing-output', user['id']))
    project = user['writingProject']
    fingerprint = hashlib.sha256(source.encode()).hexdigest()
    key = hashlib.sha256(f'{project}:{user["id"]}:{submission or assignment["id"]}:{report}:{fingerprint}'.encode()).hexdigest()
    cached = await asyncio.to_thread(gateway.database.writing_artifacts.find_one, {'_id':key})
    if cached:
        return {'source':source, 'pdf':base64.b64encode(bytes(cached['pdf'])).decode(), 'artifact':True}
    with tempfile.TemporaryDirectory(prefix='latex-writing-output-') as directory:
        store = Store(Path(directory))
        temporary = store.create('IELTS output')
        identifier = temporary['id']
        store.save(identifier, 'main.tex', source)
        for task, attachment in assignment.get('promptFiles', {}).items():
            if attachment['kind'] in {'pdf','image'}:
                row = await asyncio.to_thread(gateway.database.writing_prompt_files.find_one, {'_id':attachment['id'],'projectId':project})
                if not row:
                    raise Problem('Không tìm thấy tệp đề.',409)
                extension = 'pdf' if attachment['kind']=='pdf' else 'png'
                atomic_write(store.path(identifier,f'question-task-{task}.{extension}'),bytes(row['data']))
        image = assignment.get('imageId')
        if image:
            if image=='sample-commuting':
                from latex_backend.writing.assets import commuting_png
                payload=commuting_png()
            else:
                row=await asyncio.to_thread(gateway.database.writing_assets.find_one,{'_id':image})
                payload=bytes(row['data']) if row else None
            if payload:
                atomic_write(store.path(identifier,'prompt-image.png'),payload)
        compiler=Compiler(store)
        job=compiler.start(identifier,'pdflatex')
        snapshot=None
        while True:
            event=await job.queue.get()
            if event['type']=='done':
                snapshot=event['snapshot']
            if event['type']=='end':
                break
        if not snapshot:
            raise Problem('Không biên dịch được phiếu IELTS. Bài viết/bản nộp vẫn được giữ trong dự án.',409)
        pdf=compiler.verified(identifier,snapshot).data
        if len(pdf) + len(source.encode()) > 12 * 1024 * 1024:
            raise Problem('Phiếu PDF quá lớn để lưu trực tiếp. Giảm dung lượng hình trong đề; bản nộp vẫn được giữ.',413)
        await asyncio.to_thread(gateway.database.writing_artifacts.update_one, {'_id':key}, {'$set':{
            'projectId':project,'userId':user['id'],'assignmentId':assignment['id'],
            'submissionId':submission,'report':report,'source':source,'pdf':Binary(pdf)}}, upsert=True)
    return {'source':source,'pdf':base64.b64encode(pdf).decode(),'artifact':True}
