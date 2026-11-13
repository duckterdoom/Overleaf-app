"""Published, project-visible LaTeX feedback files; drafts stay private."""
import re
import unicodedata
from latex_backend.storage.store import Problem, atomic_write, escape_tex


def slug(value):
    value=unicodedata.normalize('NFKD',value).encode('ascii','ignore').decode()
    return re.sub(r'[^a-zA-Z0-9_-]+','-',value).strip('-')[:65] or 'student'


def write_results(gateway, service, user, store, submission, publication):
    row=service.submission(user,submission)
    project=user['writingProject']
    files=[]
    payloads={}
    current=gateway.database.writing_results.find_one({'_id':submission})
    if not current or current['publishedVersion']!=publication['publishedVersion']:
        raise Problem('Kết quả đã đổi; tải lại trước khi tạo phiếu.',409)
    for number in row['assignment']['tasks']:
        profile=row['assignment']['profiles'][str(number)]
        category=row['assignment'].get('category','ielts')
        scope=f'task{number}' if category=='ielts' else category
        name=f"result/{slug(row['studentName'])}-{scope}-{slug(profile)}-{submission[:12]}-v{publication['publishedVersion']}.tex"
        source,_=service.latex(user,submission=submission,report=True,task_number=number)
        source=source.replace(r'\begin{document}',r'\begin{document}'+'\n'+r'\section*{'+escape_tex(row['studentName'])+r'}')
        source=source.replace('question-task-',f'result/assets/{submission}-question-task-')
        source=source.replace('{prompt-image}',f'{{result/assets/{submission}-prompt-image}}')
        payloads[name]=source.encode('utf-8');files.append(name)
    for task,attachment in row['assignment'].get('promptFiles',{}).items():
        if attachment['kind'] not in {'pdf','image'}:
            continue
        asset=gateway.database.writing_prompt_files.find_one({'_id':attachment['id'],'projectId':project})
        if not asset:
            raise Problem('Không tìm thấy tệp đề để lưu kết quả.',409)
        ext='pdf' if attachment['kind']=='pdf' else 'png'
        payloads[f'result/assets/{submission}-question-task-{task}.{ext}']=bytes(asset['data'])
    image=row['assignment'].get('imageId')
    if image:
        if image=='sample-commuting':
            from latex_backend.writing.assets import commuting_png
            data=commuting_png()
        else:
            asset=gateway.database.writing_assets.find_one({'_id':image})
            data=bytes(asset['data']) if asset else None
        if data:
            payloads[f'result/assets/{submission}-prompt-image.png']=data
    # Never overwrite a generated file someone subsequently edited by hand.
    current=gateway.database.writing_results.find_one({'_id':submission})
    if current['publishedVersion']!=publication['publishedVersion']:
        raise Problem('Kết quả đã đổi; tải lại trước khi tạo phiếu.',409)
    with store.shared_files.edit(project):
        for name,data in payloads.items():
            path=store.path(project,name)
            if path.exists() and path.read_bytes()!=data:
                raise Problem('Tệp kết quả đã được chỉnh sửa trong editor; không ghi đè tự động.',409)
        for name,data in payloads.items():
            path=store.path(project,name);path.parent.mkdir(parents=True,exist_ok=True)
            if not path.exists():atomic_write(path,data)
    return files
