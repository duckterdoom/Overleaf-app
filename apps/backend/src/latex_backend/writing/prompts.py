"""Import an immutable, ACL-checked question from the current editor project."""
import hashlib
import io
import uuid
from pathlib import Path
from bson import Binary
from PIL import Image, ImageOps
import pymupdf
from latex_backend.storage.store import Problem

MAX_BYTES = 12 * 1024 * 1024
EXTENSIONS = {'.tex', '.pdf', '.png', '.jpg', '.jpeg', '.webp', '.txt', '.md'}


def capture(gateway, user, store, compiler, data):
    project = user.get('writingProject')
    if not project or user['role'] not in {'tutor','student'}:
        raise Problem('Mở dự án để lấy tệp đề bài.', 403)
    gateway.accounts.throttle(('writing-prompt-import', user['id']))
    source = store.path(project, data.path)
    if not source.is_file() or source.suffix.lower() not in EXTENSIONS:
        raise Problem('Chọn tệp .tex, PDF, ảnh PNG/JPEG/WebP hoặc TXT/Markdown có trong dự án.')
    extension = source.suffix.lower()
    if extension == '.tex':
        if not data.snapshot:
            raise Problem('Biên dịch tệp LaTeX trước khi dùng làm đề.', 409)
        snapshot = compiler.verified(project, data.snapshot)
        if snapshot.main != data.path:
            raise Problem('PDF được chọn không thuộc tệp đề này.', 409)
        payload, kind, mime = snapshot.data, 'pdf', 'application/pdf'
    else:
        if source.stat().st_size > MAX_BYTES:
            raise Problem('Tệp đề tối đa 12 MiB.', 413)
        with source.open('rb') as stream:
            payload = stream.read(MAX_BYTES + 1)
        kind = 'pdf' if extension == '.pdf' else 'text' if extension in {'.txt', '.md'} else 'image'
        mime = {'pdf': 'application/pdf', 'text': 'text/plain; charset=utf-8', 'image': 'image/png'}[kind]
    if len(payload) > MAX_BYTES:
        raise Problem('Đề sau biên dịch tối đa 12 MiB. Hãy dùng tệp chỉ chứa đề bài.', 413)
    pages, text = None, None
    if kind == 'pdf':
        try:
            with pymupdf.open(stream=payload, filetype='pdf') as document:
                if document.needs_pass or not 1 <= len(document) <= 100:
                    raise Problem('Đề PDF cần 1–100 trang và không có mật khẩu.')
                first, last = data.firstPage, data.lastPage or len(document)
                if not 1 <= first <= last <= len(document):
                    raise Problem('Khoảng trang đề không hợp lệ.')
                if first != 1 or last != len(document):
                    with pymupdf.open() as selected:
                        selected.insert_pdf(document, from_page=first - 1, to_page=last - 1)
                        payload = selected.tobytes(garbage=4, deflate=True)
                pages = last - first + 1
        except (pymupdf.FileDataError, RuntimeError):
            raise Problem('Không đọc được PDF đề bài.') from None
    elif kind == 'image':
        try:
            with Image.open(io.BytesIO(payload)) as image:
                if image.width * image.height > 12000000:
                    raise Problem('Ảnh đề tối đa 12 triệu pixel.')
                target = io.BytesIO(); ImageOps.exif_transpose(image).convert('RGB').save(target, format='PNG'); payload = target.getvalue()
        except (OSError, Image.DecompressionBombError):
            raise Problem('Không đọc được ảnh đề bài.') from None
    else:
        try:
            text = payload.decode('utf-8-sig')
        except UnicodeError:
            raise Problem('Tệp văn bản đề cần mã hóa UTF-8.') from None
        if len(text) > 20000 or not text.strip():
            raise Problem('Tệp đề văn bản cần 1–20.000 ký tự.')
    if len(payload) > MAX_BYTES:
        raise Problem('Tệp đề sau chuẩn hóa vượt 12 MiB.', 413)
    identifier = uuid.uuid4().hex
    meta = {'id': identifier, 'projectId': project, 'sourcePath': data.path, 'kind': kind, 'mime': mime,
            'digest': hashlib.sha256(payload).hexdigest(), 'pages': pages, 'firstPage': data.firstPage, 'lastPage': data.lastPage}
    gateway.database.writing_prompt_files.insert_one({'_id': identifier, **meta, 'ownerId': user['id'], 'data': Binary(payload)})
    return {**meta, **({'text': text} if text is not None else {})}


def metadata(row):
    return {key: value for key, value in row.items() if key not in {'_id', 'ownerId', 'data'}}
