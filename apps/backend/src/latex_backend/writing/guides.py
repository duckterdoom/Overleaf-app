"""Tutor-scoped teaching guides; students read explicitly enrolled class publications."""
import copy
import hashlib
import json
import re
from functools import lru_cache
from typing import Annotated, Literal

from fastapi import Query, Request
from pydantic import Field, StringConstraints
from pymongo.errors import DuplicateKeyError

from latex_backend.core.config import RESOURCES
from latex_backend.storage.store import Problem
from latex_backend.writing.assessment import assessment_group, category_matches
from latex_backend.writing.service import identifier, now

RESOURCE = RESOURCES / 'writing/guides'


@lru_cache(maxsize=1)
def defaults():
    return json.loads((RESOURCE / 'index.json').read_text(encoding='utf-8'))


def scope_for(current):
    return 'project:' + current['writingProject'] if current.get('writingProject') else 'tutor:' + current['id']


def builtin_rows(current):
    scope = scope_for(current)
    prefix = hashlib.sha256(scope.encode()).hexdigest()[:24]
    return [{**row, 'category':'ielts', 'id': prefix + '-' + row['key'], 'scope': scope, 'projectId': current.get('writingProject'),
             'revision': 0, 'classIds': [], 'builtin': True, 'ownerName': 'Default teaching guide'} for row in defaults()]


def student_courses(service, current):
    ids = service.db.writing_memberships.distinct('classId', {'studentId': current['id']})
    return set(service.db.writing_classes.distinct('_id', {'_id': {'$in': ids}, 'projectId': current.get('writingProject')}))


def permitted(service, current, row):
    if not row or row.get('deleted') or row.get('projectId') != current.get('writingProject'):
        return False
    if current['role'] == 'tutor':
        return row['scope'] == scope_for(current)
    return bool(student_courses(service, current).intersection(row.get('classIds', [])))


def find(service, current, key):
    row = service.db.writing_guides.find_one({'_id': key})
    if not row and current['role'] == 'tutor':
        row = next((r for r in builtin_rows(current) if r['id'] == key), None)
    if not permitted(service, current, row):
        raise Problem('Writing guide not found or not shared with your class.', 404)
    return row


def default_sample(row):
    match = next((item for item in defaults() if row.get('builtin') and row.get('id', '').endswith('-' + item['key'])), None)
    return (RESOURCE / match['sampleFile']).read_text(encoding='utf-8') if match and match.get('sampleFile') else ''


def builtin_content(row):
    """Refresh unchanged saved defaults, while preserving any tutor text edits."""
    if not row.get('builtin') or 'content' not in row:
        return None
    baseline = row.get('baseContentSha256') or row.get('sourceSha256')
    if not baseline or hashlib.sha256(row['content'].encode('utf-8')).hexdigest() != baseline:
        return None
    template = next((item for item in defaults() if row.get('id', '').endswith('-' + item['key'])), None)
    return (RESOURCE / template['file']).read_text(encoding='utf-8') if template else None


def response(row, current, detail=False):
    value = {k: copy.deepcopy(v) for k, v in row.items() if k not in {'_id', 'scope', 'file', 'key', 'deleted', 'content', 'sampleContent', 'sampleFile'}}
    value.setdefault('category','ielts')
    if current['role'] != 'tutor':
        value.pop('classIds', None)
    sample = row.get('sampleContent') if 'sampleContent' in row else (default_sample(row) if row.get('hasSample') is not False else '')
    if row.get('sampleFile') and 'sampleContent' not in row:
        sample = (RESOURCE / row['sampleFile']).read_text(encoding='utf-8')
    value['hasSample'] = bool(sample) or bool(row.get('hasSample'))
    if detail:
        value['sampleContent'] = sample or ''
        refreshed = builtin_content(row)
        value['content'] = refreshed if refreshed is not None else row['content'] if 'content' in row else (RESOURCE / row['file']).read_text(encoding='utf-8')
    return value


