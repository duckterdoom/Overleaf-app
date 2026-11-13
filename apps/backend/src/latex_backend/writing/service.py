import copy
from datetime import datetime, timedelta, timezone
import hashlib
import json
import math
import re
import secrets
import unicodedata
import uuid

from pymongo import ReturnDocument
from pymongo.errors import DuplicateKeyError
from latex_backend.core.config import RESOURCES
from latex_backend.storage.store import Problem
from latex_backend.documents.word import tex_escape

CATALOG = json.loads((RESOURCES / 'writing/catalog.json').read_text(encoding='utf-8'))


def default_catalog():
    value=copy.deepcopy(CATALOG)
    for category,label in [('report','Report'),('research','Research paper')]:
        value[category]={'category':category,'family':category,'task':1,'label':label,'focus':[],
            'sectionTitles':{'intro':'Introduction','body':'Body / evidence','conclusion':'Conclusion','whole':'Whole paper'},
            'checks':{kind:[{'id':category+'_'+kind,'text':text,'codes':['CONTENT']}]
                for kind,text in [('intro','State the problem, objectives and scope clearly.'),('body','Support arguments with appropriate evidence, methods and sources.'),
                    ('conclusion','Conclusions address the objectives, limitations and recommendations.'),('whole','Use consistent structure, citations and presentation.')]}}
    return value


def now():
    return datetime.now(timezone.utc).isoformat()


def word_count(text):
    return len(re.findall(r"[^\W_]+(?:['’\-][^\W_]+)*", unicodedata.normalize('NFC', text)))


def public(row):
    return {key: value for key, value in row.items() if key not in {'_id','examSecret'}}


def identifier():
    return uuid.uuid4().hex


