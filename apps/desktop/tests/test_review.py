import pytest
from overleaf_desktop.store import Store, Problem
from overleaf_desktop.review import Review


def test_review_persists_without_changing_source_or_compile_fingerprint(tmp_path):
    store = Store(tmp_path)
    project = store.create('Review')['id']
    review = Review(store)
    source = store.path(project, 'main.tex').read_bytes()
    fingerprint = store.fingerprint(project)
    thread = review.update(project, {'operation': 'create', 'file': 'main.tex', 'from': 5, 'line': 1, 'quote': 'test', 'text': 'Nhận xét tiếng Việt'})[0]
    review.update(project, {'operation': 'reply', 'id': thread['id'], 'text': 'Trả lời'})
    review.update(project, {'operation': 'resolve', 'id': thread['id'], 'resolved': True})
    persisted = Review(Store(tmp_path)).read(project)[0]
    assert persisted['resolved'] is True
    assert len(persisted['messages']) == 2
    assert persisted['messages'][0]['text'] == 'Nhận xét tiếng Việt'
    assert store.path(project, 'main.tex').read_bytes() == source
    assert store.fingerprint(project) == fingerprint
    assert all(item['path'] != '.overleaf-review.json' for item in store.tree(project))
    review.update(project, {'operation': 'delete', 'id': thread['id']})
    assert review.read(project) == []


def test_review_rejects_unsafe_paths_empty_text_and_unknown_thread(tmp_path):
    store = Store(tmp_path)
    project = store.create('Review')['id']
    review = Review(store)
    for data in [
        {'operation': 'create', 'file': '../outside.tex', 'text': 'test'},
        {'operation': 'create', 'file': 'main.tex', 'text': '   '},
        {'operation': 'create', 'file': 'main.tex', 'text': 'ok', 'from': -1},
        {'operation': 'reply', 'id': 'missing', 'text': 'reply'},
        {'operation': 'create', 'file': 'main.tex', 'text': 'a' * 10001},
    ]:
        with pytest.raises(Problem):
            review.update(project, data)
    assert review.read(project) == []