def register_guides(api, service, user):
    from latex_backend.writing.api import Model

    class Guide(Model):
        category: Literal['ielts','report','research'] = 'ielts'
        title: str = Field(min_length=1, max_length=160)
        summary: str = Field(default='', max_length=1000)
        task: Literal[1, 2]
        profiles: list[Annotated[str, StringConstraints(pattern=r'^[a-z0-9_-]{1,80}$')]] = Field(min_length=1, max_length=10)
        content: str = Field(min_length=1, max_length=150000)
        sampleContent: str | None = Field(default=None, max_length=150000)
        classIds: list[Annotated[str, StringConstraints(max_length=100)]] = Field(default_factory=list, max_length=100)
        revision: int = Field(default=0, ge=0)

    def save(current, data, key=None):
        service.role(current, 'tutor')
        prior = find(service, current, key) if key else None
        if not data.title.strip() or not data.content.strip():
            raise Problem('Enter a title and guide content.')
        catalog = service.settings(current)['catalog']
        if any(p not in catalog or catalog[p]['task'] != data.task or assessment_group(catalog[p].get('category'))!=assessment_group(data.category) for p in data.profiles):
            raise Problem('Guide types must match the selected assessment type and task.')
        if not key and data.revision != 0:
            raise Problem('A new guide must start at revision zero.')
        for course_id in set(data.classIds):
            course = service.classroom(current, course_id)
            if not service.tutor(current, course):
                raise Problem('Only a course tutor can share guides to this course.', 403)
        scope = scope_for(current)
        if not prior and service.db.writing_guides.count_documents({'scope': scope, 'deleted': {'$ne': True}}) >= 200:
            raise Problem('Keep at most 200 custom guides per workspace.')
        key = key or identifier()
        row = {**data.model_dump(), '_id': key, 'id': key, 'scope': scope, 'projectId': current.get('writingProject'),
               'title': data.title.strip(), 'classIds': sorted(set(data.classIds)), 'profiles': list(dict.fromkeys(data.profiles)),
               'revision': data.revision + 1, 'updatedAt': now(), 'ownerName': current['name'],
               'builtin': bool(prior and prior.get('builtin')), 'sourceUrl': prior.get('sourceUrl') if prior else None,
               'sourceSha256': prior.get('sourceSha256') if prior else None}
        row['sampleContent'] = data.sampleContent if data.sampleContent is not None else (prior.get('sampleContent', default_sample(prior)) if prior else '')
        row['hasSample'] = bool(row['sampleContent'])
        if row['builtin']:
            template = next((item for item in defaults() if key.endswith('-' + item['key'])), None)
            if template:
                row['baseContentSha256'] = hashlib.sha256((RESOURCE / template['file']).read_text(encoding='utf-8').encode('utf-8')).hexdigest()
                row['contentVersion'] = template.get('contentVersion', 1)
        if prior and prior['revision'] != data.revision:
            raise Problem('Guide changed. Reload before saving.', 409)
        if not prior or prior['revision'] == 0:
            try:
                service.db.writing_guides.insert_one(row)
            except DuplicateKeyError:
                raise Problem('Guide changed. Reload before saving.', 409) from None
        elif not service.db.writing_guides.replace_one({'_id': key, 'scope': scope, 'revision': data.revision, 'deleted': {'$ne': True}}, row).matched_count:
            raise Problem('Guide changed. Reload before saving.', 409)
        return response(row, current, True)

    @api.get('/api/writing/guides')
    def list_guides(request: Request, page: int = Query(default=0, ge=0, le=10000),
                    search: str = Query(default='', max_length=160), task: int | None = Query(default=None, ge=1, le=2),
                    profile: str = Query(default='', max_length=80), classId: str | None = Query(default=None, max_length=100),
                    category: Literal['ielts','report','research'] | None = None):
        current = user(request)
        if current['role'] == 'tutor':
            if classId:
                service.classroom(current, classId)
            rows = {r['id']: r for r in builtin_rows(current)}
            for row in service.db.writing_guides.find({'scope': scope_for(current), '$or': [
                    {'deleted': {'$ne': True}}, {'_id': {'$in': list(rows)}}]}, {'content': 0, 'sampleContent': 0}):
                rows[row['id']] = row
            rows = [r for r in rows.values() if not r.get('deleted')]
        else:
            courses = student_courses(service, current)
            if classId and classId not in courses:
                raise Problem('Course or group not found, or you are not enrolled.', 404)
            selector = {'projectId': current.get('writingProject'), 'deleted': {'$ne': True},
                        'classIds': {'$in': list(courses if not classId else {classId})}}
            if category:
                selector['category'] = {'$in':['ielts',None]} if category=='ielts' else {'$in':['report','research']} if category=='research' else category
            if task:
                selector['task'] = task
            if profile:
                selector['profiles'] = profile
            if search:
                selector['$or'] = [{'title': {'$regex': re.escape(search), '$options': 'i'}},
                                   {'summary': {'$regex': re.escape(search), '$options': 'i'}}]
            total = service.db.writing_guides.count_documents(selector)
            rows = service.db.writing_guides.find(selector, {'content': 0, 'sampleContent': 0}).sort([('task', 1), ('title', 1), ('id', 1)]).skip(page * 24).limit(24)
            return {'items': [response(r, current) for r in rows], 'total': total, 'page': page}
        rows = [r for r in rows if category_matches(r.get('category','ielts'), category) and (not task or r['task'] == task) and (not profile or profile in r['profiles'])
                and (not classId or classId in r['classIds']) and search.casefold() in (r['title'] + ' ' + r['summary']).casefold()]
        rows.sort(key=lambda r: (r['task'], r['title'].casefold(), r['id']))
        return {'items': [response(r, current) for r in rows[page * 24:(page + 1) * 24]], 'total': len(rows), 'page': page}

    @api.get('/api/writing/guide-assets/{filename}')
    def guide_asset(filename: str, request: Request):
        user(request)
        if not re.fullmatch(r'[a-f0-9]{64}\.(?:png|jpg)', filename):
            raise Problem('Guide image not found.', 404)
        path = RESOURCE / 'images' / filename
        if not path.is_file():
            raise Problem('Guide image not found.', 404)
        from fastapi.responses import FileResponse
        return FileResponse(path, media_type='image/png' if filename.endswith('.png') else 'image/jpeg', headers={'X-Content-Type-Options': 'nosniff'})

    @api.get('/api/writing/guides/{key}')
    def get_guide(key: str, request: Request):
        current = user(request)
        return response(find(service, current, key), current, True)

    @api.post('/api/writing/guides')
    def create_guide(data: Guide, request: Request):
        return save(user(request), data)

    @api.put('/api/writing/guides/{key}')
    def update_guide(key: str, data: Guide, request: Request):
        return save(user(request), data, key)

    @api.delete('/api/writing/guides/{key}')
    def delete_guide(key: str, request: Request, revision: int = Query(ge=0)):
        current = user(request)
        service.role(current, 'tutor')
        row = find(service, current, key)
        if row['revision'] != revision:
            raise Problem('Guide changed. Reload before deleting.', 409)
        if revision == 0:
            try:
                service.db.writing_guides.insert_one({**row, '_id': key, 'deleted': True, 'classIds': [], 'revision': 1})
            except DuplicateKeyError:
                raise Problem('Guide changed. Reload before deleting.', 409) from None
        elif not service.db.writing_guides.update_one({'_id': key, 'scope': scope_for(current), 'revision': revision, 'deleted': {'$ne': True}},
                {'$set': {'deleted': True, 'classIds': []}, '$inc': {'revision': 1}}).matched_count:
            raise Problem('Guide changed. Reload before deleting.', 409)
        return {'deleted': True}
