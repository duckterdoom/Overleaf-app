from fastapi.testclient import TestClient
from latex_backend.core.application import create_web_app
from test_web_sharing import URL,use
from test_writing_workflow import setup,submit


def test_overview_is_compact_and_batches_publications(tmp_path,mongo_database,monkeypatch):
    with TestClient(create_web_app(tmp_path),base_url=URL) as c:
        tutor,student,_,assignment=setup(c)
        first,_=submit(c,assignment)
        second,_=submit(c,assignment)
        use(c,tutor)
        reads=[]
        collection=mongo_database.writing_results
        original=collection.find
        monkeypatch.setattr(collection,'find',lambda *args,**kwargs:(reads.append(args[0]) or original(*args,**kwargs)))
        response=c.get('/api/writing/overview');response.raise_for_status()
        overview=response.json()
        assert 'rubricCatalog' not in overview['assignments'][0]
        assert len(overview['submissions'])==2
        assert all('rubricCatalog' not in row['assignment'] and 'answers' not in row for row in overview['submissions'])
        assert len(reads)==1 and set(reads[0]['_id']['$in'])=={first['id'],second['id']}
        # Detail endpoints still provide immutable grading metadata and answers.
        assert c.get('/api/writing/assignments/'+assignment['id']).json()['rubricCatalog']
        detail=c.get('/api/writing/submissions/'+first['id']).json()['submission']
        assert detail['assignment']['rubricCatalog'] and detail['answers']
