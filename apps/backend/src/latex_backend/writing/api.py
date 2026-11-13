import io
import uuid
from typing import Annotated, Literal
from datetime import datetime

from fastapi import Request
from fastapi.responses import Response
from pydantic import BaseModel, ConfigDict, Field, StringConstraints
from bson import Binary
from PIL import Image, UnidentifiedImageError

from latex_backend.storage.store import Problem, atomic_write
from latex_backend.writing.service import Writing, public


class Model(BaseModel):
    model_config = ConfigDict(extra='forbid')


class Title(Model):
    title: str = Field(min_length=1, max_length=160)


class RubricCheck(Model):
    id: str = Field(pattern=r'^[a-zA-Z0-9_-]{1,80}$')
    text: str = Field(min_length=1, max_length=1000)
    codes: list[Annotated[str, StringConstraints(pattern=r'^[A-Z0-9_-]{1,30}$')]] = Field(min_length=1, max_length=10)


class RubricProfile(Model):
    category: Literal['ielts','report','research'] = 'ielts'
    task: Literal[1,2] = 1
    family: Literal['academic','general','essay','report','research']
    label: str = Field(min_length=1,max_length=160)
    focus: list[Annotated[str,StringConstraints(max_length=1000)]] = Field(default_factory=list,max_length=20)
    sectionTitles: dict[str,Annotated[str,StringConstraints(max_length=100)]] = Field(default_factory=dict,max_length=20)
    checks: dict[str,list[RubricCheck]] = Field(max_length=20)


class RubricSettings(Model):
    revision: int = Field(ge=0)
    checks: dict[str, dict[str, list[RubricCheck]]] | None = None
    catalog: dict[str,RubricProfile] | None = Field(default=None,max_length=80)


class Join(Model):
    code: str = Field(min_length=1, max_length=30)


class Assignment(Title):
    kind: Literal['assignment','exam'] = 'assignment'
    opensAt: datetime | None = None
    closesAt: datetime | None = None
    examPassword: str | None = Field(default=None,min_length=6,max_length=128)
    category: Literal['ielts','report','research'] = 'ielts'
    classId: str | None = None
    variant: Literal['academic', 'general'] = 'academic'
    tasks: list[Literal[1, 2]] = Field(min_length=1, max_length=2)
    prompts: dict[Literal['1', '2'], Annotated[str, StringConstraints(max_length=20000)]] = Field(default_factory=dict)
    profiles: dict[Literal['1', '2'], str]
    imageId: str | None = None
    promptFiles: dict[Literal['1', '2'], str] = Field(default_factory=dict, max_length=2)
    dueAt: datetime | None = None
    durationMinutes: int | None = Field(default=None, ge=1, le=180)
    allowLate: bool = True
    published: bool = False
    practice: bool = False
    revision: int = Field(default=0, ge=0)


class Draft(Model):
    revision: int = Field(ge=0)
    answers: dict[Literal['1', '2'], Annotated[str, StringConstraints(max_length=100000)]]
    latexSources: dict[Literal['1', '2'], Annotated[str, StringConstraints(max_length=200000)]] = Field(default_factory=dict)


class ExamEntry(Model):
    password: str = Field(min_length=1,max_length=128)


class ExamPassword(ExamEntry):
    revision: int = Field(ge=0)


class Submit(Model):
    revision: int = Field(ge=0)
    requestId: str = Field(min_length=16, max_length=64, pattern='^[a-zA-Z0-9-]+$')


class Section(Model):
    paragraph: int = Field(ge=-1, le=1000)
    kind: str = Field(pattern=r'^[a-zA-Z0-9_-]{1,40}$')
    title: str = Field(max_length=160)
    checks: dict[str, Literal['unreviewed', 'met', 'partial', 'missing', 'na']] = Field(default_factory=dict, max_length=100)
    note: str = Field(default='', max_length=10000)
    quote: str = Field(default='', max_length=3000)


class Grade(Model):
    scoreSource: Literal['manual','checklist'] = 'manual'
    profile: str = Field(max_length=40)
    scores: dict[str, float | None] = Field(default_factory=dict,max_length=4)
    notes: dict[str, Annotated[str, StringConstraints(max_length=10000)]] = Field(default_factory=dict, max_length=4)
    overall: str = Field(default='', max_length=10000)
    sections: list[Section] = Field(default_factory=list, max_length=200)


class Review(Model):
    revision: int = Field(ge=0)
    tasks: dict[Literal['1', '2'], Grade]
    summary: str = Field(default='', max_length=20000)
    recommendations: str = Field(default='', max_length=20000)


class Publish(Model):
    revision: int = Field(ge=1)
    reason: str = Field(default='', max_length=3000)


class Latex(Model):
    assignmentId: str | None = None
    submissionId: str | None = None
    report: bool = False
    createProject: bool = False
    useDraftSource: bool = False


class PromptImport(Model):
    path: str = Field(min_length=1, max_length=1024)
    snapshot: str | None = Field(default=None, max_length=100)
    firstPage: int = Field(default=1, ge=1, le=100)
    lastPage: int | None = Field(default=None, ge=1, le=100)


