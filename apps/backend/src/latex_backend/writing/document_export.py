"""Export published writing as a new, privately owned four-file LaTeX project."""
import asyncio
from latex_backend.storage.store import Problem, atomic_write


def document_files(service, user, submission):
    sources = {name: service.latex(user, submission=submission, report=True, part=part)[0]
               for name, part in [('question.tex', 'question'), ('answer.tex', 'answer'), ('feedback.tex', 'feedback')]}
    # Use canonical escaped answers; never execute the learner's custom source.
    header, _ = sources['question.tex'].split(r'\begin{document}', 1)
    files = {}
    for name, source in sources.items():
        _, body = source.split(r'\begin{document}', 1)
        body = body.rsplit(r'\end{document}', 1)[0]
        files[name] = (r'\ifdefined\WritingCombined\else' + '\n' + header + r'\begin{document}' + '\n' + r'\fi' + '\n' + body + '\n'
                       + r'\ifdefined\WritingCombined\let\FinishWritingPart\relax\else\def\FinishWritingPart{\end{document}}\fi' + '\n' + r'\FinishWritingPart' + '\n')
    files['main.tex'] = header + r'\begin{document}' + '\n' + r'\def\WritingCombined{1}' + '\n' + '\n'.join(
        r'\input{' + name + '}' for name in ('question.tex', 'answer.tex', 'feedback.tex')) + '\n' + r'\end{document}' + '\n'
    return files


async def export_document(gateway, service, user, submission):
    view = service.review(user, submission)
    if not view['published']:
        raise Problem('Publish the assessment before exporting its document.', 409)
    assignment = view['submission']['assignment']
    files = document_files(service, user, submission)
    payloads = {}
    for task, attachment in assignment.get('promptFiles', {}).items():
        if attachment['kind'] in {'pdf', 'image'}:
            record = gateway.database.writing_prompt_files.find_one({'_id': attachment['id']})
            if not record:
                raise Problem('The question attachment is no longer available.', 409)
            extension = 'pdf' if attachment['kind'] == 'pdf' else 'png'
            payloads['question-task-' + task + '.' + extension] = bytes(record['data'])
    image = assignment.get('imageId')
    if image:
        if image == 'sample-commuting':
            from latex_backend.writing.assets import commuting_png
            payloads['prompt-image.png'] = commuting_png()
        else:
            record = gateway.database.writing_assets.find_one({'_id': image})
            if not record:
                raise Problem('The question image is no longer available.', 409)
            payloads['prompt-image.png'] = bytes(record['data'])
    current = gateway.database.writing_results.find_one({'_id': submission})
    if not current or current['publishedVersion'] != view['published']['publishedVersion']:
        raise Problem('Published results changed. Retry the export.', 409)
    _, store = await gateway.workspace(user)
    def create():
        title = f"{assignment['title']} - {view['submission']['studentName']}"
        project = store.create(title[:120])
        try:
            if store.path(project['id'], 'preamble.tex').is_file():
                store.delete_file(project['id'], 'preamble.tex')
            for name, text in files.items():
                if not store.path(project['id'], name).is_file():
                    store.add(project['id'], name, False)
                store.save(project['id'], name, text)
            with store.shared_files.edit(project['id']):
                for name, payload in payloads.items():
                    atomic_write(store.path(project['id'], name), payload)
            gateway.sharing.register(project['id'], user['id'])
            return store.info(project['id'])
        except Exception:
            store.delete_project(project['id'])
            raise
    project = await asyncio.to_thread(create)
    return {'project': project, 'source': files['main.tex'], 'files': list(files)}
