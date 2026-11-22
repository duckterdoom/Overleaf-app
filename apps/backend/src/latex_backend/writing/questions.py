"""Reusable tutor-owned questions. Public is the authenticated tutor community."""
import copy
import hashlib
import re
from typing import Annotated, Literal

from fastapi import Request, Query
from pydantic import Field, StringConstraints
from pymongo import ReturnDocument

from latex_backend.storage.store import Problem
from latex_backend.writing.service import identifier, now, public
from latex_backend.writing.rubrics import assignment_catalog
from latex_backend.writing.assessment import category_matches


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
        fields = ['title', 'description', 'category', 'variant', 'tasks', 'profiles', 'prompts', 'writingScope', 'purpose', 'scoring', 'assessmentCategories', 'kind', 'durationMinutes', 'allowLate']
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
        from latex_backend.writing.points import prepare_points
        prepare_points(value)
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
    def questions(request: Request, page: int = Query(default=0, ge=0, le=10000),
                  scope: Literal['all', 'mine', 'public'] = 'all',
                  search: str = Query(default='', max_length=160),
                  category: Literal['ielts', 'report', 'research'] | None = None,
                  document_type: Literal['report','research'] | None = Query(default=None, alias='documentType'),
                  task: int | None = Query(default=None, ge=1, le=2),
                  profile: str = Query(default='', pattern=r'^[a-zA-Z0-9_-]{0,40}$'),
                  section: str = Query(default='', pattern=r'^[a-zA-Z0-9_-]{0,40}$'),
                  sections: list[Annotated[str, StringConstraints(pattern=r'^[a-zA-Z0-9_-]{1,40}$')]] = Query(default=[], max_length=20)):
        current = user(request)
        service.role(current, 'tutor')
        selector = {'deleted': {'$ne': True}, '$or': [{'ownerId': current['id']}, {'visibility': 'public'}]}
        if scope == 'mine':
            selector['ownerId'] = current['id']
        elif scope == 'public':
            selector.update(ownerId={'$ne': current['id']}, visibility='public')
        # Facets use exactly the same visibility guard, before pagination. Keep
        # prompts and attachment payloads out of this metadata-only scan.
        from latex_backend.writing.question_taxonomy import facets
        taxonomy = facets(service.db.writing_questions.find(selector, {
            'content.category':1, 'content.tasks':1, 'content.profiles':1,
            'content.rubricCatalog':1}), service.settings(current)['catalog'])
        if search.strip():
            selector['title'] = {'$regex': re.escape(search.strip()), '$options': 'i'}
        if category:
            selector['content.category'] = {'$in': [category, None]} if category == 'ielts' else {'$in': ['research','report']} if category == 'research' else category
        if document_type:
            if category and not category_matches(document_type, category):
                raise Problem('Choose a document type within the assessment group.')
            selector['content.category'] = document_type
        if task:
            selector['content.tasks'] = task
        chosen = list(dict.fromkeys([*sections, *([section] if section else [])]))
        if profile or chosen:
            candidates = [p for p in taxonomy if (not profile or p['key']==profile)
                and category_matches(p['category'], category) and (not document_type or p['category']==document_type) and (not task or p['task']==task)]
            conditions = [{f"content.profiles.{p['task']}":p['key'], 'content.tasks':p['task'],
                **{f"content.rubricCatalog.{p['key']}.checks.{key}.0":{'$exists':True} for key in chosen}}
                for p in candidates if set(chosen)<={s['key'] for s in p['sections']}]
            selector['$and'] = [{'$or': conditions}] if conditions else [{'_id': {'$in': []}}]
        rows = service.db.writing_questions.find(selector).sort('updatedAt', -1).skip(page * 50).limit(50)
        items = [public(row) for row in rows]
        if scope == 'public':
            saved = {row['sourceQuestionId'] for row in service.db.writing_questions.find({
                'ownerId': current['id'], 'sourceQuestionId': {'$in': [item['id'] for item in items]},
                'deleted': {'$ne': True}}, {'sourceQuestionId': 1})}
            for item in items:
                item['savedToLibrary'] = item['id'] in saved
        return {'items': items, 'page': page, 'total': service.db.writing_questions.count_documents(selector), 'taxonomy': taxonomy}

    @api.post('/api/writing/questions/{key}/copy')
    def copy_to_library(key: str, request: Request):
        current = user(request)
        original = question_for(service, current, key)
        if original['ownerId'] == current['id']:
            return public(original)
        copy_id = 'saved-' + hashlib.sha256((current['id'] + ':' + key).encode()).hexdigest()[:32]
        prior = service.db.writing_questions.find_one({'_id': copy_id})
        if prior:
            if prior.get('deleted'):
                prior = service.db.writing_questions.find_one_and_update({'_id': copy_id},
                    {'$set': {'deleted': False, 'visibility': 'private', 'updatedAt': now()}, '$inc': {'revision': 1}},
                    return_document=ReturnDocument.AFTER)
            return public(prior)
        content = copy.deepcopy(original['content'])
        content['promptFiles'] = copy_prompt_assets(service, current, content.get('promptFiles', {}))
        row = {'id': copy_id, 'ownerId': current['id'], 'ownerName': current['name'],
               'title': original['title'], 'content': content, 'visibility': 'private',
               'sourceQuestionId': key, 'sourceOwnerName': original['ownerName'],
               'revision': 1, 'updatedAt': now()}
        saved = service.db.writing_questions.find_one_and_update({'_id': copy_id},
            {'$setOnInsert': row}, upsert=True, return_document=ReturnDocument.AFTER)
        return public(saved)

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


def copy_prompt_assets(service, user, prompt_files):
    from latex_backend.writing.prompts import metadata
    files = {}
    for task, attachment in prompt_files.items():
        original = service.db.writing_prompt_files.find_one({'_id': attachment['id']})
        if not original:
            raise Problem('Question attachment is unavailable.', 409)
        asset = copy.deepcopy(original)
        asset['_id'] = asset['id'] = identifier()
        asset['ownerId'], asset['projectId'] = user['id'], user.get('writingProject')
        service.db.writing_prompt_files.insert_one(asset)
        files[task] = metadata(asset)
    return files


def copy_question_assets(service, user, question_id):
    row = question_for(service, user, question_id)
    return {task: asset['id'] for task, asset in
            copy_prompt_assets(service, user, row['content'].get('promptFiles', {})).items()}


def save_assignment_question(service, user, assignment):
    key = 'assignment-' + assignment['id']
    if not assignment['published']:
        # Incomplete drafts must not appear in the public question library.
        service.db.writing_questions.update_one({'_id': key},
            {'$set': {'visibility': 'private'}, '$inc': {'revision': 1}})
        return
    fields = ['title', 'description', 'category', 'variant', 'tasks', 'profiles', 'prompts',
              'writingScope', 'purpose', 'rubricCatalog', 'scoring', 'assessmentCategories']
    content = {field: copy.deepcopy(assignment[field]) for field in fields if field in assignment}
    content['promptFiles'] = copy_prompt_assets(service, user, assignment.get('promptFiles', {}))
    service.db.writing_questions.update_one({'_id': key}, {
        '$set': {'id': key, 'ownerId': user['id'], 'ownerName': user['name'],
                 'title': assignment['title'], 'visibility': assignment['libraryVisibility'],
                 'sourceAssignmentId': assignment['id'], 'content': content,
                 'updatedAt': now(), 'deleted': False},
        '$inc': {'revision': 1}}, upsert=True)
