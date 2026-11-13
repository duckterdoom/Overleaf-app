"""Exam entry and server-side timing, shared by all assessment categories."""
from datetime import datetime,timezone
from latex_backend.storage.store import Problem


def utc_now():
    return datetime.now(timezone.utc)


def is_exam(row):
    return row.get('kind','assignment')=='exam'


def admission(db,user,row):
    return db.writing_exam_entries.find_one({'_id':row['id']+':'+user['id']})


def require_entry(db,user,row):
    if is_exam(row) and user['role']!='tutor' and not admission(db,user,row):
        raise Problem('Enter the password to start the examination.',403)


def require_open(row):
    stamp=utc_now()
    if stamp < datetime.fromisoformat(row['opensAt']):raise Problem('The examination has not opened yet.',403)
    if stamp >= datetime.fromisoformat(row['closesAt']):raise Problem('The examination has closed.',403)


def require_writable(row,draft):
    if not is_exam(row):return
    require_open(row)
    if utc_now()>=datetime.fromisoformat(draft['deadline']):raise Problem('Examination time is up. Your saved draft is preserved.',403)
