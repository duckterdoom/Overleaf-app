"""Read-only bundled template inspection and bounded, isolated PDF previews."""
import asyncio
from collections import OrderedDict
from datetime import date, datetime, timezone
from pathlib import Path
from tempfile import TemporaryDirectory

from latex_backend.compilation.compiler import Compiler
from latex_backend.storage.store import Problem, Store, TEXT_SUFFIXES


class TemplatePreviews:
    def __init__(self, templates, executable):
        self.templates, self.executable = templates, executable
        self.cache = OrderedDict()
        self.tasks = {}
        self.slot = asyncio.Semaphore(1)

    def item(self, key, language):
        item = self.templates.items.get(key)
        if not item or item['category'] in {'internal', 'ielts'}:
            raise Problem('Template not found.', 404)
        if language not in {'en', 'vi'}:
            raise Problem('Choose English or Vietnamese.', 422)
        return item

    def source(self, key, language):
        item = self.item(key, language)
        with TemporaryDirectory(prefix='latex-template-source-') as folder:
            root = Path(folder)
            self.templates.write(root, key, item['title'][language], language)
            files = []
            total = 0
            for path in sorted(root.rglob('*')):
                if not path.is_file():
                    continue
                content = None
                if path.suffix.lower() in TEXT_SUFFIXES:
                    total += path.stat().st_size
                    if total > 4 * 1024 * 1024:
                        raise Problem('Template source is too large to display.', 413)
                    content = path.read_text(encoding='utf-8')
                files.append({'path': path.relative_to(root).as_posix(), 'content': content})
        metadata = self.templates.root / item['resource'] / 'template.json'
        return {'template': {**{k: v for k, v in item.items() if k != 'resource'},
                            'author': item.get('author', 'Latex built-in library'),
                            'license': item.get('license'),
                            'updated': datetime.fromtimestamp(metadata.stat().st_mtime, timezone.utc).isoformat()},
                'files': files}

    async def preview(self, key, language):
        self.item(key, language)
        cache_key = (key, language, date.today().isoformat())
        if cache_key in self.cache:
            self.cache.move_to_end(cache_key)
            return self.cache[cache_key]
        if cache_key not in self.tasks:
            if len(self.tasks) >= 8:
                raise Problem('Template previews are busy. Please try again shortly.', 429)
            task = asyncio.create_task(self._build(cache_key))
            self.tasks[cache_key] = task
            def finished(completed):
                self.tasks.pop(cache_key, None)
                if not completed.cancelled():
                    completed.exception()  # Observe failures even if the browser left.
            task.add_done_callback(finished)
        return await asyncio.shield(self.tasks[cache_key])

    async def _build(self, cache_key):
        key, language, _ = cache_key
        async with self.slot:
            item = self.item(key, language)
            with TemporaryDirectory(prefix='latex-template-preview-') as folder:
                store = Store(Path(folder), templates=self.templates)
                project = await asyncio.to_thread(store.create, item['title'][language], key, language)
                compiler = Compiler(store, self.executable)
                job = compiler.start(project['id'], item['engine'], item['mainFile'])
                snapshot = None
                errors = []
                try:
                    while True:
                        event = await job.queue.get()
                        if event['type'] == 'error':
                            errors.append(event.get('message', 'Compilation failed.'))
                        elif event['type'] == 'done' and event.get('current'):
                            snapshot = event['snapshot']
                        elif event['type'] == 'end':
                            break
                    if not snapshot:
                        raise Problem('Template PDF could not be compiled on the server. ' + ' '.join(errors)[-2400:], 422)
                    data = compiler.verified(project['id'], snapshot).data
                    if len(data) > 16 * 1024 * 1024:
                        raise Problem('Template PDF exceeds the preview size limit.', 413)
                    thumbnail = await asyncio.to_thread(self._thumbnail, data)
                finally:
                    if job.task and not job.task.done():
                        await compiler.cancel(project['id'])
                result = {'pdf': data, 'thumbnail': thumbnail}
            while self.cache and (len(self.cache) >= 24 or sum(len(v['pdf']) + len(v['thumbnail']) for v in self.cache.values()) + len(data) + len(thumbnail) > 64 * 1024 * 1024):
                self.cache.popitem(last=False)
            self.cache[cache_key] = result
            return result

    @staticmethod
    def _thumbnail(data):
        import pymupdf
        with pymupdf.open(stream=data, filetype='pdf') as pdf:
            if not pdf.page_count:
                raise Problem('Template PDF contains no pages.', 422)
            page = pdf[0]
            return page.get_pixmap(matrix=pymupdf.Matrix(420 / max(page.rect.width, 1), 420 / max(page.rect.width, 1)), alpha=False).tobytes('png')

    async def close(self):
        tasks = list(self.tasks.values())
        for task in tasks:
            task.cancel()
        await asyncio.gather(*tasks, return_exceptions=True)
