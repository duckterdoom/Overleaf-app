"""Explicit course enrollment, independent from document collaboration."""
from fastapi import Request
from pydantic import Field
from pymongo import ReturnDocument

from latex_backend.storage.store import Problem
from latex_backend.writing.service import public


def register_courses(api, service, user):
    from typing import Literal
    from latex_backend.writing.api import Model, Title

    class Course(Title):
        kind: Literal['course', 'group'] = 'course'
        revision: int = Field(default=0, ge=0)

    class Member(Model):
        email: str = Field(min_length=3, max_length=254)

    class MemberDetails(Model):
        displayName: str = Field(default='', max_length=160)
        studentCode: str = Field(default='', max_length=80)
        note: str = Field(default='', max_length=2000)
        revision: int = Field(default=0, ge=0)

    def owned(current, key):
        row = service.classroom(current, key)
        service.role(current, 'tutor')
        if row['tutorId'] != current['id'] and not current.get('writingProject'):
            raise Problem('Only the course owner can manage its membership.', 403)
        return row

    def version(key, revision):
        if revision == 0:
            return {'_id': key, '$or': [{'revision': 0}, {'revision': {'$exists': False}}]}
        return {'_id': key, 'revision': revision}

    @api.post('/api/writing/courses')
    def create(data: Course, request: Request):
        current = user(request)
        row = service.create_class(current, data.title)
        service.db.writing_classes.update_one({'_id': row['id']}, {'$set': {'kind': 'course', 'revision': 1, 'enrollment': 'explicit'}})
        return {**row, 'kind': 'course', 'revision': 1}

    @api.get('/api/writing/courses/{key}')
    def details(key: str, request: Request):
        current = user(request)
        service.classroom(current, key)
        return next(row for row in service.classes(current) if row['id'] == key)

    @api.put('/api/writing/courses/{key}')
    def update(key: str, data: Course, request: Request):
        current = user(request)
        owned(current, key)
        if not data.title.strip():
            raise Problem('Enter a course name.')
        row = service.db.writing_classes.find_one_and_update(version(key, data.revision),
            {'$set': {'title': data.title.strip(), 'kind': 'course'}, '$inc': {'revision': 1}}, return_document=ReturnDocument.AFTER)
        if not row:
            raise Problem('Course changed. Reload before saving.', 409)
        return public(row)

    @api.delete('/api/writing/courses/{key}')
    def delete(key: str, revision: int, request: Request):
        current = user(request)
        owned(current, key)
        if service.db.writing_assignments.count_documents({'classId': key, 'deleted': {'$ne': True}}):
            raise Problem('Keep courses with exercises for access to existing work. Remove unused exercises first.', 409)
        if not service.db.writing_classes.delete_one(version(key, revision)).deleted_count:
            raise Problem('Course changed. Reload before deleting.', 409)
        service.db.writing_memberships.delete_many({'classId': key})
        return {'deleted': True}

    @api.post('/api/writing/courses/{key}/members')
    def add_member(key: str, data: Member, request: Request):
        current = user(request)
        course = owned(current, key)
        account = service.db.users.find_one({'email': data.email.strip().lower()})
        project = course.get('projectId')
        if not account or (not project and account.get('role') != 'student'):
            raise Problem('No registered student found with that email.', 404)
        if project and (not service.gateway.sharing.access(project, account['_id']) or
                service.gateway.sharing.workspace_role(project, account['_id']) != 'student'):
            raise Problem('Share this project with the student before adding them to this course.', 403)
        service.db.writing_memberships.update_one({'_id': key + ':' + account['_id']}, {'$setOnInsert': {
            'classId': key, 'studentId': account['_id']}}, upsert=True)
        return {'added': True}

    @api.patch('/api/writing/courses/{key}/members/{student}')
    def edit_member(key: str, student: str, data: MemberDetails, request: Request):
        owned(user(request), key)
        member_id = key + ':' + student
        if not service.db.writing_memberships.find_one({'_id': member_id, 'classId': key, 'studentId': student}):
            raise Problem('Student membership not found.', 404)
        updated = service.db.writing_memberships.find_one_and_update(version(member_id, data.revision),
            {'$set': {'displayName': data.displayName.strip(), 'studentCode': data.studentCode.strip(), 'note': data.note.strip()},
             '$inc': {'revision': 1}}, return_document=ReturnDocument.AFTER)
        if not updated:
            raise Problem('Student details changed. Reload before saving.', 409)
        return {'id': student, 'displayName': updated['displayName'], 'studentCode': updated['studentCode'],
                'note': updated['note'], 'revision': updated['revision']}

    @api.delete('/api/writing/courses/{key}/members/{student}')
    def remove_member(key: str, student: str, request: Request):
        current = user(request)
        owned(current, key)
        service.db.writing_memberships.delete_one({'_id': key + ':' + student})
        return {'removed': True}
