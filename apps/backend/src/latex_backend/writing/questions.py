"""Reusable tutor-owned questions. Public is the authenticated tutor community."""
import copy

from fastapi import Request, Query
from pydantic import Field
from pymongo import ReturnDocument

from latex_backend.storage.store import Problem
from latex_backend.writing.service import identifier, now, public
from latex_backend.writing.rubrics import assignment_catalog


def question_for(service, user, key):
    service.role(user, 'tutor')
    row = service.db.writing_questions.find_one({'_id': key, 'deleted': {'$ne': True}})
    if not row or (row['ownerId'] != user['id'] and row['visibility'] != 'public'):
        raise Problem('Question not found.', 404)
    return row


def register_questions(api, service, user):
    from typing import Literal
    from latex_backend.writing.api import Assignment, Model

    class Question(Model):
        visibility: Literal['private', 'public'] = 'private'
        content: Assignment
        revision: int = Field(default=0, ge=0)

    def save(current, data, key=None):
        service.role(current, 'tutor')
        prior = question_for(service, current, key) if key else None
        if prior and prior['ownerId'] != current['id']:
            raise Problem('Only the author can modify a question. Reuse it as your own exercise.', 403)
        content = data.content.model_dump(mode='json')
        if not content['title'].strip():
            raise Problem('Enter a question title.')
        if content['category'] != 'ielts' and content['tasks'] != [1]:
            raise Problem('Reports and research papers use a single document task.')
        fields = ['title', 'category', 'variant', 'tasks', 'profiles', 'prompts', 'writingScope']
        value = {field: content[field] for field in fields}
        value['rubricCatalog'] = assignment_catalog(content['rubricCatalog'] or service.settings(current)['catalog'], content['writingScope'])
        value['promptFiles'] = {}
        for task in value['tasks']:
            profile = value['rubricCatalog'].get(value['profiles'].get(str(task)))
            if not profile or profile['task'] != task or profile.get('category', 'ielts') != value['category'] or (
                    value['category'] == 'ielts' and task == 1 and profile['family'] != value['variant']):
                raise Problem('Select an appropriate rubric for each task.')
            if not profile['checks']:
                raise Problem('This rubric has no criteria for the selected writing scope.')
            asset_id = content['promptFiles'].get(str(task))
            if asset_id:
                from latex_backend.writing.prompts import metadata
                asset = service.db.writing_prompt_files.find_one({'_id': asset_id})
                if not asset or asset['ownerId'] != current['id']:
                    raise Problem('Only your own imported source can be published as a question.', 403)
                value['promptFiles'][str(task)] = metadata(asset)
            if not value['prompts'].get(str(task), '').strip() and not asset_id:
                raise Problem('Enter a question or import its source.')
        value['rubricCatalog'] = {value['profiles'][str(task)]: value['rubricCatalog'][value['profiles'][str(task)]] for task in value['tasks']}
        key = key or identifier()
        row = {'_id': key, 'id': key, 'ownerId': current['id'], 'ownerName': current['name'],
               'title': value['title'], 'visibility': data.visibility, 'content': value,
               'revision': data.revision + 1, 'updatedAt': now()}
        if prior:
            updated = service.db.writing_questions.find_one_and_replace(
                {'_id': key, 'revision': data.revision}, row, return_document=ReturnDocument.AFTER)
            if not updated:
                raise Problem('The question changed. Reload before saving.', 409)
        else:
            service.db.writing_questions.insert_one(row)
        return public(row)

    @api.get('/api/writing/questions')
    def questions(request: Request, page: int = Query(default=0, ge=0, le=10000)):
        current = user(request)
        service.role(current, 'tutor')
        selector = {'deleted': {'$ne': True}, '$or': [{'ownerId': current['id']}, {'visibility': 'public'}]}
        rows = service.db.writing_questions.find(selector).sort('updatedAt', -1).skip(page * 50).limit(50)
        return {'items': [public(row) for row in rows], 'page': page, 'total': service.db.writing_questions.count_documents(selector)}

    @api.post('/api/writing/questions')
    def create(data: Question, request: Request):
        return save(user(request), data)

    @api.put('/api/writing/questions/{key}')
    def update(key: str, data: Question, request: Request):
        return save(user(request), data, key)

    @api.delete('/api/writing/questions/{key}')
    def delete(key: str, revision: int, request: Request):
        current = user(request)
        row = question_for(service, current, key)
        if row['ownerId'] != current['id']:
            raise Problem('Only the author can delete a question.', 403)
        if not service.db.writing_questions.update_one({'_id': key, 'revision': revision},
                {'$set': {'deleted': True}, '$inc': {'revision': 1}}).matched_count:
            raise Problem('The question changed. Reload before deleting.', 409)
        return {'deleted': True}


def copy_question_assets(service, user, question_id):
    row = question_for(service, user, question_id)
    files = {}
    for task, metadata in row['content'].get('promptFiles', {}).items():
        original = service.db.writing_prompt_files.find_one({'_id': metadata['id']})
        if not original:
            raise Problem('Question attachment is unavailable.', 409)
        asset = copy.deepcopy(original)
        asset['_id'] = asset['id'] = identifier()
        asset['ownerId'], asset['projectId'] = user['id'], user.get('writingProject')
        service.db.writing_prompt_files.insert_one(asset)
        files[task] = asset['id']
    return files
