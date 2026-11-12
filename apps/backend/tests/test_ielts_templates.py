"""The question bank must cover the same profiles Tutors can grade."""
import csv
import io
import json
import re

from latex_backend.core.config import RESOURCES
from latex_backend.storage.store import Templates


def test_ielts_bank_covers_every_profile_and_has_assignable_question_files(tmp_path):
    templates = Templates()
    profiles = json.loads((RESOURCES / 'writing/catalog.json').read_text(encoding='utf-8'))
    items = [t for t in templates.public()['templates'] if t['category'] == 'ielts']
    assert [item['id'] for item in items] == ['ielts-complete-bank']
    for language in ('en', 'vi'):
        folder = tmp_path / language
        folder.mkdir()
        templates.write(folder, 'ielts-complete-bank', 'IELTS & charts', language)
        bank = json.loads((folder / 'ielts-bank.json').read_text(encoding='utf-8'))
        assert {p['id'] for p in bank['profiles']} == set(profiles)
        assert bank['originalPractice'] is True
        for item in bank['profiles']:
            standalone = (folder / item['questionFile']).read_text(encoding='utf-8')
            body = (folder / item['bodyFile']).read_text(encoding='utf-8')
            assert r'\documentclass' in standalone and r'\input{preamble}' in standalone
            assert 'WRITING TASK ' + str(item['task']) in body
            assert '150 words' in body if item['task'] == 1 else '250 words' in body
            assert 'Not an official' in body
            if profiles[item['id']]['family'] == 'academic':
                assert r'\begin{tikzpicture}' in body or r'\begin{tabular}' in body
            if profiles[item['id']]['family'] == 'general':
                assert body.count(r'\item ') == 3
        assert r'IELTS \& charts' in (folder / 'main.tex').read_text(encoding='utf-8')
        assert 'question-task1-bar.tex' in (folder / 'GUIDE.md').read_text(encoding='utf-8')
        assert (folder / 'answers.tex').is_file()


def test_bank_has_consistent_chart_data(tmp_path):
    templates = Templates()
    folder = tmp_path / 'bank'
    folder.mkdir()
    templates.write(folder, 'ielts-complete-bank', 'Bank', 'en')
    rows = list(csv.DictReader(io.StringIO((folder / 'data/task1-bar.csv').read_text(encoding='utf-8'))))
    assert sum(int(r['2005_percent']) for r in rows) == 100
    assert sum(int(r['2025_percent']) for r in rows) == 100
    source = (folder / 'questions/task1-bar.tex').read_text(encoding='utf-8')
    for row in rows:
        assert f"/{row['2005_percent']}/{row['2025_percent']}/{row['mode']}" in source