class Writing:
    def __init__(self, gateway):
        self.gateway, self.db = gateway, gateway.database

    def assignment_view(self,user,row,entered=None):
        from latex_backend.writing.exams import is_exam,admission
        value=public(row)
        if is_exam(row) and user['role']!='tutor':
            entered=bool(admission(self.db,user,row)) if entered is None else entered
            value['examEntered']=entered
            if not entered:
                for field in ['prompts','promptFiles','rubricCatalog','imageId']:value.pop(field,None)
        return value

    def enter_exam(self,user,key,password):
        from latex_backend.storage.state import State
        from latex_backend.writing.exams import is_exam,require_open,utc_now
        self.role(user,'student')
        row=self.assignment(user,key)
        if not is_exam(row):raise Problem('This exercise is not an examination.')
        require_open(row)
        self.gateway.accounts.throttle(('exam-entry',user['id'],key))
        if not row.get('examSecret') or not State.matches(row['examSecret'],password):raise Problem('Incorrect examination password.',403)
        self.db.writing_exam_entries.update_one({'_id':key+':'+user['id']},{'$setOnInsert':{
            'projectId':user.get('writingProject'),'assignmentId':key,'studentId':user['id'],'enteredAt':utc_now().isoformat()}},upsert=True)
        draft=self.draft(user,key)
        return {'assignment':self.assignment_view(user,row,True),'draft':draft}

    def exam_password(self,user,key,password,revision):
        from latex_backend.storage.state import State
        from latex_backend.writing.exams import is_exam
        row=self.assignment(user,key)
        if not self.tutor(user,row) or not is_exam(row):raise Problem('Only a tutor can change an examination password.',403)
        secret=State.password(password)
        updated=self.db.writing_assignments.find_one_and_update({'_id':key,'revision':revision},
            {'$set':{'examSecret':secret},'$inc':{'revision':1}},return_document=ReturnDocument.AFTER)
        if not updated:raise Problem('The examination changed. Reload before changing its password.',409)
        return public(updated)

    def settings(self, user):
        project = user.get('writingProject')
        project = project or 'tutor:' + user['id']
        row = self.db.writing_project_settings.find_one({'_id': project})
        catalog=copy.deepcopy(row['catalog'] if row else default_catalog())
        if row and row.get('schemaVersion',1)<2:
            for key,profile in default_catalog().items():
                if profile.get('category') in {'report','research'}:catalog.setdefault(key,profile)
        return {'revision': row['revision'] if row else 0,
                'catalog': catalog}

    def project_students(self,user):
        project=user.get('writingProject')
        if not project or user['role']!='tutor':return []
        ids=set(self.db.invitations.distinct('user_id',{'project':project})) | set(self.db.link_visitors.distinct('user_id',{'project':project}))
        ids.add(self.gateway.sharing.owner(project))
        result=[]
        for uid in ids:
            if uid and self.gateway.sharing.access(project,uid) and self.gateway.sharing.workspace_role(project,uid)=='student':
                account=self.gateway.accounts.user(uid)
                if account:result.append({'id':uid,'name':account['name']})
        return result

    def save_settings(self, user, data):
        self.role(user, 'tutor')
        if user.get('writingProject') and user.get('projectPermission') not in {'owner', 'editor'}:
            raise Problem('Project editing access is required.', 403)
        current = self.settings(user)
        catalog = copy.deepcopy(data.get('catalog') if data.get('catalog') is not None else current['catalog'])
        if data.get('catalog') is None and data.get('checks') is None:
            raise Problem('Rubric configuration is missing.')
        if data.get('checks') is not None:
            if set(data['checks']) != set(catalog):
                raise Problem('The assessment profiles changed. Reload before continuing.')
            for key,sections in data['checks'].items():catalog[key]['checks']=sections
        from latex_backend.writing.rubrics import validate_catalog
        validate_catalog(catalog)
        project = user.get('writingProject') or 'tutor:' + user['id']
        row = {'_id': project, 'catalog': catalog, 'revision': data['revision'] + 1, 'schemaVersion':2}
        if current['revision'] != data['revision']:
            raise Problem('Settings changed. Reload before saving.', 409)
        if data['revision'] == 0:
            try:
                self.db.writing_project_settings.insert_one(row)
            except DuplicateKeyError:
                raise Problem('Settings changed. Reload before saving.', 409)
        elif not self.db.writing_project_settings.replace_one({'_id':project,'revision':data['revision']}, row).matched_count:
            raise Problem('Settings changed. Reload before saving.', 409)
        return {'revision': row['revision'], 'catalog': catalog}

    @staticmethod
    def role(user, role):
        if user.get('role') != role:
            raise Problem('Your role does not allow this action.', 403)

    @staticmethod
    def scoped(user, row):
        return row and row.get('projectId') == user.get('writingProject')

    def tutor(self, user, row):
        return self.scoped(user, row) and not row.get('practiceOwner') and not row.get('assignment', {}).get('practiceOwner') and user['role'] == 'tutor' and (
            bool(user.get('writingProject')) or row.get('tutorId') == user['id'])

    def legacy(self, user):
        return {'classes': self.db.writing_classes.count_documents({'projectId': None, 'tutorId': user['id']}) if user['role'] == 'tutor' else 0,
                'assignments': self.db.writing_assignments.count_documents({'projectId': None,
                    **({'tutorId': user['id']} if user['role'] == 'tutor' else {'practiceOwner': user['id']})})}

    def import_legacy(self, user):
        project = user.get('writingProject')
        if not project:
            raise Problem('Choose the destination project before importing existing data.')
        # Claim each class/assignment conditionally. A competing project cannot
        # steal a record already claimed by another import. Retrying repairs children.
        classes = []
        if user['role'] == 'tutor':
            for row in self.db.writing_classes.find({'tutorId': user['id'], 'projectId': {'$in': [None, project]}}):
                if self.db.writing_classes.update_one({'_id': row['_id'], 'projectId': {'$in': [None, project]}},
                        {'$set': {'projectId': project}}).matched_count:
                    classes.append(row['id'])
        ids = []
        selector = {'tutorId': user['id'], 'classId': {'$in': classes}} if user['role'] == 'tutor' else {'practiceOwner': user['id']}
        for row in self.db.writing_assignments.find({**selector, 'projectId': {'$in': [None, project]}}):
            if self.db.writing_assignments.update_one({'_id': row['_id'], 'projectId': {'$in': [None, project]}},
                    {'$set': {'projectId': project}}).matched_count:
                ids.append(row['id'])
        self.db.writing_submissions.update_many({'assignmentId': {'$in': ids}},
            {'$set': {'projectId': project, 'assignment.projectId': project}})
        return {'classes': len(classes), 'assignments': len(ids)}

    def classroom(self, user, classroom):
        row = self.db.writing_classes.find_one({'_id': classroom})
        if not self.scoped(user, row) or not (row['tutorId'] == user['id'] or
                user.get('writingProject') and (user['role'] == 'tutor' or row.get('enrollment') != 'explicit') or
                self.db.writing_memberships.find_one({'_id': classroom + ':' + user['id']})):
            raise Problem('Course or group not found, or you are not enrolled.', 404)
        return row

    def classes(self, user):
        query = {'tutorId': user['id']} if user['role'] == 'tutor' else {'_id': {'$in': self.db.writing_memberships.distinct('classId', {'studentId': user['id']})}}
        if user.get('writingProject') and user['role'] == 'tutor':
            query = {}
        if user.get('writingProject') and user['role'] == 'student':
            query = {'$or': [query, {'enrollment': {'$ne': 'explicit'}}]}
        query = {**query, 'projectId': user.get('writingProject')}
        courses = list(self.db.writing_classes.find(query))
        memberships = list(self.db.writing_memberships.find({'classId': {'$in': [row['id'] for row in courses]}})) if courses else []
        students = {row['id']: row for row in self.db.users.find({'_id': {'$in': list({m['studentId'] for m in memberships})}},
            {'id': 1, 'name': 1})} if memberships else {}
        rosters = {row['id']: [] for row in courses}
        for member in memberships:
            student = students.get(member['studentId'])
            if student:
                rosters[member['classId']].append({'id': student['id'], 'name': student['name']})
        result = []
        for row in courses:
            members = rosters[row['id']]
            value = {**public(row), 'studentCount': len(members)}
            if user['role'] == 'tutor':
                value['students'] = members
            else:
                value.pop('joinCode', None)
            result.append(value)
        return result

    def create_class(self, user, title):
        self.role(user, 'tutor')
        key = identifier()
        row = {'_id': key, 'id': key, 'title': title.strip(), 'tutorId': user['id'],
               'tutorName': user['name'], 'joinCode': secrets.token_hex(5).upper(), 'createdAt': now(), 'projectId': user.get('writingProject')}
        if not row['title']:
            raise Problem('Enter a course or group name.')
        self.db.writing_classes.insert_one(row)
        return public(row)

    def join(self, user, code):
        self.role(user, 'student')
        self.gateway.accounts.throttle(('writing-join', user['id']))
        row = self.db.writing_classes.find_one({'joinCode': code.strip().upper()})
        if not self.scoped(user, row):
            raise Problem('Invalid course or group join code.', 404)
        self.db.writing_memberships.update_one({'_id': row['id'] + ':' + user['id']}, {'$setOnInsert': {
            'classId': row['id'], 'studentId': user['id'], 'joinedAt': now()}}, upsert=True)
        return {'joined': True, 'classId': row['id']}

    def assignment(self, user, key):
        row = self.db.writing_assignments.find_one({'_id': key})
        if not self.scoped(user, row) or row.get('deleted'):
            raise Problem('Exercise not found.', 404)
        if self.tutor(user, row) or row.get('practiceOwner') == user['id']:
            return row
        if user.get('writingProject') and row['published'] and not row.get('practiceOwner'):
            if row.get('classId'):self.classroom(user,row['classId'])
            return row
        if row['published'] and row['classId']:
            self.classroom(user, row['classId'])
            return row
        raise Problem('You do not have access to this exercise.', 404)

    def assignments(self, user, compact=False):
        if user['role'] == 'tutor':
            query = {'tutorId': user['id']}
        else:
            ids = self.db.writing_memberships.distinct('classId', {'studentId': user['id']})
            query = {'$or': [{'classId': {'$in': ids}, 'published': True}, {'practiceOwner': user['id']}]}
        if user.get('writingProject'):
            query = {'projectId': user['writingProject'], **({'practiceOwner': None} if user['role'] == 'tutor' else {
                '$or': [{'published': True, 'practiceOwner': None}, {'practiceOwner': user['id']}]})}
        else:
            query['projectId'] = None
        query['deleted'] = {'$ne': True}
        rows=list(self.db.writing_assignments.find(query, {'rubricCatalog':0} if compact else None).sort('createdAt', -1))
        entries=set(self.db.writing_exam_entries.distinct('assignmentId',{'studentId':user['id'],'assignmentId':{'$in':[r['id'] for r in rows]}})) if user['role']!='tutor' else set()
        if user['role']!='tutor':
            memberships=set(self.db.writing_memberships.distinct('classId',{'studentId':user['id']}))
            legacy_classes = set(self.db.writing_classes.distinct('id', {'projectId': user['writingProject'],
                'enrollment': {'$ne': 'explicit'}})) if user.get('writingProject') else set()
            rows=[row for row in rows if not row.get('classId') or row['classId'] in memberships | legacy_classes or row.get('practiceOwner')==user['id']]
        return [self.assignment_view(user,row,row['id'] in entries) for row in rows]

    def delete_assignment(self, user, key, revision):
        row = self.assignment(user, key)
        if not self.tutor(user, row):
            raise Problem('Only a tutor can delete an exercise.', 403)
        if not self.db.writing_assignments.update_one({'_id': key, 'revision': revision},
                {'$set': {'deleted': True}, '$inc': {'revision': 1}}).matched_count:
            raise Problem('The exercise changed. Reload before deleting it.', 409)
        return {'deleted': True}

    def save_assignment(self, user, data, key=None):
        from latex_backend.storage.state import State
        from latex_backend.writing.exams import is_exam
        password=data.pop('examPassword',None)
        custom_catalog=data.pop('rubricCatalog',None)
        question_id=data.pop('questionId',None)
        if question_id:
            from latex_backend.writing.questions import copy_question_assets
            data['promptFiles']=copy_question_assets(self,user,question_id)
        data.setdefault('writingScope','whole')
        data.setdefault('purpose','homework')
        if data.get('purpose')=='exam':data['kind']='exam'
        elif data.get('kind')=='exam':data['purpose']='exam'
        prior=self.assignment(user,key) if key else None
        exam_secret=None
        if data.get('kind')=='exam':
            if data.get('practice'):raise Problem('Examinations must be assigned by a tutor.')
            if not data.get('durationMinutes') or not data.get('opensAt') or not data.get('closesAt'):
                raise Problem('Examinations require a duration and opening and closing times.')
            for field in ['opensAt','closesAt']:
                stamp=datetime.fromisoformat(data[field])
                if stamp.tzinfo is None:raise Problem('Examination times require an explicit timezone.')
                data[field]=stamp.astimezone(timezone.utc).isoformat()
            if data['opensAt']>=data['closesAt']:raise Problem('The closing time must be after the opening time.')
            exam_secret=State.password(password) if password else prior.get('examSecret') if prior and is_exam(prior) else None
            if not exam_secret:raise Problem('Set an examination password with at least 6 characters.')
            data['dueAt']=None;data['allowLate']=False
        practice = data.pop('practice', False)
        if practice:
            self.role(user, 'student')
        else:
            self.role(user, 'tutor')
            if not user.get('writingProject') and not data.get('classId'):
                raise Problem('Choose a course or group to deliver this exercise.')
            if data.get('classId') and not self.tutor(user, self.classroom(user, data['classId'])):
                raise Problem('You are not a tutor of this course or group.', 403)
        tasks = sorted(set(data['tasks']))
        if not tasks or any(task not in (1, 2) for task in tasks):
            raise Problem('Choose Task 1, Task 2 or both.')
        from latex_backend.writing.prompts import metadata
        attachments = {}
        for number, asset_id in data.get('promptFiles', {}).items():
            if int(number) not in tasks:
                raise Problem('The question attachment does not belong to a selected task.', 403)
            asset = self.db.writing_prompt_files.find_one({'_id': asset_id, 'projectId': user.get('writingProject')})
            if not asset:
                raise Problem('The question attachment does not belong to this workspace.', 404)
            if user['role'] != 'tutor' and (not practice or asset['ownerId'] != user['id']):
                raise Problem('Select your own question attachment for personal practice.', 403)
            attachments[number] = metadata(asset)
        data['promptFiles'] = attachments
        from latex_backend.writing.rubrics import assignment_catalog
        data['rubricCatalog'] = assignment_catalog(custom_catalog if custom_catalog is not None else self.settings(user)['catalog'],data['writingScope'])
        category=data.get('category','ielts')
        if category!='ielts' and tasks!=[1]:raise Problem('Reports and research papers use one document, not separate IELTS tasks.')
        for task in tasks:
            profile = data['rubricCatalog'].get(data['profiles'].get(str(task), ''))
            if not profile or profile.get('category','ielts')!=category or profile['task'] != task or (category=='ielts' and task == 1 and profile['family'] != data['variant']):
                raise Problem('The rubric does not match the task or Academic/General Training variant.')
            if not profile['checks'] or not any(profile['checks'].values()):
                raise Problem('Add criteria for the selected writing scope.')
            if (data['published'] or practice) and not data['prompts'].get(str(task), '').strip() and str(task) not in attachments:
                raise Problem('Enter a question for every assigned task.')
        data['rubricCatalog'] = {data['profiles'][str(task)]: data['rubricCatalog'][data['profiles'][str(task)]] for task in tasks}
        if data.get('imageId'):
            image = self.db.writing_assets.find_one({'_id': data['imageId'], 'ownerId': user['id']})
            if data['imageId'] != 'sample-commuting' and not image:
                raise Problem('Question image not found.')
        if data.get('dueAt'):
            due = datetime.fromisoformat(data['dueAt'])
            if due.tzinfo is None:
                raise Problem('The due date requires an explicit timezone.')
            data['dueAt'] = due.astimezone(timezone.utc).isoformat()
        current = None
        if key:
            current = self.assignment(user, key)
            if not self.tutor(user, current) or (current['published'] and (
                    self.db.writing_drafts.count_documents({'assignmentId': key}) or
                    self.db.writing_submissions.count_documents({'assignmentId': key}))):
                raise Problem('An exercise already started by learners is immutable. Create another exercise instead.', 409)
        version = data.pop('revision', 0)
        key = key or identifier()
        row = {**data, '_id': key, 'id': key, 'tasks': tasks, 'tutorId': None if practice else user['id'],
               'questionId': question_id,
               'tutorName': '' if practice else user['name'], 'practiceOwner': user['id'] if practice else None,
               'classId': None if practice else data['classId'], 'published': True if practice else data['published'],
               'createdAt': current['createdAt'] if current else now(), 'revision': version + 1, 'projectId': user.get('writingProject')}
        if exam_secret:row['examSecret']=exam_secret
        if current:
            if not self.db.writing_assignments.replace_one({'_id': key, 'revision': version}, row).matched_count:
                raise Problem('The exercise draft changed in another window. Reload before saving.', 409)
        else:
            self.db.writing_assignments.insert_one(row)
        return public(row)

    def draft(self, user, assignment):
        self.role(user, 'student')
        task = self.assignment(user, assignment)
        from latex_backend.writing.exams import is_exam,require_entry,require_open,utc_now
        require_entry(self.db,user,task)
        if not task['published']:
            raise Problem('The exercise has not been assigned yet.', 403)
        key = assignment + ':' + user['id']
        existing=self.db.writing_drafts.find_one({'_id':key})
        if is_exam(task) and not existing:require_open(task)
        started = utc_now()
        deadline = (started + timedelta(minutes=task['durationMinutes'])).isoformat() if task.get('durationMinutes') else None
        if is_exam(task):deadline=min(datetime.fromisoformat(deadline),datetime.fromisoformat(task['closesAt'])).isoformat()
        initial = {'assignmentId': assignment,
            'studentId': user['id'], 'answers': {'1': '', '2': ''}, 'latexSources': {}, 'revision': 0,
            'attempt': 0, 'startedAt': started.isoformat(), 'deadline': deadline, 'updatedAt': now()}
        if user.get('projectPermission') == 'viewer':
            return {**public(self.db.writing_drafts.find_one({'_id': key}) or initial), 'serverNow': now()}
        self.db.writing_drafts.update_one({'_id': key}, {'$setOnInsert': initial}, upsert=True)
        row = self.db.writing_drafts.find_one({'_id': key})
        exam_readonly=is_exam(task) and (utc_now()>=datetime.fromisoformat(row['deadline']) or bool(self.db.writing_submissions.find_one({'assignmentId':assignment,'studentId':user['id']},{'_id':1})))
        return {**public(row), 'serverNow': now(), 'readOnly':exam_readonly}

    def save_draft(self, user, assignment, data):
        draft=self.draft(user, assignment)
        if draft.get('readOnly'):raise Problem('The examination has expired or been submitted and is read only.',403)
        from latex_backend.writing.exams import require_writable
        require_writable(self.assignment(user,assignment),draft)
        blocks = data.get('blocks', {})
        for number, parts in blocks.items():
            if len(parts) > 100 or '\n\n'.join(part['text'] for part in parts if part['text'].strip()) != data['answers'].get(number, ''):
                raise Problem('Paper sections must match the canonical answer text.')
        row = self.db.writing_drafts.find_one_and_update({'_id': assignment + ':' + user['id'], 'revision': data['revision']},
            {'$set': {'answers': data['answers'], 'blocks': blocks, 'latexSources': data['latexSources'], 'updatedAt': now()}, '$inc': {'revision': 1}}, return_document=ReturnDocument.AFTER)
        if not row:
            raise Problem('The draft changed in another window. Keep your work and reload to compare.', 409)
        return {**public(row), 'serverNow': now()}

    def submit(self, user, assignment, revision, request_id):
        draft = self.draft(user, assignment)
        task = self.assignment(user, assignment)
        key = hashlib.sha256((assignment + ':' + user['id'] + ':' + request_id).encode()).hexdigest()[:32]
        existing = self.db.writing_submissions.find_one({'_id': key})
        if existing:
            return public(existing)
        if draft.get('readOnly'):raise Problem('The examination has expired or been submitted.',403)
        from latex_backend.writing.exams import require_writable
        require_writable(task,draft)
        if any(not draft['answers'].get(str(t), '').strip() for t in task['tasks']):
            raise Problem('Answer every assigned task before submitting.')
        timestamp = now()
        late = any(value and timestamp > value for value in (task.get('dueAt'), draft.get('deadline')))
        if late and not task['allowLate']:
            raise Problem('The deadline has passed. Your writing is saved; contact your tutor.', 409)
        frozen = self.db.writing_drafts.find_one_and_update({'_id': assignment + ':' + user['id'], 'revision': revision},
            {'$inc': {'revision': 1, 'attempt': 1}, '$set': {'updatedAt': timestamp}}, return_document=ReturnDocument.BEFORE)
        if not frozen:
            raise Problem('The draft changed. Save it before submitting.', 409)
        row = {'_id': key, 'id': key, 'assignmentId': assignment, 'assignment': public(task),
               'projectId': user.get('writingProject'),
               'studentId': user['id'], 'studentName': user['name'], 'tutorId': task['tutorId'],
               'answers': copy.deepcopy(frozen['answers']), 'latexSources': copy.deepcopy(frozen['latexSources']),
               'blocks': copy.deepcopy(frozen.get('blocks', {})),
               'attempt': frozen['attempt'] + 1, 'submittedAt': timestamp, 'late': bool(late),
               'wordCounts': {str(t): word_count(frozen['answers'][str(t)]) for t in task['tasks']}}
        self.db.writing_submissions.insert_one(row)
        return public(row)

    def submission(self, user, key):
        row = self.db.writing_submissions.find_one({'_id': key})
        if not self.scoped(user, row) or not (user['id'] == row['studentId'] or self.tutor(user, row)):
            raise Problem('Submission not found or access denied.', 404)
        return row

    def submissions(self, user, compact=False):
        query = {'tutorId': user['id']} if user['role'] == 'tutor' else {'studentId': user['id']}
        query = {'projectId': user['writingProject'], **({'tutorId': {'$ne': None}} if user['role'] == 'tutor' else {'studentId': user['id']})} if user.get('writingProject') else {**query, 'projectId': None}
        result = []
        rows=list(self.db.writing_submissions.find(query, {'assignment.rubricCatalog':0,'answers':0,'latexSources':0} if compact else None).sort('submittedAt', -1))
        publications={row['_id']:row for row in self.db.writing_results.find({'_id':{'$in':[row['id'] for row in rows]}})} if rows else {}
        for row in rows:
            published = publications.get(row['id'])
            value = public(row)
            value.pop('latexSources', None)
            value.pop('answers', None)
            value['result'] = public(published) if published else None
            result.append(value)
        return result

    def review(self, user, submission):
        row = self.submission(user, submission)
        published = self.db.writing_results.find_one({'_id': submission})
        if self.tutor(user, row):
            draft = self.db.writing_reviews.find_one({'_id': submission})
            return {'submission': public(row), 'review': public(draft) if draft else {'revision': 0, 'tasks': {}, 'summary': '', 'recommendations': ''},
                    'published': public(published) if published else None}
        return {'submission': public(row), 'review': public(published) if published else None, 'published': public(published) if published else None}

    def save_review(self, user, submission, data):
        self.role(user, 'tutor')
        row = self.submission(user, submission)
        if not self.tutor(user, row):
            raise Problem('You are not a tutor of this exercise.', 403)
        for task, grade in data['tasks'].items():
            if task not in {str(t) for t in row['assignment']['tasks']}:
                raise Problem('The assessment task does not belong to this submission.')
            category=row['assignment'].get('category','ielts')
            from latex_backend.writing.rubrics import uses_bands
            expected = {'TA' if task == '1' else 'TR', 'CC', 'LR', 'GRA'} if uses_bands(row['assignment']) else set()
            if set(grade['scores']) != expected or set(grade['notes']) - expected:
                raise Problem("Assessment scores must match this task's scoring scheme.")
            if any(v is not None and (not math.isfinite(v) or not 0 <= v <= 9 or v * 2 != int(v * 2)) for v in grade['scores'].values()):
                raise Problem('Bands must range from 0 to 9 in steps of 0.5, or be ungraded.')
            profile = row['assignment'].get('rubricCatalog', CATALOG).get(grade['profile'])
            if not profile or profile.get('category','ielts')!=category or profile['task'] != int(task) or (category=='ielts' and task == '1' and profile['family'] != row['assignment']['variant']):
                raise Problem('The assessment profile does not match this exercise.')
            paragraphs = re.split(r'\n\s*\n', row['answers'][task].strip())
            seen = set()
            for section in grade['sections']:
                index = section['paragraph']
                if index >= len(paragraphs) or index in seen:
                    raise Problem('The reviewed paragraph is outside the submission or duplicated.')
                if section['kind'] not in profile['checks'] or set(section['checks']) - {c['id'] for c in profile['checks'][section['kind']]}:
                    raise Problem('Review criteria must belong to the assignment rubric snapshot.')
                seen.add(index)
                evidence = row['answers'][task] if index == -1 else paragraphs[index]
                if section['quote'] and section['quote'] not in evidence:
                    raise Problem('Evidence must occur in the corresponding submitted paragraph.')
        revision = data.pop('revision')
        value = {**data, '_id': submission, 'submissionId': submission, 'revision': revision + 1,
                 'tutorId': user['id'], 'tutorName': user['name'], 'updatedAt': now()}
        if revision == 0:
            try:
                self.db.writing_reviews.insert_one(value)
            except DuplicateKeyError:
                raise Problem('An assessment already exists in another window. Reload before saving.', 409) from None
        elif not self.db.writing_reviews.replace_one({'_id': submission, 'revision': revision}, value).matched_count:
            raise Problem('The assessment changed elsewhere. Keep your draft and reload.', 409)
        return public(value)

    def publish(self, user, submission, revision, reason):
        self.role(user, 'tutor')
        original = self.submission(user, submission)
        if not self.tutor(user, original):
            raise Problem('You are not a tutor of this submission.', 403)
        review = self.db.writing_reviews.find_one({'_id': submission, 'revision': revision})
        if not review:
            raise Problem('Save the current assessment before publishing.', 409)
        raw = {}
        for task in original['assignment']['tasks']:
            from latex_backend.writing.rubrics import uses_bands
            if not uses_bands(original['assignment']):
                if str(task) not in review['tasks']:raise Problem('Assess the document before publishing.')
                continue
            scores = review['tasks'].get(str(task), {}).get('scores', {})
            if len(scores) != 4 or any(value is None for value in scores.values()):
                raise Problem('Grade all four criteria for every task before publishing.')
            raw[str(task)] = sum(scores.values()) / 4
        previous = self.db.writing_results.find_one({'_id': submission})
        if previous and previous['publishedVersion'] > revision:
            raise Problem('Newer results already exist. Reload before publishing.', 409)
        if previous and previous['publishedVersion'] == revision:
            return public(previous)
        if previous and len(reason.strip()) < 3:
            raise Problem('Give a reason for revising published results.')
        rounded = lambda score: math.floor(score * 2 + .5) / 2
        estimate = (raw['1'] + 2 * raw['2']) / 3 if set(raw) == {'1', '2'} else None
        result = {**copy.deepcopy(review), 'publishedVersion': revision, 'publishedAt': now(), 'reason': reason,
                  'taskRaw': raw, 'taskBands': {task: rounded(score) for task, score in raw.items()},
                  'writingBand': rounded(estimate) if estimate is not None else None, 'writingRaw': estimate}
        if original['assignment'].get('writingScope','whole')!='whole':
            from latex_backend.writing.rubrics import checklist_result
            for number in original['assignment']['tasks']:
                grade = review['tasks'].get(str(number), {})
                paragraph_count = len(re.split(r'\n\s*\n', original['answers'][str(number)].strip()))
                covered = {section['paragraph'] for section in grade.get('sections', []) if section['paragraph'] >= 0}
                if covered != set(range(paragraph_count)):
                    raise Problem('Review every submitted paragraph before publishing this section exercise.')
            result.update(checklist_result(original['assignment'],review))
        publication = {**result, '_id': submission + ':' + str(revision)}
        self.db.writing_publications.update_one({'_id': publication['_id']}, {'$setOnInsert': publication}, upsert=True)
        if previous:
            if not self.db.writing_results.replace_one({'_id': submission, 'publishedVersion': previous['publishedVersion']}, result).matched_count:
                raise Problem('Results changed in another window.', 409)
        else:
            try:
                self.db.writing_results.insert_one(result)
            except DuplicateKeyError:
                raise Problem('Results were just published in another window.', 409) from None
        return public(result)

    def latex(self, user, assignment=None, submission=None, report=False, task_number=None):
        result = None
        if submission:
            view = self.review(user, submission)
            snapshot = view['submission']
            task, answers = snapshot['assignment'], snapshot['answers']
            result = view['published'] if report else None
            if report and result is None:
                raise Problem('No published results are available for the report.', 409)
        else:
            task = self.assignment(user, assignment)
            answers = self.draft(user, assignment)['answers']
        blocks = snapshot.get('blocks', {}) if submission else self.draft(user, assignment).get('blocks', {})
        category=task.get('category','ielts')
        lines = [r'\documentclass[12pt,a4paper]{article}', r'\usepackage{iftex}',
                 r'\ifPDFTeX\usepackage[utf8]{inputenc}\usepackage[T5]{fontenc}\else\usepackage{fontspec}\setmainfont{TeX Gyre Termes}\fi',
                 r'\usepackage[margin=25mm]{geometry}', r'\usepackage{graphicx}', r'\usepackage{hyperref}',
                 r'\setlength{\parindent}{0pt}\setlength{\parskip}{8pt}', r'\begin{document}',
                 r'\section*{' + tex_escape(task['title']) + '}', r'\textit{'+('IELTS Writing practice - not an official examination result.' if category=='ielts' else category.title()+' - Tutor feedback.')+'}']
        for number in task['tasks']:
            if task_number is not None and number != task_number:
                continue
            key = str(number)
            lines += [r'\section*{' + ('Task '+key if category=='ielts' else category.title()) + '}', r'\subsection*{Question}', tex_escape(task['prompts'].get(key, ''))]
            attachment = task.get('promptFiles', {}).get(key)
            if attachment and attachment['kind'] in {'pdf', 'image'}:
                extension = 'pdf' if attachment['kind'] == 'pdf' else 'png'
                for page in range(1, (attachment.get('pages') or 1) + 1):
                    option = f',page={page}' if extension == 'pdf' else ''
                    lines += [r'\begin{center}\includegraphics[width=.9\linewidth,height=.8\textheight,keepaspectratio' + option + ']{question-task-' + key + '.' + extension + r'}\end{center}']
            if number == 1 and task.get('imageId'):
                lines += [r'\begin{center}\includegraphics[width=.85\linewidth]{prompt-image}\end{center}']
            lines += [r'\subsection*{Answer}']
            if blocks.get(key):
                for block in blocks[key]:
                    if block['text'].strip():
                        lines += [r'\subsubsection*{' + tex_escape(block['heading']) + '}', tex_escape(block['text'])]
            else:
                lines += [tex_escape(answers.get(key, ''))]
            if result:
                grade = result['tasks'][key]
                lines += [r'\subsection*{Tutor feedback}']
                if key in result['taskBands']:lines += ['Estimated task band: ' + str(result['taskBands'][key])]
                if grade.get('overall'):
                    lines += [tex_escape(grade['overall'])]
                for code, score in grade['scores'].items():
                    lines += [r'\paragraph{' + code + ': ' + str(score) + '}', tex_escape(grade['notes'].get(code, ''))]
                for section in grade['sections']:
                    checks = task.get('rubricCatalog', CATALOG)[grade['profile']]['checks'].get(section['kind'], [])
                    reviewed = [(check, section['checks'].get(check['id'])) for check in checks
                                if section['checks'].get(check['id']) not in (None, 'unreviewed')]
                    if section['note'] or section['quote'] or reviewed:
                        lines += [r'\paragraph{' + tex_escape(section['title']) + '}', tex_escape(section['quote']), tex_escape(section['note'])]
                        if reviewed:
                            labels = {'met': 'Met', 'partial': 'Partially met', 'missing': 'Needs improvement', 'na': 'Not applicable'}
                            lines += [r'\begin{itemize}']
                            lines += [r'\item[--] ' + tex_escape(labels[state] + ': ' + check['text']) for check, state in reviewed]
                            lines += [r'\end{itemize}']
        if result:
            lines += [r'\section*{Summary}', tex_escape(result['summary']), tex_escape(result['recommendations'])]
            if 'scorePercent' in result:
                lines += ['Checklist score: ' + str(result['scorePercent']) + r'\%']
            if result['writingBand'] is not None:
                lines += ['Estimated Writing band: ' + str(result['writingBand'])]
        lines += [r'\end{document}']
        return '\n\n'.join(lines), task
