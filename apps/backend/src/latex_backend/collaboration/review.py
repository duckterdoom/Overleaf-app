"""Local project review threads, independent of LaTeX source and compile cache."""
import json
import threading
import uuid
from datetime import datetime, timezone
from latex_backend.storage.store import Problem, atomic_write


class Review:
    def __init__(self, store):
        self.store = store
        self.lock = threading.RLock()

    def path(self, project):
        path = self.store.root(project) / '.overleaf-review.json'
        if path.is_symlink():
            raise Problem('Review không được là symbolic link.')
        return path

    def read(self, project):
        with self.lock:
            if hasattr(self.store, 'records'):
                return self.store.records.read(project, 'review', [])
            path = self.path(project)
            if not path.exists():
                return []
            if path.stat().st_size > 8 * 1024 * 1024:
                raise Problem('Review vượt giới hạn 8 MiB.')
            return json.loads(path.read_text(encoding='utf-8'))

    def update(self, project, data):
        with self.lock:
            threads = self.read(project)
            operation = data.get('operation')
            session = self.store.state.session or {}
            author = str(session.get('name') or 'Local User')[:80]
            now = datetime.now(timezone.utc).isoformat()
            if operation in {'create', 'reply'}:
                text = data.get('text', '')
                if not isinstance(text, str) or not text.strip() or len(text) > 10000:
                    raise Problem('Nhận xét cần từ 1 đến 10.000 ký tự.')
                message = {'id': uuid.uuid4().hex, 'author': author, 'time': now, 'text': text.strip()}
            if operation == 'create':
                file = data.get('file', '')
                if not self.store.path(project, file).is_file():
                    raise Problem('Không tìm thấy tệp.')
                quote = data.get('quote', '')
                start = data.get('from', 0)
                line = data.get('line', 1)
                if not isinstance(quote, str) or len(quote) > 20000 or type(start) is not int or start < 0 or type(line) is not int or line < 1:
                    raise Problem('Vị trí nhận xét không hợp lệ.')
                if len(threads) >= 2000:
                    raise Problem('Đã đạt giới hạn 2.000 nhận xét.')
                threads.append({'id': uuid.uuid4().hex, 'file': file, 'from': start, 'line': line, 'quote': quote, 'resolved': False, 'messages': [message]})
            else:
                thread = next((item for item in threads if item['id'] == data.get('id')), None)
                if thread is None:
                    raise Problem('Không tìm thấy nhận xét.', 404)
                if operation == 'reply':
                    if len(thread['messages']) >= 200:
                        raise Problem('Đã đạt giới hạn trả lời.')
                    thread['messages'].append(message)
                elif operation == 'resolve':
                    if type(data.get('resolved')) is not bool:
                        raise Problem('Trạng thái không hợp lệ.')
                    thread['resolved'] = data['resolved']
                elif operation == 'delete':
                    threads.remove(thread)
                else:
                    raise Problem('Thao tác review không hợp lệ.')
            payload = json.dumps(threads, ensure_ascii=False).encode('utf-8')
            if len(payload) > 8 * 1024 * 1024:
                raise Problem('Review vượt giới hạn 8 MiB.')
            if hasattr(self.store, 'records'):
                self.store.records.write(project, 'review', threads)
            else:
                atomic_write(self.path(project), payload)
            return threads
