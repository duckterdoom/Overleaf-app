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


def now():
    return datetime.now(timezone.utc).isoformat()


def word_count(text):
    return len(re.findall(r"[^\W_]+(?:['’\-][^\W_]+)*", unicodedata.normalize('NFC', text)))


def public(row):
    return {key: value for key, value in row.items() if key != '_id'}


def identifier():
    return uuid.uuid4().hex


class Writing:
    def __init__(self, gateway):
        self.gateway, self.db = gateway, gateway.database

    @staticmethod
    def role(user, role):
        if user.get('role') != role:
            raise Problem('Vai trò của bạn không có quyền thực hiện thao tác này.', 403)

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
            raise Problem('Chọn dự án đích trước khi đưa dữ liệu cũ vào.')
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
        if not self.scoped(user, row) or not (user.get('writingProject') or row['tutorId'] == user['id'] or self.db.writing_memberships.find_one({'_id': classroom + ':' + user['id']})):
            raise Problem('Không tìm thấy lớp học hoặc bạn chưa tham gia.', 404)
        return row

    def classes(self, user):
        query = {'tutorId': user['id']} if user['role'] == 'tutor' else {'_id': {'$in': self.db.writing_memberships.distinct('classId', {'studentId': user['id']})}}
        query = {'projectId': user['writingProject']} if user.get('writingProject') else {**query, 'projectId': None}
        result = []
        for row in self.db.writing_classes.find(query):
            members = []
            for member in self.db.writing_memberships.find({'classId': row['id']}):
                student = self.gateway.accounts.user(member['studentId'])
                if student:
                    members.append({'id': student['id'], 'name': student['name']})
            if user.get('writingProject'):
                ids = set(self.db.invitations.distinct('user_id', {'project': user['writingProject']})) | set(self.db.link_visitors.distinct('user_id', {'project': user['writingProject']}))
                ids.add(self.gateway.sharing.owner(user['writingProject']))
                for identifier in ids - {m['id'] for m in members}:
                    if identifier and self.gateway.sharing.access(user['writingProject'], identifier) and self.gateway.sharing.workspace_role(user['writingProject'], identifier) == 'student':
                        student = self.gateway.accounts.user(identifier)
                        if student:
                            members.append({'id': student['id'], 'name': student['name']})
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
            raise Problem('Nhập tên lớp học.')
        self.db.writing_classes.insert_one(row)
        return public(row)

    def join(self, user, code):
        self.role(user, 'student')
        self.gateway.accounts.throttle(('writing-join', user['id']))
        row = self.db.writing_classes.find_one({'joinCode': code.strip().upper()})
        if not self.scoped(user, row):
            raise Problem('Mã lớp không hợp lệ.', 404)
        self.db.writing_memberships.update_one({'_id': row['id'] + ':' + user['id']}, {'$setOnInsert': {
            'classId': row['id'], 'studentId': user['id'], 'joinedAt': now()}}, upsert=True)
        return {'joined': True, 'classId': row['id']}

    def assignment(self, user, key):
        row = self.db.writing_assignments.find_one({'_id': key})
        if not self.scoped(user, row):
            raise Problem('Không tìm thấy bài tập.', 404)
        if self.tutor(user, row) or row.get('practiceOwner') == user['id']:
            return row
        if row['published'] and row['classId']:
            self.classroom(user, row['classId'])
            return row
        raise Problem('Bạn không có quyền xem bài tập này.', 404)

    def assignments(self, user):
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
        return [public(row) for row in self.db.writing_assignments.find(query).sort('createdAt', -1)]

    def save_assignment(self, user, data, key=None):
        practice = data.pop('practice', False)
        if practice:
            self.role(user, 'student')
        else:
            self.role(user, 'tutor')
            if not data.get('classId'):
                raise Problem('Chọn lớp trước khi giao bài.')
            if not self.tutor(user, self.classroom(user, data['classId'])):
                raise Problem('Bạn không phụ trách lớp này.', 403)
        tasks = sorted(set(data['tasks']))
        if not tasks or any(task not in (1, 2) for task in tasks):
            raise Problem('Chọn Task 1, Task 2 hoặc cả hai.')
        for task in tasks:
            profile = CATALOG.get(data['profiles'].get(str(task), ''))
            if not profile or profile['task'] != task or (task == 1 and profile['family'] != data['variant']):
                raise Problem('Dạng bài không phù hợp với task/Academic/General Training.')
            if (data['published'] or practice) and not data['prompts'].get(str(task), '').strip():
                raise Problem('Nhập đề cho từng task được giao.')
        if data.get('imageId'):
            image = self.db.writing_assets.find_one({'_id': data['imageId'], 'ownerId': user['id']})
            if data['imageId'] != 'sample-commuting' and not image:
                raise Problem('Không tìm thấy hình của đề bài.')
        if data.get('dueAt'):
            due = datetime.fromisoformat(data['dueAt'])
            if due.tzinfo is None:
                raise Problem('Hạn nộp cần múi giờ rõ ràng.')
            data['dueAt'] = due.astimezone(timezone.utc).isoformat()
        current = None
        if key:
            current = self.assignment(user, key)
            if not self.tutor(user, current) or current['published']:
                raise Problem('Bài đã giao được giữ nguyên; hãy tạo bài tập mới.', 409)
        version = data.pop('revision', 0)
        key = key or identifier()
        row = {**data, '_id': key, 'id': key, 'tasks': tasks, 'tutorId': None if practice else user['id'],
               'tutorName': '' if practice else user['name'], 'practiceOwner': user['id'] if practice else None,
               'classId': None if practice else data['classId'], 'published': True if practice else data['published'],
               'createdAt': current['createdAt'] if current else now(), 'revision': version + 1, 'projectId': user.get('writingProject')}
        if current:
            if not self.db.writing_assignments.replace_one({'_id': key, 'revision': version}, row).matched_count:
                raise Problem('Đề nháp đã đổi ở cửa sổ khác. Tải lại trước khi lưu.', 409)
        else:
            self.db.writing_assignments.insert_one(row)
        return public(row)

    def draft(self, user, assignment):
        self.role(user, 'student')
        task = self.assignment(user, assignment)
        if not task['published']:
            raise Problem('Bài tập chưa được giao.', 403)
        key = assignment + ':' + user['id']
        started = datetime.now(timezone.utc)
        deadline = (started + timedelta(minutes=task['durationMinutes'])).isoformat() if task.get('durationMinutes') else None
        initial = {'assignmentId': assignment,
            'studentId': user['id'], 'answers': {'1': '', '2': ''}, 'latexSources': {}, 'revision': 0,
            'attempt': 0, 'startedAt': started.isoformat(), 'deadline': deadline, 'updatedAt': now()}
        if user.get('projectPermission') == 'viewer':
            return {**public(self.db.writing_drafts.find_one({'_id': key}) or initial), 'serverNow': now()}
        self.db.writing_drafts.update_one({'_id': key}, {'$setOnInsert': initial}, upsert=True)
        row = self.db.writing_drafts.find_one({'_id': key})
        return {**public(row), 'serverNow': now()}

    def save_draft(self, user, assignment, data):
        self.draft(user, assignment)
        row = self.db.writing_drafts.find_one_and_update({'_id': assignment + ':' + user['id'], 'revision': data['revision']},
            {'$set': {'answers': data['answers'], 'latexSources': data['latexSources'], 'updatedAt': now()}, '$inc': {'revision': 1}}, return_document=ReturnDocument.AFTER)
        if not row:
            raise Problem('Bài viết đã đổi ở cửa sổ khác. Giữ bản đang viết và tải lại để đối chiếu.', 409)
        return {**public(row), 'serverNow': now()}

    def submit(self, user, assignment, revision, request_id):
        draft = self.draft(user, assignment)
        task = self.assignment(user, assignment)
        key = hashlib.sha256((assignment + ':' + user['id'] + ':' + request_id).encode()).hexdigest()[:32]
        existing = self.db.writing_submissions.find_one({'_id': key})
        if existing:
            return public(existing)
        if any(not draft['answers'].get(str(t), '').strip() for t in task['tasks']):
            raise Problem('Cần có câu trả lời cho tất cả task được giao.')
        timestamp = now()
        late = any(value and timestamp > value for value in (task.get('dueAt'), draft.get('deadline')))
        if late and not task['allowLate']:
            raise Problem('Đã quá hạn/thời gian được giao. Bài viết vẫn được lưu; liên hệ Tutor để xử lý.', 409)
        frozen = self.db.writing_drafts.find_one_and_update({'_id': assignment + ':' + user['id'], 'revision': revision},
            {'$inc': {'revision': 1, 'attempt': 1}, '$set': {'updatedAt': timestamp}}, return_document=ReturnDocument.BEFORE)
        if not frozen:
            raise Problem('Bản viết vừa đổi; lưu lại trước khi nộp.', 409)
        row = {'_id': key, 'id': key, 'assignmentId': assignment, 'assignment': public(task),
               'projectId': user.get('writingProject'),
               'studentId': user['id'], 'studentName': user['name'], 'tutorId': task['tutorId'],
               'answers': copy.deepcopy(frozen['answers']), 'latexSources': copy.deepcopy(frozen['latexSources']),
               'attempt': frozen['attempt'] + 1, 'submittedAt': timestamp, 'late': bool(late),
               'wordCounts': {str(t): word_count(frozen['answers'][str(t)]) for t in task['tasks']}}
        self.db.writing_submissions.insert_one(row)
        return public(row)

    def submission(self, user, key):
        row = self.db.writing_submissions.find_one({'_id': key})
        if not self.scoped(user, row) or not (user['id'] == row['studentId'] or self.tutor(user, row)):
            raise Problem('Không tìm thấy bản nộp hoặc bạn không có quyền xem.', 404)
        return row

    def submissions(self, user):
        query = {'tutorId': user['id']} if user['role'] == 'tutor' else {'studentId': user['id']}
        query = {'projectId': user['writingProject'], **({'tutorId': {'$ne': None}} if user['role'] == 'tutor' else {'studentId': user['id']})} if user.get('writingProject') else {**query, 'projectId': None}
        result = []
        for row in self.db.writing_submissions.find(query).sort('submittedAt', -1):
            published = self.db.writing_results.find_one({'_id': row['id']})
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
            raise Problem('Bạn không phải Tutor của bài này.', 403)
        for task, grade in data['tasks'].items():
            if task not in {str(t) for t in row['assignment']['tasks']}:
                raise Problem('Task chấm không thuộc bản nộp.')
            expected = {'TA' if task == '1' else 'TR', 'CC', 'LR', 'GRA'}
            if set(grade['scores']) != expected or set(grade['notes']) - expected:
                raise Problem('Cần đúng bốn tiêu chí IELTS của task.')
            if any(v is not None and (not math.isfinite(v) or not 0 <= v <= 9 or v * 2 != int(v * 2)) for v in grade['scores'].values()):
                raise Problem('Band phải từ 0 đến 9, bước 0.5 hoặc chưa chấm.')
            profile = CATALOG.get(grade['profile'])
            if not profile or profile['task'] != int(task) or (task == '1' and profile['family'] != row['assignment']['variant']):
                raise Problem('Dạng bài chấm không phù hợp.')
            paragraphs = re.split(r'\n\s*\n', row['answers'][task].strip())
            seen = set()
            for section in grade['sections']:
                index = section['paragraph']
                if index >= len(paragraphs) or index in seen:
                    raise Problem('Đoạn nhận xét không thuộc snapshot hoặc bị trùng.')
                seen.add(index)
                evidence = row['answers'][task] if index == -1 else paragraphs[index]
                if section['quote'] and section['quote'] not in evidence:
                    raise Problem('Minh chứng phải thuộc đúng đoạn trong bản nộp.')
        revision = data.pop('revision')
        value = {**data, '_id': submission, 'submissionId': submission, 'revision': revision + 1,
                 'tutorId': user['id'], 'tutorName': user['name'], 'updatedAt': now()}
        if revision == 0:
            try:
                self.db.writing_reviews.insert_one(value)
            except DuplicateKeyError:
                raise Problem('Bản chấm đã có ở cửa sổ khác. Tải lại trước khi lưu.', 409) from None
        elif not self.db.writing_reviews.replace_one({'_id': submission, 'revision': revision}, value).matched_count:
            raise Problem('Bản chấm đã đổi ở cửa sổ khác. Giữ nháp và tải lại.', 409)
        return public(value)

    def publish(self, user, submission, revision, reason):
        self.role(user, 'tutor')
        original = self.submission(user, submission)
        if not self.tutor(user, original):
            raise Problem('Bạn không phụ trách bản nộp này.', 403)
        review = self.db.writing_reviews.find_one({'_id': submission, 'revision': revision})
        if not review:
            raise Problem('Lưu bản chấm hiện tại trước khi công bố.', 409)
        raw = {}
        for task in original['assignment']['tasks']:
            scores = review['tasks'].get(str(task), {}).get('scores', {})
            if len(scores) != 4 or any(value is None for value in scores.values()):
                raise Problem('Chấm đủ bốn tiêu chí cho mọi task trước khi công bố.')
            raw[str(task)] = sum(scores.values()) / 4
        previous = self.db.writing_results.find_one({'_id': submission})
        if previous and previous['publishedVersion'] > revision:
            raise Problem('Đã có kết quả mới hơn. Tải lại trước khi công bố.', 409)
        if previous and previous['publishedVersion'] == revision:
            return public(previous)
        if previous and len(reason.strip()) < 3:
            raise Problem('Nhập lý do điều chỉnh kết quả đã công bố.')
        rounded = lambda score: math.floor(score * 2 + .5) / 2
        estimate = (raw['1'] + 2 * raw['2']) / 3 if set(raw) == {'1', '2'} else None
        result = {**copy.deepcopy(review), 'publishedVersion': revision, 'publishedAt': now(), 'reason': reason,
                  'taskRaw': raw, 'taskBands': {task: rounded(score) for task, score in raw.items()},
                  'writingBand': rounded(estimate) if estimate is not None else None, 'writingRaw': estimate}
        publication = {**result, '_id': submission + ':' + str(revision)}
        self.db.writing_publications.update_one({'_id': publication['_id']}, {'$setOnInsert': publication}, upsert=True)
        if previous:
            if not self.db.writing_results.replace_one({'_id': submission, 'publishedVersion': previous['publishedVersion']}, result).matched_count:
                raise Problem('Kết quả đã đổi ở cửa sổ khác.', 409)
        else:
            try:
                self.db.writing_results.insert_one(result)
            except DuplicateKeyError:
                raise Problem('Kết quả vừa được công bố ở cửa sổ khác.', 409) from None
        return public(result)

    def latex(self, user, assignment=None, submission=None, report=False):
        result = None
        if submission:
            view = self.review(user, submission)
            snapshot = view['submission']
            task, answers = snapshot['assignment'], snapshot['answers']
            result = view['published'] if report else None
            if report and result is None:
                raise Problem('Chưa có kết quả được công bố để xuất phiếu.', 409)
        else:
            task = self.assignment(user, assignment)
            answers = self.draft(user, assignment)['answers']
        lines = [r'\documentclass[12pt,a4paper]{article}', r'\usepackage{iftex}',
                 r'\ifPDFTeX\usepackage[utf8]{inputenc}\usepackage[T5]{fontenc}\else\usepackage{fontspec}\setmainfont{TeX Gyre Termes}\fi',
                 r'\usepackage[margin=25mm]{geometry}', r'\usepackage{graphicx}', r'\usepackage{hyperref}',
                 r'\setlength{\parindent}{0pt}\setlength{\parskip}{8pt}', r'\begin{document}',
                 r'\section*{' + tex_escape(task['title']) + '}', r'\textit{IELTS Writing practice - not an official examination result.}']
        for number in task['tasks']:
            key = str(number)
            lines += [r'\section*{Task ' + key + '}', r'\subsection*{Question}', tex_escape(task['prompts'][key])]
            if number == 1 and task.get('imageId'):
                lines += [r'\begin{center}\includegraphics[width=.85\linewidth]{prompt-image}\end{center}']
            lines += [r'\subsection*{Answer}', tex_escape(answers.get(key, ''))]
            if result:
                grade = result['tasks'][key]
                lines += [r'\subsection*{Tutor feedback}', 'Estimated task band: ' + str(result['taskBands'][key])]
                for code, score in grade['scores'].items():
                    lines += [r'\paragraph{' + code + ': ' + str(score) + '}', tex_escape(grade['notes'].get(code, ''))]
                for section in grade['sections']:
                    checks = CATALOG[grade['profile']]['checks'].get(section['kind'], [])
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
            if result['writingBand'] is not None:
                lines += ['Estimated Writing band: ' + str(result['writingBand'])]
        lines += [r'\end{document}']
        return '\n\n'.join(lines), task
