"""Bounded source-only ZIP downloads; authorization is resolved before building."""
import io
import re
import zipfile
from latex_backend.storage.store import Problem


def build_archive(projects):
    output = io.BytesIO()
    total = count = 0
    with zipfile.ZipFile(output, 'w', zipfile.ZIP_DEFLATED) as archive:
        for store, project in projects:
            with store.lock:
                title = re.sub(r'[^\w -]', '_', store.info(project)['title'])[:80] or 'project'
                prefix = title + '-' + project[:8] + '/'
                for item in store.tree(project):
                    path = store.path(project, item['path'])
                    count += 1
                    if count > 20000:
                        raise Problem('Select fewer projects: archive exceeds 20,000 entries.', 413)
                    if item['directory']:
                        archive.writestr(prefix + item['path'] + '/', b'')
                        continue
                    total += path.stat().st_size
                    if total > 128 * 1024 * 1024:
                        raise Problem('Select fewer projects: archive exceeds 128 MiB.', 413)
                    archive.write(path, prefix + item['path'])
    return output.getvalue()
