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
        raise Problem('Nhập mật khẩu để bắt đầu bài kiểm tra.',403)


def require_open(row):
    stamp=utc_now()
    if stamp < datetime.fromisoformat(row['opensAt']):raise Problem('Bài kiểm tra chưa đến giờ mở.',403)
    if stamp >= datetime.fromisoformat(row['closesAt']):raise Problem('Bài kiểm tra đã đóng.',403)


def require_writable(row,draft):
    if not is_exam(row):return
    require_open(row)
    if utc_now()>=datetime.fromisoformat(draft['deadline']):raise Problem('Đã hết thời gian làm bài kiểm tra. Bản nháp đã lưu vẫn được giữ.',403)