def register_writing(gateway):
    api, service = gateway.auth, Writing(gateway)
    gateway.writing = service

    def user(request):
        value = gateway.principal(request)
        project = request.query_params.get('projectId')
        if project:
            permission = gateway.sharing.access(project, value['id'])
            if not permission:
                raise Problem('Bạn không có quyền truy cập dự án.', 404)
            value = {**value, 'role': gateway.sharing.workspace_role(project, value['id']),
                     'writingProject': project, 'projectPermission': permission}
            if request.method not in {'GET', 'HEAD'} and permission == 'viewer' and request.url.path != '/api/writing/latex':
                raise Problem('Bạn chỉ có quyền xem dự án; cần quyền chỉnh sửa để thay đổi IELTS.', 403)
        if value.get('role') not in {'student', 'tutor'}:
            raise Problem('Hoàn tất lựa chọn Student/Tutor trước khi dùng IELTS Writing.', 403)
        return value

    @api.get('/api/writing/overview')
    def overview(request: Request):
        current = user(request)
        return {'classes': service.classes(current), 'assignments': service.assignments(current, compact=True),
                'submissions': service.submissions(current, compact=True), 'role': current['role'],
                'permission': current.get('projectPermission', 'owner'), 'legacy': service.legacy(current),
                'students': service.project_students(current)}

    @api.get('/api/writing/settings')
    def writing_settings(request: Request):
        return service.settings(user(request))

    @api.put('/api/writing/settings')
    def save_writing_settings(data: RubricSettings, request: Request):
        return service.save_settings(user(request), data.model_dump())

    @api.post('/api/writing/import-legacy')
    def import_legacy(request: Request):
        return service.import_legacy(user(request))

    @api.post('/api/writing/prompt-import')
    async def import_prompt(data: PromptImport, request: Request):
        import asyncio
        from latex_backend.writing.prompts import capture
        current = user(request)
        if not current.get('writingProject'):
            raise Problem('Mở dự án chứa đề trước khi nhập.', 400)
        app, store, _ = await gateway.project_workspace(current['writingProject'], current)
        return await asyncio.to_thread(capture, gateway, current, store, app.state.compiler, data)

    @api.get('/api/writing/prompt-files/{key}')
    def prompt_file(key: str, request: Request):
        current = user(request)
        row = gateway.database.writing_prompt_files.find_one({'_id': key})
        if not row or row['projectId'] != current.get('writingProject'):
            raise Problem('Không tìm thấy tệp đề trong dự án.', 404)
        allowed = current['role'] == 'tutor' or row.get('ownerId') == current['id']
        if not allowed:
            for assignment in gateway.database.writing_assignments.find({'projectId': row['projectId'], '$or': [
                    {'promptFiles.1.id': key}, {'promptFiles.2.id': key}]}):
                try:
                    task=service.assignment(current, assignment['id'])
                    from latex_backend.writing.exams import require_entry
                    require_entry(gateway.database,current,task)
                    allowed = True; break
                except Problem:
                    pass
        if not allowed:
            raise Problem('Đề chưa được giao cho bạn.', 404)
        return Response(bytes(row['data']), media_type=row['mime'], headers={'Cache-Control': 'private, no-store'})

    @api.post('/api/writing/classes')
    def create_class(data: Title, request: Request):
        return service.create_class(user(request), data.title)

    @api.post('/api/writing/classes/join')
    def join_class(data: Join, request: Request):
        return service.join(user(request), data.code)

    @api.post('/api/writing/assignments')
    def create_assignment(data: Assignment, request: Request):
        return service.save_assignment(user(request), data.model_dump(mode='json'))

    @api.put('/api/writing/assignments/{key}')
    def update_assignment(key: str, data: Assignment, request: Request):
        return service.save_assignment(user(request), data.model_dump(mode='json'), key)

    @api.get('/api/writing/assignments/{key}')
    def get_assignment(key: str, request: Request):
        current=user(request)
        return service.assignment_view(current,service.assignment(current,key))

    @api.post('/api/writing/assignments/{key}/enter')
    def enter_exam(key: str, data: ExamEntry, request: Request):
        return service.enter_exam(user(request),key,data.password)

    @api.patch('/api/writing/assignments/{key}/exam-password')
    def exam_password(key: str, data: ExamPassword, request: Request):
        return service.exam_password(user(request),key,data.password,data.revision)

    @api.delete('/api/writing/assignments/{key}')
    def delete_assignment(key: str, request: Request, revision: int):
        return service.delete_assignment(user(request), key, revision)

    @api.get('/api/writing/assignments/{key}/draft')
    def draft(key: str, request: Request):
        return service.draft(user(request), key)

    @api.put('/api/writing/assignments/{key}/draft')
    def save_draft(key: str, data: Draft, request: Request):
        return service.save_draft(user(request), key, data.model_dump())

    @api.post('/api/writing/assignments/{key}/submit')
    def submit(key: str, data: Submit, request: Request):
        return service.submit(user(request), key, data.revision, data.requestId)

    @api.get('/api/writing/submissions/{key}')
    def submission(key: str, request: Request):
        return service.review(user(request), key)

    @api.put('/api/writing/submissions/{key}/review')
    def save_review(key: str, data: Review, request: Request):
        return service.save_review(user(request), key, data.model_dump())

    @api.post('/api/writing/submissions/{key}/publish')
    async def publish(key: str, data: Publish, request: Request):
        import asyncio
        current=user(request)
        result=await asyncio.to_thread(service.publish,current,key,data.revision,data.reason)
        if current.get('writingProject'):
            from latex_backend.writing.results import write_results
            _,store,_=await gateway.project_workspace(current['writingProject'],current)
            result['resultFiles']=await asyncio.to_thread(write_results,gateway,service,current,store,key,result)
        return result

    @api.post('/api/writing/assets')
    async def upload_image(request: Request):
        current = user(request)
        data = await request.body()
        if len(data) > 2 * 1024 * 1024:
            raise Problem('Ảnh đề bài tối đa 2 MiB.', 413)
        try:
            with Image.open(io.BytesIO(data)) as image:
                if image.format not in {'PNG', 'JPEG', 'WEBP'} or image.width * image.height > 12000000:
                    raise Problem('Chỉ dùng PNG/JPEG/WebP, tối đa 12 triệu pixel.')
                image.load()
                output = io.BytesIO()
                image.convert('RGB').save(output, format='PNG')
                payload = output.getvalue()
                if len(payload) > 4 * 1024 * 1024:
                    raise Problem('Ảnh sau chuẩn hóa quá lớn.', 413)
        except (UnidentifiedImageError, OSError, Image.DecompressionBombError):
            raise Problem('Tệp ảnh không hợp lệ.') from None
        key = uuid.uuid4().hex
        gateway.database.writing_assets.insert_one({'_id': key, 'ownerId': current['id'], 'data': Binary(payload)})
        return {'id': key}

    @api.get('/api/writing/assets/{key}')
    def image(key: str, request: Request):
        current = user(request)
        asset = gateway.database.writing_assets.find_one({'_id': key})
        if not asset:
            raise Problem('Không tìm thấy ảnh.', 404)
        allowed = asset['ownerId'] == current['id']
        if not allowed:
            for assignment in gateway.database.writing_assignments.find({'imageId': key}):
                try:
                    task=service.assignment(current, assignment['id'])
                    from latex_backend.writing.exams import require_entry
                    require_entry(gateway.database,current,task)
                    allowed = True
                    break
                except Problem:
                    pass
        if not allowed:
            raise Problem('Bạn không có quyền xem hình.', 404)
        return Response(bytes(asset['data']), media_type='image/png', headers={'Cache-Control': 'private, no-store'})

    @api.post('/api/writing/latex')
    async def latex(data: Latex, request: Request):
        current = user(request)
        if bool(data.assignmentId) == bool(data.submissionId):
            raise Problem('Chọn đúng một bản viết hoặc bản nộp.')
        source, assignment = service.latex(current, data.assignmentId, data.submissionId, data.report)
        if data.useDraftSource:
            if not data.assignmentId or data.submissionId or data.report:
                raise Problem('Nhánh source tự sửa chỉ dùng trong bản viết của chính học viên.', 403)
            own_draft = service.draft(current, data.assignmentId)
            source = own_draft['latexSources'].get(str(assignment['tasks'][0])) or source
        if not data.createProject:
            return {'source': source, 'notice': 'LaTeX là nhánh trình bày; sửa source không tự đổi văn bản dùng để chấm.'}
        if current.get('writingProject'):
            from latex_backend.writing.artifacts import render
            return await render(gateway, current, assignment, source, data.report, data.submissionId)
        _, store = await gateway.workspace(current)
        # Always generate escaped LaTeX from canonical answers, not executable
        # student-supplied source in the Tutor's account.
        import asyncio
        def create():
            project = store.create(('IELTS · ' + assignment['title'])[:120])
            store.save(project['id'], 'main.tex', source)
            image_id = assignment.get('imageId')
            if image_id:
                if image_id == 'sample-commuting':
                    from latex_backend.writing.assets import commuting_png
                    payload = commuting_png()
                else:
                    asset = gateway.database.writing_assets.find_one({'_id': image_id})
                    payload = bytes(asset['data']) if asset else None
                if payload:
                    with store.shared_files.edit(project['id']):
                        atomic_write(store.path(project['id'], 'prompt-image.png'), payload)
            gateway.sharing.register(project['id'], current['id'])
            for task, attachment in assignment.get('promptFiles', {}).items():
                if attachment['kind'] in {'pdf', 'image'}:
                    record = gateway.database.writing_prompt_files.find_one({'_id': attachment['id']})
                    if not record:
                        raise Problem('Tệp đề không còn khả dụng.', 409)
                    extension = 'pdf' if attachment['kind'] == 'pdf' else 'png'
                    with store.shared_files.edit(project['id']):
                        atomic_write(store.path(project['id'], 'question-task-' + task + '.' + extension), bytes(record['data']))
            return store.info(project['id'])
        project = await asyncio.to_thread(create)
        return {'source': source, 'project': project}
