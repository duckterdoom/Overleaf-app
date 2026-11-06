"""Persistent editable DOCX, initialized from the original PDF converter bytes."""
import hashlib
import io
import json
import re
import uuid
import zipfile
from .store import Problem, atomic_write
from .pdf_export.service import exporter


class DocxWorkspace:
    def __init__(self, compiler, main=""):
        self.compiler = compiler
        self.store = compiler.store
        self.main = main

    def folder(self, project):
        path = self.store.root(project) / '.overleaf-docx'
        if self.main and self.main != self.store.info(project)['main']:
            self.store.path(project, self.main)
            path = self.store.root(project) / ('.overleaf-docx-' + hashlib.sha256(self.main.encode()).hexdigest()[:16])
        if path.is_symlink():
            raise Problem('DOCX workspace không được là symbolic link.')
        path.mkdir(exist_ok=True)
        return path

    def status(self, project):
        path = self.folder(project) / 'current.json'
        if path.is_symlink():
            raise Problem('Metadata DOCX không hợp lệ.')
        if not path.exists():
            return None
        if path.stat().st_size > 16384:
            raise Problem('Metadata DOCX quá lớn.')
        value = json.loads(path.read_text(encoding='utf-8'))
        value['stale'] = value['fingerprint'] != self.store.fingerprint(project)
        return value

    def read(self, project, revision):
        if not re.fullmatch('[a-f0-9]{32}', revision):
            raise Problem('Revision DOCX không hợp lệ.')
        metadata = self.status(project)
        if not metadata or metadata['revision'] != revision:
            raise Problem('Bản DOCX đã thay đổi ở cửa sổ khác.', 409)
        path = self.folder(project) / (revision + '.docx')
        if path.is_symlink() or path.stat().st_size > 32 * 1024 * 1024:
            raise Problem('Tệp DOCX không hợp lệ hoặc quá lớn.')
        data = path.read_bytes()
        if hashlib.sha256(data).hexdigest() != metadata['sha256']:
            raise Problem('Checksum DOCX không khớp.')
        return data

    def commit(self, project, data, metadata):
        if len(data) > 32 * 1024 * 1024:
            raise Problem('DOCX vượt giới hạn 32 MiB.')
        try:
            with zipfile.ZipFile(io.BytesIO(data)) as archive:
                if len(archive.infolist()) > 2048 or sum(item.file_size for item in archive.infolist()) > 128 * 1024 * 1024:
                    raise Problem('DOCX vượt giới hạn giải nén.')
                if 'word/document.xml' not in archive.namelist() or any('vbaProject' in name for name in archive.namelist()):
                    raise Problem('DOCX cần có document.xml và không chứa macro.')
        except zipfile.BadZipFile as error:
            raise Problem('Không phải file DOCX hợp lệ.') from error
        folder = self.folder(project)
        old = self.status(project)
        revision = uuid.uuid4().hex
        atomic_write(folder / (revision + '.docx'), data)
        value = {**metadata, 'revision': revision, 'sha256': hashlib.sha256(data).hexdigest(), 'previous': old['revision'] if old else None}
        value.pop('stale', None)
        atomic_write(folder / 'current.json', json.dumps(value).encode())
        if old and old.get('previous') and re.fullmatch('[a-f0-9]{32}', old['previous']):
            previous = folder / (old['previous'] + '.docx')
            if previous.is_file() and not previous.is_symlink():
                previous.unlink()
        return self.status(project)

    def open(self, project, snapshot, reset=False, revision=None):
        # Existing drafts may be reviewed after source edits; conversion and
        # download still require a verified current PDF below/in the exporter.
        pdf = self.compiler.preview(project, snapshot)
        if (self.main or self.store.info(project)['main']) != (pdf.main or self.store.info(project)['main']):
            raise Problem('PDF không thuộc tệp Word đang mở.', 409)
        with self.store.lock:
            current = self.status(project)
            if current and not reset and (current['fingerprint'] == pdf.fingerprint or current['edited']):
                return current
            if reset and (current or {}).get('revision') != revision:
                raise Problem('DOCX đã thay đổi. Mở lại trước khi tạo lại.', 409)
            ticket = (current or {}).get('revision')
        data = exporter(self.compiler).export(project, snapshot)
        with self.store.lock:
            self.compiler.verified(project, snapshot)
            if (self.status(project) or {}).get('revision') != ticket:
                raise Problem('DOCX vừa được sửa ở cửa sổ khác.', 409)
            return self.commit(project, data, {'snapshot': snapshot, 'fingerprint': pdf.fingerprint, 'edited': False, 'main': pdf.main})

    def save(self, project, revision, data):
        with self.store.lock:
            current = self.status(project)
            if not current or current['revision'] != revision:
                raise Problem('DOCX đã thay đổi ở cửa sổ khác.', 409)
            return self.commit(project, data, {**current, 'edited': True})


def download(compiler, project, snapshot):
    pdf = compiler.verified(project, snapshot)
    workspace = DocxWorkspace(compiler, pdf.main)
    with compiler.store.lock:
        current = workspace.status(project)
        if current and current['edited']:
            if current['fingerprint'] != pdf.fingerprint:
                raise Problem('PDF đã đổi trong khi DOCX có chỉnh sửa. Giữ bản Word hoặc tạo lại từ PDF trong Word preview.', 409)
            return workspace.read(project, current['revision']), current['revision']
    return exporter(compiler).export(project, snapshot), None
